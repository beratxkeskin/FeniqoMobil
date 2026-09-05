package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceLocalMutationDaoTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private var operationIdCounter = 1
    private fun testOperationId(): String = (operationIdCounter++).toString().padStart(32, '0')

    private fun sampleWorkspace(
        id: String = "ws-1",
        ownerId: String = "user-1",
        name: String = "Ortak Aile",
        typeCode: String = "shared",
        currencyCode: String = "TRY",
        description: String? = "Aile bütçesi",
        syncStatus: String = "PENDING_CREATE",
        version: Long = 0L,
        baseVersion: Long? = null,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        ownerId = ownerId,
        typeCode = typeCode,
        currencyCode = currencyCode,
        description = description,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    private fun sampleOwnerMember(
        workspaceId: String = "ws-1",
        userId: String = "user-1",
        syncStatus: String = "PENDING_CREATE",
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = "OWNER",
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 0L,
            baseVersion = null,
            lastSyncError = null,
        ),
    )

    @Test
    fun mutateWorkspaceV2_create_atomicallyWritesWorkspaceOwnerMemberAndOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val workspace = sampleWorkspace(id = "ws-1", ownerId = "user-1")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val payload = """{"id":"ws-1","name":"Ortak Aile","type":"shared","currency":"TRY"}"""

            val result = mutationDao.mutateWorkspaceV2(
                entity = workspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = payload,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            assertEquals(V2EnqueueDecision.INSERTED, result.decision)

            // 1. Workspace kontrolü
            val members = workspaceDao.observeMembers("ws-1").first()
            assertEquals(1, members.size)
            assertEquals("user-1", members[0].userId)
            assertEquals("OWNER", members[0].roleCode)

            val workspaces = workspaceDao.observeForUser("user-1").first()
            assertEquals(1, workspaces.size)
            assertEquals("ws-1", workspaces[0].id)
            assertEquals("Ortak Aile", workspaces[0].name)
            assertEquals("shared", workspaces[0].typeCode)

            // 2. Outbox kontrolü
            val op = operationDao.getById(result.operationId)
            assertNotNull(op)
            assertEquals("WORKSPACE", op.entityTypeCode)
            assertEquals("ws-1", op.entityId)
            assertEquals("CREATE", op.operationTypeCode)
            assertEquals(2, op.protocolVersion)
            assertEquals(payload, op.payloadJson)
            assertNull(op.baseVersion)
            assertNull(op.predecessorOperationId)
            assertFalse(op.isBlocked)
            assertEquals("PENDING", op.statusCode)
            assertEquals(0, op.attemptCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_invalidMetadataOrBlankPayload_rejectedFailClosed() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val workspace = sampleWorkspace(id = "ws-1", ownerId = "user-1")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")

            // Boş payloadJson reddedilmeli
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(ownerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = "   ",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // CREATE işleminde baseVersion dolu olamaz
            val invalidCreateWithBaseVersion = workspace.copy(
                sync = workspace.sync.copy(baseVersion = 1L),
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidCreateWithBaseVersion,
                    members = listOf(ownerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // CREATE işleminde deletedAtEpochMillis dolu olamaz
            val invalidCreateWithDeleted = workspace.copy(
                sync = workspace.sync.copy(deletedAtEpochMillis = 1500L),
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidCreateWithDeleted,
                    members = listOf(ownerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // CREATE işleminde SYNCED status olamaz
            val invalidCreateSynced = workspace.copy(
                sync = workspace.sync.copy(syncStatus = SyncStatus.SYNCED.name),
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidCreateSynced,
                    members = listOf(ownerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // Hiçbir satır yazılmadığını doğrula
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            val members = workspaceDao.observeMembers("ws-1").first()
            assertTrue(members.isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_updateMetadataRules_enforced() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()

            // UPDATE işleminde PENDING_UPDATE olmalı, deletedAtEpochMillis null olmalı
            val invalidUpdateWithDeleted = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                deletedAtEpochMillis = 1500L,
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidUpdateWithDeleted,
                    members = emptyList(),
                    type = OutboxOperationType.UPDATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            val invalidUpdateWithSynced = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.SYNCED.name,
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidUpdateWithSynced,
                    members = emptyList(),
                    type = OutboxOperationType.UPDATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_deleteMetadataRules_enforced() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()

            // DELETE işleminde deletedAtEpochMillis zorunludur ve PENDING_DELETE olmalıdır
            val invalidDeleteWithoutDeletedTime = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.PENDING_DELETE.name,
                deletedAtEpochMillis = null,
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidDeleteWithoutDeletedTime,
                    members = emptyList(),
                    type = OutboxOperationType.DELETE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            val invalidDeleteWithPendingUpdate = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                deletedAtEpochMillis = 2000L,
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = invalidDeleteWithPendingUpdate,
                    members = emptyList(),
                    type = OutboxOperationType.DELETE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_pendingV2Mutation_coalescingAndHardDeleteBehavior() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // 1. CREATE mutasyonu
            val initialWorkspace = sampleWorkspace(id = "ws-1", name = "İlk İsim")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"İlk İsim"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, createResult.decision)

            // 2. Henüz gönderilmemiş PENDING iken UPDATE mutasyonu -> COALESCED olmalı
            val updatedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "İkinci İsim",
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                baseVersion = 1L,
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = updatedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"İkinci İsim"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            assertEquals(V2EnqueueDecision.COALESCED, updateResult.decision)
            assertEquals(createResult.operationId, updateResult.operationId)

            val coalescedOp = operationDao.getById(createResult.operationId)
            assertNotNull(coalescedOp)
            assertEquals("""{"name":"İkinci İsim"}""", coalescedOp.payloadJson)

            // 3. Henüz gönderilmemiş PENDING CREATE iken DELETE mutasyonu -> HARD_DELETED olmalı
            val deletedWorkspace = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.PENDING_DELETE.name,
                deletedAtEpochMillis = 3000L,
                baseVersion = 1L,
            )
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = deletedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-1"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 3000L,
            )
            assertEquals(V2EnqueueDecision.HARD_DELETED, deleteResult.decision)

            // Hem workspace satırı hem de outbox kaydı temizlenmiş olmalı (doğrudan getWorkspaceById ile de kanıtlanır)
            assertNull(operationDao.getById(createResult.operationId))
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            val members = workspaceDao.observeMembers("ws-1").first()
            assertTrue(members.isEmpty())
            val workspaces = workspaceDao.observeForUser("user-1").first()
            assertTrue(workspaces.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_hardDeleteWithCascadeInvitations_cleansAllRelatedEntities() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // 1. CREATE workspace + owner member
            val workspace = sampleWorkspace(id = "ws-cascade")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-cascade", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = workspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-cascade"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, createResult.decision)

            // 2. Bir davet kaydı ekle (FK cascade kontrolü için)
            val invitation = com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity(
                id = "inv-1",
                workspaceId = "ws-cascade",
                inviterId = "user-1",
                roleCode = "EDITOR",
                createdAtEpochMillis = 1000L,
                expiresAtEpochMillis = 2000L,
                maxUses = 5,
                usesCount = 0,
                sync = SyncMetadata(
                    syncStatus = "PENDING_CREATE",
                    updatedAtEpochMillis = 1000L,
                    localUpdatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    version = 0L,
                    baseVersion = null,
                    lastSyncError = null,
                ),
            )
            workspaceDao.upsertInvitation(invitation)
            assertEquals(1, workspaceDao.observeInvitations("ws-cascade").first().size)

            // 3. Henüz gönderilmemiş PENDING CREATE iken DELETE mutasyonu -> HARD_DELETED
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = workspace.copy(
                    sync = workspace.sync.copy(
                        syncStatus = SyncStatus.PENDING_DELETE.name,
                        deletedAtEpochMillis = 3000L,
                        baseVersion = 1L,
                    ),
                ),
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-cascade"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 3000L,
            )
            assertEquals(V2EnqueueDecision.HARD_DELETED, deleteResult.decision)

            // Workspace, member, davetler ve outbox tamamen silinmiş olmalı (orphan kalmadığı getWorkspaceById ile de doğrulanır)
            assertNull(operationDao.getById(createResult.operationId))
            assertNull(workspaceDao.getWorkspaceById("ws-cascade"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-cascade").first().isEmpty())
            assertTrue(workspaceDao.observeInvitations("ws-cascade").first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_createMembershipInvariants_rejectedFailClosedWithoutSideEffects() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val workspace = sampleWorkspace(id = "ws-1", ownerId = "user-1")
            val validOwnerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")

            // Negatif Senaryo 1: CREATE + empty members
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 1000L,
                )
            }
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-1").first().isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())

            // Negatif Senaryo 2: CREATE + farklı workspaceId taşıyan member
            val foreignMember = validOwnerMember.copy(workspaceId = "other-ws")
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(foreignMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 1000L,
                )
            }
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-1").first().isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())

            // Negatif Senaryo 3: CREATE + owner üyesi yok veya owner rolü OWNER değil
            val nonOwnerMember = validOwnerMember.copy(userId = "other-user", roleCode = "OWNER")
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(nonOwnerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 1000L,
                )
            }
            assertNull(workspaceDao.getWorkspaceById("ws-1"))

            val editorOwnerMember = validOwnerMember.copy(roleCode = "EDITOR")
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(editorOwnerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 1000L,
                )
            }
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-1").first().isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())

            // Negatif Senaryo 4: CREATE + duplicate member (aynı workspaceId, userId)
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(validOwnerMember, validOwnerMember.copy(roleCode = "EDITOR")),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"id":"ws-1"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 1000L,
                )
            }
            assertNull(workspaceDao.getWorkspaceById("ws-1"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-1").first().isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_transactionRollback_revertsWorkspaceMembersAndOutboxAtomically() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val workspace = sampleWorkspace(id = "ws-rollback", ownerId = "user-1")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-rollback", userId = "user-1")

            // validateOperationId kontrolünde patlayacak geçersiz (32-hex olmayan) operationId üreten fabrika
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceV2(
                    entity = workspace,
                    members = listOf(ownerMember),
                    type = OutboxOperationType.CREATE,
                    payloadJson = """{"name":"Rollback"}""",
                    operationIdFactory = { "invalid-non-hex-id" },
                    nowEpochMillis = 1000L,
                )
            }

            // Transaction rollback neticesinde Room içinde hiçbir satır (workspaces dahil) yazılmamış olmalı
            assertNull(workspaceDao.getWorkspaceById("ws-rollback"))
            assertTrue(workspaceDao.observeForUser("user-1").first().isEmpty())
            assertTrue(workspaceDao.observeMembers("ws-rollback").first().isEmpty())
            assertEquals(0, operationDao.observePendingCount().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceV2_inFlightPredecessor_createsBlockedSuccessor() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            // 1. CREATE ekle
            val initialWorkspace = sampleWorkspace(id = "ws-1")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"name":"Ws"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )

            // 2. İlk işlemi IN_FLIGHT ve attemptCount = 1 yap
            val claimed = operationDao.claimOperation(
                operationId = createResult.operationId,
                nowEpochMillis = 1500L,
            )
            assertEquals(1, claimed)

            // 3. Şimdi bir UPDATE mutasyonu yap -> predecessor bağlı ve isBlocked = true olmalı
            val updatedWorkspace = sampleWorkspace(
                id = "ws-1",
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                baseVersion = 1L,
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = updatedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"name":"Ws Updated"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, updateResult.decision)

            val successorOp = operationDao.getById(updateResult.operationId)
            assertNotNull(successorOp)
            assertEquals(createResult.operationId, successorOp.predecessorOperationId)
            assertTrue(successorOp.isBlocked)
            assertEquals("PENDING", successorOp.statusCode)
            assertNull(successorOp.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_create_appliesRemoteCanonicalRecordAndRemovesOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val initialWorkspace = sampleWorkspace(id = "ws-1", name = "Yerel Ad", syncStatus = "PENDING_CREATE")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-1","name":"Yerel Ad","type_code":"shared","currency_code":"TRY"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )

            // Claim -> IN_FLIGHT
            operationDao.claimOperation(createResult.operationId, 1500L)

            val remoteDto = WorkspaceDto(
                id = "ws-1",
                name = "Sunucu Canonical Ad",
                normalizedName = "sunucu canonical ad",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = "Uzak açıklama",
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = mutationDao.ackWorkspaceWriteV2(
                operationId = createResult.operationId,
                record = remoteDto,
                nowEpochMillis = 2000L,
            )
            assertTrue(ackResult)

            // Outbox silinmeli
            assertNull(operationDao.getById(createResult.operationId))

            // Workspace güncellenmeli
            val stored = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(stored)
            assertEquals("Sunucu Canonical Ad", stored.name)
            assertEquals("sunucu canonical ad", stored.normalizedName)
            assertEquals("Uzak açıklama", stored.description)
            assertEquals(1L, stored.sync.version)
            assertNull(stored.sync.baseVersion)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(2000L, stored.sync.localUpdatedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_update_updatesVersionAndMetadata() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // Var olan synced workspace
            val syncedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Eski Ad",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
            )
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1", syncStatus = "SYNCED")
            workspaceDao.upsertWorkspace(syncedWorkspace)
            workspaceDao.upsertMember(ownerMember)

            // UPDATE mutasyonu
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = syncedWorkspace.copy(
                    name = "Yeni Yerel Ad",
                    sync = syncedWorkspace.sync.copy(syncStatus = "PENDING_UPDATE", baseVersion = 1L),
                ),
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"ws-1","name":"Yeni Yerel Ad","type_code":"shared","currency_code":"TRY"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            operationDao.claimOperation(updateResult.operationId, 2500L)

            val remoteDto = WorkspaceDto(
                id = "ws-1",
                name = "Yeni Yerel Ad",
                normalizedName = "yeni yerel ad",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:05Z",
                deletedAt = null,
                version = 2L,
            )

            val ackResult = mutationDao.ackWorkspaceWriteV2(
                operationId = updateResult.operationId,
                record = remoteDto,
                nowEpochMillis = 3000L,
            )
            assertTrue(ackResult)

            assertNull(operationDao.getById(updateResult.operationId))
            val stored = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(stored)
            assertEquals("Yeni Yerel Ad", stored.name)
            assertEquals(2L, stored.sync.version)
            assertEquals("SYNCED", stored.sync.syncStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_delete_preservesTombstoneAndMetadata() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val syncedWorkspace = sampleWorkspace(
                id = "ws-1",
                syncStatus = "SYNCED",
                version = 2L,
                baseVersion = 2L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = syncedWorkspace.copy(
                    sync = syncedWorkspace.sync.copy(
                        syncStatus = "PENDING_DELETE",
                        deletedAtEpochMillis = 3000L,
                        baseVersion = 2L,
                    ),
                ),
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-1"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 3000L,
            )

            operationDao.claimOperation(deleteResult.operationId, 3500L)

            val remoteDto = WorkspaceDto(
                id = "ws-1",
                name = "Ortak Aile",
                normalizedName = "ortak aile",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = "Aile bütçesi",
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:10Z",
                deletedAt = "2026-03-01T10:00:10Z",
                version = 3L,
            )

            val ackResult = mutationDao.ackWorkspaceWriteV2(
                operationId = deleteResult.operationId,
                record = remoteDto,
                nowEpochMillis = 4000L,
            )
            assertTrue(ackResult)

            assertNull(operationDao.getById(deleteResult.operationId))
            val stored = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(stored)
            assertEquals(3L, stored.sync.version)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertNotNull(stored.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_withBlockedSuccessor_preservesPendingMutationAndUnblocksSuccessor() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // 1. Initial CREATE outbox
            val initialWorkspace = sampleWorkspace(id = "ws-1", name = "Ilk Ad")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-1","name":"Ilk Ad","type_code":"shared","currency_code":"TRY"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )

            // Claim CREATE -> IN_FLIGHT
            operationDao.claimOperation(createResult.operationId, 1500L)

            // 2. Add UPDATE successor while CREATE is IN_FLIGHT
            val successorWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Ikinci Ad (Yerel)",
                syncStatus = "PENDING_UPDATE",
                version = 0L,
                baseVersion = null,
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = successorWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"ws-1","name":"Ikinci Ad (Yerel)","type_code":"shared","currency_code":"TRY"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, updateResult.decision)

            val successorBeforeAck = operationDao.getById(updateResult.operationId)
            assertNotNull(successorBeforeAck)
            assertTrue(successorBeforeAck.isBlocked)
            assertEquals(createResult.operationId, successorBeforeAck.predecessorOperationId)
            assertNull(successorBeforeAck.baseVersion)

            // 3. ACK CREATE with remote version = 1
            val remoteCreateDto = WorkspaceDto(
                id = "ws-1",
                name = "Ilk Ad",
                normalizedName = "ilk ad",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            val ackResult = mutationDao.ackWorkspaceWriteV2(
                operationId = createResult.operationId,
                record = remoteCreateDto,
                nowEpochMillis = 2500L,
            )
            assertTrue(ackResult)

            // Predecessor silinmeli
            assertNull(operationDao.getById(createResult.operationId))

            // Successor unblock ve rebase edilmiş olmalı
            val successorAfterAck = operationDao.getById(updateResult.operationId)
            assertNotNull(successorAfterAck)
            assertFalse(successorAfterAck.isBlocked)
            assertEquals(createResult.operationId, successorAfterAck.predecessorOperationId)
            assertEquals(1L, successorAfterAck.baseVersion)
            assertEquals("PENDING", successorAfterAck.statusCode)

            // Yerel workspace entity'sinin "Ikinci Ad (Yerel)" değeri ezilmemeli, sürümü rebase edilmeli
            val stored = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(stored)
            assertEquals("Ikinci Ad (Yerel)", stored.name)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_entityTypeOrIdMismatch_rejectedFailClosedAndRollback() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val initialWorkspace = sampleWorkspace(id = "ws-1", name = "Yerel")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-1", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-1"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )
            operationDao.claimOperation(createResult.operationId, 1500L)

            // ID mismatch DTO
            val mismatchedDto = WorkspaceDto(
                id = "ws-other",
                name = "Diger",
                normalizedName = "diger",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )

            assertFailsWith<IllegalStateException> {
                mutationDao.ackWorkspaceWriteV2(
                    operationId = createResult.operationId,
                    record = mismatchedDto,
                    nowEpochMillis = 2000L,
                )
            }

            // Outbox ve Workspace bozulmadan kalmalı
            val op = operationDao.getById(createResult.operationId)
            assertNotNull(op)
            assertEquals("IN_FLIGHT", op.statusCode)

            val ws = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals("Yerel", ws.name)
            assertEquals(0L, ws.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_deletePredecessorWithNonTombstoneDto_rejectedFailClosedAndRollback() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val syncedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Yerel",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val tombstoneWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Yerel",
                syncStatus = SyncStatus.PENDING_DELETE.name,
                version = 1L,
                baseVersion = 1L,
                deletedAtEpochMillis = 2000L,
            )
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = tombstoneWorkspace,
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-1"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            operationDao.claimOperation(deleteResult.operationId, 2500L)

            // DELETE işlemine karşılık gelen DTO'nun deletedAt alanı null (tombstone değil)
            val nonTombstoneDto = WorkspaceDto(
                id = "ws-1",
                name = "Yerel",
                normalizedName = "yerel",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 2L,
            )

            assertFailsWith<IllegalStateException> {
                mutationDao.ackWorkspaceWriteV2(
                    operationId = deleteResult.operationId,
                    record = nonTombstoneDto,
                    nowEpochMillis = 3000L,
                )
            }

            // Outbox ve Workspace durumu bozulmamış olmalı
            val op = operationDao.getById(deleteResult.operationId)
            assertNotNull(op)
            assertEquals("IN_FLIGHT", op.statusCode)

            val ws = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals(SyncStatus.PENDING_DELETE.name, ws.sync.syncStatus)
            assertEquals(1L, ws.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceWriteV2_updatePredecessorWithTombstoneDto_rejectedFailClosedAndRollback() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val syncedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Yerel",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val updatedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Guncel Yerel",
                syncStatus = SyncStatus.PENDING_UPDATE.name,
                version = 1L,
                baseVersion = 1L,
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = updatedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"ws-1","name":"Guncel Yerel"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            operationDao.claimOperation(updateResult.operationId, 2500L)

            // UPDATE işlemine karşılık gelen DTO'nun deletedAt alanı dolu (tombstone)
            val tombstoneDto = WorkspaceDto(
                id = "ws-1",
                name = "Guncel Yerel",
                normalizedName = "guncel yerel",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = "2026-03-01T10:00:01Z",
                version = 2L,
            )

            assertFailsWith<IllegalStateException> {
                mutationDao.ackWorkspaceWriteV2(
                    operationId = updateResult.operationId,
                    record = tombstoneDto,
                    nowEpochMillis = 3000L,
                )
            }

            // Outbox ve Workspace durumu bozulmamış olmalı
            val op = operationDao.getById(updateResult.operationId)
            assertNotNull(op)
            assertEquals("IN_FLIGHT", op.statusCode)

            val ws = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals("Guncel Yerel", ws.name)
            assertEquals(SyncStatus.PENDING_UPDATE.name, ws.sync.syncStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackMissingDeleteV2_workspaceWithoutLocalTombstone_rejectedFailClosedAndOutboxPreserved() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // Yerelde aktif (deletedAtEpochMillis == null) bir workspace var
            val activeWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Aktif",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
                deletedAtEpochMillis = null,
            )
            workspaceDao.upsertWorkspace(activeWorkspace)

            // Sahte DELETE outbox ekleyip IN_FLIGHT yapalım
            val deleteOp = SyncOperationEntity(
                operationId = testOperationId(),
                entityTypeCode = "WORKSPACE",
                entityId = "ws-1",
                operationTypeCode = "DELETE",
                baseVersion = 1L,
                payloadJson = """{"id":"ws-1"}""",
                predecessorOperationId = null,
                isBlocked = false,
                protocolVersion = 2,
                statusCode = "IN_FLIGHT",
                attemptCount = 1,
                lastError = null,
                nextAttemptAtEpochMillis = 2000L,
                createdAtEpochMillis = 2000L,
                updatedAtEpochMillis = 2000L,
            )
            operationDao.insert(deleteOp)

            // Yerel kayıt tombstone olmadığından markWorkspaceSyncedIfDeleted 0 döner ve hata fırlatılır
            assertFailsWith<IllegalStateException> {
                mutationDao.ackMissingDeleteV2(
                    operationId = deleteOp.operationId,
                    entityTypeCode = "WORKSPACE",
                    entityId = "ws-1",
                    nowEpochMillis = 3000L,
                )
            }

            // Outbox ve Workspace korunur
            assertNotNull(operationDao.getById(deleteOp.operationId))
            val ws = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertNull(ws.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackMissingDeleteV2_workspaceWithLocalTombstone_marksSyncedAndRemovesOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val syncedWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Silinecek",
                syncStatus = "SYNCED",
                version = 1L,
                baseVersion = 1L,
            )
            workspaceDao.upsertWorkspace(syncedWorkspace)

            val tombstoneWorkspace = sampleWorkspace(
                id = "ws-1",
                name = "Silinecek",
                syncStatus = SyncStatus.PENDING_DELETE.name,
                version = 1L,
                baseVersion = 1L,
                deletedAtEpochMillis = 2000L,
            )
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = tombstoneWorkspace,
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-1"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            operationDao.claimOperation(deleteResult.operationId, 2500L)

            val ackResult = mutationDao.ackMissingDeleteV2(
                operationId = deleteResult.operationId,
                entityTypeCode = "WORKSPACE",
                entityId = "ws-1",
                nowEpochMillis = 3000L,
            )
            assertTrue(ackResult)

            // Outbox silinmeli
            assertNull(operationDao.getById(deleteResult.operationId))

            // Yerel workspace SYNCED olmalı ve tombstone korunmalı
            val ws = workspaceDao.getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals("SYNCED", ws.sync.syncStatus)
            assertEquals(2000L, ws.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackV2Execution_workspaceApplied_createUpdateDelete_delegatesProperly() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            // 1. CREATE via ackV2Execution(WorkspaceApplied)
            val initialWorkspace = sampleWorkspace(id = "ws-v2-generic", name = "Generic Baslangic")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-v2-generic", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-v2-generic","name":"Generic Baslangic"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )
            operationDao.claimOperation(createResult.operationId, 1500L)

            val remoteCreateDto = WorkspaceDto(
                id = "ws-v2-generic",
                name = "Generic Baslangic",
                normalizedName = "generic baslangic",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )
            val createAck = mutationDao.ackV2Execution(
                operationId = createResult.operationId,
                result = OutboxExecutionResult.WorkspaceApplied(remoteCreateDto),
                nowEpochMillis = 2000L,
            )
            assertTrue(createAck)
            assertNull(operationDao.getById(createResult.operationId))
            val storedAfterCreate = workspaceDao.getWorkspaceById("ws-v2-generic")
            assertNotNull(storedAfterCreate)
            assertEquals(1L, storedAfterCreate.sync.version)
            assertEquals("SYNCED", storedAfterCreate.sync.syncStatus)

            // 2. UPDATE via ackV2Execution(WorkspaceApplied)
            val updatedWorkspace = storedAfterCreate.copy(
                name = "Generic Guncel",
                sync = storedAfterCreate.sync.copy(
                    syncStatus = "PENDING_UPDATE",
                    baseVersion = 1L,
                ),
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = updatedWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"ws-v2-generic","name":"Generic Guncel"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2500L,
            )
            operationDao.claimOperation(updateResult.operationId, 3000L)

            val remoteUpdateDto = remoteCreateDto.copy(
                name = "Generic Guncel",
                normalizedName = "generic guncel",
                updatedAt = "2026-03-01T10:00:10Z",
                version = 2L,
            )
            val updateAck = mutationDao.ackV2Execution(
                operationId = updateResult.operationId,
                result = OutboxExecutionResult.WorkspaceApplied(remoteUpdateDto),
                nowEpochMillis = 3500L,
            )
            assertTrue(updateAck)
            assertNull(operationDao.getById(updateResult.operationId))
            val storedAfterUpdate = workspaceDao.getWorkspaceById("ws-v2-generic")
            assertNotNull(storedAfterUpdate)
            assertEquals(2L, storedAfterUpdate.sync.version)
            assertEquals("Generic Guncel", storedAfterUpdate.name)

            // 3. DELETE via ackV2Execution(WorkspaceApplied)
            val deleteResult = mutationDao.mutateWorkspaceV2(
                entity = storedAfterUpdate.copy(
                    sync = storedAfterUpdate.sync.copy(
                        syncStatus = "PENDING_DELETE",
                        deletedAtEpochMillis = 4000L,
                        baseVersion = 2L,
                    ),
                ),
                members = emptyList(),
                type = OutboxOperationType.DELETE,
                payloadJson = """{"id":"ws-v2-generic"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 4000L,
            )
            operationDao.claimOperation(deleteResult.operationId, 4500L)

            val remoteDeleteDto = remoteUpdateDto.copy(
                deletedAt = "2026-03-01T10:00:20Z",
                updatedAt = "2026-03-01T10:00:20Z",
                version = 3L,
            )
            val deleteAck = mutationDao.ackV2Execution(
                operationId = deleteResult.operationId,
                result = OutboxExecutionResult.WorkspaceApplied(remoteDeleteDto),
                nowEpochMillis = 5000L,
            )
            assertTrue(deleteAck)
            assertNull(operationDao.getById(deleteResult.operationId))
            val storedAfterDelete = workspaceDao.getWorkspaceById("ws-v2-generic")
            assertNotNull(storedAfterDelete)
            assertEquals(3L, storedAfterDelete.sync.version)
            assertEquals("SYNCED", storedAfterDelete.sync.syncStatus)
            assertNotNull(storedAfterDelete.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackV2Execution_workspaceApplied_withBlockedSuccessor_preservesPendingMutationAndUnblocksSuccessor() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val initialWorkspace = sampleWorkspace(id = "ws-v2-succ", name = "V1 Ad")
            val ownerMember = sampleOwnerMember(workspaceId = "ws-v2-succ", userId = "user-1")
            val createResult = mutationDao.mutateWorkspaceV2(
                entity = initialWorkspace,
                members = listOf(ownerMember),
                type = OutboxOperationType.CREATE,
                payloadJson = """{"id":"ws-v2-succ","name":"V1 Ad"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 1000L,
            )
            operationDao.claimOperation(createResult.operationId, 1500L)

            // Blocked successor
            val successorWorkspace = sampleWorkspace(
                id = "ws-v2-succ",
                name = "V2 Yerel Duzenleme",
                syncStatus = "PENDING_UPDATE",
                version = 0L,
                baseVersion = null,
            )
            val updateResult = mutationDao.mutateWorkspaceV2(
                entity = successorWorkspace,
                members = emptyList(),
                type = OutboxOperationType.UPDATE,
                payloadJson = """{"id":"ws-v2-succ","name":"V2 Yerel Duzenleme"}""",
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            val successorBeforeAck = operationDao.getById(updateResult.operationId)
            assertNotNull(successorBeforeAck)
            assertTrue(successorBeforeAck.isBlocked)

            // ACK CREATE via ackV2Execution(WorkspaceApplied)
            val remoteCreateDto = WorkspaceDto(
                id = "ws-v2-succ",
                name = "V1 Ad",
                normalizedName = "v1 ad",
                ownerId = "user-1",
                typeCode = "shared",
                currencyCode = "TRY",
                description = null,
                createdAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:01Z",
                deletedAt = null,
                version = 1L,
            )
            val ackResult = mutationDao.ackV2Execution(
                operationId = createResult.operationId,
                result = OutboxExecutionResult.WorkspaceApplied(remoteCreateDto),
                nowEpochMillis = 2500L,
            )
            assertTrue(ackResult)

            assertNull(operationDao.getById(createResult.operationId))

            val successorAfterAck = operationDao.getById(updateResult.operationId)
            assertNotNull(successorAfterAck)
            assertFalse(successorAfterAck.isBlocked)
            assertEquals(createResult.operationId, successorAfterAck.predecessorOperationId)
            assertEquals(1L, successorAfterAck.baseVersion)
            assertEquals("PENDING", successorAfterAck.statusCode)

            val stored = workspaceDao.getWorkspaceById("ws-v2-succ")
            assertNotNull(stored)
            assertEquals("V2 Yerel Duzenleme", stored.name)
            assertEquals(1L, stored.sync.version)
            assertEquals(1L, stored.sync.baseVersion)
        } finally {
            database.close()
        }
    }
}
