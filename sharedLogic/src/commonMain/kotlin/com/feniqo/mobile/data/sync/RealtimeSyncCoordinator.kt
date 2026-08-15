package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.remote.realtime.RealtimeInvalidationSource
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.retryWhen

/** Realtime sinyalini UI'a uğratmadan mevcut Room senkronizasyon sınırına yönlendirir. */
class RealtimeSyncCoordinator(
    private val authRepository: AuthRepository,
    private val invalidationSource: RealtimeInvalidationSource,
    private val syncRepository: SyncRepository,
    private val sourceRestartDelayMillis: Long = DEFAULT_SOURCE_RESTART_DELAY_MILLIS,
) {
    suspend fun run() {
        authRepository.observeSession().collectLatest { session ->
            if (session == null) return@collectLatest

            invalidationSource.observeFor(session.userId)
                .conflate()
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) return@retryWhen false

                    // SDK dışındaki beklenmeyen akış hatalarında da aboneliği kontrollü biçimde yeniden kur.
                    delay(sourceRestartDelayMillis)
                    true
                }
                .collect {
                    // Bağlantı sinyali de tablo sinyali de yalnız repository üzerinden Room'u günceller.
                    syncRepository.requestSync()
                }
        }
    }

    private companion object {
        const val DEFAULT_SOURCE_RESTART_DELAY_MILLIS = 5_000L
    }
}
