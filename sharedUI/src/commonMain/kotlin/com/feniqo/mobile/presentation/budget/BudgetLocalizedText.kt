@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.budget

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.YearMonth
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

fun formatBasisPointsRateNumber(
    basisPoints: Int,
    decimalSeparator: String,
): String {
    val absValue = kotlin.math.abs(basisPoints)
    val major = absValue / 100
    val minor = absValue % 100
    return if (minor == 0) {
        "$major"
    } else {
        val minorStr = minor.toString().padStart(2, '0').trimEnd('0')
        "$major$decimalSeparator$minorStr"
    }
}

fun formatBasisPointsRate(
    basisPoints: Int,
    decimalSeparator: String,
    percentFormat: (formattedNumber: String) -> String,
): String {
    val formattedNumber = formatBasisPointsRateNumber(basisPoints, decimalSeparator)
    val prefix = if (basisPoints < 0) "-" else ""
    return prefix + percentFormat(formattedNumber)
}

@Composable
fun formatLocalizedRateBasisPoints(basisPoints: Int): String {
    val decimalSeparator = stringResource(Res.string.budget_rate_decimal_separator)
    val formattedNumber = formatBasisPointsRateNumber(basisPoints, decimalSeparator)
    val prefix = if (basisPoints < 0) "-" else ""
    val formattedPercent = stringResource(Res.string.budget_rate_percent_format, formattedNumber)
    return prefix + formattedPercent
}

suspend fun resolveLocalizedRateBasisPoints(basisPoints: Int): String {
    val decimalSeparator = getString(Res.string.budget_rate_decimal_separator)
    val formattedNumber = formatBasisPointsRateNumber(basisPoints, decimalSeparator)
    val prefix = if (basisPoints < 0) "-" else ""
    val formattedPercent = getString(Res.string.budget_rate_percent_format, formattedNumber)
    return prefix + formattedPercent
}

@Composable
fun BudgetInsight.toLocalizedText(): String = when (this) {
    is BudgetInsight.FastestCategoryUsage -> {
        val resolvedCategoryName = if (isCategoryMissing || categoryName.isBlank()) {
            stringResource(Res.string.budget_fallback_category_name)
        } else {
            categoryName
        }
        val rateText = formatLocalizedRateBasisPoints(usageRateBasisPoints)
        stringResource(Res.string.budget_insight_fastest_usage, resolvedCategoryName, rateText)
    }
    is BudgetInsight.BudgetsInAlert -> {
        stringResource(Res.string.budget_insight_budgets_in_alert, totalBudgetsCount, alertCount)
    }
    is BudgetInsight.RemainingTotalBudget -> {
        val rateText = formatLocalizedRateBasisPoints(remainingRateBasisPoints)
        stringResource(Res.string.budget_insight_remaining_total, rateText)
    }
}

@Composable
fun BudgetFormFieldError.toLocalizedText(): String = stringResource(toStringResource())

suspend fun BudgetFormFieldError.resolveLocalizedText(): String = getString(toStringResource())

internal fun BudgetFormFieldError.toStringResource(): StringResource = when (this) {
    BudgetFormFieldError.CATEGORY_REQUIRED -> Res.string.budget_form_error_category_required
    BudgetFormFieldError.MONTH_REQUIRED -> Res.string.budget_form_error_month_required
    BudgetFormFieldError.MONTH_INVALID_FORMAT -> Res.string.budget_form_error_month_invalid_format
    BudgetFormFieldError.AMOUNT_REQUIRED -> Res.string.budget_form_error_amount_required
    BudgetFormFieldError.AMOUNT_INVALID_FORMAT -> Res.string.budget_form_error_amount_invalid_format
    BudgetFormFieldError.AMOUNT_NON_POSITIVE -> Res.string.budget_form_error_amount_non_positive
    BudgetFormFieldError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS -> Res.string.budget_form_error_amount_excessive_decimal_digits
    BudgetFormFieldError.AMOUNT_MAX_EXCEEDED -> Res.string.budget_form_error_amount_max_exceeded
    BudgetFormFieldError.SOURCE_AND_TARGET_MONTH_SAME -> Res.string.budget_form_error_source_and_target_month_same
}

@Composable
fun BudgetProgressDisplayModel.toLocalizedStatusSummary(): String {
    return if (isRemainingNegative) {
        stringResource(Res.string.budget_status_exceeded, prefixRemaining)
    } else {
        stringResource(Res.string.budget_status_remaining, prefixRemaining)
    }
}

@Composable
fun BudgetProgressDisplayModel.resolveCategoryDisplayName(): String {
    return if (isCategoryMissing) {
        stringResource(Res.string.budget_fallback_category_name)
    } else {
        categoryName
    }
}

@Composable
fun YearMonth.toLocalizedBudgetPeriod(): String {
    val monthRes = monthToMonthStringResource(month)
    return stringResource(Res.string.budget_period_month_year, stringResource(monthRes), year.toString())
}

suspend fun YearMonth.resolveLocalizedBudgetPeriod(): String {
    val monthRes = monthToMonthStringResource(month)
    return getString(Res.string.budget_period_month_year, getString(monthRes), year.toString())
}

@Composable
fun localizedShortMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.budget_short_month_january
        2 -> Res.string.budget_short_month_february
        3 -> Res.string.budget_short_month_march
        4 -> Res.string.budget_short_month_april
        5 -> Res.string.budget_short_month_may
        6 -> Res.string.budget_short_month_june
        7 -> Res.string.budget_short_month_july
        8 -> Res.string.budget_short_month_august
        9 -> Res.string.budget_short_month_september
        10 -> Res.string.budget_short_month_october
        11 -> Res.string.budget_short_month_november
        12 -> Res.string.budget_short_month_december
        else -> Res.string.budget_short_month_january
    }
    return stringResource(res)
}

@Composable
fun localizedCurrencyDisplayName(currency: Currency): String = when (currency) {
    Currency.TRY -> stringResource(Res.string.budget_currency_try)
    Currency.USD -> stringResource(Res.string.budget_currency_usd)
    Currency.EUR -> stringResource(Res.string.budget_currency_eur)
    Currency.GBP -> stringResource(Res.string.budget_currency_gbp)
}

suspend fun resolveBudgetCopyResultMessage(copiedCount: Int, skippedCount: Int): String {
    return when {
        copiedCount > 0 && skippedCount == 0 -> getString(Res.string.budget_copy_result_copied_only, copiedCount)
        copiedCount > 0 && skippedCount > 0 -> getString(Res.string.budget_copy_result_copied_and_skipped, copiedCount, skippedCount)
        copiedCount == 0 && skippedCount > 0 -> getString(Res.string.budget_copy_result_skipped_only, skippedCount)
        else -> getString(Res.string.budget_copy_result_none)
    }
}

@Composable
fun localizedBudgetCopyResultMessage(copiedCount: Int, skippedCount: Int): String {
    return when {
        copiedCount > 0 && skippedCount == 0 -> stringResource(Res.string.budget_copy_result_copied_only, copiedCount)
        copiedCount > 0 && skippedCount > 0 -> stringResource(Res.string.budget_copy_result_copied_and_skipped, copiedCount, skippedCount)
        copiedCount == 0 && skippedCount > 0 -> stringResource(Res.string.budget_copy_result_skipped_only, skippedCount)
        else -> stringResource(Res.string.budget_copy_result_none)
    }
}

internal fun monthToMonthStringResource(month: Int): StringResource = when (month) {
    1 -> Res.string.budget_month_january
    2 -> Res.string.budget_month_february
    3 -> Res.string.budget_month_march
    4 -> Res.string.budget_month_april
    5 -> Res.string.budget_month_may
    6 -> Res.string.budget_month_june
    7 -> Res.string.budget_month_july
    8 -> Res.string.budget_month_august
    9 -> Res.string.budget_month_september
    10 -> Res.string.budget_month_october
    11 -> Res.string.budget_month_november
    12 -> Res.string.budget_month_december
    else -> Res.string.budget_month_january
}

@Composable
fun budgetFormLoadingText(): String = stringResource(Res.string.budget_form_loading)

@Composable
fun budgetFormNotFoundTitleText(): String = stringResource(Res.string.budget_form_not_found_title)

@Composable
fun budgetFormNotFoundDescText(): String = stringResource(Res.string.budget_form_not_found_desc)

@Composable
fun budgetFormErrorTitleText(): String = stringResource(Res.string.budget_form_error_title)

@Composable
fun budgetFormBackActionText(): String = stringResource(Res.string.budget_form_back_action)
