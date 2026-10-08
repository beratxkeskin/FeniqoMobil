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
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.validation.SubscriptionEstimatedCostSummary
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.domain.validation.SubscriptionInsight
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatusCalculator
import com.feniqo.mobile.domain.validation.SubscriptionTrendResult
import com.feniqo.mobile.presentation.common.FinanceUiMessage

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
 * Para birimi bazında normalize tahmini maliyet saf UI modelidir.
 */
data class SubscriptionEstimatedCostSummaryUiModel(
    val currency: Currency,
    val monthlyCost: Money?,
    val yearlyCost: Money?,
    val activeCount: Int,
    val isUnavailable: Boolean = false,
)

/**
 * Para birimi bazında gerçekleşen dönem harcama ve trend saf UI modelidir.
 */
data class SubscriptionActualSpendingUiModel(
    val currency: Currency,
    val currentMonthActual: Money,
    val previousMonthActual: Money,
    val monthlyTrendBasisPoints: Long?,
    val isPreviousZero: Boolean,
    val isCurrentPartial: Boolean = true,
)

/**
 * Gerçek veriye dayalı abonelik içgörü payload sözleşmesidir.
 */
sealed interface SubscriptionInsightPayload {
    data class TrialEndingSoon(
        val subscriptionName: String,
        val daysRemaining: Long,
        val trialEndDate: LocalDate,
    ) : SubscriptionInsightPayload

    data class PriceIncreases(
        val count: Int,
    ) : SubscriptionInsightPayload

    data class PausedSavings(
        val pausedCount: Int,
        val monthlyEstimatedSavings: Money,
    ) : SubscriptionInsightPayload

    data class TopCost(
        val subscriptionName: String,
        val monthlyEstimated: Money,
    ) : SubscriptionInsightPayload
}

/**
 * Gerçek veriye dayalı abonelik içgörü kartı saf UI modelidir.
 */
data class SubscriptionInsightUiModel(
    val id: String,
    val payload: SubscriptionInsightPayload,
    val isWarning: Boolean = false,
)

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

    data class Create(
        val command: CreateSubscriptionCommand,
    ) : SubscriptionsIntent

    data class Update(
        val command: UpdateSubscriptionCommand,
    ) : SubscriptionsIntent

    data class SetActive(
        val command: SetSubscriptionActiveCommand,
    ) : SubscriptionsIntent

    data class SetLifecycle(
        val command: SetSubscriptionLifecycleCommand,
    ) : SubscriptionsIntent

    data class SelectFilter(
        val filter: SubscriptionFilter,
    ) : SubscriptionsIntent

    data class SetNotificationPermissionGranted(
        val isGranted: Boolean,
    ) : SubscriptionsIntent

    data object RequestNotificationPermission : SubscriptionsIntent

    data class RequestDelete(
        val id: EntityId,
    ) : SubscriptionsIntent

    data object ConfirmDelete : SubscriptionsIntent

    data object DismissDelete : SubscriptionsIntent

    data class RequestAdvanceRenewal(
        val id: EntityId,
        val nextRenewalDate: LocalDate,
    ) : SubscriptionsIntent

    data object ConfirmAdvanceRenewal : SubscriptionsIntent

    data object DismissAdvanceRenewal : SubscriptionsIntent
}

/**
 * Abonelikler ekranı tek-seferlik (one-shot) UI olaylarıdır.
 */
sealed interface SubscriptionUiEvent {
    data class ShowMessage(
        val message: FinanceUiMessage,
    ) : SubscriptionUiEvent

    data class MutationSuccess(
        val message: FinanceUiMessage,
    ) : SubscriptionUiEvent

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
    val currency: Currency,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextRenewalDate: LocalDate,
    val isActive: Boolean,
    val isPaused: Boolean,
    val renewalStatus: SubscriptionRenewalStatus,
    val lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
    val trialEndDate: LocalDate? = null,
    val cancellationDate: LocalDate? = null,
    val accessEndDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    val hasPriceIncrease: Boolean = false,
    val previousAmount: Money? = null,
    val priceIncreaseAmount: Money? = null,
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
        val priceHistoryMap =
            priceHistories
                .groupBy { it.subscriptionId }
                .mapValues { (_, list) -> list.maxByOrNull { it.changedAt } }

        val subscriptionComparator =
            compareByDescending<SubscriptionDisplayModel> { it.isActive }
                .thenBy { it.nextRenewalDate }
                .thenBy { it.id.value }

        val mapped =
            subscriptions.map { item ->
                mapItem(
                    item = item,
                    category = item.categoryId?.let { categoryMap[it] },
                    latestPriceHistory = priceHistoryMap[item.id],
                    today = today,
                    upcomingWindowDays = upcomingWindowDays,
                )
            }
        return mapped.sortedWith(subscriptionComparator)
    }

    fun mapItem(
        item: Subscription,
        category: Category?,
        latestPriceHistory: SubscriptionPriceHistory? = null,
        today: LocalDate,
        upcomingWindowDays: Int = SubscriptionRenewalStatusCalculator.DEFAULT_UPCOMING_WINDOW_DAYS,
    ): SubscriptionDisplayModel {
        val isUnassigned = item.categoryId == null
        val isMissing = item.categoryId != null && category == null
        val categoryName = category?.name ?: ""

        val categoryColorHex = category?.color?.hex
        val categoryIconKey = category?.icon?.key

        val renewalStatus =
            SubscriptionRenewalStatusCalculator.calculate(
                subscription = item,
                today = today,
                upcomingWindowDays = upcomingWindowDays,
            )

        val hasPriceIncrease =
            latestPriceHistory?.isPriceIncreased == true &&
                latestPriceHistory.oldAmount.currency == item.amount.currency

        val previousAmount =
            if (hasPriceIncrease && latestPriceHistory != null) {
                latestPriceHistory.oldAmount
            } else {
                null
            }

        val priceIncreaseAmount =
            if (hasPriceIncrease && latestPriceHistory != null && latestPriceHistory.increaseAmountMinor > 0L) {
                Money(latestPriceHistory.increaseAmountMinor, latestPriceHistory.newAmount.currency)
            } else {
                null
            }

        val priceIncreaseBasisPoints =
            if (hasPriceIncrease) {
                latestPriceHistory?.increaseBasisPoints
            } else {
                null
            }

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
            currency = item.amount.currency,
            frequency = item.renewalRule.frequency,
            interval = item.renewalRule.interval,
            startDate = item.renewalRule.startDate,
            endDate = item.renewalRule.endDate,
            nextRenewalDate = item.nextRenewalDate,
            isActive = item.isActive,
            isPaused = item.lifecycleStatus == SubscriptionLifecycleStatus.PAUSED,
            renewalStatus = renewalStatus,
            lifecycleStatus = item.lifecycleStatus,
            trialEndDate = item.trialEndDate,
            cancellationDate = item.cancellationDate,
            accessEndDate = item.accessEndDate,
            reminderEnabled = item.reminderEnabled,
            hasPriceIncrease = hasPriceIncrease,
            previousAmount = previousAmount,
            priceIncreaseAmount = priceIncreaseAmount,
            priceIncreaseBasisPoints = priceIncreaseBasisPoints,
        )
    }

    fun mapEstimatedSummaries(summaries: Map<Currency, SubscriptionEstimatedCostSummary>): List<SubscriptionEstimatedCostSummaryUiModel> {
        val result =
            summaries.values.map { summary ->
                val isUnavailable = summary.isOverflowOrUnavailable
                SubscriptionEstimatedCostSummaryUiModel(
                    currency = summary.currency,
                    monthlyCost = if (isUnavailable) null else Money(summary.monthlyEstimatedMinor, summary.currency),
                    yearlyCost = if (isUnavailable) null else Money(summary.yearlyEstimatedMinor, summary.currency),
                    activeCount = summary.activeSubscriptionCount,
                    isUnavailable = isUnavailable,
                )
            }
        return result.sortedBy { it.currency.code }
    }

    fun mapActualSpendings(trends: Map<Currency, SubscriptionTrendResult>): List<SubscriptionActualSpendingUiModel> {
        val result =
            trends.values.map { trend ->
                SubscriptionActualSpendingUiModel(
                    currency = trend.currency,
                    currentMonthActual = Money(trend.currentMonthActualMinor, trend.currency),
                    previousMonthActual = Money(trend.previousMonthActualMinor, trend.currency),
                    monthlyTrendBasisPoints = trend.percentageBasisPoints,
                    isPreviousZero = trend.isPreviousMonthZero,
                    isCurrentPartial = trend.isCurrentMonthIncomplete,
                )
            }
        return result.sortedBy { it.currency.code }
    }

    fun mapInsights(insights: List<SubscriptionInsight>): List<SubscriptionInsightUiModel> =
        insights.mapIndexed { index, insight ->
            when (insight) {
                is SubscriptionInsight.TrialEndingSoon ->
                    SubscriptionInsightUiModel(
                        id = "trial-$index",
                        payload =
                            SubscriptionInsightPayload.TrialEndingSoon(
                                subscriptionName = insight.subscriptionName,
                                daysRemaining = insight.daysRemaining,
                                trialEndDate = insight.trialEndDate,
                            ),
                        isWarning = true,
                    )
                is SubscriptionInsight.PriceIncreases ->
                    SubscriptionInsightUiModel(
                        id = "price-inc-$index",
                        payload =
                            SubscriptionInsightPayload.PriceIncreases(
                                count = insight.count,
                            ),
                        isWarning = false,
                    )
                is SubscriptionInsight.PausedSavings ->
                    SubscriptionInsightUiModel(
                        id = "paused-$index",
                        payload =
                            SubscriptionInsightPayload.PausedSavings(
                                pausedCount = insight.pausedCount,
                                monthlyEstimatedSavings = insight.monthlyEstimatedSavings,
                            ),
                        isWarning = false,
                    )
                is SubscriptionInsight.TopCost ->
                    SubscriptionInsightUiModel(
                        id = "top-cost-$index",
                        payload =
                            SubscriptionInsightPayload.TopCost(
                                subscriptionName = insight.subscriptionName,
                                monthlyEstimated = insight.monthlyEstimated,
                            ),
                        isWarning = false,
                    )
            }
        }
}
