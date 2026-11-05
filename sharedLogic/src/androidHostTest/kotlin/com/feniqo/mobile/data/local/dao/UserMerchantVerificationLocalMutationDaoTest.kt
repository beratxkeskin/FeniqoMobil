package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationLocalMutationDaoTest {
    private lateinit var db: FeniqoDatabase
    private lateinit var queue: OfflineWriteQueue

    private val userA = "11111111-1111-4111-8111-111111111111"
    private val syncScopeA = SyncScopeKey.forUser(userA)
    private val entityScopeA = "user:$userA"

    private val userB = "22222222-2222-4222-8222-222222222222"
    private val syncScopeB = SyncScopeKey.forUser(userB)
    private val entityScopeB = "user:$userB"

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
    fun create_insertsEntityAndOutboxAtomically() =
        runTest {
            val alias = "NETFLIX"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity =
                sampleEntity(id, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)

            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            assertNotNull(opId)

            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals(id, stored.id)
            assertEquals("PENDING_CREATE", stored.sync.syncStatus)
            assertEquals(0L, stored.sync.version)
            assertNull(stored.sync.baseVersion)

            val outbox = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10)
            assertEquals(1, outbox.size)
            val op = outbox.first()
            assertEquals("USER_MERCHANT_VERIFICATION", op.entityTypeCode)
            assertEquals(id, op.entityId)
            assertEquals("CREATE", op.operationTypeCode)
            assertNull(op.baseVersion)

            val payload =
                UserMerchantVerificationPayloadCodec.parseAndValidate(
                    jsonString = op.payloadJson!!,
                    operation = "CREATE",
                    baseVersion = op.baseVersion,
                )
            assertEquals(id, payload.id)
            assertEquals(userA, payload.userId)
            assertEquals(alias, payload.normalizedAlias)
            assertEquals("ASSIGN", payload.decisionCode)
            assertEquals("netflix", payload.merchantId)
        }

    @Test
    fun rollbackProof_whenOutboxFails_entityNotPersisted() =
        runTest {
            val alias = "SPOTIFY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)

            // Passing an invalid operation factory or breaking the transaction
            assertFailsWith<Exception> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = payload,
                    operationIdFactory = { error("Simulated forced crash during outbox enqueue") },
                    nowEpochMillis = 1000L,
                )
            }

            // Both entity and outbox must be absent
            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNull(stored)
            val ops = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10)
            assertTrue(ops.isEmpty())
        }

    @Test
    fun pendingCreate_thenUpdate_coalescesPayloadWithoutNewOutboxOperation() =
        runTest {
            val alias = "YOUTUBE"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(id, userA, alias, "ASSIGN", "youtube", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)

            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            val opsBefore = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10)
            assertEquals(1, opsBefore.size)
            val originalOpId = opsBefore.first().operationId

            // Now mutate to BLOCK while still PENDING_CREATE
            val entityUpdate =
                sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate, OutboxOperationType.UPDATE)
            assertEquals(originalOpId, updateOpId)

            val opsAfter = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10)
            assertEquals(1, opsAfter.size)
            assertEquals(originalOpId, opsAfter.first().operationId)
            assertEquals("CREATE", opsAfter.first().operationTypeCode)

            // Payload should reflect BLOCK
            val updatedPayload =
                UserMerchantVerificationPayloadCodec.parseAndValidate(
                    jsonString = opsAfter.first().payloadJson!!,
                    operation = "CREATE",
                    baseVersion = opsAfter.first().baseVersion,
                )
            assertEquals("BLOCK", updatedPayload.decisionCode)
            assertNull(updatedPayload.merchantId)

            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("BLOCK", stored.decisionCode)
        }

    @Test
    fun pendingCreate_thenDelete_hardDeletesBothEntityAndOutbox() =
        runTest {
            val alias = "DISNEY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(id, userA, alias, "ASSIGN", "disney", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)

            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)
            assertEquals(1, db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).size)

            // Now delete before remote sync
            val entityDelete =
                sampleEntity(
                    id,
                    userA,
                    alias,
                    "ASSIGN",
                    "disney",
                    syncStatus = "PENDING_DELETE",
                    version = 0L,
                    baseVersion = null,
                    deletedAt = 1000L,
                )
            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)

            // Both completely gone
            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            val ops = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10)
            assertTrue(ops.isEmpty())
        }

    @Test
    fun inFlightPredecessor_linksSuccessorAsBlocked() =
        runTest {
            val alias = "AMAZON"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(id, userA, alias, "ASSIGN", "amazon", syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityUpdate1 =
                sampleEntity(id, userA, alias, "ASSIGN", "amazon_tr", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
            val firstOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate1, OutboxOperationType.UPDATE)

            db.syncOperationDao().claimOperation(syncScopeA, firstOpId, 1500L) // mark IN_FLIGHT

            // Now update while first op is IN_FLIGHT
            val entityUpdate2 =
                sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
            val succOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate2, OutboxOperationType.UPDATE)

            val successors = db.syncOperationDao().getSuccessors(syncScopeA, firstOpId)
            assertEquals(1, successors.size)
            val succ = successors.first()
            assertEquals(succOpId, succ.operationId)
            assertTrue(succ.isBlocked)
            assertEquals(firstOpId, succ.predecessorOperationId)
        }

    @Test
    fun ackApplied_noSuccessor_writesSyncedEntityAndClearsOutbox() =
        runTest {
            val alias = "STEAM"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(id, userA, alias, "ASSIGN", "steam", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            val op = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).first()
            db.syncOperationDao().claimOperation(syncScopeA, op.operationId, 1500L)

            val remoteDto =
                UserMerchantVerificationDto(
                    id = id,
                    userId = userA,
                    workspaceId = null,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "steam",
                    scopeCode = "PERSONAL",
                    createdAt = "2026-10-15T10:00:00Z",
                    updatedAt = "2026-10-15T10:00:00Z",
                    deletedAt = null,
                    version = 1L,
                )

            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                result = OutboxExecutionResult.UserMerchantVerificationApplied(remoteDto),
                nowEpochMillis = 2000L,
            )

            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)

            // Outbox entry should be deleted
            assertNull(db.syncOperationDao().getById(syncScopeA, op.operationId))
        }

    @Test
    fun ackApplied_withSuccessor_rebasesVersionAndUnblocksSuccessorWithoutOverwritingLocalDecision() =
        runTest {
            val alias = "EPIC"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(id, userA, alias, "ASSIGN", "epic", syncStatus = "SYNCED", version = 1L, baseVersion = 1L)
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityUpdate1 =
                sampleEntity(id, userA, alias, "ASSIGN", "epic_games", syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
            val firstOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate1, OutboxOperationType.UPDATE)

            db.syncOperationDao().claimOperation(syncScopeA, firstOpId, 1500L)

            // While first is IN_FLIGHT, enqueue local BLOCK update
            val entityUpdate2 =
                sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 1L, baseVersion = 1L)
            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate2, OutboxOperationType.UPDATE)

            val remoteDto =
                UserMerchantVerificationDto(
                    id = id,
                    userId = userA,
                    workspaceId = null,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "epic_games",
                    scopeCode = "PERSONAL",
                    createdAt = "2026-10-15T10:00:00Z",
                    updatedAt = "2026-10-15T10:00:00Z",
                    deletedAt = null,
                    version = 2L,
                )

            // ACK the first UPDATE
            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = firstOpId,
                result = OutboxExecutionResult.UserMerchantVerificationApplied(remoteDto),
                nowEpochMillis = 2000L,
            )

            // Local BLOCK decision must NOT be overwritten by remote ASSIGN!
            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("BLOCK", stored.decisionCode)
            assertNull(stored.merchantId)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)
            assertEquals(2L, stored.sync.version)
            assertEquals(2L, stored.sync.baseVersion)

            // Successor must now be unblocked
            val ready = db.syncOperationDao().getReadyOperations(syncScopeA, 3000L, 10)
            assertEquals(1, ready.size)
            val unblockedOp = ready.first()
            assertEquals("UPDATE", unblockedOp.operationTypeCode)
            assertEquals(false, unblockedOp.isBlocked)
            assertEquals(firstOpId, unblockedOp.predecessorOperationId)
            assertEquals(2L, unblockedOp.baseVersion)
        }

    @Test
    fun deleteNotFound_acksMissingDeleteAndFinalizesTombstone() =
        runTest {
            val alias = "GETIR"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced = sampleEntity(id, userA, alias, "ASSIGN", "getir", syncStatus = "SYNCED", version = 2L, baseVersion = 2L)
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id,
                    userA,
                    alias,
                    "ASSIGN",
                    "getir",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)

            val op = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).first()
            db.syncOperationDao().claimOperation(syncScopeA, op.operationId, 1500L)

            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 2000L,
            )

            // Entity tombstone should now be SYNCED
            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertNotNull(stored.sync.deletedAtEpochMillis)

            // Outbox operation removed
            assertNull(db.syncOperationDao().getById(syncScopeA, op.operationId))
        }

    @Test
    fun deleteNotFound_withAssignSuccessor_convertsToCreateAndPreservesLocalDecision() =
        runTest {
            val alias = "UBER"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced = sampleEntity(id, userA, alias, "ASSIGN", "uber", syncStatus = "SYNCED", version = 3L, baseVersion = 3L)
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            // 1. DELETE enqueue edilir
            val entityDelete =
                sampleEntity(
                    id,
                    userA,
                    alias,
                    "ASSIGN",
                    "uber",
                    syncStatus = "PENDING_DELETE",
                    version = 3L,
                    baseVersion = 3L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)

            // DELETE IN_FLIGHT olur
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            // 2. Kullanıcı aynı alias'ı yeniden ASSIGN yapar -> blocked successor UPDATE oluşur
            val entityReAssign =
                sampleEntity(id, userA, alias, "ASSIGN", "uber_eats", syncStatus = "PENDING_UPDATE", version = 3L, baseVersion = 3L)
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityReAssign, OutboxOperationType.UPDATE)

            val successorBefore = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertNotNull(successorBefore)
            assertTrue(successorBefore.isBlocked)
            assertEquals("UPDATE", successorBefore.operationTypeCode)
            val originalSnapshot = successorBefore.payloadJson

            // 3. Sunucu DELETE için NOT_FOUND döner
            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = deleteOpId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 2000L,
            )

            // Predecessor DELETE silinmiş olmalı
            assertNull(db.syncOperationDao().getById(syncScopeA, deleteOpId))

            // Successor CREATE'e dönüşmüş, baseVersion=null, unblocked, PENDING olmalı
            val successorAfter = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertNotNull(successorAfter)
            assertEquals("CREATE", successorAfter.operationTypeCode)
            assertNull(successorAfter.baseVersion)
            assertNull(successorAfter.predecessorOperationId)
            assertEquals(false, successorAfter.isBlocked)
            assertEquals("PENDING", successorAfter.statusCode)
            assertEquals(0, successorAfter.attemptCount)
            assertEquals(originalSnapshot, successorAfter.payloadJson)

            // Entity son ASSIGN kararını korumalı, sync_status PENDING_CREATE, version=0, baseVersion=null, deletedAt=null
            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("ASSIGN", stored.decisionCode)
            assertEquals("uber_eats", stored.merchantId)
            assertEquals("PENDING_CREATE", stored.sync.syncStatus)
            assertEquals(0L, stored.sync.version)
            assertNull(stored.sync.baseVersion)
            assertNull(stored.sync.deletedAtEpochMillis)
        }

    @Test
    fun deleteNotFound_withBlockSuccessor_convertsToCreateAndPreservesLocalDecision() =
        runTest {
            val alias = "BOLT"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced = sampleEntity(id, userA, alias, "ASSIGN", "bolt", syncStatus = "SYNCED", version = 2L, baseVersion = 2L)
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id,
                    userA,
                    alias,
                    "ASSIGN",
                    "bolt",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            // Kullanıcı BLOCK yapıyor
            val entityBlock = sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityBlock, OutboxOperationType.UPDATE)

            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = deleteOpId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 2000L,
            )

            val successorAfter = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertNotNull(successorAfter)
            assertEquals("CREATE", successorAfter.operationTypeCode)
            assertNull(successorAfter.baseVersion)
            assertEquals(false, successorAfter.isBlocked)

            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("BLOCK", stored.decisionCode)
            assertNull(stored.merchantId)
            assertEquals("PENDING_CREATE", stored.sync.syncStatus)
            assertEquals(0L, stored.sync.version)
            assertNull(stored.sync.deletedAtEpochMillis)
        }

    @Test
    fun rawBypass_mutateUserMerchantVerification_rejectsMismatchedOrMalformedPayload() =
        runTest {
            val alias = "SAHTE"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "netflix",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )

            // 1. Mismatched decision code in payload
            val mismatchedPayload =
                """{"id":"$id","user_id":"$userA","workspace_id":null,"normalized_alias":"$alias",""" +
                    """"decision_code":"BLOCK","merchant_id":null,"scope_code":"PERSONAL"}"""

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = mismatchedPayload,
                    operationIdFactory = { "op-1" },
                    nowEpochMillis = 1000L,
                )
            }

            // Entity ve outbox yazılmamalı
            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertTrue(db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).isEmpty())
        }

    @Test
    fun conflict_preservesBothPayloadsAndDoesNotSilentlyOverwrite() =
        runTest {
            val alias = "MIGROS"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "migros", syncStatus = "PENDING_UPDATE", version = 2L, baseVersion = 2L)
            queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.UPDATE)

            val op = db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).first()
            db.syncOperationDao().claimOperation(syncScopeA, op.operationId, 1500L)

            val conflictRecord =
                SyncConflictEntity(
                    operationId = op.operationId,
                    syncScopeKey = syncScopeA,
                    entityTypeCode = "USER_MERCHANT_VERIFICATION",
                    entityId = id,
                    localVersion = 2L,
                    remoteVersion = 4L,
                    localPayloadJson = op.payloadJson!!,
                    remotePayloadJson = """{"id":"$id","user_id":"$userA","decision_code":"BLOCK","version":4}""",
                    detectedAtEpochMillis = 2000L,
                )

            db.localMutationDao().ackV2Execution(
                syncScopeKey = syncScopeA,
                operationId = op.operationId,
                result = OutboxExecutionResult.ConflictDetected(conflictRecord),
                nowEpochMillis = 2000L,
            )

            // Entity status should be CONFLICT
            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("CONFLICT", stored.sync.syncStatus)
            // Local decision ASSIGN is preserved, not overwritten by remote BLOCK
            assertEquals("ASSIGN", stored.decisionCode)

            // Outbox remains in CONFLICT
            val conflictOp = db.syncOperationDao().getById(syncScopeA, op.operationId)
            assertNotNull(conflictOp)
            assertEquals("CONFLICT", conflictOp.statusCode)
        }

    @Test
    fun userScopeIsolation_cannotMutateAcrossScopes() =
        runTest {
            val alias = "KAFE"
            val idA = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityA =
                sampleEntity(idA, userA, alias, "ASSIGN", "kahve_dunyasi", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)

            queue.enqueueUserMerchantVerificationV2(syncScopeA, entityA, OutboxOperationType.CREATE)

            // Scope B cannot see scope A's row or outbox
            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeB, alias))
            assertTrue(db.syncOperationDao().getReadyOperations(syncScopeB, 2000L, 10).isEmpty())
        }

    @Test
    fun deleteNotFound_successorDifferentEntityType_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "TRENDYOL"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "trendyol",
                    syncStatus = "SYNCED",
                    version = 2L,
                    baseVersion = 2L,
                )
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "trendyol",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            val entityReAssign =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "trendyol_yemek",
                    syncStatus = "PENDING_UPDATE",
                    version = 2L,
                    baseVersion = 2L,
                )
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityReAssign, OutboxOperationType.UPDATE)

            // Successor'ın entityTypeCode'unu farklı bir tip yap (ör. CATEGORY)
            val successorBefore = db.syncOperationDao().getById(syncScopeA, updateOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(successorBefore.copy(entityTypeCode = "CATEGORY"))

            val snapshotPredecessor = db.syncOperationDao().getById(syncScopeA, deleteOpId)
            val snapshotSuccessor = db.syncOperationDao().getById(syncScopeA, updateOpId)
            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }

            // Snapshot korunmalı (tam rollback)
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
        }

    @Test
    fun deleteNotFound_successorDifferentEntityId_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "GETIR"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "getir",
                    syncStatus = "SYNCED",
                    version = 2L,
                    baseVersion = 2L,
                )
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "getir",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            val entityReAssign =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "getir_buyuk",
                    syncStatus = "PENDING_UPDATE",
                    version = 2L,
                    baseVersion = 2L,
                )
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityReAssign, OutboxOperationType.UPDATE)

            // Successor'ın entityId'sini farklı bir ID yap
            val differentId = UuidHelper.deterministicMerchantVerificationUuid(userA, "BASKET")
            val successorBefore = db.syncOperationDao().getById(syncScopeA, updateOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(successorBefore.copy(entityId = differentId))

            val snapshotPredecessor = db.syncOperationDao().getById(syncScopeA, deleteOpId)
            val snapshotSuccessor = db.syncOperationDao().getById(syncScopeA, updateOpId)
            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
        }

    @Test
    fun deleteNotFound_successorNotUpdateOperation_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "MARTI"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "marti",
                    syncStatus = "SYNCED",
                    version = 2L,
                    baseVersion = 2L,
                )
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "marti",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            val entityReAssign =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "marti_tag",
                    syncStatus = "PENDING_UPDATE",
                    version = 2L,
                    baseVersion = 2L,
                )
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityReAssign, OutboxOperationType.UPDATE)

            // Successor'ın operationTypeCode'unu DELETE yap
            val successorBefore = db.syncOperationDao().getById(syncScopeA, updateOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(successorBefore.copy(operationTypeCode = "DELETE"))

            val snapshotPredecessor = db.syncOperationDao().getById(syncScopeA, deleteOpId)
            val snapshotSuccessor = db.syncOperationDao().getById(syncScopeA, updateOpId)
            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
        }

    @Test
    fun deleteNotFound_successorInvalidInvariants_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "TIKTAK"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "tiktak",
                    syncStatus = "SYNCED",
                    version = 2L,
                    baseVersion = 2L,
                )
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            val entityDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "tiktak",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = 2L,
                    deletedAt = 1000L,
                )
            val deleteOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityDelete, OutboxOperationType.DELETE)
            db.syncOperationDao().claimOperation(syncScopeA, deleteOpId, 1500L)

            val entityReAssign =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "tiktak_car",
                    syncStatus = "PENDING_UPDATE",
                    version = 2L,
                    baseVersion = 2L,
                )
            val updateOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityReAssign, OutboxOperationType.UPDATE)
            val originalSuccessor = db.syncOperationDao().getById(syncScopeA, updateOpId)!!

            // Invariant 1: protocolVersion == 1
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor.copy(protocolVersion = 1))
            val snapshotPredecessor = db.syncOperationDao().getById(syncScopeA, deleteOpId)
            val snapshotSuccessor1 = db.syncOperationDao().getById(syncScopeA, updateOpId)
            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor1, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Invariant 2: attemptCount > 0
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor.copy(attemptCount = 2))
            val snapshotSuccessor2 = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor2, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Invariant 3: isBlocked == false
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor.copy(isBlocked = false))
            val snapshotSuccessor3 = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor3, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Invariant 4: statusCode == "IN_FLIGHT"
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor.copy(statusCode = "IN_FLIGHT"))
            val snapshotSuccessor4 = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor4, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Invariant 5: payloadJson bozuk
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor.copy(payloadJson = "{invalid json}"))
            val snapshotSuccessor5 = db.syncOperationDao().getById(syncScopeA, updateOpId)
            assertFailsWith<IllegalStateException> {
                db.localMutationDao().ackV2Execution(
                    syncScopeKey = syncScopeA,
                    operationId = deleteOpId,
                    result = OutboxExecutionResult.MissingDeleteAcknowledged,
                    nowEpochMillis = 2000L,
                )
            }
            assertEquals(snapshotPredecessor, db.syncOperationDao().getById(syncScopeA, deleteOpId))
            assertEquals(snapshotSuccessor5, db.syncOperationDao().getById(syncScopeA, updateOpId))
            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Restore original successor in clean state
            db.localMutationDao().deleteOutboxRow(syncScopeA, updateOpId)
            db.syncOperationDao().insert(originalSuccessor)
        }

    @Test
    fun rawBypass_tailYok_updateWithNullBaseVersion_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "SPOTIFY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "spotify",
                    syncStatus = "PENDING_UPDATE",
                    version = 2L,
                    baseVersion = null,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeUpdate(entity)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entity,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-1" },
                    nowEpochMillis = 1000L,
                )
            }

            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertTrue(db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).isEmpty())
        }

    @Test
    fun rawBypass_tailYok_deleteWithNullBaseVersion_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "DEEZER"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "deezer",
                    syncStatus = "PENDING_DELETE",
                    version = 2L,
                    baseVersion = null,
                    deletedAt = 1000L,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeDelete(entity)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entity,
                    type = OutboxOperationType.DELETE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-2" },
                    nowEpochMillis = 1000L,
                )
            }

            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertTrue(db.syncOperationDao().getReadyOperations(syncScopeA, 2000L, 10).isEmpty())
        }

    @Test
    fun rawBypass_rootPendingCreate_updateWithNullBaseVersion_coalescesSuccessfully() =
        runTest {
            val alias = "DISNEY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "disney_plus",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            val outboxBefore = db.syncOperationDao().getById(syncScopeA, createOpId)
            assertNotNull(outboxBefore)
            assertEquals("CREATE", outboxBefore.operationTypeCode)
            assertEquals(0, outboxBefore.attemptCount)
            assertEquals("PENDING", outboxBefore.statusCode)

            // Kullanıcı baseVersion=null olan bir UPDATE mutate çağrısı yapar
            val entityUpdate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "disney_star",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val updatePayload = UserMerchantVerificationPayloadCodec.encodeUpdate(entityUpdate)

            val result =
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityUpdate,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = updatePayload,
                    operationIdFactory = { "op-raw-update" },
                    nowEpochMillis = 1500L,
                )

            assertEquals(createOpId, result.operationId)
            assertEquals(V2EnqueueDecision.COALESCED, result.decision)

            val outboxAfter = db.syncOperationDao().getById(syncScopeA, createOpId)
            assertNotNull(outboxAfter)
            assertEquals("CREATE", outboxAfter.operationTypeCode)
            assertEquals(updatePayload, outboxAfter.payloadJson)

            val stored = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            assertNotNull(stored)
            assertEquals("disney_star", stored.merchantId)
            assertNull(stored.sync.baseVersion)
        }

    @Test
    fun rawBypass_rootPendingCreate_deleteWithNullBaseVersion_hardDeletesSuccessfully() =
        runTest {
            val alias = "BLUTV"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "blutv",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            assertNotNull(db.syncOperationDao().getById(syncScopeA, createOpId))
            assertNotNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))

            // Kullanıcı baseVersion=null olan bir DELETE mutate çağrısı yapar
            val entityDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "blutv",
                    syncStatus = "PENDING_DELETE",
                    version = 0L,
                    baseVersion = null,
                    deletedAt = 1500L,
                )
            val deletePayload = UserMerchantVerificationPayloadCodec.encodeDelete(entityDelete)

            val result =
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityDelete,
                    type = OutboxOperationType.DELETE,
                    payloadJson = deletePayload,
                    operationIdFactory = { "op-raw-delete" },
                    nowEpochMillis = 1500L,
                )

            assertEquals(createOpId, result.operationId)
            assertEquals(V2EnqueueDecision.HARD_DELETED, result.decision)

            // Entity ve outbox tamamen silinmiş olmalı
            assertNull(db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertNull(db.syncOperationDao().getById(syncScopeA, createOpId))
        }

    @Test
    fun rawBypass_tailNotEligibleRootCreate_updateWithNullBaseVersion_rejectedFailClosedAndRolledBack() =
        runTest {
            val alias = "EXXEN"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entitySynced =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "exxen",
                    syncStatus = "SYNCED",
                    version = 3L,
                    baseVersion = 3L,
                )
            db.localMutationDao().upsertUserMerchantVerificationRow(entitySynced)

            // Tail bir UPDATE operasyonu (kök CREATE değil)
            val entityUpdate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "exxen_spor",
                    syncStatus = "PENDING_UPDATE",
                    version = 3L,
                    baseVersion = 3L,
                )
            val tailOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityUpdate, OutboxOperationType.UPDATE)

            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, tailOpId)

            // BaseVersion null olan bir UPDATE gönder
            val entityNullBase =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "exxen_pro",
                    syncStatus = "PENDING_UPDATE",
                    version = 3L,
                    baseVersion = null,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeUpdate(entityNullBase)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityNullBase,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-invalid" },
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, tailOpId))
        }

    @Test
    fun rawBypass_rootCreateBlocked_nullBaseUpdate_rejectedFailClosedAndPreservesSnapshot() =
        runTest {
            val alias = "GAZMAN"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "gazman",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            // Fixture boz: root CREATE satırını isBlocked=true yap
            val opBefore = db.syncOperationDao().getById(syncScopeA, createOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, createOpId)
            db.syncOperationDao().insert(opBefore.copy(isBlocked = true))

            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, createOpId)

            val entityNullBaseUpdate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "gazman_official",
                    syncStatus = "PENDING_UPDATE",
                    version = 0L,
                    baseVersion = null,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entityNullBaseUpdate)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityNullBaseUpdate,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-blocked" },
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, createOpId))
        }

    @Test
    fun rawBypass_rootCreateWithBaseVersion_nullBaseUpdate_rejectedFailClosedAndPreservesSnapshot() =
        runTest {
            val alias = "AVVA"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "avva",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            // Fixture boz: root CREATE satırına baseVersion=1L ver
            val opBefore = db.syncOperationDao().getById(syncScopeA, createOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, createOpId)
            db.syncOperationDao().insert(opBefore.copy(baseVersion = 1L))

            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, createOpId)

            val entityNullBaseUpdate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "avva_official",
                    syncStatus = "PENDING_UPDATE",
                    version = 0L,
                    baseVersion = null,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entityNullBaseUpdate)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityNullBaseUpdate,
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-basever" },
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, createOpId))
        }

    @Test
    fun rawBypass_rootCreateBlocked_nullBaseDelete_rejectedFailClosedAndPreservesSnapshot() =
        runTest {
            val alias = "KIP"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "kip",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            // Fixture boz: root CREATE satırını isBlocked=true yap
            val opBefore = db.syncOperationDao().getById(syncScopeA, createOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, createOpId)
            db.syncOperationDao().insert(opBefore.copy(isBlocked = true))

            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, createOpId)

            val entityNullBaseDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "kip",
                    syncStatus = "PENDING_DELETE",
                    version = 0L,
                    baseVersion = null,
                    deletedAt = 2000L,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entityNullBaseDelete)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityNullBaseDelete,
                    type = OutboxOperationType.DELETE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-delete-blocked" },
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, createOpId))
        }

    @Test
    fun rawBypass_rootCreateWithBaseVersion_nullBaseDelete_rejectedFailClosedAndPreservesSnapshot() =
        runTest {
            val alias = "DAMAT"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "damat",
                    syncStatus = "PENDING_CREATE",
                    version = 0L,
                    baseVersion = null,
                )
            val createOpId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)

            // Fixture boz: root CREATE satırına baseVersion=1L ver
            val opBefore = db.syncOperationDao().getById(syncScopeA, createOpId)!!
            db.localMutationDao().deleteOutboxRow(syncScopeA, createOpId)
            db.syncOperationDao().insert(opBefore.copy(baseVersion = 1L))

            val snapshotEntity = db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, createOpId)

            val entityNullBaseDelete =
                sampleEntity(
                    id = id,
                    userId = userA,
                    normalizedAlias = alias,
                    decisionCode = "ASSIGN",
                    merchantId = "damat",
                    syncStatus = "PENDING_DELETE",
                    version = 0L,
                    baseVersion = null,
                    deletedAt = 2000L,
                )
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entityNullBaseDelete)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().mutateUserMerchantVerificationV2(
                    syncScopeKey = syncScopeA,
                    entity = entityNullBaseDelete,
                    type = OutboxOperationType.DELETE,
                    payloadJson = payload,
                    operationIdFactory = { "op-raw-delete-basever" },
                    nowEpochMillis = 2000L,
                )
            }

            assertEquals(snapshotEntity, db.userMerchantVerificationDao().getByScopeAndAlias(entityScopeA, alias))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, createOpId))
        }

    @Test
    fun recoverEquivalent_noSuccessor_atomicRecoveryAppliesRemoteAndCleansOutboxAndConflict() =
        runTest {
            val alias = "NETFLIX"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "netflix", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val expectedLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            assertEquals("CONFLICT", expectedLocal.sync.syncStatus)

            val ok = db.localMutationDao().recoverEquivalentUserMerchantVerification(
                syncScopeKey = syncScopeA,
                operationId = opId,
                remoteRecord = remoteDto,
                nowEpochMillis = 2000L,
                expectedLocalEntity = expectedLocal,
            )
            assertTrue(ok)

            // Assert outbox and conflict deleted
            assertNull(db.syncOperationDao().getById(syncScopeA, opId))
            assertNull(db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))

            // Assert local entity updated with SYNCED status and version 1L
            val synced = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            assertEquals("SYNCED", synced.sync.syncStatus)
            assertEquals(1L, synced.sync.version)
            assertEquals(1L, synced.sync.baseVersion)
            assertEquals(2000L, synced.sync.localUpdatedAtEpochMillis)
        }

    @Test
    fun recoverEquivalent_withBlockedSuccessor_rebasesAndUnblocksSuccessorAndCleansConflict() =
        runTest {
            val alias = "SPOTIFY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate = sampleEntity(id, userA, alias, "ASSIGN", "spotify", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payloadCreate = UserMerchantVerificationPayloadCodec.encodeCreate(entityCreate)
            val opId1 = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId1)

            // While op1 is in flight, user updates decision to BLOCK
            val entityBlock = sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_UPDATE", version = 0L, baseVersion = null)
            val payloadBlock = UserMerchantVerificationPayloadCodec.encodeCreate(entityBlock)
            db.localMutationDao().upsertUserMerchantVerificationRow(entityBlock)
            val opId2 = "2".padStart(32, '0')
            val successor = SyncOperationEntity(
                syncScopeKey = syncScopeA,
                operationId = opId2,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationTypeCode = "UPDATE",
                baseVersion = null,
                payloadJson = payloadBlock,
                predecessorOperationId = opId1,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1500L,
                createdAtEpochMillis = 1500L,
                updatedAtEpochMillis = 1500L,
            )
            db.syncOperationDao().insert(successor)
            val successorBefore = db.syncOperationDao().getById(syncScopeA, opId2)!!
            assertTrue(successorBefore.isBlocked)
            assertEquals(opId1, successorBefore.predecessorOperationId)
            assertNull(successorBefore.baseVersion)

            // Op1 fails with CONFLICT
            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "spotify",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId1,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1600L,
            )
            db.syncOperationDao().markConflict(syncScopeA, opId1, "Conflict", 1600L)
            db.syncStateDao().upsertConflict(conflict)

            val expectedLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            assertEquals("BLOCK", expectedLocal.decisionCode) // Local decision preserved!

            val ok = db.localMutationDao().recoverEquivalentUserMerchantVerification(
                syncScopeKey = syncScopeA,
                operationId = opId1,
                remoteRecord = remoteDto,
                nowEpochMillis = 2000L,
                expectedLocalEntity = expectedLocal,
            )
            assertTrue(ok)

            // Assert conflict deleted
            assertNull(db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
            // Assert predecessor op1 deleted
            assertNull(db.syncOperationDao().getById(syncScopeA, opId1))

            // Assert successor op2 unblocked
            val successorAfter = db.syncOperationDao().getById(syncScopeA, opId2)!!
            assertEquals("UPDATE", successorAfter.operationTypeCode)
            assertFalse(successorAfter.isBlocked)
            assertEquals(opId1, successorAfter.predecessorOperationId)
            assertEquals(1L, successorAfter.baseVersion)
            assertEquals("PENDING", successorAfter.statusCode)

            // Assert local entity preserved local decision "BLOCK" and rebased to version 1L
            val rebasedLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            assertEquals("BLOCK", rebasedLocal.decisionCode)
            assertNull(rebasedLocal.merchantId)
            assertEquals(1L, rebasedLocal.sync.version)
            assertEquals(1L, rebasedLocal.sync.baseVersion)
            assertEquals(2000L, rebasedLocal.sync.localUpdatedAtEpochMillis)
        }

    @Test
    fun recoverEquivalent_staleExpectedLocalEntity_onlyLocalUpdatedAtDiffers_failsAndRollsBack() =
        runTest {
            val alias = "YOUTUBE"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "youtube", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "youtube",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val staleExpectedLocal = liveLocal.copy(
                sync = liveLocal.sync.copy(localUpdatedAtEpochMillis = 99999L),
            )

            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = staleExpectedLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_multipleSuccessors_failsAndRollsBack() =
        runTest {
            val alias = "DISNEY"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "disney", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "disney",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            // Inject 2 successors pointing to opId
            val succ1 = SyncOperationEntity(
                syncScopeKey = syncScopeA,
                operationId = "op-succ-1",
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationTypeCode = "UPDATE",
                baseVersion = null,
                payloadJson = payload,
                predecessorOperationId = opId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 2000L,
                createdAtEpochMillis = 2000L,
                updatedAtEpochMillis = 2000L,
            )
            val succ2 = succ1.copy(operationId = "op-succ-2")
            db.syncOperationDao().insert(succ1)
            db.syncOperationDao().insert(succ2)

            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)
            val liveLocal = snapshotEntity!!

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_orphanSuccessor_failsAndRollsBack() =
        runTest {
            val alias = "EXXEN"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "exxen", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "exxen",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            // Successor whose baseVersion is not null (violates V2 un-ACKed successor invariant)
            val succ = SyncOperationEntity(
                syncScopeKey = syncScopeA,
                operationId = "op-succ-orphan",
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationTypeCode = "UPDATE",
                baseVersion = 1L,
                payloadJson = payload,
                predecessorOperationId = opId,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 2000L,
                createdAtEpochMillis = 2000L,
                updatedAtEpochMillis = 2000L,
            )
            db.syncOperationDao().insert(succ)

            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)
            val liveLocal = snapshotEntity!!

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_malformedLocalOrRemoteSnapshot_failsAndRollsBack() =
        runTest {
            val alias = "GETIR"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "getir", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "getir",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = "{malformed local json}",
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!

            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalArgumentException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_tombstoneRemoteSnapshot_failsAndRollsBack() =
        runTest {
            val alias = "HEPSIBURADA"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "hepsiburada", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val payload = UserMerchantVerificationPayloadCodec.encodeCreate(entity)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "hepsiburada",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                deletedAt = "1970-01-01T00:00:02Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(deletedAt = null, version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!

            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_predecessorPayloadDecisionOrMerchantMismatch_rejectsAndRollsBack() =
        runTest {
            val alias = "STEAM"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "steam", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "steam",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            // Tamper predecessor payload in outbox so it differs in decision (BLOCK instead of ASSIGN)
            val tamperedPayload = UserMerchantVerificationPayloadCodec.encodeCreate(
                sampleEntity(id, userA, alias, "BLOCK", null, syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null),
            )
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_operations SET payload_json = ? WHERE sync_scope_key = ? AND operation_id = ?",
                arrayOf(tamperedPayload, syncScopeA, opId),
            )

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_conflictLocalVersionMismatch_rejectsAndRollsBack() =
        runTest {
            val alias = "DISCORD"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "discord", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "discord",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            // conflict.localVersion = 1L (mismatches localVersion=0L)
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 1L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_conflictRemoteVersionMismatch_rejectsAndRollsBack() =
        runTest {
            val alias = "TWITCH"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entity = sampleEntity(id, userA, alias, "ASSIGN", "twitch", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val opId = queue.enqueueUserMerchantVerificationV2(syncScopeA, entity, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "twitch",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            // conflict.remoteVersion = 2L (mismatches remoteRecord.version=1L)
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId,
                localVersion = 0L,
                remoteVersion = 2L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1500L,
            )
            queue.recordV2Conflict(syncScopeA, conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox = db.syncOperationDao().getById(syncScopeA, opId)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox, db.syncOperationDao().getById(syncScopeA, opId))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_blockedSuccessorCreate_rejectsAndRollsBack() =
        runTest {
            val alias = "GITHUB"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate = sampleEntity(id, userA, alias, "ASSIGN", "github", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val opId1 = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId1)

            // Successor with invalid operationTypeCode CREATE
            val opId2 = "00000000000000000000000000000002"
            val successor = SyncOperationEntity(
                syncScopeKey = syncScopeA,
                operationId = opId2,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationTypeCode = "CREATE",
                baseVersion = null,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeCreate(entityCreate),
                predecessorOperationId = opId1,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1500L,
                createdAtEpochMillis = 1500L,
                updatedAtEpochMillis = 1500L,
            )
            db.syncOperationDao().insert(successor)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "github",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId1,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1600L,
            )
            db.syncOperationDao().markConflict(syncScopeA, opId1, "Conflict", 1600L)
            db.syncStateDao().upsertConflict(conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox1 = db.syncOperationDao().getById(syncScopeA, opId1)
            val snapshotOutbox2 = db.syncOperationDao().getById(syncScopeA, opId2)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId1,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox1, db.syncOperationDao().getById(syncScopeA, opId1))
            assertEquals(snapshotOutbox2, db.syncOperationDao().getById(syncScopeA, opId2))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    @Test
    fun recoverEquivalent_blockedSuccessorDelete_rejectsAndRollsBack() =
        runTest {
            val alias = "GITLAB"
            val id = UuidHelper.deterministicMerchantVerificationUuid(userA, alias)
            val entityCreate = sampleEntity(id, userA, alias, "ASSIGN", "gitlab", syncStatus = "PENDING_CREATE", version = 0L, baseVersion = null)
            val opId1 = queue.enqueueUserMerchantVerificationV2(syncScopeA, entityCreate, OutboxOperationType.CREATE)
            queue.claimOperation(syncScopeA, opId1)

            // Successor with invalid operationTypeCode DELETE
            val opId2 = "00000000000000000000000000000002"
            val successor = SyncOperationEntity(
                syncScopeKey = syncScopeA,
                operationId = opId2,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationTypeCode = "DELETE",
                baseVersion = null,
                payloadJson = UserMerchantVerificationPayloadCodec.encodeDelete(entityCreate),
                predecessorOperationId = opId1,
                isBlocked = true,
                protocolVersion = 2,
                statusCode = "PENDING",
                attemptCount = 0,
                lastError = null,
                nextAttemptAtEpochMillis = 1500L,
                createdAtEpochMillis = 1500L,
                updatedAtEpochMillis = 1500L,
            )
            db.syncOperationDao().insert(successor)

            val remoteDto = UserMerchantVerificationDto(
                id = id,
                userId = userA,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "gitlab",
                scopeCode = "PERSONAL",
                createdAt = "1970-01-01T00:00:01Z",
                updatedAt = "1970-01-01T00:00:01Z",
                version = 1L,
            )
            val conflict = SyncConflictEntity(
                syncScopeKey = syncScopeA,
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = opId1,
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto.copy(version = 0L)),
                remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(remoteDto),
                detectedAtEpochMillis = 1600L,
            )
            db.syncOperationDao().markConflict(syncScopeA, opId1, "Conflict", 1600L)
            db.syncStateDao().upsertConflict(conflict)

            val liveLocal = db.localMutationDao().getUserMerchantVerificationRow(id)!!
            val snapshotEntity = db.localMutationDao().getUserMerchantVerificationRow(id)
            val snapshotOutbox1 = db.syncOperationDao().getById(syncScopeA, opId1)
            val snapshotOutbox2 = db.syncOperationDao().getById(syncScopeA, opId2)
            val snapshotConflict = db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id)

            assertFailsWith<IllegalStateException> {
                db.localMutationDao().recoverEquivalentUserMerchantVerification(
                    syncScopeKey = syncScopeA,
                    operationId = opId1,
                    remoteRecord = remoteDto,
                    nowEpochMillis = 2000L,
                    expectedLocalEntity = liveLocal,
                )
            }

            assertEquals(snapshotEntity, db.localMutationDao().getUserMerchantVerificationRow(id))
            assertEquals(snapshotOutbox1, db.syncOperationDao().getById(syncScopeA, opId1))
            assertEquals(snapshotOutbox2, db.syncOperationDao().getById(syncScopeA, opId2))
            assertEquals(snapshotConflict, db.syncStateDao().getConflict(syncScopeA, "USER_MERCHANT_VERIFICATION", id))
        }

    private companion object {
        fun sampleEntity(
            id: String,
            userId: String,
            normalizedAlias: String,
            decisionCode: String,
            merchantId: String?,
            syncStatus: String,
            version: Long,
            baseVersion: Long?,
            deletedAt: Long? = null,
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
                        syncStatus = syncStatus,
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = deletedAt,
                        version = version,
                        baseVersion = baseVersion,
                        lastSyncError = null,
                    ),
            )
    }
}
