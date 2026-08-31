package com.feniqo.mobile.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama başlangıcında tekrarlayan işlem kontrollerini başlatan test edilebilir adaptör.
 */
@Singleton
class RecurringTransactionStartupInitializer @Inject constructor(
    private val scheduler: RecurringTransactionWorkScheduler,
) {
    fun onAppCreate() {
        scheduler.scheduleRecurringCheck()
    }
}
