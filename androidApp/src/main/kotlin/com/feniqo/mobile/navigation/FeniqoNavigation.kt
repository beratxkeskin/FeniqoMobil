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
import com.feniqo.mobile.presentation.debt.DebtFormScreenRoute
import com.feniqo.mobile.presentation.debt.DebtFormViewModel
import com.feniqo.mobile.presentation.debt.DebtPaymentFormScreenRoute
import com.feniqo.mobile.presentation.debt.DebtPaymentFormViewModel
import com.feniqo.mobile.presentation.debt.DebtsScreenRoute
import com.feniqo.mobile.presentation.goal.GoalContributionFormScreenRoute
import com.feniqo.mobile.presentation.goal.GoalContributionFormViewModel
import com.feniqo.mobile.presentation.goal.GoalFormScreenRoute
import com.feniqo.mobile.presentation.goal.GoalFormViewModel
import com.feniqo.mobile.presentation.goal.GoalsScreenRoute
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
import com.feniqo.mobile.presentation.screen.MoreHubScreen
import com.feniqo.mobile.presentation.screen.PlanHubScreen
import com.feniqo.mobile.presentation.screen.RegisterScreen
import com.feniqo.mobile.presentation.screen.SplashLoadingScreen
import com.feniqo.mobile.presentation.screen.ThemeSettingsPlaceholderScreen
import com.feniqo.mobile.presentation.subscription.SubscriptionFormScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsViewModel
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
    val isSubscriptionForm = destination?.hasRoute<SubscriptionFormRoute>() == true
    val isGoalForm = destination?.hasRoute<GoalFormRoute>() == true
    val isGoalContributionForm = destination?.hasRoute<GoalContributionFormRoute>() == true
    val isDebtForm = destination?.hasRoute<DebtFormRoute>() == true
    val isDebtPaymentForm = destination?.hasRoute<DebtPaymentFormRoute>() == true
    val isDetailForm = isTransactionForm || isCategoryForm || isBudgetForm || isRecurringForm || isSubscriptionForm || isGoalForm || isGoalContributionForm || isDebtForm || isDebtPaymentForm

    val currentSection = when {
        destination?.hasRoute<TransactionsRoute>() == true || isTransactionForm -> AppSection.TRANSACTIONS
        destination?.hasRoute<PlanRoute>() == true || destination?.hasRoute<BudgetsRoute>() == true || isBudgetForm || destination?.hasRoute<RecurringTransactionsRoute>() == true || isRecurringForm || destination?.hasRoute<SubscriptionsRoute>() == true || isSubscriptionForm || destination?.hasRoute<GoalsRoute>() == true || isGoalForm || isGoalContributionForm || destination?.hasRoute<DebtsRoute>() == true || isDebtForm || isDebtPaymentForm -> AppSection.PLAN
        destination?.hasRoute<MoreRoute>() == true || destination?.hasRoute<CategoriesRoute>() == true || isCategoryForm || destination?.hasRoute<SettingsRoute>() == true -> AppSection.MORE
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
        onPrimaryAction = {
            navController.navigate(TransactionFormRoute(null)) {
                launchSingleTop = true
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
            composable<PlanRoute> {
                PlanHubScreen(
                    onNavigateToBudgets = {
                        navController.navigate(BudgetsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRecurringTransactions = {
                        navController.navigate(RecurringTransactionsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSubscriptions = {
                        navController.navigate(SubscriptionsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToGoals = {
                        navController.navigate(GoalsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDebts = {
                        navController.navigate(DebtsRoute) {
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
            composable<MoreRoute> {
                MoreHubScreen(
                    onNavigateToCategories = {
                        navController.navigate(CategoriesRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute) {
                            launchSingleTop = true
                        }
                    },
                )
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
                )
            }
            composable<SubscriptionsRoute> {
                SubscriptionsScreenRoute(
                    onAddSubscription = {
                        navController.navigate(SubscriptionFormRoute(subscriptionId = null)) {
                            launchSingleTop = true
                        }
                    },
                    onSubscriptionClick = { subscriptionId ->
                        navController.navigate(SubscriptionFormRoute(subscriptionId = subscriptionId.value)) {
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
            composable<SubscriptionFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<SubscriptionFormRoute>()
                val subscriptionsViewModel = hiltViewModel<SubscriptionsViewModel>()
                val categoriesViewModel = hiltViewModel<CategoriesViewModel>()
                val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
                val availableCategories = (categoriesState.systemCategories + categoriesState.customCategories).map { it.toDomainCategory() }

                val subscriptionRouteIdResult = remember(route.subscriptionId) {
                    parseSubscriptionRouteId(route.subscriptionId)
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    SubscriptionFormScreenRoute(
                        availableCategories = availableCategories,
                        viewModel = subscriptionsViewModel,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(SubscriptionsRoute) {
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
                        initialSubscriptionId = (subscriptionRouteIdResult as? SubscriptionRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = subscriptionRouteIdResult is SubscriptionRouteIdResult.InvalidId,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<GoalsRoute> {
                GoalsScreenRoute(
                    onAddGoal = {
                        navController.navigate(GoalFormRoute(null)) {
                            launchSingleTop = true
                        }
                    },
                    onGoalClick = { goalId ->
                        navController.navigate(GoalFormRoute(goalId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<GoalFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GoalFormRoute>()
                val goalViewModel = hiltViewModel<GoalFormViewModel>()
                val goalRouteIdResult = remember(route.goalId) {
                    parseGoalRouteId(route.goalId)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    GoalFormScreenRoute(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(GoalsRoute) {
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
                        initialGoalId = (goalRouteIdResult as? GoalRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = goalRouteIdResult is GoalRouteIdResult.InvalidId,
                        onAddContribution = { goalId ->
                            navController.navigate(GoalContributionFormRoute(goalId.value)) {
                                launchSingleTop = true
                            }
                        },
                        viewModel = goalViewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<GoalContributionFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GoalContributionFormRoute>()
                val contributionViewModel = hiltViewModel<GoalContributionFormViewModel>()
                val childRouteIdResult = remember(route.goalId) {
                    parseGoalContributionRouteId(route.goalId)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    GoalContributionFormScreenRoute(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(GoalsRoute) {
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
                        parentGoalId = (childRouteIdResult as? ChildRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = childRouteIdResult is ChildRouteIdResult.InvalidId,
                        viewModel = contributionViewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<DebtsRoute> {
                DebtsScreenRoute(
                    onAddDebt = {
                        navController.navigate(DebtFormRoute(null)) {
                            launchSingleTop = true
                        }
                    },
                    onDebtClick = { debtId ->
                        navController.navigate(DebtFormRoute(debtId.value)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSnowballPlan = {
                        navController.navigate(DebtSnowballPlanRoute) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<DebtFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DebtFormRoute>()
                val debtViewModel = hiltViewModel<DebtFormViewModel>()
                val debtRouteIdResult = remember(route.debtId) {
                    parseDebtRouteId(route.debtId)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    DebtFormScreenRoute(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(DebtsRoute) {
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
                        initialDebtId = (debtRouteIdResult as? DebtRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = debtRouteIdResult is DebtRouteIdResult.InvalidId,
                        onAddPayment = { debtId ->
                            navController.navigate(DebtPaymentFormRoute(debtId.value)) {
                                launchSingleTop = true
                            }
                        },
                        viewModel = debtViewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<DebtPaymentFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DebtPaymentFormRoute>()
                val paymentViewModel = hiltViewModel<DebtPaymentFormViewModel>()
                val childRouteIdResult = remember(route.debtId) {
                    parseDebtPaymentRouteId(route.debtId)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    DebtPaymentFormScreenRoute(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(DebtsRoute) {
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
                        parentDebtId = (childRouteIdResult as? ChildRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = childRouteIdResult is ChildRouteIdResult.InvalidId,
                        viewModel = paymentViewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<DebtSnowballPlanRoute> {
                val snowballViewModel = hiltViewModel<com.feniqo.mobile.presentation.debt.DebtSnowballPlanViewModel>()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    com.feniqo.mobile.presentation.debt.DebtSnowballPlanScreenRoute(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(DebtsRoute) {
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
                        viewModel = snowballViewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
