package com.feniqo.mobile.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OfflineFirstWorkspaceRepositoryTest {

    private fun inMemoryDatabase(): FeniqoDatabase {
        return Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
            context = ApplicationProvider.getApplicationContext(),
            factory = { FeniqoDatabaseConstructor.initialize() },
        ).allowMainThreadQueries().build()
    }

    private class FakeAuthRepository(
        private val sessionFlow: MutableStateFlow<AuthSession?> = MutableStateFlow(
            AuthSession(
                userId = EntityId("user-1"),
                email = "user1@example.com",
                expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
            ),
        ),
    ) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        fun setSession(session: AuthSession?) {
            sessionFlow.value = session
        }
    }

    private var opCounter = 1
    private fun testOpIdFactory(): String = (opCounter++).toString().padStart(32, '0')

    @Test
    fun createWorkspace_writes_workspace_owner_member_and_canonical_v2_outbox_payload() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            var idCounter = 100
            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                entityIdGenerator = EntityIdGenerator { EntityId("ws-${idCounter++}") },
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1757160000000L },
            )

            val result = repo.createWorkspace(
                CreateWorkspaceCommand(
                    name = "Yeni Ortak Alan 🚀",
                    type = WorkspaceType.SHARED,
                    currency = Currency.TRY,
                    description = "Ortak harcamalar",
                ),
            )

            assertTrue(result is RepositoryResult.Success)
            val createdId = result.value.value
            assertEquals("ws-100", createdId)

            // Room SSOT Workspace kontrolü
            val wsEntity = workspaceDao.getWorkspaceById("ws-100")
            assertNotNull(wsEntity)
            assertEquals("Yeni Ortak Alan 🚀", wsEntity.name)
            assertEquals("yeni ortak alan 🚀", wsEntity.normalizedName)
            assertEquals("user-1", wsEntity.ownerId)
            assertEquals("shared", wsEntity.typeCode)
            assertEquals("TRY", wsEntity.currencyCode)
            assertEquals("Ortak harcamalar", wsEntity.description)
            assertEquals(1757160000000L, wsEntity.createdAtEpochMillis)
            assertEquals("PENDING_CREATE", wsEntity.sync.syncStatus)
            assertEquals(0L, wsEntity.sync.version)
            assertNull(wsEntity.sync.baseVersion)
            assertNull(wsEntity.sync.deletedAtEpochMillis)

            // Room SSOT Member kontrolü (Strict OWNER bootstrap)
            val members = workspaceDao.observeMembers("ws-100").first()
            assertEquals(1, members.size)
            val ownerMember = members.first()
            assertEquals("ws-100", ownerMember.workspaceId)
            assertEquals("user-1", ownerMember.userId)
            assertEquals("OWNER", ownerMember.roleCode)
            assertEquals("PENDING_CREATE", ownerMember.sync.syncStatus)

            // Outbox V2 kaydı ve canonical D3 payload kontrolü
            val readyOps = operationDao.getReadyOperations(nowEpochMillis = 1757160000000L, limit = 10)
            assertEquals(1, readyOps.size)
            val op = readyOps.first()
            assertEquals("WORKSPACE", op.entityTypeCode)
            assertEquals("ws-100", op.entityId)
            assertEquals("CREATE", op.operationTypeCode)
            assertNull(op.baseVersion)
            assertEquals(2, op.protocolVersion)

            val json = Json { ignoreUnknownKeys = false }
            val payloadObj = json.parseToJsonElement(op.payloadJson!!).jsonObject
            assertEquals("ws-100", payloadObj["id"]?.jsonPrimitive?.content)
            assertEquals("Yeni Ortak Alan 🚀", payloadObj["name"]?.jsonPrimitive?.content)
            assertEquals("shared", payloadObj["type_code"]?.jsonPrimitive?.content)
            assertEquals("TRY", payloadObj["currency_code"]?.jsonPrimitive?.content)
            assertEquals("Ortak harcamalar", payloadObj["description"]?.jsonPrimitive?.content)
            assertEquals("2025-09-06T12:00:00Z", payloadObj["created_at"]?.jsonPrimitive?.content)

            // Yasak alanların yokluğu
            assertFalse(payloadObj.containsKey("owner_id"))
            assertFalse(payloadObj.containsKey("normalized_name"))
            assertFalse(payloadObj.containsKey("version"))
            assertFalse(payloadObj.containsKey("base_version"))
        } finally {
            database.close()
        }
    }

    @Test
    fun createWorkspace_with_default_generator_produces_rfc4122_hyphenated_uuid() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1757160000000L },
            )

            val result = repo.createWorkspace(
                CreateWorkspaceCommand(
                    name = "UUID Test Workspace",
                    type = WorkspaceType.PERSONAL,
                    currency = Currency.TRY,
                ),
            )

            assertTrue(result is RepositoryResult.Success)
            val generatedId = result.value.value
            val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            assertTrue(uuidRegex.matches(generatedId), "Workspace ID must match RFC-4122 v4 hyphenated UUID: $generatedId")

            val readyOps = operationDao.getReadyOperations(nowEpochMillis = 1757160000000L, limit = 10)
            assertEquals(1, readyOps.size)
            val op = readyOps.first()
            assertEquals(generatedId, op.entityId)

            val json = Json { ignoreUnknownKeys = false }
            val payloadObj = json.parseToJsonElement(op.payloadJson!!).jsonObject
            assertEquals(generatedId, payloadObj["id"]?.jsonPrimitive?.content)
        } finally {
            database.close()
        }
    }

    @Test
    fun updateWorkspace_validates_permission_and_writes_v2_outbox_with_base_version() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1757170000000L },
            )

            // Mevcut SYNCED workspace ve OWNER üye kaydı hazırla
            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-200",
                    name = "Eski Başlık",
                    normalizedName = "eski başlık",
                    ownerId = "user-1",
                    typeCode = "personal",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 3L,
                        baseVersion = 3L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-200",
                    userId = "user-1",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            val updateResult = repo.updateWorkspace(
                UpdateWorkspaceCommand(
                    id = EntityId("ws-200"),
                    name = "Güncel Başlık 🇹🇷",
                    type = WorkspaceType.SHARED,
                    currency = Currency.USD,
                    description = "Yeni açıklama",
                ),
            )

            assertTrue(updateResult is RepositoryResult.Success)

            // Room SSOT Workspace kontrolü
            val updatedWs = workspaceDao.getWorkspaceById("ws-200")
            assertNotNull(updatedWs)
            assertEquals("Güncel Başlık 🇹🇷", updatedWs.name)
            assertEquals("güncel başlık 🇹🇷", updatedWs.normalizedName)
            assertEquals("shared", updatedWs.typeCode)
            assertEquals("USD", updatedWs.currencyCode)
            assertEquals("Yeni açıklama", updatedWs.description)
            assertEquals("PENDING_UPDATE", updatedWs.sync.syncStatus)
            assertEquals(3L, updatedWs.sync.baseVersion)

            // Outbox kontrolü
            val readyOps = operationDao.getReadyOperations(nowEpochMillis = 1757170000000L, limit = 10)
            assertEquals(1, readyOps.size)
            val op = readyOps.first()
            assertEquals("UPDATE", op.operationTypeCode)
            assertEquals("ws-200", op.entityId)
            assertEquals(3L, op.baseVersion)

            val json = Json { ignoreUnknownKeys = false }
            val payloadObj = json.parseToJsonElement(op.payloadJson!!).jsonObject
            assertEquals(setOf("id", "name", "type_code", "currency_code", "description"), payloadObj.keys)
            assertEquals("Güncel Başlık 🇹🇷", payloadObj["name"]?.jsonPrimitive?.content)
            assertEquals("shared", payloadObj["type_code"]?.jsonPrimitive?.content)
            assertEquals("USD", payloadObj["currency_code"]?.jsonPrimitive?.content)
            assertEquals("Yeni açıklama", payloadObj["description"]?.jsonPrimitive?.content)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteWorkspace_checks_owner_permission_and_writes_pending_delete_tombstone_and_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1757180000000L },
            )

            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-300",
                    name = "Silinecek Çalışma Alanı",
                    normalizedName = "silinecek çalışma alanı",
                    ownerId = "user-1",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 5L,
                        baseVersion = 5L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-300",
                    userId = "user-1",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            val deleteResult = repo.deleteWorkspace(EntityId("ws-300"))
            assertTrue(deleteResult is RepositoryResult.Success)

            // Room SSOT Workspace kontrolü: tombstone oluştu
            val deletedWs = workspaceDao.getWorkspaceById("ws-300")
            assertNotNull(deletedWs)
            assertEquals("PENDING_DELETE", deletedWs.sync.syncStatus)
            assertEquals(1757180000000L, deletedWs.sync.deletedAtEpochMillis)
            assertEquals(5L, deletedWs.sync.baseVersion)

            // Outbox kontrolü
            val readyOps = operationDao.getReadyOperations(nowEpochMillis = 1757180000000L, limit = 10)
            assertEquals(1, readyOps.size)
            val op = readyOps.first()
            assertEquals("DELETE", op.operationTypeCode)
            assertEquals("ws-300", op.entityId)
            assertEquals(5L, op.baseVersion)

            val json = Json { ignoreUnknownKeys = false }
            val payloadObj = json.parseToJsonElement(op.payloadJson!!).jsonObject
            assertEquals(setOf("id"), payloadObj.keys)
            assertEquals("ws-300", payloadObj["id"]?.jsonPrimitive?.content)
        } finally {
            database.close()
        }
    }

    @Test
    fun rejects_update_or_delete_when_actor_is_not_permitted_without_side_effects() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            // Aktif kullanıcı viewer olsun
            authRepo.setSession(
                AuthSession(
                    userId = EntityId("user-viewer"),
                    email = "viewer@example.com",
                    expiresAt = Instant.fromEpochMilliseconds(2000000000000L),
                ),
            )

            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1757190000000L },
            )

            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-400",
                    name = "Korumalı Alan",
                    normalizedName = "korumalı alan",
                    ownerId = "user-owner",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 2L,
                        baseVersion = 2L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-400",
                    userId = "user-owner",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-400",
                    userId = "user-viewer",
                    roleCode = "VIEWER",
                    joinedAtEpochMillis = 1757150000000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1757150000000L,
                        localUpdatedAtEpochMillis = 1757150000000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            // UPDATE denemesi -> Viewer yetkisizdir
            val updateResult = repo.updateWorkspace(
                UpdateWorkspaceCommand(
                    id = EntityId("ws-400"),
                    name = "Değiştirilemez",
                    type = WorkspaceType.SHARED,
                    currency = Currency.TRY,
                ),
            )
            assertTrue(updateResult is RepositoryResult.Failure)
            assertEquals("actor_not_permitted", (updateResult.error as AppError.Authentication).code)

            // DELETE denemesi -> Viewer yetkisizdir
            val deleteResult = repo.deleteWorkspace(EntityId("ws-400"))
            assertTrue(deleteResult is RepositoryResult.Failure)
            assertEquals("actor_not_permitted", (deleteResult.error as AppError.Authentication).code)

            // Hiçbir outbox kaydı veya yerel değişiklik oluşmamalı
            val readyOps = operationDao.getReadyOperations(nowEpochMillis = 1757190000000L, limit = 10)
            assertEquals(0, readyOps.size)

            val ws = workspaceDao.getWorkspaceById("ws-400")
            assertEquals("Korumalı Alan", ws?.name)
            assertEquals("SYNCED", ws?.sync?.syncStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun observeWorkspaces_presents_room_ssot_and_filters_deleted_and_other_users() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
            )

            // Workspace 1: Aktif kullanıcı üye (SYNCED)
            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-active-1",
                    name = "Aktif Alan A",
                    normalizedName = "aktif alan a",
                    ownerId = "user-1",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-active-1",
                    userId = "user-1",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            // Workspace 2: Silinmiş (tombstone)
            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-deleted",
                    name = "Silinmiş Alan",
                    normalizedName = "silinmiş alan",
                    ownerId = "user-1",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "PENDING_DELETE",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = 2000L,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-deleted",
                    userId = "user-1",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "PENDING_DELETE",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            // Workspace 3: Başka kullanıcının alanı
            workspaceDao.upsertWorkspace(
                WorkspaceEntity(
                    id = "ws-other-user",
                    name = "Başkası",
                    normalizedName = "başkası",
                    ownerId = "user-2",
                    typeCode = "shared",
                    currencyCode = "TRY",
                    description = null,
                    createdAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    workspaceId = "ws-other-user",
                    userId = "user-2",
                    roleCode = "OWNER",
                    joinedAtEpochMillis = 1000L,
                    sync = SyncMetadata(
                        syncStatus = "SYNCED",
                        updatedAtEpochMillis = 1000L,
                        localUpdatedAtEpochMillis = 1000L,
                        deletedAtEpochMillis = null,
                        version = 1L,
                        baseVersion = 1L,
                        lastSyncError = null,
                    ),
                ),
            )

            val list = repo.observeWorkspaces().first()
            assertEquals(1, list.size)
            assertEquals("ws-active-1", list.first().id.value)
            assertEquals("Aktif Alan A", list.first().name)
        } finally {
            database.close()
        }
    }

    @Test
    fun pending_create_workspace_updates_by_coalescing_into_single_create_outbox_op() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                entityIdGenerator = EntityIdGenerator { EntityId("ws-coalesce") },
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1000L },
            )

            // 1. CREATE işlemi
            val createResult = repo.createWorkspace(
                CreateWorkspaceCommand(
                    name = "İlk İsim",
                    type = WorkspaceType.SHARED,
                    currency = Currency.TRY,
                    description = "İlk açıklama",
                ),
            )
            assertTrue(createResult is RepositoryResult.Success)

            val initialOps = operationDao.getReadyOperations(nowEpochMillis = 1000L, limit = 10)
            assertEquals(1, initialOps.size)
            val initialOpId = initialOps.first().operationId
            assertEquals("CREATE", initialOps.first().operationTypeCode)
            assertNull(initialOps.first().baseVersion)

            // 2. Henüz gönderilmemiş PENDING_CREATE workspace üzerinde UPDATE
            val updateResult = repo.updateWorkspace(
                UpdateWorkspaceCommand(
                    id = EntityId("ws-coalesce"),
                    name = "Güncellenmiş İsim 🚀",
                    type = WorkspaceType.PERSONAL,
                    currency = Currency.USD,
                    description = "Yeni açıklama",
                ),
            )
            assertTrue(updateResult is RepositoryResult.Success)

            // Outbox kontrolü: Hâlâ tek bir CREATE operasyonu olmalı (coalesce edildi)
            val pendingOps = operationDao.getReadyOperations(nowEpochMillis = 2000L, limit = 10)
            assertEquals(1, pendingOps.size)
            val coalescedOp = pendingOps.first()
            assertEquals(initialOpId, coalescedOp.operationId)
            assertEquals("CREATE", coalescedOp.operationTypeCode)
            assertNull(coalescedOp.baseVersion)

            val json = Json { ignoreUnknownKeys = false }
            val payloadObj = json.parseToJsonElement(coalescedOp.payloadJson!!).jsonObject
            assertEquals("ws-coalesce", payloadObj["id"]?.jsonPrimitive?.content)
            assertEquals("Güncellenmiş İsim 🚀", payloadObj["name"]?.jsonPrimitive?.content)
            assertEquals("personal", payloadObj["type_code"]?.jsonPrimitive?.content)
            assertEquals("USD", payloadObj["currency_code"]?.jsonPrimitive?.content)
            assertEquals("Yeni açıklama", payloadObj["description"]?.jsonPrimitive?.content)

            // Room SSOT kontrolü
            val wsEntity = workspaceDao.getWorkspaceById("ws-coalesce")
            assertNotNull(wsEntity)
            assertEquals("Güncellenmiş İsim 🚀", wsEntity.name)
            assertEquals("personal", wsEntity.typeCode)
            assertEquals("USD", wsEntity.currencyCode)
            assertEquals("Yeni açıklama", wsEntity.description)
            assertEquals("PENDING_CREATE", wsEntity.sync.syncStatus)
            assertNull(wsEntity.sync.baseVersion)
        } finally {
            database.close()
        }
    }

    @Test
    fun pending_create_workspace_deletes_by_hard_deleting_workspace_member_and_outbox() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()
            val operationDao = database.syncOperationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
                entityIdGenerator = EntityIdGenerator { EntityId("ws-hard-delete") },
                operationIdFactory = ::testOpIdFactory,
                nowEpochMillisProvider = { 1000L },
            )

            // 1. CREATE işlemi
            val createResult = repo.createWorkspace(
                CreateWorkspaceCommand(
                    name = "Vazgeçilecek Alan",
                    type = WorkspaceType.SHARED,
                    currency = Currency.TRY,
                ),
            )
            assertTrue(createResult is RepositoryResult.Success)

            val initialOps = operationDao.getReadyOperations(nowEpochMillis = 1000L, limit = 10)
            assertEquals(1, initialOps.size)

            // 2. Henüz gönderilmemiş PENDING_CREATE workspace üzerinde DELETE -> HARD_DELETE olmalı
            val deleteResult = repo.deleteWorkspace(EntityId("ws-hard-delete"))
            assertTrue(deleteResult is RepositoryResult.Success)

            // Outbox sıfırlanmış olmalı
            val pendingOps = operationDao.getReadyOperations(nowEpochMillis = 2000L, limit = 10)
            assertEquals(0, pendingOps.size)

            // Workspace ve Member Room'dan tamamen silinmiş olmalı
            assertNull(workspaceDao.getWorkspaceById("ws-hard-delete"))
            val members = workspaceDao.observeMembers("ws-hard-delete").first()
            assertTrue(members.isEmpty())
            val userWorkspaces = repo.observeWorkspaces().first()
            assertTrue(userWorkspaces.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun setActive_fails_closed_in_this_phase() = runTest {
        val database = inMemoryDatabase()
        try {
            val authRepo = FakeAuthRepository()
            val workspaceDao = database.workspaceDao()
            val localMutationDao = database.localMutationDao()

            val repo = OfflineFirstWorkspaceRepository(
                authRepository = authRepo,
                workspaceDao = workspaceDao,
                localMutationDao = localMutationDao,
            )

            val result = repo.setActive(EntityId("ws-100"))
            assertTrue(result is RepositoryResult.Failure)
            assertEquals("active_workspace_v2_not_implemented_in_this_phase", result.error.code)
        } finally {
            database.close()
        }
    }
}
