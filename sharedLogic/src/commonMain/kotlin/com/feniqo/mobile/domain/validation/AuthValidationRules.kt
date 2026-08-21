package com.feniqo.mobile.domain.validation

/**
 * Kimlik doğrulama form alanlarına ait tipli hata kodlarıdır.
 */
enum class AuthValidationError {
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    NEW_PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
    FULL_NAME_TOO_SHORT,
}

/**
 * Form doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface AuthValidationResult {
    data object Valid : AuthValidationResult
    data class Invalid(val error: AuthValidationError) : AuthValidationResult
}

/**
 * Platformdan bağımsız, saf kimlik doğrulama kuralları ve normalizasyon yardımcıları.
 */
object AuthValidationRules {
    /**
     * Feniqo V1 ürün politikası gereği yeni hesap parolası en az 6 karakter olmalıdır.
     */
    const val MIN_PASSWORD_LENGTH = 6

    /**
     * Girilen ad-soyad alanı için minimum karakter sınırı.
     */
    const val MIN_FULL_NAME_LENGTH = 2

    /**
     * E-posta doğrulama kuralı.
     * Pragmatik kontroller:
     * - trim sonrası boş olamaz (EMAIL_REQUIRED)
     * - boşluk/whitespace içeremez (EMAIL_INVALID)
     * - tam olarak tek bir '@' karakteri içermeli (EMAIL_INVALID)
     * - '@' öncesi ve sonrası boş olamaz (EMAIL_INVALID)
     * - domain bölümünde en az bir '.' karakteri içermeli ve nokta ne domain'in başında ne de sonunda olmalı (EMAIL_INVALID)
     */
    fun validateEmail(email: String): AuthValidationResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return AuthValidationResult.Invalid(AuthValidationError.EMAIL_REQUIRED)
        if (trimmed.any { it.isWhitespace() }) return AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID)

        val atIndex = trimmed.indexOf('@')
        val lastAtIndex = trimmed.lastIndexOf('@')
        if (atIndex <= 0 || atIndex != lastAtIndex || atIndex == trimmed.length - 1) {
            return AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID)
        }

        val domain = trimmed.substring(atIndex + 1)
        val dotIndex = domain.indexOf('.')
        val lastDotIndex = domain.lastIndexOf('.')
        if (dotIndex <= 0 || lastDotIndex == domain.length - 1) {
            return AuthValidationResult.Invalid(AuthValidationError.EMAIL_INVALID)
        }

        return AuthValidationResult.Valid
    }

    /**
     * Giriş parolası doğrulama kuralı.
     * İstemci tarafında yalnızca boş olmaması kontrol edilir; mevcut kullanıcıların geçerli kısa parolalarını engellemez.
     */
    fun validateLoginPassword(password: String): AuthValidationResult {
        if (password.isEmpty()) return AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED)
        return AuthValidationResult.Valid
    }

    /**
     * Yeni kayıt parolası doğrulama kuralı.
     * Feniqo V1 ürün politikası gereği en az 6 karakter olmalıdır.
     */
    fun validateNewPassword(password: String): AuthValidationResult {
        if (password.isEmpty()) return AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED)
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthValidationResult.Invalid(AuthValidationError.NEW_PASSWORD_TOO_SHORT)
        }
        return AuthValidationResult.Valid
    }

    /**
     * Parola tekrarı doğrulama kuralı.
     * Yeni kayıt parolası ile birebir aynı olmalıdır.
     */
    fun validateConfirmPassword(password: String, confirmPassword: String): AuthValidationResult {
        if (confirmPassword.isEmpty()) return AuthValidationResult.Invalid(AuthValidationError.PASSWORD_REQUIRED)
        if (password != confirmPassword) {
            return AuthValidationResult.Invalid(AuthValidationError.PASSWORDS_DO_NOT_MATCH)
        }
        return AuthValidationResult.Valid
    }

    /**
     * Ad Soyad doğrulama kuralı.
     * Opsiyoneldir; null veya trim sonrası boşsa geçerlidir. Dolu girildiyse en az 2 karakter olmalıdır.
     */
    fun validateFullName(fullName: String?): AuthValidationResult {
        val normalized = normalizeFullName(fullName) ?: return AuthValidationResult.Valid
        if (normalized.length < MIN_FULL_NAME_LENGTH) {
            return AuthValidationResult.Invalid(AuthValidationError.FULL_NAME_TOO_SHORT)
        }
        return AuthValidationResult.Valid
    }

    /**
     * E-posta adresini trim ederek normalize eder.
     */
    fun normalizeEmail(email: String): String = email.trim()

    /**
     * Ad soyad değerini normalize eder. Boşsa null döner.
     */
    fun normalizeFullName(fullName: String?): String? =
        fullName?.trim()?.takeIf { it.isNotEmpty() }
}
