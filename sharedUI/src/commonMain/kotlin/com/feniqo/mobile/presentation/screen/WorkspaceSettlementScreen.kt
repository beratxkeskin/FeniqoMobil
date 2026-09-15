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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.workspace.MemberBalanceStatus
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberBalanceUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementTransferUiModel
import com.feniqo.mobile.presentation.workspace.WorkspaceSettlementUiState

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
                    text = "Ödeşme",
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
                                text = "Bu çalışma alanının üyesi değilsiniz.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            ) {
                                Text("Geri dön")
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
                                text = "Hesaplanacak ortak gider bulunamadı.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Ortak giderler eklendiğinde net üye bakiyeleri ve transfer önerileri burada görünecektir.",
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
                        // Alan adı ve Para birimi alt başlığı
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
                                    text = "Üye bakiyeleri",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "Ortak gider paylaşımına göre",
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
                            MemberBalanceRowCard(balance = balance)
                        }

                        // 3. Transfer Önerileri Başlığı
                        item(key = "transfers_section_header") {
                            Text(
                                text = "Transfer önerileri",
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
                                        text = "Bunlar hesaplanan önerilerdir; uygulama para göndermez.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // 6. Dipnot
                        item(key = "settlement_footnote") {
                            Text(
                                text = "Tutarlar bu alanın geçerli ortak giderlerinden hesaplanır.",
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
    modifier: Modifier = Modifier,
) {
    val initialLetter = balance.displayName.firstOrNull()?.uppercase() ?: "Ü"

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
                val displayNameWithSelf = if (balance.isCurrentUser && !balance.displayName.contains("sen", ignoreCase = true)) {
                    "${balance.displayName} (sen)"
                } else {
                    balance.displayName
                }
                Text(
                    text = displayNameWithSelf,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                val statusText = when (balance.status) {
                    MemberBalanceStatus.CREDITOR -> "Alacaklı"
                    MemberBalanceStatus.DEBTOR -> "Borçlu"
                    MemberBalanceStatus.SETTLED -> "Dengede"
                }
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

            Text(
                text = balance.formattedAmount,
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
    val fromInitial = transfer.fromDisplayName.firstOrNull()?.uppercase() ?: "B"
    val toInitial = transfer.toDisplayName.firstOrNull()?.uppercase() ?: "A"

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
                        text = transfer.fromDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = transfer.formattedAmount,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFDC2626),
                    )
                }
            }

            // Orta: Yön Oku
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "ödeyecek kişi yönü",
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
                    text = transfer.toDisplayName,
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
                color = Color(0xFFDCFCE7),
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
                text = "Hesaplar dengede",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Ödeşme önerisi bulunmuyor. Üyeler arasındaki net ödeşme bakiyeleri sıfır.",
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
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
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
                    text = "Bazı giderler hesaplamaya dahil edilemedi.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF92400E),
                )
                Text(
                    text = "Eksik veriler nedeniyle tutarlar değişebilir.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFFB45309),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Uyarıyı kapat",
                    tint = Color(0xFF92400E),
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
