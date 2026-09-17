package com.feniqo.mobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.feniqo.mobile.data.local.dao.SubscriptionPaymentReminderReceiptDao
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentReminderReceiptEntity
import com.feniqo.mobile.domain.notification.NotificationPolicy
import com.feniqo.mobile.domain.notification.ReminderCategory
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.PlanSubscriptionPaymentRemindersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Hilt destekli CoroutineWorker.
 * Vadesi gelen veya yaklaşan abonelik ödeme hatırlatıcılarını planlar, Room receipt tablosu üzerinden
 * atomik claim-before-dispatch uygular, kullanıcı ayarlarındaki bildirim tercihleri ve sessiz saatleri
 * (NotificationPolicy) doğrular ve bildirim izinleri mevcut olduğunda at-most-once teslimatla bildirir.
 */
@HiltWorker
class SubscriptionPaymentReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val planSubscriptionPaymentRemindersUseCase: PlanSubscriptionPaymentRemindersUseCase,
    private val userSettingsRepository: UserSettingsRepository,
    private val receiptDao: SubscriptionPaymentReminderReceiptDao,
    private val notifier: SubscriptionPaymentReminderNotifier,
    private val timeProvider: RecurringTransactionTimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val localToday = timeProvider.currentLocalDate()
            val nowInstant = timeProvider.currentInstant()
            val (currentHour, currentMinute) = timeProvider.currentLocalTime()

            val settings = userSettingsRepository.observeSettings().first()
            val notificationPrefs = settings.notifications

            // NotificationPolicy kontrolü: Kullanıcı abonelik bildirimlerini kapatmış mı veya sessiz saatlerde miyiz?
            val shouldDeliver = NotificationPolicy.shouldDeliverImmediateReminder(
                preferences = notificationPrefs,
                category = ReminderCategory.SUBSCRIPTION,
                currentHour = currentHour,
                currentMinute = currentMinute,
            )

            if (!shouldDeliver) {
                return Result.success()
            }

            val subscriptions = observeSubscriptionsUseCase().first()
            val candidates = planSubscriptionPaymentRemindersUseCase(
                subscriptions = subscriptions,
                today = localToday,
            )

            for (candidate in candidates) {
                // Sistem bildirim izni veya uygunluğu yoksa receipt claim edilmez ve bildirim tetiklenmez.
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
                    notifier.notifyReminder(
                        candidate = candidate,
                        hideAmounts = notificationPrefs.hideAmountsInNotifications,
                    )
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
