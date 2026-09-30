package com.feniqo.mobile.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class ProfileNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun setupNavHost() {
        compose.setContent {
            FeniqoTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = ProfileRoute) {
                    composable<ProfileRoute> {
                        ProfileScreen(
                            displayName = "Berat Keskin",
                            email = "berat@feniqo.com",
                            workspaceName = "Kişisel alan",
                            isProfileLoading = false,
                            signOutError = null,
                            syncStatus = SyncStatusUiState.Initial,
                            onBack = { navController.popBackStack() },
                            onOpenAccount = { navController.navigate(AccountRoute) },
                            onOpenPersonalInfo = { navController.navigate(PersonalInfoRoute) },
                            onOpenSharedSpaces = { navController.navigate(WorkspacePickerRoute) },
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                            onOpenDataManagement = { navController.navigate(DataManagementRoute) },
                            onSignOut = {},
                        )
                    }
                    composable<AccountRoute> { }
                    composable<PersonalInfoRoute> { }
                    composable<WorkspacePickerRoute> { }
                    composable<SettingsRoute> { }
                    composable<DataManagementRoute> { }
                }
            }
        }
    }

    @Test
    fun clicking_profile_summary_transitions_to_account() {
        setupNavHost()
        compose.onNodeWithText("Berat Keskin").performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<AccountRoute>())
    }

    @Test
    fun clicking_personal_info_transitions_to_personal_info() {
        setupNavHost()
        compose.onNodeWithText("Kişisel Bilgiler").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<PersonalInfoRoute>())
    }

    @Test
    fun clicking_shared_spaces_transitions_to_workspace_picker() {
        setupNavHost()
        compose.onNodeWithText("Ortak Alanlar").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<WorkspacePickerRoute>())
    }

    @Test
    fun clicking_application_settings_transitions_to_settings() {
        setupNavHost()
        compose.onNodeWithText("Uygulama Ayarları").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<SettingsRoute>())
    }
}
