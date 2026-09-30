package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.util.UuidHelper

/**
 * Senkronizasyon işlemlerinin, imleçlerinin ve çakışmalarının ait olduğu kullanıcı veya karantina kapsamını temsil eder.
 *
 * Normal biçim: `USER:<canonical_uuid>`
 * Karantina: `LEGACY_UNRESOLVED`
 */
sealed interface SyncScopeKey {
    val rawValue: String

    data class User(val userId: String) : SyncScopeKey {
        init {
            require(UuidHelper.isCanonicalUuid(userId)) {
                "User sync scope requires canonical lowercase UUID, but was: '$userId'"
            }
        }

        override val rawValue: String get() = "$PREFIX$userId"

        override fun toString(): String = rawValue
    }

    data object LegacyUnresolved : SyncScopeKey {
        override val rawValue: String get() = UNRESOLVED_RAW

        override fun toString(): String = rawValue
    }

    companion object {
        const val PREFIX = "USER:"
        const val UNRESOLVED_RAW = "LEGACY_UNRESOLVED"

        fun user(userId: String): User {
            val canonical = UuidHelper.toCanonicalOrNull(userId)
                ?: throw IllegalArgumentException("Invalid user UUID for sync scope: '$userId'")
            return User(canonical)
        }

        fun forUser(userId: String): String = user(userId).rawValue

        fun parse(rawValue: String): SyncScopeKey {
            if (rawValue == UNRESOLVED_RAW) {
                return LegacyUnresolved
            }
            if (rawValue.startsWith(PREFIX)) {
                val candidate = rawValue.substring(PREFIX.length)
                val canonical = UuidHelper.toCanonicalOrNull(candidate)
                    ?: throw IllegalArgumentException("Invalid user UUID in sync_scope_key: '$rawValue'")
                return User(canonical)
            }
            throw IllegalArgumentException("Invalid sync_scope_key: '$rawValue'")
        }

        fun requireValid(rawValue: String): SyncScopeKey = parse(rawValue)

        fun requireUserScope(rawValue: String): User {
            val parsed = parse(rawValue)
            if (parsed is User) {
                require(parsed.rawValue == rawValue) {
                    "Runtime sync scope key must be strictly canonical, but was: '$rawValue'"
                }
                return parsed
            }
            throw IllegalArgumentException("Expected USER scope but was: '$rawValue'")
        }

        fun parseOrNull(rawValue: String?): SyncScopeKey? {
            if (rawValue == null) return null
            return runCatching { parse(rawValue) }.getOrNull()
        }

        fun isUserScope(rawValue: String): Boolean =
            rawValue.startsWith(PREFIX) && UuidHelper.isCanonicalUuid(rawValue.substring(PREFIX.length))

        fun isLegacyUnresolved(rawValue: String): Boolean =
            rawValue == UNRESOLVED_RAW
    }
}
