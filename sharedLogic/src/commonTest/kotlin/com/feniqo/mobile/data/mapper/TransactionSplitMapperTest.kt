package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionSplitMapperTest {

    private val ownerId = EntityId("user-owner")
    private val member1Id = EntityId("user-1")
    private val member2Id = EntityId("user-2")
    private val workspaceId = EntityId("workspace-1")
    private val categoryId = EntityId("category-1")
    private val now = Instant.parse("2026-09-17T00:00:00Z")

    private val syncMetadata = SyncMetadata(
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtEpochMillis = now.toEpochMilliseconds(),
        localUpdatedAtEpochMillis = now.toEpochMilliseconds(),
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private fun sampleExpenseTransaction(
        splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
        shares: List<TransactionParticipantShare> = emptyList(),
    ) = Transaction(
        id = EntityId("tx-1"),
        ownerId = ownerId,
        workspaceId = workspaceId,
        amount = Money(10_000L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        description = "Test expense",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 9, 17),
        receiptPath = null,
        installment = null,
        createdAt = now,
        paidByUserId = ownerId,
        participantUserIds = if (splitMode == TransactionSplitMode.CUSTOM) {
            shares.map { it.userId }
        } else {
            listOf(ownerId, member1Id)
        },
        splitMode = splitMode,
        participantShares = shares,
    )

    @Test
    fun equalDomainToEntityToDomain_isPreserved() {
        val domain = sampleExpenseTransaction(
            splitMode = TransactionSplitMode.EQUAL,
            shares = emptyList(),
        )

        val entity = domain.toEntity(syncMetadata)
        assertEquals("EQUAL", entity.splitMode)
        assertEquals("[]", entity.participantSharesJson)

        val mappedBack = entity.toDomain()
        assertEquals(TransactionSplitMode.EQUAL, mappedBack.splitMode)
        assertTrue(mappedBack.participantShares.isEmpty())
        assertEquals(domain.paidByUserId, mappedBack.paidByUserId)
        assertEquals(domain.participantUserIds, mappedBack.participantUserIds)
    }

    @Test
    fun custom50_30_20_domainToEntityToDomain_isPreserved() {
        val shares = listOf(
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
            TransactionParticipantShare(member2Id, 2_000L),
        )
        val domain = sampleExpenseTransaction(
            splitMode = TransactionSplitMode.CUSTOM,
            shares = shares,
        )

        val entity = domain.toEntity(syncMetadata)
        assertEquals("CUSTOM", entity.splitMode)

        val mappedBack = entity.toDomain()
        assertEquals(TransactionSplitMode.CUSTOM, mappedBack.splitMode)
        assertEquals(3, mappedBack.participantShares.size)
        assertTrue(mappedBack.hasEquivalentShares(shares))
    }

    @Test
    fun customShares_storedInCanonicalOrder() {
        // user-2, user-owner, user-1 sırasız
        val uncanonicalShares = listOf(
            TransactionParticipantShare(member2Id, 2_000L),
            TransactionParticipantShare(ownerId, 5_000L),
            TransactionParticipantShare(member1Id, 3_000L),
        )
        val domain = sampleExpenseTransaction(
            splitMode = TransactionSplitMode.CUSTOM,
            shares = uncanonicalShares,
        )

        val entity = domain.toEntity(syncMetadata)
        // user-1, user-2, user-owner alfabetik artan sıraya dizilmiş olmalı
        assertEquals(
            """[{"userId":"user-1","amountMinor":3000},{"userId":"user-2","amountMinor":2000},{"userId":"user-owner","amountMinor":5000}]""",
            entity.participantSharesJson,
        )
    }

    @Test
    fun malformedCustomJson_producesInvalidSentinelWithoutThrowing() {
        val corruptEntity = sampleExpenseTransaction().toEntity(syncMetadata).copy(
            splitMode = "CUSTOM",
            participantSharesJson = "[corrupted json",
        )

        // Exception fırlatmamalı; kontrollü invalid sentinel üretmeli
        val domain = corruptEntity.toDomain()
        assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
    }

    @Test
    fun unknownSplitMode_producesInvalidSentinelInsteadOfDefaultingToEqual() {
        val unknownEntity = sampleExpenseTransaction().toEntity(syncMetadata).copy(
            splitMode = "PERCENTAGE",
            participantSharesJson = "[]",
        )

        val domain = unknownEntity.toDomain()
        assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
    }

    @Test
    fun equalWithNonEmptyShares_producesInvalidSentinelInsteadOfSilentlyAcceptingEqual() {
        val invalidEqualEntity = sampleExpenseTransaction().toEntity(syncMetadata).copy(
            splitMode = "EQUAL",
            participantSharesJson = """[{"userId":"user-1","amountMinor":5000}]""",
        )

        val domain = invalidEqualEntity.toDomain()
        // Sessizce EQUAL yapılmamalı, hatalı split olarak işaretlenmeli
        assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
    }

    @Test
    fun legacyV18DefaultFields_producesEqualSplitBehavior() {
        // v18'den gelen kayıtlarda default split_mode = 'EQUAL' ve participant_shares_json = '[]'
        val legacyEntity = TransactionEntity(
            id = "tx-legacy",
            ownerId = "user-owner",
            workspaceId = "workspace-1",
            paidByUserId = "user-owner",
            participantUserIdsJson = """["user-owner","user-1"]""",
            amountMinor = 10_000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = "category-1",
            description = "Legacy v18 transaction",
            searchText = "legacy",
            paymentMethodCode = "CASH",
            transactionDate = "2026-09-17",
            receiptPath = null,
            installmentNumber = null,
            totalInstallments = null,
            installmentGroupId = null,
            createdAtEpochMillis = now.toEpochMilliseconds(),
            note = null,
            sync = syncMetadata,
            // default değerler:
            splitMode = "EQUAL",
            participantSharesJson = "[]",
        )

        val domain = legacyEntity.toDomain()
        assertEquals(TransactionSplitMode.EQUAL, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
        assertEquals(listOf(EntityId("user-owner"), EntityId("user-1")), domain.participantUserIds)
    }
}
