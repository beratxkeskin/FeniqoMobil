package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_7_8
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentReminderReceiptEntity
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderKey
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import java.io.File
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SubscriptionPaymentReminderReceiptDaoTest {

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

    private fun createReceipt(
        subscriptionId: String,
        nextRenewalDate: String,
        reminderKind: SubscriptionReminderKind,
        claimedAtEpochMillis: Long = 1725148800000L,
    ): SubscriptionPaymentReminderReceiptEntity {
        val key = SubscriptionPaymentReminderKey(
            subscriptionId = EntityId(subscriptionId),
            nextRenewalDate = LocalDate.parse(nextRenewalDate),
            reminderKind = reminderKind,
        )
        return SubscriptionPaymentReminderReceiptEntity(
            stableKey = key.stableKey,
            subscriptionId = key.subscriptionId.value,
            nextRenewalDate = key.nextRenewalDate.toString(),
            reminderKind = key.reminderKind.name,
            claimedAtEpochMillis = claimedAtEpochMillis,
        )
    }

    @Test
    fun claim_firstCallSucceeds_secondCallWithSameStableKeyIsRejected() = runTest {
        val db = inMemoryDatabase()
        try {
            val dao = db.subscriptionPaymentReminderReceiptDao()
            val receipt = createReceipt(
                subscriptionId = "sub-123",
                nextRenewalDate = "2026-09-01",
                reminderKind = SubscriptionReminderKind.DUE_TODAY,
                claimedAtEpochMillis = 1000L,
            )

            assertFalse(dao.isClaimed(receipt.stableKey))
            assertNull(dao.getByStableKey(receipt.stableKey))

            // İlk claim atomik olarak kabul edilmeli (true)
            val firstClaimResult = dao.claim(receipt)
            assertTrue(firstClaimResult)

            assertTrue(dao.isClaimed(receipt.stableKey))
            val stored = dao.getByStableKey(receipt.stableKey)
            assertNotNull(stored)
            assertEquals(receipt.stableKey, stored.stableKey)
            assertEquals("sub-123", stored.subscriptionId)
            assertEquals("2026-09-01", stored.nextRenewalDate)
            assertEquals(SubscriptionReminderKind.DUE_TODAY.name, stored.reminderKind)
            assertEquals(1000L, stored.claimedAtEpochMillis)

            // Aynı stable_key ile ikinci claim atomik olarak reddedilmeli (false)
            val secondReceiptWithDifferentTime = receipt.copy(claimedAtEpochMillis = 2000L)
            val secondClaimResult = dao.claim(secondReceiptWithDifferentTime)
            assertFalse(secondClaimResult)

            // Mevcut kayıt üzerine yazılmamalı, ilk claimed_at korunmalı
            val currentStored = dao.getByStableKey(receipt.stableKey)
            assertNotNull(currentStored)
            assertEquals(1000L, currentStored.claimedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun claim_differentRenewalDatesOrKinds_areClaimedIndependently() = runTest {
        val db = inMemoryDatabase()
        try {
            val dao = db.subscriptionPaymentReminderReceiptDao()

            val receiptDueToday = createReceipt("sub-1", "2026-09-01", SubscriptionReminderKind.DUE_TODAY)
            val receiptUpcoming = createReceipt("sub-1", "2026-09-01", SubscriptionReminderKind.UPCOMING)
            val receiptNextCycle = createReceipt("sub-1", "2026-10-01", SubscriptionReminderKind.DUE_TODAY)
            val receiptOtherSub = createReceipt("sub-2", "2026-09-01", SubscriptionReminderKind.DUE_TODAY)

            assertTrue(dao.claim(receiptDueToday))
            assertTrue(dao.claim(receiptUpcoming))
            assertTrue(dao.claim(receiptNextCycle))
            assertTrue(dao.claim(receiptOtherSub))

            // Tekrarlar reddedilir
            assertFalse(dao.claim(receiptDueToday))
            assertFalse(dao.claim(receiptUpcoming))
            assertFalse(dao.claim(receiptNextCycle))
            assertFalse(dao.claim(receiptOtherSub))
        } finally {
            db.close()
        }
    }

    @Test
    fun migration_7_to_8_createsReceiptsTable_andPreservesExistingSubscriptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration-test-7-8.db"
        val testDbFile = File(context.filesDir, testDbName)
        val testDbPath = testDbFile.absolutePath
        context.deleteDatabase(testDbName)

        val v7Db = migrationHelper.createDatabase(testDbPath, 7)
        try {
            v7Db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, owner_id, workspace_id, name, amount_minor, currency_code,
                    category_id, frequency_code, `interval`, start_date, end_date,
                    next_renewal_date, is_active, created_at_epoch_ms, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'sub-v7', 'user-1', NULL, 'Netflix', 19999, 'TRY',
                    NULL, 'MONTHLY', 1, '2026-08-01', NULL,
                    '2026-09-01', 1, 1000, 'SYNCED',
                    1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        } finally {
            v7Db.close()
        }

        val v8Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            8,
            true,
            ANDROID_MIGRATION_7_8,
        )
        try {
            // Verify existing v7 subscription data preserved
            val subCursor = v8Db.query("SELECT id, name, amount_minor, next_renewal_date FROM subscriptions WHERE id = 'sub-v7'")
            assertTrue(subCursor.moveToFirst())
            assertEquals("sub-v7", subCursor.getString(0))
            assertEquals("Netflix", subCursor.getString(1))
            assertEquals(19999L, subCursor.getLong(2))
            assertEquals("2026-09-01", subCursor.getString(3))
            subCursor.close()

            // Insert into new v8 subscription_payment_reminder_receipts table
            v8Db.execSQL(
                """
                INSERT INTO subscription_payment_reminder_receipts (
                    stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis
                ) VALUES (
                    'sub-v7#2026-09-01#DUE_TODAY', 'sub-v7', '2026-09-01', 'DUE_TODAY', 1725148800000
                )
                """.trimIndent(),
            )

            val receiptCursor = v8Db.query(
                "SELECT stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis " +
                    "FROM subscription_payment_reminder_receipts WHERE stable_key = 'sub-v7#2026-09-01#DUE_TODAY'",
            )
            assertTrue(receiptCursor.moveToFirst())
            assertEquals("sub-v7#2026-09-01#DUE_TODAY", receiptCursor.getString(0))
            assertEquals("sub-v7", receiptCursor.getString(1))
            assertEquals("2026-09-01", receiptCursor.getString(2))
            assertEquals("DUE_TODAY", receiptCursor.getString(3))
            assertEquals(1725148800000L, receiptCursor.getLong(4))
            receiptCursor.close()
        } finally {
            v8Db.close()
            context.deleteDatabase(testDbName)
        }
    }
}
