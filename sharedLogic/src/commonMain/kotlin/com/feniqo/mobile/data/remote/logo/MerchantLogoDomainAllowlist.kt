package com.feniqo.mobile.data.remote.logo

/**
 * Sağlayıcıdan bağımsız (provider-independent) doğrulanmış alan adı izin listesi (allowlist).
 *
 * Mimari Sözleşmesi:
 * 1. Tek Kaynak (Single Source of Truth): Stable merchant key -> canonical domain eşlemesi
 *    yalnızca bu nesne içinde tanımlanır. Brandfetch, Logo.dev veya gelecekteki herhangi bir
 *    uzak logo sağlayıcısı kendi içinde paralel bir map tutamaz; bu nesneye başvurmalıdır.
 * 2. Kanonik Alan Adları: Tüm domainler lowercase ASCII'dir; scheme (http/https), path, query,
 *    fragment, port veya userInfo içermez.
 * 3. Domain Katmanı İzolasyonu: Domain katmanı sağlayıcı adaptörlerini bilmez; yalnızca
 *    Merchant.verifiedLogoKey taşır.
 * 4. Kullanıcı Girdisi İzolasyonu: Ham kullanıcı metni, raw açıklama veya kullanıcı tarafından
 *    girilmiş URL asla bu allowlist'ten geçirilmez; yalnız doğrulanmış katalog anahtarları çözümlenir.
 */
object MerchantLogoDomainAllowlist {
    private val ALLOWLIST: Map<String, String> =
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
        )

    fun resolveDomain(logoKey: String?): String? {
        if (logoKey.isNullOrBlank()) return null
        val normalized = logoKey.trim().lowercase()
        return ALLOWLIST[normalized]
    }

    fun isAllowlisted(domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        val normalized = domain.trim().lowercase()
        return ALLOWLIST.values.contains(normalized)
    }

    fun allowlistKeys(): Set<String> = ALLOWLIST.keys

    fun size(): Int = ALLOWLIST.size
}
