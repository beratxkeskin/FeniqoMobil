package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.dto.WorkspaceOwnershipTransferResultDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WorkspaceOwnershipTransferRepositoryTest {

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
    }

    private val fixedNow = 1757160000000L

    private fun createRepository(
        database: FeniqoDatabase,
        authRepo: AuthRepository,
        remote: FakeWorkspaceCoreRemoteDataSource,
    ): OfflineFirstWorkspaceRepository {
        return OfflineFirstWorkspaceRepository(
            authRepository = authRepo,
            workspaceDao = database.workspaceDao(),
            localMutationDao = database.localMutationDao(),
            remoteDataSource = remote,
            remoteSyncDao = database.remoteSyncDao(),
            nowEpochMillisProvider = { fixedNow },
        )
    }

    private fun sampleWorkspace(
        id: String = "ws-1",
        ownerId: String = "user-owner",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
        deletedAtEpochMillis: Long? = null,
    ) = WorkspaceEntity(
        id = id,
        name = "Transfer Çalışma Alanı",
        normalizedName = "transfer calisma alani",
        ownerId = ownerId,
        typeCode = "shared",
        currencyCode = "TRY",
        description = "Açıklama",
        createdAtEpochMillis = 1000L,
        sync = SyncMetadata(
            syncStatus = syncStatus,
            updatedAtEpochMillis = 1000L,
            localUpdatedAtEpochMillis = 1000L,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = version,
            lastSyncError = null,
        ),
    )

    private fun sampleMember(
        workspaceId: String = "ws-1",
        userId: String = "user-owner",
        roleCode: String = "OWNER",
        syncStatus: String = "SYNCED",
        version: Long = 1L,
        deletedAtEpochMillis: Long? = null,
    ) = WorkspaceMemberEntity(
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
            baseVersion = version,
            lastSyncError = null,
        ),
    )

    private fun sampleTransferResult(
        workspaceId: String = "ws-1",
        actorUserId: String = "user-owner",
        targetUserId: String = "user-target",
        wsVersion: Long = 2L,
        actorVersion: Long = 2L,
        targetVersion: Long = 2L,
        actorRole: String = "EDITOR",
        targetRole: String = "OWNER",
        workspaceDeletedAt: String? = null,
        actorDeletedAt: String? = null,
        targetDeletedAt: String? = null,
    ) = WorkspaceOwnershipTransferResultDto(
        workspace = WorkspaceDto(
            id = workspaceId,
            name = "Transfer Çalışma Alanı",
            normalizedName = "transfer calisma alani",
            ownerId = targetUserId,
            typeCode = "shared",
            currencyCode = "TRY",
            description = "Açıklama",
            createdAt = "2026-09-08T10:00:00Z",
            updatedAt = "2026-09-08T10:00:00Z",
            deletedAt = workspaceDeletedAt,
            version = wsVersion,
        ),
        actorMember = WorkspaceMemberDto(
            workspaceId = workspaceId,
            userId = actorUserId,
            roleCode = actorRole,
            joinedAt = "2026-09-08T10:00:00Z",
            updatedAt = "2026-09-08T10:00:00Z",
            deletedAt = actorDeletedAt,
            version = actorVersion,
        ),
        targetMember = WorkspaceMemberDto(
            workspaceId = workspaceId,
            userId = targetUserId,
            roleCode = targetRole,
            joinedAt = "2026-09-08T10:00:00Z",
            updatedAt = "2026-09-08T10:00:00Z",
            deletedAt = targetDeletedAt,
            version = targetVersion,
        ),
    )

    @Test
    fun unauthenticated_session_returns_auth_session_required_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(session = null)
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Authentication>(error)
            assertEquals("auth_session_required", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun non_existent_or_deleted_workspace_returns_workspace_not_found() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            val result = repo.transferOwnership(EntityId("ws-non-existent"), EntityId("user-target"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("workspace_not_found", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun non_owner_actor_is_rejected_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(
                session = AuthSession(
                    userId = EntityId("user-editor"),
                    email = "editor@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
                ),
            )
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-real-owner")))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-editor", roleCode = "EDITOR"),
                    sampleMember(workspaceId = "ws-1", userId = "user-target", roleCode = "VIEWER"),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Authentication>(error)
            assertEquals("transfer_actor_not_owner", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun target_not_member_is_rejected_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner")))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER")),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-not-member"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("target_member_not_found", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun target_already_owner_is_rejected_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner")))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER"),
                    sampleMember(workspaceId = "ws-1", userId = "user-second-owner", roleCode = "OWNER"),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-second-owner"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("transfer_target_already_owner", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun self_transfer_is_rejected_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner")))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER")),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-owner"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("cannot_change_own_role", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun uncommitted_local_changes_prevent_ownership_transfer_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            // target member PENDING_UPDATE durumunda
            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner")))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER", syncStatus = "SYNCED"),
                    sampleMember(workspaceId = "ws-1", userId = "user-target", roleCode = "EDITOR", syncStatus = "PENDING_UPDATE"),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(0, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Conflict>(error)
            assertEquals("local_uncommitted_changes_prevent_ownership_transfer", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun successful_transfer_atomically_updates_room_and_produces_no_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource().apply {
                transferOwnershipHandler = { wsId, targetId, wsVer, actorVer, targetVer ->
                    assertEquals("ws-1", wsId)
                    assertEquals("user-target", targetId)
                    assertEquals(1L, wsVer)
                    assertEquals(1L, actorVer)
                    assertEquals(1L, targetVer)
                    sampleTransferResult(
                        workspaceId = wsId,
                        actorUserId = "user-owner",
                        targetUserId = targetId,
                        wsVersion = 2L,
                        actorVersion = 2L,
                        targetVersion = 2L,
                    )
                }
            }
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner", version = 1L)))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER", version = 1L),
                    sampleMember(workspaceId = "ws-1", userId = "user-target", roleCode = "EDITOR", version = 1L),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(1, remote.transferCalls)
            assertIs<RepositoryResult.Success<Unit>>(result)

            // Room doğrulaması
            val updatedWs = database.workspaceDao().getWorkspaceById("ws-1")
            assertNotNull(updatedWs)
            assertEquals("user-target", updatedWs.ownerId)
            assertEquals(2L, updatedWs.sync.version)

            val members = database.remoteSyncDao().getWorkspaceMemberRows("ws-1")
            val actorAfter = members.firstOrNull { it.userId == "user-owner" }
            assertNotNull(actorAfter)
            assertEquals("EDITOR", actorAfter.roleCode)
            assertEquals(2L, actorAfter.sync.version)

            val targetAfter = members.firstOrNull { it.userId == "user-target" }
            assertNotNull(targetAfter)
            assertEquals("OWNER", targetAfter.roleCode)
            assertEquals(2L, targetAfter.sync.version)

            // Outbox üretilmediği doğrulandı
            val outboxOps = database.remoteSyncDao().getAllWorkspaceOperations("ws-1")
            assertTrue(outboxOps.isEmpty(), "Ownership transfer işleminde kesinlikle outbox üretilmemelidir.")
        } finally {
            database.close()
        }
    }

    @Test
    fun remote_version_conflict_preserves_local_state_and_produces_no_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource().apply {
                transferOwnershipHandler = { _, _, _, _, _ ->
                    throw IllegalStateException("ownership_transfer_version_conflict")
                }
            }
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner", version = 1L)))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER", version = 1L),
                    sampleMember(workspaceId = "ws-1", userId = "user-target", roleCode = "EDITOR", version = 1L),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(1, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Conflict>(error)
            assertEquals("ownership_transfer_version_conflict", error.code)

            // Local state korunmalı
            val ws = database.workspaceDao().getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals("user-owner", ws.ownerId)

            val actor = database.remoteSyncDao().getWorkspaceMemberRow("ws-1", "user-owner")
            assertNotNull(actor)
            assertEquals("OWNER", actor.roleCode)

            val outboxOps = database.remoteSyncDao().getAllWorkspaceOperations("ws-1")
            assertTrue(outboxOps.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun inconsistent_remote_dto_returns_invalid_remote_payload_and_preserves_local_state() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource().apply {
                transferOwnershipHandler = { wsId, targetId, _, _, _ ->
                    // Hedef üye OWNER yerine yanlışlıkla VIEWER dönüyor
                    sampleTransferResult(
                        workspaceId = wsId,
                        actorUserId = "user-owner",
                        targetUserId = targetId,
                        actorRole = "EDITOR",
                        targetRole = "VIEWER", // Tutarsız rol!
                    )
                }
            }
            val repo = createRepository(database, authRepo, remote)

            database.remoteSyncDao().upsertWorkspaceRows(listOf(sampleWorkspace(id = "ws-1", ownerId = "user-owner", version = 1L)))
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    sampleMember(workspaceId = "ws-1", userId = "user-owner", roleCode = "OWNER", version = 1L),
                    sampleMember(workspaceId = "ws-1", userId = "user-target", roleCode = "EDITOR", version = 1L),
                ),
            )

            val result = repo.transferOwnership(EntityId("ws-1"), EntityId("user-target"))

            assertEquals(1, remote.transferCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("invalid_remote_payload", error.code)

            // Local state korunmalı
            val ws = database.workspaceDao().getWorkspaceById("ws-1")
            assertNotNull(ws)
            assertEquals("user-owner", ws.ownerId)

            val actor = database.remoteSyncDao().getWorkspaceMemberRow("ws-1", "user-owner")
            assertNotNull(actor)
            assertEquals("OWNER", actor.roleCode)
        } finally {
            database.close()
        }
    }
}
