package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed scope for finance data.  The selected workspace is a local profile
 * preference, so this deliberately has no remote dependency and is the only
 * source used by repositories when selecting a finance scope.
 */
interface ActiveWorkspaceScope {
    fun observe(profileId: EntityId): Flow<EntityId?>
    suspend fun current(profileId: EntityId): EntityId?
}

class RoomActiveWorkspaceScope(
    private val workspaceDao: WorkspaceDao,
) : ActiveWorkspaceScope {
    override fun observe(profileId: EntityId): Flow<EntityId?> =
        workspaceDao.observeActive(profileId.value).map { workspace -> workspace?.id?.let(::EntityId) }

    override suspend fun current(profileId: EntityId): EntityId? = observe(profileId).first()
}

/** Test-only/default personal mode. Production wiring always supplies RoomActiveWorkspaceScope. */
object PersonalActiveWorkspaceScope : ActiveWorkspaceScope {
    override fun observe(profileId: EntityId): Flow<EntityId?> = flowOf(null)
    override suspend fun current(profileId: EntityId): EntityId? = null
}
