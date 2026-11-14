package com.feniqo.mobile.data.remote.logo

import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FAZ H4-G2-B kapsamı için merkezi [MerchantLogoDomainAllowlist] hedefli sözleşme testleri.
 */
class MerchantLogoDomainAllowlistTest {
    private val historicalTwentyTwoAllowlist: Map<String, String> =
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
        )

    private val marketSixAllowlist: Map<String, String> =
        mapOf(
            "macrocenter" to "macrocenter.com.tr",
            "file_market" to "file.com.tr",
            "tarim_kredi" to "tkkoop.com.tr",
            "getir" to "getir.com",
            "bizim_toptan" to "bizimtoptan.com.tr",
            "metro_turkiye" to "metro-tr.com",
        )

    private val newSixteenFoodDiningAllowlist: Map<String, String> =
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

    private val newNineteenSubscriptionAllowlist: Map<String, String> =
        mapOf(
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
        )

    private val newEightRoofBrandAllowlist: Map<String, String> =
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

    private val newTwentyThreeShoppingAllowlist: Map<String, String> =
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

    private val newFifteenPersonalCareAllowlist: Map<String, String> =
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

    private val newSeventeenTransportationAndTravelAllowlist: Map<String, String> =
        mapOf(
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
        )

    private val newFifteenEntertainmentAllowlist: Map<String, String> =
        mapOf(
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
        )

    private val expectedFullSixtyThreeAllowlist: Map<String, String> =
        historicalTwentyTwoAllowlist + marketSixAllowlist + newSixteenFoodDiningAllowlist + newNineteenSubscriptionAllowlist

    private val expectedFullSeventyOneAllowlist: Map<String, String> =
        expectedFullSixtyThreeAllowlist + newEightRoofBrandAllowlist

    private val expectedFullNinetyFourAllowlist: Map<String, String> =
        expectedFullSeventyOneAllowlist + newTwentyThreeShoppingAllowlist

    private val expectedFullOneHundredNineAllowlist: Map<String, String> =
        expectedFullNinetyFourAllowlist + newFifteenPersonalCareAllowlist

    private val expectedFullOneHundredTwentySixAllowlist: Map<String, String> =
        expectedFullOneHundredNineAllowlist + newSeventeenTransportationAndTravelAllowlist

    private val expectedFullOneHundredFortyOneAllowlist: Map<String, String> =
        expectedFullOneHundredTwentySixAllowlist + newFifteenEntertainmentAllowlist

    private val newSevenHousingAllowlist: Map<String, String> =
        mapOf(
            "apsiyon" to "apsiyon.com",
            "yonetimcell" to "yonetimcell.com",
            "armut" to "armut.com",
            "senyonet" to "senyonet.net",
            "mrusta" to "mrusta.com.tr",
            "pronet" to "pronet.com.tr",
            "kale_alarm" to "kalealarm.com.tr",
        )

    private val expectedFullOneHundredFortyEightAllowlist: Map<String, String> =
        expectedFullOneHundredFortyOneAllowlist + newSevenHousingAllowlist

    private val newNineHealthcareAllowlist: Map<String, String> =
        mapOf(
            "acibadem" to "acibadem.com.tr",
            "memorial" to "memorial.com.tr",
            "medical_park" to "medicalpark.com.tr",
            "liv_hospital" to "livhospital.com",
            "medicana" to "medicana.com.tr",
            "florence_nightingale" to "florence.com.tr",
            "anadolu_saglik" to "anadolusaglik.org",
            "amerikan_hastanesi" to "amerikanhastanesi.org",
            "dunyagoz" to "dunyagoz.com",
        )

    private val expectedFullOneHundredFiftySevenAllowlist: Map<String, String> =
        expectedFullOneHundredFortyEightAllowlist + newNineHealthcareAllowlist

    private val newTenHealthcareSearchableAllowlist: Map<String, String> =
        mapOf(
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
        )

    private val expectedFullOneHundredSixtySevenAllowlist: Map<String, String> =
        expectedFullOneHundredFiftySevenAllowlist + newTenHealthcareSearchableAllowlist

    private val newNineEducationAllowlist: Map<String, String> =
        mapOf(
            "udemy" to "udemy.com",
            "coursera" to "coursera.org",
            "duolingo" to "duolingo.com",
            "cambly" to "cambly.com",
            "kunduz" to "kunduz.com",
            "tonguc_akademi" to "tongucakademi.com",
            "okulistik" to "okulistik.com",
            "morpa_kampus" to "morpakampus.com",
            "raunt" to "raunt.com",
        )

    private val expectedFullOneHundredSeventySixAllowlist: Map<String, String> =
        expectedFullOneHundredSixtySevenAllowlist + newNineEducationAllowlist

    private val newFifteenEducationAndBookstoreAllowlist: Map<String, String> =
        mapOf(
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

    private val expectedFullOneHundredNinetyOneAllowlist: Map<String, String> =
        expectedFullOneHundredSeventySixAllowlist + newFifteenEducationAndBookstoreAllowlist

    // 1. Doğrulanıp eklenen her stable logo key doğru kanonik domaini döndürür
    @Test
    fun resolvesAllSixteenNewlyAddedFoodDiningKeysCorrectly() {
        for ((key, expectedDomain) in newSixteenFoodDiningAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllNineteenNewlyAddedSubscriptionKeysCorrectly() {
        for ((key, expectedDomain) in newNineteenSubscriptionAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllEightNewlyAddedRoofBrandKeysCorrectly() {
        for ((key, expectedDomain) in newEightRoofBrandAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllTwentyThreeNewlyAddedShoppingKeysCorrectly() {
        for ((key, expectedDomain) in newTwentyThreeShoppingAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Shopping key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllFifteenNewlyAddedPersonalCareKeysCorrectly() {
        for ((key, expectedDomain) in newFifteenPersonalCareAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Personal care key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllSeventeenNewlyAddedTransportationAndTravelKeysCorrectly() {
        for ((key, expectedDomain) in newSeventeenTransportationAndTravelAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Transportation/travel key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllFifteenNewlyAddedEntertainmentKeysCorrectly() {
        for ((key, expectedDomain) in newFifteenEntertainmentAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Entertainment key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllSevenNewlyAddedHousingKeysCorrectly() {
        for ((key, expectedDomain) in newSevenHousingAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Housing key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllNineNewlyAddedHealthcareKeysCorrectly() {
        for ((key, expectedDomain) in newNineHealthcareAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Healthcare key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllTenNewlyAddedHealthcareSearchableKeysCorrectly() {
        for ((key, expectedDomain) in newTenHealthcareSearchableAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Healthcare key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllNineNewlyAddedEducationKeysCorrectly() {
        for ((key, expectedDomain) in newNineEducationAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Education key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    @Test
    fun resolvesAllFifteenNewlyAddedEducationAndBookstoreKeysCorrectly() {
        for ((key, expectedDomain) in newFifteenEducationAndBookstoreAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Education/Bookstore key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    // 13. Mevcut allowlist kayıtları regresyona uğramaz
    @Test
    fun historicalTwentyEightKeys_haveNoRegression() {
        for ((key, expectedDomain) in historicalTwentyTwoAllowlist + marketSixAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel key '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalSixtyThreeKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullSixtyThreeAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 63 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalSeventyOneKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullSeventyOneAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 71 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalNinetyFourKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullNinetyFourAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 94 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalOneHundredNineKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullOneHundredNineAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 109 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalOneHundredTwentySixKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullOneHundredTwentySixAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 126 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalOneHundredFortyOneKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullOneHundredFortyOneAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 141 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalOneHundredFortyEightKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullOneHundredFortyEightAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 148 anahtardan '$key' regresyona uğramamalı")
        }
    }

    @Test
    fun historicalOneHundredFiftySevenKeys_haveNoRegression() {
        for ((key, expectedDomain) in expectedFullOneHundredFiftySevenAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel 157 anahtardan '$key' regresyona uğramamalı")
        }
    }

    // 2. Domainler lowercase ASCII ve yalnız host biçimindedir
    // 3. Scheme, path, query, fragment, port ve userInfo bulunmaz
    @Test
    fun allAllowlistedDomains_areStrictLowercaseAsciiHostOnly() {
        val canonicalHostPattern = Regex("^[a-z0-9]+([\\-.][a-z0-9]+)*\\.[a-z]{2,}$")

        for (key in MerchantLogoDomainAllowlist.allowlistKeys()) {
            val domain = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertNotNull(domain, "Allowlisted key '$key' domain döndürmeli")

            assertTrue(canonicalHostPattern.matches(domain), "Domain '$domain' kanonik host pattern'ına uymalı")
            assertEquals(domain.lowercase(), domain, "Domain tamamen lowercase olmalı: $domain")

            assertFalse(domain.contains("://"), "Domain scheme içeremez: $domain")
            assertFalse(domain.contains("/"), "Domain path içeremez: $domain")
            assertFalse(domain.contains("?"), "Domain query içeremez: $domain")
            assertFalse(domain.contains("#"), "Domain fragment içeremez: $domain")
            assertFalse(domain.contains(":"), "Domain port içeremez: $domain")
            assertFalse(domain.contains("@"), "Domain userInfo içeremez: $domain")
            assertFalse(domain.any { it.isWhitespace() }, "Domain boşluk içeremez: $domain")
        }
    }

    // 4. Allowlist size ve exact key set beklentisi günceldir (191 anahtar)
    @Test
    fun allowlist_sizeAndExactKeys_areExactlyOneHundredNinetyOne() {
        assertEquals(191, MerchantLogoDomainAllowlist.size())
        assertEquals(expectedFullOneHundredNinetyOneAllowlist.keys, MerchantLogoDomainAllowlist.allowlistKeys())
    }

    // 5. Katalogdaki her non-null verifiedLogoKey allowlist’te çözümlenir (197 merchant)
    @Test
    fun starterCatalog_everyNonNullVerifiedLogoKey_resolvesInAllowlist() {
        val nonNullMerchants = StarterMerchantCatalog.merchants.filter { it.verifiedLogoKey != null }
        assertEquals(197, nonNullMerchants.size, "Katalogda tam 197 doğrulanmış logo anahtarı bulunmalı")

        for (m in nonNullMerchants) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(m.verifiedLogoKey)
            assertNotNull(resolved, "Katalogdaki ${m.id} (${m.verifiedLogoKey}) allowlist'te çözülmelidir")
            assertEquals(expectedFullOneHundredNinetyOneAllowlist[m.verifiedLogoKey], resolved)
        }
    }

    // 6. Eklenen doğrudan merchant katalogdan aynı stable ID ile bulunur
    @Test
    fun addedMerchants_foundInCatalogWithSameStableId() {
        val allDirectKeys =
            newSixteenFoodDiningAllowlist.keys +
                newNineteenSubscriptionAllowlist.keys +
                newTwentyThreeShoppingAllowlist.keys +
                newFifteenPersonalCareAllowlist.keys +
                newSeventeenTransportationAndTravelAllowlist.keys +
                newFifteenEntertainmentAllowlist.keys +
                newSevenHousingAllowlist.keys +
                newNineHealthcareAllowlist.keys +
                newTenHealthcareSearchableAllowlist.keys +
                newNineEducationAllowlist.keys +
                newFifteenEducationAndBookstoreAllowlist.keys
        for (key in allDirectKeys) {
            val merchant = StarterMerchantCatalog.merchants.firstOrNull { it.id == key }
            assertNotNull(merchant, "Eklenen merchant '$key' katalogda stable ID ile bulunmalıdır")
            assertEquals(key, merchant.verifiedLogoKey, "Merchant '$key' verifiedLogoKey alanında kendi stable ID'sini taşımalı")
        }
    }

    // 7. Yeni sekiz çatı logo anahtarının katalogda en az bir doğrulanmış merchant tarafından referans verildiğini doğrula
    @Test
    fun newEightRoofBrandKeys_referencedByAtLeastOneVerifiedMerchantInCatalog() {
        for (roofKey in newEightRoofBrandAllowlist.keys) {
            val referencingMerchants = StarterMerchantCatalog.merchants.filter { it.verifiedLogoKey == roofKey }
            assertTrue(
                referencingMerchants.isNotEmpty(),
                "Yeni çatı logo anahtarı '$roofKey' katalogda en az bir doğrulanmış hizmet tarafından referans verilmelidir",
            )
        }
    }

    // 8. Hizmet merchant ID'leri doğrudan allowlist anahtarı değildir; çözümleme yalnız verifiedLogoKey üzerinden yapılır
    @Test
    fun subscriptionCandidates_haveNoDirectAllowlistEntry_andResolveOnlyThroughVerifiedLogoKey() {
        val candidatesWithoutAllowlist =
            listOf(
                "youtube_premium",
                "amazon_prime",
                "google_one",
                "microsoft_365",
                "playstation_plus",
                "xbox_game_pass",
                "linkedin_premium",
                "nintendo_switch_online",
                "ea_play",
                "hepsiburada_premium",
                "money_gold",
                "ypro",
            )
        for (merchantId in candidatesWithoutAllowlist) {
            // Doğrudan merchant ID allowlist'te aranırsa null döner
            assertNull(
                MerchantLogoDomainAllowlist.resolveDomain(merchantId),
                "Candidate '$merchantId' must not be in allowlist directly",
            )

            // Çözümleme yalnız merchant.verifiedLogoKey üzerinden gerçekleşir
            val merchant = StarterMerchantCatalog.merchants.first { it.id == merchantId }
            assertNotNull(merchant.verifiedLogoKey, "Merchant '$merchantId' verifiedLogoKey taşımalıdır")
            val resolvedDomain = MerchantLogoDomainAllowlist.resolveDomain(merchant.verifiedLogoKey)
            assertNotNull(
                resolvedDomain,
                "Merchant '$merchantId' verifiedLogoKey '${merchant.verifiedLogoKey}' üzerinden çözülmelidir",
            )
        }
    }

    // 7. Unknown key null döner
    @Test
    fun unknownKeys_andFallbackCandidates_returnNull() {
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("unknown_brand_xyz"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros_sanal_market"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("yemeksepeti_market"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("trendyol_yemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("getiryemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("migros_yemek"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("turk_telekom"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("kahve_diyari"))
        // Ulaşım ve seyahat kasıtlı fallback kalan 5 aday
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("kamil_koc"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("budo"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("getir_arac"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("tiktak"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("tatilbudur"))
    }

    // 8. Null/blank key null döner
    @Test
    fun nullOrBlankKeys_returnNull() {
        assertNull(MerchantLogoDomainAllowlist.resolveDomain(null))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain(""))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("   "))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("\t\n"))
    }

    // 9. Kullanıcı metni ve işlem açıklaması domain oluşturamaz
    @Test
    fun userTextOrDescription_cannotProduceDomain() {
        val userDescriptions =
            listOf(
                "Migros Sanal Market Siparişi",
                "Macrocenter Gurme Alışverişi",
                "File Market Fatura",
                "Getir Kurye 150 TL",
                "Bizim Toptan Gıda",
                "Metro Grosmarket Alışverişi",
                "Starbucks Latte 120 TL",
                "McDonalds Menü Siparişi",
                "Burger King Whopper",
                "KFC Kova Menü",
                "Popeyes Tavuk",
                "Dominos Pizza Büyük Boy",
                "Little Caesars Pizza",
                "Pizza Lazza Sipariş",
                "Tavuk Dünyası Kekiklim",
                "Baydöner İskender",
                "Köfteci Yusuf Porsiyon",
                "Kahve Dünyası Filtre Kahve",
                "Mado Dondurma",
                "Espressolab Cold Brew",
                "Simit Sarayı Kahvaltı",
                "BigChefs Akşam Yemeği",
                "https://getir.com",
                "getir.com",
                "starbucks.com",
                "starbucks.com.tr",
                "kfcturkiye.com",
            )

        for (desc in userDescriptions) {
            assertNull(
                MerchantLogoDomainAllowlist.resolveDomain(desc),
                "Serbest kullanıcı metni veya URL asla allowlist domain'ine çözümlenemez: '$desc'",
            )
        }
    }

    // isAllowlisted doğrulaması
    @Test
    fun isAllowlisted_verifiesDomainMembership() {
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("getir.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("macrocenter.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("file.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("tkkoop.com.tr"))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("tarimkredi.org.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("bizimtoptan.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("metro-tr.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("migros.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("starbucks.com"))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("starbucks.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("mcdonalds.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("burgerking.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("kfcturkiye.com"))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("kfc.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("popeyes.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("dominos.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("littlecaesars.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("pizzalazza.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("tavukdunyasi.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("baydoner.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("kofteciyusuf.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("kahvedunyasi.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("mado.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("espressolab.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("simitsarayi.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("bigchefs.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("max.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("exxen.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("apple.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("muud.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("dropbox.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("google.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("youtube.com"))
        // 23 Yeni Alışveriş Kanonik Alan Adları
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("n11.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("ciceksepeti.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("pazarama.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("boyner.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("lcwaikiki.bg"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("defacto.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("teknosa.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("mediamarkt.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("flo.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("koton.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("mavi.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("zara.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("hm.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("pullandbear.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("bershka.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("stradivarius.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("beymen.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("vakko.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("ikea.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("koctas.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("bauhaus.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("vatanbilgisayar.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("e-bebek.com"))
        // 15 Yeni Kişisel Bakım Kanonik Alan Adları
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("gratis.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("watsons.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("rossmann.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("sephora.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("eveshop.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("flormar.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("goldenrose.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("yvesrocher.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("thebodyshop.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("kikomilano.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("maccosmetics.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("clinique.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("avon.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("oriflame.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("farmasi.com.tr"))
        // 17 Yeni Ulaşım ve Seyahat Kanonik Alan Adları
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("istanbulkart.istanbul"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("tcdd.gov.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("bitaksi.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("marti.tech"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("ispark.istanbul"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("metroturizm.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("pamukkale.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("varan.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("ido.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("ajet.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("sunexpress.com.tr"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("corendonairlines.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("booking.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("airbnb.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("etstur.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("jollytur.com"))
        assertTrue(MerchantLogoDomainAllowlist.isAllowlisted("setur.com.tr"))

        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted(null))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted(""))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("unallowlisted-site.com"))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("malicious.site"))
    }

    // Benzer veya yönlenen ancak kanonik kabul edilmeyen domainler fail-closed reddedilir
    @Test
    fun similarAlternativeOrRedirectDomains_areRejectedFailClosed() {
        val nonCanonicalOrRedirectDomains =
            listOf(
                "boyner.com",
                "mediamarkt.com",
                "ikea.com",
                "lcwaikiki.com",
                "lcw.com",
                "ebebek.com",
                "tarimkredi.org.tr",
                "starbucks.com.tr",
                "kfc.com.tr",
                // Kişisel Bakım seçilmemiş / istenmeyen alan adları (unselected logo domain)
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
                // Ulaşım ve Seyahat seçilmemiş / yönlenen alan adları ve kasıtlı fallback kalanlar
                "kamilkoc.com.tr",
                "budo.burulas.com.tr",
                "getirarac.com",
                "tiktak.com.tr",
                "tatilbudur.com",
                "ajet.com.tr",
            )

        for (domain in nonCanonicalOrRedirectDomains) {
            assertFalse(
                MerchantLogoDomainAllowlist.isAllowlisted(domain),
                "Kanonik olmayan domain '$domain' allowlist içinde yer alamaz",
            )
            assertNull(
                MerchantLogoDomainAllowlist.resolveDomain(domain),
                "Kanonik olmayan domain '$domain' doğrudan resolveDomain ile çözümlenemez",
            )
        }
    }

    // Alışveriş kategorisi toplam ve fallback metrik sözleşmesi (27/27 uzaktan logolu, 0 fallback)
    @Test
    fun shoppingCategoryTotalAndFallbackMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val shoppingMerchants = allMerchants.filter { it.categoryIconKey.key == "shopping" }
        assertEquals(31, shoppingMerchants.size, "Alışveriş kategorisinde tam 31 merchant olmalıdır")

        val verifiedShopping = shoppingMerchants.filter { it.verifiedLogoKey != null }
        assertEquals(31, verifiedShopping.size, "Alışveriş kategorisindeki 31 merchant'ın tamamı verifiedLogoKey taşımalıdır")

        val fallbackShopping = shoppingMerchants.filter { it.verifiedLogoKey == null }
        assertEquals(0, fallbackShopping.size, "Alışveriş kategorisinde fallback merchant sayısı 0 olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 23 Alışveriş merchant'ının stable ID, displayName, kategori ve alias yapılarının korunduğu snapshot
    @Test
    fun shoppingMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "n11" to Triple("n11", "shopping", setOf("N11", "DOGUS PLANET")),
                "ciceksepeti" to Triple("ÇiçekSepeti", "shopping", setOf("CICEKSEPETI", "CICEK SEPETI")),
                "pazarama" to Triple("Pazarama", "shopping", setOf("PAZARAMA", "TOPKAPI DANISMANLIK")),
                "boyner" to Triple("Boyner", "shopping", setOf("BOYNER", "BOYNER BUYUK MAGAZACILIK")),
                "lc_waikiki" to Triple("LC Waikiki", "shopping", setOf("LC WAIKIKI", "LCWAIKIKI", "LCW")),
                "defacto" to Triple("DeFacto", "shopping", setOf("DEFACTO", "DE FACTO")),
                "teknosa" to Triple("Teknosa", "shopping", setOf("TEKNOSA", "TEKNOSA IC VE DIS TICARET")),
                "mediamarkt" to Triple("MediaMarkt", "shopping", setOf("MEDIAMARKT", "MEDIA MARKT")),
                "flo" to Triple("FLO", "shopping", setOf("FLO", "FLO MAGAZACILIK")),
                "koton" to Triple("Koton", "shopping", setOf("KOTON", "KOTON MAGAZACILIK")),
                "mavi" to Triple("Mavi", "shopping", setOf("MAVI GIYIM", "MAVI JEANS")),
                "zara" to Triple("Zara", "shopping", setOf("ZARA", "ZARA GIYIM")),
                "hm" to Triple("H&M", "shopping", setOf("H M MAGAZACILIK", "H AND M", "HENNES MAURITZ", "H M GIYIM")),
                "pull_and_bear" to Triple("Pull&Bear", "shopping", setOf("PULL BEAR", "PULL AND BEAR", "PULLBEAR")),
                "bershka" to Triple("Bershka", "shopping", setOf("BERSHKA", "BERSHKA TEKSTIL")),
                "stradivarius" to Triple("Stradivarius", "shopping", setOf("STRADIVARIUS")),
                "beymen" to Triple("Beymen", "shopping", setOf("BEYMEN", "BEYMEN MAGAZACILIK")),
                "vakko" to Triple("Vakko", "shopping", setOf("VAKKO", "VAKKO TEKSTIL")),
                "ikea" to Triple("IKEA", "shopping", setOf("IKEA", "IKEA MOBILYA")),
                "koctas" to Triple("Koçtaş", "shopping", setOf("KOCTAS", "KOCTAS YAPI MARKET")),
                "bauhaus" to Triple("Bauhaus", "shopping", setOf("BAUHAUS", "BAUHAUS YAPI MARKET")),
                "vatan_bilgisayar" to Triple("Vatan Bilgisayar", "shopping", setOf("VATAN BILGISAYAR", "VATAN BILG")),
                "ebebek" to Triple("ebebek", "shopping", setOf("EBEBEK", "E BEBEK", "EBEBEK MAGAZACILIK")),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            assertEquals(expected.second, merchant.categoryIconKey.key, "Merchant '$id' category 'shopping' olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey kendi stable ID'si olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    // Kişisel Bakım kategorisi toplam ve fallback metrik sözleşmesi (15/15 uzaktan logolu, 0 fallback)
    @Test
    fun personalCareCategoryTotalAndFallbackMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val personalCareMerchants = allMerchants.filter { it.categoryIconKey.key == "personal_care" }
        assertEquals(15, personalCareMerchants.size, "Kişisel bakım kategorisinde tam 15 merchant olmalıdır")

        val verifiedPersonalCare = personalCareMerchants.filter { it.verifiedLogoKey != null }
        assertEquals(15, verifiedPersonalCare.size, "Kişisel bakım kategorisindeki 15 merchant'ın tamamı verifiedLogoKey taşımalıdır")

        val fallbackPersonalCare = personalCareMerchants.filter { it.verifiedLogoKey == null }
        assertEquals(0, fallbackPersonalCare.size, "Kişisel bakım kategorisinde fallback merchant sayısı 0 olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 15 Kişisel Bakım merchant'ının stable ID, displayName, kategori ve alias yapılarının korunduğu snapshot
    @Test
    fun personalCareMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "gratis" to Triple("Gratis", "personal_care", setOf("GRATIS")),
                "watsons" to Triple("Watsons", "personal_care", setOf("WATSONS")),
                "rossmann" to Triple("Rossmann", "personal_care", setOf("ROSSMANN")),
                "sephora" to Triple("Sephora", "personal_care", setOf("SEPHORA")),
                "eve_shop" to Triple("EVE", "personal_care", setOf("EVE SHOP")),
                "flormar" to Triple("Flormar", "personal_care", setOf("FLORMAR")),
                "golden_rose" to Triple("Golden Rose", "personal_care", setOf("GOLDEN ROSE")),
                "yves_rocher" to Triple("Yves Rocher", "personal_care", setOf("YVES ROCHER")),
                "the_body_shop" to Triple("The Body Shop", "personal_care", setOf("THE BODY SHOP")),
                "kiko_milano" to Triple("KIKO Milano", "personal_care", setOf("KIKO MILANO")),
                "mac_cosmetics" to Triple("MAC Cosmetics", "personal_care", setOf("MAC COSMETICS")),
                "clinique" to Triple("Clinique", "personal_care", setOf("CLINIQUE")),
                "avon" to Triple("Avon", "personal_care", setOf("AVON", "AVON KOZMETIK")),
                "oriflame" to Triple("Oriflame", "personal_care", setOf("ORIFLAME")),
                "farmasi" to Triple("Farmasi", "personal_care", setOf("FARMASI")),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            assertEquals(expected.second, merchant.categoryIconKey.key, "Merchant '$id' category 'personal_care' olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey kendi stable ID'si olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    // Ulaşım ve Seyahat kategorisi toplam ve fallback metrik sözleşmesi (22 uzaktan logolu, 5 fallback)
    @Test
    fun transportationAndTravelCategoryTotalAndFallbackMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val transportationMerchants = allMerchants.filter { it.categoryIconKey.key == "transportation" }
        assertEquals(14, transportationMerchants.size, "Ulaşım kategorisinde tam 14 merchant olmalıdır")
        assertEquals(10, transportationMerchants.count { it.verifiedLogoKey != null }, "Ulaşım kategorisinde 10 doğrulanmış logo olmalıdır")
        assertEquals(4, transportationMerchants.count { it.verifiedLogoKey == null }, "Ulaşım kategorisinde 4 fallback merchant olmalıdır")

        val travelMerchants = allMerchants.filter { it.categoryIconKey.key == "travel" }
        assertEquals(13, travelMerchants.size, "Seyahat kategorisinde tam 13 merchant olmalıdır")
        assertEquals(12, travelMerchants.count { it.verifiedLogoKey != null }, "Seyahat kategorisinde 12 doğrulanmış logo olmalıdır")
        assertEquals(1, travelMerchants.count { it.verifiedLogoKey == null }, "Seyahat kategorisinde 1 fallback merchant olmalıdır")

        val combinedPool = transportationMerchants + travelMerchants
        assertEquals(27, combinedPool.size, "Ulaşım ve seyahat birleşik havuzunda 27 merchant olmalıdır")
        assertEquals(22, combinedPool.count { it.verifiedLogoKey != null }, "Birleşik havuzda 22 doğrulanmış logo olmalıdır")
        assertEquals(5, combinedPool.count { it.verifiedLogoKey == null }, "Birleşik havuzda 5 fallback merchant olmalıdır")

        val deliberateFallbacks = listOf("kamil_koc", "budo", "getir_arac", "tiktak", "tatilbudur")
        for (fallbackId in deliberateFallbacks) {
            val merchant = allMerchants.first { it.id == fallbackId }
            assertNull(merchant.verifiedLogoKey, "Kasıtlı fallback '$fallbackId' verifiedLogoKey taşımamalıdır")
            assertNull(MerchantLogoDomainAllowlist.resolveDomain(fallbackId), "'$fallbackId' allowlist'te bulunmamalıdır")
        }

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 17 Ulaşım ve Seyahat merchant'ının stable ID, displayName, kategori ve alias yapılarının korunduğu snapshot
    @Test
    fun transportationAndTravelMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "istanbulkart" to
                    Triple(
                        "İstanbulkart",
                        "transportation",
                        setOf("ISTANBULKART", "BELBIM", "BELBIM ELEKTRONIK PARA", "ISTANBUL KART"),
                    ),
                "tcdd" to Triple("TCDD", "transportation", setOf("TCDD", "TCDD TASIMACILIK")),
                "bitaksi" to Triple("BiTaksi", "transportation", setOf("BITAKSI", "BI TAKSI")),
                "marti" to Triple("Martı", "transportation", setOf("MARTI", "MARTI ILERI TEKNOLOJI", "MARTI TAG", "MARTI SCOOTER")),
                "ispark" to Triple("İSPARK", "transportation", setOf("ISPARK", "ISTANBUL OTOPARK", "ISPARK OTOPARK")),
                "metro_turizm" to Triple("Metro Turizm", "transportation", setOf("METRO TURIZM", "METRO TURIZM TASIMACILIK")),
                "pamukkale_turizm" to
                    Triple(
                        "Pamukkale Turizm",
                        "transportation",
                        setOf("PAMUKKALE TURIZM", "PAMUKKALE SEYAHAT", "PAMUKKALE OTOBUS"),
                    ),
                "varan_turizm" to Triple("Varan Turizm", "transportation", setOf("VARAN TURIZM", "VARAN SEYAHAT")),
                "ido" to Triple("İDO", "transportation", setOf("IDO DENIZ OTOBUSLERI", "ISTANBUL DENIZ OTOBUSLERI", "IDO")),
                "ajet" to Triple("AJet", "travel", setOf("AJET HAVA TASIMACILIGI", "ANADOLUJET", "ANADOLU JET", "AJET")),
                "sunexpress" to Triple("SunExpress", "travel", setOf("SUNEXPRESS", "SUN EXPRESS")),
                "corendon" to Triple("Corendon", "travel", setOf("CORENDON", "CORENDON AIRLINES", "CORENDON HAVA YOLLARI")),
                "booking" to Triple("Booking.com", "travel", setOf("BOOKING COM", "BOOKINGCOM", "BOOKING BV")),
                "airbnb" to Triple("Airbnb", "travel", setOf("AIRBNB", "AIRBNB IRELAND", "AIRBNB PAYMENTS")),
                "ets_tur" to Triple("ETS Tur", "travel", setOf("ETSTUR", "ETS TUR", "ERSOY TURISTIK SERVISLERI", "ETS")),
                "jolly_tur" to Triple("Jolly Tur", "travel", setOf("JOLLY TUR", "JOLLY TURIZM", "JOLLY")),
                "setur" to Triple("Setur", "travel", setOf("SETUR", "SETUR SERVIS TURISTIK", "SETUR TURIZM")),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            assertEquals(expected.second, merchant.categoryIconKey.key, "Merchant '$id' category '${expected.second}' olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey kendi stable ID'si olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    // Eğlence kategorisi toplam ve metrik sözleşmesi (17/17 uzaktan logolu, 0 fallback)
    @Test
    fun entertainmentCategoryTotalAndMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val entertainmentMerchants = allMerchants.filter { it.categoryIconKey.key == "entertainment" }
        assertEquals(17, entertainmentMerchants.size, "Eğlence kategorisinde tam 17 merchant olmalıdır")

        val verifiedEntertainment = entertainmentMerchants.filter { it.verifiedLogoKey != null }
        assertEquals(17, verifiedEntertainment.size, "Eğlence kategorisindeki 17 merchant'ın tamamı verifiedLogoKey taşımalıdır")

        val fallbackEntertainment = entertainmentMerchants.filter { it.verifiedLogoKey == null }
        assertEquals(0, fallbackEntertainment.size, "Eğlence kategorisinde fallback merchant sayısı 0 olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 17 Eğlence merchant'ının stable ID, displayName, kategori, verifiedLogoKey ve alias snapshot doğrulaması
    @Test
    fun entertainmentMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "paribu_cineverse" to
                    Triple(
                        "Paribu Cineverse",
                        "paribu_cineverse",
                        setOf("PARIBU CINEVERSE", "CINEVERSE", "CGV MARS", "CINEMAXIMUM"),
                    ),
                "biletix" to Triple("Biletix", "biletix", setOf("BILETIX", "BILETIX BILET DAGITIM")),
                "steam" to Triple("Steam", "steam", setOf("STEAM GAMES", "STEAMPOWERED COM", "VALVE SOFTWARE")),
                "biletinial" to Triple("Biletinial", "biletinial", setOf("BILETINIAL", "BILETINIAL COM")),
                "bubilet" to Triple("Bubilet", "bubilet", setOf("BUBILET", "BUBILET BILET BASIM", "BUBILET BILET DAGITIM")),
                "passo" to Triple("Passo", "passo", setOf("PASSO", "PASSO DIJITAL HIZMETLER")),
                "mobilet" to Triple("Mobilet", "mobilet", setOf("MOBILET", "EVENT MASTER")),
                "biletino" to Triple("Biletino", "biletino", setOf("BILETINO")),
                "bugece" to Triple("BUGECE", "bugece", setOf("BUGECE", "BUGECE CO")),
                "epic_games" to Triple("Epic Games", "epic_games", setOf("EPIC GAMES", "EPICGAMES")),
                "playstation_store" to Triple("PlayStation Store", "playstation", setOf("PLAYSTATION STORE", "SONY PLAYSTATION STORE")),
                "xbox_store" to Triple("Xbox Store", "xbox", setOf("XBOX STORE", "MICROSOFT XBOX STORE")),
                "riot_games" to Triple("Riot Games", "riot_games", setOf("RIOT GAMES", "RIOTGAMES")),
                "battle_net" to Triple("Battle.net", "battle_net", setOf("BATTLE NET", "BATTLENET", "BLIZZARD ENTERTAINMENT")),
                "ubisoft_store" to Triple("Ubisoft Store", "ubisoft_store", setOf("UBISOFT STORE", "UBISOFT CONNECT", "UBISOFT")),
                "cinemarine" to Triple("Cinemarine", "cinemarine", setOf("CINEMARINE")),
                "vialand" to Triple("Vialand", "vialand", setOf("VIALAND", "ISFANBUL")),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            assertEquals("entertainment", merchant.categoryIconKey.key, "Merchant '$id' category 'entertainment' olmalıdır")
            assertEquals(expected.second, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey '${expected.second}' olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    // Housing kategorisi toplam ve metrik sözleşmesi (7/7 uzaktan logolu, 0 fallback)
    @Test
    fun housingCategoryTotalAndMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val housingMerchants = allMerchants.filter { it.categoryIconKey.key == "housing" }
        assertEquals(7, housingMerchants.size, "Konut kategorisinde tam 7 merchant olmalıdır")

        val verifiedHousing = housingMerchants.filter { it.verifiedLogoKey != null }
        assertEquals(7, verifiedHousing.size, "Konut kategorisindeki 7 merchant'ın tamamı verifiedLogoKey taşımalıdır")

        val fallbackHousing = housingMerchants.filter { it.verifiedLogoKey == null }
        assertEquals(0, fallbackHousing.size, "Konut kategorisinde fallback merchant sayısı 0 olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 7 Konut merchant'ının stable ID, displayName, kategori, verifiedLogoKey ve alias snapshot doğrulaması
    @Test
    fun housingMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "apsiyon" to
                    Triple(
                        "Apsiyon",
                        "apsiyon",
                        setOf(
                            "APSIYON",
                            "APSIYON BILISIM",
                            "APSIYON AIDAT",
                            "APSIYON.COM",
                            "APSIYON BILISIM SISTEMLERI SANAYI VE TICARET ANONIM SIRKETI",
                        ),
                    ),
                "yonetimcell" to
                    Triple(
                        "Yönetimcell",
                        "yonetimcell",
                        setOf(
                            "YONETIMCELL",
                            "YONETIM CELL",
                            "YONETIMCELL.COM",
                            "YONETIMCELL YAZILIM BILISIM LIMITED SIRKETI",
                        ),
                    ),
                "armut" to
                    Triple(
                        "Armut",
                        "armut",
                        setOf(
                            "ARMUT.COM",
                            "ARMUT TEKNOLOJI",
                            "ARMUT HIZMET",
                            "ARMUT TEMIZLIK",
                            "ARMUT TADILAT",
                            "ARMUT TEKNOLOJI ANONIM SIRKETI",
                        ),
                    ),
                "senyonet" to
                    Triple(
                        "Senyonet",
                        "senyonet",
                        setOf(
                            "SENYONET",
                            "SEN YONET",
                            "SENYONET.NET",
                            "SENYONET BILISIM SISTEMLERI SANAYI VE TICARET ANONIM SIRKETI",
                        ),
                    ),
                "mrusta" to
                    Triple(
                        "Mr Usta",
                        "mrusta",
                        setOf(
                            "MR USTA",
                            "MRUSTA",
                            "MRUSTA.COM.TR",
                            "MR USTA ELEKTRONIK HIZMETLER VE TICARET ANONIM SIRKETI",
                        ),
                    ),
                "pronet" to
                    Triple(
                        "Pronet",
                        "pronet",
                        setOf(
                            "PRONET",
                            "PRONET GUVENLIK",
                            "PRONET ALARM",
                            "PRONET.COM.TR",
                            "PRONET GUVENLIK HIZMETLERI ANONIM SIRKETI",
                        ),
                    ),
                "kale_alarm" to
                    Triple(
                        "Kale Alarm",
                        "kale_alarm",
                        setOf(
                            "KALE ALARM",
                            "KALE GUVENLIK",
                            "KALEALARM.COM.TR",
                            "KALE GUVENLIK SISTEMLERI ANONIM SIRKETI",
                        ),
                    ),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            assertEquals("housing", merchant.categoryIconKey.key, "Merchant '$id' category 'housing' olmalıdır")
            assertEquals(expected.second, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey '${expected.second}' olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    // Sağlık kategorisi toplam ve metrik sözleşmesi (18/18 uzaktan logolu, 0 fallback, Atasun shopping altında)
    @Test
    fun healthcareCategoryTotalAndMetricsContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val healthcareMerchants = allMerchants.filter { it.categoryIconKey.key == "healthcare" }
        assertEquals(18, healthcareMerchants.size, "Sağlık kategorisinde tam 18 merchant olmalıdır (Atasun shopping kategorisindedir)")

        val verifiedHealthcare = healthcareMerchants.filter { it.verifiedLogoKey != null }
        assertEquals(18, verifiedHealthcare.size, "Sağlık kategorisindeki 18 merchant'ın tamamı verifiedLogoKey taşımalıdır")

        val fallbackHealthcare = healthcareMerchants.filter { it.verifiedLogoKey == null }
        assertEquals(0, fallbackHealthcare.size, "Sağlık kategorisinde fallback merchant sayısı 0 olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")
    }

    // 19 Sağlık-arama merchant'ının stable ID, displayName, kategori, verifiedLogoKey ve alias snapshot doğrulaması
    @Test
    fun healthcareMerchants_preserveIdentityAndAliasesSnapshot() {
        val merchants = StarterMerchantCatalog.merchants
        val snapshotExpected =
            mapOf(
                "acibadem" to
                    Triple(
                        "Acıbadem",
                        "acibadem",
                        setOf("ACIBADEM", "ACIBADEM HASTANESI", "ACIBADEM SAGLIK"),
                    ),
                "memorial" to
                    Triple(
                        "Memorial",
                        "memorial",
                        setOf("MEMORIAL", "MEMORIAL HASTANESI", "MEMORIAL SAGLIK"),
                    ),
                "medical_park" to
                    Triple(
                        "Medical Park",
                        "medical_park",
                        setOf("MEDICAL PARK", "MEDICALPARK", "VM MEDICAL PARK"),
                    ),
                "liv_hospital" to
                    Triple(
                        "Liv Hospital",
                        "liv_hospital",
                        setOf(
                            "LIV HOSPITAL",
                            "LIV ULUS",
                            "LIV VADI",
                            "LIV ANKARA",
                            "MLP SAGLIK HIZMETLERI LIV HOSPITAL",
                        ),
                    ),
                "medicana" to
                    Triple(
                        "Medicana",
                        "medicana",
                        setOf("MEDICANA", "MEDICANA HASTANESI", "MEDICANA SAGLIK"),
                    ),
                "florence_nightingale" to
                    Triple(
                        "Florence Nightingale",
                        "florence_nightingale",
                        setOf(
                            "FLORENCE NIGHTINGALE",
                            "FLORENCE HASTANESI",
                            "ISTANBUL FLORENCE NIGHTINGALE",
                        ),
                    ),
                "anadolu_saglik" to
                    Triple(
                        "Anadolu Sağlık Merkezi",
                        "anadolu_saglik",
                        setOf("ANADOLU SAGLIK MERKEZI", "ANADOLU SAGLIK"),
                    ),
                "amerikan_hastanesi" to
                    Triple(
                        "Amerikan Hastanesi",
                        "amerikan_hastanesi",
                        setOf(
                            "AMERIKAN HASTANESI",
                            "VKV AMERIKAN HASTANESI",
                            "KOC AMERIKAN HASTANESI",
                        ),
                    ),
                "dunyagoz" to
                    Triple(
                        "Dünyagöz",
                        "dunyagoz",
                        setOf("DUNYAGOZ", "DUNYA GOZ", "DUNYAGOZ HASTANESI"),
                    ),
                "medipol" to
                    Triple(
                        "Medipol",
                        "medipol",
                        setOf("MEDIPOL HASTANESI", "MEDIPOL SAGLIK", "MEDIPOL MEGA", "MEDIPOL UNIVERSITE HASTANESI"),
                    ),
                "biruni_hastanesi" to
                    Triple(
                        "Biruni Hastanesi",
                        "biruni_hastanesi",
                        setOf("BIRUNI HASTANESI", "BIRUNI UNIVERSITE HASTANESI", "BIRUNI UNIVERSITESI TIP FAKULTESI HASTANESI"),
                    ),
                "guven_hastanesi" to
                    Triple(
                        "Güven Hastanesi",
                        "guven_hastanesi",
                        setOf("GUVEN HASTANESI", "ANKARA GUVEN HASTANESI", "GUVEN SAGLIK"),
                    ),
                "lokman_hekim" to
                    Triple(
                        "Lokman Hekim",
                        "lokman_hekim",
                        setOf("LOKMAN HEKIM", "LOKMAN HEKIM HASTANESI"),
                    ),
                "baskent_hastanesi" to
                    Triple(
                        "Başkent Hastanesi",
                        "baskent_hastanesi",
                        setOf("BASKENT HASTANESI", "BASKENT UNIVERSITESI HASTANESI"),
                    ),
                "synevo" to
                    Triple(
                        "Synevo",
                        "synevo",
                        setOf("SYNEVO", "SYNEVO LABORATUVAR", "SYNEVO LAB"),
                    ),
                "duzen_laboratuvari" to
                    Triple(
                        "Düzen Laboratuvarı",
                        "duzen_laboratuvari",
                        setOf("DUZEN LABORATUVARI", "DUZEN LAB", "DUZEN SAGLIK"),
                    ),
                "dentgroup" to
                    Triple(
                        "DentGroup",
                        "dentgroup",
                        setOf("DENTGROUP", "DENT GROUP", "DENTGROUP DIS"),
                    ),
                "hospitadent" to
                    Triple(
                        "Hospitadent",
                        "hospitadent",
                        setOf("HOSPITADENT", "HOSPITA DENT", "HOSPITADENT DIS"),
                    ),
                "atasun_optik" to
                    Triple(
                        "Atasun Optik",
                        "atasun_optik",
                        setOf("ATASUN OPTIK", "ATASUN", "ATASUN GOZLUK"),
                    ),
            )

        for ((id, expected) in snapshotExpected) {
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            requireNotNull(merchant)
            assertEquals(expected.first, merchant.displayName, "Merchant '$id' displayName korunmalıdır")
            val expectedCategory = if (id == "atasun_optik") "shopping" else "healthcare"
            assertEquals(expectedCategory, merchant.categoryIconKey.key, "Merchant '$id' category '$expectedCategory' olmalıdır")
            assertEquals(expected.second, merchant.verifiedLogoKey, "Merchant '$id' verifiedLogoKey '${expected.second}' olmalıdır")

            val actualAliases = merchant.aliases.map { it.value }.toSet()
            assertEquals(expected.third, actualAliases, "Merchant '$id' alias seti korunmalıdır")
        }
    }

    @Test
    fun educationSuggestedNine_snapshotAndAllowlistContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")

        val educationMerchants = allMerchants.filter { it.categoryIconKey.key == "education" }
        assertEquals(20, educationMerchants.size, "Eğitim kategorisinde tam 20 merchant olmalıdır")
        assertEquals(20, educationMerchants.count { it.verifiedLogoKey != null }, "Eğitim kategorisinde 20 doğrulanmış logo olmalıdır")
        assertEquals(0, educationMerchants.count { it.verifiedLogoKey == null }, "Eğitim kategorisinde 0 fallback merchant olmalıdır")

        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")

        val expectedNineEducation =
            listOf(
                "udemy" to ("Udemy" to "udemy.com"),
                "coursera" to ("Coursera" to "coursera.org"),
                "duolingo" to ("Duolingo" to "duolingo.com"),
                "cambly" to ("Cambly" to "cambly.com"),
                "kunduz" to ("Kunduz" to "kunduz.com"),
                "tonguc_akademi" to ("Tonguç Akademi" to "tongucakademi.com"),
                "okulistik" to ("Okulistik" to "okulistik.com"),
                "morpa_kampus" to ("Morpa Kampüs" to "morpakampus.com"),
                "raunt" to ("Raunt" to "raunt.com"),
            )

        for ((id, pair) in expectedNineEducation) {
            val (displayName, domain) = pair
            val merchant = educationMerchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Eğitim merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals(displayName, merchant.displayName)
            assertEquals("education", merchant.categoryIconKey.key)
            assertEquals(id, merchant.verifiedLogoKey)

            val resolved = MerchantLogoDomainAllowlist.resolveDomain(id)
            assertNotNull(resolved, "Allowlist '$id' için domain döndürmelidir")
            assertEquals(domain, resolved)
            assertTrue(MerchantLogoDomainAllowlist.isAllowlisted(domain), "Domain '$domain' allowlist'te kayıtlı olmalıdır")
        }

        // Benzer / yanlış domainler fail-closed
        val invalidOrUntrustedDomains =
            listOf(
                "udemy.net",
                "coursera.com",
                "duolingo.net",
                "cambly.net",
                "kunduz.net",
                "tongucakademi.net",
                "okulistik.net",
                "morpakampus.net",
                "raunt.net",
            )
        for (invalidDomain in invalidOrUntrustedDomains) {
            assertFalse(
                MerchantLogoDomainAllowlist.isAllowlisted(invalidDomain),
                "Yanlış domain '$invalidDomain' allowlist'te olmamalıdır",
            )
        }

        // Bilinmeyen anahtarlar ve serbest metinler fail-closed
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("unknown_education_key"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("udemy.com"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("https://udemy.com"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("udemy?size=128"))
    }

    @Test
    fun educationAndBookstoresSearchableFifteen_snapshotAndAllowlistContract() {
        val allMerchants = StarterMerchantCatalog.merchants
        assertEquals(234, allMerchants.size, "Toplam merchant sayısı 234 olmalıdır")
        assertEquals(197, allMerchants.count { it.verifiedLogoKey != null }, "Katalog genelinde 197 doğrulanmış logo olmalıdır")
        assertEquals(37, allMerchants.count { it.verifiedLogoKey == null }, "Katalog genelinde 37 fallback merchant olmalıdır")
        assertEquals(191, MerchantLogoDomainAllowlist.size(), "Allowlist boyutu 191 olmalıdır")

        val expectedFifteen =
            listOf(
                "busuu" to ("Busuu" to "busuu.com"),
                "preply" to ("Preply" to "preply.com"),
                "italki" to ("italki" to "italki.com"),
                "skillshare" to ("Skillshare" to "skillshare.com"),
                "derslig" to ("Derslig" to "derslig.com"),
                "benim_hocam" to ("Benim Hocam" to "benimhocam.com"),
                "pegem_akademi" to ("Pegem Akademi" to "pegem.net"),
                "english_time" to ("English Time" to "englishtime.com"),
                "bilge_adam" to ("Bilge Adam" to "bilgeadam.com"),
                "wall_street_english" to ("Wall Street English" to "wse.com.tr"),
                "amerikankultur" to ("Amerikan Kültür" to "amerikankultur.org.tr"),
                "dr" to ("D&R" to "dr.com.tr"),
                "kitapyurdu" to ("Kitapyurdu" to "kitapyurdu.com"),
                "bkm_kitap" to ("BKM Kitap" to "bkmkitap.com"),
                "idefix" to ("Idefix" to "idefix.com"),
            )

        for ((id, pair) in expectedFifteen) {
            val (displayName, domain) = pair
            val merchant = allMerchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Merchant '$id' katalogda bulunmalıdır")
            assertEquals(displayName, merchant.displayName)
            val expectedCategory = if (id in listOf("dr", "kitapyurdu", "bkm_kitap", "idefix")) "shopping" else "education"
            assertEquals(expectedCategory, merchant.categoryIconKey.key)
            assertEquals(id, merchant.verifiedLogoKey)

            val resolved = MerchantLogoDomainAllowlist.resolveDomain(id)
            assertNotNull(resolved, "Allowlist '$id' için domain döndürmelidir")
            assertEquals(domain, resolved)
            assertTrue(MerchantLogoDomainAllowlist.isAllowlisted(domain), "Domain '$domain' allowlist'te kayıtlı olmalıdır")
        }

        // Benzer / yanlış domainler fail-closed
        val invalidOrUntrustedDomains =
            listOf(
                "busuu.net",
                "preply.net",
                "italki.net",
                "skillshare.net",
                "derslig.net",
                "benimhocam.net",
                "pegem.org",
                "englishtime.net",
                "bilgeadam.net",
                "wse.net",
                "amerikankultur.com",
                "dr.com",
                "kitapyurdu.net",
                "bkmkitap.net",
                "idefix.net",
            )
        for (invalidDomain in invalidOrUntrustedDomains) {
            assertFalse(
                MerchantLogoDomainAllowlist.isAllowlisted(invalidDomain),
                "Yanlış domain '$invalidDomain' allowlist'te olmamalıdır",
            )
        }

        // Bilinmeyen anahtarlar ve serbest metinler fail-closed
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("unknown_education_searchable_key"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("dr.com.tr"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("https://dr.com.tr"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("busuu?size=128"))
    }
}
