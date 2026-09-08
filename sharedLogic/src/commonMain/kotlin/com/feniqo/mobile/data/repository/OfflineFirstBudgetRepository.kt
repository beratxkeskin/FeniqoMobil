package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.BudgetDao
import com.feniqo.mobile.data.local.dao.BudgetMutationInputV2
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.data.util.RandomHexEntityIdGenerator
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CopyBudgetsResult
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.BudgetRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstBudgetRepository(
    private val authRepository: AuthRepository,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val entityIdGenerator: EntityIdGenerator = RandomHexEntityIdGenerator(),
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : BudgetRepository {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override fun observeBudgets(
        month: YearMonth,
        workspaceId: EntityId?,
    ): Flow<List<Budget>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    if (workspaceId != null && workspaceId != activeWorkspaceId) flowOf(emptyList())
                    else budgetDao.observeForMonth(
                        ownerId = session.userId.value,
                        workspaceId = activeWorkspaceId?.value,
                        month = month.value,
                    ).map { entities -> entities.map(BudgetEntity::toDomain) }
                }
            }
        }
    }

    override fun observeBudget(id: EntityId): Flow<Budget?> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    budgetDao.observeById(id.value).map { entity ->
                    if (entity != null && entity.ownerId == session.userId.value && entity.workspaceId == activeWorkspaceId?.value) {
                        entity.toDomain()
                    } else {
                        null
                    }
                    }
                }
            }
        }
    }

    override suspend fun create(command: CreateBudgetCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val category = categoryDao.getByIdAndOwner(command.categoryId.value, session.userId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("budget_category_not_found"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)

            if (category.typeCode != TransactionType.EXPENSE.name) {
                return RepositoryResult.Failure(AppError.Validation("budget_category_must_be_expense"))
            }

            val isValidCategoryOwner = (category.isDefault && category.ownerId == null) ||
                (category.ownerId == session.userId.value && category.workspaceId == activeWorkspaceId?.value)

            if (!isValidCategoryOwner) {
                return RepositoryResult.Failure(AppError.Authentication("budget_category_owner_mismatch"))
            }

            val scopeKey = activeWorkspaceId?.let { "workspace:${it.value}" } ?: "user:${session.userId.value}"
            val existing = budgetDao.getAnyByScopeCategoryAndMonth(
                scopeKey = scopeKey,
                categoryId = command.categoryId.value,
                month = command.month.value,
            )

            if (existing != null && existing.sync.deletedAtEpochMillis == null) {
                return RepositoryResult.Failure(AppError.Conflict("budget_already_exists"))
            }

            val now = nowEpochMillisProvider()

            if (existing == null) {
                val budgetId = entityIdGenerator.nextId()
                val domainBudget = Budget(
                    id = budgetId,
                    ownerId = session.userId,
                    workspaceId = activeWorkspaceId,
                    categoryId = command.categoryId,
                    month = command.month,
                    limit = command.limit,
                    createdAt = Instant.fromEpochMilliseconds(now),
                )
                val entity = domainBudget.toEntity(newSyncMetadata(now))
                val payloadJson = json.encodeToString(domainBudget.toDto())

                offlineWriteQueue.enqueueBudgetV2(
                    entity = entity,
                    type = OutboxOperationType.CREATE,
                    payloadJson = payloadJson,
                )
                return RepositoryResult.Success(budgetId)
            } else {
                // Soft-deleted kaydın reaktivasyonu (aynı id ve createdAt korunur)
                val restoredId = EntityId(existing.id)
                val restoredDomain = Budget(
                    id = restoredId,
                    ownerId = session.userId,
                    workspaceId = activeWorkspaceId,
                    categoryId = command.categoryId,
                    month = command.month,
                    limit = command.limit,
                    createdAt = Instant.fromEpochMilliseconds(existing.createdAtEpochMillis),
                )
                val payloadJson = json.encodeToString(restoredDomain.toDto())

                val (entity, outboxType) = if (existing.sync.baseVersion != null) {
                    val updatedSync = existing.sync.toPendingUpdate(now).copy(deletedAtEpochMillis = null)
                    restoredDomain.toEntity(updatedSync) to OutboxOperationType.UPDATE
                } else {
                    val freshSync = newSyncMetadata(now).copy(deletedAtEpochMillis = null)
                    restoredDomain.toEntity(freshSync) to OutboxOperationType.CREATE
                }

                offlineWriteQueue.enqueueBudgetV2(
                    entity = entity,
                    type = outboxType,
                    payloadJson = payloadJson,
                )
                return RepositoryResult.Success(restoredId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun update(command: UpdateBudgetCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val existing = budgetDao.getByIdAndOwner(command.id.value, session.userId.value)
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            if (existing == null || existing.workspaceId != activeWorkspaceId?.value) {
                return RepositoryResult.Failure(AppError.Validation("budget_not_found"))
            }

            val now = nowEpochMillisProvider()
            val updatedDomain = Budget(
                id = command.id,
                ownerId = session.userId,
                workspaceId = activeWorkspaceId,
                categoryId = EntityId(existing.categoryId),
                month = YearMonth(existing.month),
                limit = command.limit,
                createdAt = Instant.fromEpochMilliseconds(existing.createdAtEpochMillis),
            )
            val updatedSync = existing.sync.toPendingUpdate(now)
            val updatedEntity = updatedDomain.toEntity(updatedSync)
            val payloadJson = json.encodeToString(updatedDomain.toDto())

            offlineWriteQueue.enqueueBudgetV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = payloadJson,
            )
            return RepositoryResult.Success(Unit)
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

            val existing = budgetDao.getByIdAndOwner(id.value, session.userId.value)
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            if (existing == null || existing.workspaceId != activeWorkspaceId?.value) {
                return RepositoryResult.Failure(AppError.Validation("budget_not_found"))
            }

            val now = nowEpochMillisProvider()
            val deletedSync = existing.sync.toPendingDelete(now)
            val deletedEntity = existing.copy(sync = deletedSync)
            val payloadJson = json.encodeToString(existing.toDomain().toDto())

            offlineWriteQueue.enqueueBudgetV2(
                entity = deletedEntity,
                type = OutboxOperationType.DELETE,
                payloadJson = payloadJson,
            )
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun copyBudgets(command: CopyBudgetsCommand): RepositoryResult<CopyBudgetsResult> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)

            val sourceBudgets = budgetDao.getForMonth(
                ownerId = session.userId.value,
                workspaceId = activeWorkspaceId?.value,
                month = command.sourceMonth.value,
            ).sortedBy { it.categoryId }

            if (sourceBudgets.isEmpty()) {
                return RepositoryResult.Success(
                    CopyBudgetsResult(
                        copiedCount = 0,
                        skippedCategoryIds = emptyList(),
                    ),
                )
            }

            val now = nowEpochMillisProvider()
            val scopeKey = activeWorkspaceId?.let { "workspace:${it.value}" } ?: "user:${session.userId.value}"
            val mutationInputs = mutableListOf<BudgetMutationInputV2>()
            val skippedCategoryIds = mutableListOf<EntityId>()

            for (source in sourceBudgets) {
                val category = categoryDao.getByIdAndOwner(source.categoryId, session.userId.value)
                val isValidCategory = category != null &&
                    category.sync.deletedAtEpochMillis == null &&
                    category.typeCode == TransactionType.EXPENSE.name &&
                    ((category.isDefault && category.ownerId == null) ||
                        (category.ownerId == session.userId.value && category.workspaceId == activeWorkspaceId?.value))

                if (!isValidCategory) {
                    skippedCategoryIds.add(EntityId(source.categoryId))
                    continue
                }

                val targetExisting = budgetDao.getAnyByScopeCategoryAndMonth(
                    scopeKey = scopeKey,
                    categoryId = source.categoryId,
                    month = command.targetMonth.value,
                )

                if (targetExisting != null && targetExisting.sync.deletedAtEpochMillis == null) {
                    skippedCategoryIds.add(EntityId(source.categoryId))
                    continue
                }

                if (targetExisting == null) {
                    val newId = entityIdGenerator.nextId()
                    val domainBudget = Budget(
                        id = newId,
                        ownerId = session.userId,
                        workspaceId = activeWorkspaceId,
                        categoryId = EntityId(source.categoryId),
                        month = command.targetMonth,
                        limit = Money(source.limitMinor, Currency.valueOf(source.currencyCode)),
                        createdAt = Instant.fromEpochMilliseconds(now),
                    )
                    val entity = domainBudget.toEntity(newSyncMetadata(now))
                    val payloadJson = json.encodeToString(domainBudget.toDto())
                    mutationInputs.add(
                        BudgetMutationInputV2(
                            entity = entity,
                            type = OutboxOperationType.CREATE,
                            payloadJson = payloadJson,
                        ),
                    )
                } else {
                    val restoredId = EntityId(targetExisting.id)
                    val domainBudget = Budget(
                        id = restoredId,
                        ownerId = session.userId,
                        workspaceId = activeWorkspaceId,
                        categoryId = EntityId(source.categoryId),
                        month = command.targetMonth,
                        limit = Money(source.limitMinor, Currency.valueOf(source.currencyCode)),
                        createdAt = Instant.fromEpochMilliseconds(targetExisting.createdAtEpochMillis),
                    )
                    val payloadJson = json.encodeToString(domainBudget.toDto())
                    val (entity, outboxType) = if (targetExisting.sync.baseVersion != null) {
                        val updatedSync = targetExisting.sync.toPendingUpdate(now).copy(deletedAtEpochMillis = null)
                        domainBudget.toEntity(updatedSync) to OutboxOperationType.UPDATE
                    } else {
                        val freshSync = newSyncMetadata(now).copy(deletedAtEpochMillis = null)
                        domainBudget.toEntity(freshSync) to OutboxOperationType.CREATE
                    }
                    mutationInputs.add(
                        BudgetMutationInputV2(
                            entity = entity,
                            type = outboxType,
                            payloadJson = payloadJson,
                        ),
                    )
                }
            }

            if (mutationInputs.isNotEmpty()) {
                offlineWriteQueue.enqueueBudgetsV2(mutationInputs)
            }

            return RepositoryResult.Success(
                CopyBudgetsResult(
                    copiedCount = mutationInputs.size,
                    skippedCategoryIds = skippedCategoryIds.distinct().sortedBy { it.value },
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }
}
