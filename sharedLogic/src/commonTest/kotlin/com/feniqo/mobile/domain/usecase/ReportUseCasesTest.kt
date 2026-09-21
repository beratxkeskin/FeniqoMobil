package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategorySpendingSummary
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportDateRange
import com.feniqo.mobile.domain.model.ReportFilter
import com.feniqo.mobile.domain.model.ReportPeriodPreset
import com.feniqo.mobile.domain.model.ReportTypeFilter
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportUseCasesTest {

    private val referenceDate = LocalDate(2026, 9, 17)

    @Test
    fun calculateReportDateRange_thisMonth_returnsFirstAndLastDayOfCurrentMonth() {
        val calculator = CalculateReportDateRangeUseCase()
        val range = calculator(ReportPeriodPreset.THIS_MONTH, null, referenceDate)
        assertEquals(LocalDate(2026, 9, 1), range.startDate)
        assertEquals(LocalDate(2026, 9, 30), range.endDate)
    }

    @Test
    fun calculateReportDateRange_lastMonth_returnsFirstAndLastDayOfPreviousMonth() {
        val calculator = CalculateReportDateRangeUseCase()
        val range = calculator(ReportPeriodPreset.LAST_MONTH, null, referenceDate)
        assertEquals(LocalDate(2026, 8, 1), range.startDate)
        assertEquals(LocalDate(2026, 8, 31), range.endDate)
    }

    @Test
    fun calculateReportDateRange_last3Months_returnsThreeMonthSpan() {
        val calculator = CalculateReportDateRangeUseCase()
        val range = calculator(ReportPeriodPreset.LAST_3_MONTHS, null, referenceDate)
        assertEquals(LocalDate(2026, 7, 1), range.startDate)
        assertEquals(LocalDate(2026, 9, 30), range.endDate)
    }

    @Test
    fun calculateReportDateRange_custom_returnsCustomRange() {
        val calculator = CalculateReportDateRangeUseCase()
        val custom = ReportDateRange(LocalDate(2026, 7, 1), LocalDate(2026, 9, 30))
        val range = calculator(ReportPeriodPreset.CUSTOM, custom, referenceDate)
        assertEquals(custom, range)
    }

    @Test
    fun reportDateRange_startAfterEnd_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            ReportDateRange(LocalDate(2026, 9, 30), LocalDate(2026, 9, 1))
        }
    }

    @Test
    fun observeFilteredReport_calculatesIncomeExpenseNetByCategoryAndExcludesOtherCurrencies() = runTest {
        val tx1 = createTx("1", 4250000L, Currency.TRY, TransactionType.INCOME, "cat-salary", LocalDate(2026, 9, 5))
        val tx2 = createTx("2", 2905000L, Currency.TRY, TransactionType.EXPENSE, "cat-market", LocalDate(2026, 9, 10))
        val txGbp = createTx("3", 50000L, Currency.GBP, TransactionType.EXPENSE, "cat-market", LocalDate(2026, 9, 12))

        val repo = FakeTxRepo(listOf(tx1, tx2, txGbp))
        val useCase = ObserveFilteredReportUseCase(repo)

        val report = useCase(
            filter = ReportFilter(periodPreset = ReportPeriodPreset.THIS_MONTH, currency = Currency.TRY),
            workspaceId = null,
            referenceDate = referenceDate,
        ).first()

        assertEquals(4250000L, report.income.amountMinor)
        assertEquals(2905000L, report.expense.amountMinor)
        assertEquals(1345000L, report.net.amountMinor)
        assertEquals(2, report.transactionCount)
        assertEquals(1, report.spendingByCategory.size)
        assertEquals(EntityId("cat-market"), report.spendingByCategory[0].categoryId)
        assertEquals(2905000L, report.spendingByCategory[0].amount.amountMinor)
    }

    @Test
    fun observeMultiCurrencyReport_groupsByCurrencyIndependentlyWithoutConversion() = runTest {
        val txTry = createTx("1", 4250000L, Currency.TRY, TransactionType.INCOME, "c1", LocalDate(2026, 9, 5))
        val txTryExpense = createTx("2", 2860000L, Currency.TRY, TransactionType.EXPENSE, "c1", LocalDate(2026, 9, 6))
        val txUsdIncome = createTx("3", 120000L, Currency.USD, TransactionType.INCOME, "c2", LocalDate(2026, 9, 7))
        val txUsdExpense = createTx("4", 76000L, Currency.USD, TransactionType.EXPENSE, "c2", LocalDate(2026, 9, 8))
        val txEurIncome = createTx("5", 82000L, Currency.EUR, TransactionType.INCOME, "c3", LocalDate(2026, 9, 9))
        val txEurExpense = createTx("6", 52000L, Currency.EUR, TransactionType.EXPENSE, "c3", LocalDate(2026, 9, 10))

        val repo = FakeTxRepo(listOf(txTry, txTryExpense, txUsdIncome, txUsdExpense, txEurIncome, txEurExpense))
        val useCase = ObserveMultiCurrencyReportUseCase(repo)

        val summaries = useCase(
            filter = ReportFilter(periodPreset = ReportPeriodPreset.THIS_MONTH),
            workspaceId = null,
            referenceDate = referenceDate,
        ).first()

        assertEquals(3, summaries.size)

        val trySum = summaries.first { it.currency == Currency.TRY }
        assertEquals(4250000L, trySum.income.amountMinor)
        assertEquals(2860000L, trySum.expense.amountMinor)
        assertEquals(1390000L, trySum.net.amountMinor)

        val usdSum = summaries.first { it.currency == Currency.USD }
        assertEquals(120000L, usdSum.income.amountMinor)
        assertEquals(76000L, usdSum.expense.amountMinor)
        assertEquals(44000L, usdSum.net.amountMinor)

        val eurSum = summaries.first { it.currency == Currency.EUR }
        assertEquals(82000L, eurSum.income.amountMinor)
        assertEquals(52000L, eurSum.expense.amountMinor)
        assertEquals(30000L, eurSum.net.amountMinor)
    }

    @Test
    fun observeReportAvailability_distinguishesWorkspaceEmptyFromFilteredEmpty() = runTest {
        val tx = createTx("1", 10000L, Currency.USD, TransactionType.EXPENSE, "c1", LocalDate(2026, 9, 5))
        val repo = FakeTxRepo(listOf(tx))
        val useCase = ObserveReportAvailabilityUseCase(repo)

        // Workspace has 1 transaction (USD), but query asks for TRY -> total=1, filtered=0 (EmptyFiltered)
        val availability = useCase(
            filter = ReportFilter(periodPreset = ReportPeriodPreset.THIS_MONTH, currency = Currency.TRY),
            workspaceId = null,
            referenceDate = referenceDate,
        ).first()

        assertFalse(availability.isWorkspaceEmpty)
        assertTrue(availability.isFilteredEmpty)
        assertFalse(availability.hasData)

        // Empty repo -> total=0, filtered=0 (EmptyWorkspace)
        val emptyRepo = FakeTxRepo(emptyList())
        val emptyUseCase = ObserveReportAvailabilityUseCase(emptyRepo)
        val emptyAvailability = emptyUseCase(
            filter = ReportFilter(periodPreset = ReportPeriodPreset.THIS_MONTH, currency = Currency.TRY),
            workspaceId = null,
            referenceDate = referenceDate,
        ).first()

        assertTrue(emptyAvailability.isWorkspaceEmpty)
        assertFalse(emptyAvailability.isFilteredEmpty)
        assertFalse(emptyAvailability.hasData)
    }

    @Test
    fun observeReportSyncStatus_mapsSyncOverviewCorrectly() = runTest {
        val fakeSync = object : SyncRepository {
            override fun observeOverview(): Flow<SyncOverview> = flowOf(
                SyncOverview(
                    phase = SyncPhase.OFFLINE,
                    pendingOperationCount = 3,
                    failedOperationCount = 0,
                    conflictCount = 0,
                    lastSuccessfulSyncAt = null,
                    lastError = null,
                )
            )
            override fun observeConflicts(): Flow<List<com.feniqo.mobile.domain.repository.SyncConflict>> = flowOf(emptyList())
            override suspend fun requestSync(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun retryFailedOperations(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun resolveConflict(
                entityId: EntityId,
                resolution: com.feniqo.mobile.domain.repository.ConflictResolution,
            ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

        val useCase = ObserveReportSyncStatusUseCase(fakeSync)
        val status = useCase().first()

        assertTrue(status.isOffline)
        assertEquals(3, status.pendingOperationCount)
    }

    private fun createTx(
        id: String,
        amountMinor: Long,
        currency: Currency,
        type: TransactionType,
        categoryId: String,
        date: LocalDate,
    ): Transaction = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = type,
        categoryId = EntityId(categoryId),
        description = "Test transaction $id",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = null,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    private class FakeTxRepo(private val transactions: List<Transaction>) : TransactionRepository {
        override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
            var res = transactions
            if (filter.period != null) {
                res = res.filter { it.transactionDate >= filter.period!!.startDate && it.transactionDate <= filter.period!!.endDate }
            }
            if (filter.type != null) {
                res = res.filter { it.type == filter.type }
            }
            if (filter.categoryId != null) {
                res = res.filter { it.categoryId == filter.categoryId }
            }
            return flowOf(res)
        }
        override fun observeTransaction(id: EntityId): Flow<Transaction?> = flowOf(transactions.find { it.id == id })
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> = RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("g1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
