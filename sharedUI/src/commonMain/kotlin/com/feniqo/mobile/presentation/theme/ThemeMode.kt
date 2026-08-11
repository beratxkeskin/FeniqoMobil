package com.feniqo.mobile.presentation.theme

/** Kullanıcının tema tercihini platformdan bağımsız ifade eder. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}
