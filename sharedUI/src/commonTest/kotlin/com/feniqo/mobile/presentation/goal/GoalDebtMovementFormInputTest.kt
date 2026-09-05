package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.debt.DebtPaymentFormFieldError
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInput
import com.feniqo.mobile.presentation.debt.DebtPaymentFormNormalizationResult
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GoalDebtMovementFormInputTest {

    private val sampleGoal = Goal(
        id = EntityId("g-1"),
        ownerId = EntityId("u-1"),
        workspaceId = null,
        name = "Tatil Fonu",
        targetAmount = Money(20_000_00L, Currency.TRY),
        currentAmount = Money(5_000_00L, Currency.TRY),
        targetDate = LocalDate(2027, 6, 1),
        color = CategoryColor("#1976D2"),
        icon = null,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private val sampleDebt = Debt(
        id = EntityId("d-1"),
        ownerId = EntityId("u-1"),
        workspaceId = null,
        title = "Arkadaşa Borç",
        amount = Money(10_000_00L, Currency.TRY),
        type = DebtType.DEBT,
        dueDate = LocalDate(2026, 12, 31),
        status = DebtStatus.OPEN,
        description = "Elden borç",
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    @Test
    fun goalContribution_add_successfulNormalizationAndCommandCreation() {
        val input = GoalContributionFormInput(
            amountInput = "1500",
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 3),
            noteInput = "  Maaş primi  ",
        )

        val result = input.toDraft(sampleGoal)
        val success = assertIs<GoalContributionFormNormalizationResult.Success>(result)
        val cmd = success.command

        assertEquals(sampleGoal.id, cmd.goalId)
        assertEquals(Money(1_500_00L, Currency.TRY), cmd.amount)
        assertEquals(GoalContributionDirection.ADD, cmd.direction)
        assertEquals(LocalDate(2026, 9, 3), cmd.occurredOn)
        assertEquals("Maaş primi", cmd.note) // Trimlenmiş
    }

    @Test
    fun goalContribution_blankNote_normalizesToNull() {
        val input = GoalContributionFormInput(
            amountInput = "500",
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 3),
            noteInput = "    ",
        )

        val result = input.toDraft(sampleGoal)
        val success = assertIs<GoalContributionFormNormalizationResult.Success>(result)
        assertNull(success.command.note)
    }

    @Test
    fun goalContribution_remove_successfulWithinCurrentAmount() {
        val input = GoalContributionFormInput(
            amountInput = "3000",
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 3),
        )

        val result = input.toDraft(sampleGoal)
        val success = assertIs<GoalContributionFormNormalizationResult.Success>(result)
        assertEquals(Money(3_000_00L, Currency.TRY), success.command.amount)
        assertEquals(GoalContributionDirection.REMOVE, success.command.direction)
    }

    @Test
    fun goalContribution_remove_exceedsCurrentAmount_failsClosed() {
        // Mevcut 5000 TL, çekilmek istenen 6000 TL
        val input = GoalContributionFormInput(
            amountInput = "6000",
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 3),
        )

        val result = input.toDraft(sampleGoal)
        val invalid = assertIs<GoalContributionFormNormalizationResult.Invalid>(result)
        assertEquals(GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT, invalid.errors.amountError)
    }

    @Test
    fun goalContribution_validations_blankAmount_invalidDate_noteTooLong_parentNull() {
        // Boş tutar
        val r1 = GoalContributionFormInput(amountInput = "", occurredOn = LocalDate(2026, 9, 3)).toDraft(sampleGoal)
        assertEquals(GoalContributionFormFieldError.AMOUNT_REQUIRED, assertIs<GoalContributionFormNormalizationResult.Invalid>(r1).errors.amountError)

        // 0 tutar
        val r2 = GoalContributionFormInput(amountInput = "0", occurredOn = LocalDate(2026, 9, 3)).toDraft(sampleGoal)
        assertEquals(GoalContributionFormFieldError.AMOUNT_NON_POSITIVE, assertIs<GoalContributionFormNormalizationResult.Invalid>(r2).errors.amountError)

        // Tarih null
        val r3 = GoalContributionFormInput(amountInput = "100", occurredOn = null).toDraft(sampleGoal)
        assertEquals(GoalContributionFormFieldError.DATE_REQUIRED, assertIs<GoalContributionFormNormalizationResult.Invalid>(r3).errors.dateError)

        // Not > 500 karakter
        val longNote = "a".repeat(501)
        val r4 = GoalContributionFormInput(amountInput = "100", occurredOn = LocalDate(2026, 9, 3), noteInput = longNote).toDraft(sampleGoal)
        assertEquals(GoalContributionFormFieldError.NOTE_TOO_LONG, assertIs<GoalContributionFormNormalizationResult.Invalid>(r4).errors.noteError)

        // Parent goal null
        val r5 = GoalContributionFormInput(amountInput = "100", occurredOn = LocalDate(2026, 9, 3)).toDraft(null)
        assertEquals(GoalContributionFormFieldError.PARENT_GOAL_MISSING, assertIs<GoalContributionFormNormalizationResult.Invalid>(r5).errors.generalError)
    }

    @Test
    fun debtPayment_successfulNormalizationAndCommandCreation() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = sampleDebt.id,
                amount = Money(3_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 1),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        // Toplam 10.000 TL, ödenen 3.000 TL -> Kalan 7.000 TL. Ödeme: 2.500 TL
        val input = DebtPaymentFormInput(
            amountInput = "2500",
            paidOn = LocalDate(2026, 9, 3),
        )

        val result = input.toDraft(sampleDebt, existingPayments)
        val success = assertIs<DebtPaymentFormNormalizationResult.Success>(result)
        val cmd = success.command

        assertEquals(sampleDebt.id, cmd.debtId)
        assertEquals(Money(2_500_00L, Currency.TRY), cmd.amount)
        assertEquals(LocalDate(2026, 9, 3), cmd.paidOn)
    }

    @Test
    fun debtPayment_exceedsRemainingAmount_failsClosed() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = sampleDebt.id,
                amount = Money(8_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 1),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        // Kalan 2.000 TL, ödenmek istenen 2.500 TL
        val input = DebtPaymentFormInput(
            amountInput = "2500",
            paidOn = LocalDate(2026, 9, 3),
        )

        val result = input.toDraft(sampleDebt, existingPayments)
        val invalid = assertIs<DebtPaymentFormNormalizationResult.Invalid>(result)
        assertEquals(DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT, invalid.errors.amountError)
    }

    @Test
    fun debtPayment_settledDebt_rejectsPayment() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = sampleDebt.id,
                amount = Money(10_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 1),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        // Kalan 0 TL -> Settled
        val input = DebtPaymentFormInput(
            amountInput = "100",
            paidOn = LocalDate(2026, 9, 3),
        )

        val result = input.toDraft(sampleDebt, existingPayments)
        val invalid = assertIs<DebtPaymentFormNormalizationResult.Invalid>(result)
        assertEquals(DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED, invalid.errors.generalError)
    }

    @Test
    fun debtPayment_validations_blankAmount_invalidDate_parentNull() {
        // Boş tutar
        val r1 = DebtPaymentFormInput(amountInput = "", paidOn = LocalDate(2026, 9, 3)).toDraft(sampleDebt, emptyList())
        assertEquals(DebtPaymentFormFieldError.AMOUNT_REQUIRED, assertIs<DebtPaymentFormNormalizationResult.Invalid>(r1).errors.amountError)

        // 0 tutar
        val r2 = DebtPaymentFormInput(amountInput = "0", paidOn = LocalDate(2026, 9, 3)).toDraft(sampleDebt, emptyList())
        assertEquals(DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE, assertIs<DebtPaymentFormNormalizationResult.Invalid>(r2).errors.amountError)

        // Tarih null
        val r3 = DebtPaymentFormInput(amountInput = "100", paidOn = null).toDraft(sampleDebt, emptyList())
        assertEquals(DebtPaymentFormFieldError.DATE_REQUIRED, assertIs<DebtPaymentFormNormalizationResult.Invalid>(r3).errors.dateError)

        // Parent debt null
        val r4 = DebtPaymentFormInput(amountInput = "100", paidOn = LocalDate(2026, 9, 3)).toDraft(null, emptyList())
        assertEquals(DebtPaymentFormFieldError.PARENT_DEBT_MISSING, assertIs<DebtPaymentFormNormalizationResult.Invalid>(r4).errors.generalError)
    }
}
