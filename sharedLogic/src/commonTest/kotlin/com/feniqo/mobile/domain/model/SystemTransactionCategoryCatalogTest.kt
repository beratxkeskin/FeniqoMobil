package com.feniqo.mobile.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SystemTransactionCategoryCatalogTest {
    @Test
    fun catalog_containsSevenUniqueSystemMovementDefinitions() {
        val definitions = SystemTransactionCategoryCatalog.definitions

        assertEquals(7, definitions.size)
        assertEquals(7, definitions.map { it.key.key }.distinct().size)
        assertEquals(7, definitions.map { it.turkishName }.distinct().size)
        definitions.forEach { assertNotNull(SystemTransactionCategoryCatalog.find(it.key)) }
    }

    @Test
    fun definitions_keepApprovedNamesIconsAndPurposes() {
        val transfer = assertNotNull(SystemTransactionCategoryCatalog.find(CategoryIcon("account_transfer")))
        assertEquals("Hesaplar Arası Transfer", transfer.turkishName)
        assertEquals("Account Transfer", transfer.englishName)
        assertEquals("bidirectional_arrows", transfer.iconMeaningKey)

        val adjustment = assertNotNull(SystemTransactionCategoryCatalog.find(CategoryIcon("balance_adjustment")))
        assertEquals("Bakiye Düzeltmesi", adjustment.turkishName)
        assertEquals("Başlangıç bakiyesi ve düzeltme işlemleri", adjustment.purpose)
    }
}
