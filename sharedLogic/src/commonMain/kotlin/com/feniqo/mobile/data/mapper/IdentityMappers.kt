package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import kotlinx.datetime.Instant

fun UserProfile.toEntity(sync: SyncMetadata): UserProfileEntity = UserProfileEntity(
    id = id.value,
    email = email,
    fullName = fullName,
    currencyCode = currency.code,
    themeCode = themePreference.name,
    languageCode = language.name,
    activeWorkspaceId = activeWorkspaceId?.value,
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    id = EntityId(id),
    email = email,
    fullName = fullName,
    currency = Currency.valueOf(currencyCode),
    themePreference = ThemePreference.valueOf(themeCode),
    language = AppLanguage.valueOf(languageCode),
    activeWorkspaceId = activeWorkspaceId?.let(::EntityId),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

fun Workspace.toEntity(sync: SyncMetadata): WorkspaceEntity = WorkspaceEntity(
    id = id.value,
    name = name.trim(),
    normalizedName = name.normalizeForStorage(),
    ownerId = ownerId.value,
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = EntityId(id),
    name = name,
    ownerId = EntityId(ownerId),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

fun WorkspaceMember.toEntity(sync: SyncMetadata): WorkspaceMemberEntity = WorkspaceMemberEntity(
    workspaceId = workspaceId.value,
    userId = userId.value,
    roleCode = role.name,
    joinedAtEpochMillis = joinedAt.toEpochMilliseconds(),
    sync = sync,
)

fun WorkspaceMemberEntity.toDomain(): WorkspaceMember = WorkspaceMember(
    workspaceId = EntityId(workspaceId),
    userId = EntityId(userId),
    role = WorkspaceRole.valueOf(roleCode),
    joinedAt = Instant.fromEpochMilliseconds(joinedAtEpochMillis),
)

internal fun String.normalizeForStorage(): String = trim().lowercase()

internal fun scopeKey(ownerId: EntityId?, workspaceId: EntityId?): String = when {
    workspaceId != null -> "workspace:${workspaceId.value}"
    ownerId != null -> "user:${ownerId.value}"
    else -> "system"
}
