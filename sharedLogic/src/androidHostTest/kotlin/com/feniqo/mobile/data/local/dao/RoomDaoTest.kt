package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.database.DefaultCategorySeeder
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.OutboxErrorClassification
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.isDefinitiveRejection
import com.feniqo.mobile.data.local.entity.isAmbiguousResult
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.RoomOutboxQueue
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Tag
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionTag
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.validation.TransactionValidationError
import com.feniqo.mobile.domain.validation.TransactionValidationResult
import com.feniqo.mobile.domain.validation.TransactionValidationRules
import com.feniqo.mobile.domain.validation.WorkspaceSettlementCalculator
import com.feniqo.mobile.domain.validation.WorkspaceSharedExpense
import com.feniqo.mobile.data.mapper.toDomain
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.feniqo.mobile.data.mapper.toPendingDelete
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {

    @Test
    fun profile_insert_if_missing_preserves_existing_local_profile() = runTest {
        val db = inMemoryDatabase()
        try {
            val dao = db.profileDao()
            val profile = UserProfileEntity(
                id = "profile-user", email = "user@example.com", fullName = "Yerel Ad",
                currencyCode = "TRY", themeCode = "SYSTEM", languageCode = "TR",
                activeWorkspaceId = null, createdAtEpochMillis = 1_000L,
                sync = newSyncMetadata(1_000L, SyncStatus.SYNCED).copy(version = 3, baseVersion = 3),
            )
            assertTrue(dao.insertIfMissing(profile) > 0)
            assertEquals(-1L, dao.insertIfMissing(profile.copy(fullName = "Uzak Ad")))
            assertEquals("Yerel Ad", dao.observeById(profile.id).first()?.fullName)
        } finally {
            db.close()
        }
    }

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    @Test
    fun migration_1_to_15_preserves_legacy_transaction_and_keeps_sync_tables_empty() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test_v1_to_v15.db"
        val path = context.getDatabasePath(name).absolutePath
        context.deleteDatabase(name)
        val v1 = migrationHelper.createDatabase(path, 1)
        try {
            v1.execSQL(
                """INSERT INTO categories (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,slug,type_code,color_hex,
                    icon_key,is_default,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('legacy-cat','legacy-user',NULL,'legacy-user','Market','market','market',
                    'EXPENSE','#123456',NULL,0,1000,'SYNCED',1100,1200,NULL,4,4,NULL)""".trimIndent(),
            )
            v1.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('legacy-tx','legacy-user',NULL,12550,'TRY','EXPENSE','legacy-cat',
                    'market','market','DEBIT_CARD','2026-08-05',NULL,NULL,NULL,NULL,1000,
                    'SYNCED',1100,1200,NULL,5,5,NULL)""".trimIndent(),
            )
        } finally {
            v1.close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 17, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_1_2,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_2_3,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_3_4,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_4_5,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_5_6,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_6_7,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_7_8,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_8_9,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_9_10,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_10_11,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_11_12,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_12_13,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_13_14,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_14_15,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_15_16,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_16_17,
        )
        try {
            migrated.query(
                """SELECT amount_minor,currency_code,paid_by_user_id,participant_user_ids_json,
                    sync_status,version,note FROM transactions WHERE id='legacy-tx'""".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(12_550L, it.getLong(0))
                assertEquals("TRY", it.getString(1))
                assertEquals("legacy-user", it.getString(2))
                assertEquals("[\"legacy-user\"]", it.getString(3))
                assertEquals("SYNCED", it.getString(4))
                assertEquals(5L, it.getLong(5))
                assertNull(it.getString(6))
            }
            for (table in listOf("sync_operations", "sync_cursors", "sync_conflicts", "sync_user_states")) {
                migrated.query("SELECT COUNT(*) FROM $table").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0L, it.getLong(0), table)
                }
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_14_to_15_adds_note_column_and_preserves_transactions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test_v14_to_v15.db"
        val path = context.getDatabasePath(name).absolutePath
        context.deleteDatabase(name)
        val v14 = migrationHelper.createDatabase(path, 14)
        try {
            v14.execSQL(
                """INSERT INTO categories (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,slug,type_code,color_hex,
                    icon_key,is_default,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('cat-14','user-14',NULL,'user-14','Market','market','market',
                    'EXPENSE','#123456',NULL,0,1000,'SYNCED',1100,1200,NULL,1,1,NULL)""".trimIndent(),
            )
            v14.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tx-14','user-14',NULL,15000,'TRY','EXPENSE','cat-14',
                    'A market alışverişi','a market alisverisi','CREDIT_CARD','2026-09-10',NULL,NULL,NULL,NULL,1000,
                    'SYNCED',1100,1200,NULL,1,1,NULL)""".trimIndent(),
            )
        } finally {
            v14.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 15, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_14_15,
        )
        try {
            migrated.query(
                "SELECT id, description, note FROM transactions WHERE id='tx-14'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-14", it.getString(0))
                assertEquals("A market alışverişi", it.getString(1))
                assertNull(it.getString(2))
            }

            migrated.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    note,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tx-15','user-14',NULL,20000,'TRY','EXPENSE','cat-14',
                    'Kahve','kahve','CASH','2026-09-12',NULL,NULL,NULL,NULL,2000,
                    'Özel çekirdek','SYNCED',2000,2000,NULL,1,1,NULL)""".trimIndent(),
            )
            migrated.query(
                "SELECT note FROM transactions WHERE id='tx-15'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("Özel çekirdek", it.getString(0))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_15_to_16_adds_subscription_lifecycle_price_history_and_payments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test_v15_to_v16.db"
        val path = context.getDatabasePath(name).absolutePath
        context.deleteDatabase(name)
        val v15 = migrationHelper.createDatabase(path, 15)
        try {
            v15.execSQL(
                """INSERT INTO subscriptions (
                    id,owner_id,workspace_id,name,amount_minor,currency_code,category_id,
                    frequency_code,`interval`,start_date,end_date,next_renewal_date,is_active,
                    created_at_epoch_ms,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,
                    deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('sub-v15-active','user-15',NULL,'Netflix',22900,'TRY',NULL,
                    'MONTHLY',1,'2026-01-01',NULL,'2026-09-15',1,
                    1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            v15.execSQL(
                """INSERT INTO subscriptions (
                    id,owner_id,workspace_id,name,amount_minor,currency_code,category_id,
                    frequency_code,`interval`,start_date,end_date,next_renewal_date,is_active,
                    created_at_epoch_ms,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,
                    deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('sub-v15-paused','user-15',NULL,'Spotify',10900,'TRY',NULL,
                    'MONTHLY',1,'2026-01-01',NULL,'2026-09-18',0,
                    1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
        } finally {
            v15.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 16, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_15_16,
        )
        try {
            migrated.query(
                "SELECT id, lifecycle_status, reminder_enabled FROM subscriptions WHERE id='sub-v15-active'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("sub-v15-active", it.getString(0))
                assertEquals("ACTIVE", it.getString(1))
                assertEquals(1, it.getInt(2))
            }
            migrated.query(
                "SELECT id, lifecycle_status FROM subscriptions WHERE id='sub-v15-paused'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("sub-v15-paused", it.getString(0))
                assertEquals("PAUSED", it.getString(1))
            }

            // subscription_price_histories tablosuna kayıt yazılabilmeli
            migrated.execSQL(
                """INSERT INTO subscription_price_histories (
                    id,subscription_id,old_amount_minor,new_amount_minor,currency_code,
                    changed_at_epoch_ms,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,
                    deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('ph-1','sub-v15-active',19900,22900,'TRY',2000,'SYNCED',2000,2000,NULL,1,1,NULL)""".trimIndent(),
            )

            // subscription_payments tablosuna kayıt yazılabilmeli
            migrated.execSQL(
                """INSERT INTO subscription_payments (
                    id,subscription_id,amount_minor,currency_code,payment_date,renewal_due_date,
                    source_type,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('pay-1','sub-v15-active',22900,'TRY','2026-09-15','2026-09-15',
                    'MANUAL',3000,'SYNCED',3000,3000,NULL,1,1,NULL)""".trimIndent(),
            )
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_16_to_17_adds_website_url_and_notes_to_subscriptions() {
        val name = "migration-test-16-17.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).absolutePath

        val v16 = migrationHelper.createDatabase(path, 16)
        try {
            v16.execSQL(
                """INSERT INTO subscriptions (
                    id,owner_id,workspace_id,name,amount_minor,currency_code,category_id,
                    frequency_code,interval,start_date,end_date,next_renewal_date,is_active,
                    lifecycle_status,trial_end_date,cancellation_date,access_end_date,reminder_enabled,
                    created_at_epoch_ms,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,
                    deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('sub-v16','user-1',NULL,'Netflix',22900,'TRY',NULL,
                    'MONTHLY',1,'2026-09-01',NULL,'2026-10-01',1,
                    'ACTIVE',NULL,NULL,NULL,1,
                    1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
        } finally {
            v16.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 17, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_16_17,
        )
        try {
            migrated.query(
                "SELECT id, website_url, notes FROM subscriptions WHERE id='sub-v16'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("sub-v16", it.getString(0))
                assertNull(it.getString(1))
                assertNull(it.getString(2))
            }
            migrated.execSQL("UPDATE subscriptions SET website_url='https://netflix.com', notes='4K Plan' WHERE id='sub-v16'")
            migrated.query(
                "SELECT website_url, notes FROM subscriptions WHERE id='sub-v16'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("https://netflix.com", it.getString(0))
                assertEquals("4K Plan", it.getString(1))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_17_to_18_adds_receipt_tables_and_preserves_legacy_data() {
        val name = "migration-test-17-18.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).absolutePath

        val v17 = migrationHelper.createDatabase(path, 17)
        try {
            // 1. Kategori
            v17.execSQL(
                """INSERT INTO categories (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,slug,type_code,color_hex,
                    icon_key,is_default,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('cat-17','user-17',NULL,'user-17','Gıda','gida','gida',
                    'EXPENSE','#AABBCC',NULL,0,1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            // 2. İşlem - receiptPath dolu ve note mevcut
            v17.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    note,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tx-legacy-receipt','user-17',NULL,45000,'TRY','EXPENSE','cat-17',
                    'Market fişi','market fisi','CREDIT_CARD','2026-09-16','receipts/legacy-user/tx-17.jpg',
                    NULL,NULL,NULL,2000,'Eski makbuzlu işlem','SYNCED',2000,2000,NULL,1,1,NULL)""".trimIndent(),
            )
            // 3. Etiket ve işlem-etiket ilişkisi
            v17.execSQL(
                """INSERT INTO tags (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,created_at_epoch_ms,
                    sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tag-17','user-17',NULL,'user-17','Acil','acil',1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO transaction_tags (
                    transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES ('tx-legacy-receipt', 'tag-17', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)""".trimIndent(),
            )
            // 4. Outbox operasyonu
            v17.execSQL(
                """INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code, base_version,
                    payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code,
                    attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES ('op-17','TRANSACTION','tx-legacy-receipt','CREATE',NULL,
                    '{"id":"tx-legacy-receipt"}',NULL,0,1,'PENDING',0,NULL,3000,3000,3000)""".trimIndent(),
            )
            // 5. Conflict kaydı
            v17.execSQL(
                """INSERT INTO sync_conflicts (
                    entity_type_code, entity_id, operation_id, local_version, remote_version,
                    local_payload_json, remote_payload_json, detected_at_epoch_ms
                ) VALUES ('TRANSACTION','tx-legacy-receipt','op-17',1,2,
                    '{"amount":45000}','{"amount":50000}',4000)""".trimIndent(),
            )
        } finally {
            v17.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 18, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_17_18,
        )
        try {
            // İşlem ve receipt_path korunmuş olmalı
            migrated.query(
                "SELECT id, amount_minor, receipt_path, note, sync_status FROM transactions WHERE id='tx-legacy-receipt'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-legacy-receipt", it.getString(0))
                assertEquals(45_000L, it.getLong(1))
                assertEquals("receipts/legacy-user/tx-17.jpg", it.getString(2))
                assertEquals("Eski makbuzlu işlem", it.getString(3))
                assertEquals("SYNCED", it.getString(4))
            }
            // Etiket ilişkisi korunmuş olmalı
            migrated.query(
                "SELECT transaction_id, tag_id FROM transaction_tags WHERE transaction_id='tx-legacy-receipt'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-legacy-receipt", it.getString(0))
                assertEquals("tag-17", it.getString(1))
            }
            // Outbox operasyonu korunmuş olmalı
            migrated.query(
                "SELECT operation_id, status_code FROM sync_operations WHERE operation_id='op-17'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("op-17", it.getString(0))
                assertEquals("PENDING", it.getString(1))
            }
            // Conflict kaydı korunmuş olmalı
            migrated.query(
                "SELECT entity_id, operation_id FROM sync_conflicts WHERE entity_id='tx-legacy-receipt'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-legacy-receipt", it.getString(0))
                assertEquals("op-17", it.getString(1))
            }
            // Yeni makbuz tabloları migration sonunda tamamen boş olmalı
            for (table in listOf("receipt_files", "receipt_linkages")) {
                migrated.query("SELECT COUNT(*) FROM $table").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0L, it.getLong(0), "$table migration sonunda boş olmalıdır.")
                }
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_18_to_19_adds_split_columns_and_preserves_data() {
        val name = "migration-test-18-19.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).absolutePath

        val v18 = migrationHelper.createDatabase(path, 18)
        try {
            // 1. Kategori
            v18.execSQL(
                """INSERT INTO categories (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,slug,type_code,color_hex,
                    icon_key,is_default,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('cat-18','user-18',NULL,'user-18','Gıda','gida','gida',
                    'EXPENSE','#AABBCC',NULL,0,1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            // 2. İşlem
            v18.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    note,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tx-18','user-18',NULL,35000,'TRY','EXPENSE','cat-18',
                    'Market fişi','market fisi','CREDIT_CARD','2026-09-16','receipts/legacy/tx-18.jpg',
                    NULL,NULL,NULL,2000,'V18 notlu işlem','SYNCED',2000,2000,NULL,1,1,NULL)""".trimIndent(),
            )
            // 3. Etiket ve ilişki
            v18.execSQL(
                """INSERT INTO tags (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,created_at_epoch_ms,
                    sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tag-18','user-18',NULL,'user-18','Ortak','ortak',1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            v18.execSQL(
                """INSERT INTO transaction_tags (
                    transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES ('tx-18', 'tag-18', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)""".trimIndent(),
            )
            // 4. Outbox operasyonu
            v18.execSQL(
                """INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code, base_version,
                    payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code,
                    attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES ('op-18','TRANSACTION','tx-18','CREATE',NULL,
                    '{"id":"tx-18"}',NULL,0,1,'PENDING',0,NULL,3000,3000,3000)""".trimIndent(),
            )
            // 5. Conflict kaydı
            v18.execSQL(
                """INSERT INTO sync_conflicts (
                    entity_type_code, entity_id, operation_id, local_version, remote_version,
                    local_payload_json, remote_payload_json, detected_at_epoch_ms
                ) VALUES ('TRANSACTION','tx-18','op-18',1,2,
                    '{"amount":35000}','{"amount":40000}',4000)""".trimIndent(),
            )
            // 6. Makbuz dosyası ve linkage
            v18.execSQL(
                """INSERT INTO receipt_files (
                    attachment_id, owner_id, content_sha256, file_size_bytes, mime_type,
                    remote_path, upload_status, verified_remote_exists, created_at_epoch_ms
                ) VALUES ('rf-18','user-18','dummyhash18',1024,'image/jpeg',
                    'receipts/user-18/rf-18.jpg','COMPLETED',1,2000)""".trimIndent(),
            )
            v18.execSQL(
                """INSERT INTO receipt_linkages (
                    transaction_id, owner_id, active_attachment_id, generation, session_epoch,
                    workspace_id, updated_at_epoch_ms
                ) VALUES ('tx-18','user-18','rf-18',1,1,NULL,2000)""".trimIndent(),
            )
        } finally {
            v18.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 19, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_18_19,
        )
        try {
            // Transaction alanları ve yeni split kolonları doğrulanır
            migrated.query(
                "SELECT id, amount_minor, receipt_path, note, sync_status, split_mode, participant_shares_json FROM transactions WHERE id='tx-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-18", it.getString(0))
                assertEquals(35_000L, it.getLong(1))
                assertEquals("receipts/legacy/tx-18.jpg", it.getString(2))
                assertEquals("V18 notlu işlem", it.getString(3))
                assertEquals("SYNCED", it.getString(4))
                assertEquals("EQUAL", it.getString(5))
                assertEquals("[]", it.getString(6))
            }
            // Etiket ilişkisi korunmuş olmalı
            migrated.query(
                "SELECT transaction_id, tag_id FROM transaction_tags WHERE transaction_id='tx-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-18", it.getString(0))
                assertEquals("tag-18", it.getString(1))
            }
            // Outbox operasyonu korunmuş olmalı
            migrated.query(
                "SELECT operation_id, status_code FROM sync_operations WHERE operation_id='op-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("op-18", it.getString(0))
                assertEquals("PENDING", it.getString(1))
            }
            // Conflict kaydı korunmuş olmalı
            migrated.query(
                "SELECT entity_id, operation_id FROM sync_conflicts WHERE entity_id='tx-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-18", it.getString(0))
                assertEquals("op-18", it.getString(1))
            }
            // Makbuz kayıtları korunmuş olmalı
            migrated.query(
                "SELECT attachment_id, upload_status FROM receipt_files WHERE attachment_id='rf-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("rf-18", it.getString(0))
                assertEquals("COMPLETED", it.getString(1))
            }
            migrated.query(
                "SELECT transaction_id, active_attachment_id FROM receipt_linkages WHERE transaction_id='tx-18'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-18", it.getString(0))
                assertEquals("rf-18", it.getString(1))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_17_to_19_chain_preserves_all_data() {
        val name = "migration-test-17-19.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).absolutePath

        val v17 = migrationHelper.createDatabase(path, 17)
        try {
            v17.execSQL(
                """INSERT INTO categories (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,slug,type_code,color_hex,
                    icon_key,is_default,created_at_epoch_ms,sync_status,updated_at_epoch_ms,
                    local_updated_at_epoch_ms,deleted_at_epoch_ms,version,base_version,last_sync_error
                ) VALUES ('cat-17','user-17',NULL,'user-17','Gıda','gida','gida',
                    'EXPENSE','#AABBCC',NULL,0,1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO transactions (
                    id,owner_id,workspace_id,amount_minor,currency_code,type_code,category_id,
                    description,search_text,payment_method_code,transaction_date,receipt_path,
                    installment_number,total_installments,installment_group_id,created_at_epoch_ms,
                    note,sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tx-17','user-17',NULL,45000,'TRY','EXPENSE','cat-17',
                    'Zincir test','zincir test','CREDIT_CARD','2026-09-16','receipts/legacy/tx-17.jpg',
                    NULL,NULL,NULL,2000,'Eski v17 not','SYNCED',2000,2000,NULL,1,1,NULL)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO tags (
                    id,owner_id,workspace_id,scope_key,name,normalized_name,created_at_epoch_ms,
                    sync_status,updated_at_epoch_ms,local_updated_at_epoch_ms,deleted_at_epoch_ms,
                    version,base_version,last_sync_error
                ) VALUES ('tag-17','user-17',NULL,'user-17','Ortak','ortak',1000,'SYNCED',1000,1000,NULL,1,1,NULL)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO transaction_tags (
                    transaction_id, tag_id, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES ('tx-17', 'tag-17', 1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code, base_version,
                    payload_json, predecessor_operation_id, is_blocked, protocol_version, status_code,
                    attempt_count, last_error, next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES ('op-17','TRANSACTION','tx-17','CREATE',NULL,
                    '{"id":"tx-17"}',NULL,0,1,'PENDING',0,NULL,3000,3000,3000)""".trimIndent(),
            )
            v17.execSQL(
                """INSERT INTO sync_conflicts (
                    entity_type_code, entity_id, operation_id, local_version, remote_version,
                    local_payload_json, remote_payload_json, detected_at_epoch_ms
                ) VALUES ('TRANSACTION','tx-17','op-17',1,2,
                    '{"amount":45000}','{"amount":50000}',4000)""".trimIndent(),
            )
        } finally {
            v17.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 19, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_17_18,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_18_19,
        )
        try {
            migrated.query(
                "SELECT id, amount_minor, receipt_path, note, sync_status, split_mode, participant_shares_json FROM transactions WHERE id='tx-17'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-17", it.getString(0))
                assertEquals(45_000L, it.getLong(1))
                assertEquals("receipts/legacy/tx-17.jpg", it.getString(2))
                assertEquals("Eski v17 not", it.getString(3))
                assertEquals("SYNCED", it.getString(4))
                assertEquals("EQUAL", it.getString(5))
                assertEquals("[]", it.getString(6))
            }
            for (table in listOf("receipt_files", "receipt_linkages")) {
                migrated.query("SELECT COUNT(*) FROM $table").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0L, it.getLong(0), "$table migration sonunda boş olmalıdır.")
                }
            }
            migrated.query(
                "SELECT operation_id, status_code FROM sync_operations WHERE operation_id='op-17'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("op-17", it.getString(0))
                assertEquals("PENDING", it.getString(1))
            }
            migrated.query(
                "SELECT entity_id, operation_id FROM sync_conflicts WHERE entity_id='tx-17'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("tx-17", it.getString(0))
                assertEquals("op-17", it.getString(1))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_19_to_20_adds_error_classification_column() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test_v19_to_v20.db"
        val path = context.getDatabasePath(name).absolutePath
        context.deleteDatabase(name)
        val v19 = migrationHelper.createDatabase(path, 19)
        try {
            v19.execSQL(
                """INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, payload_json, predecessor_operation_id, is_blocked,
                    protocol_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'op-v19-1', 'TRANSACTION', 'tx-v19-1', 'CREATE',
                    NULL, '{"amount":100}', NULL, 0,
                    2, 'FAILED', 1, 'timeout',
                    1000, 1000, 1000
                )""".trimIndent(),
            )
        } finally {
            v19.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            path, 20, true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_19_20,
        )
        try {
            migrated.query(
                "SELECT operation_id, status_code, error_classification FROM sync_operations WHERE operation_id='op-v19-1'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("op-v19-1", it.getString(0))
                assertEquals("FAILED", it.getString(1))
                assertNull(it.getString(2))
            }

            migrated.execSQL(
                """INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, payload_json, predecessor_operation_id, is_blocked,
                    protocol_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms,
                    error_classification
                ) VALUES (
                    'op-v20-1', 'TRANSACTION', 'tx-v20-1', 'CREATE',
                    NULL, '{"amount":200}', NULL, 0,
                    2, 'FAILED', 1, 'validation_error',
                    2000, 2000, 2000, 'DEFINITIVE_REJECTION'
                )""".trimIndent(),
            )

            migrated.query(
                "SELECT operation_id, error_classification FROM sync_operations WHERE operation_id='op-v20-1'".trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("op-v20-1", it.getString(0))
                assertEquals("DEFINITIVE_REJECTION", it.getString(1))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun custom_split_persistence_roundtrip_reopen_and_corrupt_json_resilience() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test-custom-split-reopen.db"
        context.deleteDatabase(dbName)

        val txId = EntityId("tx-custom-split")
        val ownerId = EntityId("user-alice")
        val bobId = EntityId("user-bob")
        val customShares = listOf(
            TransactionParticipantShare(bobId, 4_000L),
            TransactionParticipantShare(ownerId, 6_000L),
        )
        val customTx = Transaction(
            id = txId,
            ownerId = ownerId,
            workspaceId = EntityId("ws-team"),
            amount = Money(10_000L, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = CATEGORY_ID,
            description = "Akşam yemeği",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 16),
            receiptPath = null,
            installment = null,
            createdAt = NOW,
            splitMode = TransactionSplitMode.CUSTOM,
            participantShares = customShares,
        )

        // 1. CUSTOM işlem Room'a yazılır.
        var db = persistentDatabase(context, dbName)
        try {
            db.categoryDao().upsert(category().toEntity(SYNC))
            db.workspaceDao().upsertWorkspace(
                Workspace(
                    id = EntityId("ws-team"),
                    name = "Ekip",
                    ownerId = ownerId,
                    createdAt = NOW,
                ).toEntity(SYNC),
            )
            db.transactionDao().upsert(customTx.toEntity(SYNC))

            // 2. DB kapatılır
        } finally {
            db.close()
        }

        // 3. DB yeniden açılır
        db = persistentDatabase(context, dbName)
        try {
            val entity = db.transactionDao().getByIdAndOwner(txId.value, ownerId.value)
            assertNotNull(entity)
            assertEquals("CUSTOM", entity.splitMode)
            // Kanonik sıralama (user-alice, user-bob)
            assertEquals(
                """[{"userId":"user-alice","amountMinor":6000},{"userId":"user-bob","amountMinor":4000}]""",
                entity.participantSharesJson,
            )

            val domain = entity.toDomain()
            assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
            assertEquals(2, domain.participantShares.size)
            assertEquals(EntityId("user-alice"), domain.participantShares[0].userId)
            assertEquals(6_000L, domain.participantShares[0].amountMinor)
            assertEquals(EntityId("user-bob"), domain.participantShares[1].userId)
            assertEquals(4_000L, domain.participantShares[1].amountMinor)

            // 4. Update ile CUSTOM -> EQUAL geçişi
            val equalTx = domain.copy(
                splitMode = TransactionSplitMode.EQUAL,
                participantShares = emptyList(),
            )
            db.transactionDao().upsert(equalTx.toEntity(SYNC.copy(version = 2, baseVersion = 1)))

            val equalEntity = db.transactionDao().getByIdAndOwner(txId.value, ownerId.value)
            assertNotNull(equalEntity)
            assertEquals("EQUAL", equalEntity.splitMode)
            assertEquals("[]", equalEntity.participantSharesJson)

            // 5. Update ile EQUAL -> CUSTOM geçişi
            val backToCustom = equalTx.copy(
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = customShares,
            )
            db.transactionDao().upsert(backToCustom.toEntity(SYNC.copy(version = 3, baseVersion = 2)))
            val customAgain = db.transactionDao().getByIdAndOwner(txId.value, ownerId.value)
            assertNotNull(customAgain)
            assertEquals("CUSTOM", customAgain.splitMode)
            assertEquals(
                """[{"userId":"user-alice","amountMinor":6000},{"userId":"user-bob","amountMinor":4000}]""",
                customAgain.participantSharesJson,
            )

            // 6. Bozuk CUSTOM JSON içeren satır normal sorgu akışını çökertmez
            // Veritabanına doğrudan bozuk JSON yazıyoruz
            db.openHelper.writableDatabase.execSQL(
                "UPDATE transactions SET participant_shares_json = '{\"bad\": json' WHERE id = '${txId.value}'",
            )

            // Flow ve tekil sorgu exception fırlatmaz
            val corruptEntity = db.transactionDao().getByIdAndOwner(txId.value, ownerId.value)
            assertNotNull(corruptEntity)
            val corruptDomain = corruptEntity.toDomain()

            // Fail-closed sentinel: splitMode = CUSTOM, participantShares = emptyList()
            assertEquals(TransactionSplitMode.CUSTOM, corruptDomain.splitMode)
            assertTrue(corruptDomain.participantShares.isEmpty())

            // 7. Bozuk kayıt settlement doğrulamasında dışlanabilir durumda kalır
            val validation = TransactionValidationRules.normalizeAndValidateSplit(
                corruptDomain,
                activeMemberUserIds = setOf(ownerId, bobId),
            )
            assertTrue(validation is TransactionValidationResult.Invalid)
            assertEquals(TransactionValidationError.CUSTOM_SPLIT_SHARES_REQUIRED, validation.error)

            // WorkspaceSharedExpense fail-closed davranışı: boş pay listesi olan CUSTOM kayıt reddedilir
            assertFailsWith<IllegalArgumentException> {
                WorkspaceSharedExpense(
                    id = corruptDomain.id,
                    paidByUserId = corruptDomain.paidByUserId,
                    amount = corruptDomain.amount,
                    participantUserIds = corruptDomain.participantUserIds,
                    splitMode = corruptDomain.splitMode,
                    participantShares = corruptDomain.participantShares,
                )
            }

            // Observe flow'u da çökmeden yayar
            val flowTx = db.transactionDao().observeById(txId.value).first()
            assertNotNull(flowTx)
            val flowDomain = flowTx.toDomain()
            assertEquals(TransactionSplitMode.CUSTOM, flowDomain.splitMode)
            assertTrue(flowDomain.participantShares.isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun offline_transaction_survives_failure_then_retry_acknowledges_and_syncs_room() = runTest {
        val database = inMemoryDatabase()
        try {
            database.categoryDao().upsert(category().toEntity(SYNC))
            var now = 1_000L
            var sequence = 0
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                operationIdFactory = { (++sequence).toString().padStart(32, '0') },
                nowEpochMillisProvider = { now },
            )
            val local = transaction().toEntity(newSyncMetadata(now))
            val remote = TransactionDto(
                id = local.id,
                userId = local.ownerId,
                paidByUserId = local.ownerId,
                participantUserIds = listOf(local.ownerId),
                amountMinor = local.amountMinor,
                currency = local.currencyCode,
                type = local.typeCode,
                categoryId = local.categoryId,
                description = local.description,
                paymentMethod = local.paymentMethodCode,
                transactionDate = local.transactionDate,
                createdAt = "2026-08-05T00:00:00Z",
                updatedAt = "2026-08-05T00:00:01Z",
                version = 1L,
            )
            val operationId = queue.enqueueTransactionV2(
                entity = local,
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
                payloadJson = Json.encodeToString(remote.copy(updatedAt = null, version = null)),
            )
            var shouldFail = true
            val processor = OutboxProcessor(RoomOutboxQueue(queue)) {
                if (shouldFail) error("offline")
                OutboxExecutionResult.TransactionApplied(remote)
            }

            val failed = processor.processReadyOperations()
            assertEquals(operationId, failed.failedOperationId)
            assertNotNull(database.transactionDao().observeWithTags(local.id).first())
            assertNotNull(database.localMutationDao().getOutboxById(operationId))

            shouldFail = false
            now = 20_000L
            val retried = processor.processReadyOperations()
            assertEquals(1, retried.succeededCount)
            assertNull(database.localMutationDao().getOutboxById(operationId))
            val synced = database.transactionDao().observeWithTags(local.id).first()!!.transaction
            assertEquals(SyncStatus.SYNCED.name, synced.sync.syncStatus)
            assertEquals(1L, synced.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun transactionMutationV2_failedCreateCanBeSafelyHardDeleted() = runTest {
        val database = inMemoryDatabase()
        try {
            database.categoryDao().upsert(category().toEntity(SYNC))
            var now = 10_000L
            val queue = OfflineWriteQueue(
                database.localMutationDao(),
                database.syncOperationDao(),
                nowEpochMillisProvider = { now },
            )
            val local = transaction().toEntity(newSyncMetadata(now))
            val remote = TransactionDto(
                id = local.id,
                userId = local.ownerId,
                paidByUserId = local.ownerId,
                participantUserIds = listOf(local.ownerId),
                amountMinor = local.amountMinor,
                currency = local.currencyCode,
                type = local.typeCode,
                categoryId = local.categoryId,
                description = local.description,
                paymentMethod = local.paymentMethodCode,
                transactionDate = local.transactionDate,
                createdAt = "2026-08-05T00:00:00Z",
                updatedAt = "2026-08-05T00:00:01Z",
                version = 1L,
            )
            val opId = queue.enqueueTransactionV2(
                entity = local,
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
                payloadJson = Json.encodeToString(remote.copy(updatedAt = null, version = null)),
            )

            // Sunucudan hata döndüğünü ve operasyonun FAILED olduğunu simüle et
            database.syncOperationDao().markFailed(
                operationId = opId,
                lastError = "VALIDATION_ERROR: Geçersiz kategori",
                errorClassification = OutboxErrorClassification.DEFINITIVE_REJECTION.name,
                nextAttemptAtEpochMillis = now + 60_000L,
                nowEpochMillis = now,
            )
            val failedOp = database.localMutationDao().getOutboxById(opId)!!
            assertEquals("FAILED", failedOp.statusCode)

            // Kullanıcı bu başarısız yerel kaydı sildiğinde
            val deletedLocal = local.copy(sync = local.sync.toPendingDelete(now))
            val result = database.localMutationDao().mutateTransactionKeepingTagsV2(
                entity = deletedLocal,
                type = OutboxOperationType.DELETE,
                payloadJson = "{}",
                operationIdFactory = { "op-del-1" },
                nowEpochMillis = now,
            )
            assertEquals(V2EnqueueDecision.HARD_DELETED, result.decision)
            assertEquals(opId, result.operationId)

            // Başarısız outbox kaydı ve yerel entity temizlenmiş olmalı
            assertNull(database.localMutationDao().getOutboxById(opId))
            assertNull(database.transactionDao().observeWithTags(local.id).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun transactionMutationV2_failedCreateCanBeUpdatedWithFreshOperationId() = runTest {
        val database = inMemoryDatabase()
        try {
            database.categoryDao().upsert(category().toEntity(SYNC))
            val correctedCat = category().copy(id = EntityId("cat-corrected-id"), name = "Düzeltilmiş Kategori").toEntity(SYNC)
            database.categoryDao().upsert(correctedCat)

            var now = 10_000L
            val queue = OfflineWriteQueue(
                database.localMutationDao(),
                database.syncOperationDao(),
                nowEpochMillisProvider = { now },
            )
            val local = transaction().toEntity(newSyncMetadata(now))
            val initialDto = TransactionDto(
                id = local.id,
                userId = local.ownerId,
                paidByUserId = local.ownerId,
                participantUserIds = listOf(local.ownerId),
                amountMinor = local.amountMinor,
                currency = local.currencyCode,
                type = local.typeCode,
                categoryId = local.categoryId,
                description = local.description,
                paymentMethod = local.paymentMethodCode,
                transactionDate = local.transactionDate,
                createdAt = "2026-08-05T00:00:00Z",
                updatedAt = "2026-08-05T00:00:01Z",
                version = 1L,
            )
            val opId1 = queue.enqueueTransactionV2(
                entity = local,
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
                payloadJson = Json.encodeToString(initialDto.copy(updatedAt = null, version = null)),
            )

            // İlk deneme başarısız oldu
            database.syncOperationDao().markFailed(
                operationId = opId1,
                lastError = "VALIDATION_ERROR: Geçersiz kategori",
                errorClassification = OutboxErrorClassification.DEFINITIVE_REJECTION.name,
                nextAttemptAtEpochMillis = now + 60_000L,
                nowEpochMillis = now,
            )

            // Kullanıcı kategoriyi düzeltti ve güncelledi
            val updatedLocal = local.copy(categoryId = correctedCat.id)
            val correctedDto = initialDto.copy(categoryId = correctedCat.id, updatedAt = null, version = null)
            val correctedPayload = Json.encodeToString(correctedDto)

            val freshOpId = "2".padStart(32, '0')
            val result = database.localMutationDao().mutateTransactionKeepingTagsV2(
                entity = updatedLocal,
                type = OutboxOperationType.UPDATE,
                payloadJson = correctedPayload,
                operationIdFactory = { freshOpId },
                nowEpochMillis = now,
            )

            assertEquals(V2EnqueueDecision.INSERTED, result.decision)
            assertEquals(freshOpId, result.operationId)

            // Eski başarısız işlem silinmiş, yeni işlem temiz PENDING ve unblocked olarak eklenmiş olmalı
            assertNull(database.localMutationDao().getOutboxById(opId1))
            val freshOp = database.localMutationDao().getOutboxById(freshOpId)!!
            assertEquals("PENDING", freshOp.statusCode)
            assertEquals(0, freshOp.attemptCount)
            assertFalse(freshOp.isBlocked)
            assertNull(freshOp.predecessorOperationId)
            assertEquals("CREATE", freshOp.operationTypeCode)
            assertTrue(freshOp.payloadJson!!.contains(correctedCat.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun defaultCategorySeeder_isOfflineIdempotentAndVisibleForEveryOwner() = runTest {
        val database = inMemoryDatabase()
        try {
            val seeder = DefaultCategorySeeder(database.remoteSyncDao()) { 1234L }

            seeder.seed()
            seeder.seed()

            val categories = database.categoryDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                typeCode = null,
            ).first()
            assertEquals(27, categories.size)
            assertTrue(categories.all { it.isDefault && it.ownerId == null && it.workspaceId == null })
            assertTrue(categories.all { it.scopeKey == "system" })
            assertEquals(9, categories.count { it.typeCode == TransactionType.INCOME.name })
            assertEquals(18, categories.count { it.typeCode == TransactionType.EXPENSE.name })
            assertEquals(27, categories.map { it.id }.distinct().size)
            assertTrue(categories.all { it.iconKey?.contains('-') == false })
        } finally {
            database.close()
        }
    }

    @Test
    fun defaultCategorySeeder_reconcilesEquivalentIdsAndHidesAmbiguousLegacyRows() = runTest {
        val database = inMemoryDatabase()
        try {
            val oldFoodId = "11111111-1111-4111-8111-111111111111"
            val ambiguousId = "11111111-1111-4111-8111-111111111120"
            val oldSystemRows = listOf(
                CategoryEntity(
                    id = oldFoodId,
                    ownerId = null,
                    workspaceId = null,
                    scopeKey = "system",
                    name = "Yemek",
                    normalizedName = "yemek",
                    slug = "yemek",
                    typeCode = "EXPENSE",
                    colorHex = "#FBBF24",
                    iconKey = "utensils",
                    isDefault = true,
                    createdAtEpochMillis = 777L,
                    sync = SYNC,
                ),
                CategoryEntity(
                    id = ambiguousId,
                    ownerId = null,
                    workspaceId = null,
                    scopeKey = "system",
                    name = "Tasarruf & Yatırım",
                    normalizedName = "tasarruf & yatırım",
                    slug = "tasarruf-yatirim",
                    typeCode = "EXPENSE",
                    colorHex = "#10B981",
                    iconKey = "trending-up",
                    isDefault = true,
                    createdAtEpochMillis = 888L,
                    sync = SYNC,
                ),
            )
            database.remoteSyncDao().upsertCategoryRows(oldSystemRows)

            DefaultCategorySeeder(database.remoteSyncDao()) { 1_234L }.seed()

            val reconciled = database.remoteSyncDao().getCategoryRow(oldFoodId)!!
            assertEquals(oldFoodId, reconciled.id)
            assertEquals("Yeme & İçme", reconciled.name)
            assertEquals("food_dining", reconciled.iconKey)
            assertEquals(777L, reconciled.createdAtEpochMillis)
            assertEquals(SYNC.version, reconciled.sync.version)

            val hidden = database.remoteSyncDao().getCategoryRow(ambiguousId)!!
            assertEquals(1_234L, hidden.sync.deletedAtEpochMillis)
            assertNull(database.categoryDao().observeById(ambiguousId).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun assetDao_observes_only_active_assets_for_the_owner() = runTest {
        val database = inMemoryDatabase()
        try {
            database.assetDao().upsert(assetEntity(id = "asset-active", ownerId = USER_ID.value))
            database.assetDao().upsert(assetEntity(id = "asset-other", ownerId = "user-2"))
            database.assetDao().upsert(
                assetEntity(
                    id = "asset-deleted",
                    ownerId = USER_ID.value,
                    sync = SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
                ),
            )

            assertEquals(
                listOf("asset-active"),
                database.assetDao().observeAll(USER_ID.value).first().map { it.id },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun transaction_with_tags_is_written_atomically_and_observed_from_room() = runTest {
        val database = inMemoryDatabase()
        try {
            val category = category()
            val transaction = transaction()
            val tag = Tag(TAG_ID, USER_ID, null, "zorunlu", NOW)
            val relation = TransactionTag(transaction.id, tag.id)

            database.categoryDao().upsert(category.toEntity(SYNC))
            database.transactionDao().upsertWithTags(
                transaction = transaction.toEntity(SYNC),
                tags = listOf(tag.toEntity(SYNC)),
                links = listOf(relation.toEntity(NOW.toEpochMilliseconds(), SYNC)),
            )

            val stored = database.transactionDao().observeWithTags(TRANSACTION_ID.value).first()
            assertEquals(TRANSACTION_ID.value, stored?.transaction?.id)
            assertEquals(listOf(TAG_ID.value), stored?.tags?.map { it.id })

            val filtered = database.transactionDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                startDate = "2026-08-01",
                endDate = "2026-08-31",
                typeCode = TransactionType.EXPENSE.name,
                categoryId = CATEGORY_ID.value,
                paymentMethodCode = PaymentMethod.DEBIT_CARD.name,
                searchQuery = "market",
            ).first()
            assertEquals(listOf(TRANSACTION_ID.value), filtered.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun personalBackupImport_writesCategoriesTransactionsAndOutboxAtomically() = runTest {
        val database = inMemoryDatabase()
        try {
            val sync = newSyncMetadata(NOW.toEpochMilliseconds())
            val categoryEntity = category().copy(id = EntityId("backup-cat")).toEntity(sync)
            val transactionEntity = transaction().copy(
                id = EntityId("backup-tx"),
                categoryId = EntityId("backup-cat"),
            ).toEntity(sync)
            var sequence = 0

            val operationIds = database.localMutationDao().importPersonalBackupV1(
                categoryInputs = listOf(BackupCategoryCreateInputV1(categoryEntity, "{\"id\":\"backup-cat\"}")),
                transactionInputs = listOf(TransactionCreateInputV2(transactionEntity, payloadJson = "{\"id\":\"backup-tx\"}")),
                operationIdFactory = { (++sequence).toString(16).padStart(32, '0') },
                nowEpochMillis = NOW.toEpochMilliseconds(),
            )

            assertEquals(2, operationIds.size)
            assertNotNull(database.categoryDao().observeById("backup-cat").first())
            assertNotNull(database.transactionDao().observeWithTags("backup-tx").first())
            assertTrue(operationIds.all { database.localMutationDao().getOutboxById(it) != null })
        } finally {
            database.close()
        }
    }

    @Test
    fun personalBackupImport_rollsBackEveryRowWhenLaterOutboxInsertFails() = runTest {
        val database = inMemoryDatabase()
        try {
            val sync = newSyncMetadata(NOW.toEpochMilliseconds())
            val first = category().copy(id = EntityId("rollback-cat-1")).toEntity(sync)
            val duplicateName = category().copy(id = EntityId("rollback-cat-2")).toEntity(sync)
            val duplicateOperationId = "1".padStart(32, '0')

            assertFailsWith<Exception> {
                database.localMutationDao().importPersonalBackupV1(
                    categoryInputs = listOf(
                        BackupCategoryCreateInputV1(first, "{\"id\":\"rollback-cat-1\"}"),
                        BackupCategoryCreateInputV1(duplicateName, "{\"id\":\"rollback-cat-2\"}"),
                    ),
                    transactionInputs = emptyList(),
                    operationIdFactory = { duplicateOperationId },
                    nowEpochMillis = NOW.toEpochMilliseconds(),
                )
            }

            assertNull(database.categoryDao().observeById("rollback-cat-1").first())
            assertNull(database.categoryDao().observeById("rollback-cat-2").first())
            assertNull(database.localMutationDao().getOutboxById(duplicateOperationId))
        } finally {
            database.close()
        }
    }

    @Test
    fun sharedWorkspaceQueries_includeMembersRows_butPersonalScopeStaysPrivate() = runTest {
        val database = inMemoryDatabase()
        try {
            val workspaceId = EntityId("workspace-shared")
            database.workspaceDao().upsertWorkspace(
                Workspace(id = workspaceId, name = "Ev", ownerId = USER_ID, createdAt = NOW).toEntity(SYNC),
            )

            val ownSharedCategory = category().copy(workspaceId = workspaceId, name = "Benim kategori")
            val memberSharedCategory = category().copy(
                id = EntityId("category-member"),
                ownerId = EntityId("user-2"),
                workspaceId = workspaceId,
                name = "Üye kategori",
            )
            val otherPersonalCategory = category().copy(
                id = EntityId("category-other-personal"),
                ownerId = EntityId("user-2"),
                name = "Gizli kategori",
            )
            database.categoryDao().upsert(ownSharedCategory.toEntity(SYNC))
            database.categoryDao().upsert(memberSharedCategory.toEntity(SYNC))
            database.categoryDao().upsert(otherPersonalCategory.toEntity(SYNC))

            val ownSharedTransaction = transaction().copy(workspaceId = workspaceId)
            val memberSharedTransaction = transaction().copy(
                id = EntityId("transaction-member"),
                ownerId = EntityId("user-2"),
                workspaceId = workspaceId,
                categoryId = memberSharedCategory.id,
            )
            val otherPersonalTransaction = transaction().copy(
                id = EntityId("transaction-other-personal"),
                ownerId = EntityId("user-2"),
                categoryId = otherPersonalCategory.id,
            )
            database.transactionDao().upsert(ownSharedTransaction.toEntity(SYNC))
            database.transactionDao().upsert(memberSharedTransaction.toEntity(SYNC))
            database.transactionDao().upsert(otherPersonalTransaction.toEntity(SYNC))

            database.budgetDao().upsert(budget("budget-mine", USER_ID, workspaceId, ownSharedCategory.id).toEntity(SYNC))
            database.budgetDao().upsert(budget("budget-member", EntityId("user-2"), workspaceId, memberSharedCategory.id).toEntity(SYNC))
            database.budgetDao().upsert(budget("budget-other-personal", EntityId("user-2"), null, otherPersonalCategory.id).toEntity(SYNC))

            val sharedCategories = database.categoryDao().observeAll(USER_ID.value, workspaceId.value, null).first()
            val sharedTransactions = database.transactionDao().observeAll(
                USER_ID.value, workspaceId.value, null, null, null, null, null, null,
            ).first()
            val sharedBudgets = database.budgetDao().getForMonth(USER_ID.value, workspaceId.value, "2026-08")
            assertEquals(setOf(ownSharedCategory.id.value, memberSharedCategory.id.value), sharedCategories.map { it.id }.toSet())
            assertEquals(setOf(ownSharedTransaction.id.value, memberSharedTransaction.id.value), sharedTransactions.map { it.id }.toSet())
            assertEquals(setOf("budget-mine", "budget-member"), sharedBudgets.map { it.id }.toSet())

            val personalCategories = database.categoryDao().observeAll(USER_ID.value, null, null).first()
            val personalTransactions = database.transactionDao().observeAll(
                USER_ID.value, null, null, null, null, null, null, null,
            ).first()
            val personalBudgets = database.budgetDao().getForMonth(USER_ID.value, null, "2026-08")
            assertTrue(personalCategories.none { it.ownerId == "user-2" })
            assertTrue(personalTransactions.none { it.ownerId == "user-2" })
            assertTrue(personalBudgets.none { it.ownerId == "user-2" })
        } finally {
            database.close()
        }
    }

    @Test
    fun soft_deleted_row_is_hidden_from_normal_queries() = runTest {
        val database = inMemoryDatabase()
        try {
            val categoryEntity = category().toEntity(SYNC)
            database.categoryDao().upsert(categoryEntity)
            assertEquals(CATEGORY_ID.value, database.categoryDao().observeById(CATEGORY_ID.value).first()?.id)

            database.categoryDao().upsert(
                categoryEntity.copy(
                    sync = categoryEntity.sync.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
                ),
            )

            assertNull(database.categoryDao().observeById(CATEGORY_ID.value).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun default_and_custom_categories_are_observed_together_and_filtered_by_type_correctly() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultExpense = CategoryEntity(
                id = "11111111-1111-4111-8111-111111111112",
                ownerId = null,
                workspaceId = null,
                scopeKey = "system",
                name = "Market",
                normalizedName = "market",
                slug = "market",
                typeCode = "EXPENSE",
                colorHex = "#EF4444",
                iconKey = "shopping-cart",
                isDefault = true,
                createdAtEpochMillis = 1_000L,
                sync = SYNC,
            )
            val defaultIncome = CategoryEntity(
                id = "11111111-1111-4111-8111-111111111101",
                ownerId = null,
                workspaceId = null,
                scopeKey = "system",
                name = "Maaş",
                normalizedName = "maaş",
                slug = "maas",
                typeCode = "INCOME",
                colorHex = "#10B981",
                iconKey = "briefcase",
                isDefault = true,
                createdAtEpochMillis = 1_000L,
                sync = SYNC,
            )
            val customExpense = CategoryEntity(
                id = "custom-exp-1",
                ownerId = USER_ID.value,
                workspaceId = null,
                scopeKey = "user:${USER_ID.value}",
                name = "Hobiler",
                normalizedName = "hobiler",
                slug = null,
                typeCode = "EXPENSE",
                colorHex = "#EC4899",
                iconKey = "film",
                isDefault = false,
                createdAtEpochMillis = 2_000L,
                sync = SYNC,
            )
            val customIncome = CategoryEntity(
                id = "custom-inc-1",
                ownerId = USER_ID.value,
                workspaceId = null,
                scopeKey = "user:${USER_ID.value}",
                name = "Ek Gelir",
                normalizedName = "ek gelir",
                slug = null,
                typeCode = "INCOME",
                colorHex = "#34D399",
                iconKey = "laptop",
                isDefault = false,
                createdAtEpochMillis = 2_000L,
                sync = SYNC,
            )
            val deletedExpense = customExpense.copy(
                id = "deleted-exp-1",
                name = "Eski Gider",
                normalizedName = "eski gider",
                sync = SYNC.copy(deletedAtEpochMillis = 3_000L),
            )

            database.categoryDao().upsert(defaultExpense)
            database.categoryDao().upsert(defaultIncome)
            database.categoryDao().upsert(customExpense)
            database.categoryDao().upsert(customIncome)
            database.categoryDao().upsert(deletedExpense)

            // Test Expense Filter: default + custom active expense, sorted default first
            val expenseCategories = database.categoryDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                typeCode = "EXPENSE",
            ).first()
            assertEquals(listOf(defaultExpense.id, customExpense.id), expenseCategories.map { it.id })

            // Test Income Filter: default + custom active income, sorted default first
            val incomeCategories = database.categoryDao().observeAll(
                ownerId = USER_ID.value,
                workspaceId = null,
                typeCode = "INCOME",
            ).first()
            assertEquals(listOf(defaultIncome.id, customIncome.id), incomeCategories.map { it.id })

            // Test History Lookup: includes soft-deleted category
            val historyCategories = database.categoryDao().observeAllForHistoryLookup(
                ownerId = USER_ID.value,
                workspaceId = null,
            ).first()
            assertTrue(historyCategories.any { it.id == deletedExpense.id })
            assertEquals(5, historyCategories.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_transaction_and_outbox_survive_database_reopen_and_store_backoff() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "feniqo-outbox-${System.nanoTime()}.db"
        var nowEpochMillis = 10_000L

        try {
            val firstDatabase = persistentDatabase(context, databaseName)
            firstDatabase.categoryDao().upsert(category().toEntity(SYNC))
            OfflineWriteQueue(
                mutationDao = firstDatabase.localMutationDao(),
                operationDao = firstDatabase.syncOperationDao(),
                nowEpochMillisProvider = { nowEpochMillis },
                operationIdFactory = { "operation-persisted" },
            ).enqueueTransaction(
                entity = transaction().toEntity(SYNC),
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
            )
            firstDatabase.close()

            val reopenedDatabase = persistentDatabase(context, databaseName)
            try {
                assertEquals(
                    TRANSACTION_ID.value,
                    reopenedDatabase.transactionDao().observeById(TRANSACTION_ID.value).first()?.id,
                )
                assertEquals(1, reopenedDatabase.syncOperationDao().observePendingCount().first())

                val queue = OfflineWriteQueue(
                    mutationDao = reopenedDatabase.localMutationDao(),
                    operationDao = reopenedDatabase.syncOperationDao(),
                    nowEpochMillisProvider = { nowEpochMillis },
                    operationIdFactory = { "unused" },
                )
                assertTrue(queue.markInFlight("operation-persisted"))
                assertTrue(queue.recordFailure("operation-persisted", "ağ bağlantısı yok"))

                val failed = reopenedDatabase.syncOperationDao().getById("operation-persisted")
                assertEquals(1, failed?.attemptCount)
                assertEquals("ağ bağlantısı yok", failed?.lastError)
                assertEquals(25_000L, failed?.nextAttemptAtEpochMillis)
                assertTrue(queue.getReadyOperations().isEmpty())

                nowEpochMillis = 25_000L
                assertEquals(listOf("operation-persisted"), queue.getReadyOperations().map { it.operationId })
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun entity_write_is_rolled_back_when_outbox_insert_fails() = runTest {
        val database = inMemoryDatabase()
        try {
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                nowEpochMillisProvider = { 10_000L },
                operationIdFactory = { "duplicate-operation" },
            )
            queue.enqueueCategory(category().toEntity(SYNC), OutboxOperationType.CREATE)

            val secondCategory = category().copy(
                id = EntityId("category-2"),
                name = "Ulaşım",
            ).toEntity(SYNC)
            assertFailsWith<Exception> {
                queue.enqueueCategory(secondCategory, OutboxOperationType.CREATE)
            }

            assertNull(database.categoryDao().observeById(secondCategory.id).first())
            assertEquals(1, database.syncOperationDao().observePendingCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun create_update_and_delete_events_are_kept_in_order() = runTest {
        val database = inMemoryDatabase()
        try {
            var nowEpochMillis = 1_000L
            var operationNumber = 0
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                nowEpochMillisProvider = { nowEpochMillis },
                operationIdFactory = { "operation-${++operationNumber}" },
            )
            val created = category().toEntity(SYNC)
            queue.enqueueCategory(created, OutboxOperationType.CREATE)

            nowEpochMillis = 2_000L
            val updated = created.copy(
                name = "Süpermarket",
                normalizedName = "süpermarket",
                sync = created.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    localUpdatedAtEpochMillis = nowEpochMillis,
                ),
            )
            queue.enqueueCategory(updated, OutboxOperationType.UPDATE)

            nowEpochMillis = 3_000L
            val deleted = updated.copy(
                sync = updated.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = nowEpochMillis,
                    localUpdatedAtEpochMillis = nowEpochMillis,
                ),
            )
            queue.enqueueCategory(deleted, OutboxOperationType.DELETE)

            assertEquals(
                listOf("CREATE", "UPDATE", "DELETE"),
                queue.getReadyOperations().map { it.operationTypeCode },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_write_queue_triggers_background_sync_scheduler_after_successful_mutation() = runTest {
        val database = inMemoryDatabase()
        try {
            var outboxSyncCalled = 0
            val scheduler = object : com.feniqo.mobile.domain.sync.BackgroundSyncScheduler {
                override fun scheduleInitialSync() {}
                override fun scheduleOutboxSync() { outboxSyncCalled++ }
                override fun cancelSyncWork() {}
            }
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
                syncScheduler = scheduler,
                nowEpochMillisProvider = { 1_000L },
                operationIdFactory = { "op-1" },
            )
            val category = category().toEntity(newSyncMetadata(1_000L).copy(syncStatus = SyncStatus.PENDING_CREATE.name))
            queue.enqueueCategory(category, OutboxOperationType.CREATE)

            assertEquals(1, outboxSyncCalled)
        } finally {
            database.close()
        }
    }

    @Test
    fun sync_user_states_stores_and_observes_last_successful_sync_at_per_user_isolated() = runTest {
        val database = inMemoryDatabase()
        try {
            val dao = database.syncStateDao()

            assertNull(dao.getUserState("user-1"))
            assertNull(dao.observeLastSuccessfulSyncAt("user-1").first())

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-1",
                    lastSuccessfulSyncAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-2",
                    lastSuccessfulSyncAtEpochMillis = 2_000L,
                    updatedAtEpochMillis = 2_000L,
                ),
            )

            assertEquals(1_000L, dao.observeLastSuccessfulSyncAt("user-1").first())
            assertEquals(2_000L, dao.observeLastSuccessfulSyncAt("user-2").first())
            assertNull(dao.observeLastSuccessfulSyncAt("user-3").first())

            dao.upsertUserState(
                com.feniqo.mobile.data.local.entity.SyncUserStateEntity(
                    userId = "user-1",
                    lastSuccessfulSyncAtEpochMillis = 3_000L,
                    updatedAtEpochMillis = 3_000L,
                ),
            )
            assertEquals(3_000L, dao.observeLastSuccessfulSyncAt("user-1").first())
            assertEquals(2_000L, dao.observeLastSuccessfulSyncAt("user-2").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun observe_failed_count_tracks_pending_in_flight_failed_and_retried_operations() = runTest {
        val database = inMemoryDatabase()
        try {
            val dao = database.syncOperationDao()

            assertEquals(0, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-1",
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-1",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.claimOperation("op-1", 1_000L)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.markFailed("op-1", "network_error", OutboxErrorClassification.AMBIGUOUS_RESULT.name, 2_000L, 1_500L)
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(1, dao.observeFailedCount().first())

            dao.insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-2",
                    entityTypeCode = "TRANSACTION",
                    entityId = "tx-1",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            dao.markFailed("op-2", "timeout", OutboxErrorClassification.AMBIGUOUS_RESULT.name, 2_000L, 1_500L)
            assertEquals(2, dao.observePendingCount().first())
            assertEquals(2, dao.observeFailedCount().first())

            dao.retryAllFailed(2_000L)
            assertEquals(2, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())

            dao.deleteCompleted("op-1")
            assertEquals(1, dao.observePendingCount().first())
            assertEquals(0, dao.observeFailedCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun offline_write_queue_observe_failed_count_delegates_to_dao() = runTest {
        val database = inMemoryDatabase()
        try {
            val queue = OfflineWriteQueue(
                mutationDao = database.localMutationDao(),
                operationDao = database.syncOperationDao(),
            )
            assertEquals(0, queue.observeFailedCount().first())

            database.syncOperationDao().insert(
                com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                    operationId = "op-f",
                    entityTypeCode = "CATEGORY",
                    entityId = "cat-f",
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    statusCode = "FAILED",
                    attemptCount = 1,
                    lastError = "error",
                    nextAttemptAtEpochMillis = 1_000L,
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            assertEquals(1, queue.observeFailedCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun migration_3_to_4_creates_sync_user_states_and_preserves_existing_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v3_to_v4.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v3Db = migrationHelper.createDatabase(testDbPath, 3)
        try {
            v3Db.execSQL("INSERT INTO sync_cursors (entity_type_code, updated_at_epoch_ms, entity_id) VALUES ('PROFILE', 1000, 'user-1')")
        } finally {
            v3Db.close()
        }

        val v4Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            4,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_3_4,
        )
        try {
            val cursor = v4Db.query("SELECT entity_type_code, updated_at_epoch_ms FROM sync_cursors WHERE entity_type_code = 'PROFILE'")
            assertTrue(cursor.moveToFirst())
            assertEquals("PROFILE", cursor.getString(0))
            assertEquals(1000L, cursor.getLong(1))
            cursor.close()

            v4Db.execSQL("INSERT INTO sync_user_states (user_id, last_successful_sync_at_epoch_ms, updated_at_epoch_ms) VALUES ('user-1', 5000, 5000)")
            val userStateCursor = v4Db.query("SELECT user_id, last_successful_sync_at_epoch_ms, updated_at_epoch_ms FROM sync_user_states WHERE user_id = 'user-1'")
            assertTrue(userStateCursor.moveToFirst())
            assertEquals("user-1", userStateCursor.getString(0))
            assertEquals(5000L, userStateCursor.getLong(1))
            assertEquals(5000L, userStateCursor.getLong(2))
            userStateCursor.close()
        } finally {
            v4Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migration_4_to_5_adds_v2_columns_and_preserves_existing_operations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v4_to_v5.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v4Db = migrationHelper.createDatabase(testDbPath, 4)
        try {
            v4Db.execSQL(
                """
                INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'legacy-op-1', 'CATEGORY', 'cat-1', 'UPDATE',
                    1, 'PENDING', 0, NULL,
                    1000, 1000, 1000
                )
                """.trimIndent(),
            )
        } finally {
            v4Db.close()
        }

        val v5Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            5,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_4_5,
        )
        try {
            val cursor = v5Db.query(
                "SELECT operation_id, entity_type_code, entity_id, protocol_version, is_blocked, payload_json, predecessor_operation_id FROM sync_operations WHERE operation_id = 'legacy-op-1'",
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-op-1", cursor.getString(0))
            assertEquals("CATEGORY", cursor.getString(1))
            assertEquals("cat-1", cursor.getString(2))
            assertEquals(1, cursor.getInt(3)) // default protocol_version = 1
            assertEquals(0, cursor.getInt(4)) // default is_blocked = 0
            assertTrue(cursor.isNull(5)) // payload_json = null
            assertTrue(cursor.isNull(6)) // predecessor_operation_id = null
            cursor.close()

            // Insert new v2 operation with payload and predecessor
            v5Db.execSQL(
                """
                INSERT INTO sync_operations (
                    operation_id, entity_type_code, entity_id, operation_type_code,
                    base_version, payload_json, predecessor_operation_id, is_blocked,
                    protocol_version, status_code, attempt_count, last_error,
                    next_attempt_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'v2-op-2', 'CATEGORY', 'cat-1', 'UPDATE',
                    NULL, '{"name":"Giyim"}', 'legacy-op-1', 1,
                    2, 'PENDING', 0, NULL,
                    2000, 2000, 2000
                )
                """.trimIndent(),
            )
            val v2Cursor = v5Db.query(
                "SELECT operation_id, protocol_version, is_blocked, payload_json, predecessor_operation_id FROM sync_operations WHERE operation_id = 'v2-op-2'",
            )
            assertTrue(v2Cursor.moveToFirst())
            assertEquals("v2-op-2", v2Cursor.getString(0))
            assertEquals(2, v2Cursor.getInt(1))
            assertEquals(1, v2Cursor.getInt(2))
            assertEquals("{\"name\":\"Giyim\"}", v2Cursor.getString(3))
            assertEquals("legacy-op-1", v2Cursor.getString(4))
            v2Cursor.close()
        } finally {
            v5Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migration_11_to_12_adds_split_columns_and_backfills_transactions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v11_to_v12.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v11Db = migrationHelper.createDatabase(testDbPath, 11)
        try {
            v11Db.execSQL(
                """
                INSERT INTO categories (
                    id, owner_id, workspace_id, scope_key, name, normalized_name, slug,
                    type_code, color_hex, icon_key, is_default, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'cat-1', 'user-1', NULL, 'user-1', 'Market', 'market', 'market',
                    'EXPENSE', '#123456', NULL, 0, 1000,
                    'SYNCED', 1000, 1000, NULL,
                    1, 1, NULL
                )
                """.trimIndent(),
            )
            v11Db.execSQL(
                """
                INSERT INTO transactions (
                    id, owner_id, workspace_id, amount_minor, currency_code, type_code,
                    category_id, description, search_text, payment_method_code, transaction_date,
                    receipt_path, installment_number, total_installments, installment_group_id,
                    created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'tx-v11-1', 'user-1', NULL, 12550, 'TRY', 'EXPENSE',
                    'cat-1', 'market', 'market', 'DEBIT_CARD', '2026-08-05',
                    NULL, NULL, NULL, NULL,
                    1000, 'SYNCED', 1000,
                    1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v11Db.close()
        }

        val v12Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            12,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_11_12,
        )
        try {
            val cursor = v12Db.query(
                "SELECT id, owner_id, paid_by_user_id, participant_user_ids_json FROM transactions WHERE id = 'tx-v11-1'",
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("tx-v11-1", cursor.getString(0))
            assertEquals("user-1", cursor.getString(1))
            assertEquals("user-1", cursor.getString(2))
            assertEquals("[\"user-1\"]", cursor.getString(3))
            cursor.close()

            // Insert new transaction with explicit split values
            v12Db.execSQL(
                """
                INSERT INTO transactions (
                    id, owner_id, workspace_id, paid_by_user_id, participant_user_ids_json,
                    amount_minor, currency_code, type_code,
                    category_id, description, search_text, payment_method_code, transaction_date,
                    receipt_path, installment_number, total_installments, installment_group_id,
                    created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'tx-v12-2', 'user-1', NULL, 'user-2', '["user-1","user-2"]',
                    25000, 'TRY', 'EXPENSE',
                    'cat-1', 'dinner', 'dinner', 'CREDIT_CARD', '2026-09-08',
                    NULL, NULL, NULL, NULL,
                    2000, 'SYNCED', 2000,
                    2000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val v12Cursor = v12Db.query(
                "SELECT id, paid_by_user_id, participant_user_ids_json FROM transactions WHERE id = 'tx-v12-2'",
            )
            assertTrue(v12Cursor.moveToFirst())
            assertEquals("tx-v12-2", v12Cursor.getString(0))
            assertEquals("user-2", v12Cursor.getString(1))
            assertEquals("[\"user-1\",\"user-2\"]", v12Cursor.getString(2))
            v12Cursor.close()
        } finally {
            v12Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun migration_12_to_13_creates_personal_assets_table() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration_test_v12_to_v13.db"
        val testDbPath = context.getDatabasePath(testDbName).absolutePath
        context.deleteDatabase(testDbName)

        val v12Db = migrationHelper.createDatabase(testDbPath, 12)
        v12Db.close()

        val v13Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            13,
            true,
            com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_12_13,
        )
        try {
            v13Db.execSQL(
                """
                INSERT INTO assets (
                    id, owner_id, name, type_code, current_value_minor, currency_code,
                    quantity_unscaled, quantity_scale, purchase_unit_price_minor, tracking_symbol,
                    auto_track, created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version, base_version, last_sync_error
                ) VALUES (
                    'asset-v13-1', 'user-1', 'Nakit', 'CASH', 10000, 'TRY',
                    NULL, NULL, NULL, NULL, 0, 1000, 'SYNCED', 1000,
                    1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            val cursor = v13Db.query("SELECT owner_id, name, current_value_minor FROM assets WHERE id = 'asset-v13-1'")
            assertTrue(cursor.moveToFirst())
            assertEquals("user-1", cursor.getString(0))
            assertEquals("Nakit", cursor.getString(1))
            assertEquals(10_000L, cursor.getLong(2))
            cursor.close()
        } finally {
            v13Db.close()
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun transactionDao_observeByIdAndOwner_isolates_by_owner_and_deleted_status() = runTest {
        val database = inMemoryDatabase()
        try {
            val category = category().toEntity(SYNC)
            database.categoryDao().upsert(category)

            val trxUser1 = transaction().toEntity(SYNC)
            val trxUser2 = transaction().copy(id = EntityId("t2"), ownerId = EntityId("user-2")).toEntity(SYNC)
            val trxDeleted = transaction().copy(id = EntityId("t3")).toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.transactionDao().upsert(trxUser1)
            database.transactionDao().upsert(trxUser2)
            database.transactionDao().upsert(trxDeleted)

            // User 1 sees their own active transaction
            val observed1 = database.transactionDao().observeByIdAndOwner("transaction-1", "user-1").first()
            assertEquals("transaction-1", observed1?.id)

            val fetched1 = database.transactionDao().getByIdAndOwner("transaction-1", "user-1")
            assertEquals("transaction-1", fetched1?.id)

            // User 1 cannot see user 2's transaction
            val observedOther = database.transactionDao().observeByIdAndOwner("t2", "user-1").first()
            assertNull(observedOther)

            val fetchedOther = database.transactionDao().getByIdAndOwner("t2", "user-1")
            assertNull(fetchedOther)

            // Deleted transaction is not returned
            val observedDel = database.transactionDao().observeByIdAndOwner("t3", "user-1").first()
            assertNull(observedDel)
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_observeByIdAndOwner_allows_default_and_matching_owner_only() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultCat = category().copy(id = EntityId("c-def"), ownerId = null, isDefault = true, name = "Varsayılan").toEntity(SYNC)
            val user1Cat = category().copy(id = EntityId("c-u1"), ownerId = EntityId("user-1"), name = "Market").toEntity(SYNC)
            val user2Cat = category().copy(id = EntityId("c-u2"), ownerId = EntityId("user-2"), name = "Giyim").toEntity(SYNC)
            val deletedCat = category().copy(id = EntityId("c-del"), ownerId = EntityId("user-1"), name = "Eski Kategori").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.categoryDao().upsert(defaultCat)
            database.categoryDao().upsert(user1Cat)
            database.categoryDao().upsert(user2Cat)
            database.categoryDao().upsert(deletedCat)

            // Default category can be observed by any user
            val obsDef = database.categoryDao().observeByIdAndOwner("c-def", "user-1").first()
            assertEquals("c-def", obsDef?.id)

            // User 1 can observe their own category
            val obsU1 = database.categoryDao().observeByIdAndOwner("c-u1", "user-1").first()
            assertEquals("c-u1", obsU1?.id)

            // User 1 cannot observe User 2's category
            val obsU2 = database.categoryDao().observeByIdAndOwner("c-u2", "user-1").first()
            assertNull(obsU2)

            // Deleted category is not returned
            val obsDel = database.categoryDao().observeByIdAndOwner("c-del", "user-1").first()
            assertNull(obsDel)
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_observeAllForHistoryLookup_includes_matching_owner_deleted_and_defaults_only() = runTest {
        val database = inMemoryDatabase()
        try {
            val defaultCat = category().copy(id = EntityId("c-def"), ownerId = null, isDefault = true, name = "Varsayılan").toEntity(SYNC)
            val user1Active = category().copy(id = EntityId("c-u1-act"), ownerId = EntityId("user-1"), name = "Market").toEntity(SYNC)
            val user1Deleted = category().copy(id = EntityId("c-u1-del"), ownerId = EntityId("user-1"), name = "Eski Kategori").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))
            val user2Deleted = category().copy(id = EntityId("c-u2-del"), ownerId = EntityId("user-2"), name = "Diğer Kullanıcı").toEntity(SYNC.copy(deletedAtEpochMillis = 2000L))

            database.categoryDao().upsert(defaultCat)
            database.categoryDao().upsert(user1Active)
            database.categoryDao().upsert(user1Deleted)
            database.categoryDao().upsert(user2Deleted)

            val historyList = database.categoryDao().observeAllForHistoryLookup("user-1", null).first()
            assertEquals(3, historyList.size)
            assertTrue(historyList.any { it.id == "c-def" })
            assertTrue(historyList.any { it.id == "c-u1-act" })
            assertTrue(historyList.any { it.id == "c-u1-del" }) // Silinmiş kategoriyi içerir
            assertTrue(historyList.none { it.id == "c-u2-del" }) // Başka kullanıcının silinmişi dönmez
        } finally {
            database.close()
        }
    }

    @Test
    fun categoryDao_rejects_corrupted_default_category_with_other_owner() = runTest {
        val database = inMemoryDatabase()
        try {
            val realDefault = category().copy(id = EntityId("c-real-def"), ownerId = null, isDefault = true, name = "Gerçek Varsayılan").toEntity(SYNC)
            val corruptedDefault = CategoryEntity(
                id = "c-bad-def",
                ownerId = "user-2",
                workspaceId = null,
                scopeKey = "personal:user-2",
                name = "Bozuk Varsayılan",
                normalizedName = "bozuk varsayilan",
                slug = null,
                typeCode = "EXPENSE",
                colorHex = "#FF0000",
                iconKey = null,
                isDefault = true,
                createdAtEpochMillis = 1000L,
                sync = SYNC,
            )
            val user1Category = category().copy(id = EntityId("c-u1"), ownerId = EntityId("user-1"), isDefault = false, name = "Kullanıcı 1").toEntity(SYNC)

            database.categoryDao().upsert(realDefault)
            database.categoryDao().upsert(corruptedDefault)
            database.categoryDao().upsert(user1Category)

            // observeAll: only real default and user1 category
            val activeList = database.categoryDao().observeAll("user-1", null, null).first()
            assertEquals(2, activeList.size)
            assertTrue(activeList.any { it.id == "c-real-def" })
            assertTrue(activeList.any { it.id == "c-u1" })
            assertTrue(activeList.none { it.id == "c-bad-def" })

            // observeByIdAndOwner: returns null for corrupted default owned by other user
            val obsBad = database.categoryDao().observeByIdAndOwner("c-bad-def", "user-1").first()
            assertNull(obsBad)

            // getByIdAndOwner: returns null for corrupted default owned by other user
            val getBad = database.categoryDao().getByIdAndOwner("c-bad-def", "user-1")
            assertNull(getBad)

            // observeAllForHistoryLookup: does not return corrupted default
            val historyList = database.categoryDao().observeAllForHistoryLookup("user-1", null).first()
            assertEquals(2, historyList.size)
            assertTrue(historyList.none { it.id == "c-bad-def" })
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionKeepingTags_preserves_cross_refs_during_update_and_delete() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val trx = transaction().toEntity(SYNC)
            val tag = Tag(TAG_ID, USER_ID, null, "Yemek", NOW).toEntity(SYNC)
            val link = TransactionTag(transaction().id, TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val createOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = "op-1",
                entityTypeCode = "TRANSACTION",
                entityId = trx.id,
                operationTypeCode = OutboxOperationType.CREATE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )

            // 1. Initial write with tags
            database.localMutationDao().upsertTransactionAndEnqueue(
                entity = trx,
                tags = listOf(tag),
                tagLinks = listOf(link),
                operation = createOp,
            )

            val initialWithTags = database.transactionDao().observeWithTags(trx.id).first()
            assertEquals(1, initialWithTags?.tags?.size)
            assertEquals("Yemek", initialWithTags?.tags?.first()?.name)

            // 2. Update using upsertTransactionKeepingTagsAndEnqueue
            val updatedTrx = trx.copy(description = "Güncellenmiş açıklama")
            val updateOp = createOp.copy(operationId = "op-2", operationTypeCode = OutboxOperationType.UPDATE.name)
            database.localMutationDao().upsertTransactionKeepingTagsAndEnqueue(
                entity = updatedTrx,
                operation = updateOp,
            )

            val afterUpdateWithTags = database.transactionDao().observeWithTags(trx.id).first()
            assertEquals("Güncellenmiş açıklama", afterUpdateWithTags?.transaction?.description)
            assertEquals(1, afterUpdateWithTags?.tags?.size)
            assertEquals("Yemek", afterUpdateWithTags?.tags?.first()?.name)

            // 3. Soft-delete using upsertTransactionKeepingTagsAndEnqueue
            val deletedTrx = updatedTrx.copy(sync = SYNC.copy(deletedAtEpochMillis = 5000L))
            val deleteOp = createOp.copy(operationId = "op-3", operationTypeCode = OutboxOperationType.DELETE.name)
            database.localMutationDao().upsertTransactionKeepingTagsAndEnqueue(
                entity = deletedTrx,
                operation = deleteOp,
            )

            // Outbox operations exist
            val pendingCount = database.syncOperationDao().observePendingCount().first()
            assertEquals(3, pendingCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionsAndEnqueue_writes_batch_atomically_and_rolls_back_on_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val tag = Tag(TAG_ID, USER_ID, null, "Zorunlu", NOW).toEntity(SYNC)

            val t1 = transaction().toEntity(SYNC).copy(id = "batch-trx-1", description = "Taksit 1/3")
            val t2 = transaction().toEntity(SYNC).copy(id = "batch-trx-2", description = "Taksit 2/3")
            val t3 = transaction().toEntity(SYNC).copy(id = "batch-trx-3", description = "Taksit 3/3")

            val link1 = TransactionTag(EntityId(t1.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)

            fun makeOp(opId: String, entityId: String) = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = opId,
                entityTypeCode = "TRANSACTION",
                entityId = entityId,
                operationTypeCode = OutboxOperationType.CREATE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1000L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
            )

            val successUnits = listOf(
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t1,
                    tags = listOf(tag),
                    tagLinks = listOf(link1),
                    operation = makeOp("op-b-1", t1.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t2,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    operation = makeOp("op-b-2", t2.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = t3,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    operation = makeOp("op-b-3", t3.id),
                ),
            )

            // 1. Success batch write
            database.localMutationDao().upsertTransactionsAndEnqueue(successUnits)

            val activeTrxs = database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first()
            assertEquals(3, activeTrxs.size)
            assertEquals(3, database.syncOperationDao().observePendingCount().first())
            assertEquals(1, database.transactionDao().observeWithTags(t1.id).first()?.tags?.size)

            // 2. Rollback test on error
            val failUnit1 = com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                entity = transaction().toEntity(SYNC).copy(id = "rollback-trx-1"),
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = makeOp("op-rb-1", "rollback-trx-1"),
            )
            val failUnit2 = com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                entity = transaction().toEntity(SYNC).copy(
                    id = "rollback-trx-2",
                    categoryId = "non-existing-category-id", // Foreign key violation!
                ),
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = makeOp("op-rb-2", "rollback-trx-2"),
            )

            assertFailsWith<androidx.sqlite.SQLiteException> {
                database.localMutationDao().upsertTransactionsAndEnqueue(listOf(failUnit1, failUnit2))
            }

            // Verify full rollback: rollback-trx-1 was NOT saved, and op-rb-1 was NOT enqueued!
            val rollbackTrx = database.transactionDao().getByIdAndOwner("rollback-trx-1", USER_ID.value)
            assertNull(rollbackTrx)
            assertEquals(3, database.syncOperationDao().observePendingCount().first()) // Still 3 from previous batch
        } finally {
            database.close()
        }
    }

    @Test
    fun localMutationDao_upsertTransactionsKeepingTagsAndEnqueue_softDeletes_batch_atomically_preserves_tags_and_rolls_back_on_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category().toEntity(SYNC)
            database.categoryDao().upsert(cat)

            val tag = Tag(TAG_ID, USER_ID, null, "Etiket", NOW).toEntity(SYNC)
            database.transactionDao().upsertTags(listOf(tag))

            val t1 = transaction().toEntity(SYNC).copy(id = "del-trx-1", description = "Taksit 1/3")
            val t2 = transaction().toEntity(SYNC).copy(id = "del-trx-2", description = "Taksit 2/3")
            val t3 = transaction().toEntity(SYNC).copy(id = "del-trx-3", description = "Taksit 3/3")
            database.transactionDao().upsert(t1)
            database.transactionDao().upsert(t2)
            database.transactionDao().upsert(t3)

            val link1 = TransactionTag(EntityId(t1.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val link2 = TransactionTag(EntityId(t2.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            val link3 = TransactionTag(EntityId(t3.id), TAG_ID).toEntity(NOW.toEpochMilliseconds(), SYNC)
            database.transactionDao().upsertTagLinks(listOf(link1, link2, link3))

            // Verify initial tag links
            assertEquals(1, database.transactionDao().observeWithTags(t1.id).first()?.tags?.size)
            assertEquals(1, database.transactionDao().observeWithTags(t2.id).first()?.tags?.size)
            assertEquals(1, database.transactionDao().observeWithTags(t3.id).first()?.tags?.size)

            fun makeDelOp(opId: String, entityId: String) = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
                operationId = opId,
                entityTypeCode = "TRANSACTION",
                entityId = entityId,
                operationTypeCode = OutboxOperationType.DELETE.name,
                baseVersion = null,
                statusCode = com.feniqo.mobile.data.local.outbox.OutboxStatus.PENDING.name,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 5000L,
                createdAtEpochMillis = 5000L,
                updatedAtEpochMillis = 5000L,
            )

            val deleteSync = SYNC.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 5000L)
            val deleteUnits = listOf(
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t1.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-1", t1.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t2.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-2", t2.id),
                ),
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = t3.copy(sync = deleteSync),
                    operation = makeDelOp("op-del-3", t3.id),
                ),
            )

            // 1. Success batch soft-delete
            database.localMutationDao().upsertTransactionsKeepingTagsAndEnqueue(deleteUnits)

            val activeTrxs = database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first()
            assertEquals(0, activeTrxs.size) // All 3 are soft-deleted
            assertEquals(3, database.syncOperationDao().observePendingCount().first())

            // Verify tag links are PRESERVED in SQLite cross-ref table!
            val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_tags WHERE transaction_id IN ('del-trx-1', 'del-trx-2', 'del-trx-3')")
            cursor.moveToFirst()
            val preservedTagCount = cursor.getInt(0)
            cursor.close()
            assertEquals(3, preservedTagCount)

            // 2. Rollback test on error
            val t4 = transaction().toEntity(SYNC).copy(id = "rollback-del-4", description = "Taksit 4")
            database.transactionDao().upsert(t4)
            assertEquals(1, database.transactionDao().observeAll(USER_ID.value, null, null, null, null, null, null, null).first().size)

            val rollbackDelUnit1 = com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                entity = t4.copy(sync = deleteSync),
                operation = makeDelOp("op-del-4", t4.id),
            )
            val rollbackDelUnit2 = com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                entity = t1.copy(categoryId = "non-existing-category-fk"), // Foreign key violation!
                operation = makeDelOp("op-del-5", t1.id),
            )

            assertFailsWith<androidx.sqlite.SQLiteException> {
                database.localMutationDao().upsertTransactionsKeepingTagsAndEnqueue(listOf(rollbackDelUnit1, rollbackDelUnit2))
            }

            // Verify rollback: t4 is STILL active (not soft-deleted!)
            val t4AfterRollback = database.transactionDao().getByIdAndOwner(t4.id, USER_ID.value)
            assertNotNull(t4AfterRollback)
            assertNull(t4AfterRollback.sync.deletedAtEpochMillis)
            assertEquals(3, database.syncOperationDao().observePendingCount().first()) // Still 3, op-del-4 was rolled back
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getByIdAndOwner_returnsOnlyActiveOwnedBudget() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category()
            database.categoryDao().upsert(cat.toEntity(SYNC))

            val activeBudget = budget("b-active", ownerId = USER_ID, categoryId = cat.id)
            val otherUserBudget = budget("b-other-user", ownerId = EntityId("other-user"), categoryId = cat.id)
            val deletedBudget = budget("b-deleted", ownerId = USER_ID, categoryId = cat.id)

            database.budgetDao().upsert(activeBudget.toEntity(SYNC))
            database.budgetDao().upsert(otherUserBudget.toEntity(SYNC))
            database.budgetDao().upsert(
                deletedBudget.toEntity(
                    SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
                ),
            )

            // Doğru owner aktif kaydı alır
            val resultOwned = database.budgetDao().getByIdAndOwner(activeBudget.id.value, USER_ID.value)
            assertNotNull(resultOwned)
            assertEquals("b-active", resultOwned.id)

            // Yanlış owner null alır
            val resultWrongOwner = database.budgetDao().getByIdAndOwner(activeBudget.id.value, "wrong-owner")
            assertNull(resultWrongOwner)

            // Başka kullanıcının kaydı null alır
            val resultOtherUser = database.budgetDao().getByIdAndOwner(otherUserBudget.id.value, USER_ID.value)
            assertNull(resultOtherUser)

            // Soft-delete edilmiş kayıt null alır
            val resultDeleted = database.budgetDao().getByIdAndOwner(deletedBudget.id.value, USER_ID.value)
            assertNull(resultDeleted)
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getAnyByScopeCategoryAndMonth_returnsSoftDeletedBudget() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat = category()
            database.categoryDao().upsert(cat.toEntity(SYNC))

            val deletedBudget = budget("b-soft-del", ownerId = USER_ID, categoryId = cat.id, month = "2026-08")
            val deletedEntity = deletedBudget.toEntity(
                SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds()),
            )
            database.budgetDao().upsert(deletedEntity)

            // deleted_at dolu kayıt reaktivasyon sorgusuyla bulunur
            val found = database.budgetDao().getAnyByScopeCategoryAndMonth(
                scopeKey = deletedEntity.scopeKey,
                categoryId = cat.id.value,
                month = "2026-08",
            )
            assertNotNull(found)
            assertEquals("b-soft-del", found.id)
            assertEquals(NOW.toEpochMilliseconds(), found.sync.deletedAtEpochMillis)

            // Yanlış scope null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth("wrong-scope", cat.id.value, "2026-08"))

            // Yanlış category null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth(deletedEntity.scopeKey, "other-cat", "2026-08"))

            // Yanlış month null döner
            assertNull(database.budgetDao().getAnyByScopeCategoryAndMonth(deletedEntity.scopeKey, cat.id.value, "2026-09"))
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getForMonth_filtersOwnerWorkspaceMonthAndDeletedRows() = runTest {
        val database = inMemoryDatabase()
        try {
            val cat1 = category().copy(id = EntityId("cat-1"), name = "Market 1")
            val cat2 = category().copy(id = EntityId("cat-2"), name = "Market 2")
            database.categoryDao().upsert(cat1.toEntity(SYNC))
            database.categoryDao().upsert(cat2.toEntity(SYNC))

            val ws = Workspace(id = EntityId("ws-1"), name = "Ortak Alan", ownerId = USER_ID, createdAt = NOW)
            database.workspaceDao().upsertWorkspace(ws.toEntity(SYNC))

            val valid1 = budget("b-v1", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-08")
            val valid2 = budget("b-v2", ownerId = USER_ID, workspaceId = null, categoryId = cat2.id, month = "2026-08")
            val otherOwner = budget("b-other-owner", ownerId = EntityId("user-2"), workspaceId = null, categoryId = cat1.id, month = "2026-08")
            val otherWorkspace = budget("b-ws", ownerId = USER_ID, workspaceId = EntityId("ws-1"), categoryId = cat1.id, month = "2026-08")
            val otherMonth = budget("b-other-m", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-09")
            val deleted = budget("b-del", ownerId = USER_ID, workspaceId = null, categoryId = cat1.id, month = "2026-08")

            database.budgetDao().upsert(valid1.toEntity(SYNC))
            database.budgetDao().upsert(valid2.toEntity(SYNC))
            database.budgetDao().upsert(otherOwner.toEntity(SYNC))
            database.budgetDao().upsert(otherWorkspace.toEntity(SYNC))
            database.budgetDao().upsert(otherMonth.toEntity(SYNC))
            database.budgetDao().upsert(deleted.toEntity(SYNC.copy(deletedAtEpochMillis = NOW.toEpochMilliseconds())))

            val personalResults = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = null,
                month = "2026-08",
            )
            assertEquals(listOf("b-v1", "b-v2"), personalResults.map { it.id })

            val wsResults = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = "ws-1",
                month = "2026-08",
            )
            assertEquals(listOf("b-ws"), wsResults.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun budgetDao_getForMonth_ordersByCategoryId() = runTest {
        val database = inMemoryDatabase()
        try {
            val catZ = category().copy(id = EntityId("cat-z"), name = "Z Market")
            val catA = category().copy(id = EntityId("cat-a"), name = "A Market")
            val catM = category().copy(id = EntityId("cat-m"), name = "M Market")
            database.categoryDao().upsert(catZ.toEntity(SYNC))
            database.categoryDao().upsert(catA.toEntity(SYNC))
            database.categoryDao().upsert(catM.toEntity(SYNC))

            val bZ = budget("b-z", categoryId = catZ.id)
            val bA = budget("b-a", categoryId = catA.id)
            val bM = budget("b-m", categoryId = catM.id)

            // Insert in non-sorted order
            database.budgetDao().upsert(bZ.toEntity(SYNC))
            database.budgetDao().upsert(bA.toEntity(SYNC))
            database.budgetDao().upsert(bM.toEntity(SYNC))

            val results = database.budgetDao().getForMonth(
                ownerId = USER_ID.value,
                workspaceId = null,
                month = "2026-08",
            )

            assertEquals(listOf("cat-a", "cat-m", "cat-z"), results.map { it.categoryId })
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    private fun persistentDatabase(context: Context, databaseName: String): FeniqoDatabase =
        Room.databaseBuilder<FeniqoDatabase>(
            context = context,
            name = databaseName,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    private fun category() = Category(
        id = CATEGORY_ID,
        ownerId = USER_ID,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#0A7A55"),
        icon = null,
        isDefault = false,
        createdAt = NOW,
    )

    private fun transaction() = Transaction(
        id = TRANSACTION_ID,
        ownerId = USER_ID,
        workspaceId = null,
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = CATEGORY_ID,
        description = "market alışverişi",
        paymentMethod = PaymentMethod.DEBIT_CARD,
        transactionDate = LocalDate(2026, 8, 5),
        receiptPath = null,
        installment = null,
        createdAt = NOW,
    )

    private fun budget(
        id: String = "budget-1",
        ownerId: EntityId = USER_ID,
        workspaceId: EntityId? = null,
        categoryId: EntityId = CATEGORY_ID,
        month: String = "2026-08",
        limitMinor: Long = 100_000L,
    ) = Budget(
        id = EntityId(id),
        ownerId = ownerId,
        workspaceId = workspaceId,
        categoryId = categoryId,
        month = YearMonth(month),
        limit = Money(limitMinor, Currency.TRY),
        createdAt = NOW,
    )

    private fun assetEntity(
        id: String,
        ownerId: String,
        sync: com.feniqo.mobile.data.local.entity.SyncMetadata = SYNC,
    ) = AssetEntity(
        id = id,
        ownerId = ownerId,
        name = "Nakit",
        typeCode = "CASH",
        currentValueMinor = 10_000L,
        currencyCode = Currency.TRY.code,
        quantityUnscaled = null,
        quantityScale = null,
        purchaseUnitPriceMinor = null,
        trackingSymbol = null,
        autoTrack = false,
        createdAtEpochMillis = NOW.toEpochMilliseconds(),
        sync = sync,
    )

    @Test
    fun applyInitialSnapshot_does_not_overwrite_pending_local_mutation_records() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val transactionDao = db.transactionDao()
        val categoryDao = db.categoryDao()

        // 1. Yerel veritabanında bir kategori ve bekleyen bir işlem (PENDING_UPDATE) oluşturuluyor
        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L)))

        val localTxEntity = transaction().toEntity(
            newSyncMetadata(1000L).copy(syncStatus = "PENDING_UPDATE"),
        ).copy(
            description = "Bekleyen Yerel Açıklama",
        )
        transactionDao.upsert(localTxEntity)

        val beforeSnapshot = syncDao.getTransactionRow(TRANSACTION_ID.value)
        assertNotNull(beforeSnapshot)
        assertEquals("PENDING_UPDATE", beforeSnapshot.sync.syncStatus)
        assertEquals("Bekleyen Yerel Açıklama", beforeSnapshot.description)

        // 2. Uzak sunucudan gelen snapshot içinde aynı işlem (eski remote sürüm, SYNCED) ve yeni bir işlem geliyor
        val remoteProfile = com.feniqo.mobile.data.local.entity.UserProfileEntity(
            id = USER_ID.value,
            email = "test@feniqo.com",
            fullName = "Test User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "tr",
            activeWorkspaceId = null,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.SYNCED),
        )

        val conflictingRemoteTx = beforeSnapshot.copy(
            description = "Eski Uzak Başlık",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        val newRemoteTx = beforeSnapshot.copy(
            id = "tx-new-remote",
            description = "Yeni Uzak İşlem",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        // 3. applyInitialSnapshot çağrılıyor
        syncDao.applyInitialSnapshot(
            profile = remoteProfile,
            categories = listOf(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED))),
            transactions = listOf(conflictingRemoteTx, newRemoteTx),
        )

        // 4. Doğrulama:
        // Bekleyen yerel işlem ezilmemeli!
        val preservedLocalTx = syncDao.getTransactionRow(TRANSACTION_ID.value)
        assertNotNull(preservedLocalTx)
        assertEquals("PENDING_UPDATE", preservedLocalTx.sync.syncStatus)
        assertEquals("Bekleyen Yerel Açıklama", preservedLocalTx.description)

        // Yerelde olmayan yeni uzak işlem ise başarıyla eklenmiş olmalı!
        val addedRemoteTx = syncDao.getTransactionRow("tx-new-remote")
        assertNotNull(addedRemoteTx)
        assertEquals("SYNCED", addedRemoteTx.sync.syncStatus)
        assertEquals("Yeni Uzak İşlem", addedRemoteTx.description)

        db.close()
    }

    @Test
    fun applyInitialSnapshot_does_not_overwrite_pending_create_or_pending_delete_records() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val transactionDao = db.transactionDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        // 1. Yerel veritabanında PENDING_CREATE ve PENDING_DELETE kayıtları oluşturuluyor
        val localCreateTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        ).copy(
            id = "tx-local-create",
            description = "Yerel Yeni Oluşturulan İşlem",
        )
        transactionDao.upsert(localCreateTx)

        val localDeleteTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_DELETE).copy(deletedAtEpochMillis = 1500L),
        ).copy(
            id = "tx-local-delete",
            description = "Yerelde Silinmiş İşlem",
        )
        transactionDao.upsert(localDeleteTx)

        // 2. Uzak sunucudan gelen snapshot:
        // - tx-local-create için sunucuda SYNCED sürüm gelirse ezmemeli
        // - tx-local-delete için sunucuda canlı (deleted_at == null, SYNCED) sürüm gelirse canlandırmamalı
        val remoteProfile = com.feniqo.mobile.data.local.entity.UserProfileEntity(
            id = USER_ID.value,
            email = "test@feniqo.com",
            fullName = "Test User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "tr",
            activeWorkspaceId = null,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.SYNCED),
        )

        val conflictingCreateRemoteTx = localCreateTx.copy(
            description = "Uzak Çakışan Açıklama",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        val resurrectingDeleteRemoteTx = localDeleteTx.copy(
            description = "Uzakta Hâlâ Canlı Açıklama",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED).copy(deletedAtEpochMillis = null),
        )

        val newRemoteTx = localCreateTx.copy(
            id = "tx-new-independent",
            description = "Yeni Bağımsız Uzak İşlem",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        val testCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "TRANSACTION",
            updatedAtEpochMillis = 2000L,
            entityId = "tx-new-independent",
        )

        // 3. applyInitialSnapshot çağrılıyor
        syncDao.applyInitialSnapshot(
            profile = remoteProfile,
            categories = listOf(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED))),
            transactions = listOf(conflictingCreateRemoteTx, resurrectingDeleteRemoteTx, newRemoteTx),
            cursors = listOf(testCursor),
        )

        // 4. Doğrulama:
        // PENDING_CREATE korunmalı
        val preservedCreate = syncDao.getTransactionRow("tx-local-create")
        assertNotNull(preservedCreate)
        assertEquals("PENDING_CREATE", preservedCreate.sync.syncStatus)
        assertEquals("Yerel Yeni Oluşturulan İşlem", preservedCreate.description)

        // PENDING_DELETE korunmalı ve deletedAt null yapılmamalı (canlandırılmamalı)
        val preservedDelete = syncDao.getTransactionRow("tx-local-delete")
        assertNotNull(preservedDelete)
        assertEquals("PENDING_DELETE", preservedDelete.sync.syncStatus)
        assertEquals(1500L, preservedDelete.sync.deletedAtEpochMillis)
        assertEquals("Yerelde Silinmiş İşlem", preservedDelete.description)

        // Yeni uzak işlem başarıyla eklenmeli
        val addedRemote = syncDao.getTransactionRow("tx-new-independent")
        assertNotNull(addedRemote)
        assertEquals("SYNCED", addedRemote.sync.syncStatus)
        assertEquals("Yeni Bağımsız Uzak İşlem", addedRemote.description)

        // Cursor Room'a yazılmış olmalı
        val storedCursor = db.syncStateDao().getCursor("TRANSACTION")
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals("tx-new-independent", storedCursor.entityId)

        db.close()
    }

    @Test
    fun applyWorkspaceSnapshot_does_not_overwrite_pending_workspace_or_member_mutations() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val wsDao = db.workspaceDao()

        // 1. Yerel veritabanında bekleyen bir workspace (PENDING_UPDATE) ve üye (PENDING_UPDATE) oluşturuluyor
        val localWs = com.feniqo.mobile.data.local.entity.WorkspaceEntity(
            id = "ws-1",
            name = "Yerel Bekleyen Workspace",
            normalizedName = "yerel bekleyen workspace",
            ownerId = USER_ID.value,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.PENDING_UPDATE),
        )
        wsDao.upsertWorkspace(localWs)

        val localMember = com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity(
            workspaceId = "ws-1",
            userId = USER_ID.value,
            roleCode = "OWNER",
            joinedAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.PENDING_UPDATE),
        )
        wsDao.upsertMember(localMember)

        // 2. Uzak sunucudan gelen workspace snapshot'ı:
        // - ws-1 için eski uzak versiyon (SYNCED, farklı isim)
        // - üye için farklı role sahip eski versiyon (SYNCED)
        // - yeni bir workspace (ws-2) ve üyesi
        val conflictingRemoteWs = localWs.copy(
            name = "Eski Uzak İsim",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )
        val conflictingRemoteMember = localMember.copy(
            roleCode = "MEMBER",
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        val newRemoteWs = com.feniqo.mobile.data.local.entity.WorkspaceEntity(
            id = "ws-2",
            name = "Yeni Uzak Workspace",
            normalizedName = "yeni uzak workspace",
            ownerId = USER_ID.value,
            createdAtEpochMillis = 2000L,
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )
        val newRemoteMember = com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity(
            workspaceId = "ws-2",
            userId = USER_ID.value,
            roleCode = "OWNER",
            joinedAtEpochMillis = 2000L,
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED),
        )

        val wsCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "WORKSPACE",
            updatedAtEpochMillis = 2000L,
            entityId = "ws-2",
        )

        // 3. applyWorkspaceSnapshot çağrılıyor
        syncDao.applyWorkspaceSnapshot(
            workspaces = listOf(conflictingRemoteWs, newRemoteWs),
            members = listOf(conflictingRemoteMember, newRemoteMember),
            cursors = listOf(wsCursor),
        )

        // 4. Doğrulama:
        // Bekleyen yerel workspace ezilmemeli!
        val preservedWs = syncDao.getWorkspaceRow("ws-1")
        assertNotNull(preservedWs)
        assertEquals("PENDING_UPDATE", preservedWs.sync.syncStatus)
        assertEquals("Yerel Bekleyen Workspace", preservedWs.name)

        // Bekleyen yerel üye ezilmemeli!
        val preservedMember = syncDao.getWorkspaceMemberRow("ws-1", USER_ID.value)
        assertNotNull(preservedMember)
        assertEquals("PENDING_UPDATE", preservedMember.sync.syncStatus)
        assertEquals("OWNER", preservedMember.roleCode)

        // Yeni uzak workspace ve üye başarıyla eklenmiş olmalı!
        val addedWs = syncDao.getWorkspaceRow("ws-2")
        assertNotNull(addedWs)
        assertEquals("SYNCED", addedWs.sync.syncStatus)
        assertEquals("Yeni Uzak Workspace", addedWs.name)

        val addedMember = syncDao.getWorkspaceMemberRow("ws-2", USER_ID.value)
        assertNotNull(addedMember)
        assertEquals("SYNCED", addedMember.sync.syncStatus)

        // Cursor kaydedilmiş olmalı
        val storedCursor = db.syncStateDao().getCursor("WORKSPACE")
        assertNotNull(storedCursor)
        assertEquals(2000L, storedCursor.updatedAtEpochMillis)
        assertEquals("ws-2", storedCursor.entityId)

        db.close()
    }

    @Test
    fun cursorAdvanceWithPendingMutation_preservesBothVersionsOnConflict_andDoesNotMissRecords() = runTest {
        val db = inMemoryDatabase()
        val syncDao = db.remoteSyncDao()
        val syncStateDao = db.syncStateDao()
        val transactionDao = db.transactionDao()
        val categoryDao = db.categoryDao()
        val opDao = db.syncOperationDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        // 1. Yerel veritabanında PENDING_UPDATE durumunda bir işlem ve buna ait outbox operasyonu oluşturuluyor
        val localTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_UPDATE).copy(version = 1L, baseVersion = 1L),
        ).copy(
            id = "tx-conflict-1",
            description = "Yerel Bekleyen Açıklama",
            amountMinor = 15_000L,
        )
        transactionDao.upsert(localTx)

        val outboxOp = com.feniqo.mobile.data.local.entity.SyncOperationEntity(
            operationId = "op-tx-1",
            entityTypeCode = "TRANSACTION",
            entityId = "tx-conflict-1",
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = """{"id":"tx-conflict-1","description":"Yerel Bekleyen Açıklama","amount_minor":15000}""",
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        opDao.insert(outboxOp)

        // 2. Uzak sunucudan gelen ilk snapshot:
        // - tx-conflict-1 için sunucuda daha yeni (version 2, amount 25000) bir sürüm var
        // - tx-other-2 için yeni bir bağımsız işlem var (timestamp 2500L)
        // - TRANSACTION cursor'ı snapshot'taki en son kayıt olan 2500L'ye ayarlanıyor
        val remoteProfile = com.feniqo.mobile.data.local.entity.UserProfileEntity(
            id = USER_ID.value,
            email = "test@feniqo.com",
            fullName = "Test User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "tr",
            activeWorkspaceId = null,
            createdAtEpochMillis = 1000L,
            sync = newSyncMetadata(1000L, SyncStatus.SYNCED),
        )

        val conflictingRemoteTx = localTx.copy(
            description = "Sunucudaki Yeni Açıklama",
            amountMinor = 25_000L,
            sync = newSyncMetadata(2000L, SyncStatus.SYNCED).copy(version = 2L),
        )

        val otherRemoteTx = localTx.copy(
            id = "tx-other-2",
            description = "Diğer Uzak İşlem",
            amountMinor = 30_000L,
            sync = newSyncMetadata(2500L, SyncStatus.SYNCED).copy(version = 1L),
        )

        val snapshotCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "TRANSACTION",
            updatedAtEpochMillis = 2500L,
            entityId = "tx-other-2",
        )

        // applyInitialSnapshot çağrılıyor:
        syncDao.applyInitialSnapshot(
            profile = remoteProfile,
            categories = listOf(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED))),
            transactions = listOf(conflictingRemoteTx, otherRemoteTx),
            cursors = listOf(snapshotCursor),
        )

        // Doğrulama 1: İlk snapshot bekleyen yerel kaydı ezmedi!
        val preservedLocal = syncDao.getTransactionRow("tx-conflict-1")
        assertNotNull(preservedLocal)
        assertEquals("PENDING_UPDATE", preservedLocal.sync.syncStatus)
        assertEquals("Yerel Bekleyen Açıklama", preservedLocal.description)
        assertEquals(15_000L, preservedLocal.amountMinor)

        // Diğer uzak işlem başarıyla eklendi ve cursor kaydedildi:
        val insertedOther = syncDao.getTransactionRow("tx-other-2")
        assertNotNull(insertedOther)
        assertEquals("SYNCED", insertedOther.sync.syncStatus)
        assertEquals(2500L, syncStateDao.getCursor("TRANSACTION")?.updatedAtEpochMillis)

        // 3. Outbox Push & Conflict Tespiti:
        // Outbox gönderiminde sunucu versiyon çakışması (baseVersion 1 < serverVersion 2) döner.
        // Sistem iki kopyayı da sync_conflicts tablosuna yazar:
        val conflictEntity = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
            entityTypeCode = "TRANSACTION",
            entityId = "tx-conflict-1",
            operationId = "op-tx-1",
            localVersion = 1L,
            remoteVersion = 2L,
            localPayloadJson = """{"amount_minor":15000,"description":"Yerel Bekleyen Açıklama"}""",
            remotePayloadJson = """{"amount_minor":25000,"description":"Sunucudaki Yeni Açıklama"}""",
            detectedAtEpochMillis = 2600L,
        )
        syncDao.upsertConflictRow(conflictEntity)

        // Doğrulama 2: Çakışma sırasında iki kopya da Room'da korunuyor!
        val storedConflict = syncStateDao.getConflict("TRANSACTION", "tx-conflict-1")
        assertNotNull(storedConflict)
        assertTrue(storedConflict.localPayloadJson.contains("Yerel Bekleyen Açıklama"))
        assertTrue(storedConflict.remotePayloadJson.contains("Sunucudaki Yeni Açıklama"))
        assertEquals(1L, storedConflict.localVersion)
        assertEquals(2L, storedConflict.remoteVersion)

        // 4. Çakışma Çözümü (KEEP_REMOTE):
        // Kullanıcı uzaktaki kopyayı seçtiğinde:
        syncDao.resolveTransactionKeepRemote(conflictingRemoteTx)

        // Doğrulama 3: Çözüm sonrası yerel kayıt uzaktaki versiyonla güncellendi ve conflict silindi:
        val resolvedTx = syncDao.getTransactionRow("tx-conflict-1")
        assertNotNull(resolvedTx)
        assertEquals("SYNCED", resolvedTx.sync.syncStatus)
        assertEquals("Sunucudaki Yeni Açıklama", resolvedTx.description)
        assertEquals(25_000L, resolvedTx.amountMinor)
        assertEquals(2L, resolvedTx.sync.version)
        assertNull(syncStateDao.getConflict("TRANSACTION", "tx-conflict-1"))
        assertNull(opDao.getById("op-tx-1")) // Outbox operasyonu silindi

        // 5. Sonraki Pull Adımı:
        // Cursor 2500L'de kalmıştı. Sunucudan 3000L zaman damgalı yeni bir tx-3 geldiğinde:
        val futureRemoteTx = localTx.copy(
            id = "tx-future-3",
            description = "Gelecekteki Yeni İşlem",
            sync = newSyncMetadata(3000L, SyncStatus.SYNCED).copy(version = 1L),
        )
        val advancedCursor = com.feniqo.mobile.data.local.entity.SyncCursorEntity(
            entityTypeCode = "TRANSACTION",
            updatedAtEpochMillis = 3000L,
            entityId = "tx-future-3",
        )
        syncDao.applyTransactionPull(futureRemoteTx, advancedCursor)

        // Doğrulama 4: Yeni işlem sorunsuz uygulandı ve hiçbir kayıt kaçırılmadı:
        val insertedFuture = syncDao.getTransactionRow("tx-future-3")
        assertNotNull(insertedFuture)
        assertEquals("Gelecekteki Yeni İşlem", insertedFuture.description)
        assertEquals(3000L, syncStateDao.getCursor("TRANSACTION")?.updatedAtEpochMillis)

        db.close()
    }

    @Test
    fun definitive_rejection_create_updated_removes_old_and_inserts_fresh_uuid() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-def-rej-1"
        val oldOpId = "11111111111111111111111111111111"
        val initialTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        ).copy(id = txId)

        // 1. İlk CREATE işlemi DEFINITIVE_REJECTION ile FAILED durumda kaydedilir
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":1000}",
            operationIdFactory = { oldOpId },
            nowEpochMillis = 1000L,
        )
        opDao.markFailed(
            operationId = oldOpId,
            lastError = "sync_operation_failed",
            errorClassification = OutboxErrorClassification.DEFINITIVE_REJECTION.name,
            nextAttemptAtEpochMillis = 2000L,
            nowEpochMillis = 1500L,
        )

        val failedOp = opDao.getById(oldOpId)
        assertNotNull(failedOp)
        assertTrue(failedOp.isDefinitiveRejection())

        // 2. Kullanıcı kesin reddedilmiş işlemi günceller (UPDATE)
        val newOpId = "22222222222222222222222222222222"
        val updatedTx = initialTx.copy(description = "Düzeltilmiş İşlem")
        val result = mutationDao.mutateTransactionV2(
            entity = updatedTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.UPDATE,
            payloadJson = "{\"amount\":1500}",
            operationIdFactory = { newOpId },
            nowEpochMillis = 2500L,
        )

        // Doğrulama: Eski operasyon kaldırıldı, yeni operation_id ile temiz CREATE oluştu
        assertEquals(newOpId, result.operationId)
        assertEquals(V2EnqueueDecision.INSERTED, result.decision)
        assertNull(opDao.getById(oldOpId)) // Eski operasyon silindi

        val freshCreate = opDao.getById(newOpId)
        assertNotNull(freshCreate)
        assertEquals("CREATE", freshCreate.operationTypeCode)
        assertNull(freshCreate.predecessorOperationId)
        assertFalse(freshCreate.isBlocked)
        assertEquals("PENDING", freshCreate.statusCode)
        assertEquals(0, freshCreate.attemptCount)

        db.close()
    }

    @Test
    fun definitive_rejection_create_deleted_performs_safe_hard_delete() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val txDao = db.transactionDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-def-del-1"
        val opId = "33333333333333333333333333333333"
        val initialTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        ).copy(id = txId)

        // 1. CREATE işlemi DEFINITIVE_REJECTION ile FAILED durumda
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":1000}",
            operationIdFactory = { opId },
            nowEpochMillis = 1000L,
        )
        opDao.markFailed(
            operationId = opId,
            lastError = "sync_operation_failed",
            errorClassification = OutboxErrorClassification.DEFINITIVE_REJECTION.name,
            nextAttemptAtEpochMillis = 2000L,
            nowEpochMillis = 1500L,
        )

        // 2. Kullanıcı siler (DELETE)
        val result = mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.DELETE,
            payloadJson = "{}",
            operationIdFactory = { "44444444444444444444444444444444" },
            nowEpochMillis = 2500L,
        )

        // Doğrulama: Güvenli yerel HARD_DELETE gerçekleşti
        assertEquals(opId, result.operationId)
        assertEquals(V2EnqueueDecision.HARD_DELETED, result.decision)
        assertNull(opDao.getById(opId))
        assertNull(db.remoteSyncDao().getTransactionRow(txId))

        db.close()
    }

    @Test
    fun ambiguous_failed_create_updated_does_not_generate_new_operation_id_for_create() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-ambig-upd-1"
        val originalCreateOpId = "55555555555555555555555555555555"
        val initialTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        ).copy(id = txId)

        // 1. CREATE işlemi timeout/stale sonrası AMBIGUOUS_RESULT ile FAILED durumda
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":1000}",
            operationIdFactory = { originalCreateOpId },
            nowEpochMillis = 1000L,
        )
        opDao.markFailed(
            operationId = originalCreateOpId,
            lastError = "sync_operation_failed",
            errorClassification = OutboxErrorClassification.AMBIGUOUS_RESULT.name,
            nextAttemptAtEpochMillis = 2000L,
            nowEpochMillis = 1500L,
        )

        val ambigOp = opDao.getById(originalCreateOpId)
        assertNotNull(ambigOp)
        assertTrue(ambigOp.isAmbiguousResult())

        // 2. Kullanıcı düzenler (UPDATE)
        val successorUpdateOpId = "66666666666666666666666666666666"
        val updatedTx = initialTx.copy(description = "Belirsiz Sonrası Güncelleme")
        val result = mutationDao.mutateTransactionV2(
            entity = updatedTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.UPDATE,
            payloadJson = "{\"amount\":2000}",
            operationIdFactory = { successorUpdateOpId },
            nowEpochMillis = 2500L,
        )

        // Doğrulama:
        // - Yeni bir CREATE üretilmedi!
        // - Eski CREATE silinmedi veya payload'ı değiştirilmedi!
        val preservedCreate = opDao.getById(originalCreateOpId)
        assertNotNull(preservedCreate)
        assertEquals("CREATE", preservedCreate.operationTypeCode)
        assertEquals("{\"amount\":1000}", preservedCreate.payloadJson)

        // - Successor UPDATE olarak zincirlendi ve bloklandı!
        val successor = opDao.getById(successorUpdateOpId)
        assertNotNull(successor)
        assertEquals("UPDATE", successor.operationTypeCode)
        assertEquals(originalCreateOpId, successor.predecessorOperationId)
        assertTrue(successor.isBlocked)

        db.close()
    }

    private class FakeRemoteTransactionRpc {
        data class RemoteTx(
            val id: String,
            val userId: String,
            val categoryId: String,
            val amountMinor: Long,
            val currency: String,
            val type: String,
            val description: String?,
            val paymentMethod: String,
            val transactionDate: String,
            val createdAt: String,
            var version: Long,
            var isDeleted: Boolean = false,
            var deletedAt: String? = null,
        )

        val remoteRecords = mutableMapOf<String, RemoteTx>()
        val remoteReceipts = mutableMapOf<String, String>() // operationId -> entityId
        var simulateAckLossOnReturn = false
        private val json = Json { ignoreUnknownKeys = true }

        fun execute(operation: SyncOperationEntity): OutboxExecutionResult {
            // Idempotency kontrolü: Bu operation_id daha önce uzakta işlendiyse
            if (remoteReceipts.containsKey(operation.operationId)) {
                val existing = remoteRecords[operation.entityId]!!
                if (simulateAckLossOnReturn) {
                    throw kotlinx.serialization.SerializationException("Simulated response decode error / ACK loss on replay")
                }
                return OutboxExecutionResult.TransactionApplied(
                    record = TransactionDto(
                        id = existing.id,
                        userId = existing.userId,
                        categoryId = existing.categoryId,
                        amountMinor = existing.amountMinor,
                        currency = existing.currency,
                        type = existing.type,
                        description = existing.description,
                        paymentMethod = existing.paymentMethod,
                        transactionDate = existing.transactionDate,
                        createdAt = existing.createdAt,
                        version = existing.version,
                        deletedAt = if (existing.isDeleted) existing.deletedAt else null,
                    )
                )
            }

            if (operation.operationTypeCode == "CREATE") {
                val rawPayload = checkNotNull(operation.payloadJson) { "CREATE requires payloadJson" }
                val decodedDto = json.decodeFromString<TransactionDto>(rawPayload)
                val record = RemoteTx(
                    id = decodedDto.id,
                    userId = decodedDto.userId,
                    categoryId = decodedDto.categoryId,
                    amountMinor = decodedDto.amountMinor,
                    currency = decodedDto.currency,
                    type = decodedDto.type,
                    description = decodedDto.description,
                    paymentMethod = decodedDto.paymentMethod,
                    transactionDate = decodedDto.transactionDate,
                    createdAt = decodedDto.createdAt,
                    version = 1L,
                    isDeleted = false,
                )
                remoteRecords[operation.entityId] = record
                remoteReceipts[operation.operationId] = operation.entityId

                if (simulateAckLossOnReturn) {
                    // Sunucu işlemi ve receipt'i kaydetti fakat istemci cevabı decode ederken koptu / ACK kayboldu!
                    throw kotlinx.serialization.SerializationException("Simulated response decode failure / ACK loss")
                }

                return OutboxExecutionResult.TransactionApplied(
                    record = TransactionDto(
                        id = record.id,
                        userId = record.userId,
                        categoryId = record.categoryId,
                        amountMinor = record.amountMinor,
                        currency = record.currency,
                        type = record.type,
                        description = record.description,
                        paymentMethod = record.paymentMethod,
                        transactionDate = record.transactionDate,
                        createdAt = record.createdAt,
                        version = record.version,
                    )
                )
            } else if (operation.operationTypeCode == "DELETE") {
                val existing = remoteRecords[operation.entityId]
                checkNotNull(existing) { "Entity not found on remote: ${operation.entityId}" }
                existing.version += 1
                existing.isDeleted = true
                existing.deletedAt = "2026-09-21T00:00:00Z"
                remoteReceipts[operation.operationId] = operation.entityId

                return OutboxExecutionResult.TransactionApplied(
                    record = TransactionDto(
                        id = existing.id,
                        userId = existing.userId,
                        categoryId = existing.categoryId,
                        amountMinor = existing.amountMinor,
                        currency = existing.currency,
                        type = existing.type,
                        description = existing.description,
                        paymentMethod = existing.paymentMethod,
                        transactionDate = existing.transactionDate,
                        createdAt = existing.createdAt,
                        version = existing.version,
                        deletedAt = existing.deletedAt,
                    )
                )
            }
            error("Desteklenmeyen işlem türü: ${operation.operationTypeCode}")
        }
    }

    @Test
    fun ambiguous_create_replayed_with_same_operation_id_keeps_remote_record_unique() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-ambig-replay-1"
        val opId = "77777777777777777777777777777777"
        val txDomain = transaction().copy(id = EntityId(txId))
        val initialTx = txDomain.toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        )
        val payloadDto = txDomain.toDto()
        val payloadJson = Json.encodeToString(payloadDto)

        val fakeRemote = FakeRemoteTransactionRpc()
        val writeQueue = OfflineWriteQueue(mutationDao, opDao)
        val roomQueue = RoomOutboxQueue(writeQueue)
        val outboxProcessor = OutboxProcessor(roomQueue) { op -> fakeRemote.execute(op) }

        // 1. CREATE kuyruğa yazılır (gerçek immutable DTO payload snapshot ile)
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = payloadJson,
            operationIdFactory = { opId },
            nowEpochMillis = 1000L,
        )

        // 2. İlk gönderimde uzak sunucu kaydı uygular fakat response decode / ACK kaybı simüle edilir:
        fakeRemote.simulateAckLossOnReturn = true
        val run1 = outboxProcessor.processReadyOperations()
        assertEquals(opId, run1.failedOperationId)

        // Yerel işlem FAILED ve AMBIGUOUS_RESULT olur:
        val failedOp = opDao.getById(opId)
        assertNotNull(failedOp)
        assertEquals("FAILED", failedOp.statusCode)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, failedOp.errorClassification)

        // Uzak sunucuda ise kayıt payload verileriyle ve idempotency receipt ZATEN oluşmuştur:
        assertEquals(1, fakeRemote.remoteRecords.size)
        assertEquals(payloadDto.amountMinor, fakeRemote.remoteRecords[txId]?.amountMinor)
        assertEquals(payloadDto.userId, fakeRemote.remoteRecords[txId]?.userId)
        assertEquals(payloadDto.categoryId, fakeRemote.remoteRecords[txId]?.categoryId)
        assertEquals(payloadDto.paymentMethod, fakeRemote.remoteRecords[txId]?.paymentMethod)
        assertEquals(payloadDto.transactionDate, fakeRemote.remoteRecords[txId]?.transactionDate)
        assertEquals(1, fakeRemote.remoteReceipts.size)
        assertTrue(fakeRemote.remoteReceipts.containsKey(opId))

        // 3. Ağ geri gelip operasyon aynı operation_id ile replay edildiğinde:
        opDao.retryAllFailed(1400L)
        fakeRemote.simulateAckLossOnReturn = false
        val run2 = outboxProcessor.processReadyOperations()
        assertEquals(1, run2.succeededCount)
        kotlin.test.assertNull(run2.failedOperationId)

        // 4. Doğrulamalar:
        // - Uzakta ÇİFT KAYIT oluşmadı, tekil kaldı!
        assertEquals(1, fakeRemote.remoteRecords.size)
        assertEquals(1L, fakeRemote.remoteRecords[txId]?.version)
        assertEquals(1, fakeRemote.remoteReceipts.size)

        // - Yerel outbox işlemi ACK sonrası başarıyla tamamlandı (temizlendi):
        val remainingOp = opDao.getById(opId)
        kotlin.test.assertNull(remainingOp)
        assertEquals(0, opDao.observePendingCount().first())

        db.close()
    }

    @Test
    fun ambiguous_failed_create_deleted_enqueues_successor_delete_preventing_remote_zombie() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-zombie-prevent-1"
        val originalCreateOpId = "88888888888888888888888888888888"
        val txDomain = transaction().copy(id = EntityId(txId))
        val initialTx = txDomain.toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        )
        val payloadDto = txDomain.toDto()
        val payloadJson = Json.encodeToString(payloadDto)

        val fakeRemote = FakeRemoteTransactionRpc()
        val writeQueue = OfflineWriteQueue(mutationDao, opDao)
        val roomQueue = RoomOutboxQueue(writeQueue)
        val outboxProcessor = OutboxProcessor(roomQueue) { op -> fakeRemote.execute(op) }

        // 1. CREATE kuyruğa eklenir ve sunucuya gönderilir, ACK kaybı yaşanır:
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = payloadJson,
            operationIdFactory = { originalCreateOpId },
            nowEpochMillis = 1000L,
        )
        fakeRemote.simulateAckLossOnReturn = true
        outboxProcessor.processReadyOperations()

        val failedCreate = opDao.getById(originalCreateOpId)
        assertNotNull(failedCreate)
        assertEquals("FAILED", failedCreate.statusCode)
        assertEquals(OutboxErrorClassification.AMBIGUOUS_RESULT.name, failedCreate.errorClassification)

        // Uzak sunucuda kayıt oluşmuş durumdadır (zombi adayı), immutable payload amount işlenmiştir:
        assertNotNull(fakeRemote.remoteRecords[txId])
        assertEquals(payloadDto.amountMinor, fakeRemote.remoteRecords[txId]!!.amountMinor)
        kotlin.test.assertFalse(fakeRemote.remoteRecords[txId]!!.isDeleted)

        // 2. Kullanıcı bu belirsiz işlemi yerelde siler (DELETE):
        val successorDeleteOpId = "99999999999999999999999999999999"
        val deletedTx = initialTx.copy(
            sync = initialTx.sync.copy(deletedAtEpochMillis = 2500L),
        )
        val deletePayloadJson = Json.encodeToString(
            payloadDto.copy(
                deletedAt = "2026-09-21T00:00:00Z",
                version = 1L,
            )
        )
        mutationDao.mutateTransactionV2(
            entity = deletedTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.DELETE,
            payloadJson = deletePayloadJson,
            operationIdFactory = { successorDeleteOpId },
            nowEpochMillis = 2500L,
        )

        // Yerel HARD_DELETE yapılmadı, CREATE korundu ve ardıl DELETE bloklu eklendi:
        assertNotNull(opDao.getById(originalCreateOpId))
        val successorDelete = opDao.getById(successorDeleteOpId)
        assertNotNull(successorDelete)
        assertEquals("DELETE", successorDelete.operationTypeCode)
        assertEquals(originalCreateOpId, successorDelete.predecessorOperationId)
        assertTrue(successorDelete.isBlocked)

        // 3. Ağ geri gelir: CREATE replay edilir ve ACK alınır:
        fakeRemote.simulateAckLossOnReturn = false
        opDao.retryAllFailed(2600L)
        val runReplay = outboxProcessor.processReadyOperations()
        assertEquals(1, runReplay.succeededCount)

        // CREATE ackV2Execution ile tamamlandı ve ardıl DELETE unblock edildi:
        kotlin.test.assertNull(opDao.getById(originalCreateOpId))
        val unblockedDelete = opDao.getById(successorDeleteOpId)
        assertNotNull(unblockedDelete)
        kotlin.test.assertFalse(unblockedDelete.isBlocked)
        assertEquals(1L, unblockedDelete.baseVersion)

        // 4. Artık bloksuz olan ardıl DELETE gerçek uzak servis üzerinde çalıştırılır:
        val runDelete = outboxProcessor.processReadyOperations()
        assertEquals(1, runDelete.succeededCount)

        // 5. Kesin Doğrulama:
        // - Uzak servis üzerinde DELETE gerçekten çalıştı!
        val remoteRecord = fakeRemote.remoteRecords[txId]
        assertNotNull(remoteRecord)
        assertTrue(remoteRecord.isDeleted, "Uzak kayıt tombstone yapılmış olmalı!")
        assertEquals(2L, remoteRecord.version)

        // - Uzakta zombi kayıt kalmadı!
        // - Yerel outbox tamamen temizlendi!
        kotlin.test.assertNull(opDao.getById(successorDeleteOpId))
        assertEquals(0, opDao.observePendingCount().first())

        db.close()
    }

    @Test
    fun predecessor_successor_chain_preserved_during_recovery() = runTest {
        val db = inMemoryDatabase()
        val mutationDao = db.localMutationDao()
        val opDao = db.syncOperationDao()
        val categoryDao = db.categoryDao()

        val cat = category()
        categoryDao.upsert(cat.toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val txId = "tx-chain-1"
        val op1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val op2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val op3 = "cccccccccccccccccccccccccccccccc"
        val initialTx = transaction().toEntity(
            newSyncMetadata(1000L, SyncStatus.PENDING_CREATE),
        ).copy(id = txId)

        // op1 (CREATE)
        mutationDao.mutateTransactionV2(
            entity = initialTx,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":100}",
            operationIdFactory = { op1 },
            nowEpochMillis = 1000L,
        )
        // op1 ambiguous failure
        opDao.markFailed(op1, "error", OutboxErrorClassification.AMBIGUOUS_RESULT.name, 2000L, 1100L)

        // op2 (UPDATE successor, op1'e bağlı ve bloklu olarak eklenir)
        mutationDao.mutateTransactionV2(
            entity = initialTx.copy(description = "Güncelleme 1"),
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.UPDATE,
            payloadJson = "{\"amount\":200}",
            operationIdFactory = { op2 },
            nowEpochMillis = 1200L,
        )

        val entityOp1 = opDao.getById(op1)
        val entityOp2Blocked = opDao.getById(op2)
        assertNotNull(entityOp1)
        assertNotNull(entityOp2Blocked)
        assertEquals(op1, entityOp2Blocked.predecessorOperationId)
        assertTrue(entityOp2Blocked.isBlocked)

        // op1 uzak sunucuda uzlaştırılıp/replay edilip tamamlandığında ardılı op2 unblock edilir:
        val unblockCount = opDao.unblockSuccessor(op2, op1, appliedVersion = 1L, nowEpochMillis = 2000L)
        assertEquals(1, unblockCount)
        val entityOp2Unblocked = opDao.getById(op2)
        assertNotNull(entityOp2Unblocked)
        assertFalse(entityOp2Unblocked.isBlocked)
        assertEquals(1L, entityOp2Unblocked.baseVersion)

        // op2 işleme alınır (IN_FLIGHT)
        val claimed = opDao.claimOperation(op2, 2100L)
        assertEquals(1, claimed)

        // op3 (UPDATE successor of op2, işlenmekte olan op2'ye zincirlenir)
        mutationDao.mutateTransactionV2(
            entity = initialTx.copy(description = "Güncelleme 2"),
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.UPDATE,
            payloadJson = "{\"amount\":300}",
            operationIdFactory = { op3 },
            nowEpochMillis = 2200L,
        )

        val entityOp3 = opDao.getById(op3)
        assertNotNull(entityOp3)
        assertEquals(op2, entityOp3.predecessorOperationId)
        assertTrue(entityOp3.isBlocked)

        db.close()
    }

    @Test
    fun app_restart_preserves_error_classification_and_recovery_decisions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test-error-class-restart.db"
        context.deleteDatabase(dbName)

        val opId1 = "dddddddddddddddddddddddddddddddd"
        val opId2 = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        // 1. İlk oturumda iki operasyon yazılır
        val db1 = persistentDatabase(context, dbName)
        val opDao1 = db1.syncOperationDao()
        val mutationDao1 = db1.localMutationDao()
        db1.categoryDao().upsert(category().toEntity(newSyncMetadata(1000L, SyncStatus.SYNCED)))

        val tx1 = transaction().toEntity(newSyncMetadata(1000L, SyncStatus.PENDING_CREATE)).copy(id = "tx-restart-1")
        mutationDao1.mutateTransactionV2(
            entity = tx1,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":100}",
            operationIdFactory = { opId1 },
            nowEpochMillis = 1000L,
        )
        opDao1.markFailed(opId1, "err", OutboxErrorClassification.DEFINITIVE_REJECTION.name, 2000L, 1100L)

        val tx2 = transaction().toEntity(newSyncMetadata(1000L, SyncStatus.PENDING_CREATE)).copy(id = "tx-restart-2")
        mutationDao1.mutateTransactionV2(
            entity = tx2,
            tags = emptyList(),
            tagLinks = emptyList(),
            type = OutboxOperationType.CREATE,
            payloadJson = "{\"amount\":200}",
            operationIdFactory = { opId2 },
            nowEpochMillis = 1200L,
        )
        opDao1.markFailed(opId2, "err", OutboxErrorClassification.AMBIGUOUS_RESULT.name, 2000L, 1300L)

        db1.close()

        // 2. Uygulama yeniden açıldığında (db2)
        val db2 = persistentDatabase(context, dbName)
        val opDao2 = db2.syncOperationDao()

        val reloadedOp1 = opDao2.getById(opId1)
        val reloadedOp2 = opDao2.getById(opId2)

        assertNotNull(reloadedOp1)
        assertNotNull(reloadedOp2)

        assertEquals("DEFINITIVE_REJECTION", reloadedOp1.errorClassification)
        assertTrue(reloadedOp1.isDefinitiveRejection())
        assertFalse(reloadedOp1.isAmbiguousResult())

        assertEquals("AMBIGUOUS_RESULT", reloadedOp2.errorClassification)
        assertTrue(reloadedOp2.isAmbiguousResult())
        assertFalse(reloadedOp2.isDefinitiveRejection())

        db2.close()
        context.deleteDatabase(dbName)
    }

    private companion object {
        val USER_ID = EntityId("user-1")
        val CATEGORY_ID = EntityId("category-1")
        val TRANSACTION_ID = EntityId("transaction-1")
        val TAG_ID = EntityId("tag-1")
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
        val SYNC = newSyncMetadata(NOW.toEpochMilliseconds())
    }
}
