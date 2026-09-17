package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceSettlementUseCasesTest {

    private val workspaceId = EntityId("ws-1")
    private val userA = EntityId("user-a")
    private val userB = EntityId("user-b")
    private val userC = EntityId("user-c")

    private val sampleWorkspace = Workspace(
        id = workspaceId,
        name = "Ev Arkadaşları",
        type = WorkspaceType.SHARED,
        currency = Currency.TRY,
        ownerId = userA,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private val members = listOf(
        WorkspaceMember(workspaceId, userA, WorkspaceRole.OWNER, Instant.fromEpochMilliseconds(1000L)),
        WorkspaceMember(workspaceId, userB, WorkspaceRole.EDITOR, Instant.fromEpochMilliseconds(1000L)),
        WorkspaceMember(workspaceId, userC, WorkspaceRole.VIEWER, Instant.fromEpochMilliseconds(1000L)),
    )

    @Test
    fun observeWorkspaceSettlement_validExpenses_calculatesSuccessfullyWithoutExclusions() = runTest {
        val validExpense = createExpense(
            id = "tx-1",
            payer = userA,
            amountMinor = 10_000L,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(
                TransactionParticipantShare(userA, 5_000L),
                TransactionParticipantShare(userB, 3_000L),
                TransactionParticipantShare(userC, 2_000L),
            ),
        )

        val authRepo = FakeAuthRepository(AuthSession(userA, "a@example.com", Instant.fromEpochMilliseconds(9999999L)))
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository(listOf(validExpense))

        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)
        val result = useCase(workspaceId).first()

        val success = assertIs<WorkspaceSettlementResult.Success>(result)
        assertFalse(success.hasExcludedExpenses)
        assertEquals(10_000L, success.totalExpenseAmount.amountMinor)
        val settlement = success.settlement
        assertTrue(settlement != null)
        assertEquals(3, settlement.balances.size)
    }

    @Test
    fun observeWorkspaceSettlement_invalidSplitExpense_isExcludedAndDoesNotCrashCalculation() = runTest {
        val validExpense = createExpense(
            id = "tx-valid",
            payer = userA,
            amountMinor = 6_000L,
            splitMode = TransactionSplitMode.EQUAL,
            shares = emptyList(),
            participants = listOf(userA, userB),
        )

        // Workspace üyesi olmayan katılımcı içeren geçersiz split
        val invalidExpense = createExpense(
            id = "tx-corrupt",
            payer = userA,
            amountMinor = 5_000L,
            splitMode = TransactionSplitMode.EQUAL,
            participants = listOf(userA, EntityId("non-member-user")),
        )

        val authRepo = FakeAuthRepository(AuthSession(userA, "a@example.com", Instant.fromEpochMilliseconds(9999999L)))
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository(listOf(validExpense, invalidExpense))

        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)
        val result = useCase(workspaceId).first()

        val success = assertIs<WorkspaceSettlementResult.Success>(result)
        assertTrue(success.hasExcludedExpenses, "Bozuk kayıt settlement'tan dışlanmalı ve hasExcludedExpenses true olmalı")
        assertEquals(6_000L, success.totalExpenseAmount.amountMinor)
        val settlement = success.settlement
        assertTrue(settlement != null, "Geçerli işlem başarıyla hesaplanmalı")
        assertEquals(2, settlement.balances.size)
    }

    @Test
    fun observeWorkspaceSettlement_invalidCustomSplitExpense_excludedViaValidateSplit() = runTest {
        val validExpense = createExpense(
            id = "tx-valid-custom",
            payer = userA,
            amountMinor = 10_000L,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(
                TransactionParticipantShare(userA, 5_000L),
                TransactionParticipantShare(userB, 5_000L),
            ),
        )

        // Toplam uyuşmazlığı olan custom split kaydı (5_000 != 4_000 + 0)
        // Transaction constructor bypass / veri bozukluğu durumunda validateSplit fail-closed dışlar
        val corruptSplitExpense = createExpense(
            id = "tx-corrupt-shares",
            payer = userA,
            amountMinor = 5_000L,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(
                TransactionParticipantShare(userA, 2_000L),
                TransactionParticipantShare(userB, 2_000L), // 4_000 != 5_000
            ),
        )

        val authRepo = FakeAuthRepository(AuthSession(userA, "a@example.com", Instant.fromEpochMilliseconds(9999999L)))
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository(listOf(validExpense, corruptSplitExpense))

        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)
        val result = useCase(workspaceId).first()

        val success = assertIs<WorkspaceSettlementResult.Success>(result)
        assertTrue(success.hasExcludedExpenses)
        assertEquals(10_000L, success.totalExpenseAmount.amountMinor)
        assertEquals(2, success.settlement?.balances?.size)
    }

    @Test
    fun observeWorkspaceSettlement_allExpensesCorrupt_excludesAllAndReturnsNullSettlement() = runTest {
        val invalidExpense = createExpense(
            id = "tx-bad",
            payer = userA,
            amountMinor = 1_000L,
            participants = listOf(userA, EntityId("unknown-user")),
        )

        val authRepo = FakeAuthRepository(AuthSession(userA, "a@example.com", Instant.fromEpochMilliseconds(9999999L)))
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository(listOf(invalidExpense))

        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)
        val result = useCase(workspaceId).first()

        val success = assertIs<WorkspaceSettlementResult.Success>(result)
        assertTrue(success.hasExcludedExpenses)
        assertEquals(0L, success.totalExpenseAmount.amountMinor)
        assertNull(success.settlement)
    }

    @Test
    fun observeWorkspaceSettlement_coroutineCancellation_isNotSwallowed() = runTest {
        val authRepo = object : AuthRepository by FakeAuthRepository() {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("Coroutine cancelled")
            }
        }
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository()
        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)

        assertFailsWith<CancellationException> {
            useCase(workspaceId).first()
        }
    }

    @Test
    fun observeWorkspaceSettlement_fatalProgrammingError_isNotSwallowed() = runTest {
        val authRepo = object : AuthRepository by FakeAuthRepository() {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw IllegalStateException("Fatal unexpected exception")
            }
        }
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository()
        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)

        assertFailsWith<IllegalStateException> {
            useCase(workspaceId).first()
        }
    }

    @Test
    fun observeWorkspaceSettlement_totalExpenseAccumulationOverflow_excludesOverflowingExpense() = runTest {
        val tx1 = createExpense(
            id = "tx-1",
            payer = userA,
            amountMinor = Money.MAX_AMOUNT_MINOR,
            participants = listOf(userA, userB),
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(
                TransactionParticipantShare(userA, 0L),
                TransactionParticipantShare(userB, Money.MAX_AMOUNT_MINOR),
            ),
        )

        val tx2 = createExpense(
            id = "tx-2",
            payer = userA,
            amountMinor = 1_000L,
            participants = listOf(userA, userB),
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(
                TransactionParticipantShare(userA, 0L),
                TransactionParticipantShare(userB, 1_000L),
            ),
        )

        val authRepo = FakeAuthRepository(AuthSession(userA, "a@example.com", Instant.fromEpochMilliseconds(9999999L)))
        val workspaceRepo = FakeWorkspaceRepository(listOf(sampleWorkspace), members)
        val transactionRepo = FakeTransactionRepository(listOf(tx1, tx2))

        val useCase = ObserveWorkspaceSettlementUseCase(workspaceRepo, transactionRepo, authRepo)
        val result = useCase(workspaceId).first()

        val success = assertIs<WorkspaceSettlementResult.Success>(result)
        assertTrue(success.hasExcludedExpenses, "Taşmaya neden olan işlem dışlanmalıdır")
        assertEquals(Money.MAX_AMOUNT_MINOR, success.totalExpenseAmount.amountMinor)
    }

    private fun createExpense(
        id: String,
        payer: EntityId,
        amountMinor: Long,
        splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
        shares: List<TransactionParticipantShare> = emptyList(),
        participants: List<EntityId>? = null,
    ): Transaction {
        val actualParticipants = participants ?: if (shares.isNotEmpty()) shares.map { it.userId } else listOf(payer)
        return Transaction(
            id = EntityId(id),
            ownerId = payer,
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amount = Money(amountMinor, Currency.TRY),
            categoryId = EntityId("cat-1"),
            transactionDate = LocalDate(2026, 9, 16),
            description = "Expense $id",
            createdAt = Instant.fromEpochMilliseconds(1000L),
            paymentMethod = PaymentMethod.CASH,
            receiptPath = null,
            installment = null,
            paidByUserId = payer,
            participantUserIds = actualParticipants,
            splitMode = splitMode,
            participantShares = shares,
        )
    }

    private class FakeAuthRepository(var session: AuthSession? = null) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) =
            RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeWorkspaceRepository(
        var workspaces: List<Workspace> = emptyList(),
        var members: List<WorkspaceMember> = emptyList(),
    ) : WorkspaceRepository {
        override fun observeWorkspaces(): Flow<List<Workspace>> = flowOf(workspaces)
        override fun observeActiveWorkspace(): Flow<Workspace?> = flowOf(workspaces.firstOrNull())
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = flowOf(members)
        override suspend fun create(name: String): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("ws-new"))
        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("ws-new"))
        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
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
            role: com.feniqo.mobile.domain.model.WorkspaceRole,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
        private val transactions = MutableStateFlow(initial)

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactions
        override fun observeTransaction(id: EntityId): Flow<Transaction?> =
            transactions.map { list -> list.firstOrNull { it.id == id } }
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactions.map { list -> list.filter { it.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            transactions.value = transactions.value + transaction
            return RepositoryResult.Success(transaction.id)
        }
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("group-1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
