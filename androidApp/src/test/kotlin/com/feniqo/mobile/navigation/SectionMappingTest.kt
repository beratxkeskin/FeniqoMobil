package com.feniqo.mobile.navigation

import com.feniqo.mobile.presentation.shell.AppSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SectionMappingTest {

    @Test
    fun appSection_mapsToExpectedTopLevelDestination() {
        assertSame(TopLevelDestination.DASHBOARD, AppSection.DASHBOARD.toTopLevelDestination())
        assertSame(TopLevelDestination.TRANSACTIONS, AppSection.TRANSACTIONS.toTopLevelDestination())
        assertSame(TopLevelDestination.BUDGETS, AppSection.BUDGETS.toTopLevelDestination())
        assertSame(TopLevelDestination.CATEGORIES, AppSection.CATEGORIES.toTopLevelDestination())
        assertSame(TopLevelDestination.SETTINGS, AppSection.SETTINGS.toTopLevelDestination())
    }

    @Test
    fun topLevelDestination_mapsToExpectedAppSection() {
        assertSame(AppSection.DASHBOARD, TopLevelDestination.DASHBOARD.toAppSection())
        assertSame(AppSection.TRANSACTIONS, TopLevelDestination.TRANSACTIONS.toAppSection())
        assertSame(AppSection.BUDGETS, TopLevelDestination.BUDGETS.toAppSection())
        assertSame(AppSection.CATEGORIES, TopLevelDestination.CATEGORIES.toAppSection())
        assertSame(AppSection.SETTINGS, TopLevelDestination.SETTINGS.toAppSection())
    }

    @Test
    fun sectionMapping_isBidirectionalAndConsistent() {
        AppSection.entries.forEach { section ->
            val destination = section.toTopLevelDestination()
            assertEquals(section, destination.toAppSection())
        }

        TopLevelDestination.entries.forEach { destination ->
            val section = destination.toAppSection()
            assertEquals(destination, section.toTopLevelDestination())
        }
    }
}
