package com.feniqo.mobile.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.dao.InitialMerchantVerificationCommand
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.toDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import kotlinx.datetime.Instant
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
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
import com.feniqo.mobile.data.remote.core.UserMerchantVerificationRemoteQuery
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
class UserMerchantVerificationInitialSyncTest {

    private lateinit var db: FeniqoDatabase
    private lateinit var remoteSyncDao: RemoteSyncDao
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var syncStateDao: SyncStateDao
    private lateinit var fakeRemote: TestCoreRemoteDataSource
    private lateinit var initialRemoteSync: InitialRemoteSync

    private val userAId = EntityId(USER_A_ID)

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"
        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        const val ENTITY_TYPE = "USER_MERCHANT_VERIFICATION"
        const val RECEIVED_AT = 1_785_767_400_000L

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
        var profile: ProfileDto = ProfileDto(
            id = USER_A_ID,
            email = "user@feniqo.com",
            fullName = "User A",
            createdAt = "2026-10-15T10:00:00Z",
            updatedAt = "2026-10-15T10:00:00Z",
            version = 1L,
        )
        var categoryList: List<CategoryDto> = listOf(
            CategoryDto(
                id = "cat-1",
                userId = USER_A_ID,
                name = "Market",
                type = "expense",
                color = "#FF0000",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T10:00:00Z",
                version = 1L,
            ),
        )
        var transactionList: List<TransactionDto> = listOf(
            TransactionDto(
                id = "tx-1",
                userId = USER_A_ID,
                amountMinor = 5000L,
                type = "expense",
                categoryId = "cat-1",
                paymentMethod = "cash",
                transactionDate = "2026-10-15",
                description = "Migros",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T10:00:00Z",
                version = 1L,
            ),
        )
        var merchantPages: List<List<UserMerchantVerificationDto>> = emptyList()
        var onFetchMerchantVerifications: (suspend () -> Unit)? = null

        override suspend fun fetchProfile(userId: String): ProfileDto? = if (userId == profile.id) profile else null

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> =
            RemotePage(if (query.page.pageIndex == 0) categoryList else emptyList(), query.page, totalCount = categoryList.size.toLong())

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(if (query.page.pageIndex == 0) transactionList else emptyList(), query.page, totalCount = transactionList.size.toLong())

        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchAssets(query: AssetRemoteQuery): RemotePage<AssetDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> =
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

        override suspend fun fetchMerchantVerifications(query: UserMerchantVerificationRemoteQuery): RemotePage<UserMerchantVerificationDto> {
            onFetchMerchantVerifications?.invoke()
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

    private data class GlobalSnapshot(
        val profile: UserProfileEntity?,
        val category: CategoryEntity?,
        val transaction: TransactionEntity?,
        val merchant: UserMerchantVerificationEntity?,
        val operation: SyncOperationEntity?,
        val conflict: SyncConflictEntity?,
        val merchantCursor: SyncCursorEntity?,
    )

    private suspend fun captureSnapshot(
        merchantId: String? = null,
        operationId: String? = null,
    ): GlobalSnapshot = GlobalSnapshot(
        profile = remoteSyncDao.getProfileRow(USER_A_ID),
        category = remoteSyncDao.getCategoryRow("cat-1"),
        transaction = remoteSyncDao.getTransactionRow("tx-1"),
        merchant = merchantId?.let { remoteSyncDao.getUserMerchantVerificationRow(it) },
        operation = operationId?.let { syncOperationDao.getById(SCOPE_A, it) },
        conflict = merchantId?.let { remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, it) },
        merchantCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE),
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
        initialRemoteSync = InitialRemoteSync(fakeRemote, remoteSyncDao, { RECEIVED_AT })
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    // 1. Boş merchant snapshot; sahte cursor yok.
    @Test
    fun emptyMerchantSnapshot_producesNoMerchantCursor() = runTest {
        fakeRemote.merchantPages = listOf(emptyList())

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(0, result.merchantVerificationCount)
        assertEquals(1, result.categoryCount)
        assertEquals(1, result.transactionCount)
        assertNotNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNotNull(remoteSyncDao.getCategoryRow("cat-1"))
        assertNotNull(remoteSyncDao.getTransactionRow("tx-1"))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 2. Tek ve çok sayfalı temiz apply.
    @Test
    fun singleAndMultiPage_cleanApply() = runTest {
        val dto1 = sampleDto(alias = "NETFLIX", updatedAt = "2026-10-15T10:00:00Z")
        val dto2 = sampleDto(alias = "SPOTIFY", updatedAt = "2026-10-15T11:00:00Z")
        val dto3 = sampleDto(alias = "MIGROS", updatedAt = "2026-10-15T12:00:00Z")

        fakeRemote.merchantPages = listOf(listOf(dto1, dto2), listOf(dto3))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(3, result.merchantVerificationCount)
        val stored1 = remoteSyncDao.getUserMerchantVerificationRow(dto1.id)
        val stored2 = remoteSyncDao.getUserMerchantVerificationRow(dto2.id)
        val stored3 = remoteSyncDao.getUserMerchantVerificationRow(dto3.id)

        assertNotNull(stored1)
        assertNotNull(stored2)
        assertNotNull(stored3)
        assertEquals("SYNCED", stored1.sync.syncStatus)
        assertEquals("SYNCED", stored2.sync.syncStatus)
        assertEquals("SYNCED", stored3.sync.syncStatus)

        // Cursor matches the latest record (dto3 at 12:00:00Z)
        val cursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(cursor)
        assertEquals(dto3.id, cursor.entityId)
        assertEquals(kotlin.time.Instant.parse("2026-10-15T12:00:00Z").toEpochMilliseconds(), cursor.updatedAtEpochMillis)
    }

    // 3. Geçerli ASSIGN.
    @Test
    fun validAssign_appliedCleanly() = runTest {
        val dto = sampleDto(alias = "NETFLIX", decisionCode = "ASSIGN", merchantId = "netflix")
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val stored = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(stored)
        assertEquals("ASSIGN", stored.decisionCode)
        assertEquals("netflix", stored.merchantId)
    }

    // 4. Geçerli BLOCK.
    @Test
    fun validBlock_appliedCleanly() = runTest {
        val dto = sampleDto(alias = "BETTING SITE", decisionCode = "BLOCK", merchantId = null)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val stored = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(stored)
        assertEquals("BLOCK", stored.decisionCode)
        assertNull(stored.merchantId)
    }

    // 5. Remote tombstone soft-delete olarak uygulanır.
    @Test
    fun remoteTombstone_appliedAsSoftDelete() = runTest {
        val dto = sampleDto(
            alias = "NETFLIX",
            deletedAt = "2026-10-15T12:00:00Z",
            updatedAt = "2026-10-15T12:00:00Z",
        )
        fakeRemote.merchantPages = listOf(listOf(dto))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val stored = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(stored)
        assertNotNull(stored.sync.deletedAtEpochMillis)
        assertEquals(
            kotlin.time.Instant.parse("2026-10-15T12:00:00Z").toEpochMilliseconds(),
            stored.sync.deletedAtEpochMillis,
        )
    }

    // 6. Cross-user DTO tüm global snapshot'ı rollback eder.
    @Test
    fun crossUserDto_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(userId = USER_B_ID, alias = "NETFLIX")
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getCategoryRow("cat-1"))
        assertNull(remoteSyncDao.getTransactionRow("tx-1"))
        assertNull(remoteSyncDao.getUserMerchantVerificationRow(dto.id))
    }

    // 7. Geçersiz deterministik ID rollback.
    @Test
    fun invalidDeterministicId_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(id = "11111111-2222-3333-4444-555555555555", alias = "NETFLIX")
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
    }

    // 8. Missing/malformed updatedAt rollback.
    @Test
    fun missingOrMalformedUpdatedAt_rollsBackGlobalSnapshot() = runTest {
        val dtoMissing = sampleDto(alias = "NETFLIX", updatedAt = null)
        fakeRemote.merchantPages = listOf(listOf(dtoMissing))

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val dtoMalformed = sampleDto(alias = "SPOTIFY", updatedAt = "invalid-date-time")
        fakeRemote.merchantPages = listOf(listOf(dtoMalformed))

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    // 9. Missing/0/negatif version rollback.
    @Test
    fun missingZeroOrNegativeVersion_rollsBackGlobalSnapshot() = runTest {
        for (v in listOf(null, 0L, -1L)) {
            val dto = sampleDto(alias = "NETFLIX", version = v)
            fakeRemote.merchantPages = listOf(listOf(dto))

            assertFailsWith<IllegalArgumentException> {
                initialRemoteSync.pullFor(userAId, SCOPE_A)
            }
            assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        }
    }

    // 10. Yerel SYNCED + ileri remote version apply.
    @Test
    fun localSynced_newerRemoteVersion_appliesRemote() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val updated = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(updated)
        assertEquals(2L, updated.sync.version)
        assertEquals(2L, updated.sync.baseVersion)
    }

    // 11. Aynı version + 15 alan eşdeğer idempotent replay.
    @Test
    fun localSynced_sameVersion_identical15AuthoritativeFields_idempotentReplay() = runTest {
        val updatedEpoch = kotlin.time.Instant.parse("2026-10-15T10:00:00Z").toEpochMilliseconds()
        val dto = sampleDto(alias = "NETFLIX", version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            createdAtEpochMillis = updatedEpoch,
            updatedAtEpochMillis = updatedEpoch,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val stored = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(stored)
        assertEquals(local, stored)
        assertNotNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 12. Aynı version + farklı içerik tüm snapshot rollback.
    @Test
    fun localSynced_sameVersion_differentContent_rollsBackGlobalSnapshot() = runTest {
        val updatedEpoch = kotlin.time.Instant.parse("2026-10-15T10:00:00Z").toEpochMilliseconds()
        // Remote has BLOCK, local has ASSIGN
        val dto = sampleDto(
            alias = "NETFLIX",
            decisionCode = "BLOCK",
            merchantId = null,
            version = 1L,
            updatedAt = "2026-10-15T10:00:00Z",
        )
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
            createdAtEpochMillis = updatedEpoch,
            updatedAtEpochMillis = updatedEpoch,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    // 13. Daha eski remote version tüm snapshot rollback.
    @Test
    fun localSynced_olderRemoteVersion_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "SYNCED",
            version = 2L,
            baseVersion = 2L,
            updatedAtEpochMillis = 2000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
    }

    // 14. Pending + remoteVersion > baseVersion conflict; local/outbox korunur.
    @Test
    fun pending_remoteVersionGreaterThanBaseVersion_recordsConflict_preservesLocalAndOutbox() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-1",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
        )
        syncOperationDao.insert(op)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertNotNull(updatedLocal)
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)

        val storedOp = syncOperationDao.getById(SCOPE_A, "op-1")
        assertNotNull(storedOp)
        assertEquals(op, storedOp)

        val conflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, dto.id)
        assertNotNull(conflict)
        assertEquals("op-1", conflict.operationId)
        assertEquals(1L, conflict.localVersion)
        assertEquals(2L, conflict.remoteVersion)
        assertNotNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 15. Pending + remoteVersion <= baseVersion cursor-only preserve.
    @Test
    fun pending_remoteVersionLessOrEqualBaseVersion_preservesLocalAndOutbox_advancesCursorOnly() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation(
            operationId = "op-1",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
        )
        syncOperationDao.insert(op)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val storedLocal = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
        assertEquals(local, storedLocal)

        val storedOp = syncOperationDao.getById(SCOPE_A, "op-1")
        assertEquals(op, storedOp)

        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, dto.id))
        assertNotNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 16. IN_FLIGHT predecessor + blocked successor conflict successor tail'e bağlanır.
    @Test
    fun inFlightPredecessorAndBlockedSuccessor_conflictAttachesToSuccessorTail() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val predOp = sampleOperation(
            operationId = "op-pred",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "IN_FLIGHT",
            baseVersion = 1L,
            isBlocked = false,
            predecessorOperationId = null,
        )
        val succOp = sampleOperation(
            operationId = "op-succ-tail",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
            isBlocked = true,
            predecessorOperationId = "op-pred",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        val conflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, dto.id)
        assertNotNull(conflict)
        assertEquals("op-succ-tail", conflict.operationId)

        assertEquals(predOp, syncOperationDao.getById(SCOPE_A, "op-pred"))
        assertEquals(succOp, syncOperationDao.getById(SCOPE_A, "op-succ-tail"))
    }

    // 17. Aynı zincirde preserve tüm outbox satırlarını korur.
    @Test
    fun inFlightPredecessorAndBlockedSuccessor_preserveProtectsAllOutboxRows() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 1L, updatedAt = "2026-10-15T10:00:00Z")
        val local = sampleLocalEntity(
            id = dto.id,
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val predOp = sampleOperation(
            operationId = "op-pred",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "IN_FLIGHT",
            baseVersion = 1L,
            isBlocked = false,
            predecessorOperationId = null,
        )
        val succOp = sampleOperation(
            operationId = "op-succ-tail",
            entityId = dto.id,
            entity = local,
            operationTypeCode = "UPDATE",
            statusCode = "PENDING",
            baseVersion = 1L,
            isBlocked = true,
            predecessorOperationId = "op-pred",
        )
        syncOperationDao.insert(predOp)
        syncOperationDao.insert(succOp)

        fakeRemote.merchantPages = listOf(listOf(dto))
        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, dto.id))
        assertEquals(predOp, syncOperationDao.getById(SCOPE_A, "op-pred"))
        assertEquals(succOp, syncOperationDao.getById(SCOPE_A, "op-succ-tail"))
        assertNotNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 18. Outbox yok / çoklu tail / orphan successor / terminal predecessor rollback.
    @Test
    fun topologyFailure_noOutbox_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L)
        val local = sampleLocalEntity(id = dto.id, alias = "NETFLIX", syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    @Test
    fun topologyFailure_multipleTails_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L)
        val local = sampleLocalEntity(id = dto.id, alias = "NETFLIX", syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val op1 = sampleOperation(operationId = "op1", entityId = dto.id, entity = local, isBlocked = false)
        val op2 = sampleOperation(operationId = "op2", entityId = dto.id, entity = local, isBlocked = false)
        syncOperationDao.insert(op1)
        syncOperationDao.insert(op2)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    @Test
    fun topologyFailure_orphanSuccessor_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L)
        val local = sampleLocalEntity(id = dto.id, alias = "NETFLIX", syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val orphanSucc = sampleOperation(
            operationId = "op-orphan",
            entityId = dto.id,
            entity = local,
            isBlocked = true,
            predecessorOperationId = "non-existent-pred",
        )
        syncOperationDao.insert(orphanSucc)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    @Test
    fun topologyFailure_terminalPredecessor_rollsBackGlobalSnapshot() = runTest {
        val dto = sampleDto(alias = "NETFLIX", version = 2L)
        val local = sampleLocalEntity(id = dto.id, alias = "NETFLIX", syncStatus = "PENDING_UPDATE")
        remoteSyncDao.upsertUserMerchantVerificationRow(local)

        val terminalPred = sampleOperation(
            operationId = "op-term",
            entityId = dto.id,
            entity = local,
            statusCode = "FAILED",
            isBlocked = false,
        )
        val validSucc = sampleOperation(
            operationId = "op-succ",
            entityId = dto.id,
            entity = local,
            isBlocked = true,
            predecessorOperationId = "op-term",
        )
        syncOperationDao.insert(terminalPred)
        syncOperationDao.insert(validSucc)
        fakeRemote.merchantPages = listOf(listOf(dto))

        val preSnapshot = captureSnapshot(dto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(dto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
    }

    // 19. Bir merchant kayıt geçerli, sonraki bozuk: hiçbir profile/category/transaction/merchant/cursor kısmi yazımı kalmaz (pre-validation).
    @Test
    fun oneValidMerchantRecord_nextBroken_preValidation_noPartialWritesRemaining() = runTest {
        val validDto = sampleDto(alias = "NETFLIX", updatedAt = "2026-10-15T10:00:00Z", version = 1L)
        val brokenDto = sampleDto(alias = "SPOTIFY", updatedAt = "2026-10-15T11:00:00Z", version = 0L)

        fakeRemote.merchantPages = listOf(listOf(validDto, brokenDto))

        val preSnapshot = captureSnapshot(validDto.id)

        assertFailsWith<IllegalArgumentException> {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        val postSnapshot = captureSnapshot(validDto.id)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getCategoryRow("cat-1"))
        assertNull(remoteSyncDao.getTransactionRow("tx-1"))
        assertNull(remoteSyncDao.getUserMerchantVerificationRow(validDto.id))
        assertNull(remoteSyncDao.getUserMerchantVerificationRow(brokenDto.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 20. Aktif remote merchant fetch sırasında cancellation: hiçbir initial snapshot yazımı oluşmaz.
    @Test
    fun activeRemoteMerchantFetch_cancellation_noInitialSnapshotWritten() = runTest {
        val gate = CompletableDeferred<Unit>()
        val reached = CompletableDeferred<Unit>()

        fakeRemote.onFetchMerchantVerifications = {
            reached.complete(Unit)
            gate.await()
        }
        fakeRemote.merchantPages = listOf(listOf(sampleDto(alias = "NETFLIX")))

        val preSnapshot = captureSnapshot()

        val deferred = async(Dispatchers.Default) {
            initialRemoteSync.pullFor(userAId, SCOPE_A)
        }

        reached.await()
        deferred.cancel(CancellationException("Test cancellation"))

        assertFailsWith<CancellationException> {
            deferred.await()
        }

        val postSnapshot = captureSnapshot()
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getCategoryRow("cat-1"))
        assertNull(remoteSyncDao.getTransactionRow("tx-1"))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 21. InitialSyncResult merchantVerificationCount doğru.
    @Test
    fun initialSyncResult_merchantVerificationCount_isAccurate() = runTest {
        val d1 = sampleDto(alias = "NETFLIX", updatedAt = "2026-10-15T10:00:00Z")
        val d2 = sampleDto(alias = "SPOTIFY", updatedAt = "2026-10-15T11:00:00Z")
        val d3 = sampleDto(alias = "MIGROS", updatedAt = "2026-10-15T12:00:00Z")

        fakeRemote.merchantPages = listOf(listOf(d1, d2, d3))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(3, result.merchantVerificationCount)
        assertEquals(1, result.categoryCount)
        assertEquals(1, result.transactionCount)
    }

    private suspend fun callApplyInitialSnapshot(
        merchantVerificationCommands: List<InitialMerchantVerificationCommand>,
    ) {
        val receivedAt = RECEIVED_AT
        val profile = fakeRemote.profile.toDomain().toEntity(fakeRemote.profile.toRemoteSyncMetadata(receivedAt))
        val categories = fakeRemote.categoryList.map { it.toDomain().toEntity(it.toRemoteSyncMetadata(receivedAt), slug = it.slug) }
        val transactions = fakeRemote.transactionList.map { it.toDomain().toEntity(it.toRemoteSyncMetadata(receivedAt)) }
        remoteSyncDao.applyInitialSnapshot(
            syncScopeKey = SCOPE_A,
            profile = profile,
            categories = categories,
            recurringTransactions = emptyList(),
            subscriptions = emptyList(),
            goals = emptyList(),
            goalContributions = emptyList(),
            debts = emptyList(),
            debtPayments = emptyList(),
            merchantVerificationCommands = merchantVerificationCommands,
            transactions = transactions,
            cursors = listOfNotNull(
                SyncCursorEntity(SCOPE_A, "PROFILE", receivedAt, profile.id),
                SyncCursorEntity(SCOPE_A, "CATEGORY", receivedAt, categories.first().id),
                SyncCursorEntity(SCOPE_A, "TRANSACTION", receivedAt, transactions.first().id),
            ),
        )
    }

    private fun sampleSnapshotJson(entity: UserMerchantVerificationEntity): String {
        val dto = entity.toDomain().toDto(
            version = entity.sync.version,
            deletedAt = entity.sync.deletedAtEpochMillis?.let { Instant.fromEpochMilliseconds(it) },
        )
        return UserMerchantVerificationPayloadCodec.encodeSnapshot(dto)
    }

    // 22. Gerçek çoklu-command rollback testi: ilk merchant command transaction içinde başarıyla apply edilir,
    // ikinci merchant command transaction içinde (outbox/topoloji/stale) başarısız olur; hiçbir tabloya yazım kalmaz.
    @Test
    fun firstMerchantValidApply_secondMerchantTransactionTopologyFailure_rollsBackAllWrites() = runTest {
        val spotifyLocal = sampleLocalEntity(
            alias = "SPOTIFY",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(spotifyLocal)
        val spotifyOp = sampleOperation("op-spotify", entityId = spotifyLocal.id, entity = spotifyLocal)
        syncOperationDao.insert(spotifyOp)

        // Command 1: Yeni geçerli remote entity (NETFLIX) -> Apply komutu (transaction içinde ilk çalışacak ve yazacak)
        val netflixRemoteDto = sampleDto(alias = "NETFLIX", version = 1L)
        val netflixRemoteEntity = netflixRemoteDto.toEntity(netflixRemoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val netflixCursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, netflixRemoteDto.id)
        val command1 = InitialMerchantVerificationCommand.Apply(
            remoteEntity = netflixRemoteEntity,
            cursor = netflixCursor,
        )

        // Command 2: Spotify için Conflict komutu, ancak stale expectedLocalEntity ile hazırlanmış (localUpdatedAtEpochMillis uyuşmazlığı)
        val spotifyRemoteDto = sampleDto(alias = "SPOTIFY", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val spotifyRemoteEntity = spotifyRemoteDto.toEntity(spotifyRemoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val spotifyCursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, spotifyRemoteDto.id)
        val staleExpectedLocal = spotifyLocal.copy(
            sync = spotifyLocal.sync.copy(localUpdatedAtEpochMillis = 9999L),
        )
        val command2 = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = spotifyLocal.id,
                operationId = spotifyOp.operationId,
                localVersion = spotifyLocal.sync.version,
                remoteVersion = 2L,
                localPayloadJson = sampleSnapshotJson(spotifyLocal),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(spotifyRemoteDto),
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = spotifyRemoteEntity,
            cursor = spotifyCursor,
            expectedLocalEntity = staleExpectedLocal,
        )

        val preSnapshot = captureSnapshot(spotifyLocal.id, spotifyOp.operationId)

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(command1, command2))
        }

        // Doğrulama: Tüm 7 tabloda tam rollback
        // 1. profiles
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        // 2. categories
        assertNull(remoteSyncDao.getCategoryRow("cat-1"))
        // 3. transactions
        assertNull(remoteSyncDao.getTransactionRow("tx-1"))
        // 4. user_merchant_verifications: ilk merchant (NETFLIX) yazılmıştı, rollback ile silindi!
        assertNull(remoteSyncDao.getUserMerchantVerificationRow(netflixRemoteDto.id))
        // İkinci merchant (SPOTIFY) ilk haline eşit kaldı
        assertEquals(spotifyLocal, remoteSyncDao.getUserMerchantVerificationRow(spotifyLocal.id))
        // 5. sync_operations: outbox işlemi ilk haline eşit kaldı
        assertEquals(spotifyOp, syncOperationDao.getById(SCOPE_A, spotifyOp.operationId))
        // 6. sync_conflicts: hiçbir conflict kaydedilmedi
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, spotifyLocal.id))
        // 7. sync_cursors: merchant cursor yazılmadı
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))

        val postSnapshot = captureSnapshot(spotifyLocal.id, spotifyOp.operationId)
        assertEquals(preSnapshot, postSnapshot)
    }

    // 23. TOCTOU A: Conflict command stale BLOCK kararı ile hazırlanır, transaction öncesi ASSIGN kararına coalesce edilir -> fail-closed rollback.
    @Test
    fun conflict_staleLocalDecision_rollsBackGlobalSnapshot() = runTest {
        val blockLocal = sampleLocalEntity(
            alias = "NETFLIX",
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(blockLocal)
        val op = sampleOperation("op-tail", entityId = blockLocal.id, entity = blockLocal)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val remoteEntity = remoteDto.toEntity(remoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val cursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, remoteDto.id)

        // Conflict command stale BLOCK snapshot'ı ile hazırlanır
        val conflictCommand = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = blockLocal.id,
                operationId = op.operationId,
                localVersion = 1L,
                remoteVersion = 2L,
                localPayloadJson = sampleSnapshotJson(blockLocal),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = remoteEntity,
            cursor = cursor,
            expectedLocalEntity = blockLocal,
        )

        // Transaction öncesi aynı version ve aynı operationId altında ASSIGN kararına coalesce edilir
        val assignLocal = blockLocal.copy(
            decisionCode = "ASSIGN",
            merchantId = "netflix",
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(assignLocal)

        val preSnapshot = captureSnapshot(blockLocal.id, op.operationId)

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(conflictCommand))
        }

        val postSnapshot = captureSnapshot(blockLocal.id, op.operationId)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, blockLocal.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 24. TOCTOU B: Version ve operationId değişmeden localUpdatedAt veya authoritative updatedAt değiştirilir -> eski snapshot reddedilir.
    @Test
    fun conflict_staleLocalTimestampOrLocalUpdatedAt_rollsBackGlobalSnapshot() = runTest {
        val local = sampleLocalEntity(
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-tail", entityId = local.id, entity = local)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val remoteEntity = remoteDto.toEntity(remoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val cursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, remoteDto.id)

        val conflictCommand = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = local.id,
                operationId = op.operationId,
                localVersion = 1L,
                remoteVersion = 2L,
                localPayloadJson = sampleSnapshotJson(local),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = remoteEntity,
            cursor = cursor,
            expectedLocalEntity = local,
        )

        // Room'daki local entity localUpdatedAtEpochMillis güncellenerek yarış simüle edilir (version aynı kalır)
        val racedLocal = local.copy(
            sync = local.sync.copy(localUpdatedAtEpochMillis = 1500L),
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(racedLocal)

        val preSnapshot = captureSnapshot(local.id, op.operationId)

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(conflictCommand))
        }

        val postSnapshot = captureSnapshot(local.id, op.operationId)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 25. TOCTOU C: remoteEntity NETFLIX iken remotePayloadJson başka alias/karar/timestamp taşır -> fail-closed rollback.
    @Test
    fun conflict_mismatchedRemoteSnapshot_rollsBackGlobalSnapshot() = runTest {
        val local = sampleLocalEntity(
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-tail", entityId = local.id, entity = local)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val remoteEntity = remoteDto.toEntity(remoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val cursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, remoteDto.id)

        // remotePayloadJson SPOTIFY DTO'su ile hazırlanmış
        val mismatchedRemoteDto = sampleDto(alias = "SPOTIFY", version = 2L, updatedAt = "2026-10-15T12:00:00Z")

        val conflictCommand = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = local.id,
                operationId = op.operationId,
                localVersion = 1L,
                remoteVersion = 2L,
                localPayloadJson = sampleSnapshotJson(local),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(mismatchedRemoteDto),
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = remoteEntity,
            cursor = cursor,
            expectedLocalEntity = local,
        )

        val preSnapshot = captureSnapshot(local.id, op.operationId)

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(conflictCommand))
        }

        val postSnapshot = captureSnapshot(local.id, op.operationId)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 26. TOCTOU D: Blank olmayan fakat decode edilemeyen veya zorunlu alanı eksik JSON reddedilir -> global rollback.
    @Test
    fun conflict_malformedLocalOrRemoteJson_rollsBackGlobalSnapshot() = runTest {
        val local = sampleLocalEntity(
            alias = "NETFLIX",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-tail", entityId = local.id, entity = local)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(alias = "NETFLIX", version = 2L, updatedAt = "2026-10-15T12:00:00Z")
        val remoteEntity = remoteDto.toEntity(remoteDto.toRemoteSyncMetadata(RECEIVED_AT))
        val cursor = SyncCursorEntity(SCOPE_A, ENTITY_TYPE, RECEIVED_AT, remoteDto.id)

        val malformedLocalCommand = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = local.id,
                operationId = op.operationId,
                localVersion = 1L,
                remoteVersion = 2L,
                localPayloadJson = "{\"unknownField\":\"malformed\"}",
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = remoteEntity,
            cursor = cursor,
            expectedLocalEntity = local,
        )

        val preSnapshot = captureSnapshot(local.id, op.operationId)

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(malformedLocalCommand))
        }

        val malformedRemoteCommand = InitialMerchantVerificationCommand.Conflict(
            conflict = SyncConflictEntity(
                syncScopeKey = SCOPE_A,
                entityTypeCode = ENTITY_TYPE,
                entityId = local.id,
                operationId = op.operationId,
                localVersion = 1L,
                remoteVersion = 2L,
                localPayloadJson = sampleSnapshotJson(local),
                remotePayloadJson = "{not even json}",
                detectedAtEpochMillis = RECEIVED_AT,
            ),
            remoteEntity = remoteEntity,
            cursor = cursor,
            expectedLocalEntity = local,
        )

        assertFailsWith<IllegalArgumentException> {
            callApplyInitialSnapshot(listOf(malformedRemoteCommand))
        }

        val postSnapshot = captureSnapshot(local.id, op.operationId)
        assertEquals(preSnapshot, postSnapshot)
        assertNull(remoteSyncDao.getProfileRow(USER_A_ID))
        assertNull(remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id))
        assertNull(syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE))
    }

    // 27. TOCTOU E: Geçerli snapshot ile conflict başarılı kaydedilir ve kaydedilen snapshot entity ile tam eşleşir.
    @Test
    fun conflict_validSnapshot_recordsConflictMatchingEntities() = runTest {
        val local = sampleLocalEntity(
            alias = "NETFLIX",
            decisionCode = "BLOCK",
            merchantId = null,
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 1L,
            updatedAtEpochMillis = 1000L,
        )
        remoteSyncDao.upsertUserMerchantVerificationRow(local)
        val op = sampleOperation("op-tail", entityId = local.id, entity = local)
        syncOperationDao.insert(op)

        val remoteDto = sampleDto(
            alias = "NETFLIX",
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            version = 2L,
            updatedAt = "2026-10-15T12:00:00Z",
        )
        fakeRemote.merchantPages = listOf(listOf(remoteDto))

        val result = initialRemoteSync.pullFor(userAId, SCOPE_A)

        assertEquals(1, result.merchantVerificationCount)

        val storedConflict = remoteSyncDao.getConflictRow(SCOPE_A, ENTITY_TYPE, local.id)
        assertNotNull(storedConflict)
        assertEquals(1L, storedConflict.localVersion)
        assertEquals(2L, storedConflict.remoteVersion)

        // Stored conflict snapshot'ları decode edilip doğrulanır
        val decodedLocal = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(storedConflict.localPayloadJson)
        val decodedRemote = UserMerchantVerificationPayloadCodec.decodeAndValidateSnapshot(storedConflict.remotePayloadJson)

        assertEquals(local.id, decodedLocal.id)
        assertEquals(local.ownerId, decodedLocal.userId)
        assertEquals(local.normalizedAlias, decodedLocal.normalizedAlias)
        assertEquals(local.decisionCode, decodedLocal.decisionCode)
        assertEquals(local.merchantId, decodedLocal.merchantId)
        assertEquals(local.sync.version, decodedLocal.version)

        assertEquals(remoteDto.id, decodedRemote.id)
        assertEquals(remoteDto.userId, decodedRemote.userId)
        assertEquals(remoteDto.normalizedAlias, decodedRemote.normalizedAlias)
        assertEquals(remoteDto.decisionCode, decodedRemote.decisionCode)
        assertEquals(remoteDto.merchantId, decodedRemote.merchantId)
        assertEquals(remoteDto.version, decodedRemote.version)

        val updatedLocal = remoteSyncDao.getUserMerchantVerificationRow(local.id)
        assertNotNull(updatedLocal)
        assertEquals("CONFLICT", updatedLocal.sync.syncStatus)

        val storedCursor = syncStateDao.getCursor(SCOPE_A, ENTITY_TYPE)
        assertNotNull(storedCursor)
        assertEquals(remoteDto.id, storedCursor.entityId)
    }
}
