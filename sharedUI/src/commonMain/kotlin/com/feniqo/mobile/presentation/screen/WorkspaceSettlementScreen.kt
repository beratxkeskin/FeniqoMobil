package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.workspace.MemberBalanceStatus
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberBalanceUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementTransferUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementUiState
import com.feniqo.mobile.presentation.workspace.resolveDisplayName
import com.feniqo.mobile.presentation.workspace.resolveWorkspaceMemberDisplayName
import com.feniqo.mobile.presentation.workspace.resolveWorkspaceMemberInitial
import com.feniqo.mobile.presentation.workspace.toLocalizedResource
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.workspace_back
import feniqomobil.sharedui.generated.resources.workspace_close_error
import feniqomobil.sharedui.generated.resources.workspace_settlement_balances_header
import feniqomobil.sharedui.generated.resources.workspace_settlement_balances_subtitle
import feniqomobil.sharedui.generated.resources.workspace_settlement_disclaimer_footnote
import feniqomobil.sharedui.generated.resources.workspace_settlement_disclaimer_notice
import feniqomobil.sharedui.generated.resources.workspace_settlement_empty_desc
import feniqomobil.sharedui.generated.resources.workspace_settlement_empty_title
import feniqomobil.sharedui.generated.resources.workspace_settlement_excluded_warning_close
import feniqomobil.sharedui.generated.resources.workspace_settlement_excluded_warning_desc
import feniqomobil.sharedui.generated.resources.workspace_settlement_excluded_warning_title
import feniqomobil.sharedui.generated.resources.workspace_settlement_member_self_suffix
import feniqomobil.sharedui.generated.resources.workspace_settlement_not_member_title
import feniqomobil.sharedui.generated.resources.workspace_settlement_title
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfer_payer_arrow_cd
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfer_suggestion_other
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfer_suggestion_self
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfers_empty_desc
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfers_empty_title
import feniqomobil.sharedui.generated.resources.workspace_settlement_transfers_header
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Ortak Alan Ödeşme Ekranı.
 * Onaylı Görsel 02 (Üçüncü Ekran), Görsel 13 (Hesaplar dengede) ve Görsel 15 (Uyarılar)
 * tasarımına sadık kalınarak hazırlanmıştır.
 */
@Composable
fun WorkspaceSettlementScreen(
    state: WorkspaceSettlementUiState,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExcludedWarning by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Üst Bar: Geri Butonu & Başlık
            val backText = stringResource(Res.string.workspace_back)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
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
                    text = stringResource(Res.string.workspace_settlement_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Hata Bannerı
            if (state.errorMessage != null) {
                SettlementErrorBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Dışlanan Hatalı Gider Uyarısı (Görsel 15)
            if (state.hasExcludedExpenses && showExcludedWarning && state.errorMessage == null) {
                ExcludedExpensesWarningBanner(
                    onDismiss = { showExcludedWarning = false },
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
                !state.isCurrentUserMember -> {
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
                            Text(
                                text = stringResource(Res.string.workspace_settlement_not_member_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            ) {
                                Text(backText)
                            }
                        }
                    }
                }
                state.isEmpty -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(Res.string.workspace_settlement_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(Res.string.workspace_settlement_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        // Alan adı alt başlığı
                        item(key = "workspace_meta_header") {
                            if (state.workspaceName.isNotBlank()) {
                                Text(
                                    text = state.workspaceName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }

                        // 1. Üye Bakiyeleri Başlığı
                        item(key = "balances_section_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(Res.string.workspace_settlement_balances_header),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = stringResource(Res.string.workspace_settlement_balances_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 2. Üye Bakiyeleri Kartları
                        items(
                            items = state.memberBalances,
                            key = { it.userId.value },
                        ) { balance ->
                            MemberBalanceRowCard(
                                balance = balance,
                                currency = state.currency,
                            )
                        }

                        // 3. Transfer Önerileri Başlığı
                        item(key = "transfers_section_header") {
                            Text(
                                text = stringResource(Res.string.workspace_settlement_transfers_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        // 4. Transfer Önerileri Kartları veya "Hesaplar Dengede" (Görsel 13)
                        if (state.suggestedTransfers.isNotEmpty()) {
                            items(
                                items = state.suggestedTransfers,
                                key = { "${it.fromUserId.value}_${it.toUserId.value}_${it.amount.amountMinor}" },
                            ) { transfer ->
                                SuggestedTransferRowCard(transfer = transfer)
                            }
                        } else {
                            item(key = "all_settled_balanced_card") {
                                AllSettledBalancedCard()
                            }
                        }

                        // 5. Bilgilendirme Notu
                        item(key = "settlement_notice_box") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = FeniqoSageGreenContainer.copy(alpha = 0.45f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = FeniqoSageGreen,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(Res.string.workspace_settlement_disclaimer_notice),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // 6. Dipnot
                        item(key = "settlement_footnote") {
                            Text(
                                text = stringResource(Res.string.workspace_settlement_disclaimer_footnote),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Üye Bakiye Kartı (Görsel 02).
 */
@Composable
private fun MemberBalanceRowCard(
    balance: WorkspaceMemberBalanceUiModel,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val displayName = balance.resolveDisplayName(includeSelfSuffix = true)
    val initialLetter = resolveWorkspaceMemberInitial(displayName)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Baş Harf Avatarları
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (balance.isCurrentUser) FeniqoSageGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initialLetter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (balance.isCurrentUser) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Üye Adı ve Durumu
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                val statusText = stringResource(balance.status.toLocalizedResource())
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Net Tutar (Alacaklı: Yeşil, Borçlu: Kırmızı, Dengede: Nötr)
            val amountColor = when (balance.status) {
                MemberBalanceStatus.CREDITOR -> Color(0xFF16A34A)
                MemberBalanceStatus.DEBTOR -> Color(0xFFDC2626)
                MemberBalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val absAmount = Money(abs(balance.netAmountMinor), currency).toLocalizedFormatted()
            val formattedAmount = when (balance.status) {
                MemberBalanceStatus.CREDITOR -> "+$absAmount"
                MemberBalanceStatus.DEBTOR -> "-$absAmount"
                MemberBalanceStatus.SETTLED -> absAmount
            }

            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
                color = amountColor,
            )
        }
    }
}

/**
 * Transfer Önerisi Kartı (Görsel 02).
 * Format: [C  Can  ₺400]   →   [A  Ayşe]
 */
@Composable
private fun SuggestedTransferRowCard(
    transfer: WorkspaceSettlementTransferUiModel,
    modifier: Modifier = Modifier,
) {
    val fromDisplayName = resolveWorkspaceMemberDisplayName(
        userId = transfer.fromUserId,
        displayName = transfer.fromDisplayName,
        isCurrentUser = transfer.fromIsCurrentUser,
        includeSelfSuffix = false,
    )
    val toDisplayName = resolveWorkspaceMemberDisplayName(
        userId = transfer.toUserId,
        displayName = transfer.toDisplayName,
        isCurrentUser = transfer.toIsCurrentUser,
        includeSelfSuffix = false,
    )
    val fromInitial = resolveWorkspaceMemberInitial(fromDisplayName)
    val toInitial = resolveWorkspaceMemberInitial(toDisplayName)
    val formattedAmount = transfer.amount.toLocalizedFormatted()

    val suggestionCd = if (transfer.fromIsCurrentUser) {
        stringResource(Res.string.workspace_settlement_transfer_suggestion_self, toDisplayName, formattedAmount)
    } else {
        stringResource(Res.string.workspace_settlement_transfer_suggestion_other, fromDisplayName, toDisplayName, formattedAmount)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = suggestionCd },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Sol Taraf: Borçlu Üye ve Tutar
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = fromInitial,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = fromDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFDC2626),
                    )
                }
            }

            // Orta: Yön Oku
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = stringResource(Res.string.workspace_settlement_transfer_payer_arrow_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(20.dp),
            )

            // Sağ Taraf: Alacaklı Üye
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = FeniqoSageGreenContainer.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = toInitial,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = FeniqoSageGreen,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = toDisplayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Hesaplar Dengede Kartı (Görsel 13).
 */
@Composable
private fun AllSettledBalancedCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.workspace_settlement_transfers_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(Res.string.workspace_settlement_transfers_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}

/**
 * Eksik / Geçersiz Split Uyarısı Kartı (Görsel 15).
 */
@Composable
private fun ExcludedExpensesWarningBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.workspace_settlement_excluded_warning_title),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(Res.string.workspace_settlement_excluded_warning_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.workspace_settlement_excluded_warning_close),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Hata Bannerı.
 */
@Composable
private fun SettlementErrorBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
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
