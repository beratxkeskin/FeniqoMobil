package com.feniqo.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.feniqo.mobile.data.local.entity.ReceiptFileEntity

/**
 * Makbuz dosyaları için sınırlı ve güvenli veri erişim nesnesi.
 *
 * Doğrudan silme veya serbest @Upsert metotları sunulmaz.
 * Ham SQL metotları protected yapılarak dış çağrıya kapatılmıştır; dışarıya yalnız
 * güvenli ve doğrulanmış transaction metotları sunulur.
 */
@Dao
abstract class ReceiptFileDao {
    @Query("SELECT * FROM receipt_files WHERE attachment_id = :attachmentId AND owner_id = :ownerId")
    abstract suspend fun getByAttachmentId(attachmentId: String, ownerId: String): ReceiptFileEntity?

    @Query("SELECT * FROM receipt_files WHERE owner_id = :ownerId ORDER BY created_at_epoch_ms DESC")
    abstract suspend fun listByOwner(ownerId: String): List<ReceiptFileEntity>

    @Query("SELECT * FROM receipt_files WHERE attachment_id = :attachmentId")
    protected abstract suspend fun getByIdInternal(attachmentId: String): ReceiptFileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInternal(file: ReceiptFileEntity)

    /**
     * Değişmez dosya kaydını kaydeder veya doğrular.
     * - Dosya henüz yoksa ekler.
     * - Aynı attachmentId altında birebir AYNI metadata varsa idempotent olarak mevcut kaydı döner.
     * - Aynı attachmentId altında farklı içerik/sahip/boyut/hash/MIME/uzak yol varsa IllegalStateException fırlatır.
     *   Değişmez dosya metadata'sı asla üzerine yazılamaz.
     * - Uzak dosya yolu (remote_path) UNIQUE constraint ile korunur.
     */
    @Transaction
    open suspend fun insertOrVerify(file: ReceiptFileEntity): ReceiptFileEntity {
        val existing = getByIdInternal(file.attachmentId)
        if (existing == null) {
            insertInternal(file)
            return file
        }
        val matches = existing.ownerId == file.ownerId &&
            existing.contentSha256 == file.contentSha256 &&
            existing.fileSizeBytes == file.fileSizeBytes &&
            existing.mimeType == file.mimeType &&
            existing.remotePath == file.remotePath

        if (!matches) {
            error(
                "Attachment '${file.attachmentId}' zaten farklı metadata ile mevcut. " +
                    "Değişmez dosya metadata'sı üzerine yazılamaz. Mevcut sahip=${existing.ownerId}, yeni sahip=${file.ownerId}"
            )
        }
        return existing
    }
}
