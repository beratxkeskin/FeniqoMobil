package com.feniqo.mobile.presentation.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubRegistryTest {

    @Test
    fun planHubRegistry_containsExpectedItemsAndStatus() {
        val allItems = PlanHubRegistry.items
        assertEquals(5, allItems.size, "Plan hub 5 öğe barındırmalıdır")

        val available = PlanHubRegistry.availableItems
        val comingSoon = PlanHubRegistry.comingSoonItems

        assertEquals(5, available.size, "Plan altında 5 aktif modül bulunmalıdır")
        assertEquals(0, comingSoon.size, "Plan altında yakında modülü kalmamalıdır")

        // Aktif modüller
        assertEquals(
            listOf("budgets", "recurring_transactions", "subscriptions", "goals", "debts"),
            available.map { it.id },
        )
        assertTrue(available.all { it.isAvailable && it.status == HubItemStatus.AVAILABLE })

        // Gelecek / pasif modüller
        assertTrue(comingSoon.isEmpty())
    }


    @Test
    fun moreHubRegistry_containsExpectedItemsAndStatus() {
        val allItems = MoreHubRegistry.items
        assertEquals(9, allItems.size, "Finans Merkezi 9 öğe barındırmalıdır")

        val available = MoreHubRegistry.availableItems
        val comingSoon = MoreHubRegistry.comingSoonItems

        assertEquals(8, available.size, "Finans Merkezi altında 8 aktif modül bulunmalıdır")
        assertEquals(1, comingSoon.size, "Finans Merkezi altında 1 yakında modülü bulunmalıdır")

        // Aktif modüller
        assertEquals(
            listOf("assets", "goals", "debts", "subscriptions", "recurring_transactions", "categories", "shared_spaces", "settings"),
            available.map { it.id },
        )
        assertTrue(available.all { it.isAvailable && it.status == HubItemStatus.AVAILABLE })

        // Gelecek / pasif modüller
        assertEquals(
            listOf("reports"),
            comingSoon.map { it.id },
        )
        assertTrue(comingSoon.all { !it.isAvailable && it.status == HubItemStatus.COMING_SOON })
    }

    @Test
    fun hubMenuItems_haveValidTitlesAndDistinctIds() {
        val allPlanIds = PlanHubRegistry.items.map { it.id }
        assertEquals(allPlanIds.size, allPlanIds.toSet().size, "Plan öğe id'leri benzersiz olmalıdır")

        val allMoreIds = MoreHubRegistry.items.map { it.id }
        assertEquals(allMoreIds.size, allMoreIds.toSet().size, "Daha Fazla öğe id'leri benzersiz olmalıdır")

        (PlanHubRegistry.items + MoreHubRegistry.items).forEach { item ->
            assertTrue(item.title.isNotBlank(), "Başlık boş olamaz: ${item.id}")
            assertTrue(item.subtitle.isNotBlank(), "Alt başlık boş olamaz: ${item.id}")
            assertTrue(item.iconSymbol.isNotBlank(), "İkon sembolü boş olamaz: ${item.id}")
        }
    }
}
