package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.ReminderTimeOffset
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.presentation.screen.AppearanceScreen
import com.feniqo.mobile.presentation.screen.AppLockScreen
import com.feniqo.mobile.presentation.screen.NotificationsScreen
import com.feniqo.mobile.presentation.screen.SecurityPrivacyScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class SettingsSliceLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appearanceScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                AppearanceScreen(
                    currentTheme = ThemePreference.SYSTEM,
                    maskAmounts = false,
                    onBack = {},
                    onThemeSelect = {},
                    onMaskAmountsChange = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Görünüm").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama temasını seç").assertIsDisplayed()
        composeRule.onNodeWithText("Cihazının görünümüne en uygun temayı belirle.").assertIsDisplayed()
        composeRule.onNodeWithText("Sistem").assertIsDisplayed()
        composeRule.onNodeWithText("Cihaz temasını kullanır.").assertIsDisplayed()
        composeRule.onNodeWithText("Açık").assertIsDisplayed()
        composeRule.onNodeWithText("Her zaman açık tema.").assertIsDisplayed()
        composeRule.onNodeWithText("Koyu").assertIsDisplayed()
        composeRule.onNodeWithText("Her zaman koyu tema.").assertIsDisplayed()
        composeRule.onNodeWithText("Tutarları gizle").assertIsDisplayed()
        composeRule.onNodeWithText("Tutarlar gizlendiğinde bakiyeler maskelenir.").assertIsDisplayed()
        composeRule.onNodeWithText("Toplam bakiye (Örnek)").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Choose app theme").assertIsDisplayed()
        composeRule.onNodeWithText("Select the theme that best fits your device.").assertIsDisplayed()
        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Follows device theme.").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Always light theme.").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
        composeRule.onNodeWithText("Always dark theme.").assertIsDisplayed()
        composeRule.onNodeWithText("Hide amounts").assertIsDisplayed()
        composeRule.onNodeWithText("Balances are masked when amounts are hidden.").assertIsDisplayed()
        composeRule.onNodeWithText("Total balance (Sample)").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Görünüm").assertCountEquals(0)
        composeRule.onAllNodesWithText("Uygulama temasını seç").assertCountEquals(0)
        composeRule.onAllNodesWithText("Açık").assertCountEquals(0)
        composeRule.onAllNodesWithText("Koyu").assertCountEquals(0)
        composeRule.onAllNodesWithText("Tutarları gizle").assertCountEquals(0)
    }

    @Test
    fun notificationsScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val preferences = NotificationPreferences(
            enabled = true,
            remindDebts = true,
            remindSubscriptions = true,
            remindRecurring = true,
            remindBudgets = true,
            reminderOffset = ReminderTimeOffset.ONE_DAY_BEFORE,
            reminderHour = 9,
            reminderMinute = 0,
            quietHoursEnabled = true,
            quietHoursStartHour = 22,
            quietHoursStartMinute = 0,
            quietHoursEndHour = 8,
            quietHoursEndMinute = 0,
            hideAmountsInNotifications = false,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                NotificationsScreen(
                    isSystemPermissionGranted = true,
                    preferences = preferences,
                    onBack = {},
                    onOpenSystemSettings = {},
                    onPreferencesChange = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Bildirimler").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirim izni açık").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama bildirim gönderebilir.").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirimlere izin ver").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama içi bildirimler açık").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirim kategorileri").assertIsDisplayed()
        composeRule.onNodeWithText("Borç ve alacak vadeleri").assertIsDisplayed()
        composeRule.onNodeWithText("Abonelik yenilemeleri").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrarlayan işlemler").assertIsDisplayed()
        composeRule.onNodeWithText("Bütçe uyarıları").assertIsDisplayed()
        composeRule.onNodeWithText("Zamanlama ve Gizlilik").assertIsDisplayed()
        composeRule.onNodeWithText("Hatırlatma zamanı").assertIsDisplayed()
        composeRule.onNodeWithText("1 gün önce • 09:00").assertIsDisplayed()
        composeRule.onNodeWithText("Sessiz saatler").assertIsDisplayed()
        composeRule.onNodeWithText("22:00 – 08:00").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirimlerde tutarları gizle").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Notification permission enabled").assertIsDisplayed()
        composeRule.onNodeWithText("The app can send notifications.").assertIsDisplayed()
        composeRule.onNodeWithText("Allow notifications").assertIsDisplayed()
        composeRule.onNodeWithText("In-app notifications enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Notification categories").assertIsDisplayed()
        composeRule.onNodeWithText("Debt and receivable due dates").assertIsDisplayed()
        composeRule.onNodeWithText("Subscription renewals").assertIsDisplayed()
        composeRule.onNodeWithText("Recurring transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Budget alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Timing and Privacy").assertIsDisplayed()
        composeRule.onNodeWithText("Reminder time").assertIsDisplayed()
        composeRule.onNodeWithText("1 day before • 09:00").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet hours").assertIsDisplayed()
        composeRule.onNodeWithText("22:00 – 08:00").assertIsDisplayed()
        composeRule.onNodeWithText("Hide amounts in notifications").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Bildirimler").assertCountEquals(0)
        composeRule.onAllNodesWithText("Bildirim kategorileri").assertCountEquals(0)
        composeRule.onAllNodesWithText("Borç ve alacak vadeleri").assertCountEquals(0)
        composeRule.onAllNodesWithText("Sessiz saatler").assertCountEquals(0)
        composeRule.onAllNodesWithText("1 gün önce • 09:00").assertCountEquals(0)
    }

    @Test
    fun securityPrivacyScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                SecurityPrivacyScreen(
                    biometricLockEnabled = true,
                    biometricLockAvailable = true,
                    autoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
                    hideAmounts = false,
                    onBack = {},
                    onBiometricLockChange = {},
                    onAutoLockTimeoutChange = {},
                    onHideAmountsChange = {},
                    onNavigateToChangePassword = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Güvenlik ve gizlilik").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama kilidi açık").assertIsDisplayed()
        composeRule.onNodeWithText("Biyometri veya cihaz ekran kilidiyle doğrulama.").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama kilidi").assertIsDisplayed()
        composeRule.onNodeWithText("Biyometri veya cihaz şifresi ile koru").assertIsDisplayed()
        composeRule.onNodeWithText("Otomatik kilit").assertIsDisplayed()
        composeRule.onNodeWithText("1 dakika sonra").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirimlerde tutarları gizle").assertIsDisplayed()
        composeRule.onNodeWithText("Kilit ekranında bakiyeleri maskele").assertIsDisplayed()
        composeRule.onNodeWithText("Parolayı değiştir").assertIsDisplayed()
        composeRule.onNodeWithText("Hesap giriş şifrenizi güncelleyin").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Security and privacy").assertIsDisplayed()
        composeRule.onNodeWithText("App lock is on").assertIsDisplayed()
        composeRule.onNodeWithText("Authenticate with biometrics or screen lock.").assertIsDisplayed()
        composeRule.onNodeWithText("App lock").assertIsDisplayed()
        composeRule.onNodeWithText("Protect with biometrics or device passcode").assertIsDisplayed()
        composeRule.onNodeWithText("Auto-lock").assertIsDisplayed()
        composeRule.onNodeWithText("After 1 minute").assertIsDisplayed()
        composeRule.onNodeWithText("Hide amounts in notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Mask balances on lock screen").assertIsDisplayed()
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.onNodeWithText("Update your account login password").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Güvenlik ve gizlilik").assertCountEquals(0)
        composeRule.onAllNodesWithText("Uygulama kilidi açık").assertCountEquals(0)
        composeRule.onAllNodesWithText("Uygulama kilidi").assertCountEquals(0)
        composeRule.onAllNodesWithText("Otomatik kilit").assertCountEquals(0)
        composeRule.onAllNodesWithText("1 dakika sonra").assertCountEquals(0)
        composeRule.onAllNodesWithText("Parolayı değiştir").assertCountEquals(0)
    }

    @Test
    fun appLockScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                AppLockScreen(
                    errorMessage = null,
                    onUnlock = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Feniqo kilitli").assertIsDisplayed()
        composeRule.onNodeWithText("Devam etmek için biyometri veya cihaz ekran kilidinizle doğrulayın.").assertIsDisplayed()
        composeRule.onNodeWithText("Kilidi Aç").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Feniqo is locked").assertIsDisplayed()
        composeRule.onNodeWithText("Verify with biometrics or your device screen lock to continue.").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Feniqo kilitli").assertCountEquals(0)
        composeRule.onAllNodesWithText("Devam etmek için biyometri veya cihaz ekran kilidinizle doğrulayın.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Kilidi Aç").assertCountEquals(0)
    }
}
