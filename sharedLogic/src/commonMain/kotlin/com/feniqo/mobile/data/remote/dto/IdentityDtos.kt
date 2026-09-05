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

/** Supabase `workspaces` satırının ağ temsilidir. */
@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    @SerialName("normalized_name")
    val normalizedName: String,
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("type_code")
    val typeCode: String,
    @SerialName("currency_code")
    val currencyCode: String,
    val description: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long,
)

/** Supabase `workspace_members` satırının ağ temsilidir. */
@Serializable
data class WorkspaceMemberDto(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("role_code")
    val roleCode: String,
    @SerialName("joined_at")
    val joinedAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long,
)


