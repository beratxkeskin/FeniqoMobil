package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.workspace.WorkspaceCreateUiState

/**
 * Yeni Çalışma Alanı Oluşturma Ekranı.
 * Onaylı Görsel 01 (Orta Ekran) ve Görsel 12 (Seçim Diyalogları) tasarımına sadık kalınarak hazırlanmıştır.
 */
@Composable
fun WorkspaceCreateScreen(
    state: WorkspaceCreateUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onTypeChanged: (WorkspaceType) -> Unit,
    onCurrencyChanged: (Currency) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTypePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Üst Bar: Geri Butonu & Başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = state.isFormEnabled,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Geri dön" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Alan oluştur",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Hata Mesajı Bannerı
            if (state.errorMessage != null) {
                CreateErrorBanner(
                    message = state.errorMessage,
                    onDismiss = onDismissError,
                    enabled = state.isFormEnabled,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Form İçeriği
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Üst İllüstrasyon ve Karşılama
                item(key = "create_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = FeniqoSageGreenContainer.copy(alpha = 0.7f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = FeniqoSageGreen,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Yeni ortak alan",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Birlikte daha düzenli, daha kolay.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 1. Alan Adı
                item(key = "workspace_name_field") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Alan adı",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChanged,
                            enabled = state.isFormEnabled,
                            singleLine = true,
                            placeholder = { Text("Evimiz") },
                            isError = state.nameError != null,
                            supportingText = state.nameError?.let { errorText ->
                                { Text(text = errorText, color = MaterialTheme.colorScheme.error) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Çalışma alanı adı alanı" },
                        )
                    }
                }

                // 2. Açıklama (İsteğe bağlı)
                item(key = "workspace_description_field") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Açıklama ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "(isteğe bağlı)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = onDescriptionChanged,
                            enabled = state.isFormEnabled,
                            minLines = 2,
                            maxLines = 3,
                            placeholder = { Text("Ev giderlerimizi birlikte takip ediyoruz.") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Çalışma alanı açıklaması alanı" },
                        )
                    }
                }

                // 3. Alan Türü Seçici Satırı
                item(key = "workspace_type_selector") {
                    SelectorRowCard(
                        icon = Icons.Outlined.Group,
                        title = "Alan türü",
                        selectedValue = state.type.toTurkishDisplayName(),
                        onClick = { if (state.isFormEnabled) showTypePicker = true },
                        enabled = state.isFormEnabled,
                        contentDescription = "Alan türü seç, şu an: ${state.type.toTurkishDisplayName()}",
                    )
                }

                // 4. Para Birimi Seçici Satırı
                item(key = "workspace_currency_selector") {
                    SelectorRowCard(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "Para birimi",
                        selectedValue = "${state.currency.code} · ${state.currency.toTurkishDisplayName()}",
                        onClick = { if (state.isFormEnabled) showCurrencyPicker = true },
                        enabled = state.isFormEnabled,
                        contentDescription = "Para birimi seç, şu an: ${state.currency.code}",
                    )
                }
            }

            // 5. Oluştur Butonu
            Button(
                onClick = onSubmit,
                enabled = state.isFormEnabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Alanı oluştur" },
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = "Alanı oluştur",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // Alan Türü Seçim Diyaloğu (Görsel 12 Mantığı)
    if (showTypePicker) {
        WorkspaceTypePickerDialog(
            currentType = state.type,
            onSelect = {
                onTypeChanged(it)
                showTypePicker = false
            },
            onDismiss = { showTypePicker = false },
        )
    }

    // Para Birimi Seçim Diyaloğu (Görsel 12)
    if (showCurrencyPicker) {
        WorkspaceCurrencyPickerDialog(
            currentCurrency = state.currency,
            onSelect = {
                onCurrencyChanged(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }
}

/**
 * Seçim Satırı Kartı (Alan türü ve Para birimi için).
 */
@Composable
private fun SelectorRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedValue: String,
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = FeniqoSageGreenContainer.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 12. Para Birimi Seçim Diyaloğu (Görsel 12).
 */
@Composable
private fun WorkspaceCurrencyPickerDialog(
    currentCurrency: Currency,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Para birimini seç",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Currency.entries.forEach { currency ->
                    val isChecked = currency == selected
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selected = currency }
                            .semantics {
                                this.selected = isChecked
                                contentDescription = "${currency.code}, ${currency.toTurkishDisplayName()}"
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) FeniqoSageGreenContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(
                            if (isChecked) 1.5.dp else 1.dp,
                            if (isChecked) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Radio butonu
                            DialogRadioCircle(isSelected = isChecked)

                            Spacer(modifier = Modifier.width(12.dp))

                            // Sembol kutusu
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = currency.symbol(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = currency.code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = currency.toTurkishDisplayName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selected) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Seçimi uygula",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = null,
    )
}

/**
 * Alan Türü Seçim Diyaloğu (Görsel 12 Mantığı).
 */
@Composable
private fun WorkspaceTypePickerDialog(
    currentType: WorkspaceType,
    onSelect: (WorkspaceType) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Alan türünü seç",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkspaceType.entries.forEach { type ->
                    val isChecked = type == selected
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selected = type }
                            .semantics {
                                this.selected = isChecked
                                contentDescription = "Alan türü ${type.toTurkishDisplayName()}"
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) FeniqoSageGreenContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(
                            if (isChecked) 1.5.dp else 1.dp,
                            if (isChecked) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogRadioCircle(isSelected = isChecked)

                            Spacer(modifier = Modifier.width(12.dp))

                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (type == WorkspaceType.PERSONAL) Icons.Outlined.Person else Icons.Outlined.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = type.toTurkishDisplayName(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (type == WorkspaceType.SHARED) "Ortak harcama ve bütçe takibi" else "Yalnızca bireysel kayıtlar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selected) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Seçimi uygula",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = null,
    )
}

@Composable
private fun DialogRadioCircle(isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(20.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.5.dp,
            if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = FeniqoSageGreen,
                ) {}
            }
        }
    }
}

/**
 * Hata Bannerı.
 */
@Composable
private fun CreateErrorBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEE2E2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message.toDisplayText(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF991B1B),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Hata mesajını kapat",
                    tint = Color(0xFF991B1B),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun WorkspaceType.toTurkishDisplayName(): String = when (this) {
    WorkspaceType.SHARED -> "Ortak"
    WorkspaceType.PERSONAL -> "Kişisel"
}

private fun Currency.toTurkishDisplayName(): String = when (this) {
    Currency.TRY -> "Türk lirası"
    Currency.USD -> "ABD doları"
    Currency.EUR -> "Euro"
}

private fun Currency.symbol(): String = when (this) {
    Currency.TRY -> "₺"
    Currency.USD -> "$"
    Currency.EUR -> "€"
}
