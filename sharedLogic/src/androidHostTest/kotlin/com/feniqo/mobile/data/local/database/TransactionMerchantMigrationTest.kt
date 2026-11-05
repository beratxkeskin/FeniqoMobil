package com.feniqo.mobile.data.local.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantMigrationTest {
    @get:Rule
    val migrationHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            FeniqoDatabase::class.java,
        )

    private fun createV22Db(name: String): Pair<String, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(name)
        val db = migrationHelper.createDatabase(dbFile.absolutePath, 22)
        return dbFile.absolutePath to db
    }

    private companion object {
        const val USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val CAT_ID = "11111111-1111-4111-8111-111111111111"
        const val TRX_ID = "tx-v22-mig-001"
    }

    @Test
    fun migration_22_to_23_preservesLegacyDataAndLeavesMerchantColumnsNull() {
        val (dbPath, db) = createV22Db("test_migration_22_23.db")

        // 1. Kategoriyi ekle (FK için)
        db.execSQL(
            """
            INSERT INTO categories (
                id, owner_id, workspace_id, scope_key, name, normalized_name, type_code,
                color_hex, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                local_updated_at_epoch_ms, version
            ) VALUES (
                '$CAT_ID', '$USER_ID', NULL, 'scope-$CAT_ID', 'Abonelik', 'abonelik', 'EXPENSE',
                '#FF0000', 0, 1000, 'SYNCED', 1000, 1000, 1
            )
            """.trimIndent(),
        )

        // 2. v22 zengin alanlı transaction ekle
        db.execSQL(
            """
            INSERT INTO transactions (
                id, owner_id, workspace_id, paid_by_user_id, participant_user_ids_json,
                amount_minor, currency_code, type_code, category_id, description, search_text,
                payment_method_code, transaction_date, receipt_path, installment_number,
                total_installments, installment_group_id, created_at_epoch_ms, note, split_mode,
                participant_shares_json, sync_status, updated_at_epoch_ms,
                local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
            ) VALUES (
                '$TRX_ID', '$USER_ID', NULL, '$USER_ID', '["$USER_ID"]',
                4599, 'TRY', 'EXPENSE', '$CAT_ID', 'Netflix Uyelik', 'netflix uyelik aylik',
                'CREDIT_CARD', '2026-10-14', 'receipts/test.jpg', 1,
                3, 'grp-001', 1700000000000, 'Aylik plan', 'CUSTOM',
                '[{"userId":"$USER_ID","amountMinor":4599}]', 'SYNCED', 1700000000000,
                1700000000000, NULL, 5, 4, NULL
            )
            """.trimIndent(),
        )

        db.close()

        // 3. v22 -> v23 migration'ını çalıştır ve şemayı 23.json ile doğrula
        val migratedDb =
            migrationHelper.runMigrationsAndValidate(
                dbPath,
                23,
                true,
                ANDROID_MIGRATION_22_23,
            )

        // 4. Verileri oku ve doğrula
        val cursor =
            migratedDb.query(
                """
                SELECT
                    id, owner_id, amount_minor, currency_code, type_code, category_id,
                    description, note, installment_number, total_installments, installment_group_id,
                    split_mode, participant_shares_json, sync_status, version, base_version,
                    merchant_id, merchant_match_source_code, merchant_confidence, merchant_catalog_version
                FROM transactions WHERE id = '$TRX_ID'
                """.trimIndent(),
            )

        cursor.use { c ->
            assertTrue(c.moveToFirst(), "Transaction migration sonrası bulunamadı")
            assertEquals(TRX_ID, c.getString(c.getColumnIndexOrThrow("id")))
            assertEquals(USER_ID, c.getString(c.getColumnIndexOrThrow("owner_id")))
            assertEquals(4599L, c.getLong(c.getColumnIndexOrThrow("amount_minor")))
            assertEquals("TRY", c.getString(c.getColumnIndexOrThrow("currency_code")))
            assertEquals("EXPENSE", c.getString(c.getColumnIndexOrThrow("type_code")))
            assertEquals(CAT_ID, c.getString(c.getColumnIndexOrThrow("category_id")))
            assertEquals("Netflix Uyelik", c.getString(c.getColumnIndexOrThrow("description")))
            assertEquals("Aylik plan", c.getString(c.getColumnIndexOrThrow("note")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("installment_number")))
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("total_installments")))
            assertEquals("grp-001", c.getString(c.getColumnIndexOrThrow("installment_group_id")))
            assertEquals("CUSTOM", c.getString(c.getColumnIndexOrThrow("split_mode")))
            assertEquals("""[{"userId":"$USER_ID","amountMinor":4599}]""", c.getString(c.getColumnIndexOrThrow("participant_shares_json")))
            assertEquals("SYNCED", c.getString(c.getColumnIndexOrThrow("sync_status")))
            assertEquals(5L, c.getLong(c.getColumnIndexOrThrow("version")))
            assertEquals(4L, c.getLong(c.getColumnIndexOrThrow("base_version")))

            // Yeni 4 kolon NULL olmalıdır
            val mIdIdx = c.getColumnIndexOrThrow("merchant_id")
            val mSourceIdx = c.getColumnIndexOrThrow("merchant_match_source_code")
            val mConfIdx = c.getColumnIndexOrThrow("merchant_confidence")
            val mVerIdx = c.getColumnIndexOrThrow("merchant_catalog_version")

            assertTrue(c.isNull(mIdIdx), "merchant_id null olmalıdır")
            assertTrue(c.isNull(mSourceIdx), "merchant_match_source_code null olmalıdır")
            assertTrue(c.isNull(mConfIdx), "merchant_confidence null olmalıdır")
            assertTrue(c.isNull(mVerIdx), "merchant_catalog_version null olmalıdır")

            assertNull(c.getString(mIdIdx))
            assertNull(c.getString(mSourceIdx))
        }

        migratedDb.close()
    }
}
