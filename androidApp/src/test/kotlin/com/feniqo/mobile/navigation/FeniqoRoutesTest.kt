package com.feniqo.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeniqoRoutesTest {

    @Test
    fun topLevelDestinations_containsExactlyFourUniqueEntries() {
        val destinations = TopLevelDestination.entries
        assertEquals("Dört ana sekme bulunmalıdır", 4, destinations.size)
        assertEquals(
            "Hedefler benzersiz olmalıdır",
            destinations.size,
            destinations.toSet().size,
        )
    }

    @Test
    fun topLevelDestinations_mapsToExpectedRouteObjects() {
        assertSame(DashboardRoute, TopLevelDestination.DASHBOARD.route)
        assertSame(TransactionsRoute, TopLevelDestination.TRANSACTIONS.route)
        assertSame(CategoriesRoute, TopLevelDestination.CATEGORIES.route)
        assertSame(SettingsRoute, TopLevelDestination.SETTINGS.route)
    }

    @Test
    fun topLevelDestinations_eachRouteIsDistinct() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(
            "Her sekmenin rotası birbirinden farklı olmalıdır",
            routes.size,
            routes.toSet().size,
        )
    }
}
