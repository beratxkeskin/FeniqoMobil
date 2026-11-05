package com.feniqo.mobile.data.repository

import com.feniqo.mobile.domain.usecase.TransactionMerchantVerificationAliasResolver
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TransactionType
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

import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.domain.validation.TransactionValidationResult
import com.feniqo.mobile.domain.validation.TransactionValidationRules
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import com.feniqo.mobile.domain.usecase.CategoryMerchantCandidateResolver
import com.feniqo.mobile.domain.usecase.MerchantRecognitionEngine
import com.feniqo.mobile.domain.usecase.TransactionMerchantAttributionResolver
import com.feniqo.mobile.domain.usecase.TransactionMerchantResolutionResult
import com.feniqo.mobile.data.local.dao.TransactionCreateInputV2
import com.feniqo.mobile.data.local.dao.TransactionDeleteInputV2
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.MerchantCorrectionRejectionReason
import com.feniqo.mobile.domain.model.TransactionMerchantCorrection
import com.feniqo.mobile.domain.model.TransactionMerchantEligibility
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.repository.TransactionMerchantCorrectionRepository
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstTransactionRepository(
    private val authRepository: AuthRepository,
    private val transactionDao: TransactionDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val workspaceDao: WorkspaceDao,
    private val userMerchantVerificationRepository: UserMerchantVerificationRepository,
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val merchantRecognitionEngine: MerchantRecognitionEngine = MerchantRecognitionEngine(),
    private val categoryDao: CategoryDao? = null,
) : TransactionRepository, TransactionMerchantCorrectionRepository {
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
        return create(transaction, TransactionMerchantCreateDecision.Auto)
    }

    override suspend fun create(
        transaction: Transaction,
        merchantDecision: TransactionMerchantCreateDecision,
    ): RepositoryResult<EntityId> {
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
                emptySet()
            }

            val attributedTransaction = when (merchantDecision) {
                is TransactionMerchantCreateDecision.Auto -> {
                    val resolvedResult =
                        TransactionMerchantAttributionResolver.resolveWithDecision(
                            description = transaction.description,
                            verificationRepository = userMerchantVerificationRepository,
                            engine = merchantRecognitionEngine,
                        )
                    val resolvedMerchantInfo = when (resolvedResult) {
                        is TransactionMerchantResolutionResult.Success -> resolvedResult.merchantInfo
                        is TransactionMerchantResolutionResult.InvalidVerification -> {
                            return RepositoryResult.Failure(AppError.Storage("invalid_stored_merchant_verification"))
                        }
                    }
                    transaction.copy(merchantInfo = resolvedMerchantInfo)
                }
                is TransactionMerchantCreateDecision.KnownMerchant,
                is TransactionMerchantCreateDecision.UseTransactionName,
                -> {
                    if (transaction.workspaceId != null || activeWorkspaceId != null) {
                        return RepositoryResult.Failure(MerchantCorrectionRejectionReason.WORKSPACE_RESTRICTED.toAppError())
                    }
                    if (transaction.type != TransactionType.EXPENSE) {
                        return RepositoryResult.Failure(AppError.Validation("expense_only_merchant_decision"))
                    }

                    val aliasResolution =
                        TransactionMerchantVerificationAliasResolver.resolve(
                            rawDescription = transaction.description,
                            decision = merchantDecision,
                        )
                    val resolved =
                        when (aliasResolution) {
                            is TransactionMerchantVerificationAliasResolver.Result.Resolved -> aliasResolution
                            is TransactionMerchantVerificationAliasResolver.Result.Rejected -> {
                                return RepositoryResult.Failure(aliasResolution.reason.toAppError())
                            }
                        }

                    if (categoryDao == null) {
                        return RepositoryResult.Failure(AppError.Validation("category_dao_required_for_merchant_decision"))
                    }

                    val category =
                        categoryDao.getByIdAndOwner(transaction.categoryId.value, session.userId.value)
                            ?: return RepositoryResult.Failure(AppError.Validation("category_not_found"))
                    if (category.sync.deletedAtEpochMillis != null) {
                        return RepositoryResult.Failure(AppError.Validation("category_deleted"))
                    }
                    if (category.typeCode != "EXPENSE" || category.workspaceId != null) {
                        return RepositoryResult.Failure(AppError.Validation("invalid_category_scope_or_type"))
                    }
                    val isValidSystem = category.isDefault && category.ownerId == null && category.workspaceId == null
                    val isValidPersonal = !category.isDefault && category.ownerId == session.userId.value && category.workspaceId == null
                    if (!isValidSystem && !isValidPersonal) {
                        return RepositoryResult.Failure(AppError.Validation("invalid_category_ownership"))
                    }

                    when (merchantDecision) {
                        is TransactionMerchantCreateDecision.KnownMerchant -> {
                            val found =
                                resolved.targetMerchant
                                    ?: return RepositoryResult.Failure(
                                        MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT.toAppError(),
                                    )
                            if (!CategoryMerchantCandidateResolver.isCompatible(category.iconKey, found)) {
                                return RepositoryResult.Failure(AppError.Validation("category_merchant_mismatch"))
                            }
                            transaction.copy(
                                merchantInfo = TransactionMerchantInfo(
                                    merchantId = EntityId(found.id),
                                    matchSource = MerchantMatchSource.USER_VERIFIED,
                                    confidence = 100,
                                    catalogVersion = found.catalogVersion,
                                ),
                            )
                        }
                        is TransactionMerchantCreateDecision.UseTransactionName -> {
                            transaction.copy(merchantInfo = null)
                        }
                        is TransactionMerchantCreateDecision.Auto -> error("Impossible")
                    }
                }
            }

            val scopedTransaction = attributedTransaction.copy(workspaceId = activeWorkspaceId)
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
            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

            if (merchantDecision is TransactionMerchantCreateDecision.Auto) {
                offlineWriteQueue.enqueueTransactionV2(
                    syncScopeKey = syncScopeKey,
                    entity = entity,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    type = OutboxOperationType.CREATE,
                    payloadJson = payloadJson,
                )
            } else {
                offlineWriteQueue.enqueueTransactionWithMerchantDecisionV2(
                    syncScopeKey = syncScopeKey,
                    entity = entity,
                    tags = emptyList(),
                    tagLinks = emptyList(),
                    payloadJson = payloadJson,
                    decision = merchantDecision,
                )
            }
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
            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

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
                emptySet()
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
                val resolvedResult =
                    TransactionMerchantAttributionResolver.resolveWithDecision(
                        description = trx.description,
                        verificationRepository = userMerchantVerificationRepository,
                        engine = merchantRecognitionEngine,
                    )
                val resolved = when (resolvedResult) {
                    is TransactionMerchantResolutionResult.Success -> resolvedResult.merchantInfo
                    is TransactionMerchantResolutionResult.InvalidVerification -> {
                        return RepositoryResult.Failure(AppError.Storage("invalid_stored_merchant_verification"))
                    }
                }
                val attributed = trx.copy(merchantInfo = resolved)
                val scopedTransaction = attributed.copy(workspaceId = activeWorkspaceId)
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

            offlineWriteQueue.enqueueTransactionCreatesV2(syncScopeKey = syncScopeKey, inputs = inputs)
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
                emptySet()
            }
            val existingDomain = existing.toDomain()
            val descriptionChanged =
                existingDomain.description?.trim() != transaction.description?.trim()
            val updatedMerchantInfo =
                if (descriptionChanged) {
                    val resolvedResult =
                        TransactionMerchantAttributionResolver.resolveWithDecision(
                            description = transaction.description,
                            verificationRepository = userMerchantVerificationRepository,
                            engine = merchantRecognitionEngine,
                        )
                    when (resolvedResult) {
                        is TransactionMerchantResolutionResult.Success -> resolvedResult.merchantInfo
                        is TransactionMerchantResolutionResult.InvalidVerification -> {
                            return RepositoryResult.Failure(AppError.Storage("invalid_stored_merchant_verification"))
                        }
                    }
                } else {
                    existingDomain.merchantInfo
                }
            val attributedTransaction = transaction.copy(merchantInfo = updatedMerchantInfo)
            val scopedTransaction = attributedTransaction.copy(workspaceId = activeWorkspaceId)
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

            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

            offlineWriteQueue.enqueueTransactionKeepingTagsV2(
                syncScopeKey = syncScopeKey,
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
            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

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
                syncScopeKey = syncScopeKey,
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
            val syncScopeKey = SyncScopeKey.user(session.userId.value).rawValue

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

            offlineWriteQueue.enqueueTransactionDeletionsV2(syncScopeKey = syncScopeKey, inputs = inputs)
            return RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun correctMerchant(
        correction: TransactionMerchantCorrection,
    ): RepositoryResult<Unit> {
        return try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(MerchantCorrectionRejectionReason.AUTH_SESSION_REQUIRED.toAppError())

            val existing = transactionDao.getByIdAndOwner(correction.transactionId.value, session.userId.value)
                ?: return RepositoryResult.Failure(MerchantCorrectionRejectionReason.TRANSACTION_NOT_FOUND.toAppError())

            if (existing.ownerId != session.userId.value) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.OWNER_MISMATCH.toAppError())
            }

            if (existing.workspaceId != null) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.WORKSPACE_RESTRICTED.toAppError())
            }

            if (existing.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.TRANSACTION_DELETED.toAppError())
            }

            val description = existing.description
            if (description.isNullOrBlank()) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.INVALID_DESCRIPTION.toAppError())
            }

            val normalized = TurkishDescriptionNormalizer.normalize(description)
            if (normalized.isBlank()) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.INVALID_ALIAS.toAppError())
            }
            if (normalized.length > Transaction.MAX_DESCRIPTION_LENGTH) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.ALIAS_TOO_LONG.toAppError())
            }
            if (normalized in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED.toAppError())
            }

            if (TransactionMerchantEligibility.isExcluded(normalized)) {
                return RepositoryResult.Failure(MerchantCorrectionRejectionReason.EXCLUDED_TRANSACTION.toAppError())
            }

            val isAssign: Boolean
            val assignedMerchantId: String?
            val assignedCatalogVersion: Int?

            when (correction) {
                is TransactionMerchantCorrection.Assign -> {
                    val cleanId = correction.merchantId.value.trim()
                    if (cleanId.isBlank()) {
                        return RepositoryResult.Failure(MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT.toAppError())
                    }
                    val found = StarterMerchantCatalog.merchants.firstOrNull { it.id == cleanId }
                        ?: return RepositoryResult.Failure(MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT.toAppError())
                    isAssign = true
                    assignedMerchantId = found.id
                    assignedCatalogVersion = found.catalogVersion
                }
                is TransactionMerchantCorrection.Block -> {
                    isAssign = false
                    assignedMerchantId = null
                    assignedCatalogVersion = null
                }
            }

            val syncScopeKey = SyncScopeKey.forUser(session.userId.value)
            offlineWriteQueue.enqueueTransactionMerchantCorrectionV2(
                syncScopeKey = syncScopeKey,
                actorUserId = session.userId.value,
                transactionId = correction.transactionId.value,
                isAssign = isAssign,
                assignedMerchantId = assignedMerchantId,
                assignedCatalogVersion = assignedCatalogVersion,
            )
            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }
}
