package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.asset.AssetDetailDisplayModel
import com.feniqo.mobile.presentation.asset.AssetDetailUiState
import com.feniqo.mobile.presentation.asset.toDisplayLabel
import com.feniqo.mobile.presentation.component.*

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
        containerColor = AssetWarmBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF1E293B),
                    )
                }

                Text(
                    text = "Varlıklar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Diğer İşlemler",
                            tint = Color(0xFF1E293B),
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color.White,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Varlığı sil",
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
                    color = AssetWarmBackground,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF1E293B),
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (state.isRefreshingPrice) "Deneniyor…" else "Yeniden dene",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Button(
                                    onClick = onEdit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
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
                                        text = "Düzenle",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        } else {
                            // 02 Numaralı Tasarım: Tek birincil Düzenle butonu
                            Button(
                                onClick = onEdit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
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
                                    text = "Varlığı düzenle",
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
                    message = "Varlık yükleniyor…",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            state.isNotFound || asset == null -> {
                ErrorState(
                    title = "Varlık Bulunamadı",
                    description = "Aradığın varlık silinmiş veya mevcut değil.",
                    onRetry = onBack,
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
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 09: Piyasa fiyatı alınamadı bilgi banner'ı
                    if (asset.isPriceVerificationFailed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Güncel piyasa fiyatı alınamadı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                            )
                        }
                    }

                    // Varlık Başlık Alanı (İkon + Ad + Tür)
                    Row(
                        modifier = Modifier
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
                                color = Color(0xFF0F172A),
                            )
                            Text(
                                text = asset.typeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        ) {
                            Text(
                                text = if (asset.isPriceVerificationFailed) "Son kayıtlı toplam değer" else "Güncel toplam değer",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = asset.currentValueFormatted,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(6.dp))

                            if (asset.isPriceVerificationFailed) {
                                // 09: Sarı / Amber uyarı rozeti
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEF3C7))
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
                                        text = "Güncelliği doğrulanamadı.",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF92400E),
                                    )
                                }
                            } else {
                                Text(
                                    text = asset.valueSourceLabel,
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
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "Miktar",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = asset.quantityFormatted ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                )
                                if (asset.quantityFormatted != null) {
                                    Text(
                                        text = "birim",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                    )
                                }
                            }
                        }

                        // Alış Birim Fiyatı Kartı
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "Alış birim fiyatı",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = asset.purchaseUnitPriceFormatted ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                )
                            }
                        }
                    }

                    // 09 Durumundaki ek miktar ve doğrulanamadı uyarısı
                    if (asset.isPriceVerificationFailed) {
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
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Bu değer güncel piyasa değeri olarak doğrulanmamıştır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                            )
                        }
                    }

                    // Finansal Sonuç Kartı: Maliyet ve Değer Farkı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            if (asset.hasCalculatedCost) {
                                Text(
                                    text = "Hesaplanan maliyet",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = asset.calculatedCostFormatted ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                )

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = "Değer farkı",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF64748B),
                                )
                                Spacer(Modifier.height(4.dp))

                                val diffColor = when (asset.isDifferencePositive) {
                                    true -> AssetSageGreen
                                    false -> Color(0xFFDC2626)
                                    null -> Color(0xFF475569)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = asset.differenceFormatted ?: "0,00 ₺",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = diffColor,
                                    )
                                    asset.differencePercentageText?.let { pct ->
                                        Text(
                                            text = pct,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = diffColor,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // Bilgilendirme kutusu
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Girilen miktar ve alış fiyatından hesaplanır. Gerçekleşmiş kazanç değildir.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF475569),
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
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "Maliyet ve Değer Farkı",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B),
                                        )
                                        Text(
                                            text = "Miktar ve alış birim fiyatı girildiğinde maliyet ve değer farkı otomatik hesaplanır.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // "Değer Kaynağı" Satırı
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSourceInfoDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(
                            modifier = Modifier
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
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Değer kaynağı",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = asset.valueSource.toDisplayLabel(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF64748B),
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Bilgi",
                                    tint = Color(0xFF94A3B8),
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
