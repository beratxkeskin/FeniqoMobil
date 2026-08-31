package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money

data class InstallmentAllocation(
    val number: Int,
    val total: Int,
    val amount: Money,
    val transactionDate: LocalDate,
)

sealed interface InstallmentPlanResult {
    data class Success(val allocations: List<InstallmentAllocation>) : InstallmentPlanResult
    data class Invalid(val error: InstallmentPlanError) : InstallmentPlanResult
}

enum class InstallmentPlanError {
    COUNT_OUT_OF_RANGE,
    AMOUNT_MUST_BE_POSITIVE,
    COUNT_EXCEEDS_AMOUNT,
}

object InstallmentPlanCalculator {
    const val MIN_INSTALLMENT_COUNT = 2
    const val MAX_INSTALLMENT_COUNT = 60

    fun calculate(
        totalAmount: Money,
        installmentCount: Int,
        anchorDate: LocalDate,
    ): InstallmentPlanResult {
        if (installmentCount !in MIN_INSTALLMENT_COUNT..MAX_INSTALLMENT_COUNT) {
            return InstallmentPlanResult.Invalid(InstallmentPlanError.COUNT_OUT_OF_RANGE)
        }
        if (totalAmount.amountMinor <= 0L) {
            return InstallmentPlanResult.Invalid(InstallmentPlanError.AMOUNT_MUST_BE_POSITIVE)
        }
        if (installmentCount > totalAmount.amountMinor) {
            return InstallmentPlanResult.Invalid(InstallmentPlanError.COUNT_EXCEEDS_AMOUNT)
        }

        val baseMinor = totalAmount.amountMinor / installmentCount
        val remainder = totalAmount.amountMinor % installmentCount

        val allocations = ArrayList<InstallmentAllocation>(installmentCount)
        for (i in 0 until installmentCount) {
            val number = i + 1
            val amountMinor = if (number == installmentCount) {
                baseMinor + remainder
            } else {
                baseMinor
            }
            val date = calculateDate(anchorDate, i)
            allocations.add(
                InstallmentAllocation(
                    number = number,
                    total = installmentCount,
                    amount = Money(amountMinor, totalAmount.currency),
                    transactionDate = date,
                ),
            )
        }
        return InstallmentPlanResult.Success(allocations)
    }

    private fun calculateDate(anchorDate: LocalDate, monthOffset: Int): LocalDate {
        val totalMonths = anchorDate.year * 12 + (anchorDate.monthNumber - 1) + monthOffset
        val targetYear = totalMonths / 12
        val targetMonth = (totalMonths % 12) + 1
        val maxDays = daysInMonth(targetYear, targetMonth)
        val targetDay = minOf(anchorDate.dayOfMonth, maxDays)
        return LocalDate(targetYear, targetMonth, targetDay)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
