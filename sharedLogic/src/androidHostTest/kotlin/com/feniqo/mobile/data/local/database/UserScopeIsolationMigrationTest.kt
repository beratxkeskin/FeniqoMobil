package com.feniqo.mobile.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserScopeIsolationMigrationTest {

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    private fun createV20Db(name: String): Pair<String, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(name)
        val db = migrationHelper.createDatabase(dbFile.absolutePath, 20)
        return dbFile.absolutePath to db
    }

    private fun createV21Db(name: String): Pair<String, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(name)
        val db = migrationHelper.createDatabase(dbFile.absolutePath, 21)
        return dbFile.absolutePath to db
    }

    private companion object {
        const val USER_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val USER_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val WORKSPACE_ID = "wwwwwwww-wwww-4www-8www-wwwwwwwwwwww"

        const val CAT_PERSONAL = "11111111-1111-4111-8111-111111111111"
        const val CAT_WORKSPACE = "11111111-1111-4111-8111-222222222222"

        const val TX_PERSONAL = "22222222-2222-4222-8222-111111111111"
        const val TX_WORKSPACE = "22222222-2222-4222-8222-222222222222"

        const val COMPACT_CAT_ID = "11111111111141118111111111111111"
        const val CANONICAL_CAT_ID = "11111111-1111-4111-8111-111111111111"

        const val COMPACT_TX_ID = "22222222222242228222222222222222"
        const val CANONICAL_TX_ID = "22222222-2222-4222-8222-222222222222"

        const val BUDGET_PERSONAL = "33333333-3333-4333-8333-111111111111"
        const val REC_PERSONAL = "44444444-4444-4444-8444-111111111111"
        const val SUB_PERSONAL = "55555555-5555-4555-8555-111111111111"
        const val ASSET_PERSONAL = "66666666-6666-4666-8666-111111111111"
        const val GOAL_PERSONAL = "77777777-7777-4777-8777-111111111111"
        const val GOAL_CONTRIB_PERSONAL = "77777777-7777-4777-8777-222222222222"
        const val DEBT_PERSONAL = "88888888-8888-4888-8888-111111111111"
        const val DEBT_PAY_PERSONAL = "88888888-8888-4888-8888-222222222222"

        const val OP_CREATE_TX = "op-tx-create-0001"
        const val OP_UPDATE_TX = "op-tx-update-0002"
        const val OP_DELETE_TX = "op-tx-delete-0003"
        const val OP_PROFILE = "op-profile-0001"
        const val OP_WS_TX = "op-ws-tx-0001"
        const val OP_UNPROVEN = "op-unproven-0001"

        const val PAYLOAD_RAW = "{\"id\":\"$TX_PERSONAL\",\"amount_minor\":12500,\"raw_escaped\":\"special/value\\n\\t\"}"
    }

    private fun insertWorkspace(db: SupportSQLiteDatabase, wsId: String, ownerId: String) {
        db.execSQL(
            """
            INSERT INTO workspaces (id, owner_id, name, normalized_name, currency_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version)
            VALUES ('$wsId', '$ownerId', 'Test Workspace', 'test workspace', 'TRY', 1000, 'SYNCED', 1000, 1000, 1)
            """.trimIndent(),
        )
    }

    private fun insertCategory(db: SupportSQLiteDatabase, id: String, ownerId: String?, wsId: String?) {
        val ownerVal = if (ownerId != null) "'$ownerId'" else "NULL"
        val wsVal = if (wsId != null) "'$wsId'" else "NULL"
        db.execSQL(
            """
            INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, type_code, color_hex, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version)
            VALUES ('$id', $ownerVal, $wsVal, 'scope-$id', 'Cat $id', 'cat-$id', 'EXPENSE', '#FFFFFF', 0, 1000, 'SYNCED', 1000, 1000, 1)
            """.trimIndent(),
        )
    }

    private fun insertTransaction(db: SupportSQLiteDatabase, id: String, ownerId: String, wsId: String?, catId: String) {
        val wsVal = if (wsId != null) "'$wsId'" else "NULL"
        db.execSQL(
            """
            INSERT INTO transactions (id, owner_id, workspace_id, amount_minor, currency_code, type_code, category_id, search_text, payment_method_code, transaction_date, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version)
            VALUES ('$id', '$ownerId', $wsVal, 1000, 'TRY', 'EXPENSE', '$catId', 'search', 'CASH', '2026-09-24', 1000, 'SYNCED', 1000, 1000, 1)
            """.trimIndent(),
        )
    }

    // 1. v21 pending CREATE aynı operation ID/payload ile korunur.
    @Test
    fun v21_pending_create_retains_operation_id_and_byte_identical_payload() {
        val (dbPath, db) = createV21Db("test_v21_pending_create")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_CREATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'CREATE', NULL, '$PAYLOAD_RAW', NULL, 0, 1, 'PENDING', 0, NULL, 1500, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cursor = migratedDb.query("SELECT operation_id, sync_scope_key, entity_type_code, entity_id, operation_type_code, payload_json, status_code FROM sync_operations WHERE operation_id = '$OP_CREATE_TX'")
        cursor.use {
            assertTrue(it.moveToFirst(), "Operation row must be preserved")
            assertEquals(OP_CREATE_TX, it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(TX_PERSONAL, it.getString(3))
            assertEquals("CREATE", it.getString(4))
            assertEquals(PAYLOAD_RAW, it.getString(5), "Payload JSON must be byte-for-byte identical")
            assertEquals("PENDING", it.getString(6))
        }
        migratedDb.close()
    }

    // 2. UPDATE, soft-delete, AMBIGUOUS_RESULT ve blocked predecessor/successor metadata’sı korunur.
    @Test
    fun metadata_fields_retained_update_softdelete_ambiguous_result_blocked() {
        val (dbPath, db) = createV21Db("test_metadata_retained")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_UPDATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'UPDATE', 2, '{"id":"$TX_PERSONAL"}', NULL, 0, 1, 'FAILED', 3, 'Network timeout', 2000, 1100, 1200, 'AMBIGUOUS_RESULT')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_DELETE_TX', 'TRANSACTION', '$TX_PERSONAL', 'DELETE', 3, NULL, '$OP_UPDATE_TX', 1, 1, 'BLOCKED', 0, NULL, 3000, 1300, 1300, NULL)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val curUpdate = migratedDb.query("SELECT operation_id, sync_scope_key, operation_type_code, base_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, error_classification, is_blocked FROM sync_operations WHERE operation_id = '$OP_UPDATE_TX'")
        curUpdate.use {
            assertTrue(it.moveToFirst())
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("UPDATE", it.getString(2))
            assertEquals(2L, it.getLong(3))
            assertEquals("FAILED", it.getString(4))
            assertEquals(3, it.getInt(5))
            assertEquals("Network timeout", it.getString(6))
            assertEquals(2000L, it.getLong(7))
            assertEquals("AMBIGUOUS_RESULT", it.getString(8))
            assertEquals(0, it.getInt(9))
        }

        val curDelete = migratedDb.query("SELECT operation_id, sync_scope_key, operation_type_code, predecessor_operation_id, is_blocked, status_code FROM sync_operations WHERE operation_id = '$OP_DELETE_TX'")
        curDelete.use {
            assertTrue(it.moveToFirst())
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("DELETE", it.getString(2))
            assertEquals(OP_UPDATE_TX, it.getString(3))
            assertEquals(1, it.getInt(4))
            assertEquals("BLOCKED", it.getString(5))
        }
        migratedDb.close()
    }

    // 3. Bilinen kişisel operation doğru USER scope’una bağlanır.
    @Test
    fun known_personal_operations_mapped_to_user_scope() {
        val (dbPath, db) = createV21Db("test_known_personal_ops")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL("INSERT INTO budgets (id, owner_id, workspace_id, scope_key, category_id, month, limit_minor, currency_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$BUDGET_PERSONAL', '$USER_A', NULL, 'sk-1', '$CAT_PERSONAL', '2026-09', 50000, 'TRY', 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO recurring_transactions (id, owner_id, workspace_id, amount_minor, currency_code, type_code, category_id, payment_method_code, frequency_code, interval, start_date, is_active, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$REC_PERSONAL', '$USER_A', NULL, 15000, 'TRY', 'EXPENSE', '$CAT_PERSONAL', 'CASH', 'MONTHLY', 1, '2026-01-01', 1, 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO subscriptions (id, owner_id, workspace_id, name, amount_minor, currency_code, frequency_code, interval, start_date, next_renewal_date, is_active, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$SUB_PERSONAL', '$USER_A', NULL, 'Sub 1', 9900, 'TRY', 'MONTHLY', 1, '2026-01-01', '2026-10-01', 1, 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO assets (id, owner_id, name, type_code, current_value_minor, currency_code, auto_track, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$ASSET_PERSONAL', '$USER_A', 'Asset 1', 'STOCK', 100000, 'TRY', 0, 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO goals (id, owner_id, workspace_id, name, target_amount_minor, current_amount_minor, currency_code, target_date, color_hex, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$GOAL_PERSONAL', '$USER_A', NULL, 'Goal 1', 500000, 10000, 'TRY', '2027-01-01', '#FFFFFF', 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO goal_contributions (id, goal_id, amount_minor, currency_code, direction_code, occurred_on, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$GOAL_CONTRIB_PERSONAL', '$GOAL_PERSONAL', 5000, 'TRY', 'INFLOW', '2026-09-01', 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO debts (id, owner_id, workspace_id, title, amount_minor, currency_code, type_code, due_date, status_code, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$DEBT_PERSONAL', '$USER_A', NULL, 'Debt 1', 80000, 'TRY', 'LENT', '2026-12-01', 'ACTIVE', 1000, 'SYNCED', 1000, 1000, 1)")
        db.execSQL("INSERT INTO debt_payments (id, debt_id, amount_minor, currency_code, paid_on, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version) VALUES ('$DEBT_PAY_PERSONAL', '$DEBT_PERSONAL', 10000, 'TRY', '2026-09-15', 1000, 'SYNCED', 1000, 1000, 1)")

        val ops = listOf(
            Triple("op-profile", "PROFILE", USER_A),
            Triple("op-cat", "CATEGORY", CAT_PERSONAL),
            Triple("op-tx", "TRANSACTION", TX_PERSONAL),
            Triple("op-budget", "BUDGET", BUDGET_PERSONAL),
            Triple("op-rec", "RECURRING_TRANSACTION", REC_PERSONAL),
            Triple("op-sub", "SUBSCRIPTION", SUB_PERSONAL),
            Triple("op-asset", "ASSET", ASSET_PERSONAL),
            Triple("op-goal", "GOAL", GOAL_PERSONAL),
            Triple("op-gcontrib", "GOAL_CONTRIBUTION", GOAL_CONTRIB_PERSONAL),
            Triple("op-debt", "DEBT", DEBT_PERSONAL),
            Triple("op-dpay", "DEBT_PAYMENT", DEBT_PAY_PERSONAL),
        )

        for ((opId, entityType, entityId) in ops) {
            db.execSQL(
                """
                INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
                VALUES ('$opId', '$entityType', '$entityId', 'CREATE', NULL, '{}', NULL, 0, 1, 'PENDING', 0, NULL, 1000, 1000, 1000, NULL)
                """.trimIndent(),
            )
        }
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT operation_id, sync_scope_key FROM sync_operations")
        cur.use {
            var count = 0
            while (it.moveToNext()) {
                count++
                val opId = it.getString(0)
                val scope = it.getString(1)
                assertEquals("USER:$USER_A", scope, "Op $opId must be mapped to USER:$USER_A")
            }
            assertEquals(ops.size, count)
        }
        migratedDb.close()
    }

    // 4. Workspace actor’ü kanıtlanamayan operation silinmez ve LEGACY_UNRESOLVED kalır.
    @Test
    fun workspace_operations_unproven_actor_retained_in_legacy_unresolved() {
        val (dbPath, db) = createV21Db("test_workspace_unproven")
        insertWorkspace(db, WORKSPACE_ID, USER_A)
        insertCategory(db, CAT_WORKSPACE, USER_A, WORKSPACE_ID)
        insertTransaction(db, TX_WORKSPACE, USER_A, WORKSPACE_ID, CAT_WORKSPACE)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_WS_TX', 'TRANSACTION', '$TX_WORKSPACE', 'CREATE', NULL, '{"workspace_id":"$WORKSPACE_ID"}', NULL, 0, 1, 'PENDING', 0, NULL, 1000, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('op-ws-entity', 'WORKSPACE', '$WORKSPACE_ID', 'UPDATE', 1, '{}', NULL, 0, 1, 'PENDING', 0, NULL, 1000, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT operation_id, sync_scope_key FROM sync_operations WHERE operation_id IN ('$OP_WS_TX', 'op-ws-entity')")
        cur.use {
            var count = 0
            while (it.moveToNext()) {
                count++
                val scope = it.getString(1)
                assertEquals("LEGACY_UNRESOLVED", scope, "Workspace operations must stay LEGACY_UNRESOLVED")
            }
            assertEquals(2, count, "Both operations must be preserved")
        }
        migratedDb.close()
    }

    // 5. Conflict bağlı operation scope’unu miras alır.
    @Test
    fun conflicts_inherit_scope_from_parent_operation() {
        val (dbPath, db) = createV21Db("test_conflict_inherits_scope")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_UPDATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'UPDATE', 1, '{}', NULL, 0, 1, 'FAILED', 1, 'conflict', 1000, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('TRANSACTION', '$TX_PERSONAL', '$OP_UPDATE_TX', 1, 2, '{"local":1}', '{"remote":2}', 5000)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, operation_id, local_payload_json, remote_payload_json FROM sync_conflicts")
        cur.use {
            assertTrue(it.moveToFirst(), "Conflict must be preserved")
            assertEquals("USER:$USER_A", it.getString(0), "Conflict must inherit parent operation USER scope")
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals(TX_PERSONAL, it.getString(2))
            assertEquals(OP_UPDATE_TX, it.getString(3))
            assertEquals("{\"local\":1}", it.getString(4))
            assertEquals("{\"remote\":2}", it.getString(5))
        }
        migratedDb.close()
    }

    // 6. Bilinmeyen conflict silinmeden unresolved kalır.
    @Test
    fun unknown_conflicts_retained_in_legacy_unresolved() {
        val (dbPath, db) = createV21Db("test_unknown_conflict")
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('TRANSACTION', 'unknown-tx-id', 'unknown-op-id', 1, 2, '{"loc":true}', '{"rem":true}', 6000)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, operation_id FROM sync_conflicts")
        cur.use {
            assertTrue(it.moveToFirst(), "Unknown conflict must not be deleted")
            assertEquals("LEGACY_UNRESOLVED", it.getString(0), "Unknown conflict must be LEGACY_UNRESOLVED")
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals("unknown-tx-id", it.getString(2))
            assertEquals("unknown-op-id", it.getString(3))
        }
        migratedDb.close()
    }

    // 7. PROFILE cursor kanıtıyla cursor’lar kullanıcı scope’una taşınır.
    @Test
    fun profile_cursor_proof_migrates_cursors_to_user_scope() {
        val (dbPath, db) = createV21Db("test_profile_cursor_proof")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$USER_A')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('CATEGORY', 2000, '$CAT_PERSONAL')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION', 3000, '$TX_PERSONAL')")
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id FROM sync_cursors ORDER BY entity_type_code")
        cur.use {
            var count = 0
            while (it.moveToNext()) {
                count++
                val scope = it.getString(0)
                assertEquals("USER:$USER_A", scope, "All cursors must be migrated to USER:$USER_A")
            }
            assertEquals(3, count, "All 3 cursors must be preserved")
        }
        migratedDb.close()
    }

    // 8. Kanıtsız cursor silinmez ve unresolved kalır.
    @Test
    fun unproven_cursors_retained_in_legacy_unresolved() {
        val (dbPath, db) = createV21Db("test_unproven_cursor")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('CATEGORY', 2000, '$CAT_PERSONAL')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION', 3000, '$TX_PERSONAL')")
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        val cur = migratedDb.query("SELECT sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id FROM sync_cursors")
        cur.use {
            var count = 0
            while (it.moveToNext()) {
                count++
                val scope = it.getString(0)
                assertEquals("LEGACY_UNRESOLVED", scope, "Cursors without PROFILE proof must stay LEGACY_UNRESOLVED")
            }
            assertEquals(2, count, "Both cursors must be preserved without deletion")
        }
        migratedDb.close()
    }

    // 9. A ve B için aynı entity_type cursor satırları v22 şemasında birlikte bulunabilir.
    @Test
    fun user_a_and_user_b_cursors_coexist_in_v22_schema() {
        val (dbPath, db) = createV21Db("test_coexist_cursors")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$USER_A')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('CATEGORY', 2000, '$CAT_PERSONAL')")
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)

        // Insert User B's cursors directly into the v22 schema:
        migratedDb.execSQL("INSERT INTO sync_cursors (sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('USER:$USER_B', 'PROFILE', 5000, '$USER_B')")
        migratedDb.execSQL("INSERT INTO sync_cursors (sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('USER:$USER_B', 'CATEGORY', 6000, '$CAT_PERSONAL')")

        val cur = migratedDb.query("SELECT sync_scope_key, entity_type_code, updated_at_epoch_ms FROM sync_cursors WHERE entity_type_code = 'CATEGORY' ORDER BY sync_scope_key")
        cur.use {
            assertTrue(it.moveToNext())
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals("CATEGORY", it.getString(1))
            assertEquals(2000L, it.getLong(2))

            assertTrue(it.moveToNext())
            assertEquals("USER:$USER_B", it.getString(0))
            assertEquals("CATEGORY", it.getString(1))
            assertEquals(6000L, it.getLong(2))
        }
        migratedDb.close()
    }

    // 10. Migration sonunda satır sayıları ve kritik alanlar birebir korunur.
    @Test
    fun row_counts_and_critical_fields_strictly_preserved() {
        val (dbPath, db) = createV21Db("test_counts_preserved")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        // 1. PENDING CREATE: NULL baseVersion, NULL predecessor, NULL errorClassification, raw payload
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_CREATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'CREATE', NULL, '$PAYLOAD_RAW', NULL, 0, 1, 'PENDING', 0, NULL, 1500, 1000, 1000, NULL)
            """.trimIndent(),
        )

        // 2. FAILED/AMBIGUOUS_RESULT UPDATE: non-null baseVersion, attemptCount, lastError, errorClassification
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_UPDATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'UPDATE', 2, '{"id":"$TX_PERSONAL","name":"Updated"}', NULL, 0, 1, 'FAILED', 3, 'Connection reset', 2500, 1100, 1200, 'AMBIGUOUS_RESULT')
            """.trimIndent(),
        )

        // 3. BLOCKED DELETE successor: NULL payload, blocked by OP_UPDATE_TX, is_blocked = 1
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_DELETE_TX', 'TRANSACTION', '$TX_PERSONAL', 'DELETE', 3, NULL, '$OP_UPDATE_TX', 1, 1, 'BLOCKED', 0, NULL, 3500, 1300, 1300, NULL)
            """.trimIndent(),
        )

        // 4. UNPROVEN operation: unknown entity type and id, must NOT be deleted
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_UNPROVEN', 'UNKNOWN_TYPE', 'unknown-id', 'CREATE', 1, '{"raw":"unproven"}', NULL, 0, 1, 'PENDING', 0, NULL, 1500, 1000, 1000, NULL)
            """.trimIndent(),
        )

        // Cursors: PROFILE, TRANSACTION, UNPROVEN_TYPE
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$USER_A')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION', 2000, '$TX_PERSONAL')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('UNPROVEN_TYPE', 3000, 'unproven-cursor-entity')")

        // Conflicts: bound conflict & unproven conflict
        val localPayload1 = "{\"local\":{\"nested\":\"val\\n\\t\"}}"
        val remotePayload1 = "{\"remote\":{\"nested\":\"val\\n\\t\"}}"
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('TRANSACTION', '$TX_PERSONAL', '$OP_UPDATE_TX', 2, 3, '$localPayload1', '$remotePayload1', 8000)
            """.trimIndent(),
        )

        val localPayload2 = "{\"loc_unp\":true}"
        val remotePayload2 = "{\"rem_unp\":true}"
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('UNKNOWN_TYPE', 'unknown-conf-entity', 'unknown-op-id', 1, 2, '$localPayload2', '$remotePayload2', 9000)
            """.trimIndent(),
        )

        // Record v21 counts before migration
        val beforeOpsCount = db.query("SELECT COUNT(*) FROM sync_operations").use { it.moveToFirst(); it.getLong(0) }
        val beforeCurCount = db.query("SELECT COUNT(*) FROM sync_cursors").use { it.moveToFirst(); it.getLong(0) }
        val beforeConfCount = db.query("SELECT COUNT(*) FROM sync_conflicts").use { it.moveToFirst(); it.getLong(0) }
        assertEquals(4L, beforeOpsCount)
        assertEquals(3L, beforeCurCount)
        assertEquals(2L, beforeConfCount)

        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)

        // 1. Satır sayılarını karşılaştır
        val afterOpsCount = migratedDb.query("SELECT COUNT(*) FROM sync_operations").use { it.moveToFirst(); it.getLong(0) }
        val afterCurCount = migratedDb.query("SELECT COUNT(*) FROM sync_cursors").use { it.moveToFirst(); it.getLong(0) }
        val afterConfCount = migratedDb.query("SELECT COUNT(*) FROM sync_conflicts").use { it.moveToFirst(); it.getLong(0) }
        assertEquals(beforeOpsCount, afterOpsCount, "sync_operations satır sayısı birebir korunmalıdır")
        assertEquals(beforeCurCount, afterCurCount, "sync_cursors satır sayısı birebir korunmalıdır")
        assertEquals(beforeConfCount, afterConfCount, "sync_conflicts satır sayısı birebir korunmalıdır")

        // 2. sync_operations için eklenen sync_scope_key dışındaki bütün v21 sütunlarını alan alan karşılaştır
        // Row 1: OP_CREATE_TX (PENDING CREATE, NULL baseVersion/predecessor/errorClassification)
        val curOp1 = migratedDb.query("SELECT operation_id, sync_scope_key, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification FROM sync_operations WHERE operation_id = '$OP_CREATE_TX'")
        curOp1.use {
            assertTrue(it.moveToFirst())
            assertEquals(OP_CREATE_TX, it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(TX_PERSONAL, it.getString(3))
            assertEquals("CREATE", it.getString(4))
            assertTrue(it.isNull(5), "base_version NULL olmalıdır")
            assertEquals(PAYLOAD_RAW, it.getString(6), "payload_json byte-for-byte korunmalıdır")
            assertTrue(it.isNull(7), "predecessor_operation_id NULL olmalıdır")
            assertEquals(0, it.getInt(8))
            assertEquals(1, it.getInt(9))
            assertEquals("PENDING", it.getString(10))
            assertEquals(0, it.getInt(11))
            assertTrue(it.isNull(12), "last_error NULL olmalıdır")
            assertEquals(1500L, it.getLong(13))
            assertEquals(1000L, it.getLong(14))
            assertEquals(1000L, it.getLong(15))
            assertTrue(it.isNull(16), "error_classification NULL olmalıdır")
        }

        // Row 2: OP_UPDATE_TX (FAILED UPDATE, AMBIGUOUS_RESULT)
        val curOp2 = migratedDb.query("SELECT operation_id, sync_scope_key, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification FROM sync_operations WHERE operation_id = '$OP_UPDATE_TX'")
        curOp2.use {
            assertTrue(it.moveToFirst())
            assertEquals(OP_UPDATE_TX, it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(TX_PERSONAL, it.getString(3))
            assertEquals("UPDATE", it.getString(4))
            assertEquals(2L, it.getLong(5))
            assertEquals("{\"id\":\"$TX_PERSONAL\",\"name\":\"Updated\"}", it.getString(6))
            assertTrue(it.isNull(7))
            assertEquals(0, it.getInt(8))
            assertEquals(1, it.getInt(9))
            assertEquals("FAILED", it.getString(10))
            assertEquals(3, it.getInt(11))
            assertEquals("Connection reset", it.getString(12))
            assertEquals(2500L, it.getLong(13))
            assertEquals(1100L, it.getLong(14))
            assertEquals(1200L, it.getLong(15))
            assertEquals("AMBIGUOUS_RESULT", it.getString(16))
        }

        // Row 3: OP_DELETE_TX (BLOCKED DELETE successor, is_blocked = 1, predecessor = OP_UPDATE_TX)
        val curOp3 = migratedDb.query("SELECT operation_id, sync_scope_key, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification FROM sync_operations WHERE operation_id = '$OP_DELETE_TX'")
        curOp3.use {
            assertTrue(it.moveToFirst())
            assertEquals(OP_DELETE_TX, it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(TX_PERSONAL, it.getString(3))
            assertEquals("DELETE", it.getString(4))
            assertEquals(3L, it.getLong(5))
            assertTrue(it.isNull(6), "payload_json NULL olmalıdır")
            assertEquals(OP_UPDATE_TX, it.getString(7))
            assertEquals(1, it.getInt(8))
            assertEquals(1, it.getInt(9))
            assertEquals("BLOCKED", it.getString(10))
            assertEquals(0, it.getInt(11))
            assertTrue(it.isNull(12))
            assertEquals(3500L, it.getLong(13))
            assertEquals(1300L, it.getLong(14))
            assertEquals(1300L, it.getLong(15))
            assertTrue(it.isNull(16))
        }

        // Row 4: OP_UNPROVEN (Kanıtsız operasyon silinmemeli ve LEGACY_UNRESOLVED olmalı)
        val curOp4 = migratedDb.query("SELECT operation_id, sync_scope_key, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification FROM sync_operations WHERE operation_id = '$OP_UNPROVEN'")
        curOp4.use {
            assertTrue(it.moveToFirst(), "Kanıtsız operasyon silinmemelidir")
            assertEquals(OP_UNPROVEN, it.getString(0))
            assertEquals("LEGACY_UNRESOLVED", it.getString(1))
            assertEquals("UNKNOWN_TYPE", it.getString(2))
            assertEquals("unknown-id", it.getString(3))
            assertEquals("CREATE", it.getString(4))
            assertEquals(1L, it.getLong(5))
            assertEquals("{\"raw\":\"unproven\"}", it.getString(6))
            assertTrue(it.isNull(7))
            assertEquals(0, it.getInt(8))
            assertEquals(1, it.getInt(9))
            assertEquals("PENDING", it.getString(10))
            assertEquals(0, it.getInt(11))
            assertTrue(it.isNull(12))
            assertEquals(1500L, it.getLong(13))
            assertEquals(1000L, it.getLong(14))
            assertEquals(1000L, it.getLong(15))
            assertTrue(it.isNull(16))
        }

        // 3. sync_conflicts local/remote payload JSON değerlerini birebir doğrula ve kanıtsız conflict'in silinmediğini teyit et
        val curConf1 = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms FROM sync_conflicts WHERE operation_id = '$OP_UPDATE_TX'")
        curConf1.use {
            assertTrue(it.moveToFirst(), "Bağlı conflict silinmemelidir")
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals(TX_PERSONAL, it.getString(2))
            assertEquals(OP_UPDATE_TX, it.getString(3))
            assertEquals(2L, it.getLong(4))
            assertEquals(3L, it.getLong(5))
            assertEquals(localPayload1, it.getString(6), "local_payload_json byte-for-byte korunmalıdır")
            assertEquals(remotePayload1, it.getString(7), "remote_payload_json byte-for-byte korunmalıdır")
            assertEquals(8000L, it.getLong(8))
        }

        val curConf2 = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms FROM sync_conflicts WHERE operation_id = 'unknown-op-id'")
        curConf2.use {
            assertTrue(it.moveToFirst(), "Kanıtsız conflict silinmemelidir")
            assertEquals("LEGACY_UNRESOLVED", it.getString(0))
            assertEquals("UNKNOWN_TYPE", it.getString(1))
            assertEquals("unknown-conf-entity", it.getString(2))
            assertEquals("unknown-op-id", it.getString(3))
            assertEquals(1L, it.getLong(4))
            assertEquals(2L, it.getLong(5))
            assertEquals(localPayload2, it.getString(6), "Kanıtsız local_payload_json byte-for-byte korunmalıdır")
            assertEquals(remotePayload2, it.getString(7), "Kanıtsız remote_payload_json byte-for-byte korunmalıdır")
            assertEquals(9000L, it.getLong(8))
        }

        // 4. Kanıtsız cursor satırının silinmediğini doğrula
        val curCurUnproven = migratedDb.query("SELECT sync_scope_key, entity_type_code, updated_at_epoch_ms, entity_id FROM sync_cursors WHERE entity_type_code = 'UNPROVEN_TYPE'")
        curCurUnproven.use {
            assertTrue(it.moveToFirst(), "Kanıtsız cursor satırı silinmemelidir")
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals("UNPROVEN_TYPE", it.getString(1))
            assertEquals(3000L, it.getLong(2))
            assertEquals("unproven-cursor-entity", it.getString(3))
        }

        migratedDb.close()
    }

    // 11. runMigrationsAndValidate(..., 22, true, ANDROID_MIGRATION_21_22) geçer.
    @Test
    fun run_migrations_and_validate_v21_to_v22_succeeds() {
        val (dbPath, db) = createV21Db("test_validate_succeeds")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$USER_A')")
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        assertNotNull(migratedDb)
        migratedDb.close()
    }

    // 12. DB close/reopen sonrasında scope ve metadata korunur.
    @Test
    fun db_close_and_reopen_persists_scope_and_metadata() {
        val (dbPath, db) = createV21Db("test_reopen_persists")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('$OP_CREATE_TX', 'TRANSACTION', '$TX_PERSONAL', 'CREATE', NULL, '$PAYLOAD_RAW', NULL, 0, 1, 'PENDING', 0, NULL, 1500, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)
        migratedDb.close()

        // Reopen with standard SQLiteDatabase:
        val rawDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
        val cur = rawDb.rawQuery("SELECT operation_id, sync_scope_key, payload_json FROM sync_operations WHERE operation_id = '$OP_CREATE_TX'", null)
        cur.use {
            assertTrue(it.moveToFirst(), "Row must exist after close and reopen")
            assertEquals(OP_CREATE_TX, it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals(PAYLOAD_RAW, it.getString(2))
        }
        rawDb.close()
    }

    // 13. Gerçek birleşik upgrade testi: v20 -> v21 (canonicalization) -> v22 (user-scope isolation)
    @Test
    fun combined_v20_to_v22_migration_canonicalizes_and_assigns_user_scope() {
        val (dbPath, db) = createV20Db("test_combined_v20_to_v22")

        // v20 Category & Transaction with compact UUIDs and canonical user owner
        db.execSQL(
            """
            INSERT INTO categories (id, owner_id, workspace_id, scope_key, name, normalized_name, type_code, color_hex, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version)
            VALUES ('$COMPACT_CAT_ID', '$USER_A', NULL, 'scope-$COMPACT_CAT_ID', 'Cat 1', 'cat 1', 'EXPENSE', '#FFFFFF', 0, 1000, 'SYNCED', 1000, 1000, 1)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO transactions (id, owner_id, workspace_id, amount_minor, currency_code, type_code, category_id, search_text, payment_method_code, transaction_date, created_at_epoch_ms, sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, version)
            VALUES ('$COMPACT_TX_ID', '$USER_A', NULL, 15000, 'TRY', 'EXPENSE', '$COMPACT_CAT_ID', 'search', 'CASH', '2026-09-24', 1000, 'SYNCED', 1000, 1000, 1)
            """.trimIndent(),
        )

        // v20 sync_operations with compact entity_id and compact top-level payload id
        val payloadV20 = "{\"id\":\"$COMPACT_TX_ID\",\"amount_minor\":15000}"
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('op-v20-update', 'TRANSACTION', '$COMPACT_TX_ID', 'UPDATE', 1, '$payloadV20', NULL, 0, 2, 'FAILED', 2, 'Temporary timeout', 2500, 1000, 1200, 'AMBIGUOUS_RESULT')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('op-v20-delete', 'TRANSACTION', '$COMPACT_TX_ID', 'DELETE', 2, NULL, 'op-v20-update', 1, 1, 'BLOCKED', 0, NULL, 3500, 1300, 1300, NULL)
            """.trimIndent(),
        )

        // v20 sync_cursors
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, '$USER_A')")
        db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('TRANSACTION', 2000, '$COMPACT_TX_ID')")

        // v20 sync_conflicts
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('TRANSACTION', '$COMPACT_TX_ID', 'op-v20-update', 1, 2, '{"id":"$COMPACT_TX_ID"}', '{"id":"$COMPACT_TX_ID"}', 4000)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbPath,
            22,
            true,
            ANDROID_MIGRATION_20_21,
            ANDROID_MIGRATION_21_22,
        )

        // Verify categories & transactions canonicalization
        val curCat = migratedDb.query("SELECT id FROM categories")
        curCat.use {
            assertTrue(it.moveToFirst())
            assertEquals(CANONICAL_CAT_ID, it.getString(0))
        }

        val curTx = migratedDb.query("SELECT id, category_id FROM transactions")
        curTx.use {
            assertTrue(it.moveToFirst())
            assertEquals(CANONICAL_TX_ID, it.getString(0))
            assertEquals(CANONICAL_CAT_ID, it.getString(1))
        }

        // Verify sync_operations: canonical UUID, correct USER scope, and preserved fields
        val curOpUpdate = migratedDb.query(
            """
            SELECT operation_id, sync_scope_key, entity_type_code, entity_id,
                   operation_type_code, base_version, payload_json, predecessor_operation_id,
                   is_blocked, protocol_version, status_code, attempt_count,
                   last_error, next_attempt_at_epoch_ms, created_at_epoch_ms,
                   updated_at_epoch_ms, error_classification
            FROM sync_operations WHERE operation_id = 'op-v20-update'
            """.trimIndent(),
        )
        curOpUpdate.use {
            assertTrue(it.moveToFirst())
            assertEquals("op-v20-update", it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(CANONICAL_TX_ID, it.getString(3))
            assertEquals("UPDATE", it.getString(4))
            assertEquals(1L, it.getLong(5))
            val expectedPayload = "{\"id\":\"$CANONICAL_TX_ID\",\"amount_minor\":15000}"
            assertEquals(expectedPayload, it.getString(6))
            assertTrue(it.isNull(7))
            assertEquals(0, it.getInt(8))
            assertEquals(2, it.getInt(9))
            assertEquals("FAILED", it.getString(10))
            assertEquals(2, it.getInt(11))
            assertEquals("Temporary timeout", it.getString(12))
            assertEquals(2500L, it.getLong(13))
            assertEquals(1000L, it.getLong(14))
            assertEquals(1200L, it.getLong(15))
            assertEquals("AMBIGUOUS_RESULT", it.getString(16))
        }

        val curOpDelete = migratedDb.query(
            """
            SELECT operation_id, sync_scope_key, entity_type_code, entity_id,
                   operation_type_code, base_version, payload_json, predecessor_operation_id,
                   is_blocked, status_code, attempt_count, next_attempt_at_epoch_ms,
                   created_at_epoch_ms, updated_at_epoch_ms, error_classification
            FROM sync_operations WHERE operation_id = 'op-v20-delete'
            """.trimIndent(),
        )
        curOpDelete.use {
            assertTrue(it.moveToFirst())
            assertEquals("op-v20-delete", it.getString(0))
            assertEquals("USER:$USER_A", it.getString(1))
            assertEquals("TRANSACTION", it.getString(2))
            assertEquals(CANONICAL_TX_ID, it.getString(3))
            assertEquals("DELETE", it.getString(4))
            assertEquals(2L, it.getLong(5))
            assertTrue(it.isNull(6))
            assertEquals("op-v20-update", it.getString(7))
            assertEquals(1, it.getInt(8))
            assertEquals("BLOCKED", it.getString(9))
            assertEquals(0, it.getInt(10))
            assertEquals(3500L, it.getLong(11))
            assertEquals(1300L, it.getLong(12))
            assertEquals(1300L, it.getLong(13))
            assertTrue(it.isNull(14))
        }

        // Verify sync_cursors: canonical entity_id and correct USER scope
        val curCursors = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, updated_at_epoch_ms FROM sync_cursors ORDER BY entity_type_code")
        curCursors.use {
            assertTrue(it.moveToNext())
            assertEquals("PROFILE", it.getString(1))
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals(USER_A, it.getString(2))
            assertEquals(1000L, it.getLong(3))

            assertTrue(it.moveToNext())
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals(CANONICAL_TX_ID, it.getString(2))
            assertEquals(2000L, it.getLong(3))
        }

        // Verify sync_conflicts: canonical entity_id, correct inherited USER scope, and preserved payload
        val curConflict = migratedDb.query("SELECT sync_scope_key, entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms FROM sync_conflicts")
        curConflict.use {
            assertTrue(it.moveToFirst())
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals(CANONICAL_TX_ID, it.getString(2))
            assertEquals("op-v20-update", it.getString(3))
            assertEquals(1L, it.getLong(4))
            assertEquals(2L, it.getLong(5))
            assertEquals("{\"id\":\"$CANONICAL_TX_ID\"}", it.getString(6))
            assertEquals("{\"id\":\"$CANONICAL_TX_ID\"}", it.getString(7))
            assertEquals(4000L, it.getLong(8))
        }

        migratedDb.close()
    }

    // 14. v22 şemasında aynı entity_type/entity_id için USER:A ve USER:B conflict kayıtları birlikte bulunabilir
    @Test
    fun user_a_and_user_b_conflicts_coexist_for_same_entity_in_v22_schema() {
        val (dbPath, db) = createV21Db("test_coexist_conflicts")
        insertCategory(db, CAT_PERSONAL, USER_A, null)
        insertTransaction(db, TX_PERSONAL, USER_A, null, CAT_PERSONAL)

        db.execSQL(
            """
            INSERT INTO sync_operations (operation_id, entity_type_code, entity_id, operation_type_code, base_version, payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code, attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms, error_classification)
            VALUES ('op-user-a-conflict', 'TRANSACTION', '$TX_PERSONAL', 'UPDATE', 1, '{}', NULL, 0, 1, 'FAILED', 1, NULL, 1000, 1000, 1000, NULL)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sync_conflicts (entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('TRANSACTION', '$TX_PERSONAL', 'op-user-a-conflict', 1, 2, '{"userA":true}', '{"remote":true}', 5000)
            """.trimIndent(),
        )
        db.close()

        val migratedDb = migrationHelper.runMigrationsAndValidate(dbPath, 22, true, ANDROID_MIGRATION_21_22)

        // User A's conflict migrated to USER:A
        // Now insert User B's conflict for the EXACT SAME entity_type_code and entity_id:
        migratedDb.execSQL(
            """
            INSERT INTO sync_conflicts (sync_scope_key, entity_type_code, entity_id, operation_id, local_version, remote_version, local_payload_json, remote_payload_json, detected_at_epoch_ms)
            VALUES ('USER:$USER_B', 'TRANSACTION', '$TX_PERSONAL', 'op-user-b-conflict', 10, 11, '{"userB":true}', '{"remoteB":true}', 7000)
            """.trimIndent(),
        )

        val cur = migratedDb.query(
            """
            SELECT sync_scope_key, entity_type_code, entity_id, operation_id, local_payload_json
            FROM sync_conflicts
            WHERE entity_type_code = 'TRANSACTION' AND entity_id = '$TX_PERSONAL'
            ORDER BY sync_scope_key
            """.trimIndent(),
        )
        cur.use {
            assertTrue(it.moveToNext(), "User A conflict must exist")
            assertEquals("USER:$USER_A", it.getString(0))
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals(TX_PERSONAL, it.getString(2))
            assertEquals("op-user-a-conflict", it.getString(3))
            assertEquals("{\"userA\":true}", it.getString(4))

            assertTrue(it.moveToNext(), "User B conflict must coexist without primary key violation")
            assertEquals("USER:$USER_B", it.getString(0))
            assertEquals("TRANSACTION", it.getString(1))
            assertEquals(TX_PERSONAL, it.getString(2))
            assertEquals("op-user-b-conflict", it.getString(3))
            assertEquals("{\"userB\":true}", it.getString(4))
        }
        migratedDb.close()
    }
}
