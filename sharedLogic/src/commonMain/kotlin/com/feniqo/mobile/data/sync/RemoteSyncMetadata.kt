package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.domain.model.SyncStatus
import kotlin.time.Instant

internal fun ProfileDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = null,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun CategoryDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun TransactionDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun BudgetDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun RecurringTransactionDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun SubscriptionDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun GoalDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun GoalContributionDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun DebtDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = updatedAt ?: createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)

internal fun DebtPaymentDto.toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata = remoteSyncMetadata(
    updatedAt = createdAt,
    deletedAt = deletedAt,
    version = version,
    receivedAtEpochMillis = receivedAtEpochMillis,
)



private fun remoteSyncMetadata(
    updatedAt: String,
    deletedAt: String?,
    version: Long?,
    receivedAtEpochMillis: Long,
): SyncMetadata = SyncMetadata(
    syncStatus = SyncStatus.SYNCED.name,
    updatedAtEpochMillis = updatedAt.toEpochMillis("updated_at"),
    localUpdatedAtEpochMillis = receivedAtEpochMillis,
    deletedAtEpochMillis = deletedAt?.toEpochMillis("deleted_at"),
    version = requireNotNull(version) { "Uzak kayıt version alanı taşımıyor; sync migration uygulanmadan devam edilemez." },
    baseVersion = requireNotNull(version),
    lastSyncError = null,
)

private fun String.toEpochMillis(field: String): Long = try {
    Instant.parse(this).toEpochMilliseconds()
} catch (error: IllegalArgumentException) {
    throw IllegalArgumentException("Uzak $field alanı geçerli UTC zaman damgası değil.", error)
}
