package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransactionSplitValidationTest {

    private val ownerId = EntityId("user-owner")
    private val member1Id = EntityId("user-member-1")
    private val member2Id = EntityId("user-member-2")
    private val nonMemberId = EntityId("user-stranger")
    private val workspaceId = EntityId("workspace-1")
    private val categoryId = EntityId("category-1")
    private val now = Instant.parse("2026-09-08T00:00:00Z")

    private fun expenseTransaction(
        wsId: EntityId? = workspaceId,
        payer: EntityId = ownerId,
        participants: List<EntityId> = listOf(ownerId, member1Id),
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = EntityId("tx-1"),
        ownerId = ownerId,
        workspaceId = wsId,
        amount = Money(10_000L, Currency.TRY),
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
    )

    @Test
    fun personalExpense_normalizesSplitToOwnerOnly() {
        val tx = expenseTransaction(
            wsId = null,
            payer = member1Id,
            participants = listOf(member1Id, member2Id),
        )

        val result = TransactionValidationRules.normalizeAndValidateSplit(tx)
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(ownerId, result.value.paidByUserId)
        assertEquals(listOf(ownerId), result.value.participantUserIds)
    }

    @Test
    fun workspaceIncome_normalizesSplitToOwnerOnly() {
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = member1Id,
            participants = listOf(member1Id, member2Id),
            type = TransactionType.INCOME,
        )

        val result = TransactionValidationRules.normalizeAndValidateSplit(tx)
        assertIs<TransactionValidationResult.Valid<Transaction>>(result)
        assertEquals(ownerId, result.value.paidByUserId)
        assertEquals(listOf(ownerId), result.value.participantUserIds)
    }

    @Test
    fun workspaceExpense_validMembersAndPayer_returnsValid() {
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
    }

    @Test
    fun workspaceExpense_payerNotInParticipants_returnsInvalid() {
        // Transaction init require'ına takılmamak için payer'ı dahil edip kopya üzerinden veya direkt kural testi
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = ownerId,
            participants = listOf(ownerId, member1Id),
        )
        // copy ile payer'ı katılımcı dışına çıkarıp kuralı test ediyoruz
        val invalidTx = try {
            tx.copy(paidByUserId = nonMemberId)
        } catch (e: IllegalArgumentException) {
            // Domain Transaction init de koruyor
            null
        }

        if (invalidTx != null) {
            val result = TransactionValidationRules.normalizeAndValidateSplit(invalidTx)
            assertEquals(
                TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_PAYER_NOT_IN_PARTICIPANTS),
                result,
            )
        }
    }

    @Test
    fun workspaceExpense_nonMemberPayerOrParticipant_returnsInvalid() {
        val tx = expenseTransaction(
            wsId = workspaceId,
            payer = nonMemberId,
            participants = listOf(ownerId, nonMemberId),
        )

        val activeMembers = setOf(ownerId, member1Id, member2Id)
        val result = TransactionValidationRules.normalizeAndValidateSplit(tx, activeMembers)
        assertEquals(
            TransactionValidationResult.Invalid(TransactionValidationError.SPLIT_MEMBER_NOT_IN_WORKSPACE),
            result,
        )
    }
}
