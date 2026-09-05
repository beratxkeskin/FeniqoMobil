package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberSyncCursor
import com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.domain.model.EntityId

/**
 * İlk senkronizasyonda (veya bağımsız bootstrap adımında) uzak kaynaktan tüm Workspace ve WorkspaceMember
 * sayfalarını deterministik olarak çeker, D2 mapper'larıyla entity'ye dönüştürür ve Room DAO'suna
 * tek bir atomik snapshot çağrısı olarak uygular.
 */
class WorkspaceInitialRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    suspend fun pull(): WorkspaceInitialSyncResult {
        val receivedAt = nowEpochMillisProvider()

        // 1. Tüm workspace sayfalarını deterministik olarak çek
        val workspaceDtos = fetchAllWorkspaces()

        // 2. Her çekilmiş workspace için tüm member sayfalarını çek
        val memberDtos = mutableListOf<WorkspaceMemberDto>()
        for (ws in workspaceDtos) {
            val membersForWs = fetchAllWorkspaceMembers(ws.id)
            // Canlı (deleted_at == null) workspace için boş member listesi D1 OWNER sözleşmesine aykırıdır; fail-closed reddet
            if (ws.deletedAt == null && membersForWs.isEmpty()) {
                error("Canlı workspace (${ws.id}) için uzak üye listesi boş dönemez.")
            }
            memberDtos += membersForWs
        }

        // 3. DTO'ları D2 mapper'ları ile Room entity'lerine dönüştür
        val workspaceEntities = workspaceDtos.map { it.toEntity(receivedAt) }
        val memberEntities = memberDtos.map { it.toEntity(receivedAt) }

        // 4. Çekilen kayıtlardan başlangıç cursor'larını üret ve her durumda bootstrap marker ekle
        val cursors = mutableListOf<SyncCursorEntity>()
        cursors += WorkspaceSyncCursorKeys.bootstrapCompleteEntity(receivedAt)

        workspaceDtos.lastOrNull()?.let { lastWs ->
            cursors += WorkspaceSyncCursorKeys.workspaceCursorToEntity(
                RemoteSyncCursor(updatedAt = lastWs.updatedAt, entityId = lastWs.id),
            )
        }

        // Workspace başına çekilen member kayıtlarından sonuncuyu bulup cursor ekle
        val membersByWs = memberDtos.groupBy { it.workspaceId }
        for ((wsId, members) in membersByWs) {
            members.lastOrNull()?.let { lastMember ->
                cursors += WorkspaceSyncCursorKeys.workspaceMemberCursorToEntity(
                    WorkspaceMemberSyncCursor(
                        updatedAt = lastMember.updatedAt,
                        workspaceId = wsId,
                        userId = lastMember.userId,
                    ),
                )
            }
        }

        // 5. Tüm fetch ve map işlemleri başarıyla tamamlandıktan sonra Room'a tek ve atomik snapshot olarak uygula
        remoteSyncDao.applyWorkspaceSnapshot(
            workspaces = workspaceEntities,
            members = memberEntities,
            cursors = cursors,
        )

        return WorkspaceInitialSyncResult(
            workspaceCount = workspaceEntities.size,
            memberCount = memberEntities.size,
        )
    }

    private suspend fun fetchAllWorkspaces(): List<WorkspaceDto> {
        val result = mutableListOf<WorkspaceDto>()
        var request = RemotePageRequest(pageIndex = 0)
        do {
            val page = remote.fetchWorkspaces(WorkspaceRemoteQuery(page = request))
            result += page.items
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)
        return result
    }

    private suspend fun fetchAllWorkspaceMembers(workspaceId: String): List<WorkspaceMemberDto> {
        val result = mutableListOf<WorkspaceMemberDto>()
        var request = RemotePageRequest(pageIndex = 0)
        do {
            val page = remote.fetchWorkspaceMembers(
                WorkspaceMemberRemoteQuery(
                    page = request,
                    workspaceId = EntityId(workspaceId),
                ),
            )
            result += page.items
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)
        return result
    }
}

data class WorkspaceInitialSyncResult(
    val workspaceCount: Int,
    val memberCount: Int,
)
