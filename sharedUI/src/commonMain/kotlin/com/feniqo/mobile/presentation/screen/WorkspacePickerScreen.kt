package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.workspace.WorkspacePickerItemUiModel
import com.feniqo.mobile.presentation.workspace.WorkspacePickerUiState

/**
 * Workspace seçici ekranının durumsuz (stateless) Compose sunumudur.
 * Kişisel mod seçeneğini ve kullanıcının canlı üyesi olduğu çalışma alanlarını listeler.
 * Aktif alan görünür biçimde işaretlidir; seçim sırasında çift tıklama engellenir.
 */
@Composable
fun WorkspacePickerScreen(
    state: WorkspacePickerUiState,
    onSelectPersonalMode: () -> Unit,
    onSelectWorkspace: (EntityId) -> Unit,
    onDismissError: () -> Unit,
    onNavigateBack: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onJoinWorkspace: () -> Unit,
    onWorkspaceDetails: (EntityId) -> Unit,
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
                    .padding(bottom = FeniqoSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onNavigateBack,
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
                    text = "Çalışma Alanı Seçimi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )

                TextButton(
                    onClick = onJoinWorkspace,
                    enabled = !state.isSelecting,
                    modifier = Modifier.semantics {
                        contentDescription = "Davet koduyla katıl"
                    },
                ) {
                    Text("Katıl")
                }

                TextButton(
                    onClick = onCreateWorkspace,
                    enabled = !state.isSelecting,
                    modifier = Modifier.semantics {
                        contentDescription = "Yeni çalışma alanı oluştur"
                    },
                ) {
                    Text("Yeni")
                }

                if (state.isSelecting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics {
                                contentDescription = "Seçim uygulanıyor"
                            },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = "Kişisel bütçeniz veya üyesi olduğunuz ortak bir çalışma alanı arasında geçiş yapın.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = FeniqoSpacing.Medium),
            )

            // Hata Mesajı Bannerı
            if (state.errorMessage != null) {
                WorkspaceErrorMessageBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    enabled = !state.isSelecting,
                    modifier = Modifier.padding(bottom = FeniqoSpacing.Medium),
                )
            }

            // İçerik Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
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
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                        ) {
                            // 1. Kişisel Mod Kartı
                            item(key = "personal_mode_option") {
                                PersonalModeItemCard(
                                    isActive = state.isPersonalModeActive,
                                    isSelecting = state.isSelecting,
                                    onClick = onSelectPersonalMode,
                                )
                            }

                            // 2. Ortak Alanlar Başlığı
                            item(key = "workspaces_header") {
                                Text(
                                    text = "Ortak Çalışma Alanları (${state.workspaces.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = FeniqoSpacing.Small),
                                )
                            }

                            // 3. Ortak Alanlar Listesi
                            if (state.workspaces.isEmpty()) {
                                item(key = "empty_workspaces_message") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ) {
                                        Text(
                                            text = "Henüz üyesi olduğunuz bir ortak alan bulunmuyor.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(FeniqoSpacing.Large),
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = state.workspaces,
                                    key = { it.id.value },
                                ) { workspace ->
                                    WorkspaceListItemCard(
                                        workspace = workspace,
                                        isSelecting = state.isSelecting,
                                        onClick = { onSelectWorkspace(workspace.id) },
                                        onDetails = { onWorkspaceDetails(workspace.id) },
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

/**
 * Kişisel mod seçim kartı.
 */
@Composable
private fun PersonalModeItemCard(
    isActive: Boolean,
    isSelecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val accessibilityDesc = if (isActive) {
        "Kişisel Mod, şu an aktif çalışma alanınız"
    } else {
        "Kişisel Mod, seçmek için tıklayın"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(
                enabled = !isSelecting && !isActive,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                selected = isActive
                contentDescription = accessibilityDesc
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "👤",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kişisel Mod",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Yalnızca bireysel kayıtlarınızı ve bütçenizi yönetin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Ortak çalışma alanı kartı.
 */
@Composable
private fun WorkspaceListItemCard(
    workspace: WorkspacePickerItemUiModel,
    isSelecting: Boolean,
    onClick: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = workspace.isActive
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val accessibilityDesc = if (isActive) {
        "${workspace.name}, şu an aktif çalışma alanınız"
    } else {
        "${workspace.name}, seçmek için tıklayın"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(
                enabled = !isSelecting && !isActive,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                selected = isActive
                contentDescription = accessibilityDesc
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "◉",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ortak Çalışma Alanı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
            }

            TextButton(
                onClick = onDetails,
                enabled = !isSelecting,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "${workspace.name} detayları"
                    },
            ) {
                Text(
                    text = "Detay",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Hata mesajı bannerı.
 */
@Composable
private fun WorkspaceErrorMessageBanner(
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
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
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
