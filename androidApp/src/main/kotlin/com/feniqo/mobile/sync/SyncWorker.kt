package com.feniqo.mobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hilt destekli CoroutineWorker.
 * Yalnızca SyncRepository arayüzünü tüketir; Room DAO veya Supabase client doğrudan enjekte edilmez.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (val result = syncRepository.requestSync()) {
            is RepositoryResult.Success -> Result.success()
            is RepositoryResult.Failure -> when (result.error) {
                is AppError.Network -> Result.retry()
                is AppError.Authentication -> {
                    // Oturum yok veya geçersiz; gereksiz retry fırtınasını önlemek için success döner.
                    Result.success()
                }
                is AppError.Conflict -> {
                    // Çakışma Room'da saklandı ve kullanıcı kararı bekliyor; iş zinciri başarıyla sonlandırılır.
                    Result.success()
                }
                is AppError.Validation,
                is AppError.Storage,
                is AppError.Unknown -> Result.failure()
            }
        }
    }
}
