package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.LocalDate
import java.time.LocalDate as JavaLocalDate

/**
 * Android sunum katmanına ait, testlerde sahte tarih ile değiştirilebilen saat/tarih sınırı.
 */
fun interface CurrentDateProvider {
    fun today(): LocalDate
}

/**
 * Android sistem saatini kullanan varsayılan tarih sağlayıcı.
 */
class SystemCurrentDateProvider : CurrentDateProvider {
    override fun today(): LocalDate {
        val now = JavaLocalDate.now()
        return LocalDate(now.year, now.monthValue, now.dayOfMonth)
    }
}
