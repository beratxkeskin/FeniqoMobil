package com.feniqo.mobile.presentation.component

/**
 * Platformlar arası Unicode code point sınıflandırma ve dönüşüm desteği.
 *
 * Unicode property tablolarını elle taklit etmek yerine platformun
 * yetkili Unicode motorunu kullanır.
 */
internal expect object MerchantUnicodeSupport {
    fun isLetter(codePoint: Int): Boolean

    fun uppercase(codePoint: Int): Int

    fun codePointToString(codePoint: Int): String
}
