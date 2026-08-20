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

    private class RecordingSyncRepository(
        private val overview: SyncOverview = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        ),
    ) : SyncRepository {
        var requestSyncCallCount = 0
        var retryFailedCallCount = 0

        override fun observeOverview(): Flow<SyncOverview> = flowOf(overview)
        override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())

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
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
