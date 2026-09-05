package com.feniqo.mobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.feniqo.mobile.data.local.dao.SubscriptionPaymentReminderReceiptDao
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentReminderReceiptEntity
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.PlanSubscriptionPaymentRemindersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Hilt destekli CoroutineWorker.
 * Vadesi gelen veya yaklaşan abonelik ödeme hatırlatıcılarını planlar, Room receipt tablosu üzerinden
 * atomik claim-before-dispatch uygular ve bildirim izinleri mevcut olduğunda at-most-once teslimatla bildirir.
 */
@HiltWorker
class SubscriptionPaymentReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val planSubscriptionPaymentRemindersUseCase: PlanSubscriptionPaymentRemindersUseCase,
    private val receiptDao: SubscriptionPaymentReminderReceiptDao,
    private val notifier: SubscriptionPaymentReminderNotifier,
    private val timeProvider: RecurringTransactionTimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val localToday = timeProvider.currentLocalDate()
            val nowInstant = timeProvider.currentInstant()

            val subscriptions = observeSubscriptionsUseCase().first()
            val candidates = planSubscriptionPaymentRemindersUseCase(
                subscriptions = subscriptions,
                today = localToday,
            )

            for (candidate in candidates) {
                // Bildirim izni veya uygunluğu yoksa receipt claim edilmez ve bildirim tetiklenmez.
                if (!notifier.canPostNotifications()) {
                    continue
                }

                val receipt = SubscriptionPaymentReminderReceiptEntity(
                    stableKey = candidate.stableKey,
                    subscriptionId = candidate.subscriptionId.value,
                    nextRenewalDate = candidate.nextRenewalDate.toString(),
                    reminderKind = candidate.reminderKind.name,
                    claimedAtEpochMillis = nowInstant.toEpochMilliseconds(),
                )

                // Atomic claim-before-dispatch: ilk claim true dönerse bildirim gönderilir.
                val claimed = receiptDao.claim(receipt)
                if (claimed) {
                    notifier.notifyReminder(candidate)
                }
            }

            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
