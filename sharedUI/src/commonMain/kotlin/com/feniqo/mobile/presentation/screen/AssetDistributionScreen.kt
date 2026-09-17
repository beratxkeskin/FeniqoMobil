package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.asset.AssetDistributionUiState
import com.feniqo.mobile.presentation.component.*

@Composable
fun AssetDistributionScreen(
    state: AssetDistributionUiState,
    onBack: () -> Unit,
    onSelectCurrency: (Currency) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distribution = state.distribution

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AssetWarmBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF1E293B),
                    )
                }

                Text(
                    text = "Varlık dağılımı",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingContent(
                    message = "Dağılım hesaplanıyor…",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            state.observationError != null -> {
                ErrorState(
                    title = "Dağılım Yüklenemedi",
                    description = state.observationError.toDisplayText(),
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Para Birimi Seçim Çipleri (03 Numaralı Tasarım)
                    if (state.availableCurrencies.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.availableCurrencies.forEach { currency ->
                                val isSelected = currency == state.selectedCurrency
                                val bg = if (isSelected) AssetSageGreen else Color(0xFFE2E8F0)
                                val textColor = if (isSelected) Color.White else Color(0xFF475569)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(bg)
                                        .clickable { onSelectCurrency(currency) }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = currency.code,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor,
                                    )
                                }
                            }
                        }
                    }

                    // Grafit Özet Kartı + Donut Grafiği
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = AssetGraphiteBg),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Toplam varlık değeri",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = distribution?.overallTotalFormatted ?: "0,00 ₺",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${distribution?.assetCount ?: 0} varlık · Kayıtlı değerler",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            // Donut Grafiği
                            AssetDonutChart(
                                items = distribution?.items.orEmpty(),
                                totalAssetCount = distribution?.assetCount ?: 0,
                            )
                        }
                    }

                    // Tür Bazlı Dağılım Listesi
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        if (distribution == null || distribution.items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Bu para biriminde kayıtlı varlık bulunmuyor.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF64748B),
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(distribution.items, key = { it.type.name }) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(getAssetTypeColor(item.type)),
                                            )
                                            Text(
                                                text = item.typeLabel,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1E293B),
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = item.formattedTotal,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                            )
                                            Text(
                                                text = item.percentageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF64748B),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Alt Kapsam Bilgi Kutusu: (i) Yalnız TRY cinsindeki kayıtlar dahildir.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Yalnız ${state.selectedCurrency.code} cinsindeki kayıtlar dahildir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
