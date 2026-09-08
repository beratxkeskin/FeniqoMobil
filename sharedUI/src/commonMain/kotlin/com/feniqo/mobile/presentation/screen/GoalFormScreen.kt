package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.goal.GOAL_PRESET_COLORS
import com.feniqo.mobile.presentation.goal.GoalFormFieldError
import com.feniqo.mobile.presentation.goal.GoalFormInput
import com.feniqo.mobile.presentation.goal.GoalFormInputErrors
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Birikim hedefi oluşturma ve düzenleme için durumsuz (stateless) form ekranıdır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalFormScreen(
    input: GoalFormInput,
    errors: GoalFormInputErrors,
    isSubmitting: Boolean,
    isEditMode: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onTargetAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onInitialAmountChange: (String) -> Unit,
    onTargetDateClick: () -> Unit,
    onColorChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onSubmit: () -> Unit,
    onAddContribution: (() -> Unit)? = null,
    contributionsHistory: List<com.feniqo.mobile.presentation.goal.GoalContributionHistoryItemUiModel> = emptyList(),
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting


    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık ve Geri Dön / Sil Butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    TextButton(
                        onClick = onBack,
                        enabled = isEnabled,
                    ) {
                        Text("← Geri")
                    }
                    Text(
                        text = if (isEditMode) "Hedefi Düzenle" else "Yeni Hedef",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    ActiveWorkspaceIndicator(
                        workspaceName = activeWorkspaceName,
                        isCompact = true,
                    )

                    if (isEditMode) {
                        OutlinedButton(
                            onClick = onRequestDelete,
                            enabled = isEnabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        ) {
                            Text("Sil")
                        }
                    }
                }
            }

            if (isEditMode && onAddContribution != null) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                OutlinedButton(
                    onClick = onAddContribution,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                ) {
                    Text("± Para Ekle / Çıkar")
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {

                // Hedef Adı
                OutlinedTextField(
                    value = input.nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Hedef Adı *") },
                    placeholder = { Text("Örn: Tatil Fonu, Ev Peşinatı") },
                    isError = errors.nameError != null,
                    supportingText = {
                        errors.nameError?.let {
                            Text(
                                text = when (it) {
                                    GoalFormFieldError.NAME_REQUIRED -> "Hedef adı zorunludur."
                                    GoalFormFieldError.NAME_TOO_LONG -> "Hedef adı en fazla 500 karakter olabilir."
                                    else -> "Geçersiz hedef adı."
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Hedef Tutar
                OutlinedTextField(
                    value = input.targetAmountInput,
                    onValueChange = onTargetAmountChange,
                    label = { Text("Hedef Tutar *") },
                    placeholder = { Text("0,00") },
                    isError = errors.targetAmountError != null,
                    supportingText = {
                        errors.targetAmountError?.let {
                            Text(
                                text = when (it) {
                                    GoalFormFieldError.TARGET_AMOUNT_REQUIRED -> "Hedef tutar zorunludur."
                                    GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE -> "Hedef tutar sıfırdan büyük olmalıdır."
                                    GoalFormFieldError.TARGET_AMOUNT_INVALID -> "Geçerli bir tutar girin."
                                    else -> "Geçersiz tutar."
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Para Birimi Seçimi (Editte kilitli)
                Column {
                    Text(
                        text = if (isEditMode) "Para Birimi (Düzenlemede Değiştirilemez)" else "Para Birimi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Currency.entries.forEach { currencyOption ->
                            FilterChip(
                                selected = input.currency == currencyOption,
                                onClick = {
                                    if (!isEditMode) {
                                        onCurrencyChange(currencyOption)
                                    }
                                },
                                enabled = isEnabled && !isEditMode,
                                label = { Text(currencyOption.code) },
                            )
                        }
                    }
                }

                // Başlangıç Tutarı (Yalnızca oluşturma modunda)
                if (!isEditMode) {
                    OutlinedTextField(
                        value = input.initialAmountInput,
                        onValueChange = onInitialAmountChange,
                        label = { Text("Başlangıç Birikim Tutarı (Opsiyonel)") },
                        placeholder = { Text("0,00") },
                        isError = errors.initialAmountError != null,
                        supportingText = {
                            if (errors.initialAmountError != null) {
                                Text(
                                    text = when (errors.initialAmountError) {
                                        GoalFormFieldError.INITIAL_AMOUNT_NEGATIVE -> "Başlangıç tutarı negatif olamaz."
                                        GoalFormFieldError.INITIAL_AMOUNT_INVALID -> "Geçerli bir tutar girin."
                                        else -> "Geçersiz başlangıç tutarı."
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text("Şu ana kadar birikmiş olan tutar varsa girebilirsiniz.")
                            }
                        },
                        singleLine = true,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Hedef Tarihi Seçici
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FeniqoRadius.Medium))
                        .clickable(
                            role = Role.Button,
                            enabled = isEnabled,
                            onClick = onTargetDateClick,
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Hedef Tarihi *",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (input.targetDate != null) {
                                    DateFormatter.formatReadableDate(input.targetDate)
                                } else {
                                    "Tarih Seçin"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (input.targetDate != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        Text(text = "📅", fontSize = 20.sp)
                    }
                }
                if (errors.targetDateError != null) {
                    Text(
                        text = "Hedef tarihi seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = FeniqoSpacing.Small),
                    )
                }

                // Renk Seçimi
                Column {
                    Text(
                        text = "Hedef Rengi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        GOAL_PRESET_COLORS.forEach { colorHex ->
                            val color = ColorParser.parseHexColorOrNull(colorHex) ?: Color.Gray
                            val isSelected = input.colorHex.equals(colorHex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        role = Role.Button,
                                        enabled = isEnabled,
                                        onClick = { onColorChange(colorHex) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (isEditMode) {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    Text(
                        text = "Hareket Geçmişi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    if (contributionsHistory.isEmpty()) {
                        Text(
                            text = "Henüz bir hareket kaydı bulunmuyor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            contributionsHistory.forEach { item ->
                                val isAdd = item.direction == com.feniqo.mobile.domain.model.GoalContributionDirection.ADD
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = if (isAdd) "+ Para Ekleme" else "- Para Çıkarma",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            )
                                            Text(
                                                text = item.formattedAmount,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = item.formattedDate,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (!item.note.isNullOrBlank()) {
                                                Text(
                                                    text = item.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }


            // Kaydet Butonu
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FeniqoSpacing.Medium),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (isEditMode) "Değişiklikleri Kaydet" else "Hedefi Kaydet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
