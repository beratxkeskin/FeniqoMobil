package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantSearchFilterTest {

    private fun sampleMerchant(
        id: String,
        displayName: String,
        aliases: List<String> = listOf(displayName),
    ) = Merchant(
        id = id,
        displayName = displayName,
        aliases = aliases.map { MerchantAlias(it) },
        categoryIconKey = CategoryIcon("shopping-bag"),
    )

    @Test
    fun shuffledCatalogs_produceExactSameOrderedResults() {
        val m1 = sampleMerchant("a101", "A101")
        val m2 = sampleMerchant("bim", "BİM")
        val m3 = sampleMerchant("carrefour", "CarrefourSA")
        val m4 = sampleMerchant("migros", "Migros")
        val m5 = sampleMerchant("sok", "ŞOK")

        val catalog1 = listOf(m4, m1, m5, m2, m3)
        val catalog2 = listOf(m2, m5, m3, m4, m1)

        val result1 = MerchantSearchFilter.filter("", catalog1)
        val result2 = MerchantSearchFilter.filter("", catalog2)

        assertEquals(result1, result2)
        // Beklenen sıralama: A101, BİM, CarrefourSA, Migros, ŞOK
        assertEquals(
            listOf(EntityId("a101"), EntityId("bim"), EntityId("carrefour"), EntityId("migros"), EntityId("sok")),
            result1.map { it.id },
        )
    }

    @Test
    fun sameDisplayName_differentiatedByStableIdTieBreaker() {
        val m1 = sampleMerchant("starbucks-branch-b", "Starbucks")
        val m2 = sampleMerchant("starbucks-branch-a", "Starbucks")

        val result = MerchantSearchFilter.filter("starbucks", listOf(m1, m2))

        assertEquals(2, result.size)
        assertEquals(EntityId("starbucks-branch-a"), result[0].id)
        assertEquals(EntityId("starbucks-branch-b"), result[1].id)
    }

    @Test
    fun aliasSearch_returnsCorrectMerchantIds() {
        val m1 = sampleMerchant("migros", "Migros", listOf("MIGROS SANAL MARKET", "MM MIGROS"))
        val m2 = sampleMerchant("yemeksepeti", "Yemeksepeti", listOf("YEMEKSEPETI MARKET"))

        val result = MerchantSearchFilter.filter("sanal", listOf(m1, m2))

        assertEquals(1, result.size)
        assertEquals(EntityId("migros"), result.first().id)
    }

    @Test
    fun emptyQuery_returnsAllMerchants_deterministicallyOrdered() {
        val m1 = sampleMerchant("trendyol", "Trendyol")
        val m2 = sampleMerchant("amazon", "Amazon")
        val m3 = sampleMerchant("hepsiburada", "Hepsiburada")

        val result = MerchantSearchFilter.filter("", listOf(m1, m2, m3))

        assertEquals(
            listOf(EntityId("amazon"), EntityId("hepsiburada"), EntityId("trendyol")),
            result.map { it.id },
        )
    }

    @Test
    fun noMatchingResults_returnsEmptyList() {
        val m1 = sampleMerchant("migros", "Migros")
        val result = MerchantSearchFilter.filter("netflix", listOf(m1))

        assertTrue(result.isEmpty())
    }

    private fun sampleSelectorItem(
        id: String,
        displayName: String,
        categoryIconKey: String? = "shopping-cart",
        verifiedLogoKey: String? = null,
    ) = MerchantSelectorItem(
        id = EntityId(id),
        displayName = displayName,
        categoryIconKey = categoryIconKey,
        verifiedLogoKey = verifiedLogoKey,
    )

    @Test
    fun filterCandidates_emptyQuery_returnsAllCandidatesDeterministicallyOrdered() {
        val i1 = sampleSelectorItem("trendyol", "Trendyol", "shopping-bag")
        val i2 = sampleSelectorItem("amazon", "Amazon", "shopping-bag")
        val i3 = sampleSelectorItem("hepsiburada", "Hepsiburada", "shopping-bag")

        val result = MerchantSearchFilter.filterCandidates("", listOf(i1, i2, i3))

        assertEquals(
            listOf(EntityId("amazon"), EntityId("hepsiburada"), EntityId("trendyol")),
            result.map { it.id },
        )
    }

    @Test
    fun filterCandidates_queryMatchingDisplayName_returnsMatchingCandidates() {
        val i1 = sampleSelectorItem("migros", "Migros")
        val i2 = sampleSelectorItem("bim", "BİM")
        val i3 = sampleSelectorItem("a101", "A101")

        val result = MerchantSearchFilter.filterCandidates("mig", listOf(i1, i2, i3))

        assertEquals(1, result.size)
        assertEquals(EntityId("migros"), result.first().id)
    }

    @Test
    fun filterCandidates_sameDisplayName_differentiatedByStableIdTieBreaker() {
        val i1 = sampleSelectorItem("starbucks-branch-b", "Starbucks", "coffee")
        val i2 = sampleSelectorItem("starbucks-branch-a", "Starbucks", "coffee")

        val result = MerchantSearchFilter.filterCandidates("starbucks", listOf(i1, i2))

        assertEquals(2, result.size)
        assertEquals(EntityId("starbucks-branch-a"), result[0].id)
        assertEquals(EntityId("starbucks-branch-b"), result[1].id)
    }

    @Test
    fun filterCandidates_noMatchingResults_returnsEmptyList() {
        val i1 = sampleSelectorItem("migros", "Migros")
        val result = MerchantSearchFilter.filterCandidates("netflix", listOf(i1))

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterCandidates_shuffledInput_producesExactSameOrderedResults() {
        val i1 = sampleSelectorItem("a101", "A101")
        val i2 = sampleSelectorItem("bim", "BİM")
        val i3 = sampleSelectorItem("carrefour", "CarrefourSA")
        val i4 = sampleSelectorItem("migros", "Migros")
        val i5 = sampleSelectorItem("sok", "ŞOK")

        val list1 = listOf(i4, i1, i5, i2, i3)
        val list2 = listOf(i2, i5, i3, i4, i1)

        val result1 = MerchantSearchFilter.filterCandidates("", list1)
        val result2 = MerchantSearchFilter.filterCandidates("", list2)

        assertEquals(result1, result2)
        assertEquals(
            listOf(EntityId("a101"), EntityId("bim"), EntityId("carrefour"), EntityId("migros"), EntityId("sok")),
            result1.map { it.id },
        )
    }
}
