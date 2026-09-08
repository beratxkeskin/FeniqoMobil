package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CreateWorkspaceCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UpdateWorkspaceCommand
import com.feniqo.mobile.domain.model.WorkspaceInvitation
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import kotlinx.datetime.Instant

/**
 * Çalışma alanı yetki aksiyonları sözlüğü.
 */
enum class WorkspacePermission {
    VIEW_WORKSPACE,
    VIEW_MEMBERS,
    VIEW_FINANCIAL_RECORDS,
    CREATE_FINANCIAL_RECORD,
    UPDATE_FINANCIAL_RECORD,
    SOFT_DELETE_FINANCIAL_RECORD,
    CREATE_OR_CANCEL_INVITE,
    CHANGE_MEMBER_ROLE,
    REMOVE_MEMBER,
    UPDATE_WORKSPACE_SETTINGS,
    DELETE_WORKSPACE,
    TRANSFER_OWNERSHIP,
}

/**
 * Rol tabanlı izin politikası.
 * - OWNER: Tüm izinlere sahiptir.
 * - EDITOR: Yalnızca görüntüleme ve finansal kayıt oluşturma/düzenleme/soft-delete izinlerine sahiptir.
 * - VIEWER: Yalnızca görüntüleme izinlerine sahiptir.
 */
object WorkspacePermissionPolicy {
    fun can(role: WorkspaceRole, permission: WorkspacePermission): Boolean {
        return when (role) {
            WorkspaceRole.OWNER -> true
            WorkspaceRole.EDITOR -> when (permission) {
                WorkspacePermission.VIEW_WORKSPACE,
                WorkspacePermission.VIEW_MEMBERS,
                WorkspacePermission.VIEW_FINANCIAL_RECORDS,
                WorkspacePermission.CREATE_FINANCIAL_RECORD,
                WorkspacePermission.UPDATE_FINANCIAL_RECORD,
                WorkspacePermission.SOFT_DELETE_FINANCIAL_RECORD -> true
                WorkspacePermission.CREATE_OR_CANCEL_INVITE,
                WorkspacePermission.CHANGE_MEMBER_ROLE,
                WorkspacePermission.REMOVE_MEMBER,
                WorkspacePermission.UPDATE_WORKSPACE_SETTINGS,
                WorkspacePermission.DELETE_WORKSPACE,
                WorkspacePermission.TRANSFER_OWNERSHIP -> false
            }
            WorkspaceRole.VIEWER -> when (permission) {
                WorkspacePermission.VIEW_WORKSPACE,
                WorkspacePermission.VIEW_MEMBERS,
                WorkspacePermission.VIEW_FINANCIAL_RECORDS -> true
                WorkspacePermission.CREATE_FINANCIAL_RECORD,
                WorkspacePermission.UPDATE_FINANCIAL_RECORD,
                WorkspacePermission.SOFT_DELETE_FINANCIAL_RECORD,
                WorkspacePermission.CREATE_OR_CANCEL_INVITE,
                WorkspacePermission.CHANGE_MEMBER_ROLE,
                WorkspacePermission.REMOVE_MEMBER,
                WorkspacePermission.UPDATE_WORKSPACE_SETTINGS,
                WorkspacePermission.DELETE_WORKSPACE,
                WorkspacePermission.TRANSFER_OWNERSHIP -> false
            }
        }
    }
}

/**
 * Çalışma alanı girdi ve iş kurallarına ait tipli doğrulama hata kodlarıdır.
 */
enum class WorkspaceValidationError {
    NAME_BLANK,
    INVITATION_ROLE_CANNOT_BE_OWNER,
    INVITATION_MAX_USES_NON_POSITIVE,
    INVITATION_EXPIRES_AT_MUST_BE_AFTER_CREATED_AT,
    ACTOR_NOT_MEMBER,
    ACTOR_NOT_PERMITTED,
    CANNOT_CHANGE_OWN_ROLE,
    OWNER_ROLE_CHANGE_REQUIRES_TRANSFER,
    TARGET_MEMBER_NOT_FOUND,
    CANNOT_REMOVE_LAST_OWNER,
    CANNOT_REMOVE_SELF_MEMBER,
    CANNOT_REMOVE_WORKSPACE_OWNER,
    CANNOT_LEAVE_AS_LAST_OWNER,
    TRANSFER_ACTOR_NOT_OWNER,
    TRANSFER_TARGET_ALREADY_OWNER,
}

/**
 * Çalışma alanı doğrulama sonucu kapalı sözleşmesidir.
 */
sealed interface WorkspaceValidationResult<out T> {
    data class Valid<T>(val value: T) : WorkspaceValidationResult<T>
    data class Invalid(val error: WorkspaceValidationError) : WorkspaceValidationResult<Nothing>
}

/**
 * Platformdan bağımsız, saf çalışma alanı girdi ve yetkilendirme ön-kontrol kuralları.
 * Not: Bu kurallar istemci ön-kontrolüdür; SQL/RPC RLS ve backend güvenlik kararlarının yerine geçmez.
 */
object WorkspaceValidationRules {

    /**
     * Boş metinleri null yapar, metnin başındaki ve sonundaki boşlukları temizler.
     */
    fun normalizeDescription(value: String?): String? = value?.trim()?.ifBlank { null }

    fun validateName(name: String): WorkspaceValidationResult<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.NAME_BLANK)
        }
        return WorkspaceValidationResult.Valid(trimmed)
    }

    fun validateDescription(description: String?): WorkspaceValidationResult<String?> {
        return WorkspaceValidationResult.Valid(normalizeDescription(description))
    }

    fun validateCreateWorkspace(
        name: String,
        type: WorkspaceType = WorkspaceType.SHARED,
        currency: Currency = Currency.TRY,
        description: String? = null,
    ): WorkspaceValidationResult<CreateWorkspaceCommand> {
        val nameResult = validateName(name)
        if (nameResult is WorkspaceValidationResult.Invalid) return nameResult

        val validName = (nameResult as WorkspaceValidationResult.Valid).value
        val validDesc = normalizeDescription(description)

        return WorkspaceValidationResult.Valid(
            CreateWorkspaceCommand(
                name = validName,
                type = type,
                currency = currency,
                description = validDesc,
            )
        )
    }

    fun validateUpdateWorkspace(
        id: EntityId,
        actorUserId: EntityId,
        name: String,
        type: WorkspaceType,
        currency: Currency,
        description: String? = null,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<UpdateWorkspaceCommand> {
        val actorMember = currentMembers.firstOrNull { it.first == actorUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (!WorkspacePermissionPolicy.can(actorMember.second, WorkspacePermission.UPDATE_WORKSPACE_SETTINGS)) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED)
        }

        val nameResult = validateName(name)
        if (nameResult is WorkspaceValidationResult.Invalid) return nameResult

        val validName = (nameResult as WorkspaceValidationResult.Valid).value
        val validDesc = normalizeDescription(description)

        return WorkspaceValidationResult.Valid(
            UpdateWorkspaceCommand(
                id = id,
                name = validName,
                type = type,
                currency = currency,
                description = validDesc,
            )
        )
    }

    fun validateInvitationParams(
        actorUserId: EntityId,
        targetRole: WorkspaceRole,
        createdAt: Instant,
        expiresAt: Instant,
        maxUses: Int,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<Unit> {
        val actorMember = currentMembers.firstOrNull { it.first == actorUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (!WorkspacePermissionPolicy.can(actorMember.second, WorkspacePermission.CREATE_OR_CANCEL_INVITE)) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED)
        }
        if (targetRole == WorkspaceRole.OWNER) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_ROLE_CANNOT_BE_OWNER)
        }
        if (maxUses <= 0) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_MAX_USES_NON_POSITIVE)
        }
        if (expiresAt <= createdAt) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_EXPIRES_AT_MUST_BE_AFTER_CREATED_AT)
        }
        return WorkspaceValidationResult.Valid(Unit)
    }

    fun validateMemberRoleChange(
        actorUserId: EntityId,
        targetUserId: EntityId,
        newRole: WorkspaceRole,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<Unit> {
        val actorMember = currentMembers.firstOrNull { it.first == actorUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (!WorkspacePermissionPolicy.can(actorMember.second, WorkspacePermission.CHANGE_MEMBER_ROLE)) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED)
        }
        if (actorUserId == targetUserId) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE)
        }
        if (newRole == WorkspaceRole.OWNER) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.OWNER_ROLE_CHANGE_REQUIRES_TRANSFER)
        }

        val targetMember = currentMembers.firstOrNull { it.first == targetUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND)

        // Eğer hedef üye OWNER ise ve rolü düşürülüyorsa, geride en az bir OWNER kalmalı
        if (targetMember.second == WorkspaceRole.OWNER) {
            val remainingOwners = currentMembers.count { it.first != targetUserId && it.second == WorkspaceRole.OWNER }
            if (remainingOwners < 1) {
                return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_REMOVE_LAST_OWNER)
            }
        }

        return WorkspaceValidationResult.Valid(Unit)
    }

    fun validateMemberRemoval(
        actorUserId: EntityId,
        targetUserId: EntityId,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<Unit> {
        val actorMember = currentMembers.firstOrNull { it.first == actorUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (actorMember.second != WorkspaceRole.OWNER || !WorkspacePermissionPolicy.can(actorMember.second, WorkspacePermission.REMOVE_MEMBER)) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED)
        }

        if (actorUserId == targetUserId) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_REMOVE_SELF_MEMBER)
        }

        val targetMember = currentMembers.firstOrNull { it.first == targetUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND)

        if (targetMember.second == WorkspaceRole.OWNER) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_REMOVE_WORKSPACE_OWNER)
        }

        return WorkspaceValidationResult.Valid(Unit)
    }

    fun validateLeaveWorkspace(
        userId: EntityId,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<Unit> {
        val userMember = currentMembers.firstOrNull { it.first == userId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (userMember.second == WorkspaceRole.OWNER) {
            val remainingOwners = currentMembers.count { it.first != userId && it.second == WorkspaceRole.OWNER }
            if (remainingOwners < 1) {
                return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_LEAVE_AS_LAST_OWNER)
            }
        }
        return WorkspaceValidationResult.Valid(Unit)
    }

    /**
     * Sahiplik devri ön-koşul kontrolü.
     * Gerçek sahiplik devri sonraki katmanda kaynak OWNER'ın düşürülmesi + hedefin OWNER yapılması olarak
     * tek atomik mutation/RPC ile uygulanacaktır.
     */
    fun validateOwnershipTransfer(
        actorUserId: EntityId,
        targetUserId: EntityId,
        currentMembers: List<Pair<EntityId, WorkspaceRole>>,
    ): WorkspaceValidationResult<Unit> {
        val actorMember = currentMembers.firstOrNull { it.first == actorUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER)

        if (actorMember.second != WorkspaceRole.OWNER || !WorkspacePermissionPolicy.can(actorMember.second, WorkspacePermission.TRANSFER_OWNERSHIP)) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.TRANSFER_ACTOR_NOT_OWNER)
        }
        if (actorUserId == targetUserId) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE)
        }

        val targetMember = currentMembers.firstOrNull { it.first == targetUserId }
            ?: return WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND)

        if (targetMember.second == WorkspaceRole.OWNER) {
            return WorkspaceValidationResult.Invalid(WorkspaceValidationError.TRANSFER_TARGET_ALREADY_OWNER)
        }

        return WorkspaceValidationResult.Valid(Unit)
    }
}
