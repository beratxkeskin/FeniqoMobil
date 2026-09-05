package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebtSnowballPlannerTest {

    private fun createDebt(
        id: String,
        amountMinor: Long,
        type: DebtType = DebtType.DEBT,
        dueDate: LocalDate = LocalDate(2026, 12, 31),
        status: DebtStatus = DebtStatus.OPEN,
        currency: Currency = Currency.TRY,
    ): Debt {
        return Debt(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            title = "Borç $id",
            amount = Money(amountMinor, currency),
            type = type,
            dueDate = dueDate,
            status = status,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
    }

    private fun createPayment(
        id: String,
        debtId: String,
        amountMinor: Long,
        currency: Currency = Currency.TRY,
        paidOn: LocalDate = LocalDate(2026, 9, 1),
    ): DebtPayment {
        return DebtPayment(
            id = EntityId(id),
            debtId = EntityId(debtId),
            amount = Money(amountMinor, currency),
            paidOn = paidOn,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
    }

    @Test
    fun calculate_singleDebt_simulatesCorrectMonthsAndAllocations() {
        val debt = createDebt(id = "d-1", amountMinor = 5000_00L) // 5,000 TRY
        val budget = Money(2000_00L, Currency.TRY) // 2,000 TRY / month

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(debt),
            payments = emptyList(),
            monthlyPaymentBudget = budget,
        )

        assertEquals(Currency.TRY, plan.currency)
        assertEquals(3, plan.totalMonths)
        assertEquals(Money(5000_00L, Currency.TRY), plan.totalDebtAmount)
        assertEquals(1, plan.debtPlans.size)

        val itemPlan = plan.debtPlans[0]
        assertEquals(EntityId("d-1"), itemPlan.debtId)
        assertEquals(3, itemPlan.settledInMonth)
        assertEquals(Money(5000_00L, Currency.TRY), itemPlan.totalAllocatedAmount)

        // Aylık dağılım: Ay 1 -> 2000 (kalan 3000), Ay 2 -> 2000 (kalan 1000), Ay 3 -> 1000 (kalan 0)
        assertEquals(3, plan.monthlyAllocations.size)
        assertEquals(Money(2000_00L, Currency.TRY), plan.monthlyAllocations[0].allocatedAmount)
        assertEquals(Money(3000_00L, Currency.TRY), plan.monthlyAllocations[0].remainingBalanceAfterPayment)

        assertEquals(Money(2000_00L, Currency.TRY), plan.monthlyAllocations[1].allocatedAmount)
        assertEquals(Money(1000_00L, Currency.TRY), plan.monthlyAllocations[1].remainingBalanceAfterPayment)

        assertEquals(Money(1000_00L, Currency.TRY), plan.monthlyAllocations[2].allocatedAmount)
        assertEquals(Money(0L, Currency.TRY), plan.monthlyAllocations[2].remainingBalanceAfterPayment)
    }

    @Test
    fun calculate_rolloverWithinSameMonth_transfersSurplusBudgetToNextDebt() {
        // Debt A: 500 TRY, Debt B: 1500 TRY. Budget: 1000 TRY / month
        val debtA = createDebt(id = "d-a", amountMinor = 500_00L)
        val debtB = createDebt(id = "d-b", amountMinor = 1500_00L)
        val budget = Money(1000_00L, Currency.TRY)

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(debtA, debtB),
            payments = emptyList(),
            monthlyPaymentBudget = budget,
        )

        assertEquals(2, plan.totalMonths)
        assertEquals(Money(2000_00L, Currency.TRY), plan.totalDebtAmount)
        assertEquals(2, plan.debtPlans.size)

        // Sıralama: Debt A (500) önce, Debt B (1500) sonra
        assertEquals(EntityId("d-a"), plan.debtPlans[0].debtId)
        assertEquals(1, plan.debtPlans[0].settledInMonth)
        assertEquals(1, plan.debtPlans[0].orderIndex)

        assertEquals(EntityId("d-b"), plan.debtPlans[1].debtId)
        assertEquals(2, plan.debtPlans[1].settledInMonth)
        assertEquals(2, plan.debtPlans[1].orderIndex)

        // Dağılım:
        // Ay 1: Debt A'ya 500 (kalan 0, kapandı), aynı ayda kalan 500 Debt B'ye aktarılır (kalan 1000)
        // Ay 2: Debt B'ye 1000 (kalan 0, kapandı)
        assertEquals(3, plan.monthlyAllocations.size)

        val alloc1 = plan.monthlyAllocations[0]
        assertEquals(1, alloc1.month)
        assertEquals(EntityId("d-a"), alloc1.debtId)
        assertEquals(Money(500_00L, Currency.TRY), alloc1.allocatedAmount)
        assertEquals(Money(0L, Currency.TRY), alloc1.remainingBalanceAfterPayment)

        val alloc2 = plan.monthlyAllocations[1]
        assertEquals(1, alloc2.month)
        assertEquals(EntityId("d-b"), alloc2.debtId)
        assertEquals(Money(500_00L, Currency.TRY), alloc2.allocatedAmount)
        assertEquals(Money(1000_00L, Currency.TRY), alloc2.remainingBalanceAfterPayment)

        val alloc3 = plan.monthlyAllocations[2]
        assertEquals(2, alloc3.month)
        assertEquals(EntityId("d-b"), alloc3.debtId)
        assertEquals(Money(1000_00L, Currency.TRY), alloc3.allocatedAmount)
        assertEquals(Money(0L, Currency.TRY), alloc3.remainingBalanceAfterPayment)
    }

    @Test
    fun calculate_snowballSorting_prioritizesSmallestBalanceThenDueDateThenId() {
        val debt1 = createDebt(id = "d-c", amountMinor = 1000_00L, dueDate = LocalDate(2026, 10, 1))
        val debt2 = createDebt(id = "d-a", amountMinor = 1000_00L, dueDate = LocalDate(2026, 9, 1))
        val debt3 = createDebt(id = "d-b", amountMinor = 1000_00L, dueDate = LocalDate(2026, 9, 1))
        val debt4 = createDebt(id = "d-small", amountMinor = 500_00L, dueDate = LocalDate(2026, 12, 1))

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(debt1, debt2, debt3, debt4),
            payments = emptyList(),
            monthlyPaymentBudget = Money(10000_00L, Currency.TRY),
        )

        // Beklenen sıralama:
        // 1. d-small (bakiye 500 < 1000)
        // 2. d-a (bakiye 1000, vade 2026-09-01, id "d-a" < "d-b")
        // 3. d-b (bakiye 1000, vade 2026-09-01, id "d-b")
        // 4. d-c (bakiye 1000, vade 2026-10-01)
        assertEquals(4, plan.debtPlans.size)
        assertEquals(EntityId("d-small"), plan.debtPlans[0].debtId)
        assertEquals(EntityId("d-a"), plan.debtPlans[1].debtId)
        assertEquals(EntityId("d-b"), plan.debtPlans[2].debtId)
        assertEquals(EntityId("d-c"), plan.debtPlans[3].debtId)
    }

    @Test
    fun calculate_existingPayments_calculatesRemainingBalanceAccurately() {
        // Debt A: anapara 10,000 TRY, 7,000 TRY ödenmiş -> kalan 3,000 TRY
        // Debt B: anapara 5,000 TRY, 0 ödenmiş -> kalan 5,000 TRY
        val debtA = createDebt(id = "d-a", amountMinor = 10000_00L)
        val debtB = createDebt(id = "d-b", amountMinor = 5000_00L)
        val paymentA = createPayment(id = "p-1", debtId = "d-a", amountMinor = 7000_00L)

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(debtA, debtB),
            payments = listOf(paymentA),
            monthlyPaymentBudget = Money(3000_00L, Currency.TRY),
        )

        // Kalan bakiyelere göre Debt A (3000) önce gelir, Debt B (5000) sonra gelir
        assertEquals(2, plan.debtPlans.size)
        assertEquals(EntityId("d-a"), plan.debtPlans[0].debtId)
        assertEquals(Money(3000_00L, Currency.TRY), plan.debtPlans[0].initialRemainingAmount)

        assertEquals(EntityId("d-b"), plan.debtPlans[1].debtId)
        assertEquals(Money(5000_00L, Currency.TRY), plan.debtPlans[1].initialRemainingAmount)

        assertEquals(Money(8000_00L, Currency.TRY), plan.totalDebtAmount)
    }

    @Test
    fun calculate_excludesReceivablesAndSettledDebts() {
        val activeDebt = createDebt(id = "d-1", amountMinor = 2000_00L, type = DebtType.DEBT)
        val receivable = createDebt(id = "r-1", amountMinor = 5000_00L, type = DebtType.RECEIVABLE)
        val settledDebt = createDebt(id = "d-settled", amountMinor = 3000_00L, type = DebtType.DEBT, status = DebtStatus.SETTLED)
        val fullyPaidDebt = createDebt(id = "d-paid", amountMinor = 1000_00L, type = DebtType.DEBT)
        val paymentPaid = createPayment(id = "p-paid", debtId = "d-paid", amountMinor = 1000_00L)

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(activeDebt, receivable, settledDebt, fullyPaidDebt),
            payments = listOf(paymentPaid),
            monthlyPaymentBudget = Money(1000_00L, Currency.TRY),
        )

        // Sadece activeDebt plana dahil olmalıdır
        assertEquals(1, plan.debtPlans.size)
        assertEquals(EntityId("d-1"), plan.debtPlans[0].debtId)
        assertEquals(Money(2000_00L, Currency.TRY), plan.totalDebtAmount)
        assertEquals(2, plan.totalMonths)
    }

    @Test
    fun calculate_emptyEligibleDebts_returnsEmptyValidPlan() {
        val receivable = createDebt(id = "r-1", amountMinor = 5000_00L, type = DebtType.RECEIVABLE)

        val plan = DebtSnowballPlanner.calculate(
            debts = listOf(receivable),
            payments = emptyList(),
            monthlyPaymentBudget = Money(1000_00L, Currency.TRY),
        )

        assertEquals(Currency.TRY, plan.currency)
        assertEquals(0, plan.totalMonths)
        assertEquals(Money(0L, Currency.TRY), plan.totalDebtAmount)
        assertTrue(plan.debtPlans.isEmpty())
        assertTrue(plan.monthlyAllocations.isEmpty())
    }

    @Test
    fun calculate_failClosed_whenBudgetIsZeroOrNegative() {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = emptyList(),
                monthlyPaymentBudget = Money(0L, Currency.TRY),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = emptyList(),
                monthlyPaymentBudget = Money(-500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenCurrencyMismatchBetweenBudgetAndDebt() {
        val debtUsd = createDebt(id = "d-1", amountMinor = 1000_00L, currency = Currency.USD)
        val budgetTry = Money(1000_00L, Currency.TRY)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debtUsd),
                payments = emptyList(),
                monthlyPaymentBudget = budgetTry,
            )
        }
    }

    @Test
    fun calculate_failClosed_whenOrphanPaymentDetected() {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        val orphanPayment = createPayment(id = "p-1", debtId = "non-existent-debt", amountMinor = 100_00L)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = listOf(orphanPayment),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenDuplicatePaymentIdDetected() {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        val payment1 = createPayment(id = "p-dup", debtId = "d-1", amountMinor = 100_00L)
        val payment2 = createPayment(id = "p-dup", debtId = "d-1", amountMinor = 200_00L)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = listOf(payment1, payment2),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenPaymentOverpaysPrincipal() {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        val overPayment = createPayment(id = "p-1", debtId = "d-1", amountMinor = 1500_00L)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = listOf(overPayment),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenPaymentCurrencyMismatchesDebt() {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L, currency = Currency.TRY)
        val paymentUsd = createPayment(id = "p-1", debtId = "d-1", amountMinor = 100_00L, currency = Currency.USD)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = listOf(paymentUsd),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenDuplicateDebtIdDetected() {
        val debt1 = createDebt(id = "d-dup", amountMinor = 1000_00L)
        val debt2 = createDebt(id = "d-dup", amountMinor = 2000_00L)

        assertFailsWith<IllegalArgumentException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt1, debt2),
                payments = emptyList(),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }

    @Test
    fun calculate_failClosed_whenTotalDebtAdditionOverflows() {
        val debt1 = createDebt(id = "d-1", amountMinor = Money.MAX_AMOUNT_MINOR)
        val debt2 = createDebt(id = "d-2", amountMinor = Money.MAX_AMOUNT_MINOR)

        assertFailsWith<IllegalStateException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt1, debt2),
                payments = emptyList(),
                monthlyPaymentBudget = Money(500_00L, Currency.TRY),
            )
        }
    }


    @Test
    fun calculate_failClosed_whenSimulationExceedsMaximumMonths() {
        val debt = createDebt(
            id = "d-large",
            amountMinor = (DebtSnowballPlanner.MAX_SIMULATION_MONTHS + 1) * 100_00L,
        )

        assertFailsWith<IllegalStateException> {
            DebtSnowballPlanner.calculate(
                debts = listOf(debt),
                payments = emptyList(),
                monthlyPaymentBudget = Money(100_00L, Currency.TRY),
            )
        }
    }
}

