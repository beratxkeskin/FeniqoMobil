package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.workspace.WorkspaceDetailsUiState
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel

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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(FeniqoSpacing.Large),
        ) {
            // Başlık ve Geri Butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FeniqoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !state.isLeaving,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Geri dön"
                        },
                ) {
                    Text(
                        text = "‹ Geri",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                Text(
                    text = "Çalışma Alanı Detayları",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }

            // Hata Mesajı Bannerı
            if (state.errorMessage != null) {
                WorkspaceDetailsErrorBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    enabled = !state.isLeaving,
                    modifier = Modifier.padding(bottom = FeniqoSpacing.Medium),
                )
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                state.workspace == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Çalışma alanı bulunamadı.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val workspace = state.workspace
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        // 1. Workspace Bilgi Kartı
                        item(key = "workspace_info") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(FeniqoSpacing.Large),
                                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                ) {
                                    Text(
                                        text = workspace.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
                                        Text(
                                            text = "Tür: ${workspace.type.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Para Birimi: ${workspace.currency.code}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    if (!workspace.description.isNullOrBlank()) {
                                        Text(
                                            text = workspace.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // 1.1 Davet Kodu Oluştur Butonu (yalnız OWNER için)
                        if (state.isOwner) {
                            item(key = "owner_invite_action") {
                                Button(
                                    onClick = onCreateInvite,
                                    enabled = state.canCreateInvite,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = "Davet kodu oluştur"
                                        },
                                ) {
                                    if (state.isCreatingInvite) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Text(
                                            text = "Davet Kodu Oluştur",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Üyeler Başlığı
                        item(key = "members_header") {
                            Text(
                                text = "Üyeler (${state.members.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = FeniqoSpacing.Small),
                            )
                        }

                        // 3. Üye Listesi
                        items(
                            items = state.members,
                            key = { it.userId.value },
                        ) { member ->
                            WorkspaceMemberItemCard(
                                member = member,
                                canChangeRole = state.canManageRoles && !member.isCurrentUser && member.role != WorkspaceRole.OWNER,
                                onRequestChangeRole = {
                                    val targetRole = if (member.role == WorkspaceRole.EDITOR) WorkspaceRole.VIEWER else WorkspaceRole.EDITOR
                                    onRequestChangeMemberRole(member, targetRole)
                                },
                                canRemoveMember = state.canRemoveMembers && !member.isCurrentUser && member.role in setOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER),
                                onRequestRemoveMember = { onRequestRemoveMember(member) },
                            )
                        }

                        // 3.1 Sahipliği Devret Bölümü (yalnız OWNER için)
                        if (state.isOwner) {
                            item(key = "owner_transfer_section") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(FeniqoSpacing.Large),
                                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                    ) {
                                        Text(
                                            text = "Sahipliği Devret",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )

                                        if (state.eligibleOwnershipTransferTargets.isEmpty()) {
                                            Button(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .semantics {
                                                        contentDescription = "Sahipliği devret (uygun hedef üye yok)"
                                                    },
                                            ) {
                                                Text(
                                                    text = "Sahipliği Devret",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Text(
                                                text = "Sahipliği devretmek için en az bir aktif EDITOR veya VIEWER üye gerekir.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                text = "Sahipliği devretmek istediğiniz üyeyi seçin:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                            ) {
                                                state.eligibleOwnershipTransferTargets.forEach { eligibleMember ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = FeniqoSpacing.ExtraSmall),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = eligibleMember.displayName,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                            Text(
                                                                text = "Rol: ${eligibleMember.role.name}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        Button(
                                                            onClick = { onRequestOwnershipTransfer(eligibleMember) },
                                                            enabled = !state.isTransferringOwnership && !state.isLoading,
                                                            modifier = Modifier.semantics {
                                                                contentDescription = "${eligibleMember.displayName} üyesine sahipliği devret"
                                                            },
                                                        ) {
                                                            Text(
                                                                text = "Devret",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
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

                        // 4. Alandan Ayrılma Bölümü
                        item(key = "leave_section") {
                            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                            if (state.isOwner) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(FeniqoRadius.Small),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ) {
                                    Text(
                                        text = "Çalışma alanı sahibi olarak alandan ayrılamazsınız. Ayrılmak için önce sahipliği devretmeniz gerekir.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                                    )
                                }
                            } else if (state.canLeave) {
                                Button(
                                    onClick = onRequestLeave,
                                    enabled = !state.isLeaving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = "Alandan ayrıl"
                                        },
                                ) {
                                    if (state.isLeaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError,
                                        )
                                    } else {
                                        Text(
                                            text = "Alandan Ayrıl",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Alandan Ayrılma Onay Diyaloğu
        if (state.showLeaveConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    if (!state.isLeaving) onDismissLeaveConfirmation()
                },
                title = {
                    Text(
                        text = "Alandan Ayrıl",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "Bu çalışma alanından ayrılmak istediğinizden emin misiniz? Alana ait kayıtlara erişiminizi kaybedeceksiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmLeave,
                        enabled = !state.isLeaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Ayrılmayı onayla"
                        },
                    ) {
                        if (state.isLeaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            Text("Ayrıl")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissLeaveConfirmation,
                        enabled = !state.isLeaving,
                        modifier = Modifier.semantics {
                            contentDescription = "Ayrılmaktan vazgeç"
                        },
                    ) {
                        Text("Vazgeç")
                    }
                },
            )
        }

        // Davet Kodu Başarı Diyaloğu
        if (state.generatedInviteCode != null) {
            val clipboardManager = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = onDismissInviteCodeDialog,
                title = {
                    Text(
                        text = "Davet Kodu Oluşturuldu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        Text(
                            text = "Bu davet kodunu alana katılmasını istediğiniz kişiyle paylaşabilirsiniz.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = state.generatedInviteCode,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium)
                                    .semantics {
                                        contentDescription = "Oluşturulan davet kodu: ${state.generatedInviteCode}"
                                    },
                            )
                        }
                        Text(
                            text = "Bu kodu yalnız güvendiğiniz kişilerle paylaşın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(state.generatedInviteCode))
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Kodu panoya kopyala"
                        },
                    ) {
                        Text("Kopyala")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissInviteCodeDialog,
                        modifier = Modifier.semantics {
                            contentDescription = "Davet kodu diyaloğunu kapat"
                        },
                    ) {
                        Text("Kapat")
                    }
                },
            )
        }

        // Üye Rolü Değiştirme Onay Diyaloğu
        if (state.pendingRoleChange != null) {
            val pending = state.pendingRoleChange
            AlertDialog(
                onDismissRequest = {
                    if (!state.isChangingMemberRole) onDismissRoleChangeConfirmation()
                },
                title = {
                    Text(
                        text = "Üye Rolünü Değiştir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = "${pending.member.displayName} adlı üyenin rolünü değiştirmek üzeresiniz.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Mevcut Rol: ${pending.member.role.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Yeni Rol Seçin:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = FeniqoSpacing.Small),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            val allowedRoles = listOf(WorkspaceRole.EDITOR, WorkspaceRole.VIEWER)
                            allowedRoles.forEach { roleOption ->
                                val isSelected = pending.newRole == roleOption
                                val isCurrent = pending.member.role == roleOption
                                Button(
                                    onClick = { onSelectPendingRole(roleOption) },
                                    enabled = !state.isChangingMemberRole && !isCurrent,
                                    colors = if (isSelected) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = "Rol ${roleOption.name} seç"
                                        },
                                ) {
                                    Text(
                                        text = roleOption.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmRoleChange,
                        enabled = !state.isChangingMemberRole && pending.newRole != pending.member.role,
                        modifier = Modifier.semantics {
                            contentDescription = "Rol değişimini onayla"
                        },
                    ) {
                        if (state.isChangingMemberRole) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Onayla")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissRoleChangeConfirmation,
                        enabled = !state.isChangingMemberRole,
                        modifier = Modifier.semantics {
                            contentDescription = "Rol değişiminden vazgeç"
                        },
                    ) {
                        Text("Vazgeç")
                    }
                },
            )
        }

        // Sahiplik Devri Onay Diyaloğu
        if (state.pendingOwnershipTransferTarget != null) {
            val target = state.pendingOwnershipTransferTarget
            AlertDialog(
                onDismissRequest = {
                    if (!state.isTransferringOwnership) onDismissOwnershipTransferConfirmation()
                },
                title = {
                    Text(
                        text = "Sahipliği Devret",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = "${target.displayName} adlı kullanıcıya sahiplik devredilecek.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Bu kullanıcı çalışma alanının sahibi olacak. Siz EDITOR rolüne geçeceksiniz.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmOwnershipTransfer,
                        enabled = !state.isTransferringOwnership,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Sahiplik devrini onayla"
                        },
                    ) {
                        if (state.isTransferringOwnership) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            Text("Devret")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissOwnershipTransferConfirmation,
                        enabled = !state.isTransferringOwnership,
                        modifier = Modifier.semantics {
                            contentDescription = "Sahiplik devrinden vazgeç"
                        },
                    ) {
                        Text("Vazgeç")
                    }
                },
            )
        }

        // Üye Çıkarma Onay Diyaloğu
        val removalTarget = state.pendingMemberRemoval
        if (removalTarget != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!state.isRemovingMember) onDismissMemberRemovalConfirmation()
                },
                title = {
                    Text(
                        text = "Üyeyi Çıkar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = "${removalTarget.displayName} adlı üye çalışma alanından çıkarılacak.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Bu üye çalışma alanına erişimini kaybedecek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmMemberRemoval,
                        enabled = !state.isRemovingMember,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Üyeyi çıkarmayı onayla"
                        },
                    ) {
                        if (state.isRemovingMember) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            Text("Çıkar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissMemberRemovalConfirmation,
                        enabled = !state.isRemovingMember,
                        modifier = Modifier.semantics {
                            contentDescription = "Üyeyi çıkarmaktan vazgeç"
                        },
                    ) {
                        Text("Vazgeç")
                    }
                },
            )
        }
    }
}

@Composable
private fun WorkspaceMemberItemCard(
    member: WorkspaceMemberUiModel,
    canChangeRole: Boolean = false,
    onRequestChangeRole: () -> Unit = {},
    canRemoveMember: Boolean = false,
    onRequestRemoveMember: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium)),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (member.isCurrentUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (member.isCurrentUser) "👤" else "👥",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (canChangeRole) {
                TextButton(
                    onClick = onRequestChangeRole,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "${member.displayName} için rolü değiştir"
                        },
                ) {
                    Text(
                        text = "Rolü Değiştir",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.ExtraSmall))
            }

            if (canRemoveMember) {
                TextButton(
                    onClick = onRequestRemoveMember,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "${member.displayName} üyesini çıkar"
                        },
                ) {
                    Text(
                        text = "Üyeyi Çıkar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.ExtraSmall))
            } else if (!canChangeRole) {
                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
            }

            // Rol Rozeti
            WorkspaceRoleBadge(role = member.role)
        }
    }
}

@Composable
private fun WorkspaceRoleBadge(
    role: WorkspaceRole,
    modifier: Modifier = Modifier,
) {
    val (containerColor, textColor) = when (role) {
        WorkspaceRole.OWNER -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        WorkspaceRole.EDITOR -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        WorkspaceRole.VIEWER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Rol: ${role.name}"
        },
        shape = CircleShape,
        color = containerColor,
    ) {
        Text(
            text = role.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 2.dp),
        )
    }
}

@Composable
private fun WorkspaceDetailsErrorBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message.toDisplayText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = "Hata mesajını kapat"
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = "Kapat",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
