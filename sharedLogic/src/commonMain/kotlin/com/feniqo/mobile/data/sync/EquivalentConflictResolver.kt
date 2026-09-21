package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionParticipantShareDto
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.serialization.json.Json

/**
 * Yanıt kaybı sonrası CREATE tekrarında oluşan sahte (spurious) çakışmaları tespit etmek için
 * yerel ve uzak kayıtları iş alanları düzeyinde karşılaştıran tip güvenli eşdeğerlik çözücü.
 */
object EquivalentConflictResolver {

    private val json = Json { ignoreUnknownKeys = true }

    fun isEquivalent(
        conflict: SyncConflictEntity,
        operation: SyncOperationEntity,
    ): Boolean {
        if (operation.operationTypeCode != "CREATE") return false
        if (conflict.operationId != operation.operationId) return false
        if (conflict.entityTypeCode != operation.entityTypeCode) return false
        if (conflict.entityId != operation.entityId) return false

        val entityType = runCatching { SyncEntityType.valueOf(conflict.entityTypeCode) }
            .getOrNull() ?: return false

        return when (entityType) {
            SyncEntityType.TRANSACTION -> isTransactionEquivalent(conflict.localPayloadJson, conflict.remotePayloadJson)
            SyncEntityType.CATEGORY -> isCategoryEquivalent(conflict.localPayloadJson, conflict.remotePayloadJson)
            SyncEntityType.PROFILE -> isProfileEquivalent(conflict.localPayloadJson, conflict.remotePayloadJson)
            else -> false
        }
    }

    fun isTransactionEquivalent(localJson: String, remoteJson: String): Boolean {
        val local = runCatching { json.decodeFromString<TransactionDto>(localJson) }.getOrNull() ?: return false
        val remote = runCatching { json.decodeFromString<TransactionDto>(remoteJson) }.getOrNull() ?: return false

        val localPayer = local.paidByUserId?.takeIf { it.isNotBlank() } ?: local.userId
        val remotePayer = remote.paidByUserId?.takeIf { it.isNotBlank() } ?: remote.userId

        val localContract = validateSplitContract(local, localPayer) ?: return false
        val remoteContract = validateSplitContract(remote, remotePayer) ?: return false

        when {
            localContract is ValidatedSplitContract.Equal && remoteContract is ValidatedSplitContract.Equal -> {
                if (localContract.participants != remoteContract.participants) return false
            }
            localContract is ValidatedSplitContract.Custom && remoteContract is ValidatedSplitContract.Custom -> {
                if (localContract.participants != remoteContract.participants) return false
                if (localContract.shares != remoteContract.shares) return false
            }
            else -> return false
        }

        return local.id == remote.id &&
            local.userId == remote.userId &&
            local.workspaceId == remote.workspaceId &&
            localPayer == remotePayer &&
            local.amountMinor == remote.amountMinor &&
            local.currency.trim().uppercase() == remote.currency.trim().uppercase() &&
            local.type.trim().lowercase() == remote.type.trim().lowercase() &&
            local.categoryId == remote.categoryId &&
            local.description.orEmpty().trim() == remote.description.orEmpty().trim() &&
            local.paymentMethod.trim().lowercase() == remote.paymentMethod.trim().lowercase() &&
            local.transactionDate.trim() == remote.transactionDate.trim() &&
            local.receiptPath.orEmpty().trim() == remote.receiptPath.orEmpty().trim() &&
            local.installmentNumber == remote.installmentNumber &&
            local.totalInstallments == remote.totalInstallments &&
            local.installmentGroupId == remote.installmentGroupId
    }

    private sealed interface ValidatedSplitContract {
        data class Equal(val participants: List<String>) : ValidatedSplitContract
        data class Custom(val participants: Set<String>, val shares: List<TransactionParticipantShareDto>) : ValidatedSplitContract
    }

    private fun validateSplitContract(dto: TransactionDto, payer: String): ValidatedSplitContract? {
        if (payer.isBlank()) return null
        val rawMode = dto.splitMode
        val mode = if (rawMode == null) {
            "EQUAL"
        } else {
            val trimmed = rawMode.trim()
            if (trimmed.isEmpty()) return null
            val upper = trimmed.uppercase()
            if (upper != "EQUAL" && upper != "CUSTOM") return null
            upper
        }

        return when (mode) {
            "EQUAL" -> {
                if (!dto.participantShares.isNullOrEmpty()) return null
                val participants = dto.participantUserIds.filter { it.isNotBlank() }
                    .ifEmpty { listOf(payer) }
                if (dto.participantUserIds.isNotEmpty() && dto.participantUserIds.any { it.isBlank() }) return null
                if (dto.participantUserIds.distinct().size != dto.participantUserIds.size) return null
                if (dto.participantUserIds.isNotEmpty() && payer !in participants) return null
                ValidatedSplitContract.Equal(participants)
            }
            "CUSTOM" -> {
                if (dto.participantUserIds.isEmpty()) return null
                if (dto.participantUserIds.any { it.isBlank() }) return null
                val participantSet = dto.participantUserIds.toSet()
                if (participantSet.size != dto.participantUserIds.size) return null

                val shares = dto.participantShares
                if (shares.isNullOrEmpty()) return null

                val seenShareUsers = mutableSetOf<String>()
                var sum = 0L

                for (share in shares) {
                    val userId = share.userId.trim()
                    if (userId.isEmpty()) return null
                    if (!seenShareUsers.add(userId)) return null
                    if (share.amountMinor < 0L) return null
                    if (userId != payer && share.amountMinor <= 0L) return null

                    if (Long.MAX_VALUE - sum < share.amountMinor) return null
                    sum += share.amountMinor
                }

                if (payer !in seenShareUsers) return null
                if (seenShareUsers != participantSet) return null
                if (sum != dto.amountMinor) return null

                ValidatedSplitContract.Custom(
                    participants = participantSet,
                    shares = shares.sortedBy { it.userId }
                )
            }
            else -> null
        }
    }

    fun isCategoryEquivalent(localJson: String, remoteJson: String): Boolean {
        val local = runCatching { json.decodeFromString<CategoryDto>(localJson) }.getOrNull() ?: return false
        val remote = runCatching { json.decodeFromString<CategoryDto>(remoteJson) }.getOrNull() ?: return false

        return local.id == remote.id &&
            local.userId == remote.userId &&
            local.workspaceId == remote.workspaceId &&
            local.name.trim().lowercase() == remote.name.trim().lowercase() &&
            local.slug.orEmpty().trim().lowercase() == remote.slug.orEmpty().trim().lowercase() &&
            local.type.trim().lowercase() == remote.type.trim().lowercase() &&
            local.color.trim().lowercase() == remote.color.trim().lowercase() &&
            local.icon.orEmpty().trim().lowercase() == remote.icon.orEmpty().trim().lowercase() &&
            local.isDefault == remote.isDefault
    }

    fun isProfileEquivalent(localJson: String, remoteJson: String): Boolean {
        val local = runCatching { json.decodeFromString<ProfileDto>(localJson) }.getOrNull() ?: return false
        val remote = runCatching { json.decodeFromString<ProfileDto>(remoteJson) }.getOrNull() ?: return false

        return local.id == remote.id &&
            local.email.trim().lowercase() == remote.email.trim().lowercase() &&
            local.fullName.orEmpty().trim() == remote.fullName.orEmpty().trim() &&
            local.currency.trim().uppercase() == remote.currency.trim().uppercase() &&
            local.theme.trim().lowercase() == remote.theme.trim().lowercase() &&
            local.lang.trim().lowercase() == remote.lang.trim().lowercase() &&
            local.activeWorkspaceId == remote.activeWorkspaceId
    }
}
