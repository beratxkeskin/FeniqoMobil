package com.feniqo.mobile.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspaceInvitationCryptoTest {

    @Test
    fun sha256_matches_known_nist_vectors() {
        // Test vector 1: "abc"
        val abcHash = WorkspaceInvitationCrypto.hashInvitationToken("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            abcHash,
        )

        // Test vector 2: 448-bit multi-block NIST vector
        val multiBlockHash = WorkspaceInvitationCrypto.hashInvitationToken("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            multiBlockHash,
        )
    }

    @Test
    fun hashInvitationToken_rejects_empty_or_blank_token() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitationCrypto.hashInvitationToken("")
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitationCrypto.hashInvitationToken("   ")
        }
    }

    @Test
    fun generateSecureInvitationToken_produces_64_char_hex_token() {
        val token1 = WorkspaceInvitationCrypto.generateSecureInvitationToken()
        val token2 = WorkspaceInvitationCrypto.generateSecureInvitationToken()

        assertEquals(64, token1.length)
        assertEquals(64, token2.length)
        assertTrue(WorkspaceInvitationCrypto.isValidTokenHash(token1))
        assertTrue(WorkspaceInvitationCrypto.isValidTokenHash(token2))
        assertNotEquals(token1, token2)
    }

    @Test
    fun hashInvitationToken_is_deterministic_and_valid() {
        val rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val hash1 = WorkspaceInvitationCrypto.hashInvitationToken(rawToken)
        val hash2 = WorkspaceInvitationCrypto.hashInvitationToken(rawToken)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(WorkspaceInvitationCrypto.isValidTokenHash(hash1))
        // Raw token and its hash must never be identical
        assertNotEquals(rawToken, hash1)
    }

    @Test
    fun isValidTokenHash_validates_correctly() {
        assertTrue(WorkspaceInvitationCrypto.isValidTokenHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        assertTrue(WorkspaceInvitationCrypto.isValidTokenHash("0000000000000000000000000000000000000000000000000000000000000000"))

        // Short
        assertFalse(WorkspaceInvitationCrypto.isValidTokenHash("ba7816bf"))
        // Uppercase
        assertFalse(WorkspaceInvitationCrypto.isValidTokenHash("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
        // Non-hex
        assertFalse(WorkspaceInvitationCrypto.isValidTokenHash("ga7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        // Blank
        assertFalse(WorkspaceInvitationCrypto.isValidTokenHash(""))
        assertFalse(WorkspaceInvitationCrypto.isValidTokenHash("   "))
    }
}
