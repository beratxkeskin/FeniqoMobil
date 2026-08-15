package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
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
) : CoreRemoteDataSource, ConditionalRemoteWriter {

    private val rpcJson = Json { ignoreUnknownKeys = true }

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

    override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> {
        val result = client.from(WORKSPACES).select {
            count(Count.EXACT)
            range(page.range)
            order("name", Order.ASCENDING)
        }
        return result.toPage(page)
    }

    override suspend fun fetchWorkspaceMembers(
        workspaceId: String,
        page: RemotePageRequest,
    ): RemotePage<WorkspaceMemberDto> {
        val result = client.from(WORKSPACE_MEMBERS).select {
            count(Count.EXACT)
            range(page.range)
            order("created_at", Order.ASCENDING)
            filter { eq("workspace_id", workspaceId) }
        }
        return result.toPage(page)
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
        ).decodeSingle<JsonObject>()

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
        const val PROFILE = "PROFILE"
        const val CATEGORY = "CATEGORY"
        const val TRANSACTION = "TRANSACTION"
        const val STATUS = "status"
        const val RECORD = "record"
        const val APPLIED = "APPLIED"
        const val CONFLICT = "CONFLICT"
        const val NOT_FOUND = "NOT_FOUND"
        const val PROFILES = "profiles"
        const val CATEGORIES = "categories"
        const val TRANSACTIONS = "transactions"
        const val BUDGETS = "budgets"
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
