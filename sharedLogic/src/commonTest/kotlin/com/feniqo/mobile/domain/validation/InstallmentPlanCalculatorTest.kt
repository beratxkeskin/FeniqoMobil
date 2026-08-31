package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstallmentPlanCalculatorTest {

    @Test
    fun calculate_with120000MinorAnd3Count_allocatesEqualAmounts() {
        val total = Money(120_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 3, LocalDate(2026, 8, 21))

        val success = assertIs<InstallmentPlanResult.Success>(result)
        assertEquals(3, success.allocations.size)
        assertEquals(40_000L, success.allocations[0].amount.amountMinor)
        assertEquals(40_000L, success.allocations[1].amount.amountMinor)
        assertEquals(40_000L, success.allocations[2].amount.amountMinor)
    }

    @Test
    fun calculate_with1000MinorAnd3Count_allocatesRemainderToLastInstallment() {
        val total = Money(1_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 3, LocalDate(2026, 8, 21))

        val success = assertIs<InstallmentPlanResult.Success>(result)
        assertEquals(3, success.allocations.size)
        assertEquals(333L, success.allocations[0].amount.amountMinor)
        assertEquals(333L, success.allocations[1].amount.amountMinor)
        assertEquals(334L, success.allocations[2].amount.amountMinor) // Kuruş artığı son takside eklenir
    }

    @Test
    fun calculate_with1001MinorAnd3Count_allocatesRemainderToLastInstallment() {
        val total = Money(1_001L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 3, LocalDate(2026, 8, 21))

        val success = assertIs<InstallmentPlanResult.Success>(result)
        assertEquals(3, success.allocations.size)
        assertEquals(333L, success.allocations[0].amount.amountMinor)
        assertEquals(333L, success.allocations[1].amount.amountMinor)
        assertEquals(335L, success.allocations[2].amount.amountMinor) // 333 + 2 = 335
    }

    @Test
    fun calculate_allocationsSumExactlyMatchesTotalAmount() {
        val testCases = listOf(
            Pair(Money(9999L, Currency.TRY), 7),
            Pair(Money(123456L, Currency.USD), 13),
            Pair(Money(5000000L, Currency.EUR), 60),
            Pair(Money(10L, Currency.TRY), 2),
        )

        for ((total, count) in testCases) {
            val result = InstallmentPlanCalculator.calculate(total, count, LocalDate(2026, 1, 15))
            val success = assertIs<InstallmentPlanResult.Success>(result)
            val sum = success.allocations.sumOf { it.amount.amountMinor }
            assertEquals(total.amountMinor, sum)
        }
    }

    @Test
    fun calculate_preservesCurrencies() {
        for (currency in listOf(Currency.TRY, Currency.USD, Currency.EUR)) {
            val total = Money(10_000L, currency)
            val result = InstallmentPlanCalculator.calculate(total, 4, LocalDate(2026, 8, 21))
            val success = assertIs<InstallmentPlanResult.Success>(result)
            assertTrue(success.allocations.all { it.amount.currency == currency })
        }
    }

    @Test
    fun calculate_whenCountIs1OrLess_returnsCountOutOfRangeError() {
        val total = Money(10_000L, Currency.TRY)
        val result0 = InstallmentPlanCalculator.calculate(total, 0, LocalDate(2026, 8, 21))
        val invalid0 = assertIs<InstallmentPlanResult.Invalid>(result0)
        assertEquals(InstallmentPlanError.COUNT_OUT_OF_RANGE, invalid0.error)

        val result1 = InstallmentPlanCalculator.calculate(total, 1, LocalDate(2026, 8, 21))
        val invalid1 = assertIs<InstallmentPlanResult.Invalid>(result1)
        assertEquals(InstallmentPlanError.COUNT_OUT_OF_RANGE, invalid1.error)
    }

    @Test
    fun calculate_whenCountIs61OrMore_returnsCountOutOfRangeError() {
        val total = Money(100_000L, Currency.TRY)
        val result61 = InstallmentPlanCalculator.calculate(total, 61, LocalDate(2026, 8, 21))
        val invalid61 = assertIs<InstallmentPlanResult.Invalid>(result61)
        assertEquals(InstallmentPlanError.COUNT_OUT_OF_RANGE, invalid61.error)

        val result100 = InstallmentPlanCalculator.calculate(total, 100, LocalDate(2026, 8, 21))
        val invalid100 = assertIs<InstallmentPlanResult.Invalid>(result100)
        assertEquals(InstallmentPlanError.COUNT_OUT_OF_RANGE, invalid100.error)
    }

    @Test
    fun calculate_acceptsBoundaryCounts2And60() {
        val total = Money(120_000L, Currency.TRY)
        val result2 = InstallmentPlanCalculator.calculate(total, 2, LocalDate(2026, 8, 21))
        val success2 = assertIs<InstallmentPlanResult.Success>(result2)
        assertEquals(2, success2.allocations.size)

        val result60 = InstallmentPlanCalculator.calculate(total, 60, LocalDate(2026, 8, 21))
        val success60 = assertIs<InstallmentPlanResult.Success>(result60)
        assertEquals(60, success60.allocations.size)
    }

    @Test
    fun calculate_whenCountExceedsTotalAmountMinor_returnsCountExceedsAmountError() {
        val total = Money(5L, Currency.TRY) // 5 kuruş
        val result = InstallmentPlanCalculator.calculate(total, 6, LocalDate(2026, 8, 21)) // 6 taksit
        val invalid = assertIs<InstallmentPlanResult.Invalid>(result)
        assertEquals(InstallmentPlanError.COUNT_EXCEEDS_AMOUNT, invalid.error)
    }

    @Test
    fun calculate_whenTotalAmountIsZero_returnsAmountMustBePositiveError() {
        val resultZero = InstallmentPlanCalculator.calculate(Money(0L, Currency.TRY), 3, LocalDate(2026, 8, 21))
        val invalidZero = assertIs<InstallmentPlanResult.Invalid>(resultZero)
        assertEquals(InstallmentPlanError.AMOUNT_MUST_BE_POSITIVE, invalidZero.error)
    }

    @Test
    fun calculate_onJanuary31InStandardYear_calculatesCorrectAnchorDates() {
        val total = Money(50_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 5, LocalDate(2026, 1, 31))
        val success = assertIs<InstallmentPlanResult.Success>(result)

        assertEquals(LocalDate(2026, 1, 31), success.allocations[0].transactionDate)
        assertEquals(LocalDate(2026, 2, 28), success.allocations[1].transactionDate)
        assertEquals(LocalDate(2026, 3, 31), success.allocations[2].transactionDate) // Tekrar 31 olur
        assertEquals(LocalDate(2026, 4, 30), success.allocations[3].transactionDate)
        assertEquals(LocalDate(2026, 5, 31), success.allocations[4].transactionDate)
    }

    @Test
    fun calculate_onJanuary31InLeapYear_calculatesCorrectAnchorDates() {
        val total = Money(40_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 4, LocalDate(2028, 1, 31))
        val success = assertIs<InstallmentPlanResult.Success>(result)

        assertEquals(LocalDate(2028, 1, 31), success.allocations[0].transactionDate)
        assertEquals(LocalDate(2028, 2, 29), success.allocations[1].transactionDate) // Artık yılda 29
        assertEquals(LocalDate(2028, 3, 31), success.allocations[2].transactionDate)
        assertEquals(LocalDate(2028, 4, 30), success.allocations[3].transactionDate)
    }

    @Test
    fun calculate_onMarch31_calculatesCorrectNextMonths() {
        val total = Money(30_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 3, LocalDate(2026, 3, 31))
        val success = assertIs<InstallmentPlanResult.Success>(result)

        assertEquals(LocalDate(2026, 3, 31), success.allocations[0].transactionDate)
        assertEquals(LocalDate(2026, 4, 30), success.allocations[1].transactionDate)
        assertEquals(LocalDate(2026, 5, 31), success.allocations[2].transactionDate)
    }

    @Test
    fun calculate_crossesYearBoundaryCorrectly() {
        val total = Money(30_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 3, LocalDate(2026, 12, 15))
        val success = assertIs<InstallmentPlanResult.Success>(result)

        assertEquals(LocalDate(2026, 12, 15), success.allocations[0].transactionDate)
        assertEquals(LocalDate(2027, 1, 15), success.allocations[1].transactionDate)
        assertEquals(LocalDate(2027, 2, 15), success.allocations[2].transactionDate)
    }

    @Test
    fun calculate_maintainsStableAllocationNumberAndTotal() {
        val total = Money(30_000L, Currency.TRY)
        val result = InstallmentPlanCalculator.calculate(total, 5, LocalDate(2026, 5, 10))
        val success = assertIs<InstallmentPlanResult.Success>(result)

        assertEquals(5, success.allocations.size)
        for (i in 0 until 5) {
            assertEquals(i + 1, success.allocations[i].number)
            assertEquals(5, success.allocations[i].total)
        }
    }
}
