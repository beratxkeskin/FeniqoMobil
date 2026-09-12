package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.workspace.MemberBalanceStatus
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberBalanceUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementTransferUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementUiState

@Composable
fun WorkspaceSettlementScreen(
    state: WorkspaceSettlementUiState,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ödeşme",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (state.workspaceName.isNotBlank()) {
                        Text(
                            text = state.workspaceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Hata Banner'ı
            if (state.errorMessage != null) {
                SettlementErrorBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(bottom = FeniqoSpacing.Medium),
                )
            }

            // Dışlanan Hatalı Gider Uyarısı
            if (state.hasExcludedExpenses && state.errorMessage == null) {
                SettlementNoticeBanner(
                    message = "Bazı geçersiz veya üyelik dışı split kayıtları hesaplamaya dahil edilmedi.",
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
                state.isEmpty || !state.isCurrentUserMember -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (!state.isCurrentUserMember) {
                                "Bu çalışma alanının üyesi değilsiniz."
                            } else {
                                "Hesaplanacak ortak gider bulunamadı."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        // 1. Özet Kartı
                        item(key = "settlement_summary") {
                            SettlementSummaryCard(
                                totalExpense = state.formattedTotalExpense,
                                memberCount = state.memberCount,
                            )
                        }

                        // 2. Transfer Önerileri Bölümü
                        item(key = "transfers_header") {
                            Text(
                                text = "Ödeme Önerileri",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = FeniqoSpacing.Small),
                            )
                        }

                        if (state.suggestedTransfers.isNotEmpty()) {
                            items(
                                items = state.suggestedTransfers,
                                key = { "${it.fromUserId.value}_${it.toUserId.value}_${it.amount.amountMinor}" },
                            ) { transfer ->
                                SuggestedTransferCard(transfer = transfer)
                            }
                        } else {
                            item(key = "all_settled_card") {
                                AllSettledCard()
                            }
                        }

                        // 3. Üye Bakiyeleri Bölümü
                        item(key = "balances_header") {
                            Text(
                                text = "Üye Bakiyeleri (${state.memberBalances.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = FeniqoSpacing.Medium),
                            )
                        }

                        items(
                            items = state.memberBalances,
                            key = { it.userId.value },
                        ) { balance ->
                            MemberBalanceCard(balance = balance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementSummaryCard(
    totalExpense: String,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Toplam Ortak Gider",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = totalExpense.ifBlank { "₺0,00" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Surface(
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            ) {
                Text(
                    text = "$memberCount üye",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = FeniqoSpacing.ExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun SuggestedTransferCard(
    transfer: WorkspaceSettlementTransferUiModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.suggestionText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

            Surface(
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = transfer.formattedAmount,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = FeniqoSpacing.ExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun AllSettledCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓ Tüm bakiyeler dengede. Herhangi bir transfer gerekmiyor.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberBalanceCard(
    balance: WorkspaceMemberBalanceUiModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = balance.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    ) {
                        Text(
                            text = balance.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (balance.role != null) {
                            Surface(
                                shape = RoundedCornerShape(FeniqoRadius.Small),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = when (balance.role) {
                                        WorkspaceRole.OWNER -> "Sahip"
                                        WorkspaceRole.EDITOR -> "Düzenleyici"
                                        WorkspaceRole.VIEWER -> "Görüntüleyici"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = when (balance.status) {
                            MemberBalanceStatus.CREDITOR -> "Alacaklı"
                            MemberBalanceStatus.DEBTOR -> "Borçlu"
                            MemberBalanceStatus.SETTLED -> "Dengede"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (balance.status) {
                            MemberBalanceStatus.CREDITOR -> MaterialTheme.colorScheme.primary
                            MemberBalanceStatus.DEBTOR -> MaterialTheme.colorScheme.error
                            MemberBalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = balance.formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (balance.status) {
                    MemberBalanceStatus.CREDITOR -> MaterialTheme.colorScheme.primary
                    MemberBalanceStatus.DEBTOR -> MaterialTheme.colorScheme.error
                    MemberBalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun SettlementErrorBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
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
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(
                    text = "Kapat",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SettlementNoticeBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(FeniqoSpacing.Medium),
        )
    }
}
