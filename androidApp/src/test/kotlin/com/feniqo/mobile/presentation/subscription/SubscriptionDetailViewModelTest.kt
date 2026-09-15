package com.feniqo.mobile.presentation.subscription

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.SetSubscriptionLifecycleCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPaymentSourceType
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase
import com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPriceHistoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionLifecycleUseCase
import com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FakeWorkspaceRepository
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testSubscriptionId = EntityId("sub-123")
    private val testCategoryId = EntityId("cat-fun")

    private val sampleCategory = Category(
        id = testCategoryId,
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Eğlence",
        type = TransactionType.EXPENSE,
        icon = CategoryIcon("entertainment"),
        color = CategoryColor("#10B981"),
        isDefault = false,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private val sampleSubscription = Subscription(
        id = testSubscriptionId,
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = "Netflix",
        amount = Money(amountMinor = 22900L, currency = Currency.TRY),
        categoryId = testCategoryId,
        renewalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = LocalDate(2025, 10, 14),
            endDate = null,
        ),
        nextRenewalDate = LocalDate(2026, 4, 14),
        lifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
        reminderEnabled = true,
        websiteUrl = "https://www.netflix.com",
        notes = "Standart HD Paket",
        createdAt = Instant.fromEpochMilliseconds(1700000000000L),
    )

    private val samplePayment = SubscriptionPayment(
        id = EntityId("pay-1"),
        subscriptionId = testSubscriptionId,
        amount = Money(22900L, Currency.TRY),
        paymentDate = LocalDate(2026, 3, 14),
        renewalDueDate = LocalDate(2026, 3, 14),
        sourceType = SubscriptionPaymentSourceType.MANUAL,
        createdAt = Instant.fromEpochMilliseconds(1710000000000L),
    )

    private class FakeSubscriptionRepository(
        val subscriptionFlow: MutableStateFlow<Subscription?> = MutableStateFlow(null),
        val paymentsFlow: MutableStateFlow<List<SubscriptionPayment>> = MutableStateFlow(emptyList()),
        val priceHistoriesFlow: MutableStateFlow<List<SubscriptionPriceHistory>> = MutableStateFlow(emptyList()),
    ) : SubscriptionRepository {
        var lastLifecycleCommand: SetSubscriptionLifecycleCommand? = null
        var lastUpdateCommand: UpdateSubscriptionCommand? = null
        var lastDeletedId: EntityId? = null
        var advanceRenewalCalled: Boolean = false

        override fun observeSubscriptions(): Flow<List<Subscription>> = flowOf(listOfNotNull(subscriptionFlow.value))
        override fun observeSubscription(id: EntityId): Flow<Subscription?> = subscriptionFlow
        override fun observePriceHistories(subscriptionId: EntityId?): Flow<List<SubscriptionPriceHistory>> = priceHistoriesFlow
        override fun observePayments(subscriptionId: EntityId?): Flow<List<SubscriptionPayment>> = paymentsFlow

        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateSubscriptionCommand) = RepositoryResult.Success(EntityId("new"))
        override suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return RepositoryResult.Success(Unit)
        }
        override suspend fun setActive(command: SetSubscriptionActiveCommand) = RepositoryResult.Success(Unit)
        override suspend fun setLifecycle(command: SetSubscriptionLifecycleCommand): RepositoryResult<Unit> {
            lastLifecycleCommand = command
            return RepositoryResult.Success(Unit)
        }
        override suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> {
            advanceRenewalCalled = true
            return RepositoryResult.Success(
                SubscriptionRenewalProgressionResult.Advanced(
                    nextRenewalDate = LocalDate(2026, 5, 14),
                ),
            )
        }
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeCategoryRepository(categories: List<Category> = emptyList()) : CategoryRepository {
        val categoriesFlow = MutableStateFlow(categories)
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(categoriesFlow.value.firstOrNull { it.id == id })
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = categoriesFlow
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FixedDateProvider(val date: LocalDate = LocalDate(2026, 4, 1)) : CurrentDateProvider {
        override fun today(): LocalDate = date
    }

    @Test
    fun `initial state observes subscription details and formats attributes correctly`() = runTest {
        val subRepo = FakeSubscriptionRepository(
            subscriptionFlow = MutableStateFlow(sampleSubscription),
            paymentsFlow = MutableStateFlow(listOf(samplePayment)),
        )
        val catRepo = FakeCategoryRepository(listOf(sampleCategory))
        val wsRepo = FakeWorkspaceRepository()

        val savedStateHandle = SavedStateHandle(mapOf("subscriptionId" to testSubscriptionId.value))
        val viewModel = SubscriptionDetailViewModel(
            savedStateHandle = savedStateHandle,
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeSubscriptionPriceHistoriesUseCase = ObserveSubscriptionPriceHistoriesUseCase(subRepo),
            observeSubscriptionPaymentsUseCase = ObserveSubscriptionPaymentsUseCase(subRepo),
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(wsRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subRepo),
            setSubscriptionLifecycleUseCase = SetSubscriptionLifecycleUseCase(subRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subRepo),
            currentDateProvider = FixedDateProvider(),
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isNotFound)
        assertEquals("Netflix", state.name)
        assertEquals("Eğlence", state.categoryName)
        assertEquals(SubscriptionLifecycleStatus.ACTIVE, state.lifecycleStatus)
        assertTrue(state.isAutoRenewActive)
        assertTrue(state.reminderEnabled)
        assertEquals("https://www.netflix.com", state.websiteUrl)
        assertEquals("Standart HD Paket", state.notes)
        assertEquals(1, state.totalPaymentsCount)

        collectJob.cancel()
    }

    @Test
    fun `toggleLifecycle changes status from ACTIVE to PAUSED`() = runTest {
        val subRepo = FakeSubscriptionRepository(
            subscriptionFlow = MutableStateFlow(sampleSubscription),
        )
        val catRepo = FakeCategoryRepository(listOf(sampleCategory))
        val wsRepo = FakeWorkspaceRepository()

        val savedStateHandle = SavedStateHandle(mapOf("subscriptionId" to testSubscriptionId.value))
        val viewModel = SubscriptionDetailViewModel(
            savedStateHandle = savedStateHandle,
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeSubscriptionPriceHistoriesUseCase = ObserveSubscriptionPriceHistoriesUseCase(subRepo),
            observeSubscriptionPaymentsUseCase = ObserveSubscriptionPaymentsUseCase(subRepo),
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(wsRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subRepo),
            setSubscriptionLifecycleUseCase = SetSubscriptionLifecycleUseCase(subRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subRepo),
            currentDateProvider = FixedDateProvider(),
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onToggleLifecycle()

        assertNotNull(subRepo.lastLifecycleCommand)
        assertEquals(SubscriptionLifecycleStatus.PAUSED, subRepo.lastLifecycleCommand?.status)

        collectJob.cancel()
    }

    @Test
    fun `advanceRenewal triggers repository advance renewal`() = runTest {
        val subRepo = FakeSubscriptionRepository(
            subscriptionFlow = MutableStateFlow(sampleSubscription),
        )
        val catRepo = FakeCategoryRepository(listOf(sampleCategory))
        val wsRepo = FakeWorkspaceRepository()

        val savedStateHandle = SavedStateHandle(mapOf("subscriptionId" to testSubscriptionId.value))
        val viewModel = SubscriptionDetailViewModel(
            savedStateHandle = savedStateHandle,
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeSubscriptionPriceHistoriesUseCase = ObserveSubscriptionPriceHistoriesUseCase(subRepo),
            observeSubscriptionPaymentsUseCase = ObserveSubscriptionPaymentsUseCase(subRepo),
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(wsRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subRepo),
            setSubscriptionLifecycleUseCase = SetSubscriptionLifecycleUseCase(subRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subRepo),
            currentDateProvider = FixedDateProvider(),
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAdvanceRenewal()

        assertTrue(subRepo.advanceRenewalCalled)

        collectJob.cancel()
    }

    @Test
    fun `deleteSubscription triggers delete and sends NavigateBack`() = runTest {
        val subRepo = FakeSubscriptionRepository(
            subscriptionFlow = MutableStateFlow(sampleSubscription),
        )
        val catRepo = FakeCategoryRepository(listOf(sampleCategory))
        val wsRepo = FakeWorkspaceRepository()

        val savedStateHandle = SavedStateHandle(mapOf("subscriptionId" to testSubscriptionId.value))
        val viewModel = SubscriptionDetailViewModel(
            savedStateHandle = savedStateHandle,
            observeSubscriptionUseCase = ObserveSubscriptionUseCase(subRepo),
            observeCategoriesUseCase = ObserveCategoriesUseCase(catRepo),
            observeSubscriptionPriceHistoriesUseCase = ObserveSubscriptionPriceHistoriesUseCase(subRepo),
            observeSubscriptionPaymentsUseCase = ObserveSubscriptionPaymentsUseCase(subRepo),
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(wsRepo),
            advanceSubscriptionRenewalUseCase = AdvanceSubscriptionRenewalUseCase(subRepo),
            setSubscriptionLifecycleUseCase = SetSubscriptionLifecycleUseCase(subRepo),
            updateSubscriptionUseCase = UpdateSubscriptionUseCase(subRepo),
            deleteSubscriptionUseCase = DeleteSubscriptionUseCase(subRepo),
            currentDateProvider = FixedDateProvider(),
        )

        val events = mutableListOf<SubscriptionDetailEvent>()
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onDeleteSubscription()

        assertEquals(testSubscriptionId, subRepo.lastDeletedId)
        assertTrue(events.any { it is SubscriptionDetailEvent.NavigateBack })

        collectJob.cancel()
        eventJob.cancel()
    }
}
