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

/**
 * ProfileScreen bileşeninin satır tıklama eylemlerinin (callback) Navigation Compose
 * NavController.navigate(Route) çağrılarına doğru bağlandığını ve hedeflenen rota sınıflarına
 * (AccountRoute, PersonalInfoRoute vb.) geçiş yaptığını doğrulayan entegrasyon testidir.
 *
 * KAPSAM NOTU: Bu test, ProfileScreen'in Compose seviyesindeki callback-to-route geçişlerini doğrular.
 * Üretim seviyesindeki tam MainNavHost ve Hilt bağlamlarının uçtan uca doğrulanması
 * fiziksel/sanal cihaz kabul adımlarıyla tamamlanmalıdır.
 */
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
                NavHost(
                    navController = navController,
                    startDestination = ProfileRoute,
                ) {
                    composable<ProfileRoute> {
                        ProfileScreen(
                            displayName = "Berat Keskin",
                            email = "berat@feniqo.com",
                            workspaceName = "Kişisel alan",
                            currencyCode = "TRY",
                            isProfileLoading = false,
                            signOutError = null,
                            themeLabel = "Açık tema",
                            biometricLockEnabled = false,
                            autoLockLabel = "Kapalı",
                            syncStatus = SyncStatusUiState.Initial,
                            appVersion = "Sürüm 1.0",
                            onBack = { navController.popBackStack() },
                            onOpenAccount = { navController.navigate(AccountRoute) { launchSingleTop = true } },
                            onOpenPersonalInfo = { navController.navigate(PersonalInfoRoute) { launchSingleTop = true } },
                            onOpenSharedSpaces = { navController.navigate(WorkspacePickerRoute) { launchSingleTop = true } },
                            onOpenAppearance = { navController.navigate(AppearanceRoute) { launchSingleTop = true } },
                            onOpenNotifications = { navController.navigate(NotificationsSettingsRoute) { launchSingleTop = true } },
                            onOpenLanguageRegion = { navController.navigate(LanguageRegionRoute) { launchSingleTop = true } },
                            onOpenSecurityPrivacy = { navController.navigate(SecurityPrivacyRoute) { launchSingleTop = true } },
                            onOpenDataManagement = { navController.navigate(DataManagementRoute) { launchSingleTop = true } },
                            onOpenLegalInfo = { navController.navigate(LegalInfoRoute) { launchSingleTop = true } },
                            onOpenHelpAbout = { navController.navigate(HelpAboutRoute) { launchSingleTop = true } },
                            onSignOut = {},
                        )
                    }
                    composable<AccountRoute> { }
                    composable<PersonalInfoRoute> { }
                    composable<WorkspacePickerRoute> { }
                    composable<AppearanceRoute> { }
                    composable<NotificationsSettingsRoute> { }
                    composable<LanguageRegionRoute> { }
                    composable<SecurityPrivacyRoute> { }
                    composable<DataManagementRoute> { }
                    composable<LegalInfoRoute> { }
                    composable<HelpAboutRoute> { }
                }
            }
        }
    }

    @Test
    fun clicking_profile_summary_transitions_navhost_to_account_route() {
        setupNavHost()
        compose.onNodeWithText("Berat Keskin").performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<AccountRoute>())
    }

    @Test
    fun clicking_personal_info_transitions_navhost_to_personal_info_route() {
        setupNavHost()
        compose.onNodeWithText("Kişisel Bilgiler").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<PersonalInfoRoute>())
    }

    @Test
    fun clicking_shared_spaces_transitions_navhost_to_workspace_picker_route() {
        setupNavHost()
        compose.onNodeWithText("Ortak Alanlar").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<WorkspacePickerRoute>())
    }

    @Test
    fun clicking_appearance_transitions_navhost_to_appearance_route() {
        setupNavHost()
        compose.onNodeWithText("Görünüm").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<AppearanceRoute>())
    }

    @Test
    fun clicking_notifications_transitions_navhost_to_notifications_route() {
        setupNavHost()
        compose.onNodeWithText("Bildirimler").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<NotificationsSettingsRoute>())
    }

    @Test
    fun clicking_language_region_transitions_navhost_to_language_region_route() {
        setupNavHost()
        compose.onNodeWithText("Dil ve Bölge").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<LanguageRegionRoute>())
    }

    @Test
    fun clicking_security_privacy_transitions_navhost_to_security_privacy_route() {
        setupNavHost()
        compose.onNodeWithText("Uygulama Kilidi").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<SecurityPrivacyRoute>())
    }

    @Test
    fun clicking_data_management_transitions_navhost_to_data_management_route() {
        setupNavHost()
        compose.onNodeWithText("Veri Yönetimi").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<DataManagementRoute>())
    }

    @Test
    fun clicking_legal_info_transitions_navhost_to_legal_info_route() {
        setupNavHost()
        compose.onNodeWithText("Gizlilik").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<LegalInfoRoute>())
    }

    @Test
    fun clicking_help_about_transitions_navhost_to_help_about_route() {
        setupNavHost()
        compose.onNodeWithText("Yardım ve Geri Bildirim").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(navController.currentDestination!!.hasRoute<HelpAboutRoute>())
    }
}
