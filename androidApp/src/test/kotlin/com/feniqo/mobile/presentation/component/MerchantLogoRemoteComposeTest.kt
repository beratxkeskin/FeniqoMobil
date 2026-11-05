package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h2400dp")
class MerchantLogoRemoteComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeRemoteMerchantLogoRenderer(
        var state: RenderState = RenderState.Success,
    ) : RemoteMerchantLogoRenderer {
        enum class RenderState { Success, Loading, Error }

        var renderCount = 0
        var renderedRequest: RemoteMerchantLogoRequest? = null

        @Composable
        override fun render(
            request: RemoteMerchantLogoRequest,
            modifier: Modifier,
            fallbackContent: @Composable () -> Unit,
        ) {
            renderCount++
            renderedRequest = request
            when (state) {
                RenderState.Success -> {
                    Box(modifier = modifier.testTag("fake_remote_logo_image"))
                }
                RenderState.Loading,
                RenderState.Error,
                -> {
                    fallbackContent()
                }
            }
        }
    }

    private class TrackingRemoteMerchantLogoResolver(
        private val sampleRequest: RemoteMerchantLogoRequest? =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/migros.com.tr/w/128/h/128/fallback/404/type/icon?c=test",
                headers = mapOf("Referer" to "https://app.feniqo.com", "User-Agent" to "FeniqoMobil/1.0"),
            ),
    ) : RemoteMerchantLogoResolver {
        var callCount = 0
        var lastKey: String? = null

        override fun resolve(verifiedLogoKey: String?): RemoteMerchantLogoRequest? {
            callCount++
            lastKey = verifiedLogoKey
            return if (verifiedLogoKey in listOf("migros", "carrefoursa", "a101", "bim", "sok")) {
                sampleRequest
            } else {
                null
            }
        }
    }

    @Test
    fun pilotKnownMerchant_validConfig_fakeSuccess_rendersRemoteLogoBranch() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer(state = FakeRemoteMerchantLogoRenderer.RenderState.Success)
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "migros",
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Migros",
                        modifier = Modifier.testTag("migros_logo"),
                    )
                }
            }
        }

        // 1. Resolver "migros" için tam 1 kez çağrıldı
        assertEquals(1, trackingResolver.callCount)
        assertEquals("migros", trackingResolver.lastKey)

        // 2. Renderer request'i aldı
        assertNotNull(fakeRenderer.renderedRequest)
        assertEquals(
            "https://cdn.brandfetch.io/domain/migros.com.tr/w/128/h/128/fallback/404/type/icon?c=test",
            fakeRenderer.renderedRequest?.url,
        )

        // 3. Fake remote logo görseli çizildi (dekoratif modda unmerged tree'de görünür)
        compose.onNodeWithTag("fake_remote_logo_image", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("migros_logo").assertIsDisplayed()
    }

    @Test
    fun pilotKnownMerchant_loadingState_showsCategoryFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer(state = FakeRemoteMerchantLogoRenderer.RenderState.Loading)
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "carrefoursa",
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Blue,
                        fallbackLabel = "CarrefourSA",
                        modifier = Modifier.testTag("carrefour_logo"),
                    )
                }
            }
        }

        // Loading sırasında remote logo henüz gelmediğinden kategori fallback'i aktiftir, crash veya boşluk yoktur
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("carrefour_logo").assertIsDisplayed()
    }

    @Test
    fun pilotKnownMerchant_errorState_withCategory_showsCategoryFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer(state = FakeRemoteMerchantLogoRenderer.RenderState.Error)
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "bim",
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Red,
                        fallbackLabel = "BİM",
                        modifier = Modifier.testTag("bim_logo"),
                    )
                }
            }
        }

        // Hata durumunda (404/offline vb.) ekran hata state'ine düşmez, kategori fallback'i görünür
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("bim_logo").assertIsDisplayed()
    }

    @Test
    fun pilotKnownMerchant_errorState_noCategory_showsInitialFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer(state = FakeRemoteMerchantLogoRenderer.RenderState.Error)
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "a101",
                        canShowMerchantLogo = true,
                        categoryIconKey = null, // kategori yok -> INITIAL dalına düşer
                        categoryColor = Color.Cyan,
                        fallbackLabel = "A101 Market",
                        modifier = Modifier.testTag("a101_logo"),
                    )
                }
            }
        }

        // Kategori yokken hata durumunda Türkçe uyumlu 'A' baş harfi görünür
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithText("A", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("a101_logo").assertIsDisplayed()
    }

    @Test
    fun pilotKnownMerchant_errorState_noCategory_noInitial_showsGenericIconFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer(state = FakeRemoteMerchantLogoRenderer.RenderState.Error)
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "sok",
                        canShowMerchantLogo = true,
                        categoryIconKey = null, // kategori yok
                        categoryColor = Color.Yellow,
                        fallbackLabel = "123", // Rakamlar harf değildir -> GENERIC_TRANSACTION_ICON
                        contentDescription = "Şok İşlemi",
                        modifier = Modifier.testTag("sok_logo"),
                    )
                }
            }
        }

        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithContentDescription("Şok İşlemi").assertIsDisplayed()
        compose.onNodeWithTag("sok_logo").assertIsDisplayed()
    }

    @Test
    fun canShowMerchantLogoFalse_doesNotCallResolver_andRendersFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "migros",
                        canShowMerchantLogo = false, // LOGO GÖSTERİLEMEZ
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Migros",
                        modifier = Modifier.testTag("suppressed_logo"),
                    )
                }
            }
        }

        // canShowMerchantLogo false iken resolver ASLA çağrılmaz
        assertEquals(0, trackingResolver.callCount)
        assertNull(fakeRenderer.renderedRequest)
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("suppressed_logo").assertIsDisplayed()
    }

    @Test
    fun verifiedLogoKeyNull_doesNotCallResolver_andRendersFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = null, // LOGO KEY YOK
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Bilinmeyen Market",
                        modifier = Modifier.testTag("no_key_logo"),
                    )
                }
            }
        }

        // Key yokken resolver çağrılmaz
        assertEquals(0, trackingResolver.callCount)
        assertNull(fakeRenderer.renderedRequest)
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("no_key_logo").assertIsDisplayed()
    }

    @Test
    fun useTransactionNamePreview_nullKey_zeroResolverCalls() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = null, // UseTransactionName durumunda logoKey boştur
                        canShowMerchantLogo = false,
                        categoryIconKey = "general",
                        categoryColor = Color.Gray,
                        fallbackLabel = "Özel Fatura",
                        modifier = Modifier.testTag("custom_tx_logo"),
                    )
                }
            }
        }

        assertEquals(0, trackingResolver.callCount)
        assertNull(fakeRenderer.renderedRequest)
        compose.onNodeWithTag("custom_tx_logo").assertIsDisplayed()
    }

    @Test
    fun providerNotConfigured_resolverNull_noCrash_rendersLocalFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides null, // PROVIDER EKSİK/YOK
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "migros",
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Migros",
                        modifier = Modifier.testTag("unconfigured_logo"),
                    )
                }
            }
        }

        // Resolver null iken uygulama çökmez, doğrudan yerel fallback çalışır
        assertNull(fakeRenderer.renderedRequest)
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("unconfigured_logo").assertIsDisplayed()
    }

    @Test
    fun forcedRecomposition_doesNotTriggerAdditionalResolverCalls_orModelCreations() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()
        var recomposeTrigger by mutableStateOf(0)

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    val trigger = recomposeTrigger
                    MerchantLogo(
                        verifiedLogoKey = "migros",
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Migros",
                        modifier = Modifier.testTag("stable_logo_$trigger"),
                    )
                }
            }
        }

        assertEquals(1, trackingResolver.callCount)
        assertEquals("migros", trackingResolver.lastKey)

        // Zorunlu Recomposition tetiklenir
        recomposeTrigger++
        compose.waitForIdle()

        // Resolver çağrı sayısı artmamalı, stabilize kalmalı
        assertEquals(1, trackingResolver.callCount)

        recomposeTrigger++
        compose.waitForIdle()

        assertEquals(1, trackingResolver.callCount)
    }

    @Test
    fun verifiedLogoKeyChange_triggersResolverOnceForNewKey() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()
        var currentKey by mutableStateOf("migros")

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = currentKey,
                        canShowMerchantLogo = true,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Market",
                        modifier = Modifier.testTag("dynamic_key_logo"),
                    )
                }
            }
        }

        assertEquals(1, trackingResolver.callCount)
        assertEquals("migros", trackingResolver.lastKey)

        // Key değiştirilir
        currentKey = "bim"
        compose.waitForIdle()

        // Resolver yeni key için tam bir kez daha çağrılır
        assertEquals(2, trackingResolver.callCount)
        assertEquals("bim", trackingResolver.lastKey)
    }

    @Test
    fun canShowMerchantLogoToggles_removesRemoteLogo_andSwitchesToFallback() {
        val fakeRenderer = FakeRemoteMerchantLogoRenderer()
        val trackingResolver = TrackingRemoteMerchantLogoResolver()
        var canShow by mutableStateOf(true)

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalRemoteMerchantLogoResolver provides trackingResolver,
                    LocalRemoteMerchantLogoRenderer provides fakeRenderer,
                ) {
                    MerchantLogo(
                        verifiedLogoKey = "migros",
                        canShowMerchantLogo = canShow,
                        categoryIconKey = "groceries",
                        categoryColor = Color.Green,
                        fallbackLabel = "Migros",
                        modifier = Modifier.testTag("toggle_logo"),
                    )
                }
            }
        }

        assertEquals(1, trackingResolver.callCount)
        compose.onNodeWithTag("fake_remote_logo_image", useUnmergedTree = true).assertIsDisplayed()

        // canShow false yapılır
        canShow = false
        compose.waitForIdle()

        // Uzak logo görseli kaldırılır, fallback görünür, yeni resolver çağrısı yapılmaz
        compose.onNodeWithTag("fake_remote_logo_image").assertDoesNotExist()
        compose.onNodeWithTag("toggle_logo").assertIsDisplayed()
        assertEquals(1, trackingResolver.callCount)
    }
}
