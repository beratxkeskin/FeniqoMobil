package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
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
    ownerId = EntityId(ownerId.requireRemoteValue("workspaces.owner_id")),
    createdAt = createdAt.toRemoteInstant("workspaces.created_at"),
)

fun WorkspaceDto.toEntity(receivedAtEpochMillis: Long): WorkspaceEntity {
    val cleanName = name.trim().requireRemoteValue("workspaces.name")
    val cleanNormalizedName = normalizedName.trim().requireRemoteValue("workspaces.normalized_name")
    val cleanTypeCode = typeCode.trim().requireRemoteValue("workspaces.type_code")
    if (cleanTypeCode !in setOf("personal", "shared")) {
        throw RemoteMappingException("Desteklenmeyen workspaces.type_code değeri: $typeCode")
    }
    val cleanCurrencyCode = currencyCode.trim().requireRemoteValue("workspaces.currency_code")
    if (cleanCurrencyCode !in setOf("TRY", "USD", "EUR")) {
        throw RemoteMappingException("Desteklenmeyen workspaces.currency_code değeri: $currencyCode")
    }

    if (version <= 0L) {
        throw RemoteMappingException("workspaces.version pozitif bir tamsayı olmalıdır: $version")
    }

    val createdAtInstant = createdAt.toRemoteInstant("workspaces.created_at")
    val updatedAtInstant = updatedAt.toRemoteInstant("workspaces.updated_at")
    val deletedAtInstant = deletedAt?.toRemoteInstant("workspaces.deleted_at")

    return WorkspaceEntity(
        id = id.requireRemoteValue("workspaces.id"),
        name = cleanName,
        normalizedName = cleanNormalizedName,
        ownerId = ownerId.requireRemoteValue("workspaces.owner_id"),
        typeCode = cleanTypeCode,
        currencyCode = cleanCurrencyCode,
        description = description?.trim()?.takeIf(String::isNotEmpty),
        createdAtEpochMillis = createdAtInstant.toEpochMilliseconds(),
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = updatedAtInstant.toEpochMilliseconds(),
            localUpdatedAtEpochMillis = receivedAtEpochMillis,
            deletedAtEpochMillis = deletedAtInstant?.toEpochMilliseconds(),
            version = version,
            baseVersion = null,
            lastSyncError = null,
        ),
    )
}

fun WorkspaceMemberDto.toDomain(): WorkspaceMember = WorkspaceMember(
    workspaceId = EntityId(workspaceId.requireRemoteValue("workspace_members.workspace_id")),
    userId = EntityId(userId.requireRemoteValue("workspace_members.user_id")),
    role = roleCode.toWorkspaceRole(),
    joinedAt = joinedAt.toRemoteInstant("workspace_members.joined_at"),
)

fun WorkspaceMemberDto.toEntity(receivedAtEpochMillis: Long): WorkspaceMemberEntity {
    val cleanRoleCode = roleCode.toWorkspaceRole().name
    val joinedAtInstant = joinedAt.toRemoteInstant("workspace_members.joined_at")
    val updatedAtInstant = updatedAt.toRemoteInstant("workspace_members.updated_at")
    val deletedAtInstant = deletedAt?.toRemoteInstant("workspace_members.deleted_at")

    if (version <= 0L) {
        throw RemoteMappingException("workspace_members.version pozitif bir tamsayı olmalıdır: $version")
    }

    return WorkspaceMemberEntity(
        workspaceId = workspaceId.requireRemoteValue("workspace_members.workspace_id"),
        userId = userId.requireRemoteValue("workspace_members.user_id"),
        roleCode = cleanRoleCode,
        joinedAtEpochMillis = joinedAtInstant.toEpochMilliseconds(),
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = updatedAtInstant.toEpochMilliseconds(),
            localUpdatedAtEpochMillis = receivedAtEpochMillis,
            deletedAtEpochMillis = deletedAtInstant?.toEpochMilliseconds(),
            version = version,
            baseVersion = null,
            lastSyncError = null,
        ),
    )
}

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

private fun String.toWorkspaceRole(): WorkspaceRole = when (trim().uppercase()) {
    "OWNER" -> WorkspaceRole.OWNER
    "EDITOR" -> WorkspaceRole.EDITOR
    "VIEWER" -> WorkspaceRole.VIEWER
    else -> throw RemoteMappingException("Desteklenmeyen workspace_members.role_code değeri: $this")
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

