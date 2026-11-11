package com.feniqo.mobile.logo

import com.feniqo.mobile.data.remote.logo.LogoDevImageFormat
import com.feniqo.mobile.data.remote.logo.LogoDevLogoConfig
import com.feniqo.mobile.data.remote.logo.MerchantLogoDomainAllowlist
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoCachePolicy
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoContentScalePolicy
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoSurfacePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoDevRemoteMerchantLogoResolverTest {
    private val validSyntheticConfig =
        LogoDevLogoConfig(
            publishableKey = "pk_test_sample_valid_key_12345",
            iconSizePx = 128,
            format = LogoDevImageFormat.PNG,
        )

    @Test
    fun validConfigAndVerifiedKeys_produceHttpsLogoDevUrlWithExpectedContract() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val verifiedKeys = MerchantLogoDomainAllowlist.allowlistKeys().sorted()

        for (key in verifiedKeys) {
            val expectedDomain = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertNotNull(expectedDomain)

            val request = resolver.resolve(key)
            assertNotNull("Doğrulanmış key '$key' için request üretilmeliydi", request)
            requireNotNull(request)

            // 1. Şema ve Host
            assertTrue("Şema HTTPS olmalıdır: ${request.url}", request.url.startsWith("https://img.logo.dev/"))

            // 2. Allowlist domain path'i
            assertTrue("Domain path allowlist'ten gelmelidir: ${request.url}", request.url.contains("/$expectedDomain?"))

            // 3. Fallback, Size, Format ve Theme parametreleri
            assertTrue("fallback=404 parametresi bulunmalıdır: ${request.url}", request.url.contains("fallback=404"))
            assertTrue("size parametresi 128 olmalıdır: ${request.url}", request.url.contains("size=128"))
            assertTrue("format parametresi png olmalıdır: ${request.url}", request.url.contains("format=png"))
            if (key == "netflix") {
                assertTrue("Netflix için theme=dark parametresi bulunmalıdır: ${request.url}", request.url.contains("theme=dark"))
                assertFalse("Netflix için theme=light parametresi bulunmamalıdır: ${request.url}", request.url.contains("theme=light"))
            } else {
                assertTrue("Diğer key'ler için theme=light parametresi bulunmalıdır: ${request.url}", request.url.contains("theme=light"))
            }

            // 4. Token parametresi URL içinde mevcut
            val expectedTokenQuery = "token=pk_test_sample_valid_key_12345"
            assertTrue(
                "Token query parametresi mevcut olmalıdır: ${request.url}",
                request.url.contains(expectedTokenQuery),
            )

            // 5. Cache Policy kesin olarak PROVIDER_HEADERS_NO_LOCAL_STORAGE
            assertEquals(
                "Cache policy PROVIDER_HEADERS_NO_LOCAL_STORAGE olmalıdır",
                RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                request.cachePolicy,
            )

            // 6. Surface Policy ve ContentScale Policy
            if (key == "netflix") {
                assertEquals(
                    "Netflix surface policy DARK_NEUTRAL olmalıdır",
                    RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL,
                    request.surfacePolicy,
                )
                assertEquals(
                    "Netflix contentScalePolicy APP_ICON_EMPHASIZED olmalıdır",
                    RemoteMerchantLogoContentScalePolicy.APP_ICON_EMPHASIZED,
                    request.contentScalePolicy,
                )
            } else {
                assertEquals(
                    "Diğer key'ler için surface policy LIGHT_NEUTRAL olmalıdır: $key",
                    RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL,
                    request.surfacePolicy,
                )
                assertEquals(
                    "Diğer key'ler için contentScalePolicy DEFAULT olmalıdır: $key",
                    RemoteMerchantLogoContentScalePolicy.DEFAULT,
                    request.contentScalePolicy,
                )
            }
        }
    }

    @Test
    fun requestHeaders_containNoFakeRefererAuthorizationOrSecretHeader() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val request = resolver.resolve("migros")
        assertNotNull(request)
        requireNotNull(request)

        // Logo.dev doğrudan CDN çağrısında referer zorunluluğu yoktur; sahte referer veya gizli auth üretilmez
        assertFalse("Sahte Referer başlığı üretilmemelidir", request.headers.containsKey("Referer"))
        assertFalse("Authorization başlığı üretilmemelidir", request.headers.containsKey("Authorization"))
        assertTrue("Headers boş veya minimal olmalıdır", request.headers.isEmpty())
    }

    @Test
    fun missingConfig_returnsNullAndZeroRemoteRequest() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = null)
        assertNull(resolver.resolve("migros"))
        assertNull(resolver.resolve("bim"))
    }

    @Test
    fun nullOrBlankKey_returnsNullAndZeroRemoteRequest() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        assertNull(resolver.resolve(null))
        assertNull(resolver.resolve(""))
        assertNull(resolver.resolve("   "))
        assertNull(resolver.resolve("\t"))
    }

    @Test
    fun unknownOrCustomMerchantKey_returnsNullAndDerivesNoUrl() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val unknownKeys =
            listOf(
                "unknown_brand_123",
                "trendyol_yemek",
                "getiryemek",
                "migros_yemek",
                "yemeksepeti_market",
                "turk_telekom",
                "kamil_koc",
                "budo",
                "getir_arac",
                "tiktak",
                "tatilbudur",
                "migros_sanal_market",
                "migros_jet",
                "https://migros.com.tr",
                "migros.com.tr",
                "kullanici_ozel_market",
                "My Coffee Shop",
            )

        for (unknown in unknownKeys) {
            val result = resolver.resolve(unknown)
            assertNull("Bilinmeyen veya serbest metin merchant key için request üretilmemelidir: $unknown", result)
        }
    }

    @Test
    fun secretKeyPrefix_returnsNullWithoutThrowingException() {
        val secretConfig =
            LogoDevLogoConfig(
                publishableKey = "sk_live_very_secret_key_9999",
            )
        val resolver = LogoDevRemoteMerchantLogoResolver(config = secretConfig)
        val result = resolver.resolve("migros")
        assertNull("Secret key fail-closed null dönmelidir", result)
    }

    @Test
    fun invalidOrWhitespaceContainingKey_returnsNull() {
        val invalidKeys =
            listOf(
                " pk_test_valid",
                "pk_test_valid ",
                "pk_test sample",
                "pk_test\tsample",
                "pk_test\nsample",
                "pk_",
                "invalid_prefix_123",
                "pk_test?param=1",
                "pk_test/key",
            )

        for (invalidKey in invalidKeys) {
            val badConfig = LogoDevLogoConfig(publishableKey = invalidKey)
            val resolver = LogoDevRemoteMerchantLogoResolver(config = badConfig)
            assertNull("Geçersiz key '$invalidKey' null request dönmelidir", resolver.resolve("migros"))
        }
    }

    @Test
    fun requestToString_masksSyntheticTokenAndPreservesSafeParts() {
        val sensitiveToken = "pk_test_sensitive_secret_value_8877"
        val customConfig =
            LogoDevLogoConfig(
                publishableKey = sensitiveToken,
                iconSizePx = 64,
                format = LogoDevImageFormat.WEBP,
            )
        val resolver = LogoDevRemoteMerchantLogoResolver(config = customConfig)
        val request = resolver.resolve("bim")
        assertNotNull(request)
        requireNotNull(request)

        val str = request.toString()

        // 1. Sentetik token değeri toString'e sızamaz
        assertFalse("Sentetik token toString()'e sızamaz: $str", str.contains(sensitiveToken))
        assertFalse("Token parçaları toString()'e sızamaz: $str", str.contains("sensitive_secret_value"))
        assertTrue("token=*** olarak maskelenmelidir: $str", str.contains("token=***"))

        // 2. Güvenli teknik parametreler korunur
        assertTrue("Safe host korunmalıdır: $str", str.contains("img.logo.dev"))
        assertTrue("Safe path korunmalıdır: $str", str.contains("/bim.com.tr"))
        assertTrue("Safe size korunmalıdır: $str", str.contains("size=64"))
        assertTrue("Safe format korunmalıdır: $str", str.contains("format=webp"))
        assertTrue("Safe theme korunmalıdır: $str", str.contains("theme=light"))
        assertTrue("Safe fallback korunmalıdır: $str", str.contains("fallback=404"))
        assertTrue("Cache policy görünmelidir: $str", str.contains("cachePolicy=PROVIDER_HEADERS_NO_LOCAL_STORAGE"))
        assertTrue("Surface policy görünmelidir: $str", str.contains("surfacePolicy=LIGHT_NEUTRAL"))
        assertTrue("Content scale policy görünmelidir: $str", str.contains("contentScalePolicy=DEFAULT"))
    }

    @Test
    fun resolver_isDeterministicForSameKeyAndConfig() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val req1 = resolver.resolve("sok")
        val req2 = resolver.resolve("sok")

        assertNotNull(req1)
        assertNotNull(req2)
        assertEquals(req1, req2)
        assertEquals(req1?.url, req2?.url)
        assertEquals(req1?.cachePolicy, req2?.cachePolicy)
    }

    @Test
    fun brandfetchResolver_preservesDefaultStrictNoStoreCachePolicy() {
        val brandfetchResolver =
            BrandfetchRemoteMerchantLogoResolver(
                config =
                    com.feniqo.mobile.data.remote.logo.BrandfetchLogoConfig(
                        clientId = "client_sample",
                        referer = "https://app.feniqo.com",
                        userAgent = "FeniqoMobil/1.0",
                    ),
            )
        val request = brandfetchResolver.resolve("migros")
        assertNotNull(request)
        requireNotNull(request)

        assertEquals(
            "Brandfetch varsayılan cache policy STRICT_NO_STORE olarak kalmalıdır",
            RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            request.cachePolicy,
        )
    }

    @Test
    fun marketAndFastDeliveryMerchants_resolveToExpectedDomainsAndLogoDevUrls() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)

        val approvedMarkets =
            mapOf(
                "macrocenter" to "macrocenter.com.tr",
                "file_market" to "file.com.tr",
                "tarim_kredi" to "tkkoop.com.tr",
                "getir" to "getir.com",
                "bizim_toptan" to "bizimtoptan.com.tr",
                "metro_turkiye" to "metro-tr.com",
            )

        for ((key, expectedDomain) in approvedMarkets) {
            val request = resolver.resolve(key)
            assertNotNull("Approved market key '$key' için request null olamaz", request)
            requireNotNull(request)

            val expectedUrl =
                "https://img.logo.dev/$expectedDomain" +
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&theme=light&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
            assertEquals(RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL, request.surfacePolicy)
            assertTrue("Header sahte bilgi içermez", request.headers.isEmpty())
        }

        // Hizmet varyantları ve fallback'te kalanlar
        assertNull(
            "migros_sanal_market fallback'te kalmalı",
            resolver.resolve("migros_sanal_market"),
        )
        assertNull(
            "yemeksepeti_market fallback'te kalmalı",
            resolver.resolve("yemeksepeti_market"),
        )
    }

    @Test
    fun foodAndDiningMerchants_resolveToExpectedDomainsAndLogoDevUrls() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)

        val approvedFoodAndDining =
            mapOf(
                "starbucks" to "starbucks.com",
                "mcdonalds" to "mcdonalds.com.tr",
                "burger_king" to "burgerking.com.tr",
                "kfc" to "kfcturkiye.com",
                "popeyes" to "popeyes.com.tr",
                "dominos" to "dominos.com.tr",
                "little_caesars" to "littlecaesars.com.tr",
                "pizza_lazza" to "pizzalazza.com.tr",
                "tavuk_dunyasi" to "tavukdunyasi.com",
                "baydoner" to "baydoner.com",
                "kofteci_yusuf" to "kofteciyusuf.com",
                "kahve_dunyasi" to "kahvedunyasi.com",
                "mado" to "mado.com.tr",
                "espressolab" to "espressolab.com",
                "simit_sarayi" to "simitsarayi.com",
                "bigchefs" to "bigchefs.com.tr",
            )

        for ((key, expectedDomain) in approvedFoodAndDining) {
            val request = resolver.resolve(key)
            assertNotNull("Approved food key '$key' için request null olamaz", request)
            requireNotNull(request)

            val expectedUrl =
                "https://img.logo.dev/$expectedDomain" +
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&theme=light&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
            assertEquals(RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL, request.surfacePolicy)
            assertTrue("Header sahte bilgi içermez", request.headers.isEmpty())
        }

        // Hizmet varyantları fallback'te kalmalıdır
        assertNull(
            "trendyol_yemek fallback'te kalmalı",
            resolver.resolve("trendyol_yemek"),
        )
        assertNull(
            "getiryemek fallback'te kalmalı",
            resolver.resolve("getiryemek"),
        )
        assertNull(
            "migros_yemek fallback'te kalmalı",
            resolver.resolve("migros_yemek"),
        )
    }

    @Test
    fun logoDevRequest_carriesExpectedSurfaceAndScalePolicy_forAllVerifiedKeys() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val verifiedKeys = MerchantLogoDomainAllowlist.allowlistKeys()

        for (key in verifiedKeys) {
            val request = resolver.resolve(key)
            assertNotNull("Doğrulanmış key '$key' için request üretilmeliydi", request)
            requireNotNull(request)

            if (key == "netflix") {
                assertEquals(
                    "Netflix isteği koyu nötr yüzey politikası taşımalıdır",
                    RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL,
                    request.surfacePolicy,
                )
                assertEquals(
                    "Netflix isteği vurgulu ölçek politikası taşımalıdır",
                    RemoteMerchantLogoContentScalePolicy.APP_ICON_EMPHASIZED,
                    request.contentScalePolicy,
                )
            } else {
                assertEquals(
                    "Logo.dev isteği açık nötr yüzey politikası taşımalıdır: $key",
                    RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL,
                    request.surfacePolicy,
                )
                assertEquals(
                    "Logo.dev isteği varsayılan ölçek politikası taşımalıdır: $key",
                    RemoteMerchantLogoContentScalePolicy.DEFAULT,
                    request.contentScalePolicy,
                )
            }
            assertEquals(
                "Logo.dev önbellek politikası PROVIDER_HEADERS_NO_LOCAL_STORAGE kalmalıdır: $key",
                RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                request.cachePolicy,
            )
        }
    }

    @Test
    fun netflix_resolvesWithThemeDark_darkNeutralSurface_andEmphasizedScale() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)
        val request = resolver.resolve("netflix")
        assertNotNull("netflix için request üretilmelidir", request)
        requireNotNull(request)

        assertTrue("netflix.com içermelidir: ${request.url}", request.url.contains("/netflix.com?"))
        assertTrue("theme=dark içermelidir: ${request.url}", request.url.contains("theme=dark"))
        assertFalse("theme=light içermemelidir: ${request.url}", request.url.contains("theme=light"))
        assertEquals(RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL, request.surfacePolicy)
        assertEquals(RemoteMerchantLogoContentScalePolicy.APP_ICON_EMPHASIZED, request.contentScalePolicy)
        assertEquals(1.8f, request.contentScalePolicy.scaleFactor, 0.001f)
        assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
        assertTrue("Headers boş kalmalıdır", request.headers.isEmpty())

        // Bilinmeyen türevler veya serbest metin fail-closed null dönmelidir
        assertNull("Bilinmeyen 'netflix_turkiye' URL'ye dönüştürülemez", resolver.resolve("netflix_turkiye"))
        assertNull("Bilinmeyen 'netflix_premium' URL'ye dönüştürülemez", resolver.resolve("netflix_premium"))
    }

    @Test
    fun roofBrandSubscriptions_resolveThroughVerifiedLogoKeys_notRawMerchantIds() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)

        // 1. Yeni sekiz çatı logo key'inin doğru kanonik domaine çözümlendiğini doğrula
        val newEightRoofKeys =
            mapOf(
                "youtube" to "youtube.com",
                "google" to "google.com",
                "microsoft" to "microsoft.com",
                "playstation" to "playstation.com",
                "xbox" to "xbox.com",
                "linkedin" to "linkedin.com",
                "nintendo" to "nintendo.com",
                "ea" to "ea.com",
            )
        for ((key, expectedDomain) in newEightRoofKeys) {
            val request = resolver.resolve(key)
            assertNotNull("Çatı key '$key' için request üretilmelidir", request)
            requireNotNull(request)
            assertTrue("Domain $expectedDomain olmalıdır: ${request.url}", request.url.contains("/$expectedDomain?"))
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
            assertEquals(RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL, request.surfacePolicy)
            assertTrue("Headers boş kalmalıdır", request.headers.isEmpty())
        }

        // 2. En az bir mevcut-key paylaşımı: amazon_prime -> verifiedLogoKey amazon -> amazon.com.tr
        val amazonPrimeMerchant = StarterMerchantCatalog.merchants.first { it.id == "amazon_prime" }
        assertEquals("amazon", amazonPrimeMerchant.verifiedLogoKey)
        val amazonRequest = resolver.resolve(amazonPrimeMerchant.verifiedLogoKey)
        assertNotNull(amazonRequest)
        requireNotNull(amazonRequest)
        assertTrue(amazonRequest.url.contains("/amazon.com.tr?"))

        // 3. En az bir yeni-key paylaşımı: youtube_premium -> verifiedLogoKey youtube -> youtube.com
        val ytMerchant = StarterMerchantCatalog.merchants.first { it.id == "youtube_premium" }
        assertEquals("youtube", ytMerchant.verifiedLogoKey)
        val ytRequest = resolver.resolve(ytMerchant.verifiedLogoKey)
        assertNotNull(ytRequest)
        requireNotNull(ytRequest)
        assertTrue(ytRequest.url.contains("/youtube.com?"))

        // 4. Ham merchant ID veya displayName URL'ye dönüştürülemez (fail-closed null döner)
        assertNull("Ham merchantId 'amazon_prime' URL'ye dönüştürülemez", resolver.resolve("amazon_prime"))
        assertNull("Ham merchantId 'youtube_premium' URL'ye dönüştürülemez", resolver.resolve("youtube_premium"))
        assertNull("DisplayName 'Amazon Prime' URL'ye dönüştürülemez", resolver.resolve("Amazon Prime"))
        assertNull("DisplayName 'YouTube Premium' URL'ye dönüştürülemez", resolver.resolve("YouTube Premium"))
    }

    @Test
    fun shoppingTwentyThreeMerchants_resolveToExpectedCanonicalDomainsAndLogoDevUrls() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)

        val approvedShopping =
            mapOf(
                "n11" to "n11.com",
                "ciceksepeti" to "ciceksepeti.com",
                "pazarama" to "pazarama.com",
                "boyner" to "boyner.com.tr",
                "lc_waikiki" to "lcwaikiki.bg",
                "defacto" to "defacto.com.tr",
                "teknosa" to "teknosa.com",
                "mediamarkt" to "mediamarkt.com.tr",
                "flo" to "flo.com.tr",
                "koton" to "koton.com",
                "mavi" to "mavi.com",
                "zara" to "zara.com",
                "hm" to "hm.com",
                "pull_and_bear" to "pullandbear.com",
                "bershka" to "bershka.com",
                "stradivarius" to "stradivarius.com",
                "beymen" to "beymen.com",
                "vakko" to "vakko.com",
                "ikea" to "ikea.com.tr",
                "koctas" to "koctas.com.tr",
                "bauhaus" to "bauhaus.com.tr",
                "vatan_bilgisayar" to "vatanbilgisayar.com",
                "ebebek" to "e-bebek.com",
            )

        for ((key, expectedDomain) in approvedShopping) {
            val request = resolver.resolve(key)
            assertNotNull("Approved shopping key '$key' için request null olamaz", request)
            requireNotNull(request)

            val expectedUrl =
                "https://img.logo.dev/$expectedDomain" +
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&theme=light&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
            assertEquals(RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL, request.surfacePolicy)
            assertEquals(RemoteMerchantLogoContentScalePolicy.DEFAULT, request.contentScalePolicy)
            assertTrue("Header sahte bilgi içermez", request.headers.isEmpty())
        }

        // Benzer fakat yanlış/yönlenen domainlerin veya serbest/hatalı anahtarların fail-closed null dönmesi
        val rejectedShoppingDomains =
            listOf(
                "boyner.com",
                "mediamarkt.com",
                "ikea.com",
                "lcw.com",
                "ebebek.com",
                "boyner_online",
                "mediamarkt_tr",
                "ikea_turkiye",
                "https://boyner.com.tr",
            )
        for (rejected in rejectedShoppingDomains) {
            assertNull("Yanlış domain veya raw string fail-closed null dönmelidir: $rejected", resolver.resolve(rejected))
        }
    }

    @Test
    fun personalCareFifteenMerchants_resolveToExpectedCanonicalDomainsAndLogoDevUrls() {
        val resolver = LogoDevRemoteMerchantLogoResolver(config = validSyntheticConfig)

        val approvedPersonalCare =
            mapOf(
                "gratis" to "gratis.com",
                "watsons" to "watsons.com.tr",
                "rossmann" to "rossmann.com.tr",
                "sephora" to "sephora.com.tr",
                "eve_shop" to "eveshop.com.tr",
                "flormar" to "flormar.com.tr",
                "golden_rose" to "goldenrose.com",
                "yves_rocher" to "yvesrocher.com.tr",
                "the_body_shop" to "thebodyshop.com.tr",
                "kiko_milano" to "kikomilano.com.tr",
                "mac_cosmetics" to "maccosmetics.com",
                "clinique" to "clinique.com.tr",
                "avon" to "avon.com.tr",
                "oriflame" to "oriflame.com.tr",
                "farmasi" to "farmasi.com.tr",
            )

        for ((key, expectedDomain) in approvedPersonalCare) {
            val request = resolver.resolve(key)
            assertNotNull("Approved personal care key '$key' için request null olamaz", request)
            requireNotNull(request)

            val expectedUrl =
                "https://img.logo.dev/$expectedDomain" +
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&theme=light&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
            assertEquals(RemoteMerchantLogoSurfacePolicy.LIGHT_NEUTRAL, request.surfacePolicy)
            assertEquals(RemoteMerchantLogoContentScalePolicy.DEFAULT, request.contentScalePolicy)
            assertTrue("Header sahte bilgi içermez", request.headers.isEmpty())
        }

        // Seçilmemiş veya istenmeyen logo varyantı domainlerinin fail-closed null dönmesi
        val unselectedPersonalCareDomains =
            listOf(
                "eve.com",
                "maccosmetics.com.tr",
                "goldenrose.com.tr",
                "watsons.com",
                "flormar.com",
                "thebodyshop.com",
                "kikomilano.com",
                "clinique.com",
                "avon.com",
                "oriflame.com",
                "https://gratis.com",
                "gratis_magaza",
                "sephora_turkiye",
            )
        for (unselected in unselectedPersonalCareDomains) {
            assertNull(
                "Seçilmemiş domain veya raw string fail-closed null dönmelidir: $unselected",
                resolver.resolve(unselected),
            )
        }
    }
}
