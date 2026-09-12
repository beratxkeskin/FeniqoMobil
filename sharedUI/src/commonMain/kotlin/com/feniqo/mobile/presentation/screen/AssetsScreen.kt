package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.asset.AssetsUiState
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onRetry: () -> Unit,
    onRefreshPrices: () -> Unit,
    onAddAsset: () -> Unit,
    onAssetClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!state.isLoading && state.observationError == null) {
                FloatingActionButton(onClick = onAddAsset) { Text("+") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(Modifier.height(FeniqoSpacing.Medium))
            Text("Varlıklar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Nakit ve yatırım varlıklarınızı tek yerde takip edin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FeniqoSpacing.Medium))
            if (!state.isLoading && state.observationError == null && state.assets.any { it.autoTrack }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.priceRefreshError) {
                        Text(
                            "Fiyatlar yenilenemedi; kayıtlı veya manuel değerler gösteriliyor.",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onRefreshPrices, enabled = !state.isRefreshingPrices) {
                            Text(if (state.isRefreshingPrices) "Yenileniyor…" else "Fiyatları Yenile")
                        }
                    }
                }
            }
            if (!state.isLoading && state.observationError == null && state.assets.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(FeniqoSpacing.Medium)) {
                        Text("Net Değer", style = MaterialTheme.typography.labelLarge)
                        if (state.netWorthError) {
                            Text(
                                "Net değer güvenli biçimde hesaplanamadı.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            state.netWorth?.totalsFormatted?.forEach { total ->
                                Text(total, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${state.netWorth?.assetCount ?: 0} varlık",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.netWorth?.hasMultipleCurrencies == true) {
                                Text(
                                    "Kur dönüşümü yapılmadan para birimine göre ayrı gösterilir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(FeniqoSpacing.Medium))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoading -> LoadingContent(
                        message = "Varlıklar yükleniyor...",
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.observationError != null -> ErrorState(
                        title = "Varlıklar Yüklenemedi",
                        description = state.observationError.toDisplayText(),
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.isEmpty -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState("Henüz varlık yok.", "İlk varlığınızı ekleyerek net değer takibine başlayın.")
                        Button(onClick = onAddAsset) { Text("Varlık Ekle") }
                    }
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        items(state.assets, key = { it.id.value }) { asset ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { onAssetClick(asset.id) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(FeniqoSpacing.Medium),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(asset.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOfNotNull(asset.typeLabel, asset.trackingSymbol, asset.quantityFormatted).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (asset.autoTrack) {
                                            Text(
                                                when (asset.valueSource) {
                                                    com.feniqo.mobile.presentation.asset.AssetValueSource.MARKET_FRESH -> "Güncel piyasa değeri"
                                                    com.feniqo.mobile.presentation.asset.AssetValueSource.MARKET_STALE -> "Kayıtlı piyasa değeri · güncel olmayabilir"
                                                    com.feniqo.mobile.presentation.asset.AssetValueSource.MANUAL -> "Manuel değer"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (asset.valueSource == com.feniqo.mobile.presentation.asset.AssetValueSource.MARKET_STALE) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(asset.currentValueFormatted, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
