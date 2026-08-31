package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class GenerateDueRecurringTransactionsUseCaseTest {

    private class FakeRecurringTransactionRepository : RecurringTransactionRepository {
        var lastThroughDate: LocalDate? = null
        var lastMaxOccurrencesPerRule: Int? = null
        var lastMaxTotalOccurrences: Int? = null
        var resultToReturn: RepositoryResult<GenerateRecurringTransactionsResult> = RepositoryResult.Success(
            GenerateRecurringTransactionsResult(
                createdCount = 1,
                alreadyGeneratedCount = 0,
                staleRecurringIds = emptyList(),
                skippedRecurringIds = emptyList(),
            )
        )

        override fun observeRecurringTransactions() = error("Test kapsamı dışı")
        override fun observeRecurringTransaction(id: EntityId): kotlinx.coroutines.flow.Flow<RecurringTransaction?> =
            error("Test kapsamı dışı")
        override suspend fun create(command: CreateRecurringTransactionCommand) = error("Test kapsamı dışı")
        override suspend fun update(command: UpdateRecurringTransactionCommand) = error("Test kapsamı dışı")
        override suspend fun setActive(command: SetRecurringTransactionActiveCommand) = error("Test kapsamı dışı")
        override suspend fun softDelete(id: EntityId) = error("Test kapsamı dışı")

        override suspend fun generateDueTransactions(
            throughDate: LocalDate,
            maxOccurrencesPerRule: Int,
            maxTotalOccurrences: Int,
            createdAt: Instant,
        ): RepositoryResult<GenerateRecurringTransactionsResult> {
            lastThroughDate = throughDate
            lastMaxOccurrencesPerRule = maxOccurrencesPerRule
            lastMaxTotalOccurrences = maxTotalOccurrences
            return resultToReturn
        }
    }

    private val fakeRepo = FakeRecurringTransactionRepository()
    private val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
    private val now = Instant.fromEpochMilliseconds(1700000000000L)

    @Test
    fun validParameters_delegatesToRepositorySuccessfully() = runTest {
        val result = useCase(
            throughDate = LocalDate(2026, 8, 15),
            maxOccurrencesPerRule = 10,
            maxTotalOccurrences = 50,
            createdAt = now,
        )

        assertTrue(result is RepositoryResult.Success)
        assertEquals(1, result.value.createdCount)
        assertEquals(LocalDate(2026, 8, 15), fakeRepo.lastThroughDate)
        assertEquals(10, fakeRepo.lastMaxOccurrencesPerRule)
        assertEquals(50, fakeRepo.lastMaxTotalOccurrences)
    }

    @Test
    fun invalidLimits_failClosedWithValidationError() = runTest {
        val badPerRule = useCase(
            throughDate = LocalDate(2026, 8, 15),
            maxOccurrencesPerRule = 0,
            createdAt = now,
        )
        assertTrue(badPerRule is RepositoryResult.Failure)
        assertTrue(badPerRule.error is AppError.Validation)

        val badTotal = useCase(
            throughDate = LocalDate(2026, 8, 15),
            maxTotalOccurrences = -1,
            createdAt = now,
        )
        assertTrue(badTotal is RepositoryResult.Failure)
        assertTrue(badTotal.error is AppError.Validation)
    }
}
