package com.feniqo.mobile.demo

import com.feniqo.mobile.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate as JavaDate

class DemoScenarioTest {
    private val categories = (ExpenseCategoryVisualCatalog.definitions.map { it.key.key } +
        IncomeCategoryVisualCatalog.definitions.map { it.key.key }).mapIndexed { i, key ->
        key to EntityId("00000000-0000-4000-8000-${i.toString().padStart(12, '0')}")
    }.toMap()

    @Test fun `history covers six months without future dates across calendar boundaries`() {
        for (day in listOf("2026-09-18", "2026-01-01", "2024-02-29", "2026-03-31")) {
            val scenario = DemoScenario(JavaDate.parse(day))
            val rows = scenario.transactions(categories)
            assertTrue(rows.size > 150)
            assertEquals(6, rows.map { YearMonth.from(it.transactionDate) }.distinct().size)
            assertTrue(rows.all { it.transactionDate <= LocalDate.parse(day) && it.amount.amountMinor > 0 })
            assertEquals(rows.size, rows.map { it.id }.distinct().size)
            assertTrue(rows.all { it.ownerId == DemoAuthRepository.USER_ID && it.workspaceId == null })
            assertEquals(rows, scenario.transactions(categories))
        }
    }

    @Test fun `installments are complete and currency comparisons remain separate`() {
        val rows = DemoScenario(JavaDate.parse("2026-09-18")).transactions(categories)
        val installments = rows.mapNotNull { it.installment }
        assertEquals(listOf(1, 2, 3), installments.map { it.number })
        assertEquals(1, installments.map { it.groupId }.distinct().size)
        assertTrue(installments.all { it.total == 3 })
        assertEquals(setOf(Currency.TRY, Currency.USD, Currency.EUR), rows.map { it.amount.currency }.toSet())
        assertEquals(6, rows.count { it.categoryId == categories.getValue("salary") })
        assertTrue(rows.any { it.paymentMethod == PaymentMethod.CASH })
    }
}
