package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionParticipantShareDto
import com.feniqo.mobile.data.sync.EquivalentConflictResolver
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransactionSplitRemoteMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val user1 = EntityId("user-1")
    private val user2 = EntityId("user-2")
    private val user3 = EntityId("user-3")

    @Test
    fun valid_custom_split_round_trips_losslessly() {
        val domain = Transaction(
            id = EntityId("tx-1"),
            ownerId = user1,
            workspaceId = EntityId("ws-1"),
            amount = Money(30_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Akşam Yemeği",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-09-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            paidByUserId = user1,
            participantUserIds = listOf(user1, user2, user3),
            note = "Özel paylaşımlı",
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = listOf(
                TransactionParticipantShare(user2, 5_000L),
                TransactionParticipantShare(user3, 10_000L),
                TransactionParticipantShare(user1, 15_000L),
            ),
        )

        val dto = domain.toDto()
        assertEquals("CUSTOM", dto.splitMode)
        // Kanonik sıralı olmalı: user-1, user-2, user-3
        assertEquals(3, dto.participantShares?.size)
        assertEquals("user-1", dto.participantShares?.get(0)?.userId)
        assertEquals(15_000L, dto.participantShares?.get(0)?.amountMinor)
        assertEquals("user-2", dto.participantShares?.get(1)?.userId)
        assertEquals(5_000L, dto.participantShares?.get(1)?.amountMinor)
        assertEquals("user-3", dto.participantShares?.get(2)?.userId)
        assertEquals(10_000L, dto.participantShares?.get(2)?.amountMinor)

        val jsonStr = json.encodeToString(dto)
        val decodedDto = json.decodeFromString<TransactionDto>(jsonStr)
        val decodedDomain = decodedDto.toDomain()

        assertEquals(domain.id, decodedDomain.id)
        assertEquals(TransactionSplitMode.CUSTOM, decodedDomain.splitMode)
        assertEquals(3, decodedDomain.participantShares.size)
        assertEquals(15_000L, decodedDomain.participantShares.first { it.userId == user1 }.amountMinor)
        assertEquals(5_000L, decodedDomain.participantShares.first { it.userId == user2 }.amountMinor)
        assertEquals(10_000L, decodedDomain.participantShares.first { it.userId == user3 }.amountMinor)
    }

    @Test
    fun legacy_payload_without_split_fields_maps_to_equal_split() {
        val legacyJson = """
            {
                "id": "tx-legacy",
                "user_id": "user-1",
                "workspace_id": "ws-1",
                "paid_by_user_id": "user-1",
                "participant_user_ids": ["user-1", "user-2"],
                "amount_minor": 20000,
                "currency": "TRY",
                "type": "expense",
                "category_id": "cat-1",
                "payment_method": "cash",
                "transaction_date": "2026-09-18",
                "created_at": "2026-09-18T10:00:00Z"
            }
        """.trimIndent()

        val dto = json.decodeFromString<TransactionDto>(legacyJson)
        assertEquals(null, dto.splitMode)
        assertEquals(null, dto.participantShares)

        val domain = dto.toDomain()
        assertEquals(TransactionSplitMode.EQUAL, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
    }

    @Test
    fun legacy_payload_with_explicit_nulls_maps_to_equal_split() {
        val legacyJsonWithNulls = """
            {
                "id": "tx-legacy-nulls",
                "user_id": "user-1",
                "workspace_id": "ws-1",
                "paid_by_user_id": "user-1",
                "participant_user_ids": ["user-1", "user-2"],
                "amount_minor": 20000,
                "currency": "TRY",
                "type": "expense",
                "category_id": "cat-1",
                "payment_method": "cash",
                "transaction_date": "2026-09-18",
                "created_at": "2026-09-18T10:00:00Z",
                "split_mode": null,
                "participant_shares": null
            }
        """.trimIndent()

        val dto = json.decodeFromString<TransactionDto>(legacyJsonWithNulls)
        val domain = dto.toDomain()
        assertEquals(TransactionSplitMode.EQUAL, domain.splitMode)
        assertTrue(domain.participantShares.isEmpty())
    }

    @Test
    fun rejects_missing_split_mode_when_participant_shares_is_present() {
        val invalidDto = baseTransactionDto().copy(
            splitMode = null,
            participantShares = listOf(TransactionParticipantShareDto(userId = "user-1", amountMinor = 10000L)),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_equal_split_with_non_empty_participant_shares() {
        val invalidDto = baseTransactionDto().copy(
            splitMode = "EQUAL",
            participantShares = listOf(TransactionParticipantShareDto(userId = "user-1", amountMinor = 10000L)),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_unknown_or_blank_split_mode() {
        assertFailsWith<RemoteMappingException> {
            baseTransactionDto().copy(splitMode = "PERCENTAGE").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            baseTransactionDto().copy(splitMode = "   ").toDomain()
        }
    }

    @Test
    fun rejects_custom_split_with_empty_or_null_participant_shares() {
        assertFailsWith<RemoteMappingException> {
            baseTransactionDto().copy(splitMode = "CUSTOM", participantShares = null).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            baseTransactionDto().copy(splitMode = "CUSTOM", participantShares = emptyList()).toDomain()
        }
    }

    @Test
    fun rejects_custom_split_with_blank_user_id() {
        val invalidDto = baseTransactionDto().copy(
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1", ""),
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 5000L),
                TransactionParticipantShareDto(userId = "   ", amountMinor = 5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_with_negative_share() {
        val invalidDto = baseTransactionDto().copy(
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1", "user-2"),
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 15000L),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = -5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_with_duplicate_participant_in_shares() {
        val invalidDto = baseTransactionDto().copy(
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1"),
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 5000L),
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_when_payer_not_in_shares() {
        val invalidDto = baseTransactionDto().copy(
            paidByUserId = "user-1",
            participantUserIds = listOf("user-2", "user-3"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 5000L),
                TransactionParticipantShareDto(userId = "user-3", amountMinor = 5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_when_non_payer_has_zero_share() {
        val invalidDto = baseTransactionDto().copy(
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 10000L),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 0L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun allows_custom_split_when_payer_has_zero_share_paid_for_others() {
        val validDto = baseTransactionDto().copy(
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 0L),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 10000L),
            ),
        )

        val domain = validDto.toDomain()
        assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
        assertEquals(0L, domain.participantShares.first { it.userId == user1 }.amountMinor)
        assertEquals(10000L, domain.participantShares.first { it.userId == user2 }.amountMinor)
    }

    @Test
    fun rejects_custom_split_when_participant_user_ids_set_mismatches_shares() {
        // user-3 participantUserIds içinde var ama shares içinde yok
        val invalidDto = baseTransactionDto().copy(
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2", "user-3"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 5000L),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_when_sum_does_not_equal_amount_minor() {
        val invalidDto = baseTransactionDto().copy(
            amountMinor = 10000L,
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 4000L),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 5000L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun rejects_custom_split_on_long_overflow_during_sum() {
        val invalidDto = baseTransactionDto().copy(
            amountMinor = 10000L,
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = Long.MAX_VALUE - 10),
                TransactionParticipantShareDto(userId = "user-2", amountMinor = 100L),
            ),
        )

        assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
    }

    @Test
    fun equivalent_conflict_resolver_compares_split_mode_and_shares() {
        val tx1 = baseTransactionDto().copy(
            amountMinor = 10000L,
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1", "user-2"),
            participantShares = listOf(
                TransactionParticipantShareDto("user-1", 6000L),
                TransactionParticipantShareDto("user-2", 4000L),
            ),
        )
        // tx2 aynı paylar farklı sırada
        val tx2 = tx1.copy(
            participantShares = listOf(
                TransactionParticipantShareDto("user-2", 4000L),
                TransactionParticipantShareDto("user-1", 6000L),
            ),
        )
        // tx3 farklı paylar
        val tx3 = tx1.copy(
            participantShares = listOf(
                TransactionParticipantShareDto("user-1", 5000L),
                TransactionParticipantShareDto("user-2", 5000L),
            ),
        )
        // tx4 EQUAL
        val tx4 = tx1.copy(
            splitMode = "EQUAL",
            participantShares = emptyList(),
        )

        val json1 = json.encodeToString(tx1)
        val json2 = json.encodeToString(tx2)
        val json3 = json.encodeToString(tx3)
        val json4 = json.encodeToString(tx4)

        assertTrue(EquivalentConflictResolver.isTransactionEquivalent(json1, json2))
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(json1, json3))
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(json1, json4))
    }

    @Test
    fun rejects_custom_split_when_participant_user_ids_is_empty_even_if_payer_is_specified() {
        val invalidDto = baseTransactionDto().copy(
            paidByUserId = "user-1",
            participantUserIds = emptyList(),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto(userId = "user-1", amountMinor = 10000L),
            ),
        )

        val ex = assertFailsWith<RemoteMappingException> {
            invalidDto.toDomain()
        }
        assertTrue(ex.message?.contains("CUSTOM modunda participant_user_ids boş olamaz") == true)
    }

    @Test
    fun equivalent_conflict_resolver_rejects_corrupt_or_inconsistent_splits() {
        // 1. Bozuk EQUAL: participantShares dolu
        val corruptEqual = baseTransactionDto().copy(
            splitMode = "EQUAL",
            participantShares = listOf(TransactionParticipantShareDto("user-1", 10000L)),
        )
        val jsonCorruptEqual = json.encodeToString(corruptEqual)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonCorruptEqual, jsonCorruptEqual))

        // 2. Bozuk CUSTOM: participantUserIds boş
        val corruptCustomNoParticipants = baseTransactionDto().copy(
            splitMode = "CUSTOM",
            participantUserIds = emptyList(),
            participantShares = listOf(TransactionParticipantShareDto("user-1", 10000L)),
        )
        val jsonNoParticipants = json.encodeToString(corruptCustomNoParticipants)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonNoParticipants, jsonNoParticipants))

        // 3. Bozuk CUSTOM: participantShares boş
        val corruptCustomNoShares = baseTransactionDto().copy(
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1"),
            participantShares = emptyList(),
        )
        val jsonNoShares = json.encodeToString(corruptCustomNoShares)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonNoShares, jsonNoShares))

        // 4. Bozuk CUSTOM: paylar toplamı tutara eşit değil (6000 + 3000 != 10000)
        val corruptCustomSumMismatch = baseTransactionDto().copy(
            amountMinor = 10000L,
            splitMode = "CUSTOM",
            participantUserIds = listOf("user-1", "user-2"),
            participantShares = listOf(
                TransactionParticipantShareDto("user-1", 6000L),
                TransactionParticipantShareDto("user-2", 3000L),
            ),
        )
        val jsonSumMismatch = json.encodeToString(corruptCustomSumMismatch)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonSumMismatch, jsonSumMismatch))

        // 5. Bozuk CUSTOM: ödeyen dışı katılımcıya 0 pay
        val corruptCustomZeroShare = baseTransactionDto().copy(
            amountMinor = 10000L,
            paidByUserId = "user-1",
            participantUserIds = listOf("user-1", "user-2"),
            splitMode = "CUSTOM",
            participantShares = listOf(
                TransactionParticipantShareDto("user-1", 10000L),
                TransactionParticipantShareDto("user-2", 0L),
            ),
        )
        val jsonZeroShare = json.encodeToString(corruptCustomZeroShare)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonZeroShare, jsonZeroShare))

        // 6. Bilinmeyen veya boş split_mode
        val invalidMode = baseTransactionDto().copy(
            splitMode = "UNKNOWN",
        )
        val jsonInvalidMode = json.encodeToString(invalidMode)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonInvalidMode, jsonInvalidMode))

        val blankMode = baseTransactionDto().copy(
            splitMode = "   ",
        )
        val jsonBlankMode = json.encodeToString(blankMode)
        assertFalse(EquivalentConflictResolver.isTransactionEquivalent(jsonBlankMode, jsonBlankMode))
    }

    private fun baseTransactionDto() = TransactionDto(
        id = "tx-1",
        userId = "user-1",
        workspaceId = "ws-1",
        paidByUserId = "user-1",
        participantUserIds = listOf("user-1"),
        amountMinor = 10000L,
        currency = "TRY",
        type = "expense",
        categoryId = "cat-1",
        description = "Test İşlem",
        paymentMethod = "cash",
        transactionDate = "2026-09-18",
        createdAt = "2026-09-18T10:00:00Z",
    )
}
