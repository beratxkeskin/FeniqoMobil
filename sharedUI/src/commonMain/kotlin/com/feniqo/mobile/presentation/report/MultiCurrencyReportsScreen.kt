package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary

/**
 * 23 Çoklu para birimi ekranı.
 * Farklı para birimlerini asla birleştirmez; her birini bağımsız kart olarak sunar.
 */
@Composable
fun MultiCurrencyReportsScreen(
    summaries: List<MultiCurrencyReportUiModel>,
    selectedCurrency: Currency,
    onSelectCurrency: (Currency) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = FeniqoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Çoklu para birimi",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. BİLGİ KUTUSU (Farklı para birimleri birleştirilmez)
            item("info_banner") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "i",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Farklı para birimleri birleştirilmez.",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kur dönüşümü yapılmaz. Her para birimi ayrı gösterilir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FeniqoTextSecondary,
                            )
                        }
                    }
                }
            }

            // 2. FİLTRE VE SEÇİM SATIRI
            item("currency_filter_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Currency.entries.forEach { curr ->
                            val isSelected = curr == selectedCurrency
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(role = Role.RadioButton) { onSelectCurrency(curr) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF1E3A2F) else Color(0xFFF3F4F6),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF1E3A2F) else Color(0xFFE5E7EB)
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = curr.code,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (isSelected) Color.White else Color(0xFF374151),
                                    )
                                }
                            }
                        }
                    }

                    // "Para birimini filtrele" butonu
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onOpenFilterSheet),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF374151),
                            )
                            Text(
                                text = "Para birimini filtrele",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF374151),
                            )
                        }
                    }
                }
            }

            // 3. PARA BİRİMİ LİSTESİ KARTLARI
            items(summaries, key = { it.currency.code }) { item ->
                CurrencyReportCard(
                    item = item,
                    onClick = { onSelectCurrency(item.currency) },
                )
            }

            item("spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CurrencyReportCard(
    item: MultiCurrencyReportUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Üst Başlık Satırı (Avatar, Başlık, >)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (avatarBg, avatarText) = when (item.currency) {
                        Currency.TRY -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                        Currency.USD -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
                        Currency.EUR -> Color(0xFFDBEAFE) to Color(0xFF2563EB)
                        Currency.GBP -> Color(0xFFF3E8FF) to Color(0xFF7C3AED)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(avatarBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.symbol,
                            color = avatarText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = FeniqoTextPrimary,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF9CA3AF),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Sütunlu Metrik Alanı (Gelir, Gider, Fark)
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gelir",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.incomeFormatted,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        color = FeniqoTextPrimary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gider",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.expenseFormatted,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        color = Color(0xFFDC2626),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fark",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.netFormatted,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        color = if (item.isNetPositive) Color(0xFF15803D) else Color(0xFFDC2626),
                    )
                }
            }
        }
    }
}
