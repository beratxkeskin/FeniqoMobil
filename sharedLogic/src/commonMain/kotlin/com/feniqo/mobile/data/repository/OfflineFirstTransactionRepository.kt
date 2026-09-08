package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.local.outbox.TransactionCreateInput
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.domain.validation.TransactionValidationResult
import com.feniqo.mobile.domain.validation.TransactionValidationRules
import com.feniqo.mobile.data.local.dao.TransactionCreateInputV2
import com.feniqo.mobile.data.local.dao.TransactionDeleteInputV2
import com.feniqo.mobile.data.remote.mapper.toDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstTransactionRepository(
    private val authRepository: AuthRepository,
    private val transactionDao: TransactionDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val workspaceDao: WorkspaceDao,
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : TransactionRepository {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    if (filter.workspaceId != null && filter.workspaceId != activeWorkspaceId) flowOf(emptyList())
                    else transactionDao.observeAll(
                        ownerId = session.userId.value,
                        workspaceId = activeWorkspaceId?.value,
                        startDate = filter.period?.startDate?.toString(),
                        endDate = filter.period?.endDate?.toString(),
                        typeCode = filter.type?.name,
                        categoryId = filter.categoryId?.value,
                        paymentMethodCode = filter.paymentMethod?.name,
                        searchQuery = filter.query?.trim()?.ifBlank { null },
                    ).map { entities -> entities.map(TransactionEntity::toDomain) }
                }
            }
        }
    }

    override fun observeTransaction(id: EntityId): Flow<Transaction?> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    transactionDao.observeByIdAndOwner(id.value, session.userId.value)
                        .map { entity -> entity?.takeIf { it.workspaceId == activeWorkspaceId?.value }?.toDomain() }
                }
            }
        }
    }

    override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    transactionDao.observeInstallmentGroupAndOwner(
                        groupId = groupId.value,
                        ownerId = session.userId.value,
                    ).map { entities ->
                        entities.filter { it.workspaceId == activeWorkspaceId?.value }.map(TransactionEntity::toDomain)
                    }
                }
            }
        }
    }

    override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (transaction.ownerId != session.userId) {
                return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
            }
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val activeMemberIds = if (activeWorkspaceId != null) {
                workspaceDao.getActiveMemberUserIds(activeWorkspaceId.value).map(::EntityId).toSet()
            } else {
                null
            }
            val scopedTransaction = transaction.copy(workspaceId = activeWorkspaceId)
            val splitValidation = TransactionValidationRules.normalizeAndValidateSplit(scopedTransaction, activeMemberIds)
            val validatedTransaction = when (splitValidation) {
                is TransactionValidationResult.Valid -> splitValidation.value
                is TransactionValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation(splitValidation.error.name.lowercase()))
            }

            val now = nowEpochMillisProvider()
            val sync = newSyncMetadata(now)
            val entity = validatedTransaction.toEntity(sync)
            val dto = validatedTransaction.toDto()
            val payloadJson = json.encodeToString(dto)

            offlineWriteQueue.enqueueTransactionV2(
                entity = entity,
                tags = emptyList(),
                tagLinks = emptyList(),
                type = OutboxOperationType.CREATE,
                payloadJson = payloadJson,
            )
            return RepositoryResult.Success(validatedTransaction.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (transactions.size !in 2..60) {
                return RepositoryResult.Failure(AppError.Validation("installment_count_out_of_range"))
            }

            if (transactions.any { it.ownerId != session.userId }) {
                return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
            }
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            val activeMemberIds = if (activeWorkspaceId != null) {
                workspaceDao.getActiveMemberUserIds(activeWorkspaceId.value).map(::EntityId).toSet()
            } else {
                null
            }

            if (transactions.map { it.id.value }.distinct().size != transactions.size) {
                return RepositoryResult.Failure(AppError.Validation("duplicate_transaction_id"))
            }

            if (transactions.any { it.installment == null }) {
                return RepositoryResult.Failure(AppError.Validation("installment_info_required"))
            }

            val groupIds = transactions.map { it.installment!!.groupId }.distinct()
            if (groupIds.size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_group_mismatch"))
            }

            val expectedTotal = transactions.size
            if (transactions.any { it.installment!!.total != expectedTotal }) {
                return RepositoryResult.Failure(AppError.Validation("installment_total_mismatch"))
            }

            val numbers = transactions.map { it.installment!!.number }.sorted()
            if (numbers != (1..expectedTotal).toList()) {
                return RepositoryResult.Failure(AppError.Validation("installment_numbers_invalid"))
            }

            if (transactions.any { it.amount.amountMinor <= 0L }) {
                return RepositoryResult.Failure(AppError.Validation("transaction_amount_must_be_positive"))
            }

            if (transactions.map { it.amount.currency }.distinct().size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_currency_mismatch"))
            }

            if (transactions.map { it.type }.distinct().size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_type_mismatch"))
            }

            if (transactions.map { it.categoryId }.distinct().size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_category_mismatch"))
            }

            if (transactions.map { it.paymentMethod }.distinct().size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_payment_method_mismatch"))
            }

            if (transactions.map { it.workspaceId }.distinct().size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_workspace_mismatch"))
            }

            val now = nowEpochMillisProvider()
            val inputs = ArrayList<TransactionCreateInputV2>(transactions.size)
            for (trx in transactions) {
                val scopedTransaction = trx.copy(workspaceId = activeWorkspaceId)
                val splitValidation = TransactionValidationRules.normalizeAndValidateSplit(scopedTransaction, activeMemberIds)
                val validatedTransaction = when (splitValidation) {
                    is TransactionValidationResult.Valid -> splitValidation.value
                    is TransactionValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation(splitValidation.error.name.lowercase()))
                }
                val sync = newSyncMetadata(now)
                val entity = validatedTransaction.toEntity(sync)
                val dto = validatedTransaction.toDto()
                val payloadJson = json.encodeToString(dto)
                inputs.add(
                    TransactionCreateInputV2(
                        entity = entity,
                        tags = emptyList(),
                        tagLinks = emptyList(),
                        payloadJson = payloadJson,
                    ),
                )
            }

            offlineWriteQueue.enqueueTransactionCreatesV2(inputs)
            return RepositoryResult.Success(groupIds.first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (transaction.ownerId != session.userId) {
                return RepositoryResult.Failure(AppError.Authentication("transaction_owner_mismatch"))
            }

            val existing = transactionDao.getByIdAndOwner(transaction.id.value, session.userId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            val activeWorkspaceId = activeWorkspaceScope.current(session.userId)
            if (existing.workspaceId != activeWorkspaceId?.value ||
                transaction.workspaceId?.value != existing.workspaceId) {
                return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            }

            val activeMemberIds = if (activeWorkspaceId != null) {
                workspaceDao.getActiveMemberUserIds(activeWorkspaceId.value).map(::EntityId).toSet()
            } else {
                null
            }
            val scopedTransaction = transaction.copy(workspaceId = activeWorkspaceId)
            val splitValidation = TransactionValidationRules.normalizeAndValidateSplit(scopedTransaction, activeMemberIds)
            val validatedTransaction = when (splitValidation) {
                is TransactionValidationResult.Valid -> splitValidation.value
                is TransactionValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation(splitValidation.error.name.lowercase()))
            }

            val now = nowEpochMillisProvider()
            val updatedSync = existing.sync.toPendingUpdate(now)
            val updatedEntity = validatedTransaction.toEntity(updatedSync)
            val dto = validatedTransaction.toDto()
            val payloadJson = json.encodeToString(dto)

            val outboxType = if (existing.sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                OutboxOperationType.CREATE
            } else {
                OutboxOperationType.UPDATE
            }

            offlineWriteQueue.enqueueTransactionKeepingTagsV2(
                entity = updatedEntity,
                type = outboxType,
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

            val existing = transactionDao.getByIdAndOwner(id.value, session.userId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            if (existing.workspaceId != activeWorkspaceScope.current(session.userId)?.value) {
                return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            }

            val now = nowEpochMillisProvider()
            val deletedSync = existing.sync.toPendingDelete(now)
            val deletedEntity = existing.copy(sync = deletedSync)
            val dto = existing.toDomain().toDto()
            val payloadJson = json.encodeToString(dto)

            offlineWriteQueue.enqueueTransactionKeepingTagsV2(
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

    override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (ids.isEmpty()) {
                return RepositoryResult.Failure(AppError.Validation("installment_ids_empty"))
            }

            val stringIds = ids.map { it.value }
            val existingList = transactionDao.getByIdsAndOwner(stringIds, session.userId.value)
            if (existingList.size != ids.size) {
                return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            }

            if (existingList.map { it.id }.toSet() != stringIds.toSet()) {
                return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            }
            if (existingList.any { it.workspaceId != activeWorkspaceScope.current(session.userId)?.value }) {
                return RepositoryResult.Failure(AppError.Validation("transaction_not_found"))
            }

            for (entity in existingList) {
                val groupId = entity.installmentGroupId
                val number = entity.installmentNumber
                val total = entity.totalInstallments

                if (groupId.isNullOrBlank() || number == null || total == null) {
                    return RepositoryResult.Failure(AppError.Validation("installment_info_required"))
                }
                if (number < 1 || total < 2 || number > total) {
                    return RepositoryResult.Failure(AppError.Validation("installment_info_required"))
                }
            }

            val groupIds = existingList.map { it.installmentGroupId }.distinct()
            if (groupIds.size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_group_mismatch"))
            }

            val totals = existingList.map { it.totalInstallments }.distinct()
            if (totals.size != 1) {
                return RepositoryResult.Failure(AppError.Validation("installment_total_mismatch"))
            }

            val now = nowEpochMillisProvider()
            val inputs = existingList.map { entity ->
                val deletedSync = entity.sync.toPendingDelete(now)
                val deletedEntity = entity.copy(sync = deletedSync)
                val dto = entity.toDomain().toDto()
                val payloadJson = json.encodeToString(dto)
                TransactionDeleteInputV2(
                    entity = deletedEntity,
                    payloadJson = payloadJson,
                )
            }

            offlineWriteQueue.enqueueTransactionDeletionsV2(inputs)
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }
}
