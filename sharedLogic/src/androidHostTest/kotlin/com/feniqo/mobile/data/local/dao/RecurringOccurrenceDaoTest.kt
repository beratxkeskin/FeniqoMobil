package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_5_6
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.domain.model.SyncStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecurringOccurrenceDaoTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private val testSync = SyncMetadata(
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtEpochMillis = 1000L,
        localUpdatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null,
        version = 1L,
        baseVersion = 1L,
        lastSyncError = null,
    )

    private fun testCategory(id: String = "cat-1", ownerId: String = "user-1"): CategoryEntity {
        return CategoryEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = null,
            scopeKey = "personal:$ownerId",
            name = "Market",
            normalizedName = "market",
            slug = null,
            typeCode = "EXPENSE",
            colorHex = "#FF0000",
            iconKey = "cart",
            isDefault = false,
            createdAtEpochMillis = 1000L,
            sync = testSync,
        )
    }

    private fun testRecurringEntity(
        id: String = "rec-1",
        ownerId: String = "user-1",
        categoryId: String = "cat-1",
        startDate: String = "2026-08-01",
        lastGeneratedDate: String? = null,
        isActive: Boolean = true,
    ): RecurringTransactionEntity {
        return RecurringTransactionEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = null,
            amountMinor = 50000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = categoryId,
            description = "Aylık internet",
            paymentMethodCode = "CREDIT_CARD",
            frequencyCode = "MONTHLY",
            interval = 1,
            startDate = startDate,
            endDate = null,
            lastGeneratedDate = lastGeneratedDate,
            isActive = isActive,
            createdAtEpochMillis = 1000L,
            sync = testSync,
        )
    }

    private fun testTransactionEntity(
        id: String = "trx-1",
        ownerId: String = "user-1",
        categoryId: String = "cat-1",
        date: String = "2026-08-01",
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = null,
            amountMinor = 50000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = categoryId,
            description = "Aylık internet",
            searchText = "aylik internet",
            paymentMethodCode = "CREDIT_CARD",
            transactionDate = date,
            receiptPath = null,
            installmentNumber = null,
            totalInstallments = null,
            installmentGroupId = null,
            createdAtEpochMillis = 1000L,
            sync = testSync.copy(syncStatus = SyncStatus.PENDING_CREATE.name),
        )
    }

    private fun testOutboxOperation(
        operationId: String = "0123456789abcdef0123456789abcdef",
        transactionId: String = "trx-1",
    ): SyncOperationEntity {
        return SyncOperationEntity(
            operationId = operationId,
            entityTypeCode = "TRANSACTION",
            entityId = transactionId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{\"amount\":50000}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
    }

    @Test
    fun generateRecurringOccurrence_success_writesOccurrenceTransactionOutboxAndAdvancesLastGeneratedDate() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            val recurring = testRecurringEntity(id = "rec-1", lastGeneratedDate = null)
            db.recurringTransactionDao().upsert(recurring)

            val cmd = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-1", date = "2026-08-01"),
                outboxOperation = testOutboxOperation(operationId = "a1b2c3d4e5f60718293a4b5c6d7e8f90", transactionId = "trx-1"),
            )

            val result = db.localMutationDao().generateRecurringOccurrence(cmd, nowEpochMillis = 2000L)

            assertTrue(result is GenerateRecurringOccurrenceResult.Created)
            assertEquals("trx-1", result.transactionId)
            assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90", result.operationId)

            // Verify Occurrence written
            val occurrence = db.recurringTransactionDao().getOccurrence("rec-1", "2026-08-01")
            assertNotNull(occurrence)
            assertEquals("rec-1", occurrence.recurringTransactionId)
            assertEquals("2026-08-01", occurrence.dueDate)
            assertEquals("trx-1", occurrence.transactionId)

            // Verify Transaction written
            val trx = db.transactionDao().observeByIdAndOwner("trx-1", "user-1").first()
            assertNotNull(trx)
            assertEquals("2026-08-01", trx.transactionDate)

            // Verify Outbox written
            val op = db.localMutationDao().getOutboxById("a1b2c3d4e5f60718293a4b5c6d7e8f90")
            assertNotNull(op)
            assertEquals("TRANSACTION", op.entityTypeCode)
            assertEquals("CREATE", op.operationTypeCode)

            // Verify lastGeneratedDate advanced
            val updatedRecurring = db.recurringTransactionDao().getById("rec-1")
            assertNotNull(updatedRecurring)
            assertEquals("2026-08-01", updatedRecurring.lastGeneratedDate)
            assertEquals(2000L, updatedRecurring.sync.localUpdatedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_alreadyGenerated_isIdempotentAndDoesNotDuplicate() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1", lastGeneratedDate = null))

            val cmd1 = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-1", date = "2026-08-01"),
                outboxOperation = testOutboxOperation(operationId = "11111111111111111111111111111111", transactionId = "trx-1"),
            )
            val result1 = db.localMutationDao().generateRecurringOccurrence(cmd1, nowEpochMillis = 2000L)
            assertTrue(result1 is GenerateRecurringOccurrenceResult.Created)

            // Attempt same occurrence again with different new transaction/operation info
            val cmd2 = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = "2026-08-01",
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-2", date = "2026-08-01"),
                outboxOperation = testOutboxOperation(operationId = "22222222222222222222222222222222", transactionId = "trx-2"),
            )
            val result2 = db.localMutationDao().generateRecurringOccurrence(cmd2, nowEpochMillis = 3000L)

            assertTrue(result2 is GenerateRecurringOccurrenceResult.AlreadyGenerated)
            assertEquals("trx-1", result2.existingTransactionId)

            // Verify trx-2 and second outbox were NOT created
            val trx2 = db.transactionDao().observeByIdAndOwner("trx-2", "user-1").first()
            assertNull(trx2)
            val op2 = db.localMutationDao().getOutboxById("22222222222222222222222222222222")
            assertNull(op2)
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_staleState_returnsStaleAndPerformsNoWrites() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            // Actual state has lastGeneratedDate = "2026-08-01"
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1", lastGeneratedDate = "2026-08-01"))

            // Caller thinks lastGeneratedDate is null (stale expectation)
            val cmd = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-09-01",
                transactionEntity = testTransactionEntity(id = "trx-stale", date = "2026-09-01"),
                outboxOperation = testOutboxOperation(operationId = "33333333333333333333333333333333", transactionId = "trx-stale"),
            )
            val result = db.localMutationDao().generateRecurringOccurrence(cmd, nowEpochMillis = 2000L)

            assertEquals(GenerateRecurringOccurrenceResult.StaleRecurringState, result)

            // Verify no transaction or outbox written
            val trx = db.transactionDao().observeByIdAndOwner("trx-stale", "user-1").first()
            assertNull(trx)
            val op = db.localMutationDao().getOutboxById("33333333333333333333333333333333")
            assertNull(op)
            val occ = db.recurringTransactionDao().getOccurrence("rec-1", "2026-09-01")
            assertNull(occ)
            // lastGeneratedDate remains 2026-08-01
            val rec = db.recurringTransactionDao().getById("rec-1")
            assertEquals("2026-08-01", rec?.lastGeneratedDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_dueDateMismatch_failsClosed() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1"))

            val cmd = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-1", date = "2026-08-02"), // Mismatched date!
                outboxOperation = testOutboxOperation(operationId = "44444444444444444444444444444444", transactionId = "trx-1"),
            )

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(cmd, nowEpochMillis = 2000L)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_invalidOutboxOperation_failsClosed() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1", lastGeneratedDate = null))

            val validTrx = testTransactionEntity(id = "trx-1", date = "2026-08-01")

            // 1. Protocol version 1 (V1)
            val v1Op = testOutboxOperation(operationId = "11111111111111111111111111111111", transactionId = "trx-1").copy(protocolVersion = 1)
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = v1Op),
                    nowEpochMillis = 2000L,
                )
            }

            // 2. Blank payloadJson
            val blankPayloadOp = testOutboxOperation(operationId = "22222222222222222222222222222222", transactionId = "trx-1").copy(payloadJson = "   ")
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = blankPayloadOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 3. Null payloadJson
            val nullPayloadOp = testOutboxOperation(operationId = "33333333333333333333333333333333", transactionId = "trx-1").copy(payloadJson = null)
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = nullPayloadOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 4. Non-null baseVersion on CREATE
            val baseVersionOp = testOutboxOperation(operationId = "44444444444444444444444444444444", transactionId = "trx-1").copy(baseVersion = 1L)
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = baseVersionOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 5. Non-PENDING statusCode
            val statusOp = testOutboxOperation(operationId = "55555555555555555555555555555555", transactionId = "trx-1").copy(statusCode = "IN_FLIGHT")
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = statusOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 6. Non-zero attemptCount
            val attemptOp = testOutboxOperation(operationId = "66666666666666666666666666666666", transactionId = "trx-1").copy(attemptCount = 2)
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = attemptOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 7. Non-null predecessorOperationId
            val predOp = testOutboxOperation(operationId = "77777777777777777777777777777777", transactionId = "trx-1").copy(predecessorOperationId = "prev-op")
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = predOp),
                    nowEpochMillis = 2000L,
                )
            }

            // 8. isBlocked = true
            val blockedOp = testOutboxOperation(operationId = "88888888888888888888888888888888", transactionId = "trx-1").copy(isBlocked = true)
            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().generateRecurringOccurrence(
                    GenerateRecurringOccurrenceCommand("rec-1", null, "2026-08-01", validTrx, outboxOperation = blockedOp),
                    nowEpochMillis = 2000L,
                )
            }

            // Verify no writes occurred
            val trx = db.transactionDao().observeByIdAndOwner("trx-1", "user-1").first()
            assertNull(trx)
            val occ = db.recurringTransactionDao().getOccurrence("rec-1", "2026-08-01")
            assertNull(occ)
            val rec = db.recurringTransactionDao().getById("rec-1")
            assertNull(rec?.lastGeneratedDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_duplicateTransactionId_revertsAllChangesAndPreservesExistingTransaction() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1", lastGeneratedDate = null))

            // Pre-insert existing transaction with id 'trx-1'
            val existingTrx = testTransactionEntity(id = "trx-1", date = "2026-07-15").copy(description = "Mevcut orijinal islem")
            db.transactionDao().upsert(existingTrx)

            // Try to generate occurrence with duplicate transaction id 'trx-1' -> insertTransactionRow ABORTs
            val cmd = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-1", date = "2026-08-01").copy(description = "Yeni cakisan islem"),
                outboxOperation = testOutboxOperation(operationId = "99999999999999999999999999999999", transactionId = "trx-1"),
            )

            assertFailsWith<Exception> {
                db.localMutationDao().generateRecurringOccurrence(cmd, nowEpochMillis = 2000L)
            }

            // Verify existing transaction data remains completely untouched
            val trx = db.transactionDao().observeByIdAndOwner("trx-1", "user-1").first()
            assertNotNull(trx)
            assertEquals("Mevcut orijinal islem", trx.description)
            assertEquals("2026-07-15", trx.transactionDate)

            // Verify no occurrence or outbox added for the recurring transaction
            val occ = db.recurringTransactionDao().getOccurrence("rec-1", "2026-08-01")
            assertNull(occ)
            val op = db.localMutationDao().getOutboxById("99999999999999999999999999999999")
            assertNull(op)

            // Verify recurring lastGeneratedDate did NOT advance
            val rec = db.recurringTransactionDao().getById("rec-1")
            assertNull(rec?.lastGeneratedDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun generateRecurringOccurrence_rollbackOnDuplicateOutbox_revertsAllChanges() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(testCategory())
            db.recurringTransactionDao().upsert(testRecurringEntity(id = "rec-1", lastGeneratedDate = null))

            // Pre-insert an outbox row with id "existing-op"
            val existingOp = testOutboxOperation(operationId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", transactionId = "other-trx")
            db.localMutationDao().insertOutboxRow(existingOp)

            // Try to generate occurrence with duplicate operationId -> insertOutboxRow will fail
            val cmd = GenerateRecurringOccurrenceCommand(
                recurringTransactionId = "rec-1",
                expectedPreviousLastGeneratedDate = null,
                dueDate = "2026-08-01",
                transactionEntity = testTransactionEntity(id = "trx-1", date = "2026-08-01"),
                outboxOperation = testOutboxOperation(operationId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", transactionId = "trx-1"),
            )

            assertFailsWith<Exception> {
                db.localMutationDao().generateRecurringOccurrence(cmd, nowEpochMillis = 2000L)
            }

            // Verify atomic rollback: transaction, occurrence, and lastGeneratedDate change were NOT persisted
            val trx = db.transactionDao().observeByIdAndOwner("trx-1", "user-1").first()
            assertNull(trx, "Transaction rollback edilmeliydi")
            val occ = db.recurringTransactionDao().getOccurrence("rec-1", "2026-08-01")
            assertNull(occ, "Occurrence rollback edilmeliydi")
            val rec = db.recurringTransactionDao().getById("rec-1")
            assertNull(rec?.lastGeneratedDate, "lastGeneratedDate rollback edilmeliydi")
        } finally {
            db.close()
        }
    }

    @Test
    fun migration_5_to_6_createsTables_preservesExistingData_andEnforcesUniqueConstraint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_5_6_test.db"
        val testDbFile = context.getDatabasePath(testDbName)
        val testDbPath = testDbFile.absolutePath
        context.deleteDatabase(testDbName)

        val v5Db = migrationHelper.createDatabase(testDbPath, 5)
        try {
            v5Db.execSQL(
                """
                INSERT INTO categories (
                    id, owner_id, workspace_id, scope_key, name, normalized_name,
                    slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'cat-1', 'user-1', NULL, 'personal:user-1', 'Market', 'market',
                    NULL, 'EXPENSE', '#FF0000', 'cart', 0, 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v5Db.execSQL(
                """
                INSERT INTO transactions (
                    id, owner_id, workspace_id, amount_minor, currency_code, type_code,
                    category_id, description, search_text, payment_method_code, transaction_date,
                    receipt_path, installment_number, total_installments, installment_group_id,
                    created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'trx-1', 'user-1', NULL, 50000, 'TRY', 'EXPENSE',
                    'cat-1', 'Eski islem', 'eski islem', 'CREDIT_CARD', '2026-08-01',
                    NULL, NULL, NULL, NULL,
                    1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v5Db.close()
        }

        val v6Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            6,
            true,
            ANDROID_MIGRATION_5_6,
        )
        try {
            // Verify existing v5 data preserved
            val trxCursor = v6Db.query("SELECT id, description, amount_minor FROM transactions WHERE id = 'trx-1'")
            assertTrue(trxCursor.moveToFirst())
            assertEquals("trx-1", trxCursor.getString(0))
            assertEquals("Eski islem", trxCursor.getString(1))
            assertEquals(50000L, trxCursor.getLong(2))
            trxCursor.close()

            // Insert new v6 recurring transaction and occurrence
            v6Db.execSQL(
                """
                INSERT INTO recurring_transactions (
                    id, owner_id, workspace_id, amount_minor, currency_code, type_code,
                    category_id, description, payment_method_code, frequency_code, `interval`,
                    start_date, end_date, last_generated_date, is_active, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'rec-1', 'user-1', NULL, 50000, 'TRY', 'EXPENSE',
                    'cat-1', 'Aylık kira', 'BANK_TRANSFER', 'MONTHLY', 1,
                    '2026-08-01', NULL, NULL, 1, 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            v6Db.execSQL(
                """
                INSERT INTO recurring_transaction_occurrences (
                    recurring_transaction_id, due_date, transaction_id, created_at_epoch_ms
                ) VALUES (
                    'rec-1', '2026-08-01', 'trx-1', 1000
                )
                """.trimIndent(),
            )

            val occCursor = v6Db.query("SELECT recurring_transaction_id, due_date, transaction_id FROM recurring_transaction_occurrences WHERE recurring_transaction_id = 'rec-1'")
            assertTrue(occCursor.moveToFirst())
            assertEquals("rec-1", occCursor.getString(0))
            assertEquals("2026-08-01", occCursor.getString(1))
            assertEquals("trx-1", occCursor.getString(2))
            occCursor.close()

            // Verify unique constraint on transaction_id in occurrences
            assertFailsWith<Exception> {
                v6Db.execSQL(
                    """
                    INSERT INTO recurring_transaction_occurrences (
                        recurring_transaction_id, due_date, transaction_id, created_at_epoch_ms
                    ) VALUES (
                        'rec-1', '2026-09-01', 'trx-1', 1000
                    )
                    """.trimIndent(),
                )
            }
        } finally {
            v6Db.close()
            context.deleteDatabase(testDbName)
        }
    }
}
