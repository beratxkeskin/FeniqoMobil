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
data object WelcomeRoute : FeniqoRoute

@Serializable
data object LoginRoute : FeniqoRoute

@Serializable
data object RegisterRoute : FeniqoRoute

@Serializable
data class AuthEmailVerificationRoute(
    val email: String,
    val isRequiredOnLogin: Boolean = false,
) : FeniqoRoute

@Serializable
data object ForgotPasswordRoute : FeniqoRoute

@Serializable
data class PasswordResetSentRoute(
    val email: String,
) : FeniqoRoute

@Serializable
data object ResetPasswordRoute : FeniqoRoute

@Serializable
data object PasswordResetSuccessRoute : FeniqoRoute

/**
 * Ana uygulama (Main) akışı altındaki sekmelerin rotaları.
 */
@Serializable
data object DashboardRoute : FeniqoRoute

@Serializable
data class TransactionsRoute(
    val categoryId: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
) : FeniqoRoute

/**
 * Bütçe detayından seçili kategori ve bütçe ayının başlangıç/bitiş tarihleriyle
 * filtrelenmiş [TransactionsRoute] oluşturan üretim fonksiyonu.
 */
fun createBudgetTransactionsRoute(categoryId: String, month: String): TransactionsRoute {
    val yearMonth = com.feniqo.mobile.domain.model.YearMonth(month)
    val (currentPeriod, _) = com.feniqo.mobile.presentation.category.CategoryAnalyticsCalculator.calculateReportPeriods(yearMonth)
    return TransactionsRoute(
        categoryId = categoryId,
        startDate = currentPeriod.startDate.toString(),
        endDate = currentPeriod.endDate.toString(),
    )
}

fun createBudgetTransactionsRoute(
    categoryId: com.feniqo.mobile.domain.model.EntityId,
    month: com.feniqo.mobile.domain.model.YearMonth,
): TransactionsRoute = createBudgetTransactionsRoute(categoryId.value, month.value)


@Serializable
data class TransactionFormRoute(
    val transactionId: String? = null,
    val initialTypeCode: String = "EXPENSE",
) : FeniqoRoute

@Serializable
data class TransactionSuccessRoute(
    val transactionId: String,
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

@Serializable
data class BudgetDetailRoute(
    val budgetId: String,
    val month: String,
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
data object SubscriptionsRoute : FeniqoRoute

@Serializable
data class SubscriptionFormRoute(
    val subscriptionId: String? = null,
) : FeniqoRoute

@Serializable
data class SubscriptionDetailRoute(
    val subscriptionId: String,
) : FeniqoRoute

/**
 * Rota veya harici kaynaktan gelen abonelik kimliğini ayrıştırır.
 * null ise Create modu (null), dolu ve geçerli ise EntityId, boşluk/geçersiz ise hata göstergesi olarak ele alınır.
 */
sealed interface SubscriptionRouteIdResult {
    data object CreateMode : SubscriptionRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : SubscriptionRouteIdResult
    data object InvalidId : SubscriptionRouteIdResult
}

fun parseSubscriptionRouteId(rawId: String?): SubscriptionRouteIdResult {
    if (rawId == null) return SubscriptionRouteIdResult.CreateMode
    if (rawId.isBlank()) return SubscriptionRouteIdResult.InvalidId
    return runCatching { SubscriptionRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { SubscriptionRouteIdResult.InvalidId }
}

@Serializable
data object GoalsRoute : FeniqoRoute

@Serializable
data class GoalDetailRoute(val goalId: String) : FeniqoRoute

@Serializable
data class GoalFormRoute(val goalId: String? = null) : FeniqoRoute

sealed interface GoalRouteIdResult {
    data object CreateMode : GoalRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : GoalRouteIdResult
    data object InvalidId : GoalRouteIdResult
}

fun parseGoalRouteId(rawId: String?): GoalRouteIdResult {
    if (rawId == null) return GoalRouteIdResult.CreateMode
    if (rawId.isBlank()) return GoalRouteIdResult.InvalidId
    return runCatching { GoalRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { GoalRouteIdResult.InvalidId }
}

@Serializable
data class GoalContributionFormRoute(val goalId: String, val initialDirectionCode: String? = null) : FeniqoRoute

sealed interface ChildRouteIdResult {
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : ChildRouteIdResult
    data object InvalidId : ChildRouteIdResult
}

fun parseGoalContributionRouteId(rawId: String?): ChildRouteIdResult {
    if (rawId.isNullOrBlank()) return ChildRouteIdResult.InvalidId
    return runCatching { ChildRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { ChildRouteIdResult.InvalidId }
}

@Serializable
data object DebtsRoute : FeniqoRoute

@Serializable
data class DebtFormRoute(val debtId: String? = null) : FeniqoRoute

sealed interface DebtRouteIdResult {
    data object CreateMode : DebtRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : DebtRouteIdResult
    data object InvalidId : DebtRouteIdResult
}

fun parseDebtRouteId(rawId: String?): DebtRouteIdResult {
    if (rawId == null) return DebtRouteIdResult.CreateMode
    if (rawId.isBlank()) return DebtRouteIdResult.InvalidId
    return runCatching { DebtRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { DebtRouteIdResult.InvalidId }
}

@Serializable
data class DebtPaymentFormRoute(val debtId: String) : FeniqoRoute

fun parseDebtPaymentRouteId(rawId: String?): ChildRouteIdResult {
    if (rawId.isNullOrBlank()) return ChildRouteIdResult.InvalidId
    return runCatching { ChildRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { ChildRouteIdResult.InvalidId }
}

@Serializable
data object DebtSnowballPlanRoute : FeniqoRoute

@Serializable
data object PlanRoute : FeniqoRoute





@Serializable
data object MoreRoute : FeniqoRoute

@Serializable
data object ReportsRoute : FeniqoRoute

@Serializable
data object PeriodSummaryReportRoute : FeniqoRoute

@Serializable
data object AllReportsHubRoute : FeniqoRoute

@Serializable
data object CategoryBreakdownReportRoute : FeniqoRoute

@Serializable
data class CategoryDetailReportRoute(val categoryId: String, val categoryName: String) : FeniqoRoute

@Serializable
data object CashFlowReportRoute : FeniqoRoute

@Serializable
data object PeriodComparisonReportRoute : FeniqoRoute

@Serializable
data object SpendingCalendarReportRoute : FeniqoRoute

@Serializable
data object BudgetPerformanceReportRoute : FeniqoRoute

@Serializable
data object SubscriptionSummaryReportRoute : FeniqoRoute

@Serializable
data object DebtSummaryReportRoute : FeniqoRoute

@Serializable
data object ForecastReportRoute : FeniqoRoute

@Serializable
data object FinancialInsightsReportRoute : FeniqoRoute

@Serializable
data object CustomDateRangeRoute : FeniqoRoute

@Serializable
data object MultiCurrencyReportRoute : FeniqoRoute

@Serializable
data object ReportSystemStatusRoute : FeniqoRoute

@Serializable
data object ProfileRoute : FeniqoRoute

@Serializable
data object AssetsRoute : FeniqoRoute

@Serializable
data class AssetDetailRoute(
    val assetId: String,
) : FeniqoRoute

@Serializable
data class AssetDistributionRoute(
    val initialCurrencyCode: String? = null,
) : FeniqoRoute

@Serializable
data class AssetFormRoute(
    val assetId: String? = null,
) : FeniqoRoute

sealed interface AssetRouteIdResult {
    data object CreateMode : AssetRouteIdResult
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : AssetRouteIdResult
    data object InvalidId : AssetRouteIdResult
}

fun parseAssetRouteId(rawId: String?): AssetRouteIdResult {
    if (rawId == null) return AssetRouteIdResult.CreateMode
    if (rawId.isBlank()) return AssetRouteIdResult.InvalidId
    return runCatching {
        AssetRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim()))
    }.getOrElse { AssetRouteIdResult.InvalidId }
}

@Serializable
data object SettingsRoute : FeniqoRoute

@Serializable
data object AccountRoute : FeniqoRoute

@Serializable
data object PersonalInfoRoute : FeniqoRoute

@Serializable
data object AppearanceRoute : FeniqoRoute

@Serializable
data object LanguageRegionRoute : FeniqoRoute

@Serializable
data object NotificationsSettingsRoute : FeniqoRoute

@Serializable
data object SecurityPrivacyRoute : FeniqoRoute

@Serializable
data object DataManagementRoute : FeniqoRoute

@Serializable
data object SyncStatusRoute : FeniqoRoute

@Serializable
data object HelpAboutRoute : FeniqoRoute

@Serializable
data object ChangeEmailRoute : FeniqoRoute

@Serializable
data class EmailVerificationRoute(val newEmail: String) : FeniqoRoute

@Serializable
data object ChangePasswordRoute : FeniqoRoute

@Serializable
data object DeleteAccountRoute : FeniqoRoute

@Serializable
data class FeedbackRoute(val isBug: Boolean = false) : FeniqoRoute

@Serializable
data class PhotoPreviewRoute(val draftFileName: String) : FeniqoRoute

@Serializable
data object EmailSettingsRoute : FeniqoRoute

@Serializable
data object HelpCenterRoute : FeniqoRoute

@Serializable
data class HelpArticleStatusRoute(val articleTitle: String) : FeniqoRoute

@Serializable
data object LegalInfoRoute : FeniqoRoute

@Serializable
data object WorkspacePickerRoute : FeniqoRoute

@Serializable
data object WorkspaceCreateRoute : FeniqoRoute

@Serializable
data object WorkspaceJoinRoute : FeniqoRoute

@Serializable
data class WorkspaceDetailsRoute(
    val workspaceId: String,
) : FeniqoRoute

sealed interface WorkspaceDetailsRouteIdResult {
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : WorkspaceDetailsRouteIdResult
    data object InvalidId : WorkspaceDetailsRouteIdResult
}

fun parseWorkspaceDetailsRouteId(rawId: String?): WorkspaceDetailsRouteIdResult {
    if (rawId.isNullOrBlank()) return WorkspaceDetailsRouteIdResult.InvalidId
    return runCatching { WorkspaceDetailsRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { WorkspaceDetailsRouteIdResult.InvalidId }
}

@Serializable
data class WorkspaceSettlementRoute(
    val workspaceId: String,
) : FeniqoRoute

sealed interface WorkspaceSettlementRouteIdResult {
    data class ValidId(val id: com.feniqo.mobile.domain.model.EntityId) : WorkspaceSettlementRouteIdResult
    data object InvalidId : WorkspaceSettlementRouteIdResult
}

fun parseWorkspaceSettlementRouteId(rawId: String?): WorkspaceSettlementRouteIdResult {
    if (rawId.isNullOrBlank()) return WorkspaceSettlementRouteIdResult.InvalidId
    return runCatching { WorkspaceSettlementRouteIdResult.ValidId(com.feniqo.mobile.domain.model.EntityId(rawId.trim())) }
        .getOrElse { WorkspaceSettlementRouteIdResult.InvalidId }
}


/**
 * Feniqo ana kabuğundaki (Bottom Navigation) üst seviye sekmelerin sözleşmesidir.
 * Yalnız hedef kimliğini ve type-safe rota nesnesi eşlemesini taşır;
 * UI metinleri ve ikonlar sunum katmanına aittir.
 * 4 gerçek sekmeyi temsil eder: DASHBOARD, TRANSACTIONS, BUDGET, MORE.
 */
enum class TopLevelDestination(val route: FeniqoRoute) {
    DASHBOARD(DashboardRoute),
    TRANSACTIONS(TransactionsRoute()),
    BUDGET(BudgetsRoute),
    MORE(MoreRoute),
}
