package com.feniqo.mobile.presentation.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.presentation.screen.SettingsScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h2200dp")
class SettingsScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settings_owns_app_controls_and_routes_account_actions_to_profile_center() {
        var profileClicked = false
        compose.setContent {
            FeniqoTheme {
                SettingsScreen(
                    themeLabel = "Sistem teması",
                    languageRegionLabel = "Türkçe • TRY",
                    onBack = {},
                    onNavigateToProfile = { profileClicked = true },
                    onNavigateToAppearance = {},
                    onNavigateToLanguageRegion = {},
                    onNavigateToNotifications = {},
                    onNavigateToSecurity = {},
                    onNavigateToDataManagement = {},
                    onNavigateToHelpAbout = {},
                )
            }
        }

        compose.onNodeWithText("Görünüm").assertIsDisplayed()
        compose.onNodeWithText("Dil ve bölge").assertIsDisplayed()
        compose.onNodeWithText("Bildirimler").assertIsDisplayed()
        compose.onNodeWithText("Güvenlik ve gizlilik").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Veri yönetimi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Yardım ve hakkında").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Hesap ve çıkış").performScrollTo().performClick()
        assertTrue(profileClicked)
        compose.onAllNodesWithText("Çıkış yap").assertCountEquals(0)
    }
}
