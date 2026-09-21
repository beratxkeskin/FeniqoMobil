package com.feniqo.mobile.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.sync.SyncConnectionUiState
import com.feniqo.mobile.presentation.sync.SyncDisplaySeverity
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class ProfileScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profile_center_all_rows_are_active_and_trigger_expected_callbacks() {
        var accountClicked = false
        var personalInfoClicked = false
        var sharedSpacesClicked = false
        var appearanceClicked = false
        var notificationsClicked = false
        var languageRegionClicked = false
        var securityPrivacyClicked = false
        var dataManagementClicked = false
        var legalInfoClicked = false
        var helpAboutClicked = false

        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "Berat Keskin",
                    email = "berat@feniqo.com",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    languageRegionLabel = "Türkçe · TRY",
                    isProfileLoading = false,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = { accountClicked = true },
                    onOpenPersonalInfo = { personalInfoClicked = true },
                    onOpenSharedSpaces = { sharedSpacesClicked = true },
                    onOpenAppearance = { appearanceClicked = true },
                    onOpenNotifications = { notificationsClicked = true },
                    onOpenLanguageRegion = { languageRegionClicked = true },
                    onOpenSecurityPrivacy = { securityPrivacyClicked = true },
                    onOpenDataManagement = { dataManagementClicked = true },
                    onOpenLegalInfo = { legalInfoClicked = true },
                    onOpenHelpAbout = { helpAboutClicked = true },
                    onSignOut = {},
                )
            }
        }

        // Profil özet kartı tıklaması -> Account
        compose.onNodeWithText("Berat Keskin").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, accountClicked)

        // Hesap grubu
        compose.onNodeWithText("Kişisel Bilgiler").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, personalInfoClicked)

        compose.onNodeWithText("Ortak Alanlar").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, sharedSpacesClicked)

        // Tercihler grubu
        compose.onNodeWithText("Görünüm").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, appearanceClicked)

        compose.onNodeWithText("Bildirimler").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, notificationsClicked)

        compose.onNodeWithText("Dil ve Bölge").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, languageRegionClicked)

        // Güvenlik ve Veri grubu
        compose.onNodeWithText("Uygulama Kilidi").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, securityPrivacyClicked)

        compose.onNodeWithText("Veri Yönetimi").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, dataManagementClicked)

        compose.onNodeWithText("Gizlilik").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, legalInfoClicked)

        // Destek grubu
        compose.onNodeWithText("Yardım ve Geri Bildirim").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, helpAboutClicked)

        // Uygulama sürümü bilgi satırı olarak görünür
        compose.onNodeWithText("Uygulama sürümü").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sürüm 1.0").performScrollTo().assertIsDisplayed()

        // Hiçbir satırda "Yakında" rozeti olmamalı
        compose.onAllNodesWithText("Yakında").assertCountEquals(0)

        // "Gizlilik ve hukuki bilgiler" açıklaması görünmeli
        compose.onNodeWithText("Gizlilik ve hukuki bilgiler").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loading_state_displays_progress_message_without_fake_name() {
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "",
                    email = "",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    languageRegionLabel = "Türkçe · TRY",
                    isProfileLoading = true,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("Profiliniz yükleniyor").assertIsDisplayed()
        compose.onAllNodesWithText("Feniqo kullanıcısı").assertCountEquals(0)
    }

    @Test
    fun empty_profile_state_shows_empty_indicator_without_fake_name() {
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "",
                    email = "",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    languageRegionLabel = "Türkçe · TRY",
                    isProfileLoading = false,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("Profil bilgisi bulunamadı").assertIsDisplayed()
        compose.onAllNodesWithText("Profiliniz yükleniyor").assertCountEquals(0)
        compose.onAllNodesWithText("Feniqo kullanıcısı").assertCountEquals(0)
    }

    @Test
    fun session_email_fallback_displays_email_as_title() {
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "session-fallback@feniqo.com",
                    email = "session-fallback@feniqo.com",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    languageRegionLabel = "Türkçe · TRY",
                    isProfileLoading = false,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("session-fallback@feniqo.com").assertIsDisplayed()
        compose.onAllNodesWithText("Feniqo kullanıcısı").assertCountEquals(0)
    }

    @Test
    fun dynamic_language_and_currency_subtitle_is_rendered() {
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "User",
                    email = "user@feniqo.com",
                    workspaceName = "Kişisel alan",
                    currencyCode = "USD",
                    languageRegionLabel = "English · USD",
                    isProfileLoading = false,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("English · USD").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sync_insight_displays_all_seven_severity_types() {
        var currentSyncState by mutableStateOf<SyncStatusUiState>(SyncStatusUiState.Initial.copy(conflictCount = 2))

        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "User",
                    email = "user@feniqo.com",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    isProfileLoading = false,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = currentSyncState,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        val testCases = listOf(
            SyncStatusUiState.Initial.copy(conflictCount = 2) to "Çakışma",
            SyncStatusUiState.Initial.copy(failedCount = 1) to "Hata",
            SyncStatusUiState.Initial.copy(connectionState = SyncConnectionUiState.OFFLINE) to "Çevrimdışı",
            SyncStatusUiState.Initial.copy(connectionState = SyncConnectionUiState.CHECKING) to "Bağlantı",
            SyncStatusUiState.Initial.copy(connectionState = SyncConnectionUiState.ONLINE, isSyncing = true) to "Senkronizasyon",
            SyncStatusUiState.Initial.copy(connectionState = SyncConnectionUiState.ONLINE, pendingCount = 3) to "Bekleyen",
            SyncStatusUiState.Initial.copy(connectionState = SyncConnectionUiState.ONLINE) to "Güncel",
        )

        for ((state, expectedTitle) in testCases) {
            currentSyncState = state
            compose.waitForIdle()
            compose.onNodeWithText(expectedTitle).assertIsDisplayed()
        }
    }

    @Test
    fun profile_when_completely_empty_displays_question_mark_avatar_and_empty_title() {
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "",
                    email = "",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    isProfileLoading = false,
                    isProfileEmpty = true,
                    profileErrorMessage = null,
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("Profil bilgisi bulunamadı").assertIsDisplayed()
        compose.onNodeWithText("?").assertIsDisplayed()
        // Ensure no fake "F" avatar or fake name
        compose.onAllNodesWithText("Feniqo kullanıcısı").assertCountEquals(0)
    }

    @Test
    fun profile_when_error_displays_error_message_exclamation_initial_and_retry_button() {
        var retried = false
        compose.setContent {
            FeniqoTheme {
                ProfileScreen(
                    displayName = "",
                    email = "",
                    workspaceName = "Kişisel alan",
                    currencyCode = "TRY",
                    isProfileLoading = false,
                    isProfileEmpty = true,
                    profileErrorMessage = "Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.",
                    onRetryProfile = { retried = true },
                    signOutError = null,
                    themeLabel = "Açık tema",
                    biometricLockEnabled = false,
                    autoLockLabel = "Kapalı",
                    syncStatus = SyncStatusUiState.Initial,
                    appVersion = "Sürüm 1.0",
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenLanguageRegion = {},
                    onOpenSecurityPrivacy = {},
                    onOpenDataManagement = {},
                    onOpenLegalInfo = {},
                    onOpenHelpAbout = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithText("Profil bilgileri yüklenemedi").assertIsDisplayed()
        compose.onNodeWithText("Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.").assertIsDisplayed()
        compose.onNodeWithText("!").assertIsDisplayed()
        compose.onNodeWithText("Tekrar Dene").performClick()
        assertEquals(true, retried)
    }
}
