package com.feniqo.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.FragmentActivity
import com.feniqo.mobile.data.sync.RealtimeSyncCoordinator
import com.feniqo.mobile.data.local.database.DefaultCategorySeeder
import com.feniqo.mobile.navigation.FeniqoNavigation
import com.feniqo.mobile.navigation.AppAuthState
import com.feniqo.mobile.presentation.navigation.RootNavViewModel
import com.feniqo.mobile.presentation.screen.AppLockScreen
import com.feniqo.mobile.presentation.sync.SyncStatusViewModel
import com.feniqo.mobile.presentation.theme.AndroidThemePreferences
import com.feniqo.mobile.presentation.theme.ThemeMode
import com.feniqo.mobile.security.AndroidBiometricAuthenticator
import com.feniqo.mobile.security.AppLockAuthenticationAction
import com.feniqo.mobile.security.AppLockViewModel
import com.feniqo.mobile.security.DeviceAuthenticationAvailability
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var realtimeSyncCoordinator: RealtimeSyncCoordinator

    @Inject
    lateinit var defaultCategorySeeder: DefaultCategorySeeder

    private val rootNavViewModel: RootNavViewModel by viewModels()
    private val syncStatusViewModel: SyncStatusViewModel by viewModels()
    private val appLockViewModel: AppLockViewModel by viewModels()
    private val themePreferences by lazy { AndroidThemePreferences(applicationContext) }
    private var pendingAuthenticationAction = AppLockAuthenticationAction.UNLOCK
    private val biometricAuthenticator by lazy {
        AndroidBiometricAuthenticator(
            activity = this,
            onSuccess = {
                appLockViewModel.onAuthenticationSucceeded(pendingAuthenticationAction)
            },
            onError = appLockViewModel::onAuthenticationError,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            defaultCategorySeeder.seed()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Realtime yalnız foreground'da çalışır; gelen sinyal Room senkronizasyonunu tetikler.
                realtimeSyncCoordinator.run()
            }
        }

        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val authState by rootNavViewModel.authState.collectAsStateWithLifecycle()
            val syncStatus by syncStatusViewModel.uiState.collectAsStateWithLifecycle()
            val appLockState by appLockViewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(authState, appLockState.isLocked) {
                if (authState == AppAuthState.Authenticated && appLockState.isLocked) {
                    authenticate(AppLockAuthenticationAction.UNLOCK)
                }
            }

            App(themeMode = themeMode) {
                if (authState == AppAuthState.Authenticated && appLockState.isLocked) {
                    AppLockScreen(
                        errorMessage = appLockState.errorMessage,
                        onUnlock = { authenticate(AppLockAuthenticationAction.UNLOCK) },
                    )
                } else {
                    FeniqoNavigation(
                        authState = authState,
                        syncStatus = syncStatus,
                        onManualSync = syncStatusViewModel::requestManualSync,
                        onRetryFailed = syncStatusViewModel::retryFailedOperations,
                        onResolveConflict = syncStatusViewModel::openConflictDialog,
                        onResolveConflictDecision = syncStatusViewModel::resolveWorkspaceConflict,
                        onDismissConflictDialog = syncStatusViewModel::dismissConflictDialog,
                        themeMode = themeMode,
                        onThemeModeChange = themePreferences::saveThemeMode,
                        biometricLockEnabled = appLockState.settings?.biometricLockEnabled == true,
                        biometricLockAvailable =
                            appLockState.availability == DeviceAuthenticationAvailability.AVAILABLE,
                        autoLockTimeout = appLockState.settings?.autoLockTimeout
                            ?: com.feniqo.mobile.domain.repository.AutoLockTimeout.AFTER_1_MINUTE,
                        onBiometricLockChange = { enabled ->
                            if (enabled) authenticate(AppLockAuthenticationAction.ENABLE)
                            else appLockViewModel.disableLock()
                        },
                        onAutoLockTimeoutChange = appLockViewModel::setAutoLockTimeout,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockViewModel.setAvailability(biometricAuthenticator.availability())
        appLockViewModel.onForegrounded(System.currentTimeMillis())
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appLockViewModel.onBackgrounded(System.currentTimeMillis())
        }
        super.onStop()
    }

    private fun authenticate(action: AppLockAuthenticationAction) {
        pendingAuthenticationAction = action
        appLockViewModel.clearError()
        biometricAuthenticator.authenticate()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App {
        // Preview placeholder
    }
}
