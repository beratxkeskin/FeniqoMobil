package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceMemberSyncCursor
import kotlin.time.Instant

/**
 * Workspace ve WorkspaceMember senkronizasyon imleçleri (cursor) için merkezi anahtar ve dönüşüm yardımcıları.
 *
 * Biçimler:
 * - Workspace: entity_type_code = "WORKSPACE", entity_id = workspace.id, updated_at_epoch_ms
 * - WorkspaceMember: entity_type_code = "WORKSPACE_MEMBER:<workspaceId>", entity_id = member.userId, updated_at_epoch_ms
 */
object WorkspaceSyncCursorKeys {
    const val WORKSPACE_ENTITY_TYPE = "WORKSPACE"
    const val WORKSPACE_BOOTSTRAP_COMPLETE = "WORKSPACE_BOOTSTRAP_COMPLETE"
    const val WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID = "COMPLETED"
    const val WORKSPACE_MEMBER_PREFIX = "WORKSPACE_MEMBER:"

    fun bootstrapCompleteEntity(nowEpochMillis: Long): SyncCursorEntity = SyncCursorEntity(
        entityTypeCode = WORKSPACE_BOOTSTRAP_COMPLETE,
        updatedAtEpochMillis = nowEpochMillis,
        entityId = WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID,
    )

    fun isBootstrapCompleteMarker(entity: SyncCursorEntity): Boolean =
        entity.entityTypeCode == WORKSPACE_BOOTSTRAP_COMPLETE &&
            entity.entityId == WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID &&
            entity.updatedAtEpochMillis > 0L

    fun workspaceMemberEntityType(workspaceId: String): String {
        require(workspaceId.isNotBlank()) { "workspaceId boş olamaz." }
        return "$WORKSPACE_MEMBER_PREFIX$workspaceId"
    }

    fun parseWorkspaceIdFromMemberEntityType(entityTypeCode: String): String? {
        if (!entityTypeCode.startsWith(WORKSPACE_MEMBER_PREFIX)) return null
        val wsId = entityTypeCode.removePrefix(WORKSPACE_MEMBER_PREFIX).trim()
        return wsId.takeIf { it.isNotEmpty() }
    }

    fun workspaceCursorToEntity(cursor: RemoteSyncCursor): SyncCursorEntity {
        require(cursor.entityId != WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID) {
            "Gerçek workspace cursor ID'si marker sabiti ile eşleşemez."
        }
        return SyncCursorEntity(
            entityTypeCode = WORKSPACE_ENTITY_TYPE,
            updatedAtEpochMillis = Instant.parse(cursor.updatedAt).toEpochMilliseconds(),
            entityId = cursor.entityId,
        )
    }

    fun workspaceEntityToCursor(entity: SyncCursorEntity): RemoteSyncCursor {
        require(entity.entityTypeCode == WORKSPACE_ENTITY_TYPE) {
            "Geçersiz workspace entity type code: ${entity.entityTypeCode}"
        }
        require(entity.entityId != WORKSPACE_BOOTSTRAP_COMPLETED_ENTITY_ID) {
            "Bootstrap marker entity RemoteSyncCursor'a dönüştürülemez."
        }
        return RemoteSyncCursor(
            updatedAt = Instant.fromEpochMilliseconds(entity.updatedAtEpochMillis).toString(),
            entityId = entity.entityId,
        )
    }

    fun workspaceMemberCursorToEntity(cursor: WorkspaceMemberSyncCursor): SyncCursorEntity = SyncCursorEntity(
        entityTypeCode = workspaceMemberEntityType(cursor.workspaceId),
        updatedAtEpochMillis = Instant.parse(cursor.updatedAt).toEpochMilliseconds(),
        entityId = cursor.userId,
    )

    fun workspaceMemberEntityToCursor(entity: SyncCursorEntity): WorkspaceMemberSyncCursor? {
        val wsId = parseWorkspaceIdFromMemberEntityType(entity.entityTypeCode) ?: return null
        return WorkspaceMemberSyncCursor(
            updatedAt = Instant.fromEpochMilliseconds(entity.updatedAtEpochMillis).toString(),
            workspaceId = wsId,
            userId = entity.entityId,
        )
    }
}
