package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.SavedStateHandle
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
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
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceSettlementUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceSettlementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val workspacesFlow = MutableStateFlow<List<Workspace>>(emptyList())
        val membersFlow = MutableStateFlow<List<WorkspaceMember>>(emptyList())

        override fun observeWorkspaces(): Flow<List<Workspace>> = workspacesFlow
        override fun observeActiveWorkspace(): Flow<Workspace?> = MutableStateFlow(null)
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> = membersFlow

        override suspend fun create(name: String): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun changeMemberRole(workspaceId: EntityId, userId: EntityId, role: WorkspaceRole): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactionsFlow
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = MutableStateFlow(null)
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = MutableStateFlow(emptyList())

        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
    }

    private class FakeAuthRepository : AuthRepository {
        val sessionFlow = MutableStateFlow<AuthSession?>(null)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun refreshSession(): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
        override suspend fun signOut(): RepositoryResult<Unit> =
            RepositoryResult.Failure(AppError.Validation("stub"))
    }

    private val workspaceRepo = FakeWorkspaceRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val authRepo = FakeAuthRepository()

    private val observeSettlementUseCase = ObserveWorkspaceSettlementUseCase(
        workspaceRepository = workspaceRepo,
        transactionRepository = transactionRepo,
        authRepository = authRepo,
    )

    private val currentUserId = EntityId("user-1")
    private val otherUserId1 = EntityId("user-2")
    private val otherUserId2 = EntityId("user-3")
    private val workspaceId = EntityId("ws-1")

    private val testWorkspace = Workspace(
        id = workspaceId,
        ownerId = currentUserId,
        name = "Ev Harcamaları",
        type = WorkspaceType.SHARED,
        currency = Currency.TRY,
        description = "Ortak ev giderleri",
        createdAt = Instant.fromEpochMilliseconds(1000),
    )

    private val testMembers = listOf(
        WorkspaceMember(workspaceId = workspaceId, userId = currentUserId, role = WorkspaceRole.OWNER, joinedAt = Instant.fromEpochMilliseconds(1000)),
        WorkspaceMember(workspaceId = workspaceId, userId = otherUserId1, role = WorkspaceRole.EDITOR, joinedAt = Instant.fromEpochMilliseconds(2000)),
        WorkspaceMember(workspaceId = workspaceId, userId = otherUserId2, role = WorkspaceRole.VIEWER, joinedAt = Instant.fromEpochMilliseconds(3000)),
    )

    private fun createViewModel(rawWorkspaceId: String? = workspaceId.value): WorkspaceSettlementViewModel {
        val handle = SavedStateHandle(mapOf("workspaceId" to rawWorkspaceId))
        return WorkspaceSettlementViewModel(
            savedStateHandle = handle,
            observeWorkspaceSettlementUseCase = observeSettlementUseCase,
        )
    }

    private fun sampleExpense(
        id: String,
        payer: EntityId,
        amountMinor: Long,
        participants: List<EntityId>,
        wsId: EntityId? = workspaceId,
        type: TransactionType = TransactionType.EXPENSE,
    ): Transaction = Transaction(
        id = EntityId(id),
        ownerId = payer,
        workspaceId = wsId,
        amount = Money(amountMinor, Currency.TRY),
        type = type,
        categoryId = EntityId("cat-1"),
        description = "Gider $id",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 8),
        receiptPath = null,
        installment = null,
        createdAt = Instant.fromEpochMilliseconds(1000),
        paidByUserId = payer,
        participantUserIds = participants,
    )

    @Test
    fun validSharedExpenseSplits_derivesCorrectNetBalances() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        // user-1 paid 300 TRY for all 3 members (user-1, user-2, user-3) -> 100 each
        // user-1 net: +200, user-2 net: -100, user-3 net: -100
        val t1 = sampleExpense("t-1", currentUserId, 30000L, listOf(currentUserId, otherUserId1, otherUserId2))
        transactionRepo.transactionsFlow.value = listOf(t1)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isCurrentUserMember)
        assertEquals(3, state.memberBalances.size)

        val user1Balance = state.memberBalances.first { it.userId == currentUserId }
        val user2Balance = state.memberBalances.first { it.userId == otherUserId1 }
        val user3Balance = state.memberBalances.first { it.userId == otherUserId2 }

        assertEquals(20000L, user1Balance.netAmountMinor)
        assertEquals(MemberBalanceStatus.CREDITOR, user1Balance.status)
        assertEquals("Siz", user1Balance.displayName)

        assertEquals(-10000L, user2Balance.netAmountMinor)
        assertEquals(MemberBalanceStatus.DEBTOR, user2Balance.status)

        assertEquals(-10000L, user3Balance.netAmountMinor)
        assertEquals(MemberBalanceStatus.DEBTOR, user3Balance.status)

        assertEquals(30000L, state.totalExpenseAmount?.amountMinor)
        job.cancel()
    }

    @Test
    fun deterministicTransferSuggestions_reflectedInState() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        // user-1 paid 300 TRY for all 3 members
        val t1 = sampleExpense("t-1", currentUserId, 30000L, listOf(currentUserId, otherUserId1, otherUserId2))
        transactionRepo.transactionsFlow.value = listOf(t1)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.suggestedTransfers.size)

        val transfer1 = state.suggestedTransfers[0]
        val transfer2 = state.suggestedTransfers[1]

        assertEquals(otherUserId1, transfer1.fromUserId)
        assertEquals(currentUserId, transfer1.toUserId)
        assertEquals(10000L, transfer1.amount.amountMinor)

        assertEquals(otherUserId2, transfer2.fromUserId)
        assertEquals(currentUserId, transfer2.toUserId)
        assertEquals(10000L, transfer2.amount.amountMinor)

        job.cancel()
    }

    @Test
    fun balancedWorkspace_showsNoTransfersAndAllSettled() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = listOf(
            WorkspaceMember(workspaceId = workspaceId, userId = currentUserId, role = WorkspaceRole.OWNER, joinedAt = Instant.fromEpochMilliseconds(1000)),
            WorkspaceMember(workspaceId = workspaceId, userId = otherUserId1, role = WorkspaceRole.EDITOR, joinedAt = Instant.fromEpochMilliseconds(2000)),
        )

        // user-1 paid 100 TRY for (user-1, user-2)
        // user-2 paid 100 TRY for (user-1, user-2)
        // Net balances are 0
        val t1 = sampleExpense("t-1", currentUserId, 10000L, listOf(currentUserId, otherUserId1))
        val t2 = sampleExpense("t-2", otherUserId1, 10000L, listOf(currentUserId, otherUserId1))
        transactionRepo.transactionsFlow.value = listOf(t1, t2)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.suggestedTransfers.isEmpty())
        assertTrue(state.isAllSettled)
        assertEquals(MemberBalanceStatus.SETTLED, state.memberBalances[0].status)
        assertEquals(MemberBalanceStatus.SETTLED, state.memberBalances[1].status)

        job.cancel()
    }

    @Test
    fun incomeAndPersonalTransactions_excludedFromCalculation() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        // 1 shared expense of 150 TRY
        val sharedExpense = sampleExpense("t-1", currentUserId, 15000L, listOf(currentUserId, otherUserId1, otherUserId2))
        // 1 income in workspace of 1000 TRY
        val income = sampleExpense("t-2", currentUserId, 100000L, listOf(currentUserId), type = TransactionType.INCOME)
        // 1 personal expense with workspaceId = null
        val personal = sampleExpense("t-3", currentUserId, 50000L, listOf(currentUserId), wsId = null)

        transactionRepo.transactionsFlow.value = listOf(sharedExpense, income, personal)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(15000L, state.totalExpenseAmount?.amountMinor)
        assertEquals(10000L, state.memberBalances.first { it.userId == currentUserId }.netAmountMinor)

        job.cancel()
    }

    @Test
    fun invalidSplitTransactions_safelyExcludedFailClosed() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        val validExpense = sampleExpense("t-1", currentUserId, 15000L, listOf(currentUserId, otherUserId1, otherUserId2))

        // Transaction with a participant who is not an active member in workspace
        val nonMemberId = EntityId("stranger-99")
        val invalidExpense = sampleExpense("t-2", currentUserId, 20000L, listOf(currentUserId, nonMemberId))

        transactionRepo.transactionsFlow.value = listOf(validExpense, invalidExpense)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        // Invalid expense was excluded
        assertEquals(15000L, state.totalExpenseAmount?.amountMinor)
        assertTrue(state.hasExcludedExpenses)

        job.cancel()
    }

    @Test
    fun transactionFlowUpdate_automaticallyUpdatesSettlementState() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        val t1 = sampleExpense("t-1", currentUserId, 30000L, listOf(currentUserId, otherUserId1, otherUserId2))
        transactionRepo.transactionsFlow.value = listOf(t1)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(30000L, vm.uiState.value.totalExpenseAmount?.amountMinor)

        // New transaction added
        val t2 = sampleExpense("t-2", otherUserId1, 60000L, listOf(currentUserId, otherUserId1, otherUserId2))
        transactionRepo.transactionsFlow.value = listOf(t1, t2)
        advanceUntilIdle()

        assertEquals(90000L, vm.uiState.value.totalExpenseAmount?.amountMinor)

        job.cancel()
    }

    @Test
    fun memberFlowUpdate_automaticallyUpdatesSettlementState() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        val t1 = sampleExpense("t-1", currentUserId, 30000L, listOf(currentUserId, otherUserId1, otherUserId2))
        transactionRepo.transactionsFlow.value = listOf(t1)

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.memberCount)

        // A new member joins the workspace
        val newMember = WorkspaceMember(workspaceId = workspaceId, userId = EntityId("user-4"), role = WorkspaceRole.VIEWER, joinedAt = Instant.fromEpochMilliseconds(4000))
        workspaceRepo.membersFlow.value = testMembers + newMember
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.memberCount)

        job.cancel()
    }

    @Test
    fun invalidWorkspaceId_failsClosedWithWorkspaceNotFound() = runTest {
        authRepo.sessionFlow.value = AuthSession(currentUserId, "user1@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        val vm = createViewModel(rawWorkspaceId = "   ")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, state.errorMessage)
        assertFalse(state.isCurrentUserMember)

        job.cancel()
    }

    @Test
    fun userNotMember_failsClosedWithActorNotMember() = runTest {
        // Authenticated user is someone else who is not in testMembers
        val nonMemberUser = EntityId("outside-user")
        authRepo.sessionFlow.value = AuthSession(nonMemberUser, "outside@feniqo.com", Instant.fromEpochMilliseconds(5000))
        workspaceRepo.workspacesFlow.value = listOf(testWorkspace)
        workspaceRepo.membersFlow.value = testMembers

        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect()
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER, state.errorMessage)
        assertFalse(state.isCurrentUserMember)
        assertTrue(state.memberBalances.isEmpty())

        job.cancel()
    }
}
