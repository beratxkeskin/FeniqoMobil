package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.asset.AssetDetailUiState
import com.feniqo.mobile.presentation.asset.formatLocalizedQuantityUnit
import com.feniqo.mobile.presentation.asset.toLocalizedLabelText
import com.feniqo.mobile.presentation.asset.toLocalizedText
import com.feniqo.mobile.presentation.common.formatAssetQuantity
import com.feniqo.mobile.presentation.common.formatLocalizedMoney
import com.feniqo.mobile.presentation.common.formatLocalizedMoneyDelta
import com.feniqo.mobile.presentation.common.formatLocalizedRateBasisPoints
import com.feniqo.mobile.presentation.component.*
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    state: AssetDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onRetryPrice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSourceInfoDialog by remember { mutableStateOf(false) }

    val asset = state.asset

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.asset_back_action),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = stringResource(Res.string.asset_screen_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.asset_detail_more_actions_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(Res.string.asset_detail_menu_delete),
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRequestDelete()
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (asset != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        if (asset.isPriceVerificationFailed) {
                            // 09 Numaralı Tasarım: Yeniden dene ve Düzenle yan yana
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onRetryPrice,
                                    enabled = !state.isRefreshingPrice,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text =
                                            if (state.isRefreshingPrice) {
                                                stringResource(Res.string.asset_retrying_action)
                                            } else {
                                                stringResource(Res.string.asset_retry_action)
                                            },
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Button(
                                    onClick = onEdit,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = AssetSageGreen,
                                            contentColor = Color.White,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(Res.string.asset_detail_action_edit),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        } else {
                            // 02 Numaralı Tasarım: Tek birincil Düzenle butonu
                            Button(
                                onClick = onEdit,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = AssetSageGreen,
                                        contentColor = Color.White,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(Res.string.asset_detail_action_edit_asset),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingContent(
                    message = stringResource(Res.string.asset_detail_loading),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }
            state.isNotFound || asset == null -> {
                ErrorState(
                    title = stringResource(Res.string.asset_detail_not_found_title),
                    description = stringResource(Res.string.asset_detail_not_found_desc),
                    onRetry = onBack,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    actionLabel = stringResource(Res.string.asset_form_back_action),
                )
            }
            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 09: Piyasa fiyatı alınamadı bilgi banner'ı
                    if (asset.isPriceVerificationFailed) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(Res.string.asset_detail_market_price_failed_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                            )
                        }
                    }

                    // Varlık Başlık Alanı (İkon + Ad + Tür)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AssetTypeSemanticIcon(
                            type = asset.type,
                            containerSize = 56.dp,
                            iconSize = 28.dp,
                        )

                        Column {
                            Text(
                                text = asset.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = asset.type.toLocalizedLabelText(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Grafit Güncel Değer Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = AssetGraphiteBg),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                        ) {
                            Text(
                                text =
                                    if (asset.isPriceVerificationFailed) {
                                        stringResource(Res.string.asset_detail_last_recorded_value_label)
                                    } else {
                                        stringResource(Res.string.asset_detail_current_total_value_label)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatLocalizedMoney(asset.currentValue),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(6.dp))

                            if (asset.isPriceVerificationFailed) {
                                // 09: Sarı / Amber uyarı rozeti
                                Row(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF92400E),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = stringResource(Res.string.asset_detail_unverified_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF92400E),
                                    )
                                }
                            } else {
                                Text(
                                    text = asset.valueSource.toLocalizedText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    // 2 Sütunlu Kart Satırı: Miktar & Alış Birim Fiyatı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Miktar Kartı
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.asset_detail_quantity_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                val decimalSeparator = stringResource(Res.string.common_decimal_separator)
                                val formattedQuantity = asset.quantity?.let { formatAssetQuantity(it, decimalSeparator) }
                                Text(
                                    text = formattedQuantity ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (formattedQuantity != null && asset.quantity != null) {
                                    Text(
                                        text = formatLocalizedQuantityUnit(asset.quantity, decimalSeparator),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Alış Birim Fiyatı Kartı
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.asset_detail_purchase_price_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = asset.purchaseUnitPrice?.let { formatLocalizedMoney(it) } ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // 09 Durumundaki ek miktar ve doğrulanamadı uyarısı
                    if (asset.isPriceVerificationFailed) {
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
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(Res.string.asset_detail_unverified_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                            )
                        }
                    }

                    // Finansal Sonuç Kartı: Maliyet ve Değer Farkı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                        ) {
                            if (asset.calculatedCost != null) {
                                Text(
                                    text = stringResource(Res.string.asset_detail_cost_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatLocalizedMoney(asset.calculatedCost),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = stringResource(Res.string.asset_detail_difference_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))

                                val difference = asset.difference
                                val diffColor =
                                    when {
                                        difference == null -> Color(0xFF475569)
                                        difference.amountMinor > 0 -> AssetSageGreen
                                        difference.amountMinor < 0 -> Color(0xFFDC2626)
                                        else -> Color(0xFF475569)
                                    }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text =
                                            difference?.let { formatLocalizedMoneyDelta(it, showPositiveSign = true) }
                                                ?: formatLocalizedMoney(Money.zero(asset.currentValue.currency)),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = diffColor,
                                    )
                                    asset.differencePercentageBps?.let { bps ->
                                        Text(
                                            text = formatLocalizedRateBasisPoints(bps),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = diffColor,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // Bilgilendirme kutusu
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(Res.string.asset_detail_cost_disclaimer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(Res.string.asset_detail_cost_placeholder_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(Res.string.asset_detail_cost_placeholder_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // "Değer Kaynağı" Satırı
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { showSourceInfoDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.asset_detail_source_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = asset.valueSource.toLocalizedText(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(Res.string.asset_detail_source_info_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Silme Onay Sheet (12)
    if (state.pendingDeleteConfirmation && asset != null) {
        AssetDeleteConfirmationModal(
            assetName = asset.name,
            isSubmitting = state.isDeleting,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }

    // Değer kaynağı bilgi diyaloğu
    if (showSourceInfoDialog && asset != null) {
        AssetValueSourceInfoDialog(
            valueSource = asset.valueSource,
            symbol = asset.trackingSymbol,
            onDismiss = { showSourceInfoDialog = false },
        )
    }
}
