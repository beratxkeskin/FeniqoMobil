package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.RecurringTransactionDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.dao.UserMerchantVerificationDao
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransactionMerchantPersonalDecisionIntegrationTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var queue: OfflineWriteQueue
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var recurringDao: RecurringTransactionDao
    private lateinit var verificationDao: UserMerchantVerificationDao
    private lateinit var workspaceDao: WorkspaceDao

    private val userAId = "11111111-1111-4111-8111-111111111111"
    private val userBId = "22222222-2222-4222-8222-222222222222"
    private val sessionA = AuthSession(EntityId(userAId), "a@feniqo.com", Instant.fromEpochMilliseconds(1000L))
    private val sessionB = AuthSession(EntityId(userBId), "b@feniqo.com", Instant.fromEpochMilliseconds(1000L))

    private val currentSession = MutableStateFlow<AuthSession?>(sessionA)

    private val fakeAuthRepository = object : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = currentSession.asStateFlow()
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
        override suspend fun refreshSession() = RepositoryResult.Success(Unit)
        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private lateinit var verificationRepository: UserMerchantVerificationRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringRepository: RecurringTransactionRepository

    private var opIndex = 0
    private var entityIndex = 0

    @BeforeTest
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
                context = ApplicationProvider.getApplicationContext(),
                factory = { FeniqoDatabaseConstructor.initialize() },
            ).allowMainThreadQueries()
                .build()

        queue = OfflineWriteQueue(
            mutationDao = db.localMutationDao(),
            operationDao = db.syncOperationDao(),
            nowEpochMillisProvider = { 1000L },
            operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
        )
        transactionDao = db.transactionDao()
        categoryDao = db.categoryDao()
        recurringDao = db.recurringTransactionDao()
        verificationDao = db.userMerchantVerificationDao()
        workspaceDao = db.workspaceDao()

        verificationRepository = OfflineFirstUserMerchantVerificationRepository(
            authRepository = fakeAuthRepository,
            dao = verificationDao,
            writeQueue = queue,
        )

        transactionRepository = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepository,
            transactionDao = transactionDao,
            offlineWriteQueue = queue,
            workspaceDao = workspaceDao,
            userMerchantVerificationRepository = verificationRepository,
        )

        recurringRepository = OfflineFirstRecurringTransactionRepository(
            authRepository = fakeAuthRepository,
            categoryDao = categoryDao,
            recurringTransactionDao = recurringDao,
            offlineWriteQueue = queue,
            entityIdGenerator = EntityIdGenerator { EntityId("generated-${++entityIndex}") },
            userMerchantVerificationRepository = verificationRepository,
        )

        // Default Category insert for userA
        runBlocking {
            categoryDao.upsert(
                CategoryEntity(
                    id = "cat-default",
                    ownerId = null,
                    workspaceId = null,
                    scopeKey = "global:cat-default",
                    name = "Genel Gider",
                    normalizedName = "GENEL GIDER",
                    slug = "genel-gider",
                    typeCode = "EXPENSE",
                    colorHex = "#112233",
                    iconKey = "cart",
                    isDefault = true,
                    createdAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = SyncStatus.SYNCED.name,
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = null,
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

    // =========================================================================
    // A. Transaction Create
    // =========================================================================

    @Test
    fun create_personalAssignOverridesDifferentCatalogResultWithConfidence100() = runTest {
        // "Netflix" normalde "netflix" merchantına eşleşir.
        // Kullanıcı A "Netflix" alias'ı için "spotify" ASSIGN kararı veriyor.
        val assignRes = verificationRepository.assignMerchant("Netflix", "spotify")
        assertIs<RepositoryResult.Success<*>>(assignRes)

        val transaction = Transaction(
            id = EntityId("tx-1"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(10000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Success<*>>(createRes)

        val stored = transactionDao.getByIdAndOwner("tx-1", userAId)
        assertNotNull(stored)
        assertEquals("spotify", stored.merchantId, "Kişisel ASSIGN katalog sonucunu bastırmalıdır")
        assertEquals(MerchantMatchSource.USER_VERIFIED.name, stored.merchantMatchSourceCode)
        assertEquals(100, stored.merchantConfidence)
    }

    @Test
    fun create_personalBlockSuppressesMerchantAttributionOnKnownAlias() = runTest {
        // "Netflix" normalde katalogda eşleşir. Kullanıcı A BLOCK ediyor.
        val blockRes = verificationRepository.blockMerchant("Netflix")
        assertIs<RepositoryResult.Success<*>>(blockRes)

        val transaction = Transaction(
            id = EntityId("tx-2"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(10000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Success<*>>(createRes)

        val stored = transactionDao.getByIdAndOwner("tx-2", userAId)
        assertNotNull(stored)
        assertNull(stored.merchantId, "BLOCK kararı merchantId üretmemeli")
        assertNull(stored.merchantMatchSourceCode)
        assertNull(stored.merchantConfidence)
    }

    @Test
    fun create_notFoundKeepsDefaultCatalogBehavior() = runTest {
        // Karar yok -> BİM normal katalog eşleşmesi (95 puan)
        val transaction = Transaction(
            id = EntityId("tx-3"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Success<*>>(createRes)

        val stored = transactionDao.getByIdAndOwner("tx-3", userAId)
        assertNotNull(stored)
        assertEquals("bim", stored.merchantId)
        assertEquals(95, stored.merchantConfidence)
    }

    @Test
    fun create_invalidStoredVerificationFailsClosedAndPreventsCatalogFallback() = runTest {
        // user_merchant_verifications tablosuna doğrudan bozuk kayıt ekliyoruz
        db.localMutationDao().upsertUserMerchantVerificationRow(
            UserMerchantVerificationEntity(
                id = "corrupt-id-1",
                ownerId = userAId,
                workspaceId = null,
                scopeKey = "user:$userAId",
                normalizedAlias = "NETFLIX",
                decisionCode = "CORRUPT_CODE", // bozuk kod -> toDomainOrNull null döner
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            ),
        )

        val transaction = Transaction(
            id = EntityId("tx-4"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(10000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Failure>(createRes)
        val error = createRes.error
        assertIs<AppError.Storage>(error)
        assertEquals("invalid_stored_merchant_verification", error.code)

        // Room'a hiçbir şey yazılmadığını doğrula
        assertNull(transactionDao.getByIdAndOwner("tx-4", userAId))
    }

    @Test
    fun create_crossUserDecisionIsNotApplied() = runTest {
        // Kullanıcı B "BİM" için BLOCK kararı verdi
        currentSession.value = sessionB
        val blockRes = verificationRepository.blockMerchant("BİM")
        assertIs<RepositoryResult.Success<*>>(blockRes)

        // Şimdi oturumu Kullanıcı A yapıyoruz
        currentSession.value = sessionA

        val transaction = Transaction(
            id = EntityId("tx-5"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Success<*>>(createRes)

        val stored = transactionDao.getByIdAndOwner("tx-5", userAId)
        assertNotNull(stored)
        // Kullanıcı B'nin BLOCK kararı Kullanıcı A'yı etkilemez, normal BİM kataloğu tanınır
        assertEquals("bim", stored.merchantId)
        assertEquals(95, stored.merchantConfidence)
    }

    @Test
    fun create_tombstoneDecisionDoesNotApplyAndCatalogBehaviorContinues() = runTest {
        // Kullanıcı A BLOCK kararı verip ardından sildi (tombstone)
        verificationRepository.blockMerchant("BİM")
        val deleteRes = verificationRepository.deleteVerification("BİM")
        assertIs<RepositoryResult.Success<*>>(deleteRes)

        val transaction = Transaction(
            id = EntityId("tx-6"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Success<*>>(createRes)

        val stored = transactionDao.getByIdAndOwner("tx-6", userAId)
        assertNotNull(stored)
        // Tombstone silinmiş olduğu için katalog davranışı devam eder
        assertEquals("bim", stored.merchantId)
    }

    @Test
    fun create_unauthenticatedCreateIsRejected() = runTest {
        currentSession.value = null

        val transaction = Transaction(
            id = EntityId("tx-7"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertIs<RepositoryResult.Failure>(createRes)
        assertIs<AppError.Authentication>(createRes.error)
    }

    // =========================================================================
    // B. Transaction Update
    // =========================================================================

    @Test
    fun update_whenDescriptionChangesPersonalDecisionIsResolved() = runTest {
        // Kullanıcı A "Migros Siparişi" için ASSIGN "migros_sanal_market" kararı veriyor
        verificationRepository.assignMerchant("Migros Siparişi", "migros_sanal_market")

        val initialTx = Transaction(
            id = EntityId("tx-up-1"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(10000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Bilinmeyen Harcama",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )
        transactionRepository.create(initialTx)
        assertNull(transactionDao.getByIdAndOwner("tx-up-1", userAId)?.merchantId)

        // Açıklamayı "Migros Siparişi" olarak güncelliyoruz
        val updatedTx = initialTx.copy(description = "Migros Siparişi")
        val updateRes = transactionRepository.update(updatedTx)
        assertIs<RepositoryResult.Success<*>>(updateRes)

        val stored = transactionDao.getByIdAndOwner("tx-up-1", userAId)
        assertNotNull(stored)
        assertEquals("migros_sanal_market", stored.merchantId)
        assertEquals(MerchantMatchSource.USER_VERIFIED.name, stored.merchantMatchSourceCode)
        assertEquals(100, stored.merchantConfidence)
    }

    @Test
    fun update_whenDescriptionChangesToBlockedAliasSuppressesCatalogPrediction() = runTest {
        // Kullanıcı A "Netflix" için BLOCK kararı verdi
        verificationRepository.blockMerchant("Netflix")

        val initialTx = Transaction(
            id = EntityId("tx-up-2"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(10000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )
        transactionRepository.create(initialTx)
        assertEquals("bim", transactionDao.getByIdAndOwner("tx-up-2", userAId)?.merchantId)

        // Açıklamayı "Netflix" olarak güncelliyoruz
        val updatedTx = initialTx.copy(description = "Netflix")
        val updateRes = transactionRepository.update(updatedTx)
        assertIs<RepositoryResult.Success<*>>(updateRes)

        val stored = transactionDao.getByIdAndOwner("tx-up-2", userAId)
        assertNotNull(stored)
        assertNull(stored.merchantId, "Yeni açıklamada BLOCK kararı katalog tahminini bastırmalıdır")
    }

    @Test
    fun update_whenDescriptionDoesNotChangeExistingMerchantInfoIsPreserved() = runTest {
        val initialTx = Transaction(
            id = EntityId("tx-up-3"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "BİM",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )
        transactionRepository.create(initialTx)
        assertEquals("bim", transactionDao.getByIdAndOwner("tx-up-3", userAId)?.merchantId)

        // Yalnızca tutarı değiştiriyoruz, açıklama aynı
        val updatedTx = initialTx.copy(amount = Money(7500, Currency.TRY))
        val updateRes = transactionRepository.update(updatedTx)
        assertIs<RepositoryResult.Success<*>>(updateRes)

        val stored = transactionDao.getByIdAndOwner("tx-up-3", userAId)
        assertNotNull(stored)
        assertEquals("bim", stored.merchantId)
        assertEquals(7500, stored.amountMinor)
    }

    // =========================================================================
    // C. Installment / Group Creation
    // =========================================================================

    @Test
    fun createInstallmentGroup_appliesCorrectPersonalDecisionForEachEntry() = runTest {
        // Kullanıcı A "Netflix 1" için ASSIGN "netflix", "Netflix 2" için BLOCK
        verificationRepository.assignMerchant("Netflix 1", "netflix")
        verificationRepository.blockMerchant("Netflix 2")

        val tx1 = Transaction(
            id = EntityId("inst-1"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix 1",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = InstallmentInfo(1, 2, EntityId("grp-1")),
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )
        val tx2 = Transaction(
            id = EntityId("inst-2"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix 2",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-11-18"),
            receiptPath = null,
            installment = InstallmentInfo(2, 2, EntityId("grp-1")),
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val groupRes = transactionRepository.createInstallmentGroup(listOf(tx1, tx2))
        assertIs<RepositoryResult.Success<*>>(groupRes)

        val stored1 = transactionDao.getByIdAndOwner("inst-1", userAId)
        assertNotNull(stored1)
        assertEquals("netflix", stored1.merchantId)
        assertEquals(MerchantMatchSource.USER_VERIFIED.name, stored1.merchantMatchSourceCode)

        val stored2 = transactionDao.getByIdAndOwner("inst-2", userAId)
        assertNotNull(stored2)
        assertNull(stored2.merchantId, "İkinci taksit BLOCK kararıyla bastırılmalıdır")
    }

    @Test
    fun createInstallmentGroup_invalidVerificationInAnyEntryPreventsPartialRoomWrites() = runTest {
        // Bozuk doğrulama ekleyelim
        db.localMutationDao().upsertUserMerchantVerificationRow(
            UserMerchantVerificationEntity(
                id = "corrupt-id-inst",
                ownerId = userAId,
                workspaceId = null,
                scopeKey = "user:$userAId",
                normalizedAlias = "BOZUK TAKSIT",
                decisionCode = "INVALID_CODE",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            ),
        )

        val tx1 = Transaction(
            id = EntityId("inst-bad-1"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Normal Taksit",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = InstallmentInfo(1, 2, EntityId("grp-bad")),
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )
        val tx2 = Transaction(
            id = EntityId("inst-bad-2"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Bozuk Taksit",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-11-18"),
            receiptPath = null,
            installment = InstallmentInfo(2, 2, EntityId("grp-bad")),
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val groupRes = transactionRepository.createInstallmentGroup(listOf(tx1, tx2))
        assertIs<RepositoryResult.Failure>(groupRes)
        assertIs<AppError.Storage>(groupRes.error)

        // Hiçbir taksit yazılmamış olmalı (kısmi yazım yok)
        assertNull(transactionDao.getByIdAndOwner("inst-bad-1", userAId))
        assertNull(transactionDao.getByIdAndOwner("inst-bad-2", userAId))
    }

    // =========================================================================
    // D. Recurring Transaction / Materialization
    // =========================================================================

    @Test
    fun recurring_processPendingOccurrencesAppliesPersonalAssignAndBlock() = runTest {
        // Kullanıcı A kural 1 için ASSIGN "spotify", kural 2 için BLOCK
        verificationRepository.assignMerchant("Aylık Müzik", "spotify")
        verificationRepository.blockMerchant("Aylık Film")

        val rule1 = RecurringTransactionEntity(
            id = "rec-1",
            ownerId = userAId,
            workspaceId = null,
            categoryId = "cat-default",
            amountMinor = 5000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            paymentMethodCode = "CREDIT_CARD",
            description = "Aylık Müzik",
            frequencyCode = RecurrenceFrequency.MONTHLY.name,
            interval = 1,
            startDate = "2026-10-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = null,
                lastSyncError = null,
            ),
        )
        val rule2 = RecurringTransactionEntity(
            id = "rec-2",
            ownerId = userAId,
            workspaceId = null,
            categoryId = "cat-default",
            amountMinor = 15000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            paymentMethodCode = "CREDIT_CARD",
            description = "Aylık Film",
            frequencyCode = RecurrenceFrequency.MONTHLY.name,
            interval = 1,
            startDate = "2026-10-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = null,
                lastSyncError = null,
            ),
        )
        recurringDao.upsert(rule1)
        recurringDao.upsert(rule2)

        val processRes = recurringRepository.generateDueTransactions(
            throughDate = LocalDate.parse("2026-10-15"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        assertIs<RepositoryResult.Success<GenerateRecurringTransactionsResult>>(processRes)

        val allTx = transactionDao.observeAll(
            ownerId = userAId,
            workspaceId = null,
            startDate = null,
            endDate = null,
            typeCode = null,
            categoryId = null,
            paymentMethodCode = null,
            searchQuery = null,
        ).first()
        val musicTx = allTx.firstOrNull { it.description == "Aylık Müzik" }
        assertNotNull(musicTx)
        assertEquals("spotify", musicTx.merchantId)
        assertEquals(MerchantMatchSource.USER_VERIFIED.name, musicTx.merchantMatchSourceCode)

        val filmTx = allTx.firstOrNull { it.description == "Aylık Film" }
        assertNotNull(filmTx)
        assertNull(filmTx.merchantId, "BLOCK kararı merchantId üretmemeli")
    }

    @Test
    fun recurring_processPendingOccurrencesSkipsOnInvalidStoredVerification() = runTest {
        // Bozuk doğrulama ekleyelim
        db.localMutationDao().upsertUserMerchantVerificationRow(
            UserMerchantVerificationEntity(
                id = "corrupt-rec-1",
                ownerId = userAId,
                workspaceId = null,
                scopeKey = "user:$userAId",
                normalizedAlias = "BOZUK RECURRING",
                decisionCode = "INVALID_DECISION",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            ),
        )

        val rule = RecurringTransactionEntity(
            id = "rec-bad",
            ownerId = userAId,
            workspaceId = null,
            categoryId = "cat-default",
            amountMinor = 5000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            paymentMethodCode = "CREDIT_CARD",
            description = "Bozuk Recurring",
            frequencyCode = RecurrenceFrequency.MONTHLY.name,
            interval = 1,
            startDate = "2026-10-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAtEpochMillis = 1000L,
            sync = SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = null,
                lastSyncError = null,
            ),
        )
        recurringDao.upsert(rule)

        val processRes = recurringRepository.generateDueTransactions(
            throughDate = LocalDate.parse("2026-10-15"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        assertIs<RepositoryResult.Success<GenerateRecurringTransactionsResult>>(processRes)
        val data = processRes.value
        assertTrue(data.skippedRecurringIds.any { it.value == "rec-bad" }, "Bozuk doğrulama taşıyan kural fail-closed atlanmalıdır")

        // Transaction üretilmediğini doğrula
        val allTx = transactionDao.observeAll(
            ownerId = userAId,
            workspaceId = null,
            startDate = null,
            endDate = null,
            typeCode = null,
            categoryId = null,
            paymentMethodCode = null,
            searchQuery = null,
        ).first()
        assertNull(allTx.firstOrNull { it.description == "Bozuk Recurring" })
    }

    // =========================================================================
    // F. Domain ve Regresyonlar
    // =========================================================================

    @Test
    fun excludedTransactions_likeSalaryOrTransfer_neverBecomeMerchantEvenWithPersonalDecision() = runTest {
        // Kullanıcı "Maaş Ödemesi" için ASSIGN "netflix" verse dahi,
        // sistem hareketi / maaş / transfer asla merchant'a çevrilmez!
        verificationRepository.assignMerchant("Maaş Ödemesi", "netflix")

        val transaction = Transaction(
            id = EntityId("tx-salary"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000000, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-default"),
            description = "Maaş Ödemesi",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = transactionRepository.create(transaction)
        assertTrue(createRes is RepositoryResult.Success)

        val stored = transactionDao.getByIdAndOwner("tx-salary", userAId)
        assertNotNull(stored)
        assertNull(stored.merchantId, "Maaş veya transfer işlemleri kişisel kararla dahi merchantInfo alamaz")
    }

    @Test
    fun cancellationException_isNeverSwallowed() = runTest {
        val cancellingRepository = object : UserMerchantVerificationRepository by verificationRepository {
            override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult {
                throw CancellationException("test cancellation")
            }
        }

        val testRepo = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepository,
            transactionDao = transactionDao,
            offlineWriteQueue = queue,
            workspaceDao = workspaceDao,
            userMerchantVerificationRepository = cancellingRepository,
        )

        val transaction = Transaction(
            id = EntityId("tx-cancel"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(5000, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = "Netflix",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        assertFailsWith<CancellationException> {
            testRepo.create(transaction)
        }
    }

    @Test
    fun excludedTransactions_doNotQueryDecisionRepository_lookupCountIsZero() = runTest {
        var lookupCalls = 0
        val countingRepo = object : UserMerchantVerificationRepository by verificationRepository {
            override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult {
                lookupCalls++
                throw IllegalStateException("Decision repository should NEVER be queried for excluded transactions!")
            }
        }

        val testRepo = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepository,
            transactionDao = transactionDao,
            offlineWriteQueue = queue,
            workspaceDao = workspaceDao,
            userMerchantVerificationRepository = countingRepo,
        )

        val tx = Transaction(
            id = EntityId("tx-excluded-1"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(100000, Currency.TRY),
            type = TransactionType.INCOME,
            categoryId = EntityId("cat-default"),
            description = "Maaş Ödemesi",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        val createRes = testRepo.create(tx)
        assertTrue(createRes is RepositoryResult.Success)
        assertEquals(0, lookupCalls, "Excluded işlem için karar repository sorgulanmamalıdır")
        val stored = transactionDao.getByIdAndOwner("tx-excluded-1", userAId)
        assertNotNull(stored)
        assertNull(stored.merchantId)

        val invalidReturningRepo = object : UserMerchantVerificationRepository by verificationRepository {
            override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult {
                return com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult.InvalidStoredVerification("corrupt-id")
            }
        }
        val testRepo2 = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepository,
            transactionDao = transactionDao,
            offlineWriteQueue = queue,
            workspaceDao = workspaceDao,
            userMerchantVerificationRepository = invalidReturningRepo,
        )
        val tx2 = tx.copy(id = EntityId("tx-excluded-2"), description = "EFT Kira")
        val createRes2 = testRepo2.create(tx2)
        assertTrue(createRes2 is RepositoryResult.Success, "Excluded işlem bozuk doğrulama olsa bile sorgulamadığı için başarılı kaydedilmelidir")
    }

    @Test
    fun unknownMerchantId_inValidAssignVerification_isTreatedAsInvalidVerificationFailClosed() = runTest {
        val unknownMerchantId = "non_existent_merchant_999"
        val rawAlias = "Bilinmeyen Market"
        val normalizedAlias = "BILINMEYEN MARKET"
        val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(
            userAId,
            normalizedAlias,
        )

        db.localMutationDao().upsertUserMerchantVerificationRow(
            UserMerchantVerificationEntity(
                id = deterministicId,
                ownerId = userAId,
                workspaceId = null,
                scopeKey = "user:$userAId",
                normalizedAlias = normalizedAlias,
                decisionCode = "ASSIGN",
                merchantId = unknownMerchantId,
                scopeCode = "PERSONAL",
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            ),
        )

        // Doğrudan ön koşul assert'ü: repository decode işlemi başarılı, mapper'da takılmıyor,
        // karar ASSIGN ve merchantId unknownMerchantId.
        val decisionResult = verificationRepository.getDecisionForAlias(rawAlias)
        assertIs<UserMerchantVerificationLookupResult.Decision>(decisionResult)
        val decision = decisionResult.decision
        assertIs<UserMerchantDecision.Assign>(decision)
        assertEquals(unknownMerchantId, decision.merchantId, "Ön koşul: Karar geçerli decode edilmiş ASSIGN(unknownMerchantId) olmalıdır")

        val tx = Transaction(
            id = EntityId("tx-unknown-merchant"),
            ownerId = EntityId(userAId),
            workspaceId = null,
            amount = Money(2500, Currency.TRY),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-default"),
            description = rawAlias,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate.parse("2026-10-18"),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
            note = null,
            merchantInfo = null,
        )

        // 1. Create: Storage failure, transaction ve outbox yazımı yok
        val initialOutboxCount = db.syncOperationDao().observePendingCount("user:$userAId").first()
        val createRes = transactionRepository.create(tx)
        assertIs<RepositoryResult.Failure>(createRes)
        val createErr = createRes.error
        assertIs<AppError.Storage>(createErr)
        assertEquals("invalid_stored_merchant_verification", createErr.code)
        assertNull(transactionDao.getByIdAndOwner("tx-unknown-merchant", userAId), "Bozuk/katalogda olmayan ASSIGN satırında tx yazılmamalıdır")
        val outboxAfterCreate = db.syncOperationDao().observePendingCount("user:$userAId").first()
        assertEquals(initialOutboxCount, outboxAfterCreate, "Create başarısız olduğunda outbox operasyonu yazılmamalıdır")

        // 2. Update: Başarısız güncellemede eski entity ve outbox snapshot'ı korunur
        val validTx = tx.copy(id = EntityId("tx-valid-for-update"), description = "Geçerli Bir Market")
        val initRes = transactionRepository.create(validTx)
        assertTrue(initRes is RepositoryResult.Success)
        val outboxBeforeUpdate = db.syncOperationDao().observePendingCount("user:$userAId").first()

        val updateRes = transactionRepository.update(validTx.copy(description = rawAlias))
        assertIs<RepositoryResult.Failure>(updateRes)
        val updateErr = updateRes.error
        assertIs<AppError.Storage>(updateErr)
        assertEquals("invalid_stored_merchant_verification", updateErr.code)
        val originalStored = transactionDao.getByIdAndOwner("tx-valid-for-update", userAId)
        assertNotNull(originalStored)
        assertEquals("Geçerli Bir Market", originalStored.description, "Başarısız update işlemi sonrasında eski açıklama korunmalıdır")
        val outboxAfterUpdate = db.syncOperationDao().observePendingCount("user:$userAId").first()
        assertEquals(outboxBeforeUpdate, outboxAfterUpdate, "Update başarısız olduğunda outbox snapshot'ı korunmalıdır")

        // 3. Installment: Sıfır kısmi transaction ve outbox yazımı
        val outboxBeforeInst = db.syncOperationDao().observePendingCount("user:$userAId").first()
        val installmentGroup = listOf(
            tx.copy(id = EntityId("inst-1"), description = "Normal Market", installment = InstallmentInfo(1, 2, EntityId("grp-unknown"))),
            tx.copy(id = EntityId("inst-2"), description = rawAlias, installment = InstallmentInfo(2, 2, EntityId("grp-unknown"))),
        )
        val instRes = transactionRepository.createInstallmentGroup(installmentGroup)
        assertIs<RepositoryResult.Failure>(instRes)
        val instErr = instRes.error
        assertIs<AppError.Storage>(instErr)
        assertEquals("invalid_stored_merchant_verification", instErr.code)
        assertNull(transactionDao.getByIdAndOwner("inst-1", userAId), "Kısmi yazım kesinlikle engellenmelidir (inst-1 yazılmamalı)")
        assertNull(transactionDao.getByIdAndOwner("inst-2", userAId), "Kısmi yazım kesinlikle engellenmelidir (inst-2 yazılmamalı)")
        val outboxAfterInst = db.syncOperationDao().observePendingCount("user:$userAId").first()
        assertEquals(outboxBeforeInst, outboxAfterInst, "Taksit grubunda kısmi outbox yazılmamalıdır")

        // 4. Recurring: Occurrence üretilmez, ilgili kural skip edilir
        recurringDao.upsert(
            RecurringTransactionEntity(
                id = "rec-unknown-m",
                ownerId = userAId,
                workspaceId = null,
                categoryId = "cat-default",
                amountMinor = 1500L,
                currencyCode = "TRY",
                typeCode = "EXPENSE",
                paymentMethodCode = "CREDIT_CARD",
                description = rawAlias,
                frequencyCode = RecurrenceFrequency.MONTHLY.name,
                interval = 1,
                startDate = "2026-10-01",
                endDate = null,
                lastGeneratedDate = null,
                isActive = true,
                createdAtEpochMillis = 1000L,
                sync = SyncMetadata(
                    syncStatus = SyncStatus.SYNCED.name,
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 1L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            ),
        )
        val genRes = recurringRepository.generateDueTransactions(
            throughDate = LocalDate.parse("2026-10-15"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        assertIs<RepositoryResult.Success<GenerateRecurringTransactionsResult>>(genRes)
        assertTrue(genRes.value.skippedRecurringIds.any { it.value == "rec-unknown-m" })
        assertNull(transactionDao.getByIdAndOwner("generated-1", userAId), "Recurring occurrence üretilmemelidir")

        // 5. CancellationException hiçbir katmanda yutulmaz
        val cancellingRepo = object : UserMerchantVerificationRepository by verificationRepository {
            override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult {
                throw CancellationException("coroutine cancelled")
            }
        }
        val cancellingTxRepo = OfflineFirstTransactionRepository(
            authRepository = fakeAuthRepository,
            transactionDao = transactionDao,
            offlineWriteQueue = queue,
            workspaceDao = workspaceDao,
            userMerchantVerificationRepository = cancellingRepo,
        )
        assertFailsWith<CancellationException> {
            cancellingTxRepo.create(tx.copy(id = EntityId("tx-cancel-check")))
        }
    }
}
