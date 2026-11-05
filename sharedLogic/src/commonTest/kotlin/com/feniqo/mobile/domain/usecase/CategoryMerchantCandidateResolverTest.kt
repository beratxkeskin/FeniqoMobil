package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ExpenseCategoryVisualCatalog
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryMerchantCandidateResolverTest {
    private val resolver = CategoryMerchantCandidateResolver()

    @Test
    fun groceries_suggestedOrder_exactMatchWithIndex() {
        val expectedSuggested =
            listOf(
                "bim",
                "a101",
                "sok",
                "migros",
                "carrefoursa",
                "file_market",
                "tarim_kredi",
                "getir",
            )

        val candidates =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
            )

        assertEquals(expectedSuggested, candidates.map { it.id })
        assertEquals(8, candidates.size)
        val indexIds =
            MerchantCategoryRecommendationIndex
                .resolveSuggestedMerchantIds(TransactionType.EXPENSE, "groceries")
                .map { it.value }
        assertEquals(indexIds, candidates.map { it.id })
    }

    @Test
    fun groceries_searchableOrder_exactMatchWithIndex() {
        val expectedSearchable =
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
            )

        val candidates =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
            )

        assertEquals(expectedSearchable, candidates.map { it.id })
        assertEquals(21, candidates.size)

        // resolveCandidates geriye uyumluluk delegasyonu da aynı sırayı üretmelidir
        val legacyCandidates =
            resolver.resolveCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
            )
        assertEquals(expectedSearchable, legacyCandidates.map { it.id })

        val indexIds =
            MerchantCategoryRecommendationIndex
                .resolveSearchableMerchantIds(TransactionType.EXPENSE, "groceries")
                .map { it.value }
        assertEquals(indexIds, candidates.map { it.id })
    }

    @Test
    fun suggestedCandidates_maxCountIsEightAcrossAllCategories() {
        ExpenseCategoryVisualCatalog.definitions.forEach { def ->
            val suggested =
                resolver.resolveSuggestedCandidates(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = def.key.key,
                )
            assertTrue(
                suggested.size <= 8,
                "Önerilen aday sayısı en fazla 8 olabilir: ${def.key.key} (${suggested.size})",
            )
        }
    }

    @Test
    fun fuelCandidates_resolvesFiveExistingRecords() {
        val expectedFuel = listOf("shell", "opet", "petrol_ofisi", "totalenergies", "aytemiz")

        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "fuel",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "fuel",
            )

        assertEquals(expectedFuel, suggested.map { it.id })
        assertEquals(expectedFuel, searchable.map { it.id })
        assertEquals(5, suggested.size)
        assertEquals(5, searchable.size)

        val shell = StarterMerchantCatalog.merchants.first { it.id == "shell" }
        assertTrue(CategoryMerchantCandidateResolver.isCompatible("fuel", shell))
    }

    @Test
    fun utilities_nineteenMerchants_candidateOrderAndCompatibility() {
        val expectedSuggested =
            listOf(
                "turkcell",
                "vodafone",
                "turk_telekom",
                "superonline",
                "turknet",
                "turksat_kablo",
                "enerjisa",
                "igdas",
            )
        val expectedSearchable =
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
            )

        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "utilities",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "utilities",
            )

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested.map { it.id })

        assertEquals(19, searchable.size)
        assertEquals(expectedSearchable, searchable.map { it.id })

        expectedSearchable.forEach { merchantId ->
            val m = StarterMerchantCatalog.merchants.first { it.id == merchantId }
            assertTrue(CategoryMerchantCandidateResolver.isCompatible("utilities", m))
            assertFalse(CategoryMerchantCandidateResolver.isCompatible("subscriptions", m))
            assertFalse(CategoryMerchantCandidateResolver.isCompatible("shopping", m))
            assertFalse(CategoryMerchantCandidateResolver.isCompatible("transportation", m))
            assertFalse(CategoryMerchantCandidateResolver.isCompatible("housing", m))
            assertFalse(CategoryMerchantCandidateResolver.isCompatible("other_expense", m))
        }
    }

    @Test
    fun transportationAndTravel_airlinesSearchableInBoth() {
        val expectedShared = listOf("thy", "pegasus", "ajet", "sunexpress", "corendon", "obilet", "enuygun")

        val transportationSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "transportation",
            )
        val travelSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "travel",
            )

        val transpIds = transportationSearchable.map { it.id }
        val travelIds = travelSearchable.map { it.id }

        expectedShared.forEach { sharedId ->
            assertTrue(sharedId in transpIds, "$sharedId transportation içinde aranabilir olmalı")
            assertTrue(sharedId in travelIds, "$sharedId travel içinde aranabilir olmalı")
        }

        val transportationSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "transportation",
            )
        assertEquals(
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
            transportationSuggested.map { it.id },
        )
    }

    @Test
    fun thy_isCompatible_trueForBothTransportationAndTravel() {
        val thy = StarterMerchantCatalog.merchants.first { it.id == "thy" }

        assertTrue(CategoryMerchantCandidateResolver.isCompatible("transportation", thy))
        assertTrue(CategoryMerchantCandidateResolver.isCompatible("travel", thy))
    }

    @Test
    fun migros_isCompatible_falseForShopping() {
        val migros = StarterMerchantCatalog.merchants.first { it.id == "migros" }

        assertTrue(CategoryMerchantCandidateResolver.isCompatible("groceries", migros))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible("shopping", migros))
    }

    @Test
    fun unknownOrFakeMerchantId_isCompatible_returnsFalse() {
        val fakeMerchant =
            Merchant(
                id = "fake_merchant_999",
                displayName = "Fake Merchant",
                aliases = listOf(MerchantAlias("FAKE")),
                categoryIconKey = CategoryIcon("groceries"),
                catalogVersion = 1,
            )

        assertFalse(CategoryMerchantCandidateResolver.isCompatible("groceries", fakeMerchant))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible("shopping", fakeMerchant))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible("transportation", fakeMerchant))
    }

    @Test
    fun tamperedMerchantInstance_decisionBasedOnCanonicalStableId() {
        val thy = StarterMerchantCatalog.merchants.first { it.id == "thy" }
        // Çağıran nesne categoryIconKey veya displayName'i manipüle etse dahi karar stable ID ile verilir
        val tamperedThy =
            thy.copy(
                displayName = "Değiştirilmiş THY",
                categoryIconKey = CategoryIcon("groceries"),
            )

        assertTrue(
            CategoryMerchantCandidateResolver.isCompatible("transportation", tamperedThy),
            "Kanonik thy transportation ile uyumlu olmalıdır.",
        )
        assertTrue(
            CategoryMerchantCandidateResolver.isCompatible("travel", tamperedThy),
            "Kanonik thy travel ile uyumlu olmalıdır.",
        )
        assertFalse(
            CategoryMerchantCandidateResolver.isCompatible("groceries", tamperedThy),
            "Nesne üzerindeki sahte groceries ikonuna güvenilmemelidir.",
        )
    }

    @Test
    fun incomeNullBlankUnknownAndLocalizedCategory_returnEmpty() {
        // 1. INCOME her zaman boş liste döner
        val incomeSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "groceries",
            )
        val incomeSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "groceries",
            )
        assertTrue(incomeSuggested.isEmpty())
        assertTrue(incomeSearchable.isEmpty())

        // 2. Null, boş veya whitespace
        assertTrue(resolver.resolveSuggestedCandidates(TransactionType.EXPENSE, null as String?).isEmpty())
        assertTrue(resolver.resolveSearchableCandidates(TransactionType.EXPENSE, "").isEmpty())
        assertTrue(resolver.resolveSearchableCandidates(TransactionType.EXPENSE, "   ").isEmpty())

        // 3. Bilinmeyen ikon
        assertTrue(resolver.resolveSuggestedCandidates(TransactionType.EXPENSE, "unknown_icon").isEmpty())
        assertTrue(resolver.resolveSearchableCandidates(TransactionType.EXPENSE, "unknown_icon").isEmpty())

        // 4. Localized Türkçe kategori adları eşleşmemelidir
        val turkishNames = listOf("Market", "Ulaşım", "Faturalar", "Akaryakıt", "Alışveriş")
        turkishNames.forEach { name ->
            assertTrue(resolver.resolveSuggestedCandidates(TransactionType.EXPENSE, name).isEmpty())
            assertTrue(resolver.resolveSearchableCandidates(TransactionType.EXPENSE, name).isEmpty())
            val thy = StarterMerchantCatalog.merchants.first { it.id == "thy" }
            assertFalse(CategoryMerchantCandidateResolver.isCompatible(name, thy))
        }

        // 5. isCompatible girdileri null veya boş
        val thy = StarterMerchantCatalog.merchants.first { it.id == "thy" }
        assertFalse(CategoryMerchantCandidateResolver.isCompatible(null as String?, thy))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible(null as CategoryIcon?, thy))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible(null as Category?, thy))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible("", thy))
        assertFalse(CategoryMerchantCandidateResolver.isCompatible("transportation", null as Merchant?))
    }

    @Test
    fun emptyProfileCategories_returnEmpty() {
        val emptyCategories = listOf("housing", "healthcare", "education", "family_pets", "other_expense")
        val thy = StarterMerchantCatalog.merchants.first { it.id == "thy" }

        emptyCategories.forEach { catKey ->
            val suggested =
                resolver.resolveSuggestedCandidates(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = catKey,
                )
            val searchable =
                resolver.resolveSearchableCandidates(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = catKey,
                )
            assertTrue(suggested.isEmpty(), "$catKey için öneri boş olmalı")
            assertTrue(searchable.isEmpty(), "$catKey için arama boş olmalı")
            assertFalse(CategoryMerchantCandidateResolver.isCompatible(catKey, thy))
        }
    }

    @Test
    fun deterministicOrder_returnsIdenticalOrderAcrossInvocations() {
        val firstSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "subscriptions",
            )
        val secondSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "subscriptions",
            )
        assertEquals(firstSuggested.map { it.id }, secondSuggested.map { it.id })

        val firstSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "subscriptions",
            )
        val secondSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "subscriptions",
            )
        assertEquals(firstSearchable.map { it.id }, secondSearchable.map { it.id })
    }

    @Test
    fun instanceAndStaticParity() {
        val instanceSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        val staticSuggested =
            CategoryMerchantCandidateResolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        assertEquals(instanceSuggested.map { it.id }, staticSuggested.map { it.id })

        val instanceSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        val staticSearchable =
            CategoryMerchantCandidateResolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        assertEquals(instanceSearchable.map { it.id }, staticSearchable.map { it.id })

        val legacyInstance =
            resolver.resolveCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        val legacyStatic =
            CategoryMerchantCandidateResolver.resolveCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        assertEquals(legacyInstance.map { it.id }, legacyStatic.map { it.id })
    }

    @Test
    fun typedCategoryModelOverloads_delegateCorrectly() {
        val validCategory =
            createCategory(
                id = "cat-1",
                name = "Market",
                type = TransactionType.EXPENSE,
                iconKey = "groceries",
            )
        val suggested = resolver.resolveSuggestedCandidates(validCategory)
        assertEquals(8, suggested.size)
        val searchable = resolver.resolveSearchableCandidates(validCategory)
        assertEquals(21, searchable.size)

        val migros = StarterMerchantCatalog.merchants.first { it.id == "migros" }
        assertTrue(CategoryMerchantCandidateResolver.isCompatible(validCategory, migros))
        assertTrue(CategoryMerchantCandidateResolver.isCompatible(validCategory.icon, migros))

        val nullCategory: Category? = null
        assertTrue(resolver.resolveSuggestedCandidates(nullCategory).isEmpty())
        assertTrue(resolver.resolveSearchableCandidates(nullCategory).isEmpty())
        assertFalse(CategoryMerchantCandidateResolver.isCompatible(nullCategory, migros))
    }

    @Test
    fun starterMerchantCatalog_containsNoDuplicateMerchantIds() {
        val ids = StarterMerchantCatalog.merchants.map { it.id }
        assertEquals(ids.distinct().size, ids.size, "StarterMerchantCatalog contains duplicate IDs: $ids")
    }

    private fun createCategory(
        id: String,
        name: String,
        type: TransactionType,
        iconKey: String?,
    ): Category =
        Category(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = name,
            type = type,
            color = CategoryColor("#EF4444"),
            icon = iconKey?.let(::CategoryIcon),
            isDefault = false,
            createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        )

    @Test
    fun newGroceriesMerchants_compatibilityRules() {
        val newTenIds =
            listOf(
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
            )

        newTenIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("transportation", merchant),
                "$id transportation ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun foodDiningExpansion_resolverAndCompatibilityRules() {
        val expectedSuggested =
            listOf(
                "yemeksepeti",
                "trendyol_yemek",
                "getiryemek",
                "migros_yemek",
                "starbucks",
                "mcdonalds",
                "burger_king",
                "kahve_dunyasi",
            )
        val expectedSearchable =
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
            )

        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "food_dining",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "food_dining",
            )

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested.map { it.id })

        assertEquals(20, searchable.size)
        assertEquals(expectedSearchable, searchable.map { it.id })

        // Income her zaman boş liste döner
        val incomeSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "food_dining",
            )
        val incomeSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "food_dining",
            )
        assertTrue(incomeSuggested.isEmpty())
        assertTrue(incomeSearchable.isEmpty())

        // 15 yeni merchant food_dining ile uyumlu, diğer kategorilerle uyumsuz
        val newFifteenIds =
            listOf(
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

        newFifteenIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("food_dining", merchant),
                "$id food_dining ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("transportation", merchant),
                "$id transportation ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun foodDining_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "food_dining",
                catalog = reversedCatalog,
            )
        assertEquals(20, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun foodDining_missingMerchantInCatalog_failsClosed() {
        val incompleteCatalog = StarterMerchantCatalog.merchants.filterNot { it.id == "kofteci_yusuf" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "food_dining",
                catalog = incompleteCatalog,
            )
        }
    }

    @Test
    fun shopping_fifteenNewMerchants_candidateOrderAndCompatibility() {
        val expectedSuggested =
            listOf(
                "trendyol",
                "hepsiburada",
                "amazon",
                "n11",
                "ciceksepeti",
                "boyner",
                "lc_waikiki",
                "defacto",
            )
        val expectedSearchable =
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
            )

        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "shopping",
            )

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested.map { it.id })

        assertEquals(26, searchable.size)
        assertEquals(expectedSearchable, searchable.map { it.id })

        // Income her zaman boş liste döner
        val incomeSuggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "shopping",
            )
        val incomeSearchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.INCOME,
                categoryIconKey = "shopping",
            )
        assertTrue(incomeSuggested.isEmpty())
        assertTrue(incomeSearchable.isEmpty())

        // 15 yeni merchant shopping ile uyumlu, diğer kategorilerle uyumsuz
        val newFifteenIds =
            listOf(
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
            )

        newFifteenIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("food_dining", merchant),
                "$id food_dining ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("transportation", merchant),
                "$id transportation ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun shopping_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "shopping",
                catalog = reversedCatalog,
            )
        assertEquals(26, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun shopping_missingMerchantInCatalog_failsClosed() {
        val incompleteCatalog = StarterMerchantCatalog.merchants.filterNot { it.id == "vatan_bilgisayar" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "shopping",
                catalog = incompleteCatalog,
            )
        }
    }

    @Test
    fun personalCare_fifteenNewMerchants_candidateOrderAndCompatibility() {
        val resolver = CategoryMerchantCandidateResolver()
        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "personal_care",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "personal_care",
            )

        val expectedSuggested =
            listOf(
                "gratis",
                "watsons",
                "rossmann",
                "sephora",
                "eve_shop",
                "flormar",
                "golden_rose",
                "yves_rocher",
            )

        val expectedSearchable =
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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested.map { it.id })

        assertEquals(15, searchable.size)
        assertEquals(expectedSearchable, searchable.map { it.id })

        // 15 yeni merchant personal_care ile uyumlu, diğer kategorilerle uyumsuz
        expectedSearchable.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("personal_care", merchant),
                "$id personal_care ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("food_dining", merchant),
                "$id food_dining ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("healthcare", merchant),
                "$id healthcare ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun personalCare_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "personal_care",
                catalog = reversedCatalog,
            )
        assertEquals(15, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun personalCare_missingMerchantInCatalog_failsClosed() {
        val incompleteCatalog = StarterMerchantCatalog.merchants.filterNot { it.id == "gratis" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "personal_care",
                catalog = incompleteCatalog,
            )
        }
    }

    @Test
    fun utilities_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "utilities",
                catalog = reversedCatalog,
            )
        assertEquals(19, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun utilities_missingTurknetOrIgdasInCatalog_failsClosed() {
        val withoutTurknet = StarterMerchantCatalog.merchants.filterNot { it.id == "turknet" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "utilities",
                catalog = withoutTurknet,
            )
        }

        val withoutIgdas = StarterMerchantCatalog.merchants.filterNot { it.id == "igdas" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "utilities",
                catalog = withoutIgdas,
            )
        }
    }

    @Test
    fun transportation_candidateOrderAndCompatibility() {
        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "transportation",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "transportation",
            )

        val expectedSuggested =
            listOf(
                "istanbulkart",
                "tcdd",
                "uber",
                "bitaksi",
                "marti",
                "ispark",
                "tiktak",
                "kamil_koc",
            )

        val expectedSearchable =
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
            )

        assertEquals(expectedSuggested, suggested.map { it.id })
        assertEquals(expectedSearchable, searchable.map { it.id })

        val transportationOnlyIds =
            listOf(
                "istanbulkart",
                "ispark",
                "tiktak",
                "getir_arac",
                "kamil_koc",
                "metro_turizm",
                "pamukkale_turizm",
                "varan_turizm",
                "ido",
                "budo",
            )

        transportationOnlyIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("transportation", merchant),
                "$id transportation ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("travel", merchant),
                "$id travel ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("utilities", merchant),
                "$id utilities ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("personal_care", merchant),
                "$id personal_care ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun travel_candidateOrderAndCompatibility() {
        val suggested =
            resolver.resolveSuggestedCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "travel",
            )
        val searchable =
            resolver.resolveSearchableCandidates(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "travel",
            )

        val expectedSuggested =
            listOf(
                "thy",
                "pegasus",
                "ajet",
                "sunexpress",
                "obilet",
                "enuygun",
                "booking",
                "airbnb",
            )

        val expectedSearchable =
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
            )

        assertEquals(expectedSuggested, suggested.map { it.id })
        assertEquals(expectedSearchable, searchable.map { it.id })

        val travelOnlyIds =
            listOf(
                "booking",
                "airbnb",
                "ets_tur",
                "jolly_tur",
                "tatilbudur",
                "setur",
            )

        travelOnlyIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("travel", merchant),
                "$id travel ile uyumlu olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("transportation", merchant),
                "$id transportation ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("utilities", merchant),
                "$id utilities ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır.",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("personal_care", merchant),
                "$id personal_care ile uyumsuz olmalıdır.",
            )
        }
    }

    @Test
    fun transportation_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "transportation",
                catalog = reversedCatalog,
            )
        assertEquals(21, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun travel_reversedCatalog_preservesDeterministicProfileOrder() {
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolved =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "travel",
                catalog = reversedCatalog,
            )
        assertEquals(13, resolved.size)
        assertEquals(
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
            resolved.map { it.id },
        )
    }

    @Test
    fun transportation_missingMerchantInCatalog_failsClosed() {
        val incompleteCatalog = StarterMerchantCatalog.merchants.filterNot { it.id == "istanbulkart" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "transportation",
                catalog = incompleteCatalog,
            )
        }
    }

    @Test
    fun travel_missingMerchantInCatalog_failsClosed() {
        val incompleteCatalog = StarterMerchantCatalog.merchants.filterNot { it.id == "ajet" }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "travel",
                catalog = incompleteCatalog,
            )
        }
    }
}
