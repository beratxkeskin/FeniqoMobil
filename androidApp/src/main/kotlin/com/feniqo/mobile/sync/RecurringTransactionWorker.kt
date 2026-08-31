package com.feniqo.mobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.GenerateDueRecurringTransactionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Hilt destekli CoroutineWorker.
 * Vadesi gelen tekrarlayan işlemlerden normal Transaction ve CREATE V2 outbox kayıtları üretir.
 * Yalnızca GenerateDueRecurringTransactionsUseCase ve RecurringTransactionTimeProvider tüketir;
 * DAO, Room, Supabase veya somut repository sınıflarına doğrudan bağımlı değildir.
 */
@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generateDueRecurringTransactionsUseCase: GenerateDueRecurringTransactionsUseCase,
    private val timeProvider: RecurringTransactionTimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val localToday = timeProvider.currentLocalDate()
            val nowInstant = timeProvider.currentInstant()

            val result = generateDueRecurringTransactionsUseCase(
                throughDate = localToday,
                createdAt = nowInstant,
            )

            return when (result) {
                is RepositoryResult.Success -> Result.success()
                is RepositoryResult.Failure -> when (result.error) {
                    is AppError.Network -> Result.retry()
                    is AppError.Authentication -> {
                        // Oturum yok veya geçersiz; gereksiz retry fırtınasını önlemek için success döner.
                        Result.success()
                    }
                    is AppError.Conflict,
                    is AppError.Validation,
                    is AppError.Storage,
                    is AppError.Unknown -> Result.failure()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
