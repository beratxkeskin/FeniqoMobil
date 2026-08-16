package com.feniqo.mobile.domain.sync

/**
 * Platformdan bağımsız arka plan senkronizasyon planlama sözleşmesi.
 * Android'de WorkManager, gelecekte iOS'ta BGTaskScheduler adaptörü ile uygulanır.
 */
interface BackgroundSyncScheduler {
    /**
     * Uygulama açılışında veya oturum açma/kayıt sonrasında ilk senkronizasyonu planlar.
     * Var olan işi korumak için KEEP politikası kullanır.
     */
    fun scheduleInitialSync()

    /**
     * Yerel outbox mutasyonu sonrasında arka plan senkronizasyonunu tetikler.
     * Çalışan iş varsa yeni mutasyonun kaybolmaması için zincirleme (APPEND_OR_REPLACE) kullanır.
     */
    fun scheduleOutboxSync()

    /**
     * Oturum kapatma gibi durumlarda yalnızca uygulamaya ait senkronizasyon işlerini iptal eder.
     */
    fun cancelSyncWork()
}
