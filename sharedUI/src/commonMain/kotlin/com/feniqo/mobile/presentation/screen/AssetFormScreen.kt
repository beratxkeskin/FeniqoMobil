package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.asset.*
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetFormScreen(
    state: AssetFormUiState,
    onBack: () -> Unit,
    onInputChange: ((AssetFormInput) -> AssetFormInput) -> Unit,
    onSubmit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = state.input
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(FeniqoSpacing.Medium), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack, enabled = !state.isSubmitting) { Text("Geri") }
                Text(if (input.assetId == null) "Varlık Ekle" else "Varlığı Düzenle", style = MaterialTheme.typography.titleLarge)
                if (input.assetId != null) TextButton(onClick = onRequestDelete, enabled = !state.isSubmitting) { Text("Sil") }
                else Spacer(Modifier.width(48.dp))
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            FormField("Ad", input.nameInput, state.errors.name, { value -> onInputChange { it.copy(nameInput = value) } })
            Text("Varlık türü", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetType.entries.forEach { type ->
                    FilterChip(
                        selected = input.type == type,
                        onClick = { onInputChange { it.copy(type = type) } },
                        label = { Text(type.toDisplayLabel()) },
                    )
                }
            }
            Text("Para birimi", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { currency ->
                    FilterChip(
                        selected = input.currency == currency,
                        enabled = input.assetId == null,
                        onClick = { onInputChange { it.copy(currency = currency) } },
                        label = { Text(currency.code) },
                    )
                }
            }
            FormField("Güncel toplam değer", input.currentValueInput, state.errors.currentValue, { value ->
                onInputChange { it.copy(currentValueInput = value) }
            }, true)
            FormField("Miktar (opsiyonel)", input.quantityInput, state.errors.quantity, { value ->
                onInputChange { it.copy(quantityInput = value) }
            }, true)
            FormField("Alış birim fiyatı (opsiyonel)", input.purchaseUnitPriceInput, state.errors.purchaseUnitPrice, { value ->
                onInputChange { it.copy(purchaseUnitPriceInput = value) }
            }, true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Otomatik fiyat takibi")
                    Text("Güvenilir piyasa servisi hazır olduğunda kullanılacak.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = input.autoTrack, onCheckedChange = { checked -> onInputChange { it.copy(autoTrack = checked) } })
            }
            FormField("Piyasa sembolü", input.trackingSymbolInput, state.errors.trackingSymbol, { value ->
                onInputChange { it.copy(trackingSymbolInput = value) }
            })
            Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (input.assetId == null) "Varlığı Kaydet" else "Değişiklikleri Kaydet")
            }
            Spacer(Modifier.height(FeniqoSpacing.Large))
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    error: AssetFormFieldError?,
    onValueChange: (String) -> Unit,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it.toDisplayText()) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun AssetDeleteDialog(isSubmitting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Varlığı sil?") },
        text = { Text("Varlık silinecek ve çevrimdışı senkronizasyon kuyruğuna alınacak.") },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !isSubmitting) { Text("Sil") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Vazgeç") } },
    )
}
