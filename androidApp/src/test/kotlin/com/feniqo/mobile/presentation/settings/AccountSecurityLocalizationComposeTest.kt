package com.feniqo.mobile.presentation.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.ChangeEmailScreen
import com.feniqo.mobile.presentation.screen.ChangePasswordScreen
import com.feniqo.mobile.presentation.screen.DeleteAccountScreen
import com.feniqo.mobile.presentation.screen.EmailVerificationScreen
import com.feniqo.mobile.presentation.screen.SignOutConfirmDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountSecurityLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun change_email_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                ChangeEmailScreen(
                    currentEmail = "user@example.com",
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onSubmitNewEmail = {},
                )
            }
        }

        composeRule.onNodeWithText("E-postayı değiştir").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Change email").assertIsDisplayed()
        composeRule.onNodeWithText("Send verification").assertIsDisplayed()
    }

    @Test
    fun account_security_surfaces_use_english_catalog() {
        var surface by mutableStateOf(0)

        composeRule.setContent {
            App(languageTag = "en") {
                when (surface) {
                    0 ->
                        EmailVerificationScreen(
                            targetEmail = "user@example.com",
                            onBackToSettings = {},
                            onResendEmail = {},
                            onCorrectAddress = {},
                        )
                    1 ->
                        ChangePasswordScreen(
                            isLoading = false,
                            errorMessage = null,
                            onBack = {},
                            onSubmit = { _, _, _ -> },
                        )
                    2 ->
                        SignOutConfirmDialog(
                            pendingChangesCount = 2,
                            onDismiss = {},
                            onNavigateToSync = {},
                            onConfirmSignOut = {},
                        )
                    else ->
                        DeleteAccountScreen(
                            onBack = {},
                            onExportData = {},
                            onCheckSharedSpaces = {},
                            onInspectSync = {},
                            onContactSupport = {},
                        )
                }
            }
        }

        composeRule.onNodeWithText("Email verification").assertIsDisplayed()
        composeRule.runOnIdle { surface = 1 }
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.runOnIdle { surface = 2 }
        composeRule.onNodeWithText("Do you want to sign out?").assertIsDisplayed()
        composeRule.onNodeWithText("Return to sync").assertIsDisplayed()
        composeRule.runOnIdle { surface = 3 }
        composeRule.onNodeWithText("Delete account").assertIsDisplayed()
        composeRule.onNodeWithText("Contact support").assertIsDisplayed()
    }

    @Test
    fun typed_account_errors_use_english_catalog() {
        var showEmailError by mutableStateOf(false)

        composeRule.setContent {
            App(languageTag = "en") {
                val errorMessage = if (showEmailError) {
                    AccountSecurityUiMessage.EMAIL_INVALID.toLocalizedText()
                } else {
                    AccountSecurityUiMessage.CURRENT_PASSWORD_INVALID.toLocalizedText()
                }
                Text(errorMessage)
            }
        }

        composeRule.onNodeWithText("Your current password is incorrect.").assertIsDisplayed()
        composeRule.runOnIdle { showEmailError = true }
        composeRule.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
    }
}
