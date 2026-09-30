package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.sync.ConflictRecoveryService
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.sync.WorkspaceIncrementalRemoteSync
import com.feniqo.mobile.data.sync.WorkspaceInitialRemoteSync
import com.feniqo.mobile.data.sync.WorkspaceSyncCursorKeys
import com.feniqo.mobile.data.sync.SyncSessionInvalidatedException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.coroutines.coroutineContext

import com.feniqo.mobile.data.local.dao.WorkspaceConflictStaleResolutionException
import com.feniqo.mobile.data.local.dao.WorkspaceResolutionPrecondition
import com.feniqo.mobile.data.local.dao.toSnapshot
import com.feniqo.mobile.data.local.dao.validateWorkspaceChain
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.data.local.entity.SyncUserStateEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** UI'a yalnız Room/outbox tabanlı gözlem sunan ortak senkronizasyon repository'si. */
class OfflineFirstSyncRepository(
    private val authRepository: AuthRepository,
    private val initialRemoteSync: InitialRemoteSync,
    private val workspaceInitialRemoteSync: WorkspaceInitialRemoteSync,
    private val outboxProcessor: OutboxProcessor,
    private val incrementalRemoteSync: IncrementalRemoteSync,
    private val workspaceIncrementalRemoteSync: WorkspaceIncrementalRemoteSync,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val syncStateDao: SyncStateDao,
    private val remoteSyncDao: RemoteSyncDao,
    private val conflictRecoveryService: ConflictRecoveryService,
    private val nowEpochMillisProvider: () -> Long,
) : SyncRepository {
    private val mutex = Mutex()
    private val phase = MutableStateFlow(SyncPhase.IDLE)
    private val lastError = MutableStateFlow<AppError?>(null)
    private val snapshotJson = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOverview(): Flow<SyncOverview> {
        val sessionFlow = authRepository.observeSession()
        val counts = sessionFlow.flatMapLatest { session ->
            if (session == null) {
                combine(
                    phase,
                    flowOf(0),
                    flowOf(0),
                    flowOf(0),
                ) { currentPhase, pendingCount, failedCount, conflictCount ->
                    OverviewCounts(currentPhase, pendingCount, failedCount, conflictCount)
                }
            } else {
                val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue
                combine(
                    phase,
                    offlineWriteQueue.observePendingCount(syncScopeKey),
                    offlineWriteQueue.observeFailedCount(syncScopeKey),
                    syncStateDao.observeConflictCount(syncScopeKey),
                ) { currentPhase, pendingCount, failedCount, conflictCount ->
                    OverviewCounts(currentPhase, pendingCount, failedCount, conflictCount)
                }
            }
        }
        val userSyncTime = sessionFlow.flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                syncStateDao.observeLastSuccessfulSyncAt(session.userId.value).map { millis ->
                    millis?.let { Instant.fromEpochMilliseconds(it) }
                }
            }
        }
        val quarantineStatus = sessionFlow.flatMapLatest { session ->
            if (session == null) {
                flowOf(false)
            } else {
                combine(
                    offlineWriteQueue.observeLegacyQuarantineOperationCount(),
                    syncStateDao.observeLegacyQuarantineCursorCount(),
                    syncStateDao.observeLegacyQuarantineConflictCount(),
                ) { opCount, cursorCount, conflictCount ->
                    opCount > 0 || cursorCount > 0 || conflictCount > 0
                }
            }
        }
        val outcome = combine(userSyncTime, lastError, quarantineStatus) { successfulAt, error, hasQuarantine ->
            Triple(successfulAt, error, hasQuarantine)
        }
        return combine(counts, outcome) { countsData, (successfulAt, error, hasQuarantine) ->
            SyncOverview(
                phase = countsData.phase,
                pendingOperationCount = countsData.pendingCount,
                failedOperationCount = countsData.failedCount,
                conflictCount = countsData.conflictCount,
                lastSuccessfulSyncAt = successfulAt,
                lastError = error,
                hasLegacyQuarantinedData = hasQuarantine,
            )
        }
    }

    override fun observeConflicts(): Flow<List<SyncConflict>> = authRepository.observeSession().flatMapLatest { session ->
        if (session == null) {
            flowOf(emptyList())
        } else {
            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue
            syncStateDao.observeConflicts(syncScopeKey).map { conflicts ->
                conflicts.map { conflict ->
                    SyncConflict(
                        entityId = EntityId(conflict.entityId),
                        entityType = SyncEntityType.valueOf(conflict.entityTypeCode),
                        localVersion = conflict.localVersion,
                        remoteVersion = conflict.remoteVersion,
                        localTransaction = decodeTransactionConflictSnapshot(conflict.entityTypeCode, conflict.localPayloadJson),
                        remoteTransaction = decodeTransactionConflictSnapshot(conflict.entityTypeCode, conflict.remotePayloadJson),
                        remoteDeleted = if (conflict.entityTypeCode == SyncEntityType.TRANSACTION.name) {
                            runCatching { snapshotJson.decodeFromString<TransactionDto>(conflict.remotePayloadJson).deletedAt != null }.getOrDefault(false)
                        } else false,
                    )
                }
            }
        }
    }

    override suspend fun requestSync(): RepositoryResult<Unit> = mutex.withLock {
        val snapshot = resolveSessionSnapshot()
            ?: return fail(AppError.Authentication("sync.session_required"))
        runSyncLocked(snapshot, retryFailed = false)
    }

    override suspend fun retryFailedOperations(): RepositoryResult<Unit> = mutex.withLock {
        val snapshot = resolveSessionSnapshot()
            ?: return fail(AppError.Authentication("sync.session_required"))
        runSyncLocked(snapshot, retryFailed = true)
    }

    private suspend fun resolveSessionSnapshot(): SyncSessionSnapshot? {
        val session = authRepository.observeSession().first() ?: return null
        val syncScopeKey = SyncScopeKey.forUser(session.userId.value)
        SyncScopeKey.requireUserScope(syncScopeKey)
        return SyncSessionSnapshot(
            userId = session.userId,
            syncScopeKey = syncScopeKey,
        )
    }

    private suspend fun runSyncLocked(
        snapshot: SyncSessionSnapshot,
        retryFailed: Boolean,
    ): RepositoryResult<Unit> {
        phase.value = SyncPhase.SYNCING
        lastError.value = null

        val checkActiveSession: suspend () -> Unit = {
            coroutineContext.ensureActive()
            val currentSession = authRepository.observeSession().first()
            if (currentSession == null || currentSession.userId != snapshot.userId) {
                throw SyncSessionInvalidatedException(AppError.Authentication("sync.session_invalidated"))
            }
        }

        return try {
            coroutineScope {
                val invalidationDeferred = async {
                    authRepository.observeSession()
                        .filter { currentSession -> currentSession == null || currentSession.userId != snapshot.userId }
                        .first()
                    AppError.Authentication("sync.session_invalidated")
                }

                val workflowDeferred = async {
                    checkActiveSession()
                    executeSyncWorkflow(snapshot, retryFailed, checkActiveSession)
                }

                try {
                    select<RepositoryResult<Unit>> {
                        workflowDeferred.onAwait { workflowResult ->
                            invalidationDeferred.cancel()
                            workflowResult
                        }
                        invalidationDeferred.onAwait { authError ->
                            workflowDeferred.cancel(SyncSessionInvalidatedException(authError))
                            runCatching { workflowDeferred.await() }
                            fail(authError)
                        }
                    }
                } finally {
                    invalidationDeferred.cancel()
                    if (workflowDeferred.isActive) {
                        workflowDeferred.cancel()
                    }
                }
            }
        } catch (sessionCancelled: SyncSessionInvalidatedException) {
            fail(sessionCancelled.authError)
        } catch (cancelled: CancellationException) {
            val authError = (cancelled as? SyncSessionInvalidatedException)?.authError
                ?: (cancelled.cause as? SyncSessionInvalidatedException)?.authError
            if (authError != null) {
                fail(authError)
            } else {
                phase.value = SyncPhase.IDLE
                throw cancelled
            }
        } catch (error: Throwable) {
            fail(error.toSyncAppError())
        }
    }

    private suspend fun executeSyncWorkflow(
        snapshot: SyncSessionSnapshot,
        retryFailed: Boolean,
        checkActiveSession: suspend () -> Unit,
    ): RepositoryResult<Unit> {
        val syncScopeKey = snapshot.syncScopeKey

        checkActiveSession()
        val profileCursor = syncStateDao.getCursor(syncScopeKey, SyncEntityType.PROFILE.name)
        val isUserChanged = profileCursor != null && profileCursor.entityId != snapshot.userId.value
        if (isUserChanged) {
            return fail(AppError.Authentication("sync.local_data_owner_mismatch"))
        }

        if (retryFailed) {
            checkActiveSession()
            offlineWriteQueue.retryAllFailed(syncScopeKey)
        }

        checkActiveSession()
        if (syncStateDao.getCursor(syncScopeKey, SyncEntityType.PROFILE.name) == null) {
            initialRemoteSync.pullFor(snapshot.userId, syncScopeKey)
        }

        checkActiveSession()
        val bootstrapMarker = syncStateDao.getCursor(syncScopeKey, WorkspaceSyncCursorKeys.WORKSPACE_BOOTSTRAP_COMPLETE)
        if (bootstrapMarker == null) {
            workspaceInitialRemoteSync.pull(syncScopeKey)
        } else {
            check(WorkspaceSyncCursorKeys.isBootstrapCompleteMarker(bootstrapMarker)) {
                "Geçersiz WORKSPACE_BOOTSTRAP_COMPLETE cursor marker formatı: $bootstrapMarker"
            }
        }

        checkActiveSession()
        // Outbox işleminden önce mevcut eşdeğer çakışmaları otomatik kurtar
        conflictRecoveryService.recoverAllPendingConflicts(syncScopeKey)

        checkActiveSession()
        val outboxResult = outboxProcessor.processReadyOperations(
            syncScopeKey = syncScopeKey,
            assertSessionCurrent = checkActiveSession,
        )
        if (outboxResult.failedOperationId != null) {
            val mappedError = outboxResult.lastError?.toSyncAppError() ?: AppError.Network("sync.push_failed")
            return fail(mappedError)
        }

        checkActiveSession()
        incrementalRemoteSync.pullFor(snapshot.userId, syncScopeKey)

        checkActiveSession()
        workspaceIncrementalRemoteSync.pull(syncScopeKey)

        checkActiveSession()
        val liveWorkspaceIds = remoteSyncDao.getAllKnownLiveWorkspaceIds().sorted()
        for (workspaceId in liveWorkspaceIds) {
            checkActiveSession()
            incrementalRemoteSync.pullWorkspaceFinance(EntityId(workspaceId), syncScopeKey)
        }

        checkActiveSession()
        val remainingConflicts = syncStateDao.getConflictCount(syncScopeKey)
        val conflictDetected = outboxResult.conflictOperationId != null || remainingConflicts > 0

        phase.value = SyncPhase.IDLE
        return if (conflictDetected) {
            val error = AppError.Conflict("sync.user_resolution_required")
            lastError.value = error
            RepositoryResult.Failure(error)
        } else {
            val now = nowEpochMillisProvider()
            syncStateDao.upsertUserState(
                SyncUserStateEntity(
                    userId = snapshot.userId.value,
                    lastSuccessfulSyncAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            RepositoryResult.Success(Unit)
        }
    }

    override suspend fun resolveConflict(
        entityId: EntityId,
        resolution: ConflictResolution,
    ): RepositoryResult<Unit> = mutex.withLock {
        try {
            val session = authRepository.observeSession().first()
                ?: return@withLock RepositoryResult.Failure(AppError.Authentication("sync.session_required"))
            val activeSyncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

            val conflict = syncStateDao.getConflict(activeSyncScopeKey, entityId.value)
                ?: return@withLock RepositoryResult.Failure(AppError.Conflict("sync.conflict_scope_mismatch"))

            if (conflict.syncScopeKey != activeSyncScopeKey) {
                return@withLock RepositoryResult.Failure(AppError.Conflict("sync.conflict_scope_mismatch"))
            }

            val resolutionError = when (resolution) {
                ConflictResolution.KEEP_REMOTE -> keepRemote(conflict, activeSyncScopeKey)
                ConflictResolution.KEEP_LOCAL -> keepLocal(conflict, activeSyncScopeKey)
            }
            if (resolutionError != null) {
                return@withLock RepositoryResult.Failure(resolutionError)
            }
            lastError.value = null
            RepositoryResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (stale: WorkspaceConflictStaleResolutionException) {
            RepositoryResult.Failure(AppError.Conflict("sync.conflict_resolution_stale"))
        } catch (_: Throwable) {
            RepositoryResult.Failure(AppError.Storage("sync.conflict_resolution_failed"))
        }
    }

    private suspend fun keepRemote(conflict: SyncConflictEntity, activeSyncScopeKey: String): AppError? {
        val receivedAt = nowEpochMillisProvider()
        when (SyncEntityType.valueOf(conflict.entityTypeCode)) {
            SyncEntityType.WORKSPACE -> {
                val dto = snapshotJson.decodeFromString<WorkspaceDto>(conflict.remotePayloadJson)
                require(dto.id == conflict.entityId) {
                    "Uzak snapshot kimliği (${dto.id}) çakışan varlık kimliği (${conflict.entityId}) ile uyuşmuyor."
                }
                val localWs = remoteSyncDao.getWorkspaceRow(conflict.entityId)
                    ?: return AppError.Conflict("sync.conflict_resolution_stale")
                val operations = remoteSyncDao.getAllWorkspaceOperations(activeSyncScopeKey, conflict.entityId)
                val opSnapshots = operations.map { it.toSnapshot() }
                val tail = runCatching { validateWorkspaceChain(opSnapshots) }.getOrNull()
                    ?: return AppError.Conflict("sync.conflict_resolution_stale")
                if (tail.operationId != conflict.operationId) {
                    return AppError.Conflict("sync.conflict_resolution_stale")
                }
                val precondition = WorkspaceResolutionPrecondition(
                    expectedConflict = conflict.toSnapshot(),
                    expectedWorkspace = localWs.toSnapshot(),
                    expectedOperations = opSnapshots,
                )
                remoteSyncDao.resolveWorkspaceKeepRemote(
                    syncScopeKey = activeSyncScopeKey,
                    precondition = precondition,
                    remoteEntity = dto.toEntity(receivedAt),
                )
            }
            SyncEntityType.PROFILE -> {
                val dto = snapshotJson.decodeFromString<ProfileDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveProfileKeepRemote(activeSyncScopeKey, dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt)))
            }
            SyncEntityType.CATEGORY -> {
                val dto = snapshotJson.decodeFromString<CategoryDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveCategoryKeepRemote(
                    activeSyncScopeKey,
                    dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt), slug = dto.slug),
                )
            }
            SyncEntityType.TRANSACTION -> {
                val dto = snapshotJson.decodeFromString<TransactionDto>(conflict.remotePayloadJson)
                remoteSyncDao.resolveTransactionKeepRemote(activeSyncScopeKey, dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt)))
            }
            SyncEntityType.RECURRING_TRANSACTION -> {
                val dto = snapshotJson.decodeFromString<RecurringTransactionDto>(conflict.remotePayloadJson)
                require(dto.id == conflict.entityId) {
                    "Uzak snapshot kimliği (${dto.id}) çakışan varlık kimliği (${conflict.entityId}) ile uyuşmuyor."
                }
                remoteSyncDao.resolveRecurringTransactionKeepRemote(activeSyncScopeKey, dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt)))
            }
            else -> error("V1/V2 conflict çözümü ${conflict.entityTypeCode} türünü desteklemiyor.")
        }
        return null
    }

    private suspend fun keepLocal(conflict: SyncConflictEntity, activeSyncScopeKey: String): AppError? {
        val now = nowEpochMillisProvider()
        return when (SyncEntityType.valueOf(conflict.entityTypeCode)) {
            SyncEntityType.WORKSPACE -> keepLocalWorkspace(conflict, activeSyncScopeKey, now)
            else -> {
                remoteSyncDao.resolveKeepLocal(
                    syncScopeKey = activeSyncScopeKey,
                    conflict = conflict,
                    operationTypeCode = localOperationType(conflict),
                    nowEpochMillis = now,
                )
                null
            }
        }
    }

    private suspend fun keepLocalWorkspace(
        conflict: SyncConflictEntity,
        activeSyncScopeKey: String,
        nowEpochMillis: Long,
    ): AppError? {
        val workspaceId = conflict.entityId
        val localWs = remoteSyncDao.getWorkspaceRow(workspaceId)
            ?: return AppError.Conflict("sync.conflict_resolution_stale")
        val operations = remoteSyncDao.getAllWorkspaceOperations(activeSyncScopeKey, workspaceId)
        val opSnapshots = operations.map { it.toSnapshot() }
        val tail = runCatching { validateWorkspaceChain(opSnapshots) }.getOrNull()
            ?: return AppError.Conflict("sync.conflict_resolution_stale")
        if (tail.operationId != conflict.operationId) {
            return AppError.Conflict("sync.conflict_resolution_stale")
        }

        val targetOperationType: String
        val targetPayloadJson: String?

        when (tail.operationTypeCode) {
            "CREATE" -> {
                val remoteDto = runCatching {
                    snapshotJson.decodeFromString<WorkspaceDto>(conflict.remotePayloadJson)
                }.getOrNull() ?: return AppError.Conflict("sync.conflict_resolution_stale")

                require(remoteDto.id == workspaceId) {
                    "Uzak snapshot kimliği (${remoteDto.id}) çakışan varlık kimliği ($workspaceId) ile uyuşmuyor."
                }

                if (remoteDto.deletedAt != null) {
                    return AppError.Conflict("sync.workspace_create_conflict_remote_tombstone")
                }

                val session = authRepository.observeSession().first()
                val actorId = session?.userId?.value
                if (actorId == null || remoteDto.ownerId != actorId || localWs.ownerId != actorId) {
                    return AppError.Conflict("sync.workspace_create_conflict_owner_mismatch")
                }

                val updateEntity = localWs.copy(
                    sync = localWs.sync.copy(
                        syncStatus = SyncStatus.PENDING_UPDATE.name,
                        baseVersion = conflict.remoteVersion,
                        deletedAtEpochMillis = null,
                    ),
                )
                targetOperationType = "UPDATE"
                targetPayloadJson = WorkspacePayloadCodec.encode(
                    entity = updateEntity,
                    operationType = OutboxOperationType.UPDATE,
                )
            }
            "UPDATE" -> {
                targetOperationType = "UPDATE"
                targetPayloadJson = tail.payloadJson
            }
            "DELETE" -> {
                targetOperationType = "DELETE"
                targetPayloadJson = tail.payloadJson
            }
            else -> return AppError.Conflict("sync.conflict_resolution_stale")
        }

        val precondition = WorkspaceResolutionPrecondition(
            expectedConflict = conflict.toSnapshot(),
            expectedWorkspace = localWs.toSnapshot(),
            expectedOperations = opSnapshots,
        )

        remoteSyncDao.resolveWorkspaceKeepLocal(
            syncScopeKey = activeSyncScopeKey,
            precondition = precondition,
            retainedOperationId = tail.operationId,
            targetOperationTypeCode = targetOperationType,
            targetPayloadJson = targetPayloadJson,
            nowEpochMillis = nowEpochMillis,
        )
        return null
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
        SyncEntityType.RECURRING_TRANSACTION -> if (
            remoteSyncDao.getRecurringTransactionRow(conflict.entityId)?.sync?.deletedAtEpochMillis != null
        ) "DELETE" else "UPDATE"
        else -> error("V1 conflict çözümü ${conflict.entityTypeCode} türünü desteklemiyor.")
    }

    private fun fail(error: AppError): RepositoryResult.Failure {
        phase.value = if (error is AppError.Authentication) SyncPhase.IDLE else SyncPhase.FAILED
        lastError.value = error
        return RepositoryResult.Failure(error)
    }
}

internal fun Throwable.toSyncAppError(): AppError = when (this) {
    is io.github.jan.supabase.auth.exception.AuthRestException -> when (errorCode) {
        io.github.jan.supabase.auth.exception.AuthErrorCode.WeakPassword,
        io.github.jan.supabase.auth.exception.AuthErrorCode.EmailAddressInvalid,
        io.github.jan.supabase.auth.exception.AuthErrorCode.ValidationFailed -> AppError.Validation("sync.invalid_request")
        io.github.jan.supabase.auth.exception.AuthErrorCode.Conflict,
        io.github.jan.supabase.auth.exception.AuthErrorCode.EmailExists,
        io.github.jan.supabase.auth.exception.AuthErrorCode.UserAlreadyExists -> AppError.Conflict("sync.remote_conflict")
        io.github.jan.supabase.auth.exception.AuthErrorCode.OverRequestRateLimit,
        io.github.jan.supabase.auth.exception.AuthErrorCode.OverEmailSendRateLimit,
        io.github.jan.supabase.auth.exception.AuthErrorCode.RequestTimeout -> AppError.Network("sync.rate_limited")
        else -> AppError.Authentication("sync.auth_failed")
    }
    is io.github.jan.supabase.exceptions.HttpRequestException -> AppError.Network("sync.network_unavailable")
    is io.github.jan.supabase.exceptions.RestException -> {
        val statusCode = try { response.status.value } catch (_: Throwable) { 0 }
        when (statusCode) {
            401, 403 -> AppError.Authentication("sync.unauthorized")
            400, 422 -> AppError.Validation("sync.invalid_request")
            409 -> AppError.Conflict("sync.remote_conflict")
            404, 408, 429, in 500..599 -> AppError.Network("sync.http_error_$statusCode")
            else -> AppError.Unknown("sync.http_error_$statusCode")
        }
    }
    is io.ktor.client.plugins.HttpRequestTimeoutException,
    is io.ktor.client.network.sockets.SocketTimeoutException,
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.util.network.UnresolvedAddressException,
    is io.ktor.utils.io.errors.IOException,
    is kotlinx.io.IOException -> AppError.Network("sync.network_failed")
    is androidx.sqlite.SQLiteException -> AppError.Storage("sync.storage_failed")
    is kotlinx.serialization.SerializationException -> AppError.Validation("sync.serialization_failed")
    is IllegalArgumentException -> AppError.Validation("sync.invalid_argument")
    is IllegalStateException -> AppError.Validation("sync.invalid_state")
    else -> AppError.Unknown("sync.unknown_error")
}

private data class OverviewCounts(
    val phase: SyncPhase,
    val pendingCount: Int,
    val failedCount: Int,
    val conflictCount: Int,
)

private data class SyncSessionSnapshot(
    val userId: EntityId,
    val syncScopeKey: String,
)
