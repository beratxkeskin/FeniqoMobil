package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.model.WorkspaceRole
import kotlinx.coroutines.flow.Flow

/**
 * Aktif kullanıcının üyesi olduğu tüm canlı çalışma alanlarını
 * Room Single Source of Truth üzerinden alfabetik gözlemler.
 */
class ObserveWorkspacesUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    operator fun invoke(): Flow<List<Workspace>> = workspaceRepository.observeWorkspaces()
}

/**
 * Aktif oturum profilinin seçili canlı çalışma alanını
 * Room Single Source of Truth üzerinden gözlemler.
 * Kişisel modda veya seçim yoksa null yayar.
 */
class ObserveActiveWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    operator fun invoke(): Flow<Workspace?> = workspaceRepository.observeActiveWorkspace()
}

/**
 * Yeni çalışma alanı oluşturur, aktif kullanıcıyı OWNER olarak ekler
 * ve V2 outbox operasyonunu Room transaction içinde atomik kaydeder.
 */
class CreateWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(command: CreateWorkspaceCommand): RepositoryResult<EntityId> =
        workspaceRepository.createWorkspace(command)
}

/**
 * Mevcut çalışma alanının ad/tür/para birimi/açıklama bilgilerini günceller.
 */
class UpdateWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(command: UpdateWorkspaceCommand): RepositoryResult<Unit> =
        workspaceRepository.updateWorkspace(command)
}

/**
 * Çalışma alanını siler (henüz sunucuya gitmediyse hard-delete, senkronize olduysa soft-delete).
 */
class DeleteWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(id: EntityId): RepositoryResult<Unit> =
        workspaceRepository.deleteWorkspace(id)
}

/**
 * Aktif çalışma alanını yerel kullanıcı tercihi olarak atomik günceller veya temizler (null = kişisel mod).
 * Uzak profil veya outbox işlemi üretmez; salt yerel Room profil tercihidir.
 */
class SetActiveWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(workspaceId: EntityId?): RepositoryResult<Unit> =
        workspaceRepository.setActive(workspaceId)
}

/**
 * Davet kodu ile mevcut bir çalışma alanına katılır.
 */
class JoinWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> =
        workspaceRepository.join(inviteCode)
}

/**
 * Belirtilen çalışma alanının üyelerini Room Single Source of Truth üzerinden gözlemler.
 */
class ObserveWorkspaceMembersUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    operator fun invoke(workspaceId: EntityId): Flow<List<WorkspaceMember>> =
        workspaceRepository.observeMembers(workspaceId)
}

/**
 * Kullanıcının mevcut çalışma alanından ayrılmasını sağlar.
 * OWNER rolündeki kullanıcılar sahiplik devri yapılmadan ayrılamaz.
 */
class LeaveWorkspaceUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(workspaceId: EntityId): RepositoryResult<Unit> =
        workspaceRepository.leave(workspaceId)
}

/**
 * Çalışma alanı için davet kodu üretir.
 * Yalnızca OWNER rolündeki kullanıcılar davet oluşturabilir.
 */
class CreateWorkspaceInviteUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> =
        workspaceRepository.createInvite(workspaceId)
}

/**
 * Çalışma alanındaki bir üyenin rolünü değiştirir (EDITOR <-> VIEWER).
 * Yalnızca OWNER rolündeki kullanıcılar rol değiştirebilir; sahiplik devri bu use case kapsamı dışındadır.
 */
class ChangeWorkspaceMemberRoleUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(
        workspaceId: EntityId,
        userId: EntityId,
        role: WorkspaceRole,
    ): RepositoryResult<Unit> =
        workspaceRepository.changeMemberRole(workspaceId, userId, role)
}

/**
 * Çalışma alanının sahipliğini başka bir üyeye devreder.
 * Eski sahip EDITOR olur, hedef üye OWNER olur ve workspace ownerId hedef üye olur.
 * Atomik RPC ile yürütülür.
 */
class TransferWorkspaceOwnershipUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(
        workspaceId: EntityId,
        targetUserId: EntityId,
    ): RepositoryResult<Unit> =
        workspaceRepository.transferOwnership(workspaceId, targetUserId)
}

/**
 * Çalışma alanındaki bir EDITOR veya VIEWER üyeyi çalışma alanından çıkarır.
 * Yalnızca OWNER rolündeki kullanıcılar başka bir üyeyi çıkarabilir.
 * OWNER kendini veya başka bir OWNER'ı çıkaramaz; V2 outbox WORKSPACE_MEMBER DELETE ile yürütülür.
 */
class RemoveWorkspaceMemberUseCase(
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend operator fun invoke(
        workspaceId: EntityId,
        userId: EntityId,
    ): RepositoryResult<Unit> =
        workspaceRepository.removeMember(workspaceId, userId)
}
