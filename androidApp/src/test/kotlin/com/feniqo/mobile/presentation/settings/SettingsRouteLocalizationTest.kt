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
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.presentation.screen.AccountScreen
import com.feniqo.mobile.presentation.screen.DataManagementScreen
import com.feniqo.mobile.presentation.screen.PersonalInfoScreen
import com.feniqo.mobile.presentation.screen.PhotoPreviewScreen
import com.feniqo.mobile.presentation.screen.SyncStatusScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class SettingsRouteLocalizationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                AccountScreen(
                    displayName = null,
                    email = "test@example.com",
                    emailVerificationStatus = EmailVerificationStatus.VERIFIED,
                    loginMethod = AccountLoginMethod.EMAIL,
                    onBack = {},
                    onNavigateToPersonalInfo = {},
                    onNavigateToChangeEmail = {},
                    onNavigateToChangePassword = {},
                    onNavigateToDeleteAccount = {},
                    onPickFromGallery = {},
                    onTakePhoto = {},
                    onRemovePhoto = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Profil fotoğrafı").assertIsDisplayed()
        composeRule.onNodeWithText("Feniqo Kullanıcısı").assertIsDisplayed()
        composeRule.onNodeWithText("Kişisel bilgiler").assertIsDisplayed()
        composeRule.onNodeWithText("Parolayı değiştir").assertIsDisplayed()
        composeRule.onNodeWithText("Hesabı sil").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Profile photo").assertIsDisplayed()
        composeRule.onNodeWithText("Feniqo User").assertIsDisplayed()
        composeRule.onNodeWithText("Personal information").assertIsDisplayed()
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.onNodeWithText("Delete account").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Profil fotoğrafı").assertCountEquals(0)
        composeRule.onAllNodesWithText("Feniqo Kullanıcısı").assertCountEquals(0)
        composeRule.onAllNodesWithText("Parolayı değiştir").assertCountEquals(0)
        composeRule.onAllNodesWithText("Hesabı sil").assertCountEquals(0)
    }

    @Test
    fun personalInfoScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                PersonalInfoScreen(
                    currentFullName = "Ahmet Yılmaz",
                    email = "ahmet@example.com",
                    isLoading = false,
                    onBack = {},
                    onChangePhoto = {},
                    onNavigateToChangeEmail = {},
                    onSave = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Kişisel bilgiler").assertIsDisplayed()
        composeRule.onNodeWithText("Görünen ad").assertIsDisplayed()
        composeRule.onNodeWithText("E-postayı değiştir").assertIsDisplayed()
        composeRule.onNodeWithText("Değişiklikleri kaydet").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Personal information").assertIsDisplayed()
        composeRule.onNodeWithText("Display name").assertIsDisplayed()
        composeRule.onNodeWithText("Change email").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Görünen ad").assertCountEquals(0)
        composeRule.onAllNodesWithText("E-postayı değiştir").assertCountEquals(0)
        composeRule.onAllNodesWithText("Değişiklikleri kaydet").assertCountEquals(0)
    }

    @Test
    fun dataManagementScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                DataManagementScreen(
                    categoryScopeCount = 5,
                    transactionScopeCount = 120,
                    pendingImport = null,
                    onBack = {},
                    onExportCsv = {},
                    onExportJsonBackup = {},
                    onSelectBackupFile = {},
                    onConfirmImport = {},
                    onDismissImport = {},
                    onNavigateToSyncStatus = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Veri yönetimi").assertIsDisplayed()
        composeRule.onNodeWithText("İşlemleri CSV olarak kaydet").assertIsDisplayed()
        composeRule.onNodeWithText("JSON yedeği oluştur").assertIsDisplayed()
        composeRule.onNodeWithText("JSON yedeğini içe aktar").assertIsDisplayed()
        composeRule.onNodeWithText("Senkronizasyon durumu").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Data management").assertIsDisplayed()
        composeRule.onNodeWithText("Save transactions as CSV").assertIsDisplayed()
        composeRule.onNodeWithText("Create JSON backup").assertIsDisplayed()
        composeRule.onNodeWithText("Import JSON backup").assertIsDisplayed()
        composeRule.onNodeWithText("Sync status").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Veri yönetimi").assertCountEquals(0)
        composeRule.onAllNodesWithText("İşlemleri CSV olarak kaydet").assertCountEquals(0)
        composeRule.onAllNodesWithText("JSON yedeği oluştur").assertCountEquals(0)
        composeRule.onAllNodesWithText("JSON yedeğini içe aktar").assertCountEquals(0)
        composeRule.onAllNodesWithText("Senkronizasyon durumu").assertCountEquals(0)
    }

    @Test
    fun syncStatusScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                SyncStatusScreen(
                    isOnline = true,
                    isSyncing = false,
                    pendingChangesCount = 0,
                    lastSyncFormatted = "",
                    conflictCount = 0,
                    hasLegacyQuarantinedData = false,
                    onBack = {},
                    onRetrySync = {},
                    onInspectConflict = {},
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Senkronizasyon").assertIsDisplayed()
        composeRule.onNodeWithText("Bekleyen değişiklikler").assertIsDisplayed()
        composeRule.onNodeWithText("Bekleyen değişiklik yok").assertIsDisplayed()
        composeRule.onNodeWithText("Son başarılı eşitleme").assertIsDisplayed()
        composeRule.onNodeWithText("Henüz eşitlenmedi").assertIsDisplayed()
        composeRule.onNodeWithText("Yeniden dene").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Synchronization").assertIsDisplayed()
        composeRule.onNodeWithText("Pending changes").assertIsDisplayed()
        composeRule.onNodeWithText("No pending changes").assertIsDisplayed()
        composeRule.onNodeWithText("Last successful sync").assertIsDisplayed()
        composeRule.onNodeWithText("Not synced yet").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Senkronizasyon").assertCountEquals(0)
        composeRule.onAllNodesWithText("Bekleyen değişiklik yok").assertCountEquals(0)
        composeRule.onAllNodesWithText("Son başarılı eşitleme").assertCountEquals(0)
        composeRule.onAllNodesWithText("Henüz eşitlenmedi").assertCountEquals(0)
        composeRule.onAllNodesWithText("Yeniden dene").assertCountEquals(0)
    }

    @Test
    fun photoPreviewScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                PhotoPreviewScreen(
                    imageBitmap = null,
                    onBack = {},
                    onReselect = {},
                    onSavePhoto = { _, _, _ -> },
                )
            }
        }

        // Turkish assertions
        composeRule.onNodeWithText("Fotoğraf önizleme").assertIsDisplayed()
        composeRule.onNodeWithText("Yeniden seç").assertIsDisplayed()
        composeRule.onNodeWithText("Fotoğrafı kaydet").assertIsDisplayed()
        composeRule.onNodeWithText("Yalnızca bu cihazda saklanır.").assertIsDisplayed()

        // Switch to English
        languageTag = "en"
        composeRule.waitForIdle()

        // English assertions
        composeRule.onNodeWithText("Photo preview").assertIsDisplayed()
        composeRule.onNodeWithText("Choose again").assertIsDisplayed()
        composeRule.onNodeWithText("Save photo").assertIsDisplayed()
        composeRule.onNodeWithText("Stored on this device only.").assertIsDisplayed()

        // Regression: Turkish texts must not remain
        composeRule.onAllNodesWithText("Fotoğraf önizleme").assertCountEquals(0)
        composeRule.onAllNodesWithText("Yeniden seç").assertCountEquals(0)
        composeRule.onAllNodesWithText("Fotoğrafı kaydet").assertCountEquals(0)
        composeRule.onAllNodesWithText("Yalnızca bu cihazda saklanır.").assertCountEquals(0)
    }
}
