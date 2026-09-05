package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.DebtSnowballItemPlan
import com.feniqo.mobile.domain.validation.DebtSnowballMonthlyAllocation
import com.feniqo.mobile.domain.validation.DebtSnowballPlan
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtSnowballPlanModelsTest {

    @Test
    fun toDisplayUiModel_correctlyFormatsAndMapsAllFields() {
        val debtId = EntityId("d-1")
        val debt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi Kartı Borcu",
            amount = Money(5000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val debtsMap = mapOf(debtId to debt)

        val domainPlan = DebtSnowballPlan(
            currency = Currency.TRY,
            monthlyPaymentBudget = Money(2000_00L, Currency.TRY),
            totalMonths = 3,
            totalDebtAmount = Money(5000_00L, Currency.TRY),
            debtPlans = listOf(
                DebtSnowballItemPlan(
                    debtId = debtId,
                    debtTitle = "Kredi Kartı Borcu",
                    initialRemainingAmount = Money(5000_00L, Currency.TRY),
                    totalAllocatedAmount = Money(5000_00L, Currency.TRY),
                    settledInMonth = 3,
                    orderIndex = 1,
                ),
            ),
            monthlyAllocations = listOf(
                DebtSnowballMonthlyAllocation(
                    month = 1,
                    debtId = debtId,
                    allocatedAmount = Money(2000_00L, Currency.TRY),
                    remainingBalanceAfterPayment = Money(3000_00L, Currency.TRY),
                ),
                DebtSnowballMonthlyAllocation(
                    month = 2,
                    debtId = debtId,
                    allocatedAmount = Money(2000_00L, Currency.TRY),
                    remainingBalanceAfterPayment = Money(1000_00L, Currency.TRY),
                ),
                DebtSnowballMonthlyAllocation(
                    month = 3,
                    debtId = debtId,
                    allocatedAmount = Money(1000_00L, Currency.TRY),
                    remainingBalanceAfterPayment = Money(0L, Currency.TRY),
                ),
            ),
        )

        val displayModel = domainPlan.toDisplayUiModel(debtsMap)

        assertEquals(Currency.TRY, displayModel.currency)
        assertEquals(3, displayModel.totalMonths)
        assertTrue(displayModel.totalDebtFormatted.contains("5.000"))
        assertTrue(displayModel.monthlyBudgetFormatted.contains("2.000"))

        assertEquals(1, displayModel.debtItems.size)
        val item = displayModel.debtItems[0]
        assertEquals("Kredi Kartı Borcu", item.debtTitle)
        assertEquals(3, item.settledInMonth)
        assertEquals(1, item.orderIndex)

        assertEquals(3, displayModel.monthlyAllocations.size)
        assertEquals(1, displayModel.monthlyAllocations[0].month)
        assertEquals("Kredi Kartı Borcu", displayModel.monthlyAllocations[0].debtTitle)
        assertTrue(displayModel.monthlyAllocations[0].allocatedFormatted.contains("2.000"))
        assertTrue(displayModel.monthlyAllocations[0].remainingBalanceFormatted.contains("3.000"))
    }

    @Test
    fun debtSnowballPlanUiState_hasSensibleDefaults() {
        val defaultState = DebtSnowballPlanUiState()
        assertEquals(listOf(Currency.TRY, Currency.USD, Currency.EUR), defaultState.availableCurrencies)
        assertEquals(Currency.TRY, defaultState.selectedCurrency)
        assertEquals("", defaultState.budgetInput)
        assertNull(defaultState.budgetError)
        assertNull(defaultState.observationError)
        assertTrue(defaultState.isLoadingDebts)
        assertEquals(0, defaultState.eligibleDebtsCount)
        assertNull(defaultState.plan)
    }
}
