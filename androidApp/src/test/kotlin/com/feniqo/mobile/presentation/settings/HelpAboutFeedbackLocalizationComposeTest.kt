package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.localization.resolveFeedbackShareSubject
import com.feniqo.mobile.presentation.screen.FeedbackScreen
import com.feniqo.mobile.presentation.screen.HelpAboutScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h2400dp")
class HelpAboutFeedbackLocalizationComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun helpAboutScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                HelpAboutScreen(
                    appVersion = "1.2.3",
                    onBack = {},
                    onNavigateToFeedback = {},
                    onOpenPrivacyPolicy = {},
                    onOpenTermsOfService = {},
                    onOpenOpenSourceLicenses = {},
                    onOpenHelpCenter = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("Yardım ve hakkında").assertIsDisplayed()
        compose.onNodeWithText("Daha bilinçli bir finansal yaşam.").assertIsDisplayed()
        compose.onNodeWithText("DESTEK").assertIsDisplayed()
        compose.onNodeWithText("HUKUKİ").assertIsDisplayed()
        compose.onNodeWithText("Sürüm 1.2.3").assertIsDisplayed()

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Help and about").assertIsDisplayed()
        compose.onNodeWithText("A more conscious financial life.").assertIsDisplayed()
        compose.onNodeWithText("SUPPORT").assertIsDisplayed()
        compose.onNodeWithText("LEGAL").assertIsDisplayed()
        compose.onNodeWithText("Version 1.2.3").assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                FeedbackScreen(
                    initialIsBug = true,
                    onBack = {},
                    onShareFeedback = { _, _, _ -> },
                    onPickAttachment = {},
                    hasAttachment = false,
                    attachmentName = null,
                    onRemoveAttachment = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("Hata bildir").assertIsDisplayed()
        compose.onNodeWithText("Öneri").assertIsDisplayed()
        compose.onNodeWithText("Hata").assertIsDisplayed()
        compose.onNodeWithText("Konu").assertIsDisplayed()
        compose.onNodeWithText("Mesaj").assertIsDisplayed()
        compose.onNodeWithText("0/1000").assertIsDisplayed()
        compose.onNodeWithText("Ekran görüntüsü (isteğe bağlı)").assertIsDisplayed()
        compose.onNodeWithText("Parola ve finansal ayrıntı paylaşma").assertIsDisplayed()
        compose.onNodeWithText("Geri bildirimi paylaş").assertIsDisplayed()

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Report a bug").assertIsDisplayed()
        compose.onNodeWithText("Suggestion").assertIsDisplayed()
        compose.onNodeWithText("Bug").assertIsDisplayed()
        compose.onNodeWithText("Subject").assertIsDisplayed()
        compose.onNodeWithText("Message").assertIsDisplayed()
        compose.onNodeWithText("0/1000").assertIsDisplayed()
        compose.onNodeWithText("Screenshot (optional)").assertIsDisplayed()
        compose.onNodeWithText("Do not share passwords or financial details").assertIsDisplayed()
        compose.onNodeWithText("Share feedback").assertIsDisplayed()
    }

    @Test
    fun resolveFeedbackShareSubject_formatsCorrectlyForBugAndSuggestion() {
        val bugTemplateTr = "[Feniqo Geri Bildirim] Hata: %s"
        val suggestionTemplateTr = "[Feniqo Geri Bildirim] Öneri: %s"

        val resolvedBug =
            resolveFeedbackShareSubject(
                isBug = true,
                subject = "Senkronizasyon gecikmesi",
                bugFormat = bugTemplateTr,
                suggestionFormat = suggestionTemplateTr,
            )
        assertEquals("[Feniqo Geri Bildirim] Hata: Senkronizasyon gecikmesi", resolvedBug)

        val resolvedSuggestion =
            resolveFeedbackShareSubject(
                isBug = false,
                subject = "Koyu tema eklensin",
                bugFormat = bugTemplateTr,
                suggestionFormat = suggestionTemplateTr,
            )
        assertEquals("[Feniqo Geri Bildirim] Öneri: Koyu tema eklensin", resolvedSuggestion)
    }
}
