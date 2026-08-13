package com.feniqo.mobile.data.remote.storage

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ReceiptPath

data class ReceiptUploadRequest(
    val ownerId: EntityId,
    val transactionId: EntityId,
    val objectId: EntityId,
    val extension: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    init {
        require(bytes.isNotEmpty()) { "Boş makbuz dosyası yüklenemez." }
        require(bytes.size <= MAX_STANDARD_UPLOAD_BYTES) { "Makbuz dosyası 6 MB sınırını aşamaz." }
        requireSafePathSegment(ownerId.value, "ownerId")
        requireSafePathSegment(transactionId.value, "transactionId")
        requireSafePathSegment(objectId.value, "objectId")

        val normalizedExtension = extension.lowercase().removePrefix(".")
        require(ALLOWED_CONTENT_TYPES[normalizedExtension] == contentType.lowercase()) {
            "Makbuz uzantısı veya içerik türü desteklenmiyor."
        }
    }

    val path: ReceiptPath
        get() = ReceiptPath(
            "${ownerId.value}/${transactionId.value}/${objectId.value}.${extension.lowercase().removePrefix(".")}",
        )

    companion object {
        const val MAX_STANDARD_UPLOAD_BYTES = 6 * 1024 * 1024
        private val SAFE_PATH_SEGMENT = Regex("^[A-Za-z0-9_-]+$")
        private val ALLOWED_CONTENT_TYPES = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "pdf" to "application/pdf",
        )

        private fun requireSafePathSegment(value: String, field: String) {
            require(SAFE_PATH_SEGMENT.matches(value)) { "$field güvenli bir Storage yol parçası değil." }
        }
    }
}

interface ReceiptStorageDataSource {
    suspend fun upload(request: ReceiptUploadRequest): ReceiptPath
    suspend fun download(path: ReceiptPath): ByteArray
    suspend fun delete(path: ReceiptPath)
}
