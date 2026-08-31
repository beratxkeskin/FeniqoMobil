package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.domain.usecase.BudgetProgress
import com.feniqo.mobile.domain.usecase.BudgetProgressItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetDisplayModelMapperTest {

    private val testBudgetId = EntityId("b-100")
    private val testCategoryId = EntityId("c-200")
    private val testOwnerId = EntityId("user-1")
    private val testMonth = YearMonth("2026-08")
    private val testInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(1724932800000L)

    private val sampleCategory = Category(
        id = testCategoryId,
        ownerId = testOwnerId,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("cart"),
        isDefault = false,
        createdAt = testInstant,
    )

    private val sampleBudget = Budget(
        id = testBudgetId,
        ownerId = testOwnerId,
        workspaceId = null,
        categoryId = testCategoryId,
        month = testMonth,
        limit = Money(100_000, Currency.TRY),
        createdAt = testInstant,
    )

    @Test
    fun toDisplayModel_mapsSafeHealthCorrectly() {
        val item = BudgetProgressItem(
            progress = BudgetProgress(
                budget = sampleBudget,
                spent = Money(50_000, Currency.TRY),
                remaining = MoneyDelta(50_000, Currency.TRY),
                usageRate = RateBasisPoints(5_000), // %50
                health = BudgetHealth.SAFE,
            ),
            category = sampleCategory,
            excludedDifferentCurrencyTransactionCount = 0,
        )

        val display = BudgetDisplayModelMapper.toDisplayModel(item)

        assertEquals(testBudgetId, display.id)
        assertEquals(testCategoryId, display.categoryId)
        assertEquals("Market", display.categoryName)
        assertEquals("#10B981", display.categoryColorHex)
        assertEquals("cart", display.categoryIconKey)
        assertFalse(display.isCategoryMissing)
        assertEquals(testMonth, display.month)
        assertEquals("1.000,00 ₺", display.formattedLimit)
        assertEquals(100_000L, display.limitMinor)
        assertEquals("500,00 ₺", display.formattedSpent)
        assertEquals(50_000L, display.spentMinor)
        assertEquals("500,00 ₺", display.formattedRemaining)
        assertEquals(50_000L, display.remainingMinor)
        assertFalse(display.isRemainingNegative)
        assertEquals(5_000, display.usageRateBasisPoints)
        assertEquals("%50,00", display.formattedUsageRate)
        assertEquals(0.5f, display.usageProgressFraction, 0.001f)
        assertEquals(BudgetHealth.SAFE, display.health)
        assertEquals(0, display.excludedDifferentCurrencyTransactionCount)
        assertFalse(display.hasExcludedTransactions)
    }

    @Test
    fun toDisplayModel_mapsWarningHealthCorrectly() {
        val item = BudgetProgressItem(
            progress = BudgetProgress(
                budget = sampleBudget,
                spent = Money(85_000, Currency.TRY),
                remaining = MoneyDelta(15_000, Currency.TRY),
                usageRate = RateBasisPoints(8_500), // %85
                health = BudgetHealth.WARNING,
            ),
            category = sampleCategory,
            excludedDifferentCurrencyTransactionCount = 0,
        )

        val display = BudgetDisplayModelMapper.toDisplayModel(item)

        assertEquals(BudgetHealth.WARNING, display.health)
        assertEquals("%85,00", display.formattedUsageRate)
        assertFalse(display.isRemainingNegative)
    }

    @Test
    fun toDisplayModel_mapsExceededHealthAndNegativeRemainingCorrectly() {
        val item = BudgetProgressItem(
            progress = BudgetProgress(
                budget = sampleBudget,
                spent = Money(130_000, Currency.TRY),
                remaining = MoneyDelta(-30_000, Currency.TRY),
                usageRate = RateBasisPoints(13_000), // %130
                health = BudgetHealth.EXCEEDED,
            ),
            category = sampleCategory,
            excludedDifferentCurrencyTransactionCount = 2,
        )

        val display = BudgetDisplayModelMapper.toDisplayModel(item)

        assertEquals(BudgetHealth.EXCEEDED, display.health)
        assertTrue(display.isRemainingNegative)
        assertEquals("-300,00 ₺", display.formattedRemaining)
        assertEquals(-30_000L, display.remainingMinor)
        assertEquals(13_000, display.usageRateBasisPoints)
        assertEquals("%130,00", display.formattedUsageRate)
        assertEquals(1.0f, display.usageProgressFraction, 0.001f)
        assertEquals(2, display.excludedDifferentCurrencyTransactionCount)
        assertTrue(display.hasExcludedTransactions)
    }

    @Test
    fun toDisplayModel_whenCategoryIsNull_usesFallbackCategoryName() {
        val item = BudgetProgressItem(
            progress = BudgetProgress(
                budget = sampleBudget,
                spent = Money(0, Currency.TRY),
                remaining = MoneyDelta(100_000, Currency.TRY),
                usageRate = RateBasisPoints(0),
                health = BudgetHealth.SAFE,
            ),
            category = null,
            excludedDifferentCurrencyTransactionCount = 0,
        )

        val display = BudgetDisplayModelMapper.toDisplayModel(item)

        assertEquals("Kategori Yok", display.categoryName)
        assertNull(display.categoryColorHex)
        assertNull(display.categoryIconKey)
        assertTrue(display.isCategoryMissing)
    }
}
