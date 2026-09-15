package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.BudgetAlertBanner
import com.feniqo.mobile.presentation.component.CurrencyScopeNoticeCard
import com.feniqo.mobile.presentation.component.DashboardHeader
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.GraphiteSummaryCard
import com.feniqo.mobile.presentation.component.HomeBudgetsSection
import com.feniqo.mobile.presentation.component.HomeInsightCard
import com.feniqo.mobile.presentation.component.HomeMoneyScoreSection
import com.feniqo.mobile.presentation.component.HomeRecentTransactionsSection
import com.feniqo.mobile.presentation.component.HomeSavingsGoalSection
import com.feniqo.mobile.presentation.component.HomeUpcomingPaymentsSection
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SavingsRateSection
import com.feniqo.mobile.presentation.dashboard.DashboardBudgetProgressItem
import com.feniqo.mobile.presentation.dashboard.DashboardDisplayModel
import com.feniqo.mobile.presentation.dashboard.DashboardInsightModel
import com.feniqo.mobile.presentation.dashboard.DashboardSavingsGoalItem
import com.feniqo.mobile.presentation.dashboard.DashboardUiState
import com.feniqo.mobile.presentation.dashboard.DashboardUpcomingBillItem
import com.feniqo.mobile.presentation.dashboard.MoneyScoreDisplayModel
import com.feniqo.mobile.presentation.dashboard.MonthlySummaryDisplayModel
import com.feniqo.mobile.presentation.dashboard.NetBalanceStatus
import com.feniqo.mobile.presentation.dashboard.TopExpenseCategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel

/**
 * Feniqo Ana Sayfa (Dashboard) ekranı.
 *
 * Sorumluluklar:
 * 1. Tamamen stateless Compose fonksiyonudur.
 * 2. Onaylı tasarım sunumuna birebir uyar (sıcak kırık beyaz zemin, beyaz kartlar, grafit özet kartı).
 * 3. İçeriği tek telefon ekranına sıkıştırmaz; doğal dikey kaydırma (LazyColumn) kullanır.
 * 4. Son işlemlerde giderleri eksi işaretli okunaklı kırmızı, gelirleri yeşil renklendirir.
 * 5. Bütçe, abonelik, hedef, profil ve işlemler bağlantılarını gerçek rotalara iletir.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (EntityId) -> Unit,
    onProfileClick: () -> Unit = {},
    onBudgetsClick: () -> Unit = {},
    onSubscriptionsClick: () -> Unit = {},
    onGoalClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> {
                    LoadingContent(
                        modifier = Modifier.fillMaxSize(),
                        message = "Finansal özet yükleniyor…",
                    )
                }

                state.observationError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ErrorState(
                            title = "Finansal Özet Alınamadı",
                            description = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                            modifier = Modifier.padding(FeniqoSpacing.Large),
                        )
                    }
                }

                state.dashboard != null -> {
                    DashboardContent(
                        dashboard = state.dashboard,
                        activeWorkspaceName = state.activeWorkspaceName,
                        onAddTransaction = onAddTransaction,
                        onViewAllTransactions = onViewAllTransactions,
                        onTransactionClick = onTransactionClick,
                        onProfileClick = onProfileClick,
                        onBudgetsClick = onBudgetsClick,
                        onSubscriptionsClick = onSubscriptionsClick,
                        onGoalClick = onGoalClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: DashboardDisplayModel,
    activeWorkspaceName: String?,
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (EntityId) -> Unit,
    onProfileClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onSubscriptionsClick: () -> Unit,
    onGoalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = FeniqoSpacing.Large,
            end = FeniqoSpacing.Large,
            top = FeniqoSpacing.Medium,
            bottom = FeniqoSpacing.Screen,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1 & 2. Feniqo logosu, profil avatarı, karşılama, "Ayına bir bakış" ve aktif çalışma alanı
        item(key = "home_header") {
            DashboardHeader(
                formattedMonth = dashboard.formattedMonth,
                userName = dashboard.userName,
                activeWorkspaceName = activeWorkspaceName,
                onProfileClick = onProfileClick,
            )
        }

        // 3. Grafit Aylık Özet Kartı ("Dönem neti", büyük tabular tutar, Gelir & Gider kutucukları)
        item(key = "graphite_summary_card") {
            GraphiteSummaryCard(
                summary = dashboard.monthlySummary,
                summaryCurrencyCode = dashboard.summaryCurrencyCode,
            )
        }

        // 4. Tasarruf oranı ve ince yeşil ilerleme göstergesi ("Aylık gelire göre")
        item(key = "savings_rate_section") {
            SavingsRateSection(
                summary = dashboard.monthlySummary,
            )
        }

        // 5. Bütçelerin (Kategori ikonları, harcanan/limit tutarları ve ilerleme çubukları)
        item(key = "home_budgets_section") {
            HomeBudgetsSection(
                items = dashboard.budgetProgressItems,
                onViewAllBudgets = onBudgetsClick,
            )
        }

        // 6. Son işlemler ve "Tümü" bağlantısı (kırmızı gider, yeşil gelir)
        item(key = "home_recent_transactions_section") {
            HomeRecentTransactionsSection(
                recentTransactions = dashboard.recentTransactions,
                onViewAllTransactions = onViewAllTransactions,
                onTransactionClick = onTransactionClick,
                onAddTransaction = onAddTransaction,
            )
        }

        // 7. Gerçek veriden üretilen Feniqo İçgörü kartı (Parıltı ikonu ve nane zemin)
        dashboard.insight?.let { insight ->
            item(key = "home_insight_card") {
                HomeInsightCard(
                    insight = insight,
                )
            }
        }

        // 8. Mevcut aktif aboneliklerden yaklaşan ödemeler (Gün/Ay rozeti ve abonelik bilgisi)
        item(key = "home_upcoming_payments_section") {
            HomeUpcomingPaymentsSection(
                upcomingBills = dashboard.upcomingBills,
                onSubscriptionsClick = onSubscriptionsClick,
            )
        }

        // 9. Mevcut veride varsa birikim hedefi
        dashboard.savingsGoal?.let { goal ->
            item(key = "home_savings_goal_section") {
                HomeSavingsGoalSection(
                    goal = goal,
                    onGoalClick = onGoalClick,
                )
            }
        }

        // 10. MoneyScore ve görünür "Ön değerlendirme" açıklaması (Dairesel arc gauge)
        dashboard.moneyScore?.let { score ->
            item(key = "home_money_score_section") {
                HomeMoneyScoreSection(
                    moneyScore = score,
                )
            }
        }

        // 11. Farklı para birimindeki işlemlerin özete dahil edilmediğini bildiren açıklama
        if (dashboard.excludedDifferentCurrencyCount > 0) {
            item(key = "currency_scope_notice") {
                CurrencyScopeNoticeCard(
                    count = dashboard.excludedDifferentCurrencyCount,
                    currencyCode = dashboard.summaryCurrencyCode,
                    onClick = onViewAllTransactions,
                )
            }
        }

        // Varsa Bütçe Aşım Uyarısı
        dashboard.budgetAlert?.let { alert ->
            item(key = "budget_alert") {
                BudgetAlertBanner(alert = alert)
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Previews
// -------------------------------------------------------------------------------------

private val sampleSummary = MonthlySummaryDisplayModel(
    formattedIncome = "+₺48.250",
    formattedExpense = "-₺12.840",
    formattedBalance = "+₺35.410",
    balanceMinor = 3541000L,
    balanceStatus = NetBalanceStatus.POSITIVE,
    formattedSavingsRate = "%73,39",
    savingsRateBasisPoints = 7339,
)

private val sampleTopExpense = TopExpenseCategoryDisplayModel(
    categoryId = EntityId("cat-market"),
    categoryName = "Market",
    categoryColorHex = "#F97352",
    categoryIconKey = "shopping-cart",
    formattedAmount = "₺4.200",
    transactionCount = 6,
    formattedTransactionCount = "6 işlem",
)

private val sampleMoneyScore = MoneyScoreDisplayModel(
    totalScore = 78,
    level = MoneyScoreLevel.HEALTHY,
    formattedLevel = "Sağlıklı",
    savingsScore = 24,
    budgetScore = 22,
    debtScore = 16,
    goalScore = 16,
    isProvisional = true,
    explanationText = "Bu puan ön değerlendirmedir; bazı bileşenler nötr değerlerle hesaplanır.",
)

private val sampleBudgets = listOf(
    DashboardBudgetProgressItem(
        categoryName = "Market",
        categoryIconKey = "shopping-cart",
        categoryColorHex = "#F97352",
        formattedSpent = "₺4.200",
        formattedLimit = "₺6.000",
        progressRatio = 0.70f,
    ),
    DashboardBudgetProgressItem(
        categoryName = "Yeme & İçme",
        categoryIconKey = "food_dining",
        categoryColorHex = "#F59E0B",
        formattedSpent = "₺2.550",
        formattedLimit = "₺3.000",
        progressRatio = 0.85f,
    ),
)

private val sampleRecentTransactions = listOf(
    TransactionDisplayModel(
        id = EntityId("t1"),
        amount = com.feniqo.mobile.domain.model.Money(48650L, com.feniqo.mobile.domain.model.Currency.TRY),
        formattedAmount = "-486,50 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-market"),
        categoryName = "Market",
        categoryColorHex = "#F97352",
        categoryIconKey = "shopping-cart",
        description = "Migros",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = com.feniqo.mobile.domain.model.LocalDate(2026, 9, 15),
        hasReceipt = false,
        installment = null,
        canEdit = true,
        canDelete = true,
    ),
    TransactionDisplayModel(
        id = EntityId("t2"),
        amount = com.feniqo.mobile.domain.model.Money(14000L, com.feniqo.mobile.domain.model.Currency.TRY),
        formattedAmount = "-140,00 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-dining"),
        categoryName = "Yeme & İçme",
        categoryColorHex = "#F59E0B",
        categoryIconKey = "food_dining",
        description = "Kahve molası",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = com.feniqo.mobile.domain.model.LocalDate(2026, 9, 15),
        hasReceipt = false,
        installment = null,
        canEdit = true,
        canDelete = true,
    ),
    TransactionDisplayModel(
        id = EntityId("t3"),
        amount = com.feniqo.mobile.domain.model.Money(4500000L, com.feniqo.mobile.domain.model.Currency.TRY),
        formattedAmount = "+45.000,00 ₺",
        type = TransactionType.INCOME,
        categoryId = EntityId("cat-salary"),
        categoryName = "Maaş",
        categoryColorHex = "#10B981",
        categoryIconKey = "salary",
        description = "Maaş",
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        transactionDate = com.feniqo.mobile.domain.model.LocalDate(2026, 9, 14),
        hasReceipt = false,
        installment = null,
        canEdit = true,
        canDelete = true,
    ),
)

private val sampleUpcomingBills = listOf(
    DashboardUpcomingBillItem(
        title = "İnternet",
        formattedDueDate = "18 Eylül 2026",
        formattedAmount = "₺499",
        iconKey = "subscriptions",
        dayNumber = "18",
        monthShort = "EYL",
    ),
    DashboardUpcomingBillItem(
        title = "Müzik üyeliği",
        formattedDueDate = "22 Eylül 2026",
        formattedAmount = "₺99,99",
        iconKey = "subscriptions",
        dayNumber = "22",
        monthShort = "EYL",
    ),
)

private val sampleSavingsGoal = DashboardSavingsGoalItem(
    goalName = "Seyahat",
    formattedCurrent = "₺12.000",
    formattedTarget = "₺30.000",
    progressRatio = 0.40f,
)

private val sampleInsight = DashboardInsightModel(
    title = "Feniqo İçgörü",
    message = "Bu ay en yüksek harcaman Market kategorisinde: ₺4.200.",
)

private val fullDashboardState = DashboardUiState(
    isLoading = false,
    dashboard = DashboardDisplayModel(
        month = YearMonth("2026-09"),
        formattedMonth = "Eylül 2026",
        monthlySummary = sampleSummary,
        topExpenseCategory = sampleTopExpense,
        recentTransactions = sampleRecentTransactions,
        moneyScore = sampleMoneyScore,
        budgetAlert = null,
        userName = "Ayşe",
        budgetProgressItems = sampleBudgets,
        upcomingBills = sampleUpcomingBills,
        savingsGoal = sampleSavingsGoal,
        insight = sampleInsight,
        excludedDifferentCurrencyCount = 2,
    ),
    selectedMonth = YearMonth("2026-09"),
    activeWorkspaceName = "Kişisel",
)

private val emptyDashboardState = DashboardUiState(
    isLoading = false,
    dashboard = DashboardDisplayModel(
        month = YearMonth("2026-09"),
        formattedMonth = "Eylül 2026",
        monthlySummary = sampleSummary.copy(
            formattedIncome = "0,00 ₺",
            formattedExpense = "0,00 ₺",
            formattedBalance = "0,00 ₺",
            balanceMinor = 0L,
            balanceStatus = NetBalanceStatus.NEUTRAL,
            formattedSavingsRate = "%0,00",
            savingsRateBasisPoints = 0,
        ),
        topExpenseCategory = null,
        recentTransactions = emptyList(),
        moneyScore = null,
        budgetAlert = null,
        userName = "Ayşe",
        budgetProgressItems = emptyList(),
        upcomingBills = emptyList(),
        savingsGoal = null,
        insight = sampleInsight.copy(message = "Küçük adımlarla finansal hedeflerine doğru ilerliyorsun. Günlük harcamalarını düzenli kaydetmeyi unutma!"),
    ),
    selectedMonth = YearMonth("2026-09"),
    activeWorkspaceName = "Kişisel",
)

@Composable
fun DashboardScreenPreview_Full_Light() {
    FeniqoTheme(darkTheme = false) {
        DashboardScreen(
            state = fullDashboardState,
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
            onProfileClick = {},
            onBudgetsClick = {},
            onSubscriptionsClick = {},
            onGoalClick = {},
        )
    }
}

@Composable
fun DashboardScreenPreview_Full_Dark() {
    FeniqoTheme(darkTheme = true) {
        DashboardScreen(
            state = fullDashboardState,
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
            onProfileClick = {},
            onBudgetsClick = {},
            onSubscriptionsClick = {},
            onGoalClick = {},
        )
    }
}

@Composable
fun DashboardScreenPreview_Empty_Light() {
    FeniqoTheme(darkTheme = false) {
        DashboardScreen(
            state = emptyDashboardState,
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
            onProfileClick = {},
            onBudgetsClick = {},
            onSubscriptionsClick = {},
            onGoalClick = {},
        )
    }
}

@Composable
fun DashboardScreenPreview_Loading() {
    FeniqoTheme(darkTheme = false) {
        DashboardScreen(
            state = DashboardUiState(isLoading = true),
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
            onProfileClick = {},
            onBudgetsClick = {},
            onSubscriptionsClick = {},
            onGoalClick = {},
        )
    }
}

@Composable
fun DashboardScreenPreview_Error() {
    FeniqoTheme(darkTheme = false) {
        DashboardScreen(
            state = DashboardUiState(
                isLoading = false,
                observationError = FinanceUiMessage.GENERIC_ERROR,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
            onProfileClick = {},
            onBudgetsClick = {},
            onSubscriptionsClick = {},
            onGoalClick = {},
        )
    }
}
