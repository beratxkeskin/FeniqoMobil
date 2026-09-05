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
        WHERE p.id = :profileId
          AND p.deleted_at_epoch_ms IS NULL
          AND w.deleted_at_epoch_ms IS NULL
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

    @Upsert
    suspend fun upsertWorkspace(entity: WorkspaceEntity)

    @Upsert
    suspend fun upsertMember(entity: WorkspaceMemberEntity)

    @Upsert
    suspend fun upsertInvitation(entity: WorkspaceInvitationEntity)
}
