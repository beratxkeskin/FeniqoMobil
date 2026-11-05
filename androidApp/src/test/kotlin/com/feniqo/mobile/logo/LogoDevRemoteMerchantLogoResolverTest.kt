package com.feniqo.mobile.logo

import com.feniqo.mobile.data.remote.logo.LogoDevImageFormat
import com.feniqo.mobile.data.remote.logo.LogoDevLogoConfig
import com.feniqo.mobile.data.remote.logo.MerchantLogoDomainAllowlist
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoCachePolicy
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

            // 3. Fallback, Size ve Format parametreleri
            assertTrue("fallback=404 parametresi bulunmalıdır: ${request.url}", request.url.contains("fallback=404"))
            assertTrue("size parametresi 128 olmalıdır: ${request.url}", request.url.contains("size=128"))
            assertTrue("format parametresi png olmalıdır: ${request.url}", request.url.contains("format=png"))

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
                "istanbulkart",
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
        assertTrue("Safe fallback korunmalıdır: $str", str.contains("fallback=404"))
        assertTrue("Cache policy görünmelidir: $str", str.contains("cachePolicy=PROVIDER_HEADERS_NO_LOCAL_STORAGE"))
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
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
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
                    "?token=pk_test_sample_valid_key_12345&size=128&format=png&fallback=404"
            assertEquals(expectedUrl, request.url)
            assertEquals(RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE, request.cachePolicy)
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
}
