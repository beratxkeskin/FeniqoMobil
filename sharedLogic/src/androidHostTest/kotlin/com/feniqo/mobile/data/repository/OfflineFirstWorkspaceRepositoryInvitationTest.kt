package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.util.WorkspaceInvitationCrypto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OfflineFirstWorkspaceRepositoryInvitationTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private class FakeAuthRepository(
        session: AuthSession? = AuthSession(
            userId = EntityId("user-owner"),
            email = "owner@example.com",
            expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
        ),
    ) : AuthRepository {
        private val sessionFlow = MutableStateFlow(session)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-owner"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        fun setSession(session: AuthSession?) {
            sessionFlow.value = session
        }
    }

    private var opCounter = 1
    private fun testOpIdFactory(): String = (opCounter++).toString().padStart(32, '0')

    private val fixedNow = 1757160000000L

    private fun createRepository(
        database: FeniqoDatabase,
        authRepo: AuthRepository,
        opIdFactory: () -> String = ::testOpIdFactory,
    ): OfflineFirstWorkspaceRepository {
        var idCounter = 100
        return OfflineFirstWorkspaceRepository(
            authRepository = authRepo,
            workspaceDao = database.workspaceDao(),
            localMutationDao = database.localMutationDao(),
            remoteDataSource = FakeWorkspaceCoreRemoteDataSource(),
            remoteSyncDao = database.remoteSyncDao(),
            entityIdGenerator = EntityIdGenerator { EntityId("00000000-0000-0000-0000-000000000${idCounter++}") },
            operationIdFactory = opIdFactory,
            nowEpochMillisProvider = { fixedNow },
        )
    }

    private suspend fun seedWorkspace(
        database: FeniqoDatabase,
        workspaceId: String = "11111111-1111-1111-1111-111111111111",
        ownerId: String = "user-owner",
        deletedAt: Long? = null,
    ) {
        val ws = WorkspaceEntity(
            id = workspaceId,
            name = "Test Workspace",
            normalizedName = "test workspace",
            ownerId = ownerId,
            typeCode = "shared",
            currencyCode = "TRY",
            description = "Davet testi alanı",
            createdAtEpochMillis = fixedNow,
            sync = SyncMetadata(
                syncStatus = "SYNCED",
                updatedAtEpochMillis = fixedNow,
                localUpdatedAtEpochMillis = fixedNow,
                deletedAtEpochMillis = deletedAt,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
        )
        database.workspaceDao().upsertWorkspace(ws)

        val ownerMember = WorkspaceMemberEntity(
            workspaceId = workspaceId,
            userId = ownerId,
            roleCode = WorkspaceRole.OWNER.name,
            joinedAtEpochMillis = fixedNow,
            sync = newSyncMetadata(fixedNow),
        )
        database.workspaceDao().upsertMember(ownerMember)
    }

    private suspend fun seedMember(
        database: FeniqoDatabase,
        workspaceId: String,
        userId: String,
        role: WorkspaceRole,
    ) {
        val member = WorkspaceMemberEntity(
            workspaceId = workspaceId,
            userId = userId,
            roleCode = role.name,
            joinedAtEpochMillis = fixedNow,
            sync = newSyncMetadata(fixedNow),
        )
        database.workspaceDao().upsertMember(member)
    }

    @Test
    fun createInvite_unauthenticated_failsWithAuthSessionRequired() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(session = null)
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId("11111111-1111-1111-1111-111111111111"))

            assertIs<RepositoryResult.Failure>(result)
            assertIs<AppError.Authentication>(result.error)
            assertEquals("auth_session_required", (result.error as AppError.Authentication).code)

            val invitations = database.workspaceDao().observeInvitations("11111111-1111-1111-1111-111111111111").first()
            assertTrue(invitations.isEmpty())
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertTrue(operations.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_workspaceNotFound_failsClosedWithoutWriting() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId("00000000-dead-beef-0000-000000000000"))

            assertIs<RepositoryResult.Failure>(result)
            assertIs<AppError.Validation>(result.error)
            assertEquals("workspace_not_found", (result.error as AppError.Validation).code)

            val invitations = database.workspaceDao().observeInvitations("00000000-dead-beef-0000-000000000000").first()
            assertTrue(invitations.isEmpty())
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertTrue(operations.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_workspaceTombstoned_failsClosedWithoutWriting() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, deletedAt = fixedNow)

            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId(wsId))

            assertIs<RepositoryResult.Failure>(result)
            assertIs<AppError.Validation>(result.error)
            assertEquals("workspace_not_found", (result.error as AppError.Validation).code)

            val invitations = database.workspaceDao().observeInvitations(wsId).first()
            assertTrue(invitations.isEmpty())
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertTrue(operations.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_actorNotOwner_failsClosed() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, ownerId = "user-owner")
            seedMember(database, workspaceId = wsId, userId = "user-editor", role = WorkspaceRole.EDITOR)
            seedMember(database, workspaceId = wsId, userId = "user-viewer", role = WorkspaceRole.VIEWER)

            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            // Case 1: Editor tries to create invite
            authRepo.setSession(
                AuthSession(
                    userId = EntityId("user-editor"),
                    email = "editor@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
                ),
            )
            val editorResult = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Failure>(editorResult)
            assertIs<AppError.Authentication>(editorResult.error)
            assertEquals("actor_not_permitted", (editorResult.error as AppError.Authentication).code)

            // Case 2: Viewer tries to create invite
            authRepo.setSession(
                AuthSession(
                    userId = EntityId("user-viewer"),
                    email = "viewer@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
                ),
            )
            val viewerResult = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Failure>(viewerResult)
            assertIs<AppError.Authentication>(viewerResult.error)
            assertEquals("actor_not_permitted", (viewerResult.error as AppError.Authentication).code)

            // Case 3: Completely external user tries to create invite
            authRepo.setSession(
                AuthSession(
                    userId = EntityId("user-stranger"),
                    email = "stranger@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
                ),
            )
            val strangerResult = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Failure>(strangerResult)
            assertIs<AppError.Authentication>(strangerResult.error)
            assertEquals("actor_not_member", (strangerResult.error as AppError.Authentication).code)

            // Verify no invitations or outbox written
            val invitations = database.workspaceDao().observeInvitations(wsId).first()
            assertTrue(invitations.isEmpty())
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertTrue(operations.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_success_returnsRawToken_storesOnlyHashInRoomAndOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, ownerId = "user-owner")

            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId(wsId))

            assertIs<RepositoryResult.Success<WorkspaceInviteCode>>(result)
            val inviteCode = result.value
            val rawToken = inviteCode.value

            // 1. Raw token non-blank and 64 hex characters (256-bit CSPRNG hex)
            assertTrue(rawToken.isNotBlank())
            assertEquals(64, rawToken.length)
            assertTrue(Regex("^[0-9a-f]{64}$").matches(rawToken))

            // 2. Expected SHA-256 hash
            val expectedHash = WorkspaceInvitationCrypto.hashInvitationToken(rawToken)
            assertFalse(expectedHash == rawToken, "Hash ham token ile birebir aynı olamaz.")

            // 3. Room doğrulaması: entity yalnız hash saklar, raw token saklanmaz
            val invitations = database.workspaceDao().observeInvitations(wsId).first()
            assertEquals(1, invitations.size)
            val storedInvitation = invitations.first()
            assertEquals(expectedHash, storedInvitation.tokenHash)
            assertFalse(storedInvitation.tokenHash == rawToken)
            assertFalse(storedInvitation.toString().contains(rawToken), "Room entity içinde raw token geçemez.")

            // 4. Outbox doğrulaması: payload yalnız token_hash taşır, raw token kesinlikle içermez
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertEquals(1, operations.size)
            val op = operations.first()
            assertEquals("WORKSPACE_INVITATION", op.entityTypeCode)
            assertEquals(storedInvitation.id, op.entityId)
            assertEquals("CREATE", op.operationTypeCode)
            assertEquals(2, op.protocolVersion)
            assertEquals("PENDING", op.statusCode)
            assertNull(op.baseVersion)
            assertNotNull(op.payloadJson)
            assertTrue(op.payloadJson!!.contains("\"token_hash\":\"$expectedHash\""))
            assertFalse(op.payloadJson!!.contains(rawToken), "Outbox payload içinde raw token geçemez.")
            assertFalse(op.toString().contains(rawToken), "SyncOperationEntity içinde raw token geçemez.")
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_defaultsVerified_roleEditor_maxUses10_expiryNowPlus7Days() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, ownerId = "user-owner")

            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Success<WorkspaceInviteCode>>(result)

            val invitations = database.workspaceDao().observeInvitations(wsId).first()
            assertEquals(1, invitations.size)
            val inv = invitations.first()

            val expectedExpiryMillis = fixedNow + (7L * 24L * 60L * 60L * 1000L)
            val expectedCreatedAtIso = Instant.fromEpochMilliseconds(fixedNow).toString()
            val expectedExpiresAtIso = Instant.fromEpochMilliseconds(expectedExpiryMillis).toString()

            // Default kural doğrulaması
            assertEquals("EDITOR", inv.roleCode)
            assertEquals(10, inv.maxUses)
            assertEquals(0, inv.usesCount)
            assertEquals(fixedNow, inv.createdAtEpochMillis)
            assertEquals(expectedExpiryMillis, inv.expiresAtEpochMillis)

            // Outbox payload allowlist doğrulaması
            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            val op = operations.single()
            val payload = op.payloadJson!!
            assertTrue(payload.contains("\"role_code\":\"EDITOR\""))
            assertTrue(payload.contains("\"max_uses\":10"))
            assertTrue(payload.contains("\"expires_at\":\"$expectedExpiresAtIso\""))
            assertTrue(payload.contains("\"created_at\":\"$expectedCreatedAtIso\""))
        } finally {
            database.close()
        }
    }

    @Test
    fun createInvite_atomicRollback_leavesNoOrphanEntityOrOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, ownerId = "user-owner")

            val authRepo = FakeAuthRepository()
            // Geçersiz operationId üreten factory -> validateOperationId require hatası atar ve transaction rollback olur
            val invalidOpIdFactory: () -> String = { "invalid-non-hex-id" }
            val repo = createRepository(database, authRepo, opIdFactory = invalidOpIdFactory)

            val result = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Failure>(result)

            // Rollback doğrulaması: invitation veya outbox'tan yalnız biri veya hiçbiri kalmamalı
            val invitations = database.workspaceDao().observeInvitations(wsId).first()
            assertTrue(invitations.isEmpty(), "Rollback sonrası davet Room'da kalmamalı.")

            val operations = database.syncOperationDao().getReadyOperations(fixedNow, 10)
            assertTrue(operations.isEmpty(), "Rollback sonrası outbox kaydı kalmamalı.")
        } finally {
            database.close()
        }
    }

    @Test
    fun dao_ackWorkspaceInvitationWriteV2_applied_preservesLocalTokenHash_andDeletesOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            seedWorkspace(database, workspaceId = wsId, ownerId = "user-owner")

            val authRepo = FakeAuthRepository()
            val repo = createRepository(database, authRepo)

            val result = repo.createInvite(EntityId(wsId))
            assertIs<RepositoryResult.Success<WorkspaceInviteCode>>(result)
            val rawToken = result.value.value
            val tokenHash = WorkspaceInvitationCrypto.hashInvitationToken(rawToken)

            val inv = database.workspaceDao().observeInvitations(wsId).first().single()
            val op = database.syncOperationDao().getReadyOperations(fixedNow, 10).single()

            // Sunucuya gönderilip IN_FLIGHT'a alındığını claimOperation ile simüle et
            val claimed = database.syncOperationDao().claimOperation(op.operationId, fixedNow)
            assertEquals(1, claimed)

            // Uzak sunucudan dönen DTO (sunucu asla token_hash dönmez)
            val remoteDto = WorkspaceInvitationDto(
                id = inv.id,
                workspaceId = wsId,
                inviterId = "user-owner",
                roleCode = "EDITOR",
                createdAt = Instant.fromEpochMilliseconds(fixedNow).toString(),
                expiresAt = Instant.fromEpochMilliseconds(fixedNow + 7 * 24 * 3600 * 1000L).toString(),
                maxUses = 10,
                usesCount = 0,
                deletedAt = null,
                version = 1L,
            )

            val acked = database.localMutationDao().ackWorkspaceInvitationWriteV2(
                operationId = op.operationId,
                record = remoteDto,
                nowEpochMillis = fixedNow + 1000L,
            )
            assertTrue(acked)

            // Outbox silindi mi?
            assertNull(database.localMutationDao().getOutboxById(op.operationId))

            // Yereldeki token_hash korundu mu ve metadata güncellendi mi?
            val updatedInv = database.localMutationDao().getWorkspaceInvitationById(inv.id)
            assertNotNull(updatedInv)
            assertEquals(tokenHash, updatedInv.tokenHash, "ACK sonrası yerel token_hash korunmalıdır.")
            assertEquals("SYNCED", updatedInv.sync.syncStatus)
            assertEquals(1L, updatedInv.sync.version)
            assertNull(updatedInv.sync.baseVersion)

            // Raw token hiçbir alanda yok
            assertFalse(updatedInv.toString().contains(rawToken))
        } finally {
            database.close()
        }
    }
}
