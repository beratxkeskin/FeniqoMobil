package com.feniqo.mobile.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.category.CategoriesScreenRoute
import com.feniqo.mobile.presentation.category.CategoriesViewModel
import com.feniqo.mobile.presentation.category.CategoryFormScreenRoute
import com.feniqo.mobile.presentation.auth.LoginViewModel
import com.feniqo.mobile.presentation.auth.RegisterViewModel
import com.feniqo.mobile.presentation.budget.BudgetFormScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetViewModel
import com.feniqo.mobile.presentation.dashboard.DashboardScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionsScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionsViewModel
import com.feniqo.mobile.presentation.recurring.toDomainCategory
import com.feniqo.mobile.presentation.screen.LoginScreen
import com.feniqo.mobile.presentation.screen.RegisterScreen
import com.feniqo.mobile.presentation.screen.SplashLoadingScreen
import com.feniqo.mobile.presentation.screen.ThemeSettingsPlaceholderScreen
import com.feniqo.mobile.presentation.transaction.TransactionFormScreenRoute
import com.feniqo.mobile.presentation.transaction.TransactionsScreenRoute
import com.feniqo.mobile.presentation.shell.AppSection
import com.feniqo.mobile.presentation.shell.FeniqoAppShell
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Android kök navigasyon bileşeni.
 * AppAuthState durumuna göre bağımsız AuthNavHost veya MainNavHost dallarını oluşturur.
 * Dal değiştiğinde eski NavHost Composition'dan çıkar ve eski back-stack yok olur.
 */
@Composable
fun FeniqoNavigation(
    authState: AppAuthState,
    syncStatus: SyncStatusUiState,
    onManualSync: () -> Unit,
    onRetryFailed: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (authState) {
        AppAuthState.Checking -> {
            SplashLoadingScreen(modifier = modifier)
        }
        AppAuthState.Unauthenticated -> {
            AuthNavHost(modifier = modifier)
        }
        AppAuthState.Authenticated -> {
            MainNavHost(
                syncStatus = syncStatus,
                onManualSync = onManualSync,
                onRetryFailed = onRetryFailed,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun AuthNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        modifier = modifier,
    ) {
        composable<LoginRoute> {
            val viewModel = hiltViewModel<LoginViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LoginScreen(
                state = state,
                onEmailChange = viewModel::onEmailChanged,
                onPasswordChange = viewModel::onPasswordChanged,
                onPasswordVisibilityToggle = viewModel::togglePasswordVisibility,
                onSubmit = viewModel::submit,
                onNavigateToRegister = {
                    viewModel.onPasswordChanged("")
                    navController.navigate(RegisterRoute) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<RegisterRoute> {
            val viewModel = hiltViewModel<RegisterViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            RegisterScreen(
                state = state,
                onFullNameChange = viewModel::onFullNameChanged,
                onEmailChange = viewModel::onEmailChanged,
                onPasswordChange = viewModel::onPasswordChanged,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
                onPasswordVisibilityToggle = viewModel::togglePasswordVisibility,
                onConfirmPasswordVisibilityToggle = viewModel::toggleConfirmPasswordVisibility,
                onSubmit = viewModel::submit,
                onNavigateToLogin = {
                    viewModel.onPasswordChanged("")
                    viewModel.onConfirmPasswordChanged("")
                    navController.navigate(LoginRoute) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@Composable
fun MainNavHost(
    syncStatus: SyncStatusUiState,
    onManualSync: () -> Unit,
    onRetryFailed: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

    val isTransactionForm = destination?.hasRoute<TransactionFormRoute>() == true
    val isCategoryForm = destination?.hasRoute<CategoryFormRoute>() == true
    val isBudgetForm = destination?.hasRoute<BudgetFormRoute>() == true
    val isRecurringForm = destination?.hasRoute<RecurringTransactionFormRoute>() == true
    val isDetailForm = isTransactionForm || isCategoryForm || isBudgetForm || isRecurringForm

    val currentSection = when {
        destination?.hasRoute<TransactionsRoute>() == true || isTransactionForm -> AppSection.TRANSACTIONS
        destination?.hasRoute<BudgetsRoute>() == true || isBudgetForm -> AppSection.BUDGETS
        destination?.hasRoute<CategoriesRoute>() == true || isCategoryForm -> AppSection.CATEGORIES
        destination?.hasRoute<SettingsRoute>() == true || destination?.hasRoute<RecurringTransactionsRoute>() == true || isRecurringForm -> AppSection.SETTINGS
        else -> AppSection.DASHBOARD
    }

    FeniqoAppShell(
        selectedSection = currentSection,
        onSectionSelect = { section ->
            val targetRoute = section.toTopLevelDestination().route
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        syncStatus = syncStatus,
        onManualSync = onManualSync,
        onRetryFailed = onRetryFailed,
        showNavigationChrome = !isDetailForm,
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
        ) {
            composable<DashboardRoute> {
                DashboardScreenRoute(
                    onAddTransaction = {
                        navController.navigate(TransactionFormRoute(null)) {
                            launchSingleTop = true
                        }
                    },
                    onViewAllTransactions = {
                        navController.navigate(TransactionsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onTransactionClick = { transactionId ->
                        navController.navigate(TransactionFormRoute(transactionId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreenRoute(
                    onAddTransaction = {
                        navController.navigate(TransactionFormRoute(null)) {
                            launchSingleTop = true
                        }
                    },
                    onEditTransaction = { transactionId ->
                        navController.navigate(TransactionFormRoute(transactionId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<TransactionFormRoute> {
                TransactionFormScreenRoute(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(TransactionsRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onAddCategory = { transactionType ->
                        navController.navigate(
                            CategoryFormRoute(
                                categoryId = null,
                                initialTypeCode = transactionType.name,
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<BudgetsRoute> {
                BudgetScreenRoute(
                    onAddBudget = { selectedMonth ->
                        navController.navigate(BudgetFormRoute(initialMonth = selectedMonth.value)) {
                            launchSingleTop = true
                        }
                    },
                    onEditBudget = { budgetId, month ->
                        navController.navigate(
                            BudgetFormRoute(
                                initialMonth = month.value,
                                budgetId = budgetId.value,
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<BudgetFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<BudgetFormRoute>()
                val budgetViewModel = hiltViewModel<BudgetViewModel>()
                val categoriesViewModel = hiltViewModel<CategoriesViewModel>()
                val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
                val availableCategories = categoriesState.systemCategories + categoriesState.customCategories

                val fallbackMonth = budgetViewModel.uiState.value.selectedMonth ?: budgetViewModel.initialSelectedMonth
                val initialMonth = parseBudgetInitialMonth(route.initialMonth, fallbackMonth)

                val editLoadState by budgetViewModel.editLoadState.collectAsStateWithLifecycle()

                val budgetRouteIdResult = remember(route.budgetId) {
                    parseBudgetRouteId(route.budgetId)
                }

                LaunchedEffect(budgetRouteIdResult) {
                    when (budgetRouteIdResult) {
                        BudgetRouteIdResult.CreateMode -> {
                            // Create mode - loadBudgetForEdit çağrılmaz
                        }
                        is BudgetRouteIdResult.ValidId -> {
                            budgetViewModel.loadBudgetForEdit(budgetRouteIdResult.id)
                        }
                        BudgetRouteIdResult.InvalidId -> {
                            budgetViewModel.setEditLoadInvalidId()
                        }
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    BudgetFormScreenRoute(
                        availableCategories = availableCategories,
                        viewModel = budgetViewModel,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(BudgetsRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onMessage = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message.toDisplayText())
                            }
                        },
                        editLoadState = editLoadState,
                        initialBudgetId = (budgetRouteIdResult as? BudgetRouteIdResult.ValidId)?.id,
                        initialMonth = initialMonth,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<CategoriesRoute> {
                CategoriesScreenRoute(
                    onAddCategory = { selectedType ->
                        navController.navigate(
                            CategoryFormRoute(
                                categoryId = null,
                                initialTypeCode = selectedType.name,
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onEditCategory = { categoryId ->
                        navController.navigate(
                            CategoryFormRoute(
                                categoryId = categoryId.value,
                                initialTypeCode = "EXPENSE",
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<CategoryFormRoute> {
                CategoryFormScreenRoute(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(CategoriesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable<SettingsRoute> {
                ThemeSettingsPlaceholderScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onNavigateToRecurringTransactions = {
                        navController.navigate(RecurringTransactionsRoute) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<RecurringTransactionsRoute> {
                RecurringTransactionsScreenRoute(
                    onAddRecurringTransaction = {
                        navController.navigate(RecurringTransactionFormRoute(recurringTransactionId = null)) {
                            launchSingleTop = true
                        }
                    },
                    onEditRecurringTransaction = { recurringId ->
                        navController.navigate(RecurringTransactionFormRoute(recurringTransactionId = recurringId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<RecurringTransactionFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RecurringTransactionFormRoute>()
                val recurringViewModel = hiltViewModel<RecurringTransactionsViewModel>()
                val categoriesViewModel = hiltViewModel<CategoriesViewModel>()
                val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
                val availableCategories = (categoriesState.systemCategories + categoriesState.customCategories).map { it.toDomainCategory() }

                val recurringRouteIdResult = remember(route.recurringTransactionId) {
                    parseRecurringRouteId(route.recurringTransactionId)
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    RecurringTransactionFormScreenRoute(
                        availableCategories = availableCategories,
                        viewModel = recurringViewModel,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(RecurringTransactionsRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onMessage = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message.toDisplayText())
                            }
                        },
                        initialRecurringTransactionId = (recurringRouteIdResult as? RecurringRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = recurringRouteIdResult is RecurringRouteIdResult.InvalidId,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
