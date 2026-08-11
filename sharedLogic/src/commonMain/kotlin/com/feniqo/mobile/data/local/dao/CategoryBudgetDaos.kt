package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE deleted_at_epoch_ms IS NULL
          AND (:typeCode IS NULL OR type_code = :typeCode)
          AND (
            is_default = 1 OR
            (owner_id = :ownerId AND (
              (:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId
            ))
          )
        ORDER BY is_default DESC, normalized_name
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
        typeCode: String?,
    ): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<CategoryEntity?>

    @Upsert
    suspend fun upsert(entity: CategoryEntity)
}

@Dao
interface BudgetDao {
    @Query(
        """
        SELECT * FROM budgets
        WHERE owner_id = :ownerId
          AND month = :month
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY category_id
        """,
    )
    fun observeForMonth(
        ownerId: String,
        workspaceId: String?,
        month: String,
    ): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<BudgetEntity?>

    @Upsert
    suspend fun upsert(entity: BudgetEntity)
}
