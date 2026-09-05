package com.feniqo.mobile.di

import android.content.Context
import androidx.work.WorkManager
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import com.feniqo.mobile.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager ve arka plan senkronizasyon yöneticisini Hilt grafiğine bağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkManagerModule {

    @Binds
    @Singleton
    abstract fun bindBackgroundSyncScheduler(
        scheduler: WorkManagerSyncScheduler,
    ): BackgroundSyncScheduler

    @Binds
    @Singleton
    abstract fun bindRecurringTransactionWorkScheduler(
        scheduler: com.feniqo.mobile.sync.WorkManagerRecurringTransactionScheduler,
    ): com.feniqo.mobile.sync.RecurringTransactionWorkScheduler

    @Binds
    @Singleton
    abstract fun bindRecurringTransactionTimeProvider(
        provider: com.feniqo.mobile.sync.SystemRecurringTransactionTimeProvider,
    ): com.feniqo.mobile.sync.RecurringTransactionTimeProvider

    @Binds
    @Singleton
    abstract fun bindSubscriptionPaymentReminderNotifier(
        notifier: com.feniqo.mobile.sync.AndroidSubscriptionPaymentReminderNotifier,
    ): com.feniqo.mobile.sync.SubscriptionPaymentReminderNotifier

    @Binds
    @Singleton
    abstract fun bindSubscriptionPaymentReminderWorkScheduler(
        scheduler: com.feniqo.mobile.sync.WorkManagerSubscriptionPaymentReminderScheduler,
    ): com.feniqo.mobile.sync.SubscriptionPaymentReminderWorkScheduler

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideSystemRecurringTransactionTimeProvider(): com.feniqo.mobile.sync.SystemRecurringTransactionTimeProvider =
            com.feniqo.mobile.sync.SystemRecurringTransactionTimeProvider()
    }
}
