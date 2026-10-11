package com.feniqo.mobile.presentation.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.usecase.CalculateNetWorthUseCase
import com.feniqo.mobile.domain.usecase.NetWorthCalculationResult
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveAssetsUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalsUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.validation.RecurrenceScheduleCalculator
import com.feniqo.mobile.domain.validation.GoalProgressCalculator
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import javax.inject.Inject

/** Room SSOT tabanlı Daha Fazla özetlerini kart bazında izler. */
@HiltViewModel
class MoreHubViewModel @Inject constructor(
    private val observeAssetsUseCase: ObserveAssetsUseCase,
    private val calculateNetWorthUseCase: CalculateNetWorthUseCase,
    private val observeGoalsUseCase: ObserveGoalsUseCase,
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val observeRecurringTransactionsUseCase: ObserveRecurringTransactionsUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    private val assets = observeCard(observeAssetsUseCase()) { items ->
        if (items.isEmpty()) return@observeCard MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoAssets)
        when (val netWorth = calculateNetWorthUseCase(items)) {
            is NetWorthCalculationResult.Success -> MoreHubOverviewCardState.Content(
                AssetsOverviewPayload(
                    totals = netWorth.totals,
                    assetCount = netWorth.assetCount,
                ),
            )
            NetWorthCalculationResult.InvalidAssetValue,
            NetWorthCalculationResult.Overflow,
            -> MoreHubOverviewCardState.Error(MoreHubCardErrorReason.NetWorthUnreliable)
        }
    }

    private val goals = observeCard(observeGoalsUseCase()) { items ->
        val activeCount = items.count {
            GoalProgressCalculator.calculateProgress(it.targetAmount, it.currentAmount).status == GoalStatus.IN_PROGRESS
        }
        if (activeCount == 0) {
            MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoActiveGoals)
        } else {
            MoreHubOverviewCardState.Content(
                GoalsOverviewPayload(activeGoalCount = activeCount),
            )
        }
    }

    private val subscriptions = observeCard(observeSubscriptionsUseCase()) { items ->
        val active = items.filter { it.isActive }
        if (active.isEmpty()) {
            MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoActiveSubscriptions)
        } else {
            val nearest = active.minByOrNull { it.nextRenewalDate }?.nextRenewalDate
            MoreHubOverviewCardState.Content(
                SubscriptionsOverviewPayload(
                    activeSubscriptionCount = active.size,
                    nearestRenewalDate = nearest,
                ),
            )
        }
    }

    private val recurringTransactions = observeCard(observeRecurringTransactionsUseCase()) { items ->
        val today = currentDateProvider.today()
        val throughDate = today.plus(DatePeriod(days = UPCOMING_DAYS))
        val upcomingRuleCount = items.count { recurring ->
            hasOccurrenceInWindow(
                recurring = recurring,
                fromDate = today,
                throughDate = throughDate,
            )
        }
        if (upcomingRuleCount == 0) {
            MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoUpcomingRecurringRules)
        } else {
            MoreHubOverviewCardState.Content(
                RecurringOverviewPayload(upcomingRuleCount = upcomingRuleCount),
            )
        }
    }

    val uiState = combine(
        observeActiveWorkspaceUseCase(),
        assets,
        goals,
        subscriptions,
        recurringTransactions,
    ) { workspace, assetsState, goalsState, subscriptionsState, recurringState ->
        MoreHubUiState(
            activeWorkspaceName = workspace?.name,
            assets = assetsState,
            goals = goalsState,
            subscriptions = subscriptionsState,
            recurringTransactions = recurringState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MoreHubUiState(),
    )

    private fun <T, R> observeCard(
        source: Flow<T>,
        mapper: (T) -> MoreHubOverviewCardState<R>,
    ): Flow<MoreHubOverviewCardState<R>> = source
        .map(mapper)
        .onStart { emit(MoreHubOverviewCardState.Loading) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(MoreHubOverviewCardState.Error(MoreHubCardErrorReason.General))
        }

    /**
     * Mevcut takvim motorunu ardışık adaylar için kullanır. Eski, hiç üretilmemiş günlük
     * kuralların sınırsız taranmasını engellemek için pencereye ulaşamayan kuralları fail-closed reddeder.
     */
    private fun hasOccurrenceInWindow(
        recurring: com.feniqo.mobile.domain.model.RecurringTransaction,
        fromDate: com.feniqo.mobile.domain.model.LocalDate,
        throughDate: com.feniqo.mobile.domain.model.LocalDate,
    ): Boolean {
        val endDate = recurring.rule.endDate
        if (!recurring.isActive || (endDate != null && endDate < fromDate)) {
            return false
        }
        var lastGeneratedDate = recurring.lastGeneratedDate
        repeat(MAX_RECURRING_OCCURRENCE_SCAN) {
            val next = RecurrenceScheduleCalculator.nextOccurrenceAfter(
                rule = recurring.rule,
                lastGeneratedDate = lastGeneratedDate,
            ) ?: return false
            when {
                next < fromDate -> lastGeneratedDate = next
                next <= throughDate -> return true
                else -> return false
            }
        }
        return false
    }

    private companion object {
        const val UPCOMING_DAYS = 30
        const val MAX_RECURRING_OCCURRENCE_SCAN = 500
    }
}
