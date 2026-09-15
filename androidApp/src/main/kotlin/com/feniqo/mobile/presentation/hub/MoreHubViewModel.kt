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
import com.feniqo.mobile.presentation.hub.MoreHubOverviewCardState
import com.feniqo.mobile.presentation.hub.MoreHubUiState
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter
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
        if (items.isEmpty()) return@observeCard MoreHubOverviewCardState.Empty("Henüz varlık yok.")
        when (val netWorth = calculateNetWorthUseCase(items)) {
            is NetWorthCalculationResult.Success -> MoreHubOverviewCardState.Content(
                primaryText = netWorth.totals.joinToString(" · ") { MoneyFormatter.format(it) },
                secondaryText = "${netWorth.assetCount} varlık",
            )
            NetWorthCalculationResult.InvalidAssetValue,
            NetWorthCalculationResult.Overflow,
            -> MoreHubOverviewCardState.Error("Varlık toplamı güvenle gösterilemiyor.")
        }
    }

    private val goals = observeCard(observeGoalsUseCase()) { items ->
        val activeCount = items.count {
            GoalProgressCalculator.calculateProgress(it.targetAmount, it.currentAmount).status == GoalStatus.IN_PROGRESS
        }
        if (activeCount == 0) {
            MoreHubOverviewCardState.Empty("Henüz aktif hedef yok.")
        } else {
            MoreHubOverviewCardState.Content(
                primaryText = "$activeCount",
                secondaryText = "aktif hedef",
            )
        }
    }

    private val subscriptions = observeCard(observeSubscriptionsUseCase()) { items ->
        val active = items.filter { it.isActive }
        if (active.isEmpty()) {
            MoreHubOverviewCardState.Empty("Henüz aktif abonelik yok.")
        } else {
            val nearest = active.minByOrNull { it.nextRenewalDate }?.nextRenewalDate
            MoreHubOverviewCardState.Content(
                primaryText = "${active.size}",
                secondaryText = nearest?.let { "aktif abonelik\nEn yakın: ${DateFormatter.formatReadableDate(it)}" }
                    ?: "aktif abonelik",
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
            MoreHubOverviewCardState.Empty("Önümüzdeki 30 günde planlı kural yok.")
        } else {
            MoreHubOverviewCardState.Content(
                primaryText = "$upcomingRuleCount",
                secondaryText = "planlı kural\nÖnümüzdeki 30 gün",
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

    private fun <T> observeCard(
        source: Flow<T>,
        mapper: (T) -> MoreHubOverviewCardState,
    ): Flow<MoreHubOverviewCardState> = source
        .map(mapper)
        .onStart { emit(MoreHubOverviewCardState.Loading) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(MoreHubOverviewCardState.Error())
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
