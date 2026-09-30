package com.feniqo.mobile.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.repository.ActiveWorkspaceScope
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PersonalBackupImporterActorScopeTest {

    private lateinit var database: FeniqoDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var syncOperationDao: SyncOperationDao
    private lateinit var localMutationDao: LocalMutationDao
    private lateinit var writeQueue: OfflineWriteQueue
    private lateinit var planner: BackupImportPlanner

    private companion object {
        const val USER_A_ID = "00000000-0000-4000-8000-00000000000a"
        const val USER_B_ID = "00000000-0000-4000-8000-00000000000b"

        val SCOPE_A = SyncScopeKey.user(USER_A_ID).rawValue
        val SCOPE_B = SyncScopeKey.user(USER_B_ID).rawValue

        fun testSession(
            userId: String,
            email: String = "$userId@feniqo.test",
            expiresAt: Instant = Instant.parse("2026-08-15T12:00:00Z"),
        ) = AuthSession(
            userId = EntityId(userId),
            email = email,
            expiresAt = expiresAt,
        )

        fun sampleBackupJson(
            categoryId: String = "cat-sample-1",
            transactionId: String = "tx-sample-1",
        ): String = FeniqoBackupCodec.encode(
            FeniqoBackupV1(
                formatVersion = 1,
                createdAt = "2026-08-15T12:00:00Z",
                scope = "PERSONAL",
                categories = listOf(
                    BackupCategoryV1(
                        id = categoryId,
                        name = "Market",
                        type = "EXPENSE",
                        color = "#FF0000",
                        icon = "cart",
                    ),
                ),
                transactions = listOf(
                    BackupTransactionV1(
                        id = transactionId,
                        amountMinor = 15000L,
                        currency = "TRY",
                        type = "EXPENSE",
                        categoryId = categoryId,
                        description = "Haftalık mutfak harcaması",
                        paymentMethod = "CREDIT_CARD",
                        transactionDate = "2026-08-15",
                        createdAt = "2026-08-15T12:00:00Z",
                        installment = null,
                        note = "Düzenli harcama",
                    ),
                ),
            ),
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = context,
            factory = { FeniqoDatabaseConstructor.initialize() },
        )
            .setQueryCoroutineContext(Dispatchers.Default)
            .allowMainThreadQueries()
            .build()

        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        syncOperationDao = database.syncOperationDao()
        localMutationDao = database.localMutationDao()
        writeQueue = OfflineWriteQueue(localMutationDao, syncOperationDao)
        planner = BackupImportPlanner(SequentialIdGenerator("import-gen"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun import_for_user_A_writes_entities_and_outbox_only_with_A_actor_scope() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        val success = assertIs<BackupImportResult.Success>(result)
        assertEquals(1, success.categoryCount)
        assertEquals(1, success.transactionCount)

        // Category ownerId A olmalı
        val categoriesA = categoryDao.observeAll(USER_A_ID, null, null).first()
        assertEquals(1, categoriesA.size)
        val categoryA = categoriesA.first()
        assertEquals(USER_A_ID, categoryA.ownerId)
        assertEquals(null, categoryA.workspaceId)

        // Transaction ownerId A olmalı
        val transactionsA = transactionDao.observeAll(
            ownerId = USER_A_ID,
            workspaceId = null,
            startDate = null,
            endDate = null,
            typeCode = null,
            categoryId = null,
            paymentMethodCode = null,
            searchQuery = null,
        ).first()
        assertEquals(1, transactionsA.size)
        val transactionA = transactionsA.first()
        assertEquals(USER_A_ID, transactionA.ownerId)
        assertEquals(null, transactionA.workspaceId)
        assertEquals(USER_A_ID, transactionA.paidByUserId)

        // Bütün outbox satırları USER:A scope'unda olmalı
        val readyOpsA = writeQueue.getReadyOperations(SCOPE_A)
        assertEquals(2, readyOpsA.size)
        assertTrue(readyOpsA.all { it.syncScopeKey == SCOPE_A })

        // USER:B ready/pending sorguları bu operasyonları görmemeli
        val readyOpsB = writeQueue.getReadyOperations(SCOPE_B)
        assertTrue(readyOpsB.isEmpty())
        assertEquals(0, syncOperationDao.observePendingCount(SCOPE_B).first())
    }

    @Test
    fun backup_payload_cannot_override_authenticated_actor() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        // Payload eski ID'ler ve yabancı referanslar içerse bile
        val backupJson = sampleBackupJson(
            categoryId = "foreign-user-category-xyz",
            transactionId = "foreign-user-transaction-999",
        )

        val result = importer.import(backupJson)
        assertIs<BackupImportResult.Success>(result)

        // Room'a yazılan category ve transaction daima authenticated actor USER:A'ya aittir
        val categories = categoryDao.observeAll(USER_A_ID, null, null).first()
        assertEquals(1, categories.size)
        assertEquals(USER_A_ID, categories.first().ownerId)

        val transactions = transactionDao.observeAll(
            ownerId = USER_A_ID,
            workspaceId = null,
            startDate = null,
            endDate = null,
            typeCode = null,
            categoryId = null,
            paymentMethodCode = null,
            searchQuery = null,
        ).first()
        assertEquals(1, transactions.size)
        assertEquals(USER_A_ID, transactions.first().ownerId)

        val opsA = writeQueue.getReadyOperations(SCOPE_A)
        assertTrue(opsA.all { it.syncScopeKey == SCOPE_A })
        assertTrue(writeQueue.getReadyOperations(SCOPE_B).isEmpty())
    }

    @Test
    fun session_switch_from_A_to_B_before_atomic_enqueue_fails_closed_without_any_rows() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        // İlk session okumasından sonra oturum B'ye geçer
        authRepo.onFirstSessionRead = {
            authRepo.sessionFlow.value = testSession(USER_B_ID)
        }
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        val failure = assertIs<BackupImportResult.Failure>(result)
        assertEquals("auth_session_changed", failure.reason)

        // Fail-closed: hiçbir category, transaction veya outbox satırı yazılmamalı
        assertEquals(0, categoryDao.observeAll(USER_A_ID, null, null).first().size)
        assertEquals(0, categoryDao.observeAll(USER_B_ID, null, null).first().size)
        assertEquals(
            0,
            transactionDao.observeAll(USER_A_ID, null, null, null, null, null, null, null).first().size,
        )
        assertEquals(
            0,
            transactionDao.observeAll(USER_B_ID, null, null, null, null, null, null, null).first().size,
        )
        assertTrue(writeQueue.getReadyOperations(SCOPE_A).isEmpty())
        assertTrue(writeQueue.getReadyOperations(SCOPE_B).isEmpty())
        assertEquals(0, syncOperationDao.observePendingCount(SCOPE_A).first())
        assertEquals(0, syncOperationDao.observePendingCount(SCOPE_B).first())
    }

    @Test
    fun sign_out_before_atomic_enqueue_fails_closed_without_any_rows() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        // İlk session okumasından sonra kullanıcı çıkış yapar (null oturum)
        authRepo.onFirstSessionRead = {
            authRepo.sessionFlow.value = null
        }
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        val failure = assertIs<BackupImportResult.Failure>(result)
        assertEquals("auth_session_changed", failure.reason)

        // Fail-closed: hiçbir satır yazılmamalı
        assertEquals(0, categoryDao.observeAll(USER_A_ID, null, null).first().size)
        assertEquals(
            0,
            transactionDao.observeAll(USER_A_ID, null, null, null, null, null, null, null).first().size,
        )
        assertTrue(writeQueue.getReadyOperations(SCOPE_A).isEmpty())
        assertEquals(0, syncOperationDao.observePendingCount(SCOPE_A).first())
    }

    @Test
    fun same_user_session_refresh_before_enqueue_is_accepted() = runTest {
        val authRepo = ControlledAuthRepository(
            testSession(
                userId = USER_A_ID,
                email = "initial@feniqo.test",
                expiresAt = Instant.parse("2026-08-15T12:00:00Z"),
            ),
        )
        // Aynı kullanıcı için token/expiry yenilenmesi gerçekleşir
        authRepo.onFirstSessionRead = {
            authRepo.sessionFlow.value = testSession(
                userId = USER_A_ID,
                email = "refreshed@feniqo.test",
                expiresAt = Instant.parse("2026-09-01T12:00:00Z"),
            )
        }
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        val success = assertIs<BackupImportResult.Success>(result)
        assertEquals(1, success.categoryCount)
        assertEquals(1, success.transactionCount)

        val opsA = writeQueue.getReadyOperations(SCOPE_A)
        assertEquals(2, opsA.size)
        assertTrue(opsA.all { it.syncScopeKey == SCOPE_A })
    }

    @Test
    fun completed_A_import_survives_switch_to_B_but_is_not_visible_in_B_outbox_queries() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        assertIs<BackupImportResult.Success>(result)

        // A'nın oluşturulan operasyon kimlikleri ve payload'ları
        val opsA = writeQueue.getReadyOperations(SCOPE_A)
        assertEquals(2, opsA.size)
        val initialOpIds = opsA.map { it.operationId }
        val initialPayloads = opsA.map { it.payloadJson }

        // Import tamamlandıktan sonra kullanıcı B oturum açar
        authRepo.sessionFlow.value = testSession(USER_B_ID)

        // B sorguları A'nın operasyonlarını kesinlikle görmez
        val opsB = writeQueue.getReadyOperations(SCOPE_B)
        assertTrue(opsB.isEmpty())
        assertEquals(0, syncOperationDao.observePendingCount(SCOPE_B).first())

        // A'nın verileri silinmez, operation ID ve payload bütünlüğü korunur
        val currentOpsA = writeQueue.getReadyOperations(SCOPE_A)
        assertEquals(2, currentOpsA.size)
        assertEquals(initialOpIds, currentOpsA.map { it.operationId })
        assertEquals(initialPayloads, currentOpsA.map { it.payloadJson })
    }

    @Test
    fun active_workspace_rejects_personal_import_without_writes() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        val activeWorkspaceScope = TestActiveWorkspaceScope(EntityId("workspace-active-1"))
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import(sampleBackupJson())
        val failure = assertIs<BackupImportResult.Failure>(result)
        assertEquals("backup_personal_scope_required", failure.reason)

        assertEquals(0, categoryDao.observeAll(USER_A_ID, null, null).first().size)
        assertEquals(
            0,
            transactionDao.observeAll(USER_A_ID, null, null, null, null, null, null, null).first().size,
        )
        assertTrue(writeQueue.getReadyOperations(SCOPE_A).isEmpty())
    }

    @Test
    fun invalid_backup_fails_before_session_or_room_mutation() = runTest {
        val authRepo = ControlledAuthRepository(testSession(USER_A_ID))
        val activeWorkspaceScope = TestActiveWorkspaceScope(null)
        val importer = DefaultPersonalBackupImporter(
            authRepository = authRepo,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
        )

        val result = importer.import("{ \"invalid\": true }")
        assertIs<BackupImportResult.Failure>(result)

        assertEquals(0, categoryDao.observeAll(USER_A_ID, null, null).first().size)
        assertEquals(
            0,
            transactionDao.observeAll(USER_A_ID, null, null, null, null, null, null, null).first().size,
        )
        assertTrue(writeQueue.getReadyOperations(SCOPE_A).isEmpty())
    }

    private class SequentialIdGenerator(private val prefix: String) : EntityIdGenerator {
        private var counter = 0
        override fun nextId(): EntityId = EntityId("$prefix-${++counter}")
    }

    private class TestActiveWorkspaceScope(
        var activeWorkspaceId: EntityId? = null,
    ) : ActiveWorkspaceScope {
        override fun observe(profileId: EntityId): Flow<EntityId?> = flowOf(activeWorkspaceId)
        override suspend fun current(profileId: EntityId): EntityId? = activeWorkspaceId
    }

    private class ControlledAuthRepository(
        initialSession: AuthSession? = null,
    ) : AuthRepository {
        val sessionFlow = MutableStateFlow(initialSession)
        var onFirstSessionRead: (() -> Unit)? = null
        private var readCount = 0

        override fun observeSession(): Flow<AuthSession?> = flow {
            val current = sessionFlow.value
            readCount++
            if (readCount == 1) {
                onFirstSessionRead?.invoke()
            }
            emit(current)
        }

        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("Kapsam dışı")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("Kapsam dışı")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("Kapsam dışı")
        override suspend fun signOut(): RepositoryResult<Unit> = error("Kapsam dışı")
    }
}
