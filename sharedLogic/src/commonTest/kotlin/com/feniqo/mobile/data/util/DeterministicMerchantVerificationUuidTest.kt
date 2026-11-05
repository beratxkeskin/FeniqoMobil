package com.feniqo.mobile.data.util

import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicMerchantVerificationUuidTest {
    private val testUserId = "67a3f892-12c4-4b9a-b8e7-9d8e7a6b5c4d"

    @Test
    fun generatesExpectedGoldenVector1_Netflix() {
        val uuid = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "NETFLIX")
        assertEquals("fddf797b-6b74-89ea-a969-f30df71d5921", uuid)
        assertEquals(UuidFormat.CANONICAL, UuidHelper.classify(uuid))
        assertEquals('8', uuid[14], "Octet 6 RFC 9562 version 8 olmalı (xxxxxxxx-xxxx-8xxx-yxxx-xxxxxxxxxxxx)")
        assertTrue(uuid[19] in listOf('8', '9', 'a', 'b'), "Octet 8 RFC 9562 variant 10xx olmalı")
    }

    @Test
    fun generatesExpectedGoldenVector2_KahveDunyasi() {
        val canonicalAlias = TurkishDescriptionNormalizer.normalize("Kahve Dünyası")
        assertEquals("KAHVE DUNYASI", canonicalAlias)

        val uuid = UuidHelper.deterministicMerchantVerificationUuid(testUserId, canonicalAlias)
        assertEquals("ac077b4d-9255-813b-abd2-0893d4b1c26a", uuid)
        assertEquals(UuidFormat.CANONICAL, UuidHelper.classify(uuid))
        assertEquals('8', uuid[14])
        assertTrue(uuid[19] in listOf('8', '9', 'a', 'b'))
    }

    @Test
    fun generatesSameIdForSameInputs() {
        val id1 = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "NETFLIX")
        val id2 = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "NETFLIX")
        assertEquals(id1, id2)
    }

    @Test
    fun generatesDifferentIdForDifferentUser() {
        val otherUserId = "a0000000-0000-4000-8000-000000000002"
        val id1 = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "NETFLIX")
        val id2 = UuidHelper.deterministicMerchantVerificationUuid(otherUserId, "NETFLIX")
        assertTrue(id1 != id2)
    }

    @Test
    fun generatesDifferentIdForDifferentAlias() {
        val id1 = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "NETFLIX")
        val id2 = UuidHelper.deterministicMerchantVerificationUuid(testUserId, "SPOTIFY")
        assertTrue(id1 != id2)
    }

    @Test
    fun rejectsNonCanonicalUserId() {
        assertFailsWith<IllegalArgumentException> {
            UuidHelper.deterministicMerchantVerificationUuid("not-a-uuid", "NETFLIX")
        }
    }

    @Test
    fun rejectsBlankAlias() {
        assertFailsWith<IllegalArgumentException> {
            UuidHelper.deterministicMerchantVerificationUuid(testUserId, "")
        }
    }

    @Test
    fun rejectsNonCanonicalAlias() {
        assertFailsWith<IllegalArgumentException> {
            UuidHelper.deterministicMerchantVerificationUuid(testUserId, "netflix")
        }
    }

    @Test
    fun rejectsDisallowedGenericTokens() {
        assertFailsWith<IllegalArgumentException> {
            UuidHelper.deterministicMerchantVerificationUuid(testUserId, "MARKET")
        }
    }
}
