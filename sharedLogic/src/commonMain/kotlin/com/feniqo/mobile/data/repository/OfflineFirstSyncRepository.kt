package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.toRemoteSyncMetadata
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncEntityType
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/** UI'a yalnız Room/outbox tabanlı gözlem sunan ortak senkronizasyon repository'si. */
class OfflineFirstSyncRepository(
    private val authRepository: AuthRepository,
    private val initialRemoteSync: InitialRemoteSync,
    private val outboxProcessor: OutboxProcessor,
    private val incrementalRemoteSync: IncrementalRemoteSync,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val syncStateDao: SyncStateDao,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
) : SyncRepository {
    private val mutex = Mutex()
    private val phase = MutableStateFlow(SyncPhase.IDLE)
    private val lastSuccessfulSyncAt = MutableStateFlow<Instant?>(null)
    private val lastError = MutableStateFlow<AppError?>(null)
    private val snapshotJson = Json { ignoreUnknownKeys = true }

    override fun observeOverview(): Flow<SyncOverview> {
        val counts = combine(
            phase,
            offlineWriteQueue.observePendingCount(),
            syncStateDao.observeConflictCount(),
        ) { currentPhase, pendingCount, conflictCount ->
            Triple(currentPhase, pendingCount, conflictCount)
        }
        val outcome = combine(lastSuccessfulSyncAt, lastError) { successfulAt, error -> successfulAt to error }
        return combine(counts, outcome) { (currentPhase, pendingCount, conflictCount), (successfulAt, error) ->
            SyncOverview(currentPhase, pendingCount, conflictCount, successfulAt, error)
        }
    }

    override fun observeConflicts(): Flow<List<SyncConflict>> = syncStateDao.observeConflicts().map { conflicts ->
        conflicts.map { conflict ->
            SyncConflict(
                entityId = EntityId(conflict.entityId),
                entityType = SyncEntityType.valueOf(conflict.entityTypeCode),
                localVersion = conflict.localVersion,
                remoteVersion = conflict.remoteVersion,
            )
        }
    }

    override suspend fun requestSync(): RepositoryResult<Unit> = mutex.withLock {
        phase.value = SyncPhase.SYNCING
        lastError.value = null
        try {
            val session = authRepository.observeSession().first()
                ?: return@withLock fail(AppError.Authentication("sync.session_required"))

            if (syncStateDao.getCursor(SyncEntityType.PROFILE.name) == null) {
                initialRemoteSync.pullFor(session.userId)
            }

            val outboxResult = outboxProcessor.processReadyOperations()
            if (outboxResult.failedOperationId != null) {
                return@withLock fail(AppError.Network("sync.push_failed"))
            }

            val pullResult = incrementalRemoteSync.pullFor(session.userId)
            val conflictDetected = outboxResult.conflictOperationId != null || pullResult.conflictCount > 0

            lastSuccessfulSyncAt.value = Instant.fromEpochMilliseconds(nowEpochMillisProvider())
            phase.value = SyncPhase.IDLE
            if (conflictDetected) {
                val error = AppError.Conflict("sync.user_resolution_required")
                lastError.value = error
                RepositoryResult.Failure(error)
            } else {
                RepositoryResult.Success(Unit)
            }
        } catch (cancelled: CancellationException) {
            phase.value = SyncPhase.IDLE
            throw cancelled
        } catch (_: Throwable) {
            fail(AppError.Network("sync.failed"))
        }
    }

    override suspend fun retryFailedOperations(): RepositoryResult<Unit> {
        offlineWriteQueue.retryAllFailed()
        return requestSync()
    }

    override suspend fun resolveConflict(
        entityId: EntityId,
        resolution: ConflictResolution,
    ): RepositoryResult<Unit> = mutex.withLock {
        try {
            val conflict = syncStateDao.getConflict(entityId.value)
                ?: return@withLock RepositoryResult.Failure(AppError.Conflict("sync.conflict_not_found"))

            when (resolution) {
                ConflictResolution.KEEP_REMOTE -> keepRemote(conflict)
                ConflictResolution.KEEP_LOCAL -> remoteSyncDao.resolveKeepLocal(
                    conflict = conflict,
                    operationTypeCode = localOperationType(conflict),
                    nowEpochMillis = nowEpochMillisProvider(),
                )
            }
            lastError.value = null
            RepositoryResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            RepositoryResult.Failure(AppError.Storage("sync.conflict_resolution_failed"))
        }
    }

    private suspend fun keepRemote(conflict: SyncConflictEntity) {
        val receivedAt = nowEpochMillisProvider()
        when (SyncEntityType.valueOf(conflict.entityTypeCode)) {
            SyncEntityType.PROFILE -> {
                val dto = snapshotJson.decodeFromString<ProfileDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveProfileKeepRemote(dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt)))
            }
            SyncEntityType.CATEGORY -> {
                val dto = snapshotJson.decodeFromString<CategoryDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveCategoryKeepRemote(
                    dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt), slug = dto.slug),
                )
            }
            SyncEntityType.TRANSACTION -> {
                val dto = snapshotJson.decodeFromString<TransactionDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveTransactionKeepRemote(dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt)))
            }
            else -> error("V1 conflict çözümü ${conflict.entityTypeCode} türünü desteklemiyor.")
        }
    }

    private suspend fun localOperationType(conflict: SyncConflictEntity): String = when (
        SyncEntityType.valueOf(conflict.entityTypeCode)
    ) {
        SyncEntityType.PROFILE -> "UPDATE"
        SyncEntityType.CATEGORY -> if (
            remoteSyncDao.getCategoryRow(conflict.entityId)?.sync?.deletedAtEpochMillis != null
        ) "DELETE" else "UPDATE"
        SyncEntityType.TRANSACTION -> if (
            remoteSyncDao.getTransactionRow(conflict.entityId)?.sync?.deletedAtEpochMillis != null
        ) "DELETE" else "UPDATE"
        else -> error("V1 conflict çözümü ${conflict.entityTypeCode} türünü desteklemiyor.")
    }

    private fun fail(error: AppError): RepositoryResult.Failure {
        phase.value = SyncPhase.FAILED
        lastError.value = error
        return RepositoryResult.Failure(error)
    }
}
