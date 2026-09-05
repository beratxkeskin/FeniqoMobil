package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.normalizeForStorage
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.util.RandomUuidEntityIdGenerator
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.WorkspaceInviteCode
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.validation.WorkspacePermission
import com.feniqo.mobile.domain.validation.WorkspacePermissionPolicy
import com.feniqo.mobile.domain.validation.WorkspaceValidationError
import com.feniqo.mobile.domain.validation.WorkspaceValidationResult
import com.feniqo.mobile.domain.validation.WorkspaceValidationRules
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Offline-first Workspace repository.
 * Workspace metadata mutasyonlarını atomik olarak Room'a yazar ve V2 outbox'a ekler.
 * Workspace gözlem okumaları tek doğruluk kaynağı (SSOT) olan Room üzerinden sunulur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstWorkspaceRepository(
    private val authRepository: AuthRepository,
    private val workspaceDao: WorkspaceDao,
    private val localMutationDao: LocalMutationDao,
    private val profileDao: ProfileDao? = null,
    private val entityIdGenerator: EntityIdGenerator = RandomUuidEntityIdGenerator(),
    private val operationIdFactory: () -> String = ::newRepositoryOperationId,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : WorkspaceRepository {

    override fun observeWorkspaces(): Flow<List<Workspace>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                workspaceDao.observeForUser(session.userId.value).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
        }
    }

    override fun observeActiveWorkspace(): Flow<Workspace?> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                workspaceDao.observeActive(session.userId.value).map { it?.toDomain() }
            }
        }
    }

    override fun observeMembers(workspaceId: EntityId): Flow<List<WorkspaceMember>> {
        return workspaceDao.observeMembers(workspaceId.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun create(name: String): RepositoryResult<EntityId> {
        val validation = WorkspaceValidationRules.validateCreateWorkspace(
            name = name,
            type = WorkspaceType.SHARED,
            currency = Currency.TRY,
            description = null,
        )
        val command = when (validation) {
            is WorkspaceValidationResult.Valid -> validation.value
            is WorkspaceValidationResult.Invalid -> return RepositoryResult.Failure(validation.error.toAppError())
        }
        return createWorkspace(command)
    }

    override suspend fun createWorkspace(command: CreateWorkspaceCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val validation = WorkspaceValidationRules.validateCreateWorkspace(
                name = command.name,
                type = command.type,
                currency = command.currency,
                description = command.description,
            )
            val validCommand = when (validation) {
                is WorkspaceValidationResult.Valid -> validation.value
                is WorkspaceValidationResult.Invalid -> return RepositoryResult.Failure(validation.error.toAppError())
            }

            val now = nowEpochMillisProvider()
            val workspaceId = entityIdGenerator.nextId().value

            val workspaceEntity = WorkspaceEntity(
                id = workspaceId,
                name = validCommand.name,
                normalizedName = validCommand.name.normalizeForStorage(),
                ownerId = session.userId.value,
                typeCode = validCommand.type.name.lowercase(),
                currencyCode = validCommand.currency.code,
                description = validCommand.description,
                createdAtEpochMillis = now,
                sync = newSyncMetadata(now),
            )

            val ownerMemberEntity = WorkspaceMemberEntity(
                workspaceId = workspaceId,
                userId = session.userId.value,
                roleCode = WorkspaceRole.OWNER.name,
                joinedAtEpochMillis = now,
                sync = newSyncMetadata(now),
            )

            val payloadJson = WorkspacePayloadCodec.encode(
                entity = workspaceEntity,
                operationType = OutboxOperationType.CREATE,
                includeCreatedAtInCreate = true,
            )

            localMutationDao.mutateWorkspaceV2(
                entity = workspaceEntity,
                members = listOf(ownerMemberEntity),
                type = OutboxOperationType.CREATE,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = now,
            )

            return RepositoryResult.Success(EntityId(workspaceId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun updateWorkspace(command: UpdateWorkspaceCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val existing = workspaceDao.getWorkspaceById(command.id.value)
            if (existing == null || existing.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val members = workspaceDao.observeMembers(command.id.value).first()
            val memberPairs = members.map { EntityId(it.userId) to WorkspaceRole.valueOf(it.roleCode) }

            val validation = WorkspaceValidationRules.validateUpdateWorkspace(
                id = command.id,
                actorUserId = session.userId,
                name = command.name,
                type = command.type,
                currency = command.currency,
                description = command.description,
                currentMembers = memberPairs,
            )
            val validCommand = when (validation) {
                is WorkspaceValidationResult.Valid -> validation.value
                is WorkspaceValidationResult.Invalid -> return RepositoryResult.Failure(validation.error.toAppError())
            }

            val now = nowEpochMillisProvider()
            val isPendingCreate = existing.sync.syncStatus == SyncStatus.PENDING_CREATE.name

            if (isPendingCreate) {
                // Sunucuya henüz gitmemiş CREATE durumundaki workspace: CREATE payload ile coalesce edilir
                val updatedEntity = existing.copy(
                    name = validCommand.name,
                    normalizedName = validCommand.name.normalizeForStorage(),
                    typeCode = validCommand.type.name.lowercase(),
                    currencyCode = validCommand.currency.code,
                    description = validCommand.description,
                    sync = existing.sync.copy(
                        localUpdatedAtEpochMillis = now,
                    ),
                )

                val payloadJson = WorkspacePayloadCodec.encode(
                    entity = updatedEntity,
                    operationType = OutboxOperationType.CREATE,
                    includeCreatedAtInCreate = true,
                )

                localMutationDao.mutateWorkspaceV2(
                    entity = updatedEntity,
                    members = emptyList(),
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = now,
                )
            } else {
                val updatedSync = existing.sync.toPendingUpdate(now)
                val targetBaseVersion = if (existing.sync.baseVersion != null) {
                    existing.sync.baseVersion
                } else if (existing.sync.version > 0L) {
                    existing.sync.version
                } else {
                    null
                }
                val syncWithBaseVersion = updatedSync.copy(baseVersion = targetBaseVersion)

                val updatedEntity = existing.copy(
                    name = validCommand.name,
                    normalizedName = validCommand.name.normalizeForStorage(),
                    typeCode = validCommand.type.name.lowercase(),
                    currencyCode = validCommand.currency.code,
                    description = validCommand.description,
                    sync = syncWithBaseVersion,
                )

                val payloadJson = WorkspacePayloadCodec.encode(
                    entity = updatedEntity,
                    operationType = OutboxOperationType.UPDATE,
                )

                localMutationDao.mutateWorkspaceV2(
                    entity = updatedEntity,
                    members = emptyList(),
                    type = OutboxOperationType.UPDATE,
                    payloadJson = payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = now,
                )
            }

            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun deleteWorkspace(id: EntityId): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val existing = workspaceDao.getWorkspaceById(id.value)
            if (existing == null || existing.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val members = workspaceDao.observeMembers(id.value).first()
            val actorMember = members.firstOrNull { it.userId == session.userId.value }
                ?: return RepositoryResult.Failure(WorkspaceValidationError.ACTOR_NOT_MEMBER.toAppError())

            val actorRole = WorkspaceRole.valueOf(actorMember.roleCode)
            if (!WorkspacePermissionPolicy.can(actorRole, WorkspacePermission.DELETE_WORKSPACE)) {
                return RepositoryResult.Failure(WorkspaceValidationError.ACTOR_NOT_PERMITTED.toAppError())
            }

            val now = nowEpochMillisProvider()
            val isPendingCreate = existing.sync.syncStatus == SyncStatus.PENDING_CREATE.name

            if (isPendingCreate) {
                // Sunucuya hiç gönderilmemiş PENDING CREATE workspace: HARD_DELETED yolunu kullanır
                val deletedSync = existing.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = now,
                    localUpdatedAtEpochMillis = now,
                )
                val deletedEntity = existing.copy(sync = deletedSync)
                val payloadJson = WorkspacePayloadCodec.encodePendingCreateHardDeletePayload(existing.id)

                localMutationDao.mutateWorkspaceV2(
                    entity = deletedEntity,
                    members = emptyList(),
                    type = OutboxOperationType.DELETE,
                    payloadJson = payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = now,
                )
            } else {
                val deletedSync = existing.sync.toPendingDelete(now)
                val targetBaseVersion = if (existing.sync.baseVersion != null) {
                    existing.sync.baseVersion
                } else if (existing.sync.version > 0L) {
                    existing.sync.version
                } else {
                    null
                }
                val syncWithBaseVersion = deletedSync.copy(baseVersion = targetBaseVersion)

                val deletedEntity = existing.copy(
                    sync = syncWithBaseVersion,
                )

                val payloadJson = WorkspacePayloadCodec.encode(
                    entity = deletedEntity,
                    operationType = OutboxOperationType.DELETE,
                )

                localMutationDao.mutateWorkspaceV2(
                    entity = deletedEntity,
                    members = emptyList(),
                    type = OutboxOperationType.DELETE,
                    payloadJson = payloadJson,
                    operationIdFactory = operationIdFactory,
                    nowEpochMillis = now,
                )
            }

            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun setActive(workspaceId: EntityId?): RepositoryResult<Unit> {
        return RepositoryResult.Failure(AppError.Validation("active_workspace_v2_not_implemented_in_this_phase"))
    }

    override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> {
        return RepositoryResult.Failure(AppError.Validation("invitations_v2_not_implemented_in_this_phase"))
    }

    override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> {
        return RepositoryResult.Failure(AppError.Validation("invitations_v2_not_implemented_in_this_phase"))
    }

    override suspend fun changeMemberRole(
        workspaceId: EntityId,
        userId: EntityId,
        role: WorkspaceRole,
    ): RepositoryResult<Unit> {
        return RepositoryResult.Failure(AppError.Validation("member_roles_v2_not_implemented_in_this_phase"))
    }

    override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> {
        return RepositoryResult.Failure(AppError.Validation("leave_workspace_v2_not_implemented_in_this_phase"))
    }
}

private fun WorkspaceValidationError.toAppError(): AppError = when (this) {
    WorkspaceValidationError.ACTOR_NOT_MEMBER -> AppError.Authentication("actor_not_member")
    WorkspaceValidationError.ACTOR_NOT_PERMITTED -> AppError.Authentication("actor_not_permitted")
    WorkspaceValidationError.NAME_BLANK -> AppError.Validation("name_blank")
    WorkspaceValidationError.INVITATION_ROLE_CANNOT_BE_OWNER -> AppError.Validation("invitation_role_cannot_be_owner")
    WorkspaceValidationError.INVITATION_MAX_USES_NON_POSITIVE -> AppError.Validation("invitation_max_uses_non_positive")
    WorkspaceValidationError.INVITATION_EXPIRES_AT_MUST_BE_AFTER_CREATED_AT -> AppError.Validation("invitation_expires_at_invalid")
    WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE -> AppError.Validation("cannot_change_own_role")
    WorkspaceValidationError.OWNER_ROLE_CHANGE_REQUIRES_TRANSFER -> AppError.Validation("owner_role_change_requires_transfer")
    WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND -> AppError.Validation("target_member_not_found")
    WorkspaceValidationError.CANNOT_REMOVE_LAST_OWNER -> AppError.Validation("cannot_remove_last_owner")
    WorkspaceValidationError.CANNOT_LEAVE_AS_LAST_OWNER -> AppError.Validation("cannot_leave_as_last_owner")
    WorkspaceValidationError.TRANSFER_ACTOR_NOT_OWNER -> AppError.Authentication("transfer_actor_not_owner")
    WorkspaceValidationError.TRANSFER_TARGET_ALREADY_OWNER -> AppError.Validation("transfer_target_already_owner")
}

private fun newRepositoryOperationId(): String = buildString(capacity = 32) {
    Random.Default.nextBytes(16).forEach { byte ->
        append(HEX_DIGITS[(byte.toInt() ushr 4) and 0x0F])
        append(HEX_DIGITS[byte.toInt() and 0x0F])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
