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
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceInvitationDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto
import kotlinx.serialization.json.JsonObject

/**
 * Outbox işlemini operation_id ile sunucuda idempotent ve koşullu olarak uygulayan V2 uzak yazıcı sözleşmesi.
 */
interface IdempotentConditionalRemoteWriter {
    suspend fun writeProfile(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: ProfileDto,
    ): ConditionalRemoteWriteResult<ProfileDto> = error("writeProfile not implemented in test fake")

    suspend fun writeCategory(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: CategoryDto,
    ): ConditionalRemoteWriteResult<CategoryDto> = error("writeCategory not implemented in test fake")

    suspend fun writeTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: TransactionDto,
    ): ConditionalRemoteWriteResult<TransactionDto> = error("writeTransaction not implemented in test fake")

    suspend fun writeBudget(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: BudgetDto,
    ): ConditionalRemoteWriteResult<BudgetDto> = error("writeBudget not implemented in test fake")

    suspend fun writeRecurringTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: RecurringTransactionDto,
    ): ConditionalRemoteWriteResult<RecurringTransactionDto> = error("writeRecurringTransaction not implemented in test fake")

    suspend fun writeSubscription(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: SubscriptionDto,
    ): ConditionalRemoteWriteResult<SubscriptionDto> = error("writeSubscription not implemented in test fake")

    suspend fun writeGoal(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: GoalDto,
    ): ConditionalRemoteWriteResult<GoalDto> = error("writeGoal not implemented in test fake")

    suspend fun writeGoalContribution(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: GoalContributionDto,
    ): ConditionalRemoteWriteResult<GoalContributionSyncRecordDto> = error("writeGoalContribution not implemented in test fake")

    suspend fun writeDebt(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: DebtDto,
    ): ConditionalRemoteWriteResult<DebtDto> = error("writeDebt not implemented in test fake")

    suspend fun writeDebtPayment(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: DebtPaymentDto,
    ): ConditionalRemoteWriteResult<DebtPaymentSyncRecordDto> = error("writeDebtPayment not implemented in test fake")

    suspend fun writeWorkspace(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceDto> = error("writeWorkspace not implemented in test fake")

    suspend fun writeWorkspaceMember(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceMemberDto> = error("writeWorkspaceMember not implemented in test fake")

    suspend fun writeWorkspaceInvitation(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payload: JsonObject,
    ): ConditionalRemoteWriteResult<WorkspaceInvitationDto> = error("writeWorkspaceInvitation not implemented in test fake")
}
