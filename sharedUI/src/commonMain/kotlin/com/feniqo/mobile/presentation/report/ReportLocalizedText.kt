package com.feniqo.mobile.presentation.report

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.presentation.common.localizedMonthName
import com.feniqo.mobile.presentation.common.localizedShortMonthName
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import com.feniqo.mobile.presentation.util.MoneyFormatter
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.report_day_friday
import feniqomobil.sharedui.generated.resources.report_day_monday
import feniqomobil.sharedui.generated.resources.report_day_saturday
import feniqomobil.sharedui.generated.resources.report_day_sunday
import feniqomobil.sharedui.generated.resources.report_day_thursday
import feniqomobil.sharedui.generated.resources.report_day_tuesday
import feniqomobil.sharedui.generated.resources.report_day_wednesday
import feniqomobil.sharedui.generated.resources.report_error_default_message
import feniqomobil.sharedui.generated.resources.report_filter_all_categories
import feniqomobil.sharedui.generated.resources.report_filter_period_last_month_with_month
import feniqomobil.sharedui.generated.resources.report_filter_period_this_month_with_month
import feniqomobil.sharedui.generated.resources.report_filter_type_all
import feniqomobil.sharedui.generated.resources.report_filter_type_expense
import feniqomobil.sharedui.generated.resources.report_filter_type_income
import feniqomobil.sharedui.generated.resources.report_insight_expense_decreased
import feniqomobil.sharedui.generated.resources.report_insight_expense_increased
import feniqomobil.sharedui.generated.resources.report_insight_transaction_count_plural
import feniqomobil.sharedui.generated.resources.report_comparison_preset_last_vs_two_months_ago
import feniqomobil.sharedui.generated.resources.report_comparison_preset_this_quarter_vs_last
import feniqomobil.sharedui.generated.resources.report_comparison_preset_this_vs_last_month
import feniqomobil.sharedui.generated.resources.report_comparison_preset_this_year_vs_last
import feniqomobil.sharedui.generated.resources.report_period_preset_custom
import feniqomobil.sharedui.generated.resources.report_period_preset_last_3_months
import feniqomobil.sharedui.generated.resources.report_period_preset_last_month
import feniqomobil.sharedui.generated.resources.report_period_preset_this_month
import feniqomobil.sharedui.generated.resources.report_hub_budget_performance_sub
import feniqomobil.sharedui.generated.resources.report_hub_budget_performance_title
import feniqomobil.sharedui.generated.resources.report_hub_cash_flow_sub
import feniqomobil.sharedui.generated.resources.report_hub_cash_flow_title
import feniqomobil.sharedui.generated.resources.report_hub_category_sub
import feniqomobil.sharedui.generated.resources.report_hub_category_title
import feniqomobil.sharedui.generated.resources.report_hub_debt_summary_sub
import feniqomobil.sharedui.generated.resources.report_hub_debt_summary_title
import feniqomobil.sharedui.generated.resources.report_hub_financial_insights_sub
import feniqomobil.sharedui.generated.resources.report_hub_financial_insights_title
import feniqomobil.sharedui.generated.resources.report_hub_forecast_sub
import feniqomobil.sharedui.generated.resources.report_hub_forecast_title
import feniqomobil.sharedui.generated.resources.report_hub_period_comparison_sub
import feniqomobil.sharedui.generated.resources.report_hub_period_comparison_title
import feniqomobil.sharedui.generated.resources.report_hub_spending_calendar_sub
import feniqomobil.sharedui.generated.resources.report_hub_spending_calendar_title
import feniqomobil.sharedui.generated.resources.report_hub_subscription_summary_sub
import feniqomobil.sharedui.generated.resources.report_hub_subscription_summary_title
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.feniqo.mobile.domain.model.YearMonth

@Composable
fun Money.toLocalizedMaskedText(mask: Boolean): String =
    if (mask) MoneyFormatter.MASKED_TEXT else toLocalizedFormatted()

@Composable
fun MoneyDelta.toLocalizedMaskedText(
    mask: Boolean,
    showPositiveSign: Boolean = false,
): String = if (mask) MoneyFormatter.MASKED_TEXT else toLocalizedFormatted(showPositiveSign = showPositiveSign)

fun ReportPeriodPreset.toLocalizedResource(): StringResource =
    when (this) {
        ReportPeriodPreset.THIS_MONTH -> Res.string.report_period_preset_this_month
        ReportPeriodPreset.LAST_MONTH -> Res.string.report_period_preset_last_month
        ReportPeriodPreset.LAST_3_MONTHS -> Res.string.report_period_preset_last_3_months
        ReportPeriodPreset.CUSTOM -> Res.string.report_period_preset_custom
    }

@Composable
fun ReportPeriodPreset.toLocalizedLabel(): String = stringResource(toLocalizedResource())

fun ReportTypeFilter.toLocalizedResource(): StringResource =
    when (this) {
        ReportTypeFilter.ALL -> Res.string.report_filter_type_all
        ReportTypeFilter.INCOME -> Res.string.report_filter_type_income
        ReportTypeFilter.EXPENSE -> Res.string.report_filter_type_expense
    }

@Composable
fun ReportTypeFilter.toLocalizedLabel(): String = stringResource(toLocalizedResource())

@Composable
fun DayOfWeek.toLocalizedDayName(): String {
    val res =
        when (this) {
            DayOfWeek.MONDAY -> Res.string.report_day_monday
            DayOfWeek.TUESDAY -> Res.string.report_day_tuesday
            DayOfWeek.WEDNESDAY -> Res.string.report_day_wednesday
            DayOfWeek.THURSDAY -> Res.string.report_day_thursday
            DayOfWeek.FRIDAY -> Res.string.report_day_friday
            DayOfWeek.SATURDAY -> Res.string.report_day_saturday
            DayOfWeek.SUNDAY -> Res.string.report_day_sunday
        }
    return stringResource(res)
}

@Composable
fun ActiveFilterChipUiModel.toLocalizedLabel(): String =
    when (this) {
        is ActiveFilterChipUiModel.Period -> {
            when (preset) {
                ReportPeriodPreset.THIS_MONTH -> {
                    stringResource(
                        Res.string.report_filter_period_this_month_with_month,
                        localizedMonthName(dateRange.startDate.monthNumber),
                        dateRange.startDate.year,
                    )
                }
                ReportPeriodPreset.LAST_MONTH -> {
                    stringResource(
                        Res.string.report_filter_period_last_month_with_month,
                        localizedMonthName(dateRange.startDate.monthNumber),
                        dateRange.startDate.year,
                    )
                }
                ReportPeriodPreset.LAST_3_MONTHS -> {
                    stringResource(Res.string.report_period_preset_last_3_months)
                }
                ReportPeriodPreset.CUSTOM -> {
                    val startShort = localizedShortMonthName(dateRange.startDate.monthNumber)
                    val endShort = localizedShortMonthName(dateRange.endDate.monthNumber)
                    if (dateRange.startDate.year == dateRange.endDate.year) {
                        if (dateRange.startDate.monthNumber == dateRange.endDate.monthNumber) {
                            "${dateRange.startDate.dayOfMonth} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                        } else {
                            "${dateRange.startDate.dayOfMonth} $startShort – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                        }
                    } else {
                        "${dateRange.startDate.dayOfMonth} $startShort ${dateRange.startDate.year} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                    }
                }
            }
        }
        is ActiveFilterChipUiModel.Type -> typeFilter.toLocalizedLabel()
        is ActiveFilterChipUiModel.CurrencyChip -> currency.code
        is ActiveFilterChipUiModel.CategoryChip -> categoryName
    }

@Composable
fun ReportInsightPayload.toLocalizedText(): String =
    when (this) {
        ReportInsightPayload.None -> ""
        is ReportInsightPayload.ExpenseChanged -> {
            val percentage = changeBasisPoints / 100
            val absMoney = Money(kotlin.math.abs(difference.amountMinor), difference.currency)
            if (difference.amountMinor <= 0L) {
                stringResource(
                    Res.string.report_insight_expense_decreased,
                    percentage,
                    absMoney.toLocalizedFormatted(),
                )
            } else {
                stringResource(
                    Res.string.report_insight_expense_increased,
                    percentage,
                    absMoney.toLocalizedFormatted(),
                )
            }
        }
        is ReportInsightPayload.TransactionCountOnly -> {
            pluralStringResource(
                Res.plurals.report_insight_transaction_count_plural,
                count,
                count,
            )
        }
    }

@Composable
fun ReportUiError.toLocalizedMessage(): String =
    when (this) {
        ReportUiError.Generic -> stringResource(Res.string.report_error_default_message)
    }

@Composable
fun formatLocalizedFilterSummary(
    filter: ReportFilterUiState,
    dateRange: ReportDateRange,
): String {
    val periodText =
        when (filter.periodPreset) {
            ReportPeriodPreset.THIS_MONTH -> stringResource(Res.string.report_period_preset_this_month)
            ReportPeriodPreset.LAST_MONTH -> stringResource(Res.string.report_period_preset_last_month)
            ReportPeriodPreset.LAST_3_MONTHS -> stringResource(Res.string.report_period_preset_last_3_months)
            ReportPeriodPreset.CUSTOM -> {
                val startShort = localizedShortMonthName(dateRange.startDate.monthNumber)
                val endShort = localizedShortMonthName(dateRange.endDate.monthNumber)
                if (dateRange.startDate.year == dateRange.endDate.year) {
                    if (dateRange.startDate.monthNumber == dateRange.endDate.monthNumber) {
                        "${dateRange.startDate.dayOfMonth} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                    } else {
                        "${dateRange.startDate.dayOfMonth} $startShort – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                    }
                } else {
                    "${dateRange.startDate.dayOfMonth} $startShort ${dateRange.startDate.year} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                }
            }
        }
    val typeText = filter.typeFilter.toLocalizedLabel()
    val currencyText = filter.currency.code
    val categoryText = filter.selectedCategoryName ?: stringResource(Res.string.report_filter_all_categories)

    return "$periodText • $typeText • $currencyText • $categoryText"
}

@Composable
fun YearMonth.toLocalizedMonthYear(): String {
    val monthName = localizedMonthName(monthNumber)
    return "$monthName $year"
}

fun AllReportsHubItem.toLocalizedTitleRes(): StringResource =
    when (this) {
        AllReportsHubItem.CATEGORY_BREAKDOWN -> Res.string.report_hub_category_title
        AllReportsHubItem.CASH_FLOW -> Res.string.report_hub_cash_flow_title
        AllReportsHubItem.PERIOD_COMPARISON -> Res.string.report_hub_period_comparison_title
        AllReportsHubItem.SPENDING_CALENDAR -> Res.string.report_hub_spending_calendar_title
        AllReportsHubItem.BUDGET_PERFORMANCE -> Res.string.report_hub_budget_performance_title
        AllReportsHubItem.SUBSCRIPTION_SUMMARY -> Res.string.report_hub_subscription_summary_title
        AllReportsHubItem.DEBT_SUMMARY -> Res.string.report_hub_debt_summary_title
        AllReportsHubItem.FORECAST -> Res.string.report_hub_forecast_title
        AllReportsHubItem.FINANCIAL_INSIGHTS -> Res.string.report_hub_financial_insights_title
    }

fun AllReportsHubItem.toLocalizedSubtitleRes(): StringResource =
    when (this) {
        AllReportsHubItem.CATEGORY_BREAKDOWN -> Res.string.report_hub_category_sub
        AllReportsHubItem.CASH_FLOW -> Res.string.report_hub_cash_flow_sub
        AllReportsHubItem.PERIOD_COMPARISON -> Res.string.report_hub_period_comparison_sub
        AllReportsHubItem.SPENDING_CALENDAR -> Res.string.report_hub_spending_calendar_sub
        AllReportsHubItem.BUDGET_PERFORMANCE -> Res.string.report_hub_budget_performance_sub
        AllReportsHubItem.SUBSCRIPTION_SUMMARY -> Res.string.report_hub_subscription_summary_sub
        AllReportsHubItem.DEBT_SUMMARY -> Res.string.report_hub_debt_summary_sub
        AllReportsHubItem.FORECAST -> Res.string.report_hub_forecast_sub
        AllReportsHubItem.FINANCIAL_INSIGHTS -> Res.string.report_hub_financial_insights_sub
    }

@Composable
fun formatLocalizedPeriod(preset: ReportPeriodPreset, dateRange: ReportDateRange? = null): String =
    when (preset) {
        ReportPeriodPreset.THIS_MONTH -> stringResource(Res.string.report_period_preset_this_month)
        ReportPeriodPreset.LAST_MONTH -> stringResource(Res.string.report_period_preset_last_month)
        ReportPeriodPreset.LAST_3_MONTHS -> stringResource(Res.string.report_period_preset_last_3_months)
        ReportPeriodPreset.CUSTOM -> {
            if (dateRange != null) {
                val startShort = localizedShortMonthName(dateRange.startDate.monthNumber)
                val endShort = localizedShortMonthName(dateRange.endDate.monthNumber)
                if (dateRange.startDate.year == dateRange.endDate.year) {
                    if (dateRange.startDate.monthNumber == dateRange.endDate.monthNumber) {
                        "${dateRange.startDate.dayOfMonth} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                    } else {
                        "${dateRange.startDate.dayOfMonth} $startShort – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                    }
                } else {
                    "${dateRange.startDate.dayOfMonth} $startShort ${dateRange.startDate.year} – ${dateRange.endDate.dayOfMonth} $endShort ${dateRange.endDate.year}"
                }
            } else {
                stringResource(Res.string.report_period_preset_custom)
            }
        }
    }

fun ComparisonPeriodPreset.toLocalizedResource(): StringResource =
    when (this) {
        ComparisonPeriodPreset.THIS_VS_LAST_MONTH -> Res.string.report_comparison_preset_this_vs_last_month
        ComparisonPeriodPreset.LAST_MONTH_VS_TWO_MONTHS_AGO -> Res.string.report_comparison_preset_last_vs_two_months_ago
        ComparisonPeriodPreset.THIS_QUARTER_VS_LAST -> Res.string.report_comparison_preset_this_quarter_vs_last
        ComparisonPeriodPreset.THIS_YEAR_VS_LAST -> Res.string.report_comparison_preset_this_year_vs_last
    }

@Composable
fun ComparisonPeriodPreset.toLocalizedLabel(): String = stringResource(toLocalizedResource())

@Composable
fun ReportTransactionUiItem.resolveLocalizedTitle(): String =
    if (!description.isNullOrBlank()) {
        description
    } else {
        type.toLocalizedText()
    }
