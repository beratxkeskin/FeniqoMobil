package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CategoryColor

sealed interface CategoryValidationResult<out T> {
    data class Valid<T>(val value: T) : CategoryValidationResult<T>
    data class Invalid(val error: CategoryValidationError) : CategoryValidationResult<Nothing>
}

enum class CategoryValidationError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    COLOR_INVALID_FORMAT,
}

object CategoryValidationRules {

    const val MAX_NAME_LENGTH: Int = 50

    private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

    fun normalizeName(name: String): String = name.trim().lowercase()

    fun validateName(name: String): CategoryValidationResult<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return CategoryValidationResult.Invalid(CategoryValidationError.NAME_EMPTY)
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return CategoryValidationResult.Invalid(CategoryValidationError.NAME_TOO_LONG)
        }
        return CategoryValidationResult.Valid(trimmed)
    }

    fun validateColor(hex: String): CategoryValidationResult<CategoryColor> {
        val trimmed = hex.trim()
        if (!HEX_COLOR_REGEX.matches(trimmed)) {
            return CategoryValidationResult.Invalid(CategoryValidationError.COLOR_INVALID_FORMAT)
        }
        return CategoryValidationResult.Valid(CategoryColor(trimmed))
    }
}
