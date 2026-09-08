package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
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
class OfflineFirstWorkspaceRepositoryMemberTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private var operationIdCounter = 1
    private fun testOperationId(): String = (operationIdCounter++).toString().padStart(32, '0')

    private class FakeAuthRepository(initialSession: AuthSession? = null) : AuthRepository {
        val sessionFlow = MutableStateFlow(initialSession)
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-id"))
        override suspend fun signOut(): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun refreshSession(): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private fun createRepository(
        database: FeniqoDatabase,
        authRepo: AuthRepository,
        remoteDataSource: FakeWorkspaceCoreRemoteDataSource = FakeWorkspaceCoreRemoteDataSource(),
    ): OfflineFirstWorkspaceRepository {
        return OfflineFirstWorkspaceRepository(
            authRepository = authRepo,
            workspaceDao = database.workspaceDao(),
            localMutationDao = database.localMutationDao(),
            remoteDataSource = remoteDataSource,
            remoteSyncDao = database.remoteSyncDao(),
            profileDao = database.profileDao(),
            operationIdFactory = ::testOperationId,
            nowEpochMillisProvider = { 1000L },
        )
    }

    private fun sampleWorkspace(
        id: String = "11111111-1111-1111-1111-111111111111",
        ownerId: String = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        version: Long = 1L,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = "Ortak Aile",
        normalizedName = "ortak aile",
        ownerId = ownerId,
        typeCode = "shared",
        currencyCode = "TRY",
        description = "Açıklama",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = null,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String,
        userId: String,
        roleCode: String,
        version: Long = 1L,
        baseVersion: Long? = null,
        deletedAtEpochMillis: Long? = null,
    ): WorkspaceMemberEntity = WorkspaceMemberEntity(
        workspaceId = workspaceId,
        userId = userId,
        roleCode = roleCode,
        joinedAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = "SYNCED",
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

    // =========================================================================
    // 1. CHANGE MEMBER ROLE TESTS
    // =========================================================================

    @Test
    fun changeMemberRole_unauthenticated_failsWithAuthSessionRequired() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(null)
            val repo = createRepository(database, authRepo)

            val result = repo.changeMemberRole(
                workspaceId = EntityId("11111111-1111-1111-1111-111111111111"),
                userId = EntityId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                role = WorkspaceRole.VIEWER,
            )

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Authentication>(error)
            assertEquals("auth_session_required", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_workspaceNotFoundOrTombstoned_failsWithWorkspaceNotFound() = runTest {
        val database = inMemoryDatabase()
        try {
            val actorId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            // 1. Workspace Room'da hiç yok
            val res1 = repo.changeMemberRole(
                workspaceId = EntityId("11111111-1111-1111-1111-111111111111"),
                userId = EntityId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                role = WorkspaceRole.VIEWER,
            )
            assertIs<RepositoryResult.Failure>(res1)
            assertEquals("workspace_not_found", (res1.error as AppError.Validation).code)

            // 2. Workspace tombstone
            val ws = sampleWorkspace(deletedAtEpochMillis = 2000L)
            database.workspaceDao().upsertWorkspace(ws)
            val res2 = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                role = WorkspaceRole.VIEWER,
            )
            assertIs<RepositoryResult.Failure>(res2)
            assertEquals("workspace_not_found", (res2.error as AppError.Validation).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_actorNotOwner_failsWithActorNotPermitted() = runTest {
        val database = inMemoryDatabase()
        try {
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "editor@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ws = sampleWorkspace()
            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val editorMember = sampleMember(ws.id, actorId, "EDITOR")
            val targetMember = sampleMember(ws.id, "cccccccc-cccc-cccc-cccc-cccccccccccc", "VIEWER")

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(editorMember)
            database.workspaceDao().upsertMember(targetMember)

            val result = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(targetMember.userId),
                role = WorkspaceRole.EDITOR,
            )

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Authentication>(error)
            assertEquals("actor_not_permitted", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_actorChangesOwnRole_failsWithCannotChangeOwnRole() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)

            val result = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(ws.ownerId),
                role = WorkspaceRole.EDITOR,
            )

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("cannot_change_own_role", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_newRoleIsOwner_failsWithOwnerRoleChangeRequiresTransfer() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val targetMember = sampleMember(ws.id, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "EDITOR")
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(targetMember)

            val result = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(targetMember.userId),
                role = WorkspaceRole.OWNER,
            )

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("owner_role_change_requires_transfer", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_targetMemberNotFoundOrTombstone_failsWithTargetMemberNotFound() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)

            // Olmayan üye
            val resultNonExistent = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId("99999999-9999-9999-9999-999999999999"),
                role = WorkspaceRole.VIEWER,
            )
            assertIs<RepositoryResult.Failure>(resultNonExistent)
            assertEquals("target_member_not_found", (resultNonExistent.error as AppError.Validation).code)

            // Silinmiş üye (tombstone)
            val tombstoned = sampleMember(ws.id, "88888888-8888-8888-8888-888888888888", "EDITOR", deletedAtEpochMillis = 2000L)
            database.workspaceDao().upsertMember(tombstoned)

            val resultTombstone = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(tombstoned.userId),
                role = WorkspaceRole.VIEWER,
            )
            assertIs<RepositoryResult.Failure>(resultTombstone)
            assertEquals("target_member_not_found", (resultTombstone.error as AppError.Validation).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_baseVersionZeroOrNegative_failsWithLocalMemberVersionUnavailable() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val targetMember = sampleMember(ws.id, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "EDITOR", version = 0L)
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(targetMember)

            val result = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(targetMember.userId),
                role = WorkspaceRole.VIEWER,
            )

            assertIs<RepositoryResult.Failure>(result)
            assertEquals("local_member_version_unavailable", (result.error as AppError.Validation).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun changeMemberRole_success_atomicallyWritesUpdatedMemberAndOutbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val targetMember = sampleMember(ws.id, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "EDITOR", version = 2L)
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(targetMember)

            val result = repo.changeMemberRole(
                workspaceId = EntityId(ws.id),
                userId = EntityId(targetMember.userId),
                role = WorkspaceRole.VIEWER,
            )

            assertIs<RepositoryResult.Success<Unit>>(result)

            // Room'da güncellenmiş üye kontrolü
            val stored = database.workspaceDao().getMember(ws.id, targetMember.userId)
            assertNotNull(stored)
            assertEquals("VIEWER", stored.roleCode)
            assertEquals("PENDING_UPDATE", stored.sync.syncStatus)

            // Outbox kontrolü
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10)
            assertEquals(1, pendingOps.size)
            val op = pendingOps.first()
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(OutboxOperationType.UPDATE.name, op.operationTypeCode)
            assertEquals(2L, op.baseVersion)
            assertNotNull(op.payloadJson)
            assertTrue(op.payloadJson!!.contains("VIEWER"))
            assertFalse(op.payloadJson!!.contains("token"))
            assertFalse(op.payloadJson!!.contains("token_hash"))
        } finally {
            database.close()
        }
    }

    // =========================================================================
    // 2. LEAVE WORKSPACE TESTS
    // =========================================================================

    @Test
    fun leave_unauthenticated_failsWithAuthSessionRequired() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(null)
            val repo = createRepository(database, authRepo)

            val result = repo.leave(EntityId("11111111-1111-1111-1111-111111111111"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("auth_session_required", (result.error as AppError.Authentication).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_workspaceNotFound_failsWithWorkspaceNotFound() = runTest {
        val database = inMemoryDatabase()
        try {
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "viewer@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.leave(EntityId("11111111-1111-1111-1111-111111111111"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("workspace_not_found", (result.error as AppError.Validation).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_actorNotMember_failsWithActorNotMember() = runTest {
        val database = inMemoryDatabase()
        try {
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "nonmember@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ws = sampleWorkspace()
            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)

            val result = repo.leave(EntityId(ws.id))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("actor_not_member", (result.error as AppError.Authentication).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_actorIsOwner_failsClosedWithCannotLeaveAsOwnerRequiresTransfer() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val authRepo = FakeAuthRepository(AuthSession(EntityId(ws.ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)

            val result = repo.leave(EntityId(ws.id))
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("cannot_leave_as_owner_requires_transfer", error.code)

            // Hiçbir outbox ve üyelik mutasyonu olmamalı
            assertTrue(database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10).isEmpty())
            val storedOwner = database.workspaceDao().getMember(ws.id, ws.ownerId)
            assertNotNull(storedOwner)
            assertNull(storedOwner.sync.deletedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_baseVersionZeroOrNegative_failsWithLocalMemberVersionUnavailable() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "viewer@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val viewerMember = sampleMember(ws.id, actorId, "VIEWER", version = 0L)
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(viewerMember)

            val result = repo.leave(EntityId(ws.id))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("local_member_version_unavailable", (result.error as AppError.Validation).code)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_success_editorOrViewer_tombstonesMember_insertsDeleteOutbox_andClearsActiveWorkspaceIfMatches() = runTest {
        val database = inMemoryDatabase()
        try {
            val ws = sampleWorkspace()
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "viewer@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val ownerMember = sampleMember(ws.id, ws.ownerId, "OWNER")
            val viewerMember = sampleMember(ws.id, actorId, "VIEWER", version = 3L)
            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(viewerMember)

            // Aktif profil: ayrılan çalışma alanı aktif
            val profile = sampleProfile(id = actorId, activeWorkspaceId = ws.id)
            database.profileDao().upsert(profile)

            val result = repo.leave(EntityId(ws.id))
            assertIs<RepositoryResult.Success<Unit>>(result)

            // 1. Profil active_workspace_id temizlenmeli
            val storedProfile = database.profileDao().observeById(actorId).first()
            assertNotNull(storedProfile)
            assertNull(storedProfile.activeWorkspaceId)

            // 2. Member tombstone olmalı
            val storedMember = database.workspaceDao().getMember(ws.id, actorId)
            assertNotNull(storedMember)
            assertNotNull(storedMember.sync.deletedAtEpochMillis)
            assertEquals(SyncStatus.PENDING_DELETE.name, storedMember.sync.syncStatus)

            // 3. Workspace satırı canlı kalmalı (silinmemeli veya tombstone olmamalı)
            val storedWs = database.workspaceDao().getWorkspaceById(ws.id)
            assertNotNull(storedWs)
            assertNull(storedWs.sync.deletedAtEpochMillis)

            // 4. DELETE outbox kaydı oluşmalı
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10)
            assertEquals(1, pendingOps.size)
            val op = pendingOps.first()
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(OutboxOperationType.DELETE.name, op.operationTypeCode)
            assertEquals(3L, op.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun leave_success_whenAnotherWorkspaceIsActive_doesNotClearActiveWorkspace() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsLeaving = sampleWorkspace(id = "11111111-1111-1111-1111-111111111111")
            val wsOther = sampleWorkspace(id = "22222222-2222-2222-2222-222222222222")
            val actorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val authRepo = FakeAuthRepository(AuthSession(EntityId(actorId), "viewer@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val memberLeaving = sampleMember(wsLeaving.id, actorId, "VIEWER", version = 2L)
            database.workspaceDao().upsertWorkspace(wsLeaving)
            database.workspaceDao().upsertWorkspace(wsOther)
            database.workspaceDao().upsertMember(memberLeaving)

            // Başka workspace aktif
            val profile = sampleProfile(id = actorId, activeWorkspaceId = wsOther.id)
            database.profileDao().upsert(profile)

            val result = repo.leave(EntityId(wsLeaving.id))
            assertIs<RepositoryResult.Success<Unit>>(result)

            // active_workspace_id wsOther.id olarak KORUNMALI
            val storedProfile = database.profileDao().observeById(actorId).first()
            assertNotNull(storedProfile)
            assertEquals(wsOther.id, storedProfile.activeWorkspaceId)
        } finally {
            database.close()
        }
    }

    @Test
    fun removeMember_success_tombstonesTarget_enqueuesDeleteOutbox_andDoesNotClearActorActiveWorkspace() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            val ownerId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val targetId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

            val ws = sampleWorkspace(id = wsId, ownerId = ownerId, version = 1L)
            val ownerMember = sampleMember(wsId, ownerId, "OWNER", version = 1L)
            val targetMember = sampleMember(wsId, targetId, "EDITOR", version = 2L)
            val ownerProfile = sampleProfile(id = ownerId, activeWorkspaceId = wsId)

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(targetMember)
            database.profileDao().upsert(ownerProfile)

            val authRepo = FakeAuthRepository(AuthSession(EntityId(ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.removeMember(EntityId(wsId), EntityId(targetId))
            assertIs<RepositoryResult.Success<Unit>>(result)

            // 1. Hedef üye Room'da tombstone olmalı
            val members = database.workspaceDao().observeMembers(wsId).first()
            assertEquals(1, members.size) // observeMembers deleted_at is null filtreler
            assertEquals(ownerId, members.first().userId)

            val rawTarget = database.workspaceDao().getMember(wsId, targetId)
            assertNotNull(rawTarget)
            assertNotNull(rawTarget.sync.deletedAtEpochMillis)
            assertEquals(SyncStatus.PENDING_DELETE.name, rawTarget.sync.syncStatus)
            assertEquals(2L, rawTarget.sync.baseVersion)

            // 2. Aktör profilinin active_workspace_id DEĞİŞMEMELİ
            val storedOwnerProfile = database.profileDao().observeById(ownerId).first()
            assertNotNull(storedOwnerProfile)
            assertEquals(wsId, storedOwnerProfile.activeWorkspaceId)

            // 3. DELETE outbox kaydı oluşmalı
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10)
            assertEquals(1, pendingOps.size)
            val op = pendingOps.first()
            assertEquals("WORKSPACE_MEMBER", op.entityTypeCode)
            assertEquals(OutboxOperationType.DELETE.name, op.operationTypeCode)
            assertEquals(2L, op.baseVersion)
            assertEquals("${wsId}:${targetId}", op.entityId)
        } finally {
            database.close()
        }
    }

    @Test
    fun removeMember_byNonOwner_rejectedWithActorNotPermitted() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            val ownerId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val editorId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            val viewerId = "cccccccc-cccc-cccc-cccc-cccccccccccc"

            val ws = sampleWorkspace(id = wsId, ownerId = ownerId, version = 1L)
            val ownerMember = sampleMember(wsId, ownerId, "OWNER", version = 1L)
            val editorMember = sampleMember(wsId, editorId, "EDITOR", version = 1L)
            val viewerMember = sampleMember(wsId, viewerId, "VIEWER", version = 1L)

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(editorMember)
            database.workspaceDao().upsertMember(viewerMember)

            val authRepo = FakeAuthRepository(AuthSession(EntityId(editorId), "editor@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.removeMember(EntityId(wsId), EntityId(viewerId))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals(AppError.Authentication("actor_not_permitted"), result.error)

            // Hiçbir outbox işlemi oluşturulmamalı
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10)
            assertTrue(pendingOps.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun removeMember_removingSelf_rejectedWithCannotRemoveSelfMember() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            val ownerId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

            val ws = sampleWorkspace(id = wsId, ownerId = ownerId, version = 1L)
            val ownerMember = sampleMember(wsId, ownerId, "OWNER", version = 1L)

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)

            val authRepo = FakeAuthRepository(AuthSession(EntityId(ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.removeMember(EntityId(wsId), EntityId(ownerId))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals(AppError.Validation("cannot_remove_self_member"), result.error)
        } finally {
            database.close()
        }
    }

    @Test
    fun removeMember_removingAnotherOwner_rejectedWithCannotRemoveWorkspaceOwner() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            val owner1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val owner2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

            val ws = sampleWorkspace(id = wsId, ownerId = owner1, version = 1L)
            val member1 = sampleMember(wsId, owner1, "OWNER", version = 1L)
            val member2 = sampleMember(wsId, owner2, "OWNER", version = 1L)

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(member1)
            database.workspaceDao().upsertMember(member2)

            val authRepo = FakeAuthRepository(AuthSession(EntityId(owner1), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.removeMember(EntityId(wsId), EntityId(owner2))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals(AppError.Validation("cannot_remove_workspace_owner"), result.error)
        } finally {
            database.close()
        }
    }

    @Test
    fun removeMember_whenTargetNotSynced_rejectedWithVersionUnavailable() = runTest {
        val database = inMemoryDatabase()
        try {
            val wsId = "11111111-1111-1111-1111-111111111111"
            val ownerId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            val targetId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

            val ws = sampleWorkspace(id = wsId, ownerId = ownerId, version = 1L)
            val ownerMember = sampleMember(wsId, ownerId, "OWNER", version = 1L)
            // Target PENDING_UPDATE (SYNCED değil)
            val targetMember = sampleMember(wsId, targetId, "EDITOR", version = 1L).copy(
                sync = SyncMetadata(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    version = 1L,
                    baseVersion = 1L,
                    localUpdatedAtEpochMillis = 2000L,
                    updatedAtEpochMillis = 1000L,
                    deletedAtEpochMillis = null,
                    lastSyncError = null,
                ),
            )

            database.workspaceDao().upsertWorkspace(ws)
            database.workspaceDao().upsertMember(ownerMember)
            database.workspaceDao().upsertMember(targetMember)

            val authRepo = FakeAuthRepository(AuthSession(EntityId(ownerId), "owner@test.com", Instant.fromEpochMilliseconds(5000L)))
            val repo = createRepository(database, authRepo)

            val result = repo.removeMember(EntityId(wsId), EntityId(targetId))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals(AppError.Validation("local_member_version_unavailable"), result.error)

            // Hiçbir outbox kaydı oluşmamalı
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = Long.MAX_VALUE, limit = 10)
            assertTrue(pendingOps.isEmpty())
        } finally {
            database.close()
        }
    }
}
