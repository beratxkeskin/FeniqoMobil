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

    /**
     * Kullanıcı kimliği ve kanonik alias için deterministik RFC 9562 UUIDv8 kimliği üretir.
     * UTF-8 girdi: "feniqo:user-merchant-verification:v1\u0000" + canonicalUserId + "\u0000" + normalizedAlias
     */
    fun deterministicMerchantVerificationUuid(userId: String, normalizedAlias: String): String {
        require(isCanonicalUuid(userId)) {
            "Kullanıcı kimliği kanonik küçük harf UUID biçiminde olmalıdır: $userId"
        }
        require(normalizedAlias.isNotBlank()) {
            "Kanonik alias boş olamaz."
        }
        require(normalizedAlias.length <= com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH) {
            "Kanonik alias en fazla ${com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH} karakter olabilir: $normalizedAlias"
        }
        val canonicalAlias = com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer.normalize(normalizedAlias)
        require(normalizedAlias == canonicalAlias) {
            "Alias kanonik normalizasyon biçiminde olmalıdır (beklenen: '$canonicalAlias', gelen: '$normalizedAlias')."
        }
        require(normalizedAlias !in com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Genel token tek başına merchant alias olamaz: $normalizedAlias"
        }

        val utf8Bytes = buildString {
            append("feniqo:user-merchant-verification:v1")
            append('\u0000')
            append(userId)
            append('\u0000')
            append(normalizedAlias)
        }.encodeToByteArray()

        val digest = FeniqoDigest.sha256(utf8Bytes)
        val raw16 = digest.copyOfRange(0, 16)

        // RFC 9562 UUIDv8: Octet 6 version = 8 (0b1000xxxx)
        raw16[6] = ((raw16[6].toInt() and 0x0F) or 0x80).toByte()
        // RFC 9562 UUIDv8: Octet 8 variant = 1 (0b10xxxxxx)
        raw16[8] = ((raw16[8].toInt() and 0x3F) or 0x80).toByte()

        return buildString(36) {
            for (i in 0 until 16) {
                if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                val v = raw16[i].toInt() and 0xFF
                append(HEX_DIGITS[v ushr 4])
                append(HEX_DIGITS[v and 0x0F])
            }
        }
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
