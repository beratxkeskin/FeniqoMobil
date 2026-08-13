package com.feniqo.mobile.data.remote.storage

import com.feniqo.mobile.domain.model.ReceiptPath
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/** Makbuzları public URL üretmeden, RLS korumalı private bucket üzerinden yönetir. */
class SupabaseReceiptStorageDataSource(
    client: SupabaseClient,
) : ReceiptStorageDataSource {
    private val bucket = client.storage.from(RECEIPTS_BUCKET)

    override suspend fun upload(request: ReceiptUploadRequest): ReceiptPath {
        val expectedPath = request.path
        val response = bucket.upload(expectedPath.value, request.bytes) {
            upsert = false
            contentType = ContentType.parse(request.contentType)
        }
        return ReceiptPath(response.path.ifBlank { expectedPath.value })
    }

    override suspend fun download(path: ReceiptPath): ByteArray =
        bucket.downloadAuthenticated(path.value)

    override suspend fun delete(path: ReceiptPath) {
        bucket.delete(listOf(path.value))
    }

    private companion object {
        const val RECEIPTS_BUCKET = "receipts"
    }
}
