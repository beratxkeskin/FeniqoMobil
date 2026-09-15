package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.*
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionConflictViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun explicitChoiceIsForwardedOnceAndFailureAllowsRetry() = runTest {
        val gate = CompletableDeferred<RepositoryResult<Unit>>()
        var calls = 0
        var choice: ConflictResolution? = null
        val repository = object : SyncRepository {
            override fun observeOverview() = flowOf(SyncOverview(SyncPhase.IDLE, 0, 0, 0, null, null))
            override fun observeConflicts() = flowOf(emptyList<SyncConflict>())
            override suspend fun requestSync() = RepositoryResult.Success(Unit)
            override suspend fun retryFailedOperations() = RepositoryResult.Success(Unit)
            override suspend fun resolveConflict(entityId: EntityId, resolution: ConflictResolution): RepositoryResult<Unit> {
                calls++
                choice = resolution
                return gate.await()
            }
        }
        val vm = TransactionConflictViewModel(repository)
        assertEquals(0, calls)
        vm.resolve(EntityId("record"), ConflictResolution.KEEP_REMOTE)
        vm.resolve(EntityId("record"), ConflictResolution.KEEP_LOCAL)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(ConflictResolution.KEEP_REMOTE, choice)
        gate.complete(RepositoryResult.Failure(com.feniqo.mobile.domain.model.AppError.Validation("conflict")))
        advanceUntilIdle()
        assertFalse(vm.resolving.value)
        assertNotNull(vm.error.value)
    }
}
