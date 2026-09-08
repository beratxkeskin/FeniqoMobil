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

/** Supabase `workspace_invitations` satırının ağ temsilidir. Ham token içermez. */
@Serializable
data class WorkspaceInvitationDto(
    val id: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("inviter_id")
    val inviterId: String,
    @SerialName("role_code")
    val roleCode: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("max_uses")
    val maxUses: Int,
    @SerialName("uses_count")
    val usesCount: Int,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long,
)

/**
 * `redeem_workspace_invitation_v1` RPC dönüş sonucudur.
 * Token veya hash içermez; güvenli WorkspaceDto ve WorkspaceMemberDto taşır.
 */
@Serializable
data class WorkspaceInvitationRedeemResultDto(
    val workspace: WorkspaceDto,
    val member: WorkspaceMemberDto,
)

/**
 * `transfer_workspace_ownership_v1` RPC dönüş sonucudur.
 * Atomik güncellenen WorkspaceDto, eski sahip (artık EDITOR) ve yeni sahip (artık OWNER) taşır.
 */
@Serializable
data class WorkspaceOwnershipTransferResultDto(
    val workspace: WorkspaceDto,
    @SerialName("actor_member")
    val actorMember: WorkspaceMemberDto,
    @SerialName("target_member")
    val targetMember: WorkspaceMemberDto,
)
