package com.feniqo.mobile.data.local.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.ReceiptFileEntity
import com.feniqo.mobile.data.local.entity.ReceiptLinkageEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReceiptPersistenceDaoTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    private fun fileBasedDatabase(file: File): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.databaseBuilder<FeniqoDatabase>(
            context = context,
            name = file.absolutePath,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    private suspend fun seedCategory(db: FeniqoDatabase, categoryId: String, ownerId: String) {
        db.categoryDao().upsert(
            CategoryEntity(
                id = categoryId,
                ownerId = ownerId,
                workspaceId = null,
                scopeKey = ownerId,
                name = "Genel $categoryId",
                normalizedName = "genel $categoryId",
                slug = "genel-$categoryId",
                typeCode = "EXPENSE",
                colorHex = "#123456",
                iconKey = null,
                isDefault = false,
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = "SYNCED",
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1,
                    baseVersion = 1,
                    lastSyncError = null,
                ),
            ),
        )
    }

    private suspend fun seedTransaction(
        db: FeniqoDatabase,
        transactionId: String,
        ownerId: String,
        categoryId: String,
        installmentGroupId: String? = null,
        installmentNumber: Int? = null,
        totalInstallments: Int? = null,
    ) {
        db.transactionDao().upsert(
            TransactionEntity(
                id = transactionId,
                ownerId = ownerId,
                workspaceId = null,
                paidByUserId = ownerId,
                participantUserIdsJson = "[\"$ownerId\"]",
                amountMinor = 25000L,
                currencyCode = "TRY",
                typeCode = "EXPENSE",
                categoryId = categoryId,
                description = "Test işlemi",
                searchText = "test islemi",
                paymentMethodCode = "CREDIT_CARD",
                transactionDate = "2026-09-16",
                receiptPath = null,
                installmentNumber = installmentNumber,
                totalInstallments = totalInstallments,
                installmentGroupId = installmentGroupId,
                createdAtEpochMillis = 1000L,
                note = "Test notu",
                sync = SyncMetadata(
                    syncStatus = "SYNCED",
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1,
                    baseVersion = 1,
                    lastSyncError = null,
                ),
            ),
        )
    }

    private fun sampleFile(
        attachmentId: String = "att-1",
        ownerId: String = "user-1",
        sha256: String = "a".repeat(64),
        sizeBytes: Long = 1024L,
        mimeType: String = "image/jpeg",
        remotePath: String = "receipts/user-1/att-1.jpg",
        uploadStatus: String = "LOCAL_READY",
        verified: Boolean = false,
        createdAtEpochMs: Long = 1000L,
    ) = ReceiptFileEntity(
        attachmentId = attachmentId,
        ownerId = ownerId,
        contentSha256 = sha256,
        fileSizeBytes = sizeBytes,
        mimeType = mimeType,
        remotePath = remotePath,
        uploadStatus = uploadStatus,
        verifiedRemoteExists = verified,
        createdAtEpochMillis = createdAtEpochMs,
    )

    private fun sampleLinkage(
        transactionId: String = "tx-1",
        ownerId: String = "user-1",
        activeAttachmentId: String? = "att-1",
        generation: Long = 1L,
        sessionEpoch: Long = 1L,
        workspaceId: String? = null,
        updatedAtEpochMs: Long = 1000L,
    ) = ReceiptLinkageEntity(
        transactionId = transactionId,
        ownerId = ownerId,
        activeAttachmentId = activeAttachmentId,
        generation = generation,
        sessionEpoch = sessionEpoch,
        workspaceId = workspaceId,
        updatedAtEpochMillis = updatedAtEpochMs,
    )

    @Test
    fun file_and_linkage_insert_and_query_preserves_all_metadata() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-1", "user-1", "cat-1")

            val file = sampleFile()
            val insertedFile = db.receiptFileDao().insertOrVerify(file)
            assertEquals(file, insertedFile)

            val linkage = sampleLinkage()
            db.receiptLinkageDao().insertInitialLinkage(linkage)

            val loadedFile = db.receiptFileDao().getByAttachmentId("att-1", "user-1")
            assertNotNull(loadedFile)
            assertEquals("att-1", loadedFile.attachmentId)
            assertEquals("user-1", loadedFile.ownerId)
            assertEquals("a".repeat(64), loadedFile.contentSha256)
            assertEquals(1024L, loadedFile.fileSizeBytes)
            assertEquals("image/jpeg", loadedFile.mimeType)
            assertEquals("LOCAL_READY", loadedFile.uploadStatus)
            assertEquals(false, loadedFile.verifiedRemoteExists)

            val loadedLinkage = db.receiptLinkageDao().getByTransactionId("tx-1", "user-1")
            assertNotNull(loadedLinkage)
            assertEquals("tx-1", loadedLinkage.transactionId)
            assertEquals("user-1", loadedLinkage.ownerId)
            assertEquals("att-1", loadedLinkage.activeAttachmentId)
            assertEquals(1L, loadedLinkage.generation)
            assertEquals(1L, loadedLinkage.sessionEpoch)
        } finally {
            db.close()
        }
    }

    @Test
    fun owner_scoped_queries_isolate_accounts() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-A")
            seedTransaction(db, "tx-A", "user-A", "cat-1")

            val fileA = sampleFile(attachmentId = "att-A", ownerId = "user-A", remotePath = "receipts/user-A/att-A.jpg")
            db.receiptFileDao().insertOrVerify(fileA)

            val linkageA = sampleLinkage(transactionId = "tx-A", ownerId = "user-A", activeAttachmentId = "att-A")
            db.receiptLinkageDao().insertInitialLinkage(linkageA)

            // User-B sorguladığında hiçbir kayıt dönmemeli
            assertNull(db.receiptFileDao().getByAttachmentId("att-A", "user-B"))
            assertTrue(db.receiptFileDao().listByOwner("user-B").isEmpty())
            assertNull(db.receiptLinkageDao().getByTransactionId("tx-A", "user-B"))
            assertTrue(db.receiptLinkageDao().listByAttachmentId("att-A", "user-B").isEmpty())

            // User-A sorguladığında tam veri dönmeli
            assertNotNull(db.receiptFileDao().getByAttachmentId("att-A", "user-A"))
            assertEquals(1, db.receiptFileDao().listByOwner("user-A").size)
            assertNotNull(db.receiptLinkageDao().getByTransactionId("tx-A", "user-A"))
            assertEquals(1, db.receiptLinkageDao().listByAttachmentId("att-A", "user-A").size)
        } finally {
            db.close()
        }
    }

    @Test
    fun linking_to_different_owner_transaction_or_file_is_rejected() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-A")
            seedCategory(db, "cat-2", "user-B")
            seedTransaction(db, "tx-A", "user-A", "cat-1")
            seedTransaction(db, "tx-B", "user-B", "cat-2")

            val fileA = sampleFile(attachmentId = "att-A", ownerId = "user-A", remotePath = "receipts/user-A/att-A.jpg")
            val fileB = sampleFile(attachmentId = "att-B", ownerId = "user-B", remotePath = "receipts/user-B/att-B.jpg")
            db.receiptFileDao().insertOrVerify(fileA)
            db.receiptFileDao().insertOrVerify(fileB)

            // Durum 1: User-B'nin linkage'ı User-A'nın transaction'ına bağlanamaz (DAO kontrolü)
            val badLinkage1 = sampleLinkage(transactionId = "tx-A", ownerId = "user-B", activeAttachmentId = "att-B")
            val ex1 = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().insertInitialLinkage(badLinkage1)
            }
            assertTrue(ex1.message!!.contains("uyuşmuyor"))

            // Durum 2: User-A'nın linkage'ı User-B'nin dosyasına bağlanamaz (DAO kontrolü)
            val badLinkage2 = sampleLinkage(transactionId = "tx-A", ownerId = "user-A", activeAttachmentId = "att-B")
            val ex2 = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().insertInitialLinkage(badLinkage2)
            }
            assertTrue(ex2.message!!.contains("uyuşmuyor"))

            // SQLite constraint seviyesinde doğrudan SQL ile zorlansa bile composite FK reddetmeli
            assertFailsWith<SQLiteConstraintException> {
                db.openHelper.writableDatabase.execSQL(
                    """INSERT INTO receipt_linkages (
                        transaction_id, owner_id, active_attachment_id, generation, session_epoch, workspace_id, updated_at_epoch_ms
                    ) VALUES ('tx-A', 'user-A', 'att-B', 1, 1, NULL, 1000)""".trimIndent(),
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun duplicate_attachment_with_identical_metadata_is_idempotent_but_different_metadata_is_rejected() = runTest {
        val db = inMemoryDatabase()
        try {
            val fileOriginal = sampleFile(attachmentId = "att-same", ownerId = "user-1", sha256 = "b".repeat(64), sizeBytes = 2048L, remotePath = "receipts/user-1/att-same.jpg")
            val first = db.receiptFileDao().insertOrVerify(fileOriginal)
            assertEquals("att-same", first.attachmentId)

            // Birebir aynı metadata ile tekrar çağrıldığında idempotent olarak başarılı olmalı
            val fileIdentical = sampleFile(attachmentId = "att-same", ownerId = "user-1", sha256 = "b".repeat(64), sizeBytes = 2048L, remotePath = "receipts/user-1/att-same.jpg")
            val second = db.receiptFileDao().insertOrVerify(fileIdentical)
            assertEquals(first, second)

            // Aynı attachment_id altında farklı hash ile gönderilirse overwrite reddedilmeli
            val fileDiffHash = sampleFile(attachmentId = "att-same", ownerId = "user-1", sha256 = "c".repeat(64), sizeBytes = 2048L, remotePath = "receipts/user-1/att-same.jpg")
            assertFailsWith<IllegalStateException> {
                db.receiptFileDao().insertOrVerify(fileDiffHash)
            }

            // Aynı attachment_id altında farklı boyut ile gönderilirse overwrite reddedilmeli
            val fileDiffSize = sampleFile(attachmentId = "att-same", ownerId = "user-1", sha256 = "b".repeat(64), sizeBytes = 4096L, remotePath = "receipts/user-1/att-same.jpg")
            assertFailsWith<IllegalStateException> {
                db.receiptFileDao().insertOrVerify(fileDiffSize)
            }

            // Başka bir kullanıcının aynı attachment_id'yi çalma girişimi reddedilmeli
            val fileDiffOwner = sampleFile(attachmentId = "att-same", ownerId = "user-2", sha256 = "b".repeat(64), sizeBytes = 2048L, remotePath = "receipts/user-2/att-same.jpg")
            assertFailsWith<IllegalStateException> {
                db.receiptFileDao().insertOrVerify(fileDiffOwner)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun remote_path_unique_constraint_enforced_across_different_attachments() = runTest {
        val db = inMemoryDatabase()
        try {
            val file1 = sampleFile(attachmentId = "att-path-1", ownerId = "user-1", remotePath = "receipts/user-1/path-unique.jpg")
            db.receiptFileDao().insertOrVerify(file1)

            // Aynı dosya ve aynı path tekrar yollandığında idempotent başarı
            val file1Duplicate = sampleFile(attachmentId = "att-path-1", ownerId = "user-1", remotePath = "receipts/user-1/path-unique.jpg")
            assertEquals(file1, db.receiptFileDao().insertOrVerify(file1Duplicate))

            // Farklı attachment ID ile AYNI remote_path eklenmeye çalışıldığında constraint hatası
            val file2ConflictingPath = sampleFile(attachmentId = "att-path-2", ownerId = "user-1", remotePath = "receipts/user-1/path-unique.jpg")
            assertFailsWith<SQLiteConstraintException> {
                db.receiptFileDao().insertOrVerify(file2ConflictingPath)
            }

            // Farklı remote path kullanan farklı attachment kaydı başarılı
            val file3DifferentPath = sampleFile(attachmentId = "att-path-3", ownerId = "user-1", remotePath = "receipts/user-1/path-different.jpg")
            val inserted3 = db.receiptFileDao().insertOrVerify(file3DifferentPath)
            assertEquals("att-path-3", inserted3.attachmentId)
        } finally {
            db.close()
        }
    }

    @Test
    fun shared_file_across_installments_allows_independent_linkage_update() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-inst", "user-1")
            seedTransaction(db, "tx-inst-1", "user-1", "cat-inst", installmentGroupId = "grp-1", installmentNumber = 1, totalInstallments = 2)
            seedTransaction(db, "tx-inst-2", "user-1", "cat-inst", installmentGroupId = "grp-1", installmentNumber = 2, totalInstallments = 2)

            val sharedFile = sampleFile(attachmentId = "att-shared", ownerId = "user-1", remotePath = "receipts/user-1/att-shared.jpg")
            db.receiptFileDao().insertOrVerify(sharedFile)

            // İki taksit de aynı dosyaya bağlanır
            db.receiptLinkageDao().insertInitialLinkage(sampleLinkage(transactionId = "tx-inst-1", ownerId = "user-1", activeAttachmentId = "att-shared", generation = 1L))
            db.receiptLinkageDao().insertInitialLinkage(sampleLinkage(transactionId = "tx-inst-2", ownerId = "user-1", activeAttachmentId = "att-shared", generation = 1L))

            val linkagesForFile = db.receiptLinkageDao().listByAttachmentId("att-shared", "user-1")
            assertEquals(2, linkagesForFile.size)

            // 1. Taksitten makbuz kaldırılır (activeAttachmentId = null, generation = 2)
            val updatedLinkage1 = sampleLinkage(
                transactionId = "tx-inst-1",
                ownerId = "user-1",
                activeAttachmentId = null,
                generation = 2L,
                updatedAtEpochMs = 2000L,
            )
            db.receiptLinkageDao().updateLinkage(updatedLinkage1)

            // 1. Taksit güncellenmiş ve generation artmış olmalı; satır silinmemiş olmalı
            val loaded1 = db.receiptLinkageDao().getByTransactionId("tx-inst-1", "user-1")
            assertNotNull(loaded1)
            assertNull(loaded1.activeAttachmentId)
            assertEquals(2L, loaded1.generation)

            // 2. Taksit HALA paylaşılan dosyaya bağlı kalmalı ve generation 1 olmalı
            val loaded2 = db.receiptLinkageDao().getByTransactionId("tx-inst-2", "user-1")
            assertNotNull(loaded2)
            assertEquals("att-shared", loaded2.activeAttachmentId)
            assertEquals(1L, loaded2.generation)

            // Paylaşılan dosya receipt_files tablosunda aynen korunmalı
            assertNotNull(db.receiptFileDao().getByAttachmentId("att-shared", "user-1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun deleting_referenced_file_is_rejected_by_foreign_key_restrict() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-1", "user-1", "cat-1")

            val file = sampleFile(attachmentId = "att-ref", ownerId = "user-1", remotePath = "receipts/user-1/att-ref.jpg")
            db.receiptFileDao().insertOrVerify(file)

            val linkage = sampleLinkage(transactionId = "tx-1", ownerId = "user-1", activeAttachmentId = "att-ref")
            db.receiptLinkageDao().insertInitialLinkage(linkage)

            // Aktif linkage tarafından referans verilen dosyanın silinmesi SQLite FK RESTRICT tarafından engellenmeli
            assertFailsWith<SQLiteConstraintException> {
                db.openHelper.writableDatabase.execSQL("DELETE FROM receipt_files WHERE attachment_id = 'att-ref'")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun deleting_transaction_cascades_linkage_deletion_while_file_is_preserved() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-cascade", "user-1")
            seedTransaction(db, "tx-cascade", "user-1", "cat-cascade")

            val file = sampleFile(attachmentId = "att-cascade", ownerId = "user-1", remotePath = "receipts/user-1/att-cascade.jpg")
            db.receiptFileDao().insertOrVerify(file)

            val linkage = sampleLinkage(transactionId = "tx-cascade", ownerId = "user-1", activeAttachmentId = "att-cascade")
            db.receiptLinkageDao().insertInitialLinkage(linkage)

            // Transaction ve linkage'ın varlığını doğrula
            assertNotNull(db.receiptLinkageDao().getByTransactionId("tx-cascade", "user-1"))
            assertNotNull(db.receiptFileDao().getByAttachmentId("att-cascade", "user-1"))

            // Transaction satırını fiziksel olarak sil
            db.openHelper.writableDatabase.execSQL("DELETE FROM transactions WHERE id = 'tx-cascade'")

            // Linkage satırı ON DELETE CASCADE ile silinmiş olmalı
            assertNull(db.receiptLinkageDao().getByTransactionId("tx-cascade", "user-1"))

            // Fiziksel receipt file kaydı başka linkage olmasa bile otomatik silinmemeli (korunmalı)
            assertNotNull(db.receiptFileDao().getByAttachmentId("att-cascade", "user-1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun invalid_foreign_key_references_are_rejected() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-1", "user-1", "cat-1")

            // Var olmayan dosyaya bağlanma reddedilmeli
            val linkageBadFile = sampleLinkage(transactionId = "tx-1", ownerId = "user-1", activeAttachmentId = "att-ghost")
            assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().insertInitialLinkage(linkageBadFile)
            }

            // Var olmayan işleme linkage bağlama reddedilmeli
            val file = sampleFile(attachmentId = "att-1", ownerId = "user-1")
            db.receiptFileDao().insertOrVerify(file)
            val linkageBadTx = sampleLinkage(transactionId = "tx-ghost", ownerId = "user-1", activeAttachmentId = "att-1")
            assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().insertInitialLinkage(linkageBadTx)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun generation_rejects_lower_generation_and_preserves_existing_linkage() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-gen-1", "user-1", "cat-1")

            val file1 = sampleFile(attachmentId = "att-gen-1", ownerId = "user-1", remotePath = "receipts/user-1/att-gen-1.jpg")
            val file2 = sampleFile(attachmentId = "att-gen-2", ownerId = "user-1", remotePath = "receipts/user-1/att-gen-2.jpg")
            db.receiptFileDao().insertOrVerify(file1)
            db.receiptFileDao().insertOrVerify(file2)

            // Başlangıç generation = 1
            db.receiptLinkageDao().insertInitialLinkage(sampleLinkage(transactionId = "tx-gen-1", ownerId = "user-1", activeAttachmentId = "att-gen-1", generation = 1L))

            // 1 -> 2 güncellemesi başarılı
            db.receiptLinkageDao().updateLinkage(sampleLinkage(transactionId = "tx-gen-1", ownerId = "user-1", activeAttachmentId = "att-gen-2", generation = 2L))

            // Mevcut generation 2 iken generation 1 ile güncelleme reddedilmeli
            val ex = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().updateLinkage(sampleLinkage(transactionId = "tx-gen-1", ownerId = "user-1", activeAttachmentId = "att-gen-1", generation = 1L))
            }
            assertTrue(ex.message!!.contains("Geçersiz generation"))

            // Başarısız güncelleme sonrasında mevcut linkage değişmeden kalmalı
            val current = db.receiptLinkageDao().getByTransactionId("tx-gen-1", "user-1")
            assertNotNull(current)
            assertEquals("att-gen-2", current.activeAttachmentId)
            assertEquals(2L, current.generation)
        } finally {
            db.close()
        }
    }

    @Test
    fun generation_rejects_same_generation() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-gen-2", "user-1", "cat-1")

            val file1 = sampleFile(attachmentId = "att-gen-a", ownerId = "user-1", remotePath = "receipts/user-1/att-gen-a.jpg")
            val file2 = sampleFile(attachmentId = "att-gen-b", ownerId = "user-1", remotePath = "receipts/user-1/att-gen-b.jpg")
            db.receiptFileDao().insertOrVerify(file1)
            db.receiptFileDao().insertOrVerify(file2)

            db.receiptLinkageDao().insertInitialLinkage(sampleLinkage(transactionId = "tx-gen-2", ownerId = "user-1", activeAttachmentId = "att-gen-a", generation = 1L))
            db.receiptLinkageDao().updateLinkage(sampleLinkage(transactionId = "tx-gen-2", ownerId = "user-1", activeAttachmentId = "att-gen-b", generation = 2L))

            // Mevcut generation 2 iken tekrar generation 2 ile güncelleme reddedilmeli
            val ex = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().updateLinkage(sampleLinkage(transactionId = "tx-gen-2", ownerId = "user-1", activeAttachmentId = "att-gen-a", generation = 2L))
            }
            assertTrue(ex.message!!.contains("Geçersiz generation"))

            // Mevcut linkage değişmeden kalmalı
            val current = db.receiptLinkageDao().getByTransactionId("tx-gen-2", "user-1")
            assertNotNull(current)
            assertEquals(2L, current.generation)
        } finally {
            db.close()
        }
    }

    @Test
    fun updating_linkage_owner_is_rejected() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-owner-test", "user-1", "cat-1")

            val file1 = sampleFile(attachmentId = "att-owner-1", ownerId = "user-1", remotePath = "receipts/user-1/att-owner-1.jpg")
            db.receiptFileDao().insertOrVerify(file1)
            db.receiptLinkageDao().insertInitialLinkage(sampleLinkage(transactionId = "tx-owner-test", ownerId = "user-1", activeAttachmentId = "att-owner-1", generation = 1L))

            // Sahip değiştirerek güncelleme girişimi reddedilmeli
            val ex = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().updateLinkage(sampleLinkage(transactionId = "tx-owner-test", ownerId = "user-2", activeAttachmentId = "att-owner-1", generation = 2L))
            }
            assertTrue(ex.message!!.contains("uyuşmuyor"))

            // Mevcut linkage ve sahibi değişmeden kalmalı
            val current = db.receiptLinkageDao().getByTransactionId("tx-owner-test", "user-1")
            assertNotNull(current)
            assertEquals("user-1", current.ownerId)
            assertEquals(1L, current.generation)
        } finally {
            db.close()
        }
    }

    @Test
    fun generation_overflow_at_max_value_is_prevented() = runTest {
        val db = inMemoryDatabase()
        try {
            seedCategory(db, "cat-1", "user-1")
            seedTransaction(db, "tx-overflow", "user-1", "cat-1")

            val file1 = sampleFile(attachmentId = "att-overflow-1", ownerId = "user-1", remotePath = "receipts/user-1/att-overflow-1.jpg")
            db.receiptFileDao().insertOrVerify(file1)

            // Direct SQL ile generation = Long.MAX_VALUE satırı oluştur
            db.openHelper.writableDatabase.execSQL(
                """INSERT INTO receipt_linkages (
                    transaction_id, owner_id, active_attachment_id, generation, session_epoch, workspace_id, updated_at_epoch_ms
                ) VALUES ('tx-overflow', 'user-1', 'att-overflow-1', ${Long.MAX_VALUE}, 1, NULL, 1000)""".trimIndent(),
            )

            // Long.MAX_VALUE üzerinde güncelleme yapılmaya çalışıldığında taşma engellenmeli
            val ex = assertFailsWith<IllegalStateException> {
                db.receiptLinkageDao().updateLinkage(
                    sampleLinkage(
                        transactionId = "tx-overflow",
                        ownerId = "user-1",
                        activeAttachmentId = null,
                        generation = 2L,
                    ),
                )
            }
            assertTrue(ex.message!!.contains("Generation taşması engellendi"))

            // Kayıt Long.MAX_VALUE olarak bozulmadan kalmalı
            val current = db.receiptLinkageDao().getByTransactionId("tx-overflow", "user-1")
            assertNotNull(current)
            assertEquals(Long.MAX_VALUE, current.generation)
        } finally {
            db.close()
        }
    }

    @Test
    fun file_based_db_close_and_reopen_preserves_metadata_and_generation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "receipt_persistence_reopen_test.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Veritabanını aç ve veri yaz
        var db = fileBasedDatabase(dbFile)
        try {
            seedCategory(db, "cat-disk", "user-disk")
            seedTransaction(db, "tx-disk", "user-disk", "cat-disk")

            val file = sampleFile(attachmentId = "att-disk", ownerId = "user-disk", sha256 = "d".repeat(64), sizeBytes = 8192L, remotePath = "receipts/user-disk/att-disk.jpg")
            db.receiptFileDao().insertOrVerify(file)

            val linkage = sampleLinkage(transactionId = "tx-disk", ownerId = "user-disk", activeAttachmentId = "att-disk", generation = 1L)
            db.receiptLinkageDao().insertInitialLinkage(linkage)
        } finally {
            db.close()
        }

        // 2. Veritabanını diskteki dosyadan yeniden aç
        db = fileBasedDatabase(dbFile)
        try {
            val loadedFile = db.receiptFileDao().getByAttachmentId("att-disk", "user-disk")
            assertNotNull(loadedFile)
            assertEquals("d".repeat(64), loadedFile.contentSha256)
            assertEquals(8192L, loadedFile.fileSizeBytes)

            val loadedLinkage = db.receiptLinkageDao().getByTransactionId("tx-disk", "user-disk")
            assertNotNull(loadedLinkage)
            assertEquals("att-disk", loadedLinkage.activeAttachmentId)
            assertEquals(1L, loadedLinkage.generation)

            // Yeniden açılan DB'de monoton +1 generation artışı ile güncelle (1 -> 2)
            val updatedLinkage = sampleLinkage(
                transactionId = "tx-disk",
                ownerId = "user-disk",
                activeAttachmentId = null,
                generation = 2L,
                updatedAtEpochMs = 5000L,
            )
            db.receiptLinkageDao().updateLinkage(updatedLinkage)
        } finally {
            db.close()
        }

        // 3. Tekrar aç ve güncellemenin de diske yazıldığını doğrula
        db = fileBasedDatabase(dbFile)
        try {
            val reloadedLinkage = db.receiptLinkageDao().getByTransactionId("tx-disk", "user-disk")
            assertNotNull(reloadedLinkage)
            assertNull(reloadedLinkage.activeAttachmentId)
            assertEquals(2L, reloadedLinkage.generation)
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
