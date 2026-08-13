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
    fun supports_legacy_member_role_during_migration() {
        val member = WorkspaceMemberDto(
            workspaceId = "workspace-1",
            userId = "user-1",
            role = "member",
            createdAt = "2026-08-13T10:00:00Z",
        ).toDomain()

        assertEquals(WorkspaceRole.EDITOR, member.role)
        assertEquals("editor", member.toDto().role)
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

        kotlin.test.assertTrue("\"full_name\"" in encoded)
        kotlin.test.assertTrue("\"created_at\"" in encoded)
        kotlin.test.assertTrue("fullName" !in encoded)
    }

    @Test
    fun rejects_unknown_role_instead_of_silently_granting_access() {
        assertFailsWith<RemoteMappingException> {
            WorkspaceMemberDto(
                workspaceId = "workspace-1",
                userId = "user-1",
                role = "admin",
                createdAt = "2026-08-13T10:00:00Z",
            ).toDomain()
        }
    }

    @Test
    fun rejects_workspace_without_an_owner_at_domain_boundary() {
        assertFailsWith<RemoteMappingException> {
            WorkspaceDto(
                id = "workspace-1",
                name = "Feniqo",
                createdBy = null,
                createdAt = "2026-08-13T10:00:00Z",
            ).toDomain()
        }
    }
}
