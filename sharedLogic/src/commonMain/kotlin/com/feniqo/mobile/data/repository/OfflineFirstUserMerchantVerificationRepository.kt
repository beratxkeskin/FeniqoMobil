package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.UserMerchantVerificationDao
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toDomainOrNull
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.model.UserMerchantVerification
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class OfflineFirstUserMerchantVerificationRepository(
    private val authRepository: AuthRepository,
    private val dao: UserMerchantVerificationDao,
    private val writeQueue: OfflineWriteQueue,
    private val nowEpochMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : UserMerchantVerificationRepository {
    override fun observeActiveVerifications(): Flow<List<UserMerchantVerification>> =
        authRepository.observeSession().flatMapLatest { session ->
            val userId = session?.userId?.value
            if (userId == null) {
                flowOf(emptyList())
            } else {
                val scopeKey = "user:$userId"
                dao.observeActiveByScope(scopeKey).map { list ->
                    list.mapNotNull { it.toDomainOrNull() }
                }
            }
        }

    override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult {
        val session = authRepository.observeSession().first()
        val userId = session?.userId?.value ?: return UserMerchantVerificationLookupResult.NotFound
        val normalized = TurkishDescriptionNormalizer.normalize(rawOrNormalizedAlias)
        if (normalized.isBlank()) return UserMerchantVerificationLookupResult.NotFound

        val scopeKey = "user:$userId"
        val entity =
            dao.getByScopeAndAlias(scopeKey, normalized)
                ?: return UserMerchantVerificationLookupResult.NotFound

        if (entity.sync.deletedAtEpochMillis != null) {
            return UserMerchantVerificationLookupResult.NotFound
        }

        val domain =
            entity.toDomainOrNull()
                ?: return UserMerchantVerificationLookupResult.InvalidStoredVerification(entity.id)

        return UserMerchantVerificationLookupResult.Decision(domain.decision)
    }

    override suspend fun assignMerchant(
        alias: String,
        merchantId: String,
    ): RepositoryResult<UserMerchantVerification> {
        val normalized = TurkishDescriptionNormalizer.normalize(alias)
        if (normalized.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("invalid_alias"))
        }
        if (normalized.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return RepositoryResult.Failure(AppError.Validation("alias_too_long"))
        }
        if (normalized in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            return RepositoryResult.Failure(AppError.Validation("generic_merchant_alias_not_allowed"))
        }

        val cleanMerchantId = merchantId.trim()
        if (cleanMerchantId.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("merchant_id_required"))
        }
        if (StarterMerchantCatalog.merchants.none { it.id == cleanMerchantId }) {
            return RepositoryResult.Failure(AppError.Validation("unknown_merchant_id"))
        }

        return try {
            mutateDecision(
                normalizedAlias = normalized,
                decisionCode = "ASSIGN",
                merchantId = cleanMerchantId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RepositoryResult.Failure(AppError.Storage("merchant_verification_storage_failed"))
        }
    }

    override suspend fun blockMerchant(alias: String): RepositoryResult<UserMerchantVerification> {
        val normalized = TurkishDescriptionNormalizer.normalize(alias)
        if (normalized.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("invalid_alias"))
        }
        if (normalized.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return RepositoryResult.Failure(AppError.Validation("alias_too_long"))
        }
        if (normalized in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            return RepositoryResult.Failure(AppError.Validation("generic_merchant_alias_not_allowed"))
        }

        return try {
            mutateDecision(
                normalizedAlias = normalized,
                decisionCode = "BLOCK",
                merchantId = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RepositoryResult.Failure(AppError.Storage("merchant_verification_storage_failed"))
        }
    }

    override suspend fun deleteVerification(alias: String): RepositoryResult<Unit> {
        val normalized = TurkishDescriptionNormalizer.normalize(alias)
        if (normalized.isBlank()) {
            return RepositoryResult.Failure(AppError.Validation("invalid_alias"))
        }

        val session = authRepository.observeSession().first()
        val userId =
            session?.userId?.value
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        val scopeKey = "user:$userId"
        val syncScopeKey = SyncScopeKey.forUser(userId)

        return try {
            val existing = dao.getByScopeAndAlias(scopeKey, normalized)
                ?: return RepositoryResult.Success(Unit)

            if (!validateStoredEntity(existing, userId, normalized)) {
                return RepositoryResult.Failure(AppError.Storage("invalid_stored_merchant_verification"))
            }

            if (existing.sync.deletedAtEpochMillis != null) {
                return RepositoryResult.Success(Unit)
            }

            val now = nowEpochMillisProvider()
            val deletedEntity =
                existing.copy(
                    sync =
                        existing.sync.copy(
                            syncStatus = SyncStatus.PENDING_DELETE.name,
                            localUpdatedAtEpochMillis = now,
                            deletedAtEpochMillis = now,
                        ),
                )

            writeQueue.enqueueUserMerchantVerificationV2(
                syncScopeKey = syncScopeKey,
                entity = deletedEntity,
                type = OutboxOperationType.DELETE,
            )

            RepositoryResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RepositoryResult.Failure(AppError.Storage("merchant_verification_storage_failed"))
        }
    }

    private suspend fun mutateDecision(
        normalizedAlias: String,
        decisionCode: String,
        merchantId: String?,
    ): RepositoryResult<UserMerchantVerification> {
        val session = authRepository.observeSession().first()
        val userId =
            session?.userId?.value
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

        val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, normalizedAlias)
        val scopeKey = "user:$userId"
        val syncScopeKey = SyncScopeKey.forUser(userId)
        val now = nowEpochMillisProvider()

        val existing = dao.getByScopeAndAlias(scopeKey, normalizedAlias)

        val entityToSave: UserMerchantVerificationEntity
        val operationType: OutboxOperationType

        if (existing == null) {
            operationType = OutboxOperationType.CREATE
            entityToSave =
                UserMerchantVerificationEntity(
                    id = deterministicId,
                    ownerId = userId,
                    workspaceId = null,
                    scopeKey = scopeKey,
                    normalizedAlias = normalizedAlias,
                    decisionCode = decisionCode,
                    merchantId = merchantId,
                    scopeCode = "PERSONAL",
                    createdAtEpochMillis = now,
                    sync =
                        SyncMetadata(
                            syncStatus = SyncStatus.PENDING_CREATE.name,
                            updatedAtEpochMillis = now,
                            localUpdatedAtEpochMillis = now,
                            deletedAtEpochMillis = null,
                            version = 0L,
                            baseVersion = null,
                            lastSyncError = null,
                        ),
                )
        } else {
            if (!validateStoredEntity(existing, userId, normalizedAlias)) {
                return RepositoryResult.Failure(AppError.Storage("invalid_stored_merchant_verification"))
            }

            operationType = OutboxOperationType.UPDATE
            val isDeleted = existing.sync.deletedAtEpochMillis != null
            val nextStatus =
                if (existing.sync.syncStatus == SyncStatus.PENDING_CREATE.name && !isDeleted) {
                    SyncStatus.PENDING_CREATE.name
                } else {
                    SyncStatus.PENDING_UPDATE.name
                }

            entityToSave =
                existing.copy(
                    decisionCode = decisionCode,
                    merchantId = merchantId,
                    sync =
                        existing.sync.copy(
                            syncStatus = nextStatus,
                            localUpdatedAtEpochMillis = now,
                            deletedAtEpochMillis = null,
                            baseVersion = if (existing.sync.version > 0L) existing.sync.version else null,
                        ),
                )
        }

        writeQueue.enqueueUserMerchantVerificationV2(
            syncScopeKey = syncScopeKey,
            entity = entityToSave,
            type = operationType,
        )

        return RepositoryResult.Success(entityToSave.toDomain())
    }

    private fun validateStoredEntity(
        entity: UserMerchantVerificationEntity,
        expectedOwnerId: String,
        expectedAlias: String,
    ): Boolean {
        if (entity.ownerId != expectedOwnerId) return false
        if (entity.scopeKey != "user:$expectedOwnerId") return false
        if (entity.workspaceId != null) return false
        if (entity.scopeCode != "PERSONAL") return false
        if (entity.normalizedAlias != expectedAlias) return false

        val expectedDeterministicId = runCatching {
            UuidHelper.deterministicMerchantVerificationUuid(expectedOwnerId, expectedAlias)
        }.getOrNull() ?: return false

        if (entity.id != expectedDeterministicId) return false
        if (entity.sync.version < 0L) return false

        when (entity.decisionCode) {
            "ASSIGN" -> if (entity.merchantId.isNullOrBlank()) return false
            "BLOCK" -> if (entity.merchantId != null) return false
            else -> return false
        }

        return true
    }
}
