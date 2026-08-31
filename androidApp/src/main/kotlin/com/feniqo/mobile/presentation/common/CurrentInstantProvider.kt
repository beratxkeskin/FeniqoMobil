package com.feniqo.mobile.presentation.common

import kotlinx.datetime.Instant

/**
 * Android sunum katmanına ait, testlerde sahte anlık zaman ile değiştirilebilen zaman sağlayıcısı.
 */
fun interface CurrentInstantProvider {
    fun now(): Instant
}

/**
 * Android sistem saatini kullanan varsayılan anlık zaman sağlayıcı.
 */
class SystemCurrentInstantProvider : CurrentInstantProvider {
    override fun now(): Instant {
        return Instant.fromEpochMilliseconds(System.currentTimeMillis())
    }
}
