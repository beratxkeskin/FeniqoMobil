package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetDetailTransactionItem
import com.feniqo.mobile.presentation.budget.BudgetDetailUiState
import com.feniqo.mobile.presentation.component.BudgetSectionHeader
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoBackgroundSand = Color(0xFFF7F5F0)

/**
 * 03 Bütçe Detayı ve B06 Bütçe Aşımı ekranının durumsuz Compose sunumudur.
 */
@Composable
fun BudgetDetailScreen(
    state: BudgetDetailUiState,
    onBack: () -> Unit,
    onEditBudget: (EntityId, YearMonth) -> Unit,
    onDeleteBudget: () -> Unit,
    onViewAllTransactions: (categoryId: EntityId, month: YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoBackgroundSand,
    ) {
        val budget = state.budget
        when {
            state.isLoading -> {
                LoadingContent(
                    message = "Bütçe detayları yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.observationError != null || budget == null -> {
                ErrorState(
                    title = "Bütçe Bulunamadı",
                    description = state.observationError?.toDisplayText() ?: "Bütçe detayı yüklenemedi.",
                    onRetry = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                val isExceeded = budget.health == BudgetHealth.EXCEEDED
                val categoryColor = ColorParser.parseHexColorOrNull(budget.categoryColorHex)
                    ?: MaterialTheme.colorScheme.primary
                val formattedMonth = DateFormatter.formatYearMonth(budget.month)

                var menuExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = FeniqoSpacing.Large)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                ) {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // 1. Üst Bar: Geri Oku, Başlık ve Seçenekler Menüsü
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri dön",
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExceeded) "Bütçe aşımı" else "${budget.categoryName} bütçesi",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Bütçe seçenekleri")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bütçeyi düzenle") },
                                    onClick = {
                                        menuExpanded = false
                                        onEditBudget(budget.id, budget.month)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Bütçeyi sil", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteBudget()
                                    },
                                )
                            }
                        }
                    }

                    // 2. Hero Alanı: Büyük İkon, Kategori Adı, Ay, Dev Tutar ve Durum Rozeti
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FeniqoSpacing.Small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CategoryTonalIcon(
                            iconKey = budget.categoryIconKey,
                            color = categoryColor,
                            containerSize = 64.dp,
                        )

                        Text(
                            text = budget.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = formattedMonth,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Dev Tutar (Kalan veya Aşım)
                        Text(
                            text = budget.prefixRemaining,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                        )

                        // Durum Hapı
                        Surface(
                            shape = CircleShape,
                            color = if (isExceeded) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                text = if (isExceeded) "Bütçe aşımı" else "Kalan bütçe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isExceeded) Color(0xFFDC2626) else Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // 3. Limit ve Harcama İlerleme Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FeniqoRadius.Large),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(FeniqoSpacing.Large),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = if (isExceeded) "Aylık limit" else "Limit",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = budget.prefixLimit,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Harcanan",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = budget.prefixSpent,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                                if (isExceeded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Kullanım oranı",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = budget.compactUsageRate,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                ) {
                                    LinearProgressIndicator(
                                        progress = { budget.usageProgressFraction },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isExceeded) Color(0xFFEF4444) else FeniqoSageGreen,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    )
                                    if (!isExceeded) {
                                        Text(
                                            text = budget.compactUsageRate,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = FeniqoSageGreen,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Durum Bildirim Kartı (Limitin İçinde vs Aşım Uyarısı)
                    if (isExceeded) {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            color = Color(0xFFFEF2F2), // Light red
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(FeniqoSpacing.Medium),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFEF4444), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(
                                        text = "Belirlediğin limiti ${budget.prefixRemaining} aştın.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B),
                                    )
                                    Text(
                                        text = "Bu kategoride belirlediğin aylık bütçeyi aştın.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF991B1B),
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            color = Color(0xFFF0FDF4), // Light green
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(FeniqoSageGreen, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Text(
                                    text = "Limitin içinde",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534),
                                )
                            }
                        }
                    }

                    // 5. "▎Son harcamalar" Bölümü
                    BudgetSectionHeader("Son harcamalar")

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FeniqoRadius.Large),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state.recentTransactions.isEmpty()) {
                                Text(
                                    text = "Bu ay için henüz harcama kaydı yok.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(FeniqoSpacing.Large),
                                )
                            } else {
                                state.recentTransactions.forEachIndexed { index, tx ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = FeniqoSpacing.Medium, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                                    ) {
                                        CategoryTonalIcon(
                                            iconKey = tx.categoryIconKey ?: budget.categoryIconKey,
                                            color = categoryColor,
                                            containerSize = 36.dp,
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tx.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = tx.formattedDate,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Text(
                                            text = tx.formattedAmount,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    if (index < state.recentTransactions.lastIndex) {
                                        androidx.compose.material3.HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }

                            // Alt Geçiş Linki: "Bu ayın tüm işlemleri >" / "İlgili işlemleri gör >"
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewAllTransactions(budget.categoryId, budget.month) }
                                    .padding(horizontal = FeniqoSpacing.Medium, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = if (isExceeded) "İlgili işlemleri gör" else "Bu ayın tüm işlemleri",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // 6. Alt Butonlar
                    Button(
                        onClick = { onEditBudget(budget.id, budget.month) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    ) {
                        Text(
                            text = if (isExceeded) "Limiti düzenle" else "Bütçeyi düzenle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (!isExceeded) {
                        Button(
                            onClick = onDeleteBudget,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFEE2E2), // Yumuşak kırmızı zemin
                                contentColor = Color(0xFFDC2626),
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Bütçeyi sil",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
                }
            }
        }
    }
}
