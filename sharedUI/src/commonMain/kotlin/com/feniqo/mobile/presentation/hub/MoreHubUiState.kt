package com.feniqo.mobile.presentation.hub

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money

sealed interface MoreHubCardEmptyReason {
    data object NoAssets : MoreHubCardEmptyReason
    data object NoActiveGoals : MoreHubCardEmptyReason
    data object NoActiveSubscriptions : MoreHubCardEmptyReason
    data object NoUpcomingRecurringRules : MoreHubCardEmptyReason
}

sealed interface MoreHubCardErrorReason {
    data object General : MoreHubCardErrorReason
    data object NetWorthUnreliable : MoreHubCardErrorReason
}

data class AssetsOverviewPayload(
    val totals: List<Money>,
    val assetCount: Int,
)

data class GoalsOverviewPayload(
    val activeGoalCount: Int,
)

data class SubscriptionsOverviewPayload(
    val activeSubscriptionCount: Int,
    val nearestRenewalDate: LocalDate?,
)

data class RecurringOverviewPayload(
    val upcomingRuleCount: Int,
)

/** Daha Fazla genel bakış kartlarının birbirinden bağımsız görünüm durumudur. */
sealed interface MoreHubOverviewCardState<out T> {
    data object Loading : MoreHubOverviewCardState<Nothing>

    data class Content<T>(
        val data: T,
    ) : MoreHubOverviewCardState<T>

    data class Empty(
        val reason: MoreHubCardEmptyReason,
    ) : MoreHubOverviewCardState<Nothing>

    data class Error(
        val reason: MoreHubCardErrorReason = MoreHubCardErrorReason.General,
    ) : MoreHubOverviewCardState<Nothing>
}

/** Daha Fazla ekranının Room SSOT akışlarından türetilen durumsuz sunum modeli. */
data class MoreHubUiState(
    val activeWorkspaceName: String? = null,
    val assets: MoreHubOverviewCardState<AssetsOverviewPayload> = MoreHubOverviewCardState.Loading,
    val goals: MoreHubOverviewCardState<GoalsOverviewPayload> = MoreHubOverviewCardState.Loading,
    val subscriptions: MoreHubOverviewCardState<SubscriptionsOverviewPayload> = MoreHubOverviewCardState.Loading,
    val recurringTransactions: MoreHubOverviewCardState<RecurringOverviewPayload> = MoreHubOverviewCardState.Loading,
)
