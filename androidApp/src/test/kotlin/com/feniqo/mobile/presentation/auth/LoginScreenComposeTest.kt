package com.feniqo.mobile.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import androidx.compose.ui.test.performScrollTo

@RunWith(RobolectricTestRunner::class)
class LoginScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ready_state_exposes_primary_actions_and_forwards_clicks() {
        var submitCount = 0
        var registerCount = 0
        composeRule.setContent {
            App(languageTag = "tr") {
                LoginScreen(
                    state = LoginUiState.Initial,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordVisibilityToggle = {},
                    onSubmit = { submitCount++ },
                    onNavigateToRegister = { registerCount++ },
                )
            }
        }

        composeRule.onNodeWithText("Giriş yap").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.onNodeWithText("Hesap oluştur").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

        assertEquals(1, submitCount)
        assertEquals(1, registerCount)
    }

    @Test
    fun submitting_state_disables_actions_and_exposes_progress_semantics() {
        composeRule.setContent {
            App(languageTag = "tr") {
                LoginScreen(
                    state = LoginUiState(isSubmitting = true),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordVisibilityToggle = {},
                    onSubmit = {},
                    onNavigateToRegister = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Giriş yapılıyor...").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hesap oluştur").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun login_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                LoginScreen(
                    state = LoginUiState.Initial,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordVisibilityToggle = {},
                    onSubmit = {},
                    onNavigateToRegister = {},
                )
            }
        }

        composeRule.onNodeWithText("Tekrar hoş geldin.").assertIsDisplayed()
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Welcome back.").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performScrollTo().assertIsDisplayed()
    }
}
