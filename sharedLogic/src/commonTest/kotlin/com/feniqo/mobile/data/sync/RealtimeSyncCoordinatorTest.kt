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
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
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
    fun initial_null_session_does_not_schedule_initial_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        advanceUntilIdle()

        assertEquals(0, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun null_to_authenticated_session_schedules_initial_sync_once() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        advanceUntilIdle()
        assertEquals(0, scheduler.scheduleInitialSyncCallCount)

        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()

        assertEquals(1, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun duplicate_authenticated_session_emission_does_not_reschedule() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleInitialSyncCallCount)

        // Same user emitted again without null intervening
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()

        assertEquals(1, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun authenticated_to_null_to_same_user_schedules_initial_sync_again() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleInitialSyncCallCount)

        // Logout
        auth.session.value = null
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleInitialSyncCallCount)

        // Re-login with same user
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(2, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun authenticated_realtime_signal_requests_room_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession(USER_ID)
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
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession(USER_ID)
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
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
            sourceRestartDelayMillis = 1_000L,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        auth.session.value = testSession(USER_ID)
        advanceTimeBy(1_001L)
        advanceUntilIdle()

        assertEquals(2, source.collectionCount)
        assertEquals(1, sync.requestCount)
    }

    @Test
    fun session_switch_from_A_to_B_cancels_in_flight_A_realtime_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        val inFlightStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val letSyncFinish = kotlinx.coroutines.CompletableDeferred<Unit>()
        sync.onRequestSync = {
            inFlightStarted.complete(Unit)
            letSyncFinish.await()
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }

        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()

        source.events.emit(RealtimeInvalidation(SyncEntityType.CATEGORY))
        inFlightStarted.await()
        assertEquals(1, sync.requestCount)
        assertEquals(0, sync.cancellationCount)

        // Aktif User A senkronizasyonu sürerken oturum User B'ye geçer
        auth.session.value = testSession(USER_ID_B)
        advanceUntilIdle()

        // User A'nın askıdaki sync'i collectLatest tarafından iptal edilir
        assertEquals(1, sync.cancellationCount)
        assertEquals(USER_ID_B, source.observedUserId)
        assertEquals(2, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun late_A_realtime_signal_after_switch_does_not_request_sync_for_B() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        val flowA = source.flowFor(USER_ID)
        val flowB = source.flowFor(USER_ID_B)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }

        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(USER_ID, source.observedUserId)

        flowA.emit(RealtimeInvalidation(SyncEntityType.CATEGORY))
        advanceUntilIdle()
        assertEquals(1, sync.requestCount)

        // Oturum User B'ye geçer
        auth.session.value = testSession(USER_ID_B)
        advanceUntilIdle()
        assertEquals(USER_ID_B, source.observedUserId)

        // Eski User A akışından gecikmeli sinyal gelir
        flowA.emit(RealtimeInvalidation(SyncEntityType.TRANSACTION))
        advanceUntilIdle()

        // User B adına gereksiz/hatalı senkronizasyon tetiklenmez
        assertEquals(1, sync.requestCount)

        // User B'nin kendi akışından gelen sinyal ise senkronizasyon tetikler
        flowB.emit(RealtimeInvalidation(SyncEntityType.TRANSACTION))
        advanceUntilIdle()
        assertEquals(2, sync.requestCount)
    }

    @Test
    fun session_switch_schedules_initial_sync_once_for_each_distinct_authenticated_user() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }
        advanceUntilIdle()
        assertEquals(0, scheduler.scheduleInitialSyncCallCount)

        // User A girişi
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleInitialSyncCallCount)

        // User A aynı oturum tekrarlanırsa tekrar tetiklenmez
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(1, scheduler.scheduleInitialSyncCallCount)

        // User B'ye geçiş
        auth.session.value = testSession(USER_ID_B)
        advanceUntilIdle()
        assertEquals(2, scheduler.scheduleInitialSyncCallCount)

        // User B aynı oturum tekrarlanırsa tekrar tetiklenmez
        auth.session.value = testSession(USER_ID_B)
        advanceUntilIdle()
        assertEquals(2, scheduler.scheduleInitialSyncCallCount)

        // Tekrar User A'ya geçiş
        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()
        assertEquals(3, scheduler.scheduleInitialSyncCallCount)
    }

    @Test
    fun sign_out_cancels_realtime_collection_and_null_session_does_not_request_sync() = runTest {
        val auth = FakeAuthRepository()
        val source = FakeInvalidationSource()
        val sync = RecordingSyncRepository()
        val scheduler = FakeBackgroundSyncScheduler()
        val coordinator = RealtimeSyncCoordinator(
            authRepository = auth,
            invalidationSource = source,
            syncRepository = sync,
            syncScheduler = scheduler,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.run()
        }

        auth.session.value = testSession(USER_ID)
        advanceUntilIdle()

        source.events.emit(RealtimeInvalidation(SyncEntityType.CATEGORY))
        advanceUntilIdle()
        assertEquals(1, sync.requestCount)

        // Çıkış yapılır
        auth.session.value = null
        advanceUntilIdle()

        // Çıkış yapıldıktan sonra gelen realtime sinyalleri senkronizasyon tetiklemez
        source.events.emit(RealtimeInvalidation(SyncEntityType.TRANSACTION))
        source.events.emit(RealtimeConnectionReady)
        advanceUntilIdle()

        assertEquals(1, sync.requestCount)
    }

    private class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
        var scheduleInitialSyncCallCount = 0
        var scheduleOutboxSyncCallCount = 0
        var cancelSyncWorkCallCount = 0

        override fun scheduleInitialSync() {
            scheduleInitialSyncCallCount++
        }

        override fun scheduleOutboxSync() {
            scheduleOutboxSyncCallCount++
        }

        override fun cancelSyncWork() {
            cancelSyncWorkCallCount++
        }
    }

    private class FakeInvalidationSource : RealtimeInvalidationSource {
        val userFlows = mutableMapOf<String, MutableSharedFlow<RealtimeSignal>>()
        val events = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = 3)
        var observedUserId: String? = null

        fun flowFor(userId: String): MutableSharedFlow<RealtimeSignal> =
            userFlows.getOrPut(userId) { MutableSharedFlow(extraBufferCapacity = 3) }

        override fun observeFor(userId: EntityId): Flow<RealtimeSignal> {
            observedUserId = userId.value
            return userFlows[userId.value] ?: events
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
        var onRequestSync: (suspend () -> Unit)? = null
        var cancellationCount = 0

        override fun observeOverview(): Flow<SyncOverview> = flowOf(
            SyncOverview(SyncPhase.IDLE, 0, 0, 0, null, null),
        )
        override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())
        override suspend fun requestSync(): RepositoryResult<Unit> {
            requestCount++
            val handler = onRequestSync
            if (handler != null) {
                try {
                    handler()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancellationCount++
                    throw e
                }
            }
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
        const val USER_ID_B = "20000000-0000-0000-0000-000000000002"

        fun testSession(userId: String = USER_ID) = AuthSession(
            userId = EntityId(userId),
            email = "realtime@feniqo.test",
            expiresAt = Instant.parse("2026-08-15T12:00:00Z"),
        )
    }
}
