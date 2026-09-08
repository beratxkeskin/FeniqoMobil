package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow

/**
 * Senkronizasyon genel durumunu (durum, bekleyen, başarısız, çakışma, son başarılı zaman ve hata)
 * Room Single Source of Truth üzerinden gözlemler.
 */
class ObserveSyncOverviewUseCase(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(): Flow<SyncOverview> = syncRepository.observeOverview()
}

/**
 * Kullanıcı tarafından tetiklenen manuel senkronizasyon işlemini başlatır.
 * Outbox push ve artımlı pull işlemlerini koordineli ve mutex korumalı olarak yürütür.
 */
class RequestManualSyncUseCase(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(): RepositoryResult<Unit> = syncRepository.requestSync()
}

/**
 * Başarısız olmuş (FAILED durumundaki) outbox operasyonlarını yeniden denenebilir duruma getirir
 * ve senkronizasyonu başlatır.
 */
class RetryFailedSyncOperationsUseCase(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(): RepositoryResult<Unit> = syncRepository.retryFailedOperations()
}

/**
 * Bekleyen çakışmaları (conflict) Room Single Source of Truth üzerinden gözlemler.
 */
class ObserveSyncConflictsUseCase(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(): Flow<List<com.feniqo.mobile.domain.repository.SyncConflict>> =
        syncRepository.observeConflicts()
}

/**
 * Belirli bir varlık çakışmasını kullanıcı kararına (KEEP_LOCAL / KEEP_REMOTE) göre çözer.
 */
class ResolveSyncConflictUseCase(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(
        entityId: com.feniqo.mobile.domain.model.EntityId,
        resolution: com.feniqo.mobile.domain.repository.ConflictResolution,
    ): RepositoryResult<Unit> = syncRepository.resolveConflict(entityId, resolution)
}
