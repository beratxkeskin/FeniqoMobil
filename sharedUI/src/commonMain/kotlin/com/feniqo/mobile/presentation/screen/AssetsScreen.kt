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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.asset.AssetsUiState
import com.feniqo.mobile.presentation.component.*
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefreshPrices: () -> Unit,
    onAddAsset: () -> Unit,
    onAssetClick: (EntityId) -> Unit,
    onDistributionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AssetWarmBackground,
        bottomBar = {
            if (!state.isLoading && state.observationError == null && !state.isEmpty) {
                Surface(
                    color = AssetWarmBackground,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = onAddAsset,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AssetSageGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Yeni varlık",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            // Üst Bar: Geri butonu, Başlık ve (+) Daire butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color(0xFF1E293B),
                        )
                    }
                    Text(
                        text = "Varlıklar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                }

                // Sağ üst dairesel (+) butonu
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AssetSageGreen)
                        .clickable(onClick = onAddAsset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Yeni Varlık Ekle",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> {
                    LoadingContent(
                        message = "Varlıklar yükleniyor...",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.observationError != null -> {
                    ErrorState(
                        title = "Varlıklar Yüklenemedi",
                        description = state.observationError.toDisplayText(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.isEmpty -> {
                    // 11 Numaralı Tasarım: Boş durum
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            // Büyük cüzdan ikonu yumuşak daire içinde
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE2EFE7)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = AssetSageGreen,
                                    modifier = Modifier.size(56.dp),
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Henüz varlığın yok",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Varlıklarını ekleyerek birikiminin dağılımını takip et.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(28.dp))

                            Button(
                                onClick = onAddAsset,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AssetSageGreen,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "İlk varlığını ekle",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // 01 & 08: Grafit Özet Kartı
                        item(key = "graphite_summary") {
                            AssetGraphiteSummaryCard(
                                netWorth = state.netWorth,
                                hasError = state.netWorthError,
                            )
                        }

                        // 01: Dağılım Kartı (Tıklanabilir -> 03 Dağılım detayına gider)
                        if (state.distributionSummary != null && state.distributionSummary.items.isNotEmpty()) {
                            item(key = "distribution_preview") {
                                AssetDistributionPreviewCard(
                                    distribution = state.distributionSummary,
                                    onClick = onDistributionClick,
                                )
                            }
                        }

                        // Fiyat yenileme geri bildirimi ve eylemi
                        if (state.assets.any { it.autoTrack }) {
                            item(key = "price_refresh_row") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (state.priceRefreshError) {
                                        Text(
                                            text = "Piyasa fiyatı güncellenemedi; kayıtlı değer gösteriliyor.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }

                                    TextButton(
                                        onClick = onRefreshPrices,
                                        enabled = !state.isRefreshingPrices,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = AssetSageGreen,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (state.isRefreshingPrices) "Yenileniyor…" else "Fiyatları yenile",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AssetSageGreen,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }

                        // Varlık kartları listesi
                        items(state.assets, key = { it.id.value }) { asset ->
                            AssetItemCard(
                                asset = asset,
                                onClick = { onAssetClick(asset.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
