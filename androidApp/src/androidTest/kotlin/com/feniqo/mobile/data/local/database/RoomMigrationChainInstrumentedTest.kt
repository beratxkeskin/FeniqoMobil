package com.feniqo.mobile.data.local.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gerçek Android SQLite üzerinde boş v1 şemasının kesintisiz biçimde güncel şemaya taşındığını doğrular. */
@RunWith(AndroidJUnit4::class)
class RoomMigrationChainInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "feniqo-full-migration-${System.nanoTime()}.db"

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeniqoDatabase::class.java,
    )

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun empty_v1_database_migrates_through_every_step_to_v22() {
        migrationHelper.createDatabase(databaseName, 1).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            22,
            true,
            ANDROID_MIGRATION_1_2,
            ANDROID_MIGRATION_2_3,
            ANDROID_MIGRATION_3_4,
            ANDROID_MIGRATION_4_5,
            ANDROID_MIGRATION_5_6,
            ANDROID_MIGRATION_6_7,
            ANDROID_MIGRATION_7_8,
            ANDROID_MIGRATION_8_9,
            ANDROID_MIGRATION_9_10,
            ANDROID_MIGRATION_10_11,
            ANDROID_MIGRATION_11_12,
            ANDROID_MIGRATION_12_13,
            ANDROID_MIGRATION_13_14,
            ANDROID_MIGRATION_14_15,
            ANDROID_MIGRATION_15_16,
            ANDROID_MIGRATION_16_17,
            ANDROID_MIGRATION_17_18,
            ANDROID_MIGRATION_18_19,
            ANDROID_MIGRATION_19_20,
            ANDROID_MIGRATION_20_21,
            ANDROID_MIGRATION_21_22,
        )
        migrated.use { database ->
            database.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(22, cursor.getInt(0))
            }
        }
    }
}
