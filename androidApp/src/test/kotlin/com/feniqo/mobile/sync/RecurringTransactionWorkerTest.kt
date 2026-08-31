package com.feniqo.mobile.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
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
import com.feniqo.mobile.domain.usecase.GenerateDueRecurringTransactionsUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurringTransactionWorkerTest {

    private lateinit var context: Context

    private val fixedToday = LocalDate(2026, 8, 30)
    private val fixedInstant = Instant.fromEpochMilliseconds(1700000000000L)

    private class FakeRecurringTimeProvider(
        var localToday: LocalDate,
        var currentInstant: Instant,
    ) : RecurringTransactionTimeProvider {
        override fun currentLocalDate(): LocalDate = localToday
        override fun currentInstant(): Instant = currentInstant
    }

    private class FakeRecurringTransactionRepository(
        var resultToReturn: RepositoryResult<GenerateRecurringTransactionsResult>,
    ) : RecurringTransactionRepository {
        var lastThroughDate: LocalDate? = null
        var lastCreatedAt: Instant? = null
        var callCount = 0

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
            callCount++
            lastThroughDate = throughDate
            lastCreatedAt = createdAt
            return resultToReturn
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun buildWorker(
        useCase: GenerateDueRecurringTransactionsUseCase,
        timeProvider: RecurringTransactionTimeProvider,
    ): RecurringTransactionWorker {
        return TestListenableWorkerBuilder<RecurringTransactionWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker {
                    return RecurringTransactionWorker(
                        appContext = appContext,
                        params = workerParameters,
                        generateDueRecurringTransactionsUseCase = useCase,
                        timeProvider = timeProvider,
                    )
                }
            })
            .build()
    }

    @Test
    fun `when generateDueTransactions succeeds then worker returns success and passes local today and instant`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Success(
                GenerateRecurringTransactionsResult(
                    createdCount = 2,
                    alreadyGeneratedCount = 1,
                    staleRecurringIds = listOf(EntityId("rec-stale")),
                    skippedRecurringIds = listOf(EntityId("rec-skipped")),
                )
            )
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeRepo.callCount)
        assertEquals(fixedToday, fakeRepo.lastThroughDate)
        assertEquals(fixedInstant, fakeRepo.lastCreatedAt)
    }

    @Test
    fun `when generateDueTransactions fails with network error then worker returns retry`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Network("network_error")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when generateDueTransactions fails with auth error then worker returns success to avoid retry storm`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Authentication("auth_session_required")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when generateDueTransactions fails with conflict error then worker returns failure`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Conflict("conflict_error")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when generateDueTransactions fails with validation error then worker returns failure`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Validation("validation_error")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when generateDueTransactions fails with storage error then worker returns failure`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Storage("storage_error")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when generateDueTransactions fails with unknown error then worker returns failure`() = runTest {
        val fakeRepo = FakeRecurringTransactionRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Unknown("unknown_error")),
        )
        val useCase = GenerateDueRecurringTransactionsUseCase(fakeRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.callCount)
    }

    @Test
    fun `when coroutine is cancelled then cancellation exception is rethrown`() = runTest {
        val throwingRepo = object : RecurringTransactionRepository {
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
                throw CancellationException("job_cancelled")
            }
        }
        val useCase = GenerateDueRecurringTransactionsUseCase(throwingRepo)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)
        val worker = buildWorker(useCase, timeProvider)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                worker.doWork()
            }
        }
    }
}
