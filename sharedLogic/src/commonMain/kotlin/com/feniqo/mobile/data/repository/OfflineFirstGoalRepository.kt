package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.GoalDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateGoalCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateGoalCommand
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationResult
import com.feniqo.mobile.domain.validation.GoalDebtValidationRules
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineFirstGoalRepository(
    private val authRepository: AuthRepository,
    private val goalDao: GoalDao,
    private val offlineWriteQueue: OfflineWriteQueue? = null,
    private val entityIdGenerator: EntityIdGenerator = EntityIdGenerator { EntityId("generated-goal-id") },
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : GoalRepository {

    override fun observeGoals(): Flow<List<Goal>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                goalDao.observeAll(session.userId.value, workspaceId = null)
                    .map { entities -> entities.map { it.toDomain() } }
            }
        }

    override fun observeGoal(id: EntityId): Flow<Goal?> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                goalDao.observeById(id.value).map { entity ->
                    entity?.takeIf { it.ownerId == session.userId.value && it.workspaceId == null }?.toDomain()
                }
            }
        }

    override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                goalDao.observeById(goalId.value).flatMapLatest { parentGoal ->
                    if (parentGoal == null || parentGoal.ownerId != session.userId.value || parentGoal.workspaceId != null) {
                        flowOf(emptyList())
                    } else {
                        goalDao.observeContributionsByGoalId(goalId.value)
                            .map { entities -> entities.map { it.toDomain() } }
                    }
                }
            }
        }

    override suspend fun create(command: CreateGoalCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val validCommand = when (val result = GoalDebtValidationRules.validateCreateGoalCommand(command)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val goalId = entityIdGenerator.nextId()
            val goal = Goal(
                id = goalId,
                ownerId = session.userId,
                workspaceId = null,
                name = validCommand.name,
                targetAmount = validCommand.targetAmount,
                currentAmount = validCommand.initialAmount ?: Money(0L, validCommand.targetAmount.currency),
                targetDate = validCommand.targetDate,
                color = validCommand.color,
                icon = validCommand.icon,
                createdAt = Instant.fromEpochMilliseconds(now),
            )

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueGoalV2(
                entity = goal.toEntity(newSyncMetadata(now)),
                type = OutboxOperationType.CREATE,
                payloadJson = json.encodeToString(goal.toDto()),
            )
            return RepositoryResult.Success(goal.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    override suspend fun update(command: UpdateGoalCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val existingEntity = goalDao.getById(command.id.value)
                ?: return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            if (existingEntity.ownerId != session.userId.value || existingEntity.workspaceId != null || existingEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            }

            val existing = existingEntity.toDomain()
            val updatedGoal = when (val result = GoalDebtValidationRules.applyGoalUpdate(existing, command)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val updatedEntity = updatedGoal.toEntity(existingEntity.sync.toPendingUpdate(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueGoalV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = json.encodeToString(updatedGoal.toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val parentGoalEntity = goalDao.getById(command.goalId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            if (parentGoalEntity.ownerId != session.userId.value || parentGoalEntity.workspaceId != null || parentGoalEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            }

            val parentGoal = parentGoalEntity.toDomain()
            val validCommand = when (val result = GoalDebtValidationRules.validateAddGoalContributionCommand(command, parentGoal)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val contribId = entityIdGenerator.nextId()
            val contrib = GoalContribution(
                id = contribId,
                goalId = parentGoal.id,
                amount = validCommand.amount,
                direction = validCommand.direction,
                occurredOn = validCommand.occurredOn,
                note = validCommand.note,
                createdAt = Instant.fromEpochMilliseconds(now),
            )

            val newAmountMinor = if (validCommand.direction == GoalContributionDirection.ADD) {
                parentGoal.currentAmount.amountMinor + validCommand.amount.amountMinor
            } else {
                parentGoal.currentAmount.amountMinor - validCommand.amount.amountMinor
            }
            require(newAmountMinor >= 0) { "Hedef bakiyesi negatif olamaz." }

            val updatedGoal = parentGoal.copy(currentAmount = Money(newAmountMinor, parentGoal.currentAmount.currency))
            val updatedGoalEntity = updatedGoal.toEntity(parentGoalEntity.sync.toPendingUpdate(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueGoalContributionV2(
                entity = contrib.toEntity(newSyncMetadata(now)),
                updatedGoal = updatedGoalEntity,
                payloadJson = json.encodeToString(contrib.toDto()),
            )
            return RepositoryResult.Success(contrib.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val existingEntity = goalDao.getById(id.value)
                ?: return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            if (existingEntity.ownerId != session.userId.value || existingEntity.workspaceId != null || existingEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("goal_not_found"))
            }

            val now = nowEpochMillisProvider()
            val deletedEntity = existingEntity.copy(sync = existingEntity.sync.toPendingDelete(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueGoalV2(
                entity = deletedEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = json.encodeToString(existingEntity.toDomain().toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    private fun <T> validationFailure(invalid: GoalDebtValidationResult.Invalid): RepositoryResult<T> =
        RepositoryResult.Failure(AppError.Validation(invalid.error.name))
}



