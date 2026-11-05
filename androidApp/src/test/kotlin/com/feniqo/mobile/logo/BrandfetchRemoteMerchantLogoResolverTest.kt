package com.feniqo.mobile.logo

import com.feniqo.mobile.data.remote.logo.BrandfetchDomainAllowlist
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandfetchRemoteMerchantLogoResolverTest {
    private val validConfig =
        BrandfetchLogoConfig(
            clientId = "client_test_xyz123",
            referer = "https://app.feniqo.com",
            userAgent = "FeniqoMobil/1.0.0 (Android 34)",
        )

    @Test
    fun configNull_returnsNullAndZeroRemoteRequest() {
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = null)
        val result = resolver.resolve("migros")
        assertNull(result)
    }

    @Test
    fun blankClientId_returnsNullAndZeroRemoteRequest() {
        val badConfig =
            BrandfetchLogoConfig(
                clientId = "   ",
                referer = "https://app.feniqo.com",
                userAgent = "FeniqoMobil/1.0.0 (Android 34)",
            )
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = badConfig)
        assertNull(resolver.resolve("migros"))
    }

    @Test
    fun blankReferer_returnsNullAndZeroRemoteRequest() {
        val badConfig =
            BrandfetchLogoConfig(
                clientId = "client_123",
                referer = "   ",
                userAgent = "FeniqoMobil/1.0.0 (Android 34)",
            )
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = badConfig)
        assertNull(resolver.resolve("migros"))
    }

    @Test
    fun invalidReferer_returnsNullAndZeroRemoteRequest() {
        val invalidReferers =
            listOf(
                "http://insecure.com",
                "https:// example.com",
                "https://example.com:abc",
                "https://example.com\\evil",
                "https://.",
                "https:///",
                "https://:443",
                "https://example.com/api",
                "https://user:pass@example.com",
                "https://example.com?query=1",
                "https://example.com#hash",
                "https://example.com:0",
                "https://example.com:65536",
            )

        for (invalidRef in invalidReferers) {
            val badConfig =
                BrandfetchLogoConfig(
                    clientId = "client_123",
                    referer = invalidRef,
                    userAgent = "FeniqoMobil/1.0.0 (Android 34)",
                )
            val resolver = BrandfetchRemoteMerchantLogoResolver(config = badConfig)
            assertNull("Referer '$invalidRef' reddedilmeliydi", resolver.resolve("migros"))
        }
    }

    @Test
    fun unknownKey_returnsNullAndZeroRemoteRequest() {
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = validConfig)
        assertNull(resolver.resolve("unknown_brand_123"))
        assertNull(resolver.resolve("trendyol_yemek"))
        assertNull(resolver.resolve("getiryemek"))
        assertNull(resolver.resolve("migros_yemek"))
        assertNull(resolver.resolve("yemeksepeti_market"))
        assertNull(resolver.resolve("migros_sanal_market"))
        assertNull(resolver.resolve("turk_telekom"))
        assertNull(resolver.resolve("istanbulkart"))
    }

    @Test
    fun nullOrBlankKey_returnsNullAndZeroRemoteRequest() {
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = validConfig)
        assertNull(resolver.resolve(null))
        assertNull(resolver.resolve(""))
        assertNull(resolver.resolve("   "))
    }

    @Test
    fun allowlistKeys_matchApprovedMerchantLogoRegistry() {
        val keys = BrandfetchDomainAllowlist.allowlistKeys()
        assertEquals(44, keys.size)
        assertTrue(keys.containsAll(approvedLogoKeys))
    }

    @Test
    fun approvedLogoKeys_produceHttpsCdnBrandfetchUrlAndExpectedHeaders() {
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = validConfig)

        for (key in approvedLogoKeys) {
            val request = resolver.resolve(key)
            assertNotNull("Pilot key '$key' için request üretilmeliydi", request)
            requireNotNull(request)

            // 1. Sabit HTTPS ve host kontrolü
            assertTrue(request.url.startsWith("https://cdn.brandfetch.io/domain/"))
            assertTrue(request.url.contains("/fallback/404/type/icon?c="))

            // 2. Query Client ID parametresi mevcut
            assertTrue(request.url.contains("c=client_test_xyz123"))

            // 3. Header kontrolleri
            assertEquals("https://app.feniqo.com", request.headers["Referer"])
            assertEquals("FeniqoMobil/1.0.0 (Android 34)", request.headers["User-Agent"])
        }
    }

    private val approvedLogoKeys =
        setOf(
            "migros",
            "carrefoursa",
            "a101",
            "bim",
            "sok",
            "netflix",
            "spotify",
            "disney_plus",
            "trendyol",
            "hepsiburada",
            "amazon",
            "yemeksepeti",
            "turkcell",
            "vodafone",
            "petrol_ofisi",
            "opet",
            "shell",
            "thy",
            "pegasus",
            "uber",
            "obilet",
            "enuygun",
            "macrocenter",
            "file_market",
            "tarim_kredi",
            "getir",
            "bizim_toptan",
            "metro_turkiye",
            "starbucks",
            "mcdonalds",
            "burger_king",
            "kfc",
            "popeyes",
            "dominos",
            "little_caesars",
            "pizza_lazza",
            "tavuk_dunyasi",
            "baydoner",
            "kofteci_yusuf",
            "kahve_dunyasi",
            "mado",
            "espressolab",
            "simit_sarayi",
            "bigchefs",
        )

    @Test
    fun clientId_isNeverLeakedInConfigToString() {
        val sensitiveClientId = "secret_client_token_9999"
        val config =
            BrandfetchLogoConfig(
                clientId = sensitiveClientId,
                referer = "https://example.com",
                userAgent = "FeniqoMobil/1.0",
            )
        val stringOutput = config.toString()
        assertFalse("Config toString Client ID sızdırmamalı", stringOutput.contains(sensitiveClientId))
        assertTrue(stringOutput.contains("***"))
    }

    @Test
    fun technicalUserAgent_formatIsStrictAndContainsNoFinancialOrUserText() {
        val userAgent =
            BrandfetchRemoteMerchantLogoResolver.buildTechnicalUserAgent(
                versionName = "1.2.3",
                sdkInt = 35,
            )
        assertEquals("FeniqoMobil/1.2.3 (Android 35)", userAgent)
        assertFalse(userAgent.contains("@"))
        assertFalse(userAgent.contains("migros"))
        assertFalse(userAgent.contains("TL"))
    }
}
