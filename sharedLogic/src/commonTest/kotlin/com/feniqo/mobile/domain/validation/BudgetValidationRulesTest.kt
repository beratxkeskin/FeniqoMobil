package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DeleteBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BudgetValidationRulesTest {

    @Test
    fun valid_amount_strings_parse_to_minor_units_correctly() {
        val resultComma = BudgetValidationRules.validateAmount("1250,50", Currency.TRY)
        assertIs<BudgetValidationResult.Valid<Money>>(resultComma)
        assertEquals(125050L, resultComma.value.amountMinor)
        assertEquals(Currency.TRY, resultComma.value.currency)

        val resultDot = BudgetValidationRules.validateAmount("500.25", Currency.TRY)
        assertIs<BudgetValidationResult.Valid<Money>>(resultDot)
        assertEquals(50025L, resultDot.value.amountMinor)

        val resultInteger = BudgetValidationRules.validateAmount("1000", Currency.TRY)
        assertIs<BudgetValidationResult.Valid<Money>>(resultInteger)
        assertEquals(100000L, resultInteger.value.amountMinor)
    }

    @Test
    fun zero_and_negative_amounts_are_rejected() {
        val resultZero = BudgetValidationRules.validateAmount("0", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultZero)
        assertEquals(BudgetValidationError.AMOUNT_NON_POSITIVE, resultZero.error)

        val resultZeroDecimal = BudgetValidationRules.validateAmount("0,00", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultZeroDecimal)
        assertEquals(BudgetValidationError.AMOUNT_NON_POSITIVE, resultZeroDecimal.error)

        val resultNegative = BudgetValidationRules.validateAmount("-50", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultNegative)
        assertEquals(BudgetValidationError.AMOUNT_INVALID_FORMAT, resultNegative.error)
    }

    @Test
    fun empty_and_invalid_amount_formats_are_rejected() {
        val resultEmpty = BudgetValidationRules.validateAmount("", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultEmpty)
        assertEquals(BudgetValidationError.AMOUNT_EMPTY, resultEmpty.error)

        val resultBlank = BudgetValidationRules.validateAmount("   ", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultBlank)
        assertEquals(BudgetValidationError.AMOUNT_EMPTY, resultBlank.error)

        val resultAlpha = BudgetValidationRules.validateAmount("abc", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultAlpha)
        assertEquals(BudgetValidationError.AMOUNT_INVALID_FORMAT, resultAlpha.error)

        val resultMixedSeparators = BudgetValidationRules.validateAmount("1.000,50", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultMixedSeparators)
        assertEquals(BudgetValidationError.AMOUNT_INVALID_FORMAT, resultMixedSeparators.error)

        val resultExcessiveDecimals = BudgetValidationRules.validateAmount("10,123", Currency.TRY)
        assertIs<BudgetValidationResult.Invalid>(resultExcessiveDecimals)
        assertEquals(BudgetValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS, resultExcessiveDecimals.error)
    }

    @Test
    fun valid_year_month_is_accepted() {
        val result = BudgetValidationRules.validateMonth("2026-08")
        assertIs<BudgetValidationResult.Valid<YearMonth>>(result)
        assertEquals("2026-08", result.value.value)

        val resultWithWhitespace = BudgetValidationRules.validateMonth("  2026-12  ")
        assertIs<BudgetValidationResult.Valid<YearMonth>>(resultWithWhitespace)
        assertEquals("2026-12", resultWithWhitespace.value.value)
    }

    @Test
    fun invalid_year_month_is_rejected_without_leaking_exceptions() {
        val resultEmpty = BudgetValidationRules.validateMonth("")
        assertIs<BudgetValidationResult.Invalid>(resultEmpty)
        assertEquals(BudgetValidationError.MONTH_REQUIRED, resultEmpty.error)

        val resultNull = BudgetValidationRules.validateMonth(null)
        assertIs<BudgetValidationResult.Invalid>(resultNull)
        assertEquals(BudgetValidationError.MONTH_REQUIRED, resultNull.error)

        val resultInvalidMonth = BudgetValidationRules.validateMonth("2026-13")
        assertIs<BudgetValidationResult.Invalid>(resultInvalidMonth)
        assertEquals(BudgetValidationError.MONTH_INVALID_FORMAT, resultInvalidMonth.error)

        val resultInvalidFormat = BudgetValidationRules.validateMonth("2026/08")
        assertIs<BudgetValidationResult.Invalid>(resultInvalidFormat)
        assertEquals(BudgetValidationError.MONTH_INVALID_FORMAT, resultInvalidFormat.error)

        val resultMalformed = BudgetValidationRules.validateMonth("invalid-date")
        assertIs<BudgetValidationResult.Invalid>(resultMalformed)
        assertEquals(BudgetValidationError.MONTH_INVALID_FORMAT, resultMalformed.error)
    }

    @Test
    fun category_validation_works_as_expected() {
        val resultValid = BudgetValidationRules.validateCategory(EntityId("cat_123"))
        assertIs<BudgetValidationResult.Valid<EntityId>>(resultValid)
        assertEquals(EntityId("cat_123"), resultValid.value)

        val resultNullEntity = BudgetValidationRules.validateCategory(null)
        assertIs<BudgetValidationResult.Invalid>(resultNullEntity)
        assertEquals(BudgetValidationError.CATEGORY_REQUIRED, resultNullEntity.error)
    }

    @Test
    fun copy_months_validation_rejects_same_month() {
        val month = YearMonth("2026-08")
        val result = BudgetValidationRules.validateCopyMonths(month, month)
        assertIs<BudgetValidationResult.Invalid>(result)
        assertEquals(BudgetValidationError.SOURCE_AND_TARGET_MONTH_SAME, result.error)

        val resultFromStrings = BudgetValidationRules.validateCopyMonths("2026-08", "2026-08")
        assertIs<BudgetValidationResult.Invalid>(resultFromStrings)
        assertEquals(BudgetValidationError.SOURCE_AND_TARGET_MONTH_SAME, resultFromStrings.error)
    }

    @Test
    fun copy_months_validation_accepts_different_valid_months() {
        val source = YearMonth("2026-07")
        val target = YearMonth("2026-08")
        val result = BudgetValidationRules.validateCopyMonths(source, target)
        assertIs<BudgetValidationResult.Valid<CopyBudgetsCommand>>(result)
        assertEquals(source, result.value.sourceMonth)
        assertEquals(target, result.value.targetMonth)

        val resultFromStrings = BudgetValidationRules.validateCopyMonths("2026-07", "2026-08")
        assertIs<BudgetValidationResult.Valid<CopyBudgetsCommand>>(resultFromStrings)
        assertEquals(source, result.value.sourceMonth)
        assertEquals(target, result.value.targetMonth)
    }

    @Test
    fun copy_budgets_command_enforces_source_and_target_month_different_invariant() {
        val month = YearMonth("2026-08")
        assertFailsWith<IllegalArgumentException> {
            CopyBudgetsCommand(sourceMonth = month, targetMonth = month)
        }
    }

    @Test
    fun command_contracts_do_not_contain_owner_or_sync_metadata() {
        val createCmd = CreateBudgetCommand(
            categoryId = EntityId("cat_1"),
            month = YearMonth("2026-08"),
            limit = Money(500000L, Currency.TRY),
        )
        assertEquals(EntityId("cat_1"), createCmd.categoryId)
        assertEquals(YearMonth("2026-08"), createCmd.month)
        assertEquals(500000L, createCmd.limit.amountMinor)

        val updateCmd = UpdateBudgetCommand(
            id = EntityId("budget_1"),
            limit = Money(600000L, Currency.TRY),
        )
        assertEquals(EntityId("budget_1"), updateCmd.id)
        assertEquals(600000L, updateCmd.limit.amountMinor)

        val deleteCmd = DeleteBudgetCommand(id = EntityId("budget_1"))
        assertEquals(EntityId("budget_1"), deleteCmd.id)

        val copyCmd = CopyBudgetsCommand(
            sourceMonth = YearMonth("2026-07"),
            targetMonth = YearMonth("2026-08"),
        )
        assertEquals(YearMonth("2026-07"), copyCmd.sourceMonth)
        assertEquals(YearMonth("2026-08"), copyCmd.targetMonth)
    }

    @Test
    fun copy_budgets_result_derives_skipped_count_and_enforces_non_negative_counters() {
        val validResult = CopyBudgetsResult(
            copiedCount = 3,
            skippedCategoryIds = listOf(EntityId("cat_skip_1"), EntityId("cat_skip_2")),
        )
        assertEquals(3, validResult.copiedCount)
        assertEquals(2, validResult.skippedCount)
        assertEquals(listOf(EntityId("cat_skip_1"), EntityId("cat_skip_2")), validResult.skippedCategoryIds)

        val emptySkipped = CopyBudgetsResult(copiedCount = 5)
        assertEquals(5, emptySkipped.copiedCount)
        assertEquals(0, emptySkipped.skippedCount)
        assertTrue(emptySkipped.skippedCategoryIds.isEmpty())

        assertFailsWith<IllegalArgumentException> {
            CopyBudgetsResult(copiedCount = -1)
        }
    }
}
