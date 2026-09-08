package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.remote.core.BudgetRemoteQuery
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceMemberRemoteQuery
import com.feniqo.mobile.data.remote.core.WorkspaceRemoteQuery
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
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationRedeemResultDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import com.feniqo.mobile.data.remote.dto.WorkspaceOwnershipTransferResultDto

class FakeWorkspaceCoreRemoteDataSource(
    var redeemHandler: (suspend (token: String) -> WorkspaceInvitationRedeemResultDto)? = null,
) : CoreRemoteDataSource {
    var transferOwnershipHandler: (suspend (
        workspaceId: String,
        targetUserId: String,
        expectedWorkspaceVersion: Long,
        expectedCurrentOwnerMemberVersion: Long,
        expectedTargetMemberVersion: Long,
    ) -> WorkspaceOwnershipTransferResultDto)? = null

    var redeemCalls = 0
    var lastRedeemToken: String? = null

    var transferCalls = 0
    var lastTransferWorkspaceId: String? = null
    var lastTransferTargetUserId: String? = null
    var lastTransferExpectedWorkspaceVersion: Long? = null
    var lastTransferExpectedCurrentOwnerMemberVersion: Long? = null
    var lastTransferExpectedTargetMemberVersion: Long? = null

    override suspend fun redeemWorkspaceInvitation(token: String): WorkspaceInvitationRedeemResultDto {
        redeemCalls++
        lastRedeemToken = token
        val handler = redeemHandler
        return if (handler != null) {
            handler(token)
        } else {
            error("redeemWorkspaceInvitation not stubbed in fake")
        }
    }

    override suspend fun transferWorkspaceOwnership(
        workspaceId: String,
        targetUserId: String,
        expectedWorkspaceVersion: Long,
        expectedCurrentOwnerMemberVersion: Long,
        expectedTargetMemberVersion: Long,
    ): WorkspaceOwnershipTransferResultDto {
        transferCalls++
        lastTransferWorkspaceId = workspaceId
        lastTransferTargetUserId = targetUserId
        lastTransferExpectedWorkspaceVersion = expectedWorkspaceVersion
        lastTransferExpectedCurrentOwnerMemberVersion = expectedCurrentOwnerMemberVersion
        lastTransferExpectedTargetMemberVersion = expectedTargetMemberVersion
        val handler = transferOwnershipHandler
        return if (handler != null) {
            handler(
                workspaceId,
                targetUserId,
                expectedWorkspaceVersion,
                expectedCurrentOwnerMemberVersion,
                expectedTargetMemberVersion,
            )
        } else {
            error("transferWorkspaceOwnership not stubbed in fake")
        }
    }

    override suspend fun fetchProfile(userId: String): ProfileDto? = null
    override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchWorkspaces(query: WorkspaceRemoteQuery): RemotePage<WorkspaceDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchWorkspaces(page: RemotePageRequest): RemotePage<WorkspaceDto> = RemotePage(emptyList(), page, 0)
    override suspend fun fetchWorkspaceMembers(query: WorkspaceMemberRemoteQuery): RemotePage<WorkspaceMemberDto> = RemotePage(emptyList(), query.page, 0)
    override suspend fun fetchWorkspaceMembers(workspaceId: String, page: RemotePageRequest): RemotePage<WorkspaceMemberDto> = RemotePage(emptyList(), page, 0)
    override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = RemotePage(emptyList(), page, 0)
    override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = emptyList()
    override suspend fun upsertProfile(dto: ProfileDto) = Unit
    override suspend fun upsertCategory(dto: CategoryDto) = Unit
    override suspend fun upsertTransaction(dto: TransactionDto) = Unit
    override suspend fun upsertBudget(dto: BudgetDto) = Unit
    override suspend fun upsertTag(dto: TagDto) = Unit
    override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
}
