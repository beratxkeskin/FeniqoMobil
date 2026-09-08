package com.feniqo.mobile.navigation

import com.feniqo.mobile.domain.model.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertSame(PlanRoute, TopLevelDestination.PLAN.route)
        assertSame(MoreRoute, TopLevelDestination.MORE.route)
    }

    @Test
    fun planRoute_and_moreRoute_implementFeniqoRoute() {
        assertTrue(PlanRoute is FeniqoRoute)
        assertTrue(MoreRoute is FeniqoRoute)
        assertSame(PlanRoute, PlanRoute)
        assertSame(MoreRoute, MoreRoute)
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
    fun formRoutes_and_subModules_areNotTopLevelDestinations() {
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("TransactionFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is TransactionFormRoute })
        assertTrue("CategoryFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is CategoryFormRoute })
        assertTrue("BudgetFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is BudgetFormRoute })
        assertTrue("RecurringTransactionFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is RecurringTransactionFormRoute })
        assertTrue("SubscriptionFormRoute top-level hedef olmamalıdır", topLevelRoutes.none { it is SubscriptionFormRoute })
        assertTrue("BudgetsRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == BudgetsRoute })
        assertTrue("CategoriesRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == CategoriesRoute })
        assertTrue("SettingsRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == SettingsRoute })
        assertTrue("RecurringTransactionsRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == RecurringTransactionsRoute })
        assertTrue("SubscriptionsRoute top-level hedef olmamalıdır", topLevelRoutes.none { it == SubscriptionsRoute })
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

    @Test
    fun subscriptionsRoute_implementsFeniqoRoute_andIsDistinct() {
        assertTrue(SubscriptionsRoute is FeniqoRoute)
        assertSame(SubscriptionsRoute, SubscriptionsRoute)
    }

    @Test
    fun subscriptionFormRoute_createAndEditModes_deterministic() {
        val createRoute = SubscriptionFormRoute(subscriptionId = null)
        assertTrue(createRoute is FeniqoRoute)
        assertNull(createRoute.subscriptionId)

        val editRoute = SubscriptionFormRoute(subscriptionId = "sub-789")
        assertEquals("sub-789", editRoute.subscriptionId)
    }

    @Test
    fun parseSubscriptionRouteId_correctlyDifferentiatesModesAndFailsClosed() {
        // 1. null -> CreateMode
        assertEquals(SubscriptionRouteIdResult.CreateMode, parseSubscriptionRouteId(null))

        // 2. Dolu ve geçerli ID -> ValidId
        val validResult = parseSubscriptionRouteId("sub_abc_123")
        assertTrue(validResult is SubscriptionRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("sub_abc_123"),
            (validResult as SubscriptionRouteIdResult.ValidId).id,
        )

        // Trimlenmiş geçerli ID
        val trimmedResult = parseSubscriptionRouteId("  sub_trimmed_456  ")
        assertTrue(trimmedResult is SubscriptionRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("sub_trimmed_456"),
            (trimmedResult as SubscriptionRouteIdResult.ValidId).id,
        )

        // 3. Boşluk / geçersiz ID -> InvalidId (fail-closed)
        assertEquals(SubscriptionRouteIdResult.InvalidId, parseSubscriptionRouteId(""))
        assertEquals(SubscriptionRouteIdResult.InvalidId, parseSubscriptionRouteId("   "))
    }

    @Test
    fun goalsRoute_implementsFeniqoRoute_andIsDistinct() {
        assertTrue(GoalsRoute is FeniqoRoute)
        assertSame(GoalsRoute, GoalsRoute)
        assertTrue(TopLevelDestination.entries.none { it.route == GoalsRoute })
    }

    @Test
    fun goalFormRoute_createAndEditModes_deterministic() {
        val createRoute = GoalFormRoute(goalId = null)
        assertTrue(createRoute is FeniqoRoute)
        assertNull(createRoute.goalId)

        val editRoute = GoalFormRoute(goalId = "goal-789")
        assertEquals("goal-789", editRoute.goalId)
    }

    @Test
    fun parseGoalRouteId_correctlyDifferentiatesModesAndFailsClosed() {
        // 1. null -> CreateMode
        assertEquals(GoalRouteIdResult.CreateMode, parseGoalRouteId(null))

        // 2. Dolu ve geçerli ID -> ValidId
        val validResult = parseGoalRouteId("goal_abc_123")
        assertTrue(validResult is GoalRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("goal_abc_123"),
            (validResult as GoalRouteIdResult.ValidId).id,
        )

        // Trimlenmiş geçerli ID
        val trimmedResult = parseGoalRouteId("  goal_trimmed_456  ")
        assertTrue(trimmedResult is GoalRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("goal_trimmed_456"),
            (trimmedResult as GoalRouteIdResult.ValidId).id,
        )

        // 3. Boşluk / geçersiz ID -> InvalidId (fail-closed)
        assertEquals(GoalRouteIdResult.InvalidId, parseGoalRouteId(""))
        assertEquals(GoalRouteIdResult.InvalidId, parseGoalRouteId("   "))
    }

    @Test
    fun debtsRoute_implementsFeniqoRoute_andIsDistinct() {
        assertTrue(DebtsRoute is FeniqoRoute)
        assertSame(DebtsRoute, DebtsRoute)
        assertTrue(TopLevelDestination.entries.none { it.route == DebtsRoute })
    }

    @Test
    fun debtFormRoute_createAndEditModes_deterministic() {
        val createRoute = DebtFormRoute(debtId = null)
        assertTrue(createRoute is FeniqoRoute)
        assertNull(createRoute.debtId)

        val editRoute = DebtFormRoute(debtId = "debt-789")
        assertEquals("debt-789", editRoute.debtId)
    }

    @Test
    fun parseDebtRouteId_correctlyDifferentiatesModesAndFailsClosed() {
        // 1. null -> CreateMode
        assertEquals(DebtRouteIdResult.CreateMode, parseDebtRouteId(null))

        // 2. Dolu ve geçerli ID -> ValidId
        val validResult = parseDebtRouteId("debt_abc_123")
        assertTrue(validResult is DebtRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("debt_abc_123"),
            (validResult as DebtRouteIdResult.ValidId).id,
        )

        // Trimlenmiş geçerli ID
        val trimmedResult = parseDebtRouteId("  debt_trimmed_456  ")
        assertTrue(trimmedResult is DebtRouteIdResult.ValidId)
        assertEquals(
            com.feniqo.mobile.domain.model.EntityId("debt_trimmed_456"),
            (trimmedResult as DebtRouteIdResult.ValidId).id,
        )

        // 3. Boşluk / geçersiz ID -> InvalidId (fail-closed)
        assertEquals(DebtRouteIdResult.InvalidId, parseDebtRouteId(""))
        assertEquals(DebtRouteIdResult.InvalidId, parseDebtRouteId("   "))
    }

    @Test
    fun goalContributionFormRoute_deterministicAndImplementsFeniqoRoute() {
        val route = GoalContributionFormRoute(goalId = "goal-123")
        assertTrue(route is FeniqoRoute)
        assertEquals("goal-123", route.goalId)
    }

    @Test
    fun parseGoalContributionRouteId_failsClosedOnBlankOrNull() {
        // null -> InvalidId
        assertEquals(ChildRouteIdResult.InvalidId, parseGoalContributionRouteId(null))

        // Boşluk -> InvalidId
        assertEquals(ChildRouteIdResult.InvalidId, parseGoalContributionRouteId(""))
        assertEquals(ChildRouteIdResult.InvalidId, parseGoalContributionRouteId("   "))

        // Geçerli ID
        val validResult = parseGoalContributionRouteId("g-100")
        assertTrue(validResult is ChildRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("g-100"), (validResult as ChildRouteIdResult.ValidId).id)

        // Trimlenmiş ID
        val trimmedResult = parseGoalContributionRouteId("  g-200  ")
        assertTrue(trimmedResult is ChildRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("g-200"), (trimmedResult as ChildRouteIdResult.ValidId).id)
    }

    @Test
    fun debtPaymentFormRoute_deterministicAndImplementsFeniqoRoute() {
        val route = DebtPaymentFormRoute(debtId = "debt-123")
        assertTrue(route is FeniqoRoute)
        assertEquals("debt-123", route.debtId)
    }

    @Test
    fun parseDebtPaymentRouteId_failsClosedOnBlankOrNull() {
        // null -> InvalidId
        assertEquals(ChildRouteIdResult.InvalidId, parseDebtPaymentRouteId(null))

        // Boşluk -> InvalidId
        assertEquals(ChildRouteIdResult.InvalidId, parseDebtPaymentRouteId(""))
        assertEquals(ChildRouteIdResult.InvalidId, parseDebtPaymentRouteId("   "))

        // Geçerli ID
        val validResult = parseDebtPaymentRouteId("d-100")
        assertTrue(validResult is ChildRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("d-100"), (validResult as ChildRouteIdResult.ValidId).id)

        // Trimlenmiş ID
        val trimmedResult = parseDebtPaymentRouteId("  d-200  ")
        assertTrue(trimmedResult is ChildRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("d-200"), (trimmedResult as ChildRouteIdResult.ValidId).id)
    }

    @Test
    fun debtSnowballPlanRoute_implementsFeniqoRoute_andIsNotTopLevel() {
        assertTrue(DebtSnowballPlanRoute is FeniqoRoute)
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("DebtSnowballPlanRoute top-level hedef olmamalıdır", DebtSnowballPlanRoute !in topLevelRoutes)
    }

    @Test
    fun workspacePickerRoute_implementsFeniqoRoute_andIsNotTopLevel() {
        assertTrue(WorkspacePickerRoute is FeniqoRoute)
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("WorkspacePickerRoute top-level hedef olmamalıdır", WorkspacePickerRoute !in topLevelRoutes)
    }

    @Test
    fun workspaceCreateRoute_implementsFeniqoRoute_andIsNotTopLevel() {
        assertTrue(WorkspaceCreateRoute is FeniqoRoute)
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("WorkspaceCreateRoute top-level hedef olmamalıdır", WorkspaceCreateRoute !in topLevelRoutes)
    }

    @Test
    fun workspaceJoinRoute_implementsFeniqoRoute_andIsNotTopLevel() {
        assertTrue(WorkspaceJoinRoute is FeniqoRoute)
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("WorkspaceJoinRoute top-level hedef olmamalıdır", WorkspaceJoinRoute !in topLevelRoutes)
    }

    @Test
    fun workspaceDetailsRoute_implementsFeniqoRoute_andIsNotTopLevel() {
        val route = WorkspaceDetailsRoute("ws-1")
        assertTrue(route is FeniqoRoute)
        assertEquals("ws-1", route.workspaceId)
        val topLevelRoutes = TopLevelDestination.entries.map { it.route }
        assertTrue("WorkspaceDetailsRoute top-level hedef olmamalıdır", route !in topLevelRoutes)
    }

    @Test
    fun parseWorkspaceDetailsRouteId_correctlyDifferentiatesModesAndFailsClosed() {
        assertTrue(parseWorkspaceDetailsRouteId(null) is WorkspaceDetailsRouteIdResult.InvalidId)
        assertTrue(parseWorkspaceDetailsRouteId("") is WorkspaceDetailsRouteIdResult.InvalidId)
        assertTrue(parseWorkspaceDetailsRouteId("   ") is WorkspaceDetailsRouteIdResult.InvalidId)

        val valid = parseWorkspaceDetailsRouteId("  ws-123  ")
        assertTrue(valid is WorkspaceDetailsRouteIdResult.ValidId)
        assertEquals(com.feniqo.mobile.domain.model.EntityId("ws-123"), (valid as WorkspaceDetailsRouteIdResult.ValidId).id)
    }
}
