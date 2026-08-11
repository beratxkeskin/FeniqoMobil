package com.feniqo.mobile.domain.model

/** Yüzde değerlerini kayan nokta yerine baz puanla taşır: %12,34 = 1234. */
data class RateBasisPoints(val value: Int)

enum class MoneyScoreLevel {
    CRITICAL,
    HEALTHY,
    EXCELLENT,
}

data class MoneyScore(
    val total: Int,
    val savings: Int,
    val budget: Int,
    val debt: Int,
    val goal: Int,
    val level: MoneyScoreLevel,
) {
    init {
        require(savings in 0..30) { "Tasarruf skoru 0..30 aralığında olmalıdır." }
        require(budget in 0..30) { "Bütçe skoru 0..30 aralığında olmalıdır." }
        require(debt in 0..20) { "Borç skoru 0..20 aralığında olmalıdır." }
        require(goal in 0..20) { "Hedef skoru 0..20 aralığında olmalıdır." }
        require(total == savings + budget + debt + goal && total in 0..100) {
            "Toplam MoneyScore alt skorların toplamı ve 0..100 aralığında olmalıdır."
        }
    }
}

data class CategorySpendingSummary(
    val categoryId: EntityId,
    val amount: Money,
    val transactionCount: Int,
) {
    init {
        require(transactionCount >= 0) { "İşlem sayısı negatif olamaz." }
    }
}

data class DashboardSummary(
    val month: YearMonth,
    val income: Money,
    val expense: Money,
    val balance: MoneyDelta,
    val savingsRate: RateBasisPoints,
    val topExpenseCategory: CategorySpendingSummary?,
    val recentTransactionIds: List<EntityId>,
    val moneyScore: MoneyScore?,
) {
    init {
        require(income.currency == expense.currency && income.currency == balance.currency) {
            "Dashboard para alanları aynı para biriminde olmalıdır."
        }
    }
}

data class ReportPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(endDate >= startDate) { "Rapor bitiş tarihi başlangıçtan önce olamaz." }
    }
}

data class FinancialReport(
    val period: ReportPeriod,
    val income: Money,
    val expense: Money,
    val net: MoneyDelta,
    val savingsRate: RateBasisPoints,
    val spendingByCategory: List<CategorySpendingSummary>,
    val transactionCount: Int,
) {
    init {
        require(income.currency == expense.currency && income.currency == net.currency) {
            "Rapor para alanları aynı para biriminde olmalıdır."
        }
        require(transactionCount >= 0) { "Rapor işlem sayısı negatif olamaz." }
    }
}

data class PeriodComparison(
    val previous: FinancialReport,
    val current: FinancialReport,
    val incomeChange: MoneyDelta,
    val expenseChange: MoneyDelta,
    val netChange: MoneyDelta,
    val savingsRateChange: RateBasisPoints,
)
