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
            (is_default = 1 AND owner_id IS NULL) OR
            (
              (:workspaceId IS NULL AND owner_id = :ownerId AND workspace_id IS NULL) OR
              (:workspaceId IS NOT NULL AND workspace_id = :workspaceId)
            )
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

    @Query(
        """
        SELECT * FROM categories
        WHERE id = :id
          AND deleted_at_epoch_ms IS NULL
          AND ((is_default = 1 AND owner_id IS NULL) OR owner_id = :ownerId)
        """,
    )
    fun observeByIdAndOwner(id: String, ownerId: String): Flow<CategoryEntity?>

    @Query(
        """
        SELECT * FROM categories
        WHERE id = :id
          AND deleted_at_epoch_ms IS NULL
          AND ((is_default = 1 AND owner_id IS NULL) OR owner_id = :ownerId)
        """,
    )
    suspend fun getByIdAndOwner(id: String, ownerId: String): CategoryEntity?

    @Query(
        """
        SELECT * FROM categories
        WHERE (
            (is_default = 1 AND owner_id IS NULL) OR
            (
              (:workspaceId IS NULL AND owner_id = :ownerId AND workspace_id IS NULL) OR
              (:workspaceId IS NOT NULL AND workspace_id = :workspaceId)
            )
        )
        ORDER BY is_default DESC, normalized_name
        """,
    )
    fun observeAllForHistoryLookup(
        ownerId: String,
        workspaceId: String?,
    ): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(entity: CategoryEntity)
}

@Dao
interface BudgetDao {
    @Query(
        """
        SELECT * FROM budgets
        WHERE month = :month
          AND deleted_at_epoch_ms IS NULL
          AND (
            (:workspaceId IS NULL AND owner_id = :ownerId AND workspace_id IS NULL) OR
            (:workspaceId IS NOT NULL AND workspace_id = :workspaceId)
          )
        ORDER BY category_id
        """,
    )
    fun observeForMonth(
        ownerId: String,
        workspaceId: String?,
        month: String,
    ): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE month = :month
          AND deleted_at_epoch_ms IS NULL
          AND (
            (:workspaceId IS NULL AND owner_id = :ownerId AND workspace_id IS NULL) OR
            (:workspaceId IS NOT NULL AND workspace_id = :workspaceId)
          )
        ORDER BY category_id
        """,
    )
    suspend fun getForMonth(
        ownerId: String,
        workspaceId: String?,
        month: String,
    ): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<BudgetEntity?>

    @Query(
        """
        SELECT * FROM budgets
        WHERE id = :id
          AND owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
        """,
    )
    suspend fun getByIdAndOwner(id: String, ownerId: String): BudgetEntity?

    @Query(
        """
        SELECT * FROM budgets
        WHERE scope_key = :scopeKey
          AND category_id = :categoryId
          AND month = :month
        """,
    )
    suspend fun getAnyByScopeCategoryAndMonth(
        scopeKey: String,
        categoryId: String,
        month: String,
    ): BudgetEntity?

    @Upsert
    suspend fun upsert(entity: BudgetEntity)
}
