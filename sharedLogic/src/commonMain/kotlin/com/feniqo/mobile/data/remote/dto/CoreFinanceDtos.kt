package com.feniqo.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    val name: String,
    val slug: String? = null,
    val type: String,
    val color: String,
    val icon: String? = null,
    @SerialName("is_default")
    val isDefault: Boolean = false,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

/**
 * Hedef mobil sözleşme yalnız `amount_minor: Long` ve private `receipt_path` kabul eder.
 * Eski `amount NUMERIC` ve public `receipt_url` alanları bilinçli olarak modellenmez.
 */
@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String = "TRY",
    val type: String,
    @SerialName("category_id")
    val categoryId: String,
    val description: String? = null,
    @SerialName("payment_method")
    val paymentMethod: String,
    @SerialName("transaction_date")
    val transactionDate: String,
    @SerialName("receipt_path")
    val receiptPath: String? = null,
    @SerialName("installment_number")
    val installmentNumber: Int? = null,
    @SerialName("total_installments")
    val totalInstallments: Int? = null,
    @SerialName("installment_group_id")
    val installmentGroupId: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class RecurringTransactionDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String = "TRY",
    val type: String,
    @SerialName("category_id")
    val categoryId: String,
    val description: String? = null,
    @SerialName("payment_method")
    val paymentMethod: String,
    val frequency: String,
    val interval: Int = 1,
    @SerialName("start_date")
    val startDate: String,
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("last_generated_date")
    val lastGeneratedDate: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class SubscriptionDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    val name: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String = "TRY",
    @SerialName("category_id")
    val categoryId: String? = null,
    val frequency: String,
    val interval: Int = 1,
    @SerialName("start_date")
    val startDate: String,
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("next_renewal_date")
    val nextRenewalDate: String,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class GoalDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    val name: String,
    @SerialName("target_amount_minor")
    val targetAmountMinor: Long,
    @SerialName("current_amount_minor")
    val currentAmountMinor: Long,
    val currency: String,
    @SerialName("target_date")
    val targetDate: String,
    @SerialName("color_hex")
    val colorHex: String,
    @SerialName("icon_key")
    val iconKey: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class GoalContributionDto(
    val id: String,
    @SerialName("goal_id")
    val goalId: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    @SerialName("occurred_on")
    val occurredOn: String,
    val note: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class DebtDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    val title: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    val type: String,
    @SerialName("due_date")
    val dueDate: String,
    val status: String,
    val description: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class DebtPaymentDto(
    val id: String,
    @SerialName("debt_id")
    val debtId: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    @SerialName("paid_on")
    val paidOn: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)


@Serializable
data class GoalContributionSyncRecordDto(
    val contribution: GoalContributionDto,
    val goal: GoalDto,
)

@Serializable
data class DebtPaymentSyncRecordDto(
    val payment: DebtPaymentDto,
    val debt: DebtDto,
)
