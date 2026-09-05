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
    @Query("SELECT * FROM sync_cursors WHERE entity_type_code = :entityTypeCode LIMIT 1")
    suspend fun getCursor(entityTypeCode: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors WHERE entity_type_code LIKE 'WORKSPACE_MEMBER:%'")
    suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity>

    @Query("SELECT * FROM sync_conflicts WHERE entity_id = :entityId LIMIT 1")
    suspend fun getConflict(entityId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId LIMIT 1")
    suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE entity_type_code = :entityTypeCode")
    suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts")
    suspend fun getAllConflicts(): List<SyncConflictEntity>

    @Upsert
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_conflicts ORDER BY detected_at_epoch_ms DESC")
    fun observeConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    fun observeConflictCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun getConflictCount(): Int

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query(
        "DELETE FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int

    @Query("SELECT last_successful_sync_at_epoch_ms FROM sync_user_states WHERE user_id = :userId LIMIT 1")
    fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?>

    @Query("SELECT * FROM sync_user_states WHERE user_id = :userId LIMIT 1")
    suspend fun getUserState(userId: String): SyncUserStateEntity?

    @Upsert
    suspend fun upsertUserState(state: SyncUserStateEntity)
}
