package com.feniqo.mobile.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.presentation.category.CategoriesScreenRoute
import com.feniqo.mobile.presentation.category.CategoriesViewModel
import com.feniqo.mobile.presentation.category.CategoryFormScreenRoute
import com.feniqo.mobile.presentation.debt.DebtFormScreenRoute
import com.feniqo.mobile.presentation.debt.DebtFormViewModel
import com.feniqo.mobile.presentation.debt.DebtPaymentFormScreenRoute
import com.feniqo.mobile.presentation.debt.DebtPaymentFormViewModel
import com.feniqo.mobile.presentation.debt.DebtsScreenRoute
import com.feniqo.mobile.presentation.goal.GoalContributionFormScreenRoute
import com.feniqo.mobile.presentation.goal.GoalDetailScreenRoute
import com.feniqo.mobile.presentation.goal.GoalContributionFormViewModel
import com.feniqo.mobile.presentation.goal.GoalFormScreenRoute
import com.feniqo.mobile.presentation.goal.GoalFormViewModel
import com.feniqo.mobile.presentation.goal.GoalsScreenRoute
import com.feniqo.mobile.presentation.auth.LoginViewModel
import com.feniqo.mobile.presentation.auth.RegisterViewModel
import com.feniqo.mobile.presentation.asset.AssetsScreenRoute
import com.feniqo.mobile.presentation.asset.AssetFormScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetDetailScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetFormScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetScreenRoute
import com.feniqo.mobile.presentation.budget.BudgetViewModel
import com.feniqo.mobile.presentation.dashboard.DashboardScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionsScreenRoute
import com.feniqo.mobile.presentation.recurring.RecurringTransactionsViewModel
import com.feniqo.mobile.presentation.recurring.toDomainCategory
import com.feniqo.mobile.presentation.screen.LoginScreen
import com.feniqo.mobile.presentation.hub.MoreHubScreenRoute
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.screen.RegisterScreen
import com.feniqo.mobile.presentation.screen.SplashLoadingScreen
import com.feniqo.mobile.presentation.settings.SettingsScreenRoute
import com.feniqo.mobile.presentation.profile.ProfileViewModel
import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailViewModel
import com.feniqo.mobile.presentation.subscription.SubscriptionFormScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsViewModel
import com.feniqo.mobile.presentation.transaction.TransactionFormScreenRoute
import com.feniqo.mobile.presentation.transaction.TransactionsScreenRoute
import com.feniqo.mobile.presentation.shell.AppSection
import com.feniqo.mobile.presentation.shell.FeniqoAppShell
import com.feniqo.mobile.presentation.component.QuickAddAction
import com.feniqo.mobile.presentation.component.QuickAddSheet
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.theme.ThemeMode
import com.feniqo.mobile.presentation.workspace.WorkspacePickerScreenRoute
import com.feniqo.mobile.presentation.workspace.WorkspaceCreateScreenRoute
import com.feniqo.mobile.presentation.workspace.WorkspaceJoinScreenRoute
import com.feniqo.mobile.presentation.workspace.WorkspaceDetailsScreenRoute
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementScreenRoute
import kotlinx.coroutines.launch

import com.feniqo.mobile.domain.repository.ConflictResolution

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
    onResolveConflict: () -> Unit = {},
    onResolveConflictDecision: (ConflictResolution) -> Unit = {},
    onDismissConflictDialog: () -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean = false,
    biometricLockAvailable: Boolean = false,
    autoLockTimeout: AutoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
    onBiometricLockChange: (Boolean) -> Unit = {},
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit = {},
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
                onResolveConflict = onResolveConflict,
                onResolveConflictDecision = onResolveConflictDecision,
                onDismissConflictDialog = onDismissConflictDialog,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                biometricLockEnabled = biometricLockEnabled,
                biometricLockAvailable = biometricLockAvailable,
                autoLockTimeout = autoLockTimeout,
                onBiometricLockChange = onBiometricLockChange,
                onAutoLockTimeoutChange = onAutoLockTimeoutChange,
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
    onResolveConflict: () -> Unit = {},
    onResolveConflictDecision: (ConflictResolution) -> Unit = {},
    onDismissConflictDialog: () -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean = false,
    biometricLockAvailable: Boolean = false,
    autoLockTimeout: AutoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
    onBiometricLockChange: (Boolean) -> Unit = {},
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit = {},
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

    val isTransactionForm = destination?.hasRoute<TransactionFormRoute>() == true
    val isTransactionSuccess = destination?.hasRoute<TransactionSuccessRoute>() == true
    val isCategoryForm = destination?.hasRoute<CategoryFormRoute>() == true
    val isBudgetForm = destination?.hasRoute<BudgetFormRoute>() == true
    val isBudgetDetail = destination?.hasRoute<BudgetDetailRoute>() == true
    val isRecurringForm = destination?.hasRoute<RecurringTransactionFormRoute>() == true
    val isSubscriptionForm = destination?.hasRoute<SubscriptionFormRoute>() == true
    val isSubscriptionDetail = destination?.hasRoute<SubscriptionDetailRoute>() == true
    val isGoalForm = destination?.hasRoute<GoalFormRoute>() == true
    val isGoalDetail = destination?.hasRoute<GoalDetailRoute>() == true
    val isGoalContributionForm = destination?.hasRoute<GoalContributionFormRoute>() == true
    val isDebtForm = destination?.hasRoute<DebtFormRoute>() == true
    val isDebtPaymentForm = destination?.hasRoute<DebtPaymentFormRoute>() == true
    val isWorkspacePicker = destination?.hasRoute<WorkspacePickerRoute>() == true
    val isWorkspaceCreate = destination?.hasRoute<WorkspaceCreateRoute>() == true
    val isWorkspaceJoin = destination?.hasRoute<WorkspaceJoinRoute>() == true
    val isWorkspaceDetails = destination?.hasRoute<WorkspaceDetailsRoute>() == true
    val isWorkspaceSettlement = destination?.hasRoute<WorkspaceSettlementRoute>() == true
    val isAssetForm = destination?.hasRoute<AssetFormRoute>() == true
    val isProfile = destination?.hasRoute<ProfileRoute>() == true
    val isSettings = destination?.hasRoute<SettingsRoute>() == true
    val isDetailForm = isTransactionForm || isTransactionSuccess || isCategoryForm || isBudgetForm || isBudgetDetail || isRecurringForm || isSubscriptionForm || isSubscriptionDetail || isGoalDetail || isGoalForm || isGoalContributionForm || isDebtForm || isDebtPaymentForm || isWorkspacePicker || isWorkspaceCreate || isWorkspaceJoin || isWorkspaceDetails || isWorkspaceSettlement || isAssetForm || isProfile || isSettings
    var showQuickAdd by remember { mutableStateOf(false) }

    val currentSection = when {
        destination?.hasRoute<TransactionsRoute>() == true || isTransactionForm -> AppSection.TRANSACTIONS
        destination?.hasRoute<BudgetsRoute>() == true || isBudgetForm || isBudgetDetail -> AppSection.BUDGET
        destination?.hasRoute<MoreRoute>() == true || destination?.hasRoute<AssetsRoute>() == true || isAssetForm || destination?.hasRoute<CategoriesRoute>() == true || isCategoryForm || destination?.hasRoute<RecurringTransactionsRoute>() == true || isRecurringForm || destination?.hasRoute<SubscriptionsRoute>() == true || isSubscriptionForm || isSubscriptionDetail || destination?.hasRoute<GoalsRoute>() == true || isGoalDetail || isGoalForm || isGoalContributionForm || destination?.hasRoute<DebtsRoute>() == true || isDebtForm || isDebtPaymentForm || isWorkspacePicker || isWorkspaceCreate || isWorkspaceJoin || isWorkspaceDetails || isWorkspaceSettlement -> AppSection.MORE
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
            showQuickAdd = true
        },
        syncStatus = syncStatus,
        onManualSync = onManualSync,
        onRetryFailed = onRetryFailed,
        onResolveConflict = onResolveConflict,
        onResolveConflictDecision = onResolveConflictDecision,
        onDismissConflictDialog = onDismissConflictDialog,
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
                        navController.navigate(TransactionsRoute()) {
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
                    onProfileClick = {
                        navController.navigate(ProfileRoute) { launchSingleTop = true }
                    },
                    onBudgetsClick = {
                        navController.navigate(BudgetsRoute) { launchSingleTop = true }
                    },
                    onSubscriptionsClick = {
                        navController.navigate(SubscriptionsRoute) { launchSingleTop = true }
                    },
                    onGoalClick = {
                        navController.navigate(GoalsRoute) { launchSingleTop = true }
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
                            navController.navigate(TransactionsRoute()) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onTransactionCreated = { createdId ->
                        navController.navigate(TransactionSuccessRoute(createdId.value)) {
                            popUpTo<TransactionFormRoute> { inclusive = true }
                            launchSingleTop = true
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
            composable<TransactionSuccessRoute> {
                com.feniqo.mobile.presentation.transaction.TransactionSuccessScreenRoute(
                    onAddNewTransaction = { type ->
                        navController.navigate(TransactionFormRoute(initialTypeCode = type.name)) {
                            popUpTo<TransactionSuccessRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onViewTransaction = { id ->
                        navController.navigate(TransactionFormRoute(transactionId = id.value)) {
                            popUpTo<TransactionSuccessRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onClose = {
                        navController.navigate(TransactionsRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
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
                    onBudgetClick = { budgetId, month ->
                        navController.navigate(
                            BudgetDetailRoute(
                                budgetId = budgetId.value,
                                month = month.value,
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
            composable<BudgetDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<BudgetDetailRoute>()
                val budgetId = EntityId(route.budgetId)
                val month = YearMonth(route.month)
                BudgetDetailScreenRoute(
                    budgetId = budgetId,
                    month = month,
                    onBack = { navController.popBackStack() },
                    onEditBudget = { bId, m ->
                        navController.navigate(
                            BudgetFormRoute(
                                initialMonth = m.value,
                                budgetId = bId.value,
                            ),
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onViewAllTransactions = { categoryId, _ ->
                        navController.navigate(TransactionsRoute(categoryId = categoryId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<MoreRoute> {
                MoreHubScreenRoute(
                    onNavigateToAssets = { navController.navigate(AssetsRoute) { launchSingleTop = true } },
                    onNavigateToGoals = { navController.navigate(GoalsRoute) { launchSingleTop = true } },
                    onNavigateToDebts = { navController.navigate(DebtsRoute) { launchSingleTop = true } },
                    onNavigateToSubscriptions = { navController.navigate(SubscriptionsRoute) { launchSingleTop = true } },
                    onNavigateToRecurringTransactions = { navController.navigate(RecurringTransactionsRoute) { launchSingleTop = true } },
                    onNavigateToCategories = {
                        navController.navigate(CategoriesRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSharedSpaces = {
                        navController.navigate(WorkspacePickerRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate(ProfileRoute) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ProfileRoute> {
                val profileViewModel = hiltViewModel<ProfileViewModel>()
                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    displayName = profileState.displayName,
                    email = profileState.email,
                    workspaceName = profileState.workspaceName,
                    currencyCode = profileState.currencyCode,
                    isProfileLoading = profileState.isLoading,
                    signOutError = profileState.signOutError,
                    themeLabel = when (themeMode) {
                        ThemeMode.SYSTEM -> "Sistem teması"
                        ThemeMode.LIGHT -> "Açık tema"
                        ThemeMode.DARK -> "Koyu tema"
                    },
                    biometricLockEnabled = biometricLockEnabled,
                    autoLockLabel = autoLockTimeout.profileLabel(),
                    syncStatus = syncStatus,
                    appVersion = "Sürüm ${BuildConfig.VERSION_NAME}",
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    onOpenSharedSpaces = { navController.navigate(WorkspacePickerRoute) { launchSingleTop = true } },
                    onSignOut = profileViewModel::signOut,
                )
            }
            composable<AssetsRoute> {
                AssetsScreenRoute(
                    onAddAsset = { navController.navigate(AssetFormRoute()) },
                    onAssetClick = { navController.navigate(AssetFormRoute(it.value)) },
                )
            }
            composable<AssetFormRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AssetFormRoute>()
                val parsed = remember(route.assetId) { parseAssetRouteId(route.assetId) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    AssetFormScreenRoute(
                        initialAssetId = (parsed as? AssetRouteIdResult.ValidId)?.id,
                        hasInvalidRouteId = parsed == AssetRouteIdResult.InvalidId,
                        onNavigateBack = { if (!navController.popBackStack()) navController.navigate(AssetsRoute) },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message.toDisplayText()) } },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            composable<WorkspacePickerRoute> {
                WorkspacePickerScreenRoute(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(MoreRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onCreateWorkspace = { navController.navigate(WorkspaceCreateRoute) { launchSingleTop = true } },
                    onJoinWorkspace = { navController.navigate(WorkspaceJoinRoute) { launchSingleTop = true } },
                    onWorkspaceDetails = { workspaceId ->
                        navController.navigate(WorkspaceDetailsRoute(workspaceId.value)) { launchSingleTop = true }
                    },
                )
            }
            composable<WorkspaceCreateRoute> {
                WorkspaceCreateScreenRoute(onBack = { navController.popBackStack() })
            }
            composable<WorkspaceJoinRoute> {
                WorkspaceJoinScreenRoute(onBack = { navController.popBackStack() })
            }
            composable<WorkspaceDetailsRoute> {
                WorkspaceDetailsScreenRoute(
                    onBack = { navController.popBackStack() },
                    onNavigateToSettlement = { workspaceId ->
                        navController.navigate(WorkspaceSettlementRoute(workspaceId)) { launchSingleTop = true }
                    },
                )
            }
            composable<WorkspaceSettlementRoute> {
                WorkspaceSettlementScreenRoute(onBack = { navController.popBackStack() })
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
                    onCategoryClick = { categoryId, yearMonth ->
                        val (currentPeriod, _) = com.feniqo.mobile.presentation.category.CategoryAnalyticsCalculator.calculateReportPeriods(yearMonth)
                        navController.navigate(
                            TransactionsRoute(
                                categoryId = categoryId.value,
                                startDate = currentPeriod.startDate.toString(),
                                endDate = currentPeriod.endDate.toString(),
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
                SettingsScreenRoute(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    biometricLockEnabled = biometricLockEnabled,
                    biometricLockAvailable = biometricLockAvailable,
                    autoLockTimeout = autoLockTimeout,
                    onBiometricLockChange = onBiometricLockChange,
                    onAutoLockTimeoutChange = onAutoLockTimeoutChange,
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
                        navController.navigate(SubscriptionDetailRoute(subscriptionId = subscriptionId.value)) {
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
                    onBack = { navController.popBackStack() },
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
            composable<SubscriptionDetailRoute> { backStackEntry ->
                val detailViewModel = hiltViewModel<SubscriptionDetailViewModel>()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    SubscriptionDetailScreenRoute(
                        viewModel = detailViewModel,
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
                        onNavigateToEdit = { editId ->
                            navController.navigate(SubscriptionFormRoute(subscriptionId = editId)) {
                                launchSingleTop = true
                            }
                        },
                        onMessage = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message.toDisplayText())
                            }
                        },
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
                        navController.navigate(GoalDetailRoute(goalId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<GoalDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GoalDetailRoute>()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                Box {
                    GoalDetailScreenRoute(
                        onBack = { if (!navController.popBackStack()) navController.navigate(GoalsRoute) },
                        onEdit = { navController.navigate(GoalFormRoute(route.goalId)) },
                        onAdd = { navController.navigate(GoalContributionFormRoute(route.goalId, "ADD")) },
                        onRemove = { navController.navigate(GoalContributionFormRoute(route.goalId, "REMOVE")) },
                        onDeleted = { navController.navigate(GoalsRoute) { popUpTo<GoalsRoute> { inclusive = false }; launchSingleTop = true } },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message.toDisplayText()) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
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
                        initialDirectionCode = route.initialDirectionCode,
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

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onAction = { action ->
                showQuickAdd = false
                when (action) {
                    QuickAddAction.EXPENSE -> navController.navigate(TransactionFormRoute(initialTypeCode = TransactionType.EXPENSE.name))
                    QuickAddAction.INCOME -> navController.navigate(TransactionFormRoute(initialTypeCode = TransactionType.INCOME.name))
                    QuickAddAction.DEBT_RECEIVABLE -> navController.navigate(DebtFormRoute())
                    QuickAddAction.RECURRING_TRANSACTION -> navController.navigate(RecurringTransactionFormRoute())
                    QuickAddAction.TRANSFER -> Unit
                }
            },
        )
    }
}

private fun AutoLockTimeout.profileLabel(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Anında"
    AutoLockTimeout.AFTER_30_SECONDS -> "30 saniye"
    AutoLockTimeout.AFTER_1_MINUTE -> "1 dakika"
    AutoLockTimeout.AFTER_5_MINUTES -> "5 dakika"
}
