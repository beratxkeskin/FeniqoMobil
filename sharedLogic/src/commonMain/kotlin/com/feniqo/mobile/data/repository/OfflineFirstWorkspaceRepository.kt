package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.data.local.dao.WorkspaceOwnershipTransferPreconditionException
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceInvitationEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.normalizeForStorage
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.codec.WorkspaceMembershipPayloadCodec
import com.feniqo.mobile.data.remote.codec.WorkspacePayloadCodec
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.mapper.toEntity
import com.feniqo.mobile.data.util.RandomUuidEntityIdGenerator
import com.feniqo.mobile.data.util.WorkspaceInvitationCrypto
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
import kotlinx.datetime.Instant
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
    private val remoteDataSource: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
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
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val dao = profileDao ?: return RepositoryResult.Failure(AppError.Storage("profile_dao_missing"))

            val rowsAffected = if (workspaceId != null) {
                dao.setActiveWorkspaceGuarded(
                    profileId = session.userId.value,
                    workspaceId = workspaceId.value,
                )
            } else {
                dao.clearActiveWorkspace(
                    profileId = session.userId.value,
                )
            }

            return if (rowsAffected > 0) {
                RepositoryResult.Success(Unit)
            } else {
                RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun createInvite(workspaceId: EntityId): RepositoryResult<WorkspaceInviteCode> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val workspace = workspaceDao.getWorkspaceById(workspaceId.value)
            if (workspace == null || workspace.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val members = workspaceDao.observeMembers(workspaceId.value).first()
            val memberPairs = members.map { EntityId(it.userId) to WorkspaceRole.valueOf(it.roleCode) }

            val now = nowEpochMillisProvider()
            val createdAt = Instant.fromEpochMilliseconds(now)
            val expiresAtMillis = now + (7L * 24L * 60L * 60L * 1000L)
            val expiresAt = Instant.fromEpochMilliseconds(expiresAtMillis)
            val defaultRole = WorkspaceRole.EDITOR
            val defaultMaxUses = 10

            val validation = WorkspaceValidationRules.validateInvitationParams(
                actorUserId = session.userId,
                targetRole = defaultRole,
                createdAt = createdAt,
                expiresAt = expiresAt,
                maxUses = defaultMaxUses,
                currentMembers = memberPairs,
            )
            when (validation) {
                is WorkspaceValidationResult.Valid -> Unit
                is WorkspaceValidationResult.Invalid -> return RepositoryResult.Failure(validation.error.toAppError())
            }

            val rawToken = WorkspaceInvitationCrypto.generateSecureInvitationToken()
            val tokenHash = WorkspaceInvitationCrypto.hashInvitationToken(rawToken)

            val invitationId = entityIdGenerator.nextId().value

            val invitationEntity = WorkspaceInvitationEntity(
                id = invitationId,
                workspaceId = workspaceId.value,
                inviterId = session.userId.value,
                tokenHash = tokenHash,
                roleCode = defaultRole.name,
                createdAtEpochMillis = now,
                expiresAtEpochMillis = expiresAtMillis,
                maxUses = defaultMaxUses,
                usesCount = 0,
                sync = newSyncMetadata(now),
            )

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeInvitationCreate(
                id = invitationId,
                workspaceId = workspaceId.value,
                tokenHash = tokenHash,
                roleCode = defaultRole.name,
                expiresAtIso = expiresAt.toString(),
                maxUses = defaultMaxUses,
                createdAtIso = createdAt.toString(),
            )

            localMutationDao.mutateWorkspaceInvitationCreateV2(
                entity = invitationEntity,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = now,
            )

            return RepositoryResult.Success(WorkspaceInviteCode(rawToken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun join(inviteCode: WorkspaceInviteCode): RepositoryResult<EntityId> {
        val rawToken = inviteCode.value.trim()
        if (rawToken.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("workspace_invitation_code_blank"))
        }

        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        return try {
            val resultDto = remoteDataSource.redeemWorkspaceInvitation(rawToken)

            // RPC response token veya token_hash alanı içeriyorsa fail-closed reddet
            val dtoDump = resultDto.toString()
            if (dtoDump.contains("token_hash", ignoreCase = true) || dtoDump.contains(rawToken)) {
                return RepositoryResult.Failure(AppError.Validation("token_leaked_in_response"))
            }

            val now = nowEpochMillisProvider()
            val workspaceEntity = resultDto.workspace.toEntity(now)
            val memberEntity = resultDto.member.toEntity(now)

            // Strict DTO ve kimlik doğrulama kontrolleri
            if (memberEntity.workspaceId != workspaceEntity.id) {
                return RepositoryResult.Failure(AppError.Validation("member_workspace_mismatch"))
            }
            if (memberEntity.userId != session.userId.value) {
                return RepositoryResult.Failure(AppError.Validation("member_user_mismatch"))
            }
            if (memberEntity.sync.deletedAtEpochMillis != null || resultDto.member.deletedAt != null) {
                return RepositoryResult.Failure(AppError.Validation("member_tombstone"))
            }
            if (workspaceEntity.sync.deletedAtEpochMillis != null || resultDto.workspace.deletedAt != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_tombstone"))
            }

            // Yerel kayıt kontrolü: SYNCED olmayan yerel kayıt varsa snapshot overwrite etmemeli
            val existingWs = workspaceDao.getWorkspaceById(workspaceEntity.id)
            if (existingWs != null && existingWs.sync.syncStatus != SyncStatus.SYNCED.name) {
                return RepositoryResult.Failure(AppError.Conflict("workspace_local_mutation_conflict"))
            }

            val existingMember = remoteSyncDao.getWorkspaceMemberRow(workspaceEntity.id, session.userId.value)
            if (existingMember != null && existingMember.sync.syncStatus != SyncStatus.SYNCED.name) {
                return RepositoryResult.Failure(AppError.Conflict("workspace_member_local_mutation_conflict"))
            }

            // Atomik Room transaction'ı ile outbox ve mutation üretmeksizin snapshot uygula
            remoteSyncDao.applyRedeemedWorkspaceMembershipSnapshot(workspaceEntity, memberEntity)

            RepositoryResult.Success(EntityId(workspaceEntity.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            RepositoryResult.Failure(e.toJoinAppError())
        }
    }

    override suspend fun changeMemberRole(
        workspaceId: EntityId,
        userId: EntityId,
        role: WorkspaceRole,
    ): RepositoryResult<Unit> {
        return try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val actorId = session.userId

            val workspace = workspaceDao.getWorkspaceById(workspaceId.value)
            if (workspace == null || workspace.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val activeMembers = workspaceDao.observeMembers(workspaceId.value).first()
            val memberPairs = activeMembers.map { EntityId(it.userId) to it.roleCode.toWorkspaceRole() }

            val validation = WorkspaceValidationRules.validateMemberRoleChange(
                actorUserId = actorId,
                targetUserId = userId,
                newRole = role,
                currentMembers = memberPairs,
            )
            if (validation is WorkspaceValidationResult.Invalid) {
                return RepositoryResult.Failure(validation.error.toAppError())
            }

            val existingMember = activeMembers.firstOrNull { it.userId == userId.value }
                ?: return RepositoryResult.Failure(AppError.Validation("target_member_not_found"))

            val baseVersion = existingMember.sync.baseVersion ?: existingMember.sync.version
            if (baseVersion <= 0L) {
                return RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))
            }

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(
                workspaceId = workspaceId.value,
                userId = userId.value,
                roleCode = role.name,
            )

            val now = nowEpochMillisProvider()
            val updatedMember = existingMember.copy(
                roleCode = role.name,
                sync = existingMember.sync.copy(
                    syncStatus = SyncStatus.PENDING_UPDATE.name,
                    localUpdatedAtEpochMillis = now,
                    baseVersion = baseVersion,
                ),
            )

            localMutationDao.mutateWorkspaceMemberRoleV2(
                entity = updatedMember,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = now,
            )

            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun leave(workspaceId: EntityId): RepositoryResult<Unit> {
        return try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val actorId = session.userId

            val workspace = workspaceDao.getWorkspaceById(workspaceId.value)
            if (workspace == null || workspace.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val activeMembers = workspaceDao.observeMembers(workspaceId.value).first()
            val actorMember = activeMembers.firstOrNull { it.userId == actorId.value }
                ?: return RepositoryResult.Failure(AppError.Authentication("actor_not_member"))

            if (actorMember.roleCode.equals(WorkspaceRole.OWNER.name, ignoreCase = true)) {
                return RepositoryResult.Failure(AppError.Validation("cannot_leave_as_owner_requires_transfer"))
            }

            val baseVersion = actorMember.sync.baseVersion ?: actorMember.sync.version
            if (baseVersion <= 0L) {
                return RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))
            }

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberLeave(
                workspaceId = workspaceId.value,
                userId = actorId.value,
            )

            val now = nowEpochMillisProvider()
            val tombstonedMember = actorMember.copy(
                sync = actorMember.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = now,
                    localUpdatedAtEpochMillis = now,
                    baseVersion = baseVersion,
                ),
            )

            localMutationDao.mutateWorkspaceMemberLeaveV2(
                entity = tombstonedMember,
                activeProfileId = actorId.value,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = now,
            )

            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun transferOwnership(
        workspaceId: EntityId,
        targetUserId: EntityId,
    ): RepositoryResult<Unit> {
        return try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val actorId = session.userId

            val workspace = workspaceDao.getWorkspaceById(workspaceId.value)
            if (workspace == null || workspace.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val activeMembers = workspaceDao.observeMembers(workspaceId.value).first()
            val memberPairs = activeMembers.map { EntityId(it.userId) to it.roleCode.toWorkspaceRole() }

            val validation = WorkspaceValidationRules.validateOwnershipTransfer(
                actorUserId = actorId,
                targetUserId = targetUserId,
                currentMembers = memberPairs,
            )
            if (validation is WorkspaceValidationResult.Invalid) {
                return RepositoryResult.Failure(validation.error.toAppError())
            }

            val actorMember = activeMembers.firstOrNull { it.userId == actorId.value }
                ?: return RepositoryResult.Failure(AppError.Authentication("transfer_actor_not_owner"))
            val targetMember = activeMembers.firstOrNull { it.userId == targetUserId.value }
                ?: return RepositoryResult.Failure(AppError.Validation("target_member_not_found"))

            // Precondition: workspace, actorMember ve targetMember SYNCED durumunda olmalı ve silinmemiş olmalıdır
            if (workspace.sync.syncStatus != SyncStatus.SYNCED.name ||
                actorMember.sync.syncStatus != SyncStatus.SYNCED.name ||
                targetMember.sync.syncStatus != SyncStatus.SYNCED.name ||
                workspace.sync.deletedAtEpochMillis != null ||
                actorMember.sync.deletedAtEpochMillis != null ||
                targetMember.sync.deletedAtEpochMillis != null
            ) {
                return RepositoryResult.Failure(AppError.Conflict("local_uncommitted_changes_prevent_ownership_transfer"))
            }

            val expectedWorkspaceVersion = workspace.sync.version
            val expectedActorMemberVersion = actorMember.sync.version
            val expectedTargetMemberVersion = targetMember.sync.version

            if (expectedWorkspaceVersion <= 0L || expectedActorMemberVersion <= 0L || expectedTargetMemberVersion <= 0L) {
                return RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))
            }

            // Remote RPC çağrısı
            val resultDto = remoteDataSource.transferWorkspaceOwnership(
                workspaceId = workspaceId.value,
                targetUserId = targetUserId.value,
                expectedWorkspaceVersion = expectedWorkspaceVersion,
                expectedCurrentOwnerMemberVersion = expectedActorMemberVersion,
                expectedTargetMemberVersion = expectedTargetMemberVersion,
            )

            // Strict DTO mapper doğrulamaları
            if (resultDto.workspace.id != workspaceId.value ||
                resultDto.workspace.ownerId != targetUserId.value ||
                resultDto.actorMember.workspaceId != workspaceId.value ||
                resultDto.actorMember.userId != actorId.value ||
                !resultDto.actorMember.roleCode.equals(WorkspaceRole.EDITOR.name, ignoreCase = true) ||
                resultDto.targetMember.workspaceId != workspaceId.value ||
                resultDto.targetMember.userId != targetUserId.value ||
                !resultDto.targetMember.roleCode.equals(WorkspaceRole.OWNER.name, ignoreCase = true) ||
                resultDto.workspace.deletedAt != null ||
                resultDto.actorMember.deletedAt != null ||
                resultDto.targetMember.deletedAt != null
            ) {
                return RepositoryResult.Failure(AppError.Validation("invalid_remote_payload"))
            }

            val now = nowEpochMillisProvider()
            val updatedWorkspaceEntity = resultDto.workspace.toEntity(now)
            val updatedActorMemberEntity = resultDto.actorMember.toEntity(now)
            val updatedTargetMemberEntity = resultDto.targetMember.toEntity(now)

            // Atomik Room transaction'ı ile outbox üretmeden snapshot uygula
            remoteSyncDao.applyOwnershipTransferSnapshot(
                workspace = updatedWorkspaceEntity,
                actorMember = updatedActorMemberEntity,
                targetMember = updatedTargetMemberEntity,
                expectedWorkspaceVersion = expectedWorkspaceVersion,
                expectedActorMemberVersion = expectedActorMemberVersion,
                expectedTargetMemberVersion = expectedTargetMemberVersion,
            )

            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            RepositoryResult.Failure(e.toTransferAppError())
        }
    }

    override suspend fun removeMember(workspaceId: EntityId, userId: EntityId): RepositoryResult<Unit> {
        return try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val actorId = session.userId

            val workspace = workspaceDao.getWorkspaceById(workspaceId.value)
            if (workspace == null || workspace.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("workspace_not_found"))
            }

            val activeMembers = workspaceDao.observeMembers(workspaceId.value).first()
            val memberPairs = activeMembers.map { EntityId(it.userId) to it.roleCode.toWorkspaceRole() }

            val validation = WorkspaceValidationRules.validateMemberRemoval(
                actorUserId = actorId,
                targetUserId = userId,
                currentMembers = memberPairs,
            )
            if (validation is WorkspaceValidationResult.Invalid) {
                return RepositoryResult.Failure(validation.error.toAppError())
            }

            val actorMember = activeMembers.firstOrNull { it.userId == actorId.value }
                ?: return RepositoryResult.Failure(AppError.Authentication("actor_not_member"))

            if (!actorMember.roleCode.equals(WorkspaceRole.OWNER.name, ignoreCase = true)) {
                return RepositoryResult.Failure(AppError.Authentication("actor_not_permitted"))
            }

            if (actorId == userId) {
                return RepositoryResult.Failure(AppError.Validation("cannot_remove_self_member"))
            }

            val targetMember = activeMembers.firstOrNull { it.userId == userId.value }
                ?: return RepositoryResult.Failure(AppError.Validation("target_member_not_found"))

            if (targetMember.roleCode.equals(WorkspaceRole.OWNER.name, ignoreCase = true)) {
                return RepositoryResult.Failure(AppError.Validation("cannot_remove_workspace_owner"))
            }

            val targetRole = targetMember.roleCode.toWorkspaceRole()
            if (targetRole !in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER)) {
                return RepositoryResult.Failure(AppError.Validation("target_member_not_found"))
            }

            // Katı senkronizasyon ve versiyon kontrolleri: workspace, aktör üyelik ve hedef üyelik
            if (workspace.sync.syncStatus != SyncStatus.SYNCED.name ||
                actorMember.sync.syncStatus != SyncStatus.SYNCED.name ||
                targetMember.sync.syncStatus != SyncStatus.SYNCED.name
            ) {
                return RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))
            }

            val workspaceBaseVersion = workspace.sync.baseVersion ?: workspace.sync.version
            val actorBaseVersion = actorMember.sync.baseVersion ?: actorMember.sync.version
            val targetBaseVersion = targetMember.sync.baseVersion ?: targetMember.sync.version

            if (workspaceBaseVersion <= 0L || actorBaseVersion <= 0L || targetBaseVersion <= 0L) {
                return RepositoryResult.Failure(AppError.Validation("local_member_version_unavailable"))
            }

            val payloadJson = WorkspaceMembershipPayloadCodec.encodeMemberLeave(
                workspaceId = workspaceId.value,
                userId = userId.value,
            )

            val now = nowEpochMillisProvider()
            val tombstonedTarget = targetMember.copy(
                sync = targetMember.sync.copy(
                    syncStatus = SyncStatus.PENDING_DELETE.name,
                    deletedAtEpochMillis = now,
                    localUpdatedAtEpochMillis = now,
                    baseVersion = targetBaseVersion,
                ),
            )

            localMutationDao.mutateWorkspaceMemberRemovalV2(
                entity = tombstonedTarget,
                payloadJson = payloadJson,
                operationIdFactory = operationIdFactory,
                nowEpochMillis = now,
            )

            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }
}

private fun String.toWorkspaceRole(): WorkspaceRole =
    runCatching { WorkspaceRole.valueOf(trim().uppercase()) }.getOrDefault(WorkspaceRole.VIEWER)

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
    WorkspaceValidationError.CANNOT_REMOVE_SELF_MEMBER -> AppError.Validation("cannot_remove_self_member")
    WorkspaceValidationError.CANNOT_REMOVE_WORKSPACE_OWNER -> AppError.Validation("cannot_remove_workspace_owner")
    WorkspaceValidationError.CANNOT_LEAVE_AS_LAST_OWNER -> AppError.Validation("cannot_leave_as_owner_requires_transfer")
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

private fun Throwable.toJoinAppError(): AppError = when (this) {
    is io.github.jan.supabase.exceptions.HttpRequestException -> AppError.Network("network_unavailable")
    is io.ktor.client.plugins.HttpRequestTimeoutException,
    is io.ktor.client.network.sockets.SocketTimeoutException,
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.util.network.UnresolvedAddressException,
    is io.ktor.utils.io.errors.IOException,
    is kotlinx.io.IOException -> AppError.Network("network_unavailable")
    is io.github.jan.supabase.auth.exception.AuthRestException -> AppError.Authentication("auth_session_required")
    is io.github.jan.supabase.exceptions.RestException -> {
        val statusCode = try { response.status.value } catch (_: Throwable) { 0 }
        when {
            statusCode == 401 || statusCode == 403 -> AppError.Authentication("auth_session_required")
            statusCode == 404 -> AppError.Validation("workspace_invitation_not_found")
            error == "workspace_invitation_expired" || description?.contains("workspace_invitation_expired") == true ->
                AppError.Validation("workspace_invitation_expired")
            error == "workspace_invitation_limit_reached" || description?.contains("workspace_invitation_limit_reached") == true ->
                AppError.Validation("workspace_invitation_limit_reached")
            error == "workspace_invitation_not_found" || description?.contains("workspace_invitation_not_found") == true ->
                AppError.Validation("workspace_invitation_not_found")
            statusCode == 408 || statusCode == 429 || statusCode in 500..599 ->
                AppError.Network("network_unavailable")
            else -> {
                val msg = error.ifBlank { description ?: "" }
                when {
                    msg.contains("workspace_invitation_expired") -> AppError.Validation("workspace_invitation_expired")
                    msg.contains("workspace_invitation_limit_reached") -> AppError.Validation("workspace_invitation_limit_reached")
                    msg.contains("workspace_invitation_not_found") -> AppError.Validation("workspace_invitation_not_found")
                    else -> AppError.Validation("workspace_invitation_not_found")
                }
            }
        }
    }
    is androidx.sqlite.SQLiteException -> AppError.Storage("storage_failed")
    is com.feniqo.mobile.data.remote.mapper.RemoteMappingException -> AppError.Validation("invalid_remote_payload")
    else -> {
        val msg = message ?: ""
        when {
            msg.contains("workspace_invitation_expired") -> AppError.Validation("workspace_invitation_expired")
            msg.contains("workspace_invitation_limit_reached") -> AppError.Validation("workspace_invitation_limit_reached")
            msg.contains("workspace_invitation_not_found") -> AppError.Validation("workspace_invitation_not_found")
            msg.contains("auth_session_required") -> AppError.Authentication("auth_session_required")
            msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> AppError.Network("network_unavailable")
            else -> AppError.Unknown("unknown_error")
        }
    }
}

private fun Throwable.toTransferAppError(): AppError = when (this) {
    is io.github.jan.supabase.exceptions.HttpRequestException -> AppError.Network("network_unavailable")
    is io.ktor.client.plugins.HttpRequestTimeoutException,
    is io.ktor.client.network.sockets.SocketTimeoutException,
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.util.network.UnresolvedAddressException,
    is io.ktor.utils.io.errors.IOException,
    is kotlinx.io.IOException -> AppError.Network("network_unavailable")
    is io.github.jan.supabase.auth.exception.AuthRestException -> AppError.Authentication("auth_session_required")
    is io.github.jan.supabase.exceptions.RestException -> {
        val statusCode = try { response.status.value } catch (_: Throwable) { 0 }
        when {
            statusCode == 401 || statusCode == 403 -> AppError.Authentication("auth_session_required")
            statusCode == 404 -> AppError.Validation("workspace_not_found")
            error == "ownership_transfer_actor_not_owner" || description?.contains("ownership_transfer_actor_not_owner") == true ->
                AppError.Authentication("transfer_actor_not_owner")
            error == "ownership_transfer_target_not_member" || description?.contains("ownership_transfer_target_not_member") == true ->
                AppError.Validation("target_member_not_found")
            error == "ownership_transfer_target_already_owner" || description?.contains("ownership_transfer_target_already_owner") == true ->
                AppError.Validation("transfer_target_already_owner")
            error == "ownership_transfer_version_conflict" || description?.contains("ownership_transfer_version_conflict") == true ->
                AppError.Conflict("ownership_transfer_version_conflict")
            error == "cannot_change_own_role" || description?.contains("cannot_change_own_role") == true ->
                AppError.Validation("cannot_change_own_role")
            statusCode == 408 || statusCode == 429 || statusCode in 500..599 ->
                AppError.Network("network_unavailable")
            else -> {
                val msg = error.ifBlank { description ?: "" }
                when {
                    msg.contains("ownership_transfer_actor_not_owner") -> AppError.Authentication("transfer_actor_not_owner")
                    msg.contains("ownership_transfer_target_not_member") -> AppError.Validation("target_member_not_found")
                    msg.contains("ownership_transfer_target_already_owner") -> AppError.Validation("transfer_target_already_owner")
                    msg.contains("ownership_transfer_version_conflict") -> AppError.Conflict("ownership_transfer_version_conflict")
                    msg.contains("cannot_change_own_role") -> AppError.Validation("cannot_change_own_role")
                    else -> AppError.Validation("transfer_failed")
                }
            }
        }
    }
    is WorkspaceOwnershipTransferPreconditionException -> AppError.Conflict("local_uncommitted_changes_prevent_ownership_transfer")
    is androidx.sqlite.SQLiteException -> AppError.Storage("storage_failed")
    is com.feniqo.mobile.data.remote.mapper.RemoteMappingException -> AppError.Validation("invalid_remote_payload")
    else -> {
        val msg = message ?: ""
        when {
            msg.contains("ownership_transfer_actor_not_owner") -> AppError.Authentication("transfer_actor_not_owner")
            msg.contains("ownership_transfer_target_not_member") -> AppError.Validation("target_member_not_found")
            msg.contains("ownership_transfer_target_already_owner") -> AppError.Validation("transfer_target_already_owner")
            msg.contains("ownership_transfer_version_conflict") -> AppError.Conflict("ownership_transfer_version_conflict")
            msg.contains("local_uncommitted_changes_prevent_ownership_transfer") -> AppError.Conflict("local_uncommitted_changes_prevent_ownership_transfer")
            msg.contains("cannot_change_own_role") -> AppError.Validation("cannot_change_own_role")
            msg.contains("auth_session_required") -> AppError.Authentication("auth_session_required")
            msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> AppError.Network("network_unavailable")
            else -> AppError.Unknown("unknown_error")
        }
    }
}
