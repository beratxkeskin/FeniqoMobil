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
data class TransactionFormRoute(
    val transactionId: String? = null,
) : FeniqoRoute

@Serializable
data object CategoriesRoute : FeniqoRoute

@Serializable
data class CategoryFormRoute(
    val categoryId: String? = null,
    val initialTypeCode: String = "EXPENSE",
) : FeniqoRoute

@Serializable
data object BudgetsRoute : FeniqoRoute

@Serializable
data class BudgetFormRoute(
    val initialMonth: String,
    val budgetId: String? = null,
) : FeniqoRoute

/**
 * Rota veya harici kaynaktan gelen bütçe kimliğini ayrıştırır.
 * null ise Create modu (null), dolu ve geçerli ise EntityId, boşluk/geçersiz ise hata göstergesi olarak ele alınır.
 */
sealed interface BudgetRouteIdResult {
    data object CreateMode : BudgetRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : BudgetRouteIdResult
    data object InvalidId : BudgetRouteIdResult
}

fun parseBudgetRouteId(rawId: String?): BudgetRouteIdResult {
    if (rawId == null) return BudgetRouteIdResult.CreateMode
    if (rawId.isBlank()) return BudgetRouteIdResult.InvalidId
    return runCatching { BudgetRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { BudgetRouteIdResult.InvalidId }
}

/**
 * Rota veya harici kaynaktan gelen ham ay metnini güvenli [YearMonth] nesnesine ayrıştırır.
 * Geçersiz, hatalı veya boş metinlerde verilen [fallbackMonth] değerini döndürür; exception fırlatmaz.
 */
fun parseBudgetInitialMonth(
    rawMonth: String?,
    fallbackMonth: com.feniqo.mobile.domain.model.YearMonth,
): com.feniqo.mobile.domain.model.YearMonth {
    if (rawMonth.isNullOrBlank()) return fallbackMonth
    return runCatching { com.feniqo.mobile.domain.model.YearMonth(rawMonth) }.getOrElse { fallbackMonth }
}

@Serializable
data object RecurringTransactionsRoute : FeniqoRoute

@Serializable
data class RecurringTransactionFormRoute(
    val recurringTransactionId: String? = null,
) : FeniqoRoute

/**
 * Rota veya harici kaynaktan gelen tekrarlayan işlem kimliğini ayrıştırır.
 * null ise Create modu (null), dolu ve geçerli ise EntityId, boşluk/geçersiz ise hata göstergesi olarak ele alınır.
 */
sealed interface RecurringRouteIdResult {
    data object CreateMode : RecurringRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : RecurringRouteIdResult
    data object InvalidId : RecurringRouteIdResult
}

fun parseRecurringRouteId(rawId: String?): RecurringRouteIdResult {
    if (rawId == null) return RecurringRouteIdResult.CreateMode
    if (rawId.isBlank()) return RecurringRouteIdResult.InvalidId
    return runCatching { RecurringRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { RecurringRouteIdResult.InvalidId }
}

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
    BUDGETS(BudgetsRoute),
    CATEGORIES(CategoriesRoute),
    SETTINGS(SettingsRoute),
}
