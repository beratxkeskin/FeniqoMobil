package com.feniqo.mobile.presentation.transaction

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.remote.core.*
import com.feniqo.mobile.data.remote.dto.*
import com.feniqo.mobile.data.repository.OfflineFirstWorkspaceRepository
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.*
import com.feniqo.mobile.domain.usecase.*
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.CurrentInstantProvider
import com.feniqo.mobile.presentation.screen.TransactionFormScreen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CustomSplitBoundaryAndLayoutAndroidTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: FeniqoDatabase
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var workspaceRepository: OfflineFirstWorkspaceRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository

    private val testSync = SyncMetadata(
        syncStatus = "SYNCED",
        updatedAtEpochMillis = 1000L,
        localUpdatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private val user1 = "user-1"
    private val wsId = "ws-boundary"

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()

        authRepository = FakeAuthRepository(EntityId(user1))

        workspaceRepository = OfflineFirstWorkspaceRepository(
            authRepository = authRepository,
            workspaceDao = database.workspaceDao(),
            localMutationDao = database.localMutationDao(),
            remoteDataSource = DummyCoreRemoteDataSource,
            remoteSyncDao = database.remoteSyncDao(),
            profileDao = database.profileDao(),
        )

        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(EntityId(wsId))

        database.profileDao().upsert(
            UserProfileEntity(
                id = user1,
                email = "user1@feniqo.com",
                fullName = "Deniz",
                currencyCode = "TRY",
                themeCode = "SYSTEM",
                languageCode = "tr",
                activeWorkspaceId = wsId,
                createdAtEpochMillis = 1000L,
                sync = testSync,
            ),
        )

        database.workspaceDao().upsertWorkspace(
            WorkspaceEntity(
                id = wsId,
                name = "Geniş Ekip",
                normalizedName = "genis ekip",
                ownerId = user1,
                typeCode = "shared",
                currencyCode = "TRY",
                description = "12 kişilik geniş grup",
                createdAtEpochMillis = 1000L,
                sync = testSync,
            ),
        )

        // Seed 12 members
        for (i in 1..12) {
            database.workspaceDao().upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = wsId,
                    userId = "user-$i",
                    roleCode = if (i == 1) "OWNER" else "EDITOR",
                    joinedAtEpochMillis = 1000L + i,
                    sync = testSync,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createViewModel(): TransactionFormViewModel {
        val idGenerator = EntityIdGenerator { EntityId("gen-1") }
        return TransactionFormViewModel(
            addTransactionUseCase = AddTransactionUseCase(authRepository, categoryRepository, transactionRepository, workspaceRepository),
            addInstallmentGroupUseCase = AddInstallmentGroupUseCase(authRepository, categoryRepository, transactionRepository, idGenerator),
            updateTransactionUseCase = UpdateTransactionUseCase(authRepository, categoryRepository, transactionRepository, workspaceRepository),
            observeTransactionUseCase = ObserveTransactionUseCase(transactionRepository),
            observeCategoriesUseCase = ObserveCategoriesUseCase(categoryRepository),
            observeCategoriesForHistoryLookupUseCase = ObserveCategoriesForHistoryLookupUseCase(categoryRepository),
            observeActiveWorkspaceUseCase = ObserveActiveWorkspaceUseCase(workspaceRepository),
            observeWorkspaceMembersUseCase = ObserveWorkspaceMembersUseCase(workspaceRepository),
            observeAuthSessionUseCase = ObserveAuthSessionUseCase(authRepository),
            currentDateProvider = CurrentDateProvider { LocalDate(2026, 9, 19) },
            currentInstantProvider = CurrentInstantProvider { Instant.parse("2026-09-19T10:00:00Z") },
            entityIdGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(mapOf("initialTypeCode" to "EXPENSE")),
        )
    }

    @Test
    fun scenario7b_twoIndividuallyValidShares_exceedingCombinedLimit_showsWarningWithoutCrash() {
        val viewModel = createViewModel()

        compose.setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                TransactionFormScreen(
                    uiState = state,
                    onAmountChange = viewModel::onAmountChanged,
                    onCurrencyChange = viewModel::onCurrencyChanged,
                    onTypeChange = viewModel::onTypeChanged,
                    onCategoryChange = viewModel::onCategoryChanged,
                    onDateClick = {},
                    onTitleChange = viewModel::onTitleChanged,
                    onNoteChange = viewModel::onNoteChanged,
                    onDescriptionChange = viewModel::onDescriptionChanged,
                    onPaymentMethodChange = viewModel::onPaymentMethodChanged,
                    onInstallmentToggle = viewModel::onInstallmentToggle,
                    onInstallmentCountChange = viewModel::onInstallmentCountChanged,
                    onRetryCategories = viewModel::retryCategories,
                    onSubmit = viewModel::submit,
                    onBack = {},
                    onDismissMessage = viewModel::consumeMessage,
                    onAddCategory = {},
                    onAttachReceipt = {},
                    onRemoveReceipt = viewModel::onReceiptRemoved,
                    onPaidByUserSelected = viewModel::onPaidByUserSelected,
                    onParticipantToggled = viewModel::onParticipantToggled,
                    onSplitDetailsApplied = viewModel::onSplitDetailsApplied,
                )
            }
        }

        compose.waitForIdle()

        viewModel.onAmountChanged("1000")
        viewModel.onTitleChanged("Büyük Tutar Testi")
        viewModel.onCategoryChanged(EntityId("cat-1"))

        // Set Custom Split with 2 participants, each having 5,000,000,000,000 TL
        viewModel.onSplitDetailsApplied(
            payer = EntityId("user-1"),
            participants = setOf(EntityId("user-1"), EntityId("user-2")),
            splitMode = TransactionSplitMode.CUSTOM,
            customSharesText = mapOf(
                EntityId("user-1") to "5000000000000",
                EntityId("user-2") to "5000000000000",
            ),
        )

        compose.waitForIdle()

        // Open Ayrıntılar
        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.waitForIdle()

        // Verify "Desteklenen para sınırı aşıldı" is visible and no crash occurred
        compose.onAllNodesWithText("Desteklenen para sınırı aşıldı", substring = true)[0].performScrollTo().assertIsDisplayed()

        // Capture evidence screenshot
        saveScreenshot("scenario_7_two_shares_sum_overflow_instrumented")
    }

    @Test
    fun scenario7c_twelveMembersList_accessibleWithKeyboardAndActions() {
        val viewModel = createViewModel()

        compose.setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                TransactionFormScreen(
                    uiState = state,
                    onAmountChange = viewModel::onAmountChanged,
                    onCurrencyChange = viewModel::onCurrencyChanged,
                    onTypeChange = viewModel::onTypeChanged,
                    onCategoryChange = viewModel::onCategoryChanged,
                    onDateClick = {},
                    onTitleChange = viewModel::onTitleChanged,
                    onNoteChange = viewModel::onNoteChanged,
                    onDescriptionChange = viewModel::onDescriptionChanged,
                    onPaymentMethodChange = viewModel::onPaymentMethodChanged,
                    onInstallmentToggle = viewModel::onInstallmentToggle,
                    onInstallmentCountChange = viewModel::onInstallmentCountChanged,
                    onRetryCategories = viewModel::retryCategories,
                    onSubmit = viewModel::submit,
                    onBack = {},
                    onDismissMessage = viewModel::consumeMessage,
                    onAddCategory = {},
                    onAttachReceipt = {},
                    onRemoveReceipt = viewModel::onReceiptRemoved,
                    onPaidByUserSelected = viewModel::onPaidByUserSelected,
                    onParticipantToggled = viewModel::onParticipantToggled,
                    onSplitDetailsApplied = viewModel::onSplitDetailsApplied,
                )
            }
        }

        compose.waitForIdle()

        viewModel.onAmountChanged("1200")
        viewModel.onTitleChanged("12 Kişilik Yemek")
        viewModel.onCategoryChanged(EntityId("cat-1"))

        // All 12 members are participants
        val all12 = (1..12).map { EntityId("user-$it") }.toSet()
        val shares = (1..12).associate { EntityId("user-$it") to "100" }

        viewModel.onSplitDetailsApplied(
            payer = EntityId("user-1"),
            participants = all12,
            splitMode = TransactionSplitMode.CUSTOM,
            customSharesText = shares,
        )

        compose.waitForIdle()

        // Open Ayrıntılar
        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.waitForIdle()

        // 1. Verify 12th member's share field is reached via scroll
        compose.onNodeWithContentDescription("Kullanıcı user-12 pay tutarı")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        // 2. Verify Remainder action is accessible
        compose.onNodeWithText("Kalan tutarı ödeyene aktar").performScrollTo().assertIsDisplayed()

        // 3. Verify 'Vazgeç' is accessible
        compose.onNodeWithText("Vazgeç").performScrollTo().assertIsDisplayed()

        // Capture evidence screenshot with 12th member, remainder action and dialog buttons
        saveScreenshot("scenario_7_twelve_members_keyboard_accessible_instrumented")

        // 4. Verify 'Ayrıntıları uygula' is accessible and clickable
        compose.onNodeWithText("Ayrıntıları uygula").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
    }

    private fun saveScreenshot(name: String) {
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap != null) {
                val dir = File("/sdcard/Download")
                dir.mkdirs()
                val file = File(dir, "$name.png")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } catch (_: Exception) {}
    }

    private class FakeAuthRepository(val userId: EntityId) : AuthRepository {
        val sessionFlow = MutableStateFlow<AuthSession?>(
            AuthSession(
                userId = userId,
                email = "user1@feniqo.com",
                expiresAt = Instant.parse("2026-12-31T00:00:00Z"),
            ),
        )
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(userId)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
        var lastCreatedTransaction: Transaction? = null
        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactionsFlow
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = transactionsFlow.map { it.find { t -> t.id == id } }
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = transactionsFlow.map { it.filter { t -> t.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            lastCreatedTransaction = transaction
            return RepositoryResult.Success(transaction.id)
        }
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("grp-1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository(private val workspaceId: EntityId) : CategoryRepository {
        val category = Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = workspaceId,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#FF0000"),
            icon = CategoryIcon("cart"),
            isDefault = false,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = flowOf(listOf(category))
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(category)
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flowOf(listOf(category))
        override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private object DummyCoreRemoteDataSource : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? = null
        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> = RemotePage(emptyList(), query.page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = RemotePage(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }
}
