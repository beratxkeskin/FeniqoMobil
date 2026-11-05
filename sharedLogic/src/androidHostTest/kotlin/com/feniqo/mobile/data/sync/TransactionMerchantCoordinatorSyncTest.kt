package com.feniqo.mobile.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
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
import com.feniqo.mobile.data.repository.OfflineFirstSyncRepository
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.TransactionMerchantAttributionResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantCoordinatorSyncTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    private val userId = "11111111-1111-4111-8111-111111111111"
    private val scope = SyncScopeKey.user(userId).rawValue
    private val nowEpoch = 1_700_000_000_000L
    private val nowInstant = Instant.parse("2026-08-21T10:00:00Z")

    private fun inMemoryDatabase(): FeniqoDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder =
            Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
                context = context,
                factory = { FeniqoDatabaseConstructor.initialize() },
            )
        builder.setQueryCoroutineContext(Dispatchers.Default)
        return builder.build()
    }

    private fun sampleTransactionDto(
        id: String = "11111111-1111-4111-8111-000000000001",
        description: String = "Spotify",
        version: Long = 2L,
        installmentNumber: Int? = null,
        totalInstallments: Int? = null,
        installmentGroupId: String? = null,
    ): TransactionDto =
        TransactionDto(
            id = id,
            userId = userId,
            workspaceId = null,
            amountMinor = 5999L,
            currency = "TRY",
            type = "EXPENSE",
            categoryId = "cat-1",
            description = description,
            paymentMethod = "CREDIT_CARD",
            transactionDate = "2026-08-21",
            receiptPath = null,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            installmentGroupId = installmentGroupId,
            version = version,
            createdAt = "2026-08-21T10:00:00Z",
            updatedAt = "2026-08-21T10:00:00Z",
            deletedAt = null,
        )

    private fun sampleTransactionEntity(
        id: String = "11111111-1111-4111-8111-000000000001",
        description: String = "Spotify",
        merchantId: String? = null,
        version: Long = 1L,
        installmentNumber: Int? = null,
        totalInstallments: Int? = null,
        installmentGroupId: String? = null,
    ): TransactionEntity {
        val merchantInfo =
            merchantId?.let {
                TransactionMerchantInfo(
                    merchantId = EntityId(it),
                    matchSource = MerchantMatchSource.EXACT_ALIAS,
                    confidence = 100,
                    catalogVersion = 1,
                )
            }
        val domain =
            Transaction(
                id = EntityId(id),
                ownerId = EntityId(userId),
                workspaceId = null,
                amount = Money(5999L, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("cat-1"),
                description = description,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                transactionDate = LocalDate(2026, 8, 21),
                receiptPath = null,
                installment = null,
                createdAt = nowInstant,
                merchantInfo = merchantInfo,
            )
        val sync =
            newSyncMetadata(nowEpoch, SyncStatus.PENDING_UPDATE).copy(
                version = version,
                baseVersion = version,
            )
        val entity = domain.toEntity(sync)
        return if (installmentGroupId != null) {
            entity.copy(
                installmentNumber = installmentNumber,
                totalInstallments = totalInstallments,
                installmentGroupId = installmentGroupId,
            )
        } else {
            entity
        }
    }

    private fun sampleProfileEntity(): UserProfileEntity =
        UserProfileEntity(
            id = userId,
            email = "test@feniqo.com",
            fullName = "Test User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "TR",
            activeWorkspaceId = null,
            createdAtEpochMillis = nowEpoch,
            sync =
                newSyncMetadata(nowEpoch, SyncStatus.SYNCED).copy(
                    version = 1L,
                    baseVersion = 1L,
                ),
        )

    private suspend fun seedBaseData(db: FeniqoDatabase) {
        db.profileDao().insertIfMissing(sampleProfileEntity())
        val category =
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId(userId),
                workspaceId = null,
                name = "Entertainment",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#FF0000"),
                icon = null,
                isDefault = false,
                createdAt = nowInstant,
            )
        db.categoryDao().upsert(
            category.toEntity(
                newSyncMetadata(nowEpoch, SyncStatus.SYNCED).copy(
                    version = 1L,
                    baseVersion = 1L,
                ),
            ),
        )
    }

    private fun createOperationEntity(
        operationId: String,
        entityId: String,
        payloadJson: String,
        baseVersion: Long? = 1L,
        operationTypeCode: String = "UPDATE",
        protocolVersion: Int = 1,
    ): SyncOperationEntity =
        SyncOperationEntity(
            operationId = operationId,
            syncScopeKey = scope,
            entityTypeCode = "TRANSACTION",
            entityId = entityId,
            operationTypeCode = operationTypeCode,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            protocolVersion = protocolVersion,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = nowEpoch,
            createdAtEpochMillis = nowEpoch,
            updatedAtEpochMillis = nowEpoch,
        )

    private open class BaseFakeCoreRemoteDataSource(
        private val transactionsToReturn: List<TransactionDto> = emptyList(),
    ) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? =
            ProfileDto(
                id = userId,
                email = "test@feniqo.com",
                fullName = "Test User",
                currency = "TRY",
                theme = "system",
                lang = "tr",
                version = 1L,
                createdAt = "2026-08-21T10:00:00Z",
                updatedAt = "2026-08-21T10:00:00Z",
            )

        override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> =
            RemotePage(
                items = transactionsToReturn,
                request = query.page,
                totalCount = transactionsToReturn.size.toLong(),
            )

        override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchTags(
            scope: com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope,
            page: RemotePageRequest,
        ): RemotePage<TagDto> = RemotePage(emptyList(), page, totalCount = 0)

        override suspend fun fetchMerchantVerifications(
            query: com.feniqo.mobile.data.remote.core.UserMerchantVerificationRemoteQuery,
        ): RemotePage<com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto> =
            RemotePage(emptyList(), query.page, totalCount = 0)

        override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()

        override suspend fun upsertProfile(dto: ProfileDto) = Unit

        override suspend fun upsertCategory(dto: CategoryDto) = Unit

        override suspend fun upsertTransaction(dto: TransactionDto) = Unit

        override suspend fun upsertBudget(dto: BudgetDto) = Unit

        override suspend fun upsertTag(dto: TagDto) = Unit

        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
    }

    private class FakeAuthRepository(
        initialSession: AuthSession?,
    ) : AuthRepository {
        private val sessionFlow = MutableStateFlow(initialSession)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow

        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)

        override suspend fun signUp(
            email: String,
            password: String,
            fullName: String?,
        ): RepositoryResult<EntityId> = error("unused")

        override suspend fun signIn(
            email: String,
            password: String,
        ): RepositoryResult<Unit> = error("unused")

        override suspend fun refreshSession(): RepositoryResult<Unit> = error("unused")

        override suspend fun signOut(): RepositoryResult<Unit> = error("unused")
    }

    private class FakeOutboxQueue : OutboxQueue {
        override suspend fun readyOperations(
            syncScopeKey: String,
            limit: Int,
        ): List<SyncOperationEntity> = emptyList()

        override suspend fun claimOperation(
            syncScopeKey: String,
            operationId: String,
        ): SyncOperationEntity? = null

        override suspend fun markSucceeded(
            syncScopeKey: String,
            operationId: String,
        ): Boolean = true

        override suspend fun ackV2Execution(
            syncScopeKey: String,
            operationId: String,
            result: OutboxExecutionResult,
        ): Boolean = true

        override suspend fun recordV2Conflict(
            syncScopeKey: String,
            conflict: SyncConflictEntity,
        ): Boolean = true

        override suspend fun recordFailure(
            syncScopeKey: String,
            operationId: String,
            errorMessage: String,
        ): Boolean = true

        override suspend fun markConflict(
            syncScopeKey: String,
            operationId: String,
            errorMessage: String,
        ): Boolean = true
    }

    // --- 1. V2OutboxOperationExecutor Tests ---

    @Test
    fun v2OutboxOperationExecutor_appliedSpotify_resolvesCandidateMerchant() =
        runTest {
            val dto =
                sampleTransactionDto(
                    id = "11111111-1111-4111-8111-000000000001",
                    description = "Spotify",
                )
            val writer =
                object : IdempotentConditionalRemoteWriter {
                    override suspend fun writeTransaction(
                        operationId: String,
                        operation: RemoteWriteOperation,
                        baseVersion: Long?,
                        dto: TransactionDto,
                    ): ConditionalRemoteWriteResult<TransactionDto> = ConditionalRemoteWriteResult.Applied(dto)
                }
            val executor = V2OutboxOperationExecutor(writer) { nowEpoch }
            val op =
                createOperationEntity(
                    operationId = "op-1",
                    entityId = dto.id,
                    payloadJson = json.encodeToString(dto),
                    protocolVersion = 2,
                )

            val result = executor.execute(op)

            val applied = assertIs<OutboxExecutionResult.TransactionApplied>(result)
            assertEquals(dto.id, applied.record.id)
            assertNotNull(applied.candidateMerchant)
            assertEquals("spotify", applied.candidateMerchant.merchantId.value)
            assertEquals(MerchantMatchSource.EXACT_ALIAS, applied.candidateMerchant.matchSource)
        }

    @Test
    fun v2OutboxOperationExecutor_appliedUnknown_candidateMerchantIsNull() =
        runTest {
            val dto =
                sampleTransactionDto(
                    id = "11111111-1111-4111-8111-000000000002",
                    description = "Bilinmeyen Yerel Harcama 123",
                )
            val writer =
                object : IdempotentConditionalRemoteWriter {
                    override suspend fun writeTransaction(
                        operationId: String,
                        operation: RemoteWriteOperation,
                        baseVersion: Long?,
                        dto: TransactionDto,
                    ): ConditionalRemoteWriteResult<TransactionDto> = ConditionalRemoteWriteResult.Applied(dto)
                }
            val executor = V2OutboxOperationExecutor(writer) { nowEpoch }
            val op =
                createOperationEntity(
                    operationId = "op-2",
                    entityId = dto.id,
                    payloadJson = json.encodeToString(dto),
                    protocolVersion = 2,
                )

            val result = executor.execute(op)

            val applied = assertIs<OutboxExecutionResult.TransactionApplied>(result)
            assertEquals(dto.id, applied.record.id)
            assertNull(applied.candidateMerchant)
        }

    // --- 2. V1OutboxOperationExecutor Tests ---

    @Test
    fun v1OutboxOperationExecutor_appliedSpotify_attachesCandidateToDaoEntity() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)
                val txId = "11111111-1111-4111-8111-000000000011"
                val initial =
                    sampleTransactionEntity(
                        id = txId,
                        description = "Eski Harcama",
                        merchantId = null,
                    )
                db.transactionDao().upsert(initial)

                val remoteDto =
                    sampleTransactionDto(
                        id = txId,
                        description = "Spotify",
                        version = 2L,
                    )
                val writer =
                    object : ConditionalRemoteWriter {
                        override suspend fun writeProfile(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: ProfileDto,
                        ) = error("unused")

                        override suspend fun writeCategory(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: CategoryDto,
                        ) = error("unused")

                        override suspend fun writeTransaction(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: TransactionDto,
                        ): ConditionalRemoteWriteResult<TransactionDto> = ConditionalRemoteWriteResult.Applied(remoteDto)
                    }

                val executor =
                    V1OutboxOperationExecutor(
                        writer = writer,
                        remoteSyncDao = db.remoteSyncDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val op =
                    createOperationEntity(
                        operationId = "op-v1-1",
                        entityId = initial.id,
                        payloadJson = json.encodeToString(remoteDto),
                    )

                executor.execute(op)

                val persisted = db.localMutationDao().getTransactionRow(initial.id)
                assertNotNull(persisted)
                assertEquals("spotify", persisted.merchantId)
                assertEquals("EXACT_ALIAS", persisted.merchantMatchSourceCode)
            } finally {
                db.close()
            }
        }

    @Test
    fun v1OutboxOperationExecutor_appliedUnknown_attachesNullCandidateToDaoEntity() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)
                val txId = "11111111-1111-4111-8111-000000000012"
                val initial =
                    sampleTransactionEntity(
                        id = txId,
                        description = "Eski Harcama",
                        merchantId = null,
                    )
                db.transactionDao().upsert(initial)

                val remoteDto =
                    sampleTransactionDto(
                        id = txId,
                        description = "Bilinmeyen Mahalle Bakkali",
                        version = 2L,
                    )
                val writer =
                    object : ConditionalRemoteWriter {
                        override suspend fun writeProfile(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: ProfileDto,
                        ) = error("unused")

                        override suspend fun writeCategory(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: CategoryDto,
                        ) = error("unused")

                        override suspend fun writeTransaction(
                            operation: RemoteWriteOperation,
                            baseVersion: Long?,
                            dto: TransactionDto,
                        ): ConditionalRemoteWriteResult<TransactionDto> = ConditionalRemoteWriteResult.Applied(remoteDto)
                    }

                val executor =
                    V1OutboxOperationExecutor(
                        writer = writer,
                        remoteSyncDao = db.remoteSyncDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val op =
                    createOperationEntity(
                        operationId = "op-v1-2",
                        entityId = initial.id,
                        payloadJson = json.encodeToString(remoteDto),
                    )

                executor.execute(op)

                val persisted = db.localMutationDao().getTransactionRow(initial.id)
                assertNotNull(persisted)
                assertNull(persisted.merchantId)
                assertNull(persisted.merchantMatchSourceCode)
            } finally {
                db.close()
            }
        }

    // --- 3. IncrementalRemoteSync Tests ---

    @Test
    fun incrementalRemoteSync_knownDescription_attachesCandidateToDaoEntity() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)

                val spotifyDto =
                    sampleTransactionDto(
                        id = "11111111-1111-4111-8111-000000000031",
                        description = "Spotify Premium",
                        version = 1L,
                    )
                val fakeRemote = BaseFakeCoreRemoteDataSource(listOf(spotifyDto))

                val sync =
                    IncrementalRemoteSync(
                        remote = fakeRemote,
                        remoteSyncDao = db.remoteSyncDao(),
                        syncStateDao = db.syncStateDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                sync.pullFor(EntityId(userId), scope)

                val persisted = db.localMutationDao().getTransactionRow("11111111-1111-4111-8111-000000000031")
                assertNotNull(persisted)
                assertEquals("spotify", persisted.merchantId)
            } finally {
                db.close()
            }
        }

    @Test
    fun incrementalRemoteSync_unknownDescription_attachesNullCandidateToDaoEntity() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)

                val unknownDto =
                    sampleTransactionDto(
                        id = "11111111-1111-4111-8111-000000000032",
                        description = "Halis Kuruyemis",
                        version = 1L,
                    )
                val fakeRemote = BaseFakeCoreRemoteDataSource(listOf(unknownDto))

                val sync =
                    IncrementalRemoteSync(
                        remote = fakeRemote,
                        remoteSyncDao = db.remoteSyncDao(),
                        syncStateDao = db.syncStateDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                sync.pullFor(EntityId(userId), scope)

                val persisted = db.localMutationDao().getTransactionRow("11111111-1111-4111-8111-000000000032")
                assertNotNull(persisted)
                assertNull(persisted.merchantId)
            } finally {
                db.close()
            }
        }

    // --- 4. InitialRemoteSync Tests ---

    @Test
    fun initialRemoteSync_snapshotTransactions_attachesCandidate() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)

                val spotifyDto =
                    sampleTransactionDto(
                        id = "11111111-1111-4111-8111-000000000041",
                        description = "Spotify",
                        version = 1L,
                    )
                val unknownDto =
                    sampleTransactionDto(
                        id = "11111111-1111-4111-8111-000000000042",
                        description = "Bilinmeyen 456",
                        version = 1L,
                    )
                val fakeRemote = BaseFakeCoreRemoteDataSource(listOf(spotifyDto, unknownDto))

                val sync =
                    InitialRemoteSync(
                        remote = fakeRemote,
                        remoteSyncDao = db.remoteSyncDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                sync.pullFor(EntityId(userId), scope)

                val spotifyEntity = db.localMutationDao().getTransactionRow("11111111-1111-4111-8111-000000000041")
                assertNotNull(spotifyEntity)
                assertEquals("spotify", spotifyEntity.merchantId)

                val unknownEntity = db.localMutationDao().getTransactionRow("11111111-1111-4111-8111-000000000042")
                assertNotNull(unknownEntity)
                assertNull(unknownEntity.merchantId)
            } finally {
                db.close()
            }
        }

    // --- 5. OfflineFirstSyncRepository keep-remote Tests ---

    @Test
    fun offlineFirstSyncRepository_keepRemote_attachesCandidate() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)

                val txId = "11111111-1111-4111-8111-000000000051"
                val spotifyRemoteDto =
                    sampleTransactionDto(
                        id = txId,
                        description = "Spotify",
                        version = 2L,
                    )
                val localDto =
                    sampleTransactionDto(
                        id = txId,
                        description = "Eski Spotify",
                        version = 1L,
                    )
                val localEntity =
                    sampleTransactionEntity(
                        id = txId,
                        description = "Eski Spotify",
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = txId,
                        operationId = "op-kr-1",
                        localVersion = 1L,
                        remoteVersion = 2L,
                        localPayloadJson = json.encodeToString(localDto),
                        remotePayloadJson = json.encodeToString(spotifyRemoteDto),
                        detectedAtEpochMillis = nowEpoch,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val fakeRemote = BaseFakeCoreRemoteDataSource()
                val auth =
                    FakeAuthRepository(
                        AuthSession(
                            userId = EntityId(userId),
                            email = "test@feniqo.com",
                            expiresAt = Instant.fromEpochMilliseconds(nowEpoch + 100_000L),
                        ),
                    )
                val queue = FakeOutboxQueue()
                val offlineWriteQueue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val recoveryService =
                    ConflictRecoveryService(
                        syncStateDao = db.syncStateDao(),
                        syncOperationDao = db.syncOperationDao(),
                        localMutationDao = db.localMutationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val initialRemoteSync =
                    InitialRemoteSync(
                        remote = fakeRemote,
                        remoteSyncDao = db.remoteSyncDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val incrementalRemoteSync =
                    IncrementalRemoteSync(
                        remote = fakeRemote,
                        remoteSyncDao = db.remoteSyncDao(),
                        syncStateDao = db.syncStateDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )
                val dummyExecutor =
                    object : OutboxOperationExecutor {
                        override suspend fun execute(operation: SyncOperationEntity): OutboxExecutionResult = error("unused")
                    }
                val outboxProcessor =
                    OutboxProcessor(
                        queue = queue,
                        executor = dummyExecutor,
                    )

                val repo =
                    OfflineFirstSyncRepository(
                        authRepository = auth,
                        initialRemoteSync = initialRemoteSync,
                        workspaceInitialRemoteSync =
                            WorkspaceInitialRemoteSync(
                                remote = fakeRemote,
                                remoteSyncDao = db.remoteSyncDao(),
                                nowEpochMillisProvider = { nowEpoch },
                            ),
                        outboxProcessor = outboxProcessor,
                        incrementalRemoteSync = incrementalRemoteSync,
                        workspaceIncrementalRemoteSync =
                            WorkspaceIncrementalRemoteSync(
                                remote = fakeRemote,
                                remoteSyncDao = db.remoteSyncDao(),
                                syncStateDao = db.syncStateDao(),
                                nowEpochMillisProvider = { nowEpoch },
                            ),
                        offlineWriteQueue = offlineWriteQueue,
                        syncStateDao = db.syncStateDao(),
                        remoteSyncDao = db.remoteSyncDao(),
                        conflictRecoveryService = recoveryService,
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val result = repo.resolveConflict(EntityId(txId), ConflictResolution.KEEP_REMOTE)
                assertTrue(result is RepositoryResult.Success)

                val persisted = db.localMutationDao().getTransactionRow(txId)
                assertNotNull(persisted)
                assertEquals("spotify", persisted.merchantId)
            } finally {
                db.close()
            }
        }

    // --- 6. ConflictRecoveryService Tests ---

    @Test
    fun conflictRecoveryService_singleRecovery_passesResolvedCandidate() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val txId = "11111111-1111-4111-8111-000000000061"
                val initialTx =
                    sampleTransactionEntity(
                        id = txId,
                        description = "Eski Açıklama",
                        merchantId = null,
                    )
                val localDto = sampleTransactionDto(id = txId, description = "Spotify", version = 0L)
                val remoteDto = sampleTransactionDto(id = txId, description = "Spotify", version = 1L)

                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = initialTx,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = json.encodeToString(localDto),
                    )
                queue.claimOperation(scope, opId)

                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = txId,
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(localDto),
                        remotePayloadJson = json.encodeToString(remoteDto),
                        detectedAtEpochMillis = nowEpoch,
                    )
                queue.recordV2Conflict(scope, conflict)

                val service =
                    ConflictRecoveryService(
                        syncStateDao = db.syncStateDao(),
                        syncOperationDao = db.syncOperationDao(),
                        localMutationDao = db.localMutationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val recovered = service.recoverSingleEquivalentCreate(scope, "TRANSACTION", txId)
                assertTrue(recovered)

                val persisted = db.localMutationDao().getTransactionRow(txId)
                assertNotNull(persisted)
                assertEquals("spotify", persisted.merchantId)
                assertEquals("SYNCED", persisted.sync.syncStatus)
            } finally {
                db.close()
            }
        }

    @Test
    fun conflictRecoveryService_installmentGroupRecovery_passesResolvedCandidates() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val grpId = "grp-rec-1"
                val txId1 = "11111111-1111-4111-8111-000000000071"
                val txId2 = "11111111-1111-4111-8111-000000000072"
                val tx1 =
                    sampleTransactionEntity(
                        id = txId1,
                        description = "Eski Açıklama 1",
                        merchantId = null,
                        installmentNumber = 1,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )
                val tx2 =
                    sampleTransactionEntity(
                        id = txId2,
                        description = "Eski Açıklama 2",
                        merchantId = null,
                        installmentNumber = 2,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )

                val localDto1 =
                    sampleTransactionDto(
                        id = txId1,
                        description = "Spotify Premium",
                        version = 0L,
                        installmentNumber = 1,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )
                val localDto2 =
                    sampleTransactionDto(
                        id = txId2,
                        description = "Bilinmeyen Harcama",
                        version = 0L,
                        installmentNumber = 2,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )

                val remoteDto1 =
                    sampleTransactionDto(
                        id = txId1,
                        description = "Spotify Premium",
                        version = 1L,
                        installmentNumber = 1,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )
                val remoteDto2 =
                    sampleTransactionDto(
                        id = txId2,
                        description = "Bilinmeyen Harcama",
                        version = 1L,
                        installmentNumber = 2,
                        totalInstallments = 2,
                        installmentGroupId = grpId,
                    )

                val opId1 =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = tx1,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = json.encodeToString(localDto1),
                    )
                val opId2 =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = tx2,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = json.encodeToString(localDto2),
                    )

                queue.claimOperation(scope, opId1)
                queue.claimOperation(scope, opId2)

                val c1 =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = txId1,
                        operationId = opId1,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(localDto1),
                        remotePayloadJson = json.encodeToString(remoteDto1),
                        detectedAtEpochMillis = nowEpoch,
                    )
                val c2 =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = txId2,
                        operationId = opId2,
                        localVersion = 0L,
                        remoteVersion = 1L,
                        localPayloadJson = json.encodeToString(localDto2),
                        remotePayloadJson = json.encodeToString(remoteDto2),
                        detectedAtEpochMillis = nowEpoch,
                    )
                queue.recordV2Conflict(scope, c1)
                queue.recordV2Conflict(scope, c2)

                val service =
                    ConflictRecoveryService(
                        syncStateDao = db.syncStateDao(),
                        syncOperationDao = db.syncOperationDao(),
                        localMutationDao = db.localMutationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val recovered = service.recoverInstallmentGroup(scope, grpId)
                assertTrue(recovered)

                val p1 = db.localMutationDao().getTransactionRow(txId1)
                assertNotNull(p1)
                assertEquals("spotify", p1.merchantId)
                assertEquals("SYNCED", p1.sync.syncStatus)

                val p2 = db.localMutationDao().getTransactionRow(txId2)
                assertNotNull(p2)
                assertNull(p2.merchantId)
                assertEquals("SYNCED", p2.sync.syncStatus)
            } finally {
                db.close()
            }
        }

    // --- 7. Candidate-Aware Group ACK Tests ---

    @Test
    fun localMutationDao_ackTransactionGroupV2_preservesKnownAndClearsUnknown() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedBaseData(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { nowEpoch },
                    )

                val txId1 = "11111111-1111-4111-8111-000000000081"
                val txId2 = "11111111-1111-4111-8111-000000000082"
                val txSpotify =
                    sampleTransactionEntity(
                        id = txId1,
                        description = "Eski Açıklama 1",
                        merchantId = null,
                    )
                val txUnknown =
                    sampleTransactionEntity(
                        id = txId2,
                        description = "Eski Açıklama 2",
                        merchantId = null,
                    )

                val dtoSpotify = sampleTransactionDto(id = txId1, description = "Spotify", version = 2L)
                val dtoUnknown = sampleTransactionDto(id = txId2, description = "Bilinmeyen Harcama", version = 2L)

                val opIdSpotify =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = txSpotify,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.UPDATE,
                        payloadJson = json.encodeToString(dtoSpotify),
                    )
                val opIdUnknown =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = txUnknown,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.UPDATE,
                        payloadJson = json.encodeToString(dtoUnknown),
                    )

                queue.claimOperation(scope, opIdSpotify)
                queue.claimOperation(scope, opIdUnknown)

                val spotifyCandidate = TransactionMerchantAttributionResolver.resolve(dtoSpotify.description)
                val unknownCandidate = TransactionMerchantAttributionResolver.resolve(dtoUnknown.description)

                val units =
                    listOf(
                        Triple(opIdSpotify, dtoSpotify, spotifyCandidate),
                        Triple(opIdUnknown, dtoUnknown, unknownCandidate),
                    )

                db.localMutationDao().ackTransactionGroupV2(scope, units, nowEpoch)

                val pSpotify = db.localMutationDao().getTransactionRow(txId1)
                assertNotNull(pSpotify)
                assertEquals("spotify", pSpotify.merchantId)
                assertEquals("SYNCED", pSpotify.sync.syncStatus)

                val pUnknown = db.localMutationDao().getTransactionRow(txId2)
                assertNotNull(pUnknown)
                assertNull(pUnknown.merchantId)
                assertEquals("SYNCED", pUnknown.sync.syncStatus)
            } finally {
                db.close()
            }
        }
}
