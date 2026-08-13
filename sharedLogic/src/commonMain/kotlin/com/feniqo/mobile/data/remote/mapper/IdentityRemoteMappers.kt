package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import kotlin.time.Instant

fun ProfileDto.toDomain(): UserProfile = UserProfile(
    id = EntityId(id.requireRemoteValue("profiles.id")),
    email = email.trim().requireRemoteValue("profiles.email"),
    fullName = fullName?.trim()?.takeIf(String::isNotEmpty),
    currency = currency.toCurrency(),
    themePreference = theme.toThemePreference(),
    language = lang.toAppLanguage(),
    activeWorkspaceId = activeWorkspaceId?.takeIf(String::isNotBlank)?.let(::EntityId),
    createdAt = createdAt.toRemoteInstant("profiles.created_at"),
)

fun UserProfile.toDto(): ProfileDto = ProfileDto(
    id = id.value,
    email = email.trim(),
    fullName = fullName?.trim()?.takeIf(String::isNotEmpty),
    currency = currency.code,
    theme = themePreference.name.lowercase(),
    lang = language.name.lowercase(),
    activeWorkspaceId = activeWorkspaceId?.value,
    createdAt = createdAt.toString(),
)

fun WorkspaceDto.toDomain(): Workspace = Workspace(
    id = EntityId(id.requireRemoteValue("workspaces.id")),
    name = name.trim().requireRemoteValue("workspaces.name"),
    ownerId = EntityId(createdBy.requireRemoteValue("workspaces.created_by")),
    createdAt = createdAt.toRemoteInstant("workspaces.created_at"),
)

fun Workspace.toDto(): WorkspaceDto = WorkspaceDto(
    id = id.value,
    name = name.trim(),
    createdBy = ownerId.value,
    createdAt = createdAt.toString(),
)

fun WorkspaceMemberDto.toDomain(): WorkspaceMember = WorkspaceMember(
    workspaceId = EntityId(workspaceId.requireRemoteValue("workspace_members.workspace_id")),
    userId = EntityId(userId.requireRemoteValue("workspace_members.user_id")),
    role = role.toWorkspaceRole(),
    joinedAt = createdAt.toRemoteInstant("workspace_members.created_at"),
)

fun WorkspaceMember.toDto(): WorkspaceMemberDto = WorkspaceMemberDto(
    workspaceId = workspaceId.value,
    userId = userId.value,
    role = role.name.lowercase(),
    createdAt = joinedAt.toString(),
)

private fun String.toCurrency(): Currency = Currency.entries.firstOrNull {
    it.code.equals(trim(), ignoreCase = true)
} ?: throw RemoteMappingException("Desteklenmeyen profiles.currency değeri: $this")

private fun String.toThemePreference(): ThemePreference = when (trim().lowercase()) {
    "system" -> ThemePreference.SYSTEM
    "light" -> ThemePreference.LIGHT
    "dark" -> ThemePreference.DARK
    else -> throw RemoteMappingException("Desteklenmeyen profiles.theme değeri: $this")
}

private fun String.toAppLanguage(): AppLanguage = when (trim().lowercase()) {
    "tr" -> AppLanguage.TR
    "en" -> AppLanguage.EN
    else -> throw RemoteMappingException("Desteklenmeyen profiles.lang değeri: $this")
}

private fun String.toWorkspaceRole(): WorkspaceRole = when (trim().lowercase()) {
    "owner" -> WorkspaceRole.OWNER
    "editor", "member" -> WorkspaceRole.EDITOR
    "viewer" -> WorkspaceRole.VIEWER
    else -> throw RemoteMappingException("Desteklenmeyen workspace_members.role değeri: $this")
}

private fun String.toRemoteInstant(field: String): Instant = try {
    Instant.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("$field geçerli bir UTC zaman damgası değil.", error)
}

private fun String?.requireRemoteValue(field: String): String = this
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: throw RemoteMappingException("$field boş olamaz.")

class RemoteMappingException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
