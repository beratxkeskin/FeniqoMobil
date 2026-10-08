package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.LocalDate
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.budget_month_april
import feniqomobil.sharedui.generated.resources.budget_month_august
import feniqomobil.sharedui.generated.resources.budget_month_december
import feniqomobil.sharedui.generated.resources.budget_month_february
import feniqomobil.sharedui.generated.resources.budget_month_january
import feniqomobil.sharedui.generated.resources.budget_month_july
import feniqomobil.sharedui.generated.resources.budget_month_june
import feniqomobil.sharedui.generated.resources.budget_month_march
import feniqomobil.sharedui.generated.resources.budget_month_may
import feniqomobil.sharedui.generated.resources.budget_month_november
import feniqomobil.sharedui.generated.resources.budget_month_october
import feniqomobil.sharedui.generated.resources.budget_month_september
import feniqomobil.sharedui.generated.resources.budget_short_month_april
import feniqomobil.sharedui.generated.resources.budget_short_month_august
import feniqomobil.sharedui.generated.resources.budget_short_month_december
import feniqomobil.sharedui.generated.resources.budget_short_month_february
import feniqomobil.sharedui.generated.resources.budget_short_month_january
import feniqomobil.sharedui.generated.resources.budget_short_month_july
import feniqomobil.sharedui.generated.resources.budget_short_month_june
import feniqomobil.sharedui.generated.resources.budget_short_month_march
import feniqomobil.sharedui.generated.resources.budget_short_month_may
import feniqomobil.sharedui.generated.resources.budget_short_month_november
import feniqomobil.sharedui.generated.resources.budget_short_month_october
import feniqomobil.sharedui.generated.resources.budget_short_month_september
import feniqomobil.sharedui.generated.resources.common_language_code
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedMonthName(month: Int): String {
    val res = when (month) {
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
    return stringResource(res)
}

suspend fun resolveLocalizedMonthName(month: Int): String {
    val res = when (month) {
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
    return getString(res)
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

suspend fun resolveLocalizedShortMonthName(month: Int): String {
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
    return getString(res)
}

@Composable
fun LocalDate.toLocalizedReadableDate(): String {
    val monthName = localizedMonthName(monthNumber)
    val languageCode = stringResource(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $monthName $year"
    } else {
        "$monthName $day, $year"
    }
}

suspend fun LocalDate.resolveLocalizedReadableDate(): String {
    val monthName = resolveLocalizedMonthName(monthNumber)
    val languageCode = getString(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $monthName $year"
    } else {
        "$monthName $day, $year"
    }
}

@Composable
fun LocalDate.toLocalizedShortReadableDate(): String {
    val shortMonth = localizedShortMonthName(monthNumber)
    val languageCode = stringResource(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $shortMonth"
    } else {
        "$shortMonth $day"
    }
}

suspend fun LocalDate.resolveLocalizedShortReadableDate(): String {
    val shortMonth = resolveLocalizedShortMonthName(monthNumber)
    val languageCode = getString(Res.string.common_language_code)
    return if (languageCode == "tr") {
        "$day $shortMonth"
    } else {
        "$shortMonth $day"
    }
}
