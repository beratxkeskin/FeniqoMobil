package com.feniqo.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BudgetDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("category_id")
    val categoryId: String,
    val month: String,
    @SerialName("limit_minor")
    val limitMinor: Long,
    val currency: String = "TRY",
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class TagDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    val name: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

@Serializable
data class TransactionTagDto(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("tag_id")
    val tagId: String,
)
