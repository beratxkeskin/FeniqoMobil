package com.feniqo.mobile.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `when requestSync succeeds then worker returns success`() = runTest {
        val fakeRepo = FakeSyncRepository(resultToReturn = RepositoryResult.Success(Unit))
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with network error then worker returns retry`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Network("sync.network_failed")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with authentication error then worker returns success to prevent retry storm`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Authentication("sync.session_required")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with conflict error then worker returns success to avoid redundant loop`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Conflict("sync.user_resolution_required")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with storage error then worker returns failure`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Storage("sync.storage_failed")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with validation error then worker returns failure`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Validation("sync.invalid_data")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    @Test
    fun `when requestSync fails with unknown error then worker returns failure`() = runTest {
        val fakeRepo = FakeSyncRepository(
            resultToReturn = RepositoryResult.Failure(AppError.Unknown("sync.unknown")),
        )
        val worker = buildWorker(fakeRepo)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, fakeRepo.requestSyncCallCount)
    }

    private fun buildWorker(syncRepository: SyncRepository): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncWorker(appContext, workerParameters, syncRepository)
            })
            .build()
}

private class FakeSyncRepository(
    var resultToReturn: RepositoryResult<Unit>,
) : SyncRepository {
    var requestSyncCallCount = 0

    override fun observeOverview(): Flow<SyncOverview> = flowOf(
        SyncOverview(
            phase = SyncPhase.IDLE,
            pendingOperationCount = 0,
            conflictCount = 0,
            lastSuccessfulSyncAt = null,
            lastError = null,
        ),
    )

    override fun observeConflicts(): Flow<List<SyncConflict>> = flowOf(emptyList())

    override suspend fun requestSync(): RepositoryResult<Unit> {
        requestSyncCallCount++
        return resultToReturn
    }

    override suspend fun retryFailedOperations(): RepositoryResult<Unit> = requestSync()

    override suspend fun resolveConflict(
        entityId: EntityId,
        resolution: ConflictResolution,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}
