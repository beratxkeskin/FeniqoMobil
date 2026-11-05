package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AddTransactionUseCaseMerchantDecisionTest {
    private val userId = EntityId("user-1")
    private val categoryId = EntityId("cat-groceries")
    private val today = LocalDate(2026, 10, 21)
    private val now = Instant.parse("2026-10-21T10:00:00Z")

    private val authRepo =
        object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flowOf(AuthSession(userId, "test@feniqo.com", now))

            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)

            override suspend fun signIn(
                email: String,
                password: String,
            ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun signUp(
                email: String,
                password: String,
                fullName: String?,
            ): RepositoryResult<EntityId> = RepositoryResult.Success(userId)

            override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

    private val categoryRepo =
        object : CategoryRepository {
            private val list =
                listOf(
                    Category(
                        id = categoryId,
                        ownerId = userId,
                        workspaceId = null,
                        name = "Market",
                        type = TransactionType.EXPENSE,
                        color = CategoryColor("#4CAF50"),
                        icon = null,
                        isDefault = false,
                        createdAt = now,
                    ),
                )

            override fun observeCategories(
                type: TransactionType?,
                workspaceId: EntityId?,
            ): Flow<List<Category>> = flowOf(list)

            override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(list.firstOrNull { it.id == id })

            override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flowOf(list)

            override suspend fun create(category: Category): RepositoryResult<EntityId> = RepositoryResult.Success(category.id)

            override suspend fun update(category: Category): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

    private val workspaceRepo =
        object : WorkspaceRepository {
            override fun observeWorkspaces(): Flow<List<Workspace>> = flowOf(emptyList())

            override fun observeActiveWorkspace(): Flow<Workspace?> = flowOf(null)

            override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = flowOf(emptyList())

            override suspend fun create(name: String): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-new"))

            override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
                RepositoryResult.Success(EntityId("ws-new"))

            override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
                RepositoryResult.Success(EntityId("ws-new"))

            override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> =
                RepositoryResult.Success(WorkspaceInviteCode("INVITE"))

            override suspend fun changeMemberRole(
                workspaceId: EntityId,
                userId: EntityId,
                role: WorkspaceRole,
            ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun transferOwnership(
                workspaceId: EntityId,
                targetUserId: EntityId,
            ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

            override suspend fun removeMember(
                workspaceId: EntityId,
                userId: EntityId,
            ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

    private open class DefaultTransactionRepositoryStub : TransactionRepository {
        var baseCreateCallCount = 0
            private set
        var lastCreatedTransaction: Transaction? = null
            private set

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(null)

        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())

        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            baseCreateCallCount++
            lastCreatedTransaction = transaction
            return RepositoryResult.Success(transaction.id)
        }

        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("group-1"))

        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class RecordingTransactionRepository : DefaultTransactionRepositoryStub() {
        var lastReceivedMerchantDecision: TransactionMerchantCreateDecision? = null
            private set

        override suspend fun create(
            transaction: Transaction,
            merchantDecision: TransactionMerchantCreateDecision,
        ): RepositoryResult<EntityId> {
            lastReceivedMerchantDecision = merchantDecision
            return super.create(transaction)
        }
    }

    @Test
    fun defaultTransactionRepository_createWithAuto_delegatesToBaseCreate() =
        runTest {
            val repo = DefaultTransactionRepositoryStub()
            val tx = sampleTransaction(EntityId("tx-auto"))

            val result = repo.create(tx, TransactionMerchantCreateDecision.Auto)

            assertIs<RepositoryResult.Success<EntityId>>(result)
            assertEquals(EntityId("tx-auto"), result.value)
            assertEquals(1, repo.baseCreateCallCount)
            assertEquals(tx, repo.lastCreatedTransaction)
        }

    @Test
    fun defaultTransactionRepository_createWithKnownMerchant_failsClosedWithoutCallingBaseCreate() =
        runTest {
            val repo = DefaultTransactionRepositoryStub()
            val tx = sampleTransaction(EntityId("tx-km"))

            val result = repo.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))

            assertIs<RepositoryResult.Failure>(result)
            val error = assertIs<AppError.Validation>(result.error)
            assertEquals(
                "explicit_merchant_decision_not_supported_by_default_repository",
                error.code,
            )
            assertEquals(0, repo.baseCreateCallCount)
            assertNull(repo.lastCreatedTransaction)
        }

    @Test
    fun defaultTransactionRepository_createWithUseTransactionName_failsClosedWithoutCallingBaseCreate() =
        runTest {
            val repo = DefaultTransactionRepositoryStub()
            val tx = sampleTransaction(EntityId("tx-utn"))

            val result = repo.create(tx, TransactionMerchantCreateDecision.UseTransactionName)

            assertIs<RepositoryResult.Failure>(result)
            val error = assertIs<AppError.Validation>(result.error)
            assertEquals(
                "explicit_merchant_decision_not_supported_by_default_repository",
                error.code,
            )
            assertEquals(0, repo.baseCreateCallCount)
            assertNull(repo.lastCreatedTransaction)
        }

    @Test
    fun addTransactionUseCase_forwardsMerchantDecisionToRepository() =
        runTest {
            val recordingRepo = RecordingTransactionRepository()
            val useCase = AddTransactionUseCase(authRepo, categoryRepo, recordingRepo, workspaceRepo)

            val command =
                sampleCommand(
                    id = EntityId("tx-recorded"),
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                )

            val result = useCase(command, today, now)

            assertIs<RepositoryResult.Success<EntityId>>(result)
            assertEquals(
                TransactionMerchantCreateDecision.KnownMerchant("migros"),
                recordingRepo.lastReceivedMerchantDecision,
            )
        }

    @Test
    fun addTransactionUseCase_whenRepositoryUsesDefaultImplementation_withExplicitDecision_returnsFailure() =
        runTest {
            val defaultRepo = DefaultTransactionRepositoryStub()
            val useCase = AddTransactionUseCase(authRepo, categoryRepo, defaultRepo, workspaceRepo)

            val command =
                sampleCommand(
                    id = EntityId("tx-default-repo"),
                    decision = TransactionMerchantCreateDecision.UseTransactionName,
                )

            val result = useCase(command, today, now)

            assertIs<RepositoryResult.Failure>(result)
            val error = assertIs<AppError.Validation>(result.error)
            assertEquals(
                "explicit_merchant_decision_not_supported_by_default_repository",
                error.code,
            )
            assertEquals(0, defaultRepo.baseCreateCallCount)
        }

    @Test
    fun addTransactionUseCase_whenRepositoryUsesDefaultImplementation_withAutoDecision_succeeds() =
        runTest {
            val defaultRepo = DefaultTransactionRepositoryStub()
            val useCase = AddTransactionUseCase(authRepo, categoryRepo, defaultRepo, workspaceRepo)

            val command =
                sampleCommand(
                    id = EntityId("tx-default-auto"),
                    decision = TransactionMerchantCreateDecision.Auto,
                )

            val result = useCase(command, today, now)

            assertIs<RepositoryResult.Success<EntityId>>(result)
            assertEquals(1, defaultRepo.baseCreateCallCount)
            assertEquals("Migros Market", defaultRepo.lastCreatedTransaction?.description)
        }

    private fun sampleCommand(
        id: EntityId,
        decision: TransactionMerchantCreateDecision,
    ) = TransactionCommand(
        id = id,
        workspaceId = null,
        amount = Money(15000, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        description = "Migros Market",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = today,
        receiptPath = null,
        merchantDecision = decision,
    )

    private fun sampleTransaction(id: EntityId) =
        Transaction(
            id = id,
            ownerId = userId,
            workspaceId = null,
            amount = Money(15000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Migros Market",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = today,
            receiptPath = null,
            installment = null,
            createdAt = now,
        )
}
