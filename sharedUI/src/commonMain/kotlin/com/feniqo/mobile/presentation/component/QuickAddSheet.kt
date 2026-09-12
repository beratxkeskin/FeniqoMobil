package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.theme.*

enum class QuickAddAction(
    val title: String,
    val subtitle: String,
    val available: Boolean,
) {
    EXPENSE("Gider", "Para çıkışı, harcama kaydı", true),
    INCOME("Gelir", "Maaş, ek gelir, tahsilat", true),
    TRANSFER("Transfer", "Hesaplar arası aktarım", false),
    DEBT_RECEIVABLE("Borç / Alacak", "Kişi bazlı borç veya alacak takibi", true),
    RECURRING_TRANSACTION("Tekrarlayan İşlem", "Aylık fatura, kira, abonelik", true),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAction: (QuickAddAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large)
                .padding(bottom = FeniqoSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Başlık Alanı
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "Hızlı Ekle",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Yapmak istediğin işlemi seç",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Seçenek Kartları
            QuickAddAction.entries.forEach { action ->
                val (icon, iconColor, containerBg) = when (action) {
                    QuickAddAction.EXPENSE -> Triple(
                        Icons.AutoMirrored.Outlined.TrendingDown,
                        FeniqoExpense,
                        FeniqoExpense.copy(alpha = 0.12f),
                    )
                    QuickAddAction.INCOME -> Triple(
                        Icons.AutoMirrored.Outlined.TrendingUp,
                        FeniqoEmerald,
                        FeniqoEmerald.copy(alpha = 0.12f),
                    )
                    QuickAddAction.TRANSFER -> Triple(
                        Icons.Outlined.SwapHoriz,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                    QuickAddAction.DEBT_RECEIVABLE -> Triple(
                        Icons.Outlined.AccountBalance,
                        FeniqoInfo,
                        FeniqoInfo.copy(alpha = 0.12f),
                    )
                    QuickAddAction.RECURRING_TRANSACTION -> Triple(
                        Icons.Outlined.Autorenew,
                        PhoenixGold,
                        PhoenixGold.copy(alpha = 0.14f),
                    )
                }

                val rowModifier = if (action.available) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "${action.title} ekle",
                    ) { onAction(action) }
                } else {
                    Modifier.alpha(0.55f)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(rowModifier),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = FeniqoTouchTarget.Minimum)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Sol Tonal İkon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Orta Metinler
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = action.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!action.available) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Text(
                                            text = "Yakında",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Sağ Ok
                        if (action.available) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Alt Bilgi / İpucu Kartı
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PhoenixGold.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = PhoenixGold,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = "Fiş veya fatura eklemek için Gider formundaki 'Fiş Tara' alanını kullanabilirsin.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
