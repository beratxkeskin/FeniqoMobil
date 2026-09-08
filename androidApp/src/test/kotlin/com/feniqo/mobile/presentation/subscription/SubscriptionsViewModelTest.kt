package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase
import com.feniqo.mobile.domain.usecase.CreateSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionActiveUseCase
import com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase

import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.common.CurrentDateProvider

import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedToday = LocalDate(2026, 9, 1)
    private val dateProvider = CurrentDateProvider { fixedToday }

    @Test
    fun initialState_isLoadingTrueAndEmpty() {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
        assertFalse(state.mutationState.isSubmitting)
        assertNull(state.mutationState.pendingDeleteId)
    }

    @Test
    fun successfulObservation_mapsDisplayModelsAndRenewalStatusCorrectly() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val category = sampleCategory("cat-1", "Eğlence")
        val sub1 = sampleSubscription(
            id = "sub-1",
            name = "Spotify",
            categoryId = "cat-1",
            amountMinor = 5999L,
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
        )
        val sub2 = sampleSubscription(
            id = "sub-2",
            name = "Netflix",
            categoryId = "cat-1",
            amountMinor = 14999L,
            nextRenewalDate = LocalDate(2026, 9, 15),
            isActive = false,
        )

        categoryRepo.categoriesFlow.value = listOf(category)
        subscriptionRepo.subscriptionsFlow.value = listOf(sub1, sub2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
        assertEquals(2, state.items.size)

        val firstItem = state.items[0]
        assertEquals("Spotify", firstItem.name)
        assertEquals("Eğlence", firstItem.categoryName)
        assertEquals(SubscriptionRenewalStatus.DueToday, firstItem.renewalStatus)

        val secondItem = state.items[1]
        assertEquals("Netflix", secondItem.name)
        assertEquals(SubscriptionRenewalStatus.Inactive, secondItem.renewalStatus)
    }

    @Test
    fun observationError_setsErrorStateAndClearsLoading() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        subscriptionRepo.shouldThrowOnObserve = RuntimeException("DB error")
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun retryObservation_restartsCollection() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        subscriptionRepo.shouldThrowOnObserve = RuntimeException("Temporary error")
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.observationError)

        subscriptionRepo.shouldThrowOnObserve = null
        val sub = sampleSubscription("sub-1", "Spotify")
        subscriptionRepo.subscriptionsFlow.value = listOf(sub)

        viewModel.onIntent(SubscriptionsIntent.Retry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(1, state.items.size)
        assertEquals("Spotify", state.items[0].name)
    }

    @Test
    fun loadSubscriptionForEdit_found_setsReadyState() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val sub = sampleSubscription("sub-1", "Spotify", amountMinor = 5999L)
        subscriptionRepo.singleSubscriptionFlow.value = sub

        viewModel.loadSubscriptionForEdit(EntityId("sub-1"))
        advanceUntilIdle()

        val loadState = viewModel.editLoadState.value
        assertTrue(loadState is SubscriptionEditLoadState.Ready)
        val ready = loadState as SubscriptionEditLoadState.Ready
        assertEquals("Spotify", ready.draft.name)
        assertEquals(EntityId("sub-1"), ready.draft.subscriptionId)
        assertTrue(ready.isActive)
    }

    @Test
    fun loadSubscriptionForEdit_notFound_setsNotFoundState() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        subscriptionRepo.singleSubscriptionFlow.value = null

        viewModel.loadSubscriptionForEdit(EntityId("sub-missing"))
        advanceUntilIdle()

        val loadState = viewModel.editLoadState.value
        assertTrue(loadState is SubscriptionEditLoadState.NotFound)
    }

    @Test
    fun loadSubscriptionForEdit_error_setsErrorState() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        subscriptionRepo.shouldThrowOnObserveSingle = RuntimeException("DB failure")
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        viewModel.loadSubscriptionForEdit(EntityId("sub-err"))
        advanceUntilIdle()

        val loadState = viewModel.editLoadState.value
        assertTrue(loadState is SubscriptionEditLoadState.Error)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, (loadState as SubscriptionEditLoadState.Error).message)
    }

    @Test
    fun loadSubscriptionForEdit_outOfOrder_latestJobWins() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val deferredFirst = CompletableDeferred<Subscription?>()
        val subSecond = sampleSubscription("sub-2", "Netflix")

        subscriptionRepo.singleSubscriptionCustomFlow = { id ->
            if (id.value == "sub-1") {
                flow {
                    val result = deferredFirst.await()
                    emit(result)
                }
            } else {
                flowOf(subSecond)
            }
        }

        viewModel.loadSubscriptionForEdit(EntityId("sub-1"))
        viewModel.loadSubscriptionForEdit(EntityId("sub-2"))
        advanceUntilIdle()

        val stateAfterSecond = viewModel.editLoadState.value
        assertTrue(stateAfterSecond is SubscriptionEditLoadState.Ready)
        assertEquals("Netflix", (stateAfterSecond as SubscriptionEditLoadState.Ready).draft.name)

        // First completes later
        deferredFirst.complete(sampleSubscription("sub-1", "Spotify"))
        advanceUntilIdle()

        // Still Netflix!
        val stateFinal = viewModel.editLoadState.value
        assertTrue(stateFinal is SubscriptionEditLoadState.Ready)
        assertEquals("Netflix", (stateFinal as SubscriptionEditLoadState.Ready).draft.name)
    }

    @Test
    fun setEditLoadInvalidId_setsNotFoundStateImmediately() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        viewModel.setEditLoadInvalidId()
        assertEquals(SubscriptionEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun createSubscription_success_emitsMutationSuccess() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = CreateSubscriptionCommand(
            name = "Spotify",
            amount = Money(5999L, Currency.TRY),
            categoryId = EntityId("cat-1"),
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 8, 1),
        )

        viewModel.onIntent(SubscriptionsIntent.Create(command))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_SAVED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun updateSubscription_success_emitsMutationSuccess() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = UpdateSubscriptionCommand(
            id = EntityId("sub-1"),
            name = "Spotify Family",
            amount = Money(9999L, Currency.TRY),
            categoryId = EntityId("cat-1"),
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

        viewModel.onIntent(SubscriptionsIntent.Update(command))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_SAVED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun setActiveSubscription_success_emitsMutationSuccess() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = SetSubscriptionActiveCommand(
            id = EntityId("sub-1"),
            isActive = false,
        )

        viewModel.onIntent(SubscriptionsIntent.SetActive(command))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_SAVED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun deleteSubscriptionFlow_requestDismissConfirm_worksCorrectly() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // 1. Request delete
        viewModel.onIntent(SubscriptionsIntent.RequestDelete(EntityId("sub-del-1")))
        assertEquals(EntityId("sub-del-1"), viewModel.uiState.value.mutationState.pendingDeleteId)

        // 2. Dismiss delete
        viewModel.onIntent(SubscriptionsIntent.DismissDelete)
        assertNull(viewModel.uiState.value.mutationState.pendingDeleteId)

        // 3. Request again and confirm
        viewModel.onIntent(SubscriptionsIntent.RequestDelete(EntityId("sub-del-1")))
        viewModel.onIntent(SubscriptionsIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_DELETED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertNull(viewModel.uiState.value.mutationState.pendingDeleteId)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun advanceRenewal_requestAndDismiss_doesNotInvokeUseCase() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val targetId = EntityId("sub-adv-1")
        val renewalDate = LocalDate(2026, 9, 1)

        // 1. Request
        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, renewalDate))
        val target = viewModel.uiState.value.mutationState.pendingAdvanceRenewal
        assertEquals(PendingAdvanceRenewalTarget(targetId, renewalDate), target)
        assertNull(subscriptionRepo.lastAdvanceRenewalId)

        // 2. Dismiss
        viewModel.onIntent(SubscriptionsIntent.DismissAdvanceRenewal)
        assertNull(viewModel.uiState.value.mutationState.pendingAdvanceRenewal)
        assertNull(subscriptionRepo.lastAdvanceRenewalId)
    }


    @Test
    fun advanceRenewal_confirmAdvanced_emitsSubscriptionRenewedAndClearsPendingTarget() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val targetId = EntityId("sub-adv-1")
        subscriptionRepo.advanceRenewalResult = RepositoryResult.Success(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 9, 1)),
        )

        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, LocalDate(2026, 9, 1)))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertEquals(targetId, subscriptionRepo.lastAdvanceRenewalId)
        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_RENEWED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertNull(viewModel.uiState.value.mutationState.pendingAdvanceRenewal)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun advanceRenewal_confirmCompleted_emitsSubscriptionCompletedAndClearsPendingTarget() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val targetId = EntityId("sub-adv-complete-1")
        subscriptionRepo.advanceRenewalResult = RepositoryResult.Success(
            SubscriptionRenewalProgressionResult.Completed,
        )

        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, LocalDate(2026, 9, 1)))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertEquals(targetId, subscriptionRepo.lastAdvanceRenewalId)
        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_COMPLETED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
        assertNull(viewModel.uiState.value.mutationState.pendingAdvanceRenewal)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun advanceRenewal_emitsRenewedWhenRepoReturnsAdvancedEvenIfEditLoadStateIsEmpty() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // editLoadState is Idle (empty) -> repo returns Advanced -> RENEWED
        val targetId = EntityId("sub-empty-load-1")
        subscriptionRepo.advanceRenewalResult = RepositoryResult.Success(
            SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 10, 1)),
        )
        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, LocalDate(2026, 9, 1)))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_RENEWED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
    }

    @Test
    fun advanceRenewal_emitsCompletedWhenRepoReturnsCompletedEvenIfEditLoadStateHasDifferentId() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // editLoadState loaded with completely different subscription ID -> repo returns Completed -> COMPLETED
        val otherSubscription = sampleSubscription(
            id = "other-sub-id",
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
        )
        subscriptionRepo.singleSubscriptionFlow.value = otherSubscription
        viewModel.loadSubscriptionForEdit(EntityId("other-sub-id"))
        advanceUntilIdle()

        val targetId = EntityId("sub-different-target-2")
        subscriptionRepo.advanceRenewalResult = RepositoryResult.Success(
            SubscriptionRenewalProgressionResult.Completed,
        )
        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, LocalDate(2026, 9, 1)))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.MutationSuccess)
        assertEquals(FinanceUiMessage.SUBSCRIPTION_COMPLETED, (events[0] as SubscriptionUiEvent.MutationSuccess).message)
    }



    @Test
    fun advanceRenewal_failure_retainsPendingTargetAndEmitsShowMessage() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        subscriptionRepo.advanceRenewalResult = RepositoryResult.Failure(AppError.Validation("subscription_inactive"))
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val targetId = EntityId("sub-adv-fail-1")
        val renewalDate = LocalDate(2026, 9, 1)
        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, renewalDate))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertEquals(targetId, subscriptionRepo.lastAdvanceRenewalId)
        assertEquals(1, events.size)
        assertTrue(events[0] is SubscriptionUiEvent.ShowMessage)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, (events[0] as SubscriptionUiEvent.ShowMessage).message)
        assertEquals(PendingAdvanceRenewalTarget(targetId, renewalDate), viewModel.uiState.value.mutationState.pendingAdvanceRenewal)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun advanceRenewal_cancellation_rethrowsAndPreservesPendingTargetWithoutGenericError() = runTest {
        val subscriptionRepo = FakeSubscriptionRepository()
        subscriptionRepo.throwOnAdvanceRenewal = CancellationException("Job was cancelled")
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(subscriptionRepo, categoryRepo)

        val events = mutableListOf<SubscriptionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val targetId = EntityId("sub-adv-cancel-1")
        val renewalDate = LocalDate(2026, 9, 1)
        viewModel.onIntent(SubscriptionsIntent.RequestAdvanceRenewal(targetId, renewalDate))
        viewModel.onIntent(SubscriptionsIntent.ConfirmAdvanceRenewal)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(PendingAdvanceRenewalTarget(targetId, renewalDate), viewModel.uiState.value.mutationState.pendingAdvanceRenewal)
    }

    @Test
    fun subscriptionFormRouteHelper_advanceRenewal_helpers() {
        val subId = EntityId("sub-1")
        val otherId = EntityId("sub-2")
        val date = LocalDate(2026, 9, 1)
        val target = PendingAdvanceRenewalTarget(subId, date)

        // shouldShowAdvanceRenewalDialog
        assertTrue(SubscriptionFormRouteHelper.shouldShowAdvanceRenewalDialog(subId, target))
        assertFalse(SubscriptionFormRouteHelper.shouldShowAdvanceRenewalDialog(otherId, target))
        assertFalse(SubscriptionFormRouteHelper.shouldShowAdvanceRenewalDialog(null, target))
        assertFalse(SubscriptionFormRouteHelper.shouldShowAdvanceRenewalDialog(subId, null))

        // computeRequestAdvanceRenewalIntent
        assertEquals(SubscriptionsIntent.RequestAdvanceRenewal(subId, date), SubscriptionFormRouteHelper.computeRequestAdvanceRenewalIntent(subId, date))
        assertNull(SubscriptionFormRouteHelper.computeRequestAdvanceRenewalIntent(null, date))
        assertNull(SubscriptionFormRouteHelper.computeRequestAdvanceRenewalIntent(subId, null))

        // confirm & dismiss
        assertEquals(SubscriptionsIntent.ConfirmAdvanceRenewal, SubscriptionFormRouteHelper.computeConfirmAdvanceRenewalIntent())
        assertEquals(SubscriptionsIntent.DismissAdvanceRenewal, SubscriptionFormRouteHelper.computeDismissAdvanceRenewalIntent())
    }

    private fun createViewModel(

        subscriptionRepo: FakeSubscriptionRepository,
        categoryRepo: FakeCategoryRepository,
    ): SubscriptionsViewModel {
        val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
        return SubscriptionsViewModel(
            observeSubscriptionsUseCase = ObserveSubscriptionsUseCase(subscriptionRepo),
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subscriptionRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(categoryRepo),
            createSubscriptionUseCase = CreateSubscriptionUseCase(subscriptionRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subscriptionRepo),
            setSubscriptionActiveUseCase = SetSubscriptionActiveUseCase(subscriptionRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subscriptionRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subscriptionRepo),
            currentDateProvider = dateProvider,
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo),
        )
    }

    private fun sampleCategory(id: String, name: String) = Category(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("briefcase"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun sampleSubscription(
        id: String,
        name: String = "Spotify",
        categoryId: String? = "cat-1",
        amountMinor: Long = 5999L,
        currency: Currency = Currency.TRY,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate(2026, 8, 1),
        endDate: LocalDate? = null,
        nextRenewalDate: LocalDate = LocalDate(2026, 9, 1),
        isActive: Boolean = true,
    ): Subscription = Subscription(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        amount = Money(amountMinor, currency),
        categoryId = categoryId?.let { EntityId(it) },
        renewalRule = RecurrenceRule(
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = endDate,
        ),
        nextRenewalDate = nextRenewalDate,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private class FakeSubscriptionRepository : SubscriptionRepository {
        val subscriptionsFlow = MutableStateFlow<List<Subscription>>(emptyList())
        val singleSubscriptionFlow = MutableStateFlow<Subscription?>(null)
        var singleSubscriptionCustomFlow: ((EntityId) -> Flow<Subscription?>)? = null
        var shouldThrowOnObserve: Throwable? = null
        var shouldThrowOnObserveSingle: Throwable? = null
        var activeCollectorCount = 0

        var advanceRenewalResult: RepositoryResult<SubscriptionRenewalProgressionResult> =
            RepositoryResult.Success(SubscriptionRenewalProgressionResult.Advanced(LocalDate(2026, 9, 1)))
        var lastAdvanceRenewalId: EntityId? = null
        var throwOnAdvanceRenewal: Throwable? = null

        override fun observeSubscriptions(): Flow<List<Subscription>> = flow {
            val error = shouldThrowOnObserve
            if (error != null) {
                throw error
            }
            activeCollectorCount++
            try {
                subscriptionsFlow.collect { emit(it) }
            } finally {
                activeCollectorCount--
            }
        }

        override fun observeSubscription(id: EntityId): Flow<Subscription?> {
            val custom = singleSubscriptionCustomFlow
            if (custom != null) {
                return custom(id)
            }
            return flow {
                val error = shouldThrowOnObserveSingle
                if (error != null) {
                    throw error
                }
                singleSubscriptionFlow.collect { emit(it) }
            }
        }

        override suspend fun create(command: CreateSubscriptionCommand): RepositoryResult<EntityId> {
            return RepositoryResult.Success(EntityId("sub-created-1"))
        }

        override suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }

        override suspend fun setActive(command: SetSubscriptionActiveCommand): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }

        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> {
            val error = throwOnAdvanceRenewal
            if (error != null) {
                throw error
            }
            lastAdvanceRenewalId = id
            return advanceRenewalResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {

            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun observeCategories(
            type: TransactionType?,
            workspaceId: EntityId?,
        ): Flow<List<Category>> = categoriesFlow

        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(null)

        override fun observeCategoriesForHistoryLookup(
            workspaceId: EntityId?,
        ): Flow<List<Category>> = categoriesFlow

        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Unknown("Not implemented"))

        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Unknown("Not implemented"))

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Unknown("Not implemented"))
    }
}

