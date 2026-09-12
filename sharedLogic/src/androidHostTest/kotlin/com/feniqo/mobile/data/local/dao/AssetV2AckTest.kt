package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.dto.AssetDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AssetV2AckTest {
    @Test
    fun applied_ack_writes_remote_record_and_cleans_outbox() = runTest {
        val db = database()
        try {
            val queue = queue(db)
            val op = queue.enqueueAssetV2(entity("PENDING_CREATE", 0, null), OutboxOperationType.CREATE, payload())
            assertNotNull(queue.claimOperation(op))

            assertTrue(db.localMutationDao().ackV2Execution(op, OutboxExecutionResult.AssetApplied(dto("Uzak Altın", 1)), NOW))
            assertNull(db.syncOperationDao().getById(op))
            val stored = assertNotNull(db.assetDao().getAnyById(ASSET_ID))
            assertEquals("Uzak Altın", stored.name)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1, stored.sync.version)
        } finally { db.close() }
    }

    @Test
    fun applied_ack_rebases_and_unblocks_successor_without_overwriting_local_fields() = runTest {
        val db = database()
        try {
            val queue = queue(db)
            val first = queue.enqueueAssetV2(entity("PENDING_CREATE", 0, null), OutboxOperationType.CREATE, payload())
            queue.claimOperation(first)
            val successor = queue.enqueueAssetV2(entity("PENDING_UPDATE", 0, null).copy(name = "Yerel yeni ad"), OutboxOperationType.UPDATE, payload("Yerel yeni ad"))
            assertTrue(db.syncOperationDao().getById(successor)!!.isBlocked)

            db.localMutationDao().ackV2Execution(first, OutboxExecutionResult.AssetApplied(dto("Sunucu eski ad", 1)), NOW)

            val next = assertNotNull(db.syncOperationDao().getById(successor))
            assertFalse(next.isBlocked)
            assertEquals(1, next.baseVersion)
            val stored = assertNotNull(db.assetDao().getAnyById(ASSET_ID))
            assertEquals("Yerel yeni ad", stored.name)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
            assertEquals(1, stored.sync.version)
        } finally { db.close() }
    }

    @Test
    fun missing_delete_ack_preserves_tombstone_marks_synced_and_cleans_outbox() = runTest {
        val db = database()
        try {
            val queue = queue(db)
            val deleted = entity("PENDING_DELETE", 2, 2).copy(sync = entity("PENDING_DELETE", 2, 2).sync.copy(deletedAtEpochMillis = NOW))
            val op = queue.enqueueAssetV2(deleted, OutboxOperationType.DELETE, payload())
            queue.claimOperation(op)

            assertTrue(db.localMutationDao().ackV2Execution(op, OutboxExecutionResult.MissingDeleteAcknowledged, NOW + 1))
            assertNull(db.syncOperationDao().getById(op))
            val stored = assertNotNull(db.assetDao().getAnyById(ASSET_ID))
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(NOW, stored.sync.deletedAtEpochMillis)
        } finally { db.close() }
    }

    private fun entity(status: String, version: Long, baseVersion: Long?) = AssetEntity(
        ASSET_ID, USER_ID, "Altın", "PRECIOUS_METALS", 100_000, "TRY", null, null, null, "XAU", true, NOW,
        SyncMetadata(status, NOW, NOW, null, version, baseVersion, null),
    )

    private fun dto(name: String, version: Long) = AssetDto(
        ASSET_ID, USER_ID, name, "PRECIOUS_METALS", 100_000, "TRY", trackingSymbol = "XAU", autoTrack = true,
        createdAt = "2026-09-08T08:00:00Z", updatedAt = "2026-09-08T09:00:00Z", version = version,
    )

    private fun payload(name: String = "Altın") = """{"id":"$ASSET_ID","user_id":"$USER_ID","name":"$name","type":"PRECIOUS_METALS","current_value_minor":100000,"currency":"TRY","tracking_symbol":"XAU","auto_track":true,"created_at":"2026-09-08T08:00:00Z"}"""
    private fun queue(db: FeniqoDatabase) = OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao(), nowEpochMillisProvider = { NOW })
    private fun database() = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(ApplicationProvider.getApplicationContext<Context>()) { FeniqoDatabaseConstructor.initialize() }.setQueryCoroutineContext(Dispatchers.Default).build()

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val ASSET_ID = "33333333-3333-3333-3333-333333333333"
        const val NOW = 1_800_000_000_000L
    }
}
