package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.SetSubscriptionLifecycleCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.validation.SubscriptionAnalyticsCalculator
import com.feniqo.mobile.domain.validation.SubscriptionEstimatedCostSummary
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.domain.validation.SubscriptionInsight
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatusCalculator
import com.feniqo.mobile.domain.validation.SubscriptionTrendResult
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModelMapper
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Abonelik yenileme ilerletme onay hedefidir.
 */
data class PendingAdvanceRenewalTarget(
    val id: EntityId,
    val nextRenewalDate: LocalDate,
)

/**
 * Abonelik mutasyon durum modelidir.
 */
data class SubscriptionMutationState(
    val isSubmitting: Boolean = false,
    val pendingDeleteId: EntityId? = null,
    val pendingAdvanceRenewal: PendingAdvanceRenewalTarget? = null,
)

/**
 * Para birimi bazında normalize tahmini maliyet UI modeli.
 */
data class SubscriptionEstimatedCostSummaryUiModel(
    val currency: Currency,
    val formattedMonthlyCost: String,
    val formattedYearlyCost: String,
    val activeCount: Int,
    val isUnavailable: Boolean = false,
)

/**
 * Para birimi bazında gerçekleşen dönem harcama ve trend UI modeli.
 */
data class SubscriptionActualSpendingUiModel(
    val currency: Currency,
    val currentMonthActualFormatted: String,
    val previousMonthActualFormatted: String,
    val monthlyTrendBasisPoints: Long?,
    val isPreviousZero: Boolean,
    val isCurrentPartial: Boolean = true,
)

/**
 * Gerçek veriye dayalı abonelik içgörü kartı UI modeli.
 */
data class SubscriptionInsightUiModel(
    val id: String,
    val title: String,
    val description: String,
    val badgeText: String? = null,
    val isWarning: Boolean = false,
)

/**
 * Abonelik filtre ekran başlığı uzantısı.
 */
val SubscriptionFilter.displayName: String
    get() = when (this) {
        SubscriptionFilter.ALL -> "Tümü"
        SubscriptionFilter.ACTIVE -> "Aktif"
        SubscriptionFilter.UPCOMING -> "Yaklaşan"
        SubscriptionFilter.OVERDUE -> "Gecikmiş"
        SubscriptionFilter.PAUSED -> "Duraklatıldı"
        SubscriptionFilter.CANCELLED -> "İptal"
        SubscriptionFilter.TRIAL -> "Deneme"
    }

/**
 * Abonelikler liste ekranı zengin UI durum modelidir.
 */
data class SubscriptionsUiState(
    val isLoading: Boolean = true,
    val items: List<SubscriptionDisplayModel> = emptyList(),
    val filteredItems: List<SubscriptionDisplayModel> = emptyList(),
    val selectedFilter: SubscriptionFilter = SubscriptionFilter.ALL,
    val estimatedSummaries: List<SubscriptionEstimatedCostSummaryUiModel> = emptyList(),
    val actualSpendings: List<SubscriptionActualSpendingUiModel> = emptyList(),
    val upcomingPayments: List<SubscriptionDisplayModel> = emptyList(),
    val overduePayments: List<SubscriptionDisplayModel> = emptyList(),
    val insights: List<SubscriptionInsightUiModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
    val mutationState: SubscriptionMutationState = SubscriptionMutationState(),
    val activeWorkspaceName: String? = null,
    val isNotificationPermissionGranted: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && items.isEmpty()
    val totalActiveCount: Int get() = items.count { it.isActive }
    val upcomingCount: Int get() = upcomingPayments.size
}

/**
 * Abonelikler ekranı MVI kullanıcı niyetleridir.
 */
sealed interface SubscriptionsIntent {
    data object Retry : SubscriptionsIntent
    data class Create(val command: CreateSubscriptionCommand) : SubscriptionsIntent
    data class Update(val command: UpdateSubscriptionCommand) : SubscriptionsIntent
    data class SetActive(val command: SetSubscriptionActiveCommand) : SubscriptionsIntent
    data class SetLifecycle(val command: SetSubscriptionLifecycleCommand) : SubscriptionsIntent
    data class SelectFilter(val filter: SubscriptionFilter) : SubscriptionsIntent
    data class SetNotificationPermissionGranted(val isGranted: Boolean) : SubscriptionsIntent
    data object RequestNotificationPermission : SubscriptionsIntent
    data class RequestDelete(val id: EntityId) : SubscriptionsIntent
    data object ConfirmDelete : SubscriptionsIntent
    data object DismissDelete : SubscriptionsIntent
    data class RequestAdvanceRenewal(val id: EntityId, val nextRenewalDate: LocalDate) : SubscriptionsIntent
    data object ConfirmAdvanceRenewal : SubscriptionsIntent
    data object DismissAdvanceRenewal : SubscriptionsIntent
}

/**
 * Abonelikler ekranı tek-seferlik (one-shot) UI olaylarıdır.
 */
sealed interface SubscriptionUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : SubscriptionUiEvent
    data class MutationSuccess(val message: FinanceUiMessage) : SubscriptionUiEvent
    data object RequestNotificationPermission : SubscriptionUiEvent
}

/**
 * Abonelik saf presentation modelidir.
 */
data class SubscriptionDisplayModel(
    val id: EntityId,
    val name: String,
    val categoryId: EntityId?,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryIconKey: String?,
    val isCategoryUnassigned: Boolean,
    val isCategoryMissing: Boolean,
    val amount: Money,
    val formattedAmount: String,
    val currency: Currency,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val formattedFrequency: String,
    val startDate: LocalDate,
    val formattedStartDate: String,
    val endDate: LocalDate?,
    val formattedEndDate: String?,
    val nextRenewalDate: LocalDate,
    val formattedNextRenewalDate: String,
    val isActive: Boolean,
    val isPaused: Boolean,
    val renewalStatus: SubscriptionRenewalStatus,
    val lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
    val trialEndDate: LocalDate? = null,
    val formattedTrialEndDate: String? = null,
    val cancellationDate: LocalDate? = null,
    val accessEndDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    val hasPriceIncrease: Boolean = false,
    val previousAmountFormatted: String? = null,
    val priceIncreaseFormatted: String? = null,
    val priceIncreaseBasisPoints: Long? = null,
)

/**
 * Domain Subscription listesini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object SubscriptionDisplayModelMapper {

    fun map(
        subscriptions: List<Subscription>,
        categories: List<Category>,
        priceHistories: List<SubscriptionPriceHistory> = emptyList(),
        today: LocalDate,
        upcomingWindowDays: Int = SubscriptionRenewalStatusCalculator.DEFAULT_UPCOMING_WINDOW_DAYS,
    ): List<SubscriptionDisplayModel> {
        if (subscriptions.isEmpty()) return emptyList()

        val categoryMap = categories.associateBy { it.id }
        val priceHistoryMap = priceHistories
            .groupBy { it.subscriptionId }
            .mapValues { (_, list) -> list.maxByOrNull { it.changedAt } }

        return subscriptions
            .map { item ->
                mapItem(
                    item = item,
                    category = item.categoryId?.let { categoryMap[it] },
                    latestPriceHistory = priceHistoryMap[item.id],
                    today = today,
                    upcomingWindowDays = upcomingWindowDays,
                )
            }
            .sortedWith(
                compareByDescending<SubscriptionDisplayModel> { it.isActive }
                    .thenBy { it.nextRenewalDate }
                    .thenBy { it.id.value },
            )
    }

    fun mapItem(
        item: Subscription,
        category: Category?,
        latestPriceHistory: SubscriptionPriceHistory? = null,
        today: LocalDate,
        upcomingWindowDays: Int = SubscriptionRenewalStatusCalculator.DEFAULT_UPCOMING_WINDOW_DAYS,
    ): SubscriptionDisplayModel {
        val (categoryName, isUnassigned, isMissing) = when {
            item.categoryId == null -> Triple("Kategorisiz", true, false)
            category == null -> Triple("Bilinmeyen Kategori", false, true)
            else -> Triple(category.name, false, false)
        }

        val categoryColorHex = category?.color?.hex
        val categoryIconKey = category?.icon?.key

        val formattedAmount = MoneyFormatter.format(
            money = item.amount,
            includeSign = false,
        )

        val formattedFrequency = RecurringTransactionDisplayModelMapper.formatRecurrenceSummary(
            frequency = item.renewalRule.frequency,
            interval = item.renewalRule.interval,
        )

        val renewalStatus = SubscriptionRenewalStatusCalculator.calculate(
            subscription = item,
            today = today,
            upcomingWindowDays = upcomingWindowDays,
        )

        val hasPriceIncrease = latestPriceHistory?.isPriceIncreased == true &&
            latestPriceHistory.oldAmount.currency == item.amount.currency

        val previousAmountFormatted = if (hasPriceIncrease && latestPriceHistory != null) {
            MoneyFormatter.format(
                money = latestPriceHistory.oldAmount,
                includeSign = false,
            )
        } else null

        val priceIncreaseFormatted = if (hasPriceIncrease && latestPriceHistory != null && latestPriceHistory.increaseAmountMinor > 0L) {
            MoneyFormatter.format(
                money = Money(latestPriceHistory.increaseAmountMinor, latestPriceHistory.newAmount.currency),
                includeSign = true,
            )
        } else null

        val priceIncreaseBasisPoints = if (hasPriceIncrease) {
            latestPriceHistory?.increaseBasisPoints
        } else null

        return SubscriptionDisplayModel(
            id = item.id,
            name = item.name,
            categoryId = item.categoryId,
            categoryName = categoryName,
            categoryColorHex = categoryColorHex,
            categoryIconKey = categoryIconKey,
            isCategoryUnassigned = isUnassigned,
            isCategoryMissing = isMissing,
            amount = item.amount,
            formattedAmount = formattedAmount,
            currency = item.amount.currency,
            frequency = item.renewalRule.frequency,
            interval = item.renewalRule.interval,
            formattedFrequency = formattedFrequency,
            startDate = item.renewalRule.startDate,
            formattedStartDate = DateFormatter.formatReadableDate(item.renewalRule.startDate),
            endDate = item.renewalRule.endDate,
            formattedEndDate = item.renewalRule.endDate?.let { DateFormatter.formatReadableDate(it) },
            nextRenewalDate = item.nextRenewalDate,
            formattedNextRenewalDate = DateFormatter.formatReadableDate(item.nextRenewalDate),
            isActive = item.isActive,
            isPaused = item.lifecycleStatus == SubscriptionLifecycleStatus.PAUSED,
            renewalStatus = renewalStatus,
            lifecycleStatus = item.lifecycleStatus,
            trialEndDate = item.trialEndDate,
            formattedTrialEndDate = item.trialEndDate?.let { DateFormatter.formatReadableDate(it) },
            cancellationDate = item.cancellationDate,
            accessEndDate = item.accessEndDate,
            reminderEnabled = item.reminderEnabled,
            hasPriceIncrease = hasPriceIncrease,
            previousAmountFormatted = previousAmountFormatted,
            priceIncreaseFormatted = priceIncreaseFormatted,
            priceIncreaseBasisPoints = priceIncreaseBasisPoints,
        )
    }

    fun mapEstimatedSummaries(
        summaries: Map<Currency, SubscriptionEstimatedCostSummary>,
    ): List<SubscriptionEstimatedCostSummaryUiModel> {
        return summaries.values.map { summary ->
            SubscriptionEstimatedCostSummaryUiModel(
                currency = summary.currency,
                formattedMonthlyCost = if (summary.isOverflowOrUnavailable) {
                    "Geçersiz"
                } else {
                    MoneyFormatter.format(Money(summary.monthlyEstimatedMinor, summary.currency), includeSign = false)
                },
                formattedYearlyCost = if (summary.isOverflowOrUnavailable) {
                    "Geçersiz"
                } else {
                    MoneyFormatter.format(Money(summary.yearlyEstimatedMinor, summary.currency), includeSign = false)
                },
                activeCount = summary.activeSubscriptionCount,
                isUnavailable = summary.isOverflowOrUnavailable,
            )
        }.sortedBy { it.currency.code }
    }

    fun mapActualSpendings(
        trends: Map<Currency, SubscriptionTrendResult>,
    ): List<SubscriptionActualSpendingUiModel> {
        return trends.values.map { trend ->
            SubscriptionActualSpendingUiModel(
                currency = trend.currency,
                currentMonthActualFormatted = MoneyFormatter.format(
                    Money(trend.currentMonthActualMinor, trend.currency),
                    includeSign = false,
                ),
                previousMonthActualFormatted = MoneyFormatter.format(
                    Money(trend.previousMonthActualMinor, trend.currency),
                    includeSign = false,
                ),
                monthlyTrendBasisPoints = trend.percentageBasisPoints,
                isPreviousZero = trend.isPreviousMonthZero,
                isCurrentPartial = trend.isCurrentMonthIncomplete,
            )
        }.sortedBy { it.currency.code }
    }

    fun mapInsights(
        insights: List<SubscriptionInsight>,
    ): List<SubscriptionInsightUiModel> {
        return insights.mapIndexed { index, insight ->
            when (insight) {
                is SubscriptionInsight.TrialEndingSoon -> SubscriptionInsightUiModel(
                    id = "trial-$index",
                    title = "Deneme Süresi Bitiyor",
                    description = "${insight.subscriptionName} deneme süresi ${insight.daysRemaining} gün sonra (${DateFormatter.formatReadableDate(insight.trialEndDate)}) sona eriyor.",
                    badgeText = "${insight.daysRemaining} gün kaldı",
                    isWarning = true,
                )
                is SubscriptionInsight.PriceIncreases -> SubscriptionInsightUiModel(
                    id = "price-inc-$index",
                    title = "Fiyat Artışı",
                    description = "${insight.count} aboneliğinizde fiyat artışı tespit edildi. Detayları listeden inceleyebilirsiniz.",
                    badgeText = "${insight.count} servis",
                    isWarning = false,
                )
                is SubscriptionInsight.PausedSavings -> SubscriptionInsightUiModel(
                    id = "paused-$index",
                    title = "Durdurulan Abonelikler",
                    description = "${insight.pausedCount} duraklatılmış abonelik ile aylık tahmini ${MoneyFormatter.format(insight.monthlyEstimatedSavings, includeSign = false)} tasarruf sağlıyorsunuz.",
                    badgeText = "${insight.pausedCount} duraklatıldı",
                    isWarning = false,
                )
                is SubscriptionInsight.TopCost -> SubscriptionInsightUiModel(
                    id = "top-cost-$index",
                    title = "En Yüksek Maliyetli Plan",
                    description = "${insight.subscriptionName} aylık tahmini ${MoneyFormatter.format(insight.monthlyEstimated, includeSign = false)} ile en yüksek abonelik harcamanız.",
                    badgeText = "Aylık En Yüksek",
                    isWarning = false,
                )
            }
        }
    }
}
