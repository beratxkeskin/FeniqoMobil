package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
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
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.usecase.AddTransactionUseCase
import com.feniqo.mobile.domain.usecase.TransactionCommand
import com.feniqo.mobile.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionSplitValidationTest {

    private val ownerId = EntityId("user-owner")
    private val member1Id = EntityId("user-member-1")
    private val member2Id = EntityId("user-member-2")
    private val member3Id = EntityId("user-member-3")
    private val nonMemberId = EntityId("user-stranger")
    private val workspaceId = EntityId("workspace-1")
    private val categoryId = EntityId("category-1")
    private val now = Instant.parse("2026-09-08T00:00:00Z")
    private val sampleCategory = com.feniqo.mobile.domain.model.Category(
        id = categoryId,
        ownerId = null,
        workspaceId = null,
        name = "Test Category",
        type = TransactionType.EXPENSE,
        color = com.feniqo.mobile.domain.model.CategoryColor("#112233"),
        icon = com.feniqo.mobile.domain.model.CategoryIcon("tag"),
        isDefault = true,
        createdAt = now,
    )
    private val incomeCategoryId = EntityId("category-income")
    private val sampleIncomeCategory = com.feniqo.mobile.domain.model.Category(
        id = incomeCategoryId,
        ownerId = null,
        workspaceId = null,
        name = "Income Category",
        type = TransactionType.INCOME,
        color = com.feniqo.mobile.domain.model.CategoryColor("#112233"),
        icon = com.feniqo.mobile.domain.model.CategoryIcon("tag"),
        isDefault = true,
        createdAt = now,
    )

    private fun expenseTransaction(
        wsId: EntityId? = workspaceId,
        payer: EntityId = ownerId,
        participants: List<EntityId> = listOf(ownerId, member1Id),
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 10_000L,
        splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
        shares: List<TransactionParticipantShare> = emptyList(),
    ) = Transaction(
        id = EntityId("tx-1"),
        ownerId = ownerId,
        workspaceId = wsId,
        amount = Money(amountMinor, Currency.TRY),
        type = type,
        categoryId = categoryId,
        description = "Test expense",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 8),
        receiptPath = null,
        installment = null,
        createdAt = now,
        paidByUserId = payer,
        participantUserIds = participants,
        splitMode = splitMode,
        participantShares = shares,
    )

    // =========================================================================
    // 1. Tipli Doğrulama API Testleri (9 adet CUSTOM_SPLIT_* hata kodu)
    // =========================================================================

    @Test
    fun validateSplit_customSplitSharesRequired_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = emptyList(),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_SHARES_REQUIRED),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitDuplicateParticipant_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 5_000L),
                TransactionParticipantShare(ownerId, 5_000L),
            ),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_DUPLICATE_PARTICIPANT),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitParticipantSetMismatch_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 6_000L),
                TransactionParticipantShare(member2Id, 4_000L), // member2 is not in participantUserIds
            ),
            activeMemberUserIds = setOf(ownerId, member1Id, member2Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_PARTICIPANT_SET_MISMATCH),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitPayerNotParticipant_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(member1Id, member2Id), // ownerId not in participants
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(member1Id, 6_000L),
                TransactionParticipantShare(member2Id, 4_000L),
            ),
            activeMemberUserIds = setOf(ownerId, member1Id, member2Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_PAYER_NOT_PARTICIPANT),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitMemberNotActive_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, nonMemberId),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 6_000L),
                TransactionParticipantShare(nonMemberId, 4_000L),
            ),
            activeMemberUserIds = setOf(ownerId, member1Id), // nonMemberId is not active
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_MEMBER_NOT_ACTIVE),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitNegativeShare_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 12_000L),
                TransactionParticipantShare(member1Id, -2_000L),
            ),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_NEGATIVE_SHARE),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitZeroShareNotAllowed_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 10_000L),
                TransactionParticipantShare(member1Id, 0L), // non-payer zero share not allowed
            ),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_ZERO_SHARE_NOT_ALLOWED),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitTotalMismatch_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 5_000L),
                TransactionParticipantShare(member1Id, 4_000L), // total 9_000 != 10_000
            ),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_TOTAL_MISMATCH),
            result,
        )
    }

    @Test
    fun validateSplit_customSplitAmountOverflow_returnsTypedError() {
        val result = TransactionValidationRules.validateSplit(
            workspaceId = workspaceId,
            type = TransactionType.EXPENSE,
            amountMinor = 10_000L,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, Long.MAX_VALUE),
                TransactionParticipantShare(member1Id, 1L),
            ),
            activeMemberUserIds = setOf(ownerId, member1Id),
        )

        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.CUSTOM_SPLIT_AMOUNT_OVERFLOW),
            result,
        )
    }

    // =========================================================================
    // 2. Normalizasyon ve Geçerli İşlem Testleri
    // =========================================================================

    @Test
    fun personalExpense_normalizesSplitToOwnerOnly() {
        val tx = expenseTransaction(
            wsId = null,
            payer = member1Id,
            participants = listOf(member1Id, member2Id),
        )

        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, emptySet())
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(ownerId, result.value.paidByUserId)
        assertEquals(listOf(ownerId), result.value.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, result.value.splitMode)
        assertTrue(result.value.participantShares.isEmpty())
    }

    @Test
    fun personalCustom_normalizesToEqualOwnerOnly() {
        val tx = expenseTransaction(
            wsId = null,
            payer = ownerId,
            participants = listOf(ownerId),
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(TransactionParticipantShare(ownerId, 10_000L)),
        )

        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, emptySet())
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(ownerId, result.value.paidByUserId)
        assertEquals(listOf(ownerId), result.value.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, result.value.splitMode)
        assertTrue(result.value.participantShares.isEmpty())
    }

    @Test
    fun incomeCustom_normalizesToEqualOwnerOnly() {
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = ownerId,
            participants = listOf(ownerId),
            type = TransactionType.INCOME,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = listOf(TransactionParticipantShare(ownerId, 10_000L)),
        )

        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, emptySet())
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(ownerId, result.value.paidByUserId)
        assertEquals(listOf(ownerId), result.value.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, result.value.splitMode)
        assertTrue(result.value.participantShares.isEmpty())
    }

    @Test
    fun sharedExpenseEqual_preservesExistingBehavior() {
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = member1Id,
            participants = listOf(ownerId, member1Id, member2Id),
        )

        val activeMembers = setOf(ownerId, member1Id, member2Id)
        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, activeMembers)
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(member1Id, result.value.paidByUserId)
        assertEquals(listOf(ownerId, member1Id, member2Id), result.value.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, result.value.splitMode)
        assertTrue(result.value.participantShares.isEmpty())
    }

    @Test
    fun validCustom50_30_20_isAccepted() {
        val participants = listOf(ownerId, member1Id, member2Id)
        val shares = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
            TransactionParticipantShare(member2Id, 2_000L),
        )
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = ownerId,
            participants = participants,
            amountMinor = 10_000L,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = shares,
        )

        val activeMembers = setOf(ownerId, member1Id, member2Id)
        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, activeMembers)
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(TransactionSplitMode.CUSTOM, result.value.splitMode)
        assertEquals(3, result.value.participantShares.size)
        assertTrue(result.value.hasEquivalentShares(shares))
    }

    @Test
    fun customSplit_payerZeroShare_isAccepted() {
        val participants = listOf(ownerId, member1Id)
        val shares = listOf(
            TransactionParticipantShare(ownerId, 0L),
            TransactionParticipantShare(member1Id, 10_000L),
        )
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = ownerId,
            participants = participants,
            amountMinor = 10_000L,
            splitMode = TransactionSplitMode.CUSTOM,
            shares = shares,
        )

        val activeMembers = setOf(ownerId, member1Id)
        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, activeMembers)
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(0L, result.value.participantShares.first { it.userId == ownerId }.amountMinor)
        assertEquals(10_000L, result.value.participantShares.first { it.userId == member1Id }.amountMinor)
    }

    // =========================================================================
    // 3. Sıralama Bağımsız Eşdeğerlik Testleri (haveEquivalentShares)
    // =========================================================================

    @Test
    fun sharesEquivalence_sameSharesDifferentOrder_returnsTrue() {
        val shares1 = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
            TransactionParticipantShare(member2Id, 2_000L),
        )
        val shares2 = listOf(
            TransactionParticipantShare(member2Id, 2_000L),
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
        )

        assertTrue(Transaction.haveEquivalentShares(shares1, shares2))
    }

    @Test
    fun sharesEquivalence_duplicateUserInEither_returnsFalse() {
        val sharesWithDuplicate = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(ownerId, 5_000L),
        )
        val validShares = listOf(
            TransactionParticipantShare(ownerId, 10_000L),
        )

        // Sol tarafta duplicate
        assertFalse(Transaction.haveEquivalentShares(sharesWithDuplicate, validShares))
        // Sağ tarafta duplicate
        assertFalse(Transaction.haveEquivalentShares(validShares, sharesWithDuplicate))
        // Her iki tarafta duplicate
        assertFalse(Transaction.haveEquivalentShares(sharesWithDuplicate, sharesWithDuplicate))
    }

    @Test
    fun sharesEquivalence_sameUsersDifferentAmounts_returnsFalse() {
        val shares1 = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 5_000L),
        )
        val shares2 = listOf(
            TransactionParticipantShare(ownerId, 6_000L),
            TransactionParticipantShare(member1Id, 4_000L),
        )

        assertFalse(Transaction.haveEquivalentShares(shares1, shares2))
    }

    @Test
    fun sharesEquivalence_differentSizes_returnsFalse() {
        val shares1 = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
            TransactionParticipantShare(member2Id, 2_000L),
        )
        val shares2 = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 5_000L),
        )

        assertFalse(Transaction.haveEquivalentShares(shares1, shares2))
    }

    // =========================================================================
    // 4. Constructor Invariant Testleri
    // =========================================================================

    @Test
    fun transactionConstructor_equalSplitWithNonEmptyShares_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            expenseTransaction(
                amountMinor = 10_000L,
                payer = ownerId,
                participants = listOf(ownerId, member1Id),
                splitMode = TransactionSplitMode.EQUAL,
                shares = listOf(
                    TransactionParticipantShare(ownerId, 5_000L),
                    TransactionParticipantShare(member1Id, 5_000L),
                ),
            )
        }
    }

    @Test
    fun transactionConstructor_nonPositiveAmount_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            expenseTransaction(amountMinor = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            expenseTransaction(amountMinor = -100L)
        }
    }

    @Test
    fun transactionConstructor_payerNotInParticipants_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            expenseTransaction(
                payer = ownerId,
                participants = listOf(member1Id, member2Id),
            )
        }
    }

    @Test
    fun transactionConstructor_duplicateParticipants_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            expenseTransaction(
                participants = listOf(ownerId, ownerId),
            )
        }
    }

    // =========================================================================
    // 5. UseCase Entegrasyon Testleri: Üyelik Sınırı ve Normalizasyon
    // =========================================================================

    @Test
    fun addTransactionUseCase_withInvalidCustomSplit_returnsTypedValidationFailure() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository(sampleCategory)
        val wsRepo = FakeWorkspaceRepository(
            members = listOf(
                WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, now),
                WorkspaceMember(workspaceId, member1Id, WorkspaceRole.EDITOR, now),
            ),
        )
        val useCase = AddTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        val command = TransactionCommand(
            id = EntityId("tx-fail"),
            workspaceId = workspaceId,
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Custom split fail test",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 5_000L),
                TransactionParticipantShare(member1Id, 4_000L), // 9_000 != 10_000
            ),
        )

        val result = useCase(command, LocalDate(2026, 9, 8), now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        val validationError = assertIs<AppError.Validation>(failure.error)
        assertEquals("custom_split_total_mismatch", validationError.code)
        assertEquals(0, txRepo.createCallCount)
    }

    @Test
    fun addTransactionUseCase_sharedCustomSplit_withInactiveParticipant_returnsTypedValidationFailure() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository(sampleCategory)
        val wsRepo = FakeWorkspaceRepository(
            members = listOf(
                WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, now),
                WorkspaceMember(workspaceId, member1Id, WorkspaceRole.EDITOR, now),
            ),
        )
        val useCase = AddTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        val command = TransactionCommand(
            id = EntityId("tx-inactive-custom"),
            workspaceId = workspaceId,
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Custom split inactive test",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, nonMemberId),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 6_000L),
                TransactionParticipantShare(nonMemberId, 4_000L),
            ),
        )

        val result = useCase(command, LocalDate(2026, 9, 8), now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        val validationError = assertIs<AppError.Validation>(failure.error)
        assertEquals("custom_split_member_not_active", validationError.code)
        assertEquals(0, txRepo.createCallCount)
    }

    @Test
    fun updateTransactionUseCase_sharedCustomSplit_withInactiveParticipant_returnsTypedValidationFailure() = runTest {
        val existingTx = expenseTransaction(
            wsId = workspaceId,
            payer = ownerId,
            participants = listOf(ownerId, member1Id),
            amountMinor = 10_000L,
            splitMode = TransactionSplitMode.EQUAL,
        )
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository(initial = listOf(existingTx))
        val catRepo = FakeCategoryRepository(sampleCategory)
        val wsRepo = FakeWorkspaceRepository(
            members = listOf(
                WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, now),
                WorkspaceMember(workspaceId, member1Id, WorkspaceRole.EDITOR, now),
            ),
        )
        val useCase = UpdateTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        val command = TransactionCommand(
            id = existingTx.id,
            workspaceId = workspaceId,
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Update custom split inactive test",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, nonMemberId),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(ownerId, 6_000L),
                TransactionParticipantShare(nonMemberId, 4_000L),
            ),
        )

        val result = useCase(command, LocalDate(2026, 9, 8))
        val failure = assertIs<RepositoryResult.Failure>(result)
        val validationError = assertIs<AppError.Validation>(failure.error)
        assertEquals("custom_split_member_not_active", validationError.code)
        assertEquals(0, txRepo.updateCallCount)
    }

    @Test
    fun addTransactionUseCase_sharedEqualSplit_withInactiveParticipant_returnsTypedValidationFailure() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository(sampleCategory)
        val wsRepo = FakeWorkspaceRepository(
            members = listOf(
                WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, now),
                WorkspaceMember(workspaceId, member1Id, WorkspaceRole.EDITOR, now),
            ),
        )
        val useCase = AddTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        val command = TransactionCommand(
            id = EntityId("tx-equal-inactive"),
            workspaceId = workspaceId,
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Equal split inactive test",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, nonMemberId),
            splitMode = TransactionSplitMode.EQUAL,
        )

        val result = useCase(command, LocalDate(2026, 9, 8), now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        val validationError = assertIs<AppError.Validation>(failure.error)
        assertEquals("split_member_not_in_workspace", validationError.code)
        assertEquals(0, txRepo.createCallCount)
    }

    @Test
    fun addTransactionUseCase_sharedCustomSplit_withActiveMembers_createsTransaction() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository(sampleCategory)
        val wsRepo = FakeWorkspaceRepository(
            members = listOf(
                WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, now),
                WorkspaceMember(workspaceId, member1Id, WorkspaceRole.EDITOR, now),
            ),
        )
        val useCase = AddTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        val shares = listOf(
            TransactionParticipantShare(ownerId, 7_000L),
            TransactionParticipantShare(member1Id, 3_000L),
        )
        val command = TransactionCommand(
            id = EntityId("tx-custom-success"),
            workspaceId = workspaceId,
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Custom split success",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = ownerId,
            participantUserIds = listOf(ownerId, member1Id),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = shares,
        )

        val result = useCase(command, LocalDate(2026, 9, 8), now)
        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(1, txRepo.createCallCount)
        val created = txRepo.lastCreatedTransaction
        kotlin.test.assertNotNull(created)
        assertEquals(ownerId, created.paidByUserId)
        assertEquals(listOf(ownerId, member1Id), created.participantUserIds)
        assertEquals(TransactionSplitMode.CUSTOM, created.splitMode)
        assertTrue(created.hasEquivalentShares(shares))
    }

    @Test
    fun addTransactionUseCase_personalAndIncomeTransactions_normalizesWithoutQueryingWorkspaceMembers() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(ownerId, "owner@example.com", now))
        val txRepo = FakeTransactionRepository()
        val catRepo = FakeCategoryRepository(listOf(sampleCategory, sampleIncomeCategory))
        val wsRepo = FakeWorkspaceRepository(members = emptyList())
        val useCase = AddTransactionUseCase(authRepo, catRepo, txRepo, wsRepo)

        // Case A: Kişisel Gider (workspaceId == null)
        val personalCommand = TransactionCommand(
            id = EntityId("tx-personal"),
            workspaceId = null,
            amount = Money(5_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            description = "Personal expense",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = member1Id,
            participantUserIds = listOf(member1Id, nonMemberId),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(member1Id, 3_000L),
                TransactionParticipantShare(nonMemberId, 2_000L),
            ),
        )

        val personalResult = useCase(personalCommand, LocalDate(2026, 9, 8), now)
        assertIs<RepositoryResult.Success<EntityId>>(personalResult)
        assertEquals(0, wsRepo.observeMembersCallCount, "Personal transaction must not query workspace members")
        val createdPersonal = txRepo.lastCreatedTransaction
        kotlin.test.assertNotNull(createdPersonal)
        assertEquals(ownerId, createdPersonal.paidByUserId)
        assertEquals(listOf(ownerId), createdPersonal.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, createdPersonal.splitMode)
        assertTrue(createdPersonal.participantShares.isEmpty())

        // Case B: Workspace GELİR (workspaceId != null, type == INCOME)
        val incomeCommand = TransactionCommand(
            id = EntityId("tx-income"),
            workspaceId = workspaceId,
            amount = Money(8_000L, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = incomeCategoryId,
            description = "Workspace income",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate(2026, 9, 8),
            receiptPath = null,
            paidByUserId = member1Id,
            participantUserIds = listOf(member1Id, nonMemberId),
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(member1Id, 4_000L),
                TransactionParticipantShare(nonMemberId, 4_000L),
            ),
        )

        val incomeResult = useCase(incomeCommand, LocalDate(2026, 9, 8), now)
        assertIs<RepositoryResult.Success<EntityId>>(incomeResult)
        assertEquals(0, wsRepo.observeMembersCallCount, "INCOME transaction must not query workspace members")
        val createdIncome = txRepo.lastCreatedTransaction
        kotlin.test.assertNotNull(createdIncome)
        assertEquals(ownerId, createdIncome.paidByUserId)
        assertEquals(listOf(ownerId), createdIncome.participantUserIds)
        assertEquals(TransactionSplitMode.EQUAL, createdIncome.splitMode)
        assertTrue(createdIncome.participantShares.isEmpty())
    }

    // =========================================================================
    // Fakes
    // =========================================================================

    private class FakeAuthRepository(var session: AuthSession? = null) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) =
            RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
        private val transactions = MutableStateFlow(initial)
        var createCallCount = 0
        var updateCallCount = 0
        var lastCreatedTransaction: Transaction? = null
        var lastUpdatedTransaction: Transaction? = null

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactions
        override fun observeTransaction(id: EntityId): Flow<Transaction?> =
            transactions.map { list -> list.firstOrNull { it.id == id } }
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            transactions.map { list -> list.filter { it.installment?.groupId == groupId } }
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            createCallCount++
            lastCreatedTransaction = transaction
            transactions.value = transactions.value + transaction
            return RepositoryResult.Success(transaction.id)
        }
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("group-1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
            updateCallCount++
            lastUpdatedTransaction = transaction
            transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
            return RepositoryResult.Success(Unit)
        }
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeWorkspaceRepository(
        var members: List<WorkspaceMember> = emptyList(),
    ) : WorkspaceRepository {
        var observeMembersCallCount = 0

        override fun observeWorkspaces(): Flow<List<Workspace>> = flowOf(emptyList())
        override fun observeActiveWorkspace(): Flow<Workspace?> = flowOf(null)
        override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> {
            observeMembersCallCount++
            return flowOf(
                if (members.any { it.workspaceId == workspaceId }) {
                    members.filter { it.workspaceId == workspaceId }
                } else {
                    members
                },
            )
        }
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
            role: WorkspaceRole,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun transferOwnership(workspaceId: EntityId, targetUserId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun removeMember(workspaceId: EntityId, userId: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeCategoryRepository(private val categories: List<com.feniqo.mobile.domain.model.Category>) : CategoryRepository {
        constructor(single: com.feniqo.mobile.domain.model.Category? = null) : this(listOfNotNull(single))

        override fun observeCategories(
            type: TransactionType?,
            workspaceId: EntityId?,
        ): Flow<List<com.feniqo.mobile.domain.model.Category>> = flowOf(categories)

        override fun observeCategory(id: EntityId): Flow<com.feniqo.mobile.domain.model.Category?> =
            flowOf(categories.firstOrNull { it.id == id })

        override fun observeCategoriesForHistoryLookup(
            workspaceId: EntityId?,
        ): Flow<List<com.feniqo.mobile.domain.model.Category>> = flowOf(categories)

        override suspend fun create(category: com.feniqo.mobile.domain.model.Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)

        override suspend fun update(category: com.feniqo.mobile.domain.model.Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
