package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteSyncCursor
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Sunucuda cursor sonrasında değişen V1 kayıtlarını Room SSOT'a uygular. */
class IncrementalRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val syncStateDao: SyncStateDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    private val snapshotJson = Json { encodeDefaults = true; explicitNulls = true }

    suspend fun pullFor(userId: EntityId): IncrementalSyncResult {
        var applied = 0
        var conflicts = 0

        remote.fetchProfile(userId.value)?.let { profile ->
            val storedCursor = syncStateDao.getCursor(PROFILE)
            if (profile.isAfter(storedCursor)) {
                val result = applyProfile(profile)
                applied += result.applied
                conflicts += result.conflicts
            }
        }

        val categoryCursor = syncStateDao.getCursor(CATEGORY)
        val categories = fetchAll { page ->
            remote.fetchCategories(
                CategoryRemoteQuery(
                    page = page,
                    workspaceScope = RemoteWorkspaceScope.Personal,
                    updatedAfter = categoryCursor?.toRemoteCursor(),
                ),
            )
        }
        categories.forEach { dto ->
            val result = applyCategory(dto)
            applied += result.applied
            conflicts += result.conflicts
        }

        val transactionCursor = syncStateDao.getCursor(TRANSACTION)
        val transactions = fetchAll { page ->
            remote.fetchTransactions(
                TransactionRemoteQuery(
                    page = page,
                    workspaceScope = RemoteWorkspaceScope.Personal,
                    updatedAfter = transactionCursor?.toRemoteCursor(),
                ),
            )
        }
        transactions.forEach { dto ->
            val result = applyTransaction(dto)
            applied += result.applied
            conflicts += result.conflicts
        }

        return IncrementalSyncResult(
            appliedCount = applied,
            conflictCount = conflicts,
            receivedCategoryCount = categories.size,
            receivedTransactionCount = transactions.size,
        )
    }

    private suspend fun applyProfile(dto: ProfileDto): ApplyResult {
        val cursor = dto.cursor(PROFILE)
        val local = remoteSyncDao.getProfileRow(dto.id)
        val remoteEntity = dto.toDomain().toEntity(dto.toRemoteSyncMetadata(nowEpochMillisProvider()))
        return applyOrConflict(
            local = local,
            remoteVersion = dto.requiredVersion(PROFILE),
            apply = { remoteSyncDao.applyProfilePull(remoteEntity, cursor) },
            conflict = {
                remoteSyncDao.recordProfilePullConflict(
                    conflict = pullConflict(
                        entityType = PROFILE,
                        entityId = dto.id,
                        operationId = requiredOutboxOperationId(PROFILE, dto.id),
                        localVersion = local!!.sync.version,
                        remoteVersion = dto.requiredVersion(PROFILE),
                        local = local.toDomain().toDto(),
                        remote = dto,
                    ),
                    cursor = cursor,
                )
            },
            advance = { remoteSyncDao.advancePullCursor(cursor) },
        )
    }

    private suspend fun applyCategory(dto: CategoryDto): ApplyResult {
        val cursor = dto.cursor(CATEGORY)
        val local = remoteSyncDao.getCategoryRow(dto.id)
        val remoteEntity = dto.toDomain().toEntity(dto.toRemoteSyncMetadata(nowEpochMillisProvider()), slug = dto.slug)
        return applyOrConflict(
            local = local,
            remoteVersion = dto.requiredVersion(CATEGORY),
            apply = { remoteSyncDao.applyCategoryPull(remoteEntity, cursor) },
            conflict = {
                val localDto = local!!.toDomain().toDto().copy(slug = local.slug)
                remoteSyncDao.recordCategoryPullConflict(
                    conflict = pullConflict(
                        entityType = CATEGORY,
                        entityId = dto.id,
                        operationId = requiredOutboxOperationId(CATEGORY, dto.id),
                        localVersion = local.sync.version,
                        remoteVersion = dto.requiredVersion(CATEGORY),
                        local = localDto,
                        remote = dto,
                    ),
                    cursor = cursor,
                )
            },
            advance = { remoteSyncDao.advancePullCursor(cursor) },
        )
    }

    private suspend fun applyTransaction(dto: TransactionDto): ApplyResult {
        val cursor = dto.cursor(TRANSACTION)
        val local = remoteSyncDao.getTransactionRow(dto.id)
        val remoteEntity = dto.toDomain().toEntity(dto.toRemoteSyncMetadata(nowEpochMillisProvider()))
        return applyOrConflict(
            local = local,
            remoteVersion = dto.requiredVersion(TRANSACTION),
            apply = { remoteSyncDao.applyTransactionPull(remoteEntity, cursor) },
            conflict = {
                remoteSyncDao.recordTransactionPullConflict(
                    conflict = pullConflict(
                        entityType = TRANSACTION,
                        entityId = dto.id,
                        operationId = requiredOutboxOperationId(TRANSACTION, dto.id),
                        localVersion = local!!.sync.version,
                        remoteVersion = dto.requiredVersion(TRANSACTION),
                        local = local.toDomain().toDto(),
                        remote = dto,
                    ),
                    cursor = cursor,
                )
            },
            advance = { remoteSyncDao.advancePullCursor(cursor) },
        )
    }

    private suspend fun applyOrConflict(
        local: Any?,
        remoteVersion: Long,
        apply: suspend () -> Unit,
        conflict: suspend () -> Unit,
        advance: suspend () -> Unit,
    ): ApplyResult {
        val sync = when (local) {
            is UserProfileEntity -> local.sync
            is CategoryEntity -> local.sync
            is TransactionEntity -> local.sync
            null -> null
            else -> error("Desteklenmeyen yerel sync entity türü.")
        }

        if (sync == null || SyncStatus.valueOf(sync.syncStatus) == SyncStatus.SYNCED) {
            apply()
            return ApplyResult(applied = 1, conflicts = 0)
        }

        val baseVersion = sync.baseVersion ?: 0L
        return if (remoteVersion > baseVersion) {
            conflict()
            ApplyResult(applied = 0, conflicts = 1)
        } else {
            // Uzak kayıt yerel mutasyonun dayandığı sürümden yeni değil; yerel değişiklik korunur.
            advance()
            ApplyResult(applied = 0, conflicts = 0)
        }
    }

    private suspend fun <T> fetchAll(fetch: suspend (RemotePageRequest) -> RemotePage<T>): List<T> {
        val result = mutableListOf<T>()
        var request = RemotePageRequest()
        do {
            val page = fetch(request)
            result += page.items
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)
        return result
    }

    private inline fun <reified L : Any, reified R : Any> pullConflict(
        entityType: String,
        entityId: String,
        operationId: String,
        localVersion: Long,
        remoteVersion: Long,
        local: L,
        remote: R,
    ): SyncConflictEntity = SyncConflictEntity(
        entityTypeCode = entityType,
        entityId = entityId,
        operationId = operationId,
        localVersion = localVersion,
        remoteVersion = remoteVersion,
        localPayloadJson = snapshotJson.encodeToString(local),
        remotePayloadJson = snapshotJson.encodeToString(remote),
        detectedAtEpochMillis = nowEpochMillisProvider(),
    )

    private suspend fun requiredOutboxOperationId(entityType: String, entityId: String): String =
        requireNotNull(remoteSyncDao.getFirstOutboxOperationId(entityType, entityId)) {
            "Pending yerel kayıt için outbox işlemi bulunamadı: $entityType/$entityId"
        }

    private fun SyncCursorEntity.toRemoteCursor(): RemoteSyncCursor = RemoteSyncCursor(
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis).toString(),
        entityId = entityId,
    )

    private fun ProfileDto.isAfter(cursor: SyncCursorEntity?): Boolean {
        if (cursor == null) return true
        val remoteInstant = Instant.parse(updatedAt ?: createdAt)
        val cursorInstant = Instant.fromEpochMilliseconds(cursor.updatedAtEpochMillis)
        return remoteInstant > cursorInstant || (remoteInstant == cursorInstant && id > cursor.entityId)
    }

    private fun ProfileDto.cursor(entityType: String) = cursor(entityType, id, updatedAt ?: createdAt)
    private fun CategoryDto.cursor(entityType: String) = cursor(entityType, id, updatedAt ?: createdAt)
    private fun TransactionDto.cursor(entityType: String) = cursor(entityType, id, updatedAt ?: createdAt)

    private fun cursor(entityType: String, entityId: String, updatedAt: String) = SyncCursorEntity(
        entityTypeCode = entityType,
        updatedAtEpochMillis = Instant.parse(updatedAt).toEpochMilliseconds(),
        entityId = entityId,
    )

    private fun ProfileDto.requiredVersion(entityType: String): Long = requiredVersion(entityType, version)
    private fun CategoryDto.requiredVersion(entityType: String): Long = requiredVersion(entityType, version)
    private fun TransactionDto.requiredVersion(entityType: String): Long = requiredVersion(entityType, version)

    private fun requiredVersion(entityType: String, version: Long?): Long = requireNotNull(version) {
        "$entityType uzak kaydı version taşımıyor; sync migration uygulanmadan pull yapılamaz."
    }

    private data class ApplyResult(val applied: Int, val conflicts: Int)

    private companion object {
        const val PROFILE = "PROFILE"
        const val CATEGORY = "CATEGORY"
        const val TRANSACTION = "TRANSACTION"
    }
}

data class IncrementalSyncResult(
    val appliedCount: Int,
    val conflictCount: Int,
    val receivedCategoryCount: Int,
    val receivedTransactionCount: Int,
)
