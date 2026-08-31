package com.feniqo.mobile.presentation.util

import androidx.compose.ui.graphics.Color

/**
 * Hex renk dizgelerini Compose Color nesnesine dönüştüren güvenli parser.
 * Yalnızca başında '#' bulunan #RRGGBB ve #AARRGGBB biçimlerini kabul eder.
 */
object ColorParser {

    fun parseHexColorOrNull(hex: String?): Color? {
        if (hex == null || !hex.startsWith('#')) {
            return null
        }
        val hexValue = hex.substring(1)
        val len = hexValue.length
        if (len != 6 && len != 8) {
            return null
        }
        for (i in 0 until len) {
            val c = hexValue[i]
            val isHexDigit = (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
            if (!isHexDigit) {
                return null
            }
        }

        return if (len == 6) {
            val r = hexValue.substring(0, 2).toInt(16)
            val g = hexValue.substring(2, 4).toInt(16)
            val b = hexValue.substring(4, 6).toInt(16)
            Color(red = r, green = g, blue = b, alpha = 0xFF)
        } else {
            val a = hexValue.substring(0, 2).toInt(16)
            val r = hexValue.substring(2, 4).toInt(16)
            val g = hexValue.substring(4, 6).toInt(16)
            val b = hexValue.substring(6, 8).toInt(16)
            Color(red = r, green = g, blue = b, alpha = a)
        }
    }
}
