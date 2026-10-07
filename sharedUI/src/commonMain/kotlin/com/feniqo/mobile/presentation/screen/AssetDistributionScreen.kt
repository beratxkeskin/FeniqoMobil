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
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.asset.AssetDistributionUiState
import com.feniqo.mobile.presentation.asset.toLocalizedLabelText
import com.feniqo.mobile.presentation.common.formatLocalizedMoney
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.*
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.asset_back_action),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = stringResource(Res.string.asset_distribution_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingContent(
                    message = stringResource(Res.string.asset_distribution_loading),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }
            state.observationError != null -> {
                ErrorState(
                    title = stringResource(Res.string.asset_distribution_error_title),
                    description = state.observationError.toLocalizedText(),
                    onRetry = onRetry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }
            else -> {
                Column(
                    modifier =
                        Modifier
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
                                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier =
                                        Modifier
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.asset_distribution_card_total_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatLocalizedMoney(distribution?.overallTotal ?: Money.zero(state.selectedCurrency)),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(6.dp))
                                val assetCount = distribution?.assetCount ?: 0
                                val countText = pluralStringResource(Res.plurals.asset_count_plural, assetCount, assetCount)
                                Text(
                                    text = stringResource(Res.string.asset_distribution_card_stored_subtitle, countText),
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        if (distribution == null || distribution.items.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.asset_distribution_empty_for_currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier
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
                                                modifier =
                                                    Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(getAssetTypeColor(item.type)),
                                            )
                                            Text(
                                                text = item.type.toLocalizedLabelText(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatLocalizedMoney(item.total),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = formatLocalizedRateBasisPoints(item.percentageBps),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Alt Kapsam Bilgi Kutusu: (i) Yalnız TRY cinsindeki kayıtlar dahildir.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(Res.string.asset_distribution_currency_scope_note, state.selectedCurrency.code),
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
