package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.component.BudgetPeriodPickerSheet
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.DateFormatter

private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoBackgroundSand = Color(0xFFF7F5F0)

/**
 * B05 Bütçeleri Kopyala ekranının durumsuz Compose sunumudur.
 */
@Composable
fun BudgetCopyScreen(
    sourceMonth: YearMonth,
    targetMonth: YearMonth,
    isCopying: Boolean,
    onSourceMonthChanged: (YearMonth) -> Unit,
    onConfirmCopy: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSourcePicker by remember { mutableStateOf(false) }

    val formattedSource = DateFormatter.formatYearMonth(sourceMonth)
    val formattedTarget = DateFormatter.formatYearMonth(targetMonth)
    val isSameMonth = sourceMonth == targetMonth

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoBackgroundSand,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                // 1. Üst Bar: Geri Oku ve Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel, enabled = !isCopying) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri dön",
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bütçeleri kopyala",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // 2. Bilgi Banner'ı: Sadece bütçe tanımları kopyalanır
                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = Color(0xFFEFF6FF), // Soft blue
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                text = "Sadece bütçe tanımları kopyalanır.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                            )
                            Text(
                                text = "Harcamalar taşınmaz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1E40AF),
                            )
                        }
                    }
                }

                // 3. Ay Eşleme Kartı (Kaynak ay -> Hedef ay)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Large),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Kaynak Ay Seçici
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Kaynak ay",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                onClick = { showSourcePicker = true },
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = formattedSource,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Kaynak ay seç",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        // Ok Simgesi
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small),
                        )

                        // Hedef Ay
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Hedef ay",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = formattedTarget,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                // 4. Uyarı Banner'ı: Hedef aydaki mevcut bütçeler korunur
                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = Color(0xFFFEF3C7), // Soft amber
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                        Column {
                            Text(
                                text = "Hedef aydaki mevcut bütçeler korunur.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                            )
                            Text(
                                text = "Aynı kategoride bütçe varsa üzerine yazılmaz, atlanır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                            )
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

            // 5. Alt Butonlar: Kopyala ve Vazgeç
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FeniqoSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Button(
                    onClick = onConfirmCopy,
                    enabled = !isCopying && !isSameMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    if (isCopying) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Kopyala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCopying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                ) {
                    Text("Vazgeç", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Kaynak Ay Seçici Modal
        if (showSourcePicker) {
            BudgetPeriodPickerSheet(
                initialMonth = sourceMonth,
                initialCurrency = Currency.TRY,
                onApply = { newMonth, _ ->
                    onSourceMonthChanged(newMonth)
                    showSourcePicker = false
                },
                onDismiss = { showSourcePicker = false },
            )
        }
    }
}
