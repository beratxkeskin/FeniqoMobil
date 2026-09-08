package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.GenerateRecurringOccurrenceResult
import com.feniqo.mobile.data.local.dao.RecurringTransactionDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.PlanDueRecurringOccurrencesUseCase
import com.feniqo.mobile.domain.validation.RecurringTransactionValidationResult
import com.feniqo.mobile.domain.validation.RecurringTransactionValidationRules
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineFirstRecurringTransactionRepository(
    private val authRepository: AuthRepository,
    private val categoryDao: CategoryDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val entityIdGenerator: EntityIdGenerator,
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
    private val planDueRecurringOccurrencesUseCase: PlanDueRecurringOccurrencesUseCase = PlanDueRecurringOccurrencesUseCase(),
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : RecurringTransactionRepository {

    override fun observeRecurringTransactions(): Flow<List<RecurringTransaction>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    recurringTransactionDao.observeAll(session.userId.value, workspaceId = activeWorkspaceId?.value)
                        .map { entities -> entities.map { it.toDomain() } }
                }
            }
        }

    override fun observeRecurringTransaction(id: EntityId): Flow<RecurringTransaction?> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    recurringTransactionDao.observeById(id.value).map { entity ->
                        entity?.takeIf { it.ownerId == session.userId.value && it.workspaceId == activeWorkspaceId?.value }?.toDomain()
                    }
                }
            }
        }

    override suspend fun create(command: CreateRecurringTransactionCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val validCommand = when (val result = RecurringTransactionValidationRules.validateCreateCommand(command)) {
                is RecurringTransactionValidationResult.Valid -> result.value
                is RecurringTransactionValidationResult.Invalid -> return validationFailure(result)
            }
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            validateExpenseCategoryForScope(validCommand.categoryId, validCommand.type, session.userId.value, activeWorkspaceId?.value)?.let { return it }

            val now = nowEpochMillisProvider()
            val recurring = RecurringTransaction(
                id = entityIdGenerator.nextId(),
                ownerId = session.userId,
                workspaceId = activeWorkspaceId,
                amount = validCommand.amount,
                type = validCommand.type,
                categoryId = validCommand.categoryId,
                description = validCommand.description,
                paymentMethod = validCommand.paymentMethod,
                rule = validCommand.rule,
                lastGeneratedDate = null,
                isActive = true,
                createdAt = Instant.fromEpochMilliseconds(now),
            )
            offlineWriteQueue.enqueueRecurringTransactionV2(
                entity = recurring.toEntity(newSyncMetadata(now)),
                type = OutboxOperationType.CREATE,
                payloadJson = json.encodeToString(recurring.toDto()),
            )
            return RepositoryResult.Success(recurring.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun update(command: UpdateRecurringTransactionCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(command.id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("recurring_transaction_not_found"))
            val updated = when (val result = RecurringTransactionValidationRules.applyRecurringRuleUpdate(existing.toDomain(), command)) {
                is RecurringTransactionValidationResult.Valid -> result.value
                is RecurringTransactionValidationResult.Invalid -> return validationFailure(result)
            }
            validateExpenseCategoryForScope(updated.categoryId, updated.type, session.userId.value, activeWorkspaceId?.value)?.let { return it }

            val now = nowEpochMillisProvider()
            offlineWriteQueue.enqueueRecurringTransactionV2(
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

    override suspend fun setActive(command: SetRecurringTransactionActiveCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(command.id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("recurring_transaction_not_found"))
            val now = nowEpochMillisProvider()
            val updated = existing.toDomain().copy(isActive = command.isActive)
            offlineWriteQueue.enqueueRecurringTransactionV2(
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

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val existing = scopedExisting(id, session.userId.value, activeWorkspaceId?.value)
                ?: return RepositoryResult.Failure(AppError.Validation("recurring_transaction_not_found"))
            val now = nowEpochMillisProvider()
            offlineWriteQueue.enqueueRecurringTransactionV2(
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
        recurringTransactionDao.getById(id.value)?.takeIf { it.ownerId == ownerId && it.workspaceId == workspaceId }

    private suspend fun validateExpenseCategoryForScope(
        categoryId: EntityId,
        type: TransactionType,
        ownerId: String,
        workspaceId: String?,
    ): RepositoryResult.Failure? {
        val category = categoryDao.getByIdAndOwner(categoryId.value, ownerId)
            ?: return RepositoryResult.Failure(AppError.Validation("recurring_transaction_category_not_found"))
        if (category.sync.deletedAtEpochMillis != null || category.typeCode != type.name) {
            return RepositoryResult.Failure(AppError.Validation("recurring_transaction_category_type_mismatch"))
        }
        val allowedScope = (category.isDefault && category.ownerId == null && category.workspaceId == null) ||
            (category.ownerId == ownerId && category.workspaceId == workspaceId)
        return if (allowedScope) null else RepositoryResult.Failure(
            AppError.Authentication("recurring_transaction_category_owner_mismatch"),
        )
    }

    private fun validationFailure(
        invalid: RecurringTransactionValidationResult.Invalid,
    ): RepositoryResult.Failure = RepositoryResult.Failure(
        AppError.Validation("recurring_transaction_${invalid.error.name.lowercase()}"),
    )

    override suspend fun generateDueTransactions(
        throughDate: LocalDate,
        maxOccurrencesPerRule: Int,
        maxTotalOccurrences: Int,
        createdAt: Instant,
    ): RepositoryResult<GenerateRecurringTransactionsResult> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val activeEntities = recurringTransactionDao.getActiveRules()
            val scopedEntities = activeEntities.filter {
                it.ownerId == session.userId.value && it.workspaceId == activeWorkspaceId?.value
            }
            val domainRules = scopedEntities.map { it.toDomain() }

            val dueOccurrences = planDueRecurringOccurrencesUseCase(
                recurringTransactions = domainRules,
                throughDate = throughDate,
                maxOccurrencesPerRule = maxOccurrencesPerRule,
                maxTotalOccurrences = maxTotalOccurrences,
            )

            var createdCount = 0
            var alreadyGeneratedCount = 0
            val staleRecurringIds = mutableListOf<EntityId>()
            val skippedRecurringIds = mutableListOf<EntityId>()
            val lastGeneratedTracking = mutableMapOf<EntityId, LocalDate?>()

            for (candidate in dueOccurrences) {
                val recurring = candidate.recurringTransaction
                val recurringId = recurring.id

                // Category validity check
                val category = categoryDao.getByIdAndOwner(recurring.categoryId.value, session.userId.value)
                val categoryScopeAllowed = category != null &&
                    category.sync.deletedAtEpochMillis == null &&
                    category.typeCode == recurring.type.name &&
                    ((category.isDefault && category.ownerId == null && category.workspaceId == null) ||
                        (category.ownerId == session.userId.value && category.workspaceId == activeWorkspaceId?.value))
                if (!categoryScopeAllowed) {
                    skippedRecurringIds.add(recurringId)
                    continue
                }

                val currentExpectedLastGenerated = if (lastGeneratedTracking.containsKey(recurringId)) {
                    lastGeneratedTracking[recurringId]
                } else {
                    recurring.lastGeneratedDate
                }

                val newTransactionId = entityIdGenerator.nextId()
                val transaction = Transaction(
                    id = newTransactionId,
                    ownerId = session.userId,
                    workspaceId = activeWorkspaceId,
                    amount = recurring.amount,
                    type = recurring.type,
                    categoryId = recurring.categoryId,
                    description = recurring.description,
                    paymentMethod = recurring.paymentMethod,
                    transactionDate = candidate.dueDate,
                    receiptPath = null,
                    installment = null,
                    createdAt = createdAt,
                )

                val dto = transaction.toDto()
                val payloadJson = json.encodeToString(dto)
                val entity = transaction.toEntity(newSyncMetadata(nowEpochMillisProvider()))

                val result = offlineWriteQueue.enqueueRecurringTransactionOccurrenceV2(
                    recurringTransactionId = recurringId.value,
                    expectedPreviousLastGeneratedDate = currentExpectedLastGenerated?.toString(),
                    dueDate = candidate.dueDate.toString(),
                    entity = entity,
                    payloadJson = payloadJson,
                )

                when (result) {
                    is GenerateRecurringOccurrenceResult.Created -> {
                        createdCount++
                        lastGeneratedTracking[recurringId] = candidate.dueDate
                    }
                    is GenerateRecurringOccurrenceResult.AlreadyGenerated -> {
                        alreadyGeneratedCount++
                        lastGeneratedTracking[recurringId] = candidate.dueDate
                    }
                    is GenerateRecurringOccurrenceResult.StaleRecurringState -> {
                        staleRecurringIds.add(recurringId)
                    }
                }
            }

            return RepositoryResult.Success(
                GenerateRecurringTransactionsResult(
                    createdCount = createdCount,
                    alreadyGeneratedCount = alreadyGeneratedCount,
                    staleRecurringIds = staleRecurringIds.distinct().sortedBy { it.value },
                    skippedRecurringIds = skippedRecurringIds.distinct().sortedBy { it.value },
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }
}
