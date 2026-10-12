package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.presentation.screen.EmailSettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h2400dp")
class EmailSettingsLocalizationComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emailSettingsScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                EmailSettingsScreen(
                    currentEmail = "test@feniqo.com",
                    verificationStatus = EmailVerificationStatus.PENDING_VERIFICATION,
                    isSendingVerification = false,
                    verificationSentSuccess = false,
                    errorMessage = null,
                    onBack = {},
                    onResendVerification = {},
                    onNavigateToChangeEmail = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("E-posta").assertIsDisplayed()
        compose.onNodeWithText("E-posta adresi").assertIsDisplayed()
        compose.onNodeWithText("Doğrulama bekleniyor").assertIsDisplayed()
        compose.onNodeWithText("Bağlantıyı e-postandan onayladığında durum güncellenir.").assertIsDisplayed()
        compose.onNodeWithText("Doğrulama e-postasını tekrar gönder").assertIsDisplayed()
        compose.onNodeWithText("E-posta adresini değiştir").assertIsDisplayed()

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Email address").assertIsDisplayed()
        compose.onNodeWithText("Pending verification").assertIsDisplayed()
        compose.onNodeWithText("The status will update once you confirm the link in your email.").assertIsDisplayed()
        compose.onNodeWithText("Resend verification email").assertIsDisplayed()
        compose.onNodeWithText("Change email address").assertIsDisplayed()
    }
}
