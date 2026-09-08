package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationRedeemResultDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class OfflineFirstWorkspaceRepositoryJoinTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private class FakeAuthRepository(
        session: AuthSession? = AuthSession(
            userId = EntityId("user-joiner"),
            email = "joiner@example.com",
            expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
        ),
    ) : AuthRepository {
        private val sessionFlow = MutableStateFlow(session)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-joiner"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        fun setSession(session: AuthSession?) {
            sessionFlow.value = session
        }
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

    private fun sampleRedeemResult(
        workspaceId: String = "ws-target-1",
        userId: String = "user-joiner",
        role: String = "EDITOR",
        version: Long = 1L,
        deletedAt: String? = null,
        workspaceDeletedAt: String? = null,
    ) = WorkspaceInvitationRedeemResultDto(
        workspace = WorkspaceDto(
            id = workspaceId,
            name = "Davetli Çalışma Alanı",
            normalizedName = "davetli calisma alani",
            ownerId = "user-owner",
            typeCode = "shared",
            currencyCode = "TRY",
            description = "Ortak çalışma alanı",
            createdAt = "2026-09-08T10:00:00Z",
            updatedAt = "2026-09-08T10:00:00Z",
            deletedAt = workspaceDeletedAt,
            version = version,
        ),
        member = WorkspaceMemberDto(
            workspaceId = workspaceId,
            userId = userId,
            roleCode = role,
            joinedAt = "2026-09-08T10:00:00Z",
            updatedAt = "2026-09-08T10:00:00Z",
            deletedAt = deletedAt,
            version = version,
        ),
    )

    @Test
    fun blank_code_does_not_call_remote_and_returns_validation_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("   "))

            assertEquals(0, remote.redeemCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Validation>(error)
            assertEquals("workspace_invitation_code_blank", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun unauthenticated_session_returns_auth_session_required_without_remote_call() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository(session = null)
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("VALID-TOKEN-123"))

            assertEquals(0, remote.redeemCalls)
            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Authentication>(error)
            assertEquals("auth_session_required", error.code)
        } finally {
            database.close()
        }
    }

    @Test
    fun successful_redeem_writes_workspace_and_member_atomically_without_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = "ws-123", userId = "user-joiner")
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("INVITE-TOKEN-ABC"))

            assertIs<RepositoryResult.Success<EntityId>>(result)
            assertEquals(EntityId("ws-123"), result.value)
            assertEquals(1, remote.redeemCalls)
            assertEquals("INVITE-TOKEN-ABC", remote.lastRedeemToken)

            // Room SSOT doğrulaması: Workspace ve Member yazılmış olmalı
            val ws = database.workspaceDao().getWorkspaceById("ws-123")
            assertNotNull(ws)
            assertEquals("Davetli Çalışma Alanı", ws.name)
            assertEquals(SyncStatus.SYNCED.name, ws.sync.syncStatus)
            assertEquals(1L, ws.sync.version)

            val member = database.remoteSyncDao().getWorkspaceMemberRow("ws-123", "user-joiner")
            assertNotNull(member)
            assertEquals("EDITOR", member.roleCode)
            assertEquals(SyncStatus.SYNCED.name, member.sync.syncStatus)
            assertEquals(1L, member.sync.version)

            // Kesin kural: V2 outbox operasyonu ASLA üretilmemeli
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = fixedNow + 10000L, limit = 100)
            assertTrue(pendingOps.isEmpty(), "Redeem sonrası hiçbir outbox işlemi oluşturulmamalıdır.")
        } finally {
            database.close()
        }
    }

    @Test
    fun idempotent_redeem_does_not_duplicate_member_and_updates_cleanly() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = "ws-idempotent", userId = "user-joiner", version = 2L)
            }
            val repo = createRepository(database, authRepo, remote)

            // İlk katılım
            val res1 = repo.join(WorkspaceInviteCode("TOKEN-SAME"))
            assertIs<RepositoryResult.Success<EntityId>>(res1)

            // İkinci katılım (idempotent)
            val res2 = repo.join(WorkspaceInviteCode("TOKEN-SAME"))
            assertIs<RepositoryResult.Success<EntityId>>(res2)

            val members = database.remoteSyncDao().getWorkspaceMemberRows("ws-idempotent")
            assertEquals(1, members.size, "Tek kullanıcı için birden fazla member kaydı oluşmamalıdır.")
            assertEquals(2L, members.first().sync.version)

            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = fixedNow + 10000L, limit = 100)
            assertTrue(pendingOps.isEmpty(), "İdempotent redeem outbox üretmemelidir.")
        } finally {
            database.close()
        }
    }

    @Test
    fun soft_deleted_member_revival_updates_member_to_active_without_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            // Yerelde daha önceden soft-delete edilmiş bir üyelik var
            database.workspaceDao().upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-revive",
                    name = "Eski Alan",
                    normalizedName = "eski alan",
                    ownerId = "user-owner",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = fixedNow - 100000L,
                    sync = SyncMetadata(
                        syncStatus = SyncStatus.SYNCED.name,
                        updatedAtEpochMillis = fixedNow - 50000L,
                        localUpdatedAtEpochMillis = fixedNow - 50000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = null,
                        lastSyncError = null,
                    ),
                ),
            )
            database.remoteSyncDao().upsertWorkspaceMemberRows(
                listOf(
                    WorkspaceMemberEntity(
                        workspaceId = "ws-revive",
                        userId = "user-joiner",
                        roleCode = "VIEWER",
                        joinedAtEpochMillis = fixedNow - 100000L,
                        sync = SyncMetadata(
                            syncStatus = SyncStatus.SYNCED.name,
                            updatedAtEpochMillis = fixedNow - 50000L,
                            localUpdatedAtEpochMillis = fixedNow - 50000L,
                            deletedAtEpochMillis = fixedNow - 50000L, // soft-deleted!
                            version = 1L,
                            baseVersion = null,
                            lastSyncError = null,
                        ),
                    ),
                ),
            )

            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = "ws-revive", userId = "user-joiner", role = "EDITOR", version = 2L, deletedAt = null)
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("REVIVE-TOKEN"))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            val member = database.remoteSyncDao().getWorkspaceMemberRow("ws-revive", "user-joiner")
            assertNotNull(member)
            assertEquals("EDITOR", member.roleCode)
            assertNull(member.sync.deletedAtEpochMillis, "Üyelik canlandırılmalı ve deletedAt null olmalıdır.")
            assertEquals(2L, member.sync.version)

            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = fixedNow + 10000L, limit = 100)
            assertTrue(pendingOps.isEmpty(), "Canlandırma işlemi outbox üretmemelidir.")
        } finally {
            database.close()
        }
    }

    @Test
    fun error_mappings_for_expiry_max_uses_and_not_found() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource()
            val repo = createRepository(database, authRepo, remote)

            // Süresi dolmuş
            remote.redeemHandler = { throw IllegalStateException("workspace_invitation_expired") }
            val resExpired = repo.join(WorkspaceInviteCode("EXPIRED-CODE"))
            assertIs<RepositoryResult.Failure>(resExpired)
            assertEquals("workspace_invitation_expired", (resExpired.error as AppError.Validation).code)

            // Limit dolmuş
            remote.redeemHandler = { throw IllegalStateException("workspace_invitation_limit_reached") }
            val resLimit = repo.join(WorkspaceInviteCode("LIMIT-CODE"))
            assertIs<RepositoryResult.Failure>(resLimit)
            assertEquals("workspace_invitation_limit_reached", (resLimit.error as AppError.Validation).code)

            // Bulunamadı
            remote.redeemHandler = { throw IllegalStateException("workspace_invitation_not_found") }
            val resNotFound = repo.join(WorkspaceInviteCode("UNKNOWN-CODE"))
            assertIs<RepositoryResult.Failure>(resNotFound)
            assertEquals("workspace_invitation_not_found", (resNotFound.error as AppError.Validation).code)

            // Network hatası
            remote.redeemHandler = { throw kotlinx.io.IOException("connection timeout") }
            val resNet = repo.join(WorkspaceInviteCode("NET-CODE"))
            assertIs<RepositoryResult.Failure>(resNet)
            assertIs<AppError.Network>(resNet.error)
        } finally {
            database.close()
        }
    }

    @Test
    fun remote_or_room_failure_leaves_no_partial_state() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource {
                // Member ve Workspace ID uyuşmazlığı Room transaction'ında require() hatası tetikler
                sampleRedeemResult(workspaceId = "ws-main", userId = "user-joiner").copy(
                    member = WorkspaceMemberDto(
                        workspaceId = "ws-mismatch",
                        userId = "user-joiner",
                        roleCode = "EDITOR",
                        joinedAt = "2026-09-08T10:00:00Z",
                        updatedAt = "2026-09-08T10:00:00Z",
                        version = 1L,
                    ),
                )
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("MISMATCH-CODE"))
            assertIs<RepositoryResult.Failure>(result)

            // Transaction rollback: Yerelde ne ws-main ne de member kalmalıdır
            assertNull(database.workspaceDao().getWorkspaceById("ws-main"))
            assertNull(database.remoteSyncDao().getWorkspaceMemberRow("ws-main", "user-joiner"))
            assertNull(database.remoteSyncDao().getWorkspaceMemberRow("ws-mismatch", "user-joiner"))
        } finally {
            database.close()
        }
    }

    @Test
    fun raw_token_and_hash_never_leak_into_room_outbox_or_error() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val secretToken = "SECRET-TOKEN-999-XYZ"
            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = "ws-secure", userId = "user-joiner")
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode(secretToken))
            assertIs<RepositoryResult.Success<EntityId>>(result)

            // Room ve Outbox kontrolü
            val ws = database.workspaceDao().getWorkspaceById("ws-secure")!!
            assertFalse(ws.name.contains(secretToken))
            assertFalse(ws.description?.contains(secretToken) == true)

            val ops = database.syncOperationDao().getReadyOperations(nowEpochMillis = fixedNow + 10000L, limit = 100)
            assertTrue(ops.isEmpty())

            // Hata mesajı içine sızmama testi
            remote.redeemHandler = { throw IllegalStateException("workspace_invitation_not_found") }
            val failResult = repo.join(WorkspaceInviteCode(secretToken))
            assertIs<RepositoryResult.Failure>(failResult)
            val errCode = (failResult.error as AppError.Validation).code
            assertFalse(errCode.contains(secretToken))
            assertFalse(errCode.contains("token_hash"))
        } finally {
            database.close()
        }
    }

    @Test
    fun member_workspace_or_user_mismatch_fails_closed() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource {
                // session'daki user-joiner yerine başka bir user_id dönüyor
                sampleRedeemResult(workspaceId = "ws-user-mismatch", userId = "user-intruder")
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("TOKEN-INTRUDER"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("member_user_mismatch", (result.error as AppError.Validation).code)

            // Veritabanına hiçbir şey yazılmamış olmalı
            assertNull(database.workspaceDao().getWorkspaceById("ws-user-mismatch"))
        } finally {
            database.close()
        }
    }

    @Test
    fun member_tombstone_in_response_fails_closed() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = "ws-tombstone", userId = "user-joiner", deletedAt = "2026-09-08T10:00:00Z")
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("TOKEN-TOMBSTONE"))
            assertIs<RepositoryResult.Failure>(result)
            assertEquals("member_tombstone", (result.error as AppError.Validation).code)
            assertNull(database.workspaceDao().getWorkspaceById("ws-tombstone"))
        } finally {
            database.close()
        }
    }

    @Test
    fun local_unsynced_record_prevents_snapshot_overwrite_and_returns_conflict_error_without_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val targetWsId = "ws-local-pending"

            // Yerelde PENDING_UPDATE durumunda bir workspace var
            database.workspaceDao().upsertWorkspace(
                WorkspaceEntity(
                    id = targetWsId,
                    name = "Yerel Değişiklikteki Alan",
                    normalizedName = "yerel degisiklikteki alan",
                    ownerId = "user-joiner",
                    typeCode = "personal",
                    currencyCode = "TRY",
                    description = "Henüz sunucuya gitmedi",
                    createdAtEpochMillis = fixedNow - 10000L,
                    sync = SyncMetadata(
                        syncStatus = SyncStatus.PENDING_UPDATE.name,
                        updatedAtEpochMillis = fixedNow,
                        localUpdatedAtEpochMillis = fixedNow,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            val remote = FakeWorkspaceCoreRemoteDataSource {
                sampleRedeemResult(workspaceId = targetWsId, userId = "user-joiner", version = 5L)
            }
            val repo = createRepository(database, authRepo, remote)

            val result = repo.join(WorkspaceInviteCode("TOKEN-CONFLICT"))

            assertIs<RepositoryResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Conflict>(error)
            assertEquals("workspace_local_mutation_conflict", error.code)

            // Yerel PENDING kayıt korunmalı, ezilmemeli!
            val localWs = database.workspaceDao().getWorkspaceById(targetWsId)!!
            assertEquals("Yerel Değişiklikteki Alan", localWs.name)
            assertEquals(SyncStatus.PENDING_UPDATE.name, localWs.sync.syncStatus)
            assertEquals(1L, localWs.sync.version)

            // Outbox üretilmemeli
            val pendingOps = database.syncOperationDao().getReadyOperations(nowEpochMillis = fixedNow + 10000L, limit = 100)
            assertTrue(pendingOps.isEmpty())
        } finally {
            database.close()
        }
    }
}
