package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.dao.TransactionWithTags
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstTransactionRepositoryTest {

    @Test
    fun observeTransactions_whenNoSession_returnsEmptyList() = runTest {
        val authRepo = FakeAuthRepository(null)
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val list = repository.observeTransactions().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun observeTransaction_whenNoSession_returnsNull() = runTest {
        val authRepo = FakeAuthRepository(null)
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val item = repository.observeTransaction(EntityId("t1")).first()
        assertNull(item)
    }

    @Test
    fun observeTransactions_switchesOwnerWhenSessionChanges() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleTransactionEntity(id = "t1", ownerId = "user-1"),
                sampleTransactionEntity(id = "t2", ownerId = "user-2"),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val list1 = repository.observeTransactions().first()
        assertEquals(1, list1.size)
        assertEquals("t1", list1.first().id.value)

        // Session changes to user-2
        authRepo.sessionFlow.value = AuthSession(EntityId("user-2"), "u2@feniqo.com", NOW)
        val list2 = repository.observeTransactions().first()
        assertEquals(1, list2.size)
        assertEquals("t2", list2.first().id.value)
    }

    @Test
    fun observeTransaction_doesNotExposeOtherOwnerData() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeTransactionDao(
            listOf(sampleTransactionEntity(id = "t2", ownerId = "user-2")),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val item = repository.observeTransaction(EntityId("t2")).first()
        assertNull(item)
    }

    @Test
    fun observeTransactions_passesAllFilterParametersToDao() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val filter = TransactionFilter(
            period = ReportPeriod(LocalDate(2026, 8, 1), LocalDate(2026, 8, 10)),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            paymentMethod = PaymentMethod.CREDIT_CARD,
            workspaceId = EntityId("ws-1"),
            query = "  market  ",
        )

        repository.observeTransactions(filter).first()

        assertEquals("user-1", dao.lastOwnerId)
        assertEquals("ws-1", dao.lastWorkspaceId)
        assertEquals("2026-08-01", dao.lastStartDate)
        assertEquals("2026-08-10", dao.lastEndDate)
        assertEquals("EXPENSE", dao.lastTypeCode)
        assertEquals("cat-1", dao.lastCategoryId)
        assertEquals("CREDIT_CARD", dao.lastPaymentMethodCode)
        assertEquals("market", dao.lastSearchQuery)
    }

    @Test
    fun create_enqueuesPendingCreateWithVersion0AndNullBaseVersion() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 10_000L },
        )

        val trx = sampleTransaction(id = "t-new", ownerId = "user-1")
        val result = repository.create(trx)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        val enqueued = queue.lastEnqueuedTransaction
        assertEquals("t-new", enqueued?.id)
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued?.sync?.syncStatus)
        assertEquals(0L, enqueued?.sync?.version)
        assertNull(enqueued?.sync?.baseVersion)
        assertNull(enqueued?.sync?.deletedAtEpochMillis)
        assertEquals(OutboxOperationType.CREATE.name, queue.lastInsertedOperation?.operationTypeCode)
        assertTrue(queue.lastTags.isEmpty())
        assertTrue(queue.lastTagLinks.isEmpty())
    }

    @Test
    fun update_preservesRemoteVersionAndSetsPendingUpdate() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 5_000L,
            localUpdatedAtEpochMillis = 5_000L,
            deletedAtEpochMillis = null,
            version = 4L,
            baseVersion = 4L,
            lastSyncError = null,
        )
        val existingEntity = sampleTransactionEntity(id = "t-remote", ownerId = "user-1", sync = existingSync)
        val dao = FakeTransactionDao(listOf(existingEntity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 15_000L },
        )

        val updatedTrx = sampleTransaction(id = "t-remote", ownerId = "user-1", description = "Yeni Açıklama")
        val result = repository.update(updatedTrx)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedTransaction
        assertEquals("t-remote", enqueued?.id)
        assertEquals(SyncStatus.PENDING_UPDATE.name, enqueued?.sync?.syncStatus)
        assertEquals(4L, enqueued?.sync?.version) // Version yerelde artırılmaz
        assertEquals(4L, enqueued?.sync?.baseVersion)
        assertEquals(15_000L, enqueued?.sync?.localUpdatedAtEpochMillis)
        assertNull(enqueued?.sync?.deletedAtEpochMillis)
        assertEquals(OutboxOperationType.UPDATE.name, queue.lastInsertedOperation?.operationTypeCode)
    }

    @Test
    fun update_onUnsyncedRecord_preservesPendingCreateAndNullBaseVersion() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = newSyncMetadata(5_000L) // PENDING_CREATE, version=0, baseVersion=null
        val existingEntity = sampleTransactionEntity(id = "t-local", ownerId = "user-1", sync = existingSync)
        val dao = FakeTransactionDao(listOf(existingEntity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 15_000L },
        )

        val updatedTrx = sampleTransaction(id = "t-local", ownerId = "user-1", description = "Düzenlenmiş")
        val result = repository.update(updatedTrx)

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedTransaction
        assertEquals(SyncStatus.PENDING_CREATE.name, enqueued?.sync?.syncStatus)
        assertEquals(0L, enqueued?.sync?.version)
        assertNull(enqueued?.sync?.baseVersion)
        assertEquals(OutboxOperationType.CREATE.name, queue.lastInsertedOperation?.operationTypeCode) // Unsynced create korunur
    }

    @Test
    fun softDelete_setsPendingDeleteAndDeletedAtTimestamp() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("user-1"), "u1@feniqo.com", NOW))
        val existingSync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 5_000L,
            localUpdatedAtEpochMillis = 5_000L,
            deletedAtEpochMillis = null,
            version = 3L,
            baseVersion = 3L,
            lastSyncError = null,
        )
        val existingEntity = sampleTransactionEntity(id = "t-del", ownerId = "user-1", sync = existingSync)
        val dao = FakeTransactionDao(listOf(existingEntity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepo,
            dao,
            queue.queue,
            nowEpochMillisProvider = { 20_000L },
        )

        val result = repository.softDelete(EntityId("t-del"))

        assertIs<RepositoryResult.Success<Unit>>(result)
        val enqueued = queue.lastEnqueuedTransaction
        assertEquals(SyncStatus.PENDING_DELETE.name, enqueued?.sync?.syncStatus)
        assertEquals(3L, enqueued?.sync?.version)
        assertEquals(3L, enqueued?.sync?.baseVersion)
        assertEquals(20_000L, enqueued?.sync?.deletedAtEpochMillis)
        assertEquals(OutboxOperationType.DELETE.name, queue.lastInsertedOperation?.operationTypeCode)
    }

    @Test
    fun operations_rethrowCancellationException() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        assertFailsWith<CancellationException> {
            repository.create(sampleTransaction("t1", "u1"))
        }
    }

    @Test
    fun operations_rethrowErrorWithoutConvertingToRepositoryResult() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw AssertionError("critical_assertion_error")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        assertFailsWith<AssertionError> {
            repository.create(sampleTransaction("t1", "u1"))
        }
    }

    @Test
    fun createInstallmentGroup_withoutSession_returnsAuthError() = runTest {
        val authRepo = FakeAuthRepository(null)
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 2),
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        val error = assertIs<AppError.Authentication>(failure.error)
        assertEquals("auth_session_required", error.code)
    }

    @Test
    fun createInstallmentGroup_withListSize1Or61_returnsOutOfRangeError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val size1 = listOf(sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 1))
        val result1 = repository.createInstallmentGroup(size1)
        val failure1 = assertIs<RepositoryResult.Failure>(result1)
        assertEquals("installment_count_out_of_range", assertIs<AppError.Validation>(failure1.error).code)

        val size61 = (1..61).map { sampleInstallmentTransaction("t$it", "u1", "grp-1", it, 61) }
        val result61 = repository.createInstallmentGroup(size61)
        val failure61 = assertIs<RepositoryResult.Failure>(result61)
        assertEquals("installment_count_out_of_range", assertIs<AppError.Validation>(failure61.error).code)
    }

    @Test
    fun createInstallmentGroup_withOwnerMismatch_returnsAuthError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t2", "other-user", "grp-1", 2, 2),
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_owner_mismatch", assertIs<AppError.Authentication>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withDuplicateTransactionId_returnsDuplicateIdError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t1", "u1", "grp-1", 2, 2), // Duplicate ID
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("duplicate_transaction_id", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withNullInstallment_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleTransaction("t2", "u1"), // installment == null
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withDifferentGroupIds_returnsGroupMismatchError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t2", "u1", "grp-2", 2, 2),
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_group_mismatch", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withWrongTotal_returnsTotalMismatchError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 3), // Total 3 ama liste 2
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 3),
        )
        val result = repository.createInstallmentGroup(transactions)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_total_mismatch", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withInvalidNumbers_returnsNumbersInvalidError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val duplicateNumbers = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 1, 2), // 1 tekrar etti
        )
        val result = repository.createInstallmentGroup(duplicateNumbers)
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_numbers_invalid", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun createInstallmentGroup_withDifferentCurrencyTypeCategoryPaymentOrWorkspace_returnsMismatchError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        // Farklı para birimi
        val diffCurrency = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2, currency = Currency.TRY),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 2, currency = Currency.USD),
        )
        val resCurr = repository.createInstallmentGroup(diffCurrency)
        assertEquals("installment_currency_mismatch", assertIs<AppError.Validation>(assertIs<RepositoryResult.Failure>(resCurr).error).code)

        // Farklı kategori
        val diffCat = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2, categoryId = "cat-1"),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 2, categoryId = "cat-2"),
        )
        val resCat = repository.createInstallmentGroup(diffCat)
        assertEquals("installment_category_mismatch", assertIs<AppError.Validation>(assertIs<RepositoryResult.Failure>(resCat).error).code)
    }

    @Test
    fun createInstallmentGroup_withValid3Installments_succeedsAndReturnsGroupId() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepository = authRepo,
            transactionDao = dao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 10_000L },
        )

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 3, amountMinor = 333L, date = LocalDate(2026, 8, 21)),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 3, amountMinor = 333L, date = LocalDate(2026, 9, 21)),
            sampleInstallmentTransaction("t3", "u1", "grp-1", 3, 3, amountMinor = 334L, date = LocalDate(2026, 10, 21)),
        )

        val result = repository.createInstallmentGroup(transactions)
        val success = assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("grp-1", success.value.value)

        // Queue'ya tek seferde 3 unit yazıldı
        assertEquals(3, queue.lastBatchUnits.size)
        for (i in 0 until 3) {
            val unit = queue.lastBatchUnits[i]
            assertEquals("t${i + 1}", unit.entity.id)
            assertEquals("PENDING_CREATE", unit.entity.sync.syncStatus)
            assertEquals(0L, unit.entity.sync.version)
            assertEquals(null, unit.entity.sync.baseVersion)
            assertEquals(null, unit.entity.sync.deletedAtEpochMillis)
            assertEquals("CREATE", unit.operation.operationTypeCode)
        }
    }

    @Test
    fun createInstallmentGroup_rethrowsCancellationExceptionAndError() = runTest {
        val authRepoCancel = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repoCancel = OfflineFirstTransactionRepository(authRepoCancel, dao, queue.queue)

        val transactions = listOf(
            sampleInstallmentTransaction("t1", "u1", "grp-1", 1, 2),
            sampleInstallmentTransaction("t2", "u1", "grp-1", 2, 2),
        )

        assertFailsWith<CancellationException> {
            repoCancel.createInstallmentGroup(transactions)
        }

        val authRepoError = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw AssertionError("critical_error")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val repoError = OfflineFirstTransactionRepository(authRepoError, dao, queue.queue)

        assertFailsWith<AssertionError> {
            repoError.createInstallmentGroup(transactions)
        }
    }

    @Test
    fun observeInstallmentGroup_withoutSession_emitsEmptyList() = runTest {
        val authRepo = FakeAuthRepository(null)
        val dao = FakeTransactionDao(listOf(sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2)))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.observeInstallmentGroup(EntityId("grp-1")).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun observeInstallmentGroup_withSession_emitsOwnerInstallmentsSortedByNumber() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t2", "u1", "grp-1", 2, 2),
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.observeInstallmentGroup(EntityId("grp-1")).first()
        assertEquals(2, result.size)
        assertEquals("t1", result[0].id.value)
        assertEquals(1, result[0].installment?.number)
        assertEquals("t2", result[1].id.value)
        assertEquals(2, result[1].installment?.number)
    }

    @Test
    fun observeInstallmentGroup_doesNotEmitOtherUsersInstallments() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2),
                sampleInstallmentTransactionEntity("t2", "other-user", "grp-1", 2, 2),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.observeInstallmentGroup(EntityId("grp-1")).first()
        assertEquals(1, result.size)
        assertEquals("t1", result[0].id.value)
    }

    @Test
    fun observeInstallmentGroup_whenSessionChanges_switchesToNewOwner() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "u1@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 1),
                sampleInstallmentTransactionEntity("t2", "u2", "grp-1", 1, 1),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val flow = repository.observeInstallmentGroup(EntityId("grp-1"))
        assertEquals("t1", flow.first().first().id.value)

        authRepo.sessionFlow.value = AuthSession(EntityId("u2"), "u2@test.com", NOW)
        assertEquals("t2", flow.first().first().id.value)
    }

    @Test
    fun softDeleteInstallments_withoutSession_returnsAuthError() = runTest {
        val authRepo = FakeAuthRepository(null)
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("auth_session_required", assertIs<AppError.Authentication>(failure.error).code)
    }

    @Test
    fun softDeleteInstallments_withEmptySet_returnsEmptyError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(emptySet())
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_ids_empty", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun softDeleteInstallments_withMissingOrOtherOwnerId_returnsNotFoundError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2),
                sampleInstallmentTransactionEntity("t2", "other-user", "grp-1", 2, 2), // other user
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1"), EntityId("t2")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_not_found", assertIs<AppError.Validation>(failure.error).code)
    }

    @Test
    fun softDeleteInstallments_withNonInstallmentTransaction_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleTransactionEntity("t1", "u1"), // installment == null
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withGroupIdPresentAndNumberNull_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3).copy(installmentNumber = null)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withGroupIdPresentAndTotalNull_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3).copy(totalInstallments = null)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withGroupIdNullAndNumberTotalPresent_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3).copy(installmentGroupId = null)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withNumberZero_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3).copy(installmentNumber = 0)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withNumberGreaterThanTotal_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3).copy(installmentNumber = 4, totalInstallments = 3)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withTotalOne_returnsInstallmentInfoRequiredError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val entity = sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 1)
        val dao = FakeTransactionDao(listOf(entity))
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_info_required", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withDifferentGroupIds_returnsGroupMismatchError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2),
                sampleInstallmentTransactionEntity("t2", "u1", "grp-2", 2, 2),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1"), EntityId("t2")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_group_mismatch", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withDifferentTotalInSameGroup_returnsTotalMismatchError() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3),
                sampleInstallmentTransactionEntity("t2", "u1", "grp-1", 2, 4), // Total 4 vs 3
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(authRepo, dao, queue.queue)

        val result = repository.softDeleteInstallments(setOf(EntityId("t1"), EntityId("t2")))
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("installment_total_mismatch", assertIs<AppError.Validation>(failure.error).code)
        assertEquals(0, queue.lastBatchDeleteUnits.size)
    }

    @Test
    fun softDeleteInstallments_withValidSubset_softDeletesAtomically() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 4),
                sampleInstallmentTransactionEntity("t2", "u1", "grp-1", 2, 4),
                sampleInstallmentTransactionEntity("t3", "u1", "grp-1", 3, 4),
                sampleInstallmentTransactionEntity("t4", "u1", "grp-1", 4, 4),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepository = authRepo,
            transactionDao = dao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 30_000L },
        )

        // Yalnız 2/4 ve 3/4 siliniyor (subset)
        val result = repository.softDeleteInstallments(setOf(EntityId("t2"), EntityId("t3")))
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(2, queue.lastBatchDeleteUnits.size)
        assertEquals("t2", queue.lastBatchDeleteUnits[0].entity.id)
        assertEquals("t3", queue.lastBatchDeleteUnits[1].entity.id)
        assertEquals("PENDING_DELETE", queue.lastBatchDeleteUnits[0].entity.sync.syncStatus)
        assertEquals("PENDING_DELETE", queue.lastBatchDeleteUnits[1].entity.sync.syncStatus)
        assertEquals(30_000L, queue.lastBatchDeleteUnits[0].entity.sync.deletedAtEpochMillis)
        assertEquals(30_000L, queue.lastBatchDeleteUnits[1].entity.sync.deletedAtEpochMillis)
    }

    @Test
    fun softDeleteInstallments_withValidSingleId_softDeletesAtomically() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 2, sync = newSyncMetadata(1000L).copy(syncStatus = "SYNCED", version = 3L, baseVersion = 3L)),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepository = authRepo,
            transactionDao = dao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 10_000L },
        )

        val result = repository.softDeleteInstallments(setOf(EntityId("t1")))
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(1, queue.lastBatchDeleteUnits.size)
        val unit = queue.lastBatchDeleteUnits[0]
        assertEquals("t1", unit.entity.id)
        assertEquals("PENDING_DELETE", unit.entity.sync.syncStatus)
        assertEquals(3L, unit.entity.sync.version)
        assertEquals(3L, unit.entity.sync.baseVersion)
        assertEquals(10_000L, unit.entity.sync.deletedAtEpochMillis)
        assertEquals(3L, unit.operation.baseVersion)
        assertEquals("DELETE", unit.operation.operationTypeCode)
    }

    @Test
    fun softDeleteInstallments_withValidMultipleIds_softDeletesAtomicallyPreservingTagsAndVersion() = runTest {
        val authRepo = FakeAuthRepository(AuthSession(EntityId("u1"), "test@test.com", NOW))
        val dao = FakeTransactionDao(
            listOf(
                sampleInstallmentTransactionEntity("t1", "u1", "grp-1", 1, 3, sync = newSyncMetadata(1000L).copy(syncStatus = "SYNCED", version = 1L, baseVersion = 1L)),
                sampleInstallmentTransactionEntity("t2", "u1", "grp-1", 2, 3, sync = newSyncMetadata(1000L).copy(syncStatus = "SYNCED", version = 2L, baseVersion = 2L)),
                sampleInstallmentTransactionEntity("t3", "u1", "grp-1", 3, 3, sync = newSyncMetadata(1000L).copy(syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)),
            ),
        )
        val queue = FakeOfflineWriteQueueHolder()
        val repository = OfflineFirstTransactionRepository(
            authRepository = authRepo,
            transactionDao = dao,
            offlineWriteQueue = queue.queue,
            nowEpochMillisProvider = { 20_000L },
        )

        val result = repository.softDeleteInstallments(setOf(EntityId("t1"), EntityId("t2"), EntityId("t3")))
        assertIs<RepositoryResult.Success<Unit>>(result)

        assertEquals(3, queue.lastBatchDeleteUnits.size)
        val u1 = queue.lastBatchDeleteUnits[0]
        assertEquals("t1", u1.entity.id)
        assertEquals(1L, u1.entity.sync.version)
        assertEquals(1L, u1.entity.sync.baseVersion)
        assertEquals(20_000L, u1.entity.sync.deletedAtEpochMillis)
        assertEquals("PENDING_DELETE", u1.entity.sync.syncStatus)

        val u3 = queue.lastBatchDeleteUnits[2]
        assertEquals("t3", u3.entity.id)
        assertEquals(0L, u3.entity.sync.version)
        assertEquals(null, u3.entity.sync.baseVersion)
        assertEquals(20_000L, u3.entity.sync.deletedAtEpochMillis)
        assertEquals("PENDING_DELETE", u3.entity.sync.syncStatus)
    }

    @Test
    fun softDeleteInstallments_rethrowsCancellationExceptionAndError() = runTest {
        val authRepoCancel = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val dao = FakeTransactionDao()
        val queue = FakeOfflineWriteQueueHolder()
        val repoCancel = OfflineFirstTransactionRepository(authRepoCancel, dao, queue.queue)

        assertFailsWith<CancellationException> {
            repoCancel.softDeleteInstallments(setOf(EntityId("t1")))
        }

        val authRepoError = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw AssertionError("critical_error")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
            override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
            override suspend fun refreshSession() = RepositoryResult.Success(Unit)
            override suspend fun signOut() = RepositoryResult.Success(Unit)
        }
        val repoError = OfflineFirstTransactionRepository(authRepoError, dao, queue.queue)

        assertFailsWith<AssertionError> {
            repoError.softDeleteInstallments(setOf(EntityId("t1")))
        }
    }

    private fun sampleTransaction(
        id: String,
        ownerId: String,
        description: String = "Test",
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId(ownerId),
        workspaceId = null,
        amount = Money(1000L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        description = description,
        paymentMethod = PaymentMethod.CASH,
        transactionDate = LocalDate(2026, 8, 21),
        receiptPath = null,
        installment = null,
        createdAt = NOW,
    )

    private fun sampleInstallmentTransaction(
        id: String,
        ownerId: String,
        groupId: String,
        number: Int,
        total: Int,
        amountMinor: Long = 1000L,
        currency: Currency = Currency.TRY,
        categoryId: String = "cat-1",
        date: LocalDate = LocalDate(2026, 8, 21),
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId(ownerId),
        workspaceId = null,
        amount = Money(amountMinor, currency),
        type = TransactionType.EXPENSE,
        categoryId = EntityId(categoryId),
        description = "Taksit $number/$total",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = null,
        installment = com.feniqo.mobile.domain.model.InstallmentInfo(
            groupId = EntityId(groupId),
            number = number,
            total = total,
        ),
        createdAt = NOW,
    )

    private fun sampleTransactionEntity(
        id: String,
        ownerId: String,
        sync: SyncMetadata = newSyncMetadata(1_000L),
        description: String = "Test",
    ) = sampleTransaction(id, ownerId, description).toEntity(sync)

    private fun sampleInstallmentTransactionEntity(
        id: String,
        ownerId: String,
        groupId: String,
        number: Int,
        total: Int,
        sync: SyncMetadata = newSyncMetadata(1_000L),
    ) = sampleInstallmentTransaction(id, ownerId, groupId, number, total).toEntity(sync)

    private companion object {
        val NOW = Instant.parse("2026-08-21T00:00:00Z")
    }
}

private class FakeAuthRepository(session: AuthSession?) : AuthRepository {
    val sessionFlow = MutableStateFlow(session)
    override fun observeSession(): Flow<AuthSession?> = sessionFlow
    override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = RepositoryResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, fullName: String?) = RepositoryResult.Success(EntityId("u-1"))
    override suspend fun refreshSession() = RepositoryResult.Success(Unit)
    override suspend fun signOut() = RepositoryResult.Success(Unit)
}

private class FakeTransactionDao(initial: List<TransactionEntity> = emptyList()) : TransactionDao {
    private val transactions = MutableStateFlow(initial)

    var lastOwnerId: String? = null
    var lastWorkspaceId: String? = null
    var lastStartDate: String? = null
    var lastEndDate: String? = null
    var lastTypeCode: String? = null
    var lastCategoryId: String? = null
    var lastPaymentMethodCode: String? = null
    var lastSearchQuery: String? = null

    override fun observeAll(
        ownerId: String,
        workspaceId: String?,
        startDate: String?,
        endDate: String?,
        typeCode: String?,
        categoryId: String?,
        paymentMethodCode: String?,
        searchQuery: String?,
    ): Flow<List<TransactionEntity>> {
        lastOwnerId = ownerId
        lastWorkspaceId = workspaceId
        lastStartDate = startDate
        lastEndDate = endDate
        lastTypeCode = typeCode
        lastCategoryId = categoryId
        lastPaymentMethodCode = paymentMethodCode
        lastSearchQuery = searchQuery

        return transactions.map { list ->
            list.filter {
                it.ownerId == ownerId &&
                it.sync.deletedAtEpochMillis == null &&
                (workspaceId == null && it.workspaceId == null || it.workspaceId == workspaceId)
            }
        }
    }

    override fun observeById(id: String): Flow<TransactionEntity?> =
        transactions.map { list -> list.firstOrNull { it.id == id && it.sync.deletedAtEpochMillis == null } }

    override fun observeByIdAndOwner(id: String, ownerId: String): Flow<TransactionEntity?> =
        transactions.map { list -> list.firstOrNull { it.id == id && it.ownerId == ownerId && it.sync.deletedAtEpochMillis == null } }

    override suspend fun getByIdAndOwner(id: String, ownerId: String): TransactionEntity? =
        transactions.value.firstOrNull { it.id == id && it.ownerId == ownerId && it.sync.deletedAtEpochMillis == null }

    override fun observeInstallmentGroupAndOwner(groupId: String, ownerId: String): Flow<List<TransactionEntity>> =
        transactions.map { list ->
            list.filter {
                it.installmentGroupId == groupId &&
                it.ownerId == ownerId &&
                it.sync.deletedAtEpochMillis == null
            }.sortedBy { it.installmentNumber ?: 0 }
        }

    override suspend fun getByIdsAndOwner(ids: List<String>, ownerId: String): List<TransactionEntity> =
        transactions.value.filter {
            it.id in ids &&
            it.ownerId == ownerId &&
            it.sync.deletedAtEpochMillis == null
        }

    override fun observeWithTags(id: String): Flow<TransactionWithTags?> = flowOf(null)
    override suspend fun upsert(entity: TransactionEntity) {
        transactions.value = transactions.value.filterNot { it.id == entity.id } + entity
    }
    override suspend fun upsertTags(entities: List<TagEntity>) {}
    override suspend fun upsertTagLinks(entities: List<TransactionTagCrossRef>) {}
    override suspend fun deleteTagLinks(transactionId: String) {}
}

private class FakeOfflineWriteQueueHolder {
    var lastEnqueuedTransaction: TransactionEntity? = null
    var lastTags: List<TagEntity> = emptyList()
    var lastTagLinks: List<TransactionTagCrossRef> = emptyList()
    var lastInsertedOperation: SyncOperationEntity? = null
    var lastBatchCreateInputs: List<com.feniqo.mobile.data.local.dao.TransactionCreateInputV2> = emptyList()
    var lastBatchDeleteInputs: List<com.feniqo.mobile.data.local.dao.TransactionDeleteInputV2> = emptyList()
    var lastBatchUnits: List<com.feniqo.mobile.data.local.dao.TransactionMutationUnit> = emptyList()
    var lastBatchDeleteUnits: List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit> = emptyList()

    val mutationDao = object : LocalMutationDao {
        override suspend fun upsertProfileRow(entity: UserProfileEntity) {}
        override suspend fun upsertWorkspaceRow(entity: WorkspaceEntity) {}
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) {}
        override suspend fun upsertCategoryRow(entity: CategoryEntity) {}
        override suspend fun upsertBudgetRow(entity: BudgetEntity) {}
        override suspend fun upsertTransactionRow(entity: TransactionEntity) {
            lastEnqueuedTransaction = entity
        }
        override suspend fun insertTransactionRow(entity: TransactionEntity) {}
        override suspend fun upsertTagRows(entities: List<TagEntity>) {
            lastTags = entities
        }
        override suspend fun upsertTransactionTagRows(entities: List<TransactionTagCrossRef>) {
            lastTagLinks = entities
        }
        override suspend fun upsertRecurringTransactionRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionEntity) {}
        override suspend fun upsertRecurringOccurrenceRow(entity: com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity) {}
        override suspend fun getOccurrence(recurringTransactionId: String, dueDate: String): com.feniqo.mobile.data.local.entity.RecurringTransactionOccurrenceEntity? = null
        override suspend fun getRecurringTransactionById(id: String): com.feniqo.mobile.data.local.entity.RecurringTransactionEntity? = null
        override suspend fun advanceRecurringLastGeneratedDate(recurringTransactionId: String, expectedPreviousLastGeneratedDate: String?, newDueDate: String, nowEpochMillis: Long): Int = 0
        override suspend fun deleteTransactionTagRows(transactionId: String): Int = 0
        override suspend fun deleteProfileRow(id: String): Int = 0
        override suspend fun deleteCategoryRow(id: String): Int = 0
        override suspend fun deleteBudgetRow(id: String): Int = 0
        override suspend fun deleteTransactionRow(id: String): Int = 0
        override suspend fun deleteOutboxRow(operationId: String): Int = 0
        override suspend fun getOutboxById(operationId: String): SyncOperationEntity? = null
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 0
        override suspend fun convertPendingDeleteToUpdate(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 0
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun insertOutboxRow(operation: SyncOperationEntity) {
            lastInsertedOperation = operation
        }
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun rebaseProfileVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseBudgetVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseRecurringTransactionVersion(id: String, appliedVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun deleteRecurringTransactionRow(id: String): Int = 0
        override suspend fun markCategorySyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markBudgetSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0
        override suspend fun markRecurringTransactionSyncedIfDeleted(id: String, nowEpochMillis: Long): Int = 0

        override suspend fun upsertConflictRow(entity: com.feniqo.mobile.data.local.entity.SyncConflictEntity) {}
        override suspend fun setOutboxStatusConflict(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun setProfileSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setCategorySyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setTransactionSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun setBudgetSyncStatus(id: String, status: String, nowEpochMillis: Long): Int = 1
        override suspend fun upsertTransactionKeepingTagsAndEnqueue(
            entity: TransactionEntity,
            operation: SyncOperationEntity,
        ) {
            lastEnqueuedTransaction = entity
            lastInsertedOperation = operation
        }
        override suspend fun upsertTransactionsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionMutationUnit>) {
            lastBatchUnits = units
        }
        override suspend fun upsertTransactionsKeepingTagsAndEnqueue(units: List<com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit>) {
            lastBatchDeleteUnits = units
        }
        override suspend fun mutateTransactionCreatesV2(
            inputs: List<com.feniqo.mobile.data.local.dao.TransactionCreateInputV2>,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): List<String> {
            lastBatchCreateInputs = inputs
            val legacyUnits = inputs.map { input ->
                val opId = operationIdFactory()
                val op = SyncOperationEntity(
                    operationId = opId,
                    entityTypeCode = "TRANSACTION",
                    entityId = input.entity.id,
                    operationTypeCode = "CREATE",
                    baseVersion = null,
                    payloadJson = input.payloadJson,
                    protocolVersion = 2,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = nowEpochMillis,
                    createdAtEpochMillis = nowEpochMillis,
                    updatedAtEpochMillis = nowEpochMillis,
                )
                lastEnqueuedTransaction = input.entity
                lastInsertedOperation = op
                com.feniqo.mobile.data.local.dao.TransactionMutationUnit(
                    entity = input.entity,
                    tags = input.tags,
                    tagLinks = input.tagLinks,
                    operation = op,
                )
            }
            lastBatchUnits = legacyUnits
            return legacyUnits.map { it.operation.operationId }
        }
        override suspend fun mutateTransactionDeletionsV2(
            inputs: List<com.feniqo.mobile.data.local.dao.TransactionDeleteInputV2>,
            operationIdFactory: () -> String,
            nowEpochMillis: Long,
        ): List<String> {
            lastBatchDeleteInputs = inputs
            val legacyUnits = inputs.map { input ->
                val opId = operationIdFactory()
                val op = SyncOperationEntity(
                    operationId = opId,
                    entityTypeCode = "TRANSACTION",
                    entityId = input.entity.id,
                    operationTypeCode = "DELETE",
                    baseVersion = input.entity.sync.baseVersion,
                    payloadJson = input.payloadJson,
                    protocolVersion = 2,
                    statusCode = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    nextAttemptAtEpochMillis = nowEpochMillis,
                    createdAtEpochMillis = nowEpochMillis,
                    updatedAtEpochMillis = nowEpochMillis,
                )
                lastEnqueuedTransaction = input.entity
                lastInsertedOperation = op
                com.feniqo.mobile.data.local.dao.TransactionKeepingTagsMutationUnit(
                    entity = input.entity,
                    operation = op,
                )
            }
            lastBatchDeleteUnits = legacyUnits
            return legacyUnits.map { it.operation.operationId }
        }
    }

    val operationDao = object : SyncOperationDao {
        override fun observePendingCount(): Flow<Int> = flowOf(0)
        override fun observeFailedCount(): Flow<Int> = flowOf(0)
        override suspend fun getReadyOperations(nowEpochMillis: Long, limit: Int) = emptyList<SyncOperationEntity>()
        override suspend fun getById(operationId: String): SyncOperationEntity? = null
        override suspend fun insert(operation: SyncOperationEntity) {
            lastInsertedOperation = operation
        }
        override suspend fun claimOperation(operationId: String, nowEpochMillis: Long): Int = 1
        override suspend fun markFailed(operationId: String, lastError: String, nextAttemptAtEpochMillis: Long, nowEpochMillis: Long): Int = 1
        override suspend fun markConflict(operationId: String, lastError: String, nowEpochMillis: Long): Int = 1
        override suspend fun recoverStaleInFlight(staleBeforeEpochMillis: Long, nowEpochMillis: Long, lastError: String): Int = 0
        override suspend fun retryAllFailed(nowEpochMillis: Long): Int = 0
        override suspend fun deleteCompleted(operationId: String): Int = 1
        override suspend fun deleteForEntity(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun getSuccessors(predecessorOperationId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun unblockSuccessor(operationId: String, predecessorOperationId: String, appliedVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun getActiveTailCandidates(entityTypeCode: String, entityId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun coalescePendingPayload(operationId: String, payloadJson: String, nowEpochMillis: Long): Int = 1
        override suspend fun convertToPendingDelete(operationId: String, payloadJson: String?, nowEpochMillis: Long): Int = 1
    }

    var opCounter = 0
    val queue = OfflineWriteQueue(
        mutationDao = mutationDao,
        operationDao = operationDao,
        nowEpochMillisProvider = { 1_000L },
        operationIdFactory = { (++opCounter).toString(16).padStart(32, '0') },
    )
}
