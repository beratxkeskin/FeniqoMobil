package com.feniqo.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Supabase `profiles` satırının ağ temsilidir; snake_case yalnız DTO katmanında kalır. */
@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    @SerialName("full_name")
    val fullName: String? = null,
    val currency: String = "TRY",
    val theme: String = "system",
    val lang: String = "tr",
    @SerialName("active_workspace_id")
    val activeWorkspaceId: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val version: Long? = null,
)

/** Mevcut web şemasındaki `created_by`, domain'deki owner kimliğine dönüştürülür. */
@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    @SerialName("created_by")
    val createdBy: String?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)

/** Üyelik satırının teknik `id` alanı domain ilişkisini etkilemez. */
@Serializable
data class WorkspaceMemberDto(
    val id: String? = null,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("user_id")
    val userId: String,
    val role: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
)
