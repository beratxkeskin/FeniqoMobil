package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

data class TransactionWithTags(
    @Embedded
    val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionTagCrossRef::class,
            parentColumn = "transaction_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
          AND (:startDate IS NULL OR transaction_date >= :startDate)
          AND (:endDate IS NULL OR transaction_date <= :endDate)
          AND (:typeCode IS NULL OR type_code = :typeCode)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:paymentMethodCode IS NULL OR payment_method_code = :paymentMethodCode)
          AND (:searchQuery IS NULL OR search_text LIKE :searchQuery || '%')
        ORDER BY transaction_date DESC, created_at_epoch_ms DESC, id DESC
        """,
    )
    fun observeAll(
        ownerId: String,
        workspaceId: String?,
        startDate: String?,
        endDate: String?,
        typeCode: String?,
        categoryId: String?,
        paymentMethodCode: String?,
        searchQuery: String?,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<TransactionEntity?>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeWithTags(id: String): Flow<TransactionWithTags?>

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Upsert
    suspend fun upsertTags(entities: List<TagEntity>)

    @Upsert
    suspend fun upsertTagLinks(entities: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun deleteTagLinks(transactionId: String)

    /** İşlem ile etiket ilişkisini tek Room transaction içinde yeniler. */
    @Transaction
    suspend fun upsertWithTags(
        transaction: TransactionEntity,
        tags: List<TagEntity>,
        links: List<TransactionTagCrossRef>,
    ) {
        upsert(transaction)
        if (tags.isNotEmpty()) upsertTags(tags)
        deleteTagLinks(transaction.id)
        if (links.isNotEmpty()) upsertTagLinks(links)
    }
}

@Dao
interface TagDao {
    @Query(
        """
        SELECT * FROM tags
        WHERE owner_id = :ownerId
          AND deleted_at_epoch_ms IS NULL
          AND ((:workspaceId IS NULL AND workspace_id IS NULL) OR workspace_id = :workspaceId)
        ORDER BY normalized_name
        """,
    )
    fun observeAll(ownerId: String, workspaceId: String?): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id AND deleted_at_epoch_ms IS NULL")
    fun observeById(id: String): Flow<TagEntity?>

    @Upsert
    suspend fun upsert(entity: TagEntity)
}
