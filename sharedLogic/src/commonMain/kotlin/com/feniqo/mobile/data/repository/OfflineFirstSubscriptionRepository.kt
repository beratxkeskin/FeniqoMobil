package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.SubscriptionDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionCalculator
import com.feniqo.mobile.domain.validation.SubscriptionRenewalProgressionResult
import com.feniqo.mobile.domain.validation.SubscriptionValidationResult
import com.feniqo.mobile.domain.validation.SubscriptionValidationRules
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineFirstSubscriptionRepository(
    private val authRepository: AuthRepository,
    private val categoryDao: CategoryDao,
    private val subscriptionDao: SubscriptionDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val entityIdGenerator: EntityIdGenerator,
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : SubscriptionRepository {

    override fun observeSubscriptions(): Flow<List<Subscription>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    subscriptionDao.observeAll(session.userId.value, workspaceId = activeWorkspaceId?.value)
                        .map { entities -> entities.map { it.toDomain() } }
                }
            }
        }

    override fun observeSubscription(id: EntityId): Flow<Subscription?> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    subscriptionDao.observeById(id.value).map { entity ->
                        entity?.takeIf { it.ownerId == session.userId.value && it.workspaceId == activeWorkspaceId?.value }?.toDomain()
                    }
                }
            }
        }

    override suspend fun create(command: CreateSubscriptionCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val validCommand = when (val result = SubscriptionValidationRules.validateCreateCommand(command)) {
                is SubscriptionValidationResult.Valid -> result.value
                is SubscriptionValidationResult.Invalid -> return validationFailure(result)
            }
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            validateExpenseCategoryForScope(validCommand.categoryId, session.userId.value, activeWorkspaceId?.value)?.let { return it }

            val now = nowEpochMillisProvider()
            val subscription = Subscription(
                id = entityIdGenerator.nextId(),
                ownerId = session.userId,
                workspaceId = activeWorkspaceId,
                name = validCommand.name,
                amount = validCommand.amount,
                categoryId = validCommand.categoryId,
                renewalRule = validCommand.renewalRule,
                nextRenewalDate = validCommand.nextRenewalDate,
                isActive = true,
                createdAt = Instant.fromEpochMilliseconds(now),
            )
            offlineWriteQueue.enqueueSubscriptionV2(
                entity = subscription.toEntity(newSyncMetadata(now)),
                type = OutboxOperationType.CREATE,
                payloadJson = json.encodeToString(subscription.toDto()),
            )
            return RepositoryResult.Success(subscription.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun update(command: UpdateSubscriptionCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(command.id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("subscription_not_found"))
            val updated = when (val result = SubscriptionValidationRules.applySubscriptionUpdate(existing.toDomain(), command)) {
                is SubscriptionValidationResult.Valid -> result.value
                is SubscriptionValidationResult.Invalid -> return validationFailure(result)
            }
            validateExpenseCategoryForScope(updated.categoryId, session.userId.value, activeWorkspaceId?.value)?.let { return it }

            val now = nowEpochMillisProvider()
            offlineWriteQueue.enqueueSubscriptionV2(
                entity = updated.toEntity(existing.sync.toPendingUpdate(now)),
                type = OutboxOperationType.UPDATE,
                payloadJson = json.encodeToString(updated.toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun setActive(command: SetSubscriptionActiveCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(command.id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("subscription_not_found"))
            val now = nowEpochMillisProvider()
            val updated = existing.toDomain().copy(isActive = command.isActive)
            offlineWriteQueue.enqueueSubscriptionV2(
                entity = updated.toEntity(existing.sync.toPendingUpdate(now)),
                type = OutboxOperationType.UPDATE,
                payloadJson = json.encodeToString(updated.toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun advanceRenewal(id: EntityId): RepositoryResult<SubscriptionRenewalProgressionResult> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("subscription_not_found"))
            val domainSubscription = existing.toDomain()
            val progressionResult = SubscriptionRenewalProgressionCalculator.calculateNextRenewal(domainSubscription)

            val updated = when (progressionResult) {
                is SubscriptionRenewalProgressionResult.Advanced -> {
                    domainSubscription.copy(nextRenewalDate = progressionResult.nextRenewalDate)
                }
                is SubscriptionRenewalProgressionResult.Completed -> {
                    domainSubscription.copy(isActive = false)
                }
                is SubscriptionRenewalProgressionResult.InactiveSubscription -> {
                    return RepositoryResult.Failure(AppError.Validation("subscription_inactive"))
                }
            }

            val now = nowEpochMillisProvider()
            offlineWriteQueue.enqueueSubscriptionV2(
                entity = updated.toEntity(existing.sync.toPendingUpdate(now)),
                type = OutboxOperationType.UPDATE,
                payloadJson = json.encodeToString(updated.toDto()),
            )
            return RepositoryResult.Success(progressionResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("subscription_not_found"))
            val now = nowEpochMillisProvider()
            offlineWriteQueue.enqueueSubscriptionV2(
                entity = existing.copy(sync = existing.sync.toPendingDelete(now)),
                type = OutboxOperationType.DELETE,
                payloadJson = json.encodeToString(existing.toDomain().toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    private suspend fun scopedExisting(id: EntityId, ownerId: String, workspaceId: String?) =
        subscriptionDao.getById(id.value)?.takeIf { it.ownerId == ownerId && it.workspaceId == workspaceId }

    private suspend fun validateExpenseCategoryForScope(
        categoryId: EntityId?,
        ownerId: String,
        workspaceId: String?,
    ): RepositoryResult.Failure? {
        if (categoryId == null) return null
        val category = categoryDao.getByIdAndOwner(categoryId.value, ownerId)
            ?: return RepositoryResult.Failure(AppError.Validation("subscription_category_not_found"))
        if (category.sync.deletedAtEpochMillis != null || category.typeCode != "EXPENSE") {
            return RepositoryResult.Failure(AppError.Validation("subscription_category_type_mismatch"))
        }
        val allowedScope = (category.isDefault && category.ownerId == null && category.workspaceId == null) ||
            (category.ownerId == ownerId && category.workspaceId == workspaceId)
        return if (allowedScope) null else RepositoryResult.Failure(
            AppError.Authentication("subscription_category_owner_mismatch"),
        )
    }

    private fun validationFailure(
        invalid: SubscriptionValidationResult.Invalid,
    ): RepositoryResult.Failure = RepositoryResult.Failure(
        AppError.Validation("subscription_${invalid.error.name.lowercase()}"),
    )
}
