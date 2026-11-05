package com.feniqo.mobile.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationMigrationTest {
    @get:Rule
    val migrationHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            FeniqoDatabase::class.java,
        )

    private fun createV23Db(name: String): Pair<String, SupportSQLiteDatabase> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(name)
        val db = migrationHelper.createDatabase(dbFile.absolutePath, 23)
        return dbFile.absolutePath to db
    }

    private companion object {
        const val USER_A_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val USER_B_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val CAT_ID = "11111111-1111-4111-8111-111111111111"
        const val TRX_ID = "tx-v23-mig-001"
        const val VERIF_1_ID = "10000000-0000-4000-8000-000000000001"
        const val VERIF_2_ID = "20000000-0000-4000-8000-000000000002"
        const val VERIF_DUPLICATE_ID = "10000000-0000-4000-8000-000000000003"
    }

    @Test
    fun migration_23_to_24_createsVerificationTableAndPreservesLegacyData() {
        val (dbPath, db) = createV23Db("test_migration_23_24.db")

        // 1. v23 kategorisini ekle
        db.execSQL(
            """
            INSERT INTO categories (
                id, owner_id, workspace_id, scope_key, name, normalized_name, type_code,
                color_hex, is_default, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                local_updated_at_epoch_ms, version
            ) VALUES (
                '$CAT_ID', '$USER_A_ID', NULL, 'scope-$CAT_ID', 'Abonelik', 'abonelik', 'EXPENSE',
                '#FF0000', 0, 1000, 'SYNCED', 1000, 1000, 1
            )
            """.trimIndent(),
        )

        // 2. v23 transaction'ı (merchant attribution kolonları dolu olarak) ekle
        db.execSQL(
            """
            INSERT INTO transactions (
                id, owner_id, workspace_id, paid_by_user_id, participant_user_ids_json,
                amount_minor, currency_code, type_code, category_id, description, search_text,
                payment_method_code, transaction_date, receipt_path, installment_number,
                total_installments, installment_group_id, created_at_epoch_ms, note, split_mode,
                participant_shares_json, sync_status, updated_at_epoch_ms,
                local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error,
                merchant_id, merchant_match_source_code, merchant_confidence, merchant_catalog_version
            ) VALUES (
                '$TRX_ID', '$USER_A_ID', NULL, '$USER_A_ID', '["$USER_A_ID"]',
                5499, 'TRY', 'EXPENSE', '$CAT_ID', 'Netflix Uyelik', 'netflix uyelik aylik',
                'CREDIT_CARD', '2026-10-14', NULL, NULL,
                NULL, NULL, 1700000000000, 'Aylik plan', 'EQUAL',
                '[]', 'SYNCED', 1700000000000,
                1700000000000, NULL, 10, 9, NULL,
                'netflix', 'STRONG_BRAND', 80, 1
            )
            """.trimIndent(),
        )

        db.close()

        // 3. v23 -> v24 migration'ını çalıştır ve 24.json şemasını doğrula
        val migratedDb =
            migrationHelper.runMigrationsAndValidate(
                dbPath,
                24,
                true,
                ANDROID_MIGRATION_23_24,
            )

        // 4. Eski verilerin kayıpsız korunduğunu doğrula
        val cursor =
            migratedDb.query(
                """
                SELECT
                    id, owner_id, amount_minor, description, merchant_id,
                    merchant_match_source_code, merchant_confidence, merchant_catalog_version
                FROM transactions WHERE id = '$TRX_ID'
                """.trimIndent(),
            )

        cursor.use {
            assertTrue(it.moveToFirst(), "Transaction satırı migration sonrası bulunamadı")
            assertEquals(TRX_ID, it.getString(0))
            assertEquals(USER_A_ID, it.getString(1))
            assertEquals(5499L, it.getLong(2))
            assertEquals("Netflix Uyelik", it.getString(3))
            assertEquals("netflix", it.getString(4))
            assertEquals("STRONG_BRAND", it.getString(5))
            assertEquals(80, it.getInt(6))
            assertEquals(1, it.getInt(7))
        }

        // 5. Yeni user_merchant_verifications tablosunun boş başladığını doğrula
        val countCursor = migratedDb.query("SELECT COUNT(*) FROM user_merchant_verifications")
        countCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0), "Yeni tablo boş başlamalıdır")
        }

        // 6. Farklı kullanıcı scopeKey'lerinin aynı alias'ı ekleyebildiğini doğrula
        migratedDb.execSQL(
            """
            INSERT INTO user_merchant_verifications (
                id, owner_id, workspace_id, scope_key, normalized_alias,
                decision_code, merchant_id, scope_code, created_at_epoch_ms,
                sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms,
                deleted_at_epoch_ms, version, base_version, last_sync_error
            ) VALUES (
                '$VERIF_1_ID', '$USER_A_ID', NULL, 'user:$USER_A_ID', 'NETFLIX',
                'ASSIGN', 'netflix', 'PERSONAL', 1000,
                'SYNCED', 1000, 1000, NULL, 1, 0, NULL
            )
            """.trimIndent(),
        )

        migratedDb.execSQL(
            """
            INSERT INTO user_merchant_verifications (
                id, owner_id, workspace_id, scope_key, normalized_alias,
                decision_code, merchant_id, scope_code, created_at_epoch_ms,
                sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms,
                deleted_at_epoch_ms, version, base_version, last_sync_error
            ) VALUES (
                '$VERIF_2_ID', '$USER_B_ID', NULL, 'user:$USER_B_ID', 'NETFLIX',
                'BLOCK', NULL, 'PERSONAL', 2000,
                'SYNCED', 2000, 2000, NULL, 1, 0, NULL
            )
            """.trimIndent(),
        )

        // 7. Aynı scope_key ve normalized_alias için UNIQUE kısıtının çalıştığını doğrula
        assertFailsWith<SQLiteConstraintException> {
            migratedDb.execSQL(
                """
                INSERT INTO user_merchant_verifications (
                    id, owner_id, workspace_id, scope_key, normalized_alias,
                    decision_code, merchant_id, scope_code, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms,
                    deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    '$VERIF_DUPLICATE_ID', '$USER_A_ID', NULL, 'user:$USER_A_ID', 'NETFLIX',
                    'BLOCK', NULL, 'PERSONAL', 3000,
                    'SYNCED', 3000, 3000, NULL, 1, 0, NULL
                )
                """.trimIndent(),
            )
        }
    }
}
