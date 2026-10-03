package com.feniqo.mobile.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feniqo.mobile.presentation.screen.HelpCenterScreen
import com.feniqo.mobile.presentation.screen.LegalInfoScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
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
    fun help_center_articles_showPublishedStatusAndOpenArticle() {
        var clickedArticle: String? = null
        compose.setContent {
            FeniqoTheme {
                HelpCenterScreen(
                    onBack = {},
                    onArticleClick = { clickedArticle = it },
                    onNavigateToFeedback = {},
                )
            }
        }

        val publishedNodes = compose.onAllNodesWithText("Yayında")
        assert(publishedNodes.fetchSemanticsNodes().isNotEmpty()) {
            "Published help center articles must expose their current status."
        }

        // Bir makaleye tıklanabilmeli
        compose.onNodeWithText("Hesap verilerim güvende mi?").assertIsDisplayed().performClick()
        assertEquals("Hesap verilerim güvende mi?", clickedArticle)
    }

    @Test
    fun legal_info_preservesPendingApprovalAndShowsPublishedLicenses() {
        compose.setContent {
            FeniqoTheme {
                LegalInfoScreen(
                    onBack = {},
                )
            }
        }

        // Hukuki belgelerde "Onaylı metin bekleniyor" korunmalı
        val pendingNodes = compose.onAllNodesWithText("Onaylı metin bekleniyor")
        assertEquals(2, pendingNodes.fetchSemanticsNodes().size)

        compose.onNodeWithText("Görüntüle").assertIsDisplayed().performClick()
        compose.onNodeWithText("Kotlin — Apache License 2.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Supabase Kotlin — MIT License", substring = true).performScrollTo().assertIsDisplayed()
    }
}
