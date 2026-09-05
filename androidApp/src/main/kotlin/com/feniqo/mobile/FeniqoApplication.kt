package com.feniqo.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.feniqo.mobile.sync.RecurringTransactionStartupInitializer
import com.feniqo.mobile.sync.SubscriptionPaymentReminderStartupInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt'in uygulama seviyesindeki bağımlılık grafiğini başlatır.
 * WorkManager özel factory'si ile başlatılır.
 */
@HiltAndroidApp
class FeniqoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recurringStartupInitializer: RecurringTransactionStartupInitializer

    @Inject
    lateinit var subscriptionReminderStartupInitializer: SubscriptionPaymentReminderStartupInitializer

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        recurringStartupInitializer.onAppCreate()
        subscriptionReminderStartupInitializer.onAppCreate()
    }
}
