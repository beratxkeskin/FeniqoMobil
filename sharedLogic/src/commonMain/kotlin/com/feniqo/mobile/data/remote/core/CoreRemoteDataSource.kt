package com.feniqo.mobile.data.remote.core

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
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationRedeemResultDto
import com.feniqo.mobile.data.remote.dto.WorkspaceOwnershipTransferResultDto

interface CoreRemoteDataSource {
    suspend fun fetchProfile(userId: String): ProfileDto?
    suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto>
    suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto>
    suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto>
    suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto>
    suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto>
    suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto>
    suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto>
    suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto>
    suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto>
    suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto>





    suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> =
        error("fetchWorkspaces(query) is not implemented in this CoreRemoteDataSource implementation")
    suspend fun fetchWorkspaces(page: RemotePageRequest = RemotePageRequest()): RemotePage<WorkspaceDto> =
        fetchWorkspaces(WorkspaceRemoteQuery(page = page))

    suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> =
        error("fetchWorkspaceMembers(query) is not implemented in this CoreRemoteDataSource implementation")
    suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest = RemotePageRequest()): RemotePage<WorkspaceMemberDto> =
        fetchWorkspaceMembers(WorkspaceMemberRemoteQuery(page = page, workspaceId = com.feniqo.mobile.domain.model.EntityId(workspaceId)))

    suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto>

    suspend fun redeemWorkspaceInvitation(token: String): WorkspaceInvitationRedeemResultDto =
        error("redeemWorkspaceInvitation is not implemented in this CoreRemoteDataSource implementation")

    suspend fun transferWorkspaceOwnership(
        workspaceId: String,
        targetUserId: String,
        expectedWorkspaceVersion: Long,
        expectedCurrentOwnerMemberVersion: Long,
        expectedTargetMemberVersion: Long,
    ): WorkspaceOwnershipTransferResultDto =
        error("transferWorkspaceOwnership is not implemented in this CoreRemoteDataSource implementation")




    suspend fun upsertProfile(dto: ProfileDto)
    suspend fun upsertCategory(dto: CategoryDto)
    suspend fun upsertTransaction(dto: TransactionDto)
    suspend fun upsertBudget(dto: BudgetDto)
    suspend fun upsertTag(dto: TagDto)
    suspend fun upsertTransactionTag(dto: TransactionTagDto)
}
