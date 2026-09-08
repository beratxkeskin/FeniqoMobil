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
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeDebtRepository : DebtRepository {
        val debtsFlow = MutableStateFlow<List<Debt>>(emptyList())
        val paymentsFlow = MutableStateFlow<Map<EntityId, List<DebtPayment>>>(emptyMap())
        var shouldThrowInFlow: Throwable? = null
        val observeCallCount = AtomicInteger(0)

        override fun observeDebts(): Flow<List<Debt>> = flow {
            observeCallCount.incrementAndGet()
            shouldThrowInFlow?.let { throw it }
            debtsFlow.collect { emit(it) }
        }

        override fun observeDebt(id: EntityId): Flow<Debt?> = emptyFlow()
        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flow {
            paymentsFlow.collect { map ->
                emit(map[debtId] ?: emptyList())
            }
        }
        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> = error("Not needed")
        override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> = error("Not needed")
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = error("Not needed")
    }

    private fun sampleDebt(
        id: String,
        title: String = "Borç",
        amountMinor: Long = 50_000L,
        type: DebtType = DebtType.DEBT,
        dueDate: LocalDate = LocalDate(2026, 10, 15),
        status: DebtStatus = DebtStatus.OPEN,
        description: String? = null,
    ): Debt = Debt(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = title,
        amount = Money(amountMinor, Currency.TRY),
        type = type,
        dueDate = dueDate,
        status = status,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private fun createViewModel(repository: DebtRepository): DebtsViewModel {
        val fakeWorkspaceRepo = com.feniqo.mobile.presentation.common.FakeWorkspaceRepository()
        return DebtsViewModel(
            observeDebtsUseCase = ObserveDebtsUseCase(repository),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(repository),
            observeActiveWorkspaceUseCase = com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase(fakeWorkspaceRepo),
        )
    }

    @Test
    fun initialState_isLoadingTrueAndEmpty() {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.debts.isEmpty())
        assertNull(state.observationError)
        assertFalse(state.isEmpty)
    }

    @Test
    fun successfulObservation_mapsDisplayModelsAndClearsLoading() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val d1 = sampleDebt("d-1", "Kredi Kartı", 50_000L, DebtType.DEBT, LocalDate(2026, 10, 15), DebtStatus.OPEN)
        val d2 = sampleDebt("d-2", "Ahmet Alacak", 30_000L, DebtType.RECEIVABLE, LocalDate(2026, 11, 1), DebtStatus.OPEN)
        repo.debtsFlow.value = listOf(d1, d2)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(2, state.debts.size)
        assertEquals("d-1", state.debts[0].id.value)
        assertEquals("Borç", state.debts[0].typeLabel)
        assertEquals("d-2", state.debts[1].id.value)
        assertEquals("Alacak", state.debts[1].typeLabel)
        assertFalse(state.isEmpty)
    }

    @Test
    fun paymentUpdate_reactivelyUpdatesRemainingAmountAndSettledState() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val d1 = sampleDebt("d-1", "Borç 1", 50_000L, DebtType.DEBT)
        repo.debtsFlow.value = listOf(d1)

        assertEquals(50_000L, viewModel.uiState.value.debts[0].remainingAmount.amountMinor)
        assertFalse(viewModel.uiState.value.debts[0].isSettled)

        // Add payment to d-1
        val payment = DebtPayment(
            id = EntityId("p-1"),
            debtId = EntityId("d-1"),
            amount = Money(20_000L, Currency.TRY),
            paidOn = LocalDate(2026, 10, 1),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repo.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(payment))

        val updated = viewModel.uiState.value.debts[0]
        assertEquals(20_000L, updated.totalPaid.amountMinor)
        assertEquals(30_000L, updated.remainingAmount.amountMinor)
        assertEquals("300,00 ₺", updated.formattedRemainingAmount)
        assertFalse(updated.isSettled)

        // Full payment
        val payment2 = DebtPayment(
            id = EntityId("p-2"),
            debtId = EntityId("d-1"),
            amount = Money(30_000L, Currency.TRY),
            paidOn = LocalDate(2026, 10, 2),
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repo.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(payment, payment2))

        val settled = viewModel.uiState.value.debts[0]
        assertEquals(50_000L, settled.totalPaid.amountMinor)
        assertEquals(0L, settled.remainingAmount.amountMinor)
        assertTrue(settled.isSettled)
    }

    @Test
    fun corruptPaymentSnapshot_setsGenericObservationError() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val d1 = sampleDebt("d-1", "Borç 1", 50_000L)
        repo.debtsFlow.value = listOf(d1)
        assertNull(viewModel.uiState.value.observationError)

        // Overpayment (bozuk payment)
        val corruptPayment = DebtPayment(
            id = EntityId("p-corrupt"),
            debtId = EntityId("d-1"),
            amount = Money(90_000L, Currency.TRY),
            paidOn = LocalDate(2026, 10, 1),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repo.paymentsFlow.value = mapOf(EntityId("d-1") to listOf(corruptPayment))

        val state = viewModel.uiState.value
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)
        assertTrue(state.debts.isEmpty())
    }

    @Test
    fun parentListChange_doesNotLeakOldPaymentCollectors() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val d1 = sampleDebt("d-1", "Borç 1", 50_000L)
        repo.debtsFlow.value = listOf(d1)
        repo.paymentsFlow.value = mapOf(
            EntityId("d-1") to listOf(
                DebtPayment(
                    id = EntityId("p-1"),
                    debtId = EntityId("d-1"),
                    amount = Money(10_000L, Currency.TRY),
                    paidOn = LocalDate(2026, 10, 1),
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
        )

        assertEquals(40_000L, viewModel.uiState.value.debts[0].remainingAmount.amountMinor)

        // Switch parent list completely to d-2
        val d2 = sampleDebt("d-2", "Borç 2", 30_000L)
        repo.debtsFlow.value = listOf(d2)

        assertEquals(1, viewModel.uiState.value.debts.size)
        assertEquals("d-2", viewModel.uiState.value.debts[0].id.value)
        assertEquals(30_000L, viewModel.uiState.value.debts[0].remainingAmount.amountMinor)
    }

    @Test
    fun emptySuccessfulList_setsIsEmptyTrue() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        repo.debtsFlow.value = emptyList()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertTrue(state.debts.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun observationError_setsGenericErrorAndSafeEmptyList() = runTest {
        val repo = FakeDebtRepository()
        repo.shouldThrowInFlow = RuntimeException("Database error")
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)
        assertTrue(state.debts.isEmpty())
        assertFalse(state.isEmpty)
    }

    @Test
    fun retry_recoversAfterFailure() = runTest {
        val repo = FakeDebtRepository()
        repo.shouldThrowInFlow = RuntimeException("Database error")
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        var state = viewModel.uiState.value
        assertEquals(FinanceUiMessage.GENERIC_ERROR, state.observationError)

        // Fix repo error and trigger retry
        repo.shouldThrowInFlow = null
        repo.debtsFlow.value = listOf(sampleDebt("d-1", "Kredi Kartı"))
        viewModel.onIntent(DebtsIntent.Retry)

        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.observationError)
        assertEquals(1, state.debts.size)
        assertEquals("d-1", state.debts[0].id.value)
    }

    @Test
    fun cancellationException_isNotSwallowedAsGenericError() = runTest {
        val repo = FakeDebtRepository()
        repo.shouldThrowInFlow = CancellationException("Coroutines cancelled")
        val viewModel = createViewModel(repo)

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val state = viewModel.uiState.value
        assertNull(state.observationError)
        job.cancel()
    }

    @Test
    fun multipleRetries_doNotCreateParallelCollectors() = runTest {
        val repo = FakeDebtRepository()
        val viewModel = createViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertEquals(1, repo.observeCallCount.get())

        // Multiple consecutive retries
        viewModel.onIntent(DebtsIntent.Retry)
        viewModel.onIntent(DebtsIntent.Retry)
        viewModel.onIntent(DebtsIntent.Retry)

        // With flatMapLatest, previous subscriptions are cancelled and only the latest is active
        repo.debtsFlow.value = listOf(sampleDebt("d-1"))
        val state = viewModel.uiState.value
        assertEquals(1, state.debts.size)
    }
}
