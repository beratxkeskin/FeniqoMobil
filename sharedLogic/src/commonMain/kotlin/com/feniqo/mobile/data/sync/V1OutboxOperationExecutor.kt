package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** V1 profile/category/transaction outbox kayıtlarını koşullu RPC üzerinden uygular. */
class V1OutboxOperationExecutor(
    private val writer: ConditionalRemoteWriter,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
) : OutboxOperationExecutor {

    private val snapshotJson = Json { encodeDefaults = true; explicitNulls = true }

    override suspend fun execute(operation: SyncOperationEntity) {
        when (SyncEntityType.valueOf(operation.entityTypeCode)) {
            SyncEntityType.PROFILE -> executeProfile(operation)
            SyncEntityType.CATEGORY -> executeCategory(operation)
            SyncEntityType.TRANSACTION -> executeTransaction(operation)
            else -> error("V1 outbox henüz ${operation.entityTypeCode} türünü desteklemiyor.")
        }
    }

    private suspend fun executeProfile(operation: SyncOperationEntity) {
        val local = requireNotNull(remoteSyncDao.getProfileRow(operation.entityId)) {
            "Outbox profil kaydının yerel karşılığı bulunamadı."
        }
        val dto = local.toDomain().toDto()
        when (val result = writer.writeProfile(local.sync.writeOperation(), local.sync.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> remoteSyncDao.applyProfileWrite(
                result.record.toDomain().toEntity(result.record.syncedMetadata()),
            )
            is ConditionalRemoteWriteResult.Conflict -> {
                remoteSyncDao.recordProfileConflict(operation.conflict(local.sync, dto, result.remoteRecord))
                throw OutboxConflictException(RemoteSyncDao.CONFLICT_ERROR)
            }
            ConditionalRemoteWriteResult.NotFound -> error("Profil koşullu yazma sırasında bulunamadı.")
        }
    }

    private suspend fun executeCategory(operation: SyncOperationEntity) {
        val local = requireNotNull(remoteSyncDao.getCategoryRow(operation.entityId)) {
            "Outbox kategori kaydının yerel karşılığı bulunamadı."
        }
        val dto = local.toDomain().toDto().copy(slug = local.slug)
        when (val result = writer.writeCategory(local.sync.writeOperation(), local.sync.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> remoteSyncDao.applyCategoryWrite(
                result.record.toDomain().toEntity(result.record.syncedMetadata(), slug = result.record.slug),
            )
            is ConditionalRemoteWriteResult.Conflict -> {
                remoteSyncDao.recordCategoryConflict(operation.conflict(local.sync, dto, result.remoteRecord))
                throw OutboxConflictException(RemoteSyncDao.CONFLICT_ERROR)
            }
            ConditionalRemoteWriteResult.NotFound -> {
                if (local.sync.deletedAtEpochMillis == null) {
                    error("Kategori koşullu yazma sırasında bulunamadı.")
                }
                remoteSyncDao.applyCategoryWrite(local.copy(sync = local.sync.acknowledgedMissingDelete()))
            }
        }
    }

    private suspend fun executeTransaction(operation: SyncOperationEntity) {
        val local = requireNotNull(remoteSyncDao.getTransactionRow(operation.entityId)) {
            "Outbox işlem kaydının yerel karşılığı bulunamadı."
        }
        val dto = local.toDomain().toDto()
        when (val result = writer.writeTransaction(local.sync.writeOperation(), local.sync.baseVersion, dto)) {
            is ConditionalRemoteWriteResult.Applied -> remoteSyncDao.applyTransactionWrite(
                result.record.toDomain().toEntity(result.record.syncedMetadata()),
            )
            is ConditionalRemoteWriteResult.Conflict -> {
                remoteSyncDao.recordTransactionConflict(operation.conflict(local.sync, dto, result.remoteRecord))
                throw OutboxConflictException(RemoteSyncDao.CONFLICT_ERROR)
            }
            ConditionalRemoteWriteResult.NotFound -> {
                if (local.sync.deletedAtEpochMillis == null) {
                    error("İşlem koşullu yazma sırasında bulunamadı.")
                }
                remoteSyncDao.applyTransactionWrite(local.copy(sync = local.sync.acknowledgedMissingDelete()))
            }
        }
    }

    private fun SyncMetadata.writeOperation(): RemoteWriteOperation = when {
        deletedAtEpochMillis != null -> RemoteWriteOperation.DELETE
        baseVersion == null -> RemoteWriteOperation.CREATE
        else -> RemoteWriteOperation.UPDATE
    }

    private fun SyncMetadata.acknowledgedMissingDelete(): SyncMetadata = copy(
        syncStatus = SyncStatus.SYNCED.name,
        localUpdatedAtEpochMillis = nowEpochMillisProvider(),
        baseVersion = baseVersion ?: version.takeIf { it > 0 },
        lastSyncError = null,
    )

    private inline fun <reified T : Any> SyncOperationEntity.conflict(
        localSync: SyncMetadata,
        local: T,
        remote: T,
    ): SyncConflictEntity = SyncConflictEntity(
        entityTypeCode = entityTypeCode,
        entityId = entityId,
        operationId = operationId,
        localVersion = localSync.version,
        remoteVersion = remote.versionOrZero(),
        localPayloadJson = snapshotJson.encodeToString(local),
        remotePayloadJson = snapshotJson.encodeToString(remote),
        detectedAtEpochMillis = nowEpochMillisProvider(),
    )

    private fun Any.versionOrZero(): Long = when (this) {
        is ProfileDto -> version
        is CategoryDto -> version
        is TransactionDto -> version
        else -> null
    } ?: 0L

    private fun ProfileDto.syncedMetadata(): SyncMetadata = toRemoteSyncMetadata(nowEpochMillisProvider())
    private fun CategoryDto.syncedMetadata(): SyncMetadata = toRemoteSyncMetadata(nowEpochMillisProvider())
    private fun TransactionDto.syncedMetadata(): SyncMetadata = toRemoteSyncMetadata(nowEpochMillisProvider())
}
