package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)

    @Query(
        """
        UPDATE profiles
        SET active_workspace_id = :workspaceId
        WHERE id = :profileId
          AND deleted_at_epoch_ms IS NULL
          AND EXISTS (
              SELECT 1 FROM workspaces w
              INNER JOIN workspace_members m ON m.workspace_id = w.id
              WHERE w.id = :workspaceId
                AND w.deleted_at_epoch_ms IS NULL
                AND m.user_id = :profileId
                AND m.deleted_at_epoch_ms IS NULL
          )
        """,
    )
    suspend fun setActiveWorkspaceGuarded(profileId: String, workspaceId: String): Int

    @Query(
        """
        UPDATE profiles
        SET active_workspace_id = NULL
        WHERE id = :profileId
          AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun clearActiveWorkspace(profileId: String): Int
}

@Dao
interface WorkspaceDao {
    @Query(
        """
        SELECT DISTINCT w.* FROM workspaces w
        INNER JOIN workspace_members m ON m.workspace_id = w.id
        WHERE m.user_id = :userId
          AND m.deleted_at_epoch_ms IS NULL
          AND w.deleted_at_epoch_ms IS NULL
        ORDER BY w.normalized_name
        """,
    )
    fun observeForUser(userId: String): Flow<List<WorkspaceEntity>>

    @Query(
        """
        SELECT w.* FROM workspaces w
        INNER JOIN profiles p ON p.active_workspace_id = w.id
        INNER JOIN workspace_members m ON m.workspace_id = w.id AND m.user_id = p.id
        WHERE p.id = :profileId
          AND p.deleted_at_epoch_ms IS NULL
          AND w.deleted_at_epoch_ms IS NULL
          AND m.deleted_at_epoch_ms IS NULL
        LIMIT 1
        """,
    )
    fun observeActive(profileId: String): Flow<WorkspaceEntity?>

    @Query(
        """
        SELECT * FROM workspace_members
        WHERE workspace_id = :workspaceId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY role_code, user_id
        """,
    )
    fun observeMembers(workspaceId: String): Flow<List<WorkspaceMemberEntity>>

    @Query(
        """
        SELECT * FROM workspace_invitations
        WHERE workspace_id = :workspaceId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY expires_at_epoch_ms ASC, id ASC
        """,
    )
    fun observeInvitations(workspaceId: String): Flow<List<WorkspaceInvitationEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Query(
        """
        SELECT * FROM workspace_invitations
        WHERE token_hash = :tokenHash
          AND deleted_at_epoch_ms IS NULL
        LIMIT 1
        """,
    )
    suspend fun getInvitationByTokenHash(tokenHash: String): WorkspaceInvitationEntity?

    @Query(
        """
        SELECT * FROM workspace_members
        WHERE workspace_id = :workspaceId
          AND user_id = :userId
        LIMIT 1
        """,
    )
    suspend fun getMember(workspaceId: String, userId: String): WorkspaceMemberEntity?

    @Query(
        """
        SELECT user_id FROM workspace_members
        WHERE workspace_id = :workspaceId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY user_id ASC
        """,
    )
    suspend fun getActiveMemberUserIds(workspaceId: String): List<String>

    @Upsert
    suspend fun upsertWorkspace(entity: WorkspaceEntity)

    @Upsert
    suspend fun upsertMember(entity: WorkspaceMemberEntity)

    @Upsert
    suspend fun upsertInvitation(entity: WorkspaceInvitationEntity)
}
