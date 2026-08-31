package com.feniqo.mobile.navigation

import com.feniqo.mobile.domain.model.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeniqoRoutesTest {

    @Test
    fun topLevelDestinations_containsExactlyFiveUniqueEntries() {
        val destinations = TopLevelDestination.entries
        assertEquals("Beş ana sekme bulunmalıdır", 5, destinations.size)
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
        assertSame(BudgetsRoute, TopLevelDestination.BUDGETS.route)
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

    @Test
    fun transactionFormRoute_nullTransactionId_representsAddMode() {
        val route = TransactionFormRoute(transactionId = null)
        assertEquals(null, route.transactionId)
    }

    @Test
    fun transactionFormRoute_validTransactionId_representsEditMode() {
        val route = TransactionFormRoute(transactionId = "trx-123")
        assertEquals("trx-123", route.transactionId)
    }

    @Test
    fun transactionFormRoute_blankTransactionId_remainsEditRequest_andDoesNotBecomeAddMode() {
        val route = TransactionFormRoute(transactionId = "   ")
        assertEquals("   ", route.transactionId)
    }

    @Test
    fun categoryFormRoute_implementsFeniqoRoute_andHasExpectedDefaults() {
        val route = CategoryFormRoute()
        assertTrue(route is FeniqoRoute)
        assertEquals(null, route.categoryId)
        assertEquals("EXPENSE", route.initialTypeCode)
    }

    @Test
    fun categoryFormRoute_customValues_arePreserved() {
        val editRoute = CategoryFormRoute(categoryId = "cat-456", initialTypeCode = "INCOME")
        assertEquals("cat-456", editRoute.categoryId)
        assertEquals("INCOME", editRoute.initialTypeCode)
    }

    @Test
    fun budgetFormRoute_implementsFeniqoRoute_andPreservesInitialMonthAndBudgetId() {
        val createRoute = BudgetFormRoute(initialMonth = "2026-08")
        assertTrue(createRoute is FeniqoRoute)
        assertEquals("2026-08", createRoute.initialMonth)
        assertNull(createRoute.budgetId)

        val editRoute = BudgetFormRoute(initialMonth = "2026-08", budgetId = "b-123")
        assertEquals("2026-08", editRoute.initialMonth)
        assertEquals("b-123", editRoute.budgetId)
    }

    @Test
    fun parseBudgetRouteId_correctlyDifferentiatesModes() {
        // 1. null -> CreateMode
        assertEquals(BudgetRouteIdResult.CreateMode, parseBudgetRouteId(null))

        // 2. Dolu ve geçerli ID -> ValidId
        val validResult = parseBudgetRouteId("b_abc_123")
        assertTrue(validResult is BudgetRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("b_abc_123"), (validResult as BudgetRouteIdResult.ValidId).id)

        // 3. Boşluk/geçersiz ID -> InvalidId
        assertEquals(BudgetRouteIdResult.InvalidId, parseBudgetRouteId(""))
        assertEquals(BudgetRouteIdResult.InvalidId, parseBudgetRouteId("   "))
    }

    @Test
    fun parseBudgetInitialMonth_validAndFallbackScenarios() {
        val fallback = YearMonth("2026-08")

        // 1. Geçerli YYYY-MM değeri aynen korunur
        val parsedValid = parseBudgetInitialMonth(rawMonth = "2026-09", fallbackMonth = fallback)
        assertEquals(YearMonth("2026-09"), parsedValid)

        // 2. Geçersiz format fallback değere döner
        val parsedInvalid = parseBudgetInitialMonth(rawMonth = "invalid-date", fallbackMonth = fallback)
        assertEquals(fallback, parsedInvalid)

        // 3. Boşluk fallback değere döner
        val parsedBlank = parseBudgetInitialMonth(rawMonth = "   ", fallbackMonth = fallback)
        assertEquals(fallback, parsedBlank)

        // 4. Null fallback değere döner
        val parsedNull = parseBudgetInitialMonth(rawMonth = null, fallbackMonth = fallback)
        assertEquals(fallback, parsedNull)
    }

    @Test
    fun formRoutes_areNotTopLevelDestinations() {
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("TransactionFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is TransactionFormRoute })
        assertTrue("CategoryFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is CategoryFormRoute })
        assertTrue("BudgetFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is BudgetFormRoute })
        assertTrue("RecurringTransactionFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is RecurringTransactionFormRoute })
        assertTrue("RecurringTransactionsRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == RecurringTransactionsRoute })
    }

    @Test
    fun recurringTransactionsRoute_implementsFeniqoRoute_andIsDistinct() {
        assertTrue(RecurringTransactionsRoute is FeniqoRoute)
        assertSame(RecurringTransactionsRoute, RecurringTransactionsRoute)
    }

    @Test
    fun recurringTransactionFormRoute_createAndEditModes_deterministic() {
        val createRoute = RecurringTransactionFormRoute(recurringTransactionId = null)
        assertTrue(createRoute is FeniqoRoute)
        assertNull(createRoute.recurringTransactionId)

        val editRoute = RecurringTransactionFormRoute(recurringTransactionId = "rec-789")
        assertEquals("rec-789", editRoute.recurringTransactionId)
    }

    @Test
    fun parseRecurringRouteId_correctlyDifferentiatesModesAndFailsClosed() {
        // 1. null -> CreateMode
        assertEquals(RecurringRouteIdResult.CreateMode, parseRecurringRouteId(null))

        // 2. Dolu ve geçerli ID -> ValidId
        val validResult = parseRecurringRouteId("rec_abc_123")
        assertTrue(validResult is RecurringRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("rec_abc_123"),
            (validResult as RecurringRouteIdResult.ValidId).id,
        )

        // Trimlenmiş geçerli ID
        val trimmedResult = parseRecurringRouteId("  rec_trimmed_456  ")
        assertTrue(trimmedResult is RecurringRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("rec_trimmed_456"),
            (trimmedResult as RecurringRouteIdResult.ValidId).id,
        )

        // 3. Boşluk / geçersiz ID -> InvalidId (fail-closed)
        assertEquals(RecurringRouteIdResult.InvalidId, parseRecurringRouteId(""))
        assertEquals(RecurringRouteIdResult.InvalidId, parseRecurringRouteId("   "))
    }
}
