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
import androidx.navigation.NavDestination
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
import com.feniqo.mobile.presentation.common.resolveLocalizedText
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
import com.feniqo.mobile.presentation.asset.AssetDetailScreenRoute
import com.feniqo.mobile.presentation.asset.AssetDistributionScreenRoute
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
import com.feniqo.mobile.presentation.screen.RegisterScreen
import com.feniqo.mobile.presentation.screen.WelcomeScreen
import com.feniqo.mobile.presentation.screen.AuthEmailVerificationScreen
import com.feniqo.mobile.presentation.screen.ForgotPasswordScreen
import com.feniqo.mobile.presentation.screen.PasswordResetSentScreen
import com.feniqo.mobile.presentation.screen.ResetPasswordScreen
import com.feniqo.mobile.presentation.screen.PasswordResetSuccessScreen
import com.feniqo.mobile.presentation.auth.EmailVerificationViewModel
import com.feniqo.mobile.presentation.auth.ForgotPasswordViewModel
import com.feniqo.mobile.presentation.auth.ResetPasswordViewModel
import com.feniqo.mobile.presentation.auth.toLocalizedText
import com.feniqo.mobile.presentation.hub.MoreHubScreenRoute
import com.feniqo.mobile.presentation.report.AllReportsHubRoute
import com.feniqo.mobile.presentation.report.BudgetPerformanceReportRoute
import com.feniqo.mobile.presentation.report.CashFlowReportRoute
import com.feniqo.mobile.presentation.report.CategoryBreakdownReportRoute
import com.feniqo.mobile.presentation.report.CategoryDetailReportRoute
import com.feniqo.mobile.presentation.report.CustomDateRangeRoute
import com.feniqo.mobile.presentation.report.DebtSummaryReportRoute
import com.feniqo.mobile.presentation.report.FinancialInsightsReportRoute
import com.feniqo.mobile.presentation.report.ForecastReportRoute
import com.feniqo.mobile.presentation.report.MultiCurrencyReportRoute
import com.feniqo.mobile.presentation.report.PeriodComparisonReportRoute
import com.feniqo.mobile.presentation.report.PeriodSummaryReportRoute
import com.feniqo.mobile.presentation.report.ReportsScreenRoute
import com.feniqo.mobile.presentation.report.SpendingCalendarReportRoute
import com.feniqo.mobile.presentation.report.SubscriptionSummaryReportRoute
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.screen.SplashLoadingScreen
import com.feniqo.mobile.presentation.settings.AccountScreenRoute
import com.feniqo.mobile.presentation.settings.AppearanceScreenRoute
import com.feniqo.mobile.presentation.settings.ChangeEmailScreenRoute
import com.feniqo.mobile.presentation.settings.ChangePasswordScreenRoute
import com.feniqo.mobile.presentation.settings.DataManagementScreenRoute
import com.feniqo.mobile.presentation.settings.DeleteAccountScreenRoute
import com.feniqo.mobile.presentation.settings.EmailSettingsScreenRoute
import com.feniqo.mobile.presentation.settings.EmailVerificationScreenRoute
import com.feniqo.mobile.presentation.settings.FeedbackScreenRoute
import com.feniqo.mobile.presentation.settings.HelpAboutScreenRoute
import com.feniqo.mobile.presentation.settings.HelpArticleStatusScreenRoute
import com.feniqo.mobile.presentation.settings.HelpCenterScreenRoute
import com.feniqo.mobile.presentation.settings.LanguageRegionScreenRoute
import com.feniqo.mobile.presentation.settings.LegalInfoScreenRoute
import com.feniqo.mobile.presentation.settings.NotificationsScreenRoute
import com.feniqo.mobile.presentation.settings.PersonalInfoScreenRoute
import com.feniqo.mobile.presentation.settings.PhotoPreviewScreenRoute
import com.feniqo.mobile.presentation.settings.SecurityPrivacyScreenRoute
import com.feniqo.mobile.presentation.settings.SettingsScreenRoute
import com.feniqo.mobile.presentation.settings.SyncStatusScreenRoute
import com.feniqo.mobile.presentation.profile.ProfileViewModel
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionDetailViewModel
import com.feniqo.mobile.presentation.subscription.SubscriptionFormScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsScreenRoute
import com.feniqo.mobile.presentation.subscription.SubscriptionsViewModel
import com.feniqo.mobile.presentation.transaction.TransactionFormScreenRoute
import com.feniqo.mobile.presentation.transaction.TransactionDetailScreenRoute
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
            AuthNavHost(
                startDestination = WelcomeRoute,
                modifier = modifier,
            )
        }
        is AppAuthState.PasswordRecovery -> {
            AuthNavHost(
                startDestination = ResetPasswordRoute,
                modifier = modifier,
            )
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
    startDestination: FeniqoRoute = WelcomeRoute,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onNavigateToLogin = {
                    navController.navigate(LoginRoute)
                },
                onNavigateToRegister = {
                    navController.navigate(RegisterRoute)
                },
            )
        }
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
                onNavigateToForgotPassword = {
                    viewModel.onPasswordChanged("")
                    navController.navigate(ForgotPasswordRoute)
                },
                onNavigateToEmailVerification = { email ->
                    navController.navigate(
                        AuthEmailVerificationRoute(email = email, isRequiredOnLogin = true)
                    )
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(WelcomeRoute) {
                            popUpTo(WelcomeRoute) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<RegisterRoute> {
            val viewModel = hiltViewModel<RegisterViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.isEmailConfirmationPending) {
                if (state.isEmailConfirmationPending) {
                    val email = state.email
                    viewModel.onPasswordChanged("")
                    viewModel.onConfirmPasswordChanged("")
                    navController.navigate(
                        AuthEmailVerificationRoute(email = email, isRequiredOnLogin = false)
                    ) {
                        popUpTo(RegisterRoute) { inclusive = true }
                    }
                }
            }

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
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(WelcomeRoute) {
                            popUpTo(WelcomeRoute) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<AuthEmailVerificationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthEmailVerificationRoute>()
            val viewModel = hiltViewModel<EmailVerificationViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            AuthEmailVerificationScreen(
                email = route.email,
                isRequiredOnLogin = route.isRequiredOnLogin,
                isResending = state.isResending,
                resendSuccess = state.resendSuccess,
                errorMessage = state.generalMessage,
                onResend = { viewModel.resend(route.email) },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<ForgotPasswordRoute> {
            val viewModel = hiltViewModel<ForgotPasswordViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            ForgotPasswordScreen(
                email = state.email,
                onEmailChange = viewModel::onEmailChanged,
                onSubmit = {
                    viewModel.submit { email ->
                        navController.navigate(PasswordResetSentRoute(email = email))
                    }
                },
                onNavigateToLogin = {
                    viewModel.clearState()
                    navController.popBackStack()
                },
                isSubmitting = state.isSubmitting,
                errorMessage = state.generalMessage,
                emailError = state.emailError?.toLocalizedText(),
                onBack = {
                    viewModel.clearState()
                    navController.popBackStack()
                },
            )
        }
        composable<PasswordResetSentRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PasswordResetSentRoute>()
            val forgotViewModel = hiltViewModel<ForgotPasswordViewModel>()

            PasswordResetSentScreen(
                email = route.email,
                onNavigateToLogin = {
                    forgotViewModel.clearState()
                    navController.navigate(LoginRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditEmail = {
                    navController.popBackStack()
                },
                onResend = {
                    forgotViewModel.submit { }
                },
            )
        }
        composable<ResetPasswordRoute> {
            val viewModel = hiltViewModel<ResetPasswordViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            ResetPasswordScreen(
                password = state.password,
                confirmPassword = state.confirmPassword,
                onPasswordChange = viewModel::onPasswordChanged,
                onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
                onPasswordVisibilityToggle = viewModel::togglePasswordVisibility,
                onConfirmPasswordVisibilityToggle = viewModel::toggleConfirmPasswordVisibility,
                isPasswordVisible = state.isPasswordVisible,
                isConfirmPasswordVisible = state.isConfirmPasswordVisible,
                isSubmitting = state.isSubmitting,
                isInvalidOrExpiredLink = state.isRecoveryLinkInvalid,
                errorMessage = state.generalMessage,
                passwordError = state.passwordError?.toLocalizedText(),
                confirmPasswordError = state.confirmPasswordError?.toLocalizedText(),
                onSubmit = {
                    viewModel.submit {
                        navController.navigate(PasswordResetSuccessRoute) {
                            popUpTo(ResetPasswordRoute) { inclusive = true }
                        }
                    }
                },
                onRequestNewLink = {
                    viewModel.abandonRecovery {
                        navController.navigate(ForgotPasswordRoute) {
                            popUpTo(LoginRoute) { inclusive = false }
                        }
                    }
                },
                onNavigateToLogin = {
                    viewModel.abandonRecovery {
                        navController.navigate(LoginRoute) {
                            popUpTo(LoginRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable<PasswordResetSuccessRoute> {
            PasswordResetSuccessScreen(
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
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
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

    val isTransactionForm = destination?.hasRoute<TransactionFormRoute>() == true
    val isTransactionDetail = destination?.hasRoute<TransactionDetailRoute>() == true
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
    val isDebtSnowballPlan = destination?.hasRoute<DebtSnowballPlanRoute>() == true
    val isWorkspacePicker = destination?.hasRoute<WorkspacePickerRoute>() == true
    val isWorkspaceCreate = destination?.hasRoute<WorkspaceCreateRoute>() == true
    val isWorkspaceJoin = destination?.hasRoute<WorkspaceJoinRoute>() == true
    val isWorkspaceDetails = destination?.hasRoute<WorkspaceDetailsRoute>() == true
    val isWorkspaceSettlement = destination?.hasRoute<WorkspaceSettlementRoute>() == true
    val isAssetForm = destination?.hasRoute<AssetFormRoute>() == true
    val isAssetDetail = destination?.hasRoute<AssetDetailRoute>() == true
    val isAssetDistribution = destination?.hasRoute<AssetDistributionRoute>() == true
    val isProfile = destination?.hasRoute<ProfileRoute>() == true
    val isSettings = destination?.hasRoute<SettingsRoute>() == true
    val isSettingsSubRoute = destination?.hasRoute<AccountRoute>() == true ||
        destination?.hasRoute<PersonalInfoRoute>() == true ||
        destination?.hasRoute<AppearanceRoute>() == true ||
        destination?.hasRoute<LanguageRegionRoute>() == true ||
        destination?.hasRoute<NotificationsSettingsRoute>() == true ||
        destination?.hasRoute<SecurityPrivacyRoute>() == true ||
        destination?.hasRoute<DataManagementRoute>() == true ||
        destination?.hasRoute<SyncStatusRoute>() == true ||
        destination?.hasRoute<HelpAboutRoute>() == true ||
        destination?.hasRoute<ChangeEmailRoute>() == true ||
        destination?.hasRoute<EmailVerificationRoute>() == true ||
        destination?.hasRoute<ChangePasswordRoute>() == true ||
        destination?.hasRoute<DeleteAccountRoute>() == true ||
        destination?.hasRoute<FeedbackRoute>() == true ||
        destination?.hasRoute<PhotoPreviewRoute>() == true ||
        destination?.hasRoute<EmailSettingsRoute>() == true ||
        destination?.hasRoute<HelpCenterRoute>() == true ||
        destination?.hasRoute<HelpArticleStatusRoute>() == true ||
        destination?.hasRoute<LegalInfoRoute>() == true
    val isReportSubRoute = destination?.hasRoute<CustomDateRangeRoute>() == true ||
        destination?.hasRoute<MultiCurrencyReportRoute>() == true ||
        destination?.hasRoute<PeriodSummaryReportRoute>() == true ||
        destination?.hasRoute<AllReportsHubRoute>() == true ||
        destination?.hasRoute<CategoryBreakdownReportRoute>() == true ||
        destination?.hasRoute<CategoryDetailReportRoute>() == true ||
        destination?.hasRoute<CashFlowReportRoute>() == true ||
        destination?.hasRoute<PeriodComparisonReportRoute>() == true ||
        destination?.hasRoute<SpendingCalendarReportRoute>() == true ||
        destination?.hasRoute<BudgetPerformanceReportRoute>() == true ||
        destination?.hasRoute<SubscriptionSummaryReportRoute>() == true ||
        destination?.hasRoute<DebtSummaryReportRoute>() == true ||
        destination?.hasRoute<ForecastReportRoute>() == true ||
        destination?.hasRoute<FinancialInsightsReportRoute>() == true
    val isReports = destination?.hasRoute<ReportsRoute>() == true
    val isDetailForm = shouldHideMainNavigationChrome(destination)
    var showQuickAdd by remember { mutableStateOf(false) }

    val currentSection = when {
        destination?.hasRoute<TransactionsRoute>() == true || isTransactionForm || isTransactionDetail -> AppSection.TRANSACTIONS
        destination?.hasRoute<BudgetsRoute>() == true || isBudgetForm || isBudgetDetail -> AppSection.BUDGET
        destination?.hasRoute<MoreRoute>() == true || destination?.hasRoute<AssetsRoute>() == true || isAssetForm || isAssetDetail || isAssetDistribution || destination?.hasRoute<CategoriesRoute>() == true || isCategoryForm || destination?.hasRoute<RecurringTransactionsRoute>() == true || isRecurringForm || destination?.hasRoute<SubscriptionsRoute>() == true || isSubscriptionForm || isSubscriptionDetail || destination?.hasRoute<GoalsRoute>() == true || isGoalDetail || isGoalForm || isGoalContributionForm || destination?.hasRoute<DebtsRoute>() == true || isDebtForm || isDebtPaymentForm || isDebtSnowballPlan || isWorkspacePicker || isWorkspaceCreate || isWorkspaceJoin || isWorkspaceDetails || isWorkspaceSettlement || isReports || isReportSubRoute -> AppSection.MORE
        else -> AppSection.DASHBOARD
    }


    FeniqoAppShell(
        selectedSection = currentSection,
        onSectionSelect = { section ->
            navController.navigateToSection(section.toTopLevelDestination())
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
        showGlobalSyncStatus = shouldShowGlobalSyncStatus(isDetailForm, isProfile),
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
                        navController.navigateToSection(TopLevelDestination.TRANSACTIONS)
                    },
                    onTransactionClick = { transactionId ->
                        navController.navigate(TransactionDetailRoute(transactionId.value)) {
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
                    onTransactionClick = { transactionId ->
                        navController.navigate(TransactionDetailRoute(transactionId.value)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<TransactionDetailRoute> {
                TransactionDetailScreenRoute(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(TransactionsRoute()) { launchSingleTop = true }
                        }
                    },
                    onEdit = { transactionId ->
                        navController.navigate(TransactionFormRoute(transactionId.value)) {
                            launchSingleTop = true
                        }
                    },
                    onDeleted = {
                        navController.navigate(TransactionsRoute()) {
                            popUpTo<TransactionDetailRoute> { inclusive = true }
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
                        navController.navigate(TransactionDetailRoute(id.value)) {
                            popUpTo<TransactionSuccessRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onClose = {
                        navController.closeTransactionSuccess()
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                val parsedArguments = remember(route.budgetId, route.month) {
                    parseBudgetDetailRouteArguments(route.budgetId, route.month)
                }
                when (parsedArguments) {
                    is BudgetDetailRouteArgumentsResult.Valid -> BudgetDetailScreenRoute(
                        budgetId = parsedArguments.budgetId,
                        month = parsedArguments.month,
                        onBack = {
                            if (!navController.popBackStack()) navController.navigate(BudgetsRoute)
                        },
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
                        onViewAllTransactions = { categoryId, month ->
                            navController.navigate(createBudgetTransactionsRoute(categoryId, month)) {
                                launchSingleTop = true
                            }
                        },
                    )
                    BudgetDetailRouteArgumentsResult.Invalid -> {
                        com.feniqo.mobile.presentation.component.ErrorState(
                            title = "Bütçe açılamadı",
                            description = "Bütçe bağlantısı geçersiz veya eksik.",
                            onRetry = {
                                if (!navController.popBackStack()) navController.navigate(BudgetsRoute)
                            },
                            actionLabel = "Geri dön",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
                    onNavigateToReports = {
                        navController.navigate(ReportsRoute)
                    },
                )
            }
            composable<ReportsRoute> {
                ReportsScreenRoute(
                    onNavigateToCustomDateRange = { navController.navigate(CustomDateRangeRoute) },
                    onNavigateToMultiCurrency = { navController.navigate(MultiCurrencyReportRoute) },
                    onNavigateToPeriodSummary = { navController.navigate(PeriodSummaryReportRoute) },
                    onNavigateToAllReportsHub = { navController.navigate(AllReportsHubRoute) },
                    onNavigateToCategoryBreakdown = { navController.navigate(CategoryBreakdownReportRoute) },
                    onNavigateToCategoryDetail = { catId, catName ->
                        navController.navigate(CategoryDetailReportRoute(catId, catName))
                    },
                    onNavigateToCashFlow = { navController.navigate(CashFlowReportRoute) },
                    onNavigateToPeriodComparison = { navController.navigate(PeriodComparisonReportRoute) },
                    onNavigateToSpendingCalendar = { navController.navigate(SpendingCalendarReportRoute) },
                    onNavigateToBudgetPerformance = { navController.navigate(BudgetPerformanceReportRoute) },
                    onNavigateToSubscriptionSummary = { navController.navigate(SubscriptionSummaryReportRoute) },
                    onNavigateToDebtSummary = { navController.navigate(DebtSummaryReportRoute) },
                    onNavigateToForecast = { navController.navigate(ForecastReportRoute) },
                    onNavigateToFinancialInsights = { navController.navigate(FinancialInsightsReportRoute) },
                    onNavigateToAddTransaction = { navController.navigate(TransactionFormRoute()) },
                    onNavigateHome = {
                        navController.navigateToSection(TopLevelDestination.DASHBOARD)
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<PeriodSummaryReportRoute> {
                PeriodSummaryReportRoute(
                    onNavigateToCategoryDetail = { catId, catName ->
                        navController.navigate(CategoryDetailReportRoute(catId, catName))
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<AllReportsHubRoute> {
                AllReportsHubRoute(
                    onNavigateToCategoryBreakdown = { navController.navigate(CategoryBreakdownReportRoute) },
                    onNavigateToCashFlow = { navController.navigate(CashFlowReportRoute) },
                    onNavigateToPeriodComparison = { navController.navigate(PeriodComparisonReportRoute) },
                    onNavigateToSpendingCalendar = { navController.navigate(SpendingCalendarReportRoute) },
                    onNavigateToBudgetPerformance = { navController.navigate(BudgetPerformanceReportRoute) },
                    onNavigateToSubscriptions = { navController.navigate(SubscriptionSummaryReportRoute) },
                    onNavigateToDebts = { navController.navigate(DebtSummaryReportRoute) },
                    onNavigateToForecast = { navController.navigate(ForecastReportRoute) },
                    onNavigateToInsights = { navController.navigate(FinancialInsightsReportRoute) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<CategoryBreakdownReportRoute> {
                CategoryBreakdownReportRoute(
                    onNavigateToCategoryDetail = { catId, catName ->
                        navController.navigate(CategoryDetailReportRoute(catId, catName))
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<CategoryDetailReportRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CategoryDetailReportRoute>()
                CategoryDetailReportRoute(
                    categoryId = route.categoryId,
                    categoryName = route.categoryName,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<CashFlowReportRoute> {
                CashFlowReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<PeriodComparisonReportRoute> {
                PeriodComparisonReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<SpendingCalendarReportRoute> {
                SpendingCalendarReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<BudgetPerformanceReportRoute> {
                BudgetPerformanceReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<SubscriptionSummaryReportRoute> {
                SubscriptionSummaryReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<DebtSummaryReportRoute> {
                DebtSummaryReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<ForecastReportRoute> {
                ForecastReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<FinancialInsightsReportRoute> {
                FinancialInsightsReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<CustomDateRangeRoute> {
                CustomDateRangeRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<MultiCurrencyReportRoute> {
                MultiCurrencyReportRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<ProfileRoute> {
                val profileViewModel = hiltViewModel<ProfileViewModel>()
                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    displayName = profileState.displayName,
                    email = profileState.email,
                    workspaceName = profileState.workspaceName,
                    isProfileLoading = profileState.isLoading,
                    isProfileEmpty = profileState.isProfileEmpty,
                    profileErrorMessage = profileState.profileErrorMessage,
                    onRetryProfile = profileViewModel::retryProfile,
                    signOutError = profileState.signOutError,
                    syncStatus = syncStatus,
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { navController.navigate(AccountRoute) { launchSingleTop = true } },
                    onOpenPersonalInfo = { navController.navigate(PersonalInfoRoute) { launchSingleTop = true } },
                    onOpenSharedSpaces = { navController.navigate(WorkspacePickerRoute) { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    onOpenDataManagement = { navController.navigate(DataManagementRoute) { launchSingleTop = true } },
                    onSignOut = profileViewModel::signOut,
                )
            }
            composable<AssetsRoute> {
                AssetsScreenRoute(
                    onBack = { navController.popBackStack() },
                    onAddAsset = { navController.navigate(AssetFormRoute()) },
                    onAssetClick = { assetId -> navController.navigate(AssetDetailRoute(assetId.value)) },
                    onDistributionClick = { navController.navigate(AssetDistributionRoute()) },
                )
            }
            composable<AssetDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AssetDetailRoute>()
                val parsedAssetId = remember(route.assetId) { parseAssetDetailRouteId(route.assetId) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    when (parsedAssetId) {
                        is ChildRouteIdResult.ValidId -> AssetDetailScreenRoute(
                            assetId = parsedAssetId.id,
                            onBack = { if (!navController.popBackStack()) navController.navigate(AssetsRoute) },
                            onEdit = { id -> navController.navigate(AssetFormRoute(id.value)) },
                            onAssetDeleted = {
                                if (!navController.popBackStack()) navController.navigate(AssetsRoute)
                            },
                            onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message.resolveLocalizedText()) } },
                            modifier = Modifier.padding(padding),
                        )
                        ChildRouteIdResult.InvalidId -> {
                            com.feniqo.mobile.presentation.component.ErrorState(
                                title = "Varlık açılamadı",
                                description = "Varlık bağlantısı geçersiz veya eksik.",
                                onRetry = {
                                    if (!navController.popBackStack()) navController.navigate(AssetsRoute)
                                },
                                actionLabel = "Geri dön",
                                modifier = Modifier.padding(padding).fillMaxSize(),
                            )
                        }
                    }
                }
            }
            composable<AssetDistributionRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AssetDistributionRoute>()
                AssetDistributionScreenRoute(
                    initialCurrencyCode = route.initialCurrencyCode,
                    onBack = { if (!navController.popBackStack()) navController.navigate(AssetsRoute) },
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
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message.resolveLocalizedText()) } },
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
                    onNavigateToProfile = {
                        if (!navController.popBackStack(ProfileRoute, inclusive = false)) {
                            navController.navigate(ProfileRoute) { launchSingleTop = true }
                        }
                    },
                    onNavigateToAppearance = { navController.navigate(AppearanceRoute) { launchSingleTop = true } },
                    onNavigateToLanguageRegion = { navController.navigate(LanguageRegionRoute) { launchSingleTop = true } },
                    onNavigateToNotifications = { navController.navigate(NotificationsSettingsRoute) { launchSingleTop = true } },
                    onNavigateToSecurity = { navController.navigate(SecurityPrivacyRoute) { launchSingleTop = true } },
                    onNavigateToDataManagement = { navController.navigate(DataManagementRoute) { launchSingleTop = true } },
                    onNavigateToHelpAbout = { navController.navigate(HelpAboutRoute) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AccountRoute> {
                AccountScreenRoute(
                    onNavigateToPersonalInfo = { navController.navigate(PersonalInfoRoute) { launchSingleTop = true } },
                    onNavigateToChangeEmail = { navController.navigate(EmailSettingsRoute) { launchSingleTop = true } },
                    onNavigateToChangePassword = { navController.navigate(ChangePasswordRoute) { launchSingleTop = true } },
                    onNavigateToDeleteAccount = { navController.navigate(DeleteAccountRoute) { launchSingleTop = true } },
                    onNavigateToPhotoPreview = { fileName ->
                        navController.navigate(PhotoPreviewRoute(fileName)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<PhotoPreviewRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<PhotoPreviewRoute>()
                PhotoPreviewScreenRoute(
                    draftFileName = route.draftFileName,
                    onBack = { navController.popBackStack() },
                    onReselect = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() },
                )
            }
            composable<EmailSettingsRoute> {
                EmailSettingsScreenRoute(
                    onNavigateToChangeEmail = { navController.navigate(ChangeEmailRoute) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<PersonalInfoRoute> {
                PersonalInfoScreenRoute(
                    onNavigateToChangeEmail = { navController.navigate(ChangeEmailRoute) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AppearanceRoute> {
                AppearanceScreenRoute(
                    currentTheme = themeMode,
                    onThemeChange = { mode ->
                        scope.launch { onThemeModeChange(mode) }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<LanguageRegionRoute> {
                LanguageRegionScreenRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NotificationsSettingsRoute> {
                NotificationsScreenRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SecurityPrivacyRoute> {
                SecurityPrivacyScreenRoute(
                    appLockEnabled = biometricLockEnabled,
                    biometricAvailable = biometricLockAvailable,
                    currentTimeout = autoLockTimeout,
                    onAppLockChange = onBiometricLockChange,
                    onTimeoutChange = onAutoLockTimeoutChange,
                    onNavigateToChangePassword = { navController.navigate(ChangePasswordRoute) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DataManagementRoute> {
                DataManagementScreenRoute(
                    onNavigateToSyncStatus = { navController.navigate(SyncStatusRoute) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SyncStatusRoute> {
                SyncStatusScreenRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HelpAboutRoute> {
                HelpAboutScreenRoute(
                    onNavigateToFeedback = { isBug ->
                        navController.navigate(FeedbackRoute(isBug = isBug)) { launchSingleTop = true }
                    },
                    onNavigateToHelpCenter = {
                        navController.navigate(HelpCenterRoute) { launchSingleTop = true }
                    },
                    onNavigateToLegalInfo = {
                        navController.navigate(LegalInfoRoute) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HelpCenterRoute> {
                HelpCenterScreenRoute(
                    onNavigateToArticle = { articleTitle ->
                        navController.navigate(HelpArticleStatusRoute(articleTitle)) { launchSingleTop = true }
                    },
                    onNavigateToFeedback = {
                        navController.navigate(FeedbackRoute(isBug = false)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HelpArticleStatusRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<HelpArticleStatusRoute>()
                HelpArticleStatusScreenRoute(
                    articleTitle = route.articleTitle,
                    onNavigateToFeedback = {
                        navController.navigate(FeedbackRoute(isBug = false)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<LegalInfoRoute> {
                LegalInfoScreenRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ChangeEmailRoute> {
                ChangeEmailScreenRoute(
                    onVerificationSent = { newEmail ->
                        navController.navigate(EmailVerificationRoute(newEmail)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<EmailVerificationRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<EmailVerificationRoute>()
                EmailVerificationScreenRoute(
                    newEmail = route.newEmail,
                    onBackToSettings = {
                        navController.popBackStack(SettingsRoute, inclusive = false)
                    },
                )
            }
            composable<ChangePasswordRoute> {
                ChangePasswordScreenRoute(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DeleteAccountRoute> {
                DeleteAccountScreenRoute(
                    onExportData = { navController.navigate(DataManagementRoute) { launchSingleTop = true } },
                    onCheckWorkspaces = { navController.navigate(WorkspacePickerRoute) { launchSingleTop = true } },
                    onInspectSync = { navController.navigate(SyncStatusRoute) { launchSingleTop = true } },
                    onContactSupport = { navController.navigate(FeedbackRoute(isBug = false)) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<FeedbackRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FeedbackRoute>()
                FeedbackScreenRoute(
                    initialIsBug = route.isBug,
                    onBack = { navController.popBackStack() },
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message.resolveLocalizedText()) } },
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                    onNavigateBack = {
                        navController.popBackStack()
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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
                                snackbarHostState.showSnackbar(message.resolveLocalizedText())
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

internal fun shouldHideMainNavigationChrome(destination: NavDestination?): Boolean =
    destination?.let { current ->
        current.hasRoute<TransactionFormRoute>() ||
            current.hasRoute<TransactionDetailRoute>() ||
            current.hasRoute<TransactionSuccessRoute>() ||
            current.hasRoute<CategoryFormRoute>() ||
            current.hasRoute<BudgetFormRoute>() ||
            current.hasRoute<BudgetDetailRoute>() ||
            current.hasRoute<RecurringTransactionFormRoute>() ||
            current.hasRoute<SubscriptionFormRoute>() ||
            current.hasRoute<SubscriptionDetailRoute>() ||
            current.hasRoute<GoalDetailRoute>() ||
            current.hasRoute<GoalFormRoute>() ||
            current.hasRoute<GoalContributionFormRoute>() ||
            current.hasRoute<DebtFormRoute>() ||
            current.hasRoute<DebtPaymentFormRoute>() ||
            current.hasRoute<DebtSnowballPlanRoute>() ||
            current.hasRoute<WorkspacePickerRoute>() ||
            current.hasRoute<WorkspaceCreateRoute>() ||
            current.hasRoute<WorkspaceJoinRoute>() ||
            current.hasRoute<WorkspaceDetailsRoute>() ||
            current.hasRoute<WorkspaceSettlementRoute>() ||
            current.hasRoute<AssetFormRoute>() ||
            current.hasRoute<AssetDetailRoute>() ||
            current.hasRoute<AssetDistributionRoute>() ||
            current.hasRoute<ProfileRoute>() ||
            current.hasRoute<SettingsRoute>() ||
            current.hasRoute<AccountRoute>() ||
            current.hasRoute<PersonalInfoRoute>() ||
            current.hasRoute<AppearanceRoute>() ||
            current.hasRoute<LanguageRegionRoute>() ||
            current.hasRoute<NotificationsSettingsRoute>() ||
            current.hasRoute<SecurityPrivacyRoute>() ||
            current.hasRoute<DataManagementRoute>() ||
            current.hasRoute<SyncStatusRoute>() ||
            current.hasRoute<HelpAboutRoute>() ||
            current.hasRoute<ChangeEmailRoute>() ||
            current.hasRoute<EmailVerificationRoute>() ||
            current.hasRoute<ChangePasswordRoute>() ||
            current.hasRoute<DeleteAccountRoute>() ||
            current.hasRoute<FeedbackRoute>() ||
            current.hasRoute<PhotoPreviewRoute>() ||
            current.hasRoute<EmailSettingsRoute>() ||
            current.hasRoute<HelpCenterRoute>() ||
            current.hasRoute<HelpArticleStatusRoute>() ||
            current.hasRoute<LegalInfoRoute>() ||
            current.hasRoute<ReportsRoute>() ||
            current.hasRoute<CustomDateRangeRoute>() ||
            current.hasRoute<MultiCurrencyReportRoute>() ||
            current.hasRoute<PeriodSummaryReportRoute>() ||
            current.hasRoute<AllReportsHubRoute>() ||
            current.hasRoute<CategoryBreakdownReportRoute>() ||
            current.hasRoute<CategoryDetailReportRoute>() ||
            current.hasRoute<CashFlowReportRoute>() ||
            current.hasRoute<PeriodComparisonReportRoute>() ||
            current.hasRoute<SpendingCalendarReportRoute>() ||
            current.hasRoute<BudgetPerformanceReportRoute>() ||
            current.hasRoute<SubscriptionSummaryReportRoute>() ||
            current.hasRoute<DebtSummaryReportRoute>() ||
            current.hasRoute<ForecastReportRoute>() ||
            current.hasRoute<FinancialInsightsReportRoute>()
    } == true

internal fun shouldShowGlobalSyncStatus(
    isDetailForm: Boolean,
    isProfile: Boolean,
): Boolean = !isDetailForm && !isProfile
