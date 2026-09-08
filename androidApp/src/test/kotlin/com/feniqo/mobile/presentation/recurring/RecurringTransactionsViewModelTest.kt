package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.DeleteRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase
import com.feniqo.mobile.domain.usecase.SetRecurringTransactionActiveUseCase
import com.feniqo.mobile.domain.usecase.UpdateRecurringTransactionUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringTransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_isLoadingTrueAndEmpty() {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
        assertFalse(state.mutationState.isSubmitting)
        assertNull(state.mutationState.pendingDeleteId)
    }

    @Test
    fun successfulObservation_mapsDisplayModelsCorrectly() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val category = sampleCategory("cat-1", "Kira")
        val recurring = sampleRecurring("rec-1", categoryId = "cat-1")

        categoryRepo.categoriesFlow.value = listOf(category)
        recurringRepo.recurringFlow.value = listOf(recurring)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(1, state.items.size)
        assertEquals("rec-1", state.items[0].id.value)
        assertEquals("Kira", state.items[0].categoryName)
        assertFalse(state.items[0].isCategoryMissing)
        assertFalse(state.isEmpty)
    }

    @Test
    fun missingCategoryFallback_reflectedInState() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val recurring = sampleRecurring("rec-1", categoryId = "cat-unknown")
        categoryRepo.categoriesFlow.value = emptyList()
        recurringRepo.recurringFlow.value = listOf(recurring)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        assertTrue(state.items[0].isCategoryMissing)
        assertEquals("Bilinmeyen Kategori", state.items[0].categoryName)
        assertNull(state.items[0].categoryColorHex)
    }

    @Test
    fun emptyList_setsIsEmptyTrue() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        categoryRepo.categoriesFlow.value = emptyList()
        recurringRepo.recurringFlow.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertNull(state.observationError)
        assertTrue(state.isEmpty)
    }

    @Test
    fun observationError_showsGenericError_andRetryRecoversSuccessfully() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.shouldThrowOnObserve = RuntimeException("Database error")

        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertTrue(errorState.items.isEmpty())
        assertEquals(FinanceUiMessage.GENERIC_ERROR, errorState.observationError)
        assertFalse(errorState.isEmpty)

        // Retry after error
        recurringRepo.shouldThrowOnObserve = null
        recurringRepo.recurringFlow.value = listOf(sampleRecurring("rec-1", "cat-1"))
        categoryRepo.categoriesFlow.value = listOf(sampleCategory("cat-1", "Kira"))

        viewModel.onIntent(RecurringTransactionsIntent.Retry)
        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertFalse(recoveredState.isLoading)
        assertNull(recoveredState.observationError)
        assertEquals(1, recoveredState.items.size)
        assertEquals("rec-1", recoveredState.items[0].id.value)
    }

    @Test
    fun cancellation_doesNotConvertToGenericError() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.shouldThrowOnObserve = CancellationException("Scope cancelled")

        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.observationError)
    }

    @Test
    fun retry_doesNotCreateParallelDuplicateCollectors() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(1, recurringRepo.activeCollectorCount)

        viewModel.onIntent(RecurringTransactionsIntent.Retry)
        advanceUntilIdle()
        assertEquals(1, recurringRepo.activeCollectorCount)

        viewModel.retryObservation()
        advanceUntilIdle()
        assertEquals(1, recurringRepo.activeCollectorCount)
    }

    @Test
    fun create_success_emitsMutationSuccessEventAndClearsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = CreateRecurringTransactionCommand(
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "İnternet",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

        viewModel.onIntent(RecurringTransactionsIntent.Create(command))
        advanceUntilIdle()

        assertEquals(command, recurringRepo.lastCreatedCommand)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun update_success_emitsMutationSuccessEventAndClearsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = Money(15000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-2"),
            description = "Güncellendi",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 2,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

        viewModel.onIntent(RecurringTransactionsIntent.Update(command))
        advanceUntilIdle()

        assertEquals(command, recurringRepo.lastUpdatedCommand)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun setActive_success_emitsMutationSuccessEventAndClearsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = SetRecurringTransactionActiveCommand(
            id = EntityId("rec-1"),
            isActive = false,
        )

        viewModel.onIntent(RecurringTransactionsIntent.SetActive(command))
        advanceUntilIdle()

        assertEquals(command, recurringRepo.lastSetActiveCommand)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun mutationFailure_emitsShowMessageAndDoesNotWriteToState() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.createResult = RepositoryResult.Failure(AppError.Storage("DB write failed"))
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val command = CreateRecurringTransactionCommand(
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = PaymentMethod.CASH,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

        viewModel.onIntent(RecurringTransactionsIntent.Create(command))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertNull(viewModel.uiState.value.observationError)
    }

    @Test
    fun requestDelete_and_dismissDelete_managePendingIdWithoutCallingUseCase() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onIntent(RecurringTransactionsIntent.RequestDelete(EntityId("rec-del")))
        advanceUntilIdle()

        assertEquals(EntityId("rec-del"), viewModel.uiState.value.mutationState.pendingDeleteId)
        assertNull(recurringRepo.lastDeletedId)

        viewModel.onIntent(RecurringTransactionsIntent.DismissDelete)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.mutationState.pendingDeleteId)
        assertNull(recurringRepo.lastDeletedId)
    }

    @Test
    fun confirmDelete_withoutPendingTarget_doesNotCallUseCase() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onIntent(RecurringTransactionsIntent.ConfirmDelete)
        advanceUntilIdle()

        assertNull(recurringRepo.lastDeletedId)
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun confirmDelete_success_callsUseCaseEmitsSuccessAndClearsPendingId() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onIntent(RecurringTransactionsIntent.RequestDelete(EntityId("rec-target")))
        advanceUntilIdle()

        viewModel.onIntent(RecurringTransactionsIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(EntityId("rec-target"), recurringRepo.lastDeletedId)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_DELETED), events[0])
        assertNull(viewModel.uiState.value.mutationState.pendingDeleteId)
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun confirmDelete_failure_emitsShowMessageAndResetsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.deleteResult = RepositoryResult.Failure(AppError.Storage("Delete failed"))
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onIntent(RecurringTransactionsIntent.RequestDelete(EntityId("rec-target")))
        advanceUntilIdle()

        viewModel.onIntent(RecurringTransactionsIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(EntityId("rec-target"), recurringRepo.lastDeletedId)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun create_cancellation_rethrowsAndDoesNotEmitGenericError() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.createThrowable = CancellationException("Create cancelled")
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val command = CreateRecurringTransactionCommand(
            amount = Money(10000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "İptal edilecek kural",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

        viewModel.onIntent(RecurringTransactionsIntent.Create(command))
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertNull(viewModel.uiState.value.observationError)
    }

    @Test
    fun confirmDelete_cancellation_rethrowsAndPreservesPendingDeleteTarget() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.deleteThrowable = CancellationException("Delete cancelled")
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onIntent(RecurringTransactionsIntent.RequestDelete(EntityId("rec-target")))
        advanceUntilIdle()

        viewModel.onIntent(RecurringTransactionsIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(EntityId("rec-target"), recurringRepo.lastDeletedId)
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
        assertEquals(EntityId("rec-target"), viewModel.uiState.value.mutationState.pendingDeleteId)
    }

    @Test
    fun setActive_success_emitsMutationSuccessAndClearsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val command = SetRecurringTransactionActiveCommand(
            id = EntityId("rec-1"),
            isActive = false,
        )

        viewModel.onIntent(RecurringTransactionsIntent.SetActive(command))
        advanceUntilIdle()

        assertEquals(command, recurringRepo.lastSetActiveCommand)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.MutationSuccess(FinanceUiMessage.TRANSACTION_SAVED), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun setActive_failure_emitsShowMessageGenericErrorAndClearsSubmitting() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.setActiveResult = RepositoryResult.Failure(AppError.Storage("SetActive failed"))
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        val events = mutableListOf<RecurringTransactionUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val command = SetRecurringTransactionActiveCommand(
            id = EntityId("rec-1"),
            isActive = false,
        )

        viewModel.onIntent(RecurringTransactionsIntent.SetActive(command))
        advanceUntilIdle()

        assertEquals(command, recurringRepo.lastSetActiveCommand)
        assertEquals(1, events.size)
        assertEquals(RecurringTransactionUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), events[0])
        assertFalse(viewModel.uiState.value.mutationState.isSubmitting)
    }

    @Test
    fun editLoad_startsInLoading_andEmitsReadyWithDraftWhenFound() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        assertEquals(RecurringTransactionEditLoadState.Idle, viewModel.editLoadState.value)

        val targetId = EntityId("rec-123")
        val sample = sampleRecurring("rec-123", categoryId = "cat-1")
        recurringRepo.singleRecurringFlows.getOrPut(targetId) { MutableStateFlow(null) }.value = sample

        viewModel.loadRecurringForEdit(targetId)
        advanceUntilIdle()

        val state = viewModel.editLoadState.value
        assertTrue(state is RecurringTransactionEditLoadState.Ready)
        val ready = state as RecurringTransactionEditLoadState.Ready
        assertEquals(targetId, ready.draft.recurringTransactionId)
        assertEquals(sample.amount, ready.draft.amount)
        assertEquals(sample.type, ready.draft.type)
        assertEquals(sample.categoryId, ready.draft.categoryId)
        assertEquals(sample.rule.frequency, ready.draft.frequency)
        assertTrue(ready.isActive)
    }

    @Test
    fun editLoad_pausedRecord_carriesIsActiveFalseInReadyState() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        val targetId = EntityId("rec-paused")
        val sample = sampleRecurring("rec-paused", categoryId = "cat-1", isActive = false)
        recurringRepo.singleRecurringFlows.getOrPut(targetId) { MutableStateFlow(null) }.value = sample

        viewModel.loadRecurringForEdit(targetId)
        advanceUntilIdle()

        val state = viewModel.editLoadState.value
        assertTrue(state is RecurringTransactionEditLoadState.Ready)
        val ready = state as RecurringTransactionEditLoadState.Ready
        assertEquals(targetId, ready.draft.recurringTransactionId)
        assertFalse(ready.isActive)
    }

    @Test
    fun editLoad_nullRecord_emitsNotFound() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        val targetId = EntityId("rec-nonexistent")
        recurringRepo.singleRecurringFlows.getOrPut(targetId) { MutableStateFlow(null) }.value = null

        viewModel.loadRecurringForEdit(targetId)
        advanceUntilIdle()

        assertEquals(RecurringTransactionEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun editLoad_invalidId_emitsNotFound() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        viewModel.loadRecurringForEdit(EntityId("rec-valid"))
        viewModel.setEditLoadInvalidId()
        advanceUntilIdle()

        assertEquals(RecurringTransactionEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun editLoad_repositoryError_emitsErrorGenericError() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.shouldThrowOnObserveSingle = RuntimeException("DB error")
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        viewModel.loadRecurringForEdit(EntityId("rec-error"))
        advanceUntilIdle()

        val state = viewModel.editLoadState.value
        assertTrue(state is RecurringTransactionEditLoadState.Error)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, (state as RecurringTransactionEditLoadState.Error).message)
    }

    @Test
    fun editLoad_cancellation_doesNotConvertToGenericError() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        recurringRepo.shouldThrowOnObserveSingle = CancellationException("Edit observation cancelled")
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        viewModel.loadRecurringForEdit(EntityId("rec-cancel"))
        advanceUntilIdle()

        val state = viewModel.editLoadState.value
        // Must NOT be Error state
        assertTrue(state !is RecurringTransactionEditLoadState.Error)
    }

    @Test
    fun editLoad_newIdCancelsPreviousJob_andOldResultDoesNotOverwrite() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        val id1 = EntityId("rec-1")
        val id2 = EntityId("rec-2")

        val flow1 = MutableStateFlow<RecurringTransaction?>(null)
        val flow2 = MutableStateFlow<RecurringTransaction?>(sampleRecurring("rec-2"))

        recurringRepo.singleRecurringFlows[id1] = flow1
        recurringRepo.singleRecurringFlows[id2] = flow2

        viewModel.loadRecurringForEdit(id1)
        viewModel.loadRecurringForEdit(id2)
        advanceUntilIdle()

        val state = viewModel.editLoadState.value
        assertTrue(state is RecurringTransactionEditLoadState.Ready)
        assertEquals(id2, (state as RecurringTransactionEditLoadState.Ready).draft.recurringTransactionId)

        // Late emission from old id1
        flow1.value = sampleRecurring("rec-1")
        advanceUntilIdle()

        // State remains id2
        val currentState = viewModel.editLoadState.value
        assertTrue(currentState is RecurringTransactionEditLoadState.Ready)
        assertEquals(id2, (currentState as RecurringTransactionEditLoadState.Ready).draft.recurringTransactionId)
    }

    @Test
    fun editLoad_cancelledJobDelayedException_doesNotOverwriteNewJobState() = runTest {
        val recurringRepo = FakeRecurringTransactionRepository()
        val categoryRepo = FakeCategoryRepository()
        val viewModel = createViewModel(recurringRepo, categoryRepo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editLoadState.collect()
        }

        val id1 = EntityId("rec-1")
        val id2 = EntityId("rec-2")

        val resumeErrorDeferred = CompletableDeferred<Unit>()
        val flow1StartedDeferred = CompletableDeferred<Unit>()

        val customFlow1 = flow<RecurringTransaction?> {
            flow1StartedDeferred.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    resumeErrorDeferred.await()
                    throw RuntimeException("Delayed late DB failure from job1")
                }
            }
        }

        recurringRepo.customSingleFlows[id1] = customFlow1
        recurringRepo.singleRecurringFlows[id2] = MutableStateFlow(sampleRecurring("rec-2"))

        viewModel.loadRecurringForEdit(id1)
        advanceUntilIdle()
        assertTrue(flow1StartedDeferred.isCompleted)

        // Now load second ID: this cancels id1 job
        viewModel.loadRecurringForEdit(id2)
        advanceUntilIdle()

        val stateAfterSecondLoad = viewModel.editLoadState.value
        assertTrue(stateAfterSecondLoad is RecurringTransactionEditLoadState.Ready)
        assertEquals(id2, (stateAfterSecondLoad as RecurringTransactionEditLoadState.Ready).draft.recurringTransactionId)

        // Release the delayed exception from the cancelled job1
        resumeErrorDeferred.complete(Unit)
        advanceUntilIdle()

        // Verify state is still Ready(id2) and did NOT change to Error
        val finalState = viewModel.editLoadState.value
        assertTrue(finalState is RecurringTransactionEditLoadState.Ready)
        assertEquals(id2, (finalState as RecurringTransactionEditLoadState.Ready).draft.recurringTransactionId)
    }

    @Test
    fun resolveEffectiveRecurringEditLoadState_correctlyResolvesStates() {
        val draft = RecurringTransactionFormDraft(
            recurringTransactionId = EntityId("rec-1"),
            amount = Money(1000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            paymentMethod = PaymentMethod.CASH,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
        )

        // 1. Edit ID + Idle -> Loading
        assertEquals(
            RecurringTransactionEditLoadState.Loading,
            resolveEffectiveRecurringEditLoadState(EntityId("rec-1"), RecurringTransactionEditLoadState.Idle),
        )

        // 2. Create mode (null ID) + Idle -> Idle
        assertEquals(
            RecurringTransactionEditLoadState.Idle,
            resolveEffectiveRecurringEditLoadState(null, RecurringTransactionEditLoadState.Idle),
        )

        // 3. Ready is preserved
        val readyState = RecurringTransactionEditLoadState.Ready(draft, isActive = true)
        assertEquals(readyState, resolveEffectiveRecurringEditLoadState(EntityId("rec-1"), readyState))
        assertEquals(readyState, resolveEffectiveRecurringEditLoadState(null, readyState))

        // 4. NotFound is preserved
        assertEquals(
            RecurringTransactionEditLoadState.NotFound,
            resolveEffectiveRecurringEditLoadState(EntityId("rec-1"), RecurringTransactionEditLoadState.NotFound),
        )

        // 5. Error is preserved
        val errorState = RecurringTransactionEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
        assertEquals(
            errorState,
            resolveEffectiveRecurringEditLoadState(EntityId("rec-1"), errorState),
        )
    }

    private fun createViewModel(
        recurringRepo: RecurringTransactionRepository,
        categoryRepo: CategoryRepository,
    ): RecurringTransactionsViewModel {
        val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
        return RecurringTransactionsViewModel(
            observeRecurringTransactionsUseCase = ObserveRecurringTransactionsUseCase(recurringRepo),
            observeRecurringTransactionUseCase = ObserveRecurringTransactionUseCase(recurringRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(categoryRepo),
            createRecurringTransactionUseCase = CreateRecurringTransactionUseCase(recurringRepo),
            updateRecurringTransactionUseCase = UpdateRecurringTransactionUseCase(recurringRepo),
            setRecurringTransactionActiveUseCase = SetRecurringTransactionActiveUseCase(recurringRepo),
            deleteRecurringTransactionUseCase = DeleteRecurringTransactionUseCase(recurringRepo),
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo),
        )
    }

    private class FakeRecurringTransactionRepository : RecurringTransactionRepository {
        val recurringFlow = MutableStateFlow<List<RecurringTransaction>>(emptyList())
        val singleRecurringFlows = mutableMapOf<EntityId, MutableStateFlow<RecurringTransaction?>>()
        val customSingleFlows = mutableMapOf<EntityId, Flow<RecurringTransaction?>>()
        var shouldThrowOnObserve: Throwable? = null
        var shouldThrowOnObserveSingle: Throwable? = null
        var activeCollectorCount = 0

        var lastCreatedCommand: CreateRecurringTransactionCommand? = null
        var createResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("r1"))
        var createThrowable: Throwable? = null

        var lastUpdatedCommand: UpdateRecurringTransactionCommand? = null
        var updateResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastSetActiveCommand: SetRecurringTransactionActiveCommand? = null
        var setActiveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastDeletedId: EntityId? = null
        var deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var deleteThrowable: Throwable? = null

        override fun observeRecurringTransactions(): Flow<List<RecurringTransaction>> = flow {
            val error = shouldThrowOnObserve
            if (error != null) {
                throw error
            }
            activeCollectorCount++
            try {
                recurringFlow.collect { emit(it) }
            } finally {
                activeCollectorCount--
            }
        }

        override fun observeRecurringTransaction(id: EntityId): Flow<RecurringTransaction?> = flow {
            val error = shouldThrowOnObserveSingle
            if (error != null) {
                throw error
            }
            val custom = customSingleFlows[id]
            if (custom != null) {
                custom.collect { emit(it) }
            } else {
                val targetFlow = singleRecurringFlows.getOrPut(id) { MutableStateFlow(null) }
                targetFlow.collect { emit(it) }
            }
        }

        override suspend fun create(command: CreateRecurringTransactionCommand): RepositoryResult<EntityId> {
            lastCreatedCommand = command
            val throwable = createThrowable
            if (throwable != null) {
                throw throwable
            }
            return createResult
        }

        override suspend fun update(command: UpdateRecurringTransactionCommand): RepositoryResult<Unit> {
            lastUpdatedCommand = command
            return updateResult
        }

        override suspend fun setActive(command: SetRecurringTransactionActiveCommand): RepositoryResult<Unit> {
            lastSetActiveCommand = command
            return setActiveResult
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            val throwable = deleteThrowable
            if (throwable != null) {
                throw throwable
            }
            return deleteResult
        }

        override suspend fun generateDueTransactions(
            throughDate: LocalDate,
            maxOccurrencesPerRule: Int,
            maxTotalOccurrences: Int,
            createdAt: Instant,
        ): RepositoryResult<com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult> = error("N/A")
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(null)
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override suspend fun create(category: Category) = RepositoryResult.Success(EntityId("c1"))
        override suspend fun update(category: Category) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
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

    private fun sampleRecurring(
        id: String,
        categoryId: String = "cat-1",
        amountMinor: Long = 50000L,
        currency: Currency = Currency.TRY,
        type: TransactionType = TransactionType.EXPENSE,
        isActive: Boolean = true,
    ): RecurringTransaction = RecurringTransaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Açıklama",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        rule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = null,
        ),
        lastGeneratedDate = null,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )
}
