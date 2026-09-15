package com.feniqo.mobile.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.usecase.DeleteCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.presentation.budget.nextMonth
import com.feniqo.mobile.presentation.budget.previousMonth
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

/**
 * Kategori listeleme, tür filtreleme, dönem bazlı harcama analizi ve özel kategori yönetimini sağlayan ViewModel'dir.
 * Veriyi doğrudan DAO'ya erişmeden, Room SSOT repository/use-case sınırından tüketir.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val observeTransactionsUseCase: ObserveTransactionsUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val initialMonth: YearMonth by lazy {
        val today = currentDateProvider.today()
        val monthStr = (today.month.ordinal + 1).toString().padStart(2, '0')
        YearMonth("${today.year}-$monthStr")
    }

    private val maxAllowedMonth: YearMonth by lazy { initialMonth }

    private val _selectedYearMonth = MutableStateFlow<YearMonth?>(null)
    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null) // null = Tümü, EXPENSE = Gider, INCOME = Gelir
    private val _isPeriodPickerVisible = MutableStateFlow(false)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _deleteTargetCategory = MutableStateFlow<CategoryDisplayModel?>(null)
    private val _isDeleteInProgress = MutableStateFlow(false)
    private val _generalMessage = MutableStateFlow<FinanceUiMessage?>(null)

    private var activeDeleteJob: Job? = null

    init {
        viewModelScope.launch {
            var previousWorkspaceId: EntityId? = null
            var isFirstEmission = true
            observeActiveWorkspaceUseCase().collect { workspace ->
                val currentWorkspaceId = workspace?.id
                if (!isFirstEmission && currentWorkspaceId != previousWorkspaceId) {
                    activeDeleteJob?.cancel()
                    _deleteTargetCategory.value = null
                    _isDeleteInProgress.value = false
                }
                isFirstEmission = false
                previousWorkspaceId = currentWorkspaceId
            }
        }
    }

    private data class ObservationQuery(
        val month: YearMonth,
        val typeFilter: TransactionType?,
        val currency: Currency,
        val retryCount: Long,
    )

    internal sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val summary: CategoriesSummaryUiModel,
            val items: List<CategorySpendingDisplayModel>,
            val systemCategories: List<CategoryDisplayModel>,
            val customCategories: List<CategoryDisplayModel>,
        ) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = combine(
        _selectedYearMonth,
        _selectedTypeFilter,
        _retryTrigger,
        observeActiveWorkspaceUseCase(),
    ) { selectedMonth, typeFilter, retryCount, activeWorkspace ->
        ObservationQuery(
            month = selectedMonth ?: initialMonth,
            typeFilter = typeFilter,
            currency = activeWorkspace?.currency ?: Currency.TRY,
            retryCount = retryCount,
        )
    }.flatMapLatest { query ->
        val (currentPeriod, previousPeriod) = CategoryAnalyticsCalculator.calculateReportPeriods(query.month)

        combine(
            observeCategoriesUseCase(type = query.typeFilter),
            observeTransactionsUseCase(TransactionFilter(type = query.typeFilter, period = currentPeriod)),
            observeTransactionsUseCase(TransactionFilter(type = query.typeFilter, period = previousPeriod)),
        ) { categories, currentTxs, prevTxs ->
            try {
                val (summary, items) = CategoryAnalyticsCalculator.calculate(
                    categories = categories,
                    currentTransactions = currentTxs,
                    previousTransactions = prevTxs,
                    selectedTypeFilter = query.typeFilter,
                    currency = query.currency,
                )
                val (systemList, customList) = categories.partition { it.isDefault }
                ObservationResult.Success(
                    summary = summary,
                    items = items,
                    systemCategories = systemList.map { it.toDisplayModel() },
                    customCategories = customList.map { it.toDisplayModel() },
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR)
            }
        }
            .onStart { emit(ObservationResult.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
            }
    }

    private val _extrasFlow = combine(
        _deleteTargetCategory,
        _isDeleteInProgress,
        _generalMessage,
        _isPeriodPickerVisible,
        observeActiveWorkspaceUseCase(),
    ) { deleteTarget, isDeleteInProgress, generalMessage, isPeriodPickerVisible, activeWorkspace ->
        CategoryUiExtras(
            deleteTarget = deleteTarget,
            isDeleteInProgress = isDeleteInProgress,
            generalMessage = generalMessage,
            isPeriodPickerVisible = isPeriodPickerVisible,
            workspaceName = activeWorkspace?.name,
        )
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        _selectedYearMonth,
        _selectedTypeFilter,
        observationResultFlow,
        _extrasFlow,
    ) { selectedMonth, typeFilter, observationResult, extras ->
        val resolvedMonth = selectedMonth ?: initialMonth
        val workspaceName = extras.workspaceName
        val deleteTarget = extras.deleteTarget
        val isDeleteInProgress = extras.isDeleteInProgress
        val generalMessage = extras.generalMessage
        val isPeriodPickerVisible = extras.isPeriodPickerVisible

        when (observationResult) {
            is ObservationResult.Loading -> CategoriesUiState(
                isLoading = true,
                selectedYearMonth = resolvedMonth,
                selectedTypeFilter = typeFilter,
                selectedType = typeFilter ?: TransactionType.EXPENSE,
                isPeriodPickerVisible = isPeriodPickerVisible,
                summary = CategoriesSummaryUiModel(),
                items = emptyList(),
                systemCategories = emptyList(),
                customCategories = emptyList(),
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                activeWorkspaceName = workspaceName,
                generalMessage = generalMessage,
            )
            is ObservationResult.Success -> CategoriesUiState(
                isLoading = false,
                selectedYearMonth = resolvedMonth,
                selectedTypeFilter = typeFilter,
                selectedType = typeFilter ?: TransactionType.EXPENSE,
                isPeriodPickerVisible = isPeriodPickerVisible,
                summary = observationResult.summary,
                items = observationResult.items,
                systemCategories = observationResult.systemCategories,
                customCategories = observationResult.customCategories,
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                activeWorkspaceName = workspaceName,
                generalMessage = generalMessage,
            )
            is ObservationResult.Failure -> CategoriesUiState(
                isLoading = false,
                selectedYearMonth = resolvedMonth,
                selectedTypeFilter = typeFilter,
                selectedType = typeFilter ?: TransactionType.EXPENSE,
                isPeriodPickerVisible = isPeriodPickerVisible,
                summary = CategoriesSummaryUiModel(),
                items = emptyList(),
                systemCategories = emptyList(),
                customCategories = emptyList(),
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                activeWorkspaceName = workspaceName,
                generalMessage = generalMessage ?: observationResult.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(
            isLoading = true,
            selectedYearMonth = initialMonth,
        ),
    )

    fun onPreviousMonth() {
        if (_isDeleteInProgress.value) return
        _deleteTargetCategory.value = null
        _selectedYearMonth.update { current ->
            (current ?: initialMonth).previousMonth()
        }
    }

    fun onNextMonth() {
        if (_isDeleteInProgress.value) return
        _deleteTargetCategory.value = null
        _selectedYearMonth.update { current ->
            val active = current ?: initialMonth
            val next = active.nextMonth()
            if (next.value <= maxAllowedMonth.value) next else active
        }
    }

    fun onYearMonthSelected(yearMonth: YearMonth) {
        if (_isDeleteInProgress.value) return
        if (yearMonth.value <= maxAllowedMonth.value) {
            _deleteTargetCategory.value = null
            _selectedYearMonth.value = yearMonth
            _isPeriodPickerVisible.value = false
        }
    }

    fun onPeriodPickerRequested() {
        if (_isDeleteInProgress.value) return
        _isPeriodPickerVisible.value = true
    }

    fun onPeriodPickerDismissed() {
        _isPeriodPickerVisible.value = false
    }

    fun onTypeFilterSelected(type: TransactionType?) {
        if (_isDeleteInProgress.value) return
        if (_selectedTypeFilter.value == type) return
        _deleteTargetCategory.value = null
        _selectedTypeFilter.value = type
    }

    fun onTypeSelected(type: TransactionType) {
        onTypeFilterSelected(type)
    }

    fun onDeleteClicked(category: CategoryDisplayModel) {
        if (_isDeleteInProgress.value) return
        if (category.isDefault) {
            _generalMessage.value = FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE
            return
        }
        _deleteTargetCategory.value = category
    }

    fun onDismissDeleteDialog() {
        if (_isDeleteInProgress.value) return
        _deleteTargetCategory.value = null
    }

    fun onConfirmDelete() {
        val target = _deleteTargetCategory.value ?: return
        if (target.isDefault) {
            _deleteTargetCategory.value = null
            _generalMessage.value = FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE
            return
        }
        if (_isDeleteInProgress.value || activeDeleteJob != null) return

        _isDeleteInProgress.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = deleteCategoryUseCase(target.id)
                when (result) {
                    is RepositoryResult.Success -> {
                        _deleteTargetCategory.value = null
                        _generalMessage.value = FinanceUiMessage.CATEGORY_DELETED
                    }
                    is RepositoryResult.Failure -> {
                        _generalMessage.value = result.error.toFinanceUiMessage()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _generalMessage.value = FinanceUiMessage.GENERIC_ERROR
            } finally {
                _isDeleteInProgress.value = false
                activeDeleteJob = null
            }
        }
        activeDeleteJob = job
        job.start()
    }

    fun onDismissMessage() {
        _generalMessage.value = null
    }

    fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }
}

private fun Category.toDisplayModel(): CategoryDisplayModel = CategoryDisplayModel(
    id = id,
    name = name,
    type = type,
    colorHex = color.hex,
    iconKey = icon?.key,
    isDefault = isDefault,
)

private data class CategoryUiExtras(
    val deleteTarget: CategoryDisplayModel?,
    val isDeleteInProgress: Boolean,
    val generalMessage: FinanceUiMessage?,
    val isPeriodPickerVisible: Boolean,
    val workspaceName: String?,
)
