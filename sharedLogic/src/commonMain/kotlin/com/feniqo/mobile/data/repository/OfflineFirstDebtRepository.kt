package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.DebtDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.AddDebtPaymentCommand
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.CreateDebtCommand
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.UpdateDebtCommand
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.DebtRepository
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

class OfflineFirstDebtRepository(
    private val authRepository: AuthRepository,
    private val debtDao: DebtDao,
    private val offlineWriteQueue: OfflineWriteQueue? = null,
    private val entityIdGenerator: EntityIdGenerator = EntityIdGenerator { EntityId("generated-debt-id") },
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : DebtRepository {

    override fun observeDebts(): Flow<List<Debt>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                debtDao.observeAll(session.userId.value, workspaceId = null)
                    .map { entities -> entities.map { it.toDomain() } }
            }
        }

    override fun observeDebt(id: EntityId): Flow<Debt?> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                debtDao.observeById(id.value).map { entity ->
                    entity?.takeIf { it.ownerId == session.userId.value && it.workspaceId == null }?.toDomain()
                }
            }
        }

    override fun observePayments(debtId: EntityId): Flow<List<DebtPayment>> =
        authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                debtDao.observeById(debtId.value).flatMapLatest { parentDebt ->
                    if (parentDebt == null || parentDebt.ownerId != session.userId.value || parentDebt.workspaceId != null) {
                        flowOf(emptyList())
                    } else {
                        debtDao.observePaymentsByDebtId(debtId.value)
                            .map { entities -> entities.map { it.toDomain() } }
                    }
                }
            }
        }

    override suspend fun create(command: CreateDebtCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val validCommand = when (val result = GoalDebtValidationRules.validateCreateDebtCommand(command)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val debtId = entityIdGenerator.nextId()
            val debt = Debt(
                id = debtId,
                ownerId = session.userId,
                workspaceId = null,
                title = validCommand.title,
                amount = validCommand.amount,
                type = validCommand.type,
                dueDate = validCommand.dueDate,
                status = DebtStatus.OPEN,
                description = validCommand.description,
                createdAt = Instant.fromEpochMilliseconds(now),
            )

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueDebtV2(
                entity = debt.toEntity(newSyncMetadata(now)),
                type = OutboxOperationType.CREATE,
                payloadJson = json.encodeToString(debt.toDto()),
            )
            return RepositoryResult.Success(debt.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    override suspend fun update(command: UpdateDebtCommand): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val existingEntity = debtDao.getById(command.id.value)
                ?: return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            if (existingEntity.ownerId != session.userId.value || existingEntity.workspaceId != null || existingEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            }

            val existingPayments = debtDao.getPaymentsByDebtId(command.id.value).map { it.toDomain() }
            val existing = existingEntity.toDomain()
            val updatedDebt = when (val result = GoalDebtValidationRules.applyDebtUpdate(existing, command, existingPayments)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val updatedEntity = updatedDebt.toEntity(existingEntity.sync.toPendingUpdate(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueDebtV2(
                entity = updatedEntity,
                type = OutboxOperationType.UPDATE,
                payloadJson = json.encodeToString(updatedDebt.toDto()),
            )
            return RepositoryResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            return RepositoryResult.Failure(AppError.Unknown(t.message ?: "unknown_error"))
        }
    }

    override suspend fun addPayment(command: AddDebtPaymentCommand): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
            val parentDebtEntity = debtDao.getById(command.debtId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            if (parentDebtEntity.ownerId != session.userId.value || parentDebtEntity.workspaceId != null || parentDebtEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            }

            val parentDebt = parentDebtEntity.toDomain()
            val existingPayments = debtDao.getPaymentsByDebtId(command.debtId.value).map { it.toDomain() }
            val validCommand = when (val result = GoalDebtValidationRules.validateAddDebtPaymentCommand(command, parentDebt, existingPayments)) {
                is GoalDebtValidationResult.Valid -> result.value
                is GoalDebtValidationResult.Invalid -> return validationFailure(result)
            }

            val now = nowEpochMillisProvider()
            val paymentId = entityIdGenerator.nextId()
            val payment = DebtPayment(
                id = paymentId,
                debtId = parentDebt.id,
                amount = validCommand.amount,
                paidOn = validCommand.paidOn,
                createdAt = Instant.fromEpochMilliseconds(now),
            )

            val newTotalPaidMinor = existingPayments.sumOf { it.amount.amountMinor } + validCommand.amount.amountMinor
            require(newTotalPaidMinor <= parentDebt.amount.amountMinor) { "Ödeme toplamı borç tutarını aşamaz." }

            val newStatus = if (newTotalPaidMinor == parentDebt.amount.amountMinor) DebtStatus.SETTLED else DebtStatus.OPEN
            val updatedDebt = parentDebt.copy(status = newStatus)
            val updatedDebtEntity = updatedDebt.toEntity(parentDebtEntity.sync.toPendingUpdate(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueDebtPaymentV2(
                entity = payment.toEntity(newSyncMetadata(now)),
                updatedDebt = updatedDebtEntity,
                payloadJson = json.encodeToString(payment.toDto()),
            )
            return RepositoryResult.Success(payment.id)
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
            val existingEntity = debtDao.getById(id.value)
                ?: return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            if (existingEntity.ownerId != session.userId.value || existingEntity.workspaceId != null || existingEntity.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(AppError.Validation("debt_not_found"))
            }

            val now = nowEpochMillisProvider()
            val deletedEntity = existingEntity.copy(sync = existingEntity.sync.toPendingDelete(now))

            val queue = requireNotNull(offlineWriteQueue) { "OfflineWriteQueue is required for writes" }
            queue.enqueueDebtV2(
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



