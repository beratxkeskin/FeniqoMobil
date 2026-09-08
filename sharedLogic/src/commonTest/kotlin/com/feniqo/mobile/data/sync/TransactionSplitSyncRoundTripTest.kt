package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.DebtEntity
import com.feniqo.mobile.data.local.entity.DebtPaymentEntity
import com.feniqo.mobile.data.local.entity.GoalContributionEntity
import com.feniqo.mobile.data.local.entity.GoalEntity
import com.feniqo.mobile.data.local.entity.RecurringTransactionEntity
import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity
import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriteResult
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.domain.model.EntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransactionSplitSyncRoundTripTest {

    @Test
    fun v2_executor_executes_transaction_create_with_split_payload_and_handles_ack() = runTest {
        val remoteDto = remoteTransaction(
            version = 1L,
            paidByUserId = "user-2",
            participantUserIds = listOf("user-1", "user-2", "user-3"),
        )
        val writer = RecordingTransactionWriter(
            transactionResult = ConditionalRemoteWriteResult.Applied(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 1000L }

        val snapshotJson = transactionSnapshotJson(
            id = TX_ID,
            version = null,
            paidByUserId = "user-2",
            participantUserIds = listOf("user-1", "user-2", "user-3"),
        )
        val op = transactionOperation(
            operationType = "CREATE",
            baseVersion = null,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.TransactionApplied>(result)
        assertEquals(TX_ID, result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(OP_ID, writer.lastOperationId)
        assertEquals(RemoteWriteOperation.CREATE, writer.lastOperation)
        assertEquals(null, writer.lastBaseVersion)
        assertEquals("user-2", writer.lastTransactionDto?.paidByUserId)
        assertEquals(listOf("user-1", "user-2", "user-3"), writer.lastTransactionDto?.participantUserIds)
    }

    @Test
    fun v2_executor_executes_transaction_update_with_split_payload_and_handles_conflict() = runTest {
        val remoteDto = remoteTransaction(
            version = 4L,
            paidByUserId = "user-3",
            participantUserIds = listOf("user-1", "user-3"),
        )
        val writer = RecordingTransactionWriter(
            transactionResult = ConditionalRemoteWriteResult.Conflict(remoteDto),
        )
        val executor = V2OutboxOperationExecutor(writer) { 2000L }

        val snapshotJson = transactionSnapshotJson(
            id = TX_ID,
            version = 2L,
            paidByUserId = "user-2",
            participantUserIds = listOf("user-1", "user-2"),
        )
        val op = transactionOperation(
            operationType = "UPDATE",
            baseVersion = 2L,
            payloadJson = snapshotJson,
        )

        val result = executor.execute(op)

        assertIs<OutboxExecutionResult.ConflictDetected>(result)
        assertEquals(OP_ID, result.conflict.operationId)
        assertEquals("TRANSACTION", result.conflict.entityTypeCode)
        assertEquals(TX_ID, result.conflict.entityId)
        assertEquals(2L, result.conflict.localVersion)
        assertEquals(4L, result.conflict.remoteVersion)
        assertTrue(result.conflict.localPayloadJson.contains("paid_by_user_id") && result.conflict.localPayloadJson.contains("user-2"))
        assertTrue(result.conflict.remotePayloadJson.contains("paid_by_user_id") && result.conflict.remotePayloadJson.contains("user-3"))
    }

    @Test
    fun incremental_pull_applies_remote_transaction_split_and_persists_to_room() = runTest {
        val cursors = mutableMapOf(TRANSACTION_CURSOR_KEY to storedTransactionCursor())
        val dao = RecordingTransactionRemoteSyncDao(cursors = cursors)
        val remote = FakeRemoteTransactionDataSource(
            transaction = remoteTransaction(
                version = 2L,
                paidByUserId = "user-2",
                participantUserIds = listOf("user-1", "user-2"),
            ),
        )

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(1, result.appliedCount)
        assertEquals(0, result.conflictCount)
        assertEquals("SYNCED", dao.transaction?.sync?.syncStatus)
        assertEquals(2L, dao.transaction?.sync?.version)
        assertEquals("user-2", dao.transaction?.paidByUserId)
        assertEquals("""["user-1","user-2"]""", dao.transaction?.participantUserIdsJson)
        assertEquals(TX_ID, cursors[TRANSACTION_CURSOR_KEY]?.entityId)
    }

    @Test
    fun incremental_pull_with_different_remote_split_detects_pending_local_conflict() = runTest {
        val cursors = mutableMapOf(TRANSACTION_CURSOR_KEY to storedTransactionCursor())
        val local = localPendingTransaction(
            paidByUserId = "user-1",
            participantUserIdsJson = """["user-1","user-2"]""",
        )
        val dao = RecordingTransactionRemoteSyncDao(transaction = local, cursors = cursors)
        val remote = FakeRemoteTransactionDataSource(
            transaction = remoteTransaction(
                version = 3L,
                paidByUserId = "user-2",
                participantUserIds = listOf("user-1", "user-2"),
            ),
        )

        val result = IncrementalRemoteSync(remote, dao, FakeSyncStateDao(cursors)) { RECEIVED_AT }
            .pullFor(EntityId(USER_ID))

        assertEquals(0, result.appliedCount)
        assertEquals(1, result.conflictCount)
        assertEquals("CONFLICT", dao.transaction?.sync?.syncStatus)
        val conflict = assertNotNull(dao.conflict)
        assertEquals(1L, conflict.localVersion)
        assertEquals(3L, conflict.remoteVersion)
        assertTrue(conflict.localPayloadJson.contains("paid_by_user_id") && conflict.localPayloadJson.contains("user-1"))
        assertTrue(conflict.remotePayloadJson.contains("paid_by_user_id") && conflict.remotePayloadJson.contains("user-2"))
    }

    private class RecordingTransactionWriter(
        var transactionResult: ConditionalRemoteWriteResult<TransactionDto> = ConditionalRemoteWriteResult.NotFound,
    ) : IdempotentConditionalRemoteWriter {
        var lastOperationId: String? = null
        var lastOperation: RemoteWriteOperation? = null
        var lastBaseVersion: Long? = null
        var lastTransactionDto: TransactionDto? = null

        override suspend fun writeTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: TransactionDto,
        ): ConditionalRemoteWriteResult<TransactionDto> {
            lastOperationId = operationId
            lastOperation = operation
            lastBaseVersion = baseVersion
            lastTransactionDto = dto
            return transactionResult
        }

        override suspend fun writeProfile(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: ProfileDto,
        ): ConditionalRemoteWriteResult<ProfileDto> = error("Test kapsamı dışı")

        override suspend fun writeCategory(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: CategoryDto,
        ): ConditionalRemoteWriteResult<CategoryDto> = error("Test kapsamı dışı")

        override suspend fun writeBudget(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: BudgetDto,
        ): ConditionalRemoteWriteResult<BudgetDto> = error("Test kapsamı dışı")

        override suspend fun writeRecurringTransaction(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: RecurringTransactionDto,
        ): ConditionalRemoteWriteResult<RecurringTransactionDto> = error("Test kapsamı dışı")

        override suspend fun writeSubscription(
            operationId: String,
            operation: RemoteWriteOperation,
            baseVersion: Long?,
            dto: SubscriptionDto,
        ): ConditionalRemoteWriteResult<SubscriptionDto> = error("Test kapsamı dışı")
    }

    private class RecordingTransactionRemoteSyncDao(
        var transaction: TransactionEntity? = null,
        val cursors: MutableMap<String, SyncCursorEntity> = mutableMapOf(),
    ) : RemoteSyncDao {
        var conflict: SyncConflictEntity? = null

        override suspend fun getProfileRow(id: String): UserProfileEntity? = null
        override suspend fun getCategoryRow(id: String): CategoryEntity? = null
        override suspend fun getTransactionRow(id: String): TransactionEntity? = transaction?.takeIf { it.id == id }
        override suspend fun getRecurringTransactionRow(id: String): RecurringTransactionEntity? = null
        override suspend fun getSubscriptionRow(id: String): SubscriptionEntity? = null
        override suspend fun getGoalRow(id: String): GoalEntity? = null
        override suspend fun getGoalContributionRow(id: String): GoalContributionEntity? = null
        override suspend fun getDebtRow(id: String): DebtEntity? = null
        override suspend fun getDebtPaymentRow(id: String): DebtPaymentEntity? = null
        override suspend fun getWorkspaceRow(id: String): WorkspaceEntity? = null
        override suspend fun getWorkspaceMemberRow(workspaceId: String, userId: String): WorkspaceMemberEntity? = null
        override suspend fun getWorkspaceMemberRows(workspaceId: String): List<WorkspaceMemberEntity> = emptyList()
        override suspend fun getAllKnownLiveWorkspaceIds(): List<String> = emptyList()
        override suspend fun getFirstOutboxOperationId(entityTypeCode: String, entityId: String): String? =
            if (transaction?.id == entityId) OP_ID else null
        override suspend fun countOutboxRows(entityTypeCode: String, entityId: String): Int = if (transaction?.id == entityId) 1 else 0
        override suspend fun upsertProfileRow(entity: UserProfileEntity) = Unit
        override suspend fun upsertWorkspaceRows(entities: List<WorkspaceEntity>) = Unit
        override suspend fun upsertWorkspaceMemberRows(entities: List<WorkspaceMemberEntity>) = Unit
        override suspend fun clearActiveWorkspaceIfMatches(profileId: String, workspaceId: String): Int = 0
        override suspend fun upsertCategoryRows(entities: List<CategoryEntity>) = Unit
        override suspend fun upsertTransactionRows(entities: List<TransactionEntity>) {
            transaction = entities.single()
        }
        override suspend fun upsertRecurringTransactionRows(entities: List<RecurringTransactionEntity>) = Unit
        override suspend fun upsertSubscriptionRows(entities: List<SubscriptionEntity>) = Unit
        override suspend fun upsertGoalRows(entities: List<GoalEntity>) = Unit
        override suspend fun upsertGoalContributionRows(entities: List<GoalContributionEntity>) = Unit
        override suspend fun upsertDebtRows(entities: List<DebtEntity>) = Unit
        override suspend fun upsertDebtPaymentRows(entities: List<DebtPaymentEntity>) = Unit
        override suspend fun upsertConflictRow(conflict: SyncConflictEntity) {
            this.conflict = conflict
        }
        override suspend fun upsertCursorRows(cursors: List<SyncCursorEntity>) {
            cursors.forEach { this.cursors[it.entityTypeCode] = it }
        }
        override suspend fun deleteConflictRow(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun markProfileConflict(entityId: String, error: String): Int = 1
        override suspend fun markCategoryConflict(entityId: String, error: String): Int = 1
        override suspend fun markTransactionConflict(entityId: String, error: String): Int {
            transaction = transaction?.copy(sync = transaction!!.sync.copy(syncStatus = "CONFLICT", lastSyncError = error))
            return 1
        }
        override suspend fun markRecurringTransactionConflict(entityId: String, error: String): Int = 1
        override suspend fun markSubscriptionConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalConflict(entityId: String, error: String): Int = 1
        override suspend fun markGoalContributionConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtConflict(entityId: String, error: String): Int = 1
        override suspend fun markDebtPaymentConflict(entityId: String, error: String): Int = 1
        override suspend fun deleteOutboxRows(entityTypeCode: String, entityId: String): Int = 0
        override suspend fun deleteOtherOutboxRows(entityTypeCode: String, entityId: String, keptOperationId: String): Int = 0
        override suspend fun resetConflictOperation(operationId: String, operationTypeCode: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseProfileForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseCategoryForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseRecurringTransactionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun rebaseSubscriptionForRetry(entityId: String, remoteVersion: Long, nowEpochMillis: Long): Int = 1
        override suspend fun getActiveWorkspaceTailOperation(workspaceId: String): SyncOperationEntity? = null
        override suspend fun markWorkspaceConflict(entityId: String, error: String): Int = 1
        override suspend fun getAllWorkspaceOperations(workspaceId: String): List<SyncOperationEntity> = emptyList()
        override suspend fun deleteSpecificWorkspaceOperations(workspaceId: String, operationIds: List<String>): Int = 0
        override suspend fun rebaseWorkspaceForRetry(workspaceId: String, syncStatus: String, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun resetWorkspaceConflictOperation(operationId: String, operationTypeCode: String, payloadJson: String?, remoteVersion: Long, nowEpochMillis: Long): Int = 0
        override suspend fun getConflictRow(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
    }

    private class FakeSyncStateDao(
        private val cursors: MutableMap<String, SyncCursorEntity>,
    ) : SyncStateDao {
        override suspend fun getCursor(entityTypeCode: String): SyncCursorEntity? = cursors[entityTypeCode]
        override suspend fun getWorkspaceMemberCursors(): List<SyncCursorEntity> = emptyList()
        override suspend fun getConflict(entityId: String): SyncConflictEntity? = null
        override suspend fun getConflict(entityTypeCode: String, entityId: String): SyncConflictEntity? = null
        override suspend fun getConflictsByEntityType(entityTypeCode: String): List<SyncConflictEntity> = emptyList()
        override suspend fun getAllConflicts(): List<SyncConflictEntity> = emptyList()
        override suspend fun upsertCursor(cursor: SyncCursorEntity) {
            cursors[cursor.entityTypeCode] = cursor
        }
        override suspend fun deleteConflict(entityTypeCode: String, entityId: String): Int = 0
        override fun observeConflicts(): Flow<List<SyncConflictEntity>> = flowOf(emptyList())
        override fun observeConflictCount(): Flow<Int> = flowOf(0)
        override suspend fun getConflictCount(): Int = 0
        override suspend fun upsertConflict(conflict: SyncConflictEntity) = Unit
        override fun observeLastSuccessfulSyncAt(userId: String): Flow<Long?> = flowOf(null)
        override suspend fun getUserState(userId: String): com.feniqo.mobile.data.local.entity.SyncUserStateEntity? = null
        override suspend fun upsertUserState(state: com.feniqo.mobile.data.local.entity.SyncUserStateEntity) = Unit
    }

    private class FakeRemoteTransactionDataSource(
        private val transaction: TransactionDto? = null,
    ) : CoreRemoteDataSource {
        override suspend fun fetchProfile(userId: String): ProfileDto? = null
        override suspend fun fetchCategories(query: CategoryRemoteQuery) = RemotePage<CategoryDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchTransactions(
            query: TransactionRemoteQuery,
        ): RemotePage<TransactionDto> {
            val items = listOfNotNull(transaction)
            return RemotePage(items, query.page, totalCount = items.size.toLong())
        }

        override suspend fun fetchBudgets(query: BudgetRemoteQuery) = RemotePage<BudgetDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery) = RemotePage<RecurringTransactionDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery) = RemotePage<SubscriptionDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchTransactionTags(transactionId: String) = emptyList<TransactionTagDto>()
        override suspend fun fetchGoals(query: GoalRemoteQuery) = RemotePage<GoalDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery) = RemotePage<GoalContributionDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebts(query: DebtRemoteQuery) = RemotePage<DebtDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery) = RemotePage<DebtPaymentDto>(emptyList(), query.page, totalCount = 0)
        override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest) = RemotePage<TagDto>(emptyList(), page, totalCount = 0)
        override suspend fun fetchWorkspaces(page: RemotePageRequest) = RemotePage<WorkspaceDto>(emptyList(), page, totalCount = 0)
        override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest) = RemotePage<WorkspaceMemberDto>(emptyList(), page, totalCount = 0)
        override suspend fun upsertProfile(dto: ProfileDto) = error("Test kapsamı dışı")
        override suspend fun upsertCategory(dto: CategoryDto) = error("Test kapsamı dışı")
        override suspend fun upsertTransaction(dto: TransactionDto) = error("Test kapsamı dışı")
        override suspend fun upsertBudget(dto: BudgetDto) = error("Test kapsamı dışı")
        override suspend fun upsertTag(dto: TagDto) = error("Test kapsamı dışı")
        override suspend fun upsertTransactionTag(dto: TransactionTagDto) = error("Test kapsamı dışı")
    }

    private companion object {
        const val OP_ID = "0123456789abcdef0123456789abcdef"
        const val TX_ID = "tx-12345678-abcd-1234-abcd-123456789abc"
        const val USER_ID = "fdbd49aa-640a-4ec5-9f1a-f348a949034c"
        const val CATEGORY_ID = "cat-12345678-abcd-1234-abcd-123456789abc"
        const val CREATED_AT = "2026-08-25T17:00:00Z"
        const val STORED_UPDATED_AT = "2026-08-25T16:00:00Z"
        const val STORED_ID = "stored-tx-id"
        const val TRANSACTION_CURSOR_KEY = "TRANSACTION"
        const val RECEIVED_AT = 1756141200000L

        fun storedTransactionCursor() = SyncCursorEntity(
            entityTypeCode = TRANSACTION_CURSOR_KEY,
            updatedAtEpochMillis = Instant.parse(STORED_UPDATED_AT).toEpochMilliseconds(),
            entityId = STORED_ID,
        )

        fun localPendingTransaction(
            paidByUserId: String = USER_ID,
            participantUserIdsJson: String = """["$USER_ID"]""",
        ) = TransactionEntity(
            id = TX_ID,
            ownerId = USER_ID,
            workspaceId = null,
            categoryId = CATEGORY_ID,
            amountMinor = 15000L,
            currencyCode = "TRY",
            typeCode = "EXPENSE",
            description = "Yerel Harcama",
            paymentMethodCode = "CASH",
            transactionDate = "2026-08-25",
            receiptPath = null,
            installmentGroupId = null,
            installmentNumber = null,
            totalInstallments = null,
            createdAtEpochMillis = 1000L,
            paidByUserId = paidByUserId,
            participantUserIdsJson = participantUserIdsJson,
            searchText = "",
            sync = SyncMetadata(
                syncStatus = "PENDING_UPDATE",
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1,
                baseVersion = 1,
                lastSyncError = null,
            ),
        )

        fun remoteTransaction(
            version: Long,
            paidByUserId: String? = null,
            participantUserIds: List<String>? = null,
        ) = TransactionDto(
            id = TX_ID,
            userId = USER_ID,
            workspaceId = null,
            categoryId = CATEGORY_ID,
            amountMinor = 15000L,
            currency = "TRY",
            type = "expense",
            description = "Uzak Harcama",
            paymentMethod = "cash",
            transactionDate = "2026-08-25",
            receiptPath = null,
            installmentGroupId = null,
            installmentNumber = null,
            totalInstallments = null,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
            deletedAt = null,
            version = version,
            paidByUserId = paidByUserId,
            participantUserIds = participantUserIds ?: listOf(USER_ID),
        )

        fun transactionSnapshotJson(
            id: String,
            version: Long?,
            paidByUserId: String? = null,
            participantUserIds: List<String>? = null,
        ): String = """
            {
                "id": "$id",
                "user_id": "$USER_ID",
                "workspace_id": null,
                "amount_minor": 15000,
                "currency": "TRY",
                "type": "expense",
                "category_id": "$CATEGORY_ID",
                "description": "Snap Harcama",
                "payment_method": "cash",
                "transaction_date": "2026-08-25",
                "receipt_path": null,
                "installment_group_id": null,
                "installment_number": null,
                "total_installments": null,
                "created_at": "$CREATED_AT",
                "version": ${version ?: "null"},
                "paid_by_user_id": ${if (paidByUserId != null) "\"$paidByUserId\"" else "null"},
                "participant_user_ids": ${if (participantUserIds != null) participantUserIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" } else "[]"}
            }
        """.trimIndent()

        fun transactionOperation(
            operationType: String = "CREATE",
            baseVersion: Long? = null,
            payloadJson: String? = null,
        ) = SyncOperationEntity(
            operationId = OP_ID,
            entityTypeCode = "TRANSACTION",
            entityId = TX_ID,
            operationTypeCode = operationType,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            predecessorOperationId = null,
            isBlocked = false,
            protocolVersion = 2,
            statusCode = "IN_FLIGHT",
            attemptCount = 1,
            lastError = null,
            nextAttemptAtEpochMillis = 1000L,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
    }
}
