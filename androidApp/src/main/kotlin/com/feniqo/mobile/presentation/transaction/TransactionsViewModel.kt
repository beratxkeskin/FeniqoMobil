package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val currentDateProvider: CurrentDateProvider,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val initialFilter: TransactionFilterUiModel = run {
        val catId = savedStateHandle.get<String>("categoryId")?.takeIf { it.isNotBlank() }?.let { EntityId(it) }
        val startStr = savedStateHandle.get<String>("startDate")?.takeIf { it.isNotBlank() }
        val endStr = savedStateHandle.get<String>("endDate")?.takeIf { it.isNotBlank() }
        val customPeriod = if (startStr != null && endStr != null) {
            try {
                ReportPeriod(LocalDate.parse(startStr), LocalDate.parse(endStr))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        TransactionFilterUiModel(
            categoryId = catId,
            customPeriod = customPeriod,
        )
    }

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(initialFilter)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _isFilterExpanded = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<FinanceUiMessage?>(null)
    private val _deleteDialog = MutableStateFlow<TransactionDeleteDialogState?>(null)
    private val _isDeleteInProgress = MutableStateFlow(false)

    private var activeDeleteJob: Job? = null

    init {
        viewModelScope.launch {
            var previousWorkspaceId: EntityId? = null
            var isFirstEmission = true
            observeActiveWorkspaceUseCase().collect { workspace ->
                val currentWorkspaceId = workspace?.id
                if (!isFirstEmission && currentWorkspaceId != previousWorkspaceId) {
                    _filter.update { it.copy(categoryId = null) }
                    activeDeleteJob?.cancel()
                    _deleteDialog.value = null
                    _isDeleteInProgress.value = false
                }
                isFirstEmission = false
                previousWorkspaceId = currentWorkspaceId
            }
        }
    }

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
            val summary: TransactionSummaryUiModel,
        ) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    private data class CategoryQueryKey(
        val type: TransactionType?,
        val workspaceId: EntityId?,
    )

    private sealed interface CategorySnapshot {
        data object Loading : CategorySnapshot
        data class Loaded(
            val activeCategories: List<Category>,
            val categoryHistory: List<Category>,
        ) : CategorySnapshot
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categorySnapshotFlow: Flow<CategorySnapshot> = _filter
        .map { CategoryQueryKey(it.type, it.workspaceId) }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            combine(
                observeCategoriesUseCase(type = key.type, workspaceId = key.workspaceId),
                observeCategoriesForHistoryLookupUseCase(workspaceId = key.workspaceId),
            ) { activeCategories, categoryHistory ->
                CategorySnapshot.Loaded(
                    activeCategories = activeCategories,
                    categoryHistory = categoryHistory,
                ) as CategorySnapshot
            }.onStart {
                emit(CategorySnapshot.Loading)
            }
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
            period = filterState.filter.customPeriod ?: TransactionPeriodPresetMapper.toReportPeriod(
                preset = filterState.filter.periodPreset,
                today = today,
            ),
            workspaceId = filterState.filter.workspaceId,
            query = filterState.query.trim().takeIf { it.isNotBlank() },
        )

        combine(
            observeTransactionsUseCase(domainFilter),
            categorySnapshotFlow,
        ) { transactions, categorySnapshot ->
            when (categorySnapshot) {
                is CategorySnapshot.Loading -> {
                    ObservationResult.Loading
                }
                is CategorySnapshot.Loaded -> {
                    val activeCategories = categorySnapshot.activeCategories
                    val categoryHistory = categorySnapshot.categoryHistory

                    val availableOptions = activeCategories.map { cat ->
                        CategoryFilterOptionUiModel(
                            id = cat.id,
                            name = cat.name,
                            type = cat.type,
                            colorHex = cat.color.hex,
                        )
                    }.sortedWith(compareBy({ it.name }, { it.id.value }))

                    val currentSelectedCatId = filterState.filter.categoryId
                    val isKnownCategory = activeCategories.any { it.id == currentSelectedCatId } ||
                        categoryHistory.any { it.id == currentSelectedCatId }

                    if (currentSelectedCatId != null && !isKnownCategory) {
                        _filter.update { currentFilter ->
                            if (currentFilter.categoryId == currentSelectedCatId) {
                                currentFilter.copy(categoryId = null)
                            } else {
                                currentFilter
                            }
                        }
                    }

                    var spendingMinor = 0L
                    var incomeMinor = 0L
                    for (trx in transactions) {
                        if (trx.amount.currency != Currency.TRY) {
                            throw IllegalArgumentException("Transactions summary only supports TRY currency in V1")
                        }
                        when (trx.type) {
                            TransactionType.EXPENSE -> spendingMinor = safeAdd(spendingMinor, trx.amount.amountMinor)
                            TransactionType.INCOME -> incomeMinor = safeAdd(incomeMinor, trx.amount.amountMinor)
                        }
                    }
                    val defaultCurrency = Currency.TRY
                    val spendingMoney = Money(spendingMinor, defaultCurrency)
                    val incomeMoney = Money(incomeMinor, defaultCurrency)

                    val netMinor = incomeMinor - spendingMinor
                    val isNetPositive = netMinor >= 0L
                    val absNetMinor = if (netMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(netMinor)
                    val netMoney = Money(absNetMinor, defaultCurrency)
                    val netBaseFormatted = com.feniqo.mobile.presentation.util.MoneyFormatter.format(netMoney, includeSign = false)
                    val netFormatted = when {
                        netMinor < 0L -> "-$netBaseFormatted"
                        netMinor > 0L -> "+$netBaseFormatted"
                        else -> netBaseFormatted
                    }

                    val period = domainFilter.period
                    val dateRangeText = if (period != null) {
                        com.feniqo.mobile.presentation.util.DateFormatter.formatDateRange(period.startDate, period.endDate)
                    } else {
                        com.feniqo.mobile.presentation.util.DateFormatter.formatReadableDate(today)
                    }

                    val periodTitle = when {
                        filterState.filter.customPeriod != null -> "Özel Dönem"
                        filterState.filter.periodPreset == TransactionPeriodPreset.THIS_MONTH -> "Bu Ay"
                        filterState.filter.periodPreset == TransactionPeriodPreset.THIS_WEEK -> "Bu Hafta"
                        filterState.filter.periodPreset == TransactionPeriodPreset.TODAY -> "Bugün"
                        filterState.filter.periodPreset == TransactionPeriodPreset.LAST_30_DAYS -> "Son 30 Gün"
                        filterState.filter.periodPreset == TransactionPeriodPreset.THIS_YEAR -> "Bu Yıl"
                        else -> "Tüm Zamanlar"
                    }

                    // Günlük mini bar grafiği hesaplaması (filtre kapsamındaki işlemlerden)
                    val dailyMap = transactions.groupBy { it.transactionDate }
                    val sortedDates = dailyMap.keys.sorted()
                    var maxDailyActivityMinor = 0L
                    for ((_, dayTrxs) in dailyMap) {
                        var dayActivity = 0L
                        for (t in dayTrxs) {
                            dayActivity = safeAdd(dayActivity, t.amount.amountMinor)
                        }
                        if (dayActivity > maxDailyActivityMinor) {
                            maxDailyActivityMinor = dayActivity
                        }
                    }

                    val dailyBars = sortedDates.map { date ->
                        val dayTrxs = dailyMap[date].orEmpty()
                        var dayExpense = 0L
                        var dayIncome = 0L
                        for (t in dayTrxs) {
                            when (t.type) {
                                TransactionType.EXPENSE -> dayExpense = safeAdd(dayExpense, t.amount.amountMinor)
                                TransactionType.INCOME -> dayIncome = safeAdd(dayIncome, t.amount.amountMinor)
                            }
                        }
                        val dayTotal = safeAdd(dayExpense, dayIncome)
                        val heightRatioBps = TransactionRatioCalculator.calculateBasisPoints(
                            numerator = dayTotal,
                            denominator = maxDailyActivityMinor,
                        )
                        DailyTransactionBarUiModel(
                            date = date,
                            dayLabel = "${date.day}",
                            expenseMinor = dayExpense,
                            incomeMinor = dayIncome,
                            heightRatioBps = heightRatioBps,
                            isDominantIncome = dayIncome > dayExpense,
                        )
                    }

                    val summary = TransactionSummaryUiModel(
                        totalSpendingFormatted = com.feniqo.mobile.presentation.util.MoneyFormatter.format(spendingMoney, includeSign = false),
                        totalIncomeFormatted = com.feniqo.mobile.presentation.util.MoneyFormatter.format(incomeMoney, includeSign = false),
                        netFormatted = netFormatted,
                        isNetPositive = isNetPositive,
                        transactionCount = transactions.size,
                        dateRangeText = dateRangeText,
                        periodTitle = periodTitle,
                        dailyBars = dailyBars,
                    )

                    val items = TransactionsDisplayModelBuilder.build(
                        transactions = transactions,
                        categoryHistory = categoryHistory,
                        today = today,
                        sortOrder = filterState.filter.sortOrder,
                    )
                    ObservationResult.Success(
                        items = items,
                        availableCategories = availableOptions,
                        summary = summary,
                    ) as ObservationResult
                }
            }
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
        observeActiveWorkspaceUseCase(),
    ) { observation, query, filter, dialogMsg, activeWorkspace ->
        val workspaceName = activeWorkspace?.name
        when (observation) {
            is ObservationResult.Loading -> {
                TransactionsUiState(
                    isLoading = true,
                    groupedItems = emptyList(),
                    availableCategories = emptyList(),
                    summary = TransactionSummaryUiModel(),
                    searchQuery = query,
                    filter = filter,
                    isFilterExpanded = dialogMsg.isExpanded,
                    activeWorkspaceName = workspaceName,
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
                    summary = observation.summary,
                    searchQuery = query,
                    filter = filter,
                    isFilterExpanded = dialogMsg.isExpanded,
                    activeWorkspaceName = workspaceName,
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
                    activeWorkspaceName = workspaceName,
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
        _filter.update { it.copy(periodPreset = preset, customPeriod = null) }
    }

    fun onSortOrderChanged(sortOrder: TransactionSortOrder) {
        _filter.update { it.copy(sortOrder = sortOrder) }
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
            TransactionFilterUiModel(
                workspaceId = current.workspaceId,
                sortOrder = TransactionSortOrder.NEWEST,
                customPeriod = null,
                periodPreset = null,
            )
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

    private fun safeAdd(a: Long, b: Long): Long {
        require(a >= 0L && b >= 0L) { "Amounts must be non-negative" }
        val sum = a + b
        if (sum < 0L || ((a xor sum) and (b xor sum)) < 0L) {
            throw ArithmeticException("Long overflow in amount calculation: $a + $b")
        }
        return sum
    }
}
