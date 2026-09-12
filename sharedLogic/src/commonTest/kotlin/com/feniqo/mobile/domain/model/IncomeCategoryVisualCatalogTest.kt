package com.feniqo.mobile.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IncomeCategoryVisualCatalogTest {
    @Test
    fun catalog_containsNineUniqueCanonicalIncomeDefinitions() {
        val definitions = IncomeCategoryVisualCatalog.definitions

        assertEquals(9, definitions.size)
        assertEquals(9, definitions.map { it.key.key }.distinct().size)
        assertEquals(9, definitions.map { it.turkishName }.distinct().size)
        definitions.forEach { definition ->
            assertNotNull(IncomeCategoryVisualCatalog.find(definition.key))
        }
    }

    @Test
    fun definitions_keepApprovedNamesColorsAndMeanings() {
        val salary = assertNotNull(IncomeCategoryVisualCatalog.find(CategoryIcon("salary")))
        assertEquals("Maaş", salary.turkishName)
        assertEquals("Salary", salary.englishName)
        assertEquals("briefcase", salary.iconMeaningKey)
        assertEquals(CategoryColor("#16A34A"), salary.color)

        val refund = assertNotNull(IncomeCategoryVisualCatalog.find(CategoryIcon("refund_reimbursement")))
        assertEquals("İade & Geri Ödeme", refund.turkishName)
        assertEquals("Refund & Reimbursement", refund.englishName)
        assertEquals("return_arrow", refund.iconMeaningKey)
        assertEquals(CategoryColor("#14B8A6"), refund.color)
    }
}
