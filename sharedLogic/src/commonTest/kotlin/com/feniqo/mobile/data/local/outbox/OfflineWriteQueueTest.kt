package com.feniqo.mobile.data.local.outbox

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TransactionMutationUnit
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfflineWriteQueueTest {

    @Test
    fun enqueueTransactionCreates_with3Inputs_inserts3OutboxOperationsAndCallsSchedulerOnce() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeOpDao = FakeSyncOperationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()

        var opCounter = 0
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = fakeOpDao,
            syncScheduler = fakeScheduler,
            nowEpochMillisProvider = { 10_000L },
            operationIdFactory = { "op-${++opCounter}" },
        )

        val inputs = listOf(
            TransactionCreateInput(createTransactionEntity("t1")),
            TransactionCreateInput(createTransactionEntity("t2")),
            TransactionCreateInput(createTransactionEntity("t3")),
        )

        val opIds = queue.enqueueTransactionCreates(inputs)

        assertEquals(listOf("op-1", "op-2", "op-3"), opIds)
        assertEquals(1, fakeMutationDao.batchCalls.size)
        assertEquals(3, fakeMutationDao.batchCalls[0].size)
        assertEquals(listOf("t1", "t2", "t3"), fakeMutationDao.batchCalls[0].map { it.entity.id })
        assertEquals(1, fakeScheduler.scheduleCount) // Yalnız 1 kez çağrıldı
    }

    @Test
    fun enqueueTransactionCreates_withDuplicateEntityId_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val inputs = listOf(
            TransactionCreateInput(createTransactionEntity("t1")),
            TransactionCreateInput(createTransactionEntity("t1")), // Duplicate ID
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withEmptyList_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(emptyList())
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withPendingUpdateEntity_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val invalidSync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_UPDATE.name)
        val inputs = listOf(TransactionCreateInput(createTransactionEntity("t1", sync = invalidSync)))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withVersionNotZero_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val invalidSync = newSyncMetadata(1000L).copy(version = 1L)
        val inputs = listOf(TransactionCreateInput(createTransactionEntity("t1", sync = invalidSync)))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withBaseVersionNotNull_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val invalidSync = newSyncMetadata(1000L).copy(baseVersion = 1L)
        val inputs = listOf(TransactionCreateInput(createTransactionEntity("t1", sync = invalidSync)))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withDeletedAtNotNull_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val invalidSync = newSyncMetadata(1000L).copy(deletedAtEpochMillis = 5000L)
        val inputs = listOf(TransactionCreateInput(createTransactionEntity("t1", sync = invalidSync)))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withMismatchedTagLinkTransactionId_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val inputs = listOf(
            TransactionCreateInput(
                entity = createTransactionEntity("t1"),
                tagLinks = listOf(TransactionTagCrossRef("t-wrong-id", "tag-1", 1000L, newSyncMetadata(1000L))),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_withDuplicateOperationId_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
            operationIdFactory = { "op-constant" }, // duplicate op ID across inputs
        )

        val inputs = listOf(
            TransactionCreateInput(createTransactionEntity("t1")),
            TransactionCreateInput(createTransactionEntity("t2")),
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeMutationDao.batchCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionCreates_whenDaoFails_doesNotCallScheduler() = runTest {
        val failingMutationDao = object : FakeLocalMutationDao() {
            override suspend fun upsertTransactionsAndEnqueue(units: List<TransactionMutationUnit>) {
                throw IllegalStateException("db_failure")
            }
        }
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = failingMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val inputs = listOf(TransactionCreateInput(createTransactionEntity("t1")))

        assertFailsWith<IllegalStateException> {
            queue.enqueueTransactionCreates(inputs)
        }

        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_with3Entities_inserts3DeleteOperationsWithCorrectBaseVersionAndCallsSchedulerOnce() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeOpDao = FakeSyncOperationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()

        var opCounter = 0
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = fakeOpDao,
            syncScheduler = fakeScheduler,
            nowEpochMillisProvider = { 10_000L },
            operationIdFactory = { "op-del-${++opCounter}" },
        )

        val sync1 = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 2L, baseVersion = 2L, deletedAtEpochMillis = 10_000L)
        val sync2 = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 5L, baseVersion = 5L, deletedAtEpochMillis = 10_000L)
        val sync3 = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 0L, baseVersion = null, deletedAtEpochMillis = 10_000L)

        val entities = listOf(
            createTransactionEntity("t1", sync1),
            createTransactionEntity("t2", sync2),
            createTransactionEntity("t3", sync3),
        )

        val opIds = queue.enqueueTransactionDeletions(entities)

        assertEquals(listOf("op-del-1", "op-del-2", "op-del-3"), opIds)
        assertEquals(1, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(3, fakeMutationDao.batchDeleteCalls[0].size)

        val units = fakeMutationDao.batchDeleteCalls[0]
        assertEquals("t1", units[0].entity.id)
        assertEquals(2L, units[0].operation.baseVersion)
        assertEquals("DELETE", units[0].operation.operationTypeCode)

        assertEquals("t2", units[1].entity.id)
        assertEquals(5L, units[1].operation.baseVersion)
        assertEquals("DELETE", units[1].operation.operationTypeCode)

        assertEquals("t3", units[2].entity.id)
        assertEquals(null, units[2].operation.baseVersion)
        assertEquals("DELETE", units[2].operation.operationTypeCode)

        assertEquals(1, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withEmptyList_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(emptyList())
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withDuplicateEntityId_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = 1L, deletedAtEpochMillis = 1000L)
        val entities = listOf(
            createTransactionEntity("t1", sync),
            createTransactionEntity("t1", sync),
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(entities)
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withPendingUpdateEntity_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_UPDATE.name, version = 1L, baseVersion = 1L, deletedAtEpochMillis = 1000L)
        val entities = listOf(createTransactionEntity("t1", sync))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(entities)
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withDeletedAtNull_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = 1L, deletedAtEpochMillis = null)
        val entities = listOf(createTransactionEntity("t1", sync))

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(entities)
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withNegativeVersionOrBaseVersion_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val negVersionSync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = -1L, baseVersion = 1L, deletedAtEpochMillis = 1000L)
        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(listOf(createTransactionEntity("t1", negVersionSync)))
        }

        val negBaseVersionSync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = -1L, deletedAtEpochMillis = 1000L)
        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(listOf(createTransactionEntity("t2", negBaseVersionSync)))
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withBaseVersionNullAndVersionGreaterThanZero_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = null, deletedAtEpochMillis = 1000L)
        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(listOf(createTransactionEntity("t1", sync)))
        }

        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_withBlankOrDuplicateOperationId_throwsWithoutCallingDaoOrScheduler() = runTest {
        val fakeMutationDao = FakeLocalMutationDao()
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = fakeMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
            operationIdFactory = { "op-fixed" },
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = 1L, deletedAtEpochMillis = 1000L)
        val entities = listOf(
            createTransactionEntity("t1", sync),
            createTransactionEntity("t2", sync),
        )

        assertFailsWith<IllegalArgumentException> {
            queue.enqueueTransactionDeletions(entities)
        }
        assertEquals(0, fakeMutationDao.batchDeleteCalls.size)
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    @Test
    fun enqueueTransactionDeletions_whenDaoFails_doesNotCallScheduler() = runTest {
        val failingMutationDao = object : FakeLocalMutationDao() {
            override suspend fun upsertTransactionsKeepingTagsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit>) {
                throw IllegalStateException("db_failure")
            }
        }
        val fakeScheduler = FakeBackgroundSyncScheduler()
        val queue = OfflineWriteQueue(
            mutationDao = failingMutationDao,
            operationDao = FakeSyncOperationDao(),
            syncScheduler = fakeScheduler,
        )

        val sync = newSyncMetadata(1000L).copy(syncStatus = SyncStatus.PENDING_DELETE.name, version = 1L, baseVersion = 1L, deletedAtEpochMillis = 1000L)
        assertFailsWith<IllegalStateException> {
            queue.enqueueTransactionDeletions(listOf(createTransactionEntity("tx-failing-2", sync)))
        }
        assertEquals(0, fakeScheduler.scheduleCount)
    }

    private fun createTransactionEntity(
        id: String,
        sync: SyncMetadata = newSyncMetadata(1000L),
    ) = TransactionEntity(
        id = id,
        ownerId = "u1",
        workspaceId = null,
        amountMinor = 1000L,
        currencyCode = "TRY",
        typeCode = "EXPENSE",
        categoryId = "cat-1",
        description = "Test",
        searchText = "test",
        paymentMethodCode = "CASH",
        transactionDate = "2026-08-21",
        receiptPath = null,
        installmentGroupId = null,
        installmentNumber = null,
        totalInstallments = null,
        createdAtEpochMillis = 1000L,
        sync = sync,
    )
}

private open class FakeLocalMutationDao : LocalMutationDao {
    val batchCalls = mutableListOf<List<TransactionMutationUnit>>()
    val batchDeleteCalls = mutableListOf<List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit>>()

    override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
    override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
    override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
    override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
    override suspend fun upsertBudgetRow(entity: BudgetEntity) {}
    override suspend fun upsertTransactionRow(entity: TransactionEntity) {}
    override suspend fun insertTransactionRow(entity: TransactionEntity) {}
    override suspend fun upsertTagRows(entities: List<TagEntity>) {}
    override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) {}
    override suspend fun upsertRecurringTransactionRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionEntity) {}
    override suspend fun upsertSubscriptionRow(entity: com.feniqo.mobile.data.local.entity.SubscriptionEntity) {}
    override suspend fun upsertRecurringOccurrenceRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity) {}
    override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity? = null
    override suspend fun getRecurringTransactionById(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
    override suspend fun advanceRecurringLastGeneratedDate(recurringTransactionId: String, expectedPreviousLastGeneratedDate: String?, newDueDate: String, nowEpochMillis: Long): Int = 0
    override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
    override suspend fun deleteProfileRow(id: String): Int = 0
    override suspend fun deleteCategoryRow(id: String): Int = 0
    override suspend fun deleteBudgetRow(id: String): Int = 0
    override suspend fun deleteTransactionRow(id: String): Int = 0
    override suspend fun deleteOutboxRow(operationId: String): Int = 0
    override suspend fun getOutboxById(operationId: String): SyncOperationEntity? = null
    override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
    override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
    override suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
    override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun insertOutboxRow(operation: SyncOperationEntity) {}
    override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
    override suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseSubscriptionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun deleteRecurringTransactionRow(id: String): Int = 0
    override suspend fun deleteSubscriptionRow(id: String): Int = 0
    override suspend fun markCategorySyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun markTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun markBudgetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun markRecurringTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun markSubscriptionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun deleteWorkspaceRow(id: String): Int = 0
    override suspend fun deleteWorkspaceMemberRows(workspaceId: String): Int = 0
    override suspend fun rebaseWorkspaceVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun markWorkspaceSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun upsertGoalRow(entity: com.feniqo.mobile.data.local.entity.GoalEntity) = Unit
    override suspend fun upsertGoalContributionRow(entity: com.feniqo.mobile.data.local.entity.GoalContributionEntity) = Unit
    override suspend fun upsertDebtRow(entity: com.feniqo.mobile.data.local.entity.DebtEntity) = Unit
    override suspend fun upsertDebtPaymentRow(entity: com.feniqo.mobile.data.local.entity.DebtPaymentEntity) = Unit
    override suspend fun deleteGoalRow(id: String): Int = 0
    override suspend fun deleteGoalContributionRow(id: String): Int = 0
    override suspend fun deleteDebtRow(id: String): Int = 0
    override suspend fun deleteDebtPaymentRow(id: String): Int = 0
    override suspend fun getGoalById(id: String): com.feniqo.mobile.data.local.entity.GoalEntity? = null
    override suspend fun getDebtById(id: String): com.feniqo.mobile.data.local.entity.DebtEntity? = null
    override suspend fun getGoalContributionById(id: String): com.feniqo.mobile.data.local.entity.GoalContributionEntity? = null
    override suspend fun getDebtPaymentById(id: String): com.feniqo.mobile.data.local.entity.DebtPaymentEntity? = null
    override suspend fun getActiveGoalContributions(goalId: String): List<com.feniqo.mobile.data.local.entity.GoalContributionEntity> = emptyList()
    override suspend fun getActiveDebtPayments(debtId: String): List<com.feniqo.mobile.data.local.entity.DebtPaymentEntity> = emptyList()
    override suspend fun rebaseGoalVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseGoalContributionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseDebtVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun rebaseDebtPaymentVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
    override suspend fun markGoalSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun markDebtSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
    override suspend fun tombstoneGoalContributionsForDeletedGoal(goalId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
    override suspend fun tombstoneDebtPaymentsForDeletedDebt(debtId: String, deletedAtEpochMillis: Long, nowEpochMillis: Long): Int = 0
    override suspend fun getActiveGoalAggregateTailCandidates(goalId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun getActiveDebtAggregateTailCandidates(debtId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun countPendingGoalAggregateOperations(goalId: String, operationId: String): Int = 0
    override suspend fun countPendingDebtAggregateOperations(debtId: String, operationId: String): Int = 0
    override suspend fun setGoalSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setGoalContributionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setDebtSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setDebtPaymentSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun upsertConflictRow(entity: com.feniqo.mobile.data.local.entity.SyncConflictEntity) {}
    override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
    override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
    override suspend fun upsertTransactionKeepingTagsAndEnqueue(
        entity: TransactionEntity,
        operation: SyncOperationEntity,
    ) {}
    override suspend fun upsertTransactionsAndEnqueue(units: List<TransactionMutationUnit>) {
        batchCalls.add(units)
    }
    override suspend fun upsertTransactionsKeepingTagsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit>) {
        batchDeleteCalls.add(units)
    }
}

private class FakeSyncOperationDao : SyncOperationDao {
    override fun observePendingCount(): Flow<Int> = flowOf(0)
    override fun observeFailedCount(): Flow<Int> = flowOf(0)
    override suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int) = emptyList<SyncOperationEntity>()
    override suspend fun getById(operationId: String): SyncOperationEntity? = null
    override suspend fun insert(operation: SyncOperationEntity) {}
    override suspend fun claimOperation(operationId: String, nowEpochMillis: Long): Int = 1
    override suspend fun markFailed(operationId: String, lastError: String, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
    override suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
    override suspend fun recoverStaleInFlight(staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
    override suspend fun retryAllFailed(nowEpochMillis: Long): Int = 0
    override suspend fun deleteCompleted(operationId: String): Int = 1
    override suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int = 0
    override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 1
    override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
    override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 1
    override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 1
}

private class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
    var scheduleCount = 0
    override fun scheduleOutboxSync() {
        scheduleCount++
    }
    override fun scheduleInitialSync() {}
    override fun cancelSyncWork() {}
}
