package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.repository.ActiveWorkspaceScope
import com.feniqo.mobile.data.repository.OfflineFirstTransactionRepository
import com.feniqo.mobile.data.repository.PersonalActiveWorkspaceScope
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantCorrectionRejectionReason
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantCorrection
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import com.feniqo.mobile.domain.usecase.MerchantRecognitionEngine
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantCorrectionDaoTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var queue: OfflineWriteQueue
    private lateinit var repository: OfflineFirstTransactionRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeVerificationRepo: FakeVerificationRepository

    private val userA = "11111111-1111-4111-8111-111111111111"
    private val syncScopeA = SyncScopeKey.forUser(userA)
    private val entityScopeA = "user:$userA"

    private val userB = "22222222-2222-4222-8222-222222222222"
    private val syncScopeB = SyncScopeKey.forUser(userB)

    private val categoryId = "cat-market-1"
    private var opIndex = 0

    @BeforeTest
    fun setup() {
        db = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()

        queue = OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
            nowEpochMillisProvider = { 1000L },
            operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
        )

        fakeAuthRepo = FakeAuthRepository(userA)
        fakeVerificationRepo = FakeVerificationRepository()

        repository = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepo,
            transactionDao = db.transactionDao(),
            offlineWriteQueue = queue,
            workspaceDao = db.workspaceDao(),
            userMerchantVerificationRepository = fakeVerificationRepo,
            activeWorkspaceScope = PersonalActiveWorkspaceScope,
            nowEpochMillisProvider = { 1000L },
            merchantRecognitionEngine = MerchantRecognitionEngine(),
        )

        kotlinx.coroutines.runBlocking {
            db.categoryDao().upsert(
                CategoryEntity(
                    id = categoryId,
                    ownerId = userA,
                    workspaceId = null,
                    scopeKey = entityScopeA,
                    name = "Market",
                    normalizedName = "MARKET",
                    slug = "market-$userA",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "shopping-cart",
                    isDefault = true,
                    createdAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = SyncStatus.SYNCED.name,
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun getAllOperations(syncScopeKey: String): List<SyncOperationEntity> {
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT * FROM sync_operations WHERE sync_scope_key = ? ORDER BY created_at_epoch_ms, operation_id",
            arrayOf(syncScopeKey),
        )
        val list = mutableListOf<SyncOperationEntity>()
        cursor.use { c ->
            val scopeIdx = c.getColumnIndexOrThrow("sync_scope_key")
            val idIdx = c.getColumnIndexOrThrow("operation_id")
            val entityTypeIdx = c.getColumnIndexOrThrow("entity_type_code")
            val entityIdIdx = c.getColumnIndexOrThrow("entity_id")
            val opTypeIdx = c.getColumnIndexOrThrow("operation_type_code")
            val baseVerIdx = c.getColumnIndexOrThrow("base_version")
            val payloadIdx = c.getColumnIndexOrThrow("payload_json")
            val predIdx = c.getColumnIndexOrThrow("predecessor_operation_id")
            val isBlockedIdx = c.getColumnIndexOrThrow("is_blocked")
            val protoIdx = c.getColumnIndexOrThrow("protocol_version")
            val statusIdx = c.getColumnIndexOrThrow("status_code")
            val attemptIdx = c.getColumnIndexOrThrow("attempt_count")
            val errIdx = c.getColumnIndexOrThrow("last_error")
            val nextAttemptIdx = c.getColumnIndexOrThrow("next_attempt_at_epoch_ms")
            val createdIdx = c.getColumnIndexOrThrow("created_at_epoch_ms")
            val updatedIdx = c.getColumnIndexOrThrow("updated_at_epoch_ms")
            val errClassIdx = c.getColumnIndexOrThrow("error_classification")

            while (c.moveToNext()) {
                list.add(
                    SyncOperationEntity(
                        operationId = c.getString(idIdx),
                        syncScopeKey = c.getString(scopeIdx),
                        entityTypeCode = c.getString(entityTypeIdx),
                        entityId = c.getString(entityIdIdx),
                        operationTypeCode = c.getString(opTypeIdx),
                        baseVersion = if (c.isNull(baseVerIdx)) null else c.getLong(baseVerIdx),
                        payloadJson = if (c.isNull(payloadIdx)) null else c.getString(payloadIdx),
                        predecessorOperationId = if (c.isNull(predIdx)) null else c.getString(predIdx),
                        isBlocked = c.getInt(isBlockedIdx) != 0,
                        protocolVersion = c.getInt(protoIdx),
                        statusCode = c.getString(statusIdx),
                        attemptCount = c.getInt(attemptIdx),
                        lastError = if (c.isNull(errIdx)) null else c.getString(errIdx),
                        nextAttemptAtEpochMillis = c.getLong(nextAttemptIdx),
                        createdAtEpochMillis = c.getLong(createdIdx),
                        updatedAtEpochMillis = c.getLong(updatedIdx),
                        errorClassification = if (c.isNull(errClassIdx)) null else c.getString(errClassIdx),
                    ),
                )
            }
        }
        return list
    }

    data class DatabaseStateSnapshot(
        val transaction: TransactionEntity?,
        val verification: UserMerchantVerificationEntity?,
        val verificationOutbox: List<SyncOperationEntity>,
        val transactionOutbox: List<SyncOperationEntity>,
        val conflicts: List<SyncConflictEntity>,
        val cursor: com.feniqo.mobile.data.local.entity.SyncCursorEntity?,
    )

    private suspend fun captureSnapshot(transactionId: String, verificationAlias: String?): DatabaseStateSnapshot {
        val trx = db.localMutationDao().getTransactionRow(transactionId)
        val ver = verificationAlias?.let { db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, it) }
        val allOps = getAllOperations(syncScopeA)
        val verOps = allOps.filter { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }.sortedBy { it.operationId }
        val trxOps = allOps.filter { it.entityTypeCode == "TRANSACTION" }.sortedBy { it.operationId }
        val conflicts = listOfNotNull(
            ver?.let { db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", it.id) },
        )
        val cursor = db.syncStateDao().getCursor(syncScopeA, "USER_MERCHANT_VERIFICATION")
        return DatabaseStateSnapshot(trx, ver, verOps, trxOps, conflicts, cursor)
    }

    private fun sampleTransaction(
        id: String,
        ownerId: String = userA,
        workspaceId: String? = null,
        description: String? = "Migros Market",
        deletedAt: Long? = null,
        merchantId: String? = null,
        merchantMatchSource: String? = null,
        merchantConfidence: Int? = null,
        merchantCatalogVersion: Int? = null,
    ) = TransactionEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = workspaceId,
        paidByUserId = ownerId,
        participantUserIdsJson = "[\"$ownerId\"]",
        amountMinor = 15000L,
        currencyCode = "TRY",
        typeCode = "EXPENSE",
        categoryId = categoryId,
        description = description,
        searchText = description?.lowercase() ?: "",
        paymentMethodCode = "CREDIT_CARD",
        transactionDate = "2026-10-18T10:00:00Z",
        receiptPath = null,
        installmentNumber = 1,
        totalInstallments = 1,
        installmentGroupId = null,
        createdAtEpochMillis = 1000L,
        note = "Haftalik alisveris",
        sync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = deletedAt,
            version = 5L,
            baseVersion = 5L,
            lastSyncError = null,
        ),
        splitMode = "EQUAL",
        participantSharesJson = "[]",
        merchantId = merchantId,
        merchantMatchSourceCode = merchantMatchSource,
        merchantConfidence = merchantConfidence,
        merchantCatalogVersion = merchantCatalogVersion,
    )

    private fun sampleEntity(
        id: String,
        userId: String,
        alias: String,
        decisionCode: String,
        merchantId: String?,
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAt: Long? = null,
    ): UserMerchantVerificationEntity =
        UserMerchantVerificationEntity(
            id = id,
            ownerId = userId,
            workspaceId = null,
            normalizedAlias = alias,
            decisionCode = decisionCode,
            merchantId = merchantId,
            scopeCode = "PERSONAL",
            scopeKey = "user:$userId",
            createdAtEpochMillis = 1000L,
            sync =
                SyncMetadata(
                    syncStatus = syncStatus,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = deletedAt,
                    version = version,
                    baseVersion = baseVersion,
                    lastSyncError = null,
                ),
        )

    // 1. Kişisel ASSIGN
    @Test
    fun assign_persistsVerificationEntity_enqueuesOutbox_updatesTransactionAttribution_preservesSyncMetadata() = runTest {
        val trx = sampleTransaction(id = "trx-assign-1", description = "Netflix Kampanya")
        db.transactionDao().upsert(trx)

        val selectedMerchant = StarterMerchantCatalog.merchants.first { it.id == "netflix" }
        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(
                transactionId = EntityId(trx.id),
                merchantId = EntityId(selectedMerchant.id),
            ),
        )
        assertIs<RepositoryResult.Success<Unit>>(result)

        val normalizedAlias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val expectedVerId = UuidHelper.deterministicMerchantVerificationUuid(userA, normalizedAlias)
        val verEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, normalizedAlias)
        assertNotNull(verEntity)
        assertEquals(expectedVerId, verEntity.id)
        assertEquals("ASSIGN", verEntity.decisionCode)
        assertEquals(selectedMerchant.id, verEntity.merchantId)
        assertEquals(SyncStatus.PENDING_CREATE.name, verEntity.sync.syncStatus)

        val outboxList = getAllOperations(syncScopeA)
        assertEquals(1, outboxList.size)
        val op = outboxList.first()
        assertEquals("USER_MERCHANT_VERIFICATION", op.entityTypeCode)
        assertEquals(expectedVerId, op.entityId)
        assertEquals("CREATE", op.operationTypeCode)

        val updatedTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertEquals(selectedMerchant.id, updatedTrx.merchantId)
        assertEquals("USER_VERIFIED", updatedTrx.merchantMatchSourceCode)
        assertEquals(100, updatedTrx.merchantConfidence)
        assertEquals(selectedMerchant.catalogVersion, updatedTrx.merchantCatalogVersion)

        assertEquals(trx.sync.syncStatus, updatedTrx.sync.syncStatus)
        assertEquals(trx.sync.version, updatedTrx.sync.version)
        assertEquals(trx.sync.baseVersion, updatedTrx.sync.baseVersion)
        assertEquals(trx.sync.updatedAtEpochMillis, updatedTrx.sync.updatedAtEpochMillis)
        assertEquals(trx.sync.localUpdatedAtEpochMillis, updatedTrx.sync.localUpdatedAtEpochMillis)

        assertTrue(outboxList.none { it.entityTypeCode == "TRANSACTION" })
    }

    // 2. Kişisel BLOCK
    @Test
    fun block_persistsBlockVerification_clearsTransactionMerchantFields_preservesOtherFields() = runTest {
        val trx = sampleTransaction(
            id = "trx-block-1",
            description = "Spotify Premium",
            merchantId = "spotify",
            merchantMatchSource = "STRONG_BRAND",
            merchantConfidence = 95,
            merchantCatalogVersion = 1,
        )
        db.transactionDao().upsert(trx)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Block(transactionId = EntityId(trx.id)),
        )
        assertIs<RepositoryResult.Success<Unit>>(result)

        val normalizedAlias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, normalizedAlias)
        assertNotNull(verEntity)
        assertEquals("BLOCK", verEntity.decisionCode)
        assertNull(verEntity.merchantId)

        val updatedTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertNull(updatedTrx.merchantId)
        assertNull(updatedTrx.merchantMatchSourceCode)
        assertNull(updatedTrx.merchantConfidence)
        assertNull(updatedTrx.merchantCatalogVersion)

        assertEquals(trx.amountMinor, updatedTrx.amountMinor)
        assertEquals(trx.currencyCode, updatedTrx.currencyCode)
        assertEquals(trx.typeCode, updatedTrx.typeCode)
        assertEquals(trx.categoryId, updatedTrx.categoryId)
        assertEquals(trx.description, updatedTrx.description)
        assertEquals(trx.paymentMethodCode, updatedTrx.paymentMethodCode)
        assertEquals(trx.transactionDate, updatedTrx.transactionDate)
        assertEquals(trx.receiptPath, updatedTrx.receiptPath)
        assertEquals(trx.installmentNumber, updatedTrx.installmentNumber)
        assertEquals(trx.totalInstallments, updatedTrx.totalInstallments)
        assertEquals(trx.installmentGroupId, updatedTrx.installmentGroupId)
        assertEquals(trx.note, updatedTrx.note)
        assertEquals(trx.splitMode, updatedTrx.splitMode)
        assertEquals(trx.participantSharesJson, updatedTrx.participantSharesJson)
    }

    // 3. Unknown merchant: sıfır yazım ve tam snapshot eşitliği
    @Test
    fun unknownMerchant_rejectsAndPerformsZeroWrites() = runTest {
        val trx = sampleTransaction(id = "trx-unknown-1", description = "Steam Games")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(
                transactionId = EntityId(trx.id),
                merchantId = EntityId("non_existent_merchant_id_xyz"),
            ),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT.code, failure.error.code)

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter, "Unknown merchant durumunda veritabanı snapshot byte-for-byte korunmalıdır!")
    }

    // 4. Null / blank description: sıfır yazım
    @Test
    fun nullOrBlankDescription_rejectsAndPerformsZeroWrites() = runTest {
        val trxBlank = sampleTransaction(id = "trx-blank-desc", description = "   ")
        db.transactionDao().upsert(trxBlank)

        val snapshotBefore = captureSnapshot(trxBlank.id, null)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Block(transactionId = EntityId(trxBlank.id)),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(MerchantCorrectionRejectionReason.INVALID_DESCRIPTION.code, failure.error.code)

        val snapshotAfter = captureSnapshot(trxBlank.id, null)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 5. Gerçek generic tokenların her biri: fail-closed ve sıfır yazım
    @Test
    fun disallowedGenericTokens_failClosedAndPerformZeroWrites() = runTest {
        assertEquals(
            setOf("MARKET", "PREMIUM", "APPLE", "SHOP", "STORE", "YEMEK"),
            DISALLOWED_STANDALONE_GENERIC_TOKENS,
        )

        for (token in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            val trx = sampleTransaction(id = "trx-generic-$token", description = token)
            db.transactionDao().upsert(trx)

            val snapshotBefore = captureSnapshot(trx.id, token)

            val assignResult = repository.correctMerchant(
                TransactionMerchantCorrection.Assign(
                    transactionId = EntityId(trx.id),
                    merchantId = EntityId("migros"),
                ),
            )
            val failure = assertIs<RepositoryResult.Failure>(assignResult)
            assertEquals(MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED.code, failure.error.code)

            val snapshotAfter = captureSnapshot(trx.id, token)
            assertEquals(snapshotBefore, snapshotAfter, "Generic token için sıfır yazım snapshot kanıtı: $token")
        }
    }

    // 6. Alias uzunluk sınırı: fail-closed ve sıfır yazım
    @Test
    fun aliasTooLong_rejectsAndPerformsZeroWrites() = runTest {
        val longDesc = "A".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1)
        val trx = sampleTransaction(id = "trx-long-alias", description = longDesc)
        db.transactionDao().upsert(trx)

        val snapshotBefore = captureSnapshot(trx.id, null)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Block(transactionId = EntityId(trx.id)),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(MerchantCorrectionRejectionReason.ALIAS_TOO_LONG.code, failure.error.code)

        val snapshotAfter = captureSnapshot(trx.id, null)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 7. Workspace transaction: WORKSPACE_RESTRICTED ve sıfır yazım
    @Test
    fun workspaceTransaction_returnsWorkspaceRestrictedAndPerformsZeroWrites() = runTest {
        db.workspaceDao().upsertWorkspace(
            WorkspaceEntity(
                id = "ws-family-1",
                name = "Family",
                normalizedName = "family",
                ownerId = userA,
                typeCode = "family",
                currencyCode = "TRY",
                description = null,
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = 1L,
                    lastSyncError = null,
                ),
            ),
        )
        val trx = sampleTransaction(id = "trx-ws-1", workspaceId = "ws-family-1", description = "Netflix Aile")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(
                transactionId = EntityId(trx.id),
                merchantId = EntityId("netflix"),
            ),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(MerchantCorrectionRejectionReason.WORKSPACE_RESTRICTED.code, failure.error.code)

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 8. Excluded transaction: typed unsupported sonucu ve sıfır yazım
    @Test
    fun excludedTransaction_rejectsAndPerformsZeroWrites() = runTest {
        val excludedDescriptions = listOf(
            "MAAS ODEMESI EKIM",
            "KREDI KARTI ODEMESI",
            "ATM PARA CEKME NAKIT",
            "FAST HAVALE GONDERIMI",
            "HESAPLAR ARASI VIRMAN",
            "FON ALIM YATIRIM",
        )

        for ((idx, desc) in excludedDescriptions.withIndex()) {
            val trx = sampleTransaction(id = "trx-excluded-$idx", description = desc)
            db.transactionDao().upsert(trx)

            val alias = TurkishDescriptionNormalizer.normalize(desc)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            val result = repository.correctMerchant(
                TransactionMerchantCorrection.Block(transactionId = EntityId(trx.id)),
            )
            val failure = assertIs<RepositoryResult.Failure>(result)
            assertEquals(MerchantCorrectionRejectionReason.EXCLUDED_TRANSACTION.code, failure.error.code)

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter, "Excluded transaction sıfır yazım snapshot kanıtı: $desc")
        }
    }

    // 9. Owner / scope mismatch: sıfır yazım
    @Test
    fun ownerOrScopeMismatch_rejectsAndPerformsZeroWrites() = runTest {
        val trx = sampleTransaction(id = "trx-owner-b", ownerId = userB, description = "Amazon Prime")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Block(transactionId = EntityId(trx.id)),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(MerchantCorrectionRejectionReason.TRANSACTION_NOT_FOUND.code, failure.error.code)

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-mismatch" },
                nowEpochMillis = 1000L,
            )
        }
    }

    // 10. Tombstone transaction: sıfır yazım
    @Test
    fun tombstoneTransaction_rejectsAndPerformsZeroWrites() = runTest {
        val trx = sampleTransaction(id = "trx-deleted-1", deletedAt = 2000L, description = "Migros Ticaret")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(
                transactionId = EntityId(trx.id),
                merchantId = EntityId("migros"),
            ),
        )
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertTrue(
            failure.error.code == MerchantCorrectionRejectionReason.TRANSACTION_DELETED.code ||
                failure.error.code == MerchantCorrectionRejectionReason.TRANSACTION_NOT_FOUND.code,
        )

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 11. Verification PENDING_CREATE coalescing
    @Test
    fun verificationPendingCreate_coalescesWithoutCreatingNewOutbox() = runTest {
        val trx = sampleTransaction(id = "trx-coalesce-1", description = "Youtube Premium")
        db.transactionDao().upsert(trx)

        val assignResult = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(
                transactionId = EntityId(trx.id),
                merchantId = EntityId("youtube_premium"),
            ),
        )
        assertIs<RepositoryResult.Success<Unit>>(assignResult)

        val outboxAfterFirst = getAllOperations(syncScopeA)
        assertEquals(1, outboxAfterFirst.size)
        val firstOpId = outboxAfterFirst.first().operationId

        val blockResult = repository.correctMerchant(
            TransactionMerchantCorrection.Block(transactionId = EntityId(trx.id)),
        )
        assertIs<RepositoryResult.Success<Unit>>(blockResult)

        val outboxAfterSecond = getAllOperations(syncScopeA)
        assertEquals(1, outboxAfterSecond.size)
        assertEquals(firstOpId, outboxAfterSecond.first().operationId)

        val payload = UserMerchantVerificationPayloadCodec.parseAndValidate(
            jsonString = outboxAfterSecond.first().payloadJson!!,
            operation = "CREATE",
            baseVersion = null,
        )
        assertEquals("BLOCK", payload.decisionCode)
        assertNull(payload.merchantId)
    }

    // 12. Verification PENDING_UPDATE coalescing
    @Test
    fun verificationPendingUpdate_coalescesPayload() = runTest {
        val alias = "GETIR YEMEK"
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
        val existingVer = UserMerchantVerificationEntity(
            id = verId,
            ownerId = userA,
            workspaceId = null,
            scopeKey = entityScopeA,
            normalizedAlias = alias,
            decisionCode = "BLOCK",
            merchantId = null,
            scopeCode = "PERSONAL",
            createdAtEpochMillis = 500L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 500L,
                localUpdatedAtEpochMillis = 500L,
                deletedAtEpochMillis = null,
                version = 3L,
                baseVersion = 3L,
                lastSyncError = null,
            ),
        )
        db.localMutationDao().upsertUserMerchantVerificationRow(existingVer)

        val trx = sampleTransaction(id = "trx-update-coalesce", description = alias)
        db.transactionDao().upsert(trx)

        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("getir")),
        )
        val ops1 = getAllOperations(syncScopeA)
        assertEquals(1, ops1.size)
        val opId = ops1.first().operationId
        assertEquals("UPDATE", ops1.first().operationTypeCode)

        repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        val ops2 = getAllOperations(syncScopeA)
        assertEquals(1, ops2.size)
        assertEquals(opId, ops2.first().operationId)

        val payload = UserMerchantVerificationPayloadCodec.parseAndValidate(
            jsonString = ops2.first().payloadJson!!,
            operation = "UPDATE",
            baseVersion = 3L,
        )
        assertEquals("BLOCK", payload.decisionCode)
    }

    // 13. Verification IN_FLIGHT predecessor + blocked successor
    @Test
    fun verificationInFlight_createsBlockedSuccessor() = runTest {
        val alias = "TRENDYOL HIZLI"
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
        val existingVer = UserMerchantVerificationEntity(
            id = verId,
            ownerId = userA,
            workspaceId = null,
            scopeKey = entityScopeA,
            normalizedAlias = alias,
            decisionCode = "BLOCK",
            merchantId = null,
            scopeCode = "PERSONAL",
            createdAtEpochMillis = 500L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 500L,
                localUpdatedAtEpochMillis = 500L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )
        db.localMutationDao().upsertUserMerchantVerificationRow(existingVer)

        val trx = sampleTransaction(id = "trx-inflight-1", description = alias)
        db.transactionDao().upsert(trx)

        val firstResult = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("trendyol")),
        )
        assertIs<RepositoryResult.Success<Unit>>(firstResult)
        val firstOp = getAllOperations(syncScopeA).first()

        val claimed = queue.claimOperation(syncScopeA, firstOp.operationId)
        assertNotNull(claimed, "firstOp claim edilmeli")

        val secondResult = repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        assertIs<RepositoryResult.Success<Unit>>(secondResult)

        val ops = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        assertEquals(2, ops.size)
        val successor = ops.last()
        assertEquals(firstOp.operationId, successor.predecessorOperationId)
        assertTrue(successor.isBlocked)
        assertEquals("PENDING", successor.statusCode)
    }

    // 14. Negatif DAO Topoloji: Multiple active tail tam rollback
    @Test
    fun multipleActiveTail_rollsBackEntirely() = runTest {
        val trx = sampleTransaction(id = "trx-multi-tail", description = "CarrefourSA Mini")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badOp1 = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-bad-1",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val badOp2 = badOp1.copy(operationId = "op-bad-2")
        db.localMutationDao().insertOutboxRow(badOp1)
        db.localMutationDao().insertOutboxRow(badOp2)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = true,
                assignedMerchantId = "carrefoursa",
                assignedCatalogVersion = StarterMerchantCatalog.merchants.first { it.id == "carrefoursa" }.catalogVersion,
                operationIdFactory = { "op-new" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14b. Negatif DAO Topoloji: Orphan blocked successor
    @Test
    fun orphanBlockedSuccessor_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-orphan", description = "Netflix Orphan")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val orphanSuccessor = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-orphan",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = "missing-predecessor-id",
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(orphanSuccessor)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-orphan" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14c. Negatif DAO Topoloji: Blocked root
    @Test
    fun blockedRoot_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-blocked-root", description = "Spotify Blocked Root")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badRoot = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-blocked-root",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = true, // Kök kaydın predecessor'ı yokken isBlocked = true olamaz!
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(badRoot)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-bad-root" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14d. Negatif DAO Topoloji: Predecessor outbox'ta hala canlı iken isBlocked = false successor
    @Test
    fun unblockedSuccessorWithPredecessor_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-unblocked-succ", description = "Getir Unblocked")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val entity = sampleEntity(verId, userA, alias, "ASSIGN", "getir", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        db.localMutationDao().upsertUserMerchantVerificationRow(entity)

        val rootOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-root-1",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val unblockedSucc = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-unblocked-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(entity),
            predecessorOperationId = rootOp.operationId,
            isBlocked = false, // Predecessor outbox'ta hala canlı iken unblocked tail kabul edilemez!
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(rootOp)
        db.localMutationDao().insertOutboxRow(unblockedSucc)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-unblocked" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14e. Negatif DAO Topoloji: Terminal / FAILED predecessor
    @Test
    fun terminalFailedPredecessor_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-failed-pred", description = "Trendyol Failed Pred")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val failedPred = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-failed-root",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "FAILED",
            attemptCount = 5,
            lastError = "Fatal error",
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succ = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-failed-pred",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = failedPred.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(failedPred)
        db.localMutationDao().insertOutboxRow(succ)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-failed-pred" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14f. Negatif DAO Topoloji: Predecessor protocolVersion != 2
    @Test
    fun predecessorProtocolVersionNot2_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-proto-pred", description = "Netflix Proto Pred")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badProtoPred = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-v1-root",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 1, // ProtocolVersion 1
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succ = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-v1-pred",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = badProtoPred.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(badProtoPred)
        db.localMutationDao().insertOutboxRow(succ)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-bad-proto-pred" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14g. Negatif DAO Topoloji: Successor protocolVersion != 2
    @Test
    fun successorProtocolVersionNot2_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-proto-succ", description = "Netflix Proto Succ")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badProtoSucc = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-v1-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 1, // ProtocolVersion 1
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(badProtoSucc)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-bad-proto-succ" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14h. Negatif DAO Topoloji: Mismatched predecessor entity type / id / scope
    @Test
    fun mismatchedPredecessorProperties_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-mismatch-pred", description = "Netflix Mismatch Pred")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val foreignPred = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-foreign-cat",
            entityTypeCode = "CATEGORY", // Wrong entity type!
            entityId = "cat-1",
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succ = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-foreign",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = foreignPred.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(foreignPred)
        db.localMutationDao().insertOutboxRow(succ)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-after-mismatch-pred" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14i. Negatif DAO: Katalogda olmayan doğrudan DAO merchant ID
    @Test
    fun unknownMerchantDirectDao_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-dao-unknown", description = "Netflix Unknown")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = true,
                assignedMerchantId = "non_existent_merchant_direct_dao",
                assignedCatalogVersion = 1,
                operationIdFactory = { "op-unknown-dao" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14j. Negatif DAO: Doğru ID + Yanlış catalog version
    @Test
    fun validIdWrongCatalogVersion_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-dao-wrong-ver", description = "Netflix Wrong Ver")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = true,
                assignedMerchantId = "netflix",
                assignedCatalogVersion = 9999, // Sahte sürüm
                operationIdFactory = { "op-wrong-ver" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14k. Negatif DAO: Excluded transaction doğrudan DAO çağrısında fail-closed reddedilir
    @Test
    fun excludedTransactionDirectDao_rejectedFailClosedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-dao-excluded", description = "MAAS ODEMESI EKIM")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-excluded-dao" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 15. Gerçek SQLite abort: transaction update aşamasında trigger ile abort enjeksiyonu ve byte-for-byte rollback kanıtı
    @Test
    fun sqliteTriggerAbort_rollsBackVerificationEntityAndOutboxByteForByte() = runTest {
        val trx = sampleTransaction(id = "trx-abort-test", description = "Netflix Normal")
        db.transactionDao().upsert(trx)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)

        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER test_abort_transaction_update
            BEFORE UPDATE ON transactions
            BEGIN
                SELECT RAISE(ABORT, 'forced_abort_on_transaction_update');
            END;
            """.trimIndent(),
        )

        try {
            assertFailsWith<Exception> {
                db.localMutationDao().applyTransactionMerchantCorrectionV2(
                    syncScopeKey = syncScopeA,
                    actorUserId = userA,
                    transactionId = trx.id,
                    isAssign = true,
                    assignedMerchantId = "netflix",
                    assignedCatalogVersion = StarterMerchantCatalog.merchants.first { it.id == "netflix" }.catalogVersion,
                    operationIdFactory = { "op-aborted" },
                    nowEpochMillis = 1000L,
                )
            }

            val storedVer = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNull(storedVer, "Verification entity rollback edilmelidir!")

            val ops = getAllOperations(syncScopeA)
            assertTrue(ops.isEmpty(), "Outbox satırları rollback edilmelidir!")

            val currentTrx = db.localMutationDao().getTransactionRow(trx.id)!!
            assertEquals(trx, currentTrx, "Transaction ilk snapshot ile birebir aynı kalmalıdır!")
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS test_abort_transaction_update")
        }
    }

    // 16. Önceden var olan TRANSACTION outbox: düzeltme sonrasında birebir korunur
    @Test
    fun preExistingTransactionOutbox_isPreservedByteForByteAfterCorrection() = runTest {
        val trx = sampleTransaction(id = "trx-with-existing-outbox", description = "Migros Market")
        db.transactionDao().upsert(trx)

        val existingTrxOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-trx-create-12345",
            entityTypeCode = "TRANSACTION",
            entityId = trx.id,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{\"id\":\"${trx.id}\",\"amount\":15000}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 500L,
            createdAtEpochMillis = 500L,
            updatedAtEpochMillis = 500L,
        )
        db.localMutationDao().insertOutboxRow(existingTrxOp)

        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("migros")),
        )

        val allOps = getAllOperations(syncScopeA)
        assertEquals(2, allOps.size)

        val preservedTrxOp = allOps.first { it.entityTypeCode == "TRANSACTION" }
        assertEquals(existingTrxOp, preservedTrxOp, "Mevcut TRANSACTION outbox satırı byte-for-byte korunmalıdır!")

        val verOp = allOps.first { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }
        assertEquals("USER_MERCHANT_VERIFICATION", verOp.entityTypeCode)
    }

    // 17. Transaction'ın amount/category/date/description/receipt/installment/split/note alanlarının birebir korunduğu doğrulanır
    @Test
    fun transactionDomainFields_areStrictlyPreservedByteForByte() = runTest {
        val initialTrx = sampleTransaction(
            id = "trx-strict-preservation",
            description = "Starbucks Coffee",
            merchantId = null,
        )
        db.transactionDao().upsert(initialTrx)

        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(initialTrx.id), EntityId("starbucks")),
        )

        val updatedTrx = db.localMutationDao().getTransactionRow(initialTrx.id)!!

        assertEquals(initialTrx.id, updatedTrx.id)
        assertEquals(initialTrx.ownerId, updatedTrx.ownerId)
        assertEquals(initialTrx.workspaceId, updatedTrx.workspaceId)
        assertEquals(initialTrx.paidByUserId, updatedTrx.paidByUserId)
        assertEquals(initialTrx.participantUserIdsJson, updatedTrx.participantUserIdsJson)
        assertEquals(initialTrx.amountMinor, updatedTrx.amountMinor)
        assertEquals(initialTrx.currencyCode, updatedTrx.currencyCode)
        assertEquals(initialTrx.typeCode, updatedTrx.typeCode)
        assertEquals(initialTrx.categoryId, updatedTrx.categoryId)
        assertEquals(initialTrx.description, updatedTrx.description)
        assertEquals(initialTrx.searchText, updatedTrx.searchText)
        assertEquals(initialTrx.paymentMethodCode, updatedTrx.paymentMethodCode)
        assertEquals(initialTrx.transactionDate, updatedTrx.transactionDate)
        assertEquals(initialTrx.receiptPath, updatedTrx.receiptPath)
        assertEquals(initialTrx.installmentNumber, updatedTrx.installmentNumber)
        assertEquals(initialTrx.totalInstallments, updatedTrx.totalInstallments)
        assertEquals(initialTrx.installmentGroupId, updatedTrx.installmentGroupId)
        assertEquals(initialTrx.createdAtEpochMillis, updatedTrx.createdAtEpochMillis)
        assertEquals(initialTrx.note, updatedTrx.note)
        assertEquals(initialTrx.splitMode, updatedTrx.splitMode)
        assertEquals(initialTrx.participantSharesJson, updatedTrx.participantSharesJson)

        assertEquals(initialTrx.sync.version, updatedTrx.sync.version)
        assertEquals(initialTrx.sync.baseVersion, updatedTrx.sync.baseVersion)
        assertEquals(initialTrx.sync.syncStatus, updatedTrx.sync.syncStatus)
        assertEquals(initialTrx.sync.updatedAtEpochMillis, updatedTrx.sync.updatedAtEpochMillis)
        assertEquals(initialTrx.sync.localUpdatedAtEpochMillis, updatedTrx.sync.localUpdatedAtEpochMillis)
        assertEquals(initialTrx.sync.deletedAtEpochMillis, updatedTrx.sync.deletedAtEpochMillis)
        assertEquals(initialTrx.sync.lastSyncError, updatedTrx.sync.lastSyncError)

        assertEquals("starbucks", updatedTrx.merchantId)
        assertEquals("USER_VERIFIED", updatedTrx.merchantMatchSourceCode)
        assertEquals(100, updatedTrx.merchantConfidence)
        assertEquals(10, updatedTrx.merchantCatalogVersion)
    }

    // 18. CancellationException yutulmaz
    @Test
    fun cancellationException_isReThrown() = runTest {
        val cancellingAuthRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("Simulated coroutine cancellation")
            }
            override fun observeCurrentProfile(): Flow<com.feniqo.mobile.domain.model.UserProfile?> = flow { }
            override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("unused")
            override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("unused")
            override suspend fun refreshSession(): RepositoryResult<Unit> = error("unused")
            override suspend fun signOut(): RepositoryResult<Unit> = error("unused")
        }

        val testRepo = OfflineFirstTransactionRepository(
            authRepository = cancellingAuthRepo,
            transactionDao = db.transactionDao(),
            offlineWriteQueue = queue,
            workspaceDao = db.workspaceDao(),
            userMerchantVerificationRepository = fakeVerificationRepo,
            activeWorkspaceScope = PersonalActiveWorkspaceScope,
            nowEpochMillisProvider = { 1000L },
            merchantRecognitionEngine = MerchantRecognitionEngine(),
        )

        assertFailsWith<CancellationException> {
            testRepo.correctMerchant(
                TransactionMerchantCorrection.Block(EntityId("any-trx-id")),
            )
        }
    }

    // 19. Pre-ACK Pozitif 1: pendingCreate_inFlight_thenBlock_createsBlockedUpdateSuccessor
    @Test
    fun pendingCreate_inFlight_thenBlock_createsBlockedUpdateSuccessor() = runTest {
        val trx = sampleTransaction(id = "trx-preack-1", description = "Netflix PreAck InFlight")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix (kök CREATE oluşturur)
        val res1 = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        assertIs<RepositoryResult.Success<Unit>>(res1)

        val rootOp = getAllOperations(syncScopeA).first()
        assertEquals("CREATE", rootOp.operationTypeCode)
        assertNull(rootOp.baseVersion)
        assertNull(rootOp.predecessorOperationId)

        // Kök CREATE IN_FLIGHT durumuna geçiyor (uçuşta)
        val claimed = queue.claimOperation(syncScopeA, rootOp.operationId)
        assertNotNull(claimed)

        // 2. Düzeltme: Henüz ACK almamışken BLOCK yapılır
        val res2 = repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        assertIs<RepositoryResult.Success<Unit>>(res2)

        val ops = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        assertEquals(2, ops.size)

        val successor = ops.last()
        assertEquals("UPDATE", successor.operationTypeCode)
        assertNull(successor.baseVersion)
        assertEquals(rootOp.operationId, successor.predecessorOperationId)
        assertTrue(successor.isBlocked)
        assertEquals("PENDING", successor.statusCode)
        assertEquals(0, successor.attemptCount)

        // Verification entity kararı BLOCK olmalıdır
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)!!
        assertEquals("BLOCK", verEntity.decisionCode)
        assertNull(verEntity.merchantId)

        // Transaction attribution da yerel son karara (BLOCK) uygun olmalıdır
        val updatedTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertNull(updatedTrx.merchantId)
        assertNull(updatedTrx.merchantMatchSourceCode)
        assertNull(updatedTrx.merchantConfidence)
        assertNull(updatedTrx.merchantCatalogVersion)
    }

    // 20. Pre-ACK Pozitif 2: pendingCreate_inFlight_thenAssign_createsBlockedUpdateSuccessor
    @Test
    fun pendingCreate_inFlight_thenAssign_createsBlockedUpdateSuccessor() = runTest {
        val trx = sampleTransaction(id = "trx-preack-2", description = "Migros PreAck Assign")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: BLOCK
        val res1 = repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        assertIs<RepositoryResult.Success<Unit>>(res1)

        val rootOp = getAllOperations(syncScopeA).first()
        val claimed = queue.claimOperation(syncScopeA, rootOp.operationId)
        assertNotNull(claimed)

        // 2. Düzeltme: Uçuşta iken ASSIGN migros
        val res2 = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("migros")),
        )
        assertIs<RepositoryResult.Success<Unit>>(res2)

        val ops = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        assertEquals(2, ops.size)
        val successor = ops.last()

        assertEquals("UPDATE", successor.operationTypeCode)
        assertNull(successor.baseVersion)
        assertEquals(rootOp.operationId, successor.predecessorOperationId)
        assertTrue(successor.isBlocked)
        assertEquals("PENDING", successor.statusCode)

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)!!
        assertEquals("ASSIGN", verEntity.decisionCode)
        assertEquals("migros", verEntity.merchantId)

        val updatedTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertEquals("migros", updatedTrx.merchantId)
        assertEquals("USER_VERIFIED", updatedTrx.merchantMatchSourceCode)
        assertEquals(100, updatedTrx.merchantConfidence)
    }

    // 21. Pre-ACK Pozitif 3: pendingCreate_blockedSuccessor_thenSecondCorrection_coalescesSuccessor
    @Test
    fun pendingCreate_blockedSuccessor_thenSecondCorrection_coalescesSuccessor() = runTest {
        val trx = sampleTransaction(id = "trx-preack-3", description = "Spotify PreAck Coalesce")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN spotify
        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("spotify")),
        )
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)

        // 2. Düzeltme: BLOCK -> blocked successor oluşur
        repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        val opsAfter2 = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        assertEquals(2, opsAfter2.size)
        val successorOpId = opsAfter2.last().operationId

        // 3. Düzeltme: Blocked successor denenmemiş PENDING iken tekrar ASSIGN yap
        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("spotify")),
        )

        // Yeni outbox eklenmemeli! Aynı successor güvenle coalesce edilmelidir!
        val opsAfter3 = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        assertEquals(2, opsAfter3.size, "3. outbox eklenmemeli, mevcut successor coalesce edilmelidir!")
        assertEquals(successorOpId, opsAfter3.last().operationId)

        val payload = UserMerchantVerificationPayloadCodec.parseAndValidate(
            jsonString = opsAfter3.last().payloadJson!!,
            operation = "CREATE",
            baseVersion = null,
        )
        assertEquals("ASSIGN", payload.decisionCode)
        assertEquals("spotify", payload.merchantId)
    }

    // 22. Pre-ACK Pozitif 4: pendingCreateAck_rebasesAndUnblocksSuccessor_preservesLatestDecision
    @Test
    fun pendingCreateAck_rebasesAndUnblocksSuccessor_preservesLatestDecision() = runTest {
        val trx = sampleTransaction(id = "trx-preack-4", description = "Netflix PreAck Rebase")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix
        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)

        // 2. Düzeltme: BLOCK -> successor UPDATE eklenir
        repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )
        val ops = getAllOperations(syncScopeA).sortedBy { it.createdAtEpochMillis }
        val successorId = ops.last().operationId

        // Şimdi sunucu kök CREATE için ACK döner (version = 1L)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
        val remoteRecord = UserMerchantVerificationDto(
            id = verId,
            userId = userA,
            workspaceId = null,
            normalizedAlias = alias,
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            scopeCode = "PERSONAL",
            createdAt = "2026-10-18T10:00:00Z",
            updatedAt = "2026-10-18T10:00:00Z",
            version = 1L,
        )

        val acknowledged = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = rootOp.operationId,
            record = remoteRecord,
            nowEpochMillis = 2000L,
        )
        assertTrue(acknowledged)

        // Kök operasyon silinmiş olmalı
        assertNull(db.syncOperationDao().getById(syncScopeA, rootOp.operationId))

        // Successor unblock edilmiş ve baseVersion 1L ile rebase edilmiş olmalı
        val unblockedSuccessor = db.syncOperationDao().getById(syncScopeA, successorId)!!
        assertFalse(unblockedSuccessor.isBlocked)
        assertEquals(1L, unblockedSuccessor.baseVersion)
        assertEquals("UPDATE", unblockedSuccessor.operationTypeCode)
        assertEquals("PENDING", unblockedSuccessor.statusCode)

        // Yerel son karar (BLOCK) ACK tarafından EZİLMEMİŞ olmalıdır!
        val localVer = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("BLOCK", localVer.decisionCode)
        assertNull(localVer.merchantId)
        assertEquals(1L, localVer.sync.version)

        // Transaction attribution da BLOCK (null) kalmalıdır!
        val currentTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertNull(currentTrx.merchantId)
        assertNull(currentTrx.merchantMatchSourceCode)
    }

    // 23. Pre-ACK Pozitif 5: pendingCreateAckFailure_rollsBackWithoutLosingLatestAttribution
    @Test
    fun pendingCreateAckFailure_rollsBackWithoutLosingLatestAttribution() = runTest {
        val trx = sampleTransaction(id = "trx-preack-5", description = "Netflix PreAck AckFail")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix
        repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)

        // 2. Düzeltme: BLOCK
        repository.correctMerchant(
            TransactionMerchantCorrection.Block(EntityId(trx.id)),
        )

        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        // Hatalı ACK çağrısı (ör. sahte veya var olmayan bir operationId ile ACK denenmesi)
        assertFailsWith<Exception> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = "non_existent_op_id",
                record = UserMerchantVerificationDto(
                    id = verId,
                    userId = userA,
                    workspaceId = null,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "netflix",
                    scopeCode = "PERSONAL",
                    createdAt = "2026-10-18T10:00:00Z",
                    updatedAt = "2026-10-18T10:00:00Z",
                    version = 1L,
                ),
                nowEpochMillis = 2000L,
            )
        }

        // Yerel attribution ve verification son kararı (BLOCK) asla kaybolmamalıdır
        val currentTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertNull(currentTrx.merchantId)
        assertNull(currentTrx.merchantMatchSourceCode)

        val localVer = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("BLOCK", localVer.decisionCode)
    }

    // 24. Pre-ACK Topoloji R2-1: CREATE IN_FLIGHT -> blocked UPDATE successor -> CREATE ACK:
    // predecessor outbox satırı silinir, successor unblock edilir, baseVersion remote version olur, tarihsel predecessorOperationId korunur.
    @Test
    fun createInFlight_blockedUpdateSuccessor_createAck_deletesPredecessorAndPreservesHistoricalRefOnUnblockedSuccessor() = runTest {
        val trx = sampleTransaction(id = "trx-canon-1", description = "Netflix Canon 1")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix (kök CREATE)
        repository.correctMerchant(TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")))
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId) // IN_FLIGHT yap

        // 2. Düzeltme: BLOCK (blocked UPDATE successor)
        repository.correctMerchant(TransactionMerchantCorrection.Block(EntityId(trx.id)))
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val opsBeforeAck = getAllOperations(syncScopeA)
        assertEquals(2, opsBeforeAck.size)
        val successorBeforeAck = opsBeforeAck.single { it.operationId != rootOp.operationId }
        assertTrue(successorBeforeAck.isBlocked)
        assertEquals(rootOp.operationId, successorBeforeAck.predecessorOperationId)
        assertNull(successorBeforeAck.baseVersion)

        // Remote ACK: version = 1L
        val ackSuccess = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = rootOp.operationId,
            record = UserMerchantVerificationDto(
                id = verId,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-19T10:00:00Z",
                updatedAt = "2026-10-19T10:00:00Z",
                version = 1L,
            ),
            nowEpochMillis = 2000L,
        )
        assertTrue(ackSuccess)

        // ACK sonrası kontrol:
        val opsAfterAck = getAllOperations(syncScopeA)
        assertEquals(1, opsAfterAck.size)
        val successorAfterAck = opsAfterAck.first()
        assertEquals(successorBeforeAck.operationId, successorAfterAck.operationId)
        assertEquals("UPDATE", successorAfterAck.operationTypeCode)
        assertFalse(successorAfterAck.isBlocked)
        assertEquals("PENDING", successorAfterAck.statusCode)
        assertEquals(0, successorAfterAck.attemptCount)
        assertEquals(1L, successorAfterAck.baseVersion)
        assertEquals(rootOp.operationId, successorAfterAck.predecessorOperationId) // Geçmiş referans korunur
        assertNull(db.syncOperationDao().getById(syncScopeA, rootOp.operationId)) // Predecessor silinmiştir

        val localVer = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("BLOCK", localVer.decisionCode)
        assertEquals(1L, localVer.sync.version)
        assertEquals(1L, localVer.sync.baseVersion)
        assertEquals("PENDING_UPDATE", localVer.sync.syncStatus)
    }

    // 25. Pre-ACK Topoloji R2-2: ACK'den sonra yeni ASSIGN/BLOCK: unblocked UPDATE'e coalesce edilir, yeni satır oluşmaz.
    @Test
    fun afterCreateAck_subsequentCorrection_coalescesIntoUnblockedPendingUpdate() = runTest {
        val trx = sampleTransaction(id = "trx-canon-2", description = "Netflix Canon 2")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix
        repository.correctMerchant(TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")))
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)

        // 2. Düzeltme: BLOCK
        repository.correctMerchant(TransactionMerchantCorrection.Block(EntityId(trx.id)))
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        // ACK
        db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = rootOp.operationId,
            record = UserMerchantVerificationDto(
                id = verId,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-19T10:00:00Z",
                updatedAt = "2026-10-19T10:00:00Z",
                version = 1L,
            ),
            nowEpochMillis = 2000L,
        )

        // 3. Düzeltme: Tekrar ASSIGN netflix (ACK sonrası unblocked UPDATE'e coalesce edilmeli)
        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        assertTrue(result is RepositoryResult.Success)

        // Kontroller:
        val ops = getAllOperations(syncScopeA)
        assertEquals(1, ops.size) // Yeni gereksiz outbox satırı oluşmaz!
        val op = ops.first()
        assertEquals("UPDATE", op.operationTypeCode)
        assertFalse(op.isBlocked)
        assertEquals(rootOp.operationId, op.predecessorOperationId) // Geçmiş referans korunur
        assertEquals(1L, op.baseVersion)
        assertTrue(op.payloadJson!!.contains("\"decision_code\":\"ASSIGN\""))
        assertTrue(op.payloadJson!!.contains("\"merchant_id\":\"netflix\""))

        // Entity ve Transaction son kararı taşır
        val localVer = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("ASSIGN", localVer.decisionCode)
        assertEquals("netflix", localVer.merchantId)
        assertEquals(1L, localVer.sync.version)

        val currentTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertEquals("netflix", currentTrx.merchantId)
        assertEquals("USER_VERIFIED", currentTrx.merchantMatchSourceCode)
        assertEquals(100, currentTrx.merchantConfidence)
    }

    // 26. Pre-ACK Topoloji R2-3: ACK sonrası UPDATE successor IN_FLIGHT iken yeni karar canonical blocked successor üretir
    @Test
    fun afterCreateAck_whenUpdateSuccessorInFlight_subsequentCorrection_createsCanonicalBlockedSuccessor_andAckPreservesLatest() = runTest {
        val trx = sampleTransaction(id = "trx-canon-3", description = "Netflix Canon 3")
        db.transactionDao().upsert(trx)

        // 1. Düzeltme: ASSIGN netflix
        repository.correctMerchant(TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")))
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)

        // 2. Düzeltme: BLOCK
        repository.correctMerchant(TransactionMerchantCorrection.Block(EntityId(trx.id)))
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        // ACK rootOp (version = 1L)
        db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = rootOp.operationId,
            record = UserMerchantVerificationDto(
                id = verId,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-19T10:00:00Z",
                updatedAt = "2026-10-19T10:00:00Z",
                version = 1L,
            ),
            nowEpochMillis = 2000L,
        )

        val updateOp = getAllOperations(syncScopeA).first()
        // Bu UPDATE operasyonunu da claimOperation ile IN_FLIGHT yapalım
        queue.claimOperation(syncScopeA, updateOp.operationId)

        // 3. Düzeltme: Tekrar ASSIGN netflix (IN_FLIGHT UPDATE successor varken yeni karar)
        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        assertTrue(result is RepositoryResult.Success)

        // Kontroller: 2 outbox operasyonu olmalı
        val ops = getAllOperations(syncScopeA)
        assertEquals(2, ops.size)
        val pred = ops.single { it.operationId == updateOp.operationId }
        val newSucc = ops.single { it.operationId != updateOp.operationId }

        assertEquals("IN_FLIGHT", pred.statusCode)
        assertEquals("UPDATE", pred.operationTypeCode)
        assertEquals(rootOp.operationId, pred.predecessorOperationId) // Geçmiş referans korunur

        assertEquals("UPDATE", newSucc.operationTypeCode)
        assertEquals("PENDING", newSucc.statusCode)
        assertEquals(0, newSucc.attemptCount)
        assertTrue(newSucc.isBlocked)
        assertEquals(pred.operationId, newSucc.predecessorOperationId)
        assertEquals(1L, newSucc.baseVersion)

        // updateOp ACK geldiğinde (remote version = 2L):
        db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = pred.operationId,
            record = UserMerchantVerificationDto(
                id = verId,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "BLOCK",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-10-19T10:00:00Z",
                updatedAt = "2026-10-19T10:00:00Z",
                version = 2L,
            ),
            nowEpochMillis = 3000L,
        )

        // pred silinmeli, newSucc unblock edilmeli, predecessorOperationId = null olmalı, baseVersion = 2L olmalı
        val finalOps = getAllOperations(syncScopeA)
        assertEquals(1, finalOps.size)
        val unblockedSuccessor = finalOps.first()
        assertEquals(newSucc.operationId, unblockedSuccessor.operationId)
        assertFalse(unblockedSuccessor.isBlocked)
        assertEquals(pred.operationId, unblockedSuccessor.predecessorOperationId) // Geçmiş referans korunur
        assertNull(db.syncOperationDao().getById(syncScopeA, pred.operationId)) // Predecessor silinmiştir
        assertEquals(2L, unblockedSuccessor.baseVersion)

        // Ve yerel en son karar (ASSIGN netflix) korunmalı!
        val currentTrx = db.localMutationDao().getTransactionRow(trx.id)!!
        assertEquals("netflix", currentTrx.merchantId)
        assertEquals("USER_VERIFIED", currentTrx.merchantMatchSourceCode)
    }

    // 27. Negatif Topoloji R2-4: Root IN_FLIGHT UPDATE + baseVersion null reddedilir ve snapshot değişmez
    @Test
    fun rootInFlightUpdate_baseVersionNull_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-bad-upd-bv", description = "Netflix Bad Upd Bv")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-bad-upd",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null, // Invalid: root UPDATE cannot have null baseVersion!
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(badOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 28. Negatif Topoloji R2-5: Root IN_FLIGHT DELETE + baseVersion null reddedilir ve snapshot değişmez
    @Test
    fun rootInFlightDelete_baseVersionNull_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-bad-del-bv", description = "Netflix Bad Del Bv")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val badOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-bad-del",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = null, // Invalid: root DELETE cannot have null baseVersion!
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(badOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 29. Negatif Topoloji R2-6: Blocked successor predecessor status PENDING reddedilir
    @Test
    fun blockedSuccessor_predecessorPending_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-pred-pending", description = "Netflix Pred Pending")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-pending",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING", // Invalid: predecessor of blocked successor must be IN_FLIGHT!
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-blocked",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 30. Negatif Topoloji R2-7: Blocked successor predecessor status CONFLICT reddedilir
    @Test
    fun blockedSuccessor_predecessorConflict_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-pred-conflict", description = "Netflix Pred Conflict")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-conflict",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "CONFLICT", // Invalid!
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-blocked-conflict",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 31. Negatif Topoloji R2-8: Blocked successor operationType CREATE reddedilir
    @Test
    fun blockedSuccessor_operationTypeCreate_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-succ-create", description = "Netflix Succ Create")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-create-flight",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-create-bad",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE", // Invalid: successor cannot be CREATE!
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 32. Negatif Topoloji R2-9: Blocked successor operationType DELETE reddedilir
    @Test
    fun blockedSuccessor_operationTypeDelete_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-succ-delete", description = "Netflix Succ Delete")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-del-flight",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-del-bad",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "DELETE", // Invalid: successor cannot be DELETE!
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 33. Negatif Topoloji R2-10: Blocked successor attemptCount > 0 reddedilir
    @Test
    fun blockedSuccessor_attemptCountGreaterThanZero_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-succ-att", description = "Netflix Succ Attempt")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-att-flight",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-att-bad",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 1, // Invalid: attemptCount > 0!
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 34. Negatif Topoloji R2-11: Blocked + IN_FLIGHT successor reddedilir
    @Test
    fun blockedAndInFlightSuccessor_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-succ-flight", description = "Netflix Succ Flight")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-f1",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-flight-bad",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT", // Invalid: cannot be both isBlocked and IN_FLIGHT!
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 35. Negatif Topoloji R2-12: Predecessor root değilse veya blocked ise reddedilir
    @Test
    fun predecessorNotRootOrBlocked_rejectedAndRolledBack() = runTest {
        val trx = sampleTransaction(id = "trx-pred-deep", description = "Netflix Pred Deep")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val predOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-pred-deep",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = "op-even-deeper-root", // Invalid: predecessor is not root!
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-succ-deep-bad",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 36. Tipli Hata Testi: Session yokluğunda AUTH_SESSION_REQUIRED döner (OWNER_MISMATCH değil)
    @Test
    fun correctMerchant_whenAuthSessionNull_returnsAuthSessionRequiredAppError() = runTest {
        val trx = sampleTransaction(id = "trx-null-session", description = "Netflix Session Null")
        db.transactionDao().upsert(trx)

        fakeAuthRepo.setSession(null) // Oturum kapalı

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")),
        )
        assertTrue(result is RepositoryResult.Failure)
        val error = result.error
        assertTrue(error is com.feniqo.mobile.domain.model.AppError.Authentication)
        assertEquals("auth_session_required", error.code)
    }

    // 37. Tipli Hata Testi: Workspace transaction durumunda WORKSPACE_RESTRICTED döner ve bu Validation türündedir
    @Test
    fun correctMerchant_whenWorkspaceRestricted_returnsValidationAppError() = runTest {
        db.workspaceDao().upsertWorkspace(
            WorkspaceEntity(
                id = "ws-123",
                name = "Test Ws",
                normalizedName = "test ws",
                ownerId = userA,
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = 1L,
                    lastSyncError = null,
                ),
            ),
        )
        val wsTrx = sampleTransaction(id = "trx-ws-type", description = "Netflix Ws").copy(
            workspaceId = "ws-123",
        )
        db.transactionDao().upsert(wsTrx)

        val result = repository.correctMerchant(
            TransactionMerchantCorrection.Assign(EntityId(wsTrx.id), EntityId("netflix")),
        )
        assertTrue(result is RepositoryResult.Failure)
        val error = result.error
        assertTrue(error is com.feniqo.mobile.domain.model.AppError.Validation)
        assertEquals("workspace_restricted", error.code)
    }

    private suspend fun setupInFlightCreateAndBlockedSuccessor(
        trxId: String,
        description: String = "Netflix Test",
    ): Triple<TransactionEntity, SyncOperationEntity, SyncOperationEntity> {
        val trx = sampleTransaction(id = trxId, description = description)
        db.transactionDao().upsert(trx)
        repository.correctMerchant(TransactionMerchantCorrection.Assign(EntityId(trx.id), EntityId("netflix")))
        val rootOp = getAllOperations(syncScopeA).first()
        queue.claimOperation(syncScopeA, rootOp.operationId)
        repository.correctMerchant(TransactionMerchantCorrection.Block(EntityId(trx.id)))
        val allOps = getAllOperations(syncScopeA)
        val succOp = allOps.single { it.operationId != rootOp.operationId }
        val refreshedTrx = db.localMutationDao().getTransactionRow(trxId)!!
        return Triple(refreshedTrx, rootOp, succOp)
    }

    private fun sampleRemoteAckDto(
        verId: String,
        alias: String,
        version: Long = 1L,
        decisionCode: String = "ASSIGN",
        merchantId: String? = "netflix",
        deletedAt: String? = null,
        createdAt: String = "2026-10-19T10:00:00Z",
        updatedAt: String? = "2026-10-19T10:00:00Z",
    ): UserMerchantVerificationDto =
        UserMerchantVerificationDto(
            id = verId,
            userId = userA,
            workspaceId = null,
            normalizedAlias = alias,
            decisionCode = decisionCode,
            merchantId = merchantId,
            scopeCode = "PERSONAL",
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            version = version,
        )

    private fun sampleInFlightOperation(
        operationId: String,
        entityId: String,
        operationTypeCode: String,
        baseVersion: Long?,
        payloadJson: String,
        predecessorOperationId: String? = null,
        isBlocked: Boolean = false,
        statusCode: String = "IN_FLIGHT",
        attemptCount: Int = 1,
    ): SyncOperationEntity =
        SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = operationId,
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = entityId,
            operationTypeCode = operationTypeCode,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = predecessorOperationId,
            isBlocked = isBlocked,
            protocolVersion = 2,
            statusCode = statusCode,
            attemptCount = attemptCount,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )

    // 38. ACK Negatif: Successor entityId uyuşmazlığı durumunda tam rollback
    @Test
    fun ack_successorDifferentEntityId_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-eid")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(entityId = "different-entity-id"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 39. ACK Negatif: Successor entityType uyuşmazlığı durumunda tam rollback
    @Test
    fun ack_successorDifferentEntityType_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-etype")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(entityTypeCode = "TRANSACTION"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 40. ACK Negatif: Successor operationType CREATE olduğunda tam rollback
    @Test
    fun ack_successorOperationTypeCreate_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-opcreate")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(operationTypeCode = "CREATE"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 41. ACK Negatif: Successor operationType DELETE olduğunda tam rollback
    @Test
    fun ack_successorOperationTypeDelete_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-opdel")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(operationTypeCode = "DELETE"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 42. ACK Negatif: Successor status IN_FLIGHT/FAILED/CONFLICT durumunda tam rollback
    @Test
    fun ack_successorStatusInvalid_failsClosedAndRollsBack() = runTest {
        val invalidStatuses = listOf("IN_FLIGHT", "FAILED", "CONFLICT")
        for (status in invalidStatuses) {
            val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-status-$status")
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
            db.localMutationDao().insertOutboxRow(succOp.copy(statusCode = status))
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = rootOp.operationId,
                    record = sampleRemoteAckDto(verId, alias),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 43. ACK Negatif: Successor attemptCount > 0 durumunda tam rollback
    @Test
    fun ack_successorAttemptCountNonZero_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-attcount")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(attemptCount = 1))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 44. ACK Negatif: Successor isBlocked=false durumunda tam rollback
    @Test
    fun ack_successorNotBlocked_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-notblocked")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(isBlocked = false))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 45. ACK Negatif: Successor predecessorOperationId uyuşmazlığında tam rollback
    @Test
    fun ack_successorPredecessorOperationIdMismatch_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-predmismatch")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(predecessorOperationId = "wrong-predecessor-id"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 46. ACK Negatif: Pre-ACK CREATE zincirinde successor baseVersion non-null ise tam rollback
    @Test
    fun ack_successorBaseVersionNonNullPreAck_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-basever")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(baseVersion = 1L))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 47. ACK Negatif: Successor payload bozuk veya şemaya uymuyorsa tam rollback
    @Test
    fun ack_successorPayloadMalformed_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-malformed")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(payloadJson = "{broken json payload"))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 48. ACK Negatif: Successor payload güncel yerel entity'den farklı ise tam rollback
    @Test
    fun ack_successorPayloadDiffersFromLocalEntity_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-ack-payloaddiff")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val wrongEntity = sampleEntity(verId, userA, alias, "ASSIGN", "amazon")
        val wrongPayload = UserMerchantVerificationPayloadCodec.encodeCreate(wrongEntity)

        db.localMutationDao().deleteOutboxRow(syncScopeA, succOp.operationId)
        db.localMutationDao().insertOutboxRow(succOp.copy(payloadJson = wrongPayload))
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 49. Rebased UPDATE Invariant: entity baseVersion > version durumunda fail-closed rollback
    @Test
    fun rebasedUpdate_entityBaseVersionGreaterThanVersion_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-basever-gt-ver", description = "Netflix BaseGtVer")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val brokenEntity = sampleEntity(
            id = verId,
            userId = userA,
            alias = alias,
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            syncStatus = "PENDING_UPDATE",
            version = 1L,
            baseVersion = 2L,
        )
        db.localMutationDao().upsertUserMerchantVerificationRow(brokenEntity)

        val tailOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-tail-update-gt",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(tailOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 50. Rebased UPDATE Invariant: entity syncStatus != PENDING_UPDATE durumunda fail-closed rollback
    @Test
    fun rebasedUpdate_entitySyncStatusNotPendingUpdate_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-syncstat-not-upd", description = "Netflix SyncStatNotUpd")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val brokenEntity = sampleEntity(
            id = verId,
            userId = userA,
            alias = alias,
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            syncStatus = "SYNCED",
            version = 1L,
            baseVersion = 1L,
        )
        db.localMutationDao().upsertUserMerchantVerificationRow(brokenEntity)

        val tailOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-tail-update-stat",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(tailOp)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 51. In-flight root CREATE Invariant: entity syncStatus != PENDING_CREATE durumunda fail-closed rollback
    @Test
    fun inFlightRootCreate_entitySyncStatusNotPendingCreate_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-inflight-stat", description = "Netflix InFlightStat")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val rootOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-root-inflight",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{}",
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(rootOp)

        val brokenEntity = sampleEntity(
            id = verId,
            userId = userA,
            alias = alias,
            decisionCode = "ASSIGN",
            merchantId = "netflix",
            syncStatus = "PENDING_UPDATE",
            version = 0L,
            baseVersion = null,
        )
        db.localMutationDao().upsertUserMerchantVerificationRow(brokenEntity)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().applyTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeA,
                actorUserId = userA,
                transactionId = trx.id,
                isAssign = false,
                assignedMerchantId = null,
                assignedCatalogVersion = null,
                operationIdFactory = { "op-next" },
                nowEpochMillis = 1000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // --- FAZ 8.6-F1-R4 ZORUNLU ACK DOĞRULAMA TESTLERİ (1-25) ---

    // 1. unknown operationType reddedilir ve snapshot değişmez
    @Test
    fun ack_unknownOperationType_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-unknown-op", description = "Netflix Unknown Op")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-unknown",
            entityId = verId,
            operationTypeCode = "UNKNOWN",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 2. CREATE baseVersion non-null ise reddedilir ve snapshot değişmez
    @Test
    fun ack_createBaseVersionNonNull_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-create-bv", description = "Netflix Create Bv")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-create-bv",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 3. UPDATE baseVersion null ve 0 reddedilir ve snapshot değişmez
    @Test
    fun ack_updateBaseVersionNullOrZero_failsClosedAndRollsBack() = runTest {
        val invalidBaseVersions = listOf<Long?>(null, 0L)
        for ((idx, badBv) in invalidBaseVersions.withIndex()) {
            val trx = sampleTransaction(id = "trx-r4-upd-bv-$idx", description = "Netflix Upd Bv $idx")
            db.transactionDao().upsert(trx)
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
            db.localMutationDao().upsertUserMerchantVerificationRow(local)

            val op = sampleInFlightOperation(
                operationId = "op-r4-upd-bv-$idx",
                entityId = verId,
                operationTypeCode = "UPDATE",
                baseVersion = badBv,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            )
            db.localMutationDao().insertOutboxRow(op)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<Exception> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = op.operationId,
                    record = sampleRemoteAckDto(verId, alias, version = 2L),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 4. DELETE baseVersion null ve 0 reddedilir ve snapshot değişmez
    @Test
    fun ack_deleteBaseVersionNullOrZero_failsClosedAndRollsBack() = runTest {
        val invalidBaseVersions = listOf<Long?>(null, 0L)
        for ((idx, badBv) in invalidBaseVersions.withIndex()) {
            val trx = sampleTransaction(id = "trx-r4-del-bv-$idx", description = "Netflix Del Bv $idx")
            db.transactionDao().upsert(trx)
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 1L, baseVersion = 1L, deletedAt = 1000L)
            db.localMutationDao().upsertUserMerchantVerificationRow(local)

            val op = sampleInFlightOperation(
                operationId = "op-r4-del-bv-$idx",
                entityId = verId,
                operationTypeCode = "DELETE",
                baseVersion = badBv,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
            )
            db.localMutationDao().insertOutboxRow(op)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<Exception> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = op.operationId,
                    record = sampleRemoteAckDto(verId, alias, version = 2L, deletedAt = "2026-10-19T10:00:00Z"),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 5. CREATE remoteVersion != 1 reddedilir ve snapshot değişmez
    @Test
    fun ack_createRemoteVersionNotOne_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-create-remver", description = "Netflix Create RemVer")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-create-remver",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 2L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 6. UPDATE remoteVersion mismatch (base'den küçük, base'e eşit, base+1'den büyük) reddedilir
    @Test
    fun ack_updateRemoteVersionMismatch_failsClosedAndRollsBack() = runTest {
        val badRemoteVersions = listOf(1L, 2L, 4L) // baseVersion = 2L iken beklenen 3L'dir
        for ((idx, badVer) in badRemoteVersions.withIndex()) {
            val trx = sampleTransaction(id = "trx-r4-upd-remver-$idx", description = "Netflix Upd RemVer $idx")
            db.transactionDao().upsert(trx)
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
            db.localMutationDao().upsertUserMerchantVerificationRow(local)

            val op = sampleInFlightOperation(
                operationId = "op-r4-upd-remver-$idx",
                entityId = verId,
                operationTypeCode = "UPDATE",
                baseVersion = 2L,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            )
            db.localMutationDao().insertOutboxRow(op)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = op.operationId,
                    record = sampleRemoteAckDto(verId, alias, version = badVer),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 7. DELETE remoteVersion != base+1 reddedilir ve snapshot değişmez
    @Test
    fun ack_deleteRemoteVersionMismatch_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-del-remver", description = "Netflix Del RemVer")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 2L, baseVersion = 2L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-del-remver",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 4L, deletedAt = "2026-10-19T10:00:00Z"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 8. baseVersion == Long.MAX_VALUE reddedilir ve snapshot değişmez
    @Test
    fun ack_baseVersionLongMaxValue_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-maxval", description = "Netflix MaxVal")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = Long.MAX_VALUE, baseVersion = Long.MAX_VALUE)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-maxval",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = Long.MAX_VALUE,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = Long.MAX_VALUE),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 9. Bozuk deterministic remote ID reddedilir ve snapshot değişmez
    @Test
    fun ack_malformedDeterministicRemoteId_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-bad-det-id", description = "Netflix Bad Det Id")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-bad-det-id",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        val wrongUuid = "33333333-3333-4333-8333-333333333333"
        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(wrongUuid, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 10. Canonical olmayan remote alias reddedilir ve snapshot değişmez
    @Test
    fun ack_nonCanonicalRemoteAlias_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-noncanon-alias", description = "Netflix NonCanon")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-noncanon-alias",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, "netflix noncanon", version = 1L), // Normalize değil (küçük harf)
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 11. ASSIGN + null merchantId reddedilir ve snapshot değişmez
    @Test
    fun ack_assignNullMerchant_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-assign-null-m", description = "Netflix Assign Null M")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-assign-null-m",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L, decisionCode = "ASSIGN", merchantId = null),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 12. BLOCK + non-null merchantId reddedilir ve snapshot değişmez
    @Test
    fun ack_blockNonNullMerchant_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-block-notnull-m", description = "Netflix Block NotNull M")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "BLOCK", null, syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-block-notnull-m",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L, decisionCode = "BLOCK", merchantId = "netflix"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 13. Malformed createdAt reddedilir ve snapshot değişmez
    @Test
    fun ack_malformedCreatedAt_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-bad-created", description = "Netflix Bad Created")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-bad-created",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L, createdAt = "invalid-date-format"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 14. Null veya malformed updatedAt reddedilir ve snapshot değişmez
    @Test
    fun ack_nullOrMalformedUpdatedAt_failsClosedAndRollsBack() = runTest {
        val badUpdatedAts = listOf(null, "invalid-updated-at")
        for ((idx, badUpdated) in badUpdatedAts.withIndex()) {
            val trx = sampleTransaction(id = "trx-r4-bad-updated-$idx", description = "Netflix Bad Updated $idx")
            db.transactionDao().upsert(trx)
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            db.localMutationDao().upsertUserMerchantVerificationRow(local)

            val op = sampleInFlightOperation(
                operationId = "op-r4-bad-updated-$idx",
                entityId = verId,
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
            )
            db.localMutationDao().insertOutboxRow(op)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = op.operationId,
                    record = sampleRemoteAckDto(verId, alias, version = 1L, updatedAt = badUpdated),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 15. Malformed deletedAt reddedilir ve snapshot değişmez
    @Test
    fun ack_malformedDeletedAt_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-bad-delat", description = "Netflix Bad DelAt")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 1L, baseVersion = 1L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-bad-delat",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 2L, deletedAt = "invalid-deleted-at"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 16. CREATE/UPDATE remote tombstone reddedilir ve snapshot değişmez
    @Test
    fun ack_createOrUpdateRemoteTombstone_failsClosedAndRollsBack() = runTest {
        val ops = listOf("CREATE", "UPDATE")
        for ((idx, opType) in ops.withIndex()) {
            val trx = sampleTransaction(id = "trx-r4-tombstone-$idx", description = "Netflix Tombstone $idx")
            db.transactionDao().upsert(trx)
            val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
            val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

            val baseVer = if (opType == "CREATE") null else 1L
            val local = sampleEntity(
                id = verId,
                userId = userA,
                alias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                syncStatus = if (opType == "CREATE") "PENDING_CREATE" else "PENDING_UPDATE",
                version = if (opType == "CREATE") 0L else 1L,
                baseVersion = baseVer,
            )
            db.localMutationDao().upsertUserMerchantVerificationRow(local)

            val payload = if (opType == "CREATE") UserMerchantVerificationPayloadCodec.encodeCreate(local) else UserMerchantVerificationPayloadCodec.encodeUpdate(local)
            val op = sampleInFlightOperation(
                operationId = "op-r4-tombstone-$idx",
                entityId = verId,
                operationTypeCode = opType,
                baseVersion = baseVer,
                payloadJson = payload,
            )
            db.localMutationDao().insertOutboxRow(op)
            val snapshotBefore = captureSnapshot(trx.id, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackUserMerchantVerificationWriteV2(
                    syncScopeKey = syncScopeA,
                    operationId = op.operationId,
                    record = sampleRemoteAckDto(verId, alias, version = if (opType == "CREATE") 1L else 2L, deletedAt = "2026-10-19T10:00:00Z"),
                    nowEpochMillis = 2000L,
                )
            }

            val snapshotAfter = captureSnapshot(trx.id, alias)
            assertEquals(snapshotBefore, snapshotAfter)
        }
    }

    // 17. DELETE remote tombstone eksikliği reddedilir ve snapshot değişmez
    @Test
    fun ack_deleteRemoteTombstoneMissing_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-del-notomb", description = "Netflix Del NoTomb")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 1L, baseVersion = 1L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-del-notomb",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 2L, deletedAt = null), // deletedAt null olamaz!
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 18. Malformed operation payload reddedilir ve snapshot değişmez
    @Test
    fun ack_malformedOperationPayload_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-bad-op-payload", description = "Netflix Bad Op Payload")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-bad-op-payload",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = "{broken json payload",
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 19. Operation payload id/alias/decision/merchant mismatch reddedilir ve snapshot değişmez
    @Test
    fun ack_operationPayloadMismatch_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-payload-mismatch", description = "Netflix Payload Mismatch")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "BLOCK", null, syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        // Payload BLOCK iken remote record ASSIGN
        val op = sampleInFlightOperation(
            operationId = "op-r4-payload-mismatch",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L, decisionCode = "ASSIGN", merchantId = "netflix"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 20. Successor yokken local entity mismatch reddedilir ve snapshot değişmez
    @Test
    fun ack_noSuccessor_localEntityMismatch_failsClosedAndRollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-r4-loc-mismatch", description = "Netflix Loc Mismatch")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        // Yerel entity PENDING_UPDATE durumunda ama ACK edilen operasyon CREATE
        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val createEntity = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        val op = sampleInFlightOperation(
            operationId = "op-r4-loc-mismatch",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(createEntity),
        )
        db.localMutationDao().insertOutboxRow(op)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 21. Successor varken predecessor payload/remote mismatch reddedilir ve snapshot değişmez
    @Test
    fun ack_withSuccessor_predecessorPayloadRemoteMismatch_failsClosedAndRollsBack() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-r4-succ-pred-mismatch")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
        val snapshotBefore = captureSnapshot(trx.id, alias)

        // rootOp ASSIGN iken remote record BLOCK gönderiliyor
        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = rootOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L, decisionCode = "BLOCK", merchantId = null),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // 22. Geçerli CREATE ACK: Room'a tam yazılır, outbox silinir
    @Test
    fun ack_validCreateAck_succeeds() = runTest {
        val trx = sampleTransaction(id = "trx-r4-valid-create", description = "Netflix Valid Create")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-valid-create",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        db.localMutationDao().insertOutboxRow(op)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = op.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 1L),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        assertNull(db.syncOperationDao().getById(syncScopeA, op.operationId))
        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("SYNCED", stored.sync.syncStatus)
        assertEquals(1L, stored.sync.version)
        assertEquals(1L, stored.sync.baseVersion)
        assertEquals("ASSIGN", stored.decisionCode)
        assertEquals("netflix", stored.merchantId)
    }

    // 23. Geçerli UPDATE ACK: Room'a tam yazılır, outbox silinir
    @Test
    fun ack_validUpdateAck_succeeds() = runTest {
        val trx = sampleTransaction(id = "trx-r4-valid-update", description = "Netflix Valid Update")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-valid-update",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        db.localMutationDao().insertOutboxRow(op)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = op.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 2L, decisionCode = "BLOCK", merchantId = null),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        assertNull(db.syncOperationDao().getById(syncScopeA, op.operationId))
        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("SYNCED", stored.sync.syncStatus)
        assertEquals(2L, stored.sync.version)
        assertEquals(2L, stored.sync.baseVersion)
        assertEquals("BLOCK", stored.decisionCode)
        assertNull(stored.merchantId)
    }

    // 24. Geçerli DELETE ACK: Room'a tam yazılır, outbox silinir
    @Test
    fun ack_validDeleteAck_succeeds() = runTest {
        val trx = sampleTransaction(id = "trx-r4-valid-delete", description = "Netflix Valid Delete")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 2L, baseVersion = 2L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val op = sampleInFlightOperation(
            operationId = "op-r4-valid-delete",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        db.localMutationDao().insertOutboxRow(op)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = op.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 3L, deletedAt = "2026-10-19T10:00:00Z"),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        assertNull(db.syncOperationDao().getById(syncScopeA, op.operationId))
        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("SYNCED", stored.sync.syncStatus)
        assertEquals(3L, stored.sync.version)
        assertEquals(3L, stored.sync.baseVersion)
        assertNotNull(stored.sync.deletedAtEpochMillis)
    }

    // 25. Geçerli predecessor ACK + canonical successor; latest local karar korunur
    @Test
    fun ack_validPredecessorAck_withCanonicalSuccessor_preservesLatestDecision() = runTest {
        val (trx, rootOp, succOp) = setupInFlightCreateAndBlockedSuccessor("trx-r4-valid-pred-succ")
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = rootOp.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 1L, decisionCode = "ASSIGN", merchantId = "netflix"),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        // Predecessor silinmiş olmalı
        assertNull(db.syncOperationDao().getById(syncScopeA, rootOp.operationId))

        // Successor unblock edilmiş, baseVersion 1L olmuş olmalı
        val unblocked = db.syncOperationDao().getById(syncScopeA, succOp.operationId)!!
        assertFalse(unblocked.isBlocked)
        assertEquals(1L, unblocked.baseVersion)
        assertEquals("UPDATE", unblocked.operationTypeCode)
        assertEquals("PENDING", unblocked.statusCode)

        // En güncel yerel karar (BLOCK) remote predecessor (ASSIGN) tarafından ezilmemiş olmalı!
        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("BLOCK", stored.decisionCode)
        assertNull(stored.merchantId)
        assertEquals(1L, stored.sync.version)
    }

    // F1-R5 Zorunlu Test 1: CREATE predecessor + corrupted local syncStatus -> rollback
    @Test
    fun f1r5_ack_createPredecessor_corruptedLocalSyncStatus_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-c1", description = "Netflix C1")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 0L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-c1-pred",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-c1-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 2: CREATE predecessor + local version != 0 -> rollback
    @Test
    fun f1r5_ack_createPredecessor_localVersionNonZero_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-c2", description = "Netflix C2")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 1L, baseVersion = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-c2-pred",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-c2-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 3: CREATE predecessor + local baseVersion != null -> rollback
    @Test
    fun f1r5_ack_createPredecessor_localBaseVersionNonNull_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-c3", description = "Netflix C3")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = 1L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-c3-pred",
            entityId = verId,
            operationTypeCode = "CREATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-c3-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = null,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 1L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 4: UPDATE predecessor + local syncStatus != PENDING_UPDATE -> rollback
    @Test
    fun f1r5_ack_updatePredecessor_corruptedLocalSyncStatus_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-u4", description = "Netflix U4")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 2L, baseVersion = 2L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-u4-pred",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-u4-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 3L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 5: UPDATE predecessor + local version/baseVersion != operation baseVersion -> rollback
    @Test
    fun f1r5_ack_updatePredecessor_localVersionMismatch_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-u5", description = "Netflix U5")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 3L, baseVersion = 3L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-u5-pred",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local.copy(sync = local.sync.copy(version = 2L, baseVersion = 2L))),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-u5-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local.copy(sync = local.sync.copy(version = 2L, baseVersion = 2L))),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 3L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 6: UPDATE predecessor + successor.baseVersion mismatch -> rollback
    @Test
    fun f1r5_ack_updatePredecessor_successorBaseVersionMismatch_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-u6", description = "Netflix U6")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-u6-pred",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-u6-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 3L),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 7: Valid UPDATE predecessor + blocked UPDATE successor -> predecessor silinir, local karar korunur, B+1 rebase/unblock
    @Test
    fun f1r5_ack_validUpdatePredecessor_withBlockedSuccessor_appliesRebaseAndPreservesLocalDecision() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-u7", description = "Netflix U7")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predEntity = local.copy(decisionCode = "ASSIGN", merchantId = "netflix")
        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-u7-pred",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(predEntity),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-u7-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = predOp.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 3L, decisionCode = "ASSIGN", merchantId = "netflix"),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        assertNull(db.syncOperationDao().getById(syncScopeA, predOp.operationId))

        val unblocked = db.syncOperationDao().getById(syncScopeA, succOp.operationId)!!
        assertFalse(unblocked.isBlocked)
        assertEquals(3L, unblocked.baseVersion)
        assertEquals("UPDATE", unblocked.operationTypeCode)
        assertEquals("PENDING", unblocked.statusCode)

        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("BLOCK", stored.decisionCode)
        assertNull(stored.merchantId)
        assertEquals(3L, stored.sync.version)
        assertEquals(3L, stored.sync.baseVersion)
        assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
    }

    // F1-R5 Zorunlu Test 8: DELETE predecessor + resurrection UPDATE successor local tombstone taşıyorsa -> rollback
    @Test
    fun f1r5_ack_deletePredecessor_resurrectionLocalHasTombstone_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-d8", description = "Netflix D8")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-d8-pred",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local.copy(sync = local.sync.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 1000L))),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-d8-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 3L, deletedAt = "2026-10-19T10:00:00Z"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 9: DELETE predecessor + successor.baseVersion mismatch -> rollback
    @Test
    fun f1r5_ack_deletePredecessor_successorBaseVersionMismatch_rollsBack() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-d9", description = "Netflix D9")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L, deletedAt = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-d9-pred",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local.copy(sync = local.sync.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 1000L))),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-d9-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 1L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        assertFailsWith<IllegalStateException> {
            db.localMutationDao().ackUserMerchantVerificationWriteV2(
                syncScopeKey = syncScopeA,
                operationId = predOp.operationId,
                record = sampleRemoteAckDto(verId, alias, version = 3L, deletedAt = "2026-10-19T10:00:00Z"),
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 10: Valid DELETE predecessor + UPDATE resurrection successor -> local karar korunur ve B+1 rebase/unblock
    @Test
    fun f1r5_ack_validDeletePredecessor_withUpdateResurrectionSuccessor_appliesRebaseAndPreservesLocalDecision() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-d10", description = "Netflix D10")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix_tr", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L, deletedAt = null)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-d10-pred",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local.copy(sync = local.sync.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 1000L))),
        )
        val succOp = SyncOperationEntity(
            syncScopeKey = syncScopeA,
            operationId = "op-f1r5-d10-succ",
            entityTypeCode = "USER_MERCHANT_VERIFICATION",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
            predecessorOperationId = predOp.operationId,
            isBlocked = true,
            protocolVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        db.localMutationDao().insertOutboxRow(predOp)
        db.localMutationDao().insertOutboxRow(succOp)

        val acked = db.localMutationDao().ackUserMerchantVerificationWriteV2(
            syncScopeKey = syncScopeA,
            operationId = predOp.operationId,
            record = sampleRemoteAckDto(verId, alias, version = 3L, decisionCode = "ASSIGN", merchantId = "netflix_tr", deletedAt = "2026-10-19T10:00:00Z"),
            nowEpochMillis = 2000L,
        )
        assertTrue(acked)

        assertNull(db.syncOperationDao().getById(syncScopeA, predOp.operationId))

        val unblocked = db.syncOperationDao().getById(syncScopeA, succOp.operationId)!!
        assertFalse(unblocked.isBlocked)
        assertEquals(3L, unblocked.baseVersion)
        assertEquals("UPDATE", unblocked.operationTypeCode)
        assertEquals("PENDING", unblocked.statusCode)

        val stored = db.localMutationDao().getUserMerchantVerificationRow(verId)!!
        assertEquals("ASSIGN", stored.decisionCode)
        assertEquals("netflix_tr", stored.merchantId)
        assertNull(stored.sync.deletedAtEpochMillis)
        assertEquals(3L, stored.sync.version)
        assertEquals(3L, stored.sync.baseVersion)
        assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
    }

    // F1-R5 Zorunlu Test 11: IN_FLIGHT UPDATE üzerine DELETE successor üretme denemesi -> sıfır yazım
    @Test
    fun f1r5_mutate_inFlightUpdate_deleteSuccessorAttempt_rejectedWithZeroWrite() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-m11", description = "Netflix M11")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-m11-inflight",
            entityId = verId,
            operationTypeCode = "UPDATE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeUpdate(local),
        )
        db.localMutationDao().insertOutboxRow(predOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        val deleteEntity = local.copy(sync = local.sync.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 2000L))
        val deletePayload = UserMerchantVerificationPayloadCodec.encodeDelete(deleteEntity)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().mutateUserMerchantVerificationV2(
                syncScopeKey = syncScopeA,
                entity = deleteEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = deletePayload,
                operationIdFactory = { "op-f1r5-m11-succ" },
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    // F1-R5 Zorunlu Test 12: IN_FLIGHT DELETE üzerine DELETE successor üretme denemesi -> sıfır yazım
    @Test
    fun f1r5_mutate_inFlightDelete_deleteSuccessorAttempt_rejectedWithZeroWrite() = runTest {
        val trx = sampleTransaction(id = "trx-f1r5-m12", description = "Netflix M12")
        db.transactionDao().upsert(trx)
        val alias = TurkishDescriptionNormalizer.normalize(trx.description!!)
        val verId = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)

        val local = sampleEntity(verId, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_DELETE", version = 2L, baseVersion = 2L, deletedAt = 1000L)
        db.localMutationDao().upsertUserMerchantVerificationRow(local)

        val predOp = sampleInFlightOperation(
            operationId = "op-f1r5-m12-inflight",
            entityId = verId,
            operationTypeCode = "DELETE",
            baseVersion = 2L,
            payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(local),
        )
        db.localMutationDao().insertOutboxRow(predOp)

        val snapshotBefore = captureSnapshot(trx.id, alias)

        val deleteEntity = local.copy(sync = local.sync.copy(syncStatus = "PENDING_DELETE", deletedAtEpochMillis = 2000L))
        val deletePayload = UserMerchantVerificationPayloadCodec.encodeDelete(deleteEntity)

        assertFailsWith<IllegalArgumentException> {
            db.localMutationDao().mutateUserMerchantVerificationV2(
                syncScopeKey = syncScopeA,
                entity = deleteEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = deletePayload,
                operationIdFactory = { "op-f1r5-m12-succ" },
                nowEpochMillis = 2000L,
            )
        }

        val snapshotAfter = captureSnapshot(trx.id, alias)
        assertEquals(snapshotBefore, snapshotAfter)
    }

    private class FakeAuthRepository(private val userId: String) : AuthRepository {
        private val sessionFlow = MutableStateFlow<AuthSession?>(
            AuthSession(
                userId = EntityId(userId),
                email = "test@example.com",
                expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(2_000_000L),
            ),
        )

        fun setSession(session: AuthSession?) {
            sessionFlow.value = session
        }

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<com.feniqo.mobile.domain.model.UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("unused")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("unused")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("unused")
        override suspend fun signOut(): RepositoryResult<Unit> = error("unused")
    }

    private class FakeVerificationRepository : UserMerchantVerificationRepository {
        override fun observeActiveVerifications(): Flow<List<com.feniqo.mobile.domain.model.UserMerchantVerification>> =
            MutableStateFlow(emptyList())

        override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult =
            UserMerchantVerificationLookupResult.NotFound

        override suspend fun assignMerchant(alias: String, merchantId: String) = error("unused")
        override suspend fun blockMerchant(alias: String) = error("unused")
        override suspend fun deleteVerification(alias: String) = error("unused")
    }
}
