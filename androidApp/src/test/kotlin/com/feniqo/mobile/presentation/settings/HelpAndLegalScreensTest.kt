package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.screen.HelpArticleStatusScreen
import com.feniqo.mobile.presentation.screen.HelpCenterScreen
import com.feniqo.mobile.presentation.screen.LegalDocumentStatus
import com.feniqo.mobile.presentation.screen.LegalInfoScreen
import com.feniqo.mobile.presentation.screen.defaultLegalDocuments
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h2400dp")
class HelpAndLegalScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun helpCenter_reactsToRuntimeLocaleChange_andNavigatesWithStableArticleId() {
        var clickedArticleId: String? = null
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                HelpCenterScreen(
                    onBack = {},
                    onArticleClick = { clickedArticleId = it },
                    onNavigateToFeedback = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("Yardım merkezi").assertIsDisplayed()
        compose.onNodeWithText("Gizlilik ve güvenlik").assertIsDisplayed()
        val publishedNodesTr = compose.onAllNodesWithText("Yayında")
        assert(publishedNodesTr.fetchSemanticsNodes().isNotEmpty()) {
            "Published help center articles must expose their current status in Turkish."
        }

        // Makaleye tıklama stable article ID ("sec_1") döndürmeli
        compose.onNodeWithText("Hesap verilerim güvende mi?").assertIsDisplayed().performClick()
        assertEquals("sec_1", clickedArticleId)

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Help center").assertIsDisplayed()
        compose.onNodeWithText("Privacy and security").assertIsDisplayed()
        val publishedNodesEn = compose.onAllNodesWithText("Published")
        assert(publishedNodesEn.fetchSemanticsNodes().isNotEmpty()) {
            "Published help center articles must expose their current status in English."
        }
        compose.onNodeWithText("Is my account data safe?").assertIsDisplayed().performClick()
        assertEquals("sec_1", clickedArticleId)
    }

    @Test
    fun helpArticleStatusScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                HelpArticleStatusScreen(
                    articleId = "sec_1",
                    onBack = {},
                    onNavigateToFeedback = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("Hesap verilerim güvende mi?").assertIsDisplayed()
        compose.onNodeWithText("Geri dön").assertIsDisplayed()
        compose.onNodeWithText("Geri bildirim gönder").assertIsDisplayed()

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Is my account data safe?").assertIsDisplayed()
        compose.onNodeWithText("Go back").assertIsDisplayed()
        compose.onNodeWithText("Send feedback").assertIsDisplayed()
    }

    @Test
    fun legalInfo_preservesPendingApproval_andShowsPublishedLicenses_withLocaleChange() {
        assertEquals(LegalDocumentStatus.PENDING_APPROVAL, defaultLegalDocuments[0].status)
        assertEquals(LegalDocumentStatus.PENDING_APPROVAL, defaultLegalDocuments[1].status)

        var languageTag by mutableStateOf("tr")

        compose.setContent {
            App(languageTag = languageTag) {
                LegalInfoScreen(
                    onBack = {},
                )
            }
        }

        // TR assertions
        compose.onNodeWithText("Gizlilik ve hukuki bilgiler").assertIsDisplayed()
        val pendingNodesTr = compose.onAllNodesWithText("Onaylı metin bekleniyor")
        assertEquals(2, pendingNodesTr.fetchSemanticsNodes().size)

        compose.onNodeWithText("Görüntüle").assertIsDisplayed().performClick()
        compose.onNodeWithText("Kotlin — Apache License 2.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jetpack Compose ve AndroidX — Apache License 2.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Supabase Kotlin — MIT License", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tamam").assertIsDisplayed().performClick()

        // Switch to EN
        languageTag = "en"
        compose.waitForIdle()

        // EN assertions
        compose.onNodeWithText("Privacy and legal info").assertIsDisplayed()
        val pendingNodesEn = compose.onAllNodesWithText("Awaiting approved text")
        assertEquals(2, pendingNodesEn.fetchSemanticsNodes().size)

        compose.onNodeWithText("View").assertIsDisplayed().performClick()
        compose.onNodeWithText("Kotlin — Apache License 2.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jetpack Compose and AndroidX — Apache License 2.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
    }
}
