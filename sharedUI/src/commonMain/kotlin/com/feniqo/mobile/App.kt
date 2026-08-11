package com.feniqo.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.feniqo.mobile.presentation.shell.FeniqoAppShell
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.ThemeMode

/** Uygulamanın ortak Compose giriş noktasıdır. */
@Composable
fun App(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: suspend (ThemeMode) -> Unit = {},
) {
    FeniqoTheme(darkTheme = themeMode.resolvesToDark(isSystemInDarkTheme())) {
        FeniqoAppShell(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}
