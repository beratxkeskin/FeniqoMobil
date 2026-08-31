package com.feniqo.mobile.presentation.dashboard

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CategorySpendingSummary
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DashboardSummary
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.MoneyScore
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardDisplayModelBuilderTest {

    private val sampleCategoryMarket = Category(
        id = EntityId("cat-market"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        icon = CategoryIcon("shopping"),
        color = CategoryColor("#10B981"),
        isDefault = false,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private val sampleCategorySalary = Category(
        id = EntityId("cat-salary"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Maaş",
        type = TransactionType.INCOME,
        icon = CategoryIcon("payments"),
        color = CategoryColor("#3B82F6"),
        isDefault = false,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private fun createTrx(
        id: String,
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "cat-market",
        date: LocalDate = LocalDate(2026, 8, 1),
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor = amount, currency = Currency.TRY),
        type = type,
        categoryId = EntityId(categoryId),
        description = "İşlem $id",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = null,
        createdAt = Instant.parse("2026-08-01T10:00:00Z"),
    )

    @Test
    fun build_positiveNetBalance_formatsValuesAndSetsPositiveStatus() {
        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 1500000, currency = Currency.TRY),
            expense = Money(amountMinor = 500000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = 1000000, currency = Currency.TRY),
            savingsRate = RateBasisPoints(6666),
            topExpenseCategory = null,
            recentTransactionIds = emptyList(),
            moneyScore = null,
        )

        val result = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = emptyList(),
            categories = listOf(sampleCategoryMarket, sampleCategorySalary),
        )

        assertEquals("Ağustos 2026", result.formattedMonth)
        assertEquals("+15.000,00 ₺", result.monthlySummary.formattedIncome)
        assertEquals("-5.000,00 ₺", result.monthlySummary.formattedExpense)
        assertEquals("+10.000,00 ₺", result.monthlySummary.formattedBalance)
        assertEquals(1000000L, result.monthlySummary.balanceMinor)
        assertEquals(NetBalanceStatus.POSITIVE, result.monthlySummary.balanceStatus)
        assertEquals("%66,66", result.monthlySummary.formattedSavingsRate)
        assertNull(result.topExpenseCategory)
        assertNull(result.moneyScore)
        assertTrue(result.recentTransactions.isEmpty())
    }

    @Test
    fun build_negativeNetBalance_formatsMinusSignAndSetsNegativeStatus() {
        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 200000, currency = Currency.TRY),
            expense = Money(amountMinor = 350000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = -150000, currency = Currency.TRY),
            savingsRate = RateBasisPoints(-7500),
            topExpenseCategory = null,
            recentTransactionIds = emptyList(),
            moneyScore = null,
        )

        val result = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = emptyList(),
            categories = emptyList(),
        )

        assertEquals("+2.000,00 ₺", result.monthlySummary.formattedIncome)
        assertEquals("-3.500,00 ₺", result.monthlySummary.formattedExpense)
        assertEquals("-1.500,00 ₺", result.monthlySummary.formattedBalance)
        assertEquals(-150000L, result.monthlySummary.balanceMinor)
        assertEquals(NetBalanceStatus.NEGATIVE, result.monthlySummary.balanceStatus)
        assertEquals("-%75,00", result.monthlySummary.formattedSavingsRate)
    }

    @Test
    fun build_zeroNetBalance_setsNeutralStatus() {
        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 100000, currency = Currency.TRY),
            expense = Money(amountMinor = 100000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = 0, currency = Currency.TRY),
            savingsRate = RateBasisPoints(0),
            topExpenseCategory = null,
            recentTransactionIds = emptyList(),
            moneyScore = null,
        )

        val result = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = emptyList(),
            categories = emptyList(),
        )

        assertEquals("0,00 ₺", result.monthlySummary.formattedBalance)
        assertEquals(0L, result.monthlySummary.balanceMinor)
        assertEquals(NetBalanceStatus.NEUTRAL, result.monthlySummary.balanceStatus)
        assertEquals("%0,00", result.monthlySummary.formattedSavingsRate)
    }

    @Test
    fun build_basisPointFormatting_handlesVariousFractions() {
        fun formatBasisPoints(bp: Int): String {
            val summary = DashboardSummary(
                month = YearMonth("2026-08"),
                income = Money(amountMinor = 100, currency = Currency.TRY),
                expense = Money(amountMinor = 100, currency = Currency.TRY),
                balance = MoneyDelta(amountMinor = 0, currency = Currency.TRY),
                savingsRate = RateBasisPoints(bp),
                topExpenseCategory = null,
                recentTransactionIds = emptyList(),
                moneyScore = null,
            )
            return DashboardDisplayModelBuilder.build(summary, emptyList(), emptyList()).monthlySummary.formattedSavingsRate
        }

        assertEquals("%34,00", formatBasisPoints(3400))
        assertEquals("%12,34", formatBasisPoints(1234))
        assertEquals("%0,50", formatBasisPoints(50))
        assertEquals("%0,05", formatBasisPoints(5))
        assertEquals("%0,00", formatBasisPoints(0))
        assertEquals("-%5,00", formatBasisPoints(-500))
    }

    @Test
    fun build_recentTransactionIds_preservesExplicitOrder() {
        val trx1 = createTrx("trx-1", amount = 25000)
        val trx2 = createTrx("trx-2", amount = 50000)
        val trx3 = createTrx("trx-3", amount = 75000)

        // Summary sırası: trx-3, trx-1, trx-2
        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 0, currency = Currency.TRY),
            expense = Money(amountMinor = 150000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = -150000, currency = Currency.TRY),
            savingsRate = RateBasisPoints(0),
            topExpenseCategory = null,
            recentTransactionIds = listOf(EntityId("trx-3"), EntityId("trx-1"), EntityId("trx-2")),
            moneyScore = null,
        )

        val result = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = listOf(trx1, trx2, trx3),
            categories = listOf(sampleCategoryMarket),
        )

        assertEquals(3, result.recentTransactions.size)
        assertEquals(EntityId("trx-3"), result.recentTransactions[0].id)
        assertEquals("-750,00 ₺", result.recentTransactions[0].formattedAmount)
        assertEquals(EntityId("trx-1"), result.recentTransactions[1].id)
        assertEquals("-250,00 ₺", result.recentTransactions[1].formattedAmount)
        assertEquals(EntityId("trx-2"), result.recentTransactions[2].id)
        assertEquals("-500,00 ₺", result.recentTransactions[2].formattedAmount)
    }

    @Test
    fun build_missingTransactionAndMissingCategory_handledSafelyWithFallback() {
        val trx1 = createTrx("trx-1", amount = 25000, categoryId = "cat-deleted")

        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 0, currency = Currency.TRY),
            expense = Money(amountMinor = 25000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = -25000, currency = Currency.TRY),
            savingsRate = RateBasisPoints(0),
            topExpenseCategory = CategorySpendingSummary(
                categoryId = EntityId("cat-nonexistent"),
                amount = Money(amountMinor = 25000, currency = Currency.TRY),
                transactionCount = 1,
            ),
            recentTransactionIds = listOf(
                EntityId("trx-missing-1"), // Bulunamayan işlem
                EntityId("trx-1"),
                EntityId("trx-missing-2"), // Bulunamayan işlem
            ),
            moneyScore = null,
        )

        val result = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = listOf(trx1),
            categories = emptyList(), // Kategori listesi boş
        )

        // 1. Bulunamayan işlemler atlandı
        assertEquals(1, result.recentTransactions.size)
        assertEquals(EntityId("trx-1"), result.recentTransactions[0].id)
        assertEquals("Kategori", result.recentTransactions[0].categoryName)
        assertNull(result.recentTransactions[0].categoryColorHex)
        assertNull(result.recentTransactions[0].categoryIconKey)

        // 2. TopExpenseCategory fallback
        val top = result.topExpenseCategory
        assertEquals(EntityId("cat-nonexistent"), top?.categoryId)
        assertEquals("Kategori", top?.categoryName)
        assertNull(top?.categoryColorHex)
        assertNull(top?.categoryIconKey)
        assertEquals("250,00 ₺", top?.formattedAmount)
        assertEquals(1, top?.transactionCount)
        assertEquals("1 işlem", top?.formattedTransactionCount)
    }

    @Test
    fun build_moneyScore_mapsCorrectlyWhenPresent() {
        val score = MoneyScore(
            total = 85,
            savings = 25,
            budget = 25,
            debt = 18,
            goal = 17,
            level = MoneyScoreLevel.HEALTHY,
        )

        val summary = DashboardSummary(
            month = YearMonth("2026-08"),
            income = Money(amountMinor = 100000, currency = Currency.TRY),
            expense = Money(amountMinor = 50000, currency = Currency.TRY),
            balance = MoneyDelta(amountMinor = 50000, currency = Currency.TRY),
            savingsRate = RateBasisPoints(5000),
            topExpenseCategory = null,
            recentTransactionIds = emptyList(),
            moneyScore = score,
        )

        val defaultResult = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = emptyList(),
            categories = emptyList(),
        )

        val defaultMs = defaultResult.moneyScore
        assertNotNull(defaultMs)
        assertEquals(85, defaultMs?.totalScore)
        assertEquals(MoneyScoreLevel.HEALTHY, defaultMs?.level)
        assertEquals("Sağlıklı", defaultMs?.formattedLevel)
        assertEquals(25, defaultMs?.savingsScore)
        assertEquals(25, defaultMs?.budgetScore)
        assertEquals(18, defaultMs?.debtScore)
        assertEquals(17, defaultMs?.goalScore)
        assertFalse(defaultMs?.isProvisional == true)
        assertEquals("", defaultMs?.explanationText)

        // Açıkça provisional ve açıklama sağlandığında
        val explicitResult = DashboardDisplayModelBuilder.build(
            summary = summary,
            transactions = emptyList(),
            categories = emptyList(),
            moneyScoreIsProvisional = true,
            moneyScoreExplanationText = "Test açıklaması",
        )

        val explicitMs = explicitResult.moneyScore
        assertNotNull(explicitMs)
        assertTrue(explicitMs?.isProvisional == true)
        assertEquals("Test açıklaması", explicitMs?.explanationText)
    }
}
