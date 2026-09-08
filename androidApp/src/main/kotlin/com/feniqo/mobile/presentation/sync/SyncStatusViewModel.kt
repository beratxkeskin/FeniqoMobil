package com.feniqo.mobile.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.network.NetworkConnectivityObserver
import com.feniqo.mobile.domain.network.NetworkStatus
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncEntityType
import com.feniqo.mobile.domain.usecase.ObserveSyncConflictsUseCase
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.RequestManualSyncUseCase
import com.feniqo.mobile.domain.usecase.ResolveSyncConflictUseCase
import com.feniqo.mobile.domain.usecase.RetryFailedSyncOperationsUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
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
    private val observeSyncConflictsUseCase: ObserveSyncConflictsUseCase,
    private val resolveSyncConflictUseCase: ResolveSyncConflictUseCase,
    private val networkObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val actionInProgress = MutableStateFlow(false)
    private val hasWorkspaceConflictsState = MutableStateFlow(false)
    private val activeDialogState = MutableStateFlow<WorkspaceConflictDialogState?>(null)
    private var latestWorkspaceConflicts: List<WorkspaceConflictUiModel> = emptyList()
    private var activeSyncJob: Job? = null

    init {
        viewModelScope.launch {
            observeSyncConflictsUseCase().collect { conflicts ->
                val wsConflicts = conflicts
                    .filter { it.entityType == SyncEntityType.WORKSPACE }
                    .map { WorkspaceConflictUiModel(it.entityId.value, it.localVersion, it.remoteVersion) }
                latestWorkspaceConflicts = wsConflicts
                hasWorkspaceConflictsState.value = wsConflicts.isNotEmpty()

                // Seçili conflict çözüm sürmüyorken kaybolursa diyalogu kapat; başka conflict'e geçme
                val currentDialog = activeDialogState.value
                if (currentDialog != null && !currentDialog.isResolving) {
                    if (wsConflicts.none { it.entityId == currentDialog.conflict.entityId }) {
                        activeDialogState.value = null
                    }
                }
            }
        }
    }

    val uiState: StateFlow<SyncStatusUiState> = combine(
        observeSyncOverviewUseCase(),
        networkObserver.observeNetworkStatus(),
        actionInProgress,
        hasWorkspaceConflictsState,
        activeDialogState,
    ) { overview, networkStatus, isActionInProgress, hasWsConflicts, dialogState ->
        mapToUiState(overview, networkStatus, isActionInProgress, hasWsConflicts, dialogState)
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

    fun openConflictDialog() {
        if (activeDialogState.value != null) return
        val target = latestWorkspaceConflicts.firstOrNull() ?: return
        activeDialogState.value = WorkspaceConflictDialogState(conflict = target)
    }

    fun dismissConflictDialog() {
        if (activeDialogState.value?.isResolving == true) return
        activeDialogState.value = null
    }

    fun resolveWorkspaceConflict(resolution: ConflictResolution) {
        val currentDialog = activeDialogState.value ?: return
        if (currentDialog.isResolving) return

        // Resolve öncesi pinlenen ID'nin hâlâ Workspace conflict olduğunu doğrula
        val stillExists = latestWorkspaceConflicts.any { it.entityId == currentDialog.conflict.entityId }
        if (!stillExists) {
            activeDialogState.value = currentDialog.copy(
                isResolving = false,
                error = FinanceUiMessage.CONFLICT_NOT_FOUND,
            )
            return
        }

        activeDialogState.value = currentDialog.copy(isResolving = true, error = null)
        viewModelScope.launch {
            val result = resolveSyncConflictUseCase(
                entityId = EntityId(currentDialog.conflict.entityId),
                resolution = resolution,
            )
            when (result) {
                is RepositoryResult.Success -> {
                    activeDialogState.value = null
                }
                is RepositoryResult.Failure -> {
                    activeDialogState.value = currentDialog.copy(
                        isResolving = false,
                        error = result.error.toFinanceUiMessage(),
                    )
                }
            }
        }
    }

    private fun mapToUiState(
        overview: SyncOverview,
        networkStatus: NetworkStatus,
        isActionInProgress: Boolean,
        hasResolvableWorkspaceConflict: Boolean,
        activeConflictDialog: WorkspaceConflictDialogState?,
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
            hasResolvableWorkspaceConflict = hasResolvableWorkspaceConflict,
            activeConflictDialog = activeConflictDialog,
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
