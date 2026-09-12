package com.feniqo.mobile.navigation

import com.feniqo.mobile.presentation.shell.AppSection

/**
 * AppSection ile TopLevelDestination arasındaki saf ve iki yönlü eşleme fonksiyonları.
 */
fun AppSection.toTopLevelDestination(): TopLevelDestination = when (this) {
    AppSection.DASHBOARD -> TopLevelDestination.DASHBOARD
    AppSection.TRANSACTIONS -> TopLevelDestination.TRANSACTIONS
    AppSection.BUDGET -> TopLevelDestination.BUDGET
    AppSection.MORE -> TopLevelDestination.MORE
}

fun TopLevelDestination.toAppSection(): AppSection = when (this) {
    TopLevelDestination.DASHBOARD -> AppSection.DASHBOARD
    TopLevelDestination.TRANSACTIONS -> AppSection.TRANSACTIONS
    TopLevelDestination.BUDGET -> AppSection.BUDGET
    TopLevelDestination.MORE -> AppSection.MORE
}
