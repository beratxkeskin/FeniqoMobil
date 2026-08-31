package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.serialization.json.Json

/**
 * Sahte (equivalent) CREATE çakışmalarını tekil veya taksit grubu olarak atomik biçimde kurtaran servis.
 */
class ConflictRecoveryService(
    private val syncStateDao: SyncStateDao,
    private val syncOperationDao: SyncOperationDao,
    private val localMutationDao: LocalMutationDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tekil bir CREATE çakışmasını inceler; operasyon durumu CONFLICT ve yerel/uzak kayıt tam eşdeğerse
     * tek Room transaction'ında çözer. IN_FLIGHT veya farklı durumlardaki operasyonlara dokunmaz.
     */
    suspend fun recoverSingleEquivalentCreate(entityTypeCode: String, entityId: String): Boolean {
        val conflict = syncStateDao.getConflict(entityTypeCode, entityId) ?: return false
        val operation = syncOperationDao.getById(conflict.operationId) ?: return false

        if (operation.statusCode != "CONFLICT") {
            return false
        }

        if (!EquivalentConflictResolver.isEquivalent(conflict, operation)) {
            return false
        }

        val now = nowEpochMillisProvider()
        return when (conflict.entityTypeCode) {
            "CATEGORY" -> {
                val remoteDto = runCatching { json.decodeFromString<CategoryDto>(conflict.remotePayloadJson) }.getOrNull() ?: return false
                localMutationDao.recoverEquivalentCategory(operation.operationId, remoteDto, now)
            }
            "TRANSACTION" -> {
                val remoteDto = runCatching { json.decodeFromString<TransactionDto>(conflict.remotePayloadJson) }.getOrNull() ?: return false
                localMutationDao.recoverEquivalentTransaction(operation.operationId, remoteDto, now)
            }
            else -> false
        }
    }

    /**
     * Taksit grubuna ait tüm çakışmaları inceler; grubun tamamı CONFLICT durumunda, eksiksiz ve eşdeğerse
     * tek Room transaction'ında all-or-nothing çözer.
     * Herhangi bir taksit eksikse, IN_FLIGHT ise veya eşdeğer değilse hiçbir taksite dokunulmaz.
     */
    suspend fun recoverInstallmentGroup(installmentGroupId: String): Boolean {
        require(installmentGroupId.isNotBlank()) { "installmentGroupId boş olamaz" }

        val allTxConflicts = syncStateDao.getConflictsByEntityType("TRANSACTION")
        val groupConflicts = allTxConflicts.filter { conflict ->
            val localDto = runCatching { json.decodeFromString<TransactionDto>(conflict.localPayloadJson) }.getOrNull()
            localDto?.installmentGroupId == installmentGroupId
        }

        if (groupConflicts.isEmpty()) return false

        val pairs = mutableListOf<Pair<SyncConflictEntity, SyncOperationEntity>>()
        val remoteDtos = mutableListOf<TransactionDto>()

        for (conflict in groupConflicts) {
            val operation = syncOperationDao.getById(conflict.operationId) ?: return false
            if (operation.statusCode != "CONFLICT") {
                return false
            }
            if (!EquivalentConflictResolver.isEquivalent(conflict, operation)) {
                return false
            }
            val remoteDto = runCatching { json.decodeFromString<TransactionDto>(conflict.remotePayloadJson) }.getOrNull()
                ?: return false
            pairs.add(conflict to operation)
            remoteDtos.add(remoteDto)
        }

        val totalInstallments = remoteDtos.first().totalInstallments ?: return false
        if (remoteDtos.size != totalInstallments) {
            return false // Eksik taksit var, all-or-nothing kuralı gereği çözme!
        }

        val installmentNumbers = remoteDtos.mapNotNull { it.installmentNumber }.toSet()
        if (installmentNumbers.size != totalInstallments || (1..totalInstallments).any { it !in installmentNumbers }) {
            return false
        }

        // Taksit grubunun tamamı CONFLICT ve eşdeğer: tek Room transaction içinde atomik çöz
        val now = nowEpochMillisProvider()
        val units = pairs.mapIndexed { index, (_, op) ->
            op.operationId to remoteDtos[index]
        }
        return localMutationDao.recoverEquivalentTransactionGroup(units, now)
    }

    /**
     * Tüm bekleyen çakışmaları tarayarak eşdeğer (equivalent) olanları otomatik kurtarır.
     * Önce taksit gruplarını, ardından tekil TRANSACTION ve CATEGORY çakışmalarını inceler.
     * Yalnızca CONFLICT, CREATE, protocol 1 veya 2 ve tam eşdeğer kayıtları çözer; gerçek çakışmalara dokunmaz.
     */
    suspend fun recoverAllPendingConflicts(): Int {
        var recoveredCount = 0

        // 1. Önce transaction çakışmalarını incele ve taksit gruplarını topla
        val txConflicts = syncStateDao.getConflictsByEntityType("TRANSACTION")
        val installmentGroupEntityIds = mutableSetOf<String>()

        val installmentGroupIds = txConflicts.mapNotNull { conflict ->
            val localDto = runCatching { json.decodeFromString<TransactionDto>(conflict.localPayloadJson) }.getOrNull()
            if (localDto?.installmentGroupId?.isNotBlank() == true) {
                installmentGroupEntityIds.add(conflict.entityId)
                localDto.installmentGroupId
            } else {
                null
            }
        }.distinct()

        for (groupId in installmentGroupIds) {
            val success = recoverInstallmentGroup(groupId)
            if (success) {
                val groupConflicts = txConflicts.filter { conflict ->
                    val localDto = runCatching { json.decodeFromString<TransactionDto>(conflict.localPayloadJson) }.getOrNull()
                    localDto?.installmentGroupId == groupId
                }
                recoveredCount += groupConflicts.size
            }
        }

        // 2. Geriye kalan tekil TRANSACTION çakışmalarını çöz (taksit gruplarına ait olanlar asla tekil döngüye girmez)
        for (conflict in txConflicts) {
            if (conflict.entityId in installmentGroupEntityIds) continue
            val success = recoverSingleEquivalentCreate("TRANSACTION", conflict.entityId)
            if (success) {
                recoveredCount++
            }
        }

        // 3. CATEGORY çakışmalarını çöz
        val categoryConflicts = syncStateDao.getConflictsByEntityType("CATEGORY")
        for (conflict in categoryConflicts) {
            val success = recoverSingleEquivalentCreate("CATEGORY", conflict.entityId)
            if (success) {
                recoveredCount++
            }
        }

        return recoveredCount
    }
}
