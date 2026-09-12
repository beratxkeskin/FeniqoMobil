package com.feniqo.mobile.presentation.hub

import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.*
import com.feniqo.mobile.domain.usecase.*
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FakeWorkspaceRepository
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoreHubViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun assetSummary_keepsCurrenciesSeparate() = runHubTest { fixture, state ->
        fixture.assets.items.value = listOf(asset("try", 10_000, Currency.TRY), asset("usd", 2_000, Currency.USD))
        val content = state().assets as MoreHubOverviewCardState.Content
        assertEquals(2, content.primaryText.split(" · ").size)
        assertEquals("2 varlık", content.secondaryText)
    }

    @Test fun assetOverflow_onlySetsAssetCardToError() = runHubTest { fixture, state ->
        fixture.assets.items.value = listOf(asset("a", Money.MAX_AMOUNT_MINOR, Currency.TRY), asset("b", 1, Currency.TRY))
        assertTrue(state().assets is MoreHubOverviewCardState.Error)
        assertTrue(state().goals is MoreHubOverviewCardState.Empty)
    }

    @Test fun goals_countOnlyInProgress() = runHubTest { fixture, state ->
        fixture.goals.items.value = listOf(goal("open", 10_000, 1), goal("done", 10_000, 10_000))
        assertTrue((state().goals as MoreHubOverviewCardState.Content).primaryText.startsWith("1"))
    }

    @Test fun subscriptions_countOnlyActiveAndUseNearestRenewal() = runHubTest { fixture, state ->
        fixture.subscriptions.items.value = listOf(subscription("later", true, LocalDate(2026, 10, 1)), subscription("nearest", true, LocalDate(2026, 9, 15)), subscription("paused", false, LocalDate(2026, 9, 13)))
        val content = state().subscriptions as MoreHubOverviewCardState.Content
        assertTrue(content.primaryText.startsWith("2"))
        assertTrue(content.secondaryText.orEmpty().contains("15"))
    }

    @Test fun recurring_countsTodayAndDayThirtyButNotDayThirtyOne() = runHubTest { fixture, state ->
        fixture.recurring.items.value = listOf(recurring("today", LocalDate(2026, 9, 12)), recurring("day30", LocalDate(2026, 10, 12)), recurring("day31", LocalDate(2026, 10, 13)))
        assertTrue((state().recurringTransactions as MoreHubOverviewCardState.Content).primaryText.startsWith("2"))
    }

    @Test fun recurring_pastStartWithoutLastGeneration_findsOccurrenceInWindow() = runHubTest { fixture, state ->
        fixture.recurring.items.value = listOf(recurring("weekly", LocalDate(2026, 8, 1), frequency = RecurrenceFrequency.WEEKLY))
        assertTrue((state().recurringTransactions as MoreHubOverviewCardState.Content).primaryText.startsWith("1"))
    }

    @Test fun recurring_ignoresInactiveAndExpiredRules() = runHubTest { fixture, state ->
        fixture.recurring.items.value = listOf(recurring("inactive", LocalDate(2026, 9, 12), active = false), recurring("expired", LocalDate(2026, 8, 1), endDate = LocalDate(2026, 9, 11)))
        assertTrue(state().recurringTransactions is MoreHubOverviewCardState.Empty)
    }

    @Test fun workspaceName_updatesFromFlow() = runHubTest { fixture, state ->
        fixture.workspace.activeWorkspaceFlow.value = Workspace(EntityId("shared"), "Home", EntityId("owner"), Instant.DISTANT_PAST)
        assertEquals("Home", state().activeWorkspaceName)
    }

    @Test fun assetFailure_doesNotHideOtherIndependentOverviewCards() = runHubTest { fixture, state ->
        fixture.assets.failure = IllegalStateException("room")
        fixture.assets.emitFailure()
        assertTrue(state().assets is MoreHubOverviewCardState.Error)
        assertTrue(state().goals is MoreHubOverviewCardState.Empty)
        assertTrue(state().subscriptions is MoreHubOverviewCardState.Empty)
        assertTrue(state().recurringTransactions is MoreHubOverviewCardState.Empty)
    }

    private fun runHubTest(block: suspend (Fixture, () -> MoreHubUiState) -> Unit) = runTest {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        block(fixture) { vm.uiState.value }
    }

    private class Fixture {
        val assets = FakeAssets(); val goals = FakeGoals(); val subscriptions = FakeSubscriptions(); val recurring = FakeRecurring(); val workspace = FakeWorkspaceRepository()
        fun viewModel() = MoreHubViewModel(ObserveAssetsUseCase(assets), CalculateNetWorthUseCase(), ObserveGoalsUseCase(goals), ObserveSubscriptionsUseCase(subscriptions), ObserveRecurringTransactionsUseCase(recurring), ObserveActiveWorkspaceUseCase(workspace), CurrentDateProvider { LocalDate(2026, 9, 12) })
    }

    private class FakeAssets : AssetRepository {
        val items = MutableStateFlow<List<Asset>>(emptyList()); var failure: Throwable? = null; private val failureTrigger = MutableStateFlow(0)
        override fun observeAssets(): Flow<List<Asset>> = failureTrigger.flatMapLatest { failure?.let { throw it }; items }
        fun emitFailure() { failureTrigger.value++ }
        override fun observeAsset(id: EntityId) = emptyFlow<Asset?>(); override suspend fun create(command: CreateAssetCommand) = error("unused"); override suspend fun update(command: UpdateAssetCommand) = error("unused"); override suspend fun softDelete(id: EntityId) = error("unused")
    }
    private class FakeGoals : GoalRepository {
        val items = MutableStateFlow<List<Goal>>(emptyList()); override fun observeGoals() = items; override fun observeGoal(id: EntityId)=emptyFlow<Goal?>(); override fun observeContributions(goalId: EntityId)=emptyFlow<List<GoalContribution>>(); override suspend fun create(command: CreateGoalCommand)=error("unused"); override suspend fun update(command: UpdateGoalCommand)=error("unused"); override suspend fun addContribution(command: AddGoalContributionCommand)=error("unused"); override suspend fun softDelete(id: EntityId)=error("unused")
    }
    private class FakeSubscriptions : SubscriptionRepository {
        val items = MutableStateFlow<List<Subscription>>(emptyList()); override fun observeSubscriptions()=items; override fun observeSubscription(id: EntityId)=emptyFlow<Subscription?>(); override suspend fun create(command: CreateSubscriptionCommand)=error("unused"); override suspend fun update(command: UpdateSubscriptionCommand)=error("unused"); override suspend fun setActive(command: SetSubscriptionActiveCommand)=error("unused"); override suspend fun advanceRenewal(id: EntityId)=error("unused"); override suspend fun softDelete(id: EntityId)=error("unused")
    }
    private class FakeRecurring : RecurringTransactionRepository {
        val items = MutableStateFlow<List<RecurringTransaction>>(emptyList()); override fun observeRecurringTransactions()=items; override fun observeRecurringTransaction(id: EntityId)=emptyFlow<RecurringTransaction?>(); override suspend fun create(command: CreateRecurringTransactionCommand)=error("unused"); override suspend fun update(command: UpdateRecurringTransactionCommand)=error("unused"); override suspend fun setActive(command: SetRecurringTransactionActiveCommand)=error("unused"); override suspend fun softDelete(id: EntityId)=error("unused"); override suspend fun generateDueTransactions(throughDate: LocalDate,maxOccurrencesPerRule:Int,maxTotalOccurrences:Int,createdAt:Instant)=error("unused")
    }

    private fun asset(id:String, amount:Long, currency:Currency)=Asset(EntityId(id),EntityId("owner"),null,id,AssetType.CASH,Money(amount,currency),null,null,null,false,Instant.DISTANT_PAST)
    private fun goal(id:String,target:Long,current:Long)=Goal(EntityId(id),EntityId("owner"),null,id,Money(target,Currency.TRY),Money(current,Currency.TRY),LocalDate(2026,12,31),CategoryColor("#123456"),null,Instant.DISTANT_PAST)
    private fun subscription(id:String,active:Boolean,next:LocalDate)=Subscription(EntityId(id),EntityId("owner"),null,id,Money(100,Currency.TRY),null,RecurrenceRule(RecurrenceFrequency.MONTHLY,1,LocalDate(2026,1,1),null),next,active,Instant.DISTANT_PAST)
    private fun recurring(id:String,start:LocalDate,frequency:RecurrenceFrequency=RecurrenceFrequency.DAILY,active:Boolean=true,endDate:LocalDate?=null)=RecurringTransaction(EntityId(id),EntityId("owner"),null,Money(100,Currency.TRY),TransactionType.EXPENSE,EntityId("category"),null,PaymentMethod.CASH,RecurrenceRule(frequency,1,start,endDate),null,active,Instant.DISTANT_PAST)
}
