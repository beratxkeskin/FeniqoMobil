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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CustomSplitMembershipDepartureAndroidTest {

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
    private val user2 = "user-2"
    private val wsId = "ws-1"

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

        // Seed Profile, Workspace, and Members into Room
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
                name = "Ev Arkadaşları",
                normalizedName = "ev arkadaslari",
                ownerId = user1,
                typeCode = "shared",
                currencyCode = "TRY",
                description = "Ortak ev giderleri",
                createdAtEpochMillis = 1000L,
                sync = testSync,
            ),
        )

        database.workspaceDao().upsertMember(
            WorkspaceMemberEntity(
                workspaceId = wsId,
                userId = user1,
                roleCode = "OWNER",
                joinedAtEpochMillis = 1000L,
                sync = testSync,
            ),
        )

        database.workspaceDao().upsertMember(
            WorkspaceMemberEntity(
                workspaceId = wsId,
                userId = user2,
                roleCode = "EDITOR",
                joinedAtEpochMillis = 1000L,
                sync = testSync,
            ),
        )
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
    fun participantDeparture_preservesShares_removesBalancedBadge_andBlocksSubmit() {
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

        // Setup 100 TL, Title, Category
        viewModel.onAmountChanged("100")
        viewModel.onTitleChanged("Ortak Alışveriş")
        viewModel.onCategoryChanged(EntityId("cat-1"))

        // Set Custom Split: user-1 (payer) = 60, user-2 = 40
        viewModel.onSplitDetailsApplied(
            payer = EntityId(user1),
            participants = setOf(EntityId(user1), EntityId(user2)),
            splitMode = TransactionSplitMode.CUSTOM,
            customSharesText = mapOf(
                EntityId(user1) to "60",
                EntityId(user2) to "40",
            ),
        )

        compose.waitForIdle()

        // 1. Expand Ayrıntılar
        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.waitForIdle()

        // 2. Verify both shares are displayed, and Tam Eşleşti badge is shown
        compose.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("✓ Tam Eşleşti").performScrollTo().assertIsDisplayed()

        // Capture initial balanced state
        saveScreenshot("scenario_6_before_departure_balanced")

        // 3. Trigger local membership departure: soft-delete user-2 in isolated Room database
        runBlocking {
            database.workspaceDao().upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = wsId,
                    userId = user2,
                    roleCode = "EDITOR",
                    joinedAtEpochMillis = 1000L,
                    sync = testSync.copy(deletedAtEpochMillis = 2000L),
                ),
            )
        }

        // Wait for Room Flow -> ViewModel -> Compose to recompute
        compose.waitForIdle()

        // 4. VERIFY: Shares text is PRESERVED
        assertEquals("60", viewModel.uiState.value.customSharesText[EntityId(user1)])
        assertEquals("40", viewModel.uiState.value.customSharesText[EntityId(user2)])
        compose.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40").performScrollTo().assertIsDisplayed()

        // 5. VERIFY: Balanced badge DISAPPEARS and inactive warning is shown
        compose.onNodeWithText("✓ Tam Eşleşti").assertDoesNotExist()
        compose.onNodeWithText("Aktif olmayan üye var").performScrollTo().assertIsDisplayed()

        // 6. VERIFY: Inactive badge 'Ayrıldı' appears for user-2
        compose.onNodeWithText("Ayrıldı").performScrollTo().assertIsDisplayed()

        // 7. VERIFY: Remainder action cannot be applied (button disabled)
        compose.onNodeWithText("Kalan tutarı ödeyene aktar").performScrollTo().assertIsNotEnabled()

        // Capture departed state
        saveScreenshot("scenario_6_participant_departed_evidence")

        // 8. VERIFY: Submit is BLOCKED
        // Close details to return to main form
        compose.onNodeWithText("Ayrıntıları uygula").performScrollTo().performClick()
        compose.waitForIdle()

        viewModel.submit()
        compose.waitForIdle()

        // Transaction must NOT be created
        assertNull(transactionRepository.lastCreatedTransaction)
        // Error for inactive member must be present
        assertEquals(
            TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE,
            viewModel.uiState.value.customShareErrors[EntityId(user2)],
        )
    }

    @Test
    fun payerDeparture_preservesShares_removesBalancedBadge_andBlocksSubmit() {
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

        viewModel.onAmountChanged("100")
        viewModel.onTitleChanged("Payer Testi")
        viewModel.onCategoryChanged(EntityId("cat-1"))

        // Set Custom Split where user-2 is PAYER: user-2 = 60, user-1 = 40
        viewModel.onSplitDetailsApplied(
            payer = EntityId(user2),
            participants = setOf(EntityId(user1), EntityId(user2)),
            splitMode = TransactionSplitMode.CUSTOM,
            customSharesText = mapOf(
                EntityId(user2) to "60",
                EntityId(user1) to "40",
            ),
        )

        compose.waitForIdle()

        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("✓ Tam Eşleşti").performScrollTo().assertIsDisplayed()

        // Trigger local membership departure for PAYER (user-2)
        runBlocking {
            database.workspaceDao().upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = wsId,
                    userId = user2,
                    roleCode = "EDITOR",
                    joinedAtEpochMillis = 1000L,
                    sync = testSync.copy(deletedAtEpochMillis = 3000L),
                ),
            )
        }

        compose.waitForIdle()

        // 1. VERIFY: Balanced badge DISAPPEARS and inactive warning is shown
        compose.onNodeWithText("✓ Tam Eşleşti").assertDoesNotExist()
        compose.onNodeWithText("Aktif olmayan üye var").performScrollTo().assertIsDisplayed()

        // 2. VERIFY: Custom shares preserved
        assertEquals("60", viewModel.uiState.value.customSharesText[EntityId(user2)])
        assertEquals("40", viewModel.uiState.value.customSharesText[EntityId(user1)])
        compose.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40").performScrollTo().assertIsDisplayed()

        // 3. VERIFY: Payer shows Ayrıldı badge
        compose.onNodeWithText("Ayrıldı").performScrollTo().assertIsDisplayed()

        // Remainder action disabled
        compose.onNodeWithText("Kalan tutarı ödeyene aktar").performScrollTo().assertIsNotEnabled()

        // Capture payer departure state
        saveScreenshot("scenario_6_payer_departed_evidence")

        // 4. VERIFY: Submit is BLOCKED
        compose.onNodeWithText("Ayrıntıları uygula").performScrollTo().performClick()
        compose.waitForIdle()

        viewModel.submit()
        compose.waitForIdle()

        assertNull(transactionRepository.lastCreatedTransaction)
        assertEquals(
            TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE,
            viewModel.uiState.value.splitError,
        )
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
