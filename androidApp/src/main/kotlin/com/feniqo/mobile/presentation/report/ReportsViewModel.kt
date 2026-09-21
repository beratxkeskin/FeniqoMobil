package com.feniqo.mobile.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialReport
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MultiCurrencyReportSummary
import com.feniqo.mobile.domain.model.ReportAvailability
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportSyncStatus
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.CalculateReportDateRangeUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveFilteredReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveMultiCurrencyReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportAvailabilityUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportSyncStatusUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val observeFilteredReportUseCase: ObserveFilteredReportUseCase,
    private val observeMultiCurrencyReportUseCase: ObserveMultiCurrencyReportUseCase,
    private val observeReportAvailabilityUseCase: ObserveReportAvailabilityUseCase,
    private val observeReportSyncStatusUseCase: ObserveReportSyncStatusUseCase,
    private val observeDetailedReportOverviewUseCase: com.feniqo.mobile.domain.usecase.ObserveDetailedReportOverviewUseCase,
    private val observeCashFlowUseCase: com.feniqo.mobile.domain.usecase.ObserveCashFlowUseCase,
    private val observeCategoryBreakdownUseCase: com.feniqo.mobile.domain.usecase.ObserveCategoryBreakdownUseCase,
    private val observeSpendingCalendarUseCase: com.feniqo.mobile.domain.usecase.ObserveSpendingCalendarUseCase,
    private val observeBudgetPerformanceReportUseCase: com.feniqo.mobile.domain.usecase.ObserveBudgetPerformanceReportUseCase,
    private val observeSubscriptionSummaryReportUseCase: com.feniqo.mobile.domain.usecase.ObserveSubscriptionSummaryReportUseCase,
    private val observeDebtSummaryReportUseCase: com.feniqo.mobile.domain.usecase.ObserveDebtSummaryReportUseCase,
    private val observeForecastReportUseCase: com.feniqo.mobile.domain.usecase.ObserveForecastReportUseCase,
    private val observeFinancialInsightsUseCase: com.feniqo.mobile.domain.usecase.ObserveFinancialInsightsUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val userSettingsRepository: UserSettingsRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val dateRangeCalculator: CalculateReportDateRangeUseCase,
) : ViewModel() {

    private val filterState = MutableStateFlow(ReportFilterUiState.Default)
    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ReportsScreenState> = combine(
        filterState,
        refreshTrigger,
        observeActiveWorkspaceUseCase(),
        userSettingsRepository.observeSettings(),
        observeCategoriesForHistoryLookupUseCase(),
    ) { filter, _, activeWorkspace, settings, categories ->
        ReportQueryContext(filter, activeWorkspace?.id, settings, categories.associateBy { it.id })
    }.flatMapLatest { ctx ->
        val refDate = currentDateProvider.today()
        val domainFilter = ctx.filter.toDomain()

        val availabilityFlow = observeReportAvailabilityUseCase(domainFilter, ctx.workspaceId, refDate)
        val reportFlow = observeFilteredReportUseCase(domainFilter, ctx.workspaceId, refDate)
        val multiCurrencyFlow = observeMultiCurrencyReportUseCase(domainFilter, ctx.workspaceId, refDate)
        val syncStatusFlow = observeReportSyncStatusUseCase()
        val detailedOverviewFlow = observeDetailedReportOverviewUseCase(domainFilter, ctx.workspaceId, refDate)

        combine(
            availabilityFlow,
            reportFlow,
            multiCurrencyFlow,
            syncStatusFlow,
            detailedOverviewFlow,
        ) { availability, report, multiCurrencyList, syncStatus, detailedOverview ->
            val start = ctx.filter.customStartDate
            val end = ctx.filter.customEndDate
            val customRange = if (ctx.filter.periodPreset == ReportPeriodPreset.CUSTOM && start != null && end != null) {
                ReportDateRange(start, end)
            } else {
                null
            }
            val dateRange = dateRangeCalculator(ctx.filter.periodPreset, customRange, refDate)
            val activeChips = ReportSummaryFormatter.generateActiveFilterChips(ctx.filter, dateRange)

            val mask = ctx.settings.maskAmounts
            val multiCurrencyUiModels = multiCurrencyList.map { summary ->
                MultiCurrencyReportUiModel(
                    currency = summary.currency,
                    title = ReportSummaryFormatter.getCurrencyDisplayName(summary.currency),
                    symbol = ReportSummaryFormatter.getCurrencySymbol(summary.currency),
                    incomeFormatted = MoneyFormatter.formatMasked(summary.income, mask = mask),
                    expenseFormatted = MoneyFormatter.formatMasked(summary.expense, mask = mask),
                    netFormatted = if (mask) {
                        MoneyFormatter.MASKED_TEXT
                    } else {
                        MoneyFormatter.formatDelta(summary.net, includeSign = true)
                    },
                    isNetPositive = summary.net.amountMinor >= 0,
                    transactionCount = summary.transactionCount,
                )
            }

            val monthlyTrendUi = detailedOverview.trendPoints.map { pt ->
                MonthlyTrendUiModel(
                    yearMonth = pt.yearMonth,
                    monthLabel = ReportSummaryFormatter.monthAbbreviation(pt.yearMonth.month),
                    incomeFormatted = MoneyFormatter.formatMasked(pt.income, mask = mask),
                    expenseFormatted = MoneyFormatter.formatMasked(pt.expense, mask = mask),
                    netFormatted = if (mask) {
                        MoneyFormatter.MASKED_TEXT
                    } else {
                        MoneyFormatter.formatDelta(pt.net, includeSign = true)
                    },
                    isNetPositive = pt.net.amountMinor >= 0,
                    incomeMinor = pt.income.amountMinor,
                    expenseMinor = pt.expense.amountMinor,
                )
            }

            val topCategoryUi = detailedOverview.topCategory?.let { tc ->
                TopCategoryUiModel(
                    name = tc.categoryName,
                    amountFormatted = MoneyFormatter.formatMasked(tc.amount, mask = mask),
                    transactionCount = tc.transactionCount,
                )
            }

            val financialRhythmUi = FinancialRhythmUiModel(
                busiestDayName = detailedOverview.financialRhythm.busiestDayName,
                busiestDayExpenseFormatted = MoneyFormatter.formatMasked(detailedOverview.financialRhythm.busiestDayExpense, mask = mask),
                lowestExpenseWeekLabel = "${detailedOverview.financialRhythm.lowestExpenseWeekNumber}. Hafta",
                lowestExpenseWeekExpenseFormatted = MoneyFormatter.formatMasked(detailedOverview.financialRhythm.lowestExpenseWeekExpense, mask = mask),
            )

            val totalExpenseMinor = report.expense.amountMinor
            val categoryBreakdownUi = report.spendingByCategory.map { cb ->
                val shareRatio = if (totalExpenseMinor > 0) cb.amount.amountMinor.toFloat() / totalExpenseMinor else 0f
                CategoryBreakdownUiItem(
                    categoryId = cb.categoryId,
                    name = ctx.categoriesById[cb.categoryId]?.name ?: "Silinmiş kategori",
                    amountFormatted = MoneyFormatter.formatMasked(cb.amount, mask = mask),
                    transactionCount = cb.transactionCount,
                    sharePercentageFormatted = "%${(shareRatio * 100).toInt()}",
                    shareRatio = shareRatio,
                )
            }

            val contentState: ReportsContentState = when {
                availability.isWorkspaceEmpty -> {
                    ReportsContentState.EmptyWorkspace
                }
                availability.isFilteredEmpty -> {
                    ReportsContentState.EmptyFiltered(activeFilters = activeChips)
                }
                else -> {
                    ReportsContentState.Success(
                        report = report,
                        incomeFormatted = MoneyFormatter.formatMasked(report.income, mask = mask),
                        expenseFormatted = MoneyFormatter.formatMasked(report.expense, mask = mask),
                        netFormatted = if (mask) {
                            MoneyFormatter.MASKED_TEXT
                        } else {
                            MoneyFormatter.formatDelta(report.net, includeSign = true)
                        },
                        isNetPositive = report.net.amountMinor >= 0,
                        savingsRateFormatted = MoneyFormatter.formatBasisPoints(report.savingsRate),
                        multiCurrencySummaries = multiCurrencyUiModels,
                        activeFilters = activeChips,
                        monthlyTrend = monthlyTrendUi,
                        topCategory = topCategoryUi,
                        financialRhythm = financialRhythmUi,
                        feniqoInsightText = detailedOverview.insightText,
                        categoryBreakdown = categoryBreakdownUi,
                    )
                }
            }

            val connectionState: ReportConnectionState = if (syncStatus.isOffline) {
                ReportConnectionState.Offline(pendingOperationCount = syncStatus.pendingOperationCount)
            } else {
                ReportConnectionState.Online
            }

            ReportsScreenState(
                contentState = contentState,
                connectionState = connectionState,
                filterState = ctx.filter,
            )
        }
    }.catch { error ->
        emit(
            ReportsScreenState(
                contentState = ReportsContentState.Error(error.message ?: "Beklenmeyen bir hata oluştu."),
                connectionState = ReportConnectionState.Online,
                filterState = filterState.value,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsScreenState(
            contentState = ReportsContentState.Loading,
            connectionState = ReportConnectionState.Online,
            filterState = ReportFilterUiState.Default,
        ),
    )

    fun observeCashFlow(monthCount: Int = 6) = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        val refDate = currentDateProvider.today()
        observeCashFlowUseCase(monthCount, currency, wsId, refDate)
    }

    fun observeSpendingCalendar(month: com.feniqo.mobile.domain.model.YearMonth) = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        observeSpendingCalendarUseCase(month, currency, wsId)
    }

    fun observeBudgetPerformance(month: com.feniqo.mobile.domain.model.YearMonth) = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        observeBudgetPerformanceReportUseCase(month, currency, wsId)
    }

    fun observeSubscriptionSummary() = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        observeSubscriptionSummaryReportUseCase(currency, wsId)
    }

    fun observeDebtSummary() = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        val refDate = currentDateProvider.today()
        observeDebtSummaryReportUseCase(currency, refDate)
    }

    fun observeForecastReport(lookaheadMonths: Int = 6) = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        ws?.id to filter.currency
    }.flatMapLatest { (wsId, currency) ->
        val refDate = currentDateProvider.today()
        observeForecastReportUseCase(currency, wsId, refDate, lookaheadMonths)
    }

    fun observeFinancialInsights() = combine(
        observeActiveWorkspaceUseCase(),
        filterState,
    ) { ws, filter ->
        Triple(ws?.id, filter.currency, filter)
    }.flatMapLatest { (wsId, currency, filterUi) ->
        val refDate = currentDateProvider.today()
        val start = filterUi.customStartDate
        val end = filterUi.customEndDate
        val customRange = if (filterUi.periodPreset == ReportPeriodPreset.CUSTOM && start != null && end != null) {
            ReportDateRange(start, end)
        } else null
        val dateRange = dateRangeCalculator(filterUi.periodPreset, customRange, refDate)
        observeFinancialInsightsUseCase(com.feniqo.mobile.domain.model.ReportPeriod(dateRange.startDate, dateRange.endDate), currency, wsId)
    }

    fun applyFilter(filter: ReportFilterUiState) {
        filterState.value = filter
    }

    fun removeFilterChip(chip: ActiveFilterChipUiModel) {
        filterState.update { current ->
            when (chip.type) {
                ActiveFilterType.PERIOD -> current.copy(
                    periodPreset = ReportPeriodPreset.THIS_MONTH,
                    customStartDate = null,
                    customEndDate = null,
                )
                ActiveFilterType.TYPE -> current.copy(typeFilter = ReportTypeFilter.ALL)
                ActiveFilterType.CURRENCY -> current.copy(currency = Currency.TRY)
                ActiveFilterType.CATEGORY -> current.copy(
                    selectedCategoryId = null,
                    selectedCategoryName = null,
                )
            }
        }
    }

    fun clearFilters() {
        filterState.value = ReportFilterUiState.Default
    }

    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        filterState.update {
            it.copy(
                periodPreset = ReportPeriodPreset.CUSTOM,
                customStartDate = startDate,
                customEndDate = endDate,
            )
        }
    }

    fun selectCurrency(currency: Currency) {
        filterState.update { it.copy(currency = currency) }
    }

    fun retry() {
        refreshTrigger.update { it + 1 }
    }

    fun getCurrentReferenceDate(): LocalDate = currentDateProvider.today()

    private data class ReportQueryContext(
        val filter: ReportFilterUiState,
        val workspaceId: EntityId?,
        val settings: UserSettings,
        val categoriesById: Map<EntityId, com.feniqo.mobile.domain.model.Category>,
    )
}
