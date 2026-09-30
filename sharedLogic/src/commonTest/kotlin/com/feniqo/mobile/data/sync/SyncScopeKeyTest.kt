package com.feniqo.mobile.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncScopeKeyTest {

    private val validUuid = "11111111-1111-4111-8111-111111111111"
    private val compactUuid = "11111111111141118111111111111111"

    // 1. canonical UUID kullanıcı scope’u kabul edilir
    @Test
    fun canonical_uuid_user_scope_is_accepted() {
        val scope = SyncScopeKey.user(validUuid)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", scope.rawValue)
        assertEquals(validUuid, scope.userId)
        assertTrue(SyncScopeKey.isUserScope(scope.rawValue))
        assertFalse(SyncScopeKey.isLegacyUnresolved(scope.rawValue))
    }

    // 2. compact UUID canonical biçime dönüştürülür
    @Test
    fun compact_uuid_is_converted_to_canonical_user_scope() {
        val scope = SyncScopeKey.user(compactUuid)
        assertEquals("USER:11111111-1111-4111-8111-111111111111", scope.rawValue)
        assertEquals(validUuid, scope.userId)
    }

    // 3. user-1 reddedilir
    @Test
    fun user_fails_on_non_uuid_string() {
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.user("user-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.user("")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.user("   ")
        }
    }

    // 4. USER:user-1 parse sırasında reddedilir
    @Test
    fun parse_fails_on_non_uuid_user_scope() {
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("USER:user-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("USER:not-a-uuid")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("USER:")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("USER:   ")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("WORKSPACE:ws-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.parse("")
        }
    }

    // 5. LEGACY_UNRESOLVED, genel persistence parse işleminde korunur
    @Test
    fun legacy_unresolved_is_preserved_in_general_parse() {
        val parsed = SyncScopeKey.parse("LEGACY_UNRESOLVED")
        assertEquals(SyncScopeKey.LegacyUnresolved, parsed)
        assertEquals("LEGACY_UNRESOLVED", parsed.rawValue)
        assertTrue(SyncScopeKey.isLegacyUnresolved(parsed.rawValue))
        assertFalse(SyncScopeKey.isUserScope(parsed.rawValue))

        val required = SyncScopeKey.requireValid("LEGACY_UNRESOLVED")
        assertEquals(SyncScopeKey.LegacyUnresolved, required)
    }

    // 6. requireUserScope("LEGACY_UNRESOLVED") reddeder
    @Test
    fun requireUserScope_rejects_legacy_unresolved() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.requireUserScope("LEGACY_UNRESOLVED")
        }
        assertTrue(ex.message!!.contains("Expected USER scope"))
    }

    // 7. requireUserScope geçerli canonical USER scope’unu kabul eder, compact veya malformed reddeder
    @Test
    fun requireUserScope_accepts_canonical_user_scope() {
        val user = SyncScopeKey.requireUserScope("USER:$validUuid")
        assertEquals(validUuid, user.userId)
        assertEquals("USER:$validUuid", user.rawValue)

        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.requireUserScope("USER:user-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.requireUserScope("WORKSPACE:ws-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.requireUserScope("")
        }
    }

    @Test
    fun requireUserScope_rejects_compact_user_scope() {
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.requireUserScope("USER:$compactUuid")
        }
    }

    @Test
    fun parse_compact_user_scope_converts_to_canonical_user_for_persistence_or_migration() {
        val parsed = SyncScopeKey.parse("USER:$compactUuid")
        assertTrue(parsed is SyncScopeKey.User)
        assertEquals(validUuid, parsed.userId)
        assertEquals("USER:$validUuid", parsed.rawValue)
    }

    // 8. doğrudan User oluşturma yolu geçersiz UUID invariant’ını aşamaz
    @Test
    fun direct_user_constructor_enforces_canonical_uuid_invariant() {
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.User("user-1")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.User("11111111111141118111111111111111")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.User("11111111-1111-4111-8111-11111111111G")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncScopeKey.User("11111111-1111-4111-8111-111111111111 ")
        }

        val validUser = SyncScopeKey.User(validUuid)
        assertEquals(validUuid, validUser.userId)
    }

    @Test
    fun parseOrNull_handles_null_and_invalid() {
        assertNull(SyncScopeKey.parseOrNull(null))
        assertNull(SyncScopeKey.parseOrNull("invalid"))
        assertNull(SyncScopeKey.parseOrNull("USER:user-1"))
        val parsed = SyncScopeKey.parseOrNull("USER:$validUuid")
        assertEquals(SyncScopeKey.User(validUuid), parsed)
    }
}
