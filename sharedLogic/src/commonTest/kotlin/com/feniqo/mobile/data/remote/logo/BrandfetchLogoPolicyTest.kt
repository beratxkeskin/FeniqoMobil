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
 * Brandfetch Logo API sözleşmesi, allowlist doğrulaması, fail-closed config ve saf URL testleri.
 *
 * Güvenlik ve Gizlilik:
 * - Gerçek Client ID kesinlikle kullanılmaz.
 * - Sıfır ağ çağrısı: Testler hiçbir ağ trafiği üretmez.
 * - Fail-closed: Hatalı veya eksik config durumlarında asla exception atılmaz, tipli Unavailable döner.
 */
class BrandfetchLogoPolicyTest {
    private val validTestConfig =
        BrandfetchLogoConfig(
            clientId = "test_client_id_12345",
            referer = "https://example.com",
            userAgent = "FeniqoMobilTest/1.0",
            iconSizePx = 128,
        )

    // -------------------------------------------------------------
    // 1. Allowlist Doğrulamaları
    // -------------------------------------------------------------

    @Test
    fun allowlist_resolvesAllOneHundredNineVerifiedKeysCorrectly() {
        expectedAllowlist.forEach { (key, domain) ->
            assertEquals(domain, BrandfetchDomainAllowlist.resolveDomain(key))
        }
    }

    @Test
    fun allowlist_unknownOrBlankKeys_returnNull() {
        assertNull(BrandfetchDomainAllowlist.resolveDomain(null))
        assertNull(BrandfetchDomainAllowlist.resolveDomain(""))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("   "))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("unknown_brand"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("https://migros.com.tr"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("migros.com.tr"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("migros_sanal_market"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("yemeksepeti_market"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("trendyol_yemek"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("getiryemek"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("migros_yemek"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("turk_telekom"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("kamil_koc"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("budo"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("getir_arac"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("tiktak"))
        assertNull(BrandfetchDomainAllowlist.resolveDomain("tatilbudur"))
    }

    @Test
    fun allowlist_domainsAreCanonicalLowercaseAsciiWithoutSchemeOrPath() {
        val domainRegex = Regex("^[a-z0-9]+([\\-.][a-z0-9]+)*\\.[a-z]{2,}$")
        for (key in BrandfetchDomainAllowlist.allowlistKeys()) {
            val domain = BrandfetchDomainAllowlist.resolveDomain(key)
            assertNotNull(domain)
            assertTrue(domainRegex.matches(domain), "Domain kanonik lowercase ASCII olmalı: $domain")
            assertFalse(domain.contains("://"), "Domain şema içeremez: $domain")
            assertFalse(domain.contains('/'), "Domain path içeremez: $domain")
            assertFalse(domain.contains('?'), "Domain query içeremez: $domain")
            assertFalse(domain.contains('#'), "Domain fragment içeremez: $domain")
            assertFalse(domain.contains('@'), "Domain userInfo içeremez: $domain")
        }
    }

    @Test
    fun allowlist_sizeAndKeysMatchVerifiedCatalog() {
        assertEquals(191, BrandfetchDomainAllowlist.size())
        assertEquals(expectedAllowlist.keys, BrandfetchDomainAllowlist.allowlistKeys())
    }

    @Test
    fun starterCatalog_everyNonNullVerifiedLogoKey_resolvesInAllowlist() {
        val nonNullMerchants = StarterMerchantCatalog.merchants.filter { it.verifiedLogoKey != null }
        assertEquals(197, nonNullMerchants.size, "Yalnızca doğrulanmış 197 merchant logo anahtarı taşımalı")

        for (merchant in nonNullMerchants) {
            val resolvedDomain = BrandfetchDomainAllowlist.resolveDomain(merchant.verifiedLogoKey)
            assertNotNull(
                resolvedDomain,
                "StarterMerchantCatalog içindeki ${merchant.displayName} (${merchant.verifiedLogoKey}) allowlist'te çözülmelidir.",
            )
        }
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
            "istanbulkart" to "istanbulkart.istanbul",
            "tcdd" to "tcdd.gov.tr",
            "bitaksi" to "bitaksi.com",
            "marti" to "marti.tech",
            "ispark" to "ispark.istanbul",
            "metro_turizm" to "metroturizm.com.tr",
            "pamukkale_turizm" to "pamukkale.com.tr",
            "varan_turizm" to "varan.com.tr",
            "ido" to "ido.com.tr",
            "ajet" to "ajet.com",
            "sunexpress" to "sunexpress.com.tr",
            "corendon" to "corendonairlines.com",
            "booking" to "booking.com",
            "airbnb" to "airbnb.com",
            "ets_tur" to "etstur.com",
            "jolly_tur" to "jollytur.com",
            "setur" to "setur.com.tr",
            "paribu_cineverse" to "paribucineverse.com",
            "biletix" to "biletix.com",
            "steam" to "steampowered.com",
            "biletinial" to "biletinial.com",
            "bubilet" to "bubilet.com.tr",
            "passo" to "passo.com.tr",
            "mobilet" to "mobilet.com",
            "biletino" to "biletino.com",
            "bugece" to "bugece.co",
            "epic_games" to "epicgames.com",
            "riot_games" to "riotgames.com",
            "battle_net" to "battle.net",
            "ubisoft_store" to "ubisoft.com",
            "cinemarine" to "cinemarine.com.tr",
            "vialand" to "vialand.com",
            "apsiyon" to "apsiyon.com",
            "yonetimcell" to "yonetimcell.com",
            "armut" to "armut.com",
            "senyonet" to "senyonet.net",
            "mrusta" to "mrusta.com.tr",
            "pronet" to "pronet.com.tr",
            "kale_alarm" to "kalealarm.com.tr",
            "acibadem" to "acibadem.com.tr",
            "memorial" to "memorial.com.tr",
            "medical_park" to "medicalpark.com.tr",
            "liv_hospital" to "livhospital.com",
            "medicana" to "medicana.com.tr",
            "florence_nightingale" to "florence.com.tr",
            "anadolu_saglik" to "anadolusaglik.org",
            "amerikan_hastanesi" to "amerikanhastanesi.org",
            "dunyagoz" to "dunyagoz.com",
            "medipol" to "medipol.com.tr",
            "biruni_hastanesi" to "birunihastanesi.com.tr",
            "guven_hastanesi" to "guven.com.tr",
            "lokman_hekim" to "lokmanhekim.com.tr",
            "baskent_hastanesi" to "baskenthastaneleri.com",
            "synevo" to "synevo.com.tr",
            "duzen_laboratuvari" to "duzen.com.tr",
            "dentgroup" to "dentgroup.com.tr",
            "hospitadent" to "hospitadent.com",
            "atasun_optik" to "atasunoptik.com.tr",
            // Eğitim / Education (H4-G2-J1)
            "udemy" to "udemy.com",
            "coursera" to "coursera.org",
            "duolingo" to "duolingo.com",
            "cambly" to "cambly.com",
            "kunduz" to "kunduz.com",
            "tonguc_akademi" to "tongucakademi.com",
            "okulistik" to "okulistik.com",
            "morpa_kampus" to "morpakampus.com",
            "raunt" to "raunt.com",
            "busuu" to "busuu.com",
            "preply" to "preply.com",
            "italki" to "italki.com",
            "skillshare" to "skillshare.com",
            "derslig" to "derslig.com",
            "benim_hocam" to "benimhocam.com",
            "pegem_akademi" to "pegem.net",
            "english_time" to "englishtime.com",
            "bilge_adam" to "bilgeadam.com",
            "wall_street_english" to "wse.com.tr",
            "amerikankultur" to "amerikankultur.org.tr",
            "dr" to "dr.com.tr",
            "kitapyurdu" to "kitapyurdu.com",
            "bkm_kitap" to "bkmkitap.com",
            "idefix" to "idefix.com",
        )

    // -------------------------------------------------------------
    // 2. Fail-Closed Config Doğrulamaları (Asla Exception Fırlatmaz)
    // -------------------------------------------------------------

    @Test
    fun resolver_nullConfig_returnsProviderNotConfigured_withoutException() {
        val result = BrandfetchLogoUrlResolver.resolve("migros", config = null)
        assertIs<BrandfetchLogoResolutionResult.Unavailable.ProviderNotConfigured>(result)
    }

    @Test
    fun resolver_missingOrBlankClientId_returnsInvalidConfiguration_withoutException() {
        val configNullClient = validTestConfig.copy(clientId = null)
        val resultNull = BrandfetchLogoUrlResolver.resolve("migros", configNullClient)
        val invalidNull = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultNull)
        assertEquals("clientId_missing_or_blank", invalidNull.reason)

        val configBlankClient = validTestConfig.copy(clientId = "   ")
        val resultBlank = BrandfetchLogoUrlResolver.resolve("migros", configBlankClient)
        val invalidBlank = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultBlank)
        assertEquals("clientId_missing_or_blank", invalidBlank.reason)
    }

    @Test
    fun resolver_missingOrBlankReferer_returnsInvalidConfiguration_withoutException() {
        val configNullRef = validTestConfig.copy(referer = null)
        val resultNull = BrandfetchLogoUrlResolver.resolve("migros", configNullRef)
        val invalidNull = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultNull)
        assertEquals("referer_missing_or_blank", invalidNull.reason)

        val configBlankRef = validTestConfig.copy(referer = "")
        val resultBlank = BrandfetchLogoUrlResolver.resolve("migros", configBlankRef)
        val invalidBlank = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultBlank)
        assertEquals("referer_missing_or_blank", invalidBlank.reason)
    }

    @Test
    fun resolver_httpReferer_isRejected_withoutException() {
        val config = validTestConfig.copy(referer = "http://example.com")
        val result = BrandfetchLogoUrlResolver.resolve("migros", config)
        val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("referer_must_use_https", invalid.reason)
    }

    @Test
    fun resolver_refererWithUserInfo_isRejected_withoutException() {
        val config = validTestConfig.copy(referer = "https://user:pass@example.com")
        val result = BrandfetchLogoUrlResolver.resolve("migros", config)
        val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("referer_userInfo_forbidden", invalid.reason)
    }

    @Test
    fun resolver_refererWithPath_isRejected_withoutException() {
        val configPath = validTestConfig.copy(referer = "https://example.com/some/path")
        val resultPath = BrandfetchLogoUrlResolver.resolve("migros", configPath)
        val invalidPath = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultPath)
        assertEquals("referer_path_forbidden", invalidPath.reason)

        // Kök path '/' geçerlidir
        val configRoot = validTestConfig.copy(referer = "https://example.com/")
        val resultRoot = BrandfetchLogoUrlResolver.resolve("migros", configRoot)
        assertIs<BrandfetchLogoResolutionResult.Available>(resultRoot)
    }

    @Test
    fun resolver_refererWithQueryOrFragment_isRejected_withoutException() {
        val configQuery = validTestConfig.copy(referer = "https://example.com?query=1")
        val resultQuery = BrandfetchLogoUrlResolver.resolve("migros", configQuery)
        val invalidQuery = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultQuery)
        assertEquals("referer_query_forbidden", invalidQuery.reason)

        val configFragment = validTestConfig.copy(referer = "https://example.com#fragment")
        val resultFragment = BrandfetchLogoUrlResolver.resolve("migros", configFragment)
        val invalidFragment = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultFragment)
        assertEquals("referer_fragment_forbidden", invalidFragment.reason)
    }

    @Test
    fun resolver_refererWithControlCharactersOrCrLf_isRejected_withoutException() {
        val configCrLf = validTestConfig.copy(referer = "https://example.com\r\ninjected")
        val result = BrandfetchLogoUrlResolver.resolve("migros", configCrLf)
        val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("referer_contains_control_characters", invalid.reason)
    }

    @Test
    fun resolver_refererWithWhitespace_isRejected_withoutException() {
        val inputs =
            listOf(
                "https:// example.com",
                "https://example .com",
                "https://exam ple.com",
                " https://example.com",
                "https://example.com ",
            )
        for (input in inputs) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = input))
            val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
            assertEquals("referer_contains_whitespace", invalid.reason, "Girdi reddedilmeli: $input")
        }
    }

    @Test
    fun resolver_refererWithBackslashEscape_isRejected_withoutException() {
        val inputs =
            listOf(
                "https://example.com\\evil",
                "https://example.com\\",
                "https:\\\\example.com",
            )
        for (input in inputs) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = input))
            assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        }
    }

    @Test
    fun resolver_refererWithInvalidHostOrEmpty_isRejected_withoutException() {
        val inputs =
            listOf(
                "https://.",
                "https:///",
                "https://..",
                "https://example..com",
                "https://-example.com",
                "https://example-.com",
            )
        for (input in inputs) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = input))
            assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(
                result,
                "Input must be rejected: $input, but was: $result",
            )
        }
    }

    @Test
    fun resolver_refererWithDoubleSchemeOrMalformedAuthority_isRejected_withoutException() {
        val inputs =
            listOf(
                "https://https://example.com",
                "https://http://example.com",
                "https://:443",
            )
        for (input in inputs) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = input))
            assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(
                result,
                "Input must be rejected: $input, but was: $result",
            )
        }
    }

    @Test
    fun resolver_refererWithInvalidPort_isRejected_withoutException() {
        val inputs =
            listOf(
                "https://example.com:abc",
                "https://example.com:0",
                "https://example.com:65536",
                "https://example.com:-1",
                "https://example.com:99999",
            )
        for (input in inputs) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = input))
            assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(
                result,
                "Input must be rejected: $input, but was: $result",
            )
        }
    }

    @Test
    fun resolver_refererWithValidPortsAndOrigins_areAccepted() {
        val validOrigins =
            listOf(
                "https://example.com",
                "https://example.com/",
                "https://example.com:443",
                "https://example.com:443/",
                "https://example.com:8443",
                "https://example.com:8443/",
                "https://sub.domain.example.com",
                "https://sub.domain.example.com:9000",
            )
        for (origin in validOrigins) {
            val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig.copy(referer = origin))
            val available = assertIs<BrandfetchLogoResolutionResult.Available>(result, "Origin kabul edilmeli: $origin")
            assertEquals(origin, available.request.headers["Referer"])
        }
    }

    @Test
    fun resolver_userAgentWithCrLfOrControlChars_isRejected_withoutException() {
        val configCrLf = validTestConfig.copy(userAgent = "App/1.0\r\nInjectedHeader: value")
        val result = BrandfetchLogoUrlResolver.resolve("migros", configCrLf)
        val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("userAgent_contains_control_characters", invalid.reason)
    }

    @Test
    fun resolver_missingOrBlankUserAgent_isRejected_withoutException() {
        val configBlankUa = validTestConfig.copy(userAgent = "")
        val result = BrandfetchLogoUrlResolver.resolve("migros", configBlankUa)
        val invalid = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(result)
        assertEquals("userAgent_missing_or_blank", invalid.reason)
    }

    @Test
    fun resolver_invalidIconSize_isRejected_withoutException() {
        val configTooSmall = validTestConfig.copy(iconSizePx = 8)
        val resultSmall = BrandfetchLogoUrlResolver.resolve("migros", configTooSmall)
        val invalidSmall = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultSmall)
        assertEquals("iconSizePx_out_of_bounds_8", invalidSmall.reason)

        val configTooLarge = validTestConfig.copy(iconSizePx = 1024)
        val resultLarge = BrandfetchLogoUrlResolver.resolve("migros", configTooLarge)
        val invalidLarge = assertIs<BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration>(resultLarge)
        assertEquals("iconSizePx_out_of_bounds_1024", invalidLarge.reason)
    }

    // -------------------------------------------------------------
    // 3. Güvenli URL Çözümleme ve Sözleşme Testleri
    // -------------------------------------------------------------

    @Test
    fun resolver_pilotKeys_resolveToExpectedHttpsCdnBrandfetchUrl() {
        val result = BrandfetchLogoUrlResolver.resolve("migros", validTestConfig)
        val available = assertIs<BrandfetchLogoResolutionResult.Available>(result)
        assertEquals("migros.com.tr", available.request.domain)
        assertEquals(
            "https://cdn.brandfetch.io/domain/migros.com.tr/w/128/h/128/fallback/404/type/icon?c=test_client_id_12345",
            available.request.url,
        )
        assertEquals("https://example.com", available.request.headers["Referer"])
        assertEquals("FeniqoMobilTest/1.0", available.request.headers["User-Agent"])
    }

    @Test
    fun resolver_unknownKey_returnsUnknownKeyResult() {
        val result = BrandfetchLogoUrlResolver.resolve("bilinmeyen_key", validTestConfig)
        val unknown = assertIs<BrandfetchLogoResolutionResult.Unavailable.UnknownKey>(result)
        assertEquals("bilinmeyen_key", unknown.key)
    }

    @Test
    fun resolver_urlAlwaysUsesHttpsAndCdnBrandfetchHost() {
        val result = BrandfetchLogoUrlResolver.resolve("a101", validTestConfig)
        val available = assertIs<BrandfetchLogoResolutionResult.Available>(result)
        assertTrue(available.request.url.startsWith("https://cdn.brandfetch.io/"))
    }

    @Test
    fun resolver_urlAlwaysContainsDomainRouteAndFallback404AndTypeIcon() {
        val result = BrandfetchLogoUrlResolver.resolve("bim", validTestConfig)
        val available = assertIs<BrandfetchLogoResolutionResult.Available>(result)
        assertTrue(available.request.url.contains("/domain/bim.com.tr/"))
        assertTrue(available.request.url.contains("/fallback/404/"))
        assertTrue(available.request.url.contains("/type/icon"))
    }

    @Test
    fun resolver_encodesClientIdSafely() {
        val specialConfig = validTestConfig.copy(clientId = "id with spaces & special=chars+?")
        val result = BrandfetchLogoUrlResolver.resolve("sok", specialConfig)
        val available = assertIs<BrandfetchLogoResolutionResult.Available>(result)
        assertTrue(
            available.request.url.endsWith("?c=id%20with%20spaces%20%26%20special%3Dchars%2B%3F"),
            "Client ID güvenli biçimde encode edilmeli: ${available.request.url}",
        )
    }

    @Test
    fun resolver_isDeterministic_sameInputProducesIdenticalOutput() {
        val result1 = BrandfetchLogoUrlResolver.resolve("carrefoursa", validTestConfig)
        val result2 = BrandfetchLogoUrlResolver.resolve("carrefoursa", validTestConfig)
        assertEquals(result1, result2)
    }

    @Test
    fun security_clientIdIsNotLeakedInToString() {
        val config = validTestConfig.copy(clientId = "secret_client_key_9999")
        assertFalse(config.toString().contains("secret_client_key_9999"))
        assertTrue(config.toString().contains("***"))

        val result = BrandfetchLogoUrlResolver.resolve("migros", config)
        val available = assertIs<BrandfetchLogoResolutionResult.Available>(result)
        assertFalse(available.request.toString().contains("secret_client_key_9999"))
        assertTrue(available.request.toString().contains("c=***"))
    }
}
