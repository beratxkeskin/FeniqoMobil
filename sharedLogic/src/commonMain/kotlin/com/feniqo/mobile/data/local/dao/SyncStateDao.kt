package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_cursors WHERE entity_type_code = :entityTypeCode LIMIT 1")
    suspend fun getCursor(entityTypeCode: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_conflicts WHERE entity_id = :entityId LIMIT 1")
    suspend fun getConflict(entityId: String): SyncConflictEntity?

    @Upsert
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_conflicts ORDER BY detected_at_epoch_ms DESC")
    fun observeConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    fun observeConflictCount(): Flow<Int>

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query(
        "DELETE FROM sync_conflicts WHERE entity_type_code = :entityTypeCode AND entity_id = :entityId",
    )
    suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int
}
