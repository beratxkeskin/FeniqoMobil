package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

import kotlinx.datetime.Instant

class CategoryAnalyticsCalculatorTest {

    private val now = Instant.parse("2026-08-21T00:00:00Z")

    private val catFood = Category(
        id = EntityId("cat-food"),
        name = "Yeme & İçme",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("utensils"),
        isDefault = true,
        ownerId = null,
        workspaceId = null,
        createdAt = now,
    )

    private val catShopping = Category(
        id = EntityId("cat-shopping"),
        name = "Alışveriş",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#F59E0B"),
        icon = CategoryIcon("shopping-bag"),
        isDefault = true,
        ownerId = null,
        workspaceId = null,
        createdAt = now,
    )

    private val catSalary = Category(
        id = EntityId("cat-salary"),
        name = "Maaş",
        type = TransactionType.INCOME,
        color = CategoryColor("#3B82F6"),
        icon = CategoryIcon("briefcase"),
        isDefault = true,
        ownerId = null,
        workspaceId = null,
        createdAt = now,
    )

    private val catCustom = Category(
        id = EntityId("cat-custom"),
        name = "Özel Hobi",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#8B5CF6"),
        icon = CategoryIcon("heart-pulse"),
        isDefault = false,
        ownerId = EntityId("user-1"),
        workspaceId = null,
        createdAt = now,
    )

    private fun buildTx(
        id: String,
        categoryId: EntityId,
        amountMinor: Long,
        type: TransactionType,
        date: LocalDate = LocalDate(2026, 9, 15),
        currency: Currency = Currency.TRY,
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = categoryId,
        description = null,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = null,
        createdAt = now,
    )

    @Test
    fun calculateReportPeriods_handlesNormalMonth_andYearTransition() {
        // Normal ay
        val (curSep, prevAug) = CategoryAnalyticsCalculator.calculateReportPeriods(YearMonth("2026-09"))
        assertEquals(LocalDate(2026, 9, 1), curSep.startDate)
        assertEquals(LocalDate(2026, 9, 30), curSep.endDate)
        assertEquals(LocalDate(2026, 8, 1), prevAug.startDate)
        assertEquals(LocalDate(2026, 8, 31), prevAug.endDate)

        // Ocak -> Aralık yıl geçişi
        val (curJan, prevDec) = CategoryAnalyticsCalculator.calculateReportPeriods(YearMonth("2026-01"))
        assertEquals(LocalDate(2026, 1, 1), curJan.startDate)
        assertEquals(LocalDate(2026, 1, 31), curJan.endDate)
        assertEquals(LocalDate(2025, 12, 1), prevDec.startDate)
        assertEquals(LocalDate(2025, 12, 31), prevDec.endDate)
    }

    @Test
    fun calculateTrend_handlesVariousScenarios() {
        // Her iki dönem sıfır -> None
        assertEquals(CategoryTrend.None, CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 0L, 0L))

        // Önceki sıfır, mevcut pozitif -> New
        assertEquals(CategoryTrend.New, CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 1000L, 0L))

        // Gider artışı -> INCREASED ve NEGATIVE (kırmızı)
        val expenseInc = CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 1200L, 1000L)
        assertTrue(expenseInc is CategoryTrend.Changed)
        assertEquals(2000, expenseInc.changeBasisPoints) // %20
        assertEquals(TrendMovement.INCREASED, expenseInc.movement)
        assertEquals(TrendSentiment.NEGATIVE, expenseInc.sentiment)

        // Gider azalışı -> DECREASED ve POSITIVE (yeşil)
        val expenseDec = CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 800L, 1000L)
        assertTrue(expenseDec is CategoryTrend.Changed)
        assertEquals(2000, expenseDec.changeBasisPoints) // %20
        assertEquals(TrendMovement.DECREASED, expenseDec.movement)
        assertEquals(TrendSentiment.POSITIVE, expenseDec.sentiment)

        // Gelir artışı -> INCREASED ve POSITIVE (yeşil)
        val incomeInc = CategoryAnalyticsCalculator.calculateTrend(TransactionType.INCOME, 1500L, 1000L)
        assertTrue(incomeInc is CategoryTrend.Changed)
        assertEquals(5000, incomeInc.changeBasisPoints) // %50
        assertEquals(TrendMovement.INCREASED, incomeInc.movement)
        assertEquals(TrendSentiment.POSITIVE, incomeInc.sentiment)

        // Gelir azalışı -> DECREASED ve NEGATIVE (kırmızı)
        val incomeDec = CategoryAnalyticsCalculator.calculateTrend(TransactionType.INCOME, 700L, 1000L)
        assertTrue(incomeDec is CategoryTrend.Changed)
        assertEquals(3000, incomeDec.changeBasisPoints) // %30
        assertEquals(TrendMovement.DECREASED, incomeDec.movement)
        assertEquals(TrendSentiment.NEGATIVE, incomeDec.sentiment)

        // Mevcut sıfır, önceki pozitif -> %100 azalış
        val zeroCurrent = CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 0L, 1000L)
        assertTrue(zeroCurrent is CategoryTrend.Changed)
        assertEquals(10_000, zeroCurrent.changeBasisPoints)
        assertEquals(TrendMovement.DECREASED, zeroCurrent.movement)
        assertEquals(TrendSentiment.POSITIVE, zeroCurrent.sentiment)
    }

    @Test
    fun safeAdd_throwsArithmeticException_onLongOverflow() {
        assertFailsWith<ArithmeticException> {
            CategoryAnalyticsCalculator.safeAdd(Long.MAX_VALUE, 1L)
        }
        assertEquals(300L, CategoryAnalyticsCalculator.safeAdd(100L, 200L))
    }

    @Test
    fun calculate_excludesTransactionsWithDifferentCurrency() {
        val categories = listOf(catFood)
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 100L, TransactionType.EXPENSE, currency = Currency.TRY),
            buildTx("tx-2", catFood.id, 50L, TransactionType.EXPENSE, currency = Currency.USD),
        )

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = null,
            currency = Currency.TRY,
        )

        assertEquals(1, items.single().transactionCount)
        assertEquals(100L, items.single().currentPeriodAmount.amountMinor)
        assertEquals("Yeme & İçme", summary.topCategoryName)
    }

    @Test
    fun calculate_aggregatesSums_andSortsDescending_zeroAmountsAtEnd() {
        val categories = listOf(catShopping, catFood, catSalary, catCustom)
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 5000L, TransactionType.EXPENSE),
            buildTx("tx-2", catFood.id, 3000L, TransactionType.EXPENSE), // Toplam food: 8000L
            buildTx("tx-3", catShopping.id, 4000L, TransactionType.EXPENSE), // Toplam shopping: 4000L
            buildTx("tx-4", catSalary.id, 20000L, TransactionType.INCOME), // Toplam salary: 20000L
            // catCustom has 0 txs
        )

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = null,
            currency = Currency.TRY,
        )

        // 4 kategori olmalı
        assertEquals(4, items.size)
        // Sıralama: Salary (20000) -> Food (8000) -> Shopping (4000) -> Custom (0)
        assertEquals("Maaş", items[0].category.name)
        assertEquals(20000L, items[0].currentPeriodAmount.amountMinor)
        assertEquals(1, items[0].transactionCount)

        assertEquals("Yeme & İçme", items[1].category.name)
        assertEquals(8000L, items[1].currentPeriodAmount.amountMinor)
        assertEquals(2, items[1].transactionCount)

        assertEquals("Alışveriş", items[2].category.name)
        assertEquals(4000L, items[2].currentPeriodAmount.amountMinor)

        assertEquals("Özel Hobi", items[3].category.name)
        assertEquals(0L, items[3].currentPeriodAmount.amountMinor)
        assertEquals(0, items[3].transactionCount)

        // Özet kartı kontrolü
        assertEquals(4, summary.totalCategoriesCount)
        assertEquals(1, summary.customCategoriesCount)
        assertEquals("Yeme & İçme", summary.topExpenseCategoryName)
        assertNotNull(summary.formattedTopExpenseAmount)
        assertTrue(summary.miniBarProportionsBasisPoints.isNotEmpty())
    }

    @Test
    fun calculate_appliesTypeFilterCorrectly() {
        val categories = listOf(catFood, catSalary)
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 5000L, TransactionType.EXPENSE),
            buildTx("tx-2", catSalary.id, 20000L, TransactionType.INCOME),
        )

        // Yalnız Gider
        val (expenseSummary, expenseItems) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = TransactionType.EXPENSE,
            currency = Currency.TRY,
        )
        assertEquals(1, expenseItems.size)
        assertEquals("Yeme & İçme", expenseItems[0].category.name)
        assertEquals(1, expenseSummary.totalCategoriesCount)

        // Yalnız Gelir
        val (incomeSummary, incomeItems) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = TransactionType.INCOME,
            currency = Currency.TRY,
        )
        assertEquals(1, incomeItems.size)
        assertEquals("Maaş", incomeItems[0].category.name)
        assertEquals(1, incomeSummary.totalCategoriesCount)
    }

    @Test
    fun safeAdd_handlesNearLongMaxValue_andThrowsOverflow() {
        // Sınır civarında güvenli toplama
        val safeSum = CategoryAnalyticsCalculator.safeAdd(Long.MAX_VALUE - 100L, 50L)
        assertEquals(Long.MAX_VALUE - 50L, safeSum)

        // Taşma halinde ArithmeticException
        assertFailsWith<ArithmeticException> {
            CategoryAnalyticsCalculator.safeAdd(Long.MAX_VALUE - 50L, 51L)
        }

        // Negatif tutarlar reddedilir
        assertFailsWith<IllegalArgumentException> {
            CategoryAnalyticsCalculator.safeAdd(-1L, 100L)
        }
        assertFailsWith<IllegalArgumentException> {
            CategoryAnalyticsCalculator.safeAdd(100L, -1L)
        }
    }

    @Test
    fun calculateProportionBasisPoints_veryLargeNumeratorSmallDenominator() {
        // Çok büyük numerator ve küçük denominator Long taşması yapmadan hesaplanmalı
        val largeNumerator = 5_000_000_000_000L // 5 trilyon kuruş
        val smallDenominator = 2L
        val result = CategoryAnalyticsCalculator.calculateProportionBasisPoints(
            numerator = largeNumerator,
            denominator = smallDenominator,
            scale = 10_000L,
            roundHalfUp = true,
        )
        // (5_000_000_000_000 / 2) * 10_000 = 25_000_000_000_000_000
        assertEquals(25_000_000_000_000_000L, result)

        // Sıfır payda açık eylemi uygular
        val zeroDenom = CategoryAnalyticsCalculator.calculateProportionBasisPoints(
            numerator = 100L,
            denominator = 0L,
            onZeroDenominator = { -1L },
        )
        assertEquals(-1L, zeroDenom)
    }

    @Test
    fun calculateProportionBasisPoints_roundingOverflowBoundary() {
        // 1/3 -> 3333 bps (HALF_UP: 10_000 / 3 = 3333.333 -> 3333)
        assertEquals(3333L, CategoryAnalyticsCalculator.calculateProportionBasisPoints(1L, 3L))

        // 2/3 -> 6667 bps (HALF_UP: 20_000 / 3 = 6666.666 -> 6667)
        assertEquals(6667L, CategoryAnalyticsCalculator.calculateProportionBasisPoints(2L, 3L))

        // 1/2 -> 5000 bps (HALF_UP: tam yarı 5000)
        assertEquals(5000L, CategoryAnalyticsCalculator.calculateProportionBasisPoints(1L, 2L))

        // Çok büyük paydada yuvarlama eklemesi taşmamalı
        val hugeDenom = Long.MAX_VALUE / 2
        val rounded = CategoryAnalyticsCalculator.calculateProportionBasisPoints(
            numerator = 100L,
            denominator = hugeDenom,
            roundHalfUp = true,
        )
        assertEquals(0L, rounded)
    }

    @Test
    fun safeLongToInt_clampsAndDoesNotWrapOnLargeValues() {
        // Int sınırını aşan değerler sessizce wrap etmez, maxLimit'e clamp edilir
        val huge = Long.MAX_VALUE
        val clamped = CategoryAnalyticsCalculator.safeLongToInt(huge, maxLimit = 1_000_000)
        assertEquals(1_000_000, clamped)

        val intOverflow = Int.MAX_VALUE.toLong() + 500L
        val clampedInt = CategoryAnalyticsCalculator.safeLongToInt(intOverflow, maxLimit = Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, clampedInt)

        // Normal değerler doğrudan döner
        assertEquals(5000, CategoryAnalyticsCalculator.safeLongToInt(5000L, maxLimit = 10_000))
    }

    @Test
    fun calculateProportionBasisPoints_halfUp_boundaryCases() {
        val denom = 20_000_000L
        // 0.5 baz puan sınırı = 1000L / 20_000_000L
        // Sınırın hemen altı: 999L -> 0 bps
        val belowBoundary = CategoryAnalyticsCalculator.calculateProportionBasisPoints(999L, denom)
        assertEquals(0L, belowBoundary)

        // Sınırın tam eşiti: 1000L (tam 0.5 bps) -> HALF_UP ile 1 bps
        val atBoundary = CategoryAnalyticsCalculator.calculateProportionBasisPoints(1000L, denom)
        assertEquals(1L, atBoundary)

        // Sınırın hemen üstü: 1001L -> 1 bps
        val aboveBoundary = CategoryAnalyticsCalculator.calculateProportionBasisPoints(1001L, denom)
        assertEquals(1L, aboveBoundary)
    }

    @Test
    fun calculateProportionBasisPoints_nearLongMaxValue_exactCalculation() {
        // (Long.MAX_VALUE / 2) * 10_000 normalde 64-bit Long'da taşar.
        // 128-bit tamsayı algoritması taşma olmadan tam %50 (5000 bps) hesaplamalıdır.
        val halfMax = Long.MAX_VALUE / 2L
        val denom = Long.MAX_VALUE
        val resultHalf = CategoryAnalyticsCalculator.calculateProportionBasisPoints(halfMax, denom)
        assertEquals(5000L, resultHalf)

        // Long.MAX_VALUE / Long.MAX_VALUE -> %100 (10000 bps)
        val resultFull = CategoryAnalyticsCalculator.calculateProportionBasisPoints(Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(10_000L, resultFull)
    }

    @Test
    fun calculateTrend_previousZero_returnsNew() {
        val trend = CategoryAnalyticsCalculator.calculateTrend(
            categoryType = TransactionType.EXPENSE,
            currentMinor = 1500L,
            prevMinor = 0L,
        )
        assertEquals(CategoryTrend.New, trend)
    }

    @Test
    fun calculateShareBasisPoints_exactShares_0_50_100_andLargeAmounts() {
        // %0 pay
        assertEquals(0, CategoryAnalyticsCalculator.calculateShareBasisPoints(0L, 10_000L))

        // %50 pay
        assertEquals(5000, CategoryAnalyticsCalculator.calculateShareBasisPoints(5000L, 10_000L))

        // %100 pay
        assertEquals(10_000, CategoryAnalyticsCalculator.calculateShareBasisPoints(10_000L, 10_000L))

        // Sıfır toplam -> 0
        assertEquals(0, CategoryAnalyticsCalculator.calculateShareBasisPoints(100L, 0L))

        // Part > Total -> MAX_BASIS_POINTS ile sınırlanır (10_000)
        assertEquals(10_000, CategoryAnalyticsCalculator.calculateShareBasisPoints(20_000L, 10_000L))

        // Çok büyük tutar payı
        val largePart = 250_000_000_000L
        val largeTotal = 1_000_000_000_000L
        assertEquals(2500, CategoryAnalyticsCalculator.calculateShareBasisPoints(largePart, largeTotal))
    }

    @Test
    fun calculateTrend_clampsToMaxTrendBasisPoints_onExtremeIncrease() {
        // Aşırı artış (örn. 1 kuruştan 100 milyon kuruşa) UI sınırında (1_000_000 baz puan = %10.000) clamp edilmeli
        val extremeTrend = CategoryAnalyticsCalculator.calculateTrend(
            categoryType = TransactionType.EXPENSE,
            currentMinor = 100_000_000L,
            prevMinor = 1L,
        )
        assertTrue(extremeTrend is CategoryTrend.Changed)
        assertEquals(CategoryAnalyticsCalculator.MAX_TREND_BASIS_POINTS, extremeTrend.changeBasisPoints)
        assertEquals(TrendMovement.INCREASED, extremeTrend.movement)
        assertEquals(TrendSentiment.NEGATIVE, extremeTrend.sentiment)

        // Eşitlik durumu
        val equalTrend = CategoryAnalyticsCalculator.calculateTrend(TransactionType.EXPENSE, 5000L, 5000L)
        assertTrue(equalTrend is CategoryTrend.Changed)
        assertEquals(0, equalTrend.changeBasisPoints)
        assertEquals(TrendMovement.UNCHANGED, equalTrend.movement)
        assertEquals(TrendSentiment.NEUTRAL, equalTrend.sentiment)
    }

    @Test
    fun calculate_withLargeAmounts_keepsMiniBarsAndSharesWithinBounds() {
        val categories = listOf(catFood, catShopping)
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 50_000_000_000L, TransactionType.EXPENSE),
            buildTx("tx-2", catShopping.id, 25_000_000_000L, TransactionType.EXPENSE),
        )

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = TransactionType.EXPENSE,
            currency = Currency.TRY,
        )

        // En yüksek pay 0..10_000 aralığında
        assertTrue(summary.topCategoryShareBasisPoints in 0..10_000)
        // 50 milyar / 75 milyar = %66.67 = 6667 bps
        assertEquals(6667, summary.topCategoryShareBasisPoints)

        // Mini barlar 0..10_000 aralığında
        for (bar in summary.miniBarProportionsBasisPoints) {
            assertTrue(bar in 0..10_000, "Mini bar oranı 0..10_000 arasında olmalı fakat $bar")
        }
    }

    @Test
    fun calculate_failsClosed_whenTransactionTypeMismatchesCategoryType() {
        val categories = listOf(catFood) // EXPENSE
        // EXPENSE kategorisine INCOME işlem bağlanmış
        val invalidTx = listOf(
            buildTx("tx-bad", catFood.id, 1000L, TransactionType.INCOME),
        )

        assertFailsWith<IllegalArgumentException> {
            CategoryAnalyticsCalculator.calculate(
                categories = categories,
                currentTransactions = invalidTx,
                previousTransactions = emptyList(),
                selectedTypeFilter = null,
                currency = Currency.TRY,
            )
        }
    }

    @Test
    fun calculate_ignoresHistoricalOrDeletedCategoryFromSummaryTotal() {
        val categories = listOf(catFood) // Yalnız food kategorisi aktif
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 5000L, TransactionType.EXPENSE),
            // Silinmiş / kategori listesinde olmayan işlem
            buildTx("tx-deleted", EntityId("cat-deleted-999"), 9999L, TransactionType.EXPENSE),
        )

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = TransactionType.EXPENSE,
            currency = Currency.TRY,
        )

        // Liste yalnız catFood'u içerir
        assertEquals(1, items.size)
        assertEquals(5000L, items[0].currentPeriodAmount.amountMinor)

        // Özet toplamı ve payı silinmiş kategoriyi içermez (kapsam liste ile tam tutarlı)
        assertEquals("Yeme & İçme", summary.topCategoryName)
        assertEquals(10_000, summary.topCategoryShareBasisPoints) // 5000/5000 = %100
        assertEquals("50,00 ₺", summary.formattedTopCategoryAmount)
    }

    @Test
    fun calculate_incomeView_selectsTopIncomeCategory() {
        val categories = listOf(catSalary, catFood)
        val currentTxs = listOf(
            buildTx("tx-1", catFood.id, 5000L, TransactionType.EXPENSE),
            buildTx("tx-2", catSalary.id, 35000L, TransactionType.INCOME),
        )

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = currentTxs,
            previousTransactions = emptyList(),
            selectedTypeFilter = TransactionType.INCOME,
            currency = Currency.TRY,
        )

        assertEquals(1, items.size)
        assertEquals("Maaş", items[0].category.name)
        assertEquals(TransactionType.INCOME, summary.topCategoryType)
        assertEquals("Maaş", summary.topCategoryName)
        assertEquals("350,00 ₺", summary.formattedTopCategoryAmount)
        assertTrue(summary.insightText?.contains("en yüksek gelir") == true)
    }

    @Test
    fun calculate_whenZeroTransactions_returnsAllCategoriesWithZero() {
        val categories = listOf(catFood, catShopping)

        val (summary, items) = CategoryAnalyticsCalculator.calculate(
            categories = categories,
            currentTransactions = emptyList(),
            previousTransactions = emptyList(),
            selectedTypeFilter = null,
            currency = Currency.TRY,
        )

        assertEquals(2, items.size)
        assertEquals(0L, items[0].currentPeriodAmount.amountMinor)
        assertEquals(0L, items[1].currentPeriodAmount.amountMinor)
        assertEquals(CategoryTrend.None, items[0].trend)
        assertEquals(CategoryTrend.None, items[1].trend)
        assertNull(summary.topCategoryName)
        assertTrue(summary.miniBarProportionsBasisPoints.isEmpty())
    }
}
