package com.feniqo.mobile.presentation.dashboard

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel

/**
 * Net bakiye yön durumu.
 * UI'da pozitif (kazanç/yeşil), negatif (açık/kırmızı) veya nötr stil belirler.
 */
enum class NetBalanceStatus {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

/**
 * Aylık gelir, gider, net bakiye ve tasarruf oranı display modelidir.
 */
data class MonthlySummaryDisplayModel(
    val formattedIncome: String,
    val formattedExpense: String,
    val formattedBalance: String,
    val balanceMinor: Long,
    val balanceStatus: NetBalanceStatus,
    val formattedSavingsRate: String,
    val savingsRateBasisPoints: Int,
)

/**
 * Ayın en yüksek gider kategorisi display modelidir.
 */
data class TopExpenseCategoryDisplayModel(
    val categoryId: EntityId,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryIconKey: String?,
    val formattedAmount: String,
    val transactionCount: Int,
    val formattedTransactionCount: String,
)

/**
 * MoneyScore finansal sağlık puanı display modelidir.
 */
data class MoneyScoreDisplayModel(
    val totalScore: Int,
    val level: MoneyScoreLevel,
    val formattedLevel: String,
    val savingsScore: Int,
    val budgetScore: Int,
    val debtScore: Int,
    val goalScore: Int,
    val isProvisional: Boolean,
    val explanationText: String,
)

/**
 * İleride kullanılacak bütçe uyarısı ve aşım bildirimleri için presentation modeli.
 */
data class BudgetAlertDisplayModel(
    val title: String,
    val message: String,
    val isExceeded: Boolean = false,
)

/**
 * Dashboard ekranı için birleştirilmiş ve formatlanmış presentation modelidir.
 */
data class DashboardDisplayModel(
    val month: YearMonth,
    val formattedMonth: String,
    val monthlySummary: MonthlySummaryDisplayModel,
    val topExpenseCategory: TopExpenseCategoryDisplayModel?,
    val recentTransactions: List<TransactionDisplayModel>,
    val moneyScore: MoneyScoreDisplayModel?,
    val budgetAlert: BudgetAlertDisplayModel? = null,
)
