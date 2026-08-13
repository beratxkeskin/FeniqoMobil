package com.feniqo.mobile.data.remote.storage

import com.feniqo.mobile.domain.model.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReceiptStorageDataSourceTest {

    @Test
    fun builds_owner_scoped_private_object_path() {
        val request = receiptRequest()

        assertEquals("user-1/tx-1/object-1.jpg", request.path.value)
    }

    @Test
    fun rejects_path_traversal_and_public_url_like_segments() {
        assertFailsWith<IllegalArgumentException> {
            receiptRequest(objectId = "../receipt")
        }
    }

    @Test
    fun rejects_mismatched_content_type() {
        assertFailsWith<IllegalArgumentException> {
            receiptRequest(extension = "jpg", contentType = "application/pdf")
        }
    }

    @Test
    fun rejects_files_above_standard_upload_limit() {
        assertFailsWith<IllegalArgumentException> {
            receiptRequest(bytes = ByteArray(ReceiptUploadRequest.MAX_STANDARD_UPLOAD_BYTES + 1))
        }
    }

    private fun receiptRequest(
        objectId: String = "object-1",
        extension: String = "jpg",
        contentType: String = "image/jpeg",
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ): ReceiptUploadRequest = ReceiptUploadRequest(
        ownerId = EntityId("user-1"),
        transactionId = EntityId("tx-1"),
        objectId = EntityId(objectId),
        extension = extension,
        contentType = contentType,
        bytes = bytes,
    )
}
