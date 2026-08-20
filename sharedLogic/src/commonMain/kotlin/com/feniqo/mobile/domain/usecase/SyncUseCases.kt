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
