package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.asset.*
import com.feniqo.mobile.presentation.component.*

@Composable
fun AssetFormScreen(
    state: AssetFormUiState,
    onBack: () -> Unit,
    onInputChange: ((AssetFormInput) -> AssetFormInput) -> Unit,
    onSubmit: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = state.input
    val isEditMode = input.assetId != null

    var showTypePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

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
                IconButton(onClick = onBack, enabled = !state.isSubmitting) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF1E293B),
                    )
                }

                Text(
                    text = if (isEditMode) "Varlığı düzenle" else "Yeni varlık",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )

                if (isEditMode) {
                    IconButton(onClick = onRequestDelete, enabled = !state.isSubmitting) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Sil",
                            tint = Color(0xFFDC2626),
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        },
        bottomBar = {
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
                        onClick = onSubmit,
                        enabled = !state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AssetSageGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            if (isEditMode) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Değişiklikleri kaydet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Varlığı kaydet",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Varlık Adı
            OutlinedTextField(
                value = input.nameInput,
                onValueChange = { value -> onInputChange { it.copy(nameInput = value) } },
                label = { Text("Varlık adı") },
                placeholder = { Text("Örn: Gram altın") },
                isError = state.errors.name != null,
                supportingText = state.errors.name?.let { { Text(it.toDisplayText()) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AssetSageGreen,
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                ),
            )

            // 2. Varlık Türü Seçici (07 Sheet açar)
            Column {
                Text(
                    text = "Varlık türü",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTypePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AssetTypeSemanticIcon(
                                type = input.type,
                                containerSize = 36.dp,
                                iconSize = 18.dp,
                            )
                            Text(
                                text = input.type.toDisplayLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A),
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Seç",
                            tint = Color(0xFF64748B),
                        )
                    }
                }
            }

            // 3. Güncel Toplam Değer (04 / 06: Grafit Kart İçinde Düzenlenebilir Alan)
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
                        text = "Güncel toplam değer",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = input.currency.symbol,
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                            ),
                        )

                        BasicTextField(
                            value = input.currentValueInput,
                            onValueChange = { value -> onInputChange { it.copy(currentValueInput = value) } },
                            textStyle = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (input.currentValueInput.isEmpty()) {
                                    Text(
                                        text = "0,00",
                                        style = TextStyle(
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.35f),
                                        ),
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Bu tutar varlığın toplam değeridir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )

                    if (state.errors.currentValue != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.errors.currentValue.toDisplayText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFCA5A5),
                        )
                    }
                }
            }

            // 4. Para Birimi Seçici (13 Sheet açar)
            Column {
                Text(
                    text = "Para birimi",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCurrencyPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${input.currency.code} (${input.currency.symbol})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A),
                        )

                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Seç",
                            tint = Color(0xFF64748B),
                        )
                    }
                }
            }

            // 5. Miktar (İsteğe Bağlı)
            OutlinedTextField(
                value = input.quantityInput,
                onValueChange = { value -> onInputChange { it.copy(quantityInput = value) } },
                label = { Text("Miktar (isteğe bağlı)") },
                placeholder = { Text("Örn: 30") },
                isError = state.errors.quantity != null,
                supportingText = state.errors.quantity?.let { { Text(it.toDisplayText()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AssetSageGreen,
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                ),
            )

            // 6. Alış Birim Fiyatı (İsteğe Bağlı)
            OutlinedTextField(
                value = input.purchaseUnitPriceInput,
                onValueChange = { value -> onInputChange { it.copy(purchaseUnitPriceInput = value) } },
                label = { Text("Alış birim fiyatı (isteğe bağlı)") },
                placeholder = { Text("Örn: 4000") },
                prefix = { Text("${input.currency.symbol} ", fontWeight = FontWeight.SemiBold) },
                isError = state.errors.purchaseUnitPrice != null,
                supportingText = state.errors.purchaseUnitPrice?.let { { Text(it.toDisplayText()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AssetSageGreen,
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                ),
            )

            // 7. Hesaplanan Maliyet Canlı Önizleme Kartı (05)
            val costPreview = state.calculatedCostPreview ?: input.computeCostPreview()
            if (costPreview != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Column {
                            Text(
                                text = "Hesaplanan maliyet",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = costPreview,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                            )
                        }
                    }
                }
            }

            // 8. Fiyat Takibi Bölümü (05 / 10)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Fiyat takibi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Otomatik fiyat takibi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A),
                            )
                            Text(
                                text = "Desteklenen semboller ve fiyat servisi gerektirir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                            )
                        }

                        Switch(
                            checked = input.autoTrack,
                            onCheckedChange = { checked -> onInputChange { it.copy(autoTrack = checked) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AssetSageGreen,
                            ),
                        )
                    }

                    if (input.autoTrack) {
                        OutlinedTextField(
                            value = input.trackingSymbolInput,
                            onValueChange = { value -> onInputChange { it.copy(trackingSymbolInput = value) } },
                            label = { Text("Piyasa sembolü") },
                            placeholder = { Text("Örn: BTC") },
                            isError = state.errors.trackingSymbol != null,
                            supportingText = state.errors.trackingSymbol?.let { { Text(it.toDisplayText()) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AssetSageGreen,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                errorBorderColor = Color(0xFFDC2626),
                            ),
                        )

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
                                text = "Veri sağlayıcısının sembolü desteklemesi gerekir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                            )
                        }
                    } else {
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
                                text = "Manuel girişte toplam değeri sen güncellersin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 07: Varlık Türü Bottom Sheet
    if (showTypePicker) {
        AssetTypeSelectionBottomSheet(
            selectedType = input.type,
            onTypeSelect = { type -> onInputChange { it.copy(type = type) } },
            onDismiss = { showTypePicker = false },
        )
    }

    // 13: Para Birimi Bottom Sheet
    if (showCurrencyPicker) {
        CurrencySelectionBottomSheet(
            selectedCurrency = input.currency,
            onCurrencySelect = { curr -> onInputChange { it.copy(currency = curr) } },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    // 12: Silme Onayı Modalı
    if (state.pendingDeleteConfirmation) {
        AssetDeleteConfirmationModal(
            assetName = input.nameInput.ifBlank { "Bu varlık" },
            isSubmitting = state.isSubmitting,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}
