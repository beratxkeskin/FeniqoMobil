package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.FinancialReport
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.datetime.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class ReportsLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reports_main_screen_success_state_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val dummyReport =
            FinancialReport(
                period = ReportPeriod(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
                income = Money(50_000_00L, Currency.TRY),
                expense = Money(20_000_00L, Currency.TRY),
                net = MoneyDelta(30_000_00L, Currency.TRY),
                savingsRate = RateBasisPoints(6_000),
                spendingByCategory = emptyList(),
                transactionCount = 10,
            )

        val state =
            ReportsScreenState(
                filterState = ReportFilterUiState.Default,
                connectionState = ReportConnectionState.Online,
                contentState =
                    ReportsContentState.Success(
                        report = dummyReport,
                        income = Money(50_000_00L, Currency.TRY),
                        expense = Money(20_000_00L, Currency.TRY),
                        net = MoneyDelta(30_000_00L, Currency.TRY),
                        isNetPositive = true,
                        savingsRateBasisPoints = 6_000,
                        monthlyTrend =
                            listOf(
                                MonthlyTrendUiModel(
                                    yearMonth = YearMonth("2026-09"),
                                    income = Money(50_000_00L, Currency.TRY),
                                    expense = Money(20_000_00L, Currency.TRY),
                                    net = MoneyDelta(30_000_00L, Currency.TRY),
                                ),
                            ),
                        topCategory =
                            TopCategoryUiModel(
                                name = "Market",
                                amount = Money(15_000_00L, Currency.TRY),
                                transactionCount = 5,
                            ),
                        categoryBreakdown =
                            listOf(
                                CategoryBreakdownUiItem(
                                    categoryId = EntityId("cat-1"),
                                    name = "Market",
                                    amount = Money(15_000_00L, Currency.TRY),
                                    transactionCount = 5,
                                    shareBasisPoints = 7500,
                                ),
                            ),
                        financialRhythm =
                            FinancialRhythmUiModel(
                                busiestDay = DayOfWeek.MONDAY,
                                busiestDayExpense = Money(8_000_00L, Currency.TRY),
                                lowestExpenseWeekNumber = 2,
                                lowestExpenseWeekExpense = Money(2_000_00L, Currency.TRY),
                            ),
                        insightPayload =
                            ReportInsightPayload.ExpenseChanged(
                                changeBasisPoints = 1500,
                                difference = MoneyDelta(-3_000_00L, Currency.TRY),
                            ),
                        multiCurrencySummaries =
                            listOf(
                                MultiCurrencyReportUiModel(
                                    currency = Currency.TRY,
                                    income = Money(50_000_00L, Currency.TRY),
                                    expense = Money(20_000_00L, Currency.TRY),
                                    net = MoneyDelta(30_000_00L, Currency.TRY),
                                    transactionCount = 10,
                                    isNetPositive = true,
                                ),
                            ),
                        activeFilters = emptyList(),
                        maskAmounts = false,
                    ),
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                ReportsScreen(
                    state = state,
                    referenceDate = LocalDate(2026, 9, 17),
                    onApplyFilter = {},
                    onRemoveFilterChip = {},
                    onClearFilters = {},
                    onNavigateToCustomDateRange = {},
                    onNavigateToMultiCurrency = {},
                    onSelectCategory = {},
                    onAddTransaction = {},
                    onNavigateToHome = {},
                    onNavigateToSyncStatus = {},
                    onRetry = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR: Başlıklar, metrikler, oranlar, ritim ve içgörü
        composeRule.onNodeWithText("Raporlar").assertIsDisplayed()
        composeRule.onNodeWithText("Net sonuç").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gelir")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Gider")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Tasarruf oranı").assertIsDisplayed()
        composeRule.onNodeWithText("%60").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık trend (son 6 ay)").assertIsDisplayed()
        composeRule.onNodeWithText("Hızlı raporlar").assertIsDisplayed()
        composeRule.onNodeWithText("Kategoriler").assertIsDisplayed()
        composeRule.onNodeWithText("Nakit akışı").assertIsDisplayed()
        composeRule.onNodeWithText("En yoğun gün").assertIsDisplayed()
        composeRule.onNodeWithText("Pazartesi").assertIsDisplayed()
        composeRule.onNodeWithText("En düşük hafta").assertIsDisplayed()
        composeRule.onNodeWithText("2. Hafta").assertIsDisplayed()
        composeRule.onNodeWithText("Tüm Raporlar Merkezi").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aylık gelir ve gider trend grafiği").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Başlıklar, metrikler, oranlar, ritim ve içgörü
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
        composeRule.onNodeWithText("Net result").assertIsDisplayed()
        composeRule.onAllNodesWithText("Income")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Expense")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Savings rate").assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly trend (last 6 months)").assertIsDisplayed()
        composeRule.onNodeWithText("Quick reports").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        composeRule.onNodeWithText("Cash flow").assertIsDisplayed()
        composeRule.onNodeWithText("Busiest day").assertIsDisplayed()
        composeRule.onNodeWithText("Monday").assertIsDisplayed()
        composeRule.onNodeWithText("Lowest week").assertIsDisplayed()
        composeRule.onNodeWithText("Week 2").assertIsDisplayed()
        composeRule.onNodeWithText("All Reports Hub").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Monthly income and expense trend chart").assertIsDisplayed()
    }

    @Test
    fun reports_filter_and_period_picker_sheets_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val filterState =
            ReportFilterUiState(
                periodPreset = ReportPeriodPreset.THIS_MONTH,
                typeFilter = ReportTypeFilter.ALL,
                currency = Currency.TRY,
                selectedCategoryId = null,
                selectedCategoryName = null,
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                ReportFiltersSheetContent(
                    initialFilter = filterState,
                    referenceDate = LocalDate(2026, 9, 17),
                    onApplyFilter = {},
                    onNavigateToCustomDateRange = {},
                    onSelectCategory = {},
                    onDismiss = {},
                )
            }
        }

        // TR: Sheet başlığı, filtre bölümleri, butonlar
        composeRule.onNodeWithText("Rapor filtreleri").assertIsDisplayed()
        composeRule.onNodeWithText("Dönem").assertIsDisplayed()
        composeRule.onNodeWithText("Bu Ay").assertIsDisplayed()
        composeRule.onNodeWithText("Geçen Ay").assertIsDisplayed()
        composeRule.onNodeWithText("Son 3 Ay").assertIsDisplayed()
        composeRule.onNodeWithText("Özel tarih").assertIsDisplayed()
        composeRule.onNodeWithText("İşlem türü").assertIsDisplayed()
        composeRule.onNodeWithText("Tümü").assertIsDisplayed()
        composeRule.onNodeWithText("Gelir").assertIsDisplayed()
        composeRule.onNodeWithText("Gider").assertIsDisplayed()
        composeRule.onNodeWithText("Para birimi").assertIsDisplayed()
        composeRule.onNodeWithText("Kategoriler").assertIsDisplayed()
        composeRule.onNodeWithText("Tüm kategoriler").assertIsDisplayed()
        composeRule.onNodeWithText("Aktif filtreler").assertIsDisplayed()
        composeRule.onNodeWithText("Temizle").assertIsDisplayed()
        composeRule.onNodeWithText("Raporu uygula").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Sheet başlığı, filtre bölümleri, butonlar
        composeRule.onNodeWithText("Report filters").assertIsDisplayed()
        composeRule.onNodeWithText("Period").assertIsDisplayed()
        composeRule.onNodeWithText("This Month").assertIsDisplayed()
        composeRule.onNodeWithText("Last Month").assertIsDisplayed()
        composeRule.onNodeWithText("Last 3 Months").assertIsDisplayed()
        composeRule.onNodeWithText("Custom date").assertIsDisplayed()
        composeRule.onNodeWithText("Transaction type").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        composeRule.onNodeWithText("All categories").assertIsDisplayed()
        composeRule.onNodeWithText("Active filters").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").assertIsDisplayed()
        composeRule.onNodeWithText("Apply report").assertIsDisplayed()
    }

    @Test
    fun reports_empty_error_and_offline_states_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var currentScenario by mutableStateOf(1) // 1: Empty Workspace, 2: Empty Filtered, 3: Error, 4: Offline

        composeRule.setContent {
            App(languageTag = languageTag) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScenario) {
                        1 -> NoReportsEmptyView(onAddTransaction = {}, onNavigateToHome = {})
                        2 ->
                            NoFilteredResultsView(
                                activeFilters =
                                    listOf(
                                        ActiveFilterChipUiModel.Period(
                                            preset = ReportPeriodPreset.THIS_MONTH,
                                            dateRange = ReportDateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
                                        ),
                                    ),
                                onRemoveFilter = {},
                                onClearFilters = {},
                                onChangePeriod = {},
                            )
                        3 ->
                            ReportErrorCard(
                                errorMessage = ReportUiError.Generic.toLocalizedMessage(),
                                onRetry = {},
                            )
                        4 ->
                            OfflineSyncWarningBanner(
                                pendingOperationCount = 4,
                                onSyncClick = {},
                            )
                    }
                }
            }
        }

        // Scenario 1: Empty Workspace TR -> EN
        composeRule.onNodeWithText("Henüz rapor oluşturacak veri yok").assertIsDisplayed()
        composeRule.onNodeWithText("+ İşlem ekle").assertIsDisplayed()
        composeRule.onNodeWithText("Ana sayfaya dön").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("No data to generate reports yet").assertIsDisplayed()
        composeRule.onNodeWithText("+ Add transaction").assertIsDisplayed()
        composeRule.onNodeWithText("Return to home").assertIsDisplayed()

        // Scenario 2: Empty Filtered EN -> TR
        composeRule.runOnIdle { currentScenario = 2 }
        composeRule.onNodeWithText("No results for these filters").assertIsDisplayed()
        composeRule.onNodeWithText("Clear filters").assertIsDisplayed()
        composeRule.onNodeWithText("Change period").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Bu filtrelerde sonuç yok").assertIsDisplayed()
        composeRule.onNodeWithText("Filtreleri temizle").assertIsDisplayed()
        composeRule.onNodeWithText("Dönemi değiştir").assertIsDisplayed()

        // Scenario 3: Error TR -> EN
        composeRule.runOnIdle { currentScenario = 3 }
        composeRule.onNodeWithText("Rapor hesaplanamadı").assertIsDisplayed()
        composeRule.onNodeWithText("Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar dene.").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrar dene").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Report could not be calculated").assertIsDisplayed()
        composeRule.onNodeWithText("An unexpected error occurred. Please try again later.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()

        // Scenario 4: Offline Plural EN -> TR
        composeRule.runOnIdle { currentScenario = 4 }
        composeRule.onNodeWithText("Showing local records. 4 changes are waiting to sync.").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "tr" }
        composeRule.onNodeWithText("Yerel kayıtlarınla gösteriliyor. 4 değişiklik eşitlenmeyi bekliyor.").assertIsDisplayed()
    }

    @Test
    fun missing_category_and_multi_currency_screen_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var isMasked by mutableStateOf(false)

        composeRule.setContent {
            App(languageTag = languageTag) {
                val summaries =
                    listOf(
                        MultiCurrencyReportUiModel(
                            currency = Currency.TRY,
                            income = Money(25_000_00L, Currency.TRY),
                            expense = Money(10_000_00L, Currency.TRY),
                            net = MoneyDelta(15_000_00L, Currency.TRY),
                            transactionCount = 5,
                            isNetPositive = true,
                            maskAmounts = isMasked,
                        ),
                        MultiCurrencyReportUiModel(
                            currency = Currency.USD,
                            income = Money(1_000_00L, Currency.USD),
                            expense = Money(400_00L, Currency.USD),
                            net = MoneyDelta(600_00L, Currency.USD),
                            transactionCount = 2,
                            isNetPositive = true,
                            maskAmounts = isMasked,
                        ),
                    )
                MultiCurrencyReportsScreen(
                    summaries = summaries,
                    selectedCurrency = Currency.TRY,
                    onSelectCurrency = {},
                    onOpenFilterSheet = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR: Çoklu para birimi başlığı, bilgi metni, sütunlar
        composeRule.onNodeWithText("Çoklu para birimi").assertIsDisplayed()
        composeRule.onNodeWithText("Farklı para birimleri birleştirilmez.").assertIsDisplayed()
        composeRule.onNodeWithText("Kur dönüşümü yapılmaz. Her para birimi ayrı gösterilir.").assertIsDisplayed()
        composeRule.onNodeWithText("Para birimini filtrele").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gelir")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Gider")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Fark")[0].assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Multi-currency title, banner, difference
        composeRule.onNodeWithText("Multi-currency").assertIsDisplayed()
        composeRule.onNodeWithText("Different currencies are not merged.").assertIsDisplayed()
        composeRule.onNodeWithText("No exchange rate conversion is performed. Each currency is shown separately.").assertIsDisplayed()
        composeRule.onNodeWithText("Filter currency").assertIsDisplayed()
        composeRule.onAllNodesWithText("Income")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Expense")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Difference")[0].assertIsDisplayed()

        // Masking test
        composeRule.runOnIdle { isMasked = true }
        composeRule.onAllNodesWithText("••••")[0].assertIsDisplayed()
    }

    @Test
    fun custom_date_range_screen_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                CustomDateRangeScreen(
                    initialStartDate = LocalDate(2026, 9, 1),
                    initialEndDate = LocalDate(2026, 9, 15),
                    referenceDate = LocalDate(2026, 9, 17),
                    onApplyRange = { _, _ -> },
                    onNavigateBack = {},
                )
            }
        }

        // TR: Başlık, başlangıç/bitiş, butonlar, gün kısaltmaları
        composeRule.onNodeWithText("Özel tarih aralığı").assertIsDisplayed()
        composeRule.onNodeWithText("Başlangıç").assertIsDisplayed()
        composeRule.onNodeWithText("Bitiş").assertIsDisplayed()
        composeRule.onNodeWithText("Tarihleri uygula").assertIsDisplayed()
        composeRule.onNodeWithText("Hızlı seçim").assertIsDisplayed()
        composeRule.onNodeWithText("Bu hafta").assertIsDisplayed()
        composeRule.onNodeWithText("Bu ay").assertIsDisplayed()
        composeRule.onNodeWithText("Son 90 gün").assertIsDisplayed()
        composeRule.onNodeWithText("Pzt").assertIsDisplayed()
        composeRule.onNodeWithText("Cum").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Önceki Ay").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sonraki Ay").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Title, start/end, buttons, day abbreviations
        composeRule.onNodeWithText("Custom date range").assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onNodeWithText("End").assertIsDisplayed()
        composeRule.onNodeWithText("Apply dates").assertIsDisplayed()
        composeRule.onNodeWithText("Quick select").assertIsDisplayed()
        composeRule.onNodeWithText("This week").assertIsDisplayed()
        composeRule.onNodeWithText("This month").assertIsDisplayed()
        composeRule.onNodeWithText("Last 90 days").assertIsDisplayed()
        composeRule.onNodeWithText("Mon").assertIsDisplayed()
        composeRule.onNodeWithText("Fri").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous Month").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next Month").assertIsDisplayed()
    }

    @Test
    fun reports_screen_missing_top_category_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val dummyReport =
            FinancialReport(
                period = ReportPeriod(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
                income = Money(50_000_00L, Currency.TRY),
                expense = Money(20_000_00L, Currency.TRY),
                net = MoneyDelta(30_000_00L, Currency.TRY),
                savingsRate = RateBasisPoints(6_000),
                spendingByCategory = emptyList(),
                transactionCount = 5,
            )

        val state =
            ReportsScreenState(
                filterState = ReportFilterUiState.Default,
                connectionState = ReportConnectionState.Online,
                contentState =
                    ReportsContentState.Success(
                        report = dummyReport,
                        income = Money(50_000_00L, Currency.TRY),
                        expense = Money(20_000_00L, Currency.TRY),
                        net = MoneyDelta(30_000_00L, Currency.TRY),
                        isNetPositive = true,
                        savingsRateBasisPoints = 6_000,
                        monthlyTrend = emptyList(),
                        topCategory =
                            TopCategoryUiModel(
                                name = null,
                                amount = Money(20_000_00L, Currency.TRY),
                                transactionCount = 5,
                                isCategoryMissing = true,
                            ),
                        categoryBreakdown = emptyList(),
                        financialRhythm =
                            FinancialRhythmUiModel(
                                busiestDay = null,
                                busiestDayExpense = Money.zero(Currency.TRY),
                                lowestExpenseWeekNumber = 1,
                                lowestExpenseWeekExpense = Money.zero(Currency.TRY),
                            ),
                        insightPayload = ReportInsightPayload.None,
                        multiCurrencySummaries = emptyList(),
                        activeFilters = emptyList(),
                        maskAmounts = false,
                    ),
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                ReportsScreen(
                    state = state,
                    referenceDate = LocalDate(2026, 9, 17),
                    onApplyFilter = {},
                    onRemoveFilterChip = {},
                    onClearFilters = {},
                    onNavigateToCustomDateRange = {},
                    onNavigateToMultiCurrency = {},
                    onSelectCategory = {},
                    onAddTransaction = {},
                    onNavigateToHome = {},
                    onNavigateToSyncStatus = {},
                    onRetry = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR: Silinmiş kategori
        composeRule.onNodeWithText("Silinmiş kategori", substring = true).assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Deleted category
        composeRule.onNodeWithText("Deleted category", substring = true).assertIsDisplayed()
    }

    @Test
    fun periodSummaryScreen_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val month = YearMonth.from(2026, 10)
        val income = Money(60_000_00L, Currency.TRY)
        val expense = Money(24_000_00L, Currency.TRY)
        val net = MoneyDelta(36_000_00L, Currency.TRY)

        val topCategoryMissing = CategoryBreakdownUiItem(
            categoryId = null,
            name = null,
            isCategoryMissing = true,
            amount = Money(15_000_00L, Currency.TRY),
            transactionCount = 4,
            shareBasisPoints = 6250,
        )

        val rhythm = FinancialRhythmUiModel(
            busiestDay = DayOfWeek.MONDAY,
            busiestDayExpense = Money(8_000_00L, Currency.TRY),
            lowestExpenseWeekNumber = 2,
            lowestExpenseWeekExpense = Money(2_000_00L, Currency.TRY),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                PeriodSummaryScreen(
                    currentMonth = month,
                    income = income,
                    expense = expense,
                    net = net,
                    isNetPositive = true,
                    savingsRateBasisPoints = 6000,
                    transactionCount = 18,
                    dailyAverageExpense = Money(800_00L, Currency.TRY),
                    weeklyPoints = listOf(
                        WeeklyDualBarUiPoint(1, 15_000_00L, 10_000_00L),
                        WeeklyDualBarUiPoint(2, 20_000_00L, 5_000_00L),
                    ),
                    topCategories = listOf(topCategoryMissing),
                    financialRhythm = rhythm,
                    insightPayload = ReportInsightPayload.None,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onNavigateToCategoryBreakdown = {},
                    onNavigateToPeriodComparison = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Dönem özeti").assertIsDisplayed()
        composeRule.onNodeWithText("Ekim 2026").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gelir")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Gider")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Net")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Tasarruf oranı").assertIsDisplayed()
        composeRule.onNodeWithText("%60").assertIsDisplayed()
        composeRule.onAllNodesWithText("Silinmiş kategori")[0].assertIsDisplayed()
        composeRule.onNodeWithText("En yoğun gün").assertIsDisplayed()
        composeRule.onNodeWithText("Pazartesi").assertIsDisplayed()
        composeRule.onAllNodesWithText("2. Hafta")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Tüm kategoriler").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Period summary").assertIsDisplayed()
        composeRule.onNodeWithText("October 2026").assertIsDisplayed()
        composeRule.onAllNodesWithText("Income")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Expense")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Net")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Savings rate").assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
        composeRule.onAllNodesWithText("Deleted category")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Busiest day").assertIsDisplayed()
        composeRule.onNodeWithText("Monday").assertIsDisplayed()
        composeRule.onAllNodesWithText("Week 2")[0].assertIsDisplayed()
        composeRule.onNodeWithText("All categories").assertIsDisplayed()
    }

    @Test
    fun allReportsHubScreen_cards_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                AllReportsHubScreen(
                    onNavigateToCategoryBreakdown = {},
                    onNavigateToCashFlow = {},
                    onNavigateToPeriodComparison = {},
                    onNavigateToSpendingCalendar = {},
                    onNavigateToBudgetPerformance = {},
                    onNavigateToSubscriptionSummary = {},
                    onNavigateToDebtSummary = {},
                    onNavigateToForecast = {},
                    onNavigateToFinancialInsights = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Tüm raporlar").assertIsDisplayed()
        composeRule.onNodeWithText("Harcama analizi & Kategori dağılımı").assertIsDisplayed()
        composeRule.onNodeWithText("Nakit akışı").assertIsDisplayed()
        composeRule.onNodeWithText("Dönem karşılaştırma").assertIsDisplayed()
        composeRule.onNodeWithText("Harcama takvimi").assertIsDisplayed()
        composeRule.onNodeWithText("Bütçe performansı").assertIsDisplayed()
        composeRule.onNodeWithText("Abonelik özeti").assertIsDisplayed()
        composeRule.onNodeWithText("Borç ve alacak özeti").assertIsDisplayed()
        composeRule.onNodeWithText("Gelecek dönem tahmini").assertIsDisplayed()
        composeRule.onNodeWithText("Finansal içgörüler").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("All reports").assertIsDisplayed()
        composeRule.onNodeWithText("Spending analysis & Category breakdown").assertIsDisplayed()
        composeRule.onNodeWithText("Cash flow").assertIsDisplayed()
        composeRule.onNodeWithText("Period comparison").assertIsDisplayed()
        composeRule.onNodeWithText("Spending calendar").assertIsDisplayed()
        composeRule.onNodeWithText("Budget performance").assertIsDisplayed()
        composeRule.onNodeWithText("Subscription summary").assertIsDisplayed()
        composeRule.onNodeWithText("Debt and receivable summary").assertIsDisplayed()
        composeRule.onNodeWithText("Future period forecast").assertIsDisplayed()
        composeRule.onNodeWithText("Financial insights").assertIsDisplayed()
    }

    @Test
    fun categoryBreakdownReportScreen_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val catItem = CategoryBreakdownUiItem(
            categoryId = EntityId("cat-1"),
            name = "Market",
            amount = Money(15_000_00L, Currency.TRY),
            transactionCount = 12,
            shareBasisPoints = 7500,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryBreakdownReportScreen(
                    periodPreset = ReportPeriodPreset.THIS_MONTH,
                    totalExpense = Money(20_000_00L, Currency.TRY),
                    totalIncome = Money(50_000_00L, Currency.TRY),
                    expenseTransactionCount = 12,
                    incomeTransactionCount = 3,
                    expenseCategories = listOf(catItem),
                    incomeCategories = emptyList(),
                    onSelectCategory = { _, _ -> },
                    onChangePeriod = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Kategori dağılımı").assertIsDisplayed()
        composeRule.onNodeWithText("Bu Ay").assertIsDisplayed()
        composeRule.onNodeWithText("Gider").assertIsDisplayed()
        composeRule.onNodeWithText("Gelir").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam gider").assertIsDisplayed()
        composeRule.onAllNodesWithText("12 işlem")[0].assertIsDisplayed()
        composeRule.onNodeWithText("12 işlem • %75").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Category breakdown").assertIsDisplayed()
        composeRule.onNodeWithText("This Month").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("Total expense").assertIsDisplayed()
        composeRule.onAllNodesWithText("12 transactions")[0].assertIsDisplayed()
        composeRule.onNodeWithText("12 transactions • 75%").assertIsDisplayed()
    }

    @Test
    fun categoryDetailReportScreen_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val merchant = MerchantBreakdownUiItem(
            merchantName = "Kahve Dünyası",
            amount = Money(450_00L, Currency.TRY),
            shareBasisPoints = 1500,
            transactionCount = 3,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryDetailReportScreen(
                    categoryName = null,
                    isCategoryMissing = true,
                    periodPreset = ReportPeriodPreset.THIS_MONTH,
                    totalSpending = Money(15_000_00L, Currency.TRY),
                    transactionCount = 5,
                    periodShareBasisPoints = 2500,
                    weeklyTrend = emptyList(),
                    comparisonState = CategoryDetailComparisonUiState.Calculating,
                    merchantBreakdown = listOf(merchant),
                    transactions = emptyList(),
                    onNavigateToTransactions = {},
                    onNavigateBack = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Silinmiş kategori").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori harcama özeti").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam harcama").assertIsDisplayed()
        composeRule.onNodeWithText("5 işlem • Dönem payı %25").assertIsDisplayed()
        composeRule.onNodeWithText("Geçen aya göre veri hesaplanıyor").assertIsDisplayed()
        composeRule.onNodeWithText("İş yeri kırılımı").assertIsDisplayed()
        composeRule.onNodeWithText("3 işlem • %15").assertIsDisplayed()
        composeRule.onNodeWithText("İşlemleri gör").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Deleted category").assertIsDisplayed()
        composeRule.onNodeWithText("Category spending summary").assertIsDisplayed()
        composeRule.onNodeWithText("Total spending").assertIsDisplayed()
        composeRule.onNodeWithText("5 transactions • Period share 25%").assertIsDisplayed()
        composeRule.onNodeWithText("Calculating comparison with previous month").assertIsDisplayed()
        composeRule.onNodeWithText("Merchant breakdown").assertIsDisplayed()
        composeRule.onNodeWithText("3 transactions • 15%").assertIsDisplayed()
        composeRule.onNodeWithText("View transactions").assertIsDisplayed()
    }

    @Test
    fun cashFlowReportScreen_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var maskAmounts by mutableStateOf(false)

        val ym = YearMonth.from(2026, 9)
        val monthItem = CashFlowMonthUiItem(
            yearMonth = ym,
            income = Money(50_000_00L, Currency.TRY),
            expense = Money(20_000_00L, Currency.TRY),
            net = MoneyDelta(30_000_00L, Currency.TRY),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                CashFlowReportScreen(
                    monthlyPoints = listOf(monthItem),
                    totalIncome = Money(50_000_00L, Currency.TRY),
                    totalExpense = Money(20_000_00L, Currency.TRY),
                    netDifference = MoneyDelta(30_000_00L, Currency.TRY),
                    isNetPositive = true,
                    averageIncome = Money(50_000_00L, Currency.TRY),
                    averageExpense = Money(20_000_00L, Currency.TRY),
                    strongestMonth = ym,
                    weakestMonth = null,
                    selectedMonthCount = 6,
                    onSelectMonthRange = {},
                    onBackClick = {},
                    maskAmounts = maskAmounts,
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Nakit Akışı").assertIsDisplayed()
        composeRule.onNodeWithText("3 Ay").assertIsDisplayed()
        composeRule.onNodeWithText("6 Ay").assertIsDisplayed()
        composeRule.onNodeWithText("1 Yıl").assertIsDisplayed()
        composeRule.onNodeWithText("Dönem Nakit Akışı").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam Gelir").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam Gider").assertIsDisplayed()
        composeRule.onNodeWithText("Gelir ve Gider Dengesi").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık Ort. Gelir").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık Ort. Gider").assertIsDisplayed()
        composeRule.onNodeWithText("En Güçlü: Eylül").assertIsDisplayed()
        composeRule.onNodeWithText("En Zayıf: Veri yok").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık Dağılım").assertIsDisplayed()
        composeRule.onNodeWithText("Eylül 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Net Fazla").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Cash Flow").assertIsDisplayed()
        composeRule.onNodeWithText("3 Months").assertIsDisplayed()
        composeRule.onNodeWithText("6 Months").assertIsDisplayed()
        composeRule.onNodeWithText("1 Year").assertIsDisplayed()
        composeRule.onNodeWithText("Period Cash Flow").assertIsDisplayed()
        composeRule.onNodeWithText("Total Income").assertIsDisplayed()
        composeRule.onNodeWithText("Total Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Income and Expense Balance").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly Avg. Income").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly Avg. Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Strongest: September").assertIsDisplayed()
        composeRule.onNodeWithText("Weakest: No data").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly Distribution").assertIsDisplayed()
        composeRule.onNodeWithText("September 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Net Surplus").assertIsDisplayed()

        // Masking: maskAmounts = true
        composeRule.runOnIdle { maskAmounts = true }

        // Real amounts should not be displayed
        composeRule.onNodeWithText("50,000.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("20,000.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("30,000.00 ₺").assertDoesNotExist()

        // Masked placeholder is displayed
        composeRule.onAllNodesWithText("••••")[0].assertIsDisplayed()
    }

    @Test
    fun periodComparisonReportScreen_availableState_and_presetSheet_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var showSheet by mutableStateOf(false)

        val availableState = PeriodComparisonUiState.Available(
            preset = ComparisonPeriodPreset.THIS_VS_LAST_MONTH,
            currentNet = MoneyDelta(30_000_00L, Currency.TRY),
            previousNet = MoneyDelta(20_000_00L, Currency.TRY),
            netDifference = MoneyDelta(10_000_00L, Currency.TRY),
            isNetImproved = true,
            incomeDelta = MoneyDelta(5_000_00L, Currency.TRY),
            expenseDelta = MoneyDelta(-5_000_00L, Currency.TRY),
            savingsRateDeltaBasisPoints = 1200,
            categoryComparisons = listOf(
                CategoryComparisonUiItem(
                    categoryId = EntityId("cat-1"),
                    name = "Market",
                    isCategoryMissing = false,
                    currentAmount = Money(10_000_00L, Currency.TRY),
                    previousAmount = Money(8_000_00L, Currency.TRY),
                    delta = MoneyDelta(2_000_00L, Currency.TRY),
                    percentageBasisPoints = 2500,
                    isIncreased = true,
                ),
            ),
            maskAmounts = false,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PeriodComparisonReportScreen(
                        comparisonState = availableState,
                        onSelectPeriodClick = { showSheet = true },
                        onCategoryClick = {},
                        onBackClick = {},
                    )
                    if (showSheet) {
                        SelectComparisonPeriodSheet(
                            initialPreset = ComparisonPeriodPreset.THIS_VS_LAST_MONTH,
                            onApplyPreset = { showSheet = false },
                            onDismissRequest = { showSheet = false },
                        )
                    }
                }
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Dönem Karşılaştırma").assertIsDisplayed()
        composeRule.onNodeWithText("Değiştir").assertIsDisplayed()
        composeRule.onNodeWithText("Bu Ay  vs  Geçen Ay").assertIsDisplayed()
        composeRule.onNodeWithText("Net Fark Değişimi").assertIsDisplayed()
        composeRule.onNodeWithText("İyileşme").assertIsDisplayed()
        composeRule.onNodeWithText("Gelir Değişimi").assertIsDisplayed()
        composeRule.onNodeWithText("Gider Değişimi").assertIsDisplayed()
        composeRule.onNodeWithText("Tasarruf").assertIsDisplayed()
        composeRule.onNodeWithText("%12").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori Bazında Değişimler").assertIsDisplayed()
        composeRule.onNodeWithText("Market").assertIsDisplayed()

        // Open preset sheet
        composeRule.runOnIdle { showSheet = true }
        composeRule.onNodeWithText("Karşılaştırma Dönemi Seç").assertIsDisplayed()
        composeRule.onNodeWithText("Karşılaştırmayı Uygula").assertIsDisplayed()
        composeRule.onNodeWithText("Bu Çeyrek  vs  Geçen Çeyrek").assertIsDisplayed()
        composeRule.onNodeWithText("Bu Yıl  vs  Geçen Yıl").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions for sheet
        composeRule.onNodeWithText("Select Comparison Period").assertIsDisplayed()
        composeRule.onNodeWithText("Apply Comparison").assertIsDisplayed()
        composeRule.onNodeWithText("This Quarter  vs  Last Quarter").assertIsDisplayed()
        composeRule.onNodeWithText("This Year  vs  Last Year").assertIsDisplayed()

        // Close sheet to verify screen in EN
        composeRule.runOnIdle { showSheet = false }
        composeRule.onNodeWithText("Period Comparison").assertIsDisplayed()
        composeRule.onNodeWithText("Change").assertIsDisplayed()
        composeRule.onNodeWithText("This Month  vs  Last Month").assertIsDisplayed()
        composeRule.onNodeWithText("Net Difference Change").assertIsDisplayed()
        composeRule.onNodeWithText("Improvement").assertIsDisplayed()
        composeRule.onNodeWithText("Income Change").assertIsDisplayed()
        composeRule.onNodeWithText("Expense Change").assertIsDisplayed()
        composeRule.onNodeWithText("Savings").assertIsDisplayed()
        composeRule.onNodeWithText("12%").assertIsDisplayed()
        composeRule.onNodeWithText("Category Changes").assertIsDisplayed()
        composeRule.onNodeWithText("Market").assertIsDisplayed()
    }

    @Test
    fun periodComparisonReportScreen_unavailableState_displays_honest_localized_message() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                PeriodComparisonReportScreen(
                    comparisonState = PeriodComparisonUiState.Unavailable,
                    onSelectPeriodClick = {},
                    onCategoryClick = {},
                    onBackClick = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Dönem Karşılaştırma").assertIsDisplayed()
        composeRule.onNodeWithText("Karşılaştırma Verisi Hazır Değil").assertIsDisplayed()
        composeRule.onNodeWithText("Seçilen dönemler arasında karşılaştırma yapabilmek için henüz yeterli veri bulunmuyor.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN
        composeRule.onNodeWithText("Period Comparison").assertIsDisplayed()
        composeRule.onNodeWithText("Comparison Data Not Available").assertIsDisplayed()
        composeRule.onNodeWithText("There is not enough data available to compare the selected periods.").assertIsDisplayed()
    }

    @Test
    fun spendingCalendarReportScreen_reacts_to_runtime_locale_change_and_masks_amounts() {
        var languageTag by mutableStateOf("tr")
        var maskAmounts by mutableStateOf(false)

        val currentMonth = YearMonth("2026-09")
        val day = CalendarDayUiModel(
            date = LocalDate(2026, 9, 17),
            dayNumber = 17,
            expense = Money(225_00L, Currency.TRY),
            transactionCount = 2,
            heatLevel = 2,
            isSelected = true,
            maskAmounts = false,
        )
        val txWithCategory = ReportTransactionUiItem(
            id = EntityId("tx-1"),
            description = "Kahve",
            type = TransactionType.EXPENSE,
            categoryId = EntityId("c-cafe"),
            categoryName = "Kafe & Restoran",
            isCategoryMissing = false,
            date = LocalDate(2026, 9, 17),
            amount = Money(150_00L, Currency.TRY),
            maskAmounts = false,
        )
        val txDeletedCategoryNoDesc = ReportTransactionUiItem(
            id = EntityId("tx-2"),
            description = null,
            type = TransactionType.EXPENSE,
            categoryId = null,
            categoryName = null,
            isCategoryMissing = true,
            date = LocalDate(2026, 9, 17),
            amount = Money(75_00L, Currency.TRY),
            maskAmounts = false,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                SpendingCalendarReportScreen(
                    currentMonth = currentMonth,
                    days = listOf(day),
                    selectedDay = day,
                    dayTransactions = listOf(txWithCategory, txDeletedCategoryNoDesc),
                    maskAmounts = maskAmounts,
                    onSelectDay = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onBackClick = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Harcama Takvimi").assertIsDisplayed()
        composeRule.onNodeWithText("Eylül 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Pzt").assertIsDisplayed()
        composeRule.onNodeWithText("Paz").assertIsDisplayed()
        composeRule.onNodeWithText("17 Eylül 2026").assertIsDisplayed()
        composeRule.onNodeWithText("2 işlem").assertIsDisplayed()
        composeRule.onNodeWithText("Günün İşlemleri").assertIsDisplayed()
        // Known category and custom description
        composeRule.onNodeWithText("Kahve").assertIsDisplayed()
        composeRule.onNodeWithText("Kafe & Restoran").assertIsDisplayed()
        // Missing category and fallback localized type title
        composeRule.onNodeWithText("Silinmiş kategori").assertIsDisplayed()
        composeRule.onNodeWithText("Gider").assertIsDisplayed()
        // No raw enum strings leaked
        composeRule.onNodeWithText("EXPENSE").assertDoesNotExist()
        composeRule.onNodeWithText("INCOME").assertDoesNotExist()
        composeRule.onNodeWithText("225,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("-150,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("-75,00 ₺").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN
        composeRule.onNodeWithText("Spending Calendar").assertIsDisplayed()
        composeRule.onNodeWithText("September 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Mon").assertIsDisplayed()
        composeRule.onNodeWithText("Sun").assertIsDisplayed()
        composeRule.onNodeWithText("September 17, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("2 transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Transactions of the Day").assertIsDisplayed()
        // Known category and custom description
        composeRule.onNodeWithText("Kahve").assertIsDisplayed()
        composeRule.onNodeWithText("Kafe & Restoran").assertIsDisplayed()
        // Missing category and fallback localized type title
        composeRule.onNodeWithText("Deleted category").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        // No raw enum strings leaked
        composeRule.onNodeWithText("EXPENSE").assertDoesNotExist()
        composeRule.onNodeWithText("INCOME").assertDoesNotExist()
        composeRule.onNodeWithText("225.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("-150.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("-75.00 ₺").assertIsDisplayed()

        // Mask amounts
        composeRule.runOnIdle { maskAmounts = true }
        composeRule.onNodeWithText("225,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("225.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("-150,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("-150.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("-75,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("-75.00 ₺").assertDoesNotExist()
        composeRule.onAllNodesWithText("••••")[0].assertIsDisplayed()
    }

    @Test
    fun budgetPerformanceReportScreen_reacts_to_runtime_locale_change_and_masks_amounts() {
        var languageTag by mutableStateOf("tr")
        var maskAmounts by mutableStateOf(false)

        val item = BudgetPerformanceUiItem(
            budgetId = EntityId("b-1"),
            categoryId = null,
            name = null,
            isCategoryMissing = true,
            budget = Money(5_000_00L, Currency.TRY),
            spent = Money(6_000_00L, Currency.TRY),
            usageBasisPoints = 12000,
            isExceeded = true,
            maskAmounts = false,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                BudgetPerformanceReportScreen(
                    currentMonth = YearMonth("2026-09"),
                    totalBudget = Money(20_000_00L, Currency.TRY),
                    totalSpent = Money(16_000_00L, Currency.TRY),
                    remaining = MoneyDelta(4_000_00L, Currency.TRY),
                    usageBasisPoints = 8000,
                    isExceeded = false,
                    budgetItems = listOf(item),
                    maskAmounts = maskAmounts,
                    onBackClick = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Bütçe Performansı").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam Bütçe Kullanımı").assertIsDisplayed()
        composeRule.onNodeWithText("%80").assertIsDisplayed()
        composeRule.onNodeWithText("Harcanan").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam Limit").assertIsDisplayed()
        composeRule.onNodeWithText("Kalan Bütçe").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori Bütçeleri").assertIsDisplayed()
        composeRule.onNodeWithText("Silinmiş kategori").assertIsDisplayed()
        composeRule.onNodeWithText("%120").assertIsDisplayed()
        composeRule.onNodeWithText("Harcanan: 6.000,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Limit: 5.000,00 ₺").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN
        composeRule.onNodeWithText("Budget Performance").assertIsDisplayed()
        composeRule.onNodeWithText("Total Budget Usage").assertIsDisplayed()
        composeRule.onNodeWithText("80%").assertIsDisplayed()
        composeRule.onNodeWithText("Spent").assertIsDisplayed()
        composeRule.onNodeWithText("Total Limit").assertIsDisplayed()
        composeRule.onNodeWithText("Remaining Budget").assertIsDisplayed()
        composeRule.onNodeWithText("Category Budgets").assertIsDisplayed()
        composeRule.onNodeWithText("Deleted category").assertIsDisplayed()
        composeRule.onNodeWithText("120%").assertIsDisplayed()
        composeRule.onNodeWithText("Spent: 6,000.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Limit: 5,000.00 ₺").assertIsDisplayed()

        // Mask amounts
        composeRule.runOnIdle { maskAmounts = true }
        composeRule.onNodeWithText("Spent: 6,000.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("Harcanan: 6.000,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("Limit: 5,000.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("Limit: 5.000,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("Spent: ••••").assertIsDisplayed()
        composeRule.onNodeWithText("Limit: ••••").assertIsDisplayed()
    }

    @Test
    fun subscriptionSummaryReportScreen_reacts_to_runtime_locale_change_and_masks_amounts() {
        var languageTag by mutableStateOf("tr")
        var maskAmounts by mutableStateOf(false)
        var items by mutableStateOf(
            listOf(
                SubscriptionReportUiItem(
                    id = EntityId("sub-1"),
                    name = "Spotify",
                    amount = Money(50_00L, Currency.TRY),
                    renewalDate = LocalDate(2026, 9, 25),
                    maskAmounts = false,
                ),
            ),
        )
        var count by mutableStateOf(1)

        composeRule.setContent {
            App(languageTag = languageTag) {
                SubscriptionSummaryReportScreen(
                    monthlyTotal = Money(350_00L, Currency.TRY),
                    activeSubscriptionCount = count,
                    upcomingSubscriptions = items,
                    maskAmounts = maskAmounts,
                    onBackClick = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Abonelik Özeti").assertIsDisplayed()
        composeRule.onNodeWithText("Aylık Düzenli Abonelik Yükü").assertIsDisplayed()
        composeRule.onNodeWithText("1 Aktif Abonelik").assertIsDisplayed()
        composeRule.onNodeWithText("Yaklaşan Yenilemeler").assertIsDisplayed()
        composeRule.onNodeWithText("Spotify").assertIsDisplayed()
        composeRule.onNodeWithText("Yenilenme: 25 Eylül 2026").assertIsDisplayed()
        composeRule.onNodeWithText("350,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("50,00 ₺").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN
        composeRule.onNodeWithText("Subscription Summary").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly Regular Subscription Load").assertIsDisplayed()
        composeRule.onNodeWithText("1 Active Subscription").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming Renewals").assertIsDisplayed()
        composeRule.onNodeWithText("Spotify").assertIsDisplayed()
        composeRule.onNodeWithText("Renewal: September 25, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("350.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("50.00 ₺").assertIsDisplayed()

        // Empty state
        composeRule.runOnIdle {
            items = emptyList()
            count = 0
        }
        composeRule.onNodeWithText("0 Active Subscriptions").assertIsDisplayed()
        composeRule.onNodeWithText("No active subscriptions registered.").assertIsDisplayed()

        // Mask amounts
        composeRule.runOnIdle { maskAmounts = true }
        composeRule.onNodeWithText("350,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("50,00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("350.00 ₺").assertDoesNotExist()
        composeRule.onNodeWithText("50.00 ₺").assertDoesNotExist()
        composeRule.onAllNodesWithText("••••")[0].assertIsDisplayed()
    }
}
