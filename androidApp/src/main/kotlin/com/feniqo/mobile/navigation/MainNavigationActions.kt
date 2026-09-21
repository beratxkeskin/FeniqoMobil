package com.feniqo.mobile.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

internal fun NavHostController.navigateToSection(section: TopLevelDestination) {
    navigate(section.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // Kök ekran zaten yığında kalır; ona eşlenen eski alt akış geri yüklenmemeli.
        restoreState = section.route != DashboardRoute
    }
}

internal fun NavHostController.closeTransactionSuccess() {
    navigate(TransactionsRoute()) {
        // Tamamlanan form/başarı akışı sekme geçmişinde saklanmaz.
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
