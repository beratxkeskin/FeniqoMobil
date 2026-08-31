package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.YearMonth

/**
 * Bütçe form alanları ve operasyonlarına ait tipli doğrulama hata kodlarıdır.
 */
enum class BudgetValidationError {
    CATEGORY_REQUIRED,
    MONTH_REQUIRED,
    MONTH_INVALID_FORMAT,
    AMOUNT_EMPTY,
    AMOUNT_INVALID_FORMAT,
    AMOUNT_NON_POSITIVE,
    AMOUNT_EXCESSIVE_DECIMAL_DIGITS,
    AMOUNT_MAX_EXCEEDED,
    SOURCE_AND_TARGET_MONTH_SAME,
}

/**
 * Bütçe doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface BudgetValidationResult<out T> {
    data class Valid<T>(val value: T) : BudgetValidationResult<T>
    data class Invalid(val error: BudgetValidationError) : BudgetValidationResult<Nothing>
}

/**
 * Platformdan bağımsız, saf bütçe girdi doğrulama kuralları.
 * Ham UI metinlerini güvenli biçimde domain modellerine dönüştürür;
 * exception fırlatmaz ve AppError üretmez.
 */
object BudgetValidationRules {

    /**
     * Seçilmiş [EntityId] nesnesini doğrular.
     */
    fun validateCategory(categoryId: EntityId?): BudgetValidationResult<EntityId> {
        if (categoryId == null) {
            return BudgetValidationResult.Invalid(BudgetValidationError.CATEGORY_REQUIRED)
        }
        return BudgetValidationResult.Valid(categoryId)
    }

    /**
     * Ham ay metnini (YYYY-MM) doğrular ve [YearMonth] nesnesine dönüştürür.
     */
    fun validateMonth(monthInput: String?): BudgetValidationResult<YearMonth> {
        val trimmed = monthInput?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return BudgetValidationResult.Invalid(BudgetValidationError.MONTH_REQUIRED)
        }
        return runCatching { YearMonth(trimmed) }
            .map { BudgetValidationResult.Valid(it) }
            .getOrElse { BudgetValidationResult.Invalid(BudgetValidationError.MONTH_INVALID_FORMAT) }
    }

    /**
     * Ham para metnini doğrular ve [Money] nesnesine dönüştürür.
     * Ortak [MoneyAmountParser] ayrıştırıcısını kullanır; Double/Float kullanılmaz.
     */
    fun validateAmount(amountInput: String, currency: Currency): BudgetValidationResult<Money> {
        return when (val result = MoneyAmountParser.parseToMinorUnits(amountInput, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                runCatching { Money(result.amountMinor, currency) }
                    .map { BudgetValidationResult.Valid(it) }
                    .getOrElse { BudgetValidationResult.Invalid(BudgetValidationError.AMOUNT_NON_POSITIVE) }
            }
            is MoneyAmountParser.ParseResult.Invalid -> BudgetValidationResult.Invalid(
                when (result.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> BudgetValidationError.AMOUNT_EMPTY
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT -> BudgetValidationError.AMOUNT_INVALID_FORMAT
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> BudgetValidationError.AMOUNT_NON_POSITIVE
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS -> BudgetValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> BudgetValidationError.AMOUNT_MAX_EXCEEDED
                },
            )
        }
    }

    /**
     * Bütçe kopyalama işleminde kaynak ve hedef ayın farklı olduğunu doğrular.
     */
    fun validateCopyMonths(sourceMonth: YearMonth, targetMonth: YearMonth): BudgetValidationResult<CopyBudgetsCommand> {
        if (sourceMonth == targetMonth) {
            return BudgetValidationResult.Invalid(BudgetValidationError.SOURCE_AND_TARGET_MONTH_SAME)
        }
        return BudgetValidationResult.Valid(
            CopyBudgetsCommand(
                sourceMonth = sourceMonth,
                targetMonth = targetMonth,
            ),
        )
    }

    /**
     * Ham ay girdileriyle kopyalama komutunu doğrular ve [CopyBudgetsCommand] üretir.
     */
    fun validateCopyMonths(sourceMonthInput: String?, targetMonthInput: String?): BudgetValidationResult<CopyBudgetsCommand> {
        val sourceResult = validateMonth(sourceMonthInput)
        if (sourceResult is BudgetValidationResult.Invalid) return sourceResult

        val targetResult = validateMonth(targetMonthInput)
        if (targetResult is BudgetValidationResult.Invalid) return targetResult

        val source = (sourceResult as BudgetValidationResult.Valid).value
        val target = (targetResult as BudgetValidationResult.Valid).value

        return validateCopyMonths(source, target)
    }
}
