package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
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
class TransactionMerchantCreateAtomicDaoTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var mutationDao: LocalMutationDao
    private lateinit var queue: OfflineWriteQueue
    private lateinit var spyScheduler: SpyBackgroundSyncScheduler

    private val userId = "11111111-1111-4111-8111-111111111111"
    private val syncScopeKey = SyncScopeKey.forUser(userId)
    private val groceriesCategoryId = "cat-groceries-atom-1"
    private val foodDiningCategoryId = "cat-food-dining-atom-1"
    private val shoppingCategoryId = "cat-shopping-atom-1"
    private val personalCareCategoryId = "cat-personal-care-atom-1"
    private val systemGroceriesCategoryId = "cat-system-groceries-atom-1"
    private var opIndex = 0

    @BeforeTest
    fun setup() =
        runTest {
            val builder =
                Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
                    context = ApplicationProvider.getApplicationContext(),
                    factory = { FeniqoDatabaseConstructor.initialize() },
                )
            builder.allowMainThreadQueries()
            db = builder.build()

            mutationDao = db.localMutationDao()
            spyScheduler = SpyBackgroundSyncScheduler()

            queue =
                OfflineWriteQueue(
                    mutationDao = mutationDao,
                    operationDao = db.syncOperationDao(),
                    syncScheduler = spyScheduler,
                    nowEpochMillisProvider = { 1000L },
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                )

            // Seed personal category (isDefault = false, ownerId = userId, workspaceId = null)
            db.categoryDao().upsert(
                CategoryEntity(
                    id = foodDiningCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Yeme İçme",
                    normalizedName = "YEME ICME",
                    slug = "food-dining-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#E91E63",
                    iconKey = "food_dining",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            db.categoryDao().upsert(
                CategoryEntity(
                    id = personalCareCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Kişisel Bakım",
                    normalizedName = "KISISEL BAKIM",
                    slug = "personal-care-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#E91E63",
                    iconKey = "personal_care",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            db.categoryDao().upsert(
                CategoryEntity(
                    id = shoppingCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Alışveriş",
                    normalizedName = "ALISVERIS",
                    slug = "shopping-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#9C27B0",
                    iconKey = "shopping",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            db.categoryDao().upsert(
                CategoryEntity(
                    id = groceriesCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Market",
                    normalizedName = "MARKET",
                    slug = "groceries-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            // Seed system default category (isDefault = true, ownerId = null, workspaceId = null)
            db.categoryDao().upsert(
                CategoryEntity(
                    id = systemGroceriesCategoryId,
                    ownerId = null,
                    workspaceId = null,
                    scopeKey = "system",
                    name = "Sistem Market",
                    normalizedName = "SISTEM MARKET",
                    slug = "system-groceries",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = true,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun buildTransactionEntity(
        id: String = "tx-atom-1",
        description: String = "MİGROS TİCARET",
        categoryId: String = groceriesCategoryId,
    ): TransactionEntity =
        TransactionEntity(
            id = id,
            ownerId = userId,
            workspaceId = null,
            amountMinor = 25000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            categoryId = categoryId,
            description = description,
            paymentMethodCode = "CREDIT_CARD",
            transactionDate = "2026-10-21",
            receiptPath = null,
            installmentNumber = null,
            totalInstallments = null,
            installmentGroupId = null,
            createdAtEpochMillis = 1000L,
            paidByUserId = userId,
            splitMode = "EQUAL",
            searchText = description,
            merchantId = null,
            merchantMatchSourceCode = null,
            merchantConfidence = null,
            merchantCatalogVersion = null,
            sync =
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

    private fun getAllTransactionsSnapshot(): List<TransactionEntity> {
        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM transactions")
        val list = mutableListOf<TransactionEntity>()
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("id")
            val descIdx = it.getColumnIndexOrThrow("description")
            val merchantIdIdx = it.getColumnIndexOrThrow("merchant_id")
            val matchSourceIdx = it.getColumnIndexOrThrow("merchant_match_source_code")
            val confIdx = it.getColumnIndexOrThrow("merchant_confidence")
            val catalogVerIdx = it.getColumnIndexOrThrow("merchant_catalog_version")

            while (it.moveToNext()) {
                list.add(
                    buildTransactionEntity(
                        id = it.getString(idIdx),
                        description = it.getString(descIdx),
                    ).copy(
                        merchantId = if (it.isNull(merchantIdIdx)) null else it.getString(merchantIdIdx),
                        merchantMatchSourceCode = if (it.isNull(matchSourceIdx)) null else it.getString(matchSourceIdx),
                        merchantConfidence = if (it.isNull(confIdx)) null else it.getInt(confIdx),
                        merchantCatalogVersion = if (it.isNull(catalogVerIdx)) null else it.getInt(catalogVerIdx),
                    ),
                )
            }
        }
        return list
    }

    private fun getAllVerificationsSnapshot(): List<UserMerchantVerificationEntity> {
        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM user_merchant_verifications")
        val list = mutableListOf<UserMerchantVerificationEntity>()
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("id")
            val ownerIdIdx = it.getColumnIndexOrThrow("owner_id")
            val scopeKeyIdx = it.getColumnIndexOrThrow("scope_key")
            val normAliasIdx = it.getColumnIndexOrThrow("normalized_alias")
            val decisionCodeIdx = it.getColumnIndexOrThrow("decision_code")
            val merchantIdIdx = it.getColumnIndexOrThrow("merchant_id")
            val scopeCodeIdx = it.getColumnIndexOrThrow("scope_code")
            val syncStatusIdx = it.getColumnIndexOrThrow("sync_status")
            val versionIdx = it.getColumnIndexOrThrow("version")
            val baseVersionIdx = it.getColumnIndexOrThrow("base_version")

            while (it.moveToNext()) {
                list.add(
                    UserMerchantVerificationEntity(
                        id = it.getString(idIdx),
                        ownerId = it.getString(ownerIdIdx),
                        workspaceId = null,
                        scopeKey = it.getString(scopeKeyIdx),
                        normalizedAlias = it.getString(normAliasIdx),
                        decisionCode = it.getString(decisionCodeIdx),
                        merchantId = if (it.isNull(merchantIdIdx)) null else it.getString(merchantIdIdx),
                        scopeCode = it.getString(scopeCodeIdx),
                        createdAtEpochMillis = 1000L,
                        sync =
                            SyncMetadata(
                                syncStatus = it.getString(syncStatusIdx),
                                updatedAtEpochMillis = 1000L,
                                localUpdatedAtEpochMillis = 1000L,
                                deletedAtEpochMillis = null,
                                version = it.getLong(versionIdx),
                                baseVersion = if (it.isNull(baseVersionIdx)) null else it.getLong(baseVersionIdx),
                                lastSyncError = null,
                            ),
                    ),
                )
            }
        }
        return list
    }

    private fun getAllOperationsSnapshot(): List<SyncOperationEntity> {
        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM sync_operations ORDER BY operation_id")
        val list = mutableListOf<SyncOperationEntity>()
        cursor.use {
            val opIdIdx = it.getColumnIndexOrThrow("operation_id")
            val syncScopeIdx = it.getColumnIndexOrThrow("sync_scope_key")
            val entityTypeIdx = it.getColumnIndexOrThrow("entity_type_code")
            val entityIdIdx = it.getColumnIndexOrThrow("entity_id")
            val opTypeIdx = it.getColumnIndexOrThrow("operation_type_code")
            val baseVersionIdx = it.getColumnIndexOrThrow("base_version")
            val payloadJsonIdx = it.getColumnIndexOrThrow("payload_json")
            val predOpIdIdx = it.getColumnIndexOrThrow("predecessor_operation_id")
            val isBlockedIdx = it.getColumnIndexOrThrow("is_blocked")
            val protoVerIdx = it.getColumnIndexOrThrow("protocol_version")
            val statusIdx = it.getColumnIndexOrThrow("status_code")
            val attemptIdx = it.getColumnIndexOrThrow("attempt_count")
            val lastErrorIdx = it.getColumnIndexOrThrow("last_error")
            val nextAttemptIdx = it.getColumnIndexOrThrow("next_attempt_at_epoch_ms")
            val createdIdx = it.getColumnIndexOrThrow("created_at_epoch_ms")
            val updatedIdx = it.getColumnIndexOrThrow("updated_at_epoch_ms")
            val errClassIdx = it.getColumnIndexOrThrow("error_classification")

            while (it.moveToNext()) {
                list.add(
                    SyncOperationEntity(
                        operationId = it.getString(opIdIdx),
                        syncScopeKey = it.getString(syncScopeIdx),
                        entityTypeCode = it.getString(entityTypeIdx),
                        entityId = it.getString(entityIdIdx),
                        operationTypeCode = it.getString(opTypeIdx),
                        baseVersion = if (it.isNull(baseVersionIdx)) null else it.getLong(baseVersionIdx),
                        payloadJson = if (it.isNull(payloadJsonIdx)) null else it.getString(payloadJsonIdx),
                        predecessorOperationId = if (it.isNull(predOpIdIdx)) null else it.getString(predOpIdIdx),
                        isBlocked = it.getInt(isBlockedIdx) != 0,
                        protocolVersion = it.getInt(protoVerIdx),
                        statusCode = it.getString(statusIdx),
                        attemptCount = it.getInt(attemptIdx),
                        lastError = if (it.isNull(lastErrorIdx)) null else it.getString(lastErrorIdx),
                        nextAttemptAtEpochMillis = it.getLong(nextAttemptIdx),
                        createdAtEpochMillis = it.getLong(createdIdx),
                        updatedAtEpochMillis = it.getLong(updatedIdx),
                        errorClassification = if (it.isNull(errClassIdx)) null else it.getString(errClassIdx),
                    ),
                )
            }
        }
        return list
    }

    @Test
    fun knownMerchant_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-1", description = "MİGROS")
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // Snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-1", savedTx.id)
            assertEquals("migros", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("MIGROS", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("migros", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun knownMerchant_kofteciYusuf_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-kofteci-1", description = "KÖFTECİ YUSUF", categoryId = foodDiningCategoryId)
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("kofteci_yusuf"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-kofteci-1", savedTx.id)
            assertEquals("kofteci_yusuf", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("KOFTECI YUSUF", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("kofteci_yusuf", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun knownMerchant_kofteciYusuf_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert (midway through chain)
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_kofteci_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on kofteci yusuf verification outbox');
                END;
                """.trimIndent(),
            )

            val tx = buildTransactionEntity(id = "tx-atom-kofteci-fail", description = "KÖFTECİ YUSUF", categoryId = foodDiningCategoryId)

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("kofteci_yusuf"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // Zero writes: transaction, verification and outbox tables must remain completely empty
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must remain unchanged")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must remain unchanged")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations table must remain unchanged")
        }

    @Test
    fun knownMerchant_vatanBilgisayar_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val tx =
                buildTransactionEntity(
                    id = "tx-atom-vatan-1",
                    description = "VATAN BİLGİSAYAR",
                    categoryId = shoppingCategoryId,
                )

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("vatan_bilgisayar"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-vatan-1", savedTx.id)
            assertEquals("vatan_bilgisayar", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("VATAN BILGISAYAR", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("vatan_bilgisayar", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun knownMerchant_vatanBilgisayar_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert (midway through chain)
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_vatan_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on vatan bilgisayar verification outbox');
                END;
                """.trimIndent(),
            )

            val tx = buildTransactionEntity(id = "tx-atom-vatan-fail", description = "VATAN BİLGİSAYAR", categoryId = shoppingCategoryId)

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("vatan_bilgisayar"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // Zero writes: transaction, verification and outbox tables must remain completely empty
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must remain unchanged")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must remain unchanged")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations table must remain unchanged")
        }

    @Test
    fun knownMerchant_gratis_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val tx =
                buildTransactionEntity(
                    id = "tx-atom-gratis-1",
                    description = "GRATIS KOZMETIK",
                    categoryId = personalCareCategoryId,
                )

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("gratis"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-gratis-1", savedTx.id)
            assertEquals("gratis", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("GRATIS KOZMETIK", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("gratis", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun knownMerchant_gratis_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert (midway through chain)
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_gratis_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on gratis verification outbox');
                END;
                """.trimIndent(),
            )

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-gratis-fail",
                    description = "GRATIS KOZMETIK",
                    categoryId = personalCareCategoryId,
                )

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("gratis"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // Zero writes: transaction, verification and outbox tables must remain completely empty
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must remain unchanged")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must remain unchanged")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations table must remain unchanged")
        }

    @Test
    fun knownMerchant_turknet_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val utilitiesCatId = "cat-turknet-util-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = utilitiesCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Turknet Fatura",
                    normalizedName = "TURKNET FATURA",
                    slug = "turknet-util-atom-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#607D8B",
                    iconKey = "utilities",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-turknet-1",
                    description = "TURKNET INTERNET",
                    categoryId = utilitiesCatId,
                )

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("turknet"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-turknet-1", savedTx.id)
            assertEquals("turknet", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("TURKNET INTERNET", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("turknet", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun knownMerchant_turknet_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert (midway through chain)
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_turknet_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on turknet verification outbox');
                END;
                """.trimIndent(),
            )

            val utilitiesCatId = "cat-turknet-util-abort-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = utilitiesCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Turknet Abort Fatura",
                    normalizedName = "TURKNET ABORT FATURA",
                    slug = "turknet-util-abort-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#607D8B",
                    iconKey = "utilities",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-turknet-fail",
                    description = "TURKNET INTERNET",
                    categoryId = utilitiesCatId,
                )

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("turknet"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // Zero writes: transaction, verification and outbox tables must remain completely empty
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must remain unchanged")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must remain unchanged")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations table must remain unchanged")
        }

    @Test
    fun knownMerchant_istanbulkart_success_writesTwoEntitiesAndTwoOutboxesAtomically() =
        runTest {
            val transportationCatId = "cat-trans-ik-atomic-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım",
                    normalizedName = "ULASIM",
                    slug = "trans-ik-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#2196F3",
                    iconKey = "transportation",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-ik-1",
                    description = "İSTANBULKART",
                    categoryId = transportationCatId,
                )
            val expectedTxPayload = """{"transaction_id":"tx-atom-ik-1","amount":1500,"currency":"TRY"}"""
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = expectedTxPayload,
                    decision = TransactionMerchantCreateDecision.KnownMerchant("istanbulkart"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-ik-1", savedTx.id)
            assertEquals("istanbulkart", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("ISTANBULKART", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("istanbulkart", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)

            val txOp = ops.firstOrNull { it.entityTypeCode == "TRANSACTION" }
            assertNotNull(txOp, "Transaction outbox operation must exist")
            assertEquals("CREATE", txOp.operationTypeCode)
            assertEquals(2, txOp.protocolVersion, "Transaction outbox must use protocol_version = 2")
            assertEquals(savedTx.id, txOp.entityId)
            assertEquals(result.transactionOperationId, txOp.operationId)
            assertEquals(syncScopeKey, txOp.syncScopeKey)
            assertEquals(expectedTxPayload, txOp.payloadJson)
            assertNull(txOp.baseVersion)

            val verifOp = ops.firstOrNull { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }
            assertNotNull(verifOp, "Verification outbox operation must exist")
            assertEquals("CREATE", verifOp.operationTypeCode)
            assertEquals(2, verifOp.protocolVersion, "Verification outbox must use protocol_version = 2")
            assertEquals(savedV.id, verifOp.entityId)
            assertEquals(result.verificationOperationId, verifOp.operationId)
            assertEquals(syncScopeKey, verifOp.syncScopeKey)
            assertNull(verifOp.baseVersion)
            assertNotNull(verifOp.payloadJson)

            val decodedPayload =
                UserMerchantVerificationPayloadCodec.parseAndValidate(
                    jsonString = verifOp.payloadJson,
                    operation = "CREATE",
                    baseVersion = null,
                )
            assertEquals(savedV.id, decodedPayload.id)
            assertEquals(userId, decodedPayload.userId)
            assertNull(decodedPayload.workspaceId)
            assertEquals(savedV.normalizedAlias, decodedPayload.normalizedAlias)
            assertEquals("ASSIGN", decodedPayload.decisionCode)
            assertEquals("istanbulkart", decodedPayload.merchantId)
            assertEquals("PERSONAL", decodedPayload.scopeCode)
        }

    @Test
    fun knownMerchant_ajet_travelCategory_successWritesAtomically() =
        runTest {
            val travelCatId = "cat-travel-ajet-atomic-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = travelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Seyahat",
                    normalizedName = "SEYAHAT",
                    slug = "travel-ajet-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#00BCD4",
                    iconKey = "travel",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-ajet-1",
                    description = "AJET UCUS",
                    categoryId = travelCatId,
                )
            val expectedTxPayload = """{"transaction_id":"tx-atom-ajet-1","amount":125000,"currency":"TRY"}"""
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = expectedTxPayload,
                    decision = TransactionMerchantCreateDecision.KnownMerchant("ajet"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-ajet-1", savedTx.id)
            assertEquals("ajet", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("AJET UCUS", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("ajet", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)

            val txOp = ops.firstOrNull { it.entityTypeCode == "TRANSACTION" }
            assertNotNull(txOp, "Transaction outbox operation must exist")
            assertEquals("CREATE", txOp.operationTypeCode)
            assertEquals(2, txOp.protocolVersion, "Transaction outbox must use protocol_version = 2")
            assertEquals(savedTx.id, txOp.entityId)
            assertEquals(result.transactionOperationId, txOp.operationId)
            assertEquals(syncScopeKey, txOp.syncScopeKey)
            assertEquals(expectedTxPayload, txOp.payloadJson)
            assertNull(txOp.baseVersion)

            val verifOp = ops.firstOrNull { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }
            assertNotNull(verifOp, "Verification outbox operation must exist")
            assertEquals("CREATE", verifOp.operationTypeCode)
            assertEquals(2, verifOp.protocolVersion, "Verification outbox must use protocol_version = 2")
            assertEquals(savedV.id, verifOp.entityId)
            assertEquals(result.verificationOperationId, verifOp.operationId)
            assertEquals(syncScopeKey, verifOp.syncScopeKey)
            assertNull(verifOp.baseVersion)
            assertNotNull(verifOp.payloadJson)

            val decodedPayload =
                UserMerchantVerificationPayloadCodec.parseAndValidate(
                    jsonString = verifOp.payloadJson,
                    operation = "CREATE",
                    baseVersion = null,
                )
            assertEquals(savedV.id, decodedPayload.id)
            assertEquals(userId, decodedPayload.userId)
            assertNull(decodedPayload.workspaceId)
            assertEquals(savedV.normalizedAlias, decodedPayload.normalizedAlias)
            assertEquals("ASSIGN", decodedPayload.decisionCode)
            assertEquals("ajet", decodedPayload.merchantId)
            assertEquals("PERSONAL", decodedPayload.scopeCode)
        }

    @Test
    fun knownMerchant_istanbulkart_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert (midway through chain)
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_istanbulkart_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on istanbulkart verification outbox');
                END;
                """.trimIndent(),
            )

            val transportationCatId = "cat-trans-ik-abort-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım Abort",
                    normalizedName = "ULASIM ABORT",
                    slug = "trans-ik-abort-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#2196F3",
                    iconKey = "transportation",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-ik-fail",
                    description = "İSTANBULKART",
                    categoryId = transportationCatId,
                )

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("istanbulkart"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // Zero writes: transaction, verification and outbox tables must remain completely empty
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must remain unchanged")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must remain unchanged")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations table must remain unchanged")
        }

    @Test
    fun useTransactionName_success_writesNullAttributionBlockAndTwoOutboxesAtomically() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-2", description = "MAHALLE KASABI")
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.UseTransactionName,
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)

            // Snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-2", savedTx.id)
            assertNull(savedTx.merchantId)
            assertNull(savedTx.merchantMatchSourceCode)
            assertNull(savedTx.merchantConfidence)
            assertNull(savedTx.merchantCatalogVersion)

            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            assertEquals("MAHALLE KASABI", savedV.normalizedAlias)
            assertEquals("BLOCK", savedV.decisionCode)
            assertNull(savedV.merchantId)

            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )
        }

    @Test
    fun verificationTopologyRejection_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            val normAlias = "MIGROS"
            val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, normAlias)

            // Seed a blocked tail with an invalid state to force mutateUserMerchantVerificationV2 to fail
            db.syncOperationDao().insert(
                SyncOperationEntity(
                    syncScopeKey = syncScopeKey,
                    operationId = "op-blocked-tail-001",
                    entityTypeCode = "USER_MERCHANT_VERIFICATION",
                    entityId = deterministicId,
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    payloadJson = "{}",
                    predecessorOperationId = null,
                    isBlocked = true, // isBlocked = true causes topology reject in mutateUserMerchantVerificationV2
                    protocolVersion = 2,
                    statusCode = "BLOCKED",
                    attemptCount = 1,
                    lastError = "simulated_block",
                    nextAttemptAtEpochMillis = 1000L,
                    createdAtEpochMillis = 1000L,
                    updatedAtEpochMillis = 1000L,
                ),
            )

            val tx = buildTransactionEntity(id = "tx-atom-fail", description = normAlias)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // İlgili tablo snapshot'larında sıfır yazım
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction table must have zero rows written")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification table must have zero rows written")
            val ops = getAllOperationsSnapshot()
            assertEquals(1, ops.size, "Only pre-existing seeded outbox op should exist")
            assertEquals("op-blocked-tail-001", ops[0].operationId)
        }

    @Test
    fun transactionOutboxInsertAbort_rollsBackVerificationAndTransactionEntities() =
        runTest {
            // Install SQLite trigger that aborts TRANSACTION outbox insert
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_tx_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'TRANSACTION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on transaction outbox insert');
                END;
                """.trimIndent(),
            )

            val tx = buildTransactionEntity(id = "tx-atom-abort-tx", description = "MİGROS")

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // True ACID rollback check: both verification and transaction must be completely rolled back!
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction entity must be rolled back")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification entity must be rolled back")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations must be rolled back")
        }

    @Test
    fun verificationOutboxInsertAbort_rollsBackEntireTransaction() =
        runTest {
            // Install SQLite trigger that aborts USER_MERCHANT_VERIFICATION outbox insert
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on verification outbox insert');
                END;
                """.trimIndent(),
            )

            val tx = buildTransactionEntity(id = "tx-atom-abort-verif", description = "MİGROS")

            assertFailsWith<android.database.sqlite.SQLiteException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // True ACID rollback check: zero writes across all tables
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction entity must not exist")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification entity must not exist")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations must not exist")
        }

    @Test
    fun existingVerification_preservesUpdateTopology() =
        runTest {
            val normAlias = "MIGROS"
            val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, normAlias)

            // Seed an existing SYNCED verification with BLOCK
            db.localMutationDao().upsertUserMerchantVerificationRow(
                UserMerchantVerificationEntity(
                    id = deterministicId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    normalizedAlias = normAlias,
                    decisionCode = "BLOCK",
                    merchantId = null,
                    scopeCode = "PERSONAL",
                    createdAtEpochMillis = 500L,
                    sync =
                        SyncMetadata(
                            syncStatus = SyncStatus.SYNCED.name,
                            updatedAtEpochMillis = 500L,
                            localUpdatedAtEpochMillis = 500L,
                            deletedAtEpochMillis = null,
                            version = 3L,
                            baseVersion = 3L,
                            lastSyncError = null,
                        ),
                ),
            )

            // Now user creates transaction with KnownMerchant("migros") -> transitions existing verification to ASSIGN with UPDATE outbox
            val tx = buildTransactionEntity(id = "tx-atom-topology", description = normAlias)
            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)

            // Verify entity updated
            val v = db.localMutationDao().getUserMerchantVerificationRow(deterministicId)
            assertNotNull(v)
            assertEquals("ASSIGN", v.decisionCode)
            assertEquals("migros", v.merchantId)
            assertEquals(SyncStatus.PENDING_UPDATE.name, v.sync.syncStatus)
            assertEquals(3L, v.sync.baseVersion)

            // Outbox operations: 1 UPDATE for verification, 1 CREATE for transaction
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val verifOp = ops.find { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }
            assertNotNull(verifOp)
            assertEquals("UPDATE", verifOp.operationTypeCode)

            val txOp = ops.find { it.entityTypeCode == "TRANSACTION" }
            assertNotNull(txOp)
            assertEquals("CREATE", txOp.operationTypeCode)
        }

    @Test
    fun syncScheduler_isCalledOnlyAfterSuccessfulRoomTransaction() =
        runTest {
            assertEquals(0, spyScheduler.scheduleCallCount)

            // 1. Successful execution
            val tx = buildTransactionEntity(id = "tx-atom-sched-1", description = "MİGROS")
            queue.enqueueTransactionWithMerchantDecisionV2(
                syncScopeKey = syncScopeKey,
                entity = tx,
                payloadJson = "{}",
                decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
            )
            assertEquals(1, spyScheduler.scheduleCallCount)

            // 2. Failed execution (category mismatch in DAO)
            val txBad = buildTransactionEntity(id = "tx-atom-sched-2", description = "MİGROS", categoryId = "non-existent-cat")
            assertFailsWith<IllegalArgumentException> {
                queue.enqueueTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    entity = txBad,
                    payloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                )
            }
            // Scheduler count must STILL be 1 (never incremented on failure)
            assertEquals(1, spyScheduler.scheduleCallCount)
        }

    @Test
    fun knownMerchant_refundDescription_rejectedAndZeroWrites() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-refund-km", description = "MİGROS İADE")

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction must not exist")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification must not exist")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Outbox operations must not exist")
        }

    @Test
    fun useTransactionName_refundDescription_rejectedAndZeroWrites() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-refund-utn", description = "MİGROS REFUND")

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.UseTransactionName,
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction must not exist")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification must not exist")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Outbox operations must not exist")
        }

    @Test
    fun category_deleted_rejectedAndZeroWrites() =
        runTest {
            val deletedCatId = "cat-deleted-atom"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = deletedCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Silinmiş",
                    normalizedName = "SILINMIS",
                    slug = "deleted-cat",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
                            syncStatus = SyncStatus.SYNCED.name,
                            updatedAtEpochMillis = 1000L,
                            localUpdatedAtEpochMillis = 1000L,
                            deletedAtEpochMillis = 2000L, // Deleted!
                            version = 1L,
                            baseVersion = 1L,
                            lastSyncError = null,
                        ),
                ),
            )

            val tx = buildTransactionEntity(id = "tx-atom-del-cat", categoryId = deletedCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_workspace_rejectedAndZeroWrites() =
        runTest {
            val wsId = "workspace-123"
            db.workspaceDao().upsertWorkspace(
                WorkspaceEntity(
                    id = wsId,
                    name = "Test WS",
                    normalizedName = "test ws",
                    ownerId = userId,
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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
            val wsCatId = "cat-ws-atom"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = wsCatId,
                    ownerId = userId,
                    workspaceId = wsId, // Workspace category!
                    scopeKey = "workspace:workspace-123",
                    name = "Workspace Market",
                    normalizedName = "WORKSPACE MARKET",
                    slug = "ws-groceries",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-ws-cat", categoryId = wsCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_income_rejectedAndZeroWrites() =
        runTest {
            val incomeCatId = "cat-income-atom"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = incomeCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Gelir",
                    normalizedName = "GELIR",
                    slug = "income-cat",
                    typeCode = "INCOME", // Income!
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-inc-cat", categoryId = incomeCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_otherUser_rejectedAndZeroWrites() =
        runTest {
            val otherCatId = "cat-other-user-atom"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = otherCatId,
                    ownerId = "other-user-999", // Other user!
                    workspaceId = null,
                    scopeKey = "user:other-user-999",
                    name = "Diğer Market",
                    normalizedName = "DIGER MARKET",
                    slug = "other-groceries",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-other-cat", categoryId = otherCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_corruptNullOwnerAndNonDefault_rejectedAndZeroWrites() =
        runTest {
            val corruptCatId = "cat-corrupt-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = corruptCatId,
                    ownerId = null, // null owner but not default!
                    workspaceId = null,
                    scopeKey = "corrupt",
                    name = "Bozuk",
                    normalizedName = "BOZUK",
                    slug = "corrupt-1",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-corrupt-1", categoryId = corruptCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_corruptOwnerAndDefault_rejectedAndZeroWrites() =
        runTest {
            val corruptCatId = "cat-corrupt-atom-2"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = corruptCatId,
                    ownerId = userId, // ownerId not null but default!
                    workspaceId = null,
                    scopeKey = "corrupt-2",
                    name = "Bozuk 2",
                    normalizedName = "BOZUK 2",
                    slug = "corrupt-2",
                    typeCode = "EXPENSE",
                    colorHex = "#4CAF50",
                    iconKey = "groceries",
                    isDefault = true,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-corrupt-2", categoryId = corruptCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun category_systemDefault_succeeds() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-sys-cat", categoryId = systemGroceriesCategoryId)

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertEquals(1, getAllTransactionsSnapshot().size)
            assertEquals(1, getAllVerificationsSnapshot().size)
            assertEquals(2, getAllOperationsSnapshot().size)
        }

    @Test
    fun transportationCategory_withTravelMerchantThy_atomicallySucceeds() =
        runTest {
            val transportationCatId = "cat-transportation-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım",
                    normalizedName = "ULASIM",
                    slug = "transportation-atom-1",
                    typeCode = "EXPENSE",
                    colorHex = "#0284C7",
                    iconKey = "transportation",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-thy-transp", description = "THY BİLET ALIMI", categoryId = transportationCatId)

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("thy"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)

            val txSnapshot = getAllTransactionsSnapshot()
            assertEquals(1, txSnapshot.size)
            val createdTx = txSnapshot.first()
            assertEquals("thy", createdTx.merchantId)
            assertEquals("USER_VERIFIED", createdTx.merchantMatchSourceCode)
            assertEquals(100, createdTx.merchantConfidence)

            val verSnapshot = getAllVerificationsSnapshot()
            assertEquals(1, verSnapshot.size)
            val createdVer = verSnapshot.first()
            assertEquals("ASSIGN", createdVer.decisionCode)
            assertEquals("thy", createdVer.merchantId)

            assertEquals(2, getAllOperationsSnapshot().size)
        }

    @Test
    fun travelCategory_withTravelMerchantThy_exactCategoryMatches_succeeds() =
        runTest {
            val travelCatId = "cat-travel-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = travelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Seyahat",
                    normalizedName = "SEYAHAT",
                    slug = "travel-atom-1",
                    typeCode = "EXPENSE",
                    colorHex = "#0891B2",
                    iconKey = "travel",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-thy-travel", description = "THY SEYAHAT", categoryId = travelCatId)

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("thy"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertEquals(1, getAllTransactionsSnapshot().size)
            assertEquals("thy", getAllTransactionsSnapshot().first().merchantId)
        }

    @Test
    fun groceriesCategory_withTravelMerchantThy_failsClosedWithoutWrites() =
        runTest {
            val tx = buildTransactionEntity(id = "tx-atom-groceries-thy", description = "THY MARKETTE", categoryId = groceriesCategoryId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("thy"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun fuelCategory_withFuelMerchantShell_atomicallySucceeds() =
        runTest {
            val fuelCatId = "cat-fuel-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = fuelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Akaryakıt",
                    normalizedName = "AKARYAKIT",
                    slug = "fuel-atom-1",
                    typeCode = "EXPENSE",
                    colorHex = "#F59E0B",
                    iconKey = "fuel",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-shell-fuel", description = "SHELL PETROL", categoryId = fuelCatId)

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("shell"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)

            val txSnapshot = getAllTransactionsSnapshot()
            assertEquals(1, txSnapshot.size)
            val createdTx = txSnapshot.first()
            assertEquals("shell", createdTx.merchantId)
            assertEquals("USER_VERIFIED", createdTx.merchantMatchSourceCode)
            assertEquals(100, createdTx.merchantConfidence)

            val verSnapshot = getAllVerificationsSnapshot()
            assertEquals(1, verSnapshot.size)
            val createdVer = verSnapshot.first()
            assertEquals("ASSIGN", createdVer.decisionCode)
            assertEquals("shell", createdVer.merchantId)

            assertEquals(2, getAllOperationsSnapshot().size)
        }

    @Test
    fun utilitiesCategory_withUtilitiesMerchantTurkcell_atomicallySucceeds() =
        runTest {
            val utilitiesCatId = "cat-util-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = utilitiesCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Faturalar",
                    normalizedName = "FATURALAR",
                    slug = "utilities-atom-1",
                    typeCode = "EXPENSE",
                    colorHex = "#6366F1",
                    iconKey = "utilities",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-turkcell-util", description = "TURKCELL FATURA", categoryId = utilitiesCatId)

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("turkcell"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)

            val txSnapshot = getAllTransactionsSnapshot()
            assertEquals(1, txSnapshot.size)
            val createdTx = txSnapshot.first()
            assertEquals("turkcell", createdTx.merchantId)
            assertEquals("USER_VERIFIED", createdTx.merchantMatchSourceCode)
            assertEquals(100, createdTx.merchantConfidence)

            val verSnapshot = getAllVerificationsSnapshot()
            assertEquals(1, verSnapshot.size)
            val createdVer = verSnapshot.first()
            assertEquals("ASSIGN", createdVer.decisionCode)
            assertEquals("turkcell", createdVer.merchantId)

            assertEquals(2, getAllOperationsSnapshot().size)
        }

    @Test
    fun shoppingCategory_withGroceriesMerchantMigros_failsClosedWithoutWrites() =
        runTest {
            val shoppingCatId = "cat-shop-atom-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = shoppingCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Alışveriş",
                    normalizedName = "ALISVERIS",
                    slug = "shopping-atom-1",
                    typeCode = "EXPENSE",
                    colorHex = "#EC4899",
                    iconKey = "shopping",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
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

            val tx = buildTransactionEntity(id = "tx-atom-shop-migros", description = "MİGROS ALIŞVERİŞ", categoryId = shoppingCatId)

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("migros"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun unknownMerchantId_failsClosedWithoutWrites() =
        runTest {
            val tx =
                buildTransactionEntity(
                    id = "tx-atom-unknown-merch",
                    description = "BİLİNMEYEN TİCARET",
                    categoryId = groceriesCategoryId,
                )

            assertFailsWith<IllegalArgumentException> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("unknown_merchant_999"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            assertTrue(getAllTransactionsSnapshot().isEmpty())
            assertTrue(getAllVerificationsSnapshot().isEmpty())
            assertTrue(getAllOperationsSnapshot().isEmpty())
        }

    @Test
    fun knownMerchant_yemeksepeti_genericDescriptionYemek_writesCanonicalVerificationAndOutboxesAtomically() =
        runTest {
            val tx =
                buildTransactionEntity(
                    id = "tx-atom-yemek-1",
                    description = "Yemek",
                    categoryId = foodDiningCategoryId,
                )

            val result =
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{\"id\":\"tx-atom-yemek-1\"}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("yemeksepeti"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )

            assertNotNull(result.transactionOperationId)
            assertNotNull(result.verificationOperationId)
            assertTrue(result.transactionOperationId != result.verificationOperationId)

            // 1. Transaction snapshot verification
            val txs = getAllTransactionsSnapshot()
            assertEquals(1, txs.size)
            val savedTx = txs[0]
            assertEquals("tx-atom-yemek-1", savedTx.id)
            assertEquals("Yemek", savedTx.description)
            assertEquals("yemeksepeti", savedTx.merchantId)
            assertEquals("USER_VERIFIED", savedTx.merchantMatchSourceCode)
            assertEquals(100, savedTx.merchantConfidence)
            assertEquals(10, savedTx.merchantCatalogVersion)

            // 2. Verification entity snapshot verification: canonical YEMEKSEPETI, NOT generic YEMEK
            val verifications = getAllVerificationsSnapshot()
            assertEquals(1, verifications.size)
            val savedV = verifications[0]
            val expectedVerifId = UuidHelper.deterministicMerchantVerificationUuid(userId, "YEMEKSEPETI")
            assertEquals(expectedVerifId, savedV.id)
            assertEquals("YEMEKSEPETI", savedV.normalizedAlias)
            assertEquals("ASSIGN", savedV.decisionCode)
            assertEquals("yemeksepeti", savedV.merchantId)
            assertEquals("PERSONAL", savedV.scopeCode)

            // 3. Outbox operations snapshot verification
            val ops = getAllOperationsSnapshot()
            assertEquals(2, ops.size)
            val opTypes = ops.map { it.entityTypeCode to it.operationTypeCode }.toSet()
            assertEquals(
                setOf(
                    "USER_MERCHANT_VERIFICATION" to "CREATE",
                    "TRANSACTION" to "CREATE",
                ),
                opTypes,
            )

            val verifOp = ops.first { it.entityTypeCode == "USER_MERCHANT_VERIFICATION" }
            assertEquals(expectedVerifId, verifOp.entityId)
            assertEquals(2, verifOp.protocolVersion)
            assertNotNull(verifOp.payloadJson)
            assertTrue(verifOp.payloadJson.contains("\"normalized_alias\":\"YEMEKSEPETI\""))

            val txOp = ops.first { it.entityTypeCode == "TRANSACTION" }
            assertEquals("tx-atom-yemek-1", txOp.entityId)
            assertEquals(2, txOp.protocolVersion)
        }

    @Test
    fun knownMerchant_yemeksepeti_triggerAbortMidway_leavesRelevantTableSnapshotsUnchanged() =
        runTest {
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_yemek_verif_outbox_insert
                BEFORE INSERT ON sync_operations
                WHEN NEW.entity_type_code = 'USER_MERCHANT_VERIFICATION'
                BEGIN
                    SELECT RAISE(ABORT, 'Simulated SQLite abort on yemeksepeti verification outbox');
                END;
                """.trimIndent(),
            )

            val tx =
                buildTransactionEntity(
                    id = "tx-atom-yemek-abort-1",
                    description = "Yemek",
                    categoryId = foodDiningCategoryId,
                )

            assertFailsWith<Exception> {
                mutationDao.createTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    actorUserId = userId,
                    transactionEntity = tx,
                    transactionPayloadJson = "{}",
                    decision = TransactionMerchantCreateDecision.KnownMerchant("yemeksepeti"),
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                    nowEpochMillis = 1000L,
                )
            }

            // True ACID rollback check: both verification and transaction must be completely rolled back!
            assertTrue(getAllTransactionsSnapshot().isEmpty(), "Transaction entity must be rolled back")
            assertTrue(getAllVerificationsSnapshot().isEmpty(), "Verification entity must be rolled back")
            assertTrue(getAllOperationsSnapshot().isEmpty(), "Sync operations must be rolled back")
        }

    private class SpyBackgroundSyncScheduler : BackgroundSyncScheduler {
        var scheduleCallCount = 0

        override fun scheduleOutboxSync() {
            scheduleCallCount++
        }

        override fun scheduleInitialSync() {}

        override fun cancelSyncWork() {}
    }
}
