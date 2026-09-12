package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.BudgetAlertBanner
import com.feniqo.mobile.presentation.component.DashboardHeader
import com.feniqo.mobile.presentation.component.DashboardTransactionItem
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.MoneyScoreSection
import com.feniqo.mobile.presentation.component.MonthlySummaryGrid
import com.feniqo.mobile.presentation.component.TopExpenseCategorySection
import com.feniqo.mobile.presentation.dashboard.DashboardDisplayModel
import com.feniqo.mobile.presentation.dashboard.DashboardUiState
import com.feniqo.mobile.presentation.dashboard.MoneyScoreDisplayModel
import com.feniqo.mobile.presentation.dashboard.MonthlySummaryDisplayModel
import com.feniqo.mobile.presentation.dashboard.NetBalanceStatus
import com.feniqo.mobile.presentation.dashboard.TopExpenseCategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel

/**
 * Dashboard ana ekranı.
 *
 * Sorumluluklar:
 * 1. Tamamen stateless Compose fonksiyonudur.
 * 2. Material 3 ve tema belirteçlerini kullanır; doğrudan renk veya sabit tarih kodlamaz.
 * 3. Loading, hata, boş liste ve zengin dashboard durumlarını kapsar.
 * 4. Tüm etkileşimli alanlar en az 48 dp dokunma hedefine ve erişilebilir contentDescription'a sahiptir.
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
    ) {
        // 1. Üst Karşılama ve Logo
        item(key = "zen_header") {
            DashboardHeader(
                formattedMonth = dashboard.formattedMonth,
                userName = dashboard.userName,
                activeWorkspaceName = activeWorkspaceName,
                onProfileClick = onProfileClick,
            )
        }

        // 2. Toplam Bakiye ve Bézier Dalgalı Çizgi Grafik Kartı
        item(key = "total_balance_card") {
            com.feniqo.mobile.presentation.component.TotalBalanceCard(
                summary = dashboard.monthlySummary,
                trendPercentage = dashboard.balanceTrendPercentage,
                trendDifference = dashboard.balanceTrendDifference,
            )
        }

        // 3. 3'lü Özet Kartları (Gelir, Harcama, Birikim)
        item(key = "monthly_summary_grid") {
            MonthlySummaryGrid(
                summary = dashboard.monthlySummary,
                incomeTrend = dashboard.incomeTrendPercentage,
                expenseTrend = dashboard.expenseTrendPercentage,
                savedTrend = dashboard.savedTrendPercentage,
            )
        }

        // 4. Bütçeler Bölümü (Kategori Renkli İlerleme Barları)
        item(key = "zen_budgets") {
            com.feniqo.mobile.presentation.component.ZenBudgetsSection(
                items = dashboard.budgetProgressItems,
                onViewAllClick = onViewAllTransactions,
            )
        }

        // 5. Feniqo İçgörü Banner (Botanik & Parıltı Kartı)
        item(key = "zen_insight") {
            com.feniqo.mobile.presentation.component.ZenInsightBanner(
                title = dashboard.insight?.title ?: "Feniqo İçgörü",
                message = dashboard.insight?.message
                    ?: "Bu ay yeme-içmeye %18 daha az harcadın. Harika ilerleme!",
            )
        }

        // 6. Yan Yana İki Sütunlu Bölüm (Son İşlemler & Yaklaşan Faturalar / Hedef)
        item(key = "zen_recent_and_upcoming") {
            com.feniqo.mobile.presentation.component.ZenRecentAndUpcomingSection(
                recentTransactions = dashboard.recentTransactions,
                upcomingBills = dashboard.upcomingBills,
                savingsGoal = dashboard.savingsGoal,
                onViewAllTransactions = onViewAllTransactions,
                onViewAllBills = onViewAllTransactions,
                onTransactionClick = onTransactionClick,
                onGoalClick = onAddTransaction,
            )
        }

        // 7. MoneyScore Finansal Sağlık (varsa ek zenginlik olarak)
        dashboard.moneyScore?.let { score ->
            item(key = "money_score") {
                MoneyScoreSection(moneyScore = score)
            }
        }

        // 8. Varsa Bütçe Aşım Uyarısı
        dashboard.budgetAlert?.let { alert ->
            item(key = "budget_alert") {
                BudgetAlertBanner(alert = alert)
            }
        }
    }
}

@Composable
private fun EmptyRecentTransactionsCard(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Text(
                text = "Henüz bu aya ait işlem bulunmuyor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onAddTransaction,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "İlk işlemini ekle"
                    },
                shape = RoundedCornerShape(FeniqoRadius.Medium),
            ) {
                Text("İlk İşlemini Ekle")
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Previews
// -------------------------------------------------------------------------------------

private val sampleSummary = MonthlySummaryDisplayModel(
    formattedIncome = "+15.000,00 ₺",
    formattedExpense = "-5.000,00 ₺",
    formattedBalance = "+10.000,00 ₺",
    balanceMinor = 1000000L,
    balanceStatus = NetBalanceStatus.POSITIVE,
    formattedSavingsRate = "%66,66",
    savingsRateBasisPoints = 6666,
)

private val sampleTopExpense = TopExpenseCategoryDisplayModel(
    categoryId = EntityId("cat-market"),
    categoryName = "Market",
    categoryColorHex = "#10B981",
    categoryIconKey = "shopping",
    formattedAmount = "2.500,00 ₺",
    transactionCount = 5,
    formattedTransactionCount = "5 işlem",
)

private val sampleMoneyScore = MoneyScoreDisplayModel(
    totalScore = 85,
    level = MoneyScoreLevel.HEALTHY,
    formattedLevel = "Sağlıklı",
    savingsScore = 25,
    budgetScore = 25,
    debtScore = 18,
    goalScore = 17,
    isProvisional = true,
    explanationText = "Skor şu anda gelir-gider hareketleri ve henüz kullanılmayan bütçe, borç ve hedef modülleri için nötr başlangıç puanlarıyla hesaplanır.",
)

private val sampleRecentTransactions = listOf(
    TransactionDisplayModel(
        id = EntityId("t1"),
        amount = com.feniqo.mobile.domain.model.Money(45000L, com.feniqo.mobile.domain.model.Currency.TRY),
        formattedAmount = "-450,00 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-market"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping",
        description = "Haftalık mutfak alışverişi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = com.feniqo.mobile.domain.model.LocalDate(2026, 8, 26),
        hasReceipt = false,
        installment = null,
        canEdit = true,
        canDelete = true,
    ),
    TransactionDisplayModel(
        id = EntityId("t2"),
        amount = com.feniqo.mobile.domain.model.Money(1500000L, com.feniqo.mobile.domain.model.Currency.TRY),
        formattedAmount = "+15.000,00 ₺",
        type = TransactionType.INCOME,
        categoryId = EntityId("cat-salary"),
        categoryName = "Maaş",
        categoryColorHex = "#3B82F6",
        categoryIconKey = "payments",
        description = "Ağustos Maaşı",
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        transactionDate = com.feniqo.mobile.domain.model.LocalDate(2026, 8, 15),
        hasReceipt = false,
        installment = null,
        canEdit = true,
        canDelete = true,
    ),
)

private val fullDashboardState = DashboardUiState(
    isLoading = false,
    dashboard = DashboardDisplayModel(
        month = YearMonth("2026-08"),
        formattedMonth = "Ağustos 2026",
        monthlySummary = sampleSummary,
        topExpenseCategory = sampleTopExpense,
        recentTransactions = sampleRecentTransactions,
        moneyScore = sampleMoneyScore,
        budgetAlert = null,
    ),
    selectedMonth = YearMonth("2026-08"),
)

private val emptyDashboardState = DashboardUiState(
    isLoading = false,
    dashboard = DashboardDisplayModel(
        month = YearMonth("2026-08"),
        formattedMonth = "Ağustos 2026",
        monthlySummary = sampleSummary.copy(
            formattedIncome = "0,00 ₺",
            formattedExpense = "0,00 ₺",
            formattedBalance = "0,00 ₺",
            balanceMinor = 0L,
            balanceStatus = NetBalanceStatus.NEUTRAL,
            formattedSavingsRate = "%0,00",
        ),
        topExpenseCategory = null,
        recentTransactions = emptyList(),
        moneyScore = null,
        budgetAlert = null,
    ),
    selectedMonth = YearMonth("2026-08"),
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
        )
    }
}

@Composable
fun DashboardScreenPreview_Empty_Dark() {
    FeniqoTheme(darkTheme = true) {
        DashboardScreen(
            state = emptyDashboardState,
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {},
            onAddTransaction = {},
            onViewAllTransactions = {},
            onTransactionClick = {},
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
        )
    }
}
