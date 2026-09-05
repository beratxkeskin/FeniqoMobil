package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AppError
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
import com.feniqo.mobile.domain.usecase.AddDebtPaymentUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebtPaymentFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate(2026, 9, 3)

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeDebtRepository : DebtRepository {
        val debtsFlow = MutableStateFlow<Map<EntityId, Debt>>(emptyMap())
        val paymentsFlow = MutableStateFlow<Map<EntityId, List<DebtPayment>>>(emptyMap())
        var addPaymentResult: RepositoryResult<EntityId>? = null
        var lastAddPaymentCommand: AddDebtPaymentCommand? = null
        var throwCancellationOnAdd: Boolean = false


        override fun observeDebts(): Flow<List<Debt>> = flowOf(debtsFlow.value.values.toList())

        override fun observeDebt(id: EntityId): Flow<Debt?> = flow {
            debtsFlow.collect { map ->
                emit(map[id])
            }
        }

        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flow {
            paymentsFlow.collect { map ->
                emit(map[debtId] ?: emptyList())
            }
        }

        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> {
            throw NotImplementedError()
        }

        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> {
            throw NotImplementedError()
        }

        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> {
            if (throwCancellationOnAdd) {
                throw CancellationException("Simulated coroutine cancellation")
            }
            lastAddPaymentCommand = command
            return addPaymentResult ?: RepositoryResult.Success(EntityId("dp-100"))
        }


        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            throw NotImplementedError()
        }
    }

    private lateinit var repository: FakeDebtRepository
    private lateinit var viewModel: DebtPaymentFormViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDebtRepository()
        viewModel = DebtPaymentFormViewModel(
            observeDebtUseCase = ObserveDebtUseCase(repository),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(repository),
            addDebtPaymentUseCase = AddDebtPaymentUseCase(repository),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialMovementState_isCorrect() {
        val state = viewModel.uiState.value
        assertEquals(fixedToday, state.input.paidOn)
        assertEquals("", state.input.amountInput)
        assertFalse(state.isSubmitting)
        assertEquals(DebtPaymentParentLoadState.Idle, viewModel.parentLoadState.value)
    }

    @Test
    fun loadParentDebt_readyPopulatesDebtAndComputesRemaining() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val samplePayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = debtId,
                amount = Money(4_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 15),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(debtId to samplePayments)

        viewModel.loadParentDebt(debtId)
        advanceUntilIdle()

        val loadState = viewModel.parentLoadState.value
        assertTrue(loadState is DebtPaymentParentLoadState.Ready)
        val ready = loadState as DebtPaymentParentLoadState.Ready
        assertEquals("Kredi", ready.debt.title)
        assertEquals(Money(6_000_00L, Currency.TRY), ready.remainingAmount)
        assertFalse(ready.isSettled)
    }

    @Test
    fun loadParentDebt_notFound_setsNotFound() = runTest {
        viewModel.loadParentDebt(EntityId("non-existent"))
        advanceUntilIdle()

        assertEquals(DebtPaymentParentLoadState.NotFound, viewModel.parentLoadState.value)
    }

    @Test
    fun setParentLoadInvalidId_setsNotFound() {
        viewModel.setParentLoadInvalidId()
        assertEquals(DebtPaymentParentLoadState.NotFound, viewModel.parentLoadState.value)
    }

    @Test
    fun submit_addPayment_successful() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        viewModel.loadParentDebt(debtId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "3000",
                paidOn = LocalDate(2026, 9, 3),
            )
        }

        val emittedEvents = mutableListOf<DebtPaymentFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(DebtPaymentFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_PAYMENT_ADDED), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        val cmd = repository.lastAddPaymentCommand
        assertTrue(cmd != null)
        assertEquals(debtId, cmd?.debtId)
        assertEquals(Money(3_000_00L, Currency.TRY), cmd?.amount)
        assertEquals(LocalDate(2026, 9, 3), cmd?.paidOn)

        job.cancel()
    }

    @Test
    fun submit_addPayment_exceedsRemaining_failsClosed() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(5_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val samplePayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = debtId,
                amount = Money(4_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 15),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(debtId to samplePayments)
        viewModel.loadParentDebt(debtId)
        advanceUntilIdle()

        // Kalan 1000 TL, girilen 2000 TL
        viewModel.updateInput {
            it.copy(
                amountInput = "2000",
                paidOn = LocalDate(2026, 9, 3),
            )
        }

        val emittedEvents = mutableListOf<DebtPaymentFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errors.hasErrors)
        assertEquals(DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT, viewModel.uiState.value.errors.amountError)
        assertEquals(0, emittedEvents.size)
        assertNull(repository.lastAddPaymentCommand)

        job.cancel()
    }

    @Test
    fun dynamicBalanceUpdate_reactivePropagation() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(5_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(debtId to emptyList())
        viewModel.loadParentDebt(debtId)
        advanceUntilIdle()

        var readyState = viewModel.parentLoadState.value as DebtPaymentParentLoadState.Ready
        assertEquals(Money(5_000_00L, Currency.TRY), readyState.remainingAmount)
        assertFalse(readyState.isSettled)

        // Yeni ödeme geldiğinde reaktif olarak kalan bakiye güncellenmeli
        val newPayments = listOf(
            DebtPayment(
                id = EntityId("p-1"),
                debtId = debtId,
                amount = Money(5_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 9, 3),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )
        repository.paymentsFlow.value = mapOf(debtId to newPayments)
        advanceUntilIdle()

        readyState = viewModel.parentLoadState.value as DebtPaymentParentLoadState.Ready
        assertEquals(Money(0, Currency.TRY), readyState.remainingAmount)
        assertTrue(readyState.isSettled)
    }

    @Test
    fun submit_whenCancelled_resetsIsSubmittingWithoutEmittingEventsAndPreservesState() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(5_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(debtId to emptyList())
        repository.throwCancellationOnAdd = true
        viewModel.loadParentDebt(debtId)
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                amountInput = "1000",
                paidOn = LocalDate(2026, 9, 3),
            )
        }

        val emittedEvents = mutableListOf<DebtPaymentFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        // 1. Hiçbir event yayılmamalı
        assertEquals(0, emittedEvents.size)
        // 2. isSubmitting kilidi kapatılmış olmalı (false)
        assertFalse(viewModel.uiState.value.isSubmitting)
        // 3. Kullanıcı girdileri ve parent load state korunmalı
        assertEquals("1000", viewModel.uiState.value.input.amountInput)
        assertEquals(LocalDate(2026, 9, 3), viewModel.uiState.value.input.paidOn)
        assertTrue(viewModel.parentLoadState.value is DebtPaymentParentLoadState.Ready)

        job.cancel()
    }
}

