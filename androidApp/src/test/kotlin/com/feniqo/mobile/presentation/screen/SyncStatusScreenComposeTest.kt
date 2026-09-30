package com.feniqo.mobile.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class SyncStatusScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val expectedQuarantineTitle = "Eski eşitleme verisi korunuyor"
    private val expectedQuarantineDescription =
        "Bazı eski eşitleme kayıtlarının hangi hesaba ait olduğu doğrulanamadı. Kayıtlar silinmedi ve otomatik olarak gönderilmeyecek."

    @Test
    fun quarantine_warning_is_visible_with_generic_safe_text() {
        compose.setContent {
            FeniqoTheme {
                SyncStatusScreen(
                    isOnline = true,
                    isSyncing = false,
                    pendingChangesCount = 0,
                    lastSyncFormatted = "Bugün 12:00",
                    conflictCount = 0,
                    hasLegacyQuarantinedData = true,
                    onBack = {},
                    onRetrySync = {},
                    onInspectConflict = {},
                )
            }
        }

        compose.onNodeWithText(expectedQuarantineTitle).assertIsDisplayed()
        compose.onNodeWithText(expectedQuarantineDescription).assertIsDisplayed()
    }

    @Test
    fun quarantine_warning_is_hidden_when_flag_is_false() {
        compose.setContent {
            FeniqoTheme {
                SyncStatusScreen(
                    isOnline = true,
                    isSyncing = false,
                    pendingChangesCount = 0,
                    lastSyncFormatted = "Bugün 12:00",
                    conflictCount = 0,
                    hasLegacyQuarantinedData = false,
                    onBack = {},
                    onRetrySync = {},
                    onInspectConflict = {},
                )
            }
        }

        compose.onNodeWithText(expectedQuarantineTitle).assertDoesNotExist()
        compose.onNodeWithText(expectedQuarantineDescription).assertDoesNotExist()
    }

    @Test
    fun quarantine_warning_coexists_with_pending_and_conflict_content() {
        compose.setContent {
            FeniqoTheme {
                SyncStatusScreen(
                    isOnline = true,
                    isSyncing = false,
                    pendingChangesCount = 3,
                    lastSyncFormatted = "Dün 18:30",
                    conflictCount = 1,
                    hasLegacyQuarantinedData = true,
                    onBack = {},
                    onRetrySync = {},
                    onInspectConflict = {},
                )
            }
        }

        // Quarantine warning must be visible
        compose.onNodeWithText(expectedQuarantineTitle).assertIsDisplayed()
        compose.onNodeWithText(expectedQuarantineDescription).assertIsDisplayed()

        // Pending changes must coexist
        compose.onNodeWithText("Bekleyen değişiklikler").assertIsDisplayed()
        compose.onNodeWithText("3 yerel değişiklik aktarılmayı bekliyor").assertIsDisplayed()

        // Conflict card must coexist
        compose.onNodeWithText("1 kayıt inceleme bekliyor.").assertIsDisplayed()
        compose.onNodeWithText("İncele").assertIsDisplayed()

        // Retry button must coexist
        compose.onNodeWithText("Yeniden dene").assertIsDisplayed()
    }

    @Test
    fun quarantine_warning_does_not_expose_count_ids_or_payload() {
        compose.setContent {
            FeniqoTheme {
                SyncStatusScreen(
                    isOnline = true,
                    isSyncing = false,
                    pendingChangesCount = 0,
                    lastSyncFormatted = "Bugün 10:00",
                    conflictCount = 0,
                    hasLegacyQuarantinedData = true,
                    onBack = {},
                    onRetrySync = {},
                    onInspectConflict = {},
                )
            }
        }

        // Must not expose internal identifiers, entity types or raw payloads
        compose.onNodeWithText("LEGACY_UNRESOLVED", substring = true).assertDoesNotExist()
        compose.onNodeWithText("operation_id", substring = true).assertDoesNotExist()
        compose.onNodeWithText("entity_id", substring = true).assertDoesNotExist()
        compose.onNodeWithText("payload", substring = true).assertDoesNotExist()
        compose.onNodeWithText("TRANSACTION", substring = true).assertDoesNotExist()
        compose.onNodeWithText("WORKSPACE", substring = true).assertDoesNotExist()

        // Must not expose quarantine count numbers (since pending=0, conflict=0, no quarantine count should appear)
        compose.onNodeWithText("karantina", substring = true, ignoreCase = true).assertDoesNotExist()

        // Must not provide any action button for quarantine (no retry, claim, recover, sync buttons for quarantine)
        compose.onNodeWithText("Sahiplen", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Kurtar", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Karantinayı Temizle", substring = true, ignoreCase = true).assertDoesNotExist()
    }
}
