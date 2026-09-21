package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.asset.*
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.util.MoneyFormatter

// Renk paleti (Mockup 01-13 uyumlu)
val AssetSageGreen = Color(0xFF2D5A43)
val AssetSageGreenContainer = Color(0xFFE8F1EC)
val AssetGraphiteBg = Color(0xFF303536)
val AssetPureWhite = Color(0xFFFFFFFF)
val AssetWarmBackground = Color(0xFFF7F5F0)

// Tip renkleri (Dağılım çubuğu ve donut grafiği için tutarlı tonlar)
fun getAssetTypeColor(type: AssetType): Color = when (type) {
    AssetType.PRECIOUS_METALS -> Color(0xFF2D5A43) // Koyu zümrüt / adaçayı yeşili
    AssetType.STOCKS -> Color(0xFF7FA88F)          // Orta adaçayı yeşili
    AssetType.CASH -> Color(0xFF6B7280)            // Slate / grafit gri
    AssetType.CRYPTO -> Color(0xFF3F6354)          // Derin orman yeşili
    AssetType.REAL_ESTATE -> Color(0xFF9EBAA8)     // Açık adaçayı
    AssetType.OTHER -> Color(0xFF9CA3AF)           // Nötr açık gri
}

/**
 * Varlık türü semantik ikonu.
 * 48dp yumuşak nane/adaçayı kutusu, ince ve net vektör ikonu.
 */
@Composable
fun AssetTypeSemanticIcon(
    type: AssetType,
    modifier: Modifier = Modifier,
    containerSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    containerColor: Color = AssetSageGreenContainer,
    iconColor: Color = AssetSageGreen,
) {
    val icon: ImageVector = when (type) {
        AssetType.CASH -> Icons.Outlined.AccountBalanceWallet
        AssetType.CRYPTO -> Icons.Outlined.CurrencyBitcoin
        AssetType.STOCKS -> Icons.Outlined.TrendingUp
        AssetType.REAL_ESTATE -> Icons.Outlined.Home
        AssetType.PRECIOUS_METALS -> Icons.Outlined.ViewInAr
        AssetType.OTHER -> Icons.Outlined.Category
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.toDisplayLabel(),
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 01 & 08 numaralı tasarımlara uygun Grafit Özet Kartı.
 * Tek para biriminde büyük tutar; çoklu para biriminde liste sunar.
 */
@Composable
fun AssetGraphiteSummaryCard(
    netWorth: NetWorthDisplayModel?,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AssetGraphiteBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Sağ üst dekoratif zarif yaylar (Canvas)
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.TopEnd),
            ) {
                val stroke = Stroke(width = 1.2f)
                val color = Color.White.copy(alpha = 0.08f)
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(-size.width * 0.2f, -size.height * 0.4f),
                    size = Size(size.width * 1.4f, size.height * 1.4f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 30f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(-size.width * 0.1f, -size.height * 0.2f),
                    size = Size(size.width * 1.1f, size.height * 1.1f),
                    style = stroke,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (hasError) {
                    Text(
                        text = "Varlık Toplamı",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Varlık toplamı güvenli biçimde hesaplanamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFCA5A5),
                    )
                } else if (netWorth == null) {
                    Text(
                        text = "Varlık Toplamı",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "0,00 ₺",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                } else if (!netWorth.hasMultipleCurrencies) {
                    // 01 Numaralı Tasarım: Tek para birimi
                    val currencyName = netWorth.primaryCurrency?.code ?: "TRY"
                    Text(
                        text = "$currencyName varlık toplamı",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = netWorth.primaryTotalFormatted ?: "0,00 ₺",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${netWorth.assetCount} varlık · ${netWorth.sourceSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    // 08 Numaralı Tasarım: Çoklu para birimi
                    Text(
                        text = "Para birimine göre toplamlar",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        netWorth.currencyTotals.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Para birimi sembol rozeti
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = item.currency.symbol,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                    Text(
                                        text = item.currency.code,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }
                                Text(
                                    text = item.formattedTotal,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Kur dönüşümü yapılmadı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 01 Numaralı Tasarım: Dağılım Önizleme Kartı.
 * Orantılı yatay segment çubuğu ve altında tür lejant listesi.
 */
@Composable
fun AssetDistributionPreviewCard(
    distribution: AssetDistributionDisplayModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dağılım",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Dağılım detayını aç",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            if (distribution == null || distribution.items.isEmpty()) {
                Text(
                    text = "Dağılım gösterilecek varlık kaydı bulunmuyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            } else {
                // Segmentli Çubuk (Horizontal Segmented Bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    distribution.items.forEach { item ->
                        val weight = item.percentageBps.coerceAtLeast(1).toFloat()
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(getAssetTypeColor(item.type)),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Lejant listesi
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    distribution.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(getAssetTypeColor(item.type)),
                                )
                                Text(
                                    text = item.typeLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF334155),
                                )
                            }
                            Text(
                                text = item.percentageText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 03 Numaralı Tasarım: Donut Dağılım Çarkı.
 */
@Composable
fun AssetDonutChart(
    items: List<AssetDistributionItemDisplayModel>,
    totalAssetCount: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
) {
    Box(
        modifier = modifier.size(130.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)

            if (items.isEmpty()) {
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
            } else {
                var currentAngle = -90f
                items.forEach { item ->
                    val sweep = (item.percentageBps.toFloat() / 10_000f) * 360f
                    if (sweep > 0f) {
                        drawArc(
                            color = getAssetTypeColor(item.type),
                            startAngle = currentAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx),
                        )
                        currentAngle += sweep
                    }
                }
            }
        }

        // Merkez: Varlık Sayısı
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalAssetCount.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "varlık",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 01 Numaralı Tasarım: Varlık Listesi Kartı.
 */
@Composable
fun AssetItemCard(
    asset: AssetDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AssetTypeSemanticIcon(type = asset.type)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = asset.currentValueFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
                Spacer(Modifier.height(2.dp))

                val subParts = buildList {
                    asset.quantityFormatted?.let { add("$it birim") }
                    add(asset.valueSource.toDisplayLabel())
                }
                Text(
                    text = subParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "${asset.name} detayına git",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 07 Numaralı Tasarım: Varlık Türü Seçici Modal Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetTypeSelectionBottomSheet(
    selectedType: AssetType,
    onTypeSelect: (AssetType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Varlık türü",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetType.entries.forEach { type ->
                    val isSelected = type == selectedType
                    val bg = if (isSelected) AssetSageGreenContainer else Color.Transparent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable {
                                onTypeSelect(type)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AssetTypeSemanticIcon(
                            type = type,
                            containerSize = 40.dp,
                            iconSize = 20.dp,
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.toDisplayLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color(0xFF0F172A),
                            )
                            Text(
                                text = type.toSubtitle(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Seçili",
                                tint = AssetSageGreen,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 13 Numaralı Tasarım: Para Birimi Seçici Modal Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectionBottomSheet(
    selectedCurrency: Currency,
    onCurrencySelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) Currency.entries.toList()
        else Currency.entries.filter {
            it.code.lowercase().contains(q) ||
                it.symbol.lowercase().contains(q) ||
                it.toTurkishName().lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Para birimi seç",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Arama kutusu
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Para birimi ara", color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AssetSageGreen,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                ),
            )

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filteredCurrencies.forEach { currency ->
                    val isSelected = currency == selectedCurrency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCurrencySelect(currency)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Sembol dairesi
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AssetSageGreenContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = currency.symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AssetSageGreen,
                            )
                        }

                        Text(
                            text = currency.code,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                        )

                        Text(
                            text = currency.toTurkishName(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f),
                        )

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onCurrencySelect(currency)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AssetSageGreen),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 12 Numaralı Tasarım: Silme Onay Modalı (Sheet / Dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDeleteConfirmationModal(
    assetName: String,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Varlık silinsin mi?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$assetName kaydını silmek istediğine emin misin?",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Bilgilendirme kutusu: Bu işlem bir satış emri oluşturmaz.
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
                    text = "Bu işlem bir satış emri oluşturmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155),
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE2E8F0),
                        contentColor = Color(0xFF334155),
                    ),
                ) {
                    Text("Vazgeç", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White,
                    ),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Sil", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Değer kaynağı açıklama dialogu.
 */
@Composable
fun AssetValueSourceInfoDialog(
    valueSource: AssetValueSource,
    symbol: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Değer Kaynağı", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (valueSource) {
                    AssetValueSource.MARKET_FRESH -> {
                        Text(
                            "Bu varlığın değeri, $symbol sembolü üzerinden güvenilir piyasa fiyat servisi ile güncellenmiştir.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    AssetValueSource.MARKET_STALE -> {
                        Text(
                            "Bu varlık otomatik takip edilmektedir ancak son piyasa fiyatı doğrulanamadı veya güncel değil. Kayıtlı son değer gösterilmektedir.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    AssetValueSource.MANUAL -> {
                        Text(
                            "Bu varlığın toplam değeri manuel olarak girilmiştir. Piyasa fiyatı hareketlerinden etkilenmez; toplam değeri dilediğin zaman düzenleyebilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Anladım", color = AssetSageGreen, fontWeight = FontWeight.Bold)
            }
        },
    )
}

fun Currency.toTurkishName(): String = when (this) {
    Currency.TRY -> "Türk lirası"
    Currency.USD -> "Amerikan doları"
    Currency.EUR -> "Euro"
    Currency.GBP -> "İngiliz sterlini"
}

val Currency.symbol: String
    get() = MoneyFormatter.getCurrencySymbol(this)
