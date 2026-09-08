package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto
import com.feniqo.mobile.data.remote.dto.GoalDto

import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationRedeemResultDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.dto.WorkspaceOwnershipTransferResultDto

import com.feniqo.mobile.domain.model.PaymentMethod
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

class SupabaseCoreRemoteDataSource(
    private val client: SupabaseClient,
) : CoreRemoteDataSource, ConditionalRemoteWriter, IdempotentConditionalRemoteWriter {

    private val rpcJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    override suspend fun fetchProfile(userId: String): ProfileDto? = client
        .from(PROFILES)
        .select {
            limit(1)
            filter { eq("id", userId) }
        }
        .decodeSingleOrNull()

    override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> {
        val result = client.from(CATEGORIES).select {
            count(Count.EXACT)
            range(query.page.range)
            if (query.updatedAfter == null) {
                order("name", Order.ASCENDING)
            } else {
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }
            filter {
                query.type?.let { eq("type", it.name.lowercase()) }
                query.updatedAfter?.let { gte("updated_at", it.updatedAt) }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = CategoryDto::id,
        )
    }

    override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> {
        val result = client.from(TRANSACTIONS).select {
            count(Count.EXACT)
            range(query.page.range)
            if (query.updatedAfter == null) {
                order("transaction_date", Order.DESCENDING)
                order("id", Order.ASCENDING)
            } else {
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }
            filter {
                query.fromDate?.let { gte("transaction_date", it.toString()) }
                query.toDate?.let { lte("transaction_date", it.toString()) }
                query.type?.let { eq("type", it.name.lowercase()) }
                query.categoryId?.let { eq("category_id", it.value) }
                query.paymentMethod?.let { eq("payment_method", it.toRemoteCode()) }
                query.updatedAfter?.let { gte("updated_at", it.updatedAt) }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = TransactionDto::id,
        )
    }

    override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> {
        val result = client.from(BUDGETS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("month", Order.DESCENDING)
            filter {
                query.month?.let { eq("month", it.value) }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toPage(query.page)
    }

    override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> {
        val result = client.from(RECURRING_TRANSACTIONS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = RecurringTransactionDto::id,
        )
    }

    override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> {
        val result = client.from(SUBSCRIPTIONS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = SubscriptionDto::id,
        )
    }

    override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> {
        val result = client.from(GOALS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = GoalDto::id,
        )
    }

    override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> {
        val result = client.from(GOAL_CONTRIBUTIONS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.goalId?.let { eq("goal_id", it.value) }
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = GoalContributionDto::id,
        )
    }

    override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> {
        val result = client.from(DEBTS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = DebtDto::id,
        )
    }

    override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> {
        val result = client.from(DEBT_PAYMENTS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.debtId?.let { eq("debt_id", it.value) }
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = { it.updatedAt ?: it.createdAt },
            id = DebtPaymentDto::id,
        )
    }

    override suspend fun fetchTags(



        scope: RemoteWorkspaceScope,
        page: RemotePageRequest,
    ): RemotePage<TagDto> {
        val result = client.from(TAGS).select {
            count(Count.EXACT)
            range(page.range)
            order("name", Order.ASCENDING)
            filter { applyWorkspaceScope(scope) }
        }
        return result.toPage(page)
    }

    override suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> {
        val result = client.from(WORKSPACES).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("id", cursor.entityId)
                        }
                    }
                }
            }
        }
        return result.toCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
            updatedAt = WorkspaceDto::updatedAt,
            id = WorkspaceDto::id,
        )
    }

    override suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> {
        val result = client.from(WORKSPACE_MEMBERS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("updated_at", Order.ASCENDING)
            order("workspace_id", Order.ASCENDING)
            order("user_id", Order.ASCENDING)
            filter {
                query.workspaceId?.let { eq("workspace_id", it.value) }
                query.updatedAfter?.let { cursor ->
                    or {
                        gt("updated_at", cursor.updatedAt)
                        and {
                            eq("updated_at", cursor.updatedAt)
                            gt("workspace_id", cursor.workspaceId)
                        }
                        and {
                            eq("updated_at", cursor.updatedAt)
                            eq("workspace_id", cursor.workspaceId)
                            gt("user_id", cursor.userId)
                        }
                    }
                }
            }
        }
        return result.toWorkspaceMemberCursorPage(
            request = query.page,
            cursor = query.updatedAfter,
        )
    }


    override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = client
        .from(TRANSACTION_TAGS)
        .select {
            filter { eq("transaction_id", transactionId) }
        }
        .decodeList()

    override suspend fun upsertProfile(dto: ProfileDto) = upsert(PROFILES, dto)
    override suspend fun upsertCategory(dto: CategoryDto) = upsert(CATEGORIES, dto)
    override suspend fun upsertTransaction(dto: TransactionDto) = upsert(TRANSACTIONS, dto)
    override suspend fun upsertBudget(dto: BudgetDto) = upsert(BUDGETS, dto)
    override suspend fun upsertTag(dto: TagDto) = upsert(TAGS, dto)

    override suspend fun upsertTransactionTag(dto: TransactionTagDto) {
        client.from(TRANSACTION_TAGS).upsert(dto) {
            onConflict = "transaction_id,tag_id"
        }
    }

    override suspend fun writeProfile(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: ProfileDto,
    ): ConditionalRemoteWriteResult<ProfileDto> = conditionalWrite(PROFILE, operation, baseVersion, dto)

    override suspend fun writeCategory(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: CategoryDto,
    ): ConditionalRemoteWriteResult<CategoryDto> = conditionalWrite(CATEGORY, operation, baseVersion, dto)

    override suspend fun writeTransaction(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: TransactionDto,
    ): ConditionalRemoteWriteResult<TransactionDto> = conditionalWrite(TRANSACTION, operation, baseVersion, dto)

    override suspend fun writeProfile(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: ProfileDto,
    ): ConditionalRemoteWriteResult<ProfileDto> =
        idempotentConditionalWrite(operationId, PROFILE, operation, baseVersion, dto)

    override suspend fun writeCategory(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: CategoryDto,
    ): ConditionalRemoteWriteResult<CategoryDto> =
        idempotentConditionalWrite(operationId, CATEGORY, operation, baseVersion, dto)

    override suspend fun writeTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: TransactionDto,
    ): ConditionalRemoteWriteResult<TransactionDto> =
        idempotentConditionalWrite(operationId, TRANSACTION, operation, baseVersion, dto)

    override suspend fun writeBudget(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: BudgetDto,
    ): ConditionalRemoteWriteResult<BudgetDto> =
        idempotentConditionalWrite(operationId, BUDGET, operation, baseVersion, dto)

    override suspend fun writeRecurringTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: RecurringTransactionDto,
    ): ConditionalRemoteWriteResult<RecurringTransactionDto> =
        idempotentConditionalWrite(operationId, RECURRING_TRANSACTION, operation, baseVersion, dto)

    override suspend fun writeSubscription(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: SubscriptionDto,
    ): ConditionalRemoteWriteResult<SubscriptionDto> =
        idempotentConditionalWrite(operationId, SUBSCRIPTION, operation, baseVersion, dto)

    override suspend fun writeGoal(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: GoalDto,
    ): ConditionalRemoteWriteResult<GoalDto> =
        idempotentConditionalWrite(operationId, GOAL, operation, baseVersion, dto)

    override suspend fun writeGoalContribution(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: GoalContributionDto,
    ): ConditionalRemoteWriteResult<GoalContributionSyncRecordDto> =
        idempotentConditionalWrite<GoalContributionDto, GoalContributionSyncRecordDto>(operationId, GOAL_CONTRIBUTION, operation, baseVersion, dto)

    override suspend fun writeDebt(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: DebtDto,
    ): ConditionalRemoteWriteResult<DebtDto> =
        idempotentConditionalWrite(operationId, DEBT, operation, baseVersion, dto)

    override suspend fun writeDebtPayment(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: DebtPaymentDto,
    ): ConditionalRemoteWriteResult<DebtPaymentSyncRecordDto> =
        idempotentConditionalWrite<DebtPaymentDto, DebtPaymentSyncRecordDto>(operationId, DEBT_PAYMENT, operation, baseVersion, dto)

    override suspend fun writeWorkspace(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceDto> =
        idempotentConditionalWrite(operationId, WORKSPACE, operation, baseVersion, payload)

    override suspend fun writeWorkspaceMember(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceMemberDto> {
        require(operation in setOf(RemoteWriteOperation.UPDATE, RemoteWriteOperation.DELETE)) {
            "WORKSPACE_MEMBER için generic outbox CREATE işlemi desteklenmez ($operationId)"
        }
        return idempotentConditionalWrite(operationId, WORKSPACE_MEMBER, operation, baseVersion, payload)
    }

    override suspend fun writeWorkspaceInvitation(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceInvitationDto> =
        idempotentConditionalWrite(operationId, WORKSPACE_INVITATION, operation, baseVersion, payload)

    override suspend fun redeemWorkspaceInvitation(token: String): WorkspaceInvitationRedeemResultDto {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "Davet kodu boş olamaz." }
        return client.postgrest.rpc(
            function = REDEEM_WORKSPACE_INVITATION_RPC,
            parameters = rpcJson.encodeToJsonElement(
                RedeemWorkspaceInvitationRpcParameters(pToken = cleanToken)
            ).jsonObject,
        ).decodeAs<WorkspaceInvitationRedeemResultDto>()
    }

    override suspend fun transferWorkspaceOwnership(
        workspaceId: String,
        targetUserId: String,
        expectedWorkspaceVersion: Long,
        expectedCurrentOwnerMemberVersion: Long,
        expectedTargetMemberVersion: Long,
    ): WorkspaceOwnershipTransferResultDto {
        val cleanWorkspaceId = workspaceId.trim()
        val cleanTargetUserId = targetUserId.trim()
        require(cleanWorkspaceId.isNotBlank()) { "Workspace ID boş olamaz." }
        require(cleanTargetUserId.isNotBlank()) { "Target user ID boş olamaz." }
        require(expectedWorkspaceVersion > 0L) { "Expected workspace version pozitif olmalıdır." }
        require(expectedCurrentOwnerMemberVersion > 0L) { "Expected actor member version pozitif olmalıdır." }
        require(expectedTargetMemberVersion > 0L) { "Expected target member version pozitif olmalıdır." }

        return client.postgrest.rpc(
            function = TRANSFER_WORKSPACE_OWNERSHIP_RPC,
            parameters = rpcJson.encodeToJsonElement(
                TransferWorkspaceOwnershipRpcParameters(
                    workspaceId = cleanWorkspaceId,
                    targetUserId = cleanTargetUserId,
                    expectedWorkspaceVersion = expectedWorkspaceVersion,
                    expectedCurrentOwnerMemberVersion = expectedCurrentOwnerMemberVersion,
                    expectedTargetMemberVersion = expectedTargetMemberVersion,
                )
            ).jsonObject,
        ).decodeAs<WorkspaceOwnershipTransferResultDto>()
    }




    private suspend inline fun <reified T : Any> conditionalWrite(
        entityType: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: T,
    ): ConditionalRemoteWriteResult<T> {
        val response = client.postgrest.rpc(
            function = CONDITIONAL_WRITE_RPC,
            parameters = rpcJson.encodeToJsonElement(
                ConditionalWriteRpcParameters(
                    entityType = entityType,
                    operation = operation.name,
                    baseVersion = baseVersion,
                    payload = rpcJson.encodeToJsonElement(dto),
                ),
            ).jsonObject,
        ).decodeAs<JsonObject>()

        val status = response[STATUS]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Koşullu Supabase yazma sonucu status taşımıyor.")
        val recordElement = response[RECORD]
        val record = recordElement
            ?.takeUnless { it is JsonNull }
            ?.let { rpcJson.decodeFromJsonElement<T>(it) }

        return when (status) {
            APPLIED -> ConditionalRemoteWriteResult.Applied(
                requireNotNull(record) { "APPLIED sonucu uzak kayıt taşımıyor." },
            )
            CONFLICT -> ConditionalRemoteWriteResult.Conflict(
                requireNotNull(record) { "CONFLICT sonucu uzak kayıt taşımıyor." },
            )
            NOT_FOUND -> ConditionalRemoteWriteResult.NotFound
            else -> throw IllegalStateException("Bilinmeyen koşullu Supabase yazma sonucu: $status")
        }
    }

    private suspend inline fun <reified P : Any, reified R : Any> idempotentConditionalWrite(
        operationId: String,
        entityType: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: P,
    ): ConditionalRemoteWriteResult<R> {
        val response = client.postgrest.rpc(
            function = SYNC_WRITE_V2_RPC,
            parameters = rpcJson.encodeToJsonElement(
                SyncWriteV2RpcParameters(
                    operationId = operationId,
                    entityType = entityType,
                    operation = operation.name,
                    baseVersion = baseVersion,
                    payload = rpcJson.encodeToJsonElement(dto),
                ),
            ).jsonObject,
        ).decodeAs<JsonObject>()

        val status = response[STATUS]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Koşullu Supabase V2 yazma sonucu status taşımıyor.")
        val recordElement = response[RECORD]
        val record = recordElement
            ?.takeUnless { it is JsonNull }
            ?.let { rpcJson.decodeFromJsonElement<R>(it) }

        return when (status) {
            APPLIED -> ConditionalRemoteWriteResult.Applied(
                requireNotNull(record) { "APPLIED sonucu uzak kayıt taşımıyor." },
            )
            CONFLICT -> ConditionalRemoteWriteResult.Conflict(
                requireNotNull(record) { "CONFLICT sonucu uzak kayıt taşımıyor." },
            )
            NOT_FOUND -> ConditionalRemoteWriteResult.NotFound
            else -> throw IllegalStateException("Bilinmeyen koşullu Supabase V2 yazma sonucu: $status")
        }
    }


    private suspend inline fun <reified T : Any> upsert(table: String, dto: T) {
        client.from(table).upsert(dto) {
            onConflict = "id"
            // Geçiş dönemindeki nullable metadata alanları sunucu değerlerini yanlışlıkla silmesin.
            stripNulls()
        }
    }

    private inline fun <reified T> PostgrestResult.toPage(request: RemotePageRequest): RemotePage<T> = RemotePage(
        items = decodeList(),
        request = request,
        totalCount = countOrNull(),
    )

    private inline fun <reified T> PostgrestResult.toCursorPage(
        request: RemotePageRequest,
        cursor: RemoteSyncCursor?,
        updatedAt: (T) -> String,
        id: (T) -> String,
    ): RemotePage<T> {
        val decoded = decodeList<T>()
        val filtered = if (cursor == null) {
            decoded
        } else {
            val cursorInstant = Instant.parse(cursor.updatedAt)
            decoded.filter { item ->
                val itemUpdatedAt = updatedAt(item)
                val itemInstant = Instant.parse(itemUpdatedAt)
                itemInstant > cursorInstant ||
                    (itemInstant == cursorInstant && id(item) > cursor.entityId)
            }
        }
        return RemotePage(filtered, request, countOrNull())
    }

    private fun PostgrestResult.toWorkspaceMemberCursorPage(
        request: RemotePageRequest,
        cursor: WorkspaceMemberSyncCursor?,
    ): RemotePage<WorkspaceMemberDto> {
        val decoded = decodeList<WorkspaceMemberDto>()
        val filtered = if (cursor == null) {
            decoded
        } else {
            val cursorInstant = Instant.parse(cursor.updatedAt)
            decoded.filter { item ->
                val itemInstant = Instant.parse(item.updatedAt)
                itemInstant > cursorInstant ||
                    (itemInstant == cursorInstant && item.workspaceId > cursor.workspaceId) ||
                    (itemInstant == cursorInstant && item.workspaceId == cursor.workspaceId && item.userId > cursor.userId)
            }
        }
        return RemotePage(filtered, request, countOrNull())
    }


    private fun PostgrestFilterBuilder.applyWorkspaceScope(scope: RemoteWorkspaceScope) {
        when (scope) {
            RemoteWorkspaceScope.All -> Unit
            RemoteWorkspaceScope.Personal -> exact("workspace_id", null)
            is RemoteWorkspaceScope.Workspace -> eq("workspace_id", scope.id.value)
        }
    }

    private fun PaymentMethod.toRemoteCode(): String = when (this) {
        PaymentMethod.CASH -> "cash"
        PaymentMethod.CREDIT_CARD -> "credit_card"
        PaymentMethod.DEBIT_CARD -> "debit_card"
        PaymentMethod.BANK_TRANSFER -> "bank_transfer"
        PaymentMethod.OTHER -> "other"
    }

    private companion object {
        const val CONDITIONAL_WRITE_RPC = "sync_write_v1"
        const val SYNC_WRITE_V2_RPC = "sync_write_v2"
        const val REDEEM_WORKSPACE_INVITATION_RPC = "redeem_workspace_invitation_v1"
        const val TRANSFER_WORKSPACE_OWNERSHIP_RPC = "transfer_workspace_ownership_v1"
        const val PROFILE = "PROFILE"
        const val CATEGORY = "CATEGORY"
        const val TRANSACTION = "TRANSACTION"
        const val BUDGET = "BUDGET"
        const val RECURRING_TRANSACTION = "RECURRING_TRANSACTION"
        const val SUBSCRIPTION = "SUBSCRIPTION"
        const val GOAL = "GOAL"
        const val GOAL_CONTRIBUTION = "GOAL_CONTRIBUTION"
        const val DEBT = "DEBT"
        const val DEBT_PAYMENT = "DEBT_PAYMENT"
        const val WORKSPACE = "WORKSPACE"
        const val WORKSPACE_MEMBER = "WORKSPACE_MEMBER"
        const val WORKSPACE_INVITATION = "WORKSPACE_INVITATION"

        const val STATUS = "status"

        const val RECORD = "record"
        const val APPLIED = "APPLIED"
        const val CONFLICT = "CONFLICT"
        const val NOT_FOUND = "NOT_FOUND"
        const val PROFILES = "profiles"
        const val CATEGORIES = "categories"
        const val TRANSACTIONS = "transactions"
        const val BUDGETS = "budgets"
        const val RECURRING_TRANSACTIONS = "recurring_transactions"
        const val SUBSCRIPTIONS = "subscriptions"
        const val GOALS = "goals"
        const val GOAL_CONTRIBUTIONS = "goal_contributions"
        const val DEBTS = "debts"
        const val DEBT_PAYMENTS = "debt_payments"
        const val TAGS = "tags"



        const val TRANSACTION_TAGS = "transaction_tags"
        const val WORKSPACES = "workspaces"
        const val WORKSPACE_MEMBERS = "workspace_members"
    }
}

@Serializable
private data class ConditionalWriteRpcParameters(
    @SerialName("p_entity_type")
    val entityType: String,
    @SerialName("p_operation")
    val operation: String,
    @SerialName("p_base_version")
    val baseVersion: Long?,
    @SerialName("p_payload")
    val payload: JsonElement,
)

@Serializable
private data class SyncWriteV2RpcParameters(
    @SerialName("p_operation_id")
    val operationId: String,
    @SerialName("p_entity_type")
    val entityType: String,
    @SerialName("p_operation")
    val operation: String,
    @SerialName("p_base_version")
    val baseVersion: Long?,
    @SerialName("p_payload")
    val payload: JsonElement,
)

@Serializable
private data class RedeemWorkspaceInvitationRpcParameters(
    @SerialName("p_token")
    val pToken: String,
)

@Serializable
private data class TransferWorkspaceOwnershipRpcParameters(
    @SerialName("p_workspace_id")
    val workspaceId: String,
    @SerialName("p_target_user_id")
    val targetUserId: String,
    @SerialName("p_expected_workspace_version")
    val expectedWorkspaceVersion: Long,
    @SerialName("p_expected_current_owner_member_version")
    val expectedCurrentOwnerMemberVersion: Long,
    @SerialName("p_expected_target_member_version")
    val expectedTargetMemberVersion: Long,
)
