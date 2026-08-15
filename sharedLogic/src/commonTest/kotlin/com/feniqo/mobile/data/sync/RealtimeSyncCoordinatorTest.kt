package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.remote.realtime.RealtimeInvalidation
import com.feniqo.mobile.data.remote.realtime.RealtimeInvalidationSource
import com.feniqo.mobile.data.remote.realtime.RealtimeConnectionReady
import com.feniqo.mobile.data.remote.realtime.RealtimeSignal
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncEntityType
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSyncCoordinatorTest {

    @Test
    fun authenticated_realtime_signal_requests_room_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val coordinator = RealtimeSyncCoordinator(auth, source, sync)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession()
        advanceUntilIdle()

        source.events.emit(RealtimeInvalidation(SyncEntityType.CATEGORY))
        advanceUntilIdle()

        assertEquals(USER_ID, source.observedUserId)
        assertEquals(1, sync.requestCount)
    }

    @Test
    fun successful_reconnection_requests_catch_up_room_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val coordinator = RealtimeSyncCoordinator(auth, source, sync)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession()
        advanceUntilIdle()

        source.events.emit(RealtimeConnectionReady)
        source.events.emit(RealtimeInvalidation(SyncEntityType.TRANSACTION))
        source.events.emit(RealtimeConnectionReady)
        advanceUntilIdle()

        assertEquals(3, sync.requestCount)
    }

    @Test
    fun unexpected_source_failure_restarts_subscription() = runTest {
        val auth = FakeAuthRepository()
        val source = FailsOnceInvalidationSource()
        val sync = RecordingSyncRepository()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            sourceRestartDelayMillis = 1_000L,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession()
        advanceTimeBy(1_001L)
        advanceUntilIdle()

        assertEquals(2, source.collectionCount)
        assertEquals(1, sync.requestCount)
    }

    private class FakeInvalidationSource : RealtimeInvalidationSource {
        val events = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = 3)
        var observedUserId: String? = null

        override fun observeFor(userId: EntityId): Flow<RealtimeSignal> {
            observedUserId = userId.value
            return events
        }
    }

    private class FailsOnceInvalidationSource : RealtimeInvalidationSource {
        var collectionCount = 0

        override fun observeFor(userId: EntityId): Flow<RealtimeSignal> = flow {
            collectionCount++
            if (collectionCount == 1) error("Test bağlantısı kesildi")
            emit(RealtimeConnectionReady)
        }
    }

    private class FakeAuthRepository : AuthRepository {
        val session = MutableStateFlow<AuthSession?>(null)

        override fun observeSession(): Flow<AuthSession?> = session
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("Kapsam dışı")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("Kapsam dışı")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("Kapsam dışı")
        override suspend fun signOut(): RepositoryResult<Unit> = error("Kapsam dışı")
    }

    private class RecordingSyncRepository : SyncRepository {
        var requestCount = 0

        override fun observeOverview(): Flow<SyncOverview> = flowOf(
            SyncOverview(SyncPhase.IDLE, 0, 0, null, null),
        )
        override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())
        override suspend fun requestSync(): RepositoryResult<Unit> {
            requestCount++
            return RepositoryResult.Success(Unit)
        }
        override suspend fun retryFailedOperations(): RepositoryResult<Unit> = error("Kapsam dışı")
        override suspend fun resolveConflict(
            entityId: EntityId,
            resolution: ConflictResolution,
        ): RepositoryResult<Unit> = error("Kapsam dışı")
    }

    private companion object {
        const val USER_ID = "20000000-0000-0000-0000-000000000001"

        fun testSession() = AuthSession(
            userId = EntityId(USER_ID),
            email = "realtime@feniqo.test",
            expiresAt = Instant.parse("2026-08-15T12:00:00Z"),
        )
    }
}
