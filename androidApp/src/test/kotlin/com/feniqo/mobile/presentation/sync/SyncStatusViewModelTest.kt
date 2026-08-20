package com.feniqo.mobile.presentation.sync

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.network.NetworkConnectivityObserver
import com.feniqo.mobile.domain.network.NetworkStatus
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.RequestManualSyncUseCase
import com.feniqo.mobile.domain.usecase.RetryFailedSyncOperationsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeSyncRepository: FakeSyncRepository
    private lateinit var fakeNetworkObserver: FakeNetworkConnectivityObserver
    private lateinit var viewModel: SyncStatusViewModel

    private class FakeNetworkConnectivityObserver : NetworkConnectivityObserver {
        val statusState = MutableStateFlow(NetworkStatus.UNKNOWN)

        override fun observeNetworkStatus(): Flow<NetworkStatus> = statusState
        override fun currentNetworkStatus(): NetworkStatus = statusState.value
    }

    private class FakeSyncRepository : SyncRepository {
        val overviewState = MutableStateFlow(
            SyncOverview(
                phase = SyncPhase.IDLE,
                pendingOperationCount = 0,
                failedOperationCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAt = null,
                lastError = null,
            ),
        )
        var requestSyncCallCount = 0
        var retryFailedCallCount = 0
        var actionDelayMillis: Long = 0
        var actionShouldThrow: Exception? = null

        override fun observeOverview(): Flow<SyncOverview> = overviewState
        override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())

        override suspend fun requestSync(): RepositoryResult<Unit> {
            requestSyncCallCount++
            if (actionDelayMillis > 0) delay(actionDelayMillis)
            actionShouldThrow?.let { throw it }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun retryFailedOperations(): RepositoryResult<Unit> {
            retryFailedCallCount++
            if (actionDelayMillis > 0) delay(actionDelayMillis)
            actionShouldThrow?.let { throw it }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun resolveConflict(
            entityId: EntityId,
            resolution: ConflictResolution,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    @Before
    fun setUp() {
        fakeSyncRepository = FakeSyncRepository()
        fakeNetworkObserver = FakeNetworkConnectivityObserver()

        viewModel = SyncStatusViewModel(
            observeSyncOverviewUseCase = ObserveSyncOverviewUseCase(fakeSyncRepository),
            requestManualSyncUseCase = RequestManualSyncUseCase(fakeSyncRepository),
            retryFailedSyncOperationsUseCase = RetryFailedSyncOperationsUseCase(fakeSyncRepository),
            networkObserver = fakeNetworkObserver,
        )
    }

    private fun TestScope.subscribeState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun test1_initialStateIsCheckingAndActionsDisabled() {
        // 1. İlk state CHECKING ve aksiyonlar kapalı.
        val state = viewModel.uiState.value
        assertEquals(SyncConnectionUiState.CHECKING, state.connectionState)
        assertFalse(state.isSyncing)
        assertEquals(0, state.pendingCount)
        assertEquals(0, state.failedCount)
        assertEquals(0, state.conflictCount)
        assertNull(state.lastSuccessfulSyncAtEpochMillis)
        assertNull(state.errorType)
        assertFalse(state.canManualSync)
        assertFalse(state.canRetryFailed)
    }

    @Test
    fun test2_networkConnectedMapsToOnline() = runTest {
        // 2. CONNECTED -> ONLINE.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED
        assertEquals(SyncConnectionUiState.ONLINE, viewModel.uiState.value.connectionState)
    }

    @Test
    fun test3_networkDisconnectedMapsToOfflineAndActionsDisabled() = runTest {
        // 3. DISCONNECTED -> OFFLINE ve aksiyonlar kapalı.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.DISCONNECTED
        val state = viewModel.uiState.value

        assertEquals(SyncConnectionUiState.OFFLINE, state.connectionState)
        assertFalse(state.canManualSync)
        assertFalse(state.canRetryFailed)
    }

    @Test
    fun test4_networkUnknownMapsToCheckingAndActionsDisabled() = runTest {
        // 4. UNKNOWN -> CHECKING; çevrimdışı gibi gösterilmez ama aksiyonlar kapalıdır.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.UNKNOWN
        val state = viewModel.uiState.value

        assertEquals(SyncConnectionUiState.CHECKING, state.connectionState)
        assertFalse(state.canManualSync)
        assertFalse(state.canRetryFailed)
    }

    @Test
    fun test5_countsAndLastSyncTimeMappedCorrectly() = runTest {
        // 5. Pending, failed, conflict ve son-sync zamanı doğru eşlenir.
        subscribeState()
        val syncTime = Instant.fromEpochMilliseconds(1700000000000L)
        fakeSyncRepository.overviewState.value = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 5,
            failedOperationCount = 2,
            conflictCount = 1,
            lastSuccessfulSyncAt = syncTime,
            lastError = null,
        )

        val state = viewModel.uiState.value
        assertEquals(5, state.pendingCount)
        assertEquals(2, state.failedCount)
        assertEquals(1, state.conflictCount)
        assertEquals(1700000000000L, state.lastSuccessfulSyncAtEpochMillis)
    }

    @Test
    fun test6_appErrorTypesMappedToCorrectSyncErrorUiTypes() = runTest {
        // 6. Her AppError türü doğru SyncErrorUiType değerine dönüşür.
        subscribeState()
        val errorsWithExpectedTypes = listOf(
            AppError.Network("net_err") to SyncErrorUiType.NETWORK,
            AppError.Authentication("auth_err") to SyncErrorUiType.AUTHENTICATION,
            AppError.Conflict("conflict_err") to SyncErrorUiType.CONFLICT,
            AppError.Validation("valid_err") to SyncErrorUiType.VALIDATION,
            AppError.Storage("storage_err") to SyncErrorUiType.STORAGE,
            AppError.Unknown("unknown_err") to SyncErrorUiType.UNKNOWN,
        )

        for ((appError, expectedUiType) in errorsWithExpectedTypes) {
            fakeSyncRepository.overviewState.value = SyncOverview(
                phase = SyncPhase.FAILED,
                pendingOperationCount = 0,
                failedOperationCount = 1,
                conflictCount = 0,
                lastSuccessfulSyncAt = null,
                lastError = appError,
            )
            assertEquals(expectedUiType, viewModel.uiState.value.errorType)
        }
    }

    @Test
    fun test7_onlineAndIdleAllowsManualSync() = runTest {
        // 7. ONLINE ve idle durumda manuel sync açıktır.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED
        fakeSyncRepository.overviewState.value = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )

        assertTrue(viewModel.uiState.value.canManualSync)
    }

    @Test
    fun test8_retryAllowedOnlyWhenFailedCountGreaterThanZero() = runTest {
        // 8. failedCount > 0 olduğunda retry açılır; sıfırken kapalıdır.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED

        fakeSyncRepository.overviewState.value = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )
        assertFalse(viewModel.uiState.value.canRetryFailed)

        fakeSyncRepository.overviewState.value = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 3,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )
        assertTrue(viewModel.uiState.value.canRetryFailed)
    }

    @Test
    fun test9_manualSyncIgnoresSecondCallWhileRunning() = runTest {
        // 9. Manuel sync sürerken ikinci hızlı çağrı repository’ye ulaşmaz.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED
        fakeSyncRepository.actionDelayMillis = 100L

        viewModel.requestManualSync()
        viewModel.requestManualSync()

        advanceUntilIdle()

        assertEquals(1, fakeSyncRepository.requestSyncCallCount)
    }

    @Test
    fun test10_retryOperationsIgnoresSecondCallWhileRunning() = runTest {
        // 10. Retry sürerken ikinci hızlı çağrı repository’ye ulaşmaz.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED
        fakeSyncRepository.overviewState.value = SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            failedOperationCount = 2,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        )
        fakeSyncRepository.actionDelayMillis = 100L

        viewModel.retryFailedOperations()
        viewModel.retryFailedOperations()

        advanceUntilIdle()

        assertEquals(1, fakeSyncRepository.retryFailedCallCount)
    }

    @Test
    fun test11_actionsDoNotCallRepositoryWhenOfflineOrUnknown() = runTest {
        // 11. Offline/unknown durumda iki aksiyon da repository’yi çağırmaz.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.DISCONNECTED
        viewModel.requestManualSync()
        viewModel.retryFailedOperations()

        fakeNetworkObserver.statusState.value = NetworkStatus.UNKNOWN
        viewModel.requestManualSync()
        viewModel.retryFailedOperations()

        assertEquals(0, fakeSyncRepository.requestSyncCallCount)
        assertEquals(0, fakeSyncRepository.retryFailedCallCount)
    }

    @Test
    fun test12_actionInProgressClearedOnSuccessFailureOrException() = runTest {
        // 12. İşlem başarı, failure veya exception ile bittiğinde actionInProgress temizlenir ve state tekrar kullanılabilir olur.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED

        // Senaryo A: Başarı
        fakeSyncRepository.actionDelayMillis = 50L
        viewModel.requestManualSync()
        assertTrue(viewModel.uiState.value.isSyncing)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.canManualSync)

        // Senaryo B: Exception
        fakeSyncRepository.actionDelayMillis = 0L
        fakeSyncRepository.actionShouldThrow = RuntimeException("Ağ koptu")
        viewModel.requestManualSync()

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.canManualSync)
    }

    @Test
    fun test13_cancellationExceptionRethrownAndStateReset() = runTest {
        // CancellationException fırlatıldığında actionInProgress temizlenir ve yeni aksiyon başlatılabilir.
        subscribeState()
        fakeNetworkObserver.statusState.value = NetworkStatus.CONNECTED

        fakeSyncRepository.actionDelayMillis = 0L
        fakeSyncRepository.actionShouldThrow = kotlinx.coroutines.CancellationException("Job cancelled")

        viewModel.requestManualSync()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.canManualSync)

        // Cancellation sonrası yeni bir başarılı istek başlatılabilir olmalı
        fakeSyncRepository.actionShouldThrow = null
        viewModel.requestManualSync()
        advanceUntilIdle()

        assertEquals(2, fakeSyncRepository.requestSyncCallCount)
    }
}
