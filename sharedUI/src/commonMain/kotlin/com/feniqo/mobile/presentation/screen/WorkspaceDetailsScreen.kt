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
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
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
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.workspace.WorkspaceDetailsUiState
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel

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
                        .semantics { contentDescription = "Geri dön" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Alan detayı",
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
                                text = "Alan bulunamadı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Bu alan artık mevcut olmayabilir veya erişimin olmayabilir.",
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
                                Text("Geri dön", fontWeight = FontWeight.Bold)
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
                            WorkspaceHeroCard(
                                name = workspace.name,
                                typeName = if (workspace.type.name == "SHARED") "Ortak alan" else "Kişisel alan",
                                currencyCode = workspace.currency.code,
                                description = workspace.description,
                                userRole = state.currentUserRole ?: WorkspaceRole.VIEWER,
                            )
                        }

                        // 2. Sahip Eylemi: Davet Kodu Oluştur
                        if (state.isOwner) {
                            item(key = "create_invite_action") {
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
                                        .semantics { contentDescription = "Davet kodu oluştur" },
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
                                            text = "Davet kodu oluştur",
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
                                text = "Üyeler · ${state.members.size}",
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
                                            .semantics { contentDescription = "Sahipliği devret" },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Sahipliği devret",
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
                                                text = "Ayrılmak için önce sahipliği devretmelisin.",
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
                                            Text(
                                                text = if (state.currentUserRole == WorkspaceRole.VIEWER) {
                                                    "Bu alanda görüntüleme yetkin var."
                                                } else {
                                                    "Bu alanda kayıt düzenleme yetkin var."
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // Alandan Ayrıl Butonu
                                    if (state.canLeave) {
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
                                                .semantics { contentDescription = "Alandan ayrıl" },
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                                contentDescription = null,
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Alandan ayrıl",
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
            workspaceName = state.workspace?.name ?: "bu",
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
            workspaceName = state.workspace?.name ?: "bu",
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
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Mevcut rolünüz: ${role.toTurkishDisplayName()}"
        },
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (icon, label) = when (role) {
                WorkspaceRole.OWNER -> Icons.Outlined.WorkspacePremium to "Sahip"
                WorkspaceRole.EDITOR -> Icons.Outlined.Edit to "Düzenleyici"
                WorkspaceRole.VIEWER -> Icons.Outlined.Visibility to "İzleyici"
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Ödeşme ve transfer önerileri" },
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
                    text = "Ödeşme ve transfer önerileri",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Üye bakiyelerini gör ve önerileri incele",
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

    val initialLetter = member.displayName.firstOrNull()?.uppercase() ?: "Ü"
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
                val displayNameWithSelf = if (member.isCurrentUser && !member.displayName.contains("sen", ignoreCase = true)) {
                    "${member.displayName} (sen)"
                } else {
                    member.displayName
                }
                Text(
                    text = displayNameWithSelf,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = member.role.toTurkishDisplayName(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = if (member.role == WorkspaceRole.OWNER) FontWeight.Bold else FontWeight.Normal,
                    color = if (member.role == WorkspaceRole.OWNER) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Sahip için 3 nokta menü butonu
            if (showMenuButton) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "${member.displayName} yönetim menüsü" },
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
                            text = { Text("Rolü Değiştir") },
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
                            text = { Text("Üyeyi Çıkar", color = Color(0xFFDC2626)) },
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
                    text = "Davet kodu oluşturuldu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Bu kod ile başkalarını alanına davet edebilirsin.",
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

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(inviteCode))
                                isCopied = true
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Kodu kopyala",
                                tint = if (isCopied) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Text(
                    text = if (isCopied) "Kod panoya kopyalandı!" else "Yalnızca güvendiğin kişilerle paylaş.",
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
                        text = if (isCopied) "Kopyalandı" else "Kodu kopyala",
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
                        text = "Kapat",
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
    val initialLetter = member.displayName.firstOrNull()?.uppercase() ?: "Ü"

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = "Üye rolünü değiştir",
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
                                text = member.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Mevcut rol: ${member.role.toTurkishDisplayName()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Düzenleyici Seçeneği
                RoleRadioItem(
                    title = "Düzenleyici",
                    subtext = "Kayıtları düzenleyebilir.",
                    icon = Icons.Outlined.Edit,
                    isSelected = selectedRole == WorkspaceRole.EDITOR,
                    onClick = { onSelectRole(WorkspaceRole.EDITOR) },
                )

                // İzleyici Seçeneği
                RoleRadioItem(
                    title = "İzleyici",
                    subtext = "Kayıtları görüntüleyebilir.",
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
                            text = "Değişikliği onayla",
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
                        text = "Vazgeç",
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
    val initialLetter = member.displayName.firstOrNull()?.uppercase() ?: "Ü"

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
                    color = Color(0xFFFEE2E2),
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
                    text = "Üye çıkarılsın mı?",
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
                            text = member.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = "Bu üye $workspaceName alanına erişimini kaybedecek.",
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
                            text = "Üyeyi çıkar",
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
                        text = "Vazgeç",
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
                text = "Sahipliği kime devretmek istiyorsun?",
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
                        text = "Sahipliği devretmek için alanda en az bir aktif üye bulunmalıdır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    eligibleMembers.forEach { member ->
                        val isChecked = member.userId == selectedMember?.userId
                        val initialLetter = member.displayName.firstOrNull()?.uppercase() ?: "Ü"

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
                                    text = member.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    selectedMember?.let { target ->
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
                                    text = "Sahiplik ${target.displayName}'a devredilsin mi?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${target.displayName} alan sahibi olacak. Sen düzenleyici olacaksın.",
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
                                text = "Sahipliği devret",
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
                        text = "Vazgeç",
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
                    color = Color(0xFFFEE2E2),
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
                    text = "Alandan ayrılmak istiyor musun?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoSageGreen,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Bu alandaki kayıtlara erişimini kaybedeceksin.",
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
                            text = "Alandan ayrıl",
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
                        text = "Vazgeç",
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEE2E2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
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
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message.toDisplayText(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF991B1B),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Hata mesajını kapat",
                    tint = Color(0xFF991B1B),
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

private fun WorkspaceRole.toTurkishDisplayName(): String = when (this) {
    WorkspaceRole.OWNER -> "Sahip"
    WorkspaceRole.EDITOR -> "Düzenleyici"
    WorkspaceRole.VIEWER -> "İzleyici"
}
