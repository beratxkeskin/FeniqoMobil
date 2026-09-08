package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

import com.feniqo.mobile.data.remote.mapper.toDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstCategoryRepository(
    private val authRepository: AuthRepository,
    private val categoryDao: CategoryDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val activeWorkspaceScope: ActiveWorkspaceScope = PersonalActiveWorkspaceScope,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : CategoryRepository {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override fun observeCategories(
        type: TransactionType?,
        workspaceId: EntityId?,
    ): Flow<List<Category>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    if (workspaceId != null && workspaceId != activeWorkspaceId) flowOf(emptyList())
                    else categoryDao.observeAll(
                        ownerId = session.userId.value,
                        workspaceId = activeWorkspaceId?.value,
                        typeCode = type?.name,
                    ).map { entities -> entities.map(CategoryEntity::toDomain) }
                }
            }
        }
    }

    override fun observeCategory(id: EntityId): Flow<Category?> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    categoryDao.observeByIdAndOwner(id.value, session.userId.value)
                        .map { entity -> entity?.takeIf { it.workspaceId == activeWorkspaceId?.value }?.toDomain() }
                }
            }
        }
    }

    override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> {
        return authRepository.observeSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                activeWorkspaceScope.observe(session.userId).flatMapLatest { activeWorkspaceId ->
                    if (workspaceId != null && workspaceId != activeWorkspaceId) flowOf(emptyList())
                    else categoryDao.observeAllForHistoryLookup(
                        ownerId = session.userId.value,
                        workspaceId = activeWorkspaceId?.value,
                    ).map { entities -> entities.map(CategoryEntity::toDomain) }
                }
            }
        }
    }

    override suspend fun create(category: Category): RepositoryResult<EntityId> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (category.isDefault || category.ownerId == null) {
                return RepositoryResult.Failure(AppError.Validation("category_default_cannot_be_created_locally"))
            }

            if (category.ownerId != session.userId) {
                return RepositoryResult.Failure(AppError.Authentication("category_owner_mismatch"))
            }
            val scopedCategory = category.copy(workspaceId = activeWorkspaceScope.current(session.userId))

            val now = nowEpochMillisProvider()
            val sync = newSyncMetadata(now)
            val entity = scopedCategory.toEntity(sync)
            val dto = scopedCategory.toDto().copy(slug = entity.slug)
            val payloadJson = json.encodeToString(dto)

            offlineWriteQueue.enqueueCategoryV2(
                entity = entity,
                type = OutboxOperationType.CREATE,
                payloadJson = payloadJson,
            )
            return RepositoryResult.Success(scopedCategory.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RepositoryResult.Failure(e.toRepositoryAppError())
        }
    }

    override suspend fun update(category: Category): RepositoryResult<Unit> {
        try {
            val session = authRepository.observeSession().first()
                ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))

            if (category.isDefault || category.ownerId == null) {
                return RepositoryResult.Failure(AppError.Validation("category_default_cannot_be_modified"))
            }

            if (category.ownerId != session.userId) {
                return RepositoryResult.Failure(AppError.Authentication("category_owner_mismatch"))
            }

            val existing = categoryDao.getByIdAndOwner(category.id.value, session.userId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("category_not_found"))
            if (existing.workspaceId != activeWorkspaceScope.current(session.userId)?.value ||
                category.workspaceId?.value != existing.workspaceId) {
                return RepositoryResult.Failure(AppError.Validation("category_not_found"))
            }

            val now = nowEpochMillisProvider()
            val updatedSync = existing.sync.toPendingUpdate(now)
            val updatedEntity = category.toEntity(updatedSync, slug = existing.slug)
            val dto = category.toDto().copy(slug = updatedEntity.slug)
            val payloadJson = json.encodeToString(dto)

            val outboxType = if (existing.sync.syncStatus == SyncStatus.PENDING_CREATE.name) {
                OutboxOperationType.CREATE
            } else {
                OutboxOperationType.UPDATE
            }

            offlineWriteQueue.enqueueCategoryV2(
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

            val existing = categoryDao.getByIdAndOwner(id.value, session.userId.value)
                ?: return RepositoryResult.Failure(AppError.Validation("category_not_found"))
            if (existing.workspaceId != activeWorkspaceScope.current(session.userId)?.value) {
                return RepositoryResult.Failure(AppError.Validation("category_not_found"))
            }

            if (existing.isDefault || existing.ownerId == null) {
                return RepositoryResult.Failure(AppError.Validation("category_default_cannot_be_deleted"))
            }

            val now = nowEpochMillisProvider()
            val deletedSync = existing.sync.toPendingDelete(now)
            val deletedEntity = existing.copy(sync = deletedSync)
            val dto = existing.toDomain().toDto().copy(slug = existing.slug)
            val payloadJson = json.encodeToString(dto)

            offlineWriteQueue.enqueueCategoryV2(
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
}

internal fun Exception.toRepositoryAppError(): AppError = when (this) {
    is androidx.sqlite.SQLiteException -> AppError.Storage("storage_failed")
    is IllegalArgumentException -> AppError.Validation("invalid_argument")
    is IllegalStateException -> AppError.Validation("invalid_state")
    else -> AppError.Unknown("unknown_error")
}
