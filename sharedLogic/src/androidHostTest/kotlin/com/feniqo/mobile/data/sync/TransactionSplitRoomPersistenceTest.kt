package com.feniqo.mobile.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.codec.TransactionSplitPersistenceCodec
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.local.outbox.OutboxStatus
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.AssetRemoteQuery
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.AssetDto
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionParticipantShareDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.data.repository.decodeTransactionConflictSnapshot
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionSplitRoomPersistenceTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            ApplicationProvider.getApplicationContext<Context>(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).setQueryCoroutineContext(Dispatchers.Default).allowMainThreadQueries().build()
    }

    private fun sampleCategory(id: String = CATEGORY_ID, ownerId: String = USER_ID) = CategoryEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        scopeKey = ownerId,
        name = "Market",
        normalizedName = "market",
        slug = "market",
        typeCode = "EXPENSE",
        colorHex = "#22C55E",
        iconKey = "shopping-cart",
        isDefault = false,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = 1L,
            lastSyncError = null,
        ),
    )

    private fun sampleCustomTransaction(
        id: String = TX_ID,
        ownerId: String = USER_ID,
        amountMinor: Long = 10_000L,
        paidByUserId: String = USER_ID,
        participantShares: List<TransactionParticipantShare> = listOf(
            TransactionParticipantShare(EntityId(USER_ID), 6_000L),
            TransactionParticipantShare(EntityId(OTHER_USER_ID), 4_000L),
        ),
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    ): Transaction = Transaction(
        id = EntityId(id),
        ownerId = EntityId(ownerId),
        workspaceId = null,
        paidByUserId = EntityId(paidByUserId),
        participantUserIds = participantShares.map { it.userId },
        amount = Money(amountMinor, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId(CATEGORY_ID),
        description = "Özel paylaşımlı harcama",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate.parse("2026-09-18"),
        receiptPath = null,
        installment = null,
        createdAt = Instant.fromEpochMilliseconds(1000L),
        syncStatus = syncStatus,
        splitMode = TransactionSplitMode.CUSTOM,
        participantShares = participantShares,
    )

    @Test
    fun room_persists_custom_split_entity_and_outbox_queue_record() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(sampleCategory())

            val txDomain = sampleCustomTransaction()
            val txEntity = txDomain.toEntity(
                SyncMetadata(
                    syncStatus = SyncStatus.PENDING_CREATE.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 0L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            )

            val queue = OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                nowEpochMillisProvider = { 1000L },
                operationIdFactory = { OP_ID },
            )

            val opId = queue.enqueueTransaction(
                entity = txEntity,
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
            )
            assertEquals(OP_ID, opId)

            // Verify Room entity persistence
            val stored = db.transactionDao().getByIdAndOwner(TX_ID, USER_ID)
            assertNotNull(stored)
            assertEquals("CUSTOM", stored.splitMode)
            val expectedSharesJson = TransactionSplitPersistenceCodec.encode(txDomain.participantShares)
            assertEquals(expectedSharesJson, stored.participantSharesJson)

            // Verify mapping back to domain
            val restored = stored.toDomain()
            assertEquals(TransactionSplitMode.CUSTOM, restored.splitMode)
            assertEquals(2, restored.participantShares.size)
            assertEquals(EntityId(USER_ID), restored.participantShares[0].userId)
            assertEquals(6_000L, restored.participantShares[0].amountMinor)
            assertEquals(EntityId(OTHER_USER_ID), restored.participantShares[1].userId)
            assertEquals(4_000L, restored.participantShares[1].amountMinor)

            // Verify outbox operation in Room
            val outboxOp = db.syncOperationDao().getById(opId)
            assertNotNull(outboxOp)
            assertEquals("TRANSACTION", outboxOp.entityTypeCode)
            assertEquals(TX_ID, outboxOp.entityId)
            assertEquals("CREATE", outboxOp.operationTypeCode)
            assertEquals("PENDING", outboxOp.statusCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun ackV2Execution_applies_remote_custom_split_to_room_and_cleans_outbox() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(sampleCategory())

            val txDomain = sampleCustomTransaction()
            val txEntity = txDomain.toEntity(
                SyncMetadata(
                    syncStatus = SyncStatus.PENDING_CREATE.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 0L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            )

            // Insert initial local mutation and outbox row
            db.localMutationDao().upsertTransactionAndEnqueue(
                entity = txEntity,
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = SyncOperationEntity(
                    operationId = OP_ID,
                    entityTypeCode = "TRANSACTION",
                    entityId = TX_ID,
                    operationTypeCode = OutboxOperationType.CREATE.name,
                    baseVersion = null,
                    statusCode = OutboxStatus.IN_FLIGHT.name,
                    attemptCount = 1,
                    lastError = null,
                    nextAttemptAtEpochMillis = 1000L,
                    createdAtEpochMillis = 1000L,
                    updatedAtEpochMillis = 1000L,
                    protocolVersion = 2,
                ),
            )

            // Remote returns applied custom split transaction
            val remoteDto = TransactionDto(
                id = TX_ID,
                userId = USER_ID,
                paidByUserId = USER_ID,
                participantUserIds = listOf(USER_ID, OTHER_USER_ID),
                amountMinor = 10_000L,
                currency = "TRY",
                type = "EXPENSE",
                categoryId = CATEGORY_ID,
                paymentMethod = "CREDIT_CARD",
                transactionDate = "2026-09-18",
                createdAt = "2026-09-18T10:00:00Z",
                updatedAt = "2026-09-18T10:00:01Z",
                version = 2L,
                splitMode = "CUSTOM",
                participantShares = listOf(
                    TransactionParticipantShareDto(USER_ID, 7_000L),
                    TransactionParticipantShareDto(OTHER_USER_ID, 3_000L),
                ),
            )

            val ackResult = db.localMutationDao().ackV2Execution(
                operationId = OP_ID,
                result = OutboxExecutionResult.TransactionApplied(remoteDto),
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            // Outbox row should be deleted
            assertNull(db.syncOperationDao().getById(OP_ID))

            // Room entity must be updated to SYNCED with remote custom shares
            val updatedEntity = db.transactionDao().getByIdAndOwner(TX_ID, USER_ID)
            assertNotNull(updatedEntity)
            assertEquals("SYNCED", updatedEntity.sync.syncStatus)
            assertEquals(2L, updatedEntity.sync.version)
            assertEquals("CUSTOM", updatedEntity.splitMode)

            val restored = updatedEntity.toDomain()
            assertEquals(TransactionSplitMode.CUSTOM, restored.splitMode)
            assertEquals(7_000L, restored.participantShares.first { it.userId == EntityId(USER_ID) }.amountMinor)
            assertEquals(3_000L, restored.participantShares.first { it.userId == EntityId(OTHER_USER_ID) }.amountMinor)
        } finally {
            db.close()
        }
    }

    @Test
    fun incremental_pull_persists_remote_custom_split_and_advances_cursor() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(sampleCategory())

            val remoteDto = TransactionDto(
                id = TX_ID,
                userId = USER_ID,
                paidByUserId = USER_ID,
                participantUserIds = listOf(USER_ID, OTHER_USER_ID),
                amountMinor = 10_000L,
                currency = "TRY",
                type = "EXPENSE",
                categoryId = CATEGORY_ID,
                paymentMethod = "CREDIT_CARD",
                transactionDate = "2026-09-18",
                createdAt = "2026-09-18T10:00:00Z",
                updatedAt = "2026-09-18T10:00:02Z",
                version = 1L,
                splitMode = "CUSTOM",
                participantShares = listOf(
                    TransactionParticipantShareDto(USER_ID, 6_000L),
                    TransactionParticipantShareDto(OTHER_USER_ID, 4_000L),
                ),
            )

            val remote = SingleTransactionRemoteDataSource(remoteDto)
            val sync = IncrementalRemoteSync(
                remote = remote,
                remoteSyncDao = db.remoteSyncDao(),
                syncStateDao = db.syncStateDao(),
                nowEpochMillisProvider = { 3000L },
            )

            val result = sync.pullFor(EntityId(USER_ID))
            assertEquals(2, result.appliedCount, "Profil + Transaction uygulandı")
            assertEquals(1, result.receivedTransactionCount)
            assertEquals(0, result.conflictCount)

            // Room persistence check
            val stored = db.transactionDao().getByIdAndOwner(TX_ID, USER_ID)
            assertNotNull(stored)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals("CUSTOM", stored.splitMode)

            val domain = stored.toDomain()
            assertEquals(TransactionSplitMode.CUSTOM, domain.splitMode)
            assertEquals(2, domain.participantShares.size)
            assertEquals(6_000L, domain.participantShares.first { it.userId == EntityId(USER_ID) }.amountMinor)

            // Cursor check
            val cursor = db.syncStateDao().getCursor("TRANSACTION")
            assertNotNull(cursor)
            assertEquals(TX_ID, cursor.entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun incremental_pull_with_custom_split_conflict_records_conflict_snapshot_in_room() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(sampleCategory())

            // Local transaction is PENDING_UPDATE (version 1, baseVersion 1)
            val localDomain = sampleCustomTransaction(
                participantShares = listOf(
                    TransactionParticipantShare(EntityId(USER_ID), 6_000L),
                    TransactionParticipantShare(EntityId(OTHER_USER_ID), 4_000L),
                ),
            )
            val localEntity = localDomain.toEntity(
                SyncMetadata(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    updatedAtEpochMillis = 2000L,
                    localUpdatedAtEpochMillis = 2000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = 1L,
                    lastSyncError = null,
                ),
            )

            // Enqueue outbox operation
            db.localMutationDao().upsertTransactionAndEnqueue(
                entity = localEntity,
                tags = emptyList(),
                tagLinks = emptyList(),
                operation = SyncOperationEntity(
                    operationId = OP_ID,
                    entityTypeCode = "TRANSACTION",
                    entityId = TX_ID,
                    operationTypeCode = OutboxOperationType.UPDATE.name,
                    baseVersion = 1L,
                    statusCode = OutboxStatus.PENDING.name,
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = 2000L,
                    createdAtEpochMillis = 2000L,
                    updatedAtEpochMillis = 2000L,
                    protocolVersion = 2,
                ),
            )

            // Remote returns version 2 with EQUAL split mode
            val remoteDto = TransactionDto(
                id = TX_ID,
                userId = USER_ID,
                paidByUserId = USER_ID,
                participantUserIds = listOf(USER_ID, OTHER_USER_ID),
                amountMinor = 10_000L,
                currency = "TRY",
                type = "EXPENSE",
                categoryId = CATEGORY_ID,
                paymentMethod = "CREDIT_CARD",
                transactionDate = "2026-09-18",
                createdAt = "2026-09-18T10:00:00Z",
                updatedAt = "2026-09-18T10:00:05Z",
                version = 2L,
                splitMode = "EQUAL",
                participantShares = emptyList(),
            )

            val remote = SingleTransactionRemoteDataSource(remoteDto)
            val sync = IncrementalRemoteSync(
                remote = remote,
                remoteSyncDao = db.remoteSyncDao(),
                syncStateDao = db.syncStateDao(),
                nowEpochMillisProvider = { 3000L },
            )

            val result = sync.pullFor(EntityId(USER_ID))
            assertEquals(1, result.appliedCount, "Profil uygulandı; transaction çakışmada kaldı")
            assertEquals(1, result.receivedTransactionCount)
            assertEquals(1, result.conflictCount)

            // Local entity in Room is in CONFLICT status
            val stored = db.transactionDao().getByIdAndOwner(TX_ID, USER_ID)
            assertNotNull(stored)
            assertEquals("CONFLICT", stored.sync.syncStatus)

            // Conflict snapshot in Room
            val conflict = db.syncStateDao().getConflict("TRANSACTION", TX_ID)
            assertNotNull(conflict)
            assertEquals(1L, conflict.localVersion)
            assertEquals(2L, conflict.remoteVersion)

            // Decode snapshots to domain models to verify custom split preservation
            val decodedLocal = decodeTransactionConflictSnapshot("TRANSACTION", conflict.localPayloadJson)
            assertNotNull(decodedLocal)
            assertEquals(TransactionSplitMode.CUSTOM, decodedLocal.splitMode)
            assertEquals(2, decodedLocal.participantShares.size)
            assertEquals(6_000L, decodedLocal.participantShares.first { it.userId == EntityId(USER_ID) }.amountMinor)

            val decodedRemote = decodeTransactionConflictSnapshot("TRANSACTION", conflict.remotePayloadJson)
            assertNotNull(decodedRemote)
            assertEquals(TransactionSplitMode.EQUAL, decodedRemote.splitMode)
            assertTrue(decodedRemote.participantShares.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun incremental_pull_with_corrupted_custom_split_fails_closed_and_does_not_advance_cursor() = runTest {
        val db = inMemoryDatabase()
        try {
            db.categoryDao().upsert(sampleCategory())

            // Remote returns invalid CUSTOM split: participant set mismatch
            val corruptDto = TransactionDto(
                id = TX_ID,
                userId = USER_ID,
                paidByUserId = USER_ID,
                participantUserIds = listOf(USER_ID, OTHER_USER_ID),
                amountMinor = 10_000L,
                currency = "TRY",
                type = "EXPENSE",
                categoryId = CATEGORY_ID,
                paymentMethod = "CREDIT_CARD",
                transactionDate = "2026-09-18",
                createdAt = "2026-09-18T10:00:00Z",
                updatedAt = "2026-09-18T10:00:02Z",
                version = 1L,
                splitMode = "CUSTOM",
                participantShares = listOf(
                    // Missing OTHER_USER_ID share!
                    TransactionParticipantShareDto(USER_ID, 10_000L),
                ),
            )

            val remote = SingleTransactionRemoteDataSource(corruptDto)
            val sync = IncrementalRemoteSync(
                remote = remote,
                remoteSyncDao = db.remoteSyncDao(),
                syncStateDao = db.syncStateDao(),
                nowEpochMillisProvider = { 3000L },
            )

            // Should throw RemoteMappingException
            assertFailsWith<RemoteMappingException> {
                sync.pullFor(EntityId(USER_ID))
            }

            // Verify cursor was NOT advanced
            val cursor = db.syncStateDao().getCursor("TRANSACTION")
            assertNull(cursor, "Bozuk kayıt nedeniyle imleç (cursor) ilerletilmemelidir.")

            // Verify corrupt transaction was NOT written to Room
            val stored = db.transactionDao().getByIdAndOwner(TX_ID, USER_ID)
            assertNull(stored, "Bozuk kayıt Room veritabanına yazılmamalıdır.")
        } finally {
            db.close()
        }
    }

    private class SingleTransactionRemoteDataSource(
        private val transaction: TransactionDto,
    ) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String) = ProfileDto(
            id = userId,
            email = "test@feniqo.app",
            createdAt = "2026-09-18T00:00:00Z",
            updatedAt = "2026-09-18T00:00:00Z",
            version = 1L,
        )

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(listOf(transaction), query.page, 1)

        override suspend fun fetchCategories(query: CategoryRemoteQuery) = RemotePage<CategoryDto>(emptyList(), query.page, 0)
        override suspend fun fetchAssets(query: AssetRemoteQuery) = RemotePage<AssetDto>(emptyList(), query.page, 0)
        override suspend fun fetchBudgets(query: BudgetRemoteQuery) = RemotePage<BudgetDto>(emptyList(), query.page, 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery) = RemotePage<RecurringTransactionDto>(emptyList(), query.page, 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery) = RemotePage<SubscriptionDto>(emptyList(), query.page, 0)
        override suspend fun fetchGoals(query: GoalRemoteQuery) = RemotePage<GoalDto>(emptyList(), query.page, 0)
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery) = RemotePage<GoalContributionDto>(emptyList(), query.page, 0)
        override suspend fun fetchDebts(query: DebtRemoteQuery) = RemotePage<DebtDto>(emptyList(), query.page, 0)
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery) = RemotePage<DebtPaymentDto>(emptyList(), query.page, 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest) = RemotePage<TagDto>(emptyList(), page, 0)
        override suspend fun fetchTransactionTags(transactionId: String) = emptyList<TransactionTagDto>()
        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_USER_ID = "22222222-2222-2222-2222-222222222222"
        const val TX_ID = "33333333-3333-3333-3333-333333333333"
        const val CATEGORY_ID = "44444444-4444-4444-4444-444444444444"
        const val OP_ID = "55555555555555555555555555555555"
    }
}
