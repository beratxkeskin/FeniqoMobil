package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTransactionCrudUseCasesTest {

    @Test
    fun crudUseCases_delegateOriginalInputsAndResults() = runTest {
        val repository = RecordingRepository()
        val create = CreateRecurringTransactionUseCase(repository)
        val update = UpdateRecurringTransactionUseCase(repository)
        val setActive = SetRecurringTransactionActiveUseCase(repository)
        val delete = DeleteRecurringTransactionUseCase(repository)
        val createCommand = createCommand()
        val updateCommand = updateCommand()
        val activeCommand = SetRecurringTransactionActiveCommand(EntityId("rec-1"), false)

        assertTrue(create(createCommand) is RepositoryResult.Success)
        assertTrue(update(updateCommand) is RepositoryResult.Success)
        assertTrue(setActive(activeCommand) is RepositoryResult.Success)
        assertTrue(delete(EntityId("rec-1")) is RepositoryResult.Success)

        assertEquals(createCommand, repository.createCommand)
        assertEquals(updateCommand, repository.updateCommand)
        assertEquals(activeCommand, repository.activeCommand)
        assertEquals(EntityId("rec-1"), repository.deletedId)
    }

    @Test
    fun observeRecurringTransactions_emitsRepositoryListDirectly() = runTest {
        val repository = RecordingRepository()
        val observeList = ObserveRecurringTransactionsUseCase(repository)
        val expected = listOf(sampleRecurringTransaction("rec-1"), sampleRecurringTransaction("rec-2"))
        repository.listToEmit = expected

        val actual = observeList().first()
        assertEquals(expected, actual)
    }

    @Test
    fun observeRecurringTransaction_delegatesIdAndEmitsNullableResult() = runTest {
        val repository = RecordingRepository()
        val observeSingle = ObserveRecurringTransactionUseCase(repository)
        val expected = sampleRecurringTransaction("rec-1")
        repository.singleToEmit = expected

        val actual = observeSingle(EntityId("rec-1")).first()
        assertEquals(expected, actual)
        assertEquals(EntityId("rec-1"), repository.lastObservedId)

        repository.singleToEmit = null
        val nullResult = observeSingle(EntityId("rec-nonexistent")).first()
        assertNull(nullResult)
        assertEquals(EntityId("rec-nonexistent"), repository.lastObservedId)
    }

    @Test
    fun observe_rethrowsCancellationDirectly() = runTest {
        val repository = RecordingRepository().apply { throwCancellation = true }
        val observeList = ObserveRecurringTransactionsUseCase(repository)
        val observeSingle = ObserveRecurringTransactionUseCase(repository)

        assertFailsWith<CancellationException> {
            observeList().first()
        }

        assertFailsWith<CancellationException> {
            observeSingle(EntityId("rec-1")).first()
        }
    }

    private class RecordingRepository : RecurringTransactionRepository {
        var createCommand: CreateRecurringTransactionCommand? = null
        var updateCommand: UpdateRecurringTransactionCommand? = null
        var activeCommand: SetRecurringTransactionActiveCommand? = null
        var deletedId: EntityId? = null
        var listToEmit: List<RecurringTransaction> = emptyList()
        var singleToEmit: RecurringTransaction? = null
        var lastObservedId: EntityId? = null
        var throwCancellation: Boolean = false

        override fun observeRecurringTransactions(): Flow<List<RecurringTransaction>> = flow {
            if (throwCancellation) throw CancellationException("List flow cancelled")
            emit(listToEmit)
        }

        override fun observeRecurringTransaction(id: EntityId): Flow<RecurringTransaction?> = flow {
            lastObservedId = id
            if (throwCancellation) throw CancellationException("Single flow cancelled")
            emit(singleToEmit)
        }

        override suspend fun create(command: CreateRecurringTransactionCommand): RepositoryResult<EntityId> {
            createCommand = command
            return RepositoryResult.Success(EntityId("rec-1"))
        }
        override suspend fun update(command: UpdateRecurringTransactionCommand): RepositoryResult<Unit> {
            updateCommand = command
            return RepositoryResult.Success(Unit)
        }
        override suspend fun setActive(command: SetRecurringTransactionActiveCommand): RepositoryResult<Unit> {
            activeCommand = command
            return RepositoryResult.Success(Unit)
        }
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            deletedId = id
            return RepositoryResult.Success(Unit)
        }
        override suspend fun generateDueTransactions(
            throughDate: com.feniqo.mobile.domain.model.LocalDate,
            maxOccurrencesPerRule: Int,
            maxTotalOccurrences: Int,
            createdAt: Instant,
        ): RepositoryResult<GenerateRecurringTransactionsResult> = error("Test kapsamı dışı")
    }

    private fun createCommand(): CreateRecurringTransactionCommand =
        com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand(
            amount = com.feniqo.mobile.domain.model.Money(50000L, com.feniqo.mobile.domain.model.Currency.TRY),
            type = com.feniqo.mobile.domain.model.TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = com.feniqo.mobile.domain.model.PaymentMethod.CASH,
            rule = com.feniqo.mobile.domain.model.RecurrenceRule(
                frequency = com.feniqo.mobile.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = com.feniqo.mobile.domain.model.LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

    private fun updateCommand(): UpdateRecurringTransactionCommand =
        UpdateRecurringTransactionCommand(
            id = EntityId("rec-1"),
            amount = com.feniqo.mobile.domain.model.Money(50000L, com.feniqo.mobile.domain.model.Currency.TRY),
            type = com.feniqo.mobile.domain.model.TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = null,
            paymentMethod = com.feniqo.mobile.domain.model.PaymentMethod.CASH,
            rule = com.feniqo.mobile.domain.model.RecurrenceRule(
                frequency = com.feniqo.mobile.domain.model.RecurrenceFrequency.MONTHLY,
                startDate = com.feniqo.mobile.domain.model.LocalDate(2026, 8, 1),
                endDate = null,
            ),
        )

    private fun sampleRecurringTransaction(id: String): RecurringTransaction =
        RecurringTransaction(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = com.feniqo.mobile.domain.model.Money(50000L, com.feniqo.mobile.domain.model.Currency.TRY),
            type = com.feniqo.mobile.domain.model.TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Subscription",
            paymentMethod = com.feniqo.mobile.domain.model.PaymentMethod.CREDIT_CARD,
            rule = com.feniqo.mobile.domain.model.RecurrenceRule(
                frequency = com.feniqo.mobile.domain.model.RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = com.feniqo.mobile.domain.model.LocalDate(2026, 8, 1),
                endDate = null,
            ),
            lastGeneratedDate = null,
            isActive = true,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
}

