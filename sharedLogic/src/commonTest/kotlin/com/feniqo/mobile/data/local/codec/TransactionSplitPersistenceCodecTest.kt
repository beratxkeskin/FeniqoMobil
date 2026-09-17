package com.feniqo.mobile.data.local.codec

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionSplitPersistenceCodecTest {

    private val user1 = EntityId("user-1")
    private val user2 = EntityId("user-2")
    private val user3 = EntityId("user-3")

    @Test
    fun customShares_encodeDecodeRoundTrip_preservesAllData() {
        val originalShares = listOf(
            TransactionParticipantShare(user1, 5_000L),
            TransactionParticipantShare(user2, 3_000L),
            TransactionParticipantShare(user3, 2_000L),
        )

        val encoded = TransactionSplitPersistenceCodec.encode(originalShares)
        val decodedResult = TransactionSplitPersistenceCodec.decode(
            splitModeCode = "CUSTOM",
            participantSharesJson = encoded,
        )

        val valid = assertIs<TransactionSplitDecodeResult.Valid>(decodedResult)
        assertEquals(TransactionSplitMode.CUSTOM, valid.splitMode)
        assertEquals(3, valid.shares.size)
        assertEquals(5_000L, valid.shares.first { it.userId == user1 }.amountMinor)
        assertEquals(3_000L, valid.shares.first { it.userId == user2 }.amountMinor)
        assertEquals(2_000L, valid.shares.first { it.userId == user3 }.amountMinor)
    }

    @Test
    fun differentInputOrder_producesIdenticalCanonicalJson() {
        val sharesOrderA = listOf(
            TransactionParticipantShare(user3, 2_000L),
            TransactionParticipantShare(user1, 5_000L),
            TransactionParticipantShare(user2, 3_000L),
        )
        val sharesOrderB = listOf(
            TransactionParticipantShare(user2, 3_000L),
            TransactionParticipantShare(user3, 2_000L),
            TransactionParticipantShare(user1, 5_000L),
        )

        val encodedA = TransactionSplitPersistenceCodec.encode(sharesOrderA)
        val encodedB = TransactionSplitPersistenceCodec.encode(sharesOrderB)

        assertEquals(encodedA, encodedB)
        // user-1, user-2, user-3 artan sırada
        assertEquals(
            """[{"userId":"user-1","amountMinor":5000},{"userId":"user-2","amountMinor":3000},{"userId":"user-3","amountMinor":2000}]""",
            encodedA,
        )
    }

    @Test
    fun largeLongValues_preservedWithoutPrecisionLoss() {
        val largeAmountMinor = Long.MAX_VALUE - 1000L
        val shares = listOf(
            TransactionParticipantShare(user1, largeAmountMinor),
        )

        val encoded = TransactionSplitPersistenceCodec.encode(shares)
        val decoded = TransactionSplitPersistenceCodec.decode("CUSTOM", encoded)

        val valid = assertIs<TransactionSplitDecodeResult.Valid>(decoded)
        assertEquals(largeAmountMinor, valid.shares.first().amountMinor)
    }

    @Test
    fun malformedJson_returnsInvalidMalformedJson() {
        val malformedJson = """[{"userId": "user-1", amountMinor: }]"""
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", malformedJson)

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.MalformedJson>(result)
        assertTrue(invalid.reason.isNotEmpty())
    }

    @Test
    fun duplicateUser_returnsInvalidDuplicateUser() {
        val jsonWithDuplicates = """[{"userId":"user-1","amountMinor":1000},{"userId":"user-1","amountMinor":2000}]"""
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", jsonWithDuplicates)

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.DuplicateUser>(result)
        assertEquals("user-1", invalid.userId)
    }

    @Test
    fun blankUserId_returnsInvalidBlankUserId() {
        val jsonWithBlankUser = """[{"userId":"","amountMinor":1000}]"""
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", jsonWithBlankUser)

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.BlankUserId>(result)
        assertEquals(0, invalid.index)
    }

    @Test
    fun missingAmountMinor_returnsInvalidMalformedJson() {
        val jsonMissingAmount = """[{"userId":"user-1"}]"""
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", jsonMissingAmount)

        assertIs<TransactionSplitDecodeResult.Invalid.MalformedJson>(result)
    }

    @Test
    fun unknownSplitMode_returnsInvalidUnknownSplitMode() {
        val result = TransactionSplitPersistenceCodec.decode("PERCENTAGE", "[]")

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.UnknownSplitMode>(result)
        assertEquals("PERCENTAGE", invalid.rawMode)
    }

    @Test
    fun equalWithEmptyShares_returnsValidEqual() {
        val result = TransactionSplitPersistenceCodec.decode("EQUAL", "[]")

        val valid = assertIs<TransactionSplitDecodeResult.Valid>(result)
        assertEquals(TransactionSplitMode.EQUAL, valid.splitMode)
        assertTrue(valid.shares.isEmpty())
    }

    @Test
    fun equalWithNonEmptyShares_returnsInvalidNonEmptySharesForEqual() {
        val jsonWithShares = """[{"userId":"user-1","amountMinor":1000}]"""
        val result = TransactionSplitPersistenceCodec.decode("EQUAL", jsonWithShares)

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.NonEmptySharesForEqual>(result)
        assertEquals(1, invalid.count)
    }

    @Test
    fun customWithEmptyShares_returnsInvalidEmptySharesForCustom() {
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", "[]")

        assertIs<TransactionSplitDecodeResult.Invalid.EmptySharesForCustom>(result)
    }

    @Test
    fun customWithNegativeShare_returnsInvalidNegativeShare() {
        val jsonWithNegative = """[{"userId":"user-1","amountMinor":-500}]"""
        val result = TransactionSplitPersistenceCodec.decode("CUSTOM", jsonWithNegative)

        val invalid = assertIs<TransactionSplitDecodeResult.Invalid.NegativeShare>(result)
        assertEquals("user-1", invalid.userId)
        assertEquals(-500L, invalid.amountMinor)
    }
}
