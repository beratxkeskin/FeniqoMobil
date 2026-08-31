package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class InstallmentUseCasesTest {

    private val now = Instant.parse("2026-08-05T00:00:00Z")
    private val today = LocalDate(2026, 8, 5)
    private val userId = EntityId("u-1")
    private val workspaceId = EntityId("w-1")
    private val categoryId = EntityId("cat-1")

    private fun sampleCategory(
        id: EntityId = categoryId,
        ownerId: EntityId? = userId,
        wsId: EntityId? = workspaceId,
        type: TransactionType = TransactionType.EXPENSE,
        isDefault: Boolean = false,
    ) = Category(
        id = id,
        ownerId = ownerId,
        workspaceId = wsId,
        name = "Kategori",
        type = type,
        color = CategoryColor("#FF0000"),
        icon = null,
        isDefault = isDefault,
        createdAt = now,
    )

    private fun sampleCommand(
        totalAmount: Money = Money(1000L, Currency.TRY),
        type: TransactionType = TransactionType.EXPENSE,
        catId: EntityId = categoryId,
        wsId: EntityId? = workspaceId,
        description: String? = "Market Alışverişi",
        paymentMethod: PaymentMethod = PaymentMethod.CREDIT_CARD,
        anchorDate: LocalDate = today,
        receiptPath: ReceiptPath? = ReceiptPath("receipts/img.png"),
        installmentCount: Int = 3,
    ) = AddInstallmentGroupCommand(
        workspaceId = wsId,
        totalAmount = totalAmount,
        type = type,
        categoryId = catId,
        description = description,
        paymentMethod = paymentMethod,
        anchorDate = anchorDate,
        receiptPath = receiptPath,
        installmentCount = installmentCount,
    )

    // --- AddInstallmentGroupUseCase Tests ---

    @Test
    fun addInstallmentGroup_withoutSession_returnsAuthSessionRequiredError() = runTest {
        val authRepo = FakeAuthRepo(null)
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("auth_session_required", assertIs<AppError.Authentication>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withIncomeType_returnsMustBeExpenseError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory(type = TransactionType.INCOME)))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(type = TransactionType.INCOME), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_type_must_be_expense", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withNonCreditCardPaymentMethod_returnsMustBeCreditCardError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(paymentMethod = PaymentMethod.CASH), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_payment_must_be_credit_card", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withFutureAnchorDate_returnsFutureDateError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(anchorDate = LocalDate(2026, 8, 6)), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_date_cannot_be_future", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCountOutOfRange_returnsCountOutOfRangeError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val resultMin = useCase(sampleCommand(installmentCount = 1), today, now)
        assertEquals("installment_count_out_of_range", assertIs<AppError.Validation>(assertIs<RepositoryResult.Failure>(resultMin).error).code)

        val resultMax = useCase(sampleCommand(installmentCount = 61), today, now)
        assertEquals("installment_count_out_of_range", assertIs<AppError.Validation>(assertIs<RepositoryResult.Failure>(resultMax).error).code)

        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCountGreaterThanTotalMinor_returnsAmountTooSmallError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(totalAmount = Money(2L, Currency.TRY), installmentCount = 3), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_amount_too_small", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCategoryNotFound_returnsCategoryNotFoundError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(emptyList())
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_category_not_found", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCategoryTypeMismatch_returnsCategoryTypeMismatchError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory(type = TransactionType.INCOME)))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(type = TransactionType.EXPENSE), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_category_type_mismatch", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCategoryOwnedByAnotherUser_returnsCategoryNotFoundError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory(ownerId = EntityId("other-user"))))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_category_not_found", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withCategoryInDifferentWorkspace_returnsWorkspaceMismatchError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory(wsId = EntityId("other-ws"))))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(wsId = workspaceId), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("category_workspace_mismatch", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withDefaultSystemCategory_succeedsAcrossAnyWorkspace() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory(ownerId = null, wsId = null, isDefault = true)))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(wsId = EntityId("any-ws")), today, now)
        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(1, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_withDescriptionTrimAndNull_normalizesDescriptionWithoutAddingInstallmentSuffix() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(description = "   Bilgisayar Alımı   "), today, now)
        assertIs<RepositoryResult.Success<EntityId>>(result)

        val created = trxRepo.createInstallmentGroupCalls[0]
        assertEquals(3, created.size)
        assertEquals("Bilgisayar Alımı", created[0].description)
        assertEquals("Bilgisayar Alımı", created[1].description)
        assertEquals("Bilgisayar Alımı", created[2].description)
    }

    @Test
    fun addInstallmentGroup_withTooLongDescription_returnsDescriptionTooLongError() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val longDesc = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1)
        val result = useCase(sampleCommand(description = longDesc), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_description_too_long", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_whenIdGeneratorProducesDuplicates_returnsEntityIdGenerationFailedAndDoesNotCallRepo() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val duplicateIdGen = EntityIdGenerator { EntityId("duplicate-id") }
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, duplicateIdGen)

        val result = useCase(sampleCommand(), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("entity_id_generation_failed", assertIs<AppError.Unknown>(failure.error).code)
        assertEquals(0, trxRepo.createInstallmentGroupCalls.size)
    }

    @Test
    fun addInstallmentGroup_whenRepositoryFails_returnsRepositoryFailure() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo(failCreateInstallmentGroup = AppError.Storage("db_write_error"))
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(sampleCommand(), today, now)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("db_write_error", assertIs<AppError.Storage>(failure.error).code)
    }

    @Test
    fun addInstallmentGroup_whenCancellationOccurs_rethrowsCancellationException() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = object : TransactionRepository by FakeTrxRepo() {
            override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> {
                throw CancellationException("cancelled")
            }
        }
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        assertFailsWith<CancellationException> {
            useCase(sampleCommand(), today, now)
        }
    }

    @Test
    fun addInstallmentGroup_withValid1000Minor3Count_creates3TransactionsWithCorrectSplitsAndMetadata() = runTest {
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val catRepo = FakeCatRepo(listOf(sampleCategory()))
        val trxRepo = FakeTrxRepo()
        val idGen = SequentialIdGenerator()
        val useCase = AddInstallmentGroupUseCase(authRepo, catRepo, trxRepo, idGen)

        val result = useCase(
            sampleCommand(
                totalAmount = Money(1000L, Currency.TRY),
                installmentCount = 3,
                anchorDate = LocalDate(2026, 1, 31),
                receiptPath = ReceiptPath("receipts/fis.jpg"),
                description = "Telefon",
            ),
            today = LocalDate(2026, 2, 1),
            createdAt = now,
        )

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(1, trxRepo.createInstallmentGroupCalls.size)

        val list = trxRepo.createInstallmentGroupCalls[0]
        assertEquals(3, list.size)

        val commonGroupId = list[0].installment!!.groupId
        assertEquals(commonGroupId, result.value)

        // Amounts: 333, 333, 334 (remainder on last installment)
        assertEquals(333L, list[0].amount.amountMinor)
        assertEquals(333L, list[1].amount.amountMinor)
        assertEquals(334L, list[2].amount.amountMinor)

        // Currencies
        assertEquals(Currency.TRY, list[0].amount.currency)
        assertEquals(Currency.TRY, list[1].amount.currency)
        assertEquals(Currency.TRY, list[2].amount.currency)

        // Dates: Jan 31 -> Feb 28 -> Mar 31
        assertEquals(LocalDate(2026, 1, 31), list[0].transactionDate)
        assertEquals(LocalDate(2026, 2, 28), list[1].transactionDate)
        assertEquals(LocalDate(2026, 3, 31), list[2].transactionDate)

        // Installment structured info (number, total, groupId)
        assertEquals(InstallmentInfo(1, 3, commonGroupId), list[0].installment)
        assertEquals(InstallmentInfo(2, 3, commonGroupId), list[1].installment)
        assertEquals(InstallmentInfo(3, 3, commonGroupId), list[2].installment)

        // Distinct IDs
        val ids = list.map { it.id }.distinct()
        assertEquals(3, ids.size)

        // Common metadata
        list.forEach { trx ->
            assertEquals(userId, trx.ownerId)
            assertEquals(workspaceId, trx.workspaceId)
            assertEquals(categoryId, trx.categoryId)
            assertEquals(TransactionType.EXPENSE, trx.type)
            assertEquals(PaymentMethod.CREDIT_CARD, trx.paymentMethod)
            assertEquals("Telefon", trx.description)
            assertEquals(ReceiptPath("receipts/fis.jpg"), trx.receiptPath)
            assertEquals(now, trx.createdAt)
        }
    }

    // --- DeleteTransactionUseCase Tests ---

    @Test
    fun deleteTransaction_withNonInstallmentAndNullScope_callsSingleSoftDelete() = runTest {
        val nonInstTrx = sampleTransaction("t1", installment = null)
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(nonInstTrx))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t1"), installmentScope = null)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(EntityId("t1"), trxRepo.lastSingleSoftDeletedId)
        assertEquals(0, trxRepo.softDeleteInstallmentsCalls.size)
    }

    @Test
    fun deleteTransaction_withNonInstallmentAndInstallmentScope_returnsScopeNotApplicableError() = runTest {
        val nonInstTrx = sampleTransaction("t1", installment = null)
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(nonInstTrx))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t1"), installmentScope = InstallmentDeleteScope.ONLY_THIS)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_scope_not_applicable", assertIs<AppError.Validation>(failure.error).code)
        assertNull(trxRepo.lastSingleSoftDeletedId)
        assertEquals(0, trxRepo.softDeleteInstallmentsCalls.size)
    }

    @Test
    fun deleteTransaction_withInstallmentAndNullScope_returnsScopeRequiredError() = runTest {
        val instTrx = sampleTransaction("t1", installment = InstallmentInfo(1, 3, EntityId("grp-1")))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(instTrx))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t1"), installmentScope = null)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_delete_scope_required", assertIs<AppError.Validation>(failure.error).code)
        assertNull(trxRepo.lastSingleSoftDeletedId)
        assertEquals(0, trxRepo.softDeleteInstallmentsCalls.size)
    }

    @Test
    fun deleteTransaction_withOnlyThisScope_softDeletesOnlyTargetId() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 3, grpId))
        val t2 = sampleTransaction("t2", installment = InstallmentInfo(2, 3, grpId))
        val t3 = sampleTransaction("t3", installment = InstallmentInfo(3, 3, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, t2, t3))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t2"), installmentScope = InstallmentDeleteScope.ONLY_THIS)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertNull(trxRepo.lastSingleSoftDeletedId)
        assertEquals(1, trxRepo.softDeleteInstallmentsCalls.size)
        assertEquals(setOf(EntityId("t2")), trxRepo.softDeleteInstallmentsCalls[0])
    }

    @Test
    fun deleteTransaction_withThisAndFollowingScope_softDeletesTargetAndHigherNumberInstallments() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 4, grpId))
        val t2 = sampleTransaction("t2", installment = InstallmentInfo(2, 4, grpId))
        val t3 = sampleTransaction("t3", installment = InstallmentInfo(3, 4, grpId))
        val t4 = sampleTransaction("t4", installment = InstallmentInfo(4, 4, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, t2, t3, t4))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t2"), installmentScope = InstallmentDeleteScope.THIS_AND_FOLLOWING)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(1, trxRepo.softDeleteInstallmentsCalls.size)
        assertEquals(setOf(EntityId("t2"), EntityId("t3"), EntityId("t4")), trxRepo.softDeleteInstallmentsCalls[0])
    }

    @Test
    fun deleteTransaction_withThisAndFollowingScopeWithGaps_selectsExistingNumberGTEQTarget() = runTest {
        val grpId = EntityId("grp-1")
        // 1, 2, 4 mevcut (3 önceden silinmiş)
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 4, grpId))
        val t2 = sampleTransaction("t2", installment = InstallmentInfo(2, 4, grpId))
        val t4 = sampleTransaction("t4", installment = InstallmentInfo(4, 4, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, t2, t4))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t2"), installmentScope = InstallmentDeleteScope.THIS_AND_FOLLOWING)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(1, trxRepo.softDeleteInstallmentsCalls.size)
        assertEquals(setOf(EntityId("t2"), EntityId("t4")), trxRepo.softDeleteInstallmentsCalls[0])
    }

    @Test
    fun deleteTransaction_withAllGroupScope_softDeletesEntireGroup() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 3, grpId))
        val t2 = sampleTransaction("t2", installment = InstallmentInfo(2, 3, grpId))
        val t3 = sampleTransaction("t3", installment = InstallmentInfo(3, 3, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, t2, t3))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t2"), installmentScope = InstallmentDeleteScope.ALL_GROUP)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(1, trxRepo.softDeleteInstallmentsCalls.size)
        assertEquals(setOf(EntityId("t1"), EntityId("t2"), EntityId("t3")), trxRepo.softDeleteInstallmentsCalls[0])
    }

    @Test
    fun deleteTransaction_selectionIsNumberBasedNotDateBased() = runTest {
        val grpId = EntityId("grp-1")
        // Date order inverted to prove selection uses installment.number, not transactionDate
        val t1 = sampleTransaction("t1", date = LocalDate(2026, 3, 1), installment = InstallmentInfo(1, 2, grpId))
        val t2 = sampleTransaction("t2", date = LocalDate(2026, 1, 1), installment = InstallmentInfo(2, 2, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, t2))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t2"), installmentScope = InstallmentDeleteScope.THIS_AND_FOLLOWING)
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(1, trxRepo.softDeleteInstallmentsCalls.size)
        assertEquals(setOf(EntityId("t2")), trxRepo.softDeleteInstallmentsCalls[0])
    }

    @Test
    fun deleteTransaction_withCorruptGroupDifferentGroupOrTotal_returnsGroupMismatchError() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 3, grpId))
        val corruptT2 = sampleTransaction("t2", installment = InstallmentInfo(2, 4, grpId)) // Total 4 vs 3
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1, corruptT2))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t1"), installmentScope = InstallmentDeleteScope.ALL_GROUP)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_group_mismatch", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, trxRepo.softDeleteInstallmentsCalls.size)
    }

    @Test
    fun deleteTransaction_whenBatchRepoFails_returnsRepositoryFailure() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 2, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = FakeTrxRepo(listOf(t1), failBatchSoftDelete = AppError.Storage("batch_db_error"))
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        val result = useCase(EntityId("t1"), installmentScope = InstallmentDeleteScope.ONLY_THIS)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("batch_db_error", assertIs<AppError.Storage>(failure.error).code)
    }

    @Test
    fun deleteTransaction_whenCancellationOccurs_rethrowsCancellationException() = runTest {
        val grpId = EntityId("grp-1")
        val t1 = sampleTransaction("t1", installment = InstallmentInfo(1, 2, grpId))
        val authRepo = FakeAuthRepo(AuthSession(userId, "u@test.com", now))
        val trxRepo = object : TransactionRepository by FakeTrxRepo(listOf(t1)) {
            override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
                throw CancellationException("cancelled")
            }
        }
        val useCase = DeleteTransactionUseCase(authRepo, trxRepo)

        assertFailsWith<CancellationException> {
            useCase(EntityId("t1"), installmentScope = InstallmentDeleteScope.ONLY_THIS)
        }
    }

    private fun sampleTransaction(
        id: String,
        date: LocalDate = today,
        installment: InstallmentInfo? = null,
    ) = Transaction(
        id = EntityId(id),
        ownerId = userId,
        workspaceId = workspaceId,
        amount = Money(500L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        description = "Açıklama",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = installment,
        createdAt = now,
    )

    private class SequentialIdGenerator : EntityIdGenerator {
        private var counter = 0
        override fun nextId(): EntityId = EntityId("gen-id-${++counter}")
    }

    private class FakeAuthRepo(private val session: AuthSession?) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(session)
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private class FakeCatRepo(private val categories: List<Category>) : CategoryRepository {
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> = flowOf(categories)
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(categories.firstOrNull { it.id == id })
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> = flowOf(categories)
        override suspend fun create(category: Category) = RepositoryResult.Success(category.id)
        override suspend fun update(category: Category) = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId) = RepositoryResult.Success(Unit)
    }

    private class FakeTrxRepo(
        initial: List<Transaction> = emptyList(),
        private val failCreateInstallmentGroup: AppError? = null,
        private val failBatchSoftDelete: AppError? = null,
    ) : TransactionRepository {
        private val list = MutableStateFlow(initial)
        val createInstallmentGroupCalls = mutableListOf<List<Transaction>>()
        val softDeleteInstallmentsCalls = mutableListOf<Set<EntityId>>()
        var lastSingleSoftDeletedId: EntityId? = null

        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = list
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = list.map { it.firstOrNull { t -> t.id == id } }
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            list.map { it.filter { t -> t.installment?.groupId == groupId } }

        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
            list.value = list.value + transaction
            return RepositoryResult.Success(transaction.id)
        }

        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> {
            failCreateInstallmentGroup?.let { return RepositoryResult.Failure(it) }
            createInstallmentGroupCalls.add(transactions)
            list.value = list.value + transactions
            return RepositoryResult.Success(transactions.first().installment!!.groupId)
        }

        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
            list.value = list.value.map { if (it.id == transaction.id) transaction else it }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            lastSingleSoftDeletedId = id
            list.value = list.value.filterNot { it.id == id }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
            failBatchSoftDelete?.let { return RepositoryResult.Failure(it) }
            softDeleteInstallmentsCalls.add(ids)
            list.value = list.value.filterNot { it.id in ids }
            return RepositoryResult.Success(Unit)
        }
    }
}
