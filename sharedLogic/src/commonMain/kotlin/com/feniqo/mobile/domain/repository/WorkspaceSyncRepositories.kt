package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class WorkspaceInviteCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Çalışma alanı davet kodu boş olamaz." }
    }
}

interface WorkspaceRepository {
    fun observeWorkspaces(): Flow<List<Workspace>>

    fun observeActiveWorkspace(): Flow<Workspace?>

    fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>>

    suspend fun create(name: String): RepositoryResult<EntityId>

    suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit>

    suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode>

    suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId>

    suspend fun changeMemberRole(
        workspaceId: EntityId,
        userId: EntityId,
        role: WorkspaceRole,
    ): RepositoryResult<Unit>

    suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit>
}

enum class SyncPhase {
    IDLE,
    SYNCING,
    OFFLINE,
    FAILED,
}

data class SyncOverview(
    val phase: SyncPhase,
    val pendingOperationCount: Int,
    val conflictCount: Int,
    val lastSuccessfulSyncAt: Instant?,
    val lastError: AppError?,
) {
    init {
        require(pendingOperationCount >= 0) { "Bekleyen senkronizasyon sayısı negatif olamaz." }
        require(conflictCount >= 0) { "Çakışma sayısı negatif olamaz." }
    }
}

enum class SyncEntityType {
    PROFILE,
    CATEGORY,
    TRANSACTION,
    BUDGET,
    WORKSPACE,
}

data class SyncConflict(
    val entityId: EntityId,
    val entityType: SyncEntityType,
    val localVersion: Long,
    val remoteVersion: Long,
) {
    init {
        require(localVersion >= 0 && remoteVersion >= 0) { "Senkronizasyon sürümü negatif olamaz." }
    }
}

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

interface SyncRepository {
    fun observeOverview(): Flow<SyncOverview>

    fun observeConflicts(): Flow<List<SyncConflict>>

    suspend fun requestSync(): RepositoryResult<Unit>

    suspend fun retryFailedOperations(): RepositoryResult<Unit>

    suspend fun resolveConflict(
        entityId: EntityId,
        resolution: ConflictResolution,
    ): RepositoryResult<Unit>
}
