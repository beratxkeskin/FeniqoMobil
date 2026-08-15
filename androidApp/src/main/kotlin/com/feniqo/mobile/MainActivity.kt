package com.feniqo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.feniqo.mobile.data.sync.RealtimeSyncCoordinator
import com.feniqo.mobile.presentation.theme.AndroidThemePreferences
import com.feniqo.mobile.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    private val themePreferences by lazy { AndroidThemePreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Realtime yalnız foreground'da çalışır; gelen sinyal Room senkronizasyonunu tetikler.
                realtimeSyncCoordinator.run()
            }
        }

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
