package com.feniqo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.feniqo.mobile.presentation.theme.AndroidThemePreferences
import com.feniqo.mobile.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themePreferences by lazy { AndroidThemePreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            App(
                themeMode = themeMode,
                onThemeModeChange = themePreferences::saveThemeMode,
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
