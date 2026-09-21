package com.feniqo.mobile.navigation

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainNavigationActionsTest {
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        nav = NavHostController(ApplicationProvider.getApplicationContext())
        nav.setViewModelStore(ViewModelStore())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = DashboardRoute) {
            composable<DashboardRoute> { }
            composable<TransactionsRoute> { }
            composable<BudgetsRoute> { }
            composable<MoreRoute> { }
            composable<ReportsRoute> { }
            composable<TransactionFormRoute> { }
            composable<TransactionSuccessRoute> { }
        }
    }

    @Test
    fun closingSuccessFromEveryTabThenSwitchingTabsNeverRestoresSuccess() {
        for (origin in TopLevelDestination.entries) {
            nav.navigateToSection(origin)
            nav.navigate(TransactionFormRoute())
            nav.navigate(TransactionSuccessRoute("created-transaction")) {
                popUpTo<TransactionFormRoute> { inclusive = true }
            }
            nav.closeTransactionSuccess()
            assertTrue(nav.currentDestination!!.hasRoute<TransactionsRoute>())
            for (target in TopLevelDestination.entries) {
                nav.navigateToSection(target)
                assertEquals(target.route::class.qualifiedName,
                    nav.currentDestination!!.route?.substringBefore("?"))
            }
            nav.navigateToSection(TopLevelDestination.TRANSACTIONS)
            assertTrue(nav.popBackStack())
            assertTrue(nav.currentDestination!!.hasRoute<DashboardRoute>())
        }
    }

    @Test
    fun homeIgnoresSuccessStackSavedByOlderNavigation() {
        nav.navigate(TransactionSuccessRoute("deleted-transaction"))
        nav.navigate(TransactionsRoute()) {
            popUpTo<DashboardRoute> { saveState = true }
        }
        nav.navigateToSection(TopLevelDestination.DASHBOARD)
        assertTrue(nav.currentDestination!!.hasRoute<DashboardRoute>())
    }

    @Test
    fun reportsHomeAndRepeatedHomeSelectionStayOnDashboard() {
        nav.navigate(ReportsRoute)
        repeat(3) {
            nav.navigateToSection(TopLevelDestination.DASHBOARD)
            assertTrue(nav.currentDestination!!.hasRoute<DashboardRoute>())
        }
    }

    @Test
    fun switchingTabsPreservesExistingSectionState() {
        nav.navigateToSection(TopLevelDestination.TRANSACTIONS)
        val entry = nav.currentBackStackEntry!!
        entry.savedStateHandle["filter"] = "expense"
        nav.navigateToSection(TopLevelDestination.BUDGET)
        nav.navigateToSection(TopLevelDestination.TRANSACTIONS)
        assertEquals("expense", nav.currentBackStackEntry!!.savedStateHandle.get<String>("filter"))
    }
}
