package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.ExpenseCategoryVisualCatalog
import com.feniqo.mobile.domain.model.MerchantCategoryRecommendationProfile
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantCategoryRecommendationIndexTest {
    @Test
    fun maxNineSuggestedMerchants_enforced() {
        MerchantCategoryRecommendationIndex.profiles.values.forEach { profile ->
            assertTrue(
                profile.suggestedMerchantIds.size <= 9,
                "Profil en fazla 9 öneri içerebilir: ${profile.categoryIconKey.key} " +
                    "(${profile.suggestedMerchantIds.size})",
            )
        }

        // 10 öneri içeren profil oluşturulması fail-closed reddedilir
        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("groceries"),
                suggestedMerchantIds = (1..10).map { EntityId("id_$it") },
                searchableMerchantIds = (1..10).map { EntityId("id_$it") },
            )
        }
    }

    @Test
    fun suggestedIsSubsetOfSearchable_enforced() {
        MerchantCategoryRecommendationIndex.profiles.values.forEach { profile ->
            val searchableSet = profile.searchableMerchantIds.toSet()
            profile.suggestedMerchantIds.forEach { suggestedId ->
                assertTrue(
                    suggestedId in searchableSet,
                    "Öneri ID'si (${suggestedId.value}) arama listesinde bulunmalı: ${profile.categoryIconKey.key}",
                )
            }
        }

        // Öneri listesinde olup aranabilir listede olmayan ID fail-closed reddedilir
        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("groceries"),
                suggestedMerchantIds = listOf(EntityId("bim"), EntityId("unknown_merchant")),
                searchableMerchantIds = listOf(EntityId("bim")),
            )
        }
    }

    @Test
    fun duplicateIdsInList_rejected() {
        // suggestedMerchantIds içinde mükerrer ID reddedilir
        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("groceries"),
                suggestedMerchantIds = listOf(EntityId("bim"), EntityId("bim")),
                searchableMerchantIds = listOf(EntityId("bim")),
            )
        }

        // searchableMerchantIds içinde mükerrer ID reddedilir
        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("groceries"),
                suggestedMerchantIds = listOf(EntityId("bim")),
                searchableMerchantIds = listOf(EntityId("bim"), EntityId("bim")),
            )
        }
    }

    @Test
    fun unknownMerchantId_failsClosed() {
        val invalidProfile =
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("groceries"),
                suggestedMerchantIds = listOf(EntityId("non_existent_merchant_xyz")),
                searchableMerchantIds = listOf(EntityId("non_existent_merchant_xyz")),
            )

        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationIndex.validateProfile(invalidProfile)
        }
    }

    @Test
    fun unknownCategory_returnsEmptyListAndNullProfile() {
        assertNull(MerchantCategoryRecommendationIndex.getProfile("unknown_category"))
        assertNull(MerchantCategoryRecommendationIndex.getProfile(TransactionType.EXPENSE, "unknown_category"))
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "unknown_category",
            )
        assertTrue(suggestedIds.isEmpty())
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "unknown_category",
            )
        assertTrue(searchableIds.isEmpty())
        val suggestedMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "unknown_category",
            )
        assertTrue(suggestedMerchants.isEmpty())
        val searchableMerchants =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "unknown_category",
            )
        assertTrue(searchableMerchants.isEmpty())
    }

    @Test
    fun nullOrBlankCategory_returnsEmptyListAndNullProfile() {
        listOf(null, "", "   ").forEach { blankKey ->
            assertNull(MerchantCategoryRecommendationIndex.getProfile(blankKey))
            assertNull(MerchantCategoryRecommendationIndex.getProfile(TransactionType.EXPENSE, blankKey))
            val suggestedIds =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                    TransactionType.EXPENSE,
                    blankKey,
                )
            assertTrue(suggestedIds.isEmpty())
            val searchableIds =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                    TransactionType.EXPENSE,
                    blankKey,
                )
            assertTrue(searchableIds.isEmpty())
            val suggestedMerchants =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                    TransactionType.EXPENSE,
                    blankKey,
                )
            assertTrue(suggestedMerchants.isEmpty())
            val searchableMerchants =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                    TransactionType.EXPENSE,
                    blankKey,
                )
            assertTrue(searchableMerchants.isEmpty())
        }
    }

    @Test
    fun incomeTransaction_returnsNoRecommendations() {
        listOf("groceries", "food_dining", "subscriptions", "fuel", "utilities").forEach { categoryKey ->
            assertNull(
                MerchantCategoryRecommendationIndex.getProfile(TransactionType.INCOME, categoryKey),
                "Gelir işlemi için profil null dönmeli: $categoryKey",
            )
            val suggestedIds =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                    TransactionType.INCOME,
                    categoryKey,
                )
            assertTrue(
                suggestedIds.isEmpty(),
                "Gelir işlemi için suggested merchant ID listesi boş dönmeli: $categoryKey",
            )
            val searchableIds =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                    TransactionType.INCOME,
                    categoryKey,
                )
            assertTrue(
                searchableIds.isEmpty(),
                "Gelir işlemi için searchable merchant ID listesi boş dönmeli: $categoryKey",
            )
            val suggestedMerchants =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                    TransactionType.INCOME,
                    categoryKey,
                )
            assertTrue(
                suggestedMerchants.isEmpty(),
                "Gelir işlemi için suggested merchant listesi boş dönmeli: $categoryKey",
            )
            val searchableMerchants =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                    TransactionType.INCOME,
                    categoryKey,
                )
            assertTrue(
                searchableMerchants.isEmpty(),
                "Gelir işlemi için searchable merchant listesi boş dönmeli: $categoryKey",
            )
            val incomeSuggestedWithEmptyCatalog =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                    TransactionType.INCOME,
                    categoryKey,
                    catalog = emptyList(),
                )
            assertTrue(incomeSuggestedWithEmptyCatalog.isEmpty())
            val incomeSearchableWithEmptyCatalog =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                    TransactionType.INCOME,
                    categoryKey,
                    catalog = emptyList(),
                )
            assertTrue(incomeSearchableWithEmptyCatalog.isEmpty())
        }
    }

    @Test
    fun deterministicOrder_returnsIdenticalOrderAcrossInvocations() {
        val firstIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "groceries",
            )
        val secondIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "groceries",
            )
        assertEquals(firstIds, secondIds)

        val firstMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "groceries",
            )
        val secondMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "groceries",
            )
        assertEquals(firstMerchants.map { it.id }, secondMerchants.map { it.id })
    }

    @Test
    fun multiCategoryPresence_sameMerchantInMultipleProfiles() {
        val travelIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "travel",
            )
        val travelSearchable = travelIds.map { it.value }

        val transportationIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "transportation",
            )
        val transportationSearchable = transportationIds.map { it.value }

        // thy, pegasus ve sunexpress hem travel hem transportation aramasında yer almalıdır
        listOf("thy", "pegasus", "sunexpress").forEach { airlineId ->
            assertTrue(
                airlineId in travelSearchable,
                "$airlineId travel profilinde bulunmalı",
            )
            assertTrue(
                airlineId in transportationSearchable,
                "$airlineId transportation profilinde bulunmalı",
            )
        }
    }

    @Test
    fun fuelAndUtilitiesProfiles_resolveCatalogRecords() {
        val fuelIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "fuel",
            )
        val fuelSuggested = fuelIds.map { it.value }
        val expectedFuel = listOf("shell", "opet", "petrol_ofisi", "totalenergies", "aytemiz")
        assertEquals(expectedFuel, fuelSuggested)

        val fuelMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "fuel",
            )
        assertEquals(expectedFuel, fuelMerchants.map { it.id })

        val utilitiesIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "utilities",
            )
        val utilitiesSuggested = utilitiesIds.map { it.value }
        val expectedUtilities =
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
        assertEquals(expectedUtilities, utilitiesSuggested)

        val utilitiesMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "utilities",
            )
        assertEquals(expectedUtilities, utilitiesMerchants.map { it.id })
    }

    @Test
    fun entertainmentProfile_resolvesCatalogRecords() {
        val expectedSuggested =
            listOf(
                "paribu_cineverse",
                "biletix",
                "biletinial",
                "bubilet",
                "passo",
                "steam",
                "epic_games",
                "playstation_store",
            )
        val expectedSearchable =
            listOf(
                "paribu_cineverse",
                "biletix",
                "biletinial",
                "bubilet",
                "passo",
                "mobilet",
                "biletino",
                "bugece",
                "steam",
                "epic_games",
                "playstation_store",
                "xbox_store",
                "riot_games",
                "battle_net",
                "ubisoft_store",
                "cinemarine",
                "vialand",
            )

        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "entertainment",
            )
        assertEquals(expectedSuggested, suggestedIds.map { it.value })

        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "entertainment",
            )
        assertEquals(expectedSearchable, searchableIds.map { it.value })

        val suggestedMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "entertainment",
            )
        assertEquals(expectedSuggested, suggestedMerchants.map { it.id })

        val searchableMerchants =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "entertainment",
            )
        assertEquals(expectedSearchable, searchableMerchants.map { it.id })

        // INCOME türünde fail-closed boş sonuç
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "entertainment",
            )
        assertTrue(incomeSuggested.isEmpty())
        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "entertainment",
            )
        assertTrue(incomeSearchable.isEmpty())
    }

    @Test
    fun emptyCategoryProfiles_returnEmptySafely() {
        val emptyCategoryKeys =
            listOf(
                "financial_expenses",
                "other_expense",
            )

        emptyCategoryKeys.forEach { key ->
            val profile = MerchantCategoryRecommendationIndex.getProfile(key)
            assertNotNull(profile, "Boş kategori için de açık profil nesnesi bulunmalı: $key")
            assertFalse(profile.hasSuggested)
            assertFalse(profile.hasSearchable)
            assertTrue(profile.suggestedMerchantIds.isEmpty())
            assertTrue(profile.searchableMerchantIds.isEmpty())

            val suggestedIds =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                    TransactionType.EXPENSE,
                    key,
                )
            assertTrue(suggestedIds.isEmpty())
            val searchableIds =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                    TransactionType.EXPENSE,
                    key,
                )
            assertTrue(searchableIds.isEmpty())
            val suggestedMerchants =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                    TransactionType.EXPENSE,
                    key,
                )
            assertTrue(suggestedMerchants.isEmpty())
            val searchableMerchants =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                    TransactionType.EXPENSE,
                    key,
                )
            assertTrue(searchableMerchants.isEmpty())
        }
    }

    @Test
    fun localizedCategoryName_notMatched() {
        val turkishLocalizedNames =
            listOf(
                "Market",
                "Yeme & İçme",
                "Faturalar",
                "Akaryakıt",
                "Ulaşım",
                "Alışveriş",
                "Abonelikler",
                "Ev & Kira",
            )

        // 1. Türkçe lokalize ad doğrudan key olarak verildiğinde hiçbir profil ile eşleşmemelidir
        turkishLocalizedNames.forEach { localizedName ->
            assertNull(
                MerchantCategoryRecommendationIndex.getProfile(localizedName),
                "Lokalize kategori adı profil anahtarı olarak kabul edilmemeli: $localizedName",
            )
            assertNull(
                MerchantCategoryRecommendationIndex.getProfile(TransactionType.EXPENSE, localizedName),
                "Lokalize kategori adı profil anahtarı olarak kabul edilmemeli: $localizedName",
            )
            val suggestedIds =
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                    TransactionType.EXPENSE,
                    localizedName,
                )
            assertTrue(suggestedIds.isEmpty())
            val searchableIds =
                MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                    TransactionType.EXPENSE,
                    localizedName,
                )
            assertTrue(searchableIds.isEmpty())
        }

        // 2. Kategori adı "Market" olsa dahi ikonu null ise öneri üretilmemelidir (ada bakılmaz)
        val categoryWithoutIcon =
            Category(
                id = EntityId("cat_no_icon"),
                ownerId = null,
                workspaceId = null,
                name = "Market",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#EF4444"),
                icon = null,
                isDefault = true,
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            )
        assertNull(MerchantCategoryRecommendationIndex.getProfile(categoryWithoutIcon))
        assertTrue(MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(categoryWithoutIcon).isEmpty())

        // 3. Kategori adı "Market" olsa bile ikonu "housing" ise housing profili uygulanır, groceries eşleşmez
        val categoryWithMismatchedIcon =
            categoryWithoutIcon.copy(
                id = EntityId("cat_housing"),
                icon = CategoryIcon("housing"),
            )
        val housingProfile = MerchantCategoryRecommendationIndex.getProfile(categoryWithMismatchedIcon)
        assertNotNull(housingProfile)
        assertEquals(CategoryIcon("housing"), housingProfile.categoryIconKey)
        assertEquals(
            listOf("apsiyon", "yonetimcell", "armut", "senyonet", "mrusta", "pronet", "kale_alarm"),
            housingProfile.suggestedMerchantIds.map { it.value },
        )

        // 4. Farklı lokalize adlara ("Market", "Daily Groceries", "Manav") sahip ancak aynı iconKey'i taşıyan kategoriler
        // ada bakılmaksızın birebir aynı öneri listesini üretir
        val catTr = categoryWithoutIcon.copy(name = "Market & Süpermarket", icon = CategoryIcon("groceries"))
        val catEn = categoryWithoutIcon.copy(name = "Groceries & Daily Essentials", icon = CategoryIcon("groceries"))
        val catCustom = categoryWithoutIcon.copy(name = "Mahalle Bakkalı ve Manavı", icon = CategoryIcon("groceries"))

        val resultTr = MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(catTr)
        val resultEn = MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(catEn)
        val resultCustom = MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(catCustom)

        assertEquals(resultTr, resultEn)
        assertEquals(resultTr, resultCustom)
        assertEquals(8, resultTr.size)
    }

    @Test
    fun profileOrderPreserved_regardlessOfCatalogOrder() {
        // Groceries profilinde sıralama: bim, a101, sok, migros, carrefoursa, file_market, tarim_kredi, getir
        // StarterMerchantCatalog içinde ise migros, carrefoursa, a101, bim, sok şeklinde tanımlıdır.
        val expectedOrder = listOf("bim", "a101", "sok", "migros", "carrefoursa", "file_market", "tarim_kredi", "getir")

        // 1. Varsayılan katalog ile çözümleme
        val defaultMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "groceries",
            )
        assertEquals(expectedOrder, defaultMerchants.map { it.id })

        // 2. Ters çevrilmiş katalog ile çözümleme (katalog sırası değişse bile açık profil sırası korunmalı)
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val merchantsFromReversed =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
                catalog = reversedCatalog,
            )
        assertEquals(
            expectedOrder,
            merchantsFromReversed.map { it.id },
            "Katalog listesi ters çevrilmiş olsa dahi açık profil sırası korunmalıdır.",
        )

        // 3. Searchable açık sıralamasının da ters çevrilmiş katalogda korunduğunu doğrula
        val expectedSearchableOrder =
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
        val searchableFromReversed =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
                catalog = reversedCatalog,
            )
        assertEquals(expectedSearchableOrder, searchableFromReversed.map { it.id })
    }

    @Test
    fun resolveSuggestedMerchants_missingMerchantInCatalog_failsClosed() {
        val catalogWithoutBim = StarterMerchantCatalog.merchants.filterNot { it.id == "bim" }

        val ex =
            assertFailsWith<IllegalArgumentException> {
                MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = "groceries",
                    catalog = catalogWithoutBim,
                )
            }
        assertTrue(
            ex.message?.contains("bim") == true,
            "Hata mesajı eksik merchant ID'sini içermeli: ${ex.message}",
        )
    }

    @Test
    fun resolveSearchableMerchants_missingSearchableOnlyMerchantInCatalog_failsClosed() {
        // yemeksepeti_market yalnız groceries searchable listesinde yer alır, suggested içinde yoktur
        val catalogWithoutSearchableOnly =
            StarterMerchantCatalog.merchants.filterNot { it.id == "yemeksepeti_market" }

        // 1. Suggested listesi eksiksiz olduğu için başarıyla çözülür
        val suggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                transactionType = TransactionType.EXPENSE,
                categoryIconKey = "groceries",
                catalog = catalogWithoutSearchableOnly,
            )
        assertEquals(8, suggested.size)

        // 2. Searchable listesinde eksik kayıt bulunduğu için fail-closed IllegalArgumentException fırlatır
        val ex =
            assertFailsWith<IllegalArgumentException> {
                MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = "groceries",
                    catalog = catalogWithoutSearchableOnly,
                )
            }
        assertTrue(
            ex.message?.contains("yemeksepeti_market") == true,
            "Hata mesajı eksik searchable merchant ID'sini içermeli: ${ex.message}",
        )
    }

    @Test
    fun allProductionProfileMerchantIds_existInStarterCatalog() {
        val allCatalogMerchantIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()

        MerchantCategoryRecommendationIndex.profiles.values.forEach { profile ->
            profile.suggestedMerchantIds.forEach { id ->
                assertTrue(
                    id.value in allCatalogMerchantIds,
                    "Üretim profilindeki suggested ID (${id.value}) StarterMerchantCatalog içinde bulunmalıdır: " +
                        profile.categoryIconKey.key,
                )
            }
            profile.searchableMerchantIds.forEach { id ->
                assertTrue(
                    id.value in allCatalogMerchantIds,
                    "Üretim profilindeki searchable ID (${id.value}) StarterMerchantCatalog içinde bulunmalıdır: " +
                        profile.categoryIconKey.key,
                )
            }
        }
    }

    @Test
    fun allVisualCatalogCategories_haveExplicitProfile() {
        val allVisualKeys = ExpenseCategoryVisualCatalog.definitions.map { it.key.key }.toSet()
        val allProfileKeys = MerchantCategoryRecommendationIndex.profiles.keys

        assertEquals(
            allVisualKeys,
            allProfileKeys,
            "ExpenseCategoryVisualCatalog'daki tüm 18 kategori için açık profil tanımlanmış olmalıdır.",
        )
        assertEquals(18, MerchantCategoryRecommendationIndex.profiles.size)
    }

    @Test
    fun unknownCategoryIconKey_rejectedInProfileCreation() {
        assertFailsWith<IllegalArgumentException> {
            MerchantCategoryRecommendationProfile(
                categoryIconKey = CategoryIcon("unknown_category_icon"),
                suggestedMerchantIds = emptyList(),
                searchableMerchantIds = emptyList(),
            )
        }
    }

    @Test
    fun categoryOverloads_resolveConsistently() {
        val testCategory =
            Category(
                id = EntityId("cat_test"),
                ownerId = null,
                workspaceId = null,
                name = "Yeme & İçme",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#F97316"),
                icon = CategoryIcon("food_dining"),
                isDefault = true,
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            )

        val byCategory = MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(testCategory)
        val byIcon =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                CategoryIcon("food_dining"),
            )
        val byKey =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "food_dining",
            )

        assertEquals(byCategory, byIcon)
        assertEquals(byIcon, byKey)
        assertEquals(
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
            byCategory.map { it.value },
        )

        // Income category overload returns empty
        val incomeCategory = testCategory.copy(type = TransactionType.INCOME)
        assertTrue(MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(incomeCategory).isEmpty())
        assertNull(MerchantCategoryRecommendationIndex.getProfile(incomeCategory))
    }

    @Test
    fun groceriesExpansion_suggestedEightAndSearchableTwentyOne() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "groceries",
            )
        val suggested = suggestedIds.map { it.value }
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "groceries",
            )
        val searchable = searchableIds.map { it.value }

        val expectedSuggested =
            listOf("bim", "a101", "sok", "migros", "carrefoursa", "file_market", "tarim_kredi", "getir")
        val expectedNewTen =
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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(21, searchable.size)
        assertEquals(expectedSuggested + listOf("migros_sanal_market", "macrocenter", "yemeksepeti_market") + expectedNewTen, searchable)

        // 10 yeni kayıt searchable listesinde var, suggested listesinde yok
        expectedNewTen.forEach { id ->
            assertTrue(id in searchable, "$id searchable listesinde bulunmalı")
            assertTrue(id !in suggested, "$id suggested listesinde bulunmamalı")
        }

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun foodDiningExpansion_suggestedEightAndSearchableTwenty() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "food_dining",
            )
        val suggested = suggestedIds.map { it.value }
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "food_dining",
            )
        val searchable = searchableIds.map { it.value }

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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(20, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // suggested, searchable listesinin alt kümesidir
        suggested.forEach { id ->
            assertTrue(id in searchable, "$id searchable listesinde yer almalıdır")
        }

        // Searchable-only merchant'lar suggested içinde yer almaz
        val searchableOnly = expectedSearchable - expectedSuggested.toSet()
        assertEquals(12, searchableOnly.size)
        searchableOnly.forEach { id ->
            assertTrue(id !in suggested, "$id suggested listesinde bulunmamalıdır")
        }

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun shoppingExpansion_suggestedEightAndSearchableTwentySix() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "shopping",
            )
        val suggested = suggestedIds.map { it.value }
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "shopping",
            )
        val searchable = searchableIds.map { it.value }

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
                "dr",
                "kitapyurdu",
                "bkm_kitap",
                "idefix",
            )

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(30, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // suggested, searchable listesinin alt kümesidir
        suggested.forEach { id ->
            assertTrue(id in searchable, "$id searchable listesinde yer almalıdır")
        }

        // Searchable-only merchant'lar suggested içinde yer almaz (22 adet)
        val searchableOnly = expectedSearchable - expectedSuggested.toSet()
        assertEquals(22, searchableOnly.size)
        searchableOnly.forEach { id ->
            assertTrue(id !in suggested, "$id suggested listesinde bulunmamalıdır")
        }

        // 15 yeni merchant shopping ile uyumlu, diğer kategorilerle uyumsuz
        val newFifteenShoppingIds =
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

        newFifteenShoppingIds.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumlu olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("food_dining", merchant),
                "$id food_dining ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır",
            )
        }

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun personalCareExpansion_suggestedEightAndSearchableFifteen() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "personal_care",
            )
        val suggested = suggestedIds.map { it.value }
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "personal_care",
            )
        val searchable = searchableIds.map { it.value }

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
        assertEquals(expectedSuggested, suggested)

        assertEquals(15, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // suggested, searchable listesinin alt kümesidir
        suggested.forEach { id ->
            assertTrue(id in searchable, "$id searchable listesinde yer almalıdır")
        }

        // Searchable-only merchant'lar suggested içinde yer almaz (7 adet)
        val searchableOnly = expectedSearchable - expectedSuggested.toSet()
        assertEquals(7, searchableOnly.size)
        searchableOnly.forEach { id ->
            assertTrue(id !in suggested, "$id suggested listesinde bulunmamalıdır")
        }

        // 15 yeni merchant personal_care ile uyumlu, diğer kategorilerle uyumsuz
        expectedSearchable.forEach { id ->
            val merchant = StarterMerchantCatalog.merchants.first { it.id == id }
            assertTrue(
                CategoryMerchantCandidateResolver.isCompatible("personal_care", merchant),
                "$id personal_care ile uyumlu olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("shopping", merchant),
                "$id shopping ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("groceries", merchant),
                "$id groceries ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("food_dining", merchant),
                "$id food_dining ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("healthcare", merchant),
                "$id healthcare ile uyumsuz olmalıdır",
            )
            assertFalse(
                CategoryMerchantCandidateResolver.isCompatible("subscriptions", merchant),
                "$id subscriptions ile uyumsuz olmalıdır",
            )
        }

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun utilitiesExpansion_suggestedEightAndSearchableNineteen() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "utilities",
            )
        val suggested = suggestedIds.map { it.value }
        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "utilities",
            )
        val searchable = searchableIds.map { it.value }

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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(19, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // Suggested, searchable'in alt kumesi olmalidir
        assertTrue(searchable.containsAll(suggested))

        // Deterministik ve tekrarsiz
        assertEquals(suggested.distinct().size, suggested.size)
        assertEquals(searchable.distinct().size, searchable.size)

        // Gelir islemlerinde aday uretilmemelidir
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.INCOME,
                "utilities",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.INCOME,
                "utilities",
            )
        assertTrue(incomeSearchable.isEmpty())

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun transportationExpansion_suggestedEightAndSearchableTwentyOne() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "transportation",
            )
        val suggested = suggestedIds.map { it.value }

        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "transportation",
            )
        val searchable = searchableIds.map { it.value }

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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(21, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // Suggested, searchable'ın alt kümesi olmalıdır
        assertTrue(searchable.containsAll(suggested))

        // Deterministik ve tekrarsız
        assertEquals(suggested.distinct().size, suggested.size)
        assertEquals(searchable.distinct().size, searchable.size)

        // Tüm ID'ler StarterMerchantCatalog'da bulunmalıdır
        val catalogIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()
        searchable.forEach { id ->
            assertTrue(id in catalogIds, "$id StarterMerchantCatalog içinde bulunmalıdır")
        }

        // Ters çevrilmiş katalog girişinde sıra değişmez
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolvedWithReversed =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "transportation",
                catalog = reversedCatalog,
            )
        assertEquals(expectedSearchable, resolvedWithReversed.map { it.id })

        // Gelir işlemlerinde aday üretilmemelidir
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.INCOME,
                "transportation",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.INCOME,
                "transportation",
            )
        assertTrue(incomeSearchable.isEmpty())

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun travelExpansion_suggestedEightAndSearchableThirteen() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "travel",
            )
        val suggested = suggestedIds.map { it.value }

        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "travel",
            )
        val searchable = searchableIds.map { it.value }

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

        assertEquals(8, suggested.size)
        assertEquals(expectedSuggested, suggested)

        assertEquals(13, searchable.size)
        assertEquals(expectedSearchable, searchable)

        // Suggested, searchable'ın alt kümesi olmalıdır
        assertTrue(searchable.containsAll(suggested))

        // Deterministik ve tekrarsız
        assertEquals(suggested.distinct().size, suggested.size)
        assertEquals(searchable.distinct().size, searchable.size)

        // Tüm ID'ler StarterMerchantCatalog'da bulunmalıdır
        val catalogIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()
        searchable.forEach { id ->
            assertTrue(id in catalogIds, "$id StarterMerchantCatalog içinde bulunmalıdır")
        }

        // Ters çevrilmiş katalog girişinde sıra değişmez
        val reversedCatalog = StarterMerchantCatalog.merchants.reversed()
        val resolvedWithReversed =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "travel",
                catalog = reversedCatalog,
            )
        assertEquals(expectedSearchable, resolvedWithReversed.map { it.id })

        // Gelir işlemlerinde aday üretilmemelidir
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.INCOME,
                "travel",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.INCOME,
                "travel",
            )
        assertTrue(incomeSearchable.isEmpty())

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun subscriptionsExpansion_suggestedEightAndSearchableThirtyFour() {
        val suggestedIds =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "subscriptions",
            )
        val suggested = suggestedIds.map { it.value }

        val searchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "subscriptions",
            )
        val searchable = searchableIds.map { it.value }

        val expectedSuggested =
            listOf("netflix", "spotify", "youtube_premium", "amazon_prime", "disney_plus", "max", "exxen", "gain")
        assertEquals(expectedSuggested, suggested)
        assertEquals(8, suggested.size)

        val newSixteenIds =
            listOf(
                "s_sport_plus",
                "tv_plus",
                "tivibu",
                "d_smart_go",
                "deezer",
                "fizy",
                "muud",
                "storytel",
                "audible",
                "dropbox",
                "linkedin_premium",
                "nintendo_switch_online",
                "ea_play",
                "hepsiburada_premium",
                "money_gold",
                "ypro",
            )

        // 16 yeni kayıt searchable listesinde var, suggested listesinde yok
        newSixteenIds.forEach { id ->
            assertTrue(id in searchable, "$id searchable listesinde bulunmalı")
            assertTrue(id !in suggested, "$id suggested listesinde bulunmamalı")
        }

        assertEquals(34, searchable.size)
        assertEquals(34, searchable.distinct().size, "searchable listesinde tekrarlı ID bulunmamalı")

        // Her searchable ID starter katalogda bulunmalıdır
        val catalogIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()
        searchable.forEach { id ->
            assertTrue(id in catalogIds, "$id starter katalogda bulunmalıdır")
        }

        // Bilinmeyen kategori güvenli boş sonuç vermeli
        val unknownSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "unknown_category_key",
            )
        assertTrue(unknownSuggested.isEmpty())
        val unknownSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "unknown_category_key",
            )
        assertTrue(unknownSearchable.isEmpty())

        // Yeni abonelik merchant'ları market veya yemek gibi ilgisiz profillere sızmamalı
        val groceriesSearchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "groceries",
            )
        val groceriesSearchable = groceriesSearchableIds.map { it.value }
        val foodDiningSearchableIds =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "food_dining",
            )
        val foodDiningSearchable = foodDiningSearchableIds.map { it.value }

        newSixteenIds.forEach { id ->
            assertTrue(id !in groceriesSearchable, "$id groceries profiline sızmamalıdır")
            assertTrue(id !in foodDiningSearchable, "$id food_dining profiline sızmamalıdır")
        }

        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun housingCategoryProfile_suggestedAndSearchableContract() {
        val profile = MerchantCategoryRecommendationIndex.getProfile("housing")
        assertNotNull(profile)
        assertTrue(profile.hasSuggested)
        assertTrue(profile.hasSearchable)

        val expectedSuggested =
            listOf(
                "apsiyon",
                "yonetimcell",
                "armut",
                "senyonet",
                "mrusta",
                "pronet",
                "kale_alarm",
            )
        val expectedSearchable =
            listOf(
                "apsiyon",
                "yonetimcell",
                "armut",
                "senyonet",
                "mrusta",
                "pronet",
                "kale_alarm",
                "koctas",
                "bauhaus",
            )

        assertEquals(expectedSuggested, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expectedSearchable, profile.searchableMerchantIds.map { it.value })

        val resolvedSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "housing",
            )
        assertEquals(expectedSuggested, resolvedSuggested.map { it.value })

        val resolvedSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "housing",
            )
        assertEquals(expectedSearchable, resolvedSearchable.map { it.value })

        // Income type should return empty safely
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "housing",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "housing",
            )
        assertTrue(incomeSearchable.isEmpty())
    }

    @Test
    fun healthcareCategoryProfile_suggestedAndSearchableContract() {
        val profile = MerchantCategoryRecommendationIndex.getProfile("healthcare")
        assertNotNull(profile)
        assertTrue(profile.hasSuggested)
        assertTrue(profile.hasSearchable)

        val expectedSuggested =
            listOf(
                "acibadem",
                "memorial",
                "medical_park",
                "liv_hospital",
                "medicana",
                "florence_nightingale",
                "anadolu_saglik",
                "amerikan_hastanesi",
                "dunyagoz",
            )

        val expectedSearchable =
            listOf(
                "acibadem",
                "memorial",
                "medical_park",
                "liv_hospital",
                "medicana",
                "florence_nightingale",
                "anadolu_saglik",
                "amerikan_hastanesi",
                "dunyagoz",
                "medipol",
                "biruni_hastanesi",
                "guven_hastanesi",
                "lokman_hekim",
                "baskent_hastanesi",
                "synevo",
                "duzen_laboratuvari",
                "dentgroup",
                "hospitadent",
                "atasun_optik",
            )

        assertEquals(expectedSuggested, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expectedSearchable, profile.searchableMerchantIds.map { it.value })
        assertEquals(9, profile.suggestedMerchantIds.size)
        assertEquals(19, profile.searchableMerchantIds.size)

        // Atasun searchable içinde, suggested dışında
        assertTrue(profile.searchableMerchantIds.any { it.value == "atasun_optik" })
        assertFalse(profile.suggestedMerchantIds.any { it.value == "atasun_optik" })

        // suggested searchable'ın alt kümesi
        assertTrue(profile.searchableMerchantIds.containsAll(profile.suggestedMerchantIds))

        val resolvedSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "healthcare",
            )
        assertEquals(expectedSuggested, resolvedSuggested.map { it.value })

        val resolvedSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "healthcare",
            )
        assertEquals(expectedSearchable, resolvedSearchable.map { it.value })

        // Tekrarlı çağrı deterministik
        val repeatSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "healthcare",
            )
        assertEquals(resolvedSearchable, repeatSearchable)

        // Income type should return empty safely
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "healthcare",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "healthcare",
            )
        assertTrue(incomeSearchable.isEmpty())
    }

    @Test
    fun educationCategoryProfile_suggestedAndSearchableContract() {
        val profile = MerchantCategoryRecommendationIndex.getProfile("education")
        assertNotNull(profile, "Education profili bulunmalı")
        assertEquals("education", profile.categoryIconKey.key)
        assertTrue(profile.hasSuggested)
        assertTrue(profile.hasSearchable)

        val expectedNine =
            listOf(
                "udemy",
                "coursera",
                "duolingo",
                "cambly",
                "kunduz",
                "tonguc_akademi",
                "okulistik",
                "morpa_kampus",
                "raunt",
            )
        val expectedTwentySeven =
            expectedNine +
                listOf(
                    "busuu",
                    "preply",
                    "italki",
                    "skillshare",
                    "derslig",
                    "benim_hocam",
                    "pegem_akademi",
                    "english_time",
                    "bilge_adam",
                    "wall_street_english",
                    "amerikankultur",
                    "dr",
                    "kitapyurdu",
                    "bkm_kitap",
                    "idefix",
                    "storytel",
                    "audible",
                    "linkedin_premium",
                )

        assertEquals(expectedNine, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expectedTwentySeven, profile.searchableMerchantIds.map { it.value })
        assertEquals(9, profile.suggestedMerchantIds.size)
        assertEquals(27, profile.searchableMerchantIds.size)
        assertEquals(27, profile.searchableMerchantIds.distinct().size)

        // suggested searchable'ın alt kümesi
        assertTrue(profile.searchableMerchantIds.containsAll(profile.suggestedMerchantIds))

        val resolvedSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "education",
            )
        assertEquals(expectedNine, resolvedSuggested.map { it.value })

        val resolvedSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "education",
            )
        assertEquals(expectedTwentySeven, resolvedSearchable.map { it.value })

        // Tekrarlı çağrı deterministik
        val repeatSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "education",
            )
        assertEquals(resolvedSearchable, repeatSearchable)

        // Income type should return empty safely
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "education",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "education",
            )
        assertTrue(incomeSearchable.isEmpty())

        // Shopping profilinde 4 kitapçı aranabilir ve suggested değil
        val shoppingProfile =
            MerchantCategoryRecommendationIndex.getProfile(
                TransactionType.EXPENSE,
                "shopping",
            )
        assertNotNull(shoppingProfile)
        val bookstores = listOf("dr", "kitapyurdu", "bkm_kitap", "idefix")
        for (b in bookstores) {
            assertTrue(shoppingProfile.searchableMerchantIds.any { it.value == b }, "Kitapçı '$b' shopping searchable olmalıdır")
            assertFalse(shoppingProfile.suggestedMerchantIds.any { it.value == b }, "Kitapçı '$b' shopping suggested olmamalıdır")
            assertTrue(profile.searchableMerchantIds.any { it.value == b }, "Kitapçı '$b' education searchable olmalıdır")
            assertFalse(profile.suggestedMerchantIds.any { it.value == b }, "Kitapçı '$b' education suggested olmamalıdır")
        }

        // Amazon, Trendyol, Hepsiburada education profilinde olmamalı
        val forbiddenInEducation = listOf("amazon", "trendyol", "hepsiburada")
        for (f in forbiddenInEducation) {
            assertFalse(profile.searchableMerchantIds.any { it.value == f }, "'$f' education profilinde olmamalıdır")
            assertFalse(profile.suggestedMerchantIds.any { it.value == f }, "'$f' education suggested olmamalıdır")
        }

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun familyPetsCategoryProfile_suggestedAndSearchableContract() {
        val profile = MerchantCategoryRecommendationIndex.getProfile("family_pets")
        assertNotNull(profile, "Family & Pets profili bulunmalı")
        assertEquals("family_pets", profile.categoryIconKey.key)
        assertTrue(profile.hasSuggested)
        assertTrue(profile.hasSearchable)

        val expectedNine =
            listOf(
                "ebebek",
                "petlebi",
                "civil",
                "temizmama",
                "toyzz_shop",
                "juen_pet_market",
                "joker_baby",
                "armagan_oyuncak",
                "petzz_shop",
            )

        val expectedSearchableSeventeen =
            expectedNine +
                listOf(
                    "markamama",
                    "petburada",
                    "petihtiyac",
                    "kolaymama",
                    "panco",
                    "bg_store",
                    "minycenter",
                    "vetamerikan",
                )

        assertEquals(expectedNine, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expectedSearchableSeventeen, profile.searchableMerchantIds.map { it.value })
        assertEquals(9, profile.suggestedMerchantIds.size)
        assertEquals(17, profile.searchableMerchantIds.size)
        assertEquals(17, profile.searchableMerchantIds.distinct().size)

        // suggested searchable'ın alt kümesi
        assertTrue(profile.searchableMerchantIds.containsAll(profile.suggestedMerchantIds))

        val resolvedSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "family_pets",
            )
        assertEquals(expectedNine, resolvedSuggested.map { it.value })

        val resolvedSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "family_pets",
            )
        assertEquals(expectedSearchableSeventeen, resolvedSearchable.map { it.value })

        // Tekrarlı çağrı deterministik
        val repeatSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "family_pets",
            )
        assertEquals(resolvedSearchable, repeatSearchable)

        // Income type should return empty safely
        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "family_pets",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "family_pets",
            )
        assertTrue(incomeSearchable.isEmpty())

        // ebebek hem shopping hem family_pets profilinde tek stable ID ile mevcut
        val shoppingProfile =
            MerchantCategoryRecommendationIndex.getProfile(
                TransactionType.EXPENSE,
                "shopping",
            )
        assertNotNull(shoppingProfile)
        assertTrue(shoppingProfile.searchableMerchantIds.any { it.value == "ebebek" })
        assertFalse(shoppingProfile.suggestedMerchantIds.any { it.value == "ebebek" })
        assertTrue(profile.suggestedMerchantIds.any { it.value == "ebebek" })
        assertEquals(1, StarterMerchantCatalog.merchants.count { it.id == "ebebek" }, "ebebek katalogda tek kayıt olmalıdır")
        val ebebekCategoryKey =
            StarterMerchantCatalog.merchants
                .first { it.id == "ebebek" }
                .categoryIconKey
                .key
        assertEquals("shopping", ebebekCategoryKey, "ebebek ana kategorisi shopping kalmalıdır")

        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)
    }

    @Test
    fun giftsDonationsCategoryProfile_deterministicOrderAndIntegrity() {
        // Starter catalog toplamı 260
        assertEquals(260, StarterMerchantCatalog.merchants.size)

        // 1. Profil mevcut ve key gifts_donations
        val profile = MerchantCategoryRecommendationIndex.getProfile("gifts_donations")
        assertNotNull(profile)
        assertEquals("gifts_donations", profile.categoryIconKey.key)

        // 2. Tam olarak 8 suggested, 8 searchable
        assertEquals(8, profile.suggestedMerchantIds.size)
        assertEquals(8, profile.searchableMerchantIds.size)

        // 3. Exact 8 sıra ve içerik
        val expectedEight =
            listOf(
                "kizilay",
                "losev",
                "tema",
                "tev",
                "mehmetcik_vakfi",
                "akut",
                "acev",
                "tog",
            )
        assertEquals(expectedEight, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expectedEight, profile.searchableMerchantIds.map { it.value })

        // 4. suggested searchable'ın alt kümesi ve distinct
        val suggestedSet = profile.suggestedMerchantIds.map { it.value }.toSet()
        val searchableSet = profile.searchableMerchantIds.map { it.value }.toSet()
        assertEquals(8, suggestedSet.size)
        assertEquals(8, searchableSet.size)
        assertTrue(searchableSet.containsAll(suggestedSet))

        // 5. EXPENSE için çözülür, INCOME için boş döner
        val expenseSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.EXPENSE,
                "gifts_donations",
            )
        assertEquals(expectedEight, expenseSuggested.map { it.value })

        val expenseSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.EXPENSE,
                "gifts_donations",
            )
        assertEquals(expectedEight, expenseSearchable.map { it.value })

        val incomeSuggested =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchantIds(
                TransactionType.INCOME,
                "gifts_donations",
            )
        assertTrue(incomeSuggested.isEmpty())

        val incomeSearchable =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchantIds(
                TransactionType.INCOME,
                "gifts_donations",
            )
        assertTrue(incomeSearchable.isEmpty())

        // 6. Ahbap ve Darüşşafaka kesinlikle bulunmaz
        assertFalse(profile.suggestedMerchantIds.any { it.value == "ahbap" })
        assertFalse(profile.searchableMerchantIds.any { it.value == "ahbap" })
        assertFalse(profile.suggestedMerchantIds.any { it.value == "darussafaka" })
        assertFalse(profile.searchableMerchantIds.any { it.value == "darussafaka" })

        // 7. Bütün ID'ler StarterMerchantCatalog içinde mevcuttur
        val catalogIds = StarterMerchantCatalog.merchants.map { it.id }.toSet()
        profile.suggestedMerchantIds.forEach { id ->
            assertTrue(id.value in catalogIds, "Öneri ID'si ${id.value} katalogda bulunmalıdır")
        }
    }

    @Test
    fun taxesFees_suggestedAndSearchableOrder_exactMatchWithIndex() {
        val profile = MerchantCategoryRecommendationIndex.getProfile("taxes_fees")
        assertNotNull(profile)
        assertTrue(profile.hasSuggested)
        assertTrue(profile.hasSearchable)

        val expected = listOf("gib", "sgk")
        assertEquals(expected, profile.suggestedMerchantIds.map { it.value })
        assertEquals(expected, profile.searchableMerchantIds.map { it.value })

        val suggestedMerchants =
            MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
                TransactionType.EXPENSE,
                "taxes_fees",
            )
        assertEquals(expected, suggestedMerchants.map { it.id })

        val searchableMerchants =
            MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
                TransactionType.EXPENSE,
                "taxes_fees",
            )
        assertEquals(expected, searchableMerchants.map { it.id })
    }
}
