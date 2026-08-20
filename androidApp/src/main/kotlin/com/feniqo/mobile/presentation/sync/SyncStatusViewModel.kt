package com.feniqo.mobile.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.network.NetworkConnectivityObserver
import com.feniqo.mobile.domain.network.NetworkStatus
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.RequestManualSyncUseCase
import com.feniqo.mobile.domain.usecase.RetryFailedSyncOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val observeSyncOverviewUseCase: ObserveSyncOverviewUseCase,
    private val requestManualSyncUseCase: RequestManualSyncUseCase,
    private val retryFailedSyncOperationsUseCase: RetryFailedSyncOperationsUseCase,
    private val networkObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val actionInProgress = MutableStateFlow(false)
    private var activeSyncJob: Job? = null

    val uiState: StateFlow<SyncStatusUiState> = combine(
        observeSyncOverviewUseCase(),
        networkObserver.observeNetworkStatus(),
        actionInProgress,
    ) { overview, networkStatus, isActionInProgress ->
        mapToUiState(overview, networkStatus, isActionInProgress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncStatusUiState.Initial,
    )

    fun requestManualSync() {
        if (activeSyncJob?.isActive == true) return
        if (networkObserver.currentNetworkStatus() != NetworkStatus.CONNECTED) return

        activeSyncJob = viewModelScope.launch {
            actionInProgress.value = true
            try {
                requestManualSyncUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // İstisnalar güvenle yakalanır; UI state genel durum akışı üzerinden güncellenir
            } finally {
                actionInProgress.value = false
                activeSyncJob = null
            }
        }
    }

    fun retryFailedOperations() {
        if (activeSyncJob?.isActive == true) return
        if (networkObserver.currentNetworkStatus() != NetworkStatus.CONNECTED) return
        if (uiState.value.failedCount <= 0) return

        activeSyncJob = viewModelScope.launch {
            actionInProgress.value = true
            try {
                retryFailedSyncOperationsUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // İstisnalar güvenle yakalanır; UI state genel durum akışı üzerinden güncellenir
            } finally {
                actionInProgress.value = false
                activeSyncJob = null
            }
        }
    }

    private fun mapToUiState(
        overview: SyncOverview,
        networkStatus: NetworkStatus,
        isActionInProgress: Boolean,
    ): SyncStatusUiState {
        val connectionState = when (networkStatus) {
            NetworkStatus.UNKNOWN -> SyncConnectionUiState.CHECKING
            NetworkStatus.CONNECTED -> SyncConnectionUiState.ONLINE
            NetworkStatus.DISCONNECTED -> SyncConnectionUiState.OFFLINE
        }

        val isSyncing = overview.phase == SyncPhase.SYNCING || isActionInProgress
        val isOnline = connectionState == SyncConnectionUiState.ONLINE

        val canManualSync = isOnline && !isSyncing
        val canRetryFailed = isOnline && !isSyncing && overview.failedOperationCount > 0

        return SyncStatusUiState(
            connectionState = connectionState,
            isSyncing = isSyncing,
            pendingCount = overview.pendingOperationCount,
            failedCount = overview.failedOperationCount,
            conflictCount = overview.conflictCount,
            lastSuccessfulSyncAtEpochMillis = overview.lastSuccessfulSyncAt?.toEpochMilliseconds(),
            errorType = overview.lastError?.let { mapAppErrorToUiType(it) },
            canManualSync = canManualSync,
            canRetryFailed = canRetryFailed,
        )
    }

    private fun mapAppErrorToUiType(error: AppError): SyncErrorUiType = when (error) {
        is AppError.Network -> SyncErrorUiType.NETWORK
        is AppError.Authentication -> SyncErrorUiType.AUTHENTICATION
        is AppError.Conflict -> SyncErrorUiType.CONFLICT
        is AppError.Validation -> SyncErrorUiType.VALIDATION
        is AppError.Storage -> SyncErrorUiType.STORAGE
        is AppError.Unknown -> SyncErrorUiType.UNKNOWN
    }
}
