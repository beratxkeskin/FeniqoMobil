package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Uzak kaynaktan cursor sonrasında değişen Workspace ve ilgili WorkspaceMember kayıtlarını deterministik olarak çeker,
 * D2 mapper'larıyla Room entity'lerine dönüştürür ve tek bir atomik snapshot çağrısı (`applyWorkspaceSnapshot`) olarak uygular.
 *
 * Kurallar:
 * - Girdi: `SyncStateDao` üzerindeki kalıcı cursor'lar ("WORKSPACE" ve "WORKSPACE_MEMBER:<wsId>").
 * - Tombstone'ları filtrelemeden deterministik sayfalama ile çeker.
 * - Çekilen her workspace ve mevcut member cursor'ı bulunan her workspace için member delta sayfalarını çeker.
 * - DTO -> Room entity dönüşümü tamamlanmadan Room'a yazma yapılmaz.
 * - Fetch veya mapper hatasında Room DAO çağrılmaz ve cursor ilerletilmez (fail-closed).
 * - Snapshot ve yeni cursor'lar tek bir atomik Room transaction'ında kaydedilir.
 */
class WorkspaceIncrementalRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val syncStateDao: SyncStateDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    private val snapshotJson = Json { encodeDefaults = true; explicitNulls = true }
    /**
     * Kalıcı `SyncStateDao` cursor'larını okur, incremental remote pull gerçekleştirir ve
     * güncellenmiş cursor'ları snapshot ile atomik olarak kaydeder.
     */
    suspend fun pull(): WorkspaceIncrementalSyncResult {
        val storedWorkspaceCursorEntity = syncStateDao.getCursor(WorkspaceSyncCursorKeys.WORKSPACE_ENTITY_TYPE)
        val initialWorkspaceCursor = storedWorkspaceCursorEntity?.let(WorkspaceSyncCursorKeys::workspaceEntityToCursor)

        val storedMemberCursorEntities = syncStateDao.getWorkspaceMemberCursors()
        val initialMemberCursors = storedMemberCursorEntities.mapNotNull { entity ->
            WorkspaceSyncCursorKeys.workspaceMemberEntityToCursor(entity)?.let { cursor ->
                cursor.workspaceId to cursor
            }
        }.toMap()

        return pullInternal(
            workspaceCursor = initialWorkspaceCursor,
            memberCursors = initialMemberCursors,
            persistCursors = true,
        )
    }

    /**
     * Açık cursor'lar ile çekme yapan yardımcı fonksiyon (test ve iç kullanım için).
     */
    suspend fun pull(
        workspaceCursor: RemoteSyncCursor?,
        memberCursors: Map<String, WorkspaceMemberSyncCursor> = emptyMap(),
    ): WorkspaceIncrementalSyncResult = pullInternal(
        workspaceCursor = workspaceCursor,
        memberCursors = memberCursors,
        persistCursors = true,
    )

    private suspend fun pullInternal(
        workspaceCursor: RemoteSyncCursor?,
        memberCursors: Map<String, WorkspaceMemberSyncCursor>,
        persistCursors: Boolean,
    ): WorkspaceIncrementalSyncResult {
        val receivedAt = nowEpochMillisProvider()

        // 1. Workspace sayfalarını deterministik olarak çek
        val workspacePull = fetchWorkspaces(workspaceCursor)
        val workspaceDtos = workspacePull.items
        val nextWorkspaceCursor = workspacePull.nextCursor

        // 2. Çekilen workspace'ler, mevcut member cursor'ı bulunan workspace'ler ve local bilinen canlı workspace'lerin birleşimini lexicographic stabil sırada işle
        val knownLiveLocalWsIds = remoteSyncDao.getAllKnownLiveWorkspaceIds()
        val memberPullTargetIds = (workspaceDtos.map { it.id } + memberCursors.keys + knownLiveLocalWsIds)
            .distinct()
            .sorted()

        val memberDtos = mutableListOf<WorkspaceMemberDto>()
        val nextMemberCursors = memberCursors.toMutableMap()

        // Çekilen workspace'lerin tombstone durumlarını hızlı arama için eşle
        val fetchedWsMap = workspaceDtos.associateBy { it.id }

        for (wsId in memberPullTargetIds) {
            val currentMemberCursor = memberCursors[wsId]
            val memberPull = fetchWorkspaceMembers(wsId, currentMemberCursor)
            
            // Eğer member cursor'ı olmayan bir workspace için tam bootstrap (updatedAfter = null) yapıldıysa
            // ve workspace canlıysa (uzakta veya yerelde silinmemişse), D1 OWNER sözleşmesine göre boş üye listesi fail-closed hatadır.
            if (currentMemberCursor == null) {
                val fetchedWs = fetchedWsMap[wsId]
                val isLive = if (fetchedWs != null) {
                    fetchedWs.deletedAt == null
                } else {
                    knownLiveLocalWsIds.contains(wsId)
                }
                if (isLive && memberPull.items.isEmpty()) {
                    error("Canlı workspace ($wsId) için üye bootstrap listesi boş dönemez.")
                }
            }

            memberDtos += memberPull.items
            if (memberPull.nextCursor != null) {
                nextMemberCursors[wsId] = memberPull.nextCursor
            }
        }

        // 3. DTO'ları D2 mapper'ları ile Room entity'lerine dönüştür (tombstone'lar dahil)
        val memberEntities = memberDtos.map { it.toEntity(receivedAt) }

        // 4. Güncellenen / ilerleyen cursor'ları üret
        val cursorEntitiesToPersist = if (persistCursors) {
            val cursors = mutableListOf<SyncCursorEntity>()
            if (nextWorkspaceCursor != null) {
                cursors += WorkspaceSyncCursorKeys.workspaceCursorToEntity(nextWorkspaceCursor)
            }
            for ((_, memberCursor) in nextMemberCursors) {
                cursors += WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(memberCursor)
            }
            cursors
        } else {
            emptyList()
        }

        // 5. In-memory karar matrisi ve plan oluştur
        val applyItems = mutableListOf<com.feniqo.mobile.data.local.dao.WorkspaceApplyItem>()
        val conflictItems = mutableListOf<com.feniqo.mobile.data.local.dao.WorkspaceConflictItem>()
        val preserveItems = mutableListOf<com.feniqo.mobile.data.local.dao.WorkspacePreserveItem>()

        for (dto in workspaceDtos) {
            val localWs = remoteSyncDao.getWorkspaceRow(dto.id)
            val localOp = remoteSyncDao.getActiveWorkspaceTailOperation(dto.id)

            if (localWs == null) {
                // Yerelde yok -> APPLY
                val precondition = com.feniqo.mobile.data.local.dao.WorkspacePrecondition(
                    expectedPresence = false,
                    expectedActiveOperationId = null,
                )
                applyItems += com.feniqo.mobile.data.local.dao.WorkspaceApplyItem(
                    entity = dto.toEntity(receivedAt),
                    precondition = precondition,
                )
            } else if (localWs.sync.syncStatus == "SYNCED") {
                val precondition = com.feniqo.mobile.data.local.dao.WorkspacePrecondition(
                    expectedPresence = true,
                    expectedSyncStatus = "SYNCED",
                    expectedVersion = localWs.sync.version,
                    expectedBaseVersion = localWs.sync.baseVersion,
                    expectedDeletedAtEpochMillis = localWs.sync.deletedAtEpochMillis,
                    expectedLocalUpdatedAtEpochMillis = localWs.sync.localUpdatedAtEpochMillis,
                    expectedActiveOperationId = null,
                    expectedOperationUpdatedAtEpochMillis = null,
                )
                if (dto.version > localWs.sync.version) {
                    applyItems += com.feniqo.mobile.data.local.dao.WorkspaceApplyItem(
                        entity = dto.toEntity(receivedAt),
                        precondition = precondition,
                    )
                } else {
                    preserveItems += com.feniqo.mobile.data.local.dao.WorkspacePreserveItem(
                        workspaceId = dto.id,
                        precondition = precondition,
                    )
                }
            } else {
                // Yerel non-SYNCED (PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, IN_FLIGHT, FAILED, CONFLICT)
                val tailOp = requireNotNull(localOp) {
                    "Pending yerel workspace (${dto.id}) için aktif outbox işlemi bulunamadı."
                }
                val localPayload = requireNotNull(tailOp.payloadJson?.takeIf { it.isNotBlank() }) {
                    "Aktif outbox işlemi (${tailOp.operationId}) için payload_json bulunamadı veya boş."
                }

                val precondition = com.feniqo.mobile.data.local.dao.WorkspacePrecondition(
                    expectedPresence = true,
                    expectedSyncStatus = localWs.sync.syncStatus,
                    expectedVersion = localWs.sync.version,
                    expectedBaseVersion = localWs.sync.baseVersion,
                    expectedDeletedAtEpochMillis = localWs.sync.deletedAtEpochMillis,
                    expectedLocalUpdatedAtEpochMillis = localWs.sync.localUpdatedAtEpochMillis,
                    expectedActiveOperationId = tailOp.operationId,
                    expectedOperationUpdatedAtEpochMillis = tailOp.updatedAtEpochMillis,
                )

                val baseVersion = localWs.sync.baseVersion ?: 0L
                if (dto.version > baseVersion) {
                    // CONFLICT
                    val conflictEntity = com.feniqo.mobile.data.local.entity.SyncConflictEntity(
                        entityTypeCode = "WORKSPACE",
                        entityId = dto.id,
                        operationId = tailOp.operationId,
                        localVersion = localWs.sync.version,
                        remoteVersion = dto.version,
                        localPayloadJson = localPayload,
                        remotePayloadJson = snapshotJson.encodeToString(dto),
                        detectedAtEpochMillis = receivedAt,
                    )
                    conflictItems += com.feniqo.mobile.data.local.dao.WorkspaceConflictItem(
                        conflict = conflictEntity,
                        precondition = precondition,
                    )
                } else {
                    // PRESERVE
                    preserveItems += com.feniqo.mobile.data.local.dao.WorkspacePreserveItem(
                        workspaceId = dto.id,
                        precondition = precondition,
                    )
                }
            }
        }

        val plan = com.feniqo.mobile.data.local.dao.WorkspaceIncrementalPlan(
            applyItems = applyItems,
            conflictItems = conflictItems,
            preserveItems = preserveItems,
            memberRows = memberEntities,
            cursorsToPersist = cursorEntitiesToPersist,
        )

        // 6. Planı tek atomik transaction olarak Room SSOT'a uygula
        remoteSyncDao.applyWorkspaceIncrementalPlan(plan)

        return WorkspaceIncrementalSyncResult(
            appliedWorkspacesCount = applyItems.size,
            appliedMembersCount = memberEntities.size,
            conflictCount = conflictItems.size,
            nextWorkspaceCursor = nextWorkspaceCursor,
            nextMemberCursors = nextMemberCursors,
        )
    }

    private suspend fun fetchWorkspaces(
        initialCursor: RemoteSyncCursor?,
    ): EntityPullResult<WorkspaceDto, RemoteSyncCursor> {
        val result = mutableListOf<WorkspaceDto>()
        var request = RemotePageRequest(pageIndex = 0)
        var latestCursor: RemoteSyncCursor? = initialCursor

        do {
            val page = remote.fetchWorkspaces(
                WorkspaceRemoteQuery(
                    page = request,
                    updatedAfter = initialCursor,
                ),
            )
            result += page.items
            page.items.lastOrNull()?.let { lastDto ->
                latestCursor = RemoteSyncCursor(
                    updatedAt = lastDto.updatedAt,
                    entityId = lastDto.id,
                )
            }
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)

        return EntityPullResult(result, latestCursor)
    }

    private suspend fun fetchWorkspaceMembers(
        workspaceId: String,
        initialCursor: WorkspaceMemberSyncCursor?,
    ): EntityPullResult<WorkspaceMemberDto, WorkspaceMemberSyncCursor> {
        val result = mutableListOf<WorkspaceMemberDto>()
        var request = RemotePageRequest(pageIndex = 0)
        var latestCursor: WorkspaceMemberSyncCursor? = initialCursor

        do {
            val page = remote.fetchWorkspaceMembers(
                WorkspaceMemberRemoteQuery(
                    page = request,
                    workspaceId = EntityId(workspaceId),
                    updatedAfter = initialCursor,
                ),
            )
            result += page.items
            page.items.lastOrNull()?.let { lastDto ->
                latestCursor = WorkspaceMemberSyncCursor(
                    updatedAt = lastDto.updatedAt,
                    workspaceId = lastDto.workspaceId,
                    userId = lastDto.userId,
                )
            }
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)

        return EntityPullResult(result, latestCursor)
    }

    private data class EntityPullResult<T, C>(
        val items: List<T>,
        val nextCursor: C?,
    )
}

data class WorkspaceIncrementalSyncResult(
    val appliedWorkspacesCount: Int,
    val appliedMembersCount: Int,
    val conflictCount: Int = 0,
    val nextWorkspaceCursor: RemoteSyncCursor?,
    val nextMemberCursors: Map<String, WorkspaceMemberSyncCursor>,
)
