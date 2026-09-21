package com.feniqo.mobile.demo

import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Demo yazıları Room/outbox'ta kalır; başarıyla buluta gönderilmiş gibi gösterilmez. */
class DemoSyncRepository(private val queue: OfflineWriteQueue) : SyncRepository {
    override fun observeOverview(): Flow<SyncOverview> = queue.observePendingCount().map {
        SyncOverview(SyncPhase.OFFLINE, it, 0, 0, null, null)
    }
    override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())
    override suspend fun requestSync(): RepositoryResult<Unit> = unavailable()
    override suspend fun retryFailedOperations(): RepositoryResult<Unit> = unavailable()
    override suspend fun resolveConflict(entityId: EntityId, resolution: ConflictResolution): RepositoryResult<Unit> = unavailable()
    private fun unavailable() = RepositoryResult.Failure(AppError.Validation("demo_local_only"))
}
