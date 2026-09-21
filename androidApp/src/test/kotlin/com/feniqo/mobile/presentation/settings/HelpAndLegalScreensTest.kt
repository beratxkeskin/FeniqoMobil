package com.feniqo.mobile.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun help_center_articles_preserve_honest_preparing_status() {
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

        // Açık olan kategorideki "Hazırlanıyor" rozeti görünmeli
        val preparingNodes = compose.onAllNodesWithText("Hazırlanıyor")
        assert(preparingNodes.fetchSemanticsNodes().isNotEmpty()) {
            "Help center articles must preserve honest 'Hazırlanıyor' status."
        }

        // Bir makaleye tıklanabilmeli
        compose.onNodeWithText("Hesap verilerim güvende mi?").assertIsDisplayed().performClick()
        assertEquals("Hesap verilerim güvende mi?", clickedArticle)
    }

    @Test
    fun legal_info_preserves_honest_preparing_and_pending_approval_badges() {
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

        // Açık kaynak lisanslar "Hazırlanıyor" rozetini korumalı
        compose.onNodeWithText("Hazırlanıyor").assertIsDisplayed()
    }
}
