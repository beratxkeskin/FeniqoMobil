package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query(
        """
        SELECT * FROM assets
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """,
    )
    fun observeAll(ownerId: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_ms IS NULL LIMIT 1")
    fun observeById(id: String, ownerId: String): Flow<AssetEntity?>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getAnyById(id: String): AssetEntity?

    @Upsert
    suspend fun upsert(asset: AssetEntity)

    @Query("DELETE FROM sync_conflicts WHERE entity_type_code = 'ASSET' AND entity_id = :id")
    suspend fun deleteConflict(id: String): Int

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Upsert
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("UPDATE assets SET sync_status = 'CONFLICT', last_sync_error = 'remote_conflict' WHERE id = :id")
    suspend fun markConflict(id: String): Int

    @Transaction
    suspend fun applyPull(asset: AssetEntity, cursor: SyncCursorEntity) {
        upsert(asset)
        deleteConflict(asset.id)
        upsertCursor(cursor)
    }

    @Transaction
    suspend fun recordPullConflict(conflict: SyncConflictEntity, cursor: SyncCursorEntity) {
        upsertConflict(conflict)
        check(markConflict(conflict.entityId) == 1)
        upsertCursor(cursor)
    }

    @Transaction
    suspend fun applyInitialSnapshot(assets: List<AssetEntity>, cursor: SyncCursorEntity?) {
        assets.forEach { upsert(it) }
        if (cursor != null) upsertCursor(cursor)
    }
}
