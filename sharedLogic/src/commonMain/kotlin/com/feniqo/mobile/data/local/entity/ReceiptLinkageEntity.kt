package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bir finansal işlemin makbuz bağlantısı ve aktif kullanıcı niyetinin kalıcı Room varlığı.
 *
 * Her işlem başına en fazla bir bağlantı satırı bulunur (transaction_id birincil anahtardır).
 * Kaldırma işleminde satır silinmez; active_attachment_id null yapılarak generation artırılır.
 * Böylece eski veya gecikmiş sonuçların yeniden geçerli hale gelmesi engellenir.
 */
@Entity(
    tableName = "receipt_linkages",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReceiptFileEntity::class,
            parentColumns = ["attachment_id", "owner_id"],
            childColumns = ["active_attachment_id", "owner_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["active_attachment_id", "owner_id"]),
    ],
)
data class ReceiptLinkageEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "active_attachment_id")
    val activeAttachmentId: String?,
    @ColumnInfo(name = "generation")
    val generation: Long,
    /**
     * Karar anında C1 snapshot doğrulaması ve geriye dönük izlenebilirlik için saklanır.
     * Bu alan tek başına güncel Auth oturumunun kanıtı değildir (C2-B'de ele alınacaktır).
     */
    @ColumnInfo(name = "session_epoch")
    val sessionEpoch: Long,
    @ColumnInfo(name = "workspace_id")
    val workspaceId: String? = null,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMillis: Long,
) {
    init {
        require(generation >= 1L) { "Generation 1 veya daha büyük olmalıdır." }
        require(sessionEpoch >= 1L) { "Session epoch 1 veya daha büyük olmalıdır." }
    }
}
