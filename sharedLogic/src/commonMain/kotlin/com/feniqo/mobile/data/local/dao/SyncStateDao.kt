package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncUserStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_cursors WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode LIMIT 1")
    suspend fun getCursor(syncScopeKey: String, entityTypeCode: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors WHERE sync_scope_key = :syncScopeKey AND entity_type_code LIKE 'WORKSPACE_MEMBER:%'")
    suspend fun getWorkspaceMemberCursors(syncScopeKey: String): List<SyncCursorEntity>

    @Query("SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_id = :entityId LIMIT 1")
    suspend fun getConflict(syncScopeKey: String, entityId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId LIMIT 1")
    suspend fun getConflict(syncScopeKey: String, entityTypeCode: String, entityId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode")
    suspend fun getConflictsByEntityType(syncScopeKey: String, entityTypeCode: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey")
    suspend fun getAllConflicts(syncScopeKey: String): List<SyncConflictEntity>

    @Upsert
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey ORDER BY detected_at_epoch_ms DESC")
    fun observeConflicts(syncScopeKey: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey")
    fun observeConflictCount(syncScopeKey: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey")
    suspend fun getConflictCount(syncScopeKey: String): Int

    @Query("SELECT COUNT(*) FROM sync_cursors WHERE sync_scope_key = 'LEGACY_UNRESOLVED'")
    fun observeLegacyQuarantineCursorCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_cursors WHERE sync_scope_key = 'LEGACY_UNRESOLVED'")
    suspend fun getLegacyQuarantineCursorCount(): Int

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE sync_scope_key = 'LEGACY_UNRESOLVED'")
    fun observeLegacyQuarantineConflictCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE sync_scope_key = 'LEGACY_UNRESOLVED'")
    suspend fun getLegacyQuarantineConflictCount(): Int

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query(
        "DELETE FROM sync_conflicts WHERE sync_scope_key = :syncScopeKey AND entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteConflict(syncScopeKey: String, entityTypeCode: String, entityId: String): Int

    @Query("SELECT last_successful_sync_at_epoch_ms FROM sync_user_states WHERE user_id = :userId LIMIT 1")
    fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?>

    @Query("SELECT * FROM sync_user_states WHERE user_id = :userId LIMIT 1")
    suspend fun getUserState(userId: String): SyncUserStateEntity?

    @Upsert
    suspend fun upsertUserState(state: SyncUserStateEntity)
}
