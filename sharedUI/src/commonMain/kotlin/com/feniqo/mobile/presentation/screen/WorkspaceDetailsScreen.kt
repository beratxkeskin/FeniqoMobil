package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.workspace.WorkspaceDetailsUiState
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel
import com.feniqo.mobile.presentation.workspace.resolveDisplayName
import com.feniqo.mobile.presentation.workspace.resolveWorkspaceMemberInitial
import com.feniqo.mobile.presentation.workspace.toLocalizedDescriptionResource
import com.feniqo.mobile.presentation.workspace.toLocalizedResource
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.workspace_back
import feniqomobil.sharedui.generated.resources.workspace_cancel
import feniqomobil.sharedui.generated.resources.workspace_close_error
import feniqomobil.sharedui.generated.resources.workspace_details_action_change_role
import feniqomobil.sharedui.generated.resources.workspace_details_action_remove_member
import feniqomobil.sharedui.generated.resources.workspace_details_create_invite_button
import feniqomobil.sharedui.generated.resources.workspace_details_current_role_label
import feniqomobil.sharedui.generated.resources.workspace_details_invite_close_button
import feniqomobil.sharedui.generated.resources.workspace_details_invite_copied_button
import feniqomobil.sharedui.generated.resources.workspace_details_invite_copied_toast
import feniqomobil.sharedui.generated.resources.workspace_details_invite_copy_button
import feniqomobil.sharedui.generated.resources.workspace_details_invite_dialog_desc
import feniqomobil.sharedui.generated.resources.workspace_details_invite_dialog_title
import feniqomobil.sharedui.generated.resources.workspace_details_leave_button
import feniqomobil.sharedui.generated.resources.workspace_details_leave_dialog_confirm
import feniqomobil.sharedui.generated.resources.workspace_details_leave_dialog_desc
import feniqomobil.sharedui.generated.resources.workspace_details_leave_dialog_title
import feniqomobil.sharedui.generated.resources.workspace_details_member_menu_cd
import feniqomobil.sharedui.generated.resources.workspace_details_member_self_suffix
import feniqomobil.sharedui.generated.resources.workspace_details_members_plural
import feniqomobil.sharedui.generated.resources.workspace_details_not_found_desc
import feniqomobil.sharedui.generated.resources.workspace_details_not_found_title
import feniqomobil.sharedui.generated.resources.workspace_details_owner_must_transfer_warning
import feniqomobil.sharedui.generated.resources.workspace_details_permission_editor
import feniqomobil.sharedui.generated.resources.workspace_details_permission_viewer
import feniqomobil.sharedui.generated.resources.workspace_details_remove_dialog_confirm
import feniqomobil.sharedui.generated.resources.workspace_details_remove_dialog_desc
import feniqomobil.sharedui.generated.resources.workspace_details_remove_dialog_title
import feniqomobil.sharedui.generated.resources.workspace_details_role_dialog_confirm
import feniqomobil.sharedui.generated.resources.workspace_details_role_dialog_current
import feniqomobil.sharedui.generated.resources.workspace_details_role_dialog_title
import feniqomobil.sharedui.generated.resources.workspace_details_settlement_action_desc
import feniqomobil.sharedui.generated.resources.workspace_details_settlement_action_title
import feniqomobil.sharedui.generated.resources.workspace_details_title
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_confirm_button
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_confirm_desc
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_confirm_title
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_dialog_empty
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_dialog_title
import feniqomobil.sharedui.generated.resources.workspace_details_transfer_ownership_button
import feniqomobil.sharedui.generated.resources.workspace_details_type_personal
import feniqomobil.sharedui.generated.resources.workspace_details_type_shared
import feniqomobil.sharedui.generated.resources.workspace_join_security_notice
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Çalışma Alanı Detay Ekranı.
 * Onaylı Görsel 02 (Sahip & İzleyici Görünümleri) ve Görsel 03 (Diyaloglar) tasarımına sadık kalınarak hazırlanmıştır.
 */
@Composable
fun WorkspaceDetailsScreen(
    state: WorkspaceDetailsUiState,
    onBack: () -> Unit,
    onRequestLeave: () -> Unit,
    onDismissLeaveConfirmation: () -> Unit,
    onConfirmLeave: () -> Unit,
    onDismissError: () -> Unit,
    onCreateInvite: () -> Unit = {},
    onDismissInviteCodeDialog: () -> Unit = {},
    onRequestChangeMemberRole: (member: WorkspaceMemberUiModel, newRole: WorkspaceRole) -> Unit = { _, _ -> },
    onSelectPendingRole: (newRole: WorkspaceRole) -> Unit = {},
    onDismissRoleChangeConfirmation: () -> Unit = {},
    onConfirmRoleChange: () -> Unit = {},
    onRequestOwnershipTransfer: (member: WorkspaceMemberUiModel) -> Unit = {},
    onDismissOwnershipTransferConfirmation: () -> Unit = {},
    onConfirmOwnershipTransfer: () -> Unit = {},
    onRequestRemoveMember: (member: WorkspaceMemberUiModel) -> Unit = {},
    onDismissMemberRemovalConfirmation: () -> Unit = {},
    onConfirmMemberRemoval: () -> Unit = {},
    onNavigateToSettlement: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOwnershipTransferSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Üst Bar: Geri Dönüş ve Başlık
            val backText = stringResource(Res.string.workspace_back)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !state.isLeaving && !state.isTransferringOwnership && !state.isRemovingMember,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = backText },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.workspace_details_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Hata Mesajı Bannerı
            if (state.errorMessage != null) {
                DetailsErrorBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    enabled = !state.isLeaving,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FeniqoSageGreen)
                    }
                }
                state.workspace == null -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(Res.string.workspace_details_not_found_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(Res.string.workspace_details_not_found_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            ) {
                                Text(backText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    val workspace = state.workspace
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        // 1. Grafit Koyu Hero Kartı (Görsel 02)
                        item(key = "hero_workspace_card") {
                            val typeName = if (workspace.type == WorkspaceType.SHARED) {
                                stringResource(Res.string.workspace_details_type_shared)
                            } else {
                                stringResource(Res.string.workspace_details_type_personal)
                            }
                            WorkspaceHeroCard(
                                name = workspace.name,
                                typeName = typeName,
                                currencyCode = workspace.currency.code,
                                description = workspace.description,
                                userRole = state.currentUserRole ?: WorkspaceRole.VIEWER,
                            )
                        }

                        // 2. Sahip Eylemi: Davet Kodu Oluştur
                        if (state.isOwner) {
                            item(key = "create_invite_action") {
                                val inviteButtonText = stringResource(Res.string.workspace_details_create_invite_button)
                                Button(
                                    onClick = onCreateInvite,
                                    enabled = state.canCreateInvite,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FeniqoSageGreen,
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .semantics { contentDescription = inviteButtonText },
                                ) {
                                    if (state.isCreatingInvite) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = inviteButtonText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Ödeşme ve Transfer Önerileri Kartı
                        item(key = "settlement_action_card") {
                            SettlementActionCard(onClick = onNavigateToSettlement)
                        }

                        // 4. Üyeler Başlığı
                        item(key = "members_header") {
                            Text(
                                text = pluralStringResource(Res.plurals.workspace_details_members_plural, state.members.size, state.members.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        // 5. Üye Kartları
                        items(
                            items = state.members,
                            key = { it.userId.value },
                        ) { member ->
                            MemberCardItem(
                                member = member,
                                isOwner = state.isOwner,
                                onRoleChangeClick = {
                                    val nextRole = if (member.role == WorkspaceRole.EDITOR) WorkspaceRole.VIEWER else WorkspaceRole.EDITOR
                                    onRequestChangeMemberRole(member, nextRole)
                                },
                                onRemoveClick = {
                                    onRequestRemoveMember(member)
                                },
                            )
                        }

                        // 6. Sahipliği Devret (Sahip İçin) / Ayrılma ve Yetki Notu (Diğerleri İçin)
                        item(key = "bottom_management_section") {
                            if (state.isOwner) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val transferButtonText = stringResource(Res.string.workspace_details_transfer_ownership_button)
                                    OutlinedButton(
                                        onClick = { showOwnershipTransferSheet = true },
                                        enabled = state.canTransferOwnership,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .semantics { contentDescription = transferButtonText },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = transferButtonText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    // Bilgilendirme Kutusu
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(Res.string.workspace_details_owner_must_transfer_warning),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Yetki Bilgi Kutusu
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            val permissionText = if (state.currentUserRole == WorkspaceRole.VIEWER) {
                                                stringResource(Res.string.workspace_details_permission_viewer)
                                            } else {
                                                stringResource(Res.string.workspace_details_permission_editor)
                                            }
                                            Text(
                                                text = permissionText,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // Alandan Ayrıl Butonu
                                    if (state.canLeave) {
                                        val leaveButtonText = stringResource(Res.string.workspace_details_leave_button)
                                        OutlinedButton(
                                            onClick = onRequestLeave,
                                            enabled = !state.isLeaving,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                contentColor = Color(0xFFDC2626),
                                            ),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .semantics { contentDescription = leaveButtonText },
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                                contentDescription = null,
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = leaveButtonText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFDC2626),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // DİYALOGLAR (Görsel 03)
    // -------------------------------------------------------------

    // 07 Davet Kodu Diyaloğu
    if (state.generatedInviteCode != null) {
        WorkspaceInviteCodeDialog(
            inviteCode = state.generatedInviteCode,
            onDismiss = onDismissInviteCodeDialog,
        )
    }

    // 08 Rol Değişikliği Diyaloğu
    if (state.pendingRoleChange != null) {
        val pending = state.pendingRoleChange
        WorkspaceRoleChangeDialog(
            member = pending.member,
            selectedRole = pending.newRole,
            onSelectRole = onSelectPendingRole,
            isSubmitting = state.isChangingMemberRole,
            onConfirm = onConfirmRoleChange,
            onDismiss = onDismissRoleChangeConfirmation,
        )
    }

    // 09 Üye Çıkarma Diyaloğu
    if (state.pendingMemberRemoval != null) {
        WorkspaceMemberRemovalDialog(
            member = state.pendingMemberRemoval,
            workspaceName = state.workspace?.name ?: "",
            isSubmitting = state.isRemovingMember,
            onConfirm = onConfirmMemberRemoval,
            onDismiss = onDismissMemberRemovalConfirmation,
        )
    }

    // 10 Sahiplik Devri Seçim / Onay Diyaloğu
    if (showOwnershipTransferSheet || state.pendingOwnershipTransferTarget != null) {
        WorkspaceOwnershipTransferDialog(
            eligibleMembers = state.eligibleOwnershipTransferTargets,
            pendingTarget = state.pendingOwnershipTransferTarget,
            onRequestTransfer = { targetMember ->
                onRequestOwnershipTransfer(targetMember)
            },
            onConfirmTransfer = onConfirmOwnershipTransfer,
            isSubmitting = state.isTransferringOwnership,
            onDismiss = {
                showOwnershipTransferSheet = false
                onDismissOwnershipTransferConfirmation()
            },
        )
    }

    // 11 Alandan Ayrılma Onay Diyaloğu
    if (state.showLeaveConfirmation) {
        WorkspaceLeaveDialog(
            workspaceName = state.workspace?.name ?: "",
            isSubmitting = state.isLeaving,
            onConfirm = onConfirmLeave,
            onDismiss = onDismissLeaveConfirmation,
        )
    }
}

/**
 * Koyu Grafit Hero Alan Kartı (Görsel 02).
 */
@Composable
private fun WorkspaceHeroCard(
    name: String,
    typeName: String,
    currencyCode: String,
    description: String?,
    userRole: WorkspaceRole,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2822),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Sol İkon
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Orta Alan Başlığı ve Türü
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$typeName · $currencyCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C8BF),
                    )
                }

                // Sağ Rol Rozeti
                HeroRoleBadge(role = userRole)
            }

            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Color(0xFFDCE6E0),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Hero Kartındaki Rol Rozeti (👑 Sahip / 👁 İzleyici / ✎ Düzenleyici).
 */
@Composable
private fun HeroRoleBadge(
    role: WorkspaceRole,
    modifier: Modifier = Modifier,
) {
    val roleDisplayName = stringResource(role.toLocalizedResource())
    val roleContentDescription = stringResource(Res.string.workspace_details_current_role_label, roleDisplayName)
    Surface(
        modifier = modifier.semantics {
            contentDescription = roleContentDescription
        },
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (role) {
                WorkspaceRole.OWNER -> Icons.Outlined.WorkspacePremium
                WorkspaceRole.EDITOR -> Icons.Outlined.Edit
                WorkspaceRole.VIEWER -> Icons.Outlined.Visibility
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = roleDisplayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/**
 * Ödeşme ve Transfer Önerileri Buton Kartı (Görsel 02).
 */
@Composable
private fun SettlementActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleText = stringResource(Res.string.workspace_details_settlement_action_title)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = titleText },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = FeniqoSageGreenContainer.copy(alpha = 0.55f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.workspace_details_settlement_action_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Üye Liste Kartı (Görsel 02).
 */
@Composable
private fun MemberCardItem(
    member: WorkspaceMemberUiModel,
    isOwner: Boolean,
    onRoleChangeClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val displayName = member.resolveDisplayName(includeSelfSuffix = true)
    val plainDisplayName = member.resolveDisplayName(includeSelfSuffix = false)
    val initialLetter = resolveWorkspaceMemberInitial(displayName)
    val showMenuButton = isOwner && !member.isCurrentUser && member.role != WorkspaceRole.OWNER

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Baş Harf Avatarları
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (member.isCurrentUser) FeniqoSageGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initialLetter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (member.isCurrentUser) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Üye Adı ve Rolü
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(member.role.toLocalizedResource()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = if (member.role == WorkspaceRole.OWNER) FontWeight.Bold else FontWeight.Normal,
                    color = if (member.role == WorkspaceRole.OWNER) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Sahip için 3 nokta menü butonu
            if (showMenuButton) {
                val menuCd = stringResource(Res.string.workspace_details_member_menu_cd, plainDisplayName)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = menuCd },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.workspace_details_action_change_role)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRoleChangeClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.workspace_details_action_remove_member), color = Color(0xFFDC2626)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PersonRemove,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemoveClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DİYALOG UYGULAMALARI (Görsel 03)
// -------------------------------------------------------------

/**
 * 07. Davet Kodu Diyaloğu (Görsel 07).
 */
@Composable
private fun WorkspaceInviteCodeDialog(
    inviteCode: String,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = FeniqoSageGreenContainer.copy(alpha = 0.7f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = FeniqoSageGreen,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.workspace_details_invite_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(Res.string.workspace_details_invite_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Kod Kutusu
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )

                        val copyCd = stringResource(Res.string.workspace_details_invite_copy_button)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(inviteCode))
                                isCopied = true
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = copyCd,
                                tint = if (isCopied) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                val helperText = if (isCopied) {
                    stringResource(Res.string.workspace_details_invite_copied_toast)
                } else {
                    stringResource(Res.string.workspace_join_security_notice)
                }
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (isCopied) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val copyButtonText = if (isCopied) {
                    stringResource(Res.string.workspace_details_invite_copied_button)
                } else {
                    stringResource(Res.string.workspace_details_invite_copy_button)
                }
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteCode))
                        isCopied = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = copyButtonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_details_invite_close_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

/**
 * 08. Rol Değişikliği Diyaloğu (Görsel 08).
 */
@Composable
private fun WorkspaceRoleChangeDialog(
    member: WorkspaceMemberUiModel,
    selectedRole: WorkspaceRole,
    onSelectRole: (WorkspaceRole) -> Unit,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val memberName = member.resolveDisplayName()
    val initialLetter = resolveWorkspaceMemberInitial(memberName)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.workspace_details_role_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hedef üye kartı
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initialLetter,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = memberName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            val currentRoleDisplayName = stringResource(member.role.toLocalizedResource())
                            Text(
                                text = stringResource(Res.string.workspace_details_role_dialog_current, currentRoleDisplayName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Düzenleyici Seçeneği
                val editorDesc = WorkspaceRole.EDITOR.toLocalizedDescriptionResource()?.let { stringResource(it) } ?: ""
                RoleRadioItem(
                    title = stringResource(WorkspaceRole.EDITOR.toLocalizedResource()),
                    subtext = editorDesc,
                    icon = Icons.Outlined.Edit,
                    isSelected = selectedRole == WorkspaceRole.EDITOR,
                    onClick = { onSelectRole(WorkspaceRole.EDITOR) },
                )

                // İzleyici Seçeneği
                val viewerDesc = WorkspaceRole.VIEWER.toLocalizedDescriptionResource()?.let { stringResource(it) } ?: ""
                RoleRadioItem(
                    title = stringResource(WorkspaceRole.VIEWER.toLocalizedResource()),
                    subtext = viewerDesc,
                    icon = Icons.Outlined.Visibility,
                    isSelected = selectedRole == WorkspaceRole.VIEWER,
                    onClick = { onSelectRole(WorkspaceRole.VIEWER) },
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.workspace_details_role_dialog_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

@Composable
private fun RoleRadioItem(
    title: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) FeniqoSageGreenContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogRadioCircle(isSelected = isSelected)
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 09. Üye Çıkarma Diyaloğu (Görsel 09).
 */
@Composable
private fun WorkspaceMemberRemovalDialog(
    member: WorkspaceMemberUiModel,
    workspaceName: String,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val memberName = member.resolveDisplayName()
    val initialLetter = resolveWorkspaceMemberInitial(memberName)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PersonRemove,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.workspace_details_remove_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                // Hedef üye kutusu
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initialLetter,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = memberName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.workspace_details_remove_dialog_desc, workspaceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.workspace_details_remove_dialog_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

/**
 * 10. Sahiplik Devri Diyaloğu (Görsel 10).
 */
@Composable
private fun WorkspaceOwnershipTransferDialog(
    eligibleMembers: List<WorkspaceMemberUiModel>,
    pendingTarget: WorkspaceMemberUiModel?,
    onRequestTransfer: (WorkspaceMemberUiModel) -> Unit,
    onConfirmTransfer: () -> Unit,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
) {
    var selectedMember by remember { mutableStateOf(pendingTarget ?: eligibleMembers.firstOrNull()) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.workspace_details_transfer_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (eligibleMembers.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.workspace_details_transfer_dialog_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    eligibleMembers.forEach { member ->
                        val isChecked = member.userId == selectedMember?.userId
                        val memberName = member.resolveDisplayName()
                        val initialLetter = resolveWorkspaceMemberInitial(memberName)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedMember = member },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) FeniqoSageGreenContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                            ),
                            border = BorderStroke(
                                if (isChecked) 1.5.dp else 1.dp,
                                if (isChecked) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DialogRadioCircle(isSelected = isChecked)
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initialLetter,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = memberName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    selectedMember?.let { target ->
                        val targetName = target.resolveDisplayName()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = FeniqoSageGreenContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, FeniqoSageGreen.copy(alpha = 0.4f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.workspace_details_transfer_confirm_title, targetName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.workspace_details_transfer_confirm_desc, targetName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (eligibleMembers.isNotEmpty() && selectedMember != null) {
                    Button(
                        onClick = {
                            val target = selectedMember
                            if (target != null) {
                                if (pendingTarget == null || pendingTarget.userId != target.userId) {
                                    onRequestTransfer(target)
                                } else {
                                    onConfirmTransfer()
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.workspace_details_transfer_confirm_button),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

/**
 * 11. Alandan Ayrılma Diyaloğu (Görsel 11).
 */
@Composable
private fun WorkspaceLeaveDialog(
    workspaceName: String,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.workspace_details_leave_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (workspaceName.isNotBlank()) {
                    Text(
                        text = workspaceName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoSageGreen,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = stringResource(Res.string.workspace_details_leave_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.workspace_details_leave_dialog_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

/**
 * Hata Bannerı.
 */
@Composable
private fun DetailsErrorBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val closeErrorCd = stringResource(Res.string.workspace_close_error)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message.toLocalizedText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = closeErrorCd,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DialogRadioCircle(isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(20.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.5.dp,
            if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = FeniqoSageGreen,
                ) {}
            }
        }
    }
}
