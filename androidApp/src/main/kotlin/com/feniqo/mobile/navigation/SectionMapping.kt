package com.feniqo.mobile.navigation

import com.feniqo.mobile.presentation.shell.AppSection

/**
 * AppSection ile TopLevelDestination arasındaki saf ve iki yönlü eşleme fonksiyonları.
 */
fun AppSection.toTopLevelDestination(): TopLevelDestination = when (this) {
    AppSection.DASHBOARD -> TopLevelDestination.DASHBOARD
    AppSection.TRANSACTIONS -> TopLevelDestination.TRANSACTIONS
    AppSection.CATEGORIES -> TopLevelDestination.CATEGORIES
    AppSection.SETTINGS -> TopLevelDestination.SETTINGS
}

fun TopLevelDestination.toAppSection(): AppSection = when (this) {
    TopLevelDestination.DASHBOARD -> AppSection.DASHBOARD
    TopLevelDestination.TRANSACTIONS -> AppSection.TRANSACTIONS
    TopLevelDestination.CATEGORIES -> AppSection.CATEGORIES
    TopLevelDestination.SETTINGS -> AppSection.SETTINGS
}
