package com.feniqo.mobile.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.UserMerchantVerificationRemoteQuery
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
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationIncrementalSyncTest {

    private lateinit var db: FeniqoDatabase
    private lateinit var remoteSyncDao: RemoteSyncDao
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var syncStateDao: SyncStateDao
    private lateinit var fakeRemote: TestCoreRemoteDataSource

    private val userAId = EntityId(USER_A_ID)

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue
        const val ENTITY_TYPE = "USER_MERCHANT_VERIFICATION"

        fun sampleDto(
            id: String? = null,
            userId: String = USER_A_ID,
            alias: String = "MIGROS",
            decisionCode: String = "ASSIGN",
            merchantId: String? = "migros",
            createdAt: String = "2026-10-15T10:00:00Z",
            updatedAt: String? = "2026-10-15T10:00:00Z",
            deletedAt: String? = null,
            version: Long? = 1L,
            scopeCode: String = "PERSONAL",
            workspaceId: String? = null,
        ): UserMerchantVerificationDto {
            val resolvedId = id ?: UuidHelper.deterministicMerchantVerificationUuid(userId, alias)
            return UserMerchantVerificationDto(
                id = resolvedId,
                userId = userId,
                workspaceId = workspaceId,
                normalizedAlias = alias,
                decisionCode = decisionCode,
                merchantId = merchantId,
                scopeCode = scopeCode,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt,
                version = version,
            )
        }

        fun sampleLocalEntity(
            id: String? = null,
            userId: String = USER_A_ID,
            alias: String = "MIGROS",
            decisionCode: String = "ASSIGN",
            merchantId: String? = "migros",
            syncStatus: String = "SYNCED",
            version: Long = 1L,
            baseVersion: Long? = 1L,
            createdAtEpochMillis: Long = 1000L,
            updatedAtEpochMillis: Long = 1000L,
            deletedAtEpochMillis: Long? = null,
            lastSyncError: String? = null,
        ): UserMerchantVerificationEntity {
            val resolvedId = id ?: UuidHelper.deterministicMerchantVerificationUuid(userId, alias)
            return UserMerchantVerificationEntity(
                id = resolvedId,
                ownerId = userId,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = decisionCode,
                merchantId = merchantId,
                scopeCode = "PERSONAL",
                scopeKey = "user:$userId",
                createdAtEpochMillis = createdAtEpochMillis,
                sync = SyncMetadata(
                    syncStatus = syncStatus,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                    localUpdatedAtEpochMillis = updatedAtEpochMillis,
                    deletedAtEpochMillis = deletedAtEpochMillis,
                    version = version,
                    baseVersion = baseVersion,
                    lastSyncError = lastSyncError,
                ),
            )
        }

        fun sampleOperation(
            operationId: String,
            syncScopeKey: String = SCOPE_A,
            entityId: String,
            entity: UserMerchantVerificationEntity,
            operationTypeCode: String = "UPDATE",
            statusCode: String = "PENDING",
            baseVersion: Long? = 1L,
            isBlocked: Boolean = false,
            predecessorOperationId: String? = null,
        ): SyncOperationEntity {
            val payload = when (operationTypeCode) {
                "CREATE" -> UserMerchantVerificationPayloadCodec.encodeCreate(entity)
                "UPDATE" -> UserMerchantVerificationPayloadCodec.encodeUpdate(entity)
                "DELETE" -> UserMerchantVerificationPayloadCodec.encodeDelete(entity)
                else -> error("Geçersiz op: $operationTypeCode")
            }
            return SyncOperationEntity(
                operationId = operationId,
                syncScopeKey = syncScopeKey,
                entityTypeCode = ENTITY_TYPE,
                entityId = entityId,
                operationTypeCode = operationTypeCode,
                payloadJson = payload,
                baseVersion = baseVersion,
                statusCode = statusCode,
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 0L,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                isBlocked = isBlocked,
                predecessorOperationId = predecessorOperationId,
                protocolVersion = 2,
                errorClassification = null,
            )
        }
    }

    private class TestCoreRemoteDataSource : CoreRemoteDataSource {
        var merchantPages: List<List<UserMerchantVerificationDto>> = emptyList()
        var lastMerchantQuery: UserMerchantVerificationRemoteQuery? = null

        override suspend fun fetchProfile(userId: String): ProfileDto? = null
        override suspend fun fetchCategories(query: com.feniqo.mobile.data.remote.core.CategoryRemoteQuery): RemotePage<CategoryDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchTransactions(query: com.feniqo.mobile.data.remote.core.TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchBudgets(query: com.feniqo.mobile.data.remote.core.BudgetRemoteQuery): RemotePage<BudgetDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchRecurringTransactions(query: com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchSubscriptions(query: com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery): RemotePage<SubscriptionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchGoals(query: com.feniqo.mobile.data.remote.core.GoalRemoteQuery): RemotePage<GoalDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchGoalContributions(query: com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery): RemotePage<GoalContributionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebts(query: com.feniqo.mobile.data.remote.core.DebtRemoteQuery): RemotePage<DebtDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebtPayments(query: com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchTags(scope: com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> =
            RemotePage(emptyList(), page, totalCount = 0)
        override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> =
            RemotePage(emptyList(), page, totalCount = 0)
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> =
            RemotePage(emptyList(), page, totalCount = 0)
        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()

        override suspend fun upsertProfile(dto: ProfileDto) = Unit
        override suspend fun upsertCategory(dto: CategoryDto) = Unit
        override suspend fun upsertTransaction(dto: TransactionDto) = Unit
        override suspend fun upsertBudget(dto: BudgetDto) = Unit
        override suspend fun upsertTag(dto: TagDto) = Unit
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit

        var onFetchMerchantVerifications: (suspend () -> Unit)? = null

        override suspend fun fetchMerchantVerifications(query: UserMerchantVerificationRemoteQuery): RemotePage<UserMerchantVerificationDto> {
            onFetchMerchantVerifications?.invoke()
            lastMerchantQuery = query
            val pageIndex = query.page.pageIndex
            val items = if (pageIndex < merchantPages.size) merchantPages[pageIndex] else emptyList()
            val totalCount = if (merchantPages.size > 1) {
                merchantPages.size.toLong() * query.page.pageSize
            } else {
                items.size.toLong()
            }
            return RemotePage(items, query.page, totalCount = totalCount)
        }
    }

    private data class TableSnapshot(
        val entity: UserMerchantVerificationEntity?,
        val outbox: SyncOperationEntity?,
        val conflict: SyncConflictEntity?,
        val cursor: SyncCursorEntity?,
    )

    private suspend fun captureSnapshot(entityId: String, operationId: String? = null): TableSnapshot = TableSnapshot(
        entity = remoteSyncDao.getUserMerchantVerificationRow(entityId),
        outbox = operationId?.let { syncOperationDao.getById(SCOPE_A, it) },
        conflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, entityId),
        cursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE),
    )

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()

        remoteSyncDao = db.remoteSyncDao()
        syncOperationDao = db.syncOperationDao()
        syncStateDao = db.syncStateDao()
        fakeRemote = TestCoreRemoteDataSource()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun createSync(): IncrementalRemoteSync = IncrementalRemoteSync(
        remote = fakeRemote,
        remoteSyncDao = remoteSyncDao,
        syncStateDao = syncStateDao,
        nowEpochMillisProvider = { 1700000000000L },
    )

    // 1. Cursor yokken birden fazla sayfadan kayıtlar çekilir ve sırayla uygulanır.
    @Test
    fun scenario01_multiplePages_pulledAndAppliedInOrderWithoutCursor() = runTest {
        val dto1 = sampleDto(alias = "MIGROS", updatedAt = "2026-10-15T10:00:00Z")
        val dto2 = sampleDto(alias = "BIM", updatedAt = "2026-10-15T10:05:00Z", merchantId = "bim")
        fakeRemote.merchantPages = listOf(listOf(dto1), listOf(dto2))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(2, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(2, result.receivedMerchantVerificationCount)

        val stored1 = remoteSyncDao.getUserMerchantVerificationRow(dto1.id)
        val stored2 = remoteSyncDao.getUserMerchantVerificationRow(dto2.id)
        assertNotNull(stored1)
        assertNotNull(stored2)
        assertEquals("MIGROS", stored1.normalizedAlias)
        assertEquals("BIM", stored2.normalizedAlias)

        val cursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(cursor)
        assertEquals(dto2.id, cursor.entityId)
    }

    // 2. Mevcut merchant cursor doğru updatedAfter değeriyle remote query’ye gönderilir.
    @Test
    fun scenario02_existingCursor_sentInRemoteQuery() = runTest {
        val cursorTimestamp = 1760522400000L // 2026-10-15T10:00:00Z
        val initialCursor = SyncCursorEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = ENTITY_TYPE,
            updatedAtEpochMillis = cursorTimestamp,
            entityId = "some-entity-id",
        )
        syncStateDao.upsertCursor(initialCursor)

        createSync().pullFor(userAId, SCOPE_A)

        assertNotNull(fakeRemote.lastMerchantQuery)
        val remoteCursor = fakeRemote.lastMerchantQuery!!.updatedAfter
        assertNotNull(remoteCursor)
        assertEquals("some-entity-id", remoteCursor.entityId)
        assertEquals(RemoteSyncCursor(kotlin.time.Instant.fromEpochMilliseconds(cursorTimestamp).toString(), "some-entity-id"), remoteCursor)
    }

    // 3. Temiz DB’ye aktif ASSIGN kaydı uygulanır ve cursor ilerler.
    @Test
    fun scenario03_cleanDb_assignRecordAppliedAndCursorAdvances() = runTest {
        val dto = sampleDto(alias = "A101", decisionCode = "ASSIGN", merchantId = "a101", version = 1L)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedMerchantVerificationCount)

        val entity = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(entity)
        assertEquals("ASSIGN", entity.decisionCode)
        assertEquals("a101", entity.merchantId)
        assertEquals("SYNCED", entity.sync.syncStatus)
        assertEquals(1L, entity.sync.version)

        val cursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(cursor)
        assertEquals(dto.id, cursor.entityId)
    }

    // 4. Temiz DB’ye BLOCK kaydı uygulanır.
    @Test
    fun scenario04_cleanDb_blockRecordApplied() = runTest {
        val dto = sampleDto(alias = "UNKNOWN VENDOR", decisionCode = "BLOCK", merchantId = null, version = 1L)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(1, result.appliedCount)
        val entity = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(entity)
        assertEquals("BLOCK", entity.decisionCode)
        assertNull(entity.merchantId)
        assertEquals("SYNCED", entity.sync.syncStatus)
    }

    // 5. Remote tombstone soft-delete olarak uygulanır; hard-delete yapılmaz.
    @Test
    fun scenario05_remoteTombstone_softDeleteAppliedNotHardDeleted() = runTest {
        val dto = sampleDto(
            alias = "MIGROS",
            deletedAt = "2026-10-15T11:00:00Z",
            updatedAt = "2026-10-15T11:00:00Z",
            version = 2L,
        )
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(1, result.appliedCount)
        val entity = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(entity)
        assertNotNull(entity.sync.deletedAtEpochMillis)
        assertEquals(kotlin.time.Instant.parse("2026-10-15T11:00:00Z").toEpochMilliseconds(), entity.sync.deletedAtEpochMillis)
    }

    // 6. SYNCED yerel kayda daha yeni remote version uygulanır.
    @Test
    fun scenario06_syncedLocalEntity_appliesNewerRemoteVersion() = runTest {
        val local = sampleLocalEntity(decisionCode = "ASSIGN", merchantId = "migros", version = 1L, baseVersion = 1L)
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val remoteDto = sampleDto(decisionCode = "BLOCK", merchantId = null, version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(1, result.appliedCount)
        val entity = remoteSyncDao.getUserMerchantVerificationRow(remoteDto.id)
        assertNotNull(entity)
        assertEquals("BLOCK", entity.decisionCode)
        assertEquals(2L, entity.sync.version)
        assertEquals("SYNCED", entity.sync.syncStatus)
    }

    // 7. Aynı version/eşdeğer replay idempotent çalışır.
    @Test
    fun scenario07_sameVersionEquivalentReplay_idempotent() = runTest {
        val initialDto = sampleDto(version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(initialDto))
        createSync().pullFor(userAId, SCOPE_A)

        // Replay same DTO
        val result = createSync().pullFor(userAId, SCOPE_A)
        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
    }

    // 8. Aynı version/farklı içerik fail-closed olur ve cursor ilerlemez.
    @Test
    fun scenario08_sameVersionDifferentContent_failsClosedAndCursorDoesNotAdvance() = runTest {
        val initialDto = sampleDto(decisionCode = "ASSIGN", merchantId = "migros", version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(initialDto))
        createSync().pullFor(userAId, SCOPE_A)

        val beforeSnapshot = captureSnapshot(initialDto.id)

        // Same version 1L but different decision BLOCK
        val conflictingDto = sampleDto(decisionCode = "BLOCK", merchantId = null, version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(conflictingDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(initialDto.id)
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 9. Pending UPDATE + daha yeni remote version conflict oluşturur; local karar ve outbox korunur.
    @Test
    fun scenario09_pendingUpdate_newerRemoteVersion_createsConflictPreservingLocalAndOutbox() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-1", entityId = local.id, entity = local, operationTypeCode = "UPDATE", baseVersion = 1L)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(decisionCode = "ASSIGN", merchantId = "migros", version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)

        val entity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(entity)
        assertEquals("BLOCK", entity.decisionCode) // Yerel karar korundu
        assertEquals("CONFLICT", entity.sync.syncStatus)

        val liveOp = syncOperationDao.getById(SCOPE_A, "op-1")
        assertNotNull(liveOp) // Outbox korundu

        val conflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(conflict)
        assertEquals(2L, conflict.localVersion)
        assertEquals(2L, conflict.remoteVersion)

        val cursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(cursor)
        assertEquals(local.id, cursor.entityId)
    }

    // 10. Pending CREATE + sunucudaki aynı deterministik kayıt conflict oluşturur.
    @Test
    fun scenario10_pendingCreate_serverDuplicate_createsConflict() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "ASSIGN",
            merchantId = "migros",
            syncStatus = "PENDING_CREATE",
            version = 0L,
            baseVersion = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-create", entityId = local.id, entity = local, operationTypeCode = "CREATE", baseVersion = null)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(decisionCode = "BLOCK", merchantId = null, version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)

        val entity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(entity)
        assertEquals("CONFLICT", entity.sync.syncStatus)
        assertEquals(0L, entity.sync.version)

        val conflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(conflict)
        assertEquals(0L, conflict.localVersion)
        assertEquals(1L, conflict.remoteVersion)
    }

    // 11. Pending DELETE + daha yeni remote version conflict oluşturur.
    @Test
    fun scenario11_pendingDelete_newerRemoteVersion_createsConflict() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_DELETE",
            version = 1L,
            baseVersion = 1L,
            deletedAtEpochMillis = 1500L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-del", entityId = local.id, entity = local, operationTypeCode = "DELETE", baseVersion = 1L)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)

        val entity = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(entity)
        assertEquals("CONFLICT", entity.sync.syncStatus)
    }

    // 12. Pending kayıt + remoteVersion <= baseVersion durumunda entity/outbox korunur, conflict oluşmaz ve yalnız cursor ilerler.
    @Test
    fun scenario12_pendingRecord_remoteVersionLessThanOrEqualToBaseVersion_preservesEntityAndAdvancesCursor() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 3L,
            baseVersion = 2L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-2", entityId = local.id, entity = local, operationTypeCode = "UPDATE", baseVersion = 2L)
        syncOperationDao.insert(op)

        val beforeSnapshot = captureSnapshot(local.id, "op-2")

        // Remote version is 2L (<= local baseVersion 2L)
        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)

        val afterSnapshot = captureSnapshot(local.id, "op-2")
        assertEquals(beforeSnapshot.entity, afterSnapshot.entity)
        assertEquals(beforeSnapshot.outbox, afterSnapshot.outbox)
        assertNull(afterSnapshot.conflict)

        // Cursor did advance
        assertNotNull(afterSnapshot.cursor)
        assertEquals(local.id, afterSnapshot.cursor.entityId)
    }

    // 13. CONFLICT yerel kayıt + daha yeni remote version conflict snapshot’ını güvenli günceller.
    @Test
    fun scenario13_conflictLocalRecord_newerRemoteVersion_updatesConflictSnapshotSafely() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "CONFLICT",
            version = 2L,
            baseVersion = 1L,
            lastSyncError = "CONFLICT_DETECTED",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-conflict", entityId = local.id, entity = local, operationTypeCode = "UPDATE", baseVersion = 1L, statusCode = "CONFLICT")
        syncOperationDao.insert(op)

        val initialConflict = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = ENTITY_TYPE,
            entityId = local.id,
            operationId = "op-conflict",
            localVersion = 2L,
            remoteVersion = 2L,
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            detectedAtEpochMillis = 1000L,
        )
        syncStateDao.upsertConflict(initialConflict)

        // Remote arrives with v3
        val remoteDto = sampleDto(version = 3L, updatedAt = "2026-10-15T12:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)

        val updatedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(updatedConflict)
        assertEquals(3L, updatedConflict.remoteVersion)
    }

    // 14. Kullanıcı B DTO’su kullanıcı A scope’unda reddedilir; hiçbir tablo değişmez.
    @Test
    fun scenario14_foreignUserDto_rejectedAndNoTablesModified() = runTest {
        val beforeSnapshot = captureSnapshot("any-id")

        val foreignDto = sampleDto(userId = USER_B_ID)
        fakeRemote.merchantPages = listOf(listOf(foreignDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot("any-id")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 15. Geçersiz/missing version reddedilir; cursor ilerlemez.
    @Test
    fun scenario15_invalidVersion_rejectedAndCursorDoesNotAdvance() = runTest {
        val beforeSnapshot = captureSnapshot("any-id")

        val invalidDto = sampleDto(version = null)
        fakeRemote.merchantPages = listOf(listOf(invalidDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot("any-id")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 16. Malformed updatedAt/deletedAt reddedilir; cursor ilerlemez.
    @Test
    fun scenario16_malformedUpdatedAt_rejectedAndCursorDoesNotAdvance() = runTest {
        val beforeSnapshot = captureSnapshot("any-id")

        val malformedDto = sampleDto(updatedAt = "invalid-date")
        fakeRemote.merchantPages = listOf(listOf(malformedDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot("any-id")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 17. Deterministik ID veya canonical alias ihlali reddedilir.
    @Test
    fun scenario17_nonDeterministicId_rejected() = runTest {
        val beforeSnapshot = captureSnapshot("any-id")

        val nonDeterministicDto = sampleDto(id = "11111111-2222-4333-8444-555555555555", alias = "MIGROS")
        fakeRemote.merchantPages = listOf(listOf(nonDeterministicDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot("any-id")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 18. Canlı outbox bulunmayan pending conflict yolu fail-closed olur.
    @Test
    fun scenario18_missingOutboxForPendingRecord_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // No outbox inserted!

        val beforeSnapshot = captureSnapshot(local.id)

        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id)
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 19. Terminal/legacy/bozuk outbox conflict yolu fail-closed olur.
    @Test
    fun scenario19_terminalOutbox_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val failedOp = sampleOperation("op-failed", entityId = local.id, entity = local, operationTypeCode = "UPDATE", statusCode = "FAILED", baseVersion = 1L)
        syncOperationDao.insert(failedOp)

        val beforeSnapshot = captureSnapshot(local.id, "op-failed")

        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-failed")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 20. Aktif remote fetch sırasında cancellation hiçbir merchant Room yazımı üretmez.
    @Test
    fun scenario20_cancellationDuringActiveRemoteFetch_producesNoMerchantRoomWrites() = runTest {
        val beforeSnapshot = captureSnapshot("any-id")

        val remoteDto = sampleDto()
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val fetchStarted = CompletableDeferred<Unit>()
        val cancelSignal = CompletableDeferred<Unit>()

        fakeRemote.onFetchMerchantVerifications = {
            fetchStarted.complete(Unit)
            cancelSignal.await()
        }

        val syncDeferred = async(Dispatchers.Default) {
            createSync().pullFor(userAId, SCOPE_A)
        }

        fetchStarted.await()
        syncDeferred.cancel()
        cancelSignal.complete(Unit)

        assertFailsWith<CancellationException> {
            syncDeferred.await()
        }

        val afterSnapshot = captureSnapshot(remoteDto.id)
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 21. Sonuç sayaçları applied/conflict/received değerlerini doğru verir.
    @Test
    fun scenario21_resultCounters_accurate() = runTest {
        // Item 1: clean apply (version 1)
        val dto1 = sampleDto(alias = "MIGROS", updatedAt = "2026-10-15T10:00:00Z", version = 1L)

        // Item 2: conflict with pending local
        val local2 = sampleLocalEntity(
            alias = "BIM",
            merchantId = "bim",
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local2)
        val op2 = sampleOperation("op-bim", entityId = local2.id, entity = local2, operationTypeCode = "UPDATE", baseVersion = 1L)
        syncOperationDao.insert(op2)
        val dto2 = sampleDto(alias = "BIM", merchantId = "bim", updatedAt = "2026-10-15T10:05:00Z", version = 2L)

        // Item 3: pending local where remoteVersion <= baseVersion (advance cursor only)
        val local3 = sampleLocalEntity(
            alias = "A101",
            merchantId = "a101",
            syncStatus = "PENDING_UPDATE",
            version = 3L,
            baseVersion = 2L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local3)
        val op3 = sampleOperation("op-a101", entityId = local3.id, entity = local3, operationTypeCode = "UPDATE", baseVersion = 2L)
        syncOperationDao.insert(op3)
        val dto3 = sampleDto(alias = "A101", merchantId = "a101", updatedAt = "2026-10-15T10:10:00Z", version = 2L)

        fakeRemote.merchantPages = listOf(listOf(dto1, dto2, dto3))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(1, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals(3, result.receivedMerchantVerificationCount)
    }

    // 22. Pending UPDATE, outbox yok, remoteVersion <= baseVersion: fail-closed; cursor ilerlemez.
    @Test
    fun scenario22_pendingUpdate_noOutbox_remoteVersionLeBaseVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // No outbox operation inserted!

        val beforeSnapshot = captureSnapshot(local.id)

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id)
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 23. Pending UPDATE, terminal FAILED outbox: fail-closed; cursor ilerlemez.
    @Test
    fun scenario23_pendingUpdate_terminalFailedOutbox_remoteVersionLeBaseVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val failedOp = sampleOperation(
            operationId = "op-failed-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "FAILED",
            baseVersion = 1L,
        )
        syncOperationDao.insert(failedOp)

        val beforeSnapshot = captureSnapshot(local.id, "op-failed-preserve")

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-failed-preserve")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 24. Pending UPDATE, protocolVersion != 2: fail-closed; cursor ilerlemez.
    @Test
    fun scenario24_pendingUpdate_protocolVersionNot2_remoteVersionLeBaseVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val legacyOp = sampleOperation(
            operationId = "op-v1-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
        ).copy(protocolVersion = 1)
        syncOperationDao.insert(legacyOp)

        val beforeSnapshot = captureSnapshot(local.id, "op-v1-preserve")

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-v1-preserve")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 25. Pending UPDATE, payload yerel entity ile uyuşmuyor: fail-closed; cursor ilerlemez.
    @Test
    fun scenario25_pendingUpdate_payloadMismatch_remoteVersionLeBaseVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 2L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        // Outbox payload says ASSIGN but local entity is BLOCK
        val mismatchedOp = sampleOperation(
            operationId = "op-mismatch-preserve",
            entityId = local.id,
            entity = local.copy(decisionCode = "ASSIGN", merchantId = "migros"),
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
        )
        syncOperationDao.insert(mismatchedOp)

        val beforeSnapshot = captureSnapshot(local.id, "op-mismatch-preserve")

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-mismatch-preserve")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 26. Pending UPDATE, local baseVersion > local version: fail-closed; cursor ilerlemez.
    @Test
    fun scenario26_pendingUpdate_baseVersionGreaterThanVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 2L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-bad-ver-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
        )
        syncOperationDao.insert(op)

        val beforeSnapshot = captureSnapshot(local.id, "op-bad-ver-preserve")

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-bad-ver-preserve")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 27. PENDING_CREATE için bozuk version/baseVersion: fail-closed.
    @Test
    fun scenario27_pendingCreate_corruptVersionOrBaseVersion_failsClosed() = runTest {
        val local = sampleLocalEntity(
            syncStatus = "PENDING_CREATE",
            version = 1L, // PENDING_CREATE must have version 0L
            baseVersion = null,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-bad-create-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "CREATE",
            baseVersion = null,
        )
        syncOperationDao.insert(op)

        val beforeSnapshot = captureSnapshot(local.id, "op-bad-create-preserve")

        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-bad-create-preserve")
        assertEquals(beforeSnapshot, afterSnapshot)
    }

    // 28. Geçerli pending UPDATE + canlı canonical V2 outbox + remoteVersion <= baseVersion: yalnız cursor ilerler.
    @Test
    fun scenario28_validPendingUpdate_liveCanonicalOutbox_remoteVersionLeBaseVersion_onlyCursorAdvances() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 3L,
            baseVersion = 2L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-valid-update-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
        )
        syncOperationDao.insert(op)

        val beforeSnapshot = captureSnapshot(local.id, "op-valid-update-preserve")

        // Remote version 2L <= local baseVersion 2L
        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedMerchantVerificationCount)

        val afterSnapshot = captureSnapshot(local.id, "op-valid-update-preserve")
        assertEquals(beforeSnapshot.entity, afterSnapshot.entity) // entity byte-for-byte preserved
        assertEquals(beforeSnapshot.outbox, afterSnapshot.outbox) // outbox byte-for-byte preserved
        assertNull(afterSnapshot.conflict) // no conflict created
        assertNotNull(afterSnapshot.cursor) // cursor advanced
        assertEquals(local.id, afterSnapshot.cursor.entityId)
    }

    // 29. Geçerli CONFLICT + canlı canonical V2 outbox + remoteVersion <= baseVersion: entity, outbox ve mevcut conflict korunur; yalnız cursor ilerler.
    @Test
    fun scenario29_validConflict_liveCanonicalOutbox_remoteVersionLeBaseVersion_preservesConflictAndOutbox_onlyCursorAdvances() = runTest {
        val local = sampleLocalEntity(
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "CONFLICT",
            version = 3L,
            baseVersion = 2L,
            lastSyncError = "CONFLICT_DETECTED",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-valid-conflict-preserve",
            entityId = local.id,
            entity = local,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            statusCode = "CONFLICT",
        )
        syncOperationDao.insert(op)

        val existingConflict = SyncConflictEntity(
            syncScopeKey = SCOPE_A,
            entityTypeCode = ENTITY_TYPE,
            entityId = local.id,
            operationId = "op-valid-conflict-preserve",
            localVersion = 3L,
            remoteVersion = 4L,
            localPayloadJson = "{\"id\":\"${local.id}\",\"local_decision\":\"BLOCK\"}",
            remotePayloadJson = "{\"id\":\"${local.id}\",\"remote_decision\":\"ASSIGN\"}",
            detectedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertConflictRow(existingConflict)

        val beforeSnapshot = captureSnapshot(local.id, "op-valid-conflict-preserve")

        // Remote version 2L <= local baseVersion 2L
        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T11:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedMerchantVerificationCount)

        val afterSnapshot = captureSnapshot(local.id, "op-valid-conflict-preserve")
        assertEquals(beforeSnapshot.entity, afterSnapshot.entity) // entity byte-for-byte preserved
        assertEquals(beforeSnapshot.outbox, afterSnapshot.outbox) // outbox byte-for-byte preserved
        assertEquals(beforeSnapshot.conflict, afterSnapshot.conflict) // conflict byte-for-byte preserved
        assertNotNull(afterSnapshot.cursor) // cursor advanced
        assertEquals(local.id, afterSnapshot.cursor.entityId)
    }

    // 30. Zincirli geçerli outbox (IN_FLIGHT predecessor + blocked PENDING successor):
    // remoteVersion > baseVersion durumunda conflict successor tail'e bağlanır, iki outbox korunur, cursor ilerler.
    @Test
    fun scenario30_chainedOutbox_remoteVersionGtBaseVersion_attachesConflictToSuccessorTail() = runTest {
        val predEntity = sampleLocalEntity(decisionCode = "ASSIGN", merchantId = "migros", syncStatus = "PENDING_UPDATE")
        val succEntity = sampleLocalEntity(decisionCode = "BLOCK", merchantId = null, syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(succEntity)

        val predOp = sampleOperation(
            operationId = "op-pred-30",
            entityId = succEntity.id,
            entity = predEntity,
            operationTypeCode = "UPDATE",
            statusCode = "IN_FLIGHT",
            baseVersion = 1L,
            isBlocked = false,
            predecessorOperationId = null,
        )
        val succOp = sampleOperation(
            operationId = "op-succ-30",
            entityId = succEntity.id,
            entity = succEntity,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
            isBlocked = true,
            predecessorOperationId = "op-pred-30",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        // Remote version 2L > local baseVersion 1L
        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals(1, result.receivedMerchantVerificationCount)

        // Local entity: CONFLICT status, decision fields preserved
        val storedEntity = remoteSyncDao.getUserMerchantVerificationRow(succEntity.id)
        assertNotNull(storedEntity)
        assertEquals("BLOCK", storedEntity.decisionCode)
        assertEquals("CONFLICT", storedEntity.sync.syncStatus)

        // Both outbox operations preserved
        val storedPred = syncOperationDao.getById(SCOPE_A, "op-pred-30")
        assertNotNull(storedPred)
        assertEquals("IN_FLIGHT", storedPred.statusCode)
        val storedSucc = syncOperationDao.getById(SCOPE_A, "op-succ-30")
        assertNotNull(storedSucc)
        assertEquals("PENDING", storedSucc.statusCode)
        assertEquals(true, storedSucc.isBlocked)

        // Conflict is attached to successor tail, NOT predecessor
        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, succEntity.id)
        assertNotNull(storedConflict)
        assertEquals("op-succ-30", storedConflict.operationId)
        assertTrue(storedConflict.operationId != "op-pred-30")
        assertEquals(2L, storedConflict.remoteVersion)

        // Cursor advanced
        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(succEntity.id, storedCursor.entityId)
    }

    // 31. Zincirli geçerli outbox (IN_FLIGHT predecessor + blocked PENDING successor):
    // remoteVersion <= baseVersion durumunda entity, predecessor, successor ve conflict değişmez; yalnız cursor ilerler.
    @Test
    fun scenario31_chainedOutbox_remoteVersionLeBaseVersion_preservesAllOperationsAndOnlyAdvancesCursor() = runTest {
        val predEntity = sampleLocalEntity(decisionCode = "ASSIGN", merchantId = "migros", syncStatus = "PENDING_UPDATE")
        val succEntity = sampleLocalEntity(decisionCode = "BLOCK", merchantId = null, syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(succEntity)

        val predOp = sampleOperation(
            operationId = "op-pred-31",
            entityId = succEntity.id,
            entity = predEntity,
            operationTypeCode = "UPDATE",
            statusCode = "IN_FLIGHT",
            baseVersion = 1L,
            isBlocked = false,
            predecessorOperationId = null,
        )
        val succOp = sampleOperation(
            operationId = "op-succ-31",
            entityId = succEntity.id,
            entity = succEntity,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
            isBlocked = true,
            predecessorOperationId = "op-pred-31",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        val beforeEntity = remoteSyncDao.getUserMerchantVerificationRow(succEntity.id)
        val beforePred = syncOperationDao.getById(SCOPE_A, "op-pred-31")
        val beforeSucc = syncOperationDao.getById(SCOPE_A, "op-succ-31")

        // Remote version 1L <= baseVersion 1L
        val remoteDto = sampleDto(version = 1L, updatedAt = "2026-10-15T12:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = createSync().pullFor(userAId, SCOPE_A)

        assertEquals(0, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.receivedMerchantVerificationCount)

        // Entity, predecessor, successor completely unchanged
        assertEquals(beforeEntity, remoteSyncDao.getUserMerchantVerificationRow(succEntity.id))
        assertEquals(beforePred, syncOperationDao.getById(SCOPE_A, "op-pred-31"))
        assertEquals(beforeSucc, syncOperationDao.getById(SCOPE_A, "op-succ-31"))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, succEntity.id))

        // Cursor advanced
        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(succEntity.id, storedCursor.entityId)
    }

    // 32. Bozuk topoloji (birden fazla aktif tail): pull işlemi fail-closed reddedilir ve rollback gerçekleşir.
    @Test
    fun scenario32_multipleActiveTails_failsClosedAndRollsBack() = runTest {
        val local = sampleLocalEntity(syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op1 = sampleOperation(
            operationId = "op-multitail-1",
            entityId = local.id,
            entity = local,
            isBlocked = false,
        )
        val op2 = sampleOperation(
            operationId = "op-multitail-2",
            entityId = local.id,
            entity = local,
            isBlocked = false,
        )
        syncOperationDao.insert(op1)
        syncOperationDao.insert(op2)

        val beforeSnapshot = captureSnapshot(local.id, "op-multitail-1")

        val remoteDto = sampleDto(version = 2L, updatedAt = "2026-10-15T13:00:00Z")
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        assertFailsWith<IllegalArgumentException> {
            createSync().pullFor(userAId, SCOPE_A)
        }

        val afterSnapshot = captureSnapshot(local.id, "op-multitail-1")
        assertEquals(beforeSnapshot.entity, afterSnapshot.entity)
        assertEquals(beforeSnapshot.outbox, afterSnapshot.outbox)
        assertNull(afterSnapshot.conflict)
        assertNull(afterSnapshot.cursor)
    }
}
