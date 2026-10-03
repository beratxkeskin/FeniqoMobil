package com.feniqo.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.feniqo.mobile.presentation.localization.provideAppLocale
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.ThemeMode

/**
 * Uygulamanın ortak Compose tema ve kök sarmalayıcısıdır.
 * Tema uygulamanın kökünde bir kez uygulanır ve içerik platforma özel navigasyona delege edilir.
 */
@Composable
fun App(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    languageTag: String? = null,
    content: @Composable () -> Unit,
) {
    provideAppLocale(languageTag = languageTag) {
        FeniqoTheme(darkTheme = themeMode.resolvesToDark(isSystemInDarkTheme())) {
            content()
        }
    }
}
