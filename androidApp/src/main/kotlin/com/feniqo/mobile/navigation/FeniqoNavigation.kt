package com.feniqo.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.feniqo.mobile.presentation.screen.CategoriesPlaceholderScreen
import com.feniqo.mobile.presentation.screen.DashboardPlaceholderScreen
import com.feniqo.mobile.presentation.screen.LoginPlaceholderScreen
import com.feniqo.mobile.presentation.screen.RegisterPlaceholderScreen
import com.feniqo.mobile.presentation.screen.SplashLoadingScreen
import com.feniqo.mobile.presentation.screen.ThemeSettingsPlaceholderScreen
import com.feniqo.mobile.presentation.screen.TransactionsPlaceholderScreen
import com.feniqo.mobile.presentation.shell.AppSection
import com.feniqo.mobile.presentation.shell.FeniqoAppShell
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.ThemeMode

/**
 * Android kök navigasyon bileşeni.
 * AppAuthState durumuna göre bağımsız AuthNavHost veya MainNavHost dallarını oluşturur.
 * Dal değiştiğinde eski NavHost Composition'dan çıkar ve eski back-stack yok olur.
 */
@Composable
fun FeniqoNavigation(
    authState: AppAuthState,
    syncStatus: SyncStatusUiState,
    onManualSync: () -> Unit,
    onRetryFailed: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (authState) {
        AppAuthState.Checking -> {
            SplashLoadingScreen(modifier = modifier)
        }
        AppAuthState.Unauthenticated -> {
            AuthNavHost(modifier = modifier)
        }
        AppAuthState.Authenticated -> {
            MainNavHost(
                syncStatus = syncStatus,
                onManualSync = onManualSync,
                onRetryFailed = onRetryFailed,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun AuthNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        modifier = modifier,
    ) {
        composable<LoginRoute> {
            LoginPlaceholderScreen(
                onNavigateToRegister = {
                    navController.navigate(RegisterRoute)
                },
            )
        }
        composable<RegisterRoute> {
            RegisterPlaceholderScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
fun MainNavHost(
    syncStatus: SyncStatusUiState,
    onManualSync: () -> Unit,
    onRetryFailed: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

    val currentSection = when {
        destination?.hasRoute<TransactionsRoute>() == true -> AppSection.TRANSACTIONS
        destination?.hasRoute<CategoriesRoute>() == true -> AppSection.CATEGORIES
        destination?.hasRoute<SettingsRoute>() == true -> AppSection.SETTINGS
        else -> AppSection.DASHBOARD
    }

    FeniqoAppShell(
        selectedSection = currentSection,
        onSectionSelect = { section ->
            val targetRoute = section.toTopLevelDestination().route
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        syncStatus = syncStatus,
        onManualSync = onManualSync,
        onRetryFailed = onRetryFailed,
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
        ) {
            composable<DashboardRoute> {
                DashboardPlaceholderScreen()
            }
            composable<TransactionsRoute> {
                TransactionsPlaceholderScreen()
            }
            composable<CategoriesRoute> {
                CategoriesPlaceholderScreen()
            }
            composable<SettingsRoute> {
                ThemeSettingsPlaceholderScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }
        }
    }
}
