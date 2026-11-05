package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationRepositoryTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var queue: OfflineWriteQueue

    private val userAId = "11111111-1111-4111-8111-111111111111"
    private val userBId = "22222222-2222-4222-8222-222222222222"
    private val sessionA = AuthSession(EntityId(userAId), "a@feniqo.com", Instant.fromEpochMilliseconds(1000L))
    private val sessionB = AuthSession(EntityId(userBId), "b@feniqo.com", Instant.fromEpochMilliseconds(1000L))

    private var opIndex = 0

    @BeforeTest
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder<FeniqoDatabase>(
                    context = ApplicationProvider.getApplicationContext(),
                    factory = { FeniqoDatabaseConstructor.initialize() },
                ).allowMainThreadQueries()
                .build()

        queue =
            OfflineWriteQueue(
                mutationDao = db.localMutationDao(),
                operationDao = db.syncOperationDao(),
                nowEpochMillisProvider = { 1000L },
                operationIdFactory = { (++opIndex).toString(16).padStart(32, '0') },
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun mutationWithoutSessionFails() =
        runTest {
            val authRepo = FakeAuthRepo(null)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val assignResult = repo.assignMerchant("Netflix", "netflix")
            assertIs<RepositoryResult.Failure>(assignResult)
            val assignError = assignResult.error
            assertIs<AppError.Authentication>(assignError)
            assertEquals("auth_session_required", assignError.code)

            val blockResult = repo.blockMerchant("Spotify")
            assertIs<RepositoryResult.Failure>(blockResult)

            val deleteResult = repo.deleteVerification("Netflix")
            assertIs<RepositoryResult.Failure>(deleteResult)
        }

    @Test
    fun userIsolationAndSignOutEmptiesFlow() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            // Seed data for user A and user B into database directly
            val idA = UuidHelper.deterministicMerchantVerificationUuid(userAId, "NETFLIX")
            val idB = UuidHelper.deterministicMerchantVerificationUuid(userBId, "SPOTIFY")
            db.localMutationDao().upsertUserMerchantVerificationRow(sampleEntity(idA, userAId, "NETFLIX", "ASSIGN", "netflix"))
            db.localMutationDao().upsertUserMerchantVerificationRow(sampleEntity(idB, userBId, "SPOTIFY", "BLOCK", null))

            val listA = repo.observeActiveVerifications().first()
            assertEquals(1, listA.size)
            assertEquals("NETFLIX", listA.first().normalizedAlias)
            assertEquals(userAId, listA.first().ownerId.value)

            // Switch to User B
            authRepo.sessionFlow.value = sessionB
            val listB = repo.observeActiveVerifications().first()
            assertEquals(1, listB.size)
            assertEquals("SPOTIFY", listB.first().normalizedAlias)
            assertEquals(userBId, listB.first().ownerId.value)

            // Sign out
            authRepo.sessionFlow.value = null
            val listEmpty = repo.observeActiveVerifications().first()
            assertTrue(listEmpty.isEmpty())
        }

    @Test
    fun assignAndBlockUsesDeterministicId() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val assignResult = repo.assignMerchant("netflix", "netflix")
            assertIs<RepositoryResult.Success<*>>(assignResult)
            val expectedAssignId = UuidHelper.deterministicMerchantVerificationUuid(userAId, "NETFLIX")
            val storedAssign = db.userMerchantVerificationDao().getByScopeAndAlias("user:$userAId", "NETFLIX")
            assertEquals(expectedAssignId, storedAssign?.id)
            assertEquals("ASSIGN", storedAssign?.decisionCode)
            assertEquals("netflix", storedAssign?.merchantId)
            assertEquals("user:$userAId", storedAssign?.scopeKey)

            val blockResult = repo.blockMerchant("trendyol")
            assertIs<RepositoryResult.Success<*>>(blockResult)
            val expectedBlockId = UuidHelper.deterministicMerchantVerificationUuid(userAId, "TRENDYOL")
            val storedBlock = db.userMerchantVerificationDao().getByScopeAndAlias("user:$userAId", "TRENDYOL")
            assertEquals(expectedBlockId, storedBlock?.id)
            assertEquals("BLOCK", storedBlock?.decisionCode)
            assertNull(storedBlock?.merchantId)
        }

    @Test
    fun genericAliasIsRejected() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val genericResult = repo.assignMerchant("MARKET", "migros")
            assertIs<RepositoryResult.Failure>(genericResult)
            val genericError = genericResult.error
            assertIs<AppError.Validation>(genericError)
            assertEquals("generic_merchant_alias_not_allowed", genericError.code)

            val genericBlock = repo.blockMerchant("STORE")
            assertIs<RepositoryResult.Failure>(genericBlock)
            val blockError = genericBlock.error
            assertIs<AppError.Validation>(blockError)
            assertEquals("generic_merchant_alias_not_allowed", blockError.code)
        }

    @Test
    fun unknownOrBlankMerchantIdIsRejectedForAssign() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val blankResult = repo.assignMerchant("netflix", "")
            assertIs<RepositoryResult.Failure>(blankResult)
            assertEquals("merchant_id_required", (blankResult as RepositoryResult.Failure).error.code)

            val unknownResult = repo.assignMerchant("netflix", "unknown_merchant_id_999")
            assertIs<RepositoryResult.Failure>(unknownResult)
            assertEquals("unknown_merchant_id", (unknownResult as RepositoryResult.Failure).error.code)
        }

    @Test
    fun malformedStoredRowYieldsInvalidStoredVerificationWithoutFallback() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val id = UuidHelper.deterministicMerchantVerificationUuid(userAId, "NETFLIX")
            // Row exists in Room, but decisionCode is ASSIGN and merchantId is null (corrupted)
            db.localMutationDao().upsertUserMerchantVerificationRow(sampleEntity(id, userAId, "NETFLIX", "ASSIGN", null))

            val result = repo.getDecisionForAlias("netflix")
            assertIs<UserMerchantVerificationLookupResult.InvalidStoredVerification>(result)
            assertEquals(id, result.id)
        }

    @Test
    fun nonDeterministicStoredIdRejectsAssignBlockAndDelete() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            // Seed with non-deterministic arbitrary UUID
            val corruptedId = "88888888-8888-4888-8888-888888888888"
            db.localMutationDao().upsertUserMerchantVerificationRow(sampleEntity(corruptedId, userAId, "NETFLIX", "ASSIGN", "netflix"))

            val assignRes = repo.assignMerchant("NETFLIX", "netflix")
            assertIs<RepositoryResult.Failure>(assignRes)
            val assignErr = assertIs<AppError.Storage>(assignRes.error)
            assertEquals("invalid_stored_merchant_verification", assignErr.code)

            val blockRes = repo.blockMerchant("NETFLIX")
            assertIs<RepositoryResult.Failure>(blockRes)
            val blockErr = assertIs<AppError.Storage>(blockRes.error)
            assertEquals("invalid_stored_merchant_verification", blockErr.code)

            val deleteRes = repo.deleteVerification("NETFLIX")
            assertIs<RepositoryResult.Failure>(deleteRes)
            val deleteErr = assertIs<AppError.Storage>(deleteRes.error)
            assertEquals("invalid_stored_merchant_verification", deleteErr.code)

            // Ensure outbox was NOT enqueued
            assertTrue(db.syncOperationDao().getReadyOperations(SyncScopeKey.forUser(userAId), 2000L, 10).isEmpty())
        }

    @Test
    fun corruptedOwnerOrScopeRowRejectsMutation() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val id = UuidHelper.deterministicMerchantVerificationUuid(userAId, "PRIME")
            // Stored row has mismatched owner or workspace
            val corruptedEntity = sampleEntity(id, userBId, "PRIME", "ASSIGN", "amazon").copy(scopeKey = "user:$userAId")
            db.localMutationDao().upsertUserMerchantVerificationRow(corruptedEntity)

            val result = repo.assignMerchant("PRIME", "amazon")
            assertIs<RepositoryResult.Failure>(result)
            val err = assertIs<AppError.Storage>(result.error)
            assertEquals("invalid_stored_merchant_verification", err.code)
        }

    @Test
    fun queueFailureReturnsStorageFailureWithoutLeakingRawException() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            // Crashing queue
            val crashingQueue =
                OfflineWriteQueue(
                    mutationDao = db.localMutationDao(),
                    operationDao = db.syncOperationDao(),
                    nowEpochMillisProvider = { 1000L },
                    operationIdFactory = { error("Database lock simulated") },
                )
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), crashingQueue)

            val result = repo.assignMerchant("NETFLIX", "netflix")
            assertIs<RepositoryResult.Failure>(result)
            val err = assertIs<AppError.Storage>(result.error)
            assertEquals("merchant_verification_storage_failed", err.code)

            // Entity must not be persisted
            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias("user:$userAId", "NETFLIX"))
        }

    @Test
    fun getDecisionForAliasReturnsDecisionOrNotFound() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            // NotFound when empty
            val notFound = repo.getDecisionForAlias("netflix")
            assertIs<UserMerchantVerificationLookupResult.NotFound>(notFound)

            // When valid decision stored
            val id = UuidHelper.deterministicMerchantVerificationUuid(userAId, "NETFLIX")
            db.localMutationDao().upsertUserMerchantVerificationRow(sampleEntity(id, userAId, "NETFLIX", "ASSIGN", "netflix"))

            val decisionResult = repo.getDecisionForAlias("Netflix")
            assertIs<UserMerchantVerificationLookupResult.Decision>(decisionResult)
            assertIs<UserMerchantDecision.Assign>(decisionResult.decision)
            assertEquals("netflix", (decisionResult.decision as UserMerchantDecision.Assign).merchantId)
        }

    @Test
    fun deleteAndReactivationFlow() =
        runTest {
            val authRepo = FakeAuthRepo(sessionA)
            val repo = OfflineFirstUserMerchantVerificationRepository(authRepo, db.userMerchantVerificationDao(), queue)

            val id = UuidHelper.deterministicMerchantVerificationUuid(userAId, "NETFLIX")
            val existing = sampleEntity(id, userAId, "NETFLIX", "ASSIGN", "netflix")
            db.localMutationDao().upsertUserMerchantVerificationRow(existing)

            // Soft delete
            val deleteRes = repo.deleteVerification("netflix")
            assertIs<RepositoryResult.Success<*>>(deleteRes)
            val deletedStored = db.userMerchantVerificationDao().getByScopeAndAlias("user:$userAId", "NETFLIX")
            assertTrue(deletedStored?.sync?.deletedAtEpochMillis != null)
            assertEquals("PENDING_DELETE", deletedStored?.sync?.syncStatus)

            // Reactivate by ASSIGN
            val reactivateRes = repo.assignMerchant("netflix", "netflix")
            assertIs<RepositoryResult.Success<*>>(reactivateRes)
            val reactivatedStored = db.userMerchantVerificationDao().getByScopeAndAlias("user:$userAId", "NETFLIX")
            assertNull(reactivatedStored?.sync?.deletedAtEpochMillis)
            assertEquals("PENDING_UPDATE", reactivatedStored?.sync?.syncStatus)
        }

    private class FakeAuthRepo(
        session: AuthSession?,
    ) : AuthRepository {
        val sessionFlow = MutableStateFlow(session)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow

        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)

        override suspend fun signIn(
            email: String,
            password: String,
        ) = RepositoryResult.Success(Unit)

        override suspend fun signUp(
            email: String,
            password: String,
            fullName: String?,
        ) = RepositoryResult.Success(EntityId("u-1"))

        override suspend fun refreshSession() = RepositoryResult.Success(Unit)

        override suspend fun signOut() = RepositoryResult.Success(Unit)
    }

    private companion object {
        fun sampleEntity(
            id: String,
            userId: String,
            normalizedAlias: String,
            decisionCode: String,
            merchantId: String?,
        ): UserMerchantVerificationEntity =
            UserMerchantVerificationEntity(
                id = id,
                ownerId = userId,
                workspaceId = null,
                normalizedAlias = normalizedAlias,
                decisionCode = decisionCode,
                merchantId = merchantId,
                scopeCode = "PERSONAL",
                scopeKey = "user:$userId",
                createdAtEpochMillis = 1000L,
                sync =
                    SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
            )
    }
}
