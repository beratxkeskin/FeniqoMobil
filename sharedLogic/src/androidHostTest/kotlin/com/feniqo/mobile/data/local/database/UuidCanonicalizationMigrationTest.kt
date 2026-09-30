package com.feniqo.mobile.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.sync.EquivalentConflictResolver
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UuidCanonicalizationMigrationTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun createV20Db(name: String): Pair<String, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(name)
        val db = migrationHelper.createDatabase(dbFile.absolutePath, 20)
        return dbFile.absolutePath to db
    }

    private fun openRawV20Db(dbPath: String): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    private companion object {
        // 32-hex compact and corresponding 36-char canonical UUIDs
        const val COMPACT_CAT_ID = "11111111111141118111111111111111"
        const val CANONICAL_CAT_ID = "11111111-1111-4111-8111-111111111111"

        const val COMPACT_TX_ID = "22222222222242228222222222222222"
        const val CANONICAL_TX_ID = "22222222-2222-4222-8222-222222222222"

        const val COMPACT_BUDGET_ID = "33333333333343338333333333333333"
        const val CANONICAL_BUDGET_ID = "33333333-3333-4333-8333-333333333333"

        const val COMPACT_REC_ID = "44444444444444448444444444444444"
        const val CANONICAL_REC_ID = "44444444-4444-4444-8444-444444444444"

        const val COMPACT_SUB_ID = "55555555555545558555555555555555"
        const val CANONICAL_SUB_ID = "55555555-5555-4555-8555-555555555555"

        const val COMPACT_PRICE_HIST_ID = "66666666666646668666666666666666"
        const val CANONICAL_PRICE_HIST_ID = "66666666-6666-4666-8666-666666666666"

        const val COMPACT_PAYMENT_ID = "77777777777747778777777777777777"
        const val CANONICAL_PAYMENT_ID = "77777777-7777-4777-8777-777777777777"

        const val COMPACT_ASSET_ID = "88888888888848888888888888888888"
        const val CANONICAL_ASSET_ID = "88888888-8888-4888-8888-888888888888"

        const val COMPACT_GOAL_ID = "99999999999949998999999999999999"
        const val CANONICAL_GOAL_ID = "99999999-9999-4999-8999-999999999999"

        const val COMPACT_CONTRIB_ID = "aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaaa"
        const val CANONICAL_CONTRIB_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

        const val COMPACT_DEBT_ID = "bbbbbbbbbbbb4bbb8bbbbbbbbbbbbbbb"
        const val CANONICAL_DEBT_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

        const val COMPACT_DEBT_PAY_ID = "cccccccccccc4ccc8ccccccccccccccc"
        const val CANONICAL_DEBT_PAY_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

        const val COMPACT_TAG_ID = "dddddddddddd4ddd8ddddddddddddddd"
        const val CANONICAL_TAG_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"

        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val OP_ID_1 = "00000000000000000000000000000001"
        const val OP_ID_2 = "00000000000000000000000000000002"
        const val OP_ID_3 = "00000000000000000000000000000003"
        const val OP_ID_4 = "00000000000000000000000000000004"
        const val OP_ID_5 = "00000000000000000000000000000005"
        const val OP_ID_6 = "00000000000000000000000000000006"
    }

    @Test
    fun test_migration_20_to_21_canonicalizes_pk_fk_graph_and_passes_fk_check() {
        val (dbPath, db) = createV20Db("test_migration_pk_fk.db")
        try {
            // Seed v20 data with compact hex PKs and FKs
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek', 'yemek', 'yemek', 'EXPENSE', '#ff0000', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, owner_id, workspace_id, category_id, amount_minor, currency_code, type_code, description, payment_method_code, transaction_date, receipt_path, installment_group_id, installment_number, total_installments, created_at_epoch_ms, paid_by_user_id, participant_user_ids_json, search_text, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error, split_mode, participant_shares_json) " +
                    "VALUES ('$COMPACT_TX_ID', '$USER_ID', NULL, '$COMPACT_CAT_ID', 5000, 'TRY', 'EXPENSE', 'Market', 'CASH', '2026-09-01', NULL, 'group-text-id', 1, 3, 1000, '$USER_ID', '[]', '', 'SYNCED', 1000, 1000, NULL, 1, 1, NULL, 'EQUAL', '[]')"
            )
            db.execSQL(
                "INSERT INTO budgets (id, owner_id, workspace_id, scope_key, category_id, month, limit_minor, currency_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_BUDGET_ID', '$USER_ID', NULL, '$USER_ID', '$COMPACT_CAT_ID', '2026-09', 100000, 'TRY', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO recurring_transactions (id, owner_id, workspace_id, category_id, amount_minor, currency_code, type_code, description, payment_method_code, frequency_code, `interval`, start_date, end_date, last_generated_date, is_active, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_REC_ID', '$USER_ID', NULL, '$COMPACT_CAT_ID', 20000, 'TRY', 'EXPENSE', 'Kira', 'BANK_TRANSFER', 'MONTHLY', 1, '2026-09-01', NULL, NULL, 1, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO recurring_transaction_occurrences (recurring_transaction_id, due_date, transaction_id, created_at_epoch_ms) " +
                    "VALUES ('$COMPACT_REC_ID', '2026-09-01', '$COMPACT_TX_ID', 1000)"
            )
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_SUB_ID', '$USER_ID', NULL, '$COMPACT_CAT_ID', 'Netflix', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_price_histories (id, subscription_id, old_amount_minor, new_amount_minor, currency_code, changed_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_PRICE_HIST_ID', '$COMPACT_SUB_ID', 10000, 15000, 'TRY', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payments (id, subscription_id, amount_minor, currency_code, payment_date, renewal_due_date, source_type, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_PAYMENT_ID', '$COMPACT_SUB_ID', 15000, 'TRY', '2026-09-01', '2026-09-01', 'AUTOMATIC', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('${COMPACT_SUB_ID}_2026-10-01_DUE_SOON', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 1000)"
            )
            db.execSQL(
                "INSERT INTO assets (id, owner_id, name, type_code, current_value_minor, currency_code, tracking_symbol, auto_track, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_ASSET_ID', '$USER_ID', 'Altın', 'PRECIOUS_METALS', 5000000, 'TRY', 'XAU', 1, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO goals (id, owner_id, workspace_id, name, target_amount_minor, current_amount_minor, currency_code, target_date, color_hex, icon_key, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_GOAL_ID', '$USER_ID', NULL, 'Araba', 100000000, 20000000, 'TRY', '2027-01-01', '#00ff00', 'car', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO goal_contributions (id, goal_id, amount_minor, currency_code, direction_code, occurred_on, note, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_CONTRIB_ID', '$COMPACT_GOAL_ID', 5000000, 'TRY', 'ADD', '2026-09-01', 'Maaş', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO debts (id, owner_id, workspace_id, title, amount_minor, currency_code, type_code, due_date, status_code, description, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_DEBT_ID', '$USER_ID', NULL, 'Borç 1', 500000, 'TRY', 'DEBT', '2026-12-31', 'OPEN', 'Açıklama', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO debt_payments (id, debt_id, amount_minor, currency_code, paid_on, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_DEBT_PAY_ID', '$COMPACT_DEBT_ID', 100000, 'TRY', '2026-09-02', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO tags (id, owner_id, workspace_id, scope_key, name, normalized_name, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_TAG_ID', '$USER_ID', NULL, '$USER_ID', 'Tatil', 'tatil', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO transaction_tags (transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_TX_ID', '$COMPACT_TAG_ID', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO receipt_files (attachment_id, owner_id, content_sha256, file_size_bytes, mime_type, remote_path, upload_status, verified_remote_exists, created_at_epoch_ms) " +
                    "VALUES ('att-1', '$USER_ID', 'hash123', 1024, 'image/jpeg', 'receipts/att-1.jpg', 'UPLOADED', 1, 1000)"
            )
            db.execSQL(
                "INSERT INTO receipt_linkages (transaction_id, owner_id, active_attachment_id, generation, session_epoch, workspace_id, updated_at_epoch_ms) " +
                    "VALUES ('$COMPACT_TX_ID', '$USER_ID', 'att-1', 1, 1, NULL, 1000)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)

        // 1. Verify foreign_key_check is completely clean
        val fkCursor = migratedDb.query("PRAGMA foreign_key_check")
        val fkViolationCount = fkCursor.count
        fkCursor.close()
        assertEquals(0, fkViolationCount, "Foreign key check must return 0 violations after migration")

        // 2. Verify all PKs and FKs are canonicalized
        fun querySingleString(query: String): String {
            val cursor = migratedDb.query(query)
            try {
                assertTrue(cursor.moveToFirst(), "Query returned no rows: $query")
                return cursor.getString(0)
            } finally {
                cursor.close()
            }
        }

        assertEquals(CANONICAL_CAT_ID, querySingleString("SELECT id FROM categories WHERE name = 'Yemek'"))
        assertEquals(CANONICAL_TX_ID, querySingleString("SELECT id FROM transactions WHERE description = 'Market'"))
        assertEquals(CANONICAL_CAT_ID, querySingleString("SELECT category_id FROM transactions WHERE description = 'Market'"))
        assertEquals("group-text-id", querySingleString("SELECT installment_group_id FROM transactions WHERE description = 'Market'"))

        assertEquals(CANONICAL_BUDGET_ID, querySingleString("SELECT id FROM budgets WHERE month = '2026-09'"))
        assertEquals(CANONICAL_CAT_ID, querySingleString("SELECT category_id FROM budgets WHERE month = '2026-09'"))

        assertEquals(CANONICAL_REC_ID, querySingleString("SELECT id FROM recurring_transactions WHERE description = 'Kira'"))
        assertEquals(CANONICAL_CAT_ID, querySingleString("SELECT category_id FROM recurring_transactions WHERE description = 'Kira'"))

        assertEquals(CANONICAL_REC_ID, querySingleString("SELECT recurring_transaction_id FROM recurring_transaction_occurrences"))
        assertEquals(CANONICAL_TX_ID, querySingleString("SELECT transaction_id FROM recurring_transaction_occurrences"))

        assertEquals(CANONICAL_SUB_ID, querySingleString("SELECT id FROM subscriptions WHERE name = 'Netflix'"))
        assertEquals(CANONICAL_CAT_ID, querySingleString("SELECT category_id FROM subscriptions WHERE name = 'Netflix'"))

        assertEquals(CANONICAL_PRICE_HIST_ID, querySingleString("SELECT id FROM subscription_price_histories"))
        assertEquals(CANONICAL_SUB_ID, querySingleString("SELECT subscription_id FROM subscription_price_histories"))

        assertEquals(CANONICAL_PAYMENT_ID, querySingleString("SELECT id FROM subscription_payments"))
        assertEquals(CANONICAL_SUB_ID, querySingleString("SELECT subscription_id FROM subscription_payments"))

        assertEquals(CANONICAL_SUB_ID, querySingleString("SELECT subscription_id FROM subscription_payment_reminder_receipts"))
        assertEquals("${CANONICAL_SUB_ID}_2026-10-01_DUE_SOON", querySingleString("SELECT stable_key FROM subscription_payment_reminder_receipts"))

        assertEquals(CANONICAL_ASSET_ID, querySingleString("SELECT id FROM assets WHERE name = 'Altın'"))
        assertEquals(CANONICAL_GOAL_ID, querySingleString("SELECT id FROM goals WHERE name = 'Araba'"))
        assertEquals(CANONICAL_CONTRIB_ID, querySingleString("SELECT id FROM goal_contributions WHERE note = 'Maaş'"))
        assertEquals(CANONICAL_GOAL_ID, querySingleString("SELECT goal_id FROM goal_contributions WHERE note = 'Maaş'"))

        assertEquals(CANONICAL_DEBT_ID, querySingleString("SELECT id FROM debts WHERE title = 'Borç 1'"))
        assertEquals(CANONICAL_DEBT_PAY_ID, querySingleString("SELECT id FROM debt_payments WHERE amount_minor = 100000"))
        assertEquals(CANONICAL_DEBT_ID, querySingleString("SELECT debt_id FROM debt_payments WHERE amount_minor = 100000"))

        assertEquals(CANONICAL_TAG_ID, querySingleString("SELECT id FROM tags WHERE name = 'Tatil'"))
        assertEquals(CANONICAL_TX_ID, querySingleString("SELECT transaction_id FROM transaction_tags"))
        assertEquals(CANONICAL_TAG_ID, querySingleString("SELECT tag_id FROM transaction_tags"))

        assertEquals(CANONICAL_TX_ID, querySingleString("SELECT transaction_id FROM receipt_linkages"))
    }

    @Test
    fun test_v2_outbox_branch_a_canonicalizes_top_level_id_and_preserves_semantic_json_tree() {
        val (dbPath, db) = createV20Db("test_v2_branch_a.db")
        val compactCatId = COMPACT_CAT_ID
        val canonicalCatId = CANONICAL_CAT_ID
        val rawPayload = """{"id":"$compactCatId","user_id":"$USER_ID","name":"Yemek","nested_cat_id":"$compactCatId"}"""

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification) " +
                    "VALUES ('$OP_ID_1', 'CATEGORY', '$compactCatId', 'CREATE', 7, '$rawPayload', 'pred-op-1', 0, 2, 'IN_FLIGHT', 3, 'err-msg', 99999, 11111, 22222, 'RETRYABLE')"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query(
            "SELECT entity_id, payload_json, operation_id, predecessor_operation_id, status_code, attempt_count, base_version, error_classification, created_at_epoch_ms, updated_at_epoch_ms, next_attempt_at_epoch_ms, is_blocked, protocol_version " +
                "FROM sync_operations WHERE operation_id = '$OP_ID_1'"
        )
        cursor.moveToFirst()
        val entityId = cursor.getString(0)
        val payloadJson = cursor.getString(1)
        val opId = cursor.getString(2)
        val predOpId = cursor.getString(3)
        val statusCode = cursor.getString(4)
        val attemptCount = cursor.getInt(5)
        val baseVersion = cursor.getLong(6)
        val errClass = cursor.getString(7)
        val createdAt = cursor.getLong(8)
        val updatedAt = cursor.getLong(9)
        val nextAttempt = cursor.getLong(10)
        val isBlocked = cursor.getInt(11)
        val protocolVersion = cursor.getInt(12)
        cursor.close()

        assertEquals(canonicalCatId, entityId)
        val parsedJson = json.parseToJsonElement(payloadJson).jsonObject
        assertEquals(canonicalCatId, parsedJson["id"]?.toString()?.trim('"'))
        // Nested field must remain untouched (compact)
        assertEquals(compactCatId, parsedJson["nested_cat_id"]?.toString()?.trim('"'))
        assertEquals("Yemek", parsedJson["name"]?.toString()?.trim('"'))

        // Item 6: Verify outbox metadata fields remain strictly unchanged
        assertEquals(OP_ID_1, opId)
        assertEquals("pred-op-1", predOpId)
        assertEquals("IN_FLIGHT", statusCode)
        assertEquals(3, attemptCount)
        assertEquals(7L, baseVersion)
        assertEquals("RETRYABLE", errClass)
        assertEquals(11111L, createdAt)
        assertEquals(22222L, updatedAt)
        assertEquals(99999L, nextAttempt)
        assertEquals(0, isBlocked)
        assertEquals(2, protocolVersion)
    }

    @Test
    fun test_v2_outbox_branch_b_preserves_raw_string_byte_for_byte_when_payload_already_canonical() {
        val (dbPath, db) = createV20Db("test_v2_branch_b.db")
        val compactCatId = COMPACT_CAT_ID
        val canonicalCatId = CANONICAL_CAT_ID
        // payload id is already canonical, entity_id is compact
        val rawPayload = """  {"id": "$canonicalCatId", "name": "Yemek"}  """

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification) " +
                    "VALUES ('$OP_ID_2', 'CATEGORY', '$compactCatId', 'CREATE', 5, '$rawPayload', 'pred-op-2', 0, 2, 'QUEUED', 1, NULL, 55555, 33333, 44444, NULL)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query(
            "SELECT entity_id, payload_json, operation_id, predecessor_operation_id, status_code, attempt_count, base_version, error_classification, created_at_epoch_ms, updated_at_epoch_ms, next_attempt_at_epoch_ms " +
                "FROM sync_operations WHERE operation_id = '$OP_ID_2'"
        )
        cursor.moveToFirst()
        val entityId = cursor.getString(0)
        val payloadJson = cursor.getString(1)
        val opId = cursor.getString(2)
        val predOpId = cursor.getString(3)
        val statusCode = cursor.getString(4)
        val attemptCount = cursor.getInt(5)
        val baseVersion = cursor.getLong(6)
        val errClass = cursor.getString(7)
        val createdAt = cursor.getLong(8)
        val updatedAt = cursor.getLong(9)
        val nextAttempt = cursor.getLong(10)
        cursor.close()

        assertEquals(canonicalCatId, entityId)
        // Must be preserved byte-for-byte (exact raw string equality)
        assertEquals(rawPayload, payloadJson)

        // Metadata preservation
        assertEquals(OP_ID_2, opId)
        assertEquals("pred-op-2", predOpId)
        assertEquals("QUEUED", statusCode)
        assertEquals(1, attemptCount)
        assertEquals(5L, baseVersion)
        assertEquals(null, errClass)
        assertEquals(33333L, createdAt)
        assertEquals(44444L, updatedAt)
        assertEquals(55555L, nextAttempt)
    }

    @Test
    fun test_v2_outbox_branch_c_mismatch_or_invalid_id_fails_closed() {
        val (dbPath, db) = createV20Db("test_v2_branch_c.db")
        // entity_id is compact CAT_ID, but payload has different id
        val rawPayload = """{"id":"22222222-2222-4222-8222-222222222222","name":"Mismatch"}"""

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms) " +
                    "VALUES ('$OP_ID_3', 'CATEGORY', '$COMPACT_CAT_ID', 'CREATE', NULL, '$rawPayload', NULL, 0, 2, 'IN_FLIGHT', 1, NULL, 1000, 1000, 1000)"
            )
        } finally {
            db.close()
        }

        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }
    }

    @Test
    fun test_v2_outbox_branch_d_already_canonical_preserves_raw_string_byte_for_byte() {
        val (dbPath, db) = createV20Db("test_v2_branch_d.db")
        val canonicalCatId = CANONICAL_CAT_ID
        val rawPayload = """  {"id": "$canonicalCatId", "name": "Yemek"}  """

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification) " +
                    "VALUES ('$OP_ID_4', 'CATEGORY', '$canonicalCatId', 'CREATE', 10, '$rawPayload', 'pred-op-4', 0, 2, 'COMPLETED', 2, NULL, 66666, 77777, 88888, 'FATAL')"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query(
            "SELECT entity_id, payload_json, operation_id, predecessor_operation_id, status_code, attempt_count, base_version, error_classification, created_at_epoch_ms, updated_at_epoch_ms, next_attempt_at_epoch_ms " +
                "FROM sync_operations WHERE operation_id = '$OP_ID_4'"
        )
        cursor.moveToFirst()
        val entityId = cursor.getString(0)
        val payloadJson = cursor.getString(1)
        val opId = cursor.getString(2)
        val predOpId = cursor.getString(3)
        val statusCode = cursor.getString(4)
        val attemptCount = cursor.getInt(5)
        val baseVersion = cursor.getLong(6)
        val errClass = cursor.getString(7)
        val createdAt = cursor.getLong(8)
        val updatedAt = cursor.getLong(9)
        val nextAttempt = cursor.getLong(10)
        cursor.close()

        assertEquals(canonicalCatId, entityId)
        assertEquals(rawPayload, payloadJson)

        // Metadata preservation
        assertEquals(OP_ID_4, opId)
        assertEquals("pred-op-4", predOpId)
        assertEquals("COMPLETED", statusCode)
        assertEquals(2, attemptCount)
        assertEquals(10L, baseVersion)
        assertEquals("FATAL", errClass)
        assertEquals(77777L, createdAt)
        assertEquals(88888L, updatedAt)
        assertEquals(66666L, nextAttempt)
    }

    @Test
    fun test_v2_outbox_binding_branch_1_entity_id_canonical_and_payload_compact() {
        val (dbPath, db) = createV20Db("test_v2_binding_branch_1.db")
        val compactCatId = COMPACT_CAT_ID
        val canonicalCatId = CANONICAL_CAT_ID
        // entity_id is already canonical, payload has compact id of the same UUID
        val rawPayload = """{"id":"$compactCatId","user_id":"$USER_ID","name":"Yemek","nested_cat_id":"$compactCatId"}"""

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification) " +
                    "VALUES ('$OP_ID_5', 'CATEGORY', '$canonicalCatId', 'CREATE', 4, '$rawPayload', 'pred-op-5', 0, 2, 'FAILED', 5, 'timeout', 12345, 23456, 34567, 'TRANSIENT')"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query(
            "SELECT entity_id, payload_json, operation_id, predecessor_operation_id, status_code, attempt_count, base_version, error_classification, created_at_epoch_ms, updated_at_epoch_ms, next_attempt_at_epoch_ms " +
                "FROM sync_operations WHERE operation_id = '$OP_ID_5'"
        )
        cursor.moveToFirst()
        val entityId = cursor.getString(0)
        val payloadJson = cursor.getString(1)
        val opId = cursor.getString(2)
        val predOpId = cursor.getString(3)
        val statusCode = cursor.getString(4)
        val attemptCount = cursor.getInt(5)
        val baseVersion = cursor.getLong(6)
        val errClass = cursor.getString(7)
        val createdAt = cursor.getLong(8)
        val updatedAt = cursor.getLong(9)
        val nextAttempt = cursor.getLong(10)
        cursor.close()

        assertEquals(canonicalCatId, entityId)
        val parsedJson = json.parseToJsonElement(payloadJson).jsonObject
        assertEquals(canonicalCatId, parsedJson["id"]?.toString()?.trim('"'))
        // Nested field remains compact
        assertEquals(compactCatId, parsedJson["nested_cat_id"]?.toString()?.trim('"'))
        assertEquals("Yemek", parsedJson["name"]?.toString()?.trim('"'))

        // Metadata preservation
        assertEquals(OP_ID_5, opId)
        assertEquals("pred-op-5", predOpId)
        assertEquals("FAILED", statusCode)
        assertEquals(5, attemptCount)
        assertEquals(4L, baseVersion)
        assertEquals("TRANSIENT", errClass)
        assertEquals(23456L, createdAt)
        assertEquals(34567L, updatedAt)
        assertEquals(12345L, nextAttempt)
    }

    @Test
    fun test_v1_outbox_payload_is_never_parsed_or_modified_even_if_malformed() {
        val (dbPath, db) = createV20Db("test_v1_payload.db")
        val malformedV1Payload = "MALFORMED_NON_JSON_{{[["

        try {
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms) " +
                    "VALUES ('$OP_ID_6', 'TRANSACTION', '$COMPACT_TX_ID', 'UPDATE', 1, '$malformedV1Payload', NULL, 0, 1, 'IN_FLIGHT', 1, NULL, 1000, 1000, 1000)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query("SELECT entity_id, payload_json FROM sync_operations WHERE operation_id = '$OP_ID_6'")
        cursor.moveToFirst()
        val entityId = cursor.getString(0)
        val payloadJson = cursor.getString(1)
        cursor.close()

        // entity_id is canonicalized so V1 executor can find the Room row
        assertEquals(CANONICAL_TX_ID, entityId)
        // payload_json is untouched byte-for-byte even though malformed
        assertEquals(malformedV1Payload, payloadJson)
    }

    @Test
    fun test_sync_conflicts_direct_dto_and_remote_wrappers() {
        val (dbPath, db) = createV20Db("test_sync_conflicts.db")

        // Item 4: Full, decodable DTOs for conflict equivalence
        val localTxDto = TransactionDto(
            id = COMPACT_TX_ID,
            userId = USER_ID,
            workspaceId = null,
            paidByUserId = null,
            participantUserIds = emptyList(),
            amountMinor = 50000,
            currency = "TRY",
            type = "expense",
            categoryId = COMPACT_CAT_ID,
            description = "Market Alışverişi",
            paymentMethod = "credit_card",
            transactionDate = "2026-09-01",
            receiptPath = null,
            installmentNumber = 1,
            totalInstallments = 1,
            installmentGroupId = null,
            createdAt = "2026-09-01T10:00:00Z",
            updatedAt = null,
            deletedAt = null,
            version = 1L,
        )
        val remoteTxDto = TransactionDto(
            id = CANONICAL_TX_ID,
            userId = USER_ID,
            workspaceId = null,
            paidByUserId = null,
            participantUserIds = emptyList(),
            amountMinor = 50000,
            currency = "try",
            type = "EXPENSE",
            categoryId = CANONICAL_CAT_ID,
            description = "Market Alışverişi ",
            paymentMethod = "CREDIT_CARD",
            transactionDate = "2026-09-01",
            receiptPath = null,
            installmentNumber = 1,
            totalInstallments = 1,
            installmentGroupId = null,
            createdAt = "2026-09-01T10:00:01Z",
            updatedAt = "2026-09-01T10:00:01Z",
            deletedAt = null,
            version = 2L,
        )

        val localCatDto = CategoryDto(
            id = COMPACT_CAT_ID,
            userId = USER_ID,
            workspaceId = null,
            name = "Market",
            slug = null,
            type = "expense",
            color = "#ff0000",
            icon = null,
            isDefault = false,
            createdAt = "2026-09-01T10:00:00Z",
            updatedAt = null,
            deletedAt = null,
            version = 1L,
        )
        val remoteCatDto = CategoryDto(
            id = CANONICAL_CAT_ID,
            userId = USER_ID,
            workspaceId = null,
            name = "Market ",
            slug = null,
            type = "EXPENSE",
            color = "#FF0000",
            icon = null,
            isDefault = false,
            createdAt = "2026-09-01T10:00:01Z",
            updatedAt = "2026-09-01T10:00:01Z",
            deletedAt = null,
            version = 2L,
        )

        val localTxJson = json.encodeToString(localTxDto)
        val remoteTxJson = json.encodeToString(remoteTxDto)
        val localCatJson = json.encodeToString(localCatDto)
        val remoteCatJson = json.encodeToString(remoteCatDto)

        val localGoalContribJson = """{"id":"$COMPACT_CONTRIB_ID","goal_id":"$COMPACT_GOAL_ID","amount_minor":1000,"direction_code":"ADD","occurred_on":"2026-09-01","note":"Maaş"}"""
        val remoteGoalContribJson = """{"contribution":{"id":"$COMPACT_CONTRIB_ID","goal_id":"$COMPACT_GOAL_ID","amount_minor":1000,"direction_code":"ADD","occurred_on":"2026-09-01","note":"Maaş"},"goal":{"id":"$COMPACT_GOAL_ID","name":"Araba","target_amount_minor":100000,"current_amount_minor":20000,"currency_code":"TRY","target_date":"2027-01-01","color_hex":"#00ff00","icon_key":"car","version":2}}"""

        val localDebtPayJson = """{"id":"$COMPACT_DEBT_PAY_ID","debt_id":"$COMPACT_DEBT_ID","amount_minor":2000,"currency_code":"TRY","paid_on":"2026-09-02"}"""
        val remoteDebtPayJson = """{"payment":{"id":"$COMPACT_DEBT_PAY_ID","debt_id":"$COMPACT_DEBT_ID","amount_minor":2000,"currency_code":"TRY","paid_on":"2026-09-02"},"debt":{"id":"$COMPACT_DEBT_ID","title":"Borç 1","amount_minor":500000,"currency_code":"TRY","type_code":"DEBT","due_date":"2026-12-31","status_code":"OPEN","version":3}}"""

        try {
            db.execSQL(
                "INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms) " +
                    "VALUES ('TRANSACTION', '$COMPACT_TX_ID', '$OP_ID_1', 1, 2, '$localTxJson', '$remoteTxJson', 1000)"
            )
            db.execSQL(
                "INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms) " +
                    "VALUES ('CATEGORY', '$COMPACT_CAT_ID', '$OP_ID_2', 1, 2, '$localCatJson', '$remoteCatJson', 1000)"
            )
            db.execSQL(
                "INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms) " +
                    "VALUES ('GOAL_CONTRIBUTION', '$COMPACT_CONTRIB_ID', '$OP_ID_3', 1, 2, '$localGoalContribJson', '$remoteGoalContribJson', 1000)"
            )
            db.execSQL(
                "INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms) " +
                    "VALUES ('DEBT_PAYMENT', '$COMPACT_DEBT_PAY_ID', '$OP_ID_4', 1, 2, '$localDebtPayJson', '$remoteDebtPayJson', 1000)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)

        // 1. Verify TRANSACTION conflict & EquivalentConflictResolver
        val txCursor = migratedDb.query("SELECT entity_id, local_payload_json, remote_payload_json FROM sync_conflicts WHERE entity_type_code = 'TRANSACTION'")
        txCursor.moveToFirst()
        val migratedTxEntityId = txCursor.getString(0)
        val migratedLocalTxStr = txCursor.getString(1)
        val migratedRemoteTxStr = txCursor.getString(2)
        txCursor.close()

        assertEquals(CANONICAL_TX_ID, migratedTxEntityId)
        val txConflictEntity = SyncConflictEntity(
            syncScopeKey = "USER:test-user",
            entityTypeCode = "TRANSACTION",
            entityId = migratedTxEntityId,
            operationId = OP_ID_1,
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = migratedLocalTxStr,
            remotePayloadJson = migratedRemoteTxStr,
            detectedAtEpochMillis = 1000L,
        )
        val txOpEntity = SyncOperationEntity(
            syncScopeKey = "USER:test-user",
            operationId = OP_ID_1,
            entityTypeCode = "TRANSACTION",
            entityId = migratedTxEntityId,
            operationTypeCode = "CREATE",
            baseVersion = 1L,
            payloadJson = migratedLocalTxStr,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        assertTrue(EquivalentConflictResolver.isTransactionEquivalent(migratedLocalTxStr, migratedRemoteTxStr))
        assertTrue(EquivalentConflictResolver.isEquivalent(txConflictEntity, txOpEntity))

        // 2. Verify CATEGORY conflict & EquivalentConflictResolver
        val catCursor = migratedDb.query("SELECT entity_id, local_payload_json, remote_payload_json FROM sync_conflicts WHERE entity_type_code = 'CATEGORY'")
        catCursor.moveToFirst()
        val migratedCatEntityId = catCursor.getString(0)
        val migratedLocalCatStr = catCursor.getString(1)
        val migratedRemoteCatStr = catCursor.getString(2)
        catCursor.close()

        assertEquals(CANONICAL_CAT_ID, migratedCatEntityId)
        val catConflictEntity = SyncConflictEntity(
            syncScopeKey = "USER:test-user",
            entityTypeCode = "CATEGORY",
            entityId = migratedCatEntityId,
            operationId = OP_ID_2,
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = migratedLocalCatStr,
            remotePayloadJson = migratedRemoteCatStr,
            detectedAtEpochMillis = 1000L,
        )
        val catOpEntity = SyncOperationEntity(
            syncScopeKey = "USER:test-user",
            operationId = OP_ID_2,
            entityTypeCode = "CATEGORY",
            entityId = migratedCatEntityId,
            operationTypeCode = "CREATE",
            baseVersion = 1L,
            payloadJson = migratedLocalCatStr,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        assertTrue(EquivalentConflictResolver.isCategoryEquivalent(migratedLocalCatStr, migratedRemoteCatStr))
        assertTrue(EquivalentConflictResolver.isEquivalent(catConflictEntity, catOpEntity))

        // 3. Verify GOAL_CONTRIBUTION wrapper conflict & semantic preservation outside IDs
        val gcCursor = migratedDb.query("SELECT entity_id, local_payload_json, remote_payload_json FROM sync_conflicts WHERE entity_type_code = 'GOAL_CONTRIBUTION'")
        gcCursor.moveToFirst()
        assertEquals(CANONICAL_CONTRIB_ID, gcCursor.getString(0))
        val parsedLocalGc = json.parseToJsonElement(gcCursor.getString(1)).jsonObject
        val parsedRemoteGc = json.parseToJsonElement(gcCursor.getString(2)).jsonObject
        gcCursor.close()

        assertEquals(CANONICAL_CONTRIB_ID, parsedLocalGc["id"]?.toString()?.trim('"'))
        assertEquals(CANONICAL_GOAL_ID, parsedLocalGc["goal_id"]?.toString()?.trim('"'))
        // Non-ID business fields preserved
        assertEquals("1000", parsedLocalGc["amount_minor"]?.toString())
        assertEquals("ADD", parsedLocalGc["direction_code"]?.toString()?.trim('"'))
        assertEquals("Maaş", parsedLocalGc["note"]?.toString()?.trim('"'))

        val remoteContrib = parsedRemoteGc["contribution"]?.jsonObject
        val remoteGoal = parsedRemoteGc["goal"]?.jsonObject
        assertNotNull(remoteContrib)
        assertNotNull(remoteGoal)
        assertEquals(CANONICAL_CONTRIB_ID, remoteContrib["id"]?.toString()?.trim('"'))
        assertEquals(CANONICAL_GOAL_ID, remoteContrib["goal_id"]?.toString()?.trim('"'))
        assertEquals("1000", remoteContrib["amount_minor"]?.toString())
        assertEquals("ADD", remoteContrib["direction_code"]?.toString()?.trim('"'))
        assertEquals("Maaş", remoteContrib["note"]?.toString()?.trim('"'))

        assertEquals(CANONICAL_GOAL_ID, remoteGoal["id"]?.toString()?.trim('"'))
        assertEquals("Araba", remoteGoal["name"]?.toString()?.trim('"'))
        assertEquals("100000", remoteGoal["target_amount_minor"]?.toString())
        assertEquals("2", remoteGoal["version"]?.toString())

        // 4. Verify DEBT_PAYMENT wrapper conflict & semantic preservation outside IDs
        val dpCursor = migratedDb.query("SELECT entity_id, local_payload_json, remote_payload_json FROM sync_conflicts WHERE entity_type_code = 'DEBT_PAYMENT'")
        dpCursor.moveToFirst()
        assertEquals(CANONICAL_DEBT_PAY_ID, dpCursor.getString(0))
        val parsedLocalDp = json.parseToJsonElement(dpCursor.getString(1)).jsonObject
        val parsedRemoteDp = json.parseToJsonElement(dpCursor.getString(2)).jsonObject
        dpCursor.close()

        assertEquals(CANONICAL_DEBT_PAY_ID, parsedLocalDp["id"]?.toString()?.trim('"'))
        assertEquals(CANONICAL_DEBT_ID, parsedLocalDp["debt_id"]?.toString()?.trim('"'))
        assertEquals("2000", parsedLocalDp["amount_minor"]?.toString())
        assertEquals("2026-09-02", parsedLocalDp["paid_on"]?.toString()?.trim('"'))

        val remotePayment = parsedRemoteDp["payment"]?.jsonObject
        val remoteDebt = parsedRemoteDp["debt"]?.jsonObject
        assertNotNull(remotePayment)
        assertNotNull(remoteDebt)
        assertEquals(CANONICAL_DEBT_PAY_ID, remotePayment["id"]?.toString()?.trim('"'))
        assertEquals(CANONICAL_DEBT_ID, remotePayment["debt_id"]?.toString()?.trim('"'))
        assertEquals("2000", remotePayment["amount_minor"]?.toString())
        assertEquals("2026-09-02", remotePayment["paid_on"]?.toString()?.trim('"'))

        assertEquals(CANONICAL_DEBT_ID, remoteDebt["id"]?.toString()?.trim('"'))
        assertEquals("Borç 1", remoteDebt["title"]?.toString()?.trim('"'))
        assertEquals("500000", remoteDebt["amount_minor"]?.toString())
        assertEquals("3", remoteDebt["version"]?.toString())
    }

    @Test
    fun test_sync_cursors_canonicalizes_exact_and_workspace_keys_and_preserves_others() {
        val (dbPath, db) = createV20Db("test_sync_cursors.db")
        try {
            // Exact keys in allowlist
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('CATEGORY', 1000, '$COMPACT_CAT_ID')")
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION', 1000, '$COMPACT_TX_ID')")
            // Workspace scoped keys
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('CATEGORY:WORKSPACE:ws1', 1000, '$COMPACT_CAT_ID')")
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION:WORKSPACE:ws1', 1000, '$COMPACT_TX_ID')")
            // Non-allowlist keys must be preserved
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$COMPACT_CAT_ID')")
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('WORKSPACE', 1000, '$COMPACT_CAT_ID')")
            db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('WORKSPACE_MEMBER:ws1', 1000, '$COMPACT_CAT_ID')")
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)

        fun getCursorEntityId(key: String): String {
            val cursor = migratedDb.query("SELECT entity_id FROM sync_cursors WHERE entity_type_code = '$key'")
            cursor.moveToFirst()
            val result = cursor.getString(0)
            cursor.close()
            return result
        }

        assertEquals(CANONICAL_CAT_ID, getCursorEntityId("CATEGORY"))
        assertEquals(CANONICAL_TX_ID, getCursorEntityId("TRANSACTION"))
        assertEquals(CANONICAL_CAT_ID, getCursorEntityId("CATEGORY:WORKSPACE:ws1"))
        assertEquals(CANONICAL_TX_ID, getCursorEntityId("TRANSACTION:WORKSPACE:ws1"))

        // Preserved non-allowlist keys
        assertEquals(COMPACT_CAT_ID, getCursorEntityId("PROFILE"))
        assertEquals(COMPACT_CAT_ID, getCursorEntityId("WORKSPACE"))
        assertEquals(COMPACT_CAT_ID, getCursorEntityId("WORKSPACE_MEMBER:ws1"))
    }

    @Test
    fun test_collision_preflight_fails_closed_and_preserves_raw_v20_records() {
        val (dbPath, db) = createV20Db("test_collision.db")
        try {
            // Insert both compact ID and the same canonical ID into categories
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek 1', 'yemek 1', 'yemek-1', 'EXPENSE', '#ff0000', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek 2', 'yemek 2', 'yemek-2', 'EXPENSE', '#00ff00', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
        } finally {
            db.close()
        }

        // Migration must fail with IllegalStateException due to collision preflight
        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        // Reopen database as raw v20 and verify both raw records are preserved
        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT id, name FROM categories ORDER BY name", null)
            val rows = mutableListOf<Pair<String, String>>()
            while (cursor.moveToNext()) {
                rows.add(cursor.getString(0) to cursor.getString(1))
            }
            cursor.close()
            assertEquals(2, rows.size)
            assertEquals(COMPACT_CAT_ID to "Yemek 1", rows[0])
            assertEquals(CANONICAL_CAT_ID to "Yemek 2", rows[1])
        } finally {
            rawDb.close()
        }
    }

    // =========================================================================
    // Item 2: Subscription Reminder Receipt Tests
    // =========================================================================

    @Test
    fun test_reminder_preserves_historical_key() {
        val (dbPath, db) = createV20Db("rem_hist.db")
        val historicalKey = "sub-v8:2026-09-01:DUE_TODAY"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_SUB_ID', '$USER_ID', NULL, NULL, 'Spotify', 5000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$historicalKey', '$CANONICAL_SUB_ID', '2026-09-01', 'DUE_TODAY', 12345)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts")
        cursor.moveToFirst()
        val key = cursor.getString(0)
        val subId = cursor.getString(1)
        val claimedAt = cursor.getLong(2)
        cursor.close()

        // Byte-for-byte identical preservation of historical key and canonical subId
        assertEquals(historicalKey, key)
        assertEquals(CANONICAL_SUB_ID, subId)
        assertEquals(12345L, claimedAt)
    }

    @Test
    fun test_reminder_canonicalizes_compact_sub() {
        val (dbPath, db) = createV20Db("rem_comp.db")
        val compactKey = "${COMPACT_SUB_ID}_2026-10-01_DUE_SOON"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_SUB_ID', '$USER_ID', NULL, NULL, 'Netflix', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$compactKey', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 77777)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts")
        cursor.moveToFirst()
        val key = cursor.getString(0)
        val subId = cursor.getString(1)
        val claimedAt = cursor.getLong(2)
        cursor.close()

        assertEquals("${CANONICAL_SUB_ID}_2026-10-01_DUE_SOON", key)
        assertEquals(CANONICAL_SUB_ID, subId)
        assertEquals(77777L, claimedAt)
    }

    @Test
    fun test_reminder_collision_fails_closed() {
        val (dbPath, db) = createV20Db("rem_col.db")
        val targetCanonicalKey = "${CANONICAL_SUB_ID}_2026-10-01_DUE_SOON"
        val compactKey = "${COMPACT_SUB_ID}_2026-10-01_DUE_SOON"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_SUB_ID', '$USER_ID', NULL, NULL, 'Netflix 1', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$targetCanonicalKey', '$CANONICAL_SUB_ID', '2026-10-01', 'DUE_SOON', 1000)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$compactKey', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 2000)"
            )
        } finally {
            db.close()
        }

        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        // Verify raw v20 records preserved, nothing deleted
        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts ORDER BY claimed_at_epoch_millis", null)
            val rows = mutableListOf<Triple<String, String, Long>>()
            while (cursor.moveToNext()) {
                rows.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
            cursor.close()
            assertEquals(2, rows.size)
            assertEquals(Triple(targetCanonicalKey, CANONICAL_SUB_ID, 1000L), rows[0])
            assertEquals(Triple(compactKey, COMPACT_SUB_ID, 2000L), rows[1])
        } finally {
            rawDb.close()
        }
    }

    @Test
    fun test_reminder_compact_id_not_in_key_fails_closed() {
        val (dbPath, db) = createV20Db("rem_nokey.db")
        val unrelatedKey = "unrelated_payment_key_2026-10-01"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_SUB_ID', '$USER_ID', NULL, NULL, 'Netflix', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$unrelatedKey', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 44444)"
            )
        } finally {
            db.close()
        }

        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts", null)
            cursor.moveToFirst()
            assertEquals(unrelatedKey, cursor.getString(0))
            assertEquals(COMPACT_SUB_ID, cursor.getString(1))
            assertEquals(44444L, cursor.getLong(2))
            cursor.close()
        } finally {
            rawDb.close()
        }
    }

    @Test
    fun test_reminder_compact_id_in_middle_fails_closed() {
        val (dbPath, db) = createV20Db("rem_mid.db")
        val middleKey = "prefix_${COMPACT_SUB_ID}_2026-10-01_DUE_SOON"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_SUB_ID', '$USER_ID', NULL, NULL, 'Netflix', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$middleKey', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 55555)"
            )
        } finally {
            db.close()
        }

        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts", null)
            cursor.moveToFirst()
            assertEquals(middleKey, cursor.getString(0))
            assertEquals(COMPACT_SUB_ID, cursor.getString(1))
            assertEquals(55555L, cursor.getLong(2))
            cursor.close()
        } finally {
            rawDb.close()
        }
    }

    @Test
    fun test_reminder_compact_prefix_preserves_suffixes() {
        val (dbPath, db) = createV20Db("rem_sfx.db")
        val compactSub2 = "5555555555554555855555555555555a"
        val canonicalSub2 = "55555555-5555-4555-8555-55555555555a"
        val compactSub3 = "5555555555554555855555555555555b"
        val canonicalSub3 = "55555555-5555-4555-8555-55555555555b"

        val keyUnderscore = "${COMPACT_SUB_ID}_2026-10-01_DUE_SOON"
        val keyHash = "${compactSub2}#2026-10-01#DUE_TODAY"
        val keyColon = "${compactSub3}:2026-10-01:REMINDER"

        try {
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_SUB_ID', '$USER_ID', NULL, NULL, 'Netflix 1', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$compactSub2', '$USER_ID', NULL, NULL, 'Netflix 2', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscriptions (id, owner_id, workspace_id, category_id, name, amount_minor, currency_code, frequency_code, `interval`, start_date, end_date, next_renewal_date, is_active, lifecycle_status, trial_end_date, cancellation_date, access_end_date, reminder_enabled, website_url, notes, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$compactSub3', '$USER_ID', NULL, NULL, 'Netflix 3', 15000, 'TRY', 'MONTHLY', 1, '2026-09-01', NULL, '2026-10-01', 1, 'ACTIVE', NULL, NULL, NULL, 1, NULL, NULL, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$keyUnderscore', '$COMPACT_SUB_ID', '2026-10-01', 'DUE_SOON', 11111)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$keyHash', '$compactSub2', '2026-10-01', 'DUE_TODAY', 22222)"
            )
            db.execSQL(
                "INSERT INTO subscription_payment_reminder_receipts (stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis) " +
                    "VALUES ('$keyColon', '$compactSub3', '2026-10-01', 'REMINDER', 33333)"
            )
        } finally {
            db.close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        val cursor = migratedDb.query("SELECT stable_key, subscription_id, claimed_at_epoch_millis FROM subscription_payment_reminder_receipts ORDER BY claimed_at_epoch_millis")
        val results = mutableListOf<Triple<String, String, Long>>()
        while (cursor.moveToNext()) {
            results.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
        }
        cursor.close()

        assertEquals(3, results.size)
        // 1. _ suffix
        assertEquals(Triple("${CANONICAL_SUB_ID}_2026-10-01_DUE_SOON", CANONICAL_SUB_ID, 11111L), results[0])
        // 2. # suffix
        assertEquals(Triple("${canonicalSub2}#2026-10-01#DUE_TODAY", canonicalSub2, 22222L), results[1])
        // 3. : suffix
        assertEquals(Triple("${canonicalSub3}:2026-10-01:REMINDER", canonicalSub3, 33333L), results[2])
    }

    // =========================================================================
    // Item 3: Real Transaction Rollback Test
    // =========================================================================

    @Test
    fun test_real_transaction_rollback() {
        val (dbPath, db) = createV20Db("rollback.db")
        val malformedJson = "this is not valid json {"

        try {
            // Preflight passes cleanly: single compact category, transaction, and outbox op
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek', 'yemek', 'yemek', 'EXPENSE', '#ff0000', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, owner_id, workspace_id, category_id, amount_minor, currency_code, type_code, description, payment_method_code, transaction_date, receipt_path, installment_group_id, installment_number, total_installments, created_at_epoch_ms, paid_by_user_id, participant_user_ids_json, search_text, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error, split_mode, participant_shares_json) " +
                    "VALUES ('$COMPACT_TX_ID', '$USER_ID', NULL, '$COMPACT_CAT_ID', 5000, 'TRY', 'EXPENSE', 'Market', 'CASH', '2026-09-01', NULL, NULL, NULL, NULL, 1000, '$USER_ID', '[]', '', 'SYNCED', 1000, 1000, NULL, 1, 1, NULL, 'EQUAL', '[]')"
            )
            db.execSQL(
                "INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms) " +
                    "VALUES ('$OP_ID_1', 'TRANSACTION', '$COMPACT_TX_ID', 'CREATE', 1, '{\"id\":\"$COMPACT_TX_ID\"}', NULL, 0, 2, 'IN_FLIGHT', 1, NULL, 1000, 1000, 1000)"
            )
            // Late mutation failure: sync_conflicts malformed JSON causes IllegalStateException during Phase 4
            db.execSQL(
                "INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms) " +
                    "VALUES ('TRANSACTION', '$COMPACT_TX_ID', '$OP_ID_1', 1, 2, '$malformedJson', '{\"id\":\"$COMPACT_TX_ID\"}', 1000)"
            )
        } finally {
            db.close()
        }

        // Migration must fail with IllegalStateException during late mutation
        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        // Reopen database as raw v20 and assert that ALL prior mutations were rolled back atomically!
        val rawDb = openRawV20Db(dbPath)
        try {
            // categories PK must still be compact
            val catCursor = rawDb.rawQuery("SELECT id FROM categories", null)
            catCursor.moveToFirst()
            val catId = catCursor.getString(0)
            catCursor.close()
            assertEquals(COMPACT_CAT_ID, catId)

            // transactions PK and FK must still be compact
            val txCursor = rawDb.rawQuery("SELECT id, category_id FROM transactions", null)
            txCursor.moveToFirst()
            val txId = txCursor.getString(0)
            val txCatId = txCursor.getString(1)
            txCursor.close()
            assertEquals(COMPACT_TX_ID, txId)
            assertEquals(COMPACT_CAT_ID, txCatId)

            // sync_operations entity_id and payload must still be compact
            val opCursor = rawDb.rawQuery("SELECT entity_id, payload_json FROM sync_operations WHERE operation_id = '$OP_ID_1'", null)
            opCursor.moveToFirst()
            val opEntityId = opCursor.getString(0)
            val opPayload = opCursor.getString(1)
            opCursor.close()
            assertEquals(COMPACT_TX_ID, opEntityId)
            assertEquals("{\"id\":\"$COMPACT_TX_ID\"}", opPayload)

            // sync_conflicts must still have original malformed payload
            val confCursor = rawDb.rawQuery("SELECT entity_id, local_payload_json FROM sync_conflicts", null)
            confCursor.moveToFirst()
            val confEntityId = confCursor.getString(0)
            val confLocalPayload = confCursor.getString(1)
            confCursor.close()
            assertEquals(COMPACT_TX_ID, confEntityId)
            assertEquals(malformedJson, confLocalPayload)
        } finally {
            rawDb.close()
        }
    }

    // =========================================================================
    // Item 5: Composite PK & Unique Index Collision Tests
    // =========================================================================

    @Test
    fun test_tx_tags_composite_pk_collision() {
        val (dbPath, db) = createV20Db("tx_tags_col.db")

        try {
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek', 'yemek', 'yemek', 'EXPENSE', '#ff0000', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            // Tags: one canonical, one compact that transforms to same canonical
            db.execSQL(
                "INSERT INTO tags (id, owner_id, workspace_id, scope_key, name, normalized_name, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_TAG_ID', '$USER_ID', NULL, '$USER_ID', 'Tag 1', 'tag 1', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO tags (id, owner_id, workspace_id, scope_key, name, normalized_name, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_TAG_ID', '$USER_ID', NULL, '$USER_ID', 'Tag 2', 'tag 2', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            // Transactions: one canonical, one compact
            db.execSQL(
                "INSERT INTO transactions (id, owner_id, workspace_id, category_id, amount_minor, currency_code, type_code, description, payment_method_code, transaction_date, receipt_path, installment_group_id, installment_number, total_installments, created_at_epoch_ms, paid_by_user_id, participant_user_ids_json, search_text, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error, split_mode, participant_shares_json) " +
                    "VALUES ('$CANONICAL_TX_ID', '$USER_ID', NULL, '$CANONICAL_CAT_ID', 5000, 'TRY', 'EXPENSE', 'Market', 'CASH', '2026-09-01', NULL, NULL, NULL, NULL, 1000, '$USER_ID', '[]', '', 'SYNCED', 1000, 1000, NULL, 1, 1, NULL, 'EQUAL', '[]')"
            )
            db.execSQL(
                "INSERT INTO transactions (id, owner_id, workspace_id, category_id, amount_minor, currency_code, type_code, description, payment_method_code, transaction_date, receipt_path, installment_group_id, installment_number, total_installments, created_at_epoch_ms, paid_by_user_id, participant_user_ids_json, search_text, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error, split_mode, participant_shares_json) " +
                    "VALUES ('$COMPACT_TX_ID', '$USER_ID', NULL, '$CANONICAL_CAT_ID', 6000, 'TRY', 'EXPENSE', 'Market 2', 'CASH', '2026-09-01', NULL, NULL, NULL, NULL, 1000, '$USER_ID', '[]', '', 'SYNCED', 1000, 1000, NULL, 1, 1, NULL, 'EQUAL', '[]')"
            )
            // Composite PK: transaction_tags (transaction_id, tag_id)
            // Row 1 is already canonical (CANONICAL_TX_ID, CANONICAL_TAG_ID)
            db.execSQL(
                "INSERT INTO transaction_tags (transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_TX_ID', '$CANONICAL_TAG_ID', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            // Row 2 is compact (COMPACT_TX_ID, COMPACT_TAG_ID) that collides when both are canonicalized
            db.execSQL(
                "INSERT INTO transaction_tags (transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_TX_ID', '$COMPACT_TAG_ID', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
        } finally {
            db.close()
        }

        // Migration must fail closed with IllegalStateException
        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        // Verify raw v20 records preserved
        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT transaction_id, tag_id FROM transaction_tags ORDER BY transaction_id", null)
            val rows = mutableListOf<Pair<String, String>>()
            while (cursor.moveToNext()) {
                rows.add(cursor.getString(0) to cursor.getString(1))
            }
            cursor.close()
            assertEquals(2, rows.size)
            assertEquals(CANONICAL_TX_ID to CANONICAL_TAG_ID, rows[0])
            assertEquals(COMPACT_TX_ID to COMPACT_TAG_ID, rows[1])
        } finally {
            rawDb.close()
        }
    }

    @Test
    fun test_budget_unique_index_collision() {
        val (dbPath, db) = createV20Db("bgt_col.db")

        try {
            // Two categories: one canonical, one compact
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$CANONICAL_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek 1', 'yemek 1', 'yemek-1', 'EXPENSE', '#ff0000', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, slug, type_code, color_hex, icon_key, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('$COMPACT_CAT_ID', '$USER_ID', NULL, '$USER_ID', 'Yemek 2', 'yemek 2', 'yemek-2', 'EXPENSE', '#00ff00', NULL, 0, 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            // Two budgets for the same month and scope_key:
            // Budget 1 uses CANONICAL_CAT_ID
            db.execSQL(
                "INSERT INTO budgets (id, owner_id, workspace_id, scope_key, category_id, month, limit_minor, currency_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('budget-1', '$USER_ID', NULL, '$USER_ID', '$CANONICAL_CAT_ID', '2026-09', 100000, 'TRY', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
            // Budget 2 uses COMPACT_CAT_ID; when COMPACT_CAT_ID canonicalizes, unique index (scope_key, category_id, month) collides!
            db.execSQL(
                "INSERT INTO budgets (id, owner_id, workspace_id, scope_key, category_id, month, limit_minor, currency_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error) " +
                    "VALUES ('budget-2', '$USER_ID', NULL, '$USER_ID', '$COMPACT_CAT_ID', '2026-09', 200000, 'TRY', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)"
            )
        } finally {
            db.close()
        }

        // Migration must fail closed with IllegalStateException
        assertFailsWith<IllegalStateException> {
            migrationHelper.runMigrationsAndValidate(dbPath, 21, true, ANDROID_MIGRATION_20_21)
        }

        // Verify raw v20 records preserved
        val rawDb = openRawV20Db(dbPath)
        try {
            val cursor = rawDb.rawQuery("SELECT id, category_id, limit_minor FROM budgets ORDER BY id", null)
            val rows = mutableListOf<Triple<String, String, Long>>()
            while (cursor.moveToNext()) {
                rows.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
            cursor.close()
            assertEquals(2, rows.size)
            assertEquals(Triple("budget-1", CANONICAL_CAT_ID, 100000L), rows[0])
            assertEquals(Triple("budget-2", COMPACT_CAT_ID, 200000L), rows[1])
        } finally {
            rawDb.close()
        }
    }
}
