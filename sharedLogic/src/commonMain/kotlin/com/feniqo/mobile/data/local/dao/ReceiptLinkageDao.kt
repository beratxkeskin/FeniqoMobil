package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.feniqo.mobile.data.local.entity.ReceiptFileEntity
import com.feniqo.mobile.data.local.entity.ReceiptLinkageEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity

/**
 * İşlem ve makbuz bağlantısı için sınırlı ve güvenli veri erişim nesnesi.
 *
 * Doğrudan satır silme veya serbest @Upsert metotları sunulmaz.
 * Ham SQL metotları protected yapılarak dış çağrıya kapatılmıştır; dışarıya yalnız
 * güvenli ve doğrulanmış transaction metotları sunulur.
 */
@Dao
abstract class ReceiptLinkageDao {
    @Query("SELECT * FROM receipt_linkages WHERE transaction_id = :transactionId AND owner_id = :ownerId")
    abstract suspend fun getByTransactionId(transactionId: String, ownerId: String): ReceiptLinkageEntity?

    @Query("SELECT * FROM receipt_linkages WHERE active_attachment_id = :attachmentId AND owner_id = :ownerId")
    abstract suspend fun listByAttachmentId(attachmentId: String, ownerId: String): List<ReceiptLinkageEntity>

    @Query("SELECT * FROM receipt_linkages WHERE transaction_id = :transactionId")
    protected abstract suspend fun getByTransactionIdInternal(transactionId: String): ReceiptLinkageEntity?

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    protected abstract suspend fun getTransactionByIdInternal(transactionId: String): TransactionEntity?

    @Query("SELECT * FROM receipt_files WHERE attachment_id = :attachmentId")
    protected abstract suspend fun getFileByIdInternal(attachmentId: String): ReceiptFileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInternal(linkage: ReceiptLinkageEntity)

    @Update
    protected abstract suspend fun updateInternal(linkage: ReceiptLinkageEntity)

    /**
     * Bir işlem için ilk bağlantı niyetini kaydeder.
     * - generation >= 1 olmalıdır.
     * - Hedef işlem mevcut olmalı ve işlem sahibi (ownerId) linkage sahibi ile uyuşmalıdır (DAO transaction kontrolü).
     * - activeAttachmentId varsa dosya mevcut olmalı ve dosya sahibi linkage sahibi ile uyuşmalıdır
     *   (hem DAO transaction kontrolü hem de SQLite composite foreign key güvencesi).
     * - transactionId için zaten bir linkage varsa hata fırlatır (updateLinkage kullanılmalıdır).
     */
    @Transaction
    open suspend fun insertInitialLinkage(linkage: ReceiptLinkageEntity) {
        require(linkage.generation >= 1L) { "Generation 1 veya daha büyük olmalıdır." }
        val targetTx = getTransactionByIdInternal(linkage.transactionId)
            ?: error("İşlem bulunamadı: ${linkage.transactionId}")
        if (targetTx.ownerId != linkage.ownerId) {
            error("İşlem sahibi (${targetTx.ownerId}) ile bağlantı sahibi (${linkage.ownerId}) uyuşmuyor.")
        }
        if (linkage.activeAttachmentId != null) {
            val targetFile = getFileByIdInternal(linkage.activeAttachmentId)
                ?: error("Makbuz dosyası bulunamadı: ${linkage.activeAttachmentId}")
            if (targetFile.ownerId != linkage.ownerId) {
                error("Makbuz sahibi (${targetFile.ownerId}) ile bağlantı sahibi (${linkage.ownerId}) uyuşmuyor.")
            }
        }
        val existing = getByTransactionIdInternal(linkage.transactionId)
        if (existing != null) {
            error("İşlem '${linkage.transactionId}' için zaten linkage mevcut. updateLinkage kullanılmalıdır.")
        }
        insertInternal(linkage)
    }

    /**
     * Mevcut bağlantıyı atomik günceller (makbuz değiştirme veya kaldırma).
     * - C1 domain kurallarıyla tutarlı olarak her kullanıcı niyetinde revizyon tam olarak +1 artmalıdır:
     *   updatedLinkage.generation == existing.generation + 1L.
     * - Long.MAX_VALUE taşması engellenir.
     * - owner_id mevcut kayıtla kesinlikle aynı kalmalıdır; sahip değişimi reddedilir.
     * - activeAttachmentId varsa dosya mevcut olmalı ve sahibi linkage sahibi ile uyuşmalıdır.
     */
    @Transaction
    open suspend fun updateLinkage(updatedLinkage: ReceiptLinkageEntity) {
        val existing = getByTransactionIdInternal(updatedLinkage.transactionId)
            ?: error("Güncellenecek linkage bulunamadı: ${updatedLinkage.transactionId}")
        if (existing.ownerId != updatedLinkage.ownerId) {
            error("Mevcut bağlantı sahibi (${existing.ownerId}) ile güncelleyen sahip (${updatedLinkage.ownerId}) uyuşmuyor.")
        }
        check(existing.generation < Long.MAX_VALUE) { "Generation taşması engellendi." }
        if (updatedLinkage.generation != existing.generation + 1L) {
            error(
                "Geçersiz generation: Mevcut generation=${existing.generation}, " +
                    "beklenen yeni generation=${existing.generation + 1L}, verilen=${updatedLinkage.generation}"
            )
        }
        if (updatedLinkage.activeAttachmentId != null) {
            val targetFile = getFileByIdInternal(updatedLinkage.activeAttachmentId)
                ?: error("Makbuz dosyası bulunamadı: ${updatedLinkage.activeAttachmentId}")
            if (targetFile.ownerId != updatedLinkage.ownerId) {
                error("Makbuz sahibi (${targetFile.ownerId}) ile bağlantı sahibi (${updatedLinkage.ownerId}) uyuşmuyor.")
            }
        }
        updateInternal(updatedLinkage)
    }
}
