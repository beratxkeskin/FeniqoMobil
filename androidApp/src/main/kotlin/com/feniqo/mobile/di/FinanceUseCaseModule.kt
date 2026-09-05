package com.feniqo.mobile.di

import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.AddCategoryUseCase
import com.feniqo.mobile.domain.usecase.AddTransactionUseCase
import com.feniqo.mobile.domain.usecase.CalculateBudgetProgressUseCase
import com.feniqo.mobile.domain.usecase.CopyBudgetsUseCase
import com.feniqo.mobile.domain.usecase.CreateBudgetUseCase
import com.feniqo.mobile.domain.usecase.DeleteCategoryUseCase
import com.feniqo.mobile.domain.usecase.DeleteBudgetUseCase
import com.feniqo.mobile.domain.usecase.DeleteTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveBudgetAlertsUseCase
import com.feniqo.mobile.domain.usecase.ObserveBudgetsWithProgressUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.UpdateBudgetUseCase
import com.feniqo.mobile.domain.usecase.UpdateCategoryUseCase
import com.feniqo.mobile.domain.usecase.UpdateTransactionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * İşlem ve kategori domain use-case bağımlılıklarını sağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
object FinanceUseCaseModule {

    @Provides
    @Singleton
    fun provideCurrentDateProvider(): com.feniqo.mobile.presentation.common.CurrentDateProvider =
        com.feniqo.mobile.presentation.common.SystemCurrentDateProvider()

    @Provides
    @Singleton
    fun provideCurrentInstantProvider(): com.feniqo.mobile.presentation.common.CurrentInstantProvider =
        com.feniqo.mobile.presentation.common.SystemCurrentInstantProvider()

    @Provides
    @Singleton
    fun provideObserveTransactionsUseCase(
        transactionRepository: TransactionRepository,
    ): ObserveTransactionsUseCase = ObserveTransactionsUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideObserveTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase =
        com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideAddTransactionUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
    ): AddTransactionUseCase = AddTransactionUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
    )

    @Provides
    @Singleton
    fun provideUpdateTransactionUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
    ): UpdateTransactionUseCase = UpdateTransactionUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
    )

    @Provides
    @Singleton
    fun provideEntityIdGenerator(): com.feniqo.mobile.domain.model.EntityIdGenerator =
        com.feniqo.mobile.data.util.RandomHexEntityIdGenerator()

    @Provides
    @Singleton
    fun provideAddInstallmentGroupUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
        entityIdGenerator: com.feniqo.mobile.domain.model.EntityIdGenerator,
    ): com.feniqo.mobile.domain.usecase.AddInstallmentGroupUseCase = com.feniqo.mobile.domain.usecase.AddInstallmentGroupUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
        entityIdGenerator = entityIdGenerator,
    )

    @Provides
    @Singleton
    fun provideDeleteTransactionUseCase(
        authRepository: AuthRepository,
        transactionRepository: TransactionRepository,
    ): DeleteTransactionUseCase = DeleteTransactionUseCase(
        authRepository = authRepository,
        transactionRepository = transactionRepository,
    )

    @Provides
    @Singleton
    fun provideObserveCategoriesUseCase(
        categoryRepository: CategoryRepository,
    ): ObserveCategoriesUseCase = ObserveCategoriesUseCase(categoryRepository)

    @Provides
    @Singleton
    fun provideObserveCategoriesForHistoryLookupUseCase(
        categoryRepository: CategoryRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase =
        com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase(categoryRepository)

    @Provides
    @Singleton
    fun provideObserveCategoryUseCase(
        categoryRepository: CategoryRepository,
    ): ObserveCategoryUseCase = ObserveCategoryUseCase(categoryRepository)

    @Provides
    @Singleton
    fun provideAddCategoryUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
    ): AddCategoryUseCase = AddCategoryUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
    )

    @Provides
    @Singleton
    fun provideUpdateCategoryUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
    ): UpdateCategoryUseCase = UpdateCategoryUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
    )

    @Provides
    @Singleton
    fun provideDeleteCategoryUseCase(
        authRepository: AuthRepository,
        categoryRepository: CategoryRepository,
    ): DeleteCategoryUseCase = DeleteCategoryUseCase(
        authRepository = authRepository,
        categoryRepository = categoryRepository,
    )

    @Provides
    @Singleton
    fun provideCalculateDashboardSummaryUseCase(): com.feniqo.mobile.domain.usecase.CalculateDashboardSummaryUseCase =
        com.feniqo.mobile.domain.usecase.CalculateDashboardSummaryUseCase()

    @Provides
    @Singleton
    fun provideObserveDashboardSummaryUseCase(
        transactionRepository: TransactionRepository,
        calculator: com.feniqo.mobile.domain.usecase.CalculateDashboardSummaryUseCase,
    ): com.feniqo.mobile.domain.usecase.ObserveDashboardSummaryUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDashboardSummaryUseCase(
            transactionRepository = transactionRepository,
            calculator = calculator,
        )

    @Provides
    @Singleton
    fun provideCalculateMoneyScoreUseCase(): com.feniqo.mobile.domain.usecase.CalculateMoneyScoreUseCase =
        com.feniqo.mobile.domain.usecase.CalculateMoneyScoreUseCase()

    @Provides
    @Singleton
    fun provideCalculateBudgetProgressUseCase(): CalculateBudgetProgressUseCase =
        CalculateBudgetProgressUseCase()

    @Provides
    @Singleton
    fun provideCreateBudgetUseCase(
        categoryRepository: CategoryRepository,
        budgetRepository: BudgetRepository,
    ): CreateBudgetUseCase = CreateBudgetUseCase(
        categoryRepository = categoryRepository,
        budgetRepository = budgetRepository,
    )

    @Provides
    @Singleton
    fun provideUpdateBudgetUseCase(
        budgetRepository: BudgetRepository,
    ): UpdateBudgetUseCase = UpdateBudgetUseCase(
        budgetRepository = budgetRepository,
    )

    @Provides
    @Singleton
    fun provideDeleteBudgetUseCase(
        budgetRepository: BudgetRepository,
    ): DeleteBudgetUseCase = DeleteBudgetUseCase(
        budgetRepository = budgetRepository,
    )

    @Provides
    @Singleton
    fun provideCopyBudgetsUseCase(
        budgetRepository: BudgetRepository,
    ): CopyBudgetsUseCase = CopyBudgetsUseCase(
        budgetRepository = budgetRepository,
    )

    @Provides
    @Singleton
    fun provideObserveBudgetsWithProgressUseCase(
        budgetRepository: BudgetRepository,
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    ): ObserveBudgetsWithProgressUseCase = ObserveBudgetsWithProgressUseCase(
        budgetRepository = budgetRepository,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        calculator = calculateBudgetProgressUseCase,
    )

    @Provides
    @Singleton
    fun provideObserveBudgetUseCase(
        budgetRepository: BudgetRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveBudgetUseCase =
        com.feniqo.mobile.domain.usecase.ObserveBudgetUseCase(budgetRepository)

    @Provides
    @Singleton
    fun provideObserveBudgetAlertsUseCase(
        observeBudgetsWithProgressUseCase: ObserveBudgetsWithProgressUseCase,
    ): ObserveBudgetAlertsUseCase = ObserveBudgetAlertsUseCase(
        observeBudgetsWithProgressUseCase = observeBudgetsWithProgressUseCase,
    )

    @Provides
    @Singleton
    fun provideGenerateDueRecurringTransactionsUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.GenerateDueRecurringTransactionsUseCase =
        com.feniqo.mobile.domain.usecase.GenerateDueRecurringTransactionsUseCase(
            recurringTransactionRepository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideObserveRecurringTransactionsUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionsUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideObserveRecurringTransactionUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionUseCase =
        com.feniqo.mobile.domain.usecase.ObserveRecurringTransactionUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideCreateRecurringTransactionUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.CreateRecurringTransactionUseCase =
        com.feniqo.mobile.domain.usecase.CreateRecurringTransactionUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideUpdateRecurringTransactionUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.UpdateRecurringTransactionUseCase =
        com.feniqo.mobile.domain.usecase.UpdateRecurringTransactionUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideSetRecurringTransactionActiveUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.SetRecurringTransactionActiveUseCase =
        com.feniqo.mobile.domain.usecase.SetRecurringTransactionActiveUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideDeleteRecurringTransactionUseCase(
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
    ): com.feniqo.mobile.domain.usecase.DeleteRecurringTransactionUseCase =
        com.feniqo.mobile.domain.usecase.DeleteRecurringTransactionUseCase(
            repository = recurringTransactionRepository,
        )

    @Provides
    @Singleton
    fun provideObserveSubscriptionsUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideObserveSubscriptionUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase =
        com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideCreateSubscriptionUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.CreateSubscriptionUseCase =
        com.feniqo.mobile.domain.usecase.CreateSubscriptionUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideUpdateSubscriptionUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase =
        com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideSetSubscriptionActiveUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.SetSubscriptionActiveUseCase =
        com.feniqo.mobile.domain.usecase.SetSubscriptionActiveUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideAdvanceSubscriptionRenewalUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase =
        com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideDeleteSubscriptionUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase =
        com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase(
            repository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun providePlanSubscriptionPaymentRemindersUseCase(): com.feniqo.mobile.domain.usecase.PlanSubscriptionPaymentRemindersUseCase =
        com.feniqo.mobile.domain.usecase.PlanSubscriptionPaymentRemindersUseCase()

    @Provides
    @Singleton
    fun provideObserveGoalsUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveGoalsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveGoalsUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideObserveGoalUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveGoalUseCase =
        com.feniqo.mobile.domain.usecase.ObserveGoalUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideObserveGoalContributionsUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideObserveDebtsUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveDebtsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDebtsUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideObserveDebtUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveDebtUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDebtUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideObserveDebtPaymentsUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDebtPaymentsUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideCreateGoalUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.CreateGoalUseCase =
        com.feniqo.mobile.domain.usecase.CreateGoalUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideUpdateGoalUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.UpdateGoalUseCase =
        com.feniqo.mobile.domain.usecase.UpdateGoalUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideAddGoalContributionUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.AddGoalContributionUseCase =
        com.feniqo.mobile.domain.usecase.AddGoalContributionUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideDeleteGoalUseCase(
        goalRepository: com.feniqo.mobile.domain.repository.GoalRepository,
    ): com.feniqo.mobile.domain.usecase.DeleteGoalUseCase =
        com.feniqo.mobile.domain.usecase.DeleteGoalUseCase(repository = goalRepository)

    @Provides
    @Singleton
    fun provideCreateDebtUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.CreateDebtUseCase =
        com.feniqo.mobile.domain.usecase.CreateDebtUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideUpdateDebtUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.UpdateDebtUseCase =
        com.feniqo.mobile.domain.usecase.UpdateDebtUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideAddDebtPaymentUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.AddDebtPaymentUseCase =
        com.feniqo.mobile.domain.usecase.AddDebtPaymentUseCase(repository = debtRepository)

    @Provides
    @Singleton
    fun provideDeleteDebtUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.DeleteDebtUseCase =
        com.feniqo.mobile.domain.usecase.DeleteDebtUseCase(repository = debtRepository)
}

