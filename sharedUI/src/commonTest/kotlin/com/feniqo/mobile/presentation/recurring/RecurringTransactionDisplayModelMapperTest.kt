package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTransactionDisplayModelMapperTest {

    @Test
    fun map_ordersActiveFirstThenByNextOccurrenceAscendingAndId() {
        val category = sampleCategory("cat-1", "Kira")

        // 4 rules:
        // 1. active, next: 2026-08-15, id: rec-2
        // 2. active, next: 2026-08-01, id: rec-1
        // 3. active, next: null (expired), id: rec-3
        // 4. paused, next: 2026-08-05, id: rec-4
        val rec1 = sampleRecurring(
            id = "rec-1",
            startDate = LocalDate(2026, 8, 1),
            isActive = true,
        )
        val rec2 = sampleRecurring(
            id = "rec-2",
            startDate = LocalDate(2026, 8, 15),
            isActive = true,
        )
        val rec3 = sampleRecurring(
            id = "rec-3",
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 1),
            lastGeneratedDate = LocalDate(2026, 8, 1),
            isActive = true,
        )
        val rec4 = sampleRecurring(
            id = "rec-4",
            startDate = LocalDate(2026, 8, 5),
            isActive = false,
        )

        val result = RecurringTransactionDisplayModelMapper.map(
            recurringTransactions = listOf(rec4, rec2, rec3, rec1),
            categories = listOf(category),
        )

        assertEquals(4, result.size)
        // Active with nearest next date first: rec-1 (08-01), then rec-2 (08-15), then rec-3 (null next date), then rec-4 (paused)
        assertEquals("rec-1", result[0].id.value)
        assertEquals(LocalDate(2026, 8, 1), result[0].nextOccurrenceDate)
        assertFalse(result[0].isPaused)

        assertEquals("rec-2", result[1].id.value)
        assertEquals(LocalDate(2026, 8, 15), result[1].nextOccurrenceDate)
        assertFalse(result[1].isPaused)

        assertEquals("rec-3", result[2].id.value)
        assertNull(result[2].nextOccurrenceDate)
        assertFalse(result[2].isPaused)

        assertEquals("rec-4", result[3].id.value)
        assertEquals(LocalDate(2026, 8, 5), result[3].nextOccurrenceDate)
        assertTrue(result[3].isPaused)
    }

    @Test
    fun map_formatsRecurrenceSummariesCorrectly() {
        val category = sampleCategory("cat-1", "Abonelik")

        val monthlySingle = sampleRecurring(
            id = "rec-m1",
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
        )
        val monthlyMultiple = sampleRecurring(
            id = "rec-m3",
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 3,
        )
        val dailySingle = sampleRecurring(
            id = "rec-d1",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
        )
        val dailyMultiple = sampleRecurring(
            id = "rec-d5",
            frequency = RecurrenceFrequency.DAILY,
            interval = 5,
        )
        val weeklySingle = sampleRecurring(
            id = "rec-w1",
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
        )
        val weeklyMultiple = sampleRecurring(
            id = "rec-w2",
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
        )
        val yearlySingle = sampleRecurring(
            id = "rec-y1",
            frequency = RecurrenceFrequency.YEARLY,
            interval = 1,
        )
        val yearlyMultiple = sampleRecurring(
            id = "rec-y2",
            frequency = RecurrenceFrequency.YEARLY,
            interval = 2,
        )

        val mappedM1 = RecurringTransactionDisplayModelMapper.mapItem(monthlySingle, category)
        val mappedM3 = RecurringTransactionDisplayModelMapper.mapItem(monthlyMultiple, category)
        val mappedD1 = RecurringTransactionDisplayModelMapper.mapItem(dailySingle, category)
        val mappedD5 = RecurringTransactionDisplayModelMapper.mapItem(dailyMultiple, category)
        val mappedW1 = RecurringTransactionDisplayModelMapper.mapItem(weeklySingle, category)
        val mappedW2 = RecurringTransactionDisplayModelMapper.mapItem(weeklyMultiple, category)
        val mappedY1 = RecurringTransactionDisplayModelMapper.mapItem(yearlySingle, category)
        val mappedY2 = RecurringTransactionDisplayModelMapper.mapItem(yearlyMultiple, category)

        assertEquals("Her ay", mappedM1.formattedFrequency)
        assertEquals("Her 3 ayda bir", mappedM3.formattedFrequency)
        assertEquals("Her gün", mappedD1.formattedFrequency)
        assertEquals("Her 5 günde bir", mappedD5.formattedFrequency)
        assertEquals("Her hafta", mappedW1.formattedFrequency)
        assertEquals("Her 2 haftada bir", mappedW2.formattedFrequency)
        assertEquals("Her yıl", mappedY1.formattedFrequency)
        assertEquals("Her 2 yılda bir", mappedY2.formattedFrequency)
    }

    @Test
    fun map_handlesEndDatePresentAndAbsent() {
        val category = sampleCategory("cat-1", "Kira")

        val withEndDate = sampleRecurring(
            id = "rec-end",
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31),
        )
        val withoutEndDate = sampleRecurring(
            id = "rec-no-end",
            startDate = LocalDate(2026, 1, 1),
            endDate = null,
        )

        val mappedWithEnd = RecurringTransactionDisplayModelMapper.mapItem(withEndDate, category)
        val mappedWithoutEnd = RecurringTransactionDisplayModelMapper.mapItem(withoutEndDate, category)

        assertEquals(LocalDate(2026, 1, 1), mappedWithEnd.startDate)
        assertEquals("1 Ocak 2026", mappedWithEnd.formattedStartDate)
        assertEquals(LocalDate(2026, 12, 31), mappedWithEnd.endDate)
        assertEquals("31 Aralık 2026", mappedWithEnd.formattedEndDate)

        assertEquals(LocalDate(2026, 1, 1), mappedWithoutEnd.startDate)
        assertEquals("1 Ocak 2026", mappedWithoutEnd.formattedStartDate)
        assertNull(mappedWithoutEnd.endDate)
        assertNull(mappedWithoutEnd.formattedEndDate)
    }

    @Test
    fun map_handlesLastGeneratedDateNullAndNonNull() {
        val category = sampleCategory("cat-1", "Fatura")

        val neverGenerated = sampleRecurring(
            id = "rec-never",
            startDate = LocalDate(2026, 8, 1),
            lastGeneratedDate = null,
        )
        val alreadyGenerated = sampleRecurring(
            id = "rec-gen",
            startDate = LocalDate(2026, 8, 1),
            lastGeneratedDate = LocalDate(2026, 8, 1),
        )

        val mappedNever = RecurringTransactionDisplayModelMapper.mapItem(neverGenerated, category)
        val mappedGen = RecurringTransactionDisplayModelMapper.mapItem(alreadyGenerated, category)

        assertTrue(mappedNever.isNeverGenerated)
        assertNull(mappedNever.lastGeneratedDate)
        assertNull(mappedNever.formattedLastGeneratedDate)

        assertFalse(mappedGen.isNeverGenerated)
        assertEquals(LocalDate(2026, 8, 1), mappedGen.lastGeneratedDate)
        assertEquals("1 Ağustos 2026", mappedGen.formattedLastGeneratedDate)
    }

    @Test
    fun map_handlesMissingCategoryFallbackSafely() {
        val recurring = sampleRecurring(
            id = "rec-missing-cat",
            categoryId = "cat-unknown",
        )

        val result = RecurringTransactionDisplayModelMapper.map(
            recurringTransactions = listOf(recurring),
            categories = emptyList(),
        )

        assertEquals(1, result.size)
        val item = result.first()
        assertTrue(item.isCategoryMissing)
        assertEquals("Bilinmeyen Kategori", item.categoryName)
        assertNull(item.categoryColorHex)
        assertNull(item.categoryIconKey)
    }

    @Test
    fun map_formatsNonTryCurrenciesCorrectly() {
        val category = sampleCategory("cat-1", "SaaS")

        val usdRecurring = sampleRecurring(
            id = "rec-usd",
            amountMinor = 1500L,
            currency = Currency.USD,
            type = TransactionType.EXPENSE,
        )
        val eurRecurring = sampleRecurring(
            id = "rec-eur",
            amountMinor = 2550L,
            currency = Currency.EUR,
            type = TransactionType.INCOME,
        )

        val mappedUsd = RecurringTransactionDisplayModelMapper.mapItem(usdRecurring, category)
        val mappedEur = RecurringTransactionDisplayModelMapper.mapItem(eurRecurring, category)

        assertEquals(Currency.USD, mappedUsd.currency)
        assertEquals("-15,00 $", mappedUsd.formattedAmount)

        assertEquals(Currency.EUR, mappedEur.currency)
        assertEquals("+25,50 €", mappedEur.formattedAmount)
    }

    @Test
    fun map_invalidRecurrenceState_rethrowsExceptionAndDoesNotMaskAsNull() {
        val category = sampleCategory("cat-1", "Abonelik")

        // Weekly interval 1 starting on 2026-08-01, but lastGeneratedDate is 2026-08-02 (1 day off, not 7 days)
        val invalidRecurring = sampleRecurring(
            id = "rec-invalid",
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            lastGeneratedDate = LocalDate(2026, 8, 2),
        )

        assertFailsWith<IllegalArgumentException> {
            RecurringTransactionDisplayModelMapper.mapItem(invalidRecurring, category)
        }

        assertFailsWith<IllegalArgumentException> {
            RecurringTransactionDisplayModelMapper.map(
                recurringTransactions = listOf(invalidRecurring),
                categories = listOf(category),
            )
        }
    }

    private fun sampleCategory(id: String, name: String) = Category(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("briefcase"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun sampleRecurring(
        id: String,
        categoryId: String = "cat-1",
        amountMinor: Long = 50000L,
        currency: Currency = Currency.TRY,
        type: TransactionType = TransactionType.EXPENSE,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate(2026, 8, 1),
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
        isActive: Boolean = true,
    ): RecurringTransaction = RecurringTransaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Açıklama",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        rule = RecurrenceRule(
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = endDate,
        ),
        lastGeneratedDate = lastGeneratedDate,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
