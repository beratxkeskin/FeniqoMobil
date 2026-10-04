package com.feniqo.mobile.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.AuthEmailVerificationScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmailVerificationLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun email_verification_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                AuthEmailVerificationScreen(
                    email = "user@example.com",
                    onResend = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("E-postanı doğrula.").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Verify your email.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to sign in").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Resend").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun required_on_login_state_uses_english_catalog() {
        composeRule.setContent {
            App(languageTag = "en") {
                AuthEmailVerificationScreen(
                    email = "user@example.com",
                    isRequiredOnLogin = true,
                    onResend = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("Verify your email to continue").assertIsDisplayed()
        composeRule.onNodeWithText("Resend").performScrollTo().assertIsDisplayed()
    }
}
