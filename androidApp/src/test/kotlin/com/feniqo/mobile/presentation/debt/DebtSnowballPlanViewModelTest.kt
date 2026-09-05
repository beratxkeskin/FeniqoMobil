package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.repository.DebtRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtsUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebtSnowballPlanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeDebtRepository : DebtRepository {
        val debtsFlow = MutableStateFlow<List<Debt>>(emptyList())
        val paymentsFlow = MutableStateFlow<Map<EntityId, List<DebtPayment>>>(emptyMap())

        override fun observeDebts(): Flow<List<Debt>> = flow {
            debtsFlow.collect { emit(it) }
        }

        override fun observeDebt(id: EntityId): Flow<Debt?> = flow {
            debtsFlow.collect { list -> emit(list.find { it.id == id }) }
        }

        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flow {
            paymentsFlow.collect { map -> emit(map[debtId] ?: emptyList()) }
        }

        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> = throw NotImplementedError()
        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> = throw NotImplementedError()
        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> = throw NotImplementedError()
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = throw NotImplementedError()
    }

    private lateinit var repository: FakeDebtRepository
    private lateinit var viewModel: DebtSnowballPlanViewModel

    private fun createDebt(
        id: String,
        amountMinor: Long,
        type: DebtType = DebtType.DEBT,
        status: DebtStatus = DebtStatus.OPEN,
        currency: Currency = Currency.TRY,
    ): Debt {
        return Debt(
            id = EntityId(id),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç $id",
            amount = Money(amountMinor, currency),
            type = type,
            dueDate = LocalDate(2026, 12, 31),
            status = status,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDebtRepository()
        viewModel = DebtSnowballPlanViewModel(
            observeDebtsUseCase = ObserveDebtsUseCase(repository),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_populatesAvailableCurrenciesAndEligibleDebtsCount() = runTest {
        val debtTry = createDebt(id = "d-1", amountMinor = 1000_00L, currency = Currency.TRY)
        val debtUsd = createDebt(id = "d-2", amountMinor = 500_00L, currency = Currency.USD)
        val receivableTry = createDebt(id = "r-1", amountMinor = 2000_00L, type = DebtType.RECEIVABLE, currency = Currency.TRY)
        val settledTry = createDebt(id = "d-3", amountMinor = 300_00L, status = DebtStatus.SETTLED, currency = Currency.TRY)

        repository.debtsFlow.value = listOf(debtTry, debtUsd, receivableTry, settledTry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingDebts)
        assertEquals(listOf(Currency.TRY, Currency.USD), state.availableCurrencies)
        assertEquals(Currency.TRY, state.selectedCurrency)
        assertEquals(1, state.eligibleDebtsCount) // Only debtTry
    }

    @Test
    fun selectCurrency_filtersEligibleCountAndClearsStalePlan() = runTest {
        val debtTry = createDebt(id = "d-1", amountMinor = 1000_00L, currency = Currency.TRY)
        val debtUsd1 = createDebt(id = "d-2", amountMinor = 500_00L, currency = Currency.USD)
        val debtUsd2 = createDebt(id = "d-3", amountMinor = 700_00L, currency = Currency.USD)

        repository.debtsFlow.value = listOf(debtTry, debtUsd1, debtUsd2)
        advanceUntilIdle()

        // Initially TRY
        assertEquals(1, viewModel.uiState.value.eligibleDebtsCount)

        // Select USD
        viewModel.selectCurrency(Currency.USD)
        val stateUsd = viewModel.uiState.value
        assertEquals(Currency.USD, stateUsd.selectedCurrency)
        assertEquals(2, stateUsd.eligibleDebtsCount)
        assertNull(stateUsd.plan)
    }

    @Test
    fun updateBudgetInput_clearsBudgetErrorAndStalePlan() = runTest {
        viewModel.updateBudgetInput("500")
        assertEquals("500", viewModel.uiState.value.budgetInput)
        assertNull(viewModel.uiState.value.budgetError)
        assertNull(viewModel.uiState.value.plan)
    }

    @Test
    fun calculatePlan_withValidBudget_computesPlanCorrectly() = runTest {
        val debt1 = createDebt(id = "d-1", amountMinor = 500_00L) // 500 TRY
        val debt2 = createDebt(id = "d-2", amountMinor = 1500_00L) // 1500 TRY

        repository.debtsFlow.value = listOf(debt1, debt2)
        advanceUntilIdle()

        viewModel.updateBudgetInput("1000")
        viewModel.calculatePlan()

        val state = viewModel.uiState.value
        assertNull(state.budgetError)
        assertNotNull(state.plan)

        val plan = state.plan!!
        assertEquals(2, plan.totalMonths)
        assertEquals(2, plan.debtItems.size)
        assertEquals("Borç d-1", plan.debtItems[0].debtTitle)
        assertEquals(1, plan.debtItems[0].settledInMonth)
        assertEquals("Borç d-2", plan.debtItems[1].debtTitle)
        assertEquals(2, plan.debtItems[1].settledInMonth)
    }

    @Test
    fun calculatePlan_withEmptyOrInvalidBudget_setsBudgetErrorAndDoesNotSetPlan() = runTest {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        repository.debtsFlow.value = listOf(debt)
        advanceUntilIdle()

        // 1. Boş bütçe
        viewModel.updateBudgetInput("")
        viewModel.calculatePlan()
        assertEquals("Aylık bütçe zorunludur.", viewModel.uiState.value.budgetError)
        assertNull(viewModel.uiState.value.plan)

        // 2. Sıfır veya negatif bütçe
        viewModel.updateBudgetInput("0")
        viewModel.calculatePlan()
        assertEquals("Aylık bütçe sıfırdan büyük olmalıdır.", viewModel.uiState.value.budgetError)
        assertNull(viewModel.uiState.value.plan)

        // 3. Geçersiz format
        viewModel.updateBudgetInput("abc")
        viewModel.calculatePlan()
        assertEquals("Geçerli bir tutar girin.", viewModel.uiState.value.budgetError)
        assertNull(viewModel.uiState.value.plan)
    }

    @Test
    fun whenDebtsOrPaymentsChangeInSSOT_clearsStalePlan() = runTest {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        repository.debtsFlow.value = listOf(debt)
        advanceUntilIdle()

        viewModel.updateBudgetInput("500")
        viewModel.calculatePlan()
        assertNotNull(viewModel.uiState.value.plan)

        // SSOT'a yeni ödeme geldiğinde
        val payment = DebtPayment(
            id = EntityId("p-1"),
            debtId = EntityId("d-1"),
            amount = Money(200_00L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(payment))
        advanceUntilIdle()

        // Eski plan sıfırlanmış olmalıdır
        assertNull(viewModel.uiState.value.plan)
    }

    @Test
    fun calculatePlan_whenPlannerThrows_emitsGenericErrorEvent() = runTest {
        // İki duplicate id içeren bozuk snapshot simülasyonu
        val debt1 = createDebt(id = "d-dup", amountMinor = 1000_00L)
        val debt2 = createDebt(id = "d-dup", amountMinor = 2000_00L)

        repository.debtsFlow.value = listOf(debt1, debt2)
        advanceUntilIdle()

        val emittedEvents = mutableListOf<DebtSnowballPlanUiEvent>()
        val job = launch { viewModel.events.collect { emittedEvents.add(it) } }

        viewModel.updateBudgetInput("500")
        viewModel.calculatePlan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.plan)
        assertEquals(1, emittedEvents.size)
        assertEquals(DebtSnowballPlanUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), emittedEvents.first())

        job.cancel()
    }

    @Test
    fun processDebtsSnapshot_whenPaymentCorrupted_setsObservationErrorAndClearsPlanAndCount() = runTest {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        repository.debtsFlow.value = listOf(debt)
        advanceUntilIdle()

        viewModel.updateBudgetInput("500")
        viewModel.calculatePlan()
        assertNotNull(viewModel.uiState.value.plan)
        assertNull(viewModel.uiState.value.observationError)
        assertEquals(1, viewModel.uiState.value.eligibleDebtsCount)

        // Bozuk snapshot: borç tutarını aşan ödeme (overpayment)
        val overPayment = DebtPayment(
            id = EntityId("p-over"),
            debtId = EntityId("d-1"),
            amount = Money(1500_00L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(overPayment))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)
        assertNull(state.plan)
        assertEquals(0, state.eligibleDebtsCount)
    }

    @Test
    fun processDebtsSnapshot_whenHealthySnapshotArrivesAfterError_clearsObservationError() = runTest {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        repository.debtsFlow.value = listOf(debt)
        advanceUntilIdle()

        // Bozuk snapshot
        val overPayment = DebtPayment(
            id = EntityId("p-over"),
            debtId = EntityId("d-1"),
            amount = Money(1500_00L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(overPayment))
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.observationError)

        // Sağlıklı snapshot ile düzelme
        val validPayment = DebtPayment(
            id = EntityId("p-valid"),
            debtId = EntityId("d-1"),
            amount = Money(300_00L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(validPayment))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.observationError)
        assertEquals(1, state.eligibleDebtsCount)
    }

    @Test
    fun observation_whenCancelled_doesNotSetObservationError() = runTest {
        val flowTrigger = MutableStateFlow(0)
        val cancellableRepo = object : DebtRepository by repository {
            override fun observeDebts(): Flow<List<Debt>> = flow {
                flowTrigger.collect {
                    if (it == 1) {
                        throw CancellationException("Simulated cancellation")
                    }
                    emit(listOf(createDebt(id = "d-1", amountMinor = 1000_00L)))
                }
            }
        }

        val testViewModel = DebtSnowballPlanViewModel(
            observeDebtsUseCase = ObserveDebtsUseCase(cancellableRepo),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(cancellableRepo),
        )
        advanceUntilIdle()

        assertNull(testViewModel.uiState.value.observationError)

        // Cancellation trigger
        flowTrigger.value = 1
        advanceUntilIdle()

        assertNull(testViewModel.uiState.value.observationError)
    }

    @Test
    fun retry_restartsObservationAndClearsError() = runTest {
        val debt = createDebt(id = "d-1", amountMinor = 1000_00L)
        repository.debtsFlow.value = listOf(debt)
        advanceUntilIdle()

        // Hata üret
        val overPayment = DebtPayment(
            id = EntityId("p-over"),
            debtId = EntityId("d-1"),
            amount = Money(1500_00L, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(overPayment))
        advanceUntilIdle()

        assertEquals(FinanceUiMessage.GENERIC_ERROR, viewModel.uiState.value.observationError)

        // Hatayı düzelt ve retry çağır
        repository.paymentsFlow.value = emptyMap()
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.observationError)
        assertEquals(1, state.eligibleDebtsCount)
    }
}
