package com.feniqo.mobile.sync

import com.feniqo.mobile.domain.model.LocalDate
import kotlinx.datetime.Instant
import java.time.LocalDate as JavaLocalDate

/**
 * Android sync katmanı için test edilebilir zaman ve yerel tarih sağlayıcı sözleşmesi.
 */
interface RecurringTransactionTimeProvider {
    fun currentLocalDate(): LocalDate
    fun currentInstant(): Instant
}

class SystemRecurringTransactionTimeProvider : RecurringTransactionTimeProvider {
    override fun currentLocalDate(): LocalDate {
        val now = JavaLocalDate.now()
        return LocalDate(now.year, now.monthValue, now.dayOfMonth)
    }

    override fun currentInstant(): Instant {
        return Instant.fromEpochMilliseconds(System.currentTimeMillis())
    }
}
