package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val observeTransactionsUseCase: ObserveTransactionsUseCase,
    private val observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(TransactionFilterUiModel())
    private val _retryTrigger = MutableStateFlow(0L)
    private val _isFilterExpanded = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<FinanceUiMessage?>(null)
    private val _deleteDialog = MutableStateFlow<TransactionDeleteDialogState?>(null)
    private val _isDeleteInProgress = MutableStateFlow(false)

    private var activeDeleteJob: Job? = null

    private data class FilterState(
        val query: String,
        val filter: TransactionFilterUiModel,
        val retryCount: Long,
    )

    private data class DialogAndMessageState(
        val isExpanded: Boolean,
        val message: FinanceUiMessage?,
        val dialog: TransactionDeleteDialogState?,
        val isDeleteInProgress: Boolean,
    )

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val items: List<DateGroupedTransactionsDisplayModel>,
            val availableCategories: List<CategoryFilterOptionUiModel>,
        ) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = combine(
        _searchQuery,
        _filter,
        _retryTrigger,
    ) { query, filter, retryCount ->
        FilterState(query, filter, retryCount)
    }.flatMapLatest { filterState ->
        val today = currentDateProvider.today()
        val domainFilter = TransactionFilter(
            type = filterState.filter.type,
            categoryId = filterState.filter.categoryId,
            paymentMethod = filterState.filter.paymentMethod,
            period = TransactionPeriodPresetMapper.toReportPeriod(
                preset = filterState.filter.periodPreset,
                today = today,
            ),
            workspaceId = filterState.filter.workspaceId,
            query = filterState.query.trim().takeIf { it.isNotBlank() },
        )

        combine(
            observeTransactionsUseCase(domainFilter),
            observeCategoriesForHistoryLookupUseCase(filterState.filter.workspaceId),
            observeCategoriesUseCase(
                type = filterState.filter.type,
                workspaceId = filterState.filter.workspaceId,
            ),
        ) { transactions, categoryHistory, activeCategories ->
            val availableOptions = activeCategories.map { cat ->
                CategoryFilterOptionUiModel(
                    id = cat.id,
                    name = cat.name,
                    type = cat.type,
                    colorHex = cat.color.hex,
                )
            }.sortedWith(compareBy({ it.name }, { it.id.value }))

            val currentSelectedCatId = filterState.filter.categoryId
            if (currentSelectedCatId != null && activeCategories.none { it.id == currentSelectedCatId }) {
                _filter.update { currentFilter ->
                    if (currentFilter.categoryId == currentSelectedCatId) {
                        currentFilter.copy(categoryId = null)
                    } else {
                        currentFilter
                    }
                }
            }

            val items = TransactionsDisplayModelBuilder.build(transactions, categoryHistory, today)
            ObservationResult.Success(
                items = items,
                availableCategories = availableOptions,
            ) as ObservationResult
        }.onStart {
            emit(ObservationResult.Loading)
        }.catch { e ->
            when (e) {
                is CancellationException -> throw e
                is Exception -> emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
                else -> throw e
            }
        }
    }

    private val dialogAndMessageFlow = combine(
        _isFilterExpanded,
        _userMessage,
        _deleteDialog,
        _isDeleteInProgress,
    ) { isExpanded, message, dialog, isDeleteInProgress ->
        DialogAndMessageState(isExpanded, message, dialog, isDeleteInProgress)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        observationResultFlow,
        _searchQuery,
        _filter,
        dialogAndMessageFlow,
    ) { observation, query, filter, dialogMsg ->
        when (observation) {
            is ObservationResult.Loading -> {
                TransactionsUiState(
                    isLoading = true,
                    groupedItems = emptyList(),
                    availableCategories = emptyList(),
                    searchQuery = query,
                    filter = filter,
                    isFilterExpanded = dialogMsg.isExpanded,
                    userMessage = dialogMsg.message,
                    deleteDialog = dialogMsg.dialog,
                    isDeleteInProgress = dialogMsg.isDeleteInProgress,
                    observationError = null,
                )
            }
            is ObservationResult.Success -> {
                TransactionsUiState(
                    isLoading = false,
                    groupedItems = observation.items,
                    availableCategories = observation.availableCategories,
                    searchQuery = query,
                    filter = filter,
                    isFilterExpanded = dialogMsg.isExpanded,
                    userMessage = dialogMsg.message,
                    deleteDialog = dialogMsg.dialog,
                    isDeleteInProgress = dialogMsg.isDeleteInProgress,
                    observationError = null,
                )
            }
            is ObservationResult.Failure -> {
                TransactionsUiState(
                    isLoading = false,
                    groupedItems = emptyList(),
                    availableCategories = emptyList(),
                    searchQuery = query,
                    filter = filter,
                    isFilterExpanded = dialogMsg.isExpanded,
                    userMessage = dialogMsg.message,
                    deleteDialog = dialogMsg.dialog,
                    isDeleteInProgress = dialogMsg.isDeleteInProgress,
                    observationError = observation.message,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(isLoading = true),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTypeFilterChanged(type: TransactionType?) {
        _filter.update { current ->
            if (current.type == type) {
                current
            } else {
                current.copy(type = type, categoryId = null)
            }
        }
    }

    fun onCategoryFilterChanged(categoryId: EntityId?) {
        _filter.update { it.copy(categoryId = categoryId) }
    }

    fun onPaymentMethodFilterChanged(paymentMethod: PaymentMethod?) {
        _filter.update { it.copy(paymentMethod = paymentMethod) }
    }

    fun onPeriodPresetChanged(preset: TransactionPeriodPreset?) {
        _filter.update { it.copy(periodPreset = preset) }
    }

    fun onWorkspaceChanged(workspaceId: EntityId?) {
        _filter.update { current ->
            if (current.workspaceId == workspaceId) {
                current
            } else {
                current.copy(workspaceId = workspaceId, categoryId = null)
            }
        }
    }

    fun openFilters() {
        _isFilterExpanded.value = true
    }

    fun dismissFilters() {
        _isFilterExpanded.value = false
    }

    fun clearFilters() {
        _filter.update { current ->
            TransactionFilterUiModel(workspaceId = current.workspaceId)
        }
        _searchQuery.value = ""
    }

    fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }

    fun onDeleteClicked(target: TransactionDisplayModel) {
        if (_isDeleteInProgress.value) return
        _deleteDialog.value = if (target.installment == null) {
            TransactionDeleteDialogState.Single(target)
        } else {
            TransactionDeleteDialogState.Installment(target)
        }
    }

    fun dismissDeleteDialog() {
        if (_isDeleteInProgress.value) return
        _deleteDialog.value = null
    }

    fun confirmSingleDelete() {
        val dialog = _deleteDialog.value as? TransactionDeleteDialogState.Single ?: return
        executeDelete(dialog.target.id, null)
    }

    fun confirmInstallmentDelete(scope: InstallmentDeleteScope) {
        val dialog = _deleteDialog.value as? TransactionDeleteDialogState.Installment ?: return
        executeDelete(dialog.target.id, scope)
    }

    fun consumeMessage() {
        _userMessage.value = null
    }

    private fun executeDelete(id: EntityId, scope: InstallmentDeleteScope?) {
        if (_isDeleteInProgress.value || activeDeleteJob != null) return

        _isDeleteInProgress.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = deleteTransactionUseCase(id, scope)
                when (result) {
                    is RepositoryResult.Success -> {
                        _deleteDialog.value = null
                        _userMessage.value = FinanceUiMessage.TRANSACTION_DELETED
                    }
                    is RepositoryResult.Failure -> {
                        _deleteDialog.value = null
                        _userMessage.value = result.error.toFinanceUiMessage()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteDialog.value = null
                _userMessage.value = FinanceUiMessage.GENERIC_ERROR
            } finally {
                _isDeleteInProgress.value = false
                activeDeleteJob = null
            }
        }
        activeDeleteJob = job
        job.start()
    }
}
