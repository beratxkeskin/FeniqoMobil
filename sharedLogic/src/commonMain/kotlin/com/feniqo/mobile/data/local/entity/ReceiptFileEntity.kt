package com.feniqo.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Değişmez fiziksel makbuz dosyasının kalıcı Room varlığı.
 *
 * attachment_id değişmez anahtardır. Bir dosya birden fazla işlem bağlantısı
 * (örneğin taksitler) tarafından paylaşılabilir.
 */
@Entity(
    tableName = "receipt_files",
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["attachment_id", "owner_id"], unique = true),
        Index(value = ["remote_path"], unique = true, name = "index_receipt_files_remote_path"),
    ],
)
data class ReceiptFileEntity(
    @PrimaryKey
    @ColumnInfo(name = "attachment_id")
    val attachmentId: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "content_sha256")
    val contentSha256: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String,
    @ColumnInfo(name = "verified_remote_exists")
    val verifiedRemoteExists: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMillis: Long,
)
