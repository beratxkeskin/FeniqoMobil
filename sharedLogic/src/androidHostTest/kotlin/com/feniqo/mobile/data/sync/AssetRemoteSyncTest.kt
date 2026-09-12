package com.feniqo.mobile.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.AssetEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.core.*
import com.feniqo.mobile.data.remote.dto.*
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class AssetRemoteSyncTest {
    @Test
    fun initial_pull_maps_asset_and_persists_asset_cursor() = runTest {
        val db = database()
        try {
            val remote = AssetRemote(assetDto(version = 3))
            val result = InitialRemoteSync(remote, db.remoteSyncDao(), { RECEIVED_AT }, db.assetDao()).pullFor(EntityId(USER_ID))

            assertEquals(1, result.assetCount)
            val stored = assertNotNull(db.assetDao().getAnyById(ASSET_ID))
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(3, stored.sync.version)
            assertEquals(ASSET_ID, db.syncStateDao().getCursor("ASSET")?.entityId)
        } finally { db.close() }
    }

    @Test
    fun incremental_pull_preserves_pending_local_asset_and_records_conflict() = runTest {
        val db = database()
        try {
            val local = assetEntity(name = "Yerel", status = "SYNCED", version = 1)
            db.assetDao().upsert(local)
            OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao(), nowEpochMillisProvider = { RECEIVED_AT })
                .enqueueAssetV2(
                    local.copy(name = "Yerel düzenleme", sync = local.sync.copy(syncStatus = "PENDING_UPDATE", baseVersion = 1)),
                    OutboxOperationType.UPDATE,
                    Json.encodeToString(assetDto(version = 1).copy(name = "Yerel düzenleme")),
                )

            val result = IncrementalRemoteSync(
                AssetRemote(assetDto(version = 2).copy(name = "Uzak değişiklik")),
                db.remoteSyncDao(), db.syncStateDao(), { RECEIVED_AT + 1 }, db.assetDao(),
            ).pullFor(EntityId(USER_ID))

            assertEquals(1, result.appliedCount, "Aynı turdaki profil pull'u uygulanır; Asset conflict olarak korunur")
            assertEquals(1, result.conflictCount)
            assertEquals(1, result.receivedAssetCount)
            assertEquals("Yerel düzenleme", db.assetDao().getAnyById(ASSET_ID)?.name)
            assertEquals("CONFLICT", db.assetDao().getAnyById(ASSET_ID)?.sync?.syncStatus)
            assertNotNull(db.syncStateDao().getConflict("ASSET", ASSET_ID))
            assertEquals(ASSET_ID, db.syncStateDao().getCursor("ASSET")?.entityId)
        } finally { db.close() }
    }

    private class AssetRemote(private val asset: AssetDto) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String) = ProfileDto(userId, "asset@feniqo.app", createdAt = CREATED_AT, updatedAt = UPDATED_AT, version = 1)
        override suspend fun fetchAssets(query: AssetRemoteQuery) = RemotePage(listOf(asset), query.page, 1)
        override suspend fun fetchCategories(query: CategoryRemoteQuery) = RemotePage<CategoryDto>(emptyList(), query.page, 0)
        override suspend fun fetchTransactions(query: TransactionRemoteQuery) = RemotePage<TransactionDto>(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery) = RemotePage<BudgetDto>(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery) = RemotePage<RecurringTransactionDto>(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery) = RemotePage<SubscriptionDto>(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: GoalRemoteQuery) = RemotePage<GoalDto>(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery) = RemotePage<GoalContributionDto>(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: DebtRemoteQuery) = RemotePage<DebtDto>(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery) = RemotePage<DebtPaymentDto>(emptyList(), query.page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest) = RemotePage<TagDto>(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String) = emptyList<TransactionTagDto>()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    private fun assetDto(version: Long) = AssetDto(ASSET_ID, USER_ID, "Altın", "PRECIOUS_METALS", 100_000, "TRY", trackingSymbol = "XAU", autoTrack = true, createdAt = CREATED_AT, updatedAt = UPDATED_AT, version = version)
    private fun assetEntity(name: String, status: String, version: Long) = AssetEntity(ASSET_ID, USER_ID, name, "PRECIOUS_METALS", 100_000, "TRY", null, null, null, "XAU", true, 1_000, SyncMetadata(status, 1_000, 1_000, null, version, version, null))
    private fun database() = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(ApplicationProvider.getApplicationContext<Context>()) { FeniqoDatabaseConstructor.initialize() }.setQueryCoroutineContext(Dispatchers.Default).build()

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val ASSET_ID = "33333333-3333-3333-3333-333333333333"
        const val CREATED_AT = "2026-09-08T08:00:00Z"
        const val UPDATED_AT = "2026-09-08T09:00:00Z"
        const val RECEIVED_AT = 1_800_000_000_000L
    }
}
