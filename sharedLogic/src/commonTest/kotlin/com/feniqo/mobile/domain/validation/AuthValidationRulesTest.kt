package com.feniqo.mobile.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthValidationRulesTest {

    // --- E-posta Doğrulama Testleri ---

    @Test
    fun validateEmail_emptyOrBlank_returnsEmailRequired() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_REQUIRED),
            AuthValidationRules.validateEmail(""),
        )
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_REQUIRED),
            AuthValidationRules.validateEmail("   "),
        )
    }

    @Test
    fun validateEmail_containsWhitespace_returnsEmailInvalid() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user @feniqo.com"),
        )
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@ feniqo.com"),
        )
    }

    @Test
    fun validateEmail_invalidStructure_returnsEmailInvalid() {
        // @ işareti yok
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("userfeniqo.com"),
        )
        // Birden fazla @ işareti
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@@feniqo.com"),
        )
        // @ başta veya sonda
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("@feniqo.com"),
        )
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@"),
        )
        // Domain kısmında nokta yok
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@feniqo"),
        )
        // Nokta domain'in başında veya sonunda
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@.com"),
        )
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID),
            AuthValidationRules.validateEmail("user@feniqo."),
        )
    }

    @Test
    fun validateEmail_validFormats_returnsValid() {
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateEmail("user@feniqo.com"),
        )
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateEmail("  name.surname+tag@sub.domain.org  "),
        )
    }

    @Test
    fun normalizeEmail_trimsLeadingAndTrailingWhitespace() {
        assertEquals("user@feniqo.com", AuthValidationRules.normalizeEmail("  user@feniqo.com \n "))
    }

    // --- Giriş Parolası Testleri ---

    @Test
    fun validateLoginPassword_empty_returnsPasswordRequired() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED),
            AuthValidationRules.validateLoginPassword(""),
        )
    }

    @Test
    fun validateLoginPassword_nonEmpty_returnsValidEvenForShortPassword() {
        // Giriş ekranında mevcut geçerli kısa parolalar engellenmez
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateLoginPassword("123"),
        )
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateLoginPassword("secretPass"),
        )
    }

    // --- Yeni Kayıt Parolası Testleri ---

    @Test
    fun validateNewPassword_empty_returnsPasswordRequired() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED),
            AuthValidationRules.validateNewPassword(""),
        )
    }

    @Test
    fun validateNewPassword_shorterThanSixCharacters_returnsNewPasswordTooShort() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.NEW_PASSWORD_TOO_SHORT),
            AuthValidationRules.validateNewPassword("12345"),
        )
    }

    @Test
    fun validateNewPassword_sixCharactersOrMore_returnsValid() {
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateNewPassword("123456"),
        )
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateNewPassword("SuperSecurePassword"),
        )
    }

    // --- Parola Tekrarı Testleri ---

    @Test
    fun validateConfirmPassword_empty_returnsPasswordRequired() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED),
            AuthValidationRules.validateConfirmPassword("123456", ""),
        )
    }

    @Test
    fun validateConfirmPassword_mismatch_returnsPasswordsDoNotMatch() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.PASSWORDS_DO_NOT_MATCH),
            AuthValidationRules.validateConfirmPassword("123456", "123457"),
        )
    }

    @Test
    fun validateConfirmPassword_exactMatch_returnsValid() {
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateConfirmPassword("123456", "123456"),
        )
    }

    // --- Ad Soyad Testleri ---

    @Test
    fun validateFullName_nullOrBlank_returnsValid() {
        assertEquals(AuthValidationResult.Valid, AuthValidationRules.validateFullName(null))
        assertEquals(AuthValidationResult.Valid, AuthValidationRules.validateFullName(""))
        assertEquals(AuthValidationResult.Valid, AuthValidationRules.validateFullName("   "))
    }

    @Test
    fun validateFullName_shorterThanTwoCharacters_returnsFullNameTooShort() {
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.FULL_NAME_TOO_SHORT),
            AuthValidationRules.validateFullName("A"),
        )
        assertEquals(
            AuthValidationResult.Invalid(AuthValidationError.FULL_NAME_TOO_SHORT),
            AuthValidationRules.validateFullName(" B "),
        )
    }

    @Test
    fun validateFullName_twoCharactersOrMore_returnsValid() {
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateFullName("Su"),
        )
        assertEquals(
            AuthValidationResult.Valid,
            AuthValidationRules.validateFullName("  Ahmet Yılmaz  "),
        )
    }

    @Test
    fun normalizeFullName_returnsTrimmedOrNull() {
        assertNull(AuthValidationRules.normalizeFullName(null))
        assertNull(AuthValidationRules.normalizeFullName(""))
        assertNull(AuthValidationRules.normalizeFullName("   "))
        assertEquals("Ali Veli", AuthValidationRules.normalizeFullName("  Ali Veli  "))
    }
}
