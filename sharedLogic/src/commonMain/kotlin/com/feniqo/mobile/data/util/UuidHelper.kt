package com.feniqo.mobile.data.util

enum class UuidFormat {
    CANONICAL,
    LEGACY_COMPACT_HEX,
    INVALID,
}

object UuidHelper {
    private val CANONICAL_LOWER_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val COMPACT_HEX_REGEX = Regex("^[0-9a-fA-F]{32}$")

    fun classify(value: String): UuidFormat = when {
        value.length == 36 && CANONICAL_LOWER_REGEX.matches(value) -> UuidFormat.CANONICAL
        value.length == 32 && COMPACT_HEX_REGEX.matches(value) -> UuidFormat.LEGACY_COMPACT_HEX
        else -> UuidFormat.INVALID
    }

    fun isCanonicalUuid(value: String): Boolean =
        value.length == 36 && CANONICAL_LOWER_REGEX.matches(value)

    fun isLegacyCompactHex(value: String): Boolean =
        value.length == 32 && COMPACT_HEX_REGEX.matches(value)

    fun compactToCanonicalUuid(compactHex: String): String {
        require(isLegacyCompactHex(compactHex)) { "Beklenen 32-hex legacy UUID, gelen: $compactHex" }
        val lower = compactHex.lowercase()
        return buildString(36) {
            append(lower, 0, 8); append('-')
            append(lower, 8, 12); append('-')
            append(lower, 12, 16); append('-')
            append(lower, 16, 20); append('-')
            append(lower, 20, 32)
        }
    }

    fun canonicalize(value: String): String = when (classify(value)) {
        UuidFormat.CANONICAL -> value
        UuidFormat.LEGACY_COMPACT_HEX -> compactToCanonicalUuid(value)
        UuidFormat.INVALID -> throw IllegalArgumentException("Geçersiz UUID formatı: $value")
    }

    fun toCanonicalOrNull(value: String?): String? = when {
        value == null -> null
        isCanonicalUuid(value) -> value
        isLegacyCompactHex(value) -> compactToCanonicalUuid(value)
        else -> null
    }
}
