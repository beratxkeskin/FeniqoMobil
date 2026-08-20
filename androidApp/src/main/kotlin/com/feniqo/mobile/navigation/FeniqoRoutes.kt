package com.feniqo.mobile.navigation

import kotlinx.serialization.Serializable

/**
 * Android type-safe rota sözleşmelerinin kapalı üst tipidir.
 */
sealed interface FeniqoRoute

/**
 * Kimlik doğrulama akışı rotaları.
 */
@Serializable
data object LoginRoute : FeniqoRoute

@Serializable
data object RegisterRoute : FeniqoRoute

/**
 * Ana uygulama (Main) akışı altındaki sekmelerin rotaları.
 */
@Serializable
data object DashboardRoute : FeniqoRoute

@Serializable
data object TransactionsRoute : FeniqoRoute

@Serializable
data object CategoriesRoute : FeniqoRoute

@Serializable
data object SettingsRoute : FeniqoRoute

/**
 * Feniqo ana kabuğundaki (Bottom Navigation) üst seviye sekmelerin sözleşmesidir.
 * Yalnız hedef kimliğini ve type-safe rota nesnesi eşlemesini taşır;
 * UI metinleri ve ikonlar sunum katmanına aittir.
 */
enum class TopLevelDestination(val route: FeniqoRoute) {
    DASHBOARD(DashboardRoute),
    TRANSACTIONS(TransactionsRoute),
    CATEGORIES(CategoriesRoute),
    SETTINGS(SettingsRoute),
}
