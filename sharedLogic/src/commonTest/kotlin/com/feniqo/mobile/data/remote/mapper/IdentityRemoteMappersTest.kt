package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.WorkspaceRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IdentityRemoteMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodes_profile_snake_case_and_maps_domain_values() {
        val dto = json.decodeFromString<ProfileDto>(
            """{"id":"user-1","email":"user@example.com","full_name":" Feniqo User ","currency":"try","theme":"dark","lang":"tr","created_at":"2026-08-13T10:00:00Z","unknown":"ignored"}""",
        )

        val profile = dto.toDomain()

        assertEquals("Feniqo User", profile.fullName)
        assertEquals(Currency.TRY, profile.currency)
        assertEquals(ThemePreference.DARK, profile.themePreference)
        assertEquals(AppLanguage.TR, profile.language)
        assertEquals("2026-08-13T10:00:00Z", profile.createdAt.toString())
    }

    @Test
    fun encodes_domain_names_as_supabase_snake_case() {
        val dto = ProfileDto(
            id = "user-1",
            email = "user@example.com",
            fullName = "Feniqo User",
            createdAt = "2026-08-13T10:00:00Z",
        )

        val encoded = json.encodeToString(dto)

        assertTrue("\"full_name\"" in encoded)
        assertTrue("\"created_at\"" in encoded)
        assertFalse("fullName" in encoded)
    }

    @Test
    fun decodes_workspace_and_member_d1_schema_and_maps_to_domain_and_entity() {
        val wsJson = """
            {
                "id": "ws-100",
                "name": "  Şirket Ana Çalışma Alanı 🚀  ",
                "normalized_name": "şirket ana çalışma alanı 🚀",
                "owner_id": "user-owner-1",
                "type_code": "shared",
                "currency_code": "USD",
                "description": "  Şirket ortak bütçesi  ",
                "created_at": "2026-09-06T12:00:00Z",
                "updated_at": "2026-09-06T12:30:00Z",
                "deleted_at": "2026-09-06T13:00:00Z",
                "version": 3
            }
        """.trimIndent()

        val wsDto = json.decodeFromString<WorkspaceDto>(wsJson)
        val wsDomain = wsDto.toDomain()

        assertEquals("ws-100", wsDomain.id.value)
        assertEquals("Şirket Ana Çalışma Alanı 🚀", wsDomain.name)
        assertEquals("user-owner-1", wsDomain.ownerId.value)
        assertEquals("2026-09-06T12:00:00Z", wsDomain.createdAt.toString())

        // Room cache Entity dönüşümü doğrulaması
        val receivedAt = 1_788_699_000_000L
        val wsEntity = wsDto.toEntity(receivedAtEpochMillis = receivedAt)

        assertEquals("ws-100", wsEntity.id)
        assertEquals("Şirket Ana Çalışma Alanı 🚀", wsEntity.name)
        assertEquals("şirket ana çalışma alanı 🚀", wsEntity.normalizedName)
        assertEquals("user-owner-1", wsEntity.ownerId)
        assertEquals("shared", wsEntity.typeCode)
        assertEquals("USD", wsEntity.currencyCode)
        assertEquals("Şirket ortak bütçesi", wsEntity.description)
        assertEquals(3L, wsEntity.sync.version)
        assertEquals("SYNCED", wsEntity.sync.syncStatus)
        assertEquals(receivedAt, wsEntity.sync.localUpdatedAtEpochMillis)
        assertEquals(1788697800000L, wsEntity.sync.updatedAtEpochMillis) // 12:30:00Z
        assertEquals(1788699600000L, wsEntity.sync.deletedAtEpochMillis) // 13:00:00Z
        assertNull(wsEntity.sync.baseVersion)

        val memberJson = """
            {
                "workspace_id": "ws-100",
                "user_id": "user-viewer-1",
                "role_code": "VIEWER",
                "joined_at": "2026-09-06T12:05:00Z",
                "updated_at": "2026-09-06T12:10:00Z",
                "deleted_at": null,
                "version": 1
            }
        """.trimIndent()

        val memberDto = json.decodeFromString<WorkspaceMemberDto>(memberJson)
        val memberDomain = memberDto.toDomain()

        assertEquals("ws-100", memberDomain.workspaceId.value)
        assertEquals("user-viewer-1", memberDomain.userId.value)
        assertEquals(WorkspaceRole.VIEWER, memberDomain.role)
        assertEquals("2026-09-06T12:05:00Z", memberDomain.joinedAt.toString())

        // Member Room cache Entity dönüşümü doğrulaması
        val memberEntity = memberDto.toEntity(receivedAtEpochMillis = receivedAt)
        assertEquals("ws-100", memberEntity.workspaceId)
        assertEquals("user-viewer-1", memberEntity.userId)
        assertEquals("VIEWER", memberEntity.roleCode)
        assertEquals(1L, memberEntity.sync.version)
        assertEquals("SYNCED", memberEntity.sync.syncStatus)
        assertNull(memberEntity.sync.deletedAtEpochMillis)
    }

    @Test
    fun rejects_legacy_member_and_unknown_roles_fail_closed() {
        // D1 sözleşmesinde MEMBER geçersizdir; yalnız OWNER/EDITOR/VIEWER geçerlidir
        assertFailsWith<RemoteMappingException> {
            WorkspaceMemberDto(
                workspaceId = "workspace-1",
                userId = "user-1",
                roleCode = "MEMBER",
                joinedAt = "2026-08-13T10:00:00Z",
                updatedAt = "2026-08-13T10:00:00Z",
                version = 1L,
            ).toDomain()
        }

        assertFailsWith<RemoteMappingException> {
            WorkspaceMemberDto(
                workspaceId = "workspace-1",
                userId = "user-1",
                roleCode = "ADMIN",
                joinedAt = "2026-08-13T10:00:00Z",
                updatedAt = "2026-08-13T10:00:00Z",
                version = 1L,
            ).toDomain()
        }
    }

    @Test
    fun rejects_missing_or_invalid_required_fields_in_workspace_and_member() {
        val validWsJson = """
            {
                "id": "ws-1",
                "name": "Test Workspace",
                "normalized_name": "test workspace",
                "owner_id": "user-1",
                "type_code": "personal",
                "currency_code": "TRY",
                "created_at": "2026-09-06T12:00:00Z",
                "updated_at": "2026-09-06T12:00:00Z",
                "version": 1
            }
        """.trimIndent()

        // Eksik normalized_name decode hatası
        assertFailsWith<Exception> {
            json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"normalized_name\": \"test workspace\",", ""))
        }

        // Eksik updated_at decode hatası
        assertFailsWith<Exception> {
            json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"updated_at\": \"2026-09-06T12:00:00Z\",", ""))
        }

        // Eksik version decode hatası
        assertFailsWith<Exception> {
            json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"version\": 1", ""))
        }

        // Geçersiz type_code toEntity hatası
        val invalidTypeDto = json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"personal\"", "\"unsupported_type\""))
        assertFailsWith<RemoteMappingException> {
            invalidTypeDto.toEntity(1_000L)
        }

        // Geçersiz currency_code toEntity hatası
        val invalidCurrencyDto = json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"TRY\"", "\"GBP\""))
        assertFailsWith<RemoteMappingException> {
            invalidCurrencyDto.toEntity(1_000L)
        }

        // Sıfır/negatif version toEntity hatası
        val zeroVersionWsDto = json.decodeFromString<WorkspaceDto>(validWsJson.replace("\"version\": 1", "\"version\": 0"))
        assertFailsWith<RemoteMappingException> {
            zeroVersionWsDto.toEntity(1_000L)
        }

        val validMemberJson = """
            {
                "workspace_id": "ws-1",
                "user_id": "user-1",
                "role_code": "OWNER",
                "joined_at": "2026-09-06T12:00:00Z",
                "updated_at": "2026-09-06T12:00:00Z",
                "version": 1
            }
        """.trimIndent()

        // Eksik updated_at decode hatası
        assertFailsWith<Exception> {
            json.decodeFromString<WorkspaceMemberDto>(validMemberJson.replace("\"updated_at\": \"2026-09-06T12:00:00Z\",", ""))
        }

        // Eksik version decode hatası
        assertFailsWith<Exception> {
            json.decodeFromString<WorkspaceMemberDto>(validMemberJson.replace("\"version\": 1", ""))
        }

        // Sıfır/negatif version toEntity hatası
        val zeroVersionMemberDto = json.decodeFromString<WorkspaceMemberDto>(validMemberJson.replace("\"version\": 1", "\"version\": 0"))
        assertFailsWith<RemoteMappingException> {
            zeroVersionMemberDto.toEntity(1_000L)
        }
    }

    @Test
    fun rejects_blank_or_invalid_datetime_in_workspace_and_member() {
        assertFailsWith<RemoteMappingException> {
            WorkspaceDto(
                id = "ws-1",
                name = "   ",
                normalizedName = "   ",
                ownerId = "user-1",
                typeCode = "personal",
                currencyCode = "TRY",
                createdAt = "2026-09-06T12:00:00Z",
                updatedAt = "2026-09-06T12:00:00Z",
                version = 1L,
            ).toDomain()
        }

        assertFailsWith<RemoteMappingException> {
            WorkspaceMemberDto(
                workspaceId = "ws-1",
                userId = "user-1",
                roleCode = "OWNER",
                joinedAt = "INVALID_TIMESTAMP",
                updatedAt = "2026-09-06T12:00:00Z",
                version = 1L,
            ).toDomain()
        }
    }
}



