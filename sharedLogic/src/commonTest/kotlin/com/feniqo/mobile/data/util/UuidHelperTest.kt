package com.feniqo.mobile.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UuidHelperTest {

    @Test
    fun classify_canonicalLowercaseUuid_returnsCanonical() {
        val canonical = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(UuidFormat.CANONICAL, UuidHelper.classify(canonical))
        assertTrue(UuidHelper.isCanonicalUuid(canonical))
        assertFalse(UuidHelper.isLegacyCompactHex(canonical))
    }

    @Test
    fun classify_legacyCompactHexLowercaseAndUppercase_returnsLegacyCompactHex() {
        val compactLower = "123e4567e89b12d3a456426614174000"
        val compactUpper = "123E4567E89B12D3A456426614174000"

        assertEquals(UuidFormat.LEGACY_COMPACT_HEX, UuidHelper.classify(compactLower))
        assertEquals(UuidFormat.LEGACY_COMPACT_HEX, UuidHelper.classify(compactUpper))
        assertTrue(UuidHelper.isLegacyCompactHex(compactLower))
        assertTrue(UuidHelper.isLegacyCompactHex(compactUpper))
        assertFalse(UuidHelper.isCanonicalUuid(compactLower))
    }

    @Test
    fun classify_invalidFormats_returnsInvalid() {
        val upperCanonical = "123E4567-E89B-12D3-A456-426614174000"
        val invalidLength = "123e4567-e89b-12d3-a456-42661417400"
        val nonHex = "123e4567-e89b-12d3-a456-42661417400z"
        val prefixed = "urn:uuid:123e4567-e89b-12d3-a456-426614174000"
        val empty = ""
        val whitespace = "   "

        assertEquals(UuidFormat.INVALID, UuidHelper.classify(upperCanonical))
        assertEquals(UuidFormat.INVALID, UuidHelper.classify(invalidLength))
        assertEquals(UuidFormat.INVALID, UuidHelper.classify(nonHex))
        assertEquals(UuidFormat.INVALID, UuidHelper.classify(prefixed))
        assertEquals(UuidFormat.INVALID, UuidHelper.classify(empty))
        assertEquals(UuidFormat.INVALID, UuidHelper.classify(whitespace))

        assertFalse(UuidHelper.isCanonicalUuid(upperCanonical))
        assertFalse(UuidHelper.isCanonicalUuid(invalidLength))
    }

    @Test
    fun compactToCanonicalUuid_convertsCorrectlyToLowercase() {
        val compactLower = "123e4567e89b12d3a456426614174000"
        val compactUpper = "123E4567E89B12D3A456426614174000"
        val expected = "123e4567-e89b-12d3-a456-426614174000"

        assertEquals(expected, UuidHelper.compactToCanonicalUuid(compactLower))
        assertEquals(expected, UuidHelper.compactToCanonicalUuid(compactUpper))
    }

    @Test
    fun compactToCanonicalUuid_invalidInput_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            UuidHelper.compactToCanonicalUuid("invalid-not-32-hex")
        }
    }

    @Test
    fun canonicalize_handlesCanonicalAndCompactAndThrowsOnInvalid() {
        val canonical = "123e4567-e89b-12d3-a456-426614174000"
        val compact = "123e4567e89b12d3a456426614174000"

        assertEquals(canonical, UuidHelper.canonicalize(canonical))
        assertEquals(canonical, UuidHelper.canonicalize(compact))

        assertFailsWith<IllegalArgumentException> {
            UuidHelper.canonicalize("not-valid")
        }
    }

    @Test
    fun toCanonicalOrNull_returnsNullOnInvalidOrNull() {
        assertNull(UuidHelper.toCanonicalOrNull(null))
        assertNull(UuidHelper.toCanonicalOrNull("invalid"))
        val canonical = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(canonical, UuidHelper.toCanonicalOrNull(canonical))
        assertEquals(canonical, UuidHelper.toCanonicalOrNull("123e4567e89b12d3a456426614174000"))
    }
}
