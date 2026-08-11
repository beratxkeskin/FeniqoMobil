package com.feniqo.mobile.domain.model

import kotlinx.datetime.LocalDate as KotlinxLocalDate

/** İşlem tarihi için KMP uyumlu, saat diliminden bağımsız sivil tarih tipi. */
typealias LocalDate = KotlinxLocalDate

/**
 * Finansal işlem tarihi cihazın yerel takvim günüdür; sunucu zamanları ayrı olarak UTC tutulur.
 * V1 ürün kararı gereği gelecekteki tarihler kullanıcı işlemlerinde kabul edilmez.
 */
object TransactionDatePolicy {
    fun isAllowed(transactionDate: LocalDate, today: LocalDate): Boolean = transactionDate <= today
}
