package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.AssetDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingDelete
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Asset
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import com.feniqo.mobile.domain.repository.AssetRepository
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.AssetValidationResult
import com.feniqo.mobile.domain.validation.AssetValidationRules
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Personal assets are local-first and never inherit the active workspace scope. */
class OfflineFirstAssetRepository(
    private val authRepository: AuthRepository,
    private val assetDao: AssetDao,
    private val offlineWriteQueue: OfflineWriteQueue,
    private val entityIdGenerator: EntityIdGenerator,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val json: Json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true },
) : AssetRepository {
    override fun observeAssets(): Flow<List<Asset>> = authRepository.observeSession().flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else assetDao.observeAll(session.userId.value).map { it.map { entity -> entity.toDomain() } }
    }

    override fun observeAsset(id: EntityId): Flow<Asset?> = authRepository.observeSession().flatMapLatest { session ->
        if (session == null) flowOf(null) else assetDao.observeById(id.value, session.userId.value).map { it?.toDomain() }
    }

    override suspend fun create(command: CreateAssetCommand): RepositoryResult<EntityId> = try {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val valid = when (val result = AssetValidationRules.validateCreate(command)) {
            is AssetValidationResult.Valid -> result.value
            is AssetValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation(result.error.name))
        }
        val now = nowEpochMillisProvider()
        val asset = Asset(
            id = entityIdGenerator.nextId(), ownerId = session.userId, workspaceId = null,
            name = valid.name, type = valid.type, currentValue = valid.currentValue, quantity = valid.quantity,
            purchaseUnitPrice = valid.purchaseUnitPrice, trackingSymbol = valid.trackingSymbol, autoTrack = valid.autoTrack,
            createdAt = Instant.fromEpochMilliseconds(now),
        )
        offlineWriteQueue.enqueueAssetV2(asset.toEntity(newSyncMetadata(now)), OutboxOperationType.CREATE, json.encodeToString(asset.toDto()))
        RepositoryResult.Success(asset.id)
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { RepositoryResult.Failure(AppError.Unknown("asset_write_failed")) }

    override suspend fun update(command: UpdateAssetCommand): RepositoryResult<Unit> = try {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val existing = assetDao.getAnyById(command.id.value)
            ?.takeIf { it.ownerId == session.userId.value && it.sync.deletedAtEpochMillis == null }
            ?: return RepositoryResult.Failure(AppError.Validation("asset_not_found"))
        val asset = when (val result = AssetValidationRules.applyUpdate(existing.toDomain(), command)) {
            is AssetValidationResult.Valid -> result.value
            is AssetValidationResult.Invalid -> return RepositoryResult.Failure(AppError.Validation(result.error.name))
        }
        val now = nowEpochMillisProvider()
        offlineWriteQueue.enqueueAssetV2(asset.toEntity(existing.sync.toPendingUpdate(now)), OutboxOperationType.UPDATE, json.encodeToString(asset.toDto()))
        RepositoryResult.Success(Unit)
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { RepositoryResult.Failure(AppError.Unknown("asset_write_failed")) }

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> = try {
        val session = authRepository.observeSession().first()
            ?: return RepositoryResult.Failure(AppError.Authentication("auth_session_required"))
        val existing = assetDao.getAnyById(id.value)
            ?.takeIf { it.ownerId == session.userId.value && it.sync.deletedAtEpochMillis == null }
            ?: return RepositoryResult.Failure(AppError.Validation("asset_not_found"))
        val now = nowEpochMillisProvider()
        offlineWriteQueue.enqueueAssetV2(existing.copy(sync = existing.sync.toPendingDelete(now)), OutboxOperationType.DELETE, json.encodeToString(existing.toDomain().toDto()))
        RepositoryResult.Success(Unit)
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { RepositoryResult.Failure(AppError.Unknown("asset_write_failed")) }

}
