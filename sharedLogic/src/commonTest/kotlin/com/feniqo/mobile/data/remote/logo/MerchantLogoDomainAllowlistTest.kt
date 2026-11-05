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

    private val expectedFullFortyFourAllowlist: Map<String, String> =
        historicalTwentyTwoAllowlist + marketSixAllowlist + newSixteenFoodDiningAllowlist

    // 1. Doğrulanıp eklenen her stable logo key doğru kanonik domaini döndürür
    @Test
    fun resolvesAllSixteenNewlyAddedFoodDiningKeysCorrectly() {
        for ((key, expectedDomain) in newSixteenFoodDiningAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Key '$key' beklenen domain '$expectedDomain' döndürmeli")
        }
    }

    // 13. Mevcut 28 allowlist kaydı regresyona uğramaz
    @Test
    fun historicalTwentyEightKeys_haveNoRegression() {
        for ((key, expectedDomain) in historicalTwentyTwoAllowlist + marketSixAllowlist) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(key)
            assertEquals(expectedDomain, resolved, "Tarihsel key '$key' regresyona uğramamalı")
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

    // 4. Allowlist size ve exact key set beklentisi günceldir
    @Test
    fun allowlist_sizeAndExactKeys_areExactlyFortyFour() {
        assertEquals(44, MerchantLogoDomainAllowlist.size())
        assertEquals(expectedFullFortyFourAllowlist.keys, MerchantLogoDomainAllowlist.allowlistKeys())
    }

    // 5. Katalogdaki her non-null verifiedLogoKey allowlist’te çözümlenir
    @Test
    fun starterCatalog_everyNonNullVerifiedLogoKey_resolvesInAllowlist() {
        val nonNullMerchants = StarterMerchantCatalog.merchants.filter { it.verifiedLogoKey != null }
        assertEquals(44, nonNullMerchants.size, "Katalogda tam 44 doğrulanmış logo anahtarı bulunmalı")

        for (m in nonNullMerchants) {
            val resolved = MerchantLogoDomainAllowlist.resolveDomain(m.verifiedLogoKey)
            assertNotNull(resolved, "Katalogdaki ${m.id} (${m.verifiedLogoKey}) allowlist'te çözülmelidir")
            assertEquals(expectedFullFortyFourAllowlist[m.verifiedLogoKey], resolved)
        }
    }

    // 6. Eklenen merchant katalogdan aynı stable ID ile bulunur
    @Test
    fun addedMerchants_foundInCatalogWithSameStableId() {
        for (key in newSixteenFoodDiningAllowlist.keys) {
            val merchant = StarterMerchantCatalog.merchants.firstOrNull { it.id == key }
            assertNotNull(merchant, "Eklenen merchant '$key' katalogda stable ID ile bulunmalıdır")
            assertEquals(key, merchant.verifiedLogoKey, "Merchant '$key' verifiedLogoKey alanında kendi stable ID'sini taşımalı")
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
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("istanbulkart"))
        assertNull(MerchantLogoDomainAllowlist.resolveDomain("kahve_diyari"))
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

        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted(null))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted(""))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("google.com"))
        assertFalse(MerchantLogoDomainAllowlist.isAllowlisted("malicious.site"))
    }
}
