package com.feniqo.mobile.presentation.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h2400dp")
class MerchantLogoComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun decorativeUse_producesNoContentDescriptionOrTextSemanticsInMergedTree() {
        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = false,
                    categoryIconKey = null, // kategori yok -> INITIAL dalına düşer
                    categoryColor = Color.Blue,
                    fallbackLabel = "istanbul kart",
                    contentDescription = null, // dekoratif kullanım
                    modifier = Modifier.testTag("decorative_logo"),
                )
            }
        }

        // 1. Görsel harf bileşeni unmerged tree'de gerçekten çizilmiş ve görünürdür (görsel kaybolmadı kanıtı)
        compose.onNodeWithText("İ", useUnmergedTree = true).assertIsDisplayed()

        // 2. Merged ağaçta ne Text ne de contentDescription bulunmamalı (çift anons veya anlamsız anons yok)
        compose.onNodeWithText("İ", useUnmergedTree = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("istanbul kart").assertDoesNotExist()
        compose.onNodeWithTag("decorative_logo").assertIsDisplayed()
    }

    @Test
    fun standaloneInitial_preservesCallerContentDescriptionWithoutDuplicateAnnouncement() {
        val label = "İstanbul Kart"
        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = false,
                    categoryIconKey = null, // INITIAL dalı
                    categoryColor = Color.Blue,
                    fallbackLabel = "istanbul kart",
                    contentDescription = label,
                    modifier = Modifier.testTag("standalone_logo"),
                )
            }
        }

        // 1. Bağımsız modda yalnızca caller'ın verdiği açıklama okunmalı
        compose.onNodeWithContentDescription(label).assertIsDisplayed()

        // 2. Görsel 'İ' harfi ekranda çizilir (unmerged tree ile kanıtlandı)
        compose.onNodeWithText("İ", useUnmergedTree = true).assertIsDisplayed()

        // 3. Ancak merged erişilebilirlik ağacında ikinci bir metin/anons olarak bulunmaz
        compose.onNodeWithText("İ", useUnmergedTree = false).assertDoesNotExist()
    }

    @Test
    fun genericFallback_composesWithoutCrashWhenNumericOnlyLabel() {
        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = false,
                    categoryIconKey = null,
                    categoryColor = Color.Gray,
                    fallbackLabel = "123", // Rakamlar harf değildir -> GENERIC_TRANSACTION_ICON'a düşer
                    contentDescription = "Genel fiş işlemi",
                    modifier = Modifier.testTag("generic_logo"),
                )
            }
        }

        compose.onNodeWithContentDescription("Genel fiş işlemi").assertIsDisplayed()
        compose.onNodeWithTag("generic_logo").assertIsDisplayed()
    }

    @Test
    fun sizeParameter_appliesExactWidthAndHeight() {
        val customSize = 48.dp
        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = false,
                    categoryIconKey = "subscriptions",
                    categoryColor = Color.Blue,
                    fallbackLabel = "Spotify",
                    size = customSize,
                    modifier = Modifier.testTag("custom_size_logo"),
                )
            }
        }

        compose.onNodeWithTag("custom_size_logo")
            .assertWidthIsEqualTo(customSize)
            .assertHeightIsEqualTo(customSize)
    }

    @Test
    fun defaultSizeParameter_applies40Dp() {
        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = false,
                    categoryIconKey = "groceries",
                    categoryColor = Color.Green,
                    fallbackLabel = "Migros",
                    modifier = Modifier.testTag("default_size_logo"),
                )
            }
        }

        compose.onNodeWithTag("default_size_logo")
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun lightAndDarkTheme_composesWithoutCrash() {
        compose.setContent {
            FeniqoTheme(darkTheme = true) {
                MerchantLogo(
                    verifiedLogoKey = null,
                    canShowMerchantLogo = true,
                    categoryIconKey = "shopping",
                    categoryColor = Color.Magenta,
                    fallbackLabel = "Trendyol",
                    size = 48.dp,
                    contentDescription = "Koyu tema Trendyol",
                )
            }
        }

        compose.onNodeWithContentDescription("Koyu tema Trendyol").assertIsDisplayed()
    }
}
