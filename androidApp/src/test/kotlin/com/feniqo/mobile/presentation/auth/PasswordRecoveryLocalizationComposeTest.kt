package com.feniqo.mobile.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.ForgotPasswordScreen
import com.feniqo.mobile.presentation.screen.PasswordResetSentScreen
import com.feniqo.mobile.presentation.screen.PasswordResetSuccessScreen
import com.feniqo.mobile.presentation.screen.ResetPasswordScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordRecoveryLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forgot_password_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                ForgotPasswordScreen(
                    email = "",
                    onEmailChange = {},
                    onSubmit = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("Parolanı yenileyelim").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Reset your password").assertIsDisplayed()
        composeRule.onNodeWithText("Send reset link").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun remaining_password_recovery_surfaces_use_english_catalog() {
        var surface by mutableStateOf(0)

        composeRule.setContent {
            App(languageTag = "en") {
                when (surface) {
                    0 ->
                        PasswordResetSentScreen(
                            email = "user@example.com",
                            onNavigateToLogin = {},
                            onEditEmail = {},
                            onResend = {},
                        )
                    1 ->
                        ResetPasswordScreen(
                            password = "",
                            confirmPassword = "",
                            onPasswordChange = {},
                            onConfirmPasswordChange = {},
                            onPasswordVisibilityToggle = {},
                            onConfirmPasswordVisibilityToggle = {},
                            isInvalidOrExpiredLink = true,
                            onSubmit = {},
                            onRequestNewLink = {},
                            onNavigateToLogin = {},
                        )
                    else -> PasswordResetSuccessScreen(onNavigateToLogin = {})
                }
            }
        }

        composeRule.onNodeWithText("Check your email").assertIsDisplayed()
        composeRule.runOnIdle { surface = 1 }
        composeRule.onNodeWithText("Request new link").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { surface = 2 }
        composeRule.onNodeWithText("Password updated").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
    }
}
