package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncUseCasesTest {

    @Test
    fun observe_sync_overview_use_case_delegates_to_repository() = runTest {
        val expectedOverview = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 3,
            failedOperationCount = 1,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )
        val repository = RecordingSyncRepository(overview = expectedOverview)
        val useCase = ObserveSyncOverviewUseCase(repository)

        val actual = useCase().first()
        assertEquals(expectedOverview, actual)
    }

    @Test
    fun request_manual_sync_use_case_calls_repository_request_sync() = runTest {
        val repository = RecordingSyncRepository()
        val useCase = RequestManualSyncUseCase(repository)

        val result = useCase()
        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, repository.requestSyncCallCount)
    }

    @Test
    fun retry_failed_sync_operations_use_case_calls_repository_retry() = runTest {
        val repository = RecordingSyncRepository()
        val useCase = RetryFailedSyncOperationsUseCase(repository)

        val result = useCase()
        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, repository.retryFailedCallCount)
    }

    @Test
    fun observe_sync_conflicts_use_case_delegates_to_repository() = runTest {
        val expectedConflicts = listOf(
            SyncConflict(
                entityId = EntityId("ws-1"),
                entityType = com.feniqo.mobile.domain.repository.SyncEntityType.WORKSPACE,
                localVersion = 1L,
                remoteVersion = 2L,
            ),
        )
        val repository = RecordingSyncRepository(conflicts = expectedConflicts)
        val useCase = ObserveSyncConflictsUseCase(repository)

        val actual = useCase().first()
        assertEquals(expectedConflicts, actual)
    }

    @Test
    fun resolve_sync_conflict_use_case_delegates_parameters_and_forwards_success() = runTest {
        val repository = RecordingSyncRepository()
        val useCase = ResolveSyncConflictUseCase(repository)

        val result = useCase(EntityId("ws-1"), ConflictResolution.KEEP_REMOTE)
        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("ws-1"), repository.lastResolvedEntityId)
        assertEquals(ConflictResolution.KEEP_REMOTE, repository.lastResolution)
    }

    @Test
    fun resolve_sync_conflict_use_case_forwards_error_from_repository() = runTest {
        val expectedError = com.feniqo.mobile.domain.model.AppError.Conflict("sync.conflict_resolution_stale")
        val repository = RecordingSyncRepository(resolveResult = RepositoryResult.Failure(expectedError))
        val useCase = ResolveSyncConflictUseCase(repository)

        val result = useCase(EntityId("ws-1"), ConflictResolution.KEEP_LOCAL)
        assertTrue(result is RepositoryResult.Failure)
        assertEquals(expectedError, result.error)
    }

    private class RecordingSyncRepository(
        private val overview: SyncOverview = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        ),
        private val conflicts: List<SyncConflict> = emptyList(),
        private val resolveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    ) : SyncRepository {
        var requestSyncCallCount = 0
        var retryFailedCallCount = 0
        var lastResolvedEntityId: EntityId? = null
        var lastResolution: ConflictResolution? = null

        override fun observeOverview(): Flow<SyncOverview> = flowOf(overview)
        override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(conflicts)

        override suspend fun requestSync(): RepositoryResult<Unit> {
            requestSyncCallCount++
            return RepositoryResult.Success(Unit)
        }

        override suspend fun retryFailedOperations(): RepositoryResult<Unit> {
            retryFailedCallCount++
            return RepositoryResult.Success(Unit)
        }

        override suspend fun resolveConflict(
            entityId: EntityId,
            resolution: ConflictResolution,
        ): RepositoryResult<Unit> {
            lastResolvedEntityId = entityId
            lastResolution = resolution
            return resolveResult
        }
    }
}
