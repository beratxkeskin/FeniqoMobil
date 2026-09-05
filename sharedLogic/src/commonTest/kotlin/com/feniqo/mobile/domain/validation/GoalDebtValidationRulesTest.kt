package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.CreateGoalCommand
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
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GoalDebtValidationRulesTest {

    private val sampleGoal = Goal(
        id = EntityId("goal-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Tatil Fonu",
        targetAmount = Money(50_000, Currency.TRY),
        currentAmount = Money(15_000, Currency.TRY),
        targetDate = LocalDate(2027, 1, 1),
        color = CategoryColor("#0A7A55"),
        icon = CategoryIcon("plane"),
        createdAt = NOW,
    )

    private val sampleDebt = Debt(
        id = EntityId("debt-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = "Kira Borcu",
        amount = Money(40_000, Currency.TRY),
        type = DebtType.DEBT,
        dueDate = LocalDate(2026, 10, 1),
        status = DebtStatus.OPEN,
        description = "Eylül ve Ekim kirası",
        createdAt = NOW,
    )

    // region Goal Validation Tests

    @Test
    fun create_goal_command_valid() {
        val command = CreateGoalCommand(
            name = "  Acil Fon  ",
            targetAmount = Money(100_000, Currency.TRY),
            initialAmount = Money(5_000, Currency.TRY),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
            icon = CategoryIcon("shield"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val valid = assertIs<GoalDebtValidationResult.Valid<CreateGoalCommand>>(result)
        assertEquals("Acil Fon", valid.value.name)
        assertEquals(Money(5_000, Currency.TRY), valid.value.initialAmount)
    }

    @Test
    fun create_goal_command_defaults_initial_amount_to_zero_when_null() {
        val command = CreateGoalCommand(
            name = "Ev Peşinatı",
            targetAmount = Money(500_000, Currency.TRY),
            initialAmount = null,
            targetDate = LocalDate(2028, 1, 1),
            color = CategoryColor("#0A7A55"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val valid = assertIs<GoalDebtValidationResult.Valid<CreateGoalCommand>>(result)
        assertEquals(Money(0, Currency.TRY), valid.value.initialAmount)
    }

    @Test
    fun create_goal_rejects_blank_name() {
        val command = CreateGoalCommand(
            name = "   ",
            targetAmount = Money(100_000, Currency.TRY),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_NAME_BLANK, invalid.error)
    }

    @Test
    fun create_goal_rejects_name_too_long() {
        val command = CreateGoalCommand(
            name = "g".repeat(Goal.MAX_NAME_LENGTH + 1),
            targetAmount = Money(100_000, Currency.TRY),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_NAME_TOO_LONG, invalid.error)
    }

    @Test
    fun create_goal_rejects_non_positive_target_amount() {
        val command = CreateGoalCommand(
            name = "Hedef",
            targetAmount = Money(0, Currency.TRY),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_TARGET_AMOUNT_NON_POSITIVE, invalid.error)
    }

    @Test
    fun create_goal_rejects_currency_mismatch_on_initial_amount() {
        val command = CreateGoalCommand(
            name = "Hedef",
            targetAmount = Money(100_000, Currency.TRY),
            initialAmount = Money(1_000, Currency.USD),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
        )
        val result = GoalDebtValidationRules.validateCreateGoalCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH, invalid.error)
    }

    @Test
    fun update_goal_command_valid() {
        val command = UpdateGoalCommand(
            id = sampleGoal.id,
            name = "  Güncel Tatil Fonu ",
            targetAmount = Money(60_000, Currency.TRY),
            targetDate = LocalDate(2027, 3, 1),
            color = CategoryColor("#123456"),
        )
        val result = GoalDebtValidationRules.validateUpdateGoalCommand(command)
        val valid = assertIs<GoalDebtValidationResult.Valid<UpdateGoalCommand>>(result)
        assertEquals("Güncel Tatil Fonu", valid.value.name)
    }

    @Test
    fun apply_goal_update_updates_only_editable_fields() {
        val command = UpdateGoalCommand(
            id = sampleGoal.id,
            name = "Yeni İsim",
            targetAmount = Money(75_000, Currency.TRY),
            targetDate = LocalDate(2027, 5, 1),
            color = CategoryColor("#123456"),
            icon = CategoryIcon("hotel"),
        )
        val result = GoalDebtValidationRules.applyGoalUpdate(sampleGoal, command)
        val valid = assertIs<GoalDebtValidationResult.Valid<Goal>>(result)

        assertEquals("Yeni İsim", valid.value.name)
        assertEquals(Money(75_000, Currency.TRY), valid.value.targetAmount)
        assertEquals(sampleGoal.currentAmount, valid.value.currentAmount) // Korundu
        assertEquals(sampleGoal.ownerId, valid.value.ownerId) // Korundu
        assertEquals(sampleGoal.createdAt, valid.value.createdAt) // Korundu
    }

    // endregion

    // region Goal Contribution Validation Tests

    @Test
    fun add_goal_contribution_command_valid() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(10_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "  Maaştan birikim  ",
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val valid = assertIs<GoalDebtValidationResult.Valid<AddGoalContributionCommand>>(result)
        assertEquals("Maaştan birikim", valid.value.note)
    }

    @Test
    fun add_goal_contribution_rejects_non_positive_amount() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(0, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CONTRIBUTION_AMOUNT_NON_POSITIVE, invalid.error)
    }

    @Test
    fun add_goal_contribution_rejects_currency_mismatch() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(500, Currency.USD),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH, invalid.error)
    }

    @Test
    fun add_goal_contribution_rejects_goal_id_mismatch() {
        val command = AddGoalContributionCommand(
            goalId = EntityId("other-goal"),
            amount = Money(5_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CONTRIBUTION_GOAL_ID_MISMATCH, invalid.error)
    }

    @Test
    fun remove_goal_contribution_rejects_exceeding_current_amount() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(15_001, Currency.TRY), // currentAmount 15_000
            direction = GoalContributionDirection.REMOVE,
            occurredOn = LocalDate(2026, 9, 1),
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CONTRIBUTION_EXCEEDS_CURRENT_AMOUNT, invalid.error)
    }

    @Test
    fun add_goal_contribution_accepts_null_note() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(5_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = null,
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val valid = assertIs<GoalDebtValidationResult.Valid<AddGoalContributionCommand>>(result)
        assertNull(valid.value.note)
    }

    @Test
    fun add_goal_contribution_rejects_whitespace_note() {
        val command = AddGoalContributionCommand(
            goalId = sampleGoal.id,
            amount = Money(5_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "   ",
        )
        val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, sampleGoal)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CONTRIBUTION_NOTE_BLANK, invalid.error)
    }

    @Test
    fun apply_goal_update_rejects_currency_change() {
        val command = UpdateGoalCommand(
            id = sampleGoal.id,
            name = "Dolar Hedefi",
            targetAmount = Money(2_000, Currency.USD),
            targetDate = LocalDate(2027, 5, 1),
            color = CategoryColor("#123456"),
        )
        val result = GoalDebtValidationRules.applyGoalUpdate(sampleGoal, command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.GOAL_CURRENCY_MISMATCH, invalid.error)
    }

    // endregion

    // region Debt Validation Tests

    @Test
    fun create_debt_command_valid() {
        val command = CreateDebtCommand(
            title = "  Elden Borç  ",
            amount = Money(20_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
            description = "  Ahmet'ten alındı  ",
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val valid = assertIs<GoalDebtValidationResult.Valid<CreateDebtCommand>>(result)
        assertEquals("Elden Borç", valid.value.title)
        assertEquals("Ahmet'ten alındı", valid.value.description)
    }

    @Test
    fun create_debt_accepts_null_description() {
        val command = CreateDebtCommand(
            title = "Borç",
            amount = Money(20_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
            description = null,
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val valid = assertIs<GoalDebtValidationResult.Valid<CreateDebtCommand>>(result)
        assertNull(valid.value.description)
    }

    @Test
    fun create_debt_rejects_whitespace_description() {
        val command = CreateDebtCommand(
            title = "Borç",
            amount = Money(20_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
            description = "   ",
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_DESCRIPTION_BLANK, invalid.error)
    }

    @Test
    fun create_debt_rejects_blank_title() {
        val command = CreateDebtCommand(
            title = "   ",
            amount = Money(20_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_TITLE_BLANK, invalid.error)
    }

    @Test
    fun create_debt_rejects_title_too_long() {
        val command = CreateDebtCommand(
            title = "d".repeat(Debt.MAX_TITLE_LENGTH + 1),
            amount = Money(20_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_TITLE_TOO_LONG, invalid.error)
    }

    @Test
    fun create_debt_rejects_non_positive_amount() {
        val command = CreateDebtCommand(
            title = "Borç",
            amount = Money(0, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
        )
        val result = GoalDebtValidationRules.validateCreateDebtCommand(command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_AMOUNT_NON_POSITIVE, invalid.error)
    }

    @Test
    fun update_debt_rejects_reducing_amount_below_already_paid() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = sampleDebt.id,
                amount = Money(25_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = NOW,
            )
        )
        val command = UpdateDebtCommand(
            id = sampleDebt.id,
            title = sampleDebt.title,
            amount = Money(20_000, Currency.TRY), // 20k < 25k paid
            type = DebtType.DEBT,
            dueDate = sampleDebt.dueDate,
        )
        val result = GoalDebtValidationRules.validateUpdateDebtCommand(command, existingPayments)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_UPDATE_AMOUNT_LESS_THAN_PAID, invalid.error)
    }

    @Test
    fun apply_debt_update_rejects_currency_change() {
        val command = UpdateDebtCommand(
            id = sampleDebt.id,
            title = "Euro Borcu",
            amount = Money(1_000, Currency.EUR),
            type = DebtType.DEBT,
            dueDate = sampleDebt.dueDate,
        )
        val result = GoalDebtValidationRules.applyDebtUpdate(sampleDebt, command)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_CURRENCY_MISMATCH, invalid.error)
    }

    @Test
    fun apply_debt_update_updates_only_editable_fields() {
        val command = UpdateDebtCommand(
            id = sampleDebt.id,
            title = "Yeni Başlık",
            amount = Money(50_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 11, 1),
            description = "Yeni açıklama",
        )
        val result = GoalDebtValidationRules.applyDebtUpdate(sampleDebt, command)
        val valid = assertIs<GoalDebtValidationResult.Valid<Debt>>(result)

        assertEquals("Yeni Başlık", valid.value.title)
        assertEquals(Money(50_000, Currency.TRY), valid.value.amount)
        assertEquals(sampleDebt.ownerId, valid.value.ownerId) // Korundu
        assertEquals(sampleDebt.status, valid.value.status) // Korundu
        assertEquals(sampleDebt.createdAt, valid.value.createdAt) // Korundu
    }

    // endregion

    // region Debt Payment Validation Tests

    @Test
    fun add_debt_payment_command_valid() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = sampleDebt.id,
                amount = Money(15_000, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = NOW,
            )
        )
        val command = AddDebtPaymentCommand(
            debtId = sampleDebt.id,
            amount = Money(20_000, Currency.TRY),
            paidOn = LocalDate(2026, 9, 15),
        )
        val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, sampleDebt, existingPayments)
        val valid = assertIs<GoalDebtValidationResult.Valid<AddDebtPaymentCommand>>(result)
        assertEquals(Money(20_000, Currency.TRY), valid.value.amount)
    }

    @Test
    fun add_debt_payment_rejects_non_positive_amount() {
        val command = AddDebtPaymentCommand(
            debtId = sampleDebt.id,
            amount = Money(0, Currency.TRY),
            paidOn = LocalDate(2026, 9, 15),
        )
        val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, sampleDebt)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_PAYMENT_AMOUNT_NON_POSITIVE, invalid.error)
    }

    @Test
    fun add_debt_payment_rejects_currency_mismatch() {
        val command = AddDebtPaymentCommand(
            debtId = sampleDebt.id,
            amount = Money(500, Currency.USD),
            paidOn = LocalDate(2026, 9, 15),
        )
        val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, sampleDebt)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_PAYMENT_CURRENCY_MISMATCH, invalid.error)
    }

    @Test
    fun add_debt_payment_rejects_debt_id_mismatch() {
        val command = AddDebtPaymentCommand(
            debtId = EntityId("other-debt"),
            amount = Money(5_000, Currency.TRY),
            paidOn = LocalDate(2026, 9, 15),
        )
        val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, sampleDebt)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_PAYMENT_DEBT_ID_MISMATCH, invalid.error)
    }

    @Test
    fun add_debt_payment_rejects_exceeding_remaining_debt() {
        val existingPayments = listOf(
            DebtPayment(
                id = EntityId("pay-1"),
                debtId = sampleDebt.id,
                amount = Money(30_000, Currency.TRY), // 40k total - 30k = 10k remaining
                paidOn = LocalDate(2026, 9, 1),
                createdAt = NOW,
            )
        )
        val command = AddDebtPaymentCommand(
            debtId = sampleDebt.id,
            amount = Money(10_001, Currency.TRY),
            paidOn = LocalDate(2026, 9, 15),
        )
        val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, sampleDebt, existingPayments)
        val invalid = assertIs<GoalDebtValidationResult.Invalid>(result)
        assertEquals(GoalDebtValidationError.DEBT_PAYMENT_EXCEEDS_REMAINING, invalid.error)
    }

    // endregion

    // region Amount Input Parsers Tests

    @Test
    fun validate_amount_input_success_and_failures() {
        val validRes = GoalDebtValidationRules.validateGoalTargetAmountInput("1500,50", Currency.TRY)
        val valid = assertIs<GoalDebtValidationResult.Valid<Money>>(validRes)
        assertEquals(150050L, valid.value.amountMinor)

        val invalidEmpty = GoalDebtValidationRules.validateGoalTargetAmountInput("", Currency.TRY)
        assertIs<GoalDebtValidationResult.Invalid>(invalidEmpty)

        val invalidZero = GoalDebtValidationRules.validateDebtAmountInput("0,00", Currency.TRY)
        assertIs<GoalDebtValidationResult.Invalid>(invalidZero)
    }

    // endregion

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T12:00:00Z")
    }
}
