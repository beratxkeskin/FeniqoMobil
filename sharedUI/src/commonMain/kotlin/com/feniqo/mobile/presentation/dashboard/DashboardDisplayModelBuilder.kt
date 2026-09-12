package com.feniqo.mobile.presentation.dashboard

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.DashboardSummary
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Domain DashboardSummary, Transaction ve Category modellerini Dashboard UI presentation modellerine
 * dönüştüren saf ve deterministik builder.
 *
 * Kurallar:
 * 1. Para hesabı yapmaz; domain katmanından gelen değerleri formatlar.
 * 2. Double/Float kullanmaz.
 * 3. recentTransactionIds sırasını korur ve silinmiş/bulunamayan ID'leri güvenle atlar.
 * 4. Kategori listesinde bulunamayan kayıtlar için güvenli fallback ("Kategori") sağlar.
 * 5. Hassas alanları (kullanıcı ID, dosya yolu vb.) UI modeline sızdırmaz.
 */
object DashboardDisplayModelBuilder {

    private const val FALLBACK_CATEGORY_NAME = "Kategori"

    fun build(
        summary: DashboardSummary,
        transactions: List<Transaction>,
        categories: List<Category>,
        budgets: List<com.feniqo.mobile.domain.usecase.BudgetProgressItem> = emptyList(),
        subscriptions: List<com.feniqo.mobile.domain.model.Subscription> = emptyList(),
        goals: List<com.feniqo.mobile.domain.model.Goal> = emptyList(),
        moneyScoreIsProvisional: Boolean = false,
        moneyScoreExplanationText: String = "",
        userName: String = "Kullanıcı",
    ): DashboardDisplayModel {
        val categoryMap = categories.associateBy { it.id }
        val transactionMap = transactions.associateBy { it.id }

        // 1. Aylık Özet
        val balanceMinor = summary.balance.amountMinor
        val balanceStatus = when {
            balanceMinor > 0 -> NetBalanceStatus.POSITIVE
            balanceMinor < 0 -> NetBalanceStatus.NEGATIVE
            else -> NetBalanceStatus.NEUTRAL
        }

        val monthlySummary = MonthlySummaryDisplayModel(
            formattedIncome = MoneyFormatter.format(
                money = summary.income,
                includeSign = true,
                type = TransactionType.INCOME,
            ),
            formattedExpense = MoneyFormatter.format(
                money = summary.expense,
                includeSign = true,
                type = TransactionType.EXPENSE,
            ),
            formattedBalance = MoneyFormatter.formatDelta(
                delta = summary.balance,
                includeSign = true,
            ),
            balanceMinor = balanceMinor,
            balanceStatus = balanceStatus,
            formattedSavingsRate = MoneyFormatter.formatBasisPoints(summary.savingsRate),
            savingsRateBasisPoints = summary.savingsRate.value,
        )

        // 2. En Yüksek Gider Kategorisi
        val topExpenseCategory = summary.topExpenseCategory?.let { top ->
            val cat = categoryMap[top.categoryId]
            TopExpenseCategoryDisplayModel(
                categoryId = top.categoryId,
                categoryName = cat?.name ?: FALLBACK_CATEGORY_NAME,
                categoryColorHex = cat?.color?.hex,
                categoryIconKey = cat?.icon?.key,
                formattedAmount = MoneyFormatter.format(top.amount),
                transactionCount = top.transactionCount,
                formattedTransactionCount = "${top.transactionCount} işlem",
            )
        }

        // 3. Son İşlemler (recentTransactionIds sırası korunur, eksikler atlanır)
        val recentTransactions = summary.recentTransactionIds.mapNotNull { trxId ->
            val trx = transactionMap[trxId] ?: return@mapNotNull null
            val cat = categoryMap[trx.categoryId]

            TransactionDisplayModel(
                id = trx.id,
                amount = trx.amount,
                formattedAmount = MoneyFormatter.format(
                    money = trx.amount,
                    includeSign = true,
                    type = trx.type,
                ),
                type = trx.type,
                categoryId = trx.categoryId,
                categoryName = cat?.name ?: FALLBACK_CATEGORY_NAME,
                categoryColorHex = cat?.color?.hex,
                categoryIconKey = cat?.icon?.key,
                description = trx.description,
                paymentMethod = trx.paymentMethod,
                transactionDate = trx.transactionDate,
                installment = trx.installment?.let { inst ->
                    InstallmentDisplayModel(
                        number = inst.number,
                        total = inst.total,
                        badgeText = "${inst.number}/${inst.total}",
                    )
                },
                hasReceipt = trx.receiptPath != null,
                canEdit = true,
                canDelete = true,
            )
        }

        // 4. Gerçek Bütçe İlerleme Satırları
        val budgetProgressItems = budgets.map { item ->
            val spent = item.progress.spent
            val limit = item.progress.budget.limit
            val ratio = if (limit.amountMinor > 0) {
                (spent.amountMinor.toFloat() / limit.amountMinor.toFloat()).coerceIn(0f, 1f)
            } else 0f

            DashboardBudgetProgressItem(
                categoryName = item.category?.name ?: FALLBACK_CATEGORY_NAME,
                categoryIconKey = item.category?.icon?.key,
                categoryColorHex = item.category?.color?.hex,
                formattedSpent = MoneyFormatter.format(spent),
                formattedLimit = MoneyFormatter.format(limit),
                progressRatio = ratio,
            )
        }

        // 5. Gerçek Yaklaşan Faturalar (Aktif Abonelikler)
        val upcomingBills = subscriptions
            .filter { it.isActive }
            .sortedBy { it.nextRenewalDate }
            .take(3)
            .map { sub ->
                DashboardUpcomingBillItem(
                    title = sub.name,
                    formattedDueDate = DateFormatter.formatReadableDate(sub.nextRenewalDate),
                    formattedAmount = MoneyFormatter.format(sub.amount),
                    iconKey = "subscriptions",
                )
            }

        // 6. Gerçek Birikim Hedefi (Hedefler listesinden ilki)
        val savingsGoal = goals
            .firstOrNull { it.targetAmount.amountMinor > 0 }
            ?.let { goal ->
                val ratio = (goal.currentAmount.amountMinor.toFloat() / goal.targetAmount.amountMinor.toFloat()).coerceIn(0f, 1f)
                DashboardSavingsGoalItem(
                    goalName = goal.name,
                    formattedCurrent = MoneyFormatter.format(goal.currentAmount),
                    formattedTarget = MoneyFormatter.format(goal.targetAmount),
                    progressRatio = ratio,
                )
            }

        // 7. Dinamik Feniqo İçgörü (Gerçek veri analizi)
        val dynamicInsight = when {
            topExpenseCategory != null -> {
                DashboardInsightModel(
                    title = "Feniqo İçgörü",
                    message = "Bu ay en yüksek harcaman ${topExpenseCategory.categoryName} kategorisinde (${topExpenseCategory.formattedAmount}). Harcamalarını dengede tutmak için harika bir fırsat!",
                )
            }
            summary.savingsRate.value > 1500 -> {
                DashboardInsightModel(
                    title = "Feniqo İçgörü",
                    message = "Bu ay %${summary.savingsRate.value / 100} tasarruf oranına ulaştın. Harika bir finansal disiplin sergiliyorsun!",
                )
            }
            else -> {
                DashboardInsightModel(
                    title = "Feniqo İçgörü",
                    message = "Küçük adımlarla finansal hedeflerine doğru ilerliyorsun. Günlük harcamalarını düzenli kaydetmeyi unutma!",
                )
            }
        }

        // 8. MoneyScore Finansal Sağlık Skoru
        val moneyScore = summary.moneyScore?.let { score ->
            MoneyScoreDisplayModel(
                totalScore = score.total,
                level = score.level,
                formattedLevel = when (score.level) {
                    MoneyScoreLevel.CRITICAL -> "Kritik"
                    MoneyScoreLevel.HEALTHY -> "Sağlıklı"
                    MoneyScoreLevel.EXCELLENT -> "Mükemmel"
                },
                savingsScore = score.savings,
                budgetScore = score.budget,
                debtScore = score.debt,
                goalScore = score.goal,
                isProvisional = moneyScoreIsProvisional,
                explanationText = moneyScoreExplanationText,
            )
        }

        return DashboardDisplayModel(
            month = summary.month,
            formattedMonth = DateFormatter.formatYearMonth(summary.month),
            monthlySummary = monthlySummary,
            topExpenseCategory = topExpenseCategory,
            recentTransactions = recentTransactions,
            moneyScore = moneyScore,
            budgetAlert = null,
            userName = userName,
            budgetProgressItems = budgetProgressItems,
            upcomingBills = upcomingBills,
            savingsGoal = savingsGoal,
            insight = dynamicInsight,
        )
    }
}
