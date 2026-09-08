package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetCopyConfirmationState
import com.feniqo.mobile.presentation.budget.BudgetDeleteConfirmationState
import com.feniqo.mobile.presentation.budget.BudgetProgressDisplayModel
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.budget.nextMonth
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Bütçeler ekranı üst başlık ve seçili dönem gezinme göstergesidir.
 */
@Composable
fun BudgetHeader(
    selectedMonth: YearMonth?,
    onMonthSelected: (YearMonth) -> Unit,
    onAddBudget: () -> Unit = {},
    onCopyBudgets: () -> Unit = {},
    canCopy: Boolean = true,
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val formattedMonth = if (selectedMonth != null) {
        DateFormatter.formatYearMonth(selectedMonth)
    } else {
        ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Text(
                        text = "Bütçeler",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    ActiveWorkspaceIndicator(
                        workspaceName = activeWorkspaceName,
                        isCompact = true,
                    )
                }
                Text(
                    text = "Aylık harcama limitlerinizi ve ilerlemenizi takip edin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCopyBudgets,
                    enabled = canCopy && selectedMonth != null,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Önceki aydan bütçeleri kopyala"
                        },
                ) {
                    Text(
                        text = "Kopyala",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = onAddBudget,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Bütçe Ekle"
                        },
                ) {
                    Text(
                        text = "Bütçe Ekle",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        // Ay Gezinme Kontrolleri (Önceki Ay - Seçili Ay Göstergesi - Sonraki Ay)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { selectedMonth?.let { onMonthSelected(it.previousMonth()) } },
                enabled = selectedMonth != null,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Önceki ay"
                    },
            ) {
                Text(
                    text = "◀ Önceki",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.semantics {
                    contentDescription = if (formattedMonth.isNotBlank()) "Seçili ay: $formattedMonth" else "Ay seçilmedi"
                },
            ) {
                Text(
                    text = formattedMonth.ifBlank { "Ay Seçilmedi" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = FeniqoSpacing.Medium,
                        vertical = FeniqoSpacing.Small,
                    ),
                )
            }

            TextButton(
                onClick = { selectedMonth?.let { onMonthSelected(it.nextMonth()) } },
                enabled = selectedMonth != null,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Sonraki ay"
                    },
            ) {
                Text(
                    text = "Sonraki ▶",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Tekil bütçe kartı bileşenidir.
 * Kategori ikonu/rengi, adı, limit, harcanan, kalan tutar, yüzde kullanım ve sağlık durumunu sunar.
 */
@Composable
fun BudgetProgressCard(
    budget: BudgetProgressDisplayModel,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(budget.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary

    val categoryIconEmoji = CategoryIconResolver.resolveIconEmojiOrNull(budget.categoryIconKey)
    val fallbackInitial = budget.categoryName.trim().firstOrNull()?.uppercase() ?: "?"

    val (healthLabel, healthColor, healthContainerColor) = when (budget.health) {
        BudgetHealth.SAFE -> Triple(
            "Normal",
            Color(0xFF10B981),
            Color(0xFF10B981).copy(alpha = 0.15f),
        )
        BudgetHealth.WARNING -> Triple(
            "Dikkat (%80+)",
            Color(0xFFF59E0B),
            Color(0xFFF59E0B).copy(alpha = 0.15f),
        )
        BudgetHealth.EXCEEDED -> Triple(
            "Aşıldı",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        )
    }

    val cardContentDescription = "${budget.categoryName} bütçesi: Limit ${budget.formattedLimit}, " +
        "Harcanan ${budget.formattedSpent}, Kalan ${budget.formattedRemaining}, Kullanım ${budget.formattedUsageRate}, Durum $healthLabel" +
        if (onEdit != null) ". Düzenlemek için dokunun." else ""

    Card(
        onClick = onEdit ?: {},
        enabled = onEdit != null,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics { contentDescription = cardContentDescription },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // 1. Üst Satır: Kategori Renk/İkon Rozeti + Adı, Sağlık Rozeti ve Silme Eylemi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = categoryIconEmoji ?: fallbackInitial,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(categoryColor),
                    )
                    Text(
                        text = budget.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Surface(
                        shape = RoundedCornerShape(FeniqoRadius.Small),
                        color = healthContainerColor,
                    ) {
                        Text(
                            text = healthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = healthColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    if (onDelete != null) {
                        androidx.compose.material3.IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "${budget.categoryName} bütçesini sil"
                                },
                        ) {
                            Text(
                                text = "🗑️",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // 2. İlerleme Çubuğu ve Kullanım Oranı
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Kullanım Oranı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = budget.formattedUsageRate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = healthColor,
                    )
                }

                LinearProgressIndicator(
                    progress = { budget.usageProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(FeniqoRadius.Small)),
                    color = healthColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                )
            }

            // 3. Tutar İstatistikleri Tablosu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Harcanan
                Column {
                    Text(
                        text = "Harcanan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = budget.formattedSpent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Kalan
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (budget.isRemainingNegative) "Aşım" else "Kalan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = budget.formattedRemaining,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (budget.isRemainingNegative) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                    )
                }

                // Limit
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = budget.formattedLimit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 4. Farklı Para Birimi Bilgilendirme Uyarısı
            if (budget.hasExcludedTransactions) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "${budget.excludedDifferentCurrencyTransactionCount} işlem farklı para birimi nedeniyle bütçe hesabına dahil edilmedi"
                        },
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "⚠️",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${budget.excludedDifferentCurrencyTransactionCount} işlem farklı para birimi nedeniyle dahil edilmedi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bütçe silme onay diyaloğudur.
 * Hedef kategoriyi, bütçe limitini gösterir ve çift tıklamayı engeller.
 */
@Composable
fun BudgetDeleteDialog(
    confirmation: BudgetDeleteConfirmationState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDeleting = confirmation.isDeleting
    val target = confirmation.target

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = "Bütçeyi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Text(
                    text = "Bu bütçeyi silmek istediğinizden emin misiniz?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${target.categoryName} • ${target.formattedLimit}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Bütçeyi silmeyi onayla" },
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Sil")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Silmekten vazgeç" },
            ) {
                Text("Vazgeç")
            }
        },
    )
}

/**
 * Önceki/seçilen aydan bütçe kopyalama onay diyaloğudur.
 * Kaynak ve hedef ayı gösterir, kaynak ayın ayarlanmasına izin verir ve aynı ay seçiminde onayı kilitler.
 */
@Composable
fun BudgetCopyDialog(
    confirmation: BudgetCopyConfirmationState,
    onSourceMonthChanged: (YearMonth) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCopying = confirmation.isCopying
    val sourceMonth = confirmation.sourceMonth
    val targetMonth = confirmation.targetMonth
    val isSameMonth = sourceMonth == targetMonth
    val formattedSource = DateFormatter.formatYearMonth(sourceMonth)
    val formattedTarget = DateFormatter.formatYearMonth(targetMonth)

    AlertDialog(
        onDismissRequest = {
            if (!isCopying) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = "Bütçeleri Kopyala",
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
                    text = "Kaynak aydaki bütçe tanımlarını hedef aya aktarın. Hedef ayda zaten tanımlı olan bütçeler korunur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Hedef Ay (Salt Okunur)
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                    Text(
                        text = "Hedef Ay",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(FeniqoRadius.Small),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Hedef ay: $formattedTarget"
                            },
                    ) {
                        Text(
                            text = formattedTarget,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = FeniqoSpacing.Medium,
                                vertical = FeniqoSpacing.Small,
                            ),
                        )
                    }
                }

                // Kaynak Ay Seçici
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                    Text(
                        text = "Kaynak Ay",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onSourceMonthChanged(sourceMonth.previousMonth()) },
                            enabled = !isCopying,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics { contentDescription = "Kaynak ayı bir ay geri al" },
                        ) {
                            Text("◀ Önceki")
                        }

                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.semantics {
                                contentDescription = "Kaynak ay: $formattedSource"
                            },
                        ) {
                            Text(
                                text = formattedSource,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = FeniqoSpacing.Medium,
                                    vertical = FeniqoSpacing.Small,
                                ),
                            )
                        }

                        TextButton(
                            onClick = { onSourceMonthChanged(sourceMonth.nextMonth()) },
                            enabled = !isCopying,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics { contentDescription = "Kaynak ayı bir ay ileri al" },
                        ) {
                            Text("Sonraki ▶")
                        }
                    }
                }

                if (isSameMonth) {
                    Text(
                        text = "Kaynak ve hedef ay aynı olamaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCopying && !isSameMonth,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Bütçeleri kopyalamayı onayla" },
            ) {
                if (isCopying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Kopyala")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCopying,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Kopyalamaktan vazgeç" },
            ) {
                Text("Vazgeç")
            }
        },
    )
}
