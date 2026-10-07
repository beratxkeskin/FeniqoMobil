@file:Suppress(
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.category

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.YearMonth
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategoryInsight.toLocalizedText(): String =
    when (this) {
        is CategoryInsight.TopIncome -> stringResource(
            Res.string.categories_insight_top_income,
            categoryName,
            sharePercent,
        )
        CategoryInsight.NoIncome -> stringResource(Res.string.categories_insight_no_income)
        is CategoryInsight.TopExpense -> stringResource(
            Res.string.categories_insight_top_expense,
            categoryName,
            sharePercent,
        )
        CategoryInsight.IncomeWithoutExpense -> stringResource(Res.string.categories_insight_income_without_expense)
        CategoryInsight.NoTransactions -> stringResource(Res.string.categories_insight_no_transactions)
        CategoryInsight.BalancedDistribution -> stringResource(Res.string.categories_insight_balanced_distribution)
    }

@Composable
fun CategoryBalanceMessage.toLocalizedText(): String = stringResource(toStringResource())

@Composable
fun YearMonth.toLocalizedCategoryPeriod(): String {
    val parts = value.split("-")
    val year = parts.getOrNull(0) ?: return value
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return value
    if (month !in 1..12) return value
    return stringResource(Res.string.categories_period_month_year, stringResource(month.toMonthResource()), year)
}

internal fun Int.toMonthResource(): StringResource =
    when (this) {
        1 -> Res.string.transaction_form_month_january
        2 -> Res.string.transaction_form_month_february
        3 -> Res.string.transaction_form_month_march
        4 -> Res.string.transaction_form_month_april
        5 -> Res.string.transaction_form_month_may
        6 -> Res.string.transaction_form_month_june
        7 -> Res.string.transaction_form_month_july
        8 -> Res.string.transaction_form_month_august
        9 -> Res.string.transaction_form_month_september
        10 -> Res.string.transaction_form_month_october
        11 -> Res.string.transaction_form_month_november
        12 -> Res.string.transaction_form_month_december
        else -> Res.string.category_form_icon_other
    }

private fun CategoryBalanceMessage.toStringResource(): StringResource =
    when (this) {
        CategoryBalanceMessage.EXPENSES_BALANCED -> Res.string.categories_balance_expenses
        CategoryBalanceMessage.INCOME_AND_EXPENSES_BALANCED -> Res.string.categories_balance_income_expense
        CategoryBalanceMessage.REVIEW_EXPENSES -> Res.string.categories_balance_review
    }
