package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.asset.*
import com.feniqo.mobile.presentation.common.formatLocalizedMoney
import com.feniqo.mobile.presentation.component.*
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }
    val currentValueFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }
    val purchasePriceFocusRequester = remember { FocusRequester() }
    val trackingSymbolFocusRequester = remember { FocusRequester() }
    val nameErrorText = state.errors.name?.toLocalizedText()
    val currentValueErrorText = state.errors.currentValue?.toLocalizedText()
    val quantityErrorText = state.errors.quantity?.toLocalizedText()
    val purchasePriceErrorText = state.errors.purchaseUnitPrice?.toLocalizedText()
    val trackingSymbolErrorText = state.errors.trackingSymbol?.toLocalizedText()
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(state.errors, input.autoTrack) {
        when {
            state.errors.name != null -> nameFocusRequester.requestFocus()
            state.errors.currentValue != null -> currentValueFocusRequester.requestFocus()
            state.errors.quantity != null -> quantityFocusRequester.requestFocus()
            state.errors.purchaseUnitPrice != null -> purchasePriceFocusRequester.requestFocus()
            input.autoTrack && state.errors.trackingSymbol != null -> trackingSymbolFocusRequester.requestFocus()
        }
    }

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
                IconButton(onClick = onBack, enabled = !state.isSubmitting) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.asset_back_action),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text =
                        if (isEditMode) {
                            stringResource(Res.string.asset_form_edit_title)
                        } else {
                            stringResource(Res.string.asset_form_create_title)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (isEditMode) {
                    IconButton(onClick = onRequestDelete, enabled = !state.isSubmitting) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(Res.string.asset_delete_confirm_action),
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
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isSubmitting,
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
                                    text = stringResource(Res.string.asset_form_save_changes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(Res.string.asset_form_save_create),
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Varlık Adı
            val nameLabel = stringResource(Res.string.asset_form_name_label)
            OutlinedTextField(
                value = input.nameInput,
                onValueChange = { value -> onInputChange { it.copy(nameInput = value) } },
                label = { Text(nameLabel) },
                placeholder = { Text(stringResource(Res.string.asset_form_name_placeholder)) },
                isError = state.errors.name != null,
                supportingText =
                    if (nameErrorText != null) {
                        { Text(nameErrorText) }
                    } else {
                        null
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions =
                    KeyboardActions(
                        onNext = { currentValueFocusRequester.requestFocus() },
                    ),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                        .semantics {
                            contentDescription = nameLabel
                            if (nameErrorText != null) error(nameErrorText)
                        },
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AssetSageGreen,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                    ),
            )

            // 2. Varlık Türü Seçici (07 Sheet açar)
            Column {
                Text(
                    text = stringResource(Res.string.asset_form_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTypePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier =
                            Modifier
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
                                text = input.type.toLocalizedLabelText(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.asset_select_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 3. Güncel Toplam Değer (04 / 06: Grafit Kart İçinde Düzenlenebilir Alan)
            val currentValueDesc = stringResource(Res.string.asset_form_current_value_label)
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
                        text = currentValueDesc,
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
                            style =
                                TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                ),
                        )

                        BasicTextField(
                            value = input.currentValueInput,
                            onValueChange = { value -> onInputChange { it.copy(currentValueInput = value) } },
                            textStyle =
                                TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onNext = { quantityFocusRequester.requestFocus() },
                                ),
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(currentValueFocusRequester)
                                    .semantics {
                                        contentDescription = currentValueDesc
                                        if (currentValueErrorText != null) error(currentValueErrorText)
                                    },
                            decorationBox = { innerTextField ->
                                if (input.currentValueInput.isEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.asset_form_current_value_placeholder),
                                        style =
                                            TextStyle(
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
                        text = stringResource(Res.string.asset_form_current_value_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )

                    if (state.errors.currentValue != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = currentValueErrorText.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFCA5A5),
                        )
                    }
                }
            }

            // 4. Para Birimi Seçici (13 Sheet açar)
            Column {
                Text(
                    text = stringResource(Res.string.asset_form_currency_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCurrencyPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${input.currency.code} (${input.currency.symbol})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.asset_select_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 5. Miktar (İsteğe Bağlı)
            val quantityLabel = stringResource(Res.string.asset_form_quantity_label)
            val quantityDesc = stringResource(Res.string.asset_form_quantity_desc)
            OutlinedTextField(
                value = input.quantityInput,
                onValueChange = { value -> onInputChange { it.copy(quantityInput = value) } },
                label = { Text(quantityLabel) },
                placeholder = { Text(stringResource(Res.string.asset_form_quantity_placeholder)) },
                isError = state.errors.quantity != null,
                supportingText =
                    if (quantityErrorText != null) {
                        { Text(quantityErrorText) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { purchasePriceFocusRequester.requestFocus() },
                    ),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(quantityFocusRequester)
                        .semantics {
                            contentDescription = quantityDesc
                            if (quantityErrorText != null) error(quantityErrorText)
                        },
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AssetSageGreen,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                    ),
            )

            // 6. Alış Birim Fiyatı (İsteğe Bağlı)
            val purchasePriceLabel = stringResource(Res.string.asset_form_purchase_price_label)
            val purchasePriceDesc = stringResource(Res.string.asset_form_purchase_price_desc)
            OutlinedTextField(
                value = input.purchaseUnitPriceInput,
                onValueChange = { value -> onInputChange { it.copy(purchaseUnitPriceInput = value) } },
                label = { Text(purchasePriceLabel) },
                placeholder = { Text(stringResource(Res.string.asset_form_purchase_price_placeholder)) },
                prefix = { Text("${input.currency.symbol} ", fontWeight = FontWeight.SemiBold) },
                isError = state.errors.purchaseUnitPrice != null,
                supportingText =
                    if (purchasePriceErrorText != null) {
                        { Text(purchasePriceErrorText) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (input.autoTrack) ImeAction.Next else ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { trackingSymbolFocusRequester.requestFocus() },
                        onDone = { dismissKeyboard() },
                    ),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(purchasePriceFocusRequester)
                        .semantics {
                            contentDescription = purchasePriceDesc
                            if (purchasePriceErrorText != null) error(purchasePriceErrorText)
                        },
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AssetSageGreen,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                    ),
            )

            // 7. Hesaplanan Maliyet Canlı Önizleme Kartı (05)
            val calculatedCost = state.calculatedCost ?: input.computeCost()
            if (calculatedCost != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(Res.string.asset_form_calculated_cost_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatLocalizedMoney(calculatedCost),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 8. Fiyat Takibi Bölümü (05 / 10)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.asset_form_price_tracking_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.asset_form_auto_track_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.asset_form_auto_track_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = input.autoTrack,
                            onCheckedChange = { checked -> onInputChange { it.copy(autoTrack = checked) } },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AssetSageGreen,
                                ),
                        )
                    }

                    if (input.autoTrack) {
                        val trackingSymbolLabel = stringResource(Res.string.asset_form_tracking_symbol_label)
                        OutlinedTextField(
                            value = input.trackingSymbolInput,
                            onValueChange = { value -> onInputChange { it.copy(trackingSymbolInput = value) } },
                            label = { Text(trackingSymbolLabel) },
                            placeholder = { Text(stringResource(Res.string.asset_form_tracking_symbol_placeholder)) },
                            isError = state.errors.trackingSymbol != null,
                            supportingText =
                                if (trackingSymbolErrorText != null) {
                                    { Text(trackingSymbolErrorText) }
                                } else {
                                    null
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                            singleLine = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(trackingSymbolFocusRequester)
                                    .semantics {
                                        contentDescription = trackingSymbolLabel
                                        if (trackingSymbolErrorText != null) error(trackingSymbolErrorText)
                                    },
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AssetSageGreen,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    errorBorderColor = Color(0xFFDC2626),
                                ),
                        )

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
                                text = stringResource(Res.string.asset_form_tracking_symbol_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
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
                                text = stringResource(Res.string.asset_form_manual_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        val fallbackName = stringResource(Res.string.asset_delete_fallback_name)
        AssetDeleteConfirmationModal(
            assetName = input.nameInput.ifBlank { fallbackName },
            isSubmitting = state.isSubmitting,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}
