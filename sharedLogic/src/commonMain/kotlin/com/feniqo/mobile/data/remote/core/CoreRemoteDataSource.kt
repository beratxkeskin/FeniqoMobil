package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto

interface CoreRemoteDataSource {
    suspend fun fetchProfile(userId: String): ProfileDto?
    suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto>
    suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto>
    suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto>
    suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto>
    suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto>
    suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto>
    suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto>

    suspend fun upsertProfile(dto: ProfileDto)
    suspend fun upsertCategory(dto: CategoryDto)
    suspend fun upsertTransaction(dto: TransactionDto)
    suspend fun upsertBudget(dto: BudgetDto)
    suspend fun upsertTag(dto: TagDto)
    suspend fun upsertTransactionTag(dto: TransactionTagDto)
}
