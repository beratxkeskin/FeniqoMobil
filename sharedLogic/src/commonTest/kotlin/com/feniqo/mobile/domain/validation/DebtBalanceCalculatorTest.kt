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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebtBalanceCalculatorTest {

    private val testDebt = Debt(
        id = EntityId("debt-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = "Ahmet Bey Borç",
        amount = Money(100_000, Currency.TRY),
        type = DebtType.DEBT,
        dueDate = LocalDate(2026, 12, 31),
        status = DebtStatus.OPEN,
        description = "Elden alınan borç",
        createdAt = NOW,
    )

    private val testReceivable = Debt(
        id = EntityId("receivable-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = "Mehmet Alacak",
        amount = Money(50_000, Currency.TRY),
        type = DebtType.RECEIVABLE,
        dueDate = LocalDate(2026, 11, 15),
        status = DebtStatus.OPEN,
        description = "Danışmanlık alacağı",
        createdAt = NOW,
    )

    @Test
    fun calculate_open_debt_with_zero_payments() {
        val balance = DebtBalanceCalculator.calculate(testDebt, emptyList())

        assertEquals(testDebt.id, balance.debtId)
        assertEquals(Money(100_000, Currency.TRY), balance.principalAmount)
        assertEquals(Money(0, Currency.TRY), balance.totalPaid)
        assertEquals(Money(100_000, Currency.TRY), balance.remainingAmount)
        assertEquals(DebtStatus.OPEN, balance.status)
        assertFalse(balance.isSettled)
        assertEquals(0, balance.paymentCount)
    }

    @Test
    fun calculate_partial_payment_leaves_status_open_with_correct_remaining() {
        val payments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(30_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            ),
            DebtPayment(
                id = EntityId("pay-2"),
                debtId = testDebt.id,
                amount = Money(25_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 20),
                createdAt = NOW,
            ),
        )

        val balance = DebtBalanceCalculator.calculate(testDebt, payments)

        assertEquals(Money(55_000, Currency.TRY), balance.totalPaid)
        assertEquals(Money(45_000, Currency.TRY), balance.remainingAmount)
        assertEquals(DebtStatus.OPEN, balance.status)
        assertFalse(balance.isSettled)
        assertEquals(2, balance.paymentCount)
    }

    @Test
    fun calculate_full_payment_sets_status_to_settled_with_zero_remaining() {
        val payments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(60_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            ),
            DebtPayment(
                id = EntityId("pay-2"),
                debtId = testDebt.id,
                amount = Money(40_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 20),
                createdAt = NOW,
            ),
        )

        val balance = DebtBalanceCalculator.calculate(testDebt, payments)

        assertEquals(Money(100_000, Currency.TRY), balance.totalPaid)
        assertEquals(Money(0, Currency.TRY), balance.remainingAmount)
        assertEquals(DebtStatus.SETTLED, balance.status)
        assertTrue(balance.isSettled)
        assertEquals(2, balance.paymentCount)
    }

    @Test
    fun calculate_rejects_overpayment_exceeding_principal() {
        val payments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(60_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            ),
            DebtPayment(
                id = EntityId("pay-2"),
                debtId = testDebt.id,
                amount = Money(45_000, Currency.TRY), // 60k + 45k = 105k > 100k
                paidOn = LocalDate(2026, 9, 20),
                createdAt = NOW,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, payments)
        }
    }

    @Test
    fun calculate_rejects_single_payment_exceeding_principal() {
        val excessPayment = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(100_001, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, excessPayment)
        }
    }

    @Test
    fun calculate_rejects_currency_mismatch_in_payments() {
        val usdPayment = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(1_000, Currency.USD),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, usdPayment)
        }
    }

    @Test
    fun calculate_rejects_mismatched_debt_id_in_payments() {
        val mismatchedPayment = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = EntityId("other-debt"),
                amount = Money(10_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, mismatchedPayment)
        }
    }

    @Test
    fun calculate_rejects_duplicate_payment_id() {
        val duplicatePayments = listOf(
            DebtPayment(
                id = EntityId("same-pay-id"),
                debtId = testDebt.id,
                amount = Money(10_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            ),
            DebtPayment(
                id = EntityId("same-pay-id"),
                debtId = testDebt.id,
                amount = Money(20_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 15),
                createdAt = NOW,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, duplicatePayments)
        }
    }

    @Test
    fun calculate_with_new_payment_validates_and_updates_balance() {
        val existing = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = testDebt.id,
                amount = Money(40_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            )
        )
        val newPayment = DebtPayment(
            id = EntityId("pay-2"),
            debtId = testDebt.id,
            amount = Money(30_000, Currency.TRY),
            paidOn = LocalDate(2026, 9, 15),
            createdAt = NOW,
        )

        val balance = DebtBalanceCalculator.calculateWithNewPayment(testDebt, existing, newPayment)
        assertEquals(Money(70_000, Currency.TRY), balance.totalPaid)
        assertEquals(Money(30_000, Currency.TRY), balance.remainingAmount)
        assertEquals(DebtStatus.OPEN, balance.status)
        assertEquals(2, balance.paymentCount)
    }

    @Test
    fun receivable_balance_calculation_behaves_identically() {
        val payments = listOf(
            DebtPayment(
                id = EntityId("rec-pay-1"),
                debtId = testReceivable.id,
                amount = Money(50_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 10),
                createdAt = NOW,
            )
        )

        val balance = DebtBalanceCalculator.calculate(testReceivable, payments)
        assertEquals(Money(50_000, Currency.TRY), balance.totalPaid)
        assertEquals(Money(0, Currency.TRY), balance.remainingAmount)
        assertEquals(DebtStatus.SETTLED, balance.status)
        assertTrue(balance.isSettled)
    }

    @Test
    fun calculate_rejects_cumulative_overpayment_in_multiple_payments() {
        // İlk 3 ödeme 30k + 30k + 30k = 90k (10k kalan)
        // 4. ödeme 10_001 gelirse fail-closed reddedilmeli
        val payments = listOf(
            DebtPayment(EntityId("pay-1"), testDebt.id, Money(30_000, Currency.TRY), LocalDate(2026, 9, 1), NOW),
            DebtPayment(EntityId("pay-2"), testDebt.id, Money(30_000, Currency.TRY), LocalDate(2026, 9, 2), NOW),
            DebtPayment(EntityId("pay-3"), testDebt.id, Money(30_000, Currency.TRY), LocalDate(2026, 9, 3), NOW),
            DebtPayment(EntityId("pay-4"), testDebt.id, Money(10_001, Currency.TRY), LocalDate(2026, 9, 4), NOW),
        )

        assertFailsWith<IllegalArgumentException> {
            DebtBalanceCalculator.calculate(testDebt, payments)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T12:00:00Z")
    }
}
