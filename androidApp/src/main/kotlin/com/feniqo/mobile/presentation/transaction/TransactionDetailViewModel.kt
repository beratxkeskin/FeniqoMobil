package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.navigation.ChildRouteIdResult
import com.feniqo.mobile.navigation.TransactionDetailRoute
import com.feniqo.mobile.navigation.parseTransactionDetailRouteId
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val item: TransactionDisplayModel? = null,
    val errorMessage: TransactionSurfaceUiMessage? = null,
    val deleteDialog: TransactionDeleteDialogState? = null,
    val isDeleteInProgress: Boolean = false,
    val userMessage: FinanceUiMessage? = null,
)

sealed interface TransactionDetailEvent {
    data object Deleted : TransactionDetailEvent
}

private data class TransactionDetailContent(
    val isLoading: Boolean,
    val item: TransactionDisplayModel?,
    val errorMessage: TransactionSurfaceUiMessage?,
)

/** Bağımsız detay rotasının Room SSOT yükleme ve silme yaşam döngüsünü yönetir. */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    observeTransactionUseCase: ObserveTransactionUseCase,
    observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    currentDateProvider: CurrentDateProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routeIdResult = parseTransactionDetailRouteId(
        rawId = runCatching { savedStateHandle.get<String>("transactionId") }.getOrNull()
            ?: runCatching {
                savedStateHandle.toRoute<TransactionDetailRoute>().transactionId
            }.getOrNull(),
    )

    private val deleteDialog = MutableStateFlow<TransactionDeleteDialogState?>(null)
    private val isDeleteInProgress = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<FinanceUiMessage?>(null)
    private val eventChannel = Channel<TransactionDetailEvent>(Channel.BUFFERED)
    private var activeDeleteJob: Job? = null

    val events: Flow<TransactionDetailEvent> = eventChannel.receiveAsFlow()

    private val contentFlow: Flow<TransactionDetailContent> = when (val result = routeIdResult) {
        ChildRouteIdResult.InvalidId -> flowOf(
            TransactionDetailContent(
                isLoading = false,
                item = null,
                errorMessage = TransactionSurfaceUiMessage.DETAIL_INVALID_LINK,
            ),
        )
        is ChildRouteIdResult.ValidId -> combine(
            observeTransactionUseCase(result.id),
            observeCategoriesForHistoryLookupUseCase(),
        ) { transaction, categories ->
            if (transaction == null) {
                TransactionDetailContent(
                    isLoading = false,
                    item = null,
                    errorMessage = TransactionSurfaceUiMessage.DETAIL_NOT_FOUND,
                )
            } else {
                val item = TransactionsDisplayModelBuilder.build(
                    transactions = listOf(transaction),
                    categoryHistory = categories,
                    today = currentDateProvider.today(),
                ).firstOrNull()?.items?.firstOrNull()
                TransactionDetailContent(
                    isLoading = false,
                    item = item,
                    errorMessage = if (item == null) TransactionSurfaceUiMessage.DETAIL_PREPARATION_FAILED else null,
                )
            }
        }.catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(
                TransactionDetailContent(
                    isLoading = false,
                    item = null,
                    errorMessage = TransactionSurfaceUiMessage.DETAIL_LOAD_FAILED,
                ),
            )
        }
    }

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        contentFlow,
        deleteDialog,
        isDeleteInProgress,
        userMessage,
    ) { content, dialog, deleting, message ->
        TransactionDetailUiState(
            isLoading = content.isLoading,
            item = content.item,
            errorMessage = content.errorMessage,
            deleteDialog = dialog,
            isDeleteInProgress = deleting,
            userMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionDetailUiState(),
    )

    fun requestDelete() {
        if (isDeleteInProgress.value) return
        val target = uiState.value.item ?: return
        deleteDialog.value = if (target.installment == null) {
            TransactionDeleteDialogState.Single(target)
        } else {
            TransactionDeleteDialogState.Installment(target)
        }
    }

    fun dismissDeleteDialog() {
        if (!isDeleteInProgress.value) deleteDialog.value = null
    }

    fun confirmSingleDelete() {
        val dialog = deleteDialog.value as? TransactionDeleteDialogState.Single ?: return
        executeDelete(dialog.target.id, scope = null)
    }

    fun confirmInstallmentDelete(scope: InstallmentDeleteScope) {
        val dialog = deleteDialog.value as? TransactionDeleteDialogState.Installment ?: return
        executeDelete(dialog.target.id, scope)
    }

    fun consumeMessage() {
        userMessage.value = null
    }

    private fun executeDelete(
        id: com.feniqo.mobile.domain.model.EntityId,
        scope: InstallmentDeleteScope?,
    ) {
        if (isDeleteInProgress.value || activeDeleteJob != null) return
        isDeleteInProgress.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val result = deleteTransactionUseCase(id, scope)) {
                    is RepositoryResult.Success -> {
                        deleteDialog.value = null
                        eventChannel.send(TransactionDetailEvent.Deleted)
                    }
                    is RepositoryResult.Failure -> {
                        deleteDialog.value = null
                        userMessage.value = result.error.toFinanceUiMessage()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                deleteDialog.value = null
                userMessage.value = FinanceUiMessage.GENERIC_ERROR
            } finally {
                isDeleteInProgress.value = false
                activeDeleteJob = null
            }
        }
        activeDeleteJob = job
        job.start()
    }
}
