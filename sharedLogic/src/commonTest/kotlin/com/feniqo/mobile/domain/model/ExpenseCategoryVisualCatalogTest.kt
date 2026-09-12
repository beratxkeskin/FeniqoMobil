package com.feniqo.mobile.domain.model

import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExpenseCategoryVisualCatalogTest {
    @Test
    fun catalog_containsCanonicalExpenseDefinitions() {
        val definitions = ExpenseCategoryVisualCatalog.definitions

        assertEquals(18, definitions.size)
        assertEquals(18, definitions.map { it.key.key }.distinct().size)
        assertEquals(18, definitions.map { it.turkishName }.distinct().size)
        definitions.forEach { definition ->
            assertNotNull(ExpenseCategoryVisualCatalog.find(definition.key))
        }
    }

    @Test
    fun representativeDefinitions_keepApprovedNamesColorsAndMeanings() {
        val dining = assertNotNull(ExpenseCategoryVisualCatalog.find(CategoryIcon("food_dining")))
        assertEquals("Yeme & İçme", dining.turkishName)
        assertEquals("Food & Dining", dining.englishName)
        assertEquals("fork_knife", dining.iconMeaningKey)
        assertEquals(CategoryColor("#F97316"), dining.color)

        val fees = assertNotNull(ExpenseCategoryVisualCatalog.find(CategoryIcon("taxes_fees")))
        assertEquals("Vergi & Ücretler", fees.turkishName)
        assertEquals(CategoryColor("#64748B"), fees.color)
    }

    @Test
    fun everyStarterMerchant_usesCanonicalExpenseCategoryKey() {
        StarterMerchantCatalog.merchants.forEach { merchant ->
            assertNotNull(
                ExpenseCategoryVisualCatalog.find(merchant.categoryIconKey),
                "${merchant.displayName} kanonik olmayan kategori anahtarı kullanıyor.",
            )
        }
    }
}
