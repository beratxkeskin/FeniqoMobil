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
