package com.feniqo.mobile.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.feniqo.mobile.presentation.screen.LoginScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
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
            FeniqoTheme {
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
            FeniqoTheme {
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
}
