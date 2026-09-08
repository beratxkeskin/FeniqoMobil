package com.feniqo.mobile.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.codec.WorkspaceMembershipPayloadCodec
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.sync.OutboxExecutionResult
import com.feniqo.mobile.data.util.WorkspaceMemberEntityId
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceMemberLocalMutationDaoTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private var operationIdCounter = 1
    private fun testOperationId(): String = (operationIdCounter++).toString().padStart(32, '0')

    private fun sampleWorkspace(
        id: String = "11111111-1111-1111-1111-111111111111",
        ownerId: String = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = "Ortak Test Alanı",
        normalizedName = "ortak test alani",
        ownerId = ownerId,
        typeCode = "shared",
        currencyCode = "TRY",
        description = "Açıklama",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = null,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String = "11111111-1111-1111-1111-111111111111",
        userId: String = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        roleCode: String = "EDITOR",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
        baseVersion: Long? = null,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = roleCode,
        joinedAtEpochMillis = 1000L,
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

    private fun sampleProfile(
        id: String,
        activeWorkspaceId: String? = null,
    ): UserProfileEntity = UserProfileEntity(
        id = id,
        email = "user-$id@test.com",
        fullName = "User $id",
        currencyCode = Currency.TRY.code,
        themeCode = ThemePreference.SYSTEM.name,
        languageCode = AppLanguage.TR.name,
        activeWorkspaceId = activeWorkspaceId,
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = null,
            lastSyncError = null,
        ),
    )

    @Test
    fun mutateWorkspaceMemberRoleV2_atomicallyWritesUpdatedMemberAndOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "EDITOR", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val updatedMember = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    localUpdatedAtEpochMillis = 2000L,
                    baseVersion = 2L,
                ),
            )
            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(
                workspaceId = ws.id,
                userId = member.userId,
                roleCode = "VIEWER",
            )

            val result = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = updatedMember,
                payloadJson = payloadJson,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            assertEquals(V2EnqueueDecision.INSERTED, result.decision)

            // Room'da güncellenmiş üye kontrolü
            val stored = workspaceDao.getMember(ws.id, member.userId)
            assertNotNull(stored)
            assertEquals("VIEWER", stored.roleCode)
            assertEquals(SyncStatus.PENDING_UPDATE.name, stored.sync.syncStatus)

            // Outbox kontrolü
            val op = operationDao.getById(result.operationId)
            assertNotNull(op)
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(WorkspaceMemberEntityId.encode(ws.id, member.userId), op.entityId)
            assertEquals(OutboxOperationType.UPDATE.name, op.operationTypeCode)
            assertEquals(2L, op.baseVersion)
            assertEquals(2, op.protocolVersion)
            assertFalse(op.isBlocked)
            assertNull(op.predecessorOperationId)
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceMemberRoleV2_invalidInvariant_failsClosedWithoutSideEffects() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "EDITOR", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            // 1. Boş payload
            val updatedMember = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    baseVersion = 2L,
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceMemberRoleV2(
                    entity = updatedMember,
                    payloadJson = "   ",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // 2. Base version pozitif değil
            val zeroVersionMember = updatedMember.copy(
                sync = updatedMember.sync.copy(baseVersion = 0L, version = 0L),
            )
            assertFailsWith<IllegalArgumentException> {
                mutationDao.mutateWorkspaceMemberRoleV2(
                    entity = zeroVersionMember,
                    payloadJson = """{"workspace_id":"${ws.id}","user_id":"${member.userId}","role_code":"VIEWER"}""",
                    operationIdFactory = ::testOperationId,
                    nowEpochMillis = 2000L,
                )
            }

            // Yan etki yok
            val stored = workspaceDao.getMember(ws.id, member.userId)
            assertNotNull(stored)
            assertEquals("EDITOR", stored.roleCode)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertTrue(operationDao.getReadyOperations(Long.MAX_VALUE, 10).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceMemberRoleV2_withActiveTail_coalescesOrCreatesBlockedSuccessor() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "EDITOR", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val payload1 = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "VIEWER")
            val op1Member = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val result1 = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = op1Member,
                payloadJson = payload1,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, result1.decision)

            // PENDING durumundaki kuyruk için ikinci UPDATE -> COALESCED
            val payload2 = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "EDITOR")
            val op2Member = member.copy(
                roleCode = "EDITOR",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val result2 = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = op2Member,
                payloadJson = payload2,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 3000L,
            )
            assertEquals(V2EnqueueDecision.COALESCED, result2.decision)
            assertEquals(result1.operationId, result2.operationId)

            // Şimdi kuyruktaki ilk işlemi IN_FLIGHT yapalım
            operationDao.claimOperation(result1.operationId, 4000L)

            // IN_FLIGHT kuyruk varken yeni UPDATE -> INSERTED successor (isBlocked = true)
            val payload3 = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "VIEWER")
            val op3Member = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val result3 = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = op3Member,
                payloadJson = payload3,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 5000L,
            )
            assertEquals(V2EnqueueDecision.INSERTED, result3.decision)

            val successor = operationDao.getById(result3.operationId)
            assertNotNull(successor)
            assertTrue(successor.isBlocked)
            assertEquals(result1.operationId, successor.predecessorOperationId)
            assertNull(successor.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceMemberLeaveV2_atomicallyTombstonesMember_clearsActiveWorkspaceIfMatches_andInsertsOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val profileDao = database.profileDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val memberUserId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val otherUserId = "cccccccc-cccc-cccc-cccc-cccccccccccc"
            val member = sampleMember(userId = memberUserId, roleCode = "EDITOR", version = 2L)

            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            // Profil 1: aktif workspace ws.id olan üye (ayrılan kullanıcı)
            val profileLeaving = sampleProfile(id = memberUserId, activeWorkspaceId = ws.id)
            profileDao.upsert(profileLeaving)

            // Profil 2: başka bir kullanıcı, o da bu workspace'te aktif
            val profileOther = sampleProfile(id = otherUserId, activeWorkspaceId = ws.id)
            profileDao.upsert(profileOther)

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberLeave(ws.id, memberUserId)
            val tombstonedMember = member.copy(
                sync = member.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                    localUpdatedAtEpochMillis = 2000L,
                    baseVersion = 2L,
                ),
            )

            val result = mutationDao.mutateWorkspaceMemberLeaveV2(
                entity = tombstonedMember,
                activeProfileId = memberUserId,
                payloadJson = payloadJson,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            assertEquals(V2EnqueueDecision.INSERTED, result.decision)

            // 1. Ayrılan kullanıcının profilinde active_workspace_id temizlenmeli
            val updatedLeavingProfile = profileDao.observeById(memberUserId).first()
            assertNotNull(updatedLeavingProfile)
            assertNull(updatedLeavingProfile.activeWorkspaceId)

            // 2. Diğer kullanıcının profiline ASLA dokunulmamalı
            val updatedOtherProfile = profileDao.observeById(otherUserId).first()
            assertNotNull(updatedOtherProfile)
            assertEquals(ws.id, updatedOtherProfile.activeWorkspaceId)

            // 3. Member Room'da tombstone olmalı (silinmemeli, soft-delete)
            val storedMember = workspaceDao.getMember(ws.id, memberUserId)
            assertNotNull(storedMember)
            assertEquals(2000L, storedMember.sync.deletedAtEpochMillis)
            assertEquals(SyncStatus.PENDING_DELETE.name, storedMember.sync.syncStatus)

            // 4. Local Workspace satırına dokunulmamalı (canlı kalmalı)
            val storedWorkspace = workspaceDao.getWorkspaceById(ws.id)
            assertNotNull(storedWorkspace)
            assertNull(storedWorkspace.sync.deletedAtEpochMillis)

            // 5. Outbox kaydı kontrolü
            val op = operationDao.getById(result.operationId)
            assertNotNull(op)
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(WorkspaceMemberEntityId.encode(ws.id, memberUserId), op.entityId)
            assertEquals(OutboxOperationType.DELETE.name, op.operationTypeCode)
            assertEquals(2L, op.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceMemberWriteV2_applied_updatesRoomMetadataAndDeletesOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "EDITOR", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "VIEWER")
            val updatedMember = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val enqueueResult = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = updatedMember,
                payloadJson = payloadJson,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            // Mark IN_FLIGHT
            operationDao.claimOperation(enqueueResult.operationId, 2500L)

            // Server ACK
            val remoteMemberDto = WorkspaceMemberDto(
                workspaceId = ws.id,
                userId = member.userId,
                roleCode = "VIEWER",
                joinedAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:05Z",
                deletedAt = null,
                version = 3L,
            )
            val ackSuccess = mutationDao.ackV2Execution(
                operationId = enqueueResult.operationId,
                result = OutboxExecutionResult.WorkspaceMemberApplied(remoteMemberDto),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            // Outbox silinmeli
            assertNull(operationDao.getById(enqueueResult.operationId))

            // Room entity SYNCED ve version 3 olmalı
            val stored = workspaceDao.getMember(ws.id, member.userId)
            assertNotNull(stored)
            assertEquals("VIEWER", stored.roleCode)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(3L, stored.sync.version)
            assertNull(stored.sync.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceMemberWriteV2_deleteApplied_preservesTombstoneAndMarksSynced() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "VIEWER", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberLeave(ws.id, member.userId)
            val tombstoned = member.copy(
                sync = member.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                    baseVersion = 2L,
                ),
            )
            val enqueueResult = mutationDao.mutateWorkspaceMemberLeaveV2(
                entity = tombstoned,
                activeProfileId = member.userId,
                payloadJson = payloadJson,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            operationDao.claimOperation(enqueueResult.operationId, 2500L)

            val remoteDeleteDto = WorkspaceMemberDto(
                workspaceId = ws.id,
                userId = member.userId,
                roleCode = "VIEWER",
                joinedAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:05Z",
                deletedAt = "2026-03-01T10:00:05Z",
                version = 3L,
            )
            val ackSuccess = mutationDao.ackV2Execution(
                operationId = enqueueResult.operationId,
                result = OutboxExecutionResult.WorkspaceMemberApplied(remoteDeleteDto),
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            assertNull(operationDao.getById(enqueueResult.operationId))

            val stored = workspaceDao.getMember(ws.id, member.userId)
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", stored.sync.syncStatus)
            assertEquals(3L, stored.sync.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackMissingDeleteV2_forWorkspaceMember_preservesTombstoneAndMarksSynced() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "VIEWER", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberLeave(ws.id, member.userId)
            val tombstoned = member.copy(
                sync = member.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                    baseVersion = 2L,
                ),
            )
            val enqueueResult = mutationDao.mutateWorkspaceMemberLeaveV2(
                entity = tombstoned,
                activeProfileId = member.userId,
                payloadJson = payloadJson,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            operationDao.claimOperation(enqueueResult.operationId, 2500L)

            // Server returned NOT_FOUND -> MissingDeleteAcknowledged
            val ackSuccess = mutationDao.ackV2Execution(
                operationId = enqueueResult.operationId,
                result = OutboxExecutionResult.MissingDeleteAcknowledged,
                nowEpochMillis = 3000L,
            )
            assertTrue(ackSuccess)

            assertNull(operationDao.getById(enqueueResult.operationId))

            val stored = workspaceDao.getMember(ws.id, member.userId)
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals("SYNCED", stored.sync.syncStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun ackWorkspaceMemberWriteV2_unblocksSuccessor() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val member = sampleMember(roleCode = "EDITOR", version = 2L)
            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(member)

            val payload1 = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "VIEWER")
            val op1Member = member.copy(
                roleCode = "VIEWER",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val result1 = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = op1Member,
                payloadJson = payload1,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            operationDao.claimOperation(result1.operationId, 2500L)

            val payload2 = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(ws.id, member.userId, "EDITOR")
            val op2Member = member.copy(
                roleCode = "EDITOR",
                sync = member.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name, baseVersion = 2L),
            )
            val result2 = mutationDao.mutateWorkspaceMemberRoleV2(
                entity = op2Member,
                payloadJson = payload2,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 3000L,
            )
            val successor = operationDao.getById(result2.operationId)
            assertNotNull(successor)
            assertTrue(successor.isBlocked)

            // ACK op1
            val remoteMemberDto = WorkspaceMemberDto(
                workspaceId = ws.id,
                userId = member.userId,
                roleCode = "VIEWER",
                joinedAt = "2026-03-01T10:00:00Z",
                updatedAt = "2026-03-01T10:00:05Z",
                deletedAt = null,
                version = 3L,
            )
            mutationDao.ackV2Execution(
                operationId = result1.operationId,
                result = OutboxExecutionResult.WorkspaceMemberApplied(remoteMemberDto),
                nowEpochMillis = 3500L,
            )

            val unblockedSuccessor = operationDao.getById(result2.operationId)
            assertNotNull(unblockedSuccessor)
            assertFalse(unblockedSuccessor.isBlocked)
            assertEquals(3L, unblockedSuccessor.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun mutateWorkspaceMemberRemovalV2_enqueuesDeleteAndDoesNotClearAnyProfile() = runTest {
        val database = inMemoryDatabase()
        try {
            val mutationDao = database.localMutationDao()
            val workspaceDao = database.workspaceDao()
            val profileDao = database.profileDao()
            val operationDao = database.syncOperationDao()

            val ws = sampleWorkspace()
            val ownerId = ws.ownerId
            val targetMember = sampleMember(roleCode = "EDITOR", version = 2L)

            workspaceDao.upsertWorkspace(ws)
            workspaceDao.upsertMember(targetMember)

            // Aktörün profili bu workspace'te aktif
            val ownerProfile = sampleProfile(id = ownerId, activeWorkspaceId = ws.id)
            profileDao.upsert(ownerProfile)

            val payload = WorkspaceMembershipPayloadCodec.encodeMemberLeave(ws.id, targetMember.userId)
            val tombstoned = targetMember.copy(
                sync = targetMember.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = 2000L,
                    baseVersion = 2L,
                ),
            )

            val result = mutationDao.mutateWorkspaceMemberRemovalV2(
                entity = tombstoned,
                payloadJson = payload,
                operationIdFactory = ::testOperationId,
                nowEpochMillis = 2000L,
            )

            assertEquals(V2EnqueueDecision.INSERTED, result.decision)

            // 1. Hedef üye tombstoned
            val stored = workspaceDao.getMember(ws.id, targetMember.userId)
            assertNotNull(stored)
            assertNotNull(stored.sync.deletedAtEpochMillis)
            assertEquals(SyncStatus.PENDING_DELETE.name, stored.sync.syncStatus)

            // 2. Aktörün aktif alanı DEĞİŞMEMELİ
            val storedOwnerProfile = profileDao.observeById(ownerId).first()
            assertNotNull(storedOwnerProfile)
            assertEquals(ws.id, storedOwnerProfile.activeWorkspaceId)

            // 3. Outbox kaydı kontrolü
            val op = operationDao.getById(result.operationId)
            assertNotNull(op)
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(OutboxOperationType.DELETE.name, op.operationTypeCode)
            assertEquals("${ws.id}:${targetMember.userId}", op.entityId)
            assertEquals(2L, op.baseVersion)
        } finally {
            database.close()
        }
    }
}
