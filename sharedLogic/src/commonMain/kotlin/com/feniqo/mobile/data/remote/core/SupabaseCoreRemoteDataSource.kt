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
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult

class SupabaseCoreRemoteDataSource(
    private val client: SupabaseClient,
) : CoreRemoteDataSource {

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
            order("name", Order.ASCENDING)
            filter {
                query.type?.let { eq("type", it.name.lowercase()) }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toPage(query.page)
    }

    override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> {
        val result = client.from(TRANSACTIONS).select {
            count(Count.EXACT)
            range(query.page.range)
            order("transaction_date", Order.DESCENDING)
            order("id", Order.ASCENDING)
            filter {
                query.fromDate?.let { gte("transaction_date", it.toString()) }
                query.toDate?.let { lte("transaction_date", it.toString()) }
                query.type?.let { eq("type", it.name.lowercase()) }
                query.categoryId?.let { eq("category_id", it.value) }
                query.paymentMethod?.let { eq("payment_method", it.toRemoteCode()) }
                applyWorkspaceScope(query.workspaceScope)
            }
        }
        return result.toPage(query.page)
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
