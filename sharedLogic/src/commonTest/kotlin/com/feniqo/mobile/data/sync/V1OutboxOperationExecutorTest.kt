package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class V1OutboxOperationExecutorTest {

    @Test
    fun applied_category_updates_room_with_server_version() = runTest {
        val dao = RecordingDao(category = localCategory())
        val writer = RecordingWriter(
            categoryResult = ConditionalRemoteWriteResult.Applied(remoteCategory(version = 3)),
        )

        V1OutboxOperationExecutor(writer, dao) { RECEIVED_AT }.execute(operation())

        assertEquals(RemoteWriteOperation.UPDATE, writer.operation)
        assertEquals(2L, writer.baseVersion)
        assertEquals("SYNCED", dao.category?.sync?.syncStatus)
        assertEquals(3L, dao.category?.sync?.version)
        assertEquals(3L, dao.category?.sync?.baseVersion)
    }

    @Test
    fun conflict_preserves_both_snapshots_and_marks_local_row() = runTest {
        val dao = RecordingDao(category = localCategory())
        val writer = RecordingWriter(
            categoryResult = ConditionalRemoteWriteResult.Conflict(remoteCategory(version = 4)),
        )

        assertFailsWith<OutboxConflictException> {
            V1OutboxOperationExecutor(writer, dao) { RECEIVED_AT }.execute(operation())
        }

        val conflict = assertNotNull(dao.conflict)
        assertEquals(2L, conflict.localVersion)
        assertEquals(4L, conflict.remoteVersion)
        assertEquals("CONFLICT", dao.category?.sync?.syncStatus)
        assertEquals(true, conflict.localPayloadJson.contains("Yerel kategori"))
        assertEquals(true, conflict.remotePayloadJson.contains("Uzak kategori"))
    }

    private class RecordingWriter(
        private val categoryResult: ConditionalRemoteWriteResult<CategoryDto>,
    ) : ConditionalRemoteWriter {
        var operation: RemoteWriteOperation? = null
        var baseVersion: Long? = null

        override suspend fun writeCategory(
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: CategoryDto,
        ): ConditionalRemoteWriteResult<CategoryDto> {
            this.operation = operation
            this.baseVersion = baseVersion
            return categoryResult
        }

        override suspend fun writeProfile(
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: ProfileDto,
        ): ConditionalRemoteWriteResult<ProfileDto> = error("Test kapsamı dışı")

        override suspend fun writeTransaction(
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: TransactionDto,
        ): ConditionalRemoteWriteResult<TransactionDto> = error("Test kapsamı dışı")
    }

    private class RecordingDao(
        var category: CategoryEntity?,
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = category?.takeIf { it.id == id }
        override suspend fun getTransactionRow(id: String): TransactionEntity? = null
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? = "operation-1"
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) {
            category = entities.single()
        }
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) {
            this.conflict = conflict
        }
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) = Unit
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int {
            conflict = null
            return 1
        }
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 0
        override suspend fun markCategoryConflict(entityId: String, error: String): Int {
            category = category?.copy(sync = category!!.sync.copy(syncStatus = "CONFLICT", lastSyncError = error))
            return 1
        }
        override suspend fun markTransactionConflict(entityId: String, error: String): Int = 0
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
    }

    private companion object {
        const val ENTITY_ID = "10000000-0000-0000-0000-000000000001"
        const val USER_ID = "20000000-0000-0000-0000-000000000001"
        const val CREATED_AT = "2026-08-10T10:00:00Z"
        const val UPDATED_AT = "2026-08-14T10:00:00Z"
        const val RECEIVED_AT = 1_765_707_200_000L

        fun localCategory() = CategoryEntity(
            id = ENTITY_ID,
            ownerId = USER_ID,
            workspaceId = null,
            scopeKey = "user:$USER_ID",
            name = "Yerel kategori",
            normalizedName = "yerel kategori",
            slug = null,
            typeCode = "EXPENSE",
            colorHex = "#123456",
            iconKey = null,
            isDefault = false,
            createdAtEpochMillis = 1_754_820_000_000L,
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1_765_700_000_000L,
                localUpdatedAtEpochMillis = 1_765_700_000_000L,
                deletedAtEpochMillis = null,
                version = 2,
                baseVersion = 2,
                lastSyncError = null,
            ),
        )

        fun remoteCategory(version: Long) = CategoryDto(
            id = ENTITY_ID,
            userId = USER_ID,
            name = if (version == 3L) "Yerel kategori" else "Uzak kategori",
            type = "expense",
            color = "#123456",
            createdAt = CREATED_AT,
            updatedAt = UPDATED_AT,
            version = version,
        )

        fun operation() = SyncOperationEntity(
            operationId = "operation-1",
            entityTypeCode = "CATEGORY",
            entityId = ENTITY_ID,
            operationTypeCode = "UPDATE",
            baseVersion = 2,
            statusCode = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAtEpochMillis = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
    }
}
