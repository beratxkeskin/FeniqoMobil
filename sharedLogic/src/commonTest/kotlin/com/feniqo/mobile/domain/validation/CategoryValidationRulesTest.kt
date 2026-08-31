package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CategoryColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryValidationRulesTest {

    @Test
    fun validateName_withValidAndBlankNames_returnsExpectedResults() {
        val valid = CategoryValidationRules.validateName("  Market  ")
        assertTrue(valid is CategoryValidationResult.Valid)
        assertEquals("Market", valid.value)

        val empty = CategoryValidationRules.validateName("")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.NAME_EMPTY), empty)

        val whitespace = CategoryValidationRules.validateName("    ")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.NAME_EMPTY), whitespace)

        val exactly50 = "A".repeat(50)
        val valid50 = CategoryValidationRules.validateName("  $exactly50  ")
        assertTrue(valid50 is CategoryValidationResult.Valid)
        assertEquals(exactly50, valid50.value)

        val length51 = "A".repeat(51)
        val invalid51 = CategoryValidationRules.validateName(length51)
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.NAME_TOO_LONG), invalid51)
    }

    @Test
    fun normalizeName_trimsAndConvertsToLowercase() {
        assertEquals("market", CategoryValidationRules.normalizeName("  Market  "))
        assertEquals("diğer gelir", CategoryValidationRules.normalizeName("  Diğer Gelir  "))
        assertEquals("fatura", CategoryValidationRules.normalizeName("FATURA"))
    }

    @Test
    fun validateColor_withValidAndInvalidHexCodes_returnsExpectedResults() {
        val valid = CategoryValidationRules.validateColor("#4CAF50")
        assertTrue(valid is CategoryValidationResult.Valid)
        assertEquals(CategoryColor("#4CAF50"), valid.value)

        val validLower = CategoryValidationRules.validateColor("#1a2b3c")
        assertTrue(validLower is CategoryValidationResult.Valid)
        assertEquals(CategoryColor("#1a2b3c"), validLower.value)

        val missingHash = CategoryValidationRules.validateColor("4CAF50")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.COLOR_INVALID_FORMAT), missingHash)

        val invalidChars = CategoryValidationRules.validateColor("#4CAF5Z")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.COLOR_INVALID_FORMAT), invalidChars)

        val shortHex = CategoryValidationRules.validateColor("#FFF")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.COLOR_INVALID_FORMAT), shortHex)

        val longHex = CategoryValidationRules.validateColor("#FF00FF00")
        assertEquals(CategoryValidationResult.Invalid(CategoryValidationError.COLOR_INVALID_FORMAT), longHex)
    }
}
