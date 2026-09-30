package com.feniqo.mobile.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.sync.SyncConnectionUiState
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Composable
    private fun TestProfile(
        syncStatus: SyncStatusUiState = SyncStatusUiState.Initial,
        isLoading: Boolean = false,
        isEmpty: Boolean = false,
        profileError: String? = null,
        onRetry: (() -> Unit)? = null,
        onAccount: () -> Unit = {},
        onPersonalInfo: () -> Unit = {},
        onSharedSpaces: () -> Unit = {},
        onSettings: () -> Unit = {},
        onDataManagement: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        FeniqoTheme {
            ProfileScreen(
                displayName = if (isLoading || isEmpty || profileError != null) "" else "Berat Keskin",
                email = if (isLoading || isEmpty || profileError != null) "" else "berat@feniqo.com",
                workspaceName = "Kişisel alan",
                isProfileLoading = isLoading,
                isProfileEmpty = isEmpty,
                profileErrorMessage = profileError,
                onRetryProfile = onRetry,
                signOutError = null,
                syncStatus = syncStatus,
                onBack = {},
                onOpenAccount = onAccount,
                onOpenPersonalInfo = onPersonalInfo,
                onOpenSharedSpaces = onSharedSpaces,
                onOpenSettings = onSettings,
                onOpenDataManagement = onDataManagement,
                onSignOut = onSignOut,
            )
        }
    }

    @Test
    fun profile_center_owns_account_workspace_sync_and_single_settings_entry() {
        var accountClicked = false
        var personalInfoClicked = false
        var sharedSpacesClicked = false
        var settingsClicked = false

        compose.setContent {
            TestProfile(
                onAccount = { accountClicked = true },
                onPersonalInfo = { personalInfoClicked = true },
                onSharedSpaces = { sharedSpacesClicked = true },
                onSettings = { settingsClicked = true },
            )
        }

        compose.onNodeWithText("Berat Keskin").performClick()
        compose.onNodeWithText("Kişisel Bilgiler").performScrollTo().performClick()
        compose.onNodeWithText("Ortak Alanlar").performScrollTo().performClick()
        compose.onNodeWithText("Uygulama Ayarları").performScrollTo().performClick()

        assertTrue(accountClicked)
        assertTrue(personalInfoClicked)
        assertTrue(sharedSpacesClicked)
        assertTrue(settingsClicked)
        compose.onAllNodesWithText("Görünüm").assertCountEquals(0)
        compose.onAllNodesWithText("Bildirimler").assertCountEquals(0)
        compose.onAllNodesWithText("Dil ve Bölge").assertCountEquals(0)
        compose.onAllNodesWithText("Uygulama Kilidi").assertCountEquals(0)
        compose.onAllNodesWithText("Yardım ve Geri Bildirim").assertCountEquals(0)
    }

    @Test
    fun sign_out_always_requires_confirmation() {
        var signedOut = false
        compose.setContent { TestProfile(onSignOut = { signedOut = true }) }

        compose.onNodeWithText("Çıkış Yap").performScrollTo().performClick()
        compose.onNodeWithText("Çıkış yapmak istiyor musun?").assertIsDisplayed()
        assertFalse(signedOut)

        compose.onNodeWithText("Vazgeç").performClick()
        assertFalse(signedOut)

        compose.onNodeWithText("Çıkış Yap").performScrollTo().performClick()
        compose.onAllNodesWithText("Çıkış Yap")[1].performClick()
        assertTrue(signedOut)
    }

    @Test
    fun pending_sign_out_warns_and_routes_to_data_management() {
        var openedDataManagement = false
        var signedOut = false
        compose.setContent {
            TestProfile(
                syncStatus = SyncStatusUiState.Initial.copy(
                    connectionState = SyncConnectionUiState.ONLINE,
                    pendingCount = 3,
                ),
                onDataManagement = { openedDataManagement = true },
                onSignOut = { signedOut = true },
            )
        }

        compose.onNodeWithText("Çıkış Yap").performScrollTo().performClick()
        compose.onNodeWithText("3 değişiklik henüz eşitlenmedi", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eşitlemeye dön").performClick()

        assertTrue(openedDataManagement)
        assertFalse(signedOut)
    }

    @Test
    fun loading_empty_and_error_states_do_not_show_fake_identity() {
        var state by mutableStateOf(0)
        var retried = false
        compose.setContent {
            when (state) {
                0 -> TestProfile(isLoading = true)
                1 -> TestProfile(isEmpty = true)
                else -> TestProfile(
                    isEmpty = true,
                    profileError = "Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.",
                    onRetry = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Profiliniz yükleniyor").assertIsDisplayed()
        compose.onAllNodesWithText("Feniqo kullanıcısı").assertCountEquals(0)

        state = 1
        compose.onNodeWithText("Profil bilgisi bulunamadı").assertIsDisplayed()

        state = 2
        compose.onNodeWithText("Profil bilgileri yüklenemedi").assertIsDisplayed()
        compose.onNodeWithText("Tekrar Dene").performClick()
        assertTrue(retried)
    }

    @Test
    fun sync_insight_displays_all_seven_severity_types() {
        var currentSyncState by mutableStateOf<SyncStatusUiState>(SyncStatusUiState.Initial.copy(conflictCount = 2))
        compose.setContent { TestProfile(syncStatus = currentSyncState) }

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
}
