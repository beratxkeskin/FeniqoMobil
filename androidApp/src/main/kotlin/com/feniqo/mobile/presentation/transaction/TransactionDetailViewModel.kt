package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionMerchantCorrection
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionMerchantCorrectionRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val item: TransactionDisplayModel? = null,
    val errorMessage: TransactionSurfaceUiMessage? = null,
    val deleteDialog: TransactionDeleteDialogState? = null,
    val isDeleteInProgress: Boolean = false,
    val userMessage: FinanceUiMessage? = null,
    val merchantCorrection: TransactionMerchantCorrectionUiState = TransactionMerchantCorrectionUiState(),
)

sealed interface TransactionDetailEvent {
    data object Deleted : TransactionDetailEvent
    data object MerchantCorrectionApplied : TransactionDetailEvent
}

private data class TransactionDetailContent(
    val isLoading: Boolean,
    val item: TransactionDisplayModel?,
    val errorMessage: TransactionSurfaceUiMessage?,
)

/** Bağımsız detay rotasının Room SSOT yükleme, silme ve merchant düzeltme yaşam döngüsünü yönetir. */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    observeTransactionUseCase: ObserveTransactionUseCase,
    observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    currentDateProvider: CurrentDateProvider,
    savedStateHandle: SavedStateHandle,
    private val merchantCorrectionRepository: TransactionMerchantCorrectionRepository,
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
    private val merchantCorrection = MutableStateFlow(
        TransactionMerchantCorrectionUiState(
            searchResults = MerchantSearchFilter.filter(""),
        ),
    )
    private val eventChannel = Channel<TransactionDetailEvent>(Channel.BUFFERED)
    private var activeDeleteJob: Job? = null
    private var activeCorrectionJob: Job? = null

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
        merchantCorrection,
    ) { content, dialog, deleting, message, correction ->
        TransactionDetailUiState(
            isLoading = content.isLoading,
            item = content.item,
            errorMessage = content.errorMessage,
            deleteDialog = dialog,
            isDeleteInProgress = deleting,
            userMessage = message,
            merchantCorrection = correction,
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

    // --- Merchant Correction UI Eylemleri ---

    fun openMerchantSelector() {
        val currentItem = uiState.value.item ?: return
        if (!currentItem.canCorrectMerchant) return
        val currentSelectedId = currentItem.merchant?.merchantId
        merchantCorrection.update {
            it.copy(
                isSelectorOpen = true,
                searchQuery = "",
                searchResults = MerchantSearchFilter.filter(""),
                selectedMerchantId = currentSelectedId,
                error = null,
            )
        }
    }

    fun dismissMerchantSelector() {
        if (merchantCorrection.value.isInProgress) return
        merchantCorrection.update {
            it.copy(
                isSelectorOpen = false,
                searchQuery = "",
                searchResults = MerchantSearchFilter.filter(""),
                selectedMerchantId = null,
                error = null,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        merchantCorrection.update {
            it.copy(
                searchQuery = query,
                searchResults = MerchantSearchFilter.filter(query),
            )
        }
    }

    fun selectMerchant(merchantId: EntityId) {
        merchantCorrection.update {
            it.copy(selectedMerchantId = merchantId)
        }
    }

    fun confirmAssign() {
        val targetMerchantId = merchantCorrection.value.selectedMerchantId ?: return
        executeAssign(targetMerchantId)
    }

    fun openBlockConfirmDialog() {
        val currentItem = uiState.value.item ?: return
        if (!currentItem.canCorrectMerchant) return
        merchantCorrection.update {
            it.copy(
                isBlockConfirmDialogOpen = true,
                error = null,
            )
        }
    }

    fun dismissBlockConfirmDialog() {
        if (merchantCorrection.value.isInProgress) return
        merchantCorrection.update {
            it.copy(
                isBlockConfirmDialogOpen = false,
                error = null,
            )
        }
    }

    fun confirmBlock() {
        executeBlock()
    }

    private fun executeAssign(merchantId: EntityId) {
        if (merchantCorrection.value.isInProgress || activeCorrectionJob != null) return
        val currentItem = uiState.value.item ?: return
        if (!currentItem.canCorrectMerchant) return
        val targetTransactionId = currentItem.id

        merchantCorrection.update { it.copy(isInProgress = true, error = null) }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val latest = uiState.value.item
                if (latest == null || latest.id != targetTransactionId) {
                    return@launch
                }

                val correction = TransactionMerchantCorrection.Assign(
                    transactionId = targetTransactionId,
                    merchantId = merchantId,
                )
                when (val result = merchantCorrectionRepository.correctMerchant(correction)) {
                    is RepositoryResult.Success -> {
                        merchantCorrection.update {
                            it.copy(
                                isSelectorOpen = false,
                                isBlockConfirmDialogOpen = false,
                                searchQuery = "",
                                searchResults = MerchantSearchFilter.filter(""),
                                selectedMerchantId = null,
                                error = null,
                            )
                        }
                        eventChannel.send(TransactionDetailEvent.MerchantCorrectionApplied)
                    }
                    is RepositoryResult.Failure -> {
                        val uiError = result.error.toMerchantCorrectionUiError()
                        merchantCorrection.update { it.copy(error = uiError) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantCorrection.update { it.copy(error = MerchantCorrectionUiError.GENERIC) }
            } finally {
                merchantCorrection.update { it.copy(isInProgress = false) }
                activeCorrectionJob = null
            }
        }
        activeCorrectionJob = job
        job.start()
    }

    private fun executeBlock() {
        if (merchantCorrection.value.isInProgress || activeCorrectionJob != null) return
        val currentItem = uiState.value.item ?: return
        if (!currentItem.canCorrectMerchant) return
        val targetTransactionId = currentItem.id

        merchantCorrection.update { it.copy(isInProgress = true, error = null) }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val latest = uiState.value.item
                if (latest == null || latest.id != targetTransactionId) {
                    return@launch
                }

                val correction = TransactionMerchantCorrection.Block(
                    transactionId = targetTransactionId,
                )
                when (val result = merchantCorrectionRepository.correctMerchant(correction)) {
                    is RepositoryResult.Success -> {
                        merchantCorrection.update {
                            it.copy(
                                isSelectorOpen = false,
                                isBlockConfirmDialogOpen = false,
                                searchQuery = "",
                                searchResults = MerchantSearchFilter.filter(""),
                                selectedMerchantId = null,
                                error = null,
                            )
                        }
                        eventChannel.send(TransactionDetailEvent.MerchantCorrectionApplied)
                    }
                    is RepositoryResult.Failure -> {
                        val uiError = result.error.toMerchantCorrectionUiError()
                        merchantCorrection.update { it.copy(error = uiError) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantCorrection.update { it.copy(error = MerchantCorrectionUiError.GENERIC) }
            } finally {
                merchantCorrection.update { it.copy(isInProgress = false) }
                activeCorrectionJob = null
            }
        }
        activeCorrectionJob = job
        job.start()
    }

    private fun executeDelete(
        id: EntityId,
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

/**
 * AppError'ı ViewModel'in tipli UI hata durumuna dönüştürür.
 * ViewModel Türkçe/İngilizce metin üretmez.
 */
fun AppError.toMerchantCorrectionUiError(): MerchantCorrectionUiError = when (this) {
    is AppError.Authentication -> when (code) {
        "auth_session_required", "auth_session_expired" -> MerchantCorrectionUiError.AUTH_REQUIRED
        "owner_mismatch", "transaction_owner_mismatch" -> MerchantCorrectionUiError.OWNER_MISMATCH
        else -> MerchantCorrectionUiError.GENERIC
    }
    is AppError.Validation -> when (code) {
        "workspace_restricted" -> MerchantCorrectionUiError.WORKSPACE_RESTRICTED
        "transaction_not_found" -> MerchantCorrectionUiError.TRANSACTION_NOT_FOUND
        "unknown_merchant" -> MerchantCorrectionUiError.UNKNOWN_MERCHANT
        "excluded_transaction", "invalid_description", "invalid_alias", "alias_too_long", "generic_alias_not_allowed" ->
            MerchantCorrectionUiError.NOT_ELIGIBLE
        else -> MerchantCorrectionUiError.GENERIC
    }
    else -> MerchantCorrectionUiError.GENERIC
}
