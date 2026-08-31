package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
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

        return local.id == remote.id &&
            local.userId == remote.userId &&
            local.workspaceId == remote.workspaceId &&
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
