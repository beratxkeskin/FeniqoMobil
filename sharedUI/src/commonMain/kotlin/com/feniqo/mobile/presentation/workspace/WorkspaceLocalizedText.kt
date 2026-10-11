package com.feniqo.mobile.presentation.workspace

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.currency_eur
import feniqomobil.sharedui.generated.resources.currency_gbp
import feniqomobil.sharedui.generated.resources.currency_try
import feniqomobil.sharedui.generated.resources.currency_usd
import feniqomobil.sharedui.generated.resources.workspace_create_name_error_blank
import feniqomobil.sharedui.generated.resources.workspace_create_type_personal_desc
import feniqomobil.sharedui.generated.resources.workspace_create_type_shared_desc
import feniqomobil.sharedui.generated.resources.workspace_details_member_self_suffix
import feniqomobil.sharedui.generated.resources.workspace_join_error_code_blank
import feniqomobil.sharedui.generated.resources.workspace_join_error_code_invalid
import feniqomobil.sharedui.generated.resources.workspace_role_editor
import feniqomobil.sharedui.generated.resources.workspace_role_editor_desc
import feniqomobil.sharedui.generated.resources.workspace_role_owner
import feniqomobil.sharedui.generated.resources.workspace_role_viewer
import feniqomobil.sharedui.generated.resources.workspace_role_viewer_desc
import feniqomobil.sharedui.generated.resources.workspace_settlement_member_fallback
import feniqomobil.sharedui.generated.resources.workspace_settlement_member_you
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_creditor
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_debtor
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_settled
import feniqomobil.sharedui.generated.resources.workspace_type_personal
import feniqomobil.sharedui.generated.resources.workspace_type_shared
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun WorkspaceCreateFormFieldError.toLocalizedResource(): StringResource = when (this) {
    WorkspaceCreateFormFieldError.NAME_BLANK -> Res.string.workspace_create_name_error_blank
}

fun WorkspaceJoinFormFieldError.toLocalizedResource(): StringResource = when (this) {
    WorkspaceJoinFormFieldError.CODE_BLANK -> Res.string.workspace_join_error_code_blank
    WorkspaceJoinFormFieldError.CODE_INVALID -> Res.string.workspace_join_error_code_invalid
}

fun WorkspaceRole.toLocalizedResource(): StringResource = when (this) {
    WorkspaceRole.OWNER -> Res.string.workspace_role_owner
    WorkspaceRole.EDITOR -> Res.string.workspace_role_editor
    WorkspaceRole.VIEWER -> Res.string.workspace_role_viewer
}

fun WorkspaceRole.toLocalizedDescriptionResource(): StringResource? = when (this) {
    WorkspaceRole.OWNER -> null
    WorkspaceRole.EDITOR -> Res.string.workspace_role_editor_desc
    WorkspaceRole.VIEWER -> Res.string.workspace_role_viewer_desc
}

fun WorkspaceType.toLocalizedResource(): StringResource = when (this) {
    WorkspaceType.SHARED -> Res.string.workspace_type_shared
    WorkspaceType.PERSONAL -> Res.string.workspace_type_personal
}

fun WorkspaceType.toLocalizedDescriptionResource(): StringResource = when (this) {
    WorkspaceType.SHARED -> Res.string.workspace_create_type_shared_desc
    WorkspaceType.PERSONAL -> Res.string.workspace_create_type_personal_desc
}

fun MemberBalanceStatus.toLocalizedResource(): StringResource = when (this) {
    MemberBalanceStatus.CREDITOR -> Res.string.workspace_settlement_status_creditor
    MemberBalanceStatus.DEBTOR -> Res.string.workspace_settlement_status_debtor
    MemberBalanceStatus.SETTLED -> Res.string.workspace_settlement_status_settled
}

fun Currency.toLocalizedDisplayNameResource(): StringResource = when (this) {
    Currency.TRY -> Res.string.currency_try
    Currency.USD -> Res.string.currency_usd
    Currency.EUR -> Res.string.currency_eur
    Currency.GBP -> Res.string.currency_gbp
}

/**
 * Çalışma alanı üyesi için locale-aware ad çözümleyici.
 *
 * - Eğer [isCurrentUser] true ve [displayName] doluysa:
 *   [includeSelfSuffix] true ise "Ahmet (sen)" / "Ahmet (you)" üretir.
 *   [includeSelfSuffix] false ise "Ahmet" üretir.
 * - Eğer [isCurrentUser] true ve [displayName] boş veya null ise:
 *   Doğrudan "Siz" / "You" üretir (asla "Siz (sen)" veya "You (you)" üretmez).
 * - Eğer [isCurrentUser] false ve [displayName] doluysa:
 *   "Mehmet" üretir.
 * - Eğer [isCurrentUser] false ve [displayName] boş veya null ise:
 *   "Kullanıcı 12345678" / "User 12345678" üretir.
 */
@Composable
fun resolveWorkspaceMemberDisplayName(
    userId: EntityId,
    displayName: String?,
    isCurrentUser: Boolean,
    includeSelfSuffix: Boolean = false,
): String {
    val cleanName = displayName?.trim()?.takeIf { it.isNotBlank() }
    val shortId = userId.value.take(8)

    return if (isCurrentUser) {
        if (cleanName != null) {
            if (includeSelfSuffix) {
                stringResource(Res.string.workspace_details_member_self_suffix, cleanName)
            } else {
                cleanName
            }
        } else {
            stringResource(Res.string.workspace_settlement_member_you)
        }
    } else {
        cleanName ?: stringResource(Res.string.workspace_settlement_member_fallback, shortId)
    }
}

/**
 * Üye avatarları için ilk harfi dinamik çözümler.
 */
fun resolveWorkspaceMemberInitial(
    resolvedDisplayName: String,
): String = resolvedDisplayName.firstOrNull()?.uppercase() ?: "U"

@Composable
fun WorkspaceMemberUiModel.resolveDisplayName(includeSelfSuffix: Boolean = false): String =
    resolveWorkspaceMemberDisplayName(
        userId = userId,
        displayName = displayName,
        isCurrentUser = isCurrentUser,
        includeSelfSuffix = includeSelfSuffix,
    )

@Composable
fun WorkspaceMemberBalanceUiModel.resolveDisplayName(includeSelfSuffix: Boolean = false): String =
    resolveWorkspaceMemberDisplayName(
        userId = userId,
        displayName = displayName,
        isCurrentUser = isCurrentUser,
        includeSelfSuffix = includeSelfSuffix,
    )
