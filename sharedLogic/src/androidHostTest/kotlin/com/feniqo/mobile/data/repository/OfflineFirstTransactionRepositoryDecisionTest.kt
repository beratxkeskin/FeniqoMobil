package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import com.feniqo.mobile.domain.usecase.MerchantRecognitionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OfflineFirstTransactionRepositoryDecisionTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var queue: OfflineWriteQueue
    private lateinit var repository: OfflineFirstTransactionRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeVerificationRepo: FakeVerificationRepository

    private val userId = "11111111-1111-4111-8111-111111111111"
    private val syncScopeKey = SyncScopeKey.forUser(userId)
    private val groceriesCategoryId = "cat-groceries-1"
    private val subscriptionsCategoryId = "cat-subscriptions-1"
    private val fuelCategoryId = "cat-fuel-1"
    private val foodDiningCategoryId = "cat-food-dining-1"
    private val shoppingCategoryId = "cat-shopping-1"
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

            queue =
                OfflineWriteQueue(
                    mutationDao = db.localMutationDao(),
                    operationDao = db.syncOperationDao(),
                    nowEpochMillisProvider = { 1000L },
                    operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
                )

            fakeAuthRepo = FakeAuthRepository(userId)
            fakeVerificationRepo = FakeVerificationRepository()

            repository =
                OfflineFirstTransactionRepository(
                    authRepository = fakeAuthRepo,
                    transactionDao = db.transactionDao(),
                    categoryDao = db.categoryDao(),
                    offlineWriteQueue = queue,
                    workspaceDao = db.workspaceDao(),
                    userMerchantVerificationRepository = fakeVerificationRepo,
                    activeWorkspaceScope = PersonalActiveWorkspaceScope,
                    nowEpochMillisProvider = { 1000L },
                    merchantRecognitionEngine = MerchantRecognitionEngine(),
                )

            // Seed Categories with proper iconKey
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

            db.categoryDao().upsert(
                CategoryEntity(
                    id = subscriptionsCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Abonelik",
                    normalizedName = "ABONELIK",
                    slug = "subscriptions-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#2196F3",
                    iconKey = "subscriptions",
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
                    id = fuelCategoryId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Yakıt",
                    normalizedName = "YAKIT",
                    slug = "fuel-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#FF9800",
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
        }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun buildTransaction(
        id: String = "tx-1",
        description: String = "MİGROS TİCARET",
        categoryId: String = groceriesCategoryId,
        type: TransactionType = TransactionType.EXPENSE,
        workspaceId: EntityId? = null,
    ): Transaction =
        Transaction(
            id = EntityId(id),
            ownerId = EntityId(userId),
            workspaceId = workspaceId,
            amount = Money(15000L, Currency.TRY),
            type = type,
            categoryId = EntityId(categoryId),
            description = description,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 10, 21),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

    private fun getAllOperations(syncScopeKey: String): List<SyncOperationEntity> {
        val cursor =
            db.openHelper.readableDatabase.query(
                "SELECT * FROM sync_operations WHERE sync_scope_key = ? ORDER BY operation_id",
                arrayOf(syncScopeKey),
            )
        val list = mutableListOf<SyncOperationEntity>()
        cursor.use { c ->
            val scopeIdx = c.getColumnIndexOrThrow("sync_scope_key")
            val idIdx = c.getColumnIndexOrThrow("operation_id")
            val entityTypeIdx = c.getColumnIndexOrThrow("entity_type_code")
            val entityIdIdx = c.getColumnIndexOrThrow("entity_id")
            val opTypeIdx = c.getColumnIndexOrThrow("operation_type_code")
            val statusIdx = c.getColumnIndexOrThrow("status_code")

            while (c.moveToNext()) {
                list.add(
                    SyncOperationEntity(
                        syncScopeKey = c.getString(scopeIdx),
                        operationId = c.getString(idIdx),
                        entityTypeCode = c.getString(entityTypeIdx),
                        entityId = c.getString(entityIdIdx),
                        operationTypeCode = c.getString(opTypeIdx),
                        baseVersion = null,
                        payloadJson = "{}",
                        predecessorOperationId = null,
                        isBlocked = false,
                        protocolVersion = 2,
                        statusCode = c.getString(statusIdx),
                        attemptCount = 0,
                        lastError = null,
                        nextAttemptAtEpochMillis = 1000L,
                        createdAtEpochMillis = 1000L,
                        updatedAtEpochMillis = 1000L,
                    ),
                )
            }
        }
        return list
    }

    private suspend fun getVerificationForAlias(alias: String): UserMerchantVerificationEntity? {
        val normalized =
            com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
                .normalize(alias)
        if (normalized in com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            return db.userMerchantVerificationDao().getByScopeAndAlias("user:$userId", normalized)
        }
        val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, normalized)
        return db.localMutationDao().getUserMerchantVerificationRow(deterministicId)
    }

    @Test
    fun autoCreate_backwardsCompatible_resolvesMerchantAndEnqueuesSingleOutbox() =
        runTest {
            val tx = buildTransaction(id = "tx-auto-1", description = "NETFLIX COM", categoryId = subscriptionsCategoryId)
            val result = repository.create(tx) // tests single-arg create() which delegates to Auto
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val entity = db.transactionDao().getByIdAndOwner("tx-auto-1", userId)
            assertNotNull(entity)
            assertEquals("netflix", entity.merchantId)
            assertEquals("STRONG_BRAND", entity.merchantMatchSourceCode)
            assertEquals(80, entity.merchantConfidence)

            // No verification entity created
            assertNull(getVerificationForAlias("NETFLIX COM"))

            // Exactly 1 outbox operation (TRANSACTION CREATE)
            val ops = getAllOperations(syncScopeKey)
            assertEquals(1, ops.size)
            assertEquals("TRANSACTION", ops[0].entityTypeCode)
            assertEquals("CREATE", ops[0].operationTypeCode)
        }

    @Test
    fun knownMerchantCreate_success_writesAttributionAndAssignVerification() =
        runTest {
            val tx = buildTransaction(id = "tx-known-1", description = "MİGROS ŞİŞLİ", categoryId = groceriesCategoryId)
            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // 1. Transaction entity assertion
            val entity = db.transactionDao().getByIdAndOwner("tx-known-1", userId)
            assertNotNull(entity)
            assertEquals("migros", entity.merchantId)
            assertEquals("USER_VERIFIED", entity.merchantMatchSourceCode)
            assertEquals(100, entity.merchantConfidence)
            assertEquals(10, entity.merchantCatalogVersion)

            // 2. Verification entity assertion (PERSONAL ASSIGN)
            val verification = getVerificationForAlias("MIGROS SISLI")
            assertNotNull(verification)
            assertEquals("MIGROS SISLI", verification.normalizedAlias)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("migros", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            // 3. Outbox operations: both TRANSACTION CREATE and USER_MERCHANT_VERIFICATION CREATE
            val ops = getAllOperations(syncScopeKey)
            assertEquals(2, ops.size)
            val types = ops.map { it.entityTypeCode }.toSet()
            assertEquals(setOf("TRANSACTION", "USER_MERCHANT_VERIFICATION"), types)
        }

    @Test
    fun knownMerchantCreate_kofteciYusuf_foodDining_success_writesAttributionAndAssignVerification() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-kofteci-1",
                    description = "KÖFTECİ YUSUF",
                    categoryId = foodDiningCategoryId,
                    type = TransactionType.EXPENSE,
                )
            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant(EntityId("kofteci_yusuf")),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // 1. Transaction entity assertion
            val entity = db.transactionDao().getByIdAndOwner("tx-kofteci-1", userId)
            assertNotNull(entity)
            assertEquals("kofteci_yusuf", entity.merchantId)
            assertEquals("USER_VERIFIED", entity.merchantMatchSourceCode)
            assertEquals(100, entity.merchantConfidence)
            assertEquals(10, entity.merchantCatalogVersion)

            // 2. Verification entity assertion (PERSONAL ASSIGN)
            val verification = getVerificationForAlias("KOFTECI YUSUF")
            assertNotNull(verification)
            assertEquals("KOFTECI YUSUF", verification.normalizedAlias)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("kofteci_yusuf", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            // 3. Outbox operations: both TRANSACTION CREATE and USER_MERCHANT_VERIFICATION CREATE
            val ops = getAllOperations(syncScopeKey)
            assertEquals(2, ops.size)
            val types = ops.map { it.entityTypeCode }.toSet()
            assertEquals(setOf("TRANSACTION", "USER_MERCHANT_VERIFICATION"), types)
        }

    @Test
    fun knownMerchantCreate_yemekGenericDescription_success_writesAttributionAndCanonicalAssignVerification() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-yemek-1",
                    description = "Yemek",
                    categoryId = foodDiningCategoryId,
                    type = TransactionType.EXPENSE,
                )
            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // 1. Transaction entity assertion
            val entity = db.transactionDao().getByIdAndOwner("tx-yemek-1", userId)
            assertNotNull(entity)
            assertEquals("Yemek", entity.description)
            assertEquals("yemeksepeti", entity.merchantId)
            assertEquals("USER_VERIFIED", entity.merchantMatchSourceCode)
            assertEquals(100, entity.merchantConfidence)
            assertEquals(10, entity.merchantCatalogVersion)

            // 2. Verification entity assertion: canonical alias YEMEKSEPETI, NOT generic YEMEK
            assertNull(getVerificationForAlias("YEMEK"))
            val verification = getVerificationForAlias("YEMEKSEPETI")
            assertNotNull(verification)
            assertEquals("YEMEKSEPETI", verification.normalizedAlias)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("yemeksepeti", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            // 3. Outbox operations: both TRANSACTION CREATE and USER_MERCHANT_VERIFICATION CREATE
            val ops = getAllOperations(syncScopeKey)
            assertEquals(2, ops.size)
            val types = ops.map { it.entityTypeCode }.toSet()
            assertEquals(setOf("TRANSACTION", "USER_MERCHANT_VERIFICATION"), types)
        }

    @Test
    fun knownMerchantCreate_kofteciYusuf_categoryMismatch_rejectedWithoutWrites() =
        runTest {
            // kofteci_yusuf category is "food_dining", but transaction uses groceries category ("groceries")
            val tx =
                buildTransaction(
                    id = "tx-kofteci-mismatch-1",
                    description = "KÖFTECİ YUSUF",
                    categoryId = groceriesCategoryId,
                    type = TransactionType.EXPENSE,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant(EntityId("kofteci_yusuf")),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-kofteci-mismatch-1", userId))
            assertNull(getVerificationForAlias("KOFTECI YUSUF"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun knownMerchantCreate_vatanBilgisayar_shopping_success_writesAttributionAndAssignVerification() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-vatan-1",
                    description = "VATAN BİLGİSAYAR",
                    categoryId = shoppingCategoryId,
                    type = TransactionType.EXPENSE,
                )
            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant(EntityId("vatan_bilgisayar")),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // 1. Transaction entity assertion
            val entity = db.transactionDao().getByIdAndOwner("tx-vatan-1", userId)
            assertNotNull(entity)
            assertEquals("vatan_bilgisayar", entity.merchantId)
            assertEquals("USER_VERIFIED", entity.merchantMatchSourceCode)
            assertEquals(100, entity.merchantConfidence)
            assertEquals(10, entity.merchantCatalogVersion)

            // 2. Verification entity assertion (PERSONAL ASSIGN)
            val verification = getVerificationForAlias("VATAN BILGISAYAR")
            assertNotNull(verification)
            assertEquals("VATAN BILGISAYAR", verification.normalizedAlias)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("vatan_bilgisayar", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            // 3. Outbox operations: both TRANSACTION CREATE and USER_MERCHANT_VERIFICATION CREATE
            val ops = getAllOperations(syncScopeKey)
            assertEquals(2, ops.size)
            val types = ops.map { it.entityTypeCode }.toSet()
            assertEquals(setOf("TRANSACTION", "USER_MERCHANT_VERIFICATION"), types)
        }

    @Test
    fun knownMerchantCreate_vatanBilgisayar_categoryMismatch_rejectedWithoutWrites() =
        runTest {
            // vatan_bilgisayar category is "shopping", but transaction uses groceries category ("groceries")
            val tx =
                buildTransaction(
                    id = "tx-vatan-mismatch-1",
                    description = "VATAN BİLGİSAYAR",
                    categoryId = groceriesCategoryId,
                    type = TransactionType.EXPENSE,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant(EntityId("vatan_bilgisayar")),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-vatan-mismatch-1", userId))
            assertNull(getVerificationForAlias("VATAN BILGISAYAR"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun useTransactionNameCreate_success_writesNullAttributionAndBlockVerification() =
        runTest {
            val tx = buildTransaction(id = "tx-block-1", description = "LOKAL KAHVECİ", categoryId = groceriesCategoryId)
            val result = repository.create(tx, TransactionMerchantCreateDecision.UseTransactionName)
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // 1. Transaction entity attribution fields must be entirely null
            val entity = db.transactionDao().getByIdAndOwner("tx-block-1", userId)
            assertNotNull(entity)
            assertNull(entity.merchantId)
            assertNull(entity.merchantMatchSourceCode)
            assertNull(entity.merchantConfidence)
            assertNull(entity.merchantCatalogVersion)

            // 2. Verification entity assertion (PERSONAL BLOCK)
            val verification = getVerificationForAlias("LOKAL KAHVECI")
            assertNotNull(verification)
            assertEquals("LOKAL KAHVECI", verification.normalizedAlias)
            assertEquals("BLOCK", verification.decisionCode)
            assertNull(verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            // 3. Outbox operations: both TRANSACTION CREATE and USER_MERCHANT_VERIFICATION CREATE
            val ops = getAllOperations(syncScopeKey)
            assertEquals(2, ops.size)
            val types = ops.map { it.entityTypeCode }.toSet()
            assertEquals(setOf("TRANSACTION", "USER_MERCHANT_VERIFICATION"), types)
        }

    /**
     * UseTransactionName kararı transaction ID'sine değil,
     * (kullanıcı + normalized_alias) kapsamına bağlanır.
     * Bu test, oluşturulan BLOCK verifikasyonunun işlem kimliğine bağlı olmadığını,
     * aynı alias için daha sonra oluşturulacak işlemlerde de BLOCK uygulanacağını doğrular.
     */
    @Test
    fun create_useTransactionName_isScopedToUserAndNormalizedAlias_notSpecificToTransactionId() =
        runTest {
            val aliasText = "ÖZEL KAHVE DÜKKANI"
            val tx1 = buildTransaction(id = "tx-first-1", description = aliasText, categoryId = groceriesCategoryId)

            val result1 = repository.create(tx1, TransactionMerchantCreateDecision.UseTransactionName)
            assertIs<RepositoryResult.Success<EntityId>>(result1)

            val v = getVerificationForAlias("OZEL KAHVE DUKKANI")
            assertNotNull(v)

            // Verification kimliği deterministic UUID(userId, normalizedAlias)'dir, tx1.id ile ilgisi yoktur
            val expectedDeterministicId =
                UuidHelper.deterministicMerchantVerificationUuid(
                    userId,
                    "OZEL KAHVE DUKKANI",
                )
            assertEquals(expectedDeterministicId, v.id)
            assertEquals("BLOCK", v.decisionCode)
            assertNull(v.merchantId)
            assertEquals("user:$userId", v.scopeKey)

            // İkinci bir işlem aynı alias ile Auto olarak geldiğinde, veritabanındaki BLOCK verification
            // sayesinde tanıma motoru bu alias'a merchant bağlamaz (kullanıcı kapsamı korunur).
            fakeVerificationRepo.personalDecisions["OZEL KAHVE DUKKANI"] =
                UserMerchantVerificationLookupResult.Decision(
                    decision = UserMerchantDecision.Block,
                )

            val tx2 = buildTransaction(id = "tx-second-2", description = aliasText, categoryId = groceriesCategoryId)
            val result2 = repository.create(tx2, TransactionMerchantCreateDecision.Auto)
            assertIs<RepositoryResult.Success<EntityId>>(result2)

            val entity2 = db.transactionDao().getByIdAndOwner("tx-second-2", userId)
            assertNotNull(entity2)
            assertNull(entity2.merchantId)
        }

    @Test
    fun knownMerchantCreate_unknownMerchant_rejectedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-unknown-1", description = "BİLİNMEYEN İŞ YERİ", categoryId = groceriesCategoryId)
            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("non_existent_merchant_xyz"))

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("unknown_merchant", error.code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-unknown-1", userId))
            assertNull(getVerificationForAlias("BILINMEYEN IS YERI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_workspaceTransaction_rejectedWithoutWrites() =
        runTest {
            val wsId = EntityId("workspace-123")
            val tx =
                buildTransaction(
                    id = "tx-ws-1",
                    description = "MİGROS ŞİŞLİ",
                    categoryId = groceriesCategoryId,
                    workspaceId = wsId,
                )

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("workspace_restricted", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-ws-1", userId))
            assertNull(getVerificationForAlias("MIGROS SISLI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_incomeTransaction_rejectedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-inc-1",
                    description = "MİGROS İADE",
                    categoryId = groceriesCategoryId,
                    type = TransactionType.INCOME,
                )

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-inc-1", userId))
            assertNull(getVerificationForAlias("MIGROS IADE"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_excludedDescription_rejectedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-excl-1", description = "Maaş Ödemesi", categoryId = groceriesCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("excluded_transaction", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-excl-1", userId))
            assertNull(getVerificationForAlias("MAAS ODEMESI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_useTransactionName_genericAlias_rejectedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-gen-1", description = "MARKET", categoryId = groceriesCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.UseTransactionName)
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("generic_alias_not_allowed", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-gen-1", userId))
            val vCursor = db.openHelper.readableDatabase.query("SELECT * FROM user_merchant_verifications")
            assertEquals(0, vCursor.use { it.count })
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun knownMerchantCreate_categoryMismatch_rejectedWithoutWrites() =
        runTest {
            // migros categoryIconKey is "groceries", but transaction uses fuel category ("fuel")
            val tx = buildTransaction(id = "tx-mismatch-1", description = "MİGROS ŞİŞLİ", categoryId = fuelCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-mismatch-1", userId))
            assertNull(getVerificationForAlias("MIGROS SISLI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_knownMerchant_withRefundDescription_rejectedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-refund-km-1",
                    description = "MİGROS İADE",
                    categoryId = groceriesCategoryId,
                    type = TransactionType.EXPENSE,
                )

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("excluded_transaction", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-refund-km-1", userId))
            assertNull(getVerificationForAlias("MIGROS IADE"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_useTransactionName_withRefundDescription_rejectedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-refund-utn-1",
                    description = "MİGROS REFUND",
                    categoryId = groceriesCategoryId,
                    type = TransactionType.EXPENSE,
                )

            val result = repository.create(tx, TransactionMerchantCreateDecision.UseTransactionName)
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("excluded_transaction", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-refund-utn-1", userId))
            assertNull(getVerificationForAlias("MIGROS REFUND"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_whenCategoryDaoNull_failsClosedWithoutWrites() =
        runTest {
            val repoWithoutCategoryDao =
                OfflineFirstTransactionRepository(
                    authRepository = fakeAuthRepo,
                    transactionDao = db.transactionDao(),
                    categoryDao = null,
                    offlineWriteQueue = queue,
                    workspaceDao = db.workspaceDao(),
                    userMerchantVerificationRepository = fakeVerificationRepo,
                    activeWorkspaceScope = PersonalActiveWorkspaceScope,
                    nowEpochMillisProvider = { 1000L },
                    merchantRecognitionEngine = MerchantRecognitionEngine(),
                )

            val tx = buildTransaction(id = "tx-nocat-1", description = "MİGROS ŞİŞLİ", categoryId = groceriesCategoryId)

            val result = repoWithoutCategoryDao.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_dao_required_for_merchant_decision", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-nocat-1", userId))
            assertNull(getVerificationForAlias("MIGROS SISLI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_withInvalidCategoryOwnership_failsClosedWithoutWrites() =
        runTest {
            val corruptCatId = "cat-corrupt-ownership"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = corruptCatId,
                    ownerId = userId, // ownerId set but isDefault true -> corrupt/invalid ownership!
                    workspaceId = null,
                    scopeKey = "corrupt",
                    name = "Bozuk",
                    normalizedName = "BOZUK",
                    slug = "bozuk",
                    typeCode = "EXPENSE",
                    colorHex = "#000000",
                    iconKey = "groceries",
                    isDefault = true,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
                            syncStatus = SyncStatus.SYNCED.name,
                            version = 1L,
                            baseVersion = null,
                            updatedAtEpochMillis = 1000L,
                            localUpdatedAtEpochMillis = 1000L,
                            deletedAtEpochMillis = null,
                            lastSyncError = null,
                        ),
                ),
            )

            val tx = buildTransaction(id = "tx-corrupt-cat-1", description = "MİGROS ŞİŞLİ", categoryId = corruptCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("invalid_category_ownership", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-corrupt-cat-1", userId))
            assertNull(getVerificationForAlias("MIGROS SISLI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_withCorruptNullOwnerAndNonDefault_failsClosedWithoutWrites() =
        runTest {
            val corruptCatId = "cat-corrupt-null-owner"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = corruptCatId,
                    ownerId = null, // ownerId null and isDefault false -> not accessible to user!
                    workspaceId = null,
                    scopeKey = "corrupt",
                    name = "Bozuk 2",
                    normalizedName = "BOZUK 2",
                    slug = "bozuk-2",
                    typeCode = "EXPENSE",
                    colorHex = "#000000",
                    iconKey = "groceries",
                    isDefault = false,
                    createdAtEpochMillis = 1000L,
                    sync =
                        SyncMetadata(
                            syncStatus = SyncStatus.SYNCED.name,
                            version = 1L,
                            baseVersion = null,
                            updatedAtEpochMillis = 1000L,
                            localUpdatedAtEpochMillis = 1000L,
                            deletedAtEpochMillis = null,
                            lastSyncError = null,
                        ),
                ),
            )

            val tx = buildTransaction(id = "tx-corrupt-cat-2", description = "MİGROS ŞİŞLİ", categoryId = corruptCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_not_found", (result.error as AppError.Validation).code)

            // Zero writes
            assertNull(db.transactionDao().getByIdAndOwner("tx-corrupt-cat-2", userId))
            assertNull(getVerificationForAlias("MIGROS SISLI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_transportationCategoryWithTravelMerchantThy_succeeds() =
        runTest {
            val transportationCatId = "cat-transportation-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım",
                    normalizedName = "ULASIM",
                    slug = "transportation-$userId",
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

            val tx = buildTransaction(id = "tx-thy-transp", description = "THY BİLET ALIMI", categoryId = transportationCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("thy"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-thy-transp", userId)
            assertNotNull(created)
            assertEquals("thy", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)

            val verification = getVerificationForAlias("THY BILET ALIMI")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("thy", verification.merchantId)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_exactTravelCategoryWithTravelMerchantThy_succeeds() =
        runTest {
            val travelCatId = "cat-travel-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = travelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Seyahat",
                    normalizedName = "SEYAHAT",
                    slug = "travel-$userId",
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

            val tx = buildTransaction(id = "tx-thy-travel", description = "THY SEYAHAT", categoryId = travelCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("thy"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-thy-travel", userId)
            assertNotNull(created)
            assertEquals("thy", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
        }

    @Test
    fun explicitDecision_groceriesCategoryWithTravelMerchantThy_failsClosedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-groceries-thy", description = "THY MARKETTE", categoryId = groceriesCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("thy"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-groceries-thy", userId))
            assertNull(getVerificationForAlias("THY MARKETTE"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_fuelCategoryWithFuelMerchantShell_succeeds() =
        runTest {
            val tx = buildTransaction(id = "tx-fuel-shell", description = "SHELL PETROL", categoryId = fuelCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("shell"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-fuel-shell", userId)
            assertNotNull(created)
            assertEquals("shell", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)

            val verification = getVerificationForAlias("SHELL PETROL")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("shell", verification.merchantId)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_utilitiesCategoryWithUtilitiesMerchantTurkcell_succeeds() =
        runTest {
            val utilitiesCatId = "cat-utilities-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = utilitiesCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Faturalar",
                    normalizedName = "FATURALAR",
                    slug = "utilities-$userId",
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

            val tx = buildTransaction(id = "tx-util-turkcell", description = "TURKCELL FATURA", categoryId = utilitiesCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("turkcell"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-util-turkcell", userId)
            assertNotNull(created)
            assertEquals("turkcell", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)

            val verification = getVerificationForAlias("TURKCELL FATURA")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("turkcell", verification.merchantId)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_shoppingCategoryWithGroceriesMerchantMigros_failsClosedWithoutWrites() =
        runTest {
            val shoppingCatId = "cat-shopping-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = shoppingCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Alışveriş",
                    normalizedName = "ALISVERIS",
                    slug = "shopping-$userId",
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

            val tx = buildTransaction(id = "tx-shopping-migros", description = "MİGROS ALIŞVERİŞ", categoryId = shoppingCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("migros"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-shopping-migros", userId))
            assertNull(getVerificationForAlias("MIGROS ALISVERIS"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_personalCareCategoryWithPersonalCareMerchantGratis_succeeds() =
        runTest {
            val personalCareCatId = "cat-care-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = personalCareCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Kişisel Bakım",
                    normalizedName = "KISISEL BAKIM",
                    slug = "personal-care-$userId",
                    typeCode = "EXPENSE",
                    colorHex = "#EC4899",
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

            val tx = buildTransaction(id = "tx-care-gratis", description = "GRATIS KOZMETIK", categoryId = personalCareCatId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("gratis"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-care-gratis", userId)
            assertNotNull(created)
            assertEquals("gratis", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)
            assertEquals(10, created.merchantCatalogVersion)

            val verification = getVerificationForAlias("GRATIS KOZMETIK")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("gratis", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_shoppingCategoryWithPersonalCareMerchantGratis_failsClosedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-shopping-gratis-fail", description = "GRATIS MAGAZASI", categoryId = shoppingCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("gratis"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-shopping-gratis-fail", userId))
            assertNull(getVerificationForAlias("GRATIS MAGAZASI"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_groceriesCategoryWithPersonalCareMerchantGratis_failsClosedWithoutWrites() =
        runTest {
            val tx = buildTransaction(id = "tx-groc-gratis-fail", description = "GRATIS MARKET", categoryId = groceriesCategoryId)

            val result = repository.create(tx, TransactionMerchantCreateDecision.KnownMerchant("gratis"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-groc-gratis-fail", userId))
            assertNull(getVerificationForAlias("GRATIS MARKET"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_utilitiesCategoryWithUtilitiesMerchantTurknet_succeeds() =
        runTest {
            val utilitiesCatId = "cat-turknet-util-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = utilitiesCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Turknet Fatura",
                    normalizedName = "TURKNET FATURA",
                    slug = "turknet-util-$userId",
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
                buildTransaction(
                    id = "tx-util-turknet-1",
                    description = "TURKNET INTERNET",
                    categoryId = utilitiesCatId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("turknet"),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-util-turknet-1", userId)
            assertNotNull(created)
            assertEquals("turknet", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)
            assertEquals(10, created.merchantCatalogVersion)

            val verification = getVerificationForAlias("TURKNET INTERNET")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("turknet", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_subscriptionsCategoryWithUtilitiesMerchantTurknet_failsClosedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-sub-turknet-fail",
                    description = "TURKNET ABONELIK",
                    categoryId = subscriptionsCategoryId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("turknet"),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-sub-turknet-fail", userId))
            assertNull(getVerificationForAlias("TURKNET ABONELIK"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_shoppingCategoryWithUtilitiesMerchantTurknet_failsClosedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-shop-turknet-fail",
                    description = "TURKNET MODEM",
                    categoryId = shoppingCategoryId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("turknet"),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-shop-turknet-fail", userId))
            assertNull(getVerificationForAlias("TURKNET MODEM"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_transportationCategoryWithTransportationMerchantIstanbulkart_succeeds() =
        runTest {
            val transportationCatId = "cat-transportation-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım",
                    normalizedName = "ULASIM",
                    slug = "transportation-$userId",
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
                buildTransaction(
                    id = "tx-trans-ik-1",
                    description = "İSTANBULKART",
                    categoryId = transportationCatId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("istanbulkart"),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-trans-ik-1", userId)
            assertNotNull(created)
            assertEquals("istanbulkart", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)
            assertEquals(10, created.merchantCatalogVersion)

            val verification = getVerificationForAlias("ISTANBULKART")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("istanbulkart", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_travelCategoryWithTravelMerchantAjet_succeeds() =
        runTest {
            val travelCatId = "cat-travel-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = travelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Seyahat",
                    normalizedName = "SEYAHAT",
                    slug = "travel-$userId",
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
                buildTransaction(
                    id = "tx-travel-ajet-1",
                    description = "AJET UCUS",
                    categoryId = travelCatId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("ajet"),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-travel-ajet-1", userId)
            assertNotNull(created)
            assertEquals("ajet", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)
            assertEquals(10, created.merchantCatalogVersion)

            val verification = getVerificationForAlias("AJET UCUS")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("ajet", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_transportationCategoryWithTravelMerchantAjet_succeeds() =
        runTest {
            val transportationCatId = "cat-transportation-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = transportationCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Ulaşım",
                    normalizedName = "ULASIM",
                    slug = "transportation-$userId",
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
                buildTransaction(
                    id = "tx-trans-ajet-1",
                    description = "AJET UCUS",
                    categoryId = transportationCatId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("ajet"),
                )
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val created = db.transactionDao().getByIdAndOwner("tx-trans-ajet-1", userId)
            assertNotNull(created)
            assertEquals("ajet", created.merchantId)
            assertEquals("USER_VERIFIED", created.merchantMatchSourceCode)
            assertEquals(100, created.merchantConfidence)
            assertEquals(10, created.merchantCatalogVersion)

            val verification = getVerificationForAlias("AJET UCUS")
            assertNotNull(verification)
            assertEquals("ASSIGN", verification.decisionCode)
            assertEquals("ajet", verification.merchantId)
            assertEquals("PERSONAL", verification.scopeCode)

            assertEquals(2, getAllOperations(syncScopeKey).size)
        }

    @Test
    fun explicitDecision_travelCategoryWithTransportationOnlyMerchantIstanbulkart_failsClosedWithoutWrites() =
        runTest {
            val travelCatId = "cat-travel-1"
            db.categoryDao().upsert(
                CategoryEntity(
                    id = travelCatId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = "user:$userId",
                    name = "Seyahat",
                    normalizedName = "SEYAHAT",
                    slug = "travel-$userId",
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
                buildTransaction(
                    id = "tx-travel-ik-fail",
                    description = "İSTANBULKART",
                    categoryId = travelCatId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("istanbulkart"),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-travel-ik-fail", userId))
            assertNull(getVerificationForAlias("ISTANBULKART"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    @Test
    fun explicitDecision_groceriesCategoryWithTransportationMerchantIstanbulkart_failsClosedWithoutWrites() =
        runTest {
            val tx =
                buildTransaction(
                    id = "tx-groc-ik-fail",
                    description = "İSTANBULKART MARKET",
                    categoryId = groceriesCategoryId,
                )

            val result =
                repository.create(
                    tx,
                    TransactionMerchantCreateDecision.KnownMerchant("istanbulkart"),
                )
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("category_merchant_mismatch", (result.error as AppError.Validation).code)

            assertNull(db.transactionDao().getByIdAndOwner("tx-groc-ik-fail", userId))
            assertNull(getVerificationForAlias("ISTANBULKART MARKET"))
            assertTrue(getAllOperations(syncScopeKey).isEmpty())
        }

    private class FakeAuthRepository(
        private val userId: String,
    ) : AuthRepository {
        private val sessionFlow =
            MutableStateFlow<AuthSession?>(
                AuthSession(
                    userId = EntityId(userId),
                    email = "test@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2_000_000L),
                ),
            )

        override fun observeSession(): Flow<AuthSession?> = sessionFlow

        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)

        override suspend fun signIn(
            email: String,
            password: String,
        ): RepositoryResult<Unit> = error("unused")

        override suspend fun signUp(
            email: String,
            password: String,
            fullName: String?,
        ): RepositoryResult<EntityId> = error("unused")

        override suspend fun refreshSession(): RepositoryResult<Unit> = error("unused")

        override suspend fun signOut(): RepositoryResult<Unit> = error("unused")
    }

    private class FakeVerificationRepository : UserMerchantVerificationRepository {
        val personalDecisions = mutableMapOf<String, UserMerchantVerificationLookupResult>()

        override fun observeActiveVerifications(): Flow<List<UserMerchantVerification>> = MutableStateFlow(emptyList())

        override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult =
            personalDecisions[rawOrNormalizedAlias] ?: UserMerchantVerificationLookupResult.NotFound

        override suspend fun assignMerchant(
            alias: String,
            merchantId: String,
        ): RepositoryResult<UserMerchantVerification> = error("unused")

        override suspend fun blockMerchant(alias: String): RepositoryResult<UserMerchantVerification> = error("unused")

        override suspend fun deleteVerification(alias: String): RepositoryResult<Unit> = error("unused")
    }
}
