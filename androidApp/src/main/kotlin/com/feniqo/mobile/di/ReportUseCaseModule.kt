package com.feniqo.mobile.di

import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.CalculateReportDateRangeUseCase
import com.feniqo.mobile.domain.usecase.ObserveFilteredReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveMultiCurrencyReportUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportAvailabilityUseCase
import com.feniqo.mobile.domain.usecase.ObserveReportSyncStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportUseCaseModule {

    @Provides
    @Singleton
    fun provideCalculateReportDateRangeUseCase(): CalculateReportDateRangeUseCase =
        CalculateReportDateRangeUseCase()

    @Provides
    @Singleton
    fun provideObserveFilteredReportUseCase(
        transactionRepository: TransactionRepository,
        dateRangeCalculator: CalculateReportDateRangeUseCase,
    ): ObserveFilteredReportUseCase =
        ObserveFilteredReportUseCase(transactionRepository, dateRangeCalculator)

    @Provides
    @Singleton
    fun provideObserveMultiCurrencyReportUseCase(
        transactionRepository: TransactionRepository,
        dateRangeCalculator: CalculateReportDateRangeUseCase,
    ): ObserveMultiCurrencyReportUseCase =
        ObserveMultiCurrencyReportUseCase(transactionRepository, dateRangeCalculator)

    @Provides
    @Singleton
    fun provideObserveReportAvailabilityUseCase(
        transactionRepository: TransactionRepository,
        dateRangeCalculator: CalculateReportDateRangeUseCase,
    ): ObserveReportAvailabilityUseCase =
        ObserveReportAvailabilityUseCase(transactionRepository, dateRangeCalculator)

    @Provides
    @Singleton
    fun provideObserveReportSyncStatusUseCase(
        syncRepository: SyncRepository,
    ): ObserveReportSyncStatusUseCase =
        ObserveReportSyncStatusUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideObserveDetailedReportOverviewUseCase(
        transactionRepository: TransactionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
        dateRangeCalculator: CalculateReportDateRangeUseCase,
    ): com.feniqo.mobile.domain.usecase.ObserveDetailedReportOverviewUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDetailedReportOverviewUseCase(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            dateRangeCalculator = dateRangeCalculator,
        )

    @Provides
    @Singleton
    fun provideObserveCashFlowUseCase(
        transactionRepository: TransactionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveCashFlowUseCase =
        com.feniqo.mobile.domain.usecase.ObserveCashFlowUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideObserveCategoryBreakdownUseCase(
        transactionRepository: TransactionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
        dateRangeCalculator: CalculateReportDateRangeUseCase,
    ): com.feniqo.mobile.domain.usecase.ObserveCategoryBreakdownUseCase =
        com.feniqo.mobile.domain.usecase.ObserveCategoryBreakdownUseCase(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            dateRangeCalculator = dateRangeCalculator,
        )

    @Provides
    @Singleton
    fun provideObserveSpendingCalendarUseCase(
        transactionRepository: TransactionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveSpendingCalendarUseCase =
        com.feniqo.mobile.domain.usecase.ObserveSpendingCalendarUseCase(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
        )

    @Provides
    @Singleton
    fun provideObserveBudgetPerformanceReportUseCase(
        budgetRepository: com.feniqo.mobile.domain.repository.BudgetRepository,
        transactionRepository: TransactionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveBudgetPerformanceReportUseCase =
        com.feniqo.mobile.domain.usecase.ObserveBudgetPerformanceReportUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
        )

    @Provides
    @Singleton
    fun provideObserveSubscriptionSummaryReportUseCase(
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveSubscriptionSummaryReportUseCase =
        com.feniqo.mobile.domain.usecase.ObserveSubscriptionSummaryReportUseCase(
            subscriptionRepository = subscriptionRepository,
            categoryRepository = categoryRepository,
        )

    @Provides
    @Singleton
    fun provideObserveDebtSummaryReportUseCase(
        debtRepository: com.feniqo.mobile.domain.repository.DebtRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveDebtSummaryReportUseCase =
        com.feniqo.mobile.domain.usecase.ObserveDebtSummaryReportUseCase(debtRepository)

    @Provides
    @Singleton
    fun provideObserveForecastReportUseCase(
        transactionRepository: TransactionRepository,
        recurringTransactionRepository: com.feniqo.mobile.domain.repository.RecurringTransactionRepository,
        subscriptionRepository: com.feniqo.mobile.domain.repository.SubscriptionRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveForecastReportUseCase =
        com.feniqo.mobile.domain.usecase.ObserveForecastReportUseCase(
            transactionRepository = transactionRepository,
            recurringTransactionRepository = recurringTransactionRepository,
            subscriptionRepository = subscriptionRepository,
        )

    @Provides
    @Singleton
    fun provideObserveFinancialInsightsUseCase(
        transactionRepository: TransactionRepository,
        categoryRepository: com.feniqo.mobile.domain.repository.CategoryRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveFinancialInsightsUseCase =
        com.feniqo.mobile.domain.usecase.ObserveFinancialInsightsUseCase(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
        )
}
