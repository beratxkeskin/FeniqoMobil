package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantCorrection
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionMerchantCorrectionRepository
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import com.feniqo.mobile.navigation.TransactionDetailRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class TransactionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = EntityId("user-1")

    private class FakeTransactionRepository : TransactionRepository {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())
        var observedId: EntityId? = null

        override fun observeTransaction(id: EntityId): Flow<Transaction?> {
            observedId = id
            return transactions.map { list -> list.find { it.id == id } }
        }

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactions
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactions.map { list -> list.filter { it.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction) = RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>) =
            RepositoryResult.Success(transactions.first().id)
        override suspend fun update(transaction: Transaction) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            transactions.value = transactions.value.filterNot { it.id == id }
            return RepositoryResult.Success(Unit)
        }
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
            transactions.value = transactions.value.filterNot { it.id in ids }
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = MutableStateFlow<List<Category>>(emptyList())
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?) = categories
        override fun observeCategory(id: EntityId) = categories.map { list -> list.find { it.id == id } }
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?) = categories
        override suspend fun create(category: Category) = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
    }

    private inner class FakeAuthRepository : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(
            AuthSession(userId, "user@feniqo.com", Instant.parse("2026-09-01T00:00:00Z")),
        )
        override fun observeCurrentProfile() = MutableStateFlow<com.feniqo.mobile.domain.model.UserProfile?>(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(userId)
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeTransactionMerchantCorrectionRepository : TransactionMerchantCorrectionRepository {
        val calls = mutableListOf<TransactionMerchantCorrection>()
        var result: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var onCall: (suspend (TransactionMerchantCorrection) -> Unit)? = null

        override suspend fun correctMerchant(correction: TransactionMerchantCorrection): RepositoryResult<Unit> {
            calls.add(correction)
            onCall?.invoke(correction)
            return result
        }
    }

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var merchantCorrectionRepository: FakeTransactionMerchantCorrectionRepository

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository()
        merchantCorrectionRepository = FakeTransactionMerchantCorrectionRepository()
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(
        rawId: Any?,
        merchantCorrectionRepo: TransactionMerchantCorrectionRepository = merchantCorrectionRepository,
    ): TransactionDetailViewModel = TransactionDetailViewModel(
        observeTransactionUseCase = ObserveTransactionUseCase(transactionRepository),
        observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(categoryRepository),
        deleteTransactionUseCase = DeleteTransactionUseCase(FakeAuthRepository(), transactionRepository),
        currentDateProvider = CurrentDateProvider { LocalDate(2026, 9, 30) },
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to rawId)),
        merchantCorrectionRepository = merchantCorrectionRepo,
    )

    @Test
    fun validRoute_observesExactTransactionAndBuildsDetail() = runTest {
        val category = category()
        val transaction = transaction()
        categoryRepository.categories.value = listOf(category)
        transactionRepository.transactions.value = listOf(transaction)
        val viewModel = createViewModel(TransactionDetailRoute("transaction-1").transactionId)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(EntityId("transaction-1"), transactionRepository.observedId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("Market", viewModel.uiState.value.item?.categoryName)
        collectJob.cancel()
    }

    @Test
    fun invalidRoute_failsClosedWithoutObservingRepository() = runTest {
        val viewModel = createViewModel("   ")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.item)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(transactionRepository.observedId)
        collectJob.cancel()
    }

    @Test
    fun confirmedDelete_emitsDeletedEventAndRemovesTransaction() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestDelete()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.deleteDialog)
        viewModel.confirmSingleDelete()
        advanceUntilIdle()

        assertEquals(TransactionDetailEvent.Deleted, viewModel.events.first())
        assertEquals(emptyList<Transaction>(), transactionRepository.transactions.value)
        collectJob.cancel()
    }

    // --- ZORUNLU VIEWMODEL MERCHANT CORRECTION TESTLERİ ---

    @Test
    fun assign_calledWithExactTransactionIdAndMerchantId_exactlyOnce() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openMerchantSelector()
        viewModel.selectMerchant(EntityId("migros"))
        viewModel.confirmAssign()
        advanceUntilIdle()

        assertEquals(1, merchantCorrectionRepository.calls.size)
        val call = merchantCorrectionRepository.calls[0] as TransactionMerchantCorrection.Assign
        assertEquals(EntityId("transaction-1"), call.transactionId)
        assertEquals(EntityId("migros"), call.merchantId)
        collectJob.cancel()
    }

    @Test
    fun block_calledWithExactTransactionId_exactlyOnce() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openBlockConfirmDialog()
        viewModel.confirmBlock()
        advanceUntilIdle()

        assertEquals(1, merchantCorrectionRepository.calls.size)
        val call = merchantCorrectionRepository.calls[0] as TransactionMerchantCorrection.Block
        assertEquals(EntityId("transaction-1"), call.transactionId)
        collectJob.cancel()
    }

    @Test
    fun selectorOpeningSearchSelectionAndDismiss_producesNoRepositoryCalls() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // 1. Selector aç, ara, seç, iptal et
        viewModel.openMerchantSelector()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merchantCorrection.isSelectorOpen)
        viewModel.onSearchQueryChanged("mig")
        advanceUntilIdle()
        viewModel.selectMerchant(EntityId("migros"))
        advanceUntilIdle()
        viewModel.dismissMerchantSelector()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.merchantCorrection.isSelectorOpen)

        // 2. Block dialogu aç ve iptal et
        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        viewModel.dismissBlockConfirmDialog()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)

        advanceUntilIdle()
        // Hiçbir repository çağrısı yapılmamış olmalıdır!
        assertEquals(0, merchantCorrectionRepository.calls.size)
        collectJob.cancel()
    }

    @Test
    fun reentrantCalls_produceSingleMutation() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val deferred = CompletableDeferred<Unit>()
        merchantCorrectionRepository.onCall = {
            deferred.await()
        }
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openMerchantSelector()
        advanceUntilIdle()
        viewModel.selectMerchant(EntityId("migros"))
        advanceUntilIdle()

        // Peş peşe hızlı tıklama
        viewModel.confirmAssign()
        testScheduler.runCurrent()
        viewModel.confirmAssign()
        viewModel.confirmAssign()

        // İlk çağrı in progress iken tek çağrı gitmiş olmalıdır
        assertEquals(1, merchantCorrectionRepository.calls.size)
        assertTrue(viewModel.uiState.value.merchantCorrection.isInProgress)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, merchantCorrectionRepository.calls.size)
        assertFalse(viewModel.uiState.value.merchantCorrection.isInProgress)
        collectJob.cancel()
    }

    @Test
    fun cancellationException_doesNotMapToError_emitsNoEvent_andCleansUpProgress() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.onCall = {
            throw CancellationException("Operation cancelled by test")
        }
        val viewModel = createViewModel("transaction-1")
        val events = mutableListOf<TransactionDetailEvent>()
        val eventsJob = launch(UnconfinedTestDispatcher()) { viewModel.events.collect { events.add(it) } }
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openMerchantSelector()
        advanceUntilIdle()
        viewModel.selectMerchant(EntityId("migros"))
        advanceUntilIdle()
        viewModel.confirmAssign()
        advanceUntilIdle()

        // 1. Cancellation generic hataya çevrilmez (error null kalır)
        assertNull(viewModel.uiState.value.merchantCorrection.error)
        // 2. Başarı eventi üretilmez
        assertTrue(events.isEmpty())
        // 3. Finally bloğunda progress temizlenmiş olmalıdır
        assertFalse(viewModel.uiState.value.merchantCorrection.isInProgress)
        // 4. İkinci mutation yanlışlıkla tetiklenmez (tek çağrı)
        assertEquals(1, merchantCorrectionRepository.calls.size)

        eventsJob.cancel()
        collectJob.cancel()
    }

    @Test
    fun assignFailure_mapsToTypedUiError() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.result = RepositoryResult.Failure(
            AppError.Validation("workspace_restricted"),
        )
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openMerchantSelector()
        advanceUntilIdle()
        viewModel.selectMerchant(EntityId("migros"))
        advanceUntilIdle()
        viewModel.confirmAssign()
        advanceUntilIdle()

        assertEquals(MerchantCorrectionUiError.WORKSPACE_RESTRICTED, viewModel.uiState.value.merchantCorrection.error)
        assertFalse(viewModel.uiState.value.merchantCorrection.isInProgress)
        assertTrue(viewModel.uiState.value.merchantCorrection.isSelectorOpen)
        collectJob.cancel()
    }

    @Test
    fun blockFailure_mapsToTypedUiError_andDialogRemainsOpen() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.result = RepositoryResult.Failure(
            AppError.Validation("workspace_restricted"),
        )
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)

        viewModel.confirmBlock()
        advanceUntilIdle()

        // Hata doğru map edilmeli, progress bitmeli, diyalog açık kalmalı
        assertEquals(MerchantCorrectionUiError.WORKSPACE_RESTRICTED, viewModel.uiState.value.merchantCorrection.error)
        assertFalse(viewModel.uiState.value.merchantCorrection.isInProgress)
        assertTrue(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        collectJob.cancel()
    }

    @Test
    fun blockFailure_dismissClearsError_andReopeningHasNoError() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.result = RepositoryResult.Failure(
            AppError.Validation("workspace_restricted"),
        )
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        viewModel.confirmBlock()
        advanceUntilIdle()
        assertEquals(MerchantCorrectionUiError.WORKSPACE_RESTRICTED, viewModel.uiState.value.merchantCorrection.error)

        // Dialog kapatıldığında hata temizlenir
        viewModel.dismissBlockConfirmDialog()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        assertNull(viewModel.uiState.value.merchantCorrection.error)

        // Tekrar açıldığında eski hata görünmez
        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        assertNull(viewModel.uiState.value.merchantCorrection.error)
        collectJob.cancel()
    }

    @Test
    fun blockFailure_retryCanSucceedAndDismissDialog() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.result = RepositoryResult.Failure(
            AppError.Validation("workspace_restricted"),
        )
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        viewModel.confirmBlock()
        advanceUntilIdle()

        assertEquals(MerchantCorrectionUiError.WORKSPACE_RESTRICTED, viewModel.uiState.value.merchantCorrection.error)
        assertTrue(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)

        // İkinci deneme başarılı olsun
        merchantCorrectionRepository.result = RepositoryResult.Success(Unit)
        viewModel.confirmBlock()
        advanceUntilIdle()

        // Başarılı olduğunda diyalog kapanır ve hata temizlenir
        assertFalse(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        assertNull(viewModel.uiState.value.merchantCorrection.error)
        assertEquals(2, merchantCorrectionRepository.calls.size)
        collectJob.cancel()
    }

    @Test
    fun openMerchantSelector_clearsAnyStaleError() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        merchantCorrectionRepository.result = RepositoryResult.Failure(
            AppError.Validation("workspace_restricted"),
        )
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openBlockConfirmDialog()
        advanceUntilIdle()
        viewModel.confirmBlock()
        advanceUntilIdle()
        assertEquals(MerchantCorrectionUiError.WORKSPACE_RESTRICTED, viewModel.uiState.value.merchantCorrection.error)

        // Selector açıldığında block hatası sızmaz, temizlenir
        viewModel.openMerchantSelector()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merchantCorrection.isSelectorOpen)
        assertNull(viewModel.uiState.value.merchantCorrection.error)
        collectJob.cancel()
    }

    @Test
    fun successfulMutation_dismissesSelectorAndDialog() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.openMerchantSelector()
        viewModel.selectMerchant(EntityId("migros"))
        viewModel.confirmAssign()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.merchantCorrection.isSelectorOpen)
        assertFalse(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)
        assertNull(viewModel.uiState.value.merchantCorrection.selectedMerchantId)
        collectJob.cancel()
    }

    @Test
    fun successDoesNotManuallyPatchItem_reliesOnlyOnRoomObserveFlow() = runTest {
        categoryRepository.categories.value = listOf(category())
        val initialTransaction = transaction()
        transactionRepository.transactions.value = listOf(initialTransaction)
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.item?.merchant)

        viewModel.openMerchantSelector()
        viewModel.selectMerchant(EntityId("migros"))
        viewModel.confirmAssign()
        advanceUntilIdle()

        // Mutation başarılı olsa dahi Room SSOT flow güncellenmediği sürece item elle yamalanmaz!
        assertNull(viewModel.uiState.value.item?.merchant)

        // Şimdi Room'daki transaction Flow'unun güncellendiğini simüle edelim:
        val updatedTransaction = initialTransaction.copy(
            merchantInfo = TransactionMerchantInfo(
                merchantId = EntityId("migros"),
                matchSource = com.feniqo.mobile.domain.model.MerchantMatchSource.USER_VERIFIED,
                confidence = 100,
                catalogVersion = 1,
            ),
        )
        transactionRepository.transactions.value = listOf(updatedTransaction)
        advanceUntilIdle()

        // Ekran ancak Room SSOT Flow'u güncellendiğinde yeni merchant'ı gösterir
        assertEquals(EntityId("migros"), viewModel.uiState.value.item?.merchant?.merchantId)
        collectJob.cancel()
    }

    @Test
    fun staleTransactionOrMissingItem_handledSafelyWithoutMutation() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = emptyList() // transaction yok
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.confirmAssign()
        viewModel.confirmBlock()
        advanceUntilIdle()

        // Item olmadığı için hiçbir repo çağrısı yapılmamalıdır
        assertEquals(0, merchantCorrectionRepository.calls.size)
        collectJob.cancel()
    }

    @Test
    fun workspaceOrIneligibleTransaction_correctionActionsNotAvailable() = runTest {
        categoryRepository.categories.value = listOf(category())
        // Workspace transaction'ı
        val workspaceTrx = transaction().copy(workspaceId = EntityId("ws-1"))
        transactionRepository.transactions.value = listOf(workspaceTrx)
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.item?.canCorrectMerchant == true)

        viewModel.openMerchantSelector()
        assertFalse(viewModel.uiState.value.merchantCorrection.isSelectorOpen)

        viewModel.openBlockConfirmDialog()
        assertFalse(viewModel.uiState.value.merchantCorrection.isBlockConfirmDialogOpen)

        collectJob.cancel()
    }

    @Test
    fun twoMerchantsWithSameDisplayName_distinguishedByStableId() = runTest {
        categoryRepository.categories.value = listOf(category())
        transactionRepository.transactions.value = listOf(transaction())
        val viewModel = createViewModel("transaction-1")
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // StarterMerchantCatalog veya benzeri iki merchant seçimi test edilir
        val id1 = EntityId("merchant-a")
        val id2 = EntityId("merchant-b")

        viewModel.openMerchantSelector()
        advanceUntilIdle()
        viewModel.selectMerchant(id1)
        advanceUntilIdle()
        assertEquals(id1, viewModel.uiState.value.merchantCorrection.selectedMerchantId)

        viewModel.selectMerchant(id2)
        advanceUntilIdle()
        assertEquals(id2, viewModel.uiState.value.merchantCorrection.selectedMerchantId)

        viewModel.confirmAssign()
        advanceUntilIdle()

        assertEquals(1, merchantCorrectionRepository.calls.size)
        val call = merchantCorrectionRepository.calls[0] as TransactionMerchantCorrection.Assign
        assertEquals(id2, call.merchantId)
        collectJob.cancel()
    }

    private fun category() = Category(
        id = EntityId("category-1"),
        ownerId = null,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#10B981"),
        icon = CategoryIcon("shopping-bag"),
        isDefault = true,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun transaction() = Transaction(
        id = EntityId("transaction-1"),
        ownerId = userId,
        workspaceId = null,
        amount = Money(12_500L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("category-1"),
        description = "Market alışverişi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 29),
        receiptPath = null,
        installment = null,
        createdAt = Instant.parse("2026-09-29T12:00:00Z"),
    )
}
