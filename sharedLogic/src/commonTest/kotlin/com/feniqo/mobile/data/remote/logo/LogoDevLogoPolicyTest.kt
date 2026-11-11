package com.feniqo.mobile.data.remote.logo

import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Logo.dev Logo API sözleşmesi, allowlist doğrulaması, fail-closed config ve saf URL testleri.
 *
 * Güvenlik ve Gizlilik:
 * - Gerçek Logo.dev token kesinlikle kullanılmaz; yalnız sentetik `pk_test_...` kullanılır.
 * - Sıfır ağ çağrısı: Testler hiçbir ağ trafiği üretmez.
 * - Fail-closed: Hatalı veya eksik config durumlarında asla exception atılmaz, tipli Unavailable döner.
 */
class LogoDevLogoPolicyTest {
    private val validSyntheticConfig =
        LogoDevLogoConfig(
            publishableKey = "pk_test_synthetic_key_12345",
            iconSizePx = 128,
            format = LogoDevImageFormat.PNG,
        )

    // -------------------------------------------------------------
    // 1. Allowlist Doğrulamaları ve Tek Kaynak Sözleşmesi
    // -------------------------------------------------------------

    @Test
    fun allowlist_resolvesAllOneHundredNineVerifiedKeysCorrectly() {
        expectedAllowlist.forEach { (key, domain) ->
            assertEquals(domain, MerchantLogoDomainAllowlist.resolveDomain(key))
        }
    }

    @Test
    fun allowlist_isSingleSourceOfTruth_brandfetchAndLogoDevResolveIdenticalDomains() {
        for (key in MerchantLogoDomainAllowlist.allowlistKeys()) {
            val centralDomain = MerchantLogoDomainAllowlist.resolveDomain(key)
            val brandfetchDomain = BrandfetchDomainAllowlist.resolveDomain(key)
            assertNotNull(centralDomain)
            assertEquals(centralDomain, brandfetchDomain, "Brandfetch ve merkezi allowlist birebir aynı sonucu dönmeli: $key")
        }
        assertEquals(MerchantLogoDomainAllowlist.size(), BrandfetchDomainAllowlist.size())
        assertEquals(MerchantLogoDomainAllowlist.allowlistKeys(), BrandfetchDomainAllowlist.allowlistKeys())
    }

    @Test
    fun allowlist_unknownOrBlankKeys_returnNull() {
        assertNull(MerchantLogoDomainAllowlist.resolveDomain(null))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain(""))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("   "))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("unknown_brand"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros_sanal_market"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("yemeksepeti_market"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("trendyol_yemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("getiryemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros_yemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("turk_telekom"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("kamil_koc"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("budo"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("getir_arac"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("tiktak"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("tatilbudur"))
    }

    @Test
    fun allowlist_rawDomainAsKey_isRejected() {
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros.com.tr"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("carrefoursa.com"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("bim.com.tr"))
    }

    @Test
    fun allowlist_userUrlOrMalformedPathQuery_isRejected() {
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("https://migros.com.tr"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("http://migros.com.tr"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros/path"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros?query=1"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros#frag"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("user@migros"))
    }

    @Test
    fun starterCatalog_everyNonNullVerifiedLogoKey_resolvesInAllowlist() {
        val nonNullMerchants = StarterMerchantCatalog.merchants.filter { it.verifiedLogoKey != null }
        assertEquals(147, nonNullMerchants.size, "Yalnızca doğrulanmış 147 merchant logo anahtarı taşımalı")

        for (merchant in nonNullMerchants) {
            val resolvedDomain = MerchantLogoDomainAllowlist.resolveDomain(merchant.verifiedLogoKey)
            assertNotNull(
                resolvedDomain,
                "StarterMerchantCatalog içindeki ${merchant.displayName} (${merchant.verifiedLogoKey}) allowlist'te çözülmelidir.",
            )
        }
    }

    // -------------------------------------------------------------
    // 2. Fail-Closed Config Doğrulamaları
    // -------------------------------------------------------------

    @Test
    fun config_nullOrBlankPublishableKey_failsValidation() {
        val nullKeyConfig = LogoDevLogoConfig(publishableKey = null)
        val emptyKeyConfig = LogoDevLogoConfig(publishableKey = "")
        val blankKeyConfig = LogoDevLogoConfig(publishableKey = "   ")

        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(nullKeyConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(emptyKeyConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(blankKeyConfig.validate())
    }

    @Test
    fun config_secretKeyPrefix_isStrictlyRejected() {
        val secretConfig = LogoDevLogoConfig(publishableKey = "sk_live_secret_sample_key")
        val result = secretConfig.validate()
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(result)
        assertEquals("secret_key_forbidden", result.reason)
    }

    @Test
    fun config_publishableKeyMustStartWithPk() {
        val invalidPrefixConfig = LogoDevLogoConfig(publishableKey = "live_sample_key_12345")
        val result = invalidPrefixConfig.validate()
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(result)
        assertEquals("publishableKey_must_start_with_pk", result.reason)
    }

    @Test
    fun config_publishableKeyOnlyPkPrefix_isTooShort() {
        val tooShortConfig = LogoDevLogoConfig(publishableKey = "pk_")
        val result = tooShortConfig.validate()
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(result)
        assertEquals("publishableKey_too_short", result.reason)
    }

    @Test
    fun config_publishableKeyWithControlCharactersOrCRLF_isRejected() {
        val crKeyConfig = LogoDevLogoConfig(publishableKey = "pk_test\rkey")
        val lfKeyConfig = LogoDevLogoConfig(publishableKey = "pk_test\nkey")
        val controlKeyConfig = LogoDevLogoConfig(publishableKey = "pk_test\u0000key")

        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(crKeyConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(lfKeyConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(controlKeyConfig.validate())
    }

    @Test
    fun config_publishableKeyWithWhitespace_isRejected() {
        val spaceKeyConfig = LogoDevLogoConfig(publishableKey = "pk_test sample key")
        val tabKeyConfig = LogoDevLogoConfig(publishableKey = "pk_test\tsample")

        val spaceResult = assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(spaceKeyConfig.validate())
        val tabResult = assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(tabKeyConfig.validate())

        assertEquals("publishableKey_contains_whitespace", spaceResult.reason)
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(tabResult)
    }

    @Test
    fun config_publishableKeyWithLeadingOrTrailingWhitespace_isRejected() {
        val leadingSpaceConfig = LogoDevLogoConfig(publishableKey = " pk_test_valid_key")
        val trailingSpaceConfig = LogoDevLogoConfig(publishableKey = "pk_test_valid_key ")

        val leadingResult = assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(leadingSpaceConfig.validate())
        val trailingResult = assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(trailingSpaceConfig.validate())

        assertEquals("publishableKey_contains_whitespace", leadingResult.reason)
        assertEquals("publishableKey_contains_whitespace", trailingResult.reason)
    }

    @Test
    fun resolver_keyWithLeadingOrTrailingWhitespace_returnsInvalidConfiguration() {
        val leadingSpaceConfig = LogoDevLogoConfig(publishableKey = " pk_test_valid_key")
        val trailingSpaceConfig = LogoDevLogoConfig(publishableKey = "pk_test_valid_key ")

        val leadingResolution = LogoDevLogoUrlResolver.resolve("migros", leadingSpaceConfig)
        val trailingResolution = LogoDevLogoUrlResolver.resolve("migros", trailingSpaceConfig)

        val leadingInvalid = assertIs<LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration>(leadingResolution)
        val trailingInvalid = assertIs<LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration>(trailingResolution)

        assertEquals("publishableKey_contains_whitespace", leadingInvalid.reason)
        assertEquals("publishableKey_contains_whitespace", trailingInvalid.reason)
    }

    @Test
    fun config_publishableKeyWithInvalidCharacters_isRejected() {
        val urlParamConfig = LogoDevLogoConfig(publishableKey = "pk_test?param=1")
        val slashConfig = LogoDevLogoConfig(publishableKey = "pk_test/key")
        val atConfig = LogoDevLogoConfig(publishableKey = "pk_test@evil")

        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(urlParamConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(slashConfig.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(atConfig.validate())
    }

    @Test
    fun config_iconSizeBounds_areEnforced() {
        val belowMin = LogoDevLogoConfig(publishableKey = "pk_test_123", iconSizePx = 15)
        val atMin = LogoDevLogoConfig(publishableKey = "pk_test_123", iconSizePx = 16)
        val atMax = LogoDevLogoConfig(publishableKey = "pk_test_123", iconSizePx = 512)
        val aboveMax = LogoDevLogoConfig(publishableKey = "pk_test_123", iconSizePx = 513)

        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(belowMin.validate())
        assertEquals(LogoDevLogoConfig.ConfigValidationResult.Valid, atMin.validate())
        assertEquals(LogoDevLogoConfig.ConfigValidationResult.Valid, atMax.validate())
        assertIs<LogoDevLogoConfig.ConfigValidationResult.Invalid>(aboveMax.validate())
    }

    // -------------------------------------------------------------
    // 3. Resolver Doğrulamaları ve URL Yapısı
    // -------------------------------------------------------------

    @Test
    fun resolver_nullConfig_returnsProviderNotConfigured() {
        val result = LogoDevLogoUrlResolver.resolve("migros", config = null)
        assertIs<LogoDevLogoResolutionResult.Unavailable.ProviderNotConfigured>(result)
    }

    @Test
    fun resolver_invalidConfig_returnsInvalidConfiguration() {
        val invalidConfig = LogoDevLogoConfig(publishableKey = "sk_live_secret")
        val result = LogoDevLogoUrlResolver.resolve("migros", config = invalidConfig)
        val unavailable = assertIs<LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("secret_key_forbidden", unavailable.reason)
    }

    @Test
    fun resolver_nullOrBlankLogoKey_returnsMissingOrBlankKey() {
        assertIs<LogoDevLogoResolutionResult.Unavailable.MissingOrBlankKey>(
            LogoDevLogoUrlResolver.resolve(null, validSyntheticConfig),
        )
        assertIs<LogoDevLogoResolutionResult.Unavailable.MissingOrBlankKey>(
            LogoDevLogoUrlResolver.resolve("", validSyntheticConfig),
        )
        assertIs<LogoDevLogoResolutionResult.Unavailable.MissingOrBlankKey>(
            LogoDevLogoUrlResolver.resolve("   ", validSyntheticConfig),
        )
    }

    @Test
    fun resolver_unknownLogoKey_returnsUnknownKey() {
        val result = LogoDevLogoUrlResolver.resolve("unknown_store", validSyntheticConfig)
        val unknown = assertIs<LogoDevLogoResolutionResult.Unavailable.UnknownKey>(result)
        assertEquals("unknown_store", unknown.key)
    }

    @Test
    fun resolver_verifiedKeys_produceStrictHttpsUrlsWithExpectedParameters() {
        for ((key, expectedDomain) in expectedAllowlist) {
            val result = LogoDevLogoUrlResolver.resolve(key, validSyntheticConfig)
            val available = assertIs<LogoDevLogoResolutionResult.Available>(result)

            val request = available.request
            assertEquals(expectedDomain, request.domain)
            assertTrue(request.headers.isEmpty(), "Logo.dev headers boş olmalıdır (sahte Referer/secret içermez)")

            val expectedUrlPrefix = "https://img.logo.dev/$expectedDomain"
            assertTrue(request.url.startsWith(expectedUrlPrefix), "URL '$expectedUrlPrefix' ile başlamalı: ${request.url}")
            assertTrue(request.url.contains("token=pk_test_synthetic_key_12345"))
            assertTrue(request.url.contains("size=128"))
            assertTrue(request.url.contains("format=png"))
            if (key == "netflix") {
                assertTrue(request.url.contains("theme=dark"), "netflix için theme=dark olmalıdır: ${request.url}")
                assertFalse(request.url.contains("theme=light"), "netflix için theme=light olmamalıdır: ${request.url}")
                assertEquals(LogoDevKeyPresentationPolicy.DARK_NEUTRAL, request.presentationPolicy)
            } else {
                assertTrue(request.url.contains("theme=light"), "$key için theme=light olmalıdır: ${request.url}")
                assertFalse(request.url.contains("theme=dark"), "$key için theme=dark olmamalıdır: ${request.url}")
                assertEquals(LogoDevKeyPresentationPolicy.LIGHT_NEUTRAL, request.presentationPolicy)
            }
            assertTrue(request.url.contains("fallback=404"))
        }
    }

    @Test
    fun resolver_netflix_producesThemeDarkForPngAndWebp_andThemeslessForJpg() {
        val pngConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.PNG)
        val webpConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.WEBP)
        val jpgConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.JPG)

        val pngResolution = LogoDevLogoUrlResolver.resolve("netflix", pngConfig)
        val pngAvailable = assertIs<LogoDevLogoResolutionResult.Available>(pngResolution)
        val pngUrl = io.ktor.http.Url(pngAvailable.request.url)
        assertEquals("dark", pngUrl.parameters["theme"], "Netflix PNG için theme parametresi 'dark' olmalıdır")
        assertEquals(1, pngUrl.parameters.getAll("theme")?.size, "Netflix PNG için theme tekil olmalıdır")
        assertEquals(LogoDevKeyPresentationPolicy.DARK_NEUTRAL, pngAvailable.request.presentationPolicy)

        val webpResolution = LogoDevLogoUrlResolver.resolve("netflix", webpConfig)
        val webpAvailable = assertIs<LogoDevLogoResolutionResult.Available>(webpResolution)
        val webpUrl = io.ktor.http.Url(webpAvailable.request.url)
        assertEquals("dark", webpUrl.parameters["theme"], "Netflix WebP için theme parametresi 'dark' olmalıdır")
        assertEquals(1, webpUrl.parameters.getAll("theme")?.size, "Netflix WebP için theme tekil olmalıdır")
        assertEquals(LogoDevKeyPresentationPolicy.DARK_NEUTRAL, webpAvailable.request.presentationPolicy)

        val jpgResolution = LogoDevLogoUrlResolver.resolve("netflix", jpgConfig)
        val jpgAvailable = assertIs<LogoDevLogoResolutionResult.Available>(jpgResolution)
        val jpgUrl = io.ktor.http.Url(jpgAvailable.request.url)
        assertFalse(jpgUrl.parameters.contains("theme"), "Netflix JPG için theme parametresi bulunmamalıdır")
        assertNull(jpgUrl.parameters["theme"], "Netflix JPG için theme parametresi null olmalıdır")
        assertFalse(jpgAvailable.request.url.contains("theme="), "Netflix JPG raw URL'si theme içermemelidir")
        assertEquals(LogoDevKeyPresentationPolicy.DARK_NEUTRAL, jpgAvailable.request.presentationPolicy)
    }

    private val expectedAllowlist =
        mapOf(
            "migros" to "migros.com.tr",
            "carrefoursa" to "carrefoursa.com",
            "a101" to "a101.com.tr",
            "bim" to "bim.com.tr",
            "sok" to "sokmarket.com.tr",
            "netflix" to "netflix.com",
            "spotify" to "spotify.com",
            "disney_plus" to "disneyplus.com",
            "trendyol" to "trendyol.com",
            "hepsiburada" to "hepsiburada.com",
            "amazon" to "amazon.com.tr",
            "yemeksepeti" to "yemeksepeti.com",
            "turkcell" to "turkcell.com.tr",
            "vodafone" to "vodafone.com.tr",
            "petrol_ofisi" to "petrolofisi.com.tr",
            "opet" to "opet.com.tr",
            "shell" to "shell.com.tr",
            "thy" to "turkishairlines.com",
            "pegasus" to "flypgs.com",
            "uber" to "uber.com",
            "obilet" to "obilet.com",
            "enuygun" to "enuygun.com",
            "macrocenter" to "macrocenter.com.tr",
            "file_market" to "file.com.tr",
            "tarim_kredi" to "tkkoop.com.tr",
            "getir" to "getir.com",
            "bizim_toptan" to "bizimtoptan.com.tr",
            "metro_turkiye" to "metro-tr.com",
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
            "max" to "max.com",
            "exxen" to "exxen.com",
            "gain" to "gain.tv",
            "mubi" to "mubi.com",
            "tod" to "todtv.com.tr",
            "tabii" to "tabii.com",
            "apple_services" to "apple.com",
            "adobe" to "adobe.com",
            "canva" to "canva.com",
            "s_sport_plus" to "ssportplus.com",
            "tv_plus" to "tvplus.com.tr",
            "tivibu" to "tivibu.com.tr",
            "d_smart_go" to "dsmartgo.com.tr",
            "deezer" to "deezer.com",
            "fizy" to "fizy.com",
            "muud" to "muud.com.tr",
            "storytel" to "storytel.com",
            "audible" to "audible.com",
            "dropbox" to "dropbox.com",
            "youtube" to "youtube.com",
            "google" to "google.com",
            "microsoft" to "microsoft.com",
            "playstation" to "playstation.com",
            "xbox" to "xbox.com",
            "linkedin" to "linkedin.com",
            "nintendo" to "nintendo.com",
            "ea" to "ea.com",
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

    @Test
    fun resolver_differentFormats_areReflectedInUrl() {
        val webpConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.WEBP)
        val jpgConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.JPG)

        val webpResolution = LogoDevLogoUrlResolver.resolve("migros", webpConfig)
        val webpResult = assertIs<LogoDevLogoResolutionResult.Available>(webpResolution)
        assertTrue(webpResult.request.url.contains("format=webp"))

        val jpgResolution = LogoDevLogoUrlResolver.resolve("migros", jpgConfig)
        val jpgResult = assertIs<LogoDevLogoResolutionResult.Available>(jpgResolution)
        assertTrue(jpgResult.request.url.contains("format=jpg"))
    }

    @Test
    fun resolver_themeParameter_pngAndWebpHaveSingleLight_jpgHasNoTheme() {
        val pngConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.PNG)
        val webpConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.WEBP)
        val jpgConfig = validSyntheticConfig.copy(format = LogoDevImageFormat.JPG)

        val pngResolution = LogoDevLogoUrlResolver.resolve("migros", pngConfig)
        val pngUrl = io.ktor.http.Url(assertIs<LogoDevLogoResolutionResult.Available>(pngResolution).request.url)
        assertEquals("light", pngUrl.parameters["theme"], "PNG için theme parametresi 'light' olmalıdır")
        assertEquals(1, pngUrl.parameters.getAll("theme")?.size, "PNG için theme parametresi tam bir kez bulunmalıdır")

        val webpResolution = LogoDevLogoUrlResolver.resolve("migros", webpConfig)
        val webpUrl = io.ktor.http.Url(assertIs<LogoDevLogoResolutionResult.Available>(webpResolution).request.url)
        assertEquals("light", webpUrl.parameters["theme"], "WebP için theme parametresi 'light' olmalıdır")
        assertEquals(1, webpUrl.parameters.getAll("theme")?.size, "WebP için theme parametresi tam bir kez bulunmalıdır")

        val jpgResult = assertIs<LogoDevLogoResolutionResult.Available>(LogoDevLogoUrlResolver.resolve("migros", jpgConfig))
        val jpgUrl = io.ktor.http.Url(jpgResult.request.url)
        assertFalse(jpgUrl.parameters.contains("theme"), "JPG için theme parametresi URL'de bulunmamalıdır")
        assertNull(jpgUrl.parameters["theme"], "JPG için theme parametresi null olmalıdır")
        assertFalse(jpgResult.request.url.contains("theme="), "JPG raw URL'si theme parametresi içermemelidir")
    }

    @Test
    fun resolver_isDeterministic() {
        val result1 = LogoDevLogoUrlResolver.resolve("bim", validSyntheticConfig)
        val result2 = LogoDevLogoUrlResolver.resolve("bim", validSyntheticConfig)

        val avail1 = assertIs<LogoDevLogoResolutionResult.Available>(result1)
        val avail2 = assertIs<LogoDevLogoResolutionResult.Available>(result2)
        assertEquals(avail1.request.url, avail2.request.url)
    }

    @Test
    fun resolver_isDeterministic_forAllThreeFormats() {
        for (format in LogoDevImageFormat.entries) {
            val config = validSyntheticConfig.copy(format = format)
            val result1 = assertIs<LogoDevLogoResolutionResult.Available>(LogoDevLogoUrlResolver.resolve("bim", config))
            val result2 = assertIs<LogoDevLogoResolutionResult.Available>(LogoDevLogoUrlResolver.resolve("bim", config))
            assertEquals(result1.request.url, result2.request.url, "$format formatı için çözümleme deterministik olmalıdır")
        }
    }

    // -------------------------------------------------------------
    // 4. Maskeleme ve Token Gizliliği (Zero Secret Leak)
    // -------------------------------------------------------------

    @Test
    fun config_toString_masksPublishableKey() {
        val config = LogoDevLogoConfig(publishableKey = "pk_test_secret_sample_key_999")
        val str = config.toString()
        assertFalse(str.contains("secret_sample_key_999"), "Publishable key içeriği toString'e sızamaz: $str")
        assertTrue(str.contains("pk_***"), "Publishable key 'pk_***' olarak maskelenmeli: $str")
    }

    @Test
    fun request_toString_masksTokenInUrl() {
        val request =
            LogoDevRemoteLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_test_secret_sample_key_999&size=128&format=png&theme=light&fallback=404",
                domain = "migros.com.tr",
                headers = emptyMap(),
            )
        val str = request.toString()
        assertFalse(str.contains("secret_sample_key_999"), "Token değeri request.toString()'e sızamaz: $str")
        assertTrue(str.contains("token=pk_***"), "Token query parametresi maskelenmelidir: $str")
    }

    @Test
    fun resolver_themeParameter_isAlwaysLightAndSingleInUrl() {
        val result = LogoDevLogoUrlResolver.resolve("migros", validSyntheticConfig)
        val available = assertIs<LogoDevLogoResolutionResult.Available>(result)
        val parsedUrl = io.ktor.http.Url(available.request.url)

        assertEquals("light", parsedUrl.parameters["theme"], "theme parametresi 'light' olmalıdır")
        assertEquals(1, parsedUrl.parameters.getAll("theme")?.size, "theme parametresi tekil olmalıdır")
    }

    @Test
    fun resolver_errorReasonsAndToString_neverContainSyntheticTokenOrRawUrl() {
        val sensitiveToken = "pk_synthetic_secret_token_12345"
        val invalidConfigs =
            listOf(
                LogoDevLogoConfig(publishableKey = "sk_synthetic_secret_token_12345"),
                LogoDevLogoConfig(publishableKey = " $sensitiveToken"),
                LogoDevLogoConfig(publishableKey = "$sensitiveToken "),
                LogoDevLogoConfig(publishableKey = "pk_synthetic?param=1"),
                LogoDevLogoConfig(publishableKey = sensitiveToken, iconSizePx = 9999),
            )

        for (cfg in invalidConfigs) {
            val resolution = LogoDevLogoUrlResolver.resolve("migros", cfg)
            val invalid = assertIs<LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration>(resolution)

            assertFalse(
                invalid.reason.contains("synthetic_secret_token"),
                "Hata nedeni token sızdıramaz: ${invalid.reason}",
            )
            assertFalse(
                invalid.reason.contains("12345"),
                "Hata nedeni token sızdıramaz: ${invalid.reason}",
            )
            assertFalse(
                invalid.reason.contains("://"),
                "Hata nedeni raw URL sızdıramaz: ${invalid.reason}",
            )

            val str = invalid.toString()
            assertFalse(
                str.contains("synthetic_secret_token"),
                "toString token sızdıramaz: $str",
            )
            assertFalse(
                str.contains("12345"),
                "toString token sızdıramaz: $str",
            )
            assertFalse(
                str.contains("://"),
                "toString raw URL sızdıramaz: $str",
            )
        }
    }

    @Test
    fun personalCareFifteenMerchants_defaultPresentationPolicyContract() {
        val personalCareKeys =
            listOf(
                "gratis",
                "watsons",
                "rossmann",
                "sephora",
                "eve_shop",
                "flormar",
                "golden_rose",
                "yves_rocher",
                "the_body_shop",
                "kiko_milano",
                "mac_cosmetics",
                "clinique",
                "avon",
                "oriflame",
                "farmasi",
            )
        for (key in personalCareKeys) {
            val merchant = StarterMerchantCatalog.merchants.first { it.id == key }
            assertEquals(key, merchant.verifiedLogoKey)
            assertEquals("personal_care", merchant.categoryIconKey.key)
            val policy = LogoDevKeyPresentationPolicy.forLogoKey(key)
            assertEquals(LogoDevKeyPresentationPolicy.LIGHT_NEUTRAL, policy)
            assertEquals("light", policy.themeParameterValue)

            val resolution = LogoDevLogoUrlResolver.resolve(key, validSyntheticConfig)
            val available = assertIs<LogoDevLogoResolutionResult.Available>(resolution)
            assertEquals(LogoDevKeyPresentationPolicy.LIGHT_NEUTRAL, available.request.presentationPolicy)
            assertTrue(available.request.url.contains("theme=light"))
            assertTrue(available.request.url.contains("fallback=404"))
        }
    }
}
