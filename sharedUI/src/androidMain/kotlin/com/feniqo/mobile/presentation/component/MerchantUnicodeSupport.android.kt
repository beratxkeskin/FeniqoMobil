package com.feniqo.mobile.presentation.component

internal actual object MerchantUnicodeSupport {
    actual fun isLetter(codePoint: Int): Boolean = java.lang.Character.isLetter(codePoint)

    actual fun uppercase(codePoint: Int): Int = java.lang.Character.toUpperCase(codePoint)

    actual fun codePointToString(codePoint: Int): String = String(java.lang.Character.toChars(codePoint))
}
