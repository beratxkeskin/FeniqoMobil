package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.subscription.SubscriptionFormFieldError
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInput
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInputErrors
import com.feniqo.mobile.presentation.subscription.SubscriptionMutationState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Abonelik oluşturma ve düzenleme için durumsuz (stateless) form ekranıdır.
 * ViewModel, Hilt veya navigasyon bağımlılığı taşımaz.
 */
@Composable
fun SubscriptionFormScreen(
    input: SubscriptionFormInput,
    categories: List<Category>,
    errors: SubscriptionFormInputErrors,
    mutationState: SubscriptionMutationState,
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onRequestAdvanceRenewal: () -> Unit,
    onRequestDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onCategoryChange: (EntityId?) -> Unit,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearEndDate: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !mutationState.isSubmitting

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

            // 1. Üst Başlık ve Geri Dön Butonu
            SubscriptionFormHeader(
                isEditMode = isEditMode,
                isActive = isActive,
                onBack = onBack,
                onRequestDelete = onRequestDelete,
                isEnabled = isEnabled,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Kaydırılabilir Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                // Abonelik Adı Alanı
                SubscriptionNameField(
                    nameInput = input.nameInput,
                    nameError = errors.nameError,
                    onNameChange = onNameChange,
                    isEnabled = isEnabled,
                )

                // Tutar ve Para Birimi Alanı
                SubscriptionAmountField(
                    amountInput = input.amountInput,
                    currency = input.currency,
                    amountError = errors.amountError,
                    onAmountChange = onAmountChange,
                    onCurrencyChange = onCurrencyChange,
                    isEnabled = isEnabled,
                )


                // Kategori Seçim Alanı (Opsiyonel)
                SubscriptionCategorySection(
                    categories = categories,
                    selectedCategoryId = input.categoryId,
                    categoryError = errors.categoryError,
                    onCategoryChange = onCategoryChange,
                    isEnabled = isEnabled,
                )

                // Tekrar Sıklığı (Frekans) ve Aralık Alanı
                SubscriptionRecurrenceSection(
                    frequency = input.frequency,
                    intervalInput = input.intervalInput,
                    intervalError = errors.intervalError,
                    onFrequencyChange = onFrequencyChange,
                    onIntervalChange = onIntervalChange,
                    isEnabled = isEnabled,
                )

                // Tarihler (Başlangıç ve Bitiş)
                SubscriptionDatesSection(
                    startDate = input.startDate,
                    endDate = input.endDate,
                    nextRenewalDate = input.nextRenewalDate,
                    isEditMode = isEditMode,
                    startDateError = errors.startDateError,
                    endDateError = errors.endDateError,
                    onStartDateClick = onStartDateClick,
                    onEndDateClick = onEndDateClick,
                    onClearEndDate = onClearEndDate,
                    isEnabled = isEnabled,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }

            // 3. Kaydet / Güncelle Butonu
            SubscriptionSubmitButton(
                isEditMode = isEditMode,
                isSubmitting = mutationState.isSubmitting,
                onSubmit = onSubmit,
            )

            // 4. Düzenleme Modunda ve Aktif Abonelikte "Bu yenilemeyi ödedim" Aksiyonu
            if (isEditMode && isActive) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                OutlinedButton(
                    onClick = onRequestAdvanceRenewal,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics {
                            contentDescription = "Bu yenilemeyi ödedim"
                        },
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FeniqoStatusColor.Success,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isEnabled) FeniqoStatusColor.Success.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ),
                ) {
                    Text(
                        text = "Bu Yenilemeyi Ödedim",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) FeniqoStatusColor.Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }

            // 5. Düzenleme Modunda Duraklat / Devam Ettir Aksiyonu
            if (isEditMode) {

                val targetActive = !isActive
                val activeActionText = if (isActive) "Aboneliği Duraklat" else "Aboneliği Devam Ettir"
                val activeActionDescription = if (isActive) "Aboneliği duraklat" else "Aboneliği devam ettir"

                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                OutlinedButton(
                    onClick = { onSetActive(targetActive) },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics {
                            contentDescription = activeActionDescription
                        },
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isActive) MaterialTheme.colorScheme.primary else FeniqoStatusColor.Success,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isEnabled) {
                            (if (isActive) MaterialTheme.colorScheme.primary else FeniqoStatusColor.Success).copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        },
                    ),
                ) {
                    Text(
                        text = activeActionText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) {
                            if (isActive) MaterialTheme.colorScheme.primary else FeniqoStatusColor.Success
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }

            // 5. Düzenleme Modunda Sil Butonu
            if (isEditMode) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                OutlinedButton(
                    onClick = onRequestDelete,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics {
                            contentDescription = "Aboneliği sil"
                        },
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ),
                ) {
                    Text(
                        text = "Aboneliği Sil",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
        }
    }
}

@Composable
private fun SubscriptionFormHeader(
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onRequestDelete: () -> Unit,
    isEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                enabled = isEnabled,
            ) {
                Text("← Geri")
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

            Text(
                text = if (isEditMode) "Aboneliği Düzenle" else "Yeni Abonelik",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (isEditMode) {
            val badgeContainerColor = if (isActive) FeniqoStatusColor.Success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
            val badgeContentColor = if (isActive) FeniqoStatusColor.Success else MaterialTheme.colorScheme.onSurfaceVariant
            val badgeText = if (isActive) "Aktif" else "Duraklatıldı"

            Surface(
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = badgeContainerColor,
                modifier = Modifier.padding(end = FeniqoSpacing.Small),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = badgeContentColor,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SubscriptionNameField(
    nameInput: String,
    nameError: SubscriptionFormFieldError?,
    onNameChange: (String) -> Unit,
    isEnabled: Boolean,
) {
    Column {
        Text(
            text = "Abonelik Adı",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            placeholder = { Text("Örn. Spotify, Netflix, Kira...") },
            isError = nameError != null,
            singleLine = true,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            colors = OutlinedTextFieldDefaults.colors(),
        )

        if (nameError != null) {
            val errorMessage = when (nameError) {
                SubscriptionFormFieldError.NAME_REQUIRED -> "Lütfen abonelik adını girin."
                SubscriptionFormFieldError.NAME_TOO_LONG -> "Abonelik adı en fazla 500 karakter olabilir."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionAmountField(
    amountInput: String,
    currency: Currency,
    amountError: SubscriptionFormFieldError?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    isEnabled: Boolean,
) {
    Column {
        Text(
            text = "Tutar ve Para Birimi",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        // Para Birimi Seçicisi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Currency.entries.forEach { curr ->
                val isSelected = curr == currency
                val label = "${curr.name} (${when (curr) {
                    Currency.TRY -> "₺"
                    Currency.USD -> "$"
                    Currency.EUR -> "€"
                }})"

                FilterChip(
                    selected = isSelected,
                    onClick = { onCurrencyChange(curr) },
                    label = { Text(label) },
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                )
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        val currencySymbol = when (currency) {
            Currency.TRY -> "₺"
            Currency.USD -> "$"
            Currency.EUR -> "€"
        }

        OutlinedTextField(
            value = amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            placeholder = { Text("0,00") },
            trailingIcon = {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = FeniqoSpacing.Medium),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            isError = amountError != null,
            singleLine = true,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            colors = OutlinedTextFieldDefaults.colors(),
        )

        if (amountError != null) {
            val errorMessage = when (amountError) {
                SubscriptionFormFieldError.AMOUNT_REQUIRED -> "Lütfen bir tutar girin."
                SubscriptionFormFieldError.AMOUNT_INVALID -> "Geçerli bir tutar formatı girin (örn. 59,99)."
                SubscriptionFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar 0'dan büyük olmalıdır."
                SubscriptionFormFieldError.AMOUNT_TOO_LARGE -> "Tutar izin verilen sınırı aşıyor."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionCategorySection(
    categories: List<Category>,
    selectedCategoryId: EntityId?,
    categoryError: SubscriptionFormFieldError?,
    onCategoryChange: (EntityId?) -> Unit,
    isEnabled: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Kategori (Opsiyonel)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (selectedCategoryId != null) {
                TextButton(
                    onClick = { onCategoryChange(null) },
                    enabled = isEnabled,
                ) {
                    Text("Seçimi Kaldır")
                }
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        if (categories.isEmpty()) {
            Text(
                text = "Tanımlı harcama kategorisi bulunamadı.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                categories.forEach { category ->
                    val isSelected = category.id == selectedCategoryId
                    val categoryColor = ColorParser.parseHexColorOrNull(category.color.hex)
                        ?: MaterialTheme.colorScheme.primary
                    val emoji = CategoryIconResolver.resolveIconEmojiOrNull(category.icon?.key) ?: "💳"

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onCategoryChange(null)
                            } else {
                                onCategoryChange(category.id)
                            }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                            ) {
                                Text(emoji)
                                Text(category.name)
                            }
                        },
                        enabled = isEnabled,
                        shape = RoundedCornerShape(FeniqoRadius.Small),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = isEnabled,
                            selected = isSelected,
                            borderColor = if (isSelected) categoryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            selectedBorderColor = categoryColor,
                        ),
                    )
                }
            }
        }

        if (categoryError != null) {
            val errorMessage = when (categoryError) {
                SubscriptionFormFieldError.CATEGORY_NOT_FOUND -> "Seçilen kategori bulunamadı."
                SubscriptionFormFieldError.CATEGORY_TYPE_MISMATCH -> "Yalnızca gider kategorileri seçilebilir."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionRecurrenceSection(
    frequency: RecurrenceFrequency,
    intervalInput: String,
    intervalError: SubscriptionFormFieldError?,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    isEnabled: Boolean,
) {
    Column {
        Text(
            text = "Yenileme Sıklığı",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            RecurrenceFrequency.entries.forEach { freq ->
                val isSelected = freq == frequency
                val label = when (freq) {
                    RecurrenceFrequency.DAILY -> "Günlük"
                    RecurrenceFrequency.WEEKLY -> "Haftalık"
                    RecurrenceFrequency.MONTHLY -> "Aylık"
                    RecurrenceFrequency.YEARLY -> "Yıllık"
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onFrequencyChange(freq) },
                    label = { Text(label) },
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                )
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aralık (Her X birimde bir)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = onIntervalChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEnabled,
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    isError = intervalError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                )
            }
        }

        if (intervalError != null) {
            val errorMessage = when (intervalError) {
                SubscriptionFormFieldError.INTERVAL_INVALID -> "Geçerli bir tam sayı girin."
                SubscriptionFormFieldError.INTERVAL_NON_POSITIVE -> "Aralık 0'dan büyük olmalıdır."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionDatesSection(
    startDate: LocalDate?,
    endDate: LocalDate?,
    nextRenewalDate: LocalDate?,
    isEditMode: Boolean,
    startDateError: SubscriptionFormFieldError?,
    endDateError: SubscriptionFormFieldError?,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearEndDate: () -> Unit,
    isEnabled: Boolean,
) {
    Column {
        Text(
            text = "Tarihler",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // Başlangıç Tarihi
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEnabled, onClick = onStartDateClick),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(FeniqoSpacing.Medium),
                ) {
                    Text(
                        text = "Başlangıç",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = startDate?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih Seç",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (startDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bitiş Tarihi (Opsiyonel)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isEnabled, onClick = onEndDateClick),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FeniqoSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bitiş (Opsiyonel)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                        Text(
                            text = endDate?.let { DateFormatter.formatReadableDate(it) } ?: "Süresiz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (endDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (endDate != null) {
                        TextButton(
                            onClick = onClearEndDate,
                            enabled = isEnabled,
                        ) {
                            Text("✕", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        if (startDateError != null) {
            val errorMessage = when (startDateError) {
                SubscriptionFormFieldError.START_DATE_REQUIRED -> "Lütfen bir başlangıç tarihi seçin."
                SubscriptionFormFieldError.START_DATE_AFTER_NEXT_RENEWAL -> "Başlangıç tarihi sonraki yenileme tarihinden sonra olamaz."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        if (endDateError != null) {
            val errorMessage = when (endDateError) {
                SubscriptionFormFieldError.END_DATE_BEFORE_START_DATE -> "Bitiş tarihi başlangıç tarihinden önce olamaz."
                SubscriptionFormFieldError.END_DATE_BEFORE_NEXT_RENEWAL -> "Bitiş tarihi sonraki yenileme tarihinden önce olamaz."
                else -> ""
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        // Düzenleme modunda Sonraki Yenileme Tarihi bilgilendirme alanı
        if (isEditMode && nextRenewalDate != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
            Text(
                text = "Sonraki Yenileme: ${DateFormatter.formatReadableDate(nextRenewalDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}


@Composable
private fun SubscriptionSubmitButton(
    isEditMode: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
) {
    Button(
        onClick = onSubmit,
        enabled = !isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                contentDescription = if (isEditMode) "Aboneliği güncelle" else "Aboneliği kaydet"
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
        }

        Text(
            text = if (isEditMode) "Aboneliği Güncelle" else "Aboneliği Kaydet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
