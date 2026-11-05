package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantCategoryRecommendationProfile
import com.feniqo.mobile.domain.model.TransactionType

/**
 * Kategoriye göre merchant öneri ve arama adaylarını tek merkezden yöneten saf ve deterministik domain indeksi.
 *
 * Sözleşme kuralları:
 * - Kategori eşleşmesi localized adla değil, kararlı [CategoryIcon] semantik anahtarıyla yapılır.
 * - Yalnız [ExpenseCategoryVisualCatalog] içindeki kararlı anahtarlar kabul edilir.
 * - Her merchant ID [StarterMerchantCatalog.merchants] içinde bulunmalıdır; bilinmeyen ID fail-closed reddedilir.
 * - Öneriler [MerchantCategoryRecommendationProfile.MAX_SUGGESTED_COUNT] (8) ile sınırlıdır ve aranabilir listenin alt kümesidir.
 * - Mükerrer ID içeremez.
 * - Aynı merchant birden fazla kategori profilinde bulunabilir (örn. travel kayıtlarının transportation'da yer alması).
 * - Aynı girdi için her zaman deterministik sıra üretilir.
 * - Bilinmeyen, null veya boş kategoriler için güvenli boş liste döner.
 * - Gelir ([TransactionType.INCOME]) işlemleri için hiçbir öneri veya arama adayı üretilmez.
 * - Otomatik seçim veya durum mutasyonu üretmez.
 */
object MerchantCategoryRecommendationIndex {
    private val PRODUCTION_PROFILES: Map<String, MerchantCategoryRecommendationProfile> =
        listOf(
            profile(
                key = "groceries",
                suggested = listOf("bim", "a101", "sok", "migros", "carrefoursa", "file_market", "tarim_kredi", "getir"),
                searchable =
                    listOf(
                        "bim",
                        "a101",
                        "sok",
                        "migros",
                        "carrefoursa",
                        "file_market",
                        "tarim_kredi",
                        "getir",
                        "migros_sanal_market",
                        "macrocenter",
                        "yemeksepeti_market",
                        "bizim_toptan",
                        "metro_turkiye",
                        "istegelsin",
                        "onur_market",
                        "happy_center",
                        "hakmar",
                        "kim_market",
                        "cagri_market",
                        "ozkuruslar",
                        "sec_market",
                    ),
            ),
            profile(
                key = "food_dining",
                suggested =
                    listOf(
                        "yemeksepeti",
                        "trendyol_yemek",
                        "getiryemek",
                        "migros_yemek",
                        "starbucks",
                        "mcdonalds",
                        "burger_king",
                        "kahve_dunyasi",
                    ),
                searchable =
                    listOf(
                        "yemeksepeti",
                        "trendyol_yemek",
                        "getiryemek",
                        "migros_yemek",
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
                    ),
            ),
            profile(
                key = "shopping",
                suggested = listOf("trendyol", "hepsiburada", "amazon", "n11", "ciceksepeti", "boyner", "lc_waikiki", "defacto"),
                searchable =
                    listOf(
                        "trendyol",
                        "hepsiburada",
                        "amazon",
                        "n11",
                        "ciceksepeti",
                        "boyner",
                        "lc_waikiki",
                        "defacto",
                        "pazarama",
                        "teknosa",
                        "mediamarkt",
                        "flo",
                        "koton",
                        "mavi",
                        "zara",
                        "hm",
                        "pull_and_bear",
                        "bershka",
                        "stradivarius",
                        "beymen",
                        "vakko",
                        "ikea",
                        "koctas",
                        "bauhaus",
                        "vatan_bilgisayar",
                        "ebebek",
                    ),
            ),
            profile(
                key = "subscriptions",
                suggested = listOf("netflix", "spotify", "youtube_premium", "amazon_prime", "disney_plus", "max", "exxen", "gain"),
                searchable =
                    listOf(
                        "netflix",
                        "spotify",
                        "youtube_premium",
                        "amazon_prime",
                        "disney_plus",
                        "max",
                        "exxen",
                        "gain",
                        "mubi",
                        "tod",
                        "tabii",
                        "apple_services",
                        "google_one",
                        "microsoft_365",
                        "adobe",
                        "canva",
                        "playstation_plus",
                        "xbox_game_pass",
                    ),
            ),
            profile(
                key = "transportation",
                suggested =
                    listOf(
                        "istanbulkart",
                        "tcdd",
                        "uber",
                        "bitaksi",
                        "marti",
                        "ispark",
                        "tiktak",
                        "kamil_koc",
                    ),
                searchable =
                    listOf(
                        "istanbulkart",
                        "tcdd",
                        "uber",
                        "bitaksi",
                        "marti",
                        "ispark",
                        "tiktak",
                        "kamil_koc",
                        "getir_arac",
                        "metro_turizm",
                        "pamukkale_turizm",
                        "varan_turizm",
                        "ido",
                        "budo",
                        "thy",
                        "pegasus",
                        "ajet",
                        "sunexpress",
                        "corendon",
                        "obilet",
                        "enuygun",
                    ),
            ),
            profile(
                key = "travel",
                suggested =
                    listOf(
                        "thy",
                        "pegasus",
                        "ajet",
                        "sunexpress",
                        "obilet",
                        "enuygun",
                        "booking",
                        "airbnb",
                    ),
                searchable =
                    listOf(
                        "thy",
                        "pegasus",
                        "ajet",
                        "sunexpress",
                        "corendon",
                        "obilet",
                        "enuygun",
                        "booking",
                        "airbnb",
                        "ets_tur",
                        "jolly_tur",
                        "tatilbudur",
                        "setur",
                    ),
            ),
            profile(
                key = "utilities",
                suggested =
                    listOf(
                        "turkcell",
                        "vodafone",
                        "turk_telekom",
                        "superonline",
                        "turknet",
                        "turksat_kablo",
                        "enerjisa",
                        "igdas",
                    ),
                searchable =
                    listOf(
                        "turkcell",
                        "vodafone",
                        "turk_telekom",
                        "superonline",
                        "turknet",
                        "millenicom",
                        "netspeed",
                        "turksat_kablo",
                        "comnet",
                        "enerjisa",
                        "ck_enerji",
                        "gediz_elektrik",
                        "aydem_enerji",
                        "iski",
                        "aski_ankara",
                        "izsu",
                        "igdas",
                        "baskentgaz",
                        "enerya",
                    ),
            ),
            profile(
                key = "fuel",
                suggested = listOf("shell", "opet", "petrol_ofisi", "totalenergies", "aytemiz"),
                searchable = listOf("shell", "opet", "petrol_ofisi", "totalenergies", "aytemiz"),
            ),
            profile(
                key = "personal_care",
                suggested =
                    listOf(
                        "gratis",
                        "watsons",
                        "rossmann",
                        "sephora",
                        "eve_shop",
                        "flormar",
                        "golden_rose",
                        "yves_rocher",
                    ),
                searchable =
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
                    ),
            ),
            // Henüz uygun merchant bulunmayan kategoriler açıkça boş profil taşır
            emptyProfile("housing"),
            emptyProfile("healthcare"),
            emptyProfile("entertainment"),
            emptyProfile("education"),
            emptyProfile("family_pets"),
            emptyProfile("financial_expenses"),
            emptyProfile("taxes_fees"),
            emptyProfile("gifts_donations"),
            emptyProfile("other_expense"),
        ).associateBy { it.categoryIconKey.key }

    init {
        // Üretim profillerinin tamamı StarterMerchantCatalog'a göre fail-closed doğrulanır
        val catalogIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()
        PRODUCTION_PROFILES.values.forEach { profile ->
            validateProfileAgainstCatalog(profile, catalogIds)
        }
    }

    val profiles: Map<String, MerchantCategoryRecommendationProfile> = PRODUCTION_PROFILES

    fun getProfile(categoryIconKey: CategoryIcon): MerchantCategoryRecommendationProfile? = getProfile(categoryIconKey.key)

    fun getProfile(categoryIconKey: String?): MerchantCategoryRecommendationProfile? {
        val normalized = categoryIconKey?.trim()?.lowercase()
        if (normalized.isNullOrEmpty()) return null
        return PRODUCTION_PROFILES[normalized]
    }

    fun getProfile(
        transactionType: TransactionType,
        categoryIconKey: CategoryIcon?,
    ): MerchantCategoryRecommendationProfile? = getProfile(transactionType, categoryIconKey?.key)

    fun getProfile(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): MerchantCategoryRecommendationProfile? {
        if (transactionType != TransactionType.EXPENSE) return null
        return getProfile(categoryIconKey)
    }

    fun getProfile(category: Category?): MerchantCategoryRecommendationProfile? {
        if (category == null) return null
        return getProfile(category.type, category.icon?.key)
    }

    fun resolveSuggestedMerchantIds(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): List<EntityId> = getProfile(transactionType, categoryIconKey)?.suggestedMerchantIds ?: emptyList()

    fun resolveSuggestedMerchantIds(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
    ): List<EntityId> = resolveSuggestedMerchantIds(transactionType, categoryIcon?.key)

    fun resolveSuggestedMerchantIds(category: Category?): List<EntityId> = getProfile(category)?.suggestedMerchantIds ?: emptyList()

    fun resolveSearchableMerchantIds(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): List<EntityId> = getProfile(transactionType, categoryIconKey)?.searchableMerchantIds ?: emptyList()

    fun resolveSearchableMerchantIds(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
    ): List<EntityId> = resolveSearchableMerchantIds(transactionType, categoryIcon?.key)

    fun resolveSearchableMerchantIds(category: Category?): List<EntityId> = getProfile(category)?.searchableMerchantIds ?: emptyList()

    fun resolveSuggestedMerchants(
        transactionType: TransactionType,
        categoryIconKey: String?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> {
        val ids = resolveSuggestedMerchantIds(transactionType, categoryIconKey)
        if (ids.isEmpty()) return emptyList()
        val catalogById = catalog.associateBy { it.id }
        return ids.map { entityId ->
            catalogById[entityId.value]
                ?: throw IllegalArgumentException(
                    "Önerilen merchant katalogda bulunamadı (fail-closed): ${entityId.value}",
                )
        }
    }

    fun resolveSuggestedMerchants(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> = resolveSuggestedMerchants(transactionType, categoryIcon?.key, catalog)

    fun resolveSuggestedMerchants(
        category: Category?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> {
        if (category == null) return emptyList()
        return resolveSuggestedMerchants(category.type, category.icon?.key, catalog)
    }

    fun resolveSearchableMerchants(
        transactionType: TransactionType,
        categoryIconKey: String?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> {
        val ids = resolveSearchableMerchantIds(transactionType, categoryIconKey)
        if (ids.isEmpty()) return emptyList()
        val catalogById = catalog.associateBy { it.id }
        return ids.map { entityId ->
            catalogById[entityId.value]
                ?: throw IllegalArgumentException(
                    "Aranabilir merchant katalogda bulunamadı (fail-closed): ${entityId.value}",
                )
        }
    }

    fun resolveSearchableMerchants(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> = resolveSearchableMerchants(transactionType, categoryIcon?.key, catalog)

    fun resolveSearchableMerchants(
        category: Category?,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<Merchant> {
        if (category == null) return emptyList()
        return resolveSearchableMerchants(category.type, category.icon?.key, catalog)
    }

    /**
     * Verilen profili ve merchant kimliklerini kataloğa karşı fail-closed doğrular.
     * Katalogda bulunmayan herhangi bir merchant ID durumunda [IllegalArgumentException] fırlatır.
     */
    fun validateProfile(
        profile: MerchantCategoryRecommendationProfile,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ) {
        val catalogIds = catalog.map { it.id }.toSet()
        validateProfileAgainstCatalog(profile, catalogIds)
    }

    private fun validateProfileAgainstCatalog(
        profile: MerchantCategoryRecommendationProfile,
        catalogIds: Set<String>,
    ) {
        profile.suggestedMerchantIds.forEach { entityId ->
            require(entityId.value in catalogIds) {
                "Önerilen merchant ID katalogda bulunamadı (fail-closed): ${entityId.value} " +
                    "(kategori: ${profile.categoryIconKey.key})"
            }
        }
        profile.searchableMerchantIds.forEach { entityId ->
            require(entityId.value in catalogIds) {
                "Aranabilir merchant ID katalogda bulunamadı (fail-closed): ${entityId.value} " +
                    "(kategori: ${profile.categoryIconKey.key})"
            }
        }
    }

    private fun profile(
        key: String,
        suggested: List<String>,
        searchable: List<String>,
    ): MerchantCategoryRecommendationProfile =
        MerchantCategoryRecommendationProfile(
            categoryIconKey = CategoryIcon(key),
            suggestedMerchantIds = suggested.map(::EntityId),
            searchableMerchantIds = searchable.map(::EntityId),
        )

    private fun emptyProfile(key: String): MerchantCategoryRecommendationProfile =
        MerchantCategoryRecommendationProfile.empty(CategoryIcon(key))
}
