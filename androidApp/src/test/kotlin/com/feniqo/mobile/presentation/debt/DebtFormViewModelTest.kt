package com.feniqo.mobile.presentation.debt

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
import com.feniqo.mobile.domain.usecase.CreateDebtUseCase
import com.feniqo.mobile.domain.usecase.DeleteDebtUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveDebtUseCase
import com.feniqo.mobile.domain.usecase.UpdateDebtUseCase
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
class DebtFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate(2026, 9, 3)

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeDebtRepository : DebtRepository {
        val debtsFlow = MutableStateFlow<Map<EntityId, Debt>>(emptyMap())
        val paymentsFlow = MutableStateFlow<Map<EntityId, List<DebtPayment>>>(emptyMap())
        var observePaymentsError: Exception? = null
        var createResult: RepositoryResult<EntityId>? = null
        var updateResult: RepositoryResult<Unit>? = null
        var deleteResult: RepositoryResult<Unit>? = null

        var lastCreateCommand: CreateDebtCommand? = null
        var createCallCount: Int = 0
        var lastUpdateCommand: UpdateDebtCommand? = null
        var lastDeletedId: EntityId? = null

        override fun observeDebts(): Flow<List<Debt>> = flowOf(debtsFlow.value.values.toList())

        override fun observeDebt(id: EntityId): Flow<Debt?> = flow {
            debtsFlow.collect { map ->
                emit(map[id])
            }
        }

        override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> = flow {
            val err = observePaymentsError
            if (err != null) throw err
            paymentsFlow.collect { map ->
                emit(map[debtId] ?: emptyList())
            }
        }

        override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> {
            createCallCount++
            lastCreateCommand = command
            return createResult ?: RepositoryResult.Success(EntityId("debt-created"))
        }

        override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> {
            lastUpdateCommand = command
            return updateResult ?: RepositoryResult.Success(Unit)
        }

        override suspend fun addPayment(command: com.feniqo.mobile.domain.model.AddDebtPaymentCommand): RepositoryResult<EntityId> {
            throw NotImplementedError()
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastDeletedId = id
            return deleteResult ?: RepositoryResult.Success(Unit)
        }
    }


    private lateinit var repository: FakeDebtRepository
    private lateinit var viewModel: DebtFormViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDebtRepository()
        viewModel = DebtFormViewModel(
            observeDebtUseCase = ObserveDebtUseCase(repository),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(repository),
            createDebtUseCase = CreateDebtUseCase(repository),
            updateDebtUseCase = UpdateDebtUseCase(repository),
            deleteDebtUseCase = DeleteDebtUseCase(repository),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )
    }


    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialCreateState_isCorrect() {
        val state = viewModel.uiState.value
        assertTrue(state.input.isCreateMode)
        assertEquals(fixedToday, state.input.dueDate)
        assertEquals(DebtType.DEBT, state.input.type)
        assertEquals(Currency.TRY, state.input.currency)
        assertEquals(DebtEditLoadState.Idle, viewModel.editLoadState.value)
        assertFalse(state.isSubmitting)
        assertFalse(state.pendingDeleteConfirmation)
    }

    @Test
    fun loadDebtForEdit_setsReadyAndPopulatesForm() = runTest {
        val debtId = EntityId("d-10")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Banka Kredisi",
            amount = Money(50_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2027, 2, 20),
            status = DebtStatus.OPEN,
            description = "Aylık ödemeli",
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        val loadState = viewModel.editLoadState.value
        assertTrue(loadState is DebtEditLoadState.Ready)
        val ready = loadState as DebtEditLoadState.Ready
        assertEquals("Banka Kredisi", ready.draft.title)
        assertEquals(DebtStatus.OPEN, ready.status)

        val formState = viewModel.uiState.value
        assertTrue(formState.input.isEditMode)
        assertEquals(debtId, formState.input.debtId)
        assertEquals("Banka Kredisi", formState.input.titleInput)
        assertEquals("50000", formState.input.amountInput)
        assertEquals(Currency.TRY, formState.input.currency)
        assertEquals(DebtType.DEBT, formState.input.type)
        assertEquals(LocalDate(2027, 2, 20), formState.input.dueDate)
        assertEquals("Aylık ödemeli", formState.input.descriptionInput)
    }

    @Test
    fun loadDebtForEdit_notFound_setsNotFound() = runTest {
        viewModel.loadDebtForEdit(EntityId("non-existent"), decimalSeparator = ',')
        advanceUntilIdle()

        assertEquals(DebtEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun setEditLoadInvalidId_setsNotFound() {
        viewModel.setEditLoadInvalidId()
        assertEquals(DebtEditLoadState.NotFound, viewModel.editLoadState.value)
    }

    @Test
    fun loadDebtForEdit_raceConditionProtection_ignoresOlderObservation() = runTest {
        val slowDebtId = EntityId("d-slow")
        val fastDebtId = EntityId("d-fast")

        val slowFlow = MutableSharedFlow<Debt?>()
        val fastDebt = Debt(
            id = fastDebtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Hızlı Borç",
            amount = Money(200_00L, Currency.TRY),
            type = DebtType.RECEIVABLE,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val customRepo = object : DebtRepository by repository {
            override fun observeDebt(id: EntityId): Flow<Debt?> {
                return if (id == slowDebtId) slowFlow else flowOf(fastDebt)
            }
        }
        val customViewModel = DebtFormViewModel(
            observeDebtUseCase = ObserveDebtUseCase(customRepo),
            observeDebtPaymentsUseCase = ObserveDebtPaymentsUseCase(customRepo),
            createDebtUseCase = CreateDebtUseCase(customRepo),
            updateDebtUseCase = UpdateDebtUseCase(customRepo),
            deleteDebtUseCase = DeleteDebtUseCase(customRepo),
            currentDateProvider = FakeCurrentDateProvider(fixedToday),
        )


        // 1. Yavaş olanı yükle
        customViewModel.loadDebtForEdit(slowDebtId, decimalSeparator = ',')
        // 2. Hızlı olanı yükle
        customViewModel.loadDebtForEdit(fastDebtId, decimalSeparator = ',')
        advanceUntilIdle()

        assertEquals(DebtEditLoadState.Ready(DebtFormDraft.fromDomain(fastDebt), fastDebt.status), customViewModel.editLoadState.value)

        // 3. Yavaş olan gecikmeli gelse bile yeni state ezilmemeli
        val slowDebt = Debt(
            id = slowDebtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Eski Yavaş",
            amount = Money(500_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        slowFlow.emit(slowDebt)
        advanceUntilIdle()

        assertEquals("Hızlı Borç", (customViewModel.editLoadState.value as DebtEditLoadState.Ready).draft.title)
    }

    @Test
    fun updateInput_preservesLockedCurrencyAndDebtIdInEditMode_whileAllowsTypeChange() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Mevcut",
            amount = Money(1000_00L, Currency.USD),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        viewModel.updateInput {
            it.copy(
                titleInput = "Yeni Başlık",
                currency = Currency.EUR, // Editte değiştirilemez
                debtId = EntityId("other-id"), // ID değiştirilemez
                type = DebtType.RECEIVABLE, // Tür değişimi serbest
            )
        }

        val state = viewModel.uiState.value
        assertEquals("Yeni Başlık", state.input.titleInput)
        assertEquals(Currency.USD, state.input.currency)
        assertEquals(debtId, state.input.debtId)
        assertEquals(DebtType.RECEIVABLE, state.input.type)
    }

    @Test
    fun submit_create_success_staysLockedUntilNavigationAndRejectsSecondSubmit() = runTest {
        viewModel.updateInput {
            it.copy(
                titleInput = "Arkadaşa Borç",
                amountInput = "1200",
                currency = Currency.TRY,
                type = DebtType.RECEIVABLE,
                dueDate = LocalDate(2026, 12, 1),
                descriptionInput = "Elden",
            )
        }

        val emittedEvents = mutableListOf<DebtFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(DebtFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_SAVED), emittedEvents.first())
        assertEquals(1, repository.createCallCount)
        assertTrue(viewModel.uiState.value.isSubmitting)

        val cmd = repository.lastCreateCommand
        assertTrue(cmd != null)
        assertEquals("Arkadaşa Borç", cmd?.title)
        assertEquals(Money(120_000L, Currency.TRY), cmd?.amount)
        assertEquals(DebtType.RECEIVABLE, cmd?.type)
        assertEquals("Elden", cmd?.description)

        job.cancel()
    }

    @Test
    fun submit_validationError_doesNotInvokeUseCase_andPopulatesErrors() = runTest {
        viewModel.updateInput {
            it.copy(
                titleInput = "", // Hatalı
                amountInput = "-5", // Hatalı
            )
        }

        val emittedEvents = mutableListOf<DebtFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errors.hasErrors)
        assertEquals(DebtFormFieldError.TITLE_REQUIRED, viewModel.uiState.value.errors.titleError)
        assertEquals(DebtFormFieldError.AMOUNT_INVALID, viewModel.uiState.value.errors.amountError)
        assertEquals(0, emittedEvents.size)
        assertNull(repository.lastCreateCommand)

        job.cancel()
    }

    @Test
    fun submit_failure_emitsShowMessage() = runTest {
        repository.createResult = RepositoryResult.Failure(AppError.Storage("hata"))
        viewModel.updateInput {
            it.copy(
                titleInput = "Borç",
                amountInput = "500",
                dueDate = LocalDate(2027, 1, 1),
            )
        }

        val emittedEvents = mutableListOf<DebtFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(DebtFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR), emittedEvents.first())
        assertFalse(viewModel.uiState.value.isSubmitting)

        job.cancel()
    }

    @Test
    fun deleteFlow_requiresConfirmation_andDeletesOnConfirm() = runTest {
        val debtId = EntityId("d-del")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Silinecek",
            amount = Money(1000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        // 1. Request delete
        viewModel.requestDelete()
        assertTrue(viewModel.uiState.value.pendingDeleteConfirmation)

        // 2. Dismiss delete
        viewModel.dismissDelete()
        assertFalse(viewModel.uiState.value.pendingDeleteConfirmation)
        assertNull(repository.lastDeletedId)

        // 3. Request delete again & Confirm
        viewModel.requestDelete()
        val emittedEvents = mutableListOf<DebtFormUiEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(debtId, repository.lastDeletedId)
        assertFalse(viewModel.uiState.value.pendingDeleteConfirmation)
        assertEquals(1, emittedEvents.size)
        assertEquals(DebtFormUiEvent.MutationSuccess(FinanceUiMessage.DEBT_DELETED), emittedEvents.first())

        job.cancel()
    }

    @Test
    fun loadDebtForEdit_observesPaymentsAndPopulatesSortedHistory() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi Kartı",
            amount = Money(20_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val payments = listOf(
            DebtPayment(
                id = EntityId("dp-1"),
                debtId = debtId,
                amount = Money(5_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 1),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
            DebtPayment(
                id = EntityId("dp-2"),
                debtId = debtId,
                amount = Money(7_000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(debtId to payments)

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        val history = viewModel.uiState.value.paymentsHistory
        assertEquals(2, history.size)
        // 2026-09-01 (dp-2) first, then 2026-08-01 (dp-1)
        assertEquals(EntityId("dp-2"), history[0].id)
        assertEquals(EntityId("dp-1"), history[1].id)
    }

    @Test
    fun loadDebtForEdit_whenDebtNotFound_clearsPaymentsHistory() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.paymentsFlow.value = mapOf(
            debtId to listOf(
                DebtPayment(
                    id = EntityId("dp-1"),
                    debtId = debtId,
                    amount = Money(5_000_00L, Currency.TRY),
                    paidOn = LocalDate(2026, 9, 1),
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
        )

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.paymentsHistory.size)

        // Borç silindiğinde
        repository.debtsFlow.value = emptyMap()
        advanceUntilIdle()

        assertEquals(DebtEditLoadState.NotFound, viewModel.editLoadState.value)
        assertTrue(viewModel.uiState.value.paymentsHistory.isEmpty())
    }

    @Test
    fun loadDebtForEdit_whenObservationErrors_clearsPaymentsHistoryAndEmitsError() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.observePaymentsError = RuntimeException("Database error")

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        assertEquals(DebtEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR), viewModel.editLoadState.value)
        assertTrue(viewModel.uiState.value.paymentsHistory.isEmpty())
    }

    @Test
    fun loadDebtForEdit_switchingParent_clearsOldPaymentsHistoryImmediately() = runTest {
        val debt1 = Debt(
            id = EntityId("d-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç 1",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val debt2 = Debt(
            id = EntityId("d-2"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç 2",
            amount = Money(20_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.debtsFlow.value = mapOf(EntityId("d-1") to debt1, EntityId("d-2") to debt2)
        repository.paymentsFlow.value = mapOf(
            EntityId("d-1") to listOf(
                DebtPayment(
                    id = EntityId("dp-1"),
                    debtId = EntityId("d-1"),
                    amount = Money(5_000_00L, Currency.TRY),
                    paidOn = LocalDate(2026, 9, 1),
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
            EntityId("d-2") to emptyList(),
        )

        viewModel.loadDebtForEdit(EntityId("d-1"), decimalSeparator = ',')
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.paymentsHistory.size)

        // Borç 2'ye geçildiğinde eski geçmiş hemen temizlenmeli
        viewModel.loadDebtForEdit(EntityId("d-2"), decimalSeparator = ',')
        assertTrue(viewModel.uiState.value.paymentsHistory.isEmpty())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.paymentsHistory.isEmpty())
    }

    @Test
    fun loadDebtForEdit_whenCancelled_doesNotMaskCancellationAsGenericErrorAndClearsHistory() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Borç",
            amount = Money(10_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)
        repository.observePaymentsError = CancellationException("Observation cancelled")

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        // CancellationException generic hataya dönüşmemeli
        assertFalse(viewModel.editLoadState.value is DebtEditLoadState.Error)
        // Geçmiş listesi generic hata veya eski veri altında kalmamalı
        assertTrue(viewModel.uiState.value.paymentsHistory.isEmpty())
        assertNull(viewModel.uiState.value.balanceSummary)
    }

    @Test
    fun loadDebtForEdit_reactivelyUpdatesBalanceSummary_andHandlesSettledState() = runTest {
        val debtId = EntityId("d-1")
        val sampleDebt = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi Kartı",
            amount = Money(20_000_00L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleDebt)

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        val initialSummary = viewModel.uiState.value.balanceSummary
        assertTrue(initialSummary != null)
        assertEquals(Money(20_000_00L, Currency.TRY), initialSummary?.principalAmount)
        assertEquals(Money(0L, Currency.TRY), initialSummary?.totalPaid)
        assertEquals(Money(20_000_00L, Currency.TRY), initialSummary?.remainingAmount)
        assertFalse(initialSummary!!.isSettled)
        assertEquals(DebtType.DEBT, initialSummary.type)

        // Ödeme eklendiğinde reaktif güncellenmeli
        val payment = DebtPayment(
            id = EntityId("p-1"),
            debtId = debtId,
            amount = Money(20_000_00L, Currency.TRY),
            paidOn = fixedToday,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(debtId to listOf(payment))
        advanceUntilIdle()

        val updatedSummary = viewModel.uiState.value.balanceSummary
        assertTrue(updatedSummary != null)
        assertEquals(Money(20_000_00L, Currency.TRY), updatedSummary?.totalPaid)
        assertEquals(Money(0L, Currency.TRY), updatedSummary?.remainingAmount)
        assertTrue(updatedSummary!!.isSettled)
    }

    @Test
    fun loadDebtForEdit_receivableBalanceSummary_usesReceivableType() = runTest {
        val debtId = EntityId("d-rec")
        val sampleReceivable = Debt(
            id = debtId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Ahmet Alacak",
            amount = Money(5_000_00L, Currency.TRY),
            type = DebtType.RECEIVABLE,
            dueDate = fixedToday,
            status = DebtStatus.OPEN,
            description = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.debtsFlow.value = mapOf(debtId to sampleReceivable)

        viewModel.loadDebtForEdit(debtId, decimalSeparator = ',')
        advanceUntilIdle()

        val summary = viewModel.uiState.value.balanceSummary
        assertTrue(summary != null)
        assertEquals(DebtType.RECEIVABLE, summary?.type)
        assertEquals(Money(5_000_00L, Currency.TRY), summary?.remainingAmount)
        assertFalse(summary!!.isSettled)

        // Tam tahsilat
        val payment = DebtPayment(
            id = EntityId("p-rec-1"),
            debtId = debtId,
            amount = Money(5_000_00L, Currency.TRY),
            paidOn = fixedToday,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )
        repository.paymentsFlow.value = mapOf(debtId to listOf(payment))
        advanceUntilIdle()

        val settledSummary = viewModel.uiState.value.balanceSummary
        assertTrue(settledSummary != null)
        assertTrue(settledSummary!!.isSettled)
        assertEquals(Money(0L, Currency.TRY), settledSummary?.remainingAmount)
    }
}


