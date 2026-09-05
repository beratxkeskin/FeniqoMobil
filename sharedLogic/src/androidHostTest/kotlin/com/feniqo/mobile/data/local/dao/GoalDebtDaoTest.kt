package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.data.local.database.ANDROID_MIGRATION_8_9
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.domain.model.SyncStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class GoalDebtDaoTest {

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

    private fun testWorkspace(id: String = "ws-1", ownerId: String = "user-1"): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            name = "İşyeri",
            normalizedName = "isyeri",
            ownerId = ownerId,
            createdAtEpochMillis = 1000L,
            sync = testSync,
        )
    }

    private fun testGoal(
        id: String,
        ownerId: String = "user-1",
        workspaceId: String? = null,
        name: String = "Hedef $id",
        targetAmountMinor: Long = 100_000L,
        currentAmountMinor: Long = 0L,
        targetDate: String = "2027-06-01",
        deletedAtEpochMillis: Long? = null,
    ): GoalEntity {
        return GoalEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = workspaceId,
            name = name,
            targetAmountMinor = targetAmountMinor,
            currentAmountMinor = currentAmountMinor,
            currencyCode = "TRY",
            targetDate = targetDate,
            colorHex = "#0A7A55",
            iconKey = "car",
            createdAtEpochMillis = 1000L,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    private fun testGoalContribution(
        id: String,
        goalId: String,
        amountMinor: Long = 10_000L,
        directionCode: String = "ADD",
        occurredOn: String = "2026-09-01",
        note: String? = "Birikim",
        createdAtEpochMillis: Long = 1000L,
        deletedAtEpochMillis: Long? = null,
    ): GoalContributionEntity {
        return GoalContributionEntity(
            id = id,
            goalId = goalId,
            amountMinor = amountMinor,
            currencyCode = "TRY",
            directionCode = directionCode,
            occurredOn = occurredOn,
            note = note,
            createdAtEpochMillis = createdAtEpochMillis,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    private fun testDebt(
        id: String,
        ownerId: String = "user-1",
        workspaceId: String? = null,
        title: String = "Borç $id",
        amountMinor: Long = 50_000L,
        typeCode: String = "DEBT",
        dueDate: String = "2026-12-31",
        statusCode: String = "OPEN",
        deletedAtEpochMillis: Long? = null,
    ): DebtEntity {
        return DebtEntity(
            id = id,
            ownerId = ownerId,
            workspaceId = workspaceId,
            title = title,
            amountMinor = amountMinor,
            currencyCode = "TRY",
            typeCode = typeCode,
            dueDate = dueDate,
            statusCode = statusCode,
            description = "Açıklama $id",
            createdAtEpochMillis = 1000L,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    private fun testDebtPayment(
        id: String,
        debtId: String,
        amountMinor: Long = 15_000L,
        paidOn: String = "2026-09-10",
        createdAtEpochMillis: Long = 1000L,
        deletedAtEpochMillis: Long? = null,
    ): DebtPaymentEntity {
        return DebtPaymentEntity(
            id = id,
            debtId = debtId,
            amountMinor = amountMinor,
            currencyCode = "TRY",
            paidOn = paidOn,
            createdAtEpochMillis = createdAtEpochMillis,
            sync = testSync.copy(deletedAtEpochMillis = deletedAtEpochMillis),
        )
    }

    @Test
    fun goalDao_observeAll_filtersPersonalAndWorkspaceScopeAndExcludesOtherOwners() = runTest {
        val db = inMemoryDatabase()
        try {
            db.workspaceDao().upsertWorkspace(testWorkspace("ws-1", "user-1"))

            val goalPersonalUser1 = testGoal("goal-1", ownerId = "user-1", workspaceId = null)
            val goalWorkspaceUser1 = testGoal("goal-2", ownerId = "user-1", workspaceId = "ws-1")
            val goalPersonalUser2 = testGoal("goal-3", ownerId = "user-2", workspaceId = null)

            db.goalDao().upsertAll(listOf(goalPersonalUser1, goalWorkspaceUser1, goalPersonalUser2))

            val personalResult = db.goalDao().observeAll("user-1", null).first()
            assertEquals(listOf(goalPersonalUser1), personalResult)

            val workspaceResult = db.goalDao().observeAll("user-1", "ws-1").first()
            assertEquals(listOf(goalWorkspaceUser1), workspaceResult)
        } finally {
            db.close()
        }
    }

    @Test
    fun goalDao_observeAll_and_getById_excludeTombstones_while_getAnyById_includesTombstones() = runTest {
        val db = inMemoryDatabase()
        try {
            val activeGoal = testGoal("goal-active", ownerId = "user-1")
            val deletedGoal = testGoal("goal-deleted", ownerId = "user-1", deletedAtEpochMillis = 2000L)

            db.goalDao().upsertAll(listOf(activeGoal, deletedGoal))

            val allList = db.goalDao().observeAll("user-1", null).first()
            assertEquals(listOf(activeGoal), allList)

            assertEquals(activeGoal, db.goalDao().getById("goal-active"))
            assertNull(db.goalDao().getById("goal-deleted"))
            assertEquals(activeGoal, db.goalDao().observeById("goal-active").first())
            assertNull(db.goalDao().observeById("goal-deleted").first())

            val anyActive = db.goalDao().getAnyById("goal-active")
            val anyDeleted = db.goalDao().getAnyById("goal-deleted")
            assertNotNull(anyActive)
            assertNotNull(anyDeleted)
            assertEquals("goal-deleted", anyDeleted.id)
            assertEquals(2000L, anyDeleted.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun goalDao_contributions_areOrderedDeterministically_andDoNotLeakAcrossGoals() = runTest {
        val db = inMemoryDatabase()
        try {
            val goal1 = testGoal("goal-1")
            val goal2 = testGoal("goal-2")
            db.goalDao().upsertAll(listOf(goal1, goal2))

            val c1 = testGoalContribution("c-1", goalId = "goal-1", occurredOn = "2026-09-05", createdAtEpochMillis = 2000L)
            val c2 = testGoalContribution("c-2", goalId = "goal-1", occurredOn = "2026-09-01", createdAtEpochMillis = 1000L)
            val c3 = testGoalContribution("c-3", goalId = "goal-1", occurredOn = "2026-09-05", createdAtEpochMillis = 3000L)
            val cDeleted = testGoalContribution("c-del", goalId = "goal-1", occurredOn = "2026-09-02", deletedAtEpochMillis = 4000L)
            val cOtherGoal = testGoalContribution("c-other", goalId = "goal-2", occurredOn = "2026-09-01")

            db.goalDao().upsertAllContributions(listOf(c1, c2, c3, cDeleted, cOtherGoal))

            val listGoal1 = db.goalDao().observeContributionsByGoalId("goal-1").first()
            assertEquals(listOf("c-2", "c-1", "c-3"), listGoal1.map { it.id })

            val listGoal2 = db.goalDao().getContributionsByGoalId("goal-2")
            assertEquals(listOf("c-other"), listGoal2.map { it.id })

            assertNull(db.goalDao().getContributionById("c-del"))
            val anyDeletedContrib = db.goalDao().getAnyContributionById("c-del")
            assertNotNull(anyDeletedContrib)
            assertEquals(4000L, anyDeletedContrib.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun debtDao_observeAll_and_observeAllByType_filterCorrectly() = runTest {
        val db = inMemoryDatabase()
        try {
            db.workspaceDao().upsertWorkspace(testWorkspace("ws-1", "user-1"))

            val debtPersonal = testDebt("debt-1", ownerId = "user-1", workspaceId = null, typeCode = "DEBT")
            val receivablePersonal = testDebt("rec-1", ownerId = "user-1", workspaceId = null, typeCode = "RECEIVABLE")
            val debtWorkspace = testDebt("debt-ws", ownerId = "user-1", workspaceId = "ws-1", typeCode = "DEBT")

            db.debtDao().upsertAll(listOf(debtPersonal, receivablePersonal, debtWorkspace))

            val allPersonal = db.debtDao().observeAll("user-1", null).first()
            assertEquals(listOf(debtPersonal, receivablePersonal), allPersonal)

            val debtsOnly = db.debtDao().observeAllByType("user-1", null, "DEBT").first()
            assertEquals(listOf(debtPersonal), debtsOnly)

            val receivablesOnly = db.debtDao().observeAllByType("user-1", null, "RECEIVABLE").first()
            assertEquals(listOf(receivablePersonal), receivablesOnly)
        } finally {
            db.close()
        }
    }

    @Test
    fun debtDao_observeAll_and_getById_excludeTombstones_while_getAnyById_includesTombstones() = runTest {
        val db = inMemoryDatabase()
        try {
            val activeDebt = testDebt("debt-active", ownerId = "user-1")
            val deletedDebt = testDebt("debt-del", ownerId = "user-1", deletedAtEpochMillis = 3000L)

            db.debtDao().upsertAll(listOf(activeDebt, deletedDebt))

            val list = db.debtDao().observeAll("user-1", null).first()
            assertEquals(listOf(activeDebt), list)

            assertEquals(activeDebt, db.debtDao().getById("debt-active"))
            assertNull(db.debtDao().getById("debt-del"))
            assertEquals(activeDebt, db.debtDao().observeById("debt-active").first())
            assertNull(db.debtDao().observeById("debt-del").first())

            val anyDeleted = db.debtDao().getAnyById("debt-del")
            assertNotNull(anyDeleted)
            assertEquals("debt-del", anyDeleted.id)
            assertEquals(3000L, anyDeleted.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun debtDao_payments_areOrderedDeterministically_andDoNotLeakAcrossDebts() = runTest {
        val db = inMemoryDatabase()
        try {
            val debt1 = testDebt("debt-1")
            val debt2 = testDebt("debt-2")
            db.debtDao().upsertAll(listOf(debt1, debt2))

            val p1 = testDebtPayment("p-1", debtId = "debt-1", paidOn = "2026-09-15", createdAtEpochMillis = 2000L)
            val p2 = testDebtPayment("p-2", debtId = "debt-1", paidOn = "2026-09-10", createdAtEpochMillis = 1000L)
            val p3 = testDebtPayment("p-3", debtId = "debt-1", paidOn = "2026-09-15", createdAtEpochMillis = 3000L)
            val pDeleted = testDebtPayment("p-del", debtId = "debt-1", paidOn = "2026-09-12", deletedAtEpochMillis = 5000L)
            val pOther = testDebtPayment("p-other", debtId = "debt-2", paidOn = "2026-09-10")

            db.debtDao().upsertAllPayments(listOf(p1, p2, p3, pDeleted, pOther))

            val listDebt1 = db.debtDao().observePaymentsByDebtId("debt-1").first()
            assertEquals(listOf("p-2", "p-1", "p-3"), listDebt1.map { it.id })

            val listDebt2 = db.debtDao().getPaymentsByDebtId("debt-2")
            assertEquals(listOf("p-other"), listDebt2.map { it.id })

            assertNull(db.debtDao().getPaymentById("p-del"))
            val anyDeletedPayment = db.debtDao().getAnyPaymentById("p-del")
            assertNotNull(anyDeletedPayment)
            assertEquals(5000L, anyDeletedPayment.sync.deletedAtEpochMillis)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration_8_to_9_createsGoalsAndDebtsTables_andPreservesExistingV8Data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbName = "migration-test-8-9.db"
        val testDbFile = File(context.filesDir, testDbName)
        val testDbPath = testDbFile.absolutePath
        context.deleteDatabase(testDbName)

        val v8Db = migrationHelper.createDatabase(testDbPath, 8)
        try {
            v8Db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, owner_id, workspace_id, name, amount_minor, currency_code,
                    category_id, frequency_code, `interval`, start_date, end_date,
                    next_renewal_date, is_active, created_at_epoch_ms, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'sub-v8', 'user-1', NULL, 'Netflix', 14999, 'TRY',
                    NULL, 'MONTHLY', 1, '2026-08-01', NULL,
                    '2026-09-01', 1, 1000, 'SYNCED',
                    1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v8Db.execSQL(
                """
                INSERT INTO subscription_payment_reminder_receipts (
                    stable_key, subscription_id, next_renewal_date, reminder_kind, claimed_at_epoch_millis
                ) VALUES (
                    'sub-v8:2026-09-01:DUE_TODAY', 'sub-v8', '2026-09-01', 'DUE_TODAY', 1000
                )
                """.trimIndent(),
            )
        } finally {
            v8Db.close()
        }

        val v9Db = migrationHelper.runMigrationsAndValidate(
            testDbPath,
            9,
            true,
            ANDROID_MIGRATION_8_9,
        )
        try {
            // 1. Mevcut v8 verilerinin korunduğunu doğrula
            val subCursor = v9Db.query("SELECT id, name, amount_minor FROM subscriptions WHERE id = 'sub-v8'")
            assertTrue(subCursor.moveToFirst())
            assertEquals("sub-v8", subCursor.getString(0))
            assertEquals("Netflix", subCursor.getString(1))
            assertEquals(14999L, subCursor.getLong(2))
            subCursor.close()

            val receiptCursor = v9Db.query("SELECT stable_key, subscription_id FROM subscription_payment_reminder_receipts WHERE stable_key = 'sub-v8:2026-09-01:DUE_TODAY'")
            assertTrue(receiptCursor.moveToFirst())
            assertEquals("sub-v8:2026-09-01:DUE_TODAY", receiptCursor.getString(0))
            assertEquals("sub-v8", receiptCursor.getString(1))
            receiptCursor.close()

            // 2. Yeni goals ve goal_contributions tablolarına veri yaz ve oku
            v9Db.execSQL(
                """
                INSERT INTO goals (
                    id, owner_id, workspace_id, name, target_amount_minor, current_amount_minor,
                    currency_code, target_date, color_hex, icon_key, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'goal-v9', 'user-1', NULL, 'Yeni Bilgisayar', 6000000, 1500000,
                    'TRY', '2027-01-01', '#0A7A55', 'laptop', 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v9Db.execSQL(
                """
                INSERT INTO goal_contributions (
                    id, goal_id, amount_minor, currency_code, direction_code,
                    occurred_on, note, created_at_epoch_ms, sync_status,
                    updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'contrib-v9', 'goal-v9', 1500000, 'TRY', 'ADD',
                    '2026-09-01', 'İlk peşinat', 1000, 'SYNCED',
                    1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val goalCursor = v9Db.query("SELECT id, name, target_amount_minor, current_amount_minor FROM goals WHERE id = 'goal-v9'")
            assertTrue(goalCursor.moveToFirst())
            assertEquals("goal-v9", goalCursor.getString(0))
            assertEquals("Yeni Bilgisayar", goalCursor.getString(1))
            assertEquals(6000000L, goalCursor.getLong(2))
            assertEquals(1500000L, goalCursor.getLong(3))
            goalCursor.close()

            val contribCursor = v9Db.query("SELECT id, goal_id, amount_minor, direction_code FROM goal_contributions WHERE id = 'contrib-v9'")
            assertTrue(contribCursor.moveToFirst())
            assertEquals("contrib-v9", contribCursor.getString(0))
            assertEquals("goal-v9", contribCursor.getString(1))
            assertEquals(1500000L, contribCursor.getLong(2))
            assertEquals("ADD", contribCursor.getString(3))
            contribCursor.close()

            // 3. Yeni debts ve debt_payments tablolarına veri yaz ve oku
            v9Db.execSQL(
                """
                INSERT INTO debts (
                    id, owner_id, workspace_id, title, amount_minor, currency_code,
                    type_code, due_date, status_code, description, created_at_epoch_ms,
                    sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms, deleted_at_epoch_ms,
                    version, base_version, last_sync_error
                ) VALUES (
                    'debt-v9', 'user-1', NULL, 'Elden Borç', 3000000, 'TRY',
                    'DEBT', '2026-12-31', 'OPEN', 'Kuzen borcu', 1000,
                    'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            v9Db.execSQL(
                """
                INSERT INTO debt_payments (
                    id, debt_id, amount_minor, currency_code, paid_on,
                    created_at_epoch_ms, sync_status, updated_at_epoch_ms,
                    local_updated_at_epoch_ms, deleted_at_epoch_ms, version,
                    base_version, last_sync_error
                ) VALUES (
                    'pay-v9', 'debt-v9', 1000000, 'TRY', '2026-09-01',
                    1000, 'SYNCED', 1000, 1000, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )

            val debtCursor = v9Db.query("SELECT id, title, amount_minor, status_code FROM debts WHERE id = 'debt-v9'")
            assertTrue(debtCursor.moveToFirst())
            assertEquals("debt-v9", debtCursor.getString(0))
            assertEquals("Elden Borç", debtCursor.getString(1))
            assertEquals(3000000L, debtCursor.getLong(2))
            assertEquals("OPEN", debtCursor.getString(3))
            debtCursor.close()

            val payCursor = v9Db.query("SELECT id, debt_id, amount_minor, paid_on FROM debt_payments WHERE id = 'pay-v9'")
            assertTrue(payCursor.moveToFirst())
            assertEquals("pay-v9", payCursor.getString(0))
            assertEquals("debt-v9", payCursor.getString(1))
            assertEquals(1000000L, payCursor.getLong(2))
            assertEquals("2026-09-01", payCursor.getString(3))
            payCursor.close()
        } finally {
            v9Db.close()
            context.deleteDatabase(testDbName)
        }
    }
}
