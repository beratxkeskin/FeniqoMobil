package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.SyncScopeKey
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
import com.feniqo.mobile.domain.usecase.TransactionMerchantAttributionResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantSyncTest {
    private val userId = "11111111-1111-4111-8111-111111111111"
    private val scope = SyncScopeKey.user(userId).rawValue
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

    private fun sampleProfile(): UserProfileEntity =
        UserProfileEntity(
            id = userId,
            email = "test@feniqo.com",
            fullName = "Test User",
            currencyCode = "TRY",
            themeCode = "SYSTEM",
            languageCode = "TR",
            activeWorkspaceId = null,
            createdAtEpochMillis = 1_000L,
            sync = newSyncMetadata(1_000L, SyncStatus.SYNCED).copy(version = 1L, baseVersion = 1L),
        )

    private suspend fun seedUserAndCategory(db: FeniqoDatabase) {
        val profile = sampleProfile()
        db.profileDao().insertIfMissing(profile)

        val category =
            Category(
                id = EntityId("cat-1"),
                ownerId = EntityId(userId),
                workspaceId = null,
                name = "Market",
                type = TransactionType.EXPENSE,
                color = CategoryColor("#abcdef"),
                icon = null,
                isDefault = false,
                createdAt = nowInstant,
            )
        db.categoryDao().upsert(
            category.toEntity(
                newSyncMetadata(1_000L, SyncStatus.SYNCED).copy(version = 1L, baseVersion = 1L),
            ),
        )
    }

    private fun sampleTransactionEntity(
        id: String = "tx-1",
        description: String = "Netflix",
        merchantId: String? = "netflix",
        matchSource: MerchantMatchSource? = MerchantMatchSource.EXACT_ALIAS,
        confidence: Int? = 95,
        catalogVersion: Int? = 1,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        version: Long = 0L,
        amountMinor: Long = 15000L,
    ): TransactionEntity {
        val merchantInfo =
            if (merchantId != null && matchSource != null && confidence != null && catalogVersion != null) {
                TransactionMerchantInfo(
                    merchantId = EntityId(merchantId),
                    matchSource = matchSource,
                    confidence = confidence,
                    catalogVersion = catalogVersion,
                )
            } else {
                null
            }

        val domain =
            Transaction(
                id = EntityId(id),
                ownerId = EntityId(userId),
                workspaceId = null,
                amount = Money(amountMinor, Currency.TRY),
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
            newSyncMetadata(10_000L, syncStatus).copy(
                version = version,
                baseVersion = if (version > 0L) version else null,
            )
        return domain.toEntity(sync)
    }

    private fun candidateRemoteEntity(
        id: String,
        description: String,
        version: Long = 1L,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        amountMinor: Long = 15000L,
    ): TransactionEntity {
        val candidate = TransactionMerchantAttributionResolver.resolve(description)
        return sampleTransactionEntity(
            id = id,
            description = description,
            merchantId = candidate?.merchantId?.value,
            matchSource = candidate?.matchSource,
            confidence = candidate?.confidence,
            catalogVersion = candidate?.catalogVersion,
            syncStatus = syncStatus,
            version = version,
            amountMinor = amountMinor,
        )
    }

    @Test
    fun roomDao_persistenceAndFlow_emitsMerchantAttribution() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val dao = db.transactionDao()

                val entity =
                    sampleTransactionEntity(
                        id = "tx-pers-1",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                        syncStatus = SyncStatus.PENDING_CREATE,
                    )
                dao.upsert(entity)

                val readBack = dao.getByIdAndOwner("tx-pers-1", userId)
                assertNotNull(readBack)
                assertEquals("netflix", readBack.merchantId)
                assertEquals("EXACT_ALIAS", readBack.merchantMatchSourceCode)
                assertEquals(95, readBack.merchantConfidence)
                assertEquals(1, readBack.merchantCatalogVersion)

                val domain = readBack.toDomain()
                assertNotNull(domain.merchantInfo)
                assertEquals("netflix", domain.merchantInfo?.merchantId?.value)
                assertEquals(MerchantMatchSource.EXACT_ALIAS, domain.merchantInfo?.matchSource)
                assertEquals(95, domain.merchantInfo?.confidence)
                assertEquals(1, domain.merchantInfo?.catalogVersion)

                val flowEntity = dao.observeByIdAndOwner("tx-pers-1", userId).first()
                assertNotNull(flowEntity)
                assertEquals("netflix", flowEntity.merchantId)
            } finally {
                db.close()
            }
        }

    @Test
    fun ackTransactionWriteV2_withoutSuccessor_sameDescription_preservesLocalMerchantAttribution() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity =
                    sampleTransactionEntity(
                        id = "tx-ack-same",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    )
                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-ack-same\",\"amount_minor\":15000,\"currency\":\"TRY\"}",
                    )

                val claimed = queue.claimOperation(scope, opId)
                assertNotNull(claimed)
                assertEquals("IN_FLIGHT", claimed.statusCode)

                val remoteDto =
                    TransactionDto(
                        id = "tx-ack-same",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Netflix",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 1L,
                    )

                val ackResult =
                    db.localMutationDao().ackV2Execution(
                        syncScopeKey = scope,
                        operationId = opId,
                        result =
                            OutboxExecutionResult.TransactionApplied(
                                record = remoteDto,
                                candidateMerchant =
                                    TransactionMerchantAttributionResolver.resolve(
                                        remoteDto.description,
                                    ),
                            ),
                        nowEpochMillis = 20_000L,
                    )
                assertTrue(ackResult)

                val row = db.localMutationDao().getTransactionRow("tx-ack-same")
                assertNotNull(row)
                assertEquals(SyncStatus.SYNCED.name, row.sync.syncStatus)
                assertEquals(1L, row.sync.version)
                assertEquals("Netflix", row.description)
                assertEquals("netflix", row.merchantId)
                assertEquals("EXACT_ALIAS", row.merchantMatchSourceCode)
                assertEquals(95, row.merchantConfidence)
                assertEquals(1, row.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun ackTransactionWriteV2_withoutSuccessor_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity =
                    sampleTransactionEntity(
                        id = "tx-ack-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    )
                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-ack-spot\",\"amount_minor\":15000,\"currency\":\"TRY\"}",
                    )

                val claimed = queue.claimOperation(scope, opId)
                assertNotNull(claimed)

                val remoteDto =
                    TransactionDto(
                        id = "tx-ack-spot",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Spotify",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 1L,
                    )

                val ackResult =
                    db.localMutationDao().ackV2Execution(
                        syncScopeKey = scope,
                        operationId = opId,
                        result =
                            OutboxExecutionResult.TransactionApplied(
                                record = remoteDto,
                                candidateMerchant =
                                    TransactionMerchantAttributionResolver.resolve(
                                        remoteDto.description,
                                    ),
                            ),
                        nowEpochMillis = 20_000L,
                    )
                assertTrue(ackResult)

                val row = db.localMutationDao().getTransactionRow("tx-ack-spot")
                assertNotNull(row)
                assertEquals("Spotify", row.description)
                assertEquals("spotify", row.merchantId)
                assertEquals(95, row.merchantConfidence)
                assertEquals(10, row.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun ackTransactionWriteV2_withoutSuccessor_descriptionChangedToUnknown_clearsMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity =
                    sampleTransactionEntity(
                        id = "tx-ack-unk",
                        description = "Netflix",
                        merchantId = "netflix",
                    )
                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-ack-unk\"}",
                    )
                val claimed = queue.claimOperation(scope, opId)
                assertNotNull(claimed)

                val remoteDto =
                    TransactionDto(
                        id = "tx-ack-unk",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Bilinmeyen Kahveci 999",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 1L,
                    )

                db.localMutationDao().ackV2Execution(
                    syncScopeKey = scope,
                    operationId = opId,
                    result =
                        OutboxExecutionResult.TransactionApplied(
                            record = remoteDto,
                            candidateMerchant =
                                TransactionMerchantAttributionResolver.resolve(
                                    remoteDto.description,
                                ),
                        ),
                    nowEpochMillis = 20_000L,
                )

                val row = db.localMutationDao().getTransactionRow("tx-ack-unk")
                assertNotNull(row)
                assertEquals("Bilinmeyen Kahveci 999", row.description)
                assertNull(row.merchantId)
                assertNull(row.merchantMatchSourceCode)
                assertNull(row.merchantConfidence)
                assertNull(row.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun ackTransactionWriteV2_withSuccessor_preservesLocalMerchantAttributionAndUnblocksSuccessor() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity = sampleTransactionEntity("tx-ack-succ", "Netflix", merchantId = "netflix")
                val op1 =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-ack-succ\"}",
                    )
                val claimed1 = queue.claimOperation(scope, op1)
                assertNotNull(claimed1)

                val op2 =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity.copy(amountMinor = 20000L),
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.UPDATE,
                        payloadJson = "{\"id\":\"tx-ack-succ\",\"amount_minor\":20000}",
                    )

                val remoteDto =
                    TransactionDto(
                        id = "tx-ack-succ",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Netflix",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 1L,
                    )

                val ackResult =
                    db.localMutationDao().ackV2Execution(
                        syncScopeKey = scope,
                        operationId = op1,
                        result =
                            OutboxExecutionResult.TransactionApplied(
                                record = remoteDto,
                                candidateMerchant =
                                    TransactionMerchantAttributionResolver.resolve(
                                        remoteDto.description,
                                    ),
                            ),
                        nowEpochMillis = 20_000L,
                    )
                assertTrue(ackResult)

                val row = db.localMutationDao().getTransactionRow("tx-ack-succ")
                assertNotNull(row)
                assertEquals("netflix", row.merchantId)
                assertEquals(95, row.merchantConfidence)

                val succOp = db.syncOperationDao().getById(scope, op2)
                assertNotNull(succOp)
                assertEquals("PENDING", succOp.statusCode)
                assertEquals(op1, succOp.predecessorOperationId)
                assertFalse(succOp.isBlocked)
                assertEquals(1L, succOp.baseVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionPull_withExistingLocalRecord_sameDescription_preservesLocalMerchantAttribution() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-pull-same",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                        amountMinor = 15000L,
                    )
                db.transactionDao().upsert(localEntity)

                val remoteEntity =
                    localEntity.copy(
                        amountMinor = 25000L,
                        description = "Netflix",
                        merchantId = null,
                        merchantMatchSourceCode = null,
                        merchantConfidence = null,
                        merchantCatalogVersion = null,
                        sync = newSyncMetadata(30_000L, SyncStatus.SYNCED).copy(version = 2L, baseVersion = 2L),
                    )

                val cursor = SyncCursorEntity(scope, "TRANSACTION", 30_000L, "tx-pull-same")
                syncDao.applyTransactionPull(scope, remoteEntity, cursor)

                val updated = syncDao.getTransactionRow("tx-pull-same")
                assertNotNull(updated)
                assertEquals(25000L, updated.amountMinor)
                assertEquals("Netflix", updated.description)
                assertEquals(2L, updated.sync.version)

                assertEquals("netflix", updated.merchantId)
                assertEquals("EXACT_ALIAS", updated.merchantMatchSourceCode)
                assertEquals(95, updated.merchantConfidence)
                assertEquals(1, updated.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionPull_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-pull-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val remoteEntity =
                    candidateRemoteEntity(
                        id = "tx-pull-spot",
                        description = "Spotify",
                        version = 2L,
                    )

                val cursor = SyncCursorEntity(scope, "TRANSACTION", 30_000L, "tx-pull-spot")
                syncDao.applyTransactionPull(scope, remoteEntity, cursor)

                val updated = syncDao.getTransactionRow("tx-pull-spot")
                assertNotNull(updated)
                assertEquals("Spotify", updated.description)
                assertEquals("spotify", updated.merchantId)
                assertEquals(95, updated.merchantConfidence)
                assertEquals(10, updated.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionPull_descriptionChangedToUnknown_clearsMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-pull-unk",
                        description = "Netflix",
                        merchantId = "netflix",
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val remoteEntity =
                    candidateRemoteEntity(
                        id = "tx-pull-unk",
                        description = "Bilinmeyen Market 123",
                        version = 2L,
                    )

                val cursor = SyncCursorEntity(scope, "TRANSACTION", 30_000L, "tx-pull-unk")
                syncDao.applyTransactionPull(scope, remoteEntity, cursor)

                val updated = syncDao.getTransactionRow("tx-pull-unk")
                assertNotNull(updated)
                assertEquals("Bilinmeyen Market 123", updated.description)
                assertNull(updated.merchantId)
                assertNull(updated.merchantMatchSourceCode)
                assertNull(updated.merchantConfidence)
                assertNull(updated.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionPull_withNewRecord_leavesMerchantColumnsNullWhenUnknown() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val remoteNewEntity =
                    candidateRemoteEntity(
                        id = "tx-new-remote",
                        description = "Uzak Yeni Islem",
                        version = 1L,
                    )

                val cursor = SyncCursorEntity(scope, "TRANSACTION", 30_000L, "tx-new-remote")
                syncDao.applyTransactionPull(scope, remoteNewEntity, cursor)

                val inserted = syncDao.getTransactionRow("tx-new-remote")
                assertNotNull(inserted)
                assertEquals("tx-new-remote", inserted.id)
                assertNull(inserted.merchantId)
                assertNull(inserted.merchantMatchSourceCode)
                assertNull(inserted.merchantConfidence)
                assertNull(inserted.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionPull_withNewRecord_resolvesMerchantWhenKnown() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val remoteNewEntity =
                    candidateRemoteEntity(
                        id = "tx-new-spot",
                        description = "Spotify",
                        version = 1L,
                    )

                val cursor = SyncCursorEntity(scope, "TRANSACTION", 30_000L, "tx-new-spot")
                syncDao.applyTransactionPull(scope, remoteNewEntity, cursor)

                val inserted = syncDao.getTransactionRow("tx-new-spot")
                assertNotNull(inserted)
                assertEquals("tx-new-spot", inserted.id)
                assertEquals("spotify", inserted.merchantId)
                assertEquals(95, inserted.merchantConfidence)
                assertEquals(10, inserted.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun resolveTransactionKeepRemote_sameDescription_preservesLocalMerchantAttribution() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-conflict-same",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    )
                db.transactionDao().upsert(localEntity)

                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = "tx-conflict-same",
                        operationId = "op-same",
                        localVersion = 1L,
                        remoteVersion = 2L,
                        localPayloadJson = "{}",
                        remotePayloadJson = "{}",
                        detectedAtEpochMillis = 10_000L,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val remoteEntity =
                    localEntity.copy(
                        amountMinor = 30000L,
                        description = "Netflix",
                        merchantId = null,
                        merchantMatchSourceCode = null,
                        merchantConfidence = null,
                        merchantCatalogVersion = null,
                        sync = newSyncMetadata(40_000L, SyncStatus.SYNCED).copy(version = 2L, baseVersion = 2L),
                    )

                syncDao.resolveTransactionKeepRemote(scope, remoteEntity)

                val resolved = syncDao.getTransactionRow("tx-conflict-same")
                assertNotNull(resolved)
                assertEquals(30000L, resolved.amountMinor)
                assertEquals("Netflix", resolved.description)
                assertEquals("netflix", resolved.merchantId)
                assertEquals(95, resolved.merchantConfidence)
                assertEquals(1, resolved.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun resolveTransactionKeepRemote_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-conflict-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                    )
                db.transactionDao().upsert(localEntity)

                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = "tx-conflict-spot",
                        operationId = "op-spot",
                        localVersion = 1L,
                        remoteVersion = 2L,
                        localPayloadJson = "{}",
                        remotePayloadJson = "{}",
                        detectedAtEpochMillis = 10_000L,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val remoteEntity =
                    candidateRemoteEntity(
                        id = "tx-conflict-spot",
                        description = "Spotify",
                        version = 2L,
                    )

                syncDao.resolveTransactionKeepRemote(scope, remoteEntity)

                val resolved = syncDao.getTransactionRow("tx-conflict-spot")
                assertNotNull(resolved)
                assertEquals("Spotify", resolved.description)
                assertEquals("spotify", resolved.merchantId)
                assertEquals(95, resolved.merchantConfidence)
                assertEquals(10, resolved.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun resolveTransactionKeepRemote_descriptionChangedToUnknown_clearsMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-conflict-unk",
                        description = "Netflix",
                        merchantId = "netflix",
                    )
                db.transactionDao().upsert(localEntity)

                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = "tx-conflict-unk",
                        operationId = "op-unk",
                        localVersion = 1L,
                        remoteVersion = 2L,
                        localPayloadJson = "{}",
                        remotePayloadJson = "{}",
                        detectedAtEpochMillis = 10_000L,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val remoteEntity =
                    candidateRemoteEntity(
                        id = "tx-conflict-unk",
                        description = "Bilinmeyen Restoran 456",
                        version = 2L,
                    )

                syncDao.resolveTransactionKeepRemote(scope, remoteEntity)

                val resolved = syncDao.getTransactionRow("tx-conflict-unk")
                assertNotNull(resolved)
                assertEquals("Bilinmeyen Restoran 456", resolved.description)
                assertNull(resolved.merchantId)
                assertNull(resolved.merchantMatchSourceCode)
                assertNull(resolved.merchantConfidence)
                assertNull(resolved.merchantCatalogVersion)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyInitialSnapshot_existingSyncedRecord_sameDescription_preservesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-snap-same",
                        description = "Netflix",
                        merchantId = "netflix",
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val snapshotEntity =
                    localEntity.copy(
                        amountMinor = 40000L,
                        merchantId = null,
                        merchantMatchSourceCode = null,
                        merchantConfidence = null,
                        merchantCatalogVersion = null,
                    )

                syncDao.applyInitialSnapshot(
                    syncScopeKey = scope,
                    profile = sampleProfile(),
                    categories = emptyList(),
                    transactions = listOf(snapshotEntity),
                    cursors = emptyList(),
                    recurringTransactions = emptyList(),
                    subscriptions = emptyList(),
                    goals = emptyList(),
                    goalContributions = emptyList(),
                    debts = emptyList(),
                    debtPayments = emptyList(),
                )

                val row = syncDao.getTransactionRow("tx-snap-same")
                assertNotNull(row)
                assertEquals(40000L, row.amountMinor)
                assertEquals("netflix", row.merchantId)
                assertEquals(95, row.merchantConfidence)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyInitialSnapshot_existingSyncedRecord_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-snap-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val snapshotEntity =
                    candidateRemoteEntity(
                        id = "tx-snap-spot",
                        description = "Spotify",
                        amountMinor = 40000L,
                        version = 2L,
                    )

                syncDao.applyInitialSnapshot(
                    syncScopeKey = scope,
                    profile = sampleProfile(),
                    categories = emptyList(),
                    transactions = listOf(snapshotEntity),
                    cursors = emptyList(),
                    recurringTransactions = emptyList(),
                    subscriptions = emptyList(),
                    goals = emptyList(),
                    goalContributions = emptyList(),
                    debts = emptyList(),
                    debtPayments = emptyList(),
                )

                val row = syncDao.getTransactionRow("tx-snap-spot")
                assertNotNull(row)
                assertEquals("Spotify", row.description)
                assertEquals("spotify", row.merchantId)
                assertEquals(95, row.merchantConfidence)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyInitialSnapshot_existingSyncedRecord_descriptionChangedToUnknown_clearsMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-snap-unk",
                        description = "Netflix",
                        merchantId = "netflix",
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val snapshotEntity =
                    candidateRemoteEntity(
                        id = "tx-snap-unk",
                        description = "Bilinmeyen Snap Islem",
                        version = 2L,
                    )

                syncDao.applyInitialSnapshot(
                    syncScopeKey = scope,
                    profile = sampleProfile(),
                    categories = emptyList(),
                    transactions = listOf(snapshotEntity),
                    cursors = emptyList(),
                    recurringTransactions = emptyList(),
                    subscriptions = emptyList(),
                    goals = emptyList(),
                    goalContributions = emptyList(),
                    debts = emptyList(),
                    debtPayments = emptyList(),
                )

                val row = syncDao.getTransactionRow("tx-snap-unk")
                assertNotNull(row)
                assertEquals("Bilinmeyen Snap Islem", row.description)
                assertNull(row.merchantId)
                assertNull(row.merchantConfidence)
            } finally {
                db.close()
            }
        }

    @Test
    fun applyTransactionWrite_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val syncDao = db.remoteSyncDao()

                val localEntity =
                    sampleTransactionEntity(
                        id = "tx-write-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                        syncStatus = SyncStatus.SYNCED,
                        version = 1L,
                    )
                db.transactionDao().upsert(localEntity)

                val writeEntity =
                    candidateRemoteEntity(
                        id = "tx-write-spot",
                        description = "Spotify",
                        version = 2L,
                    )

                syncDao.applyTransactionWrite(scope, writeEntity)

                val updated = syncDao.getTransactionRow("tx-write-spot")
                assertNotNull(updated)
                assertEquals("Spotify", updated.description)
                assertEquals("spotify", updated.merchantId)
                assertEquals(95, updated.merchantConfidence)
            } finally {
                db.close()
            }
        }

    @Test
    fun recoverEquivalentTransaction_sameDescription_preservesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity =
                    sampleTransactionEntity(
                        id = "tx-eq-same",
                        description = "Netflix",
                        merchantId = "netflix",
                    )
                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-eq-same\"}",
                    )
                queue.claimOperation(scope, opId)
                db.syncOperationDao().markConflict(scope, opId, "409 Conflict", 15_000L)
                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = "tx-eq-same",
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 2L,
                        localPayloadJson = "{}",
                        remotePayloadJson = "{}",
                        detectedAtEpochMillis = 15_000L,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val remoteDto =
                    TransactionDto(
                        id = "tx-eq-same",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Netflix",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 2L,
                    )

                val candidate = TransactionMerchantAttributionResolver.resolve(remoteDto.description)
                db.localMutationDao().recoverEquivalentTransaction(
                    syncScopeKey = scope,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 20_000L,
                    candidateMerchant = candidate,
                )

                val row = db.localMutationDao().getTransactionRow("tx-eq-same")
                assertNotNull(row)
                assertEquals("netflix", row.merchantId)
                assertEquals(95, row.merchantConfidence)
            } finally {
                db.close()
            }
        }

    @Test
    fun recoverEquivalentTransaction_descriptionChangedToSpotify_reResolvesMerchant() =
        runTest {
            val db = inMemoryDatabase()
            try {
                seedUserAndCategory(db)
                val queue =
                    OfflineWriteQueue(
                        mutationDao = db.localMutationDao(),
                        operationDao = db.syncOperationDao(),
                        nowEpochMillisProvider = { 10_000L },
                    )

                val entity =
                    sampleTransactionEntity(
                        id = "tx-eq-spot",
                        description = "Netflix",
                        merchantId = "netflix",
                    )
                val opId =
                    queue.enqueueTransactionV2(
                        syncScopeKey = scope,
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        type = OutboxOperationType.CREATE,
                        payloadJson = "{\"id\":\"tx-eq-spot\"}",
                    )
                queue.claimOperation(scope, opId)
                db.syncOperationDao().markConflict(scope, opId, "409 Conflict", 15_000L)
                val conflict =
                    SyncConflictEntity(
                        syncScopeKey = scope,
                        entityTypeCode = "TRANSACTION",
                        entityId = "tx-eq-spot",
                        operationId = opId,
                        localVersion = 0L,
                        remoteVersion = 2L,
                        localPayloadJson = "{}",
                        remotePayloadJson = "{}",
                        detectedAtEpochMillis = 15_000L,
                    )
                db.syncStateDao().upsertConflict(conflict)

                val remoteDto =
                    TransactionDto(
                        id = "tx-eq-spot",
                        userId = userId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "expense",
                        categoryId = "cat-1",
                        description = "Spotify",
                        paymentMethod = "credit_card",
                        transactionDate = "2026-08-21",
                        receiptPath = null,
                        createdAt = "2026-08-21T10:00:00Z",
                        updatedAt = "2026-08-21T10:00:01Z",
                        version = 2L,
                    )

                val candidate = TransactionMerchantAttributionResolver.resolve(remoteDto.description)
                db.localMutationDao().recoverEquivalentTransaction(
                    syncScopeKey = scope,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 20_000L,
                    candidateMerchant = candidate,
                )

                val row = db.localMutationDao().getTransactionRow("tx-eq-spot")
                assertNotNull(row)
                assertEquals("Spotify", row.description)
                assertEquals("spotify", row.merchantId)
                assertEquals(95, row.merchantConfidence)
            } finally {
                db.close()
            }
        }
}
