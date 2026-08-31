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
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormFieldError
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInput
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInputErrors
import com.feniqo.mobile.presentation.recurring.RecurringTransactionMutationState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * Tekrarlayan işlem (abonelik veya düzenli gelir) oluşturma ve düzenleme için durumsuz (stateless) form ekranıdır.
 * ViewModel, Hilt veya navigasyon bağımlılığı taşımaz.
 */
@Composable
fun RecurringTransactionFormScreen(
    input: RecurringTransactionFormInput,
    categories: List<Category>,
    errors: RecurringTransactionFormInputErrors,
    mutationState: RecurringTransactionMutationState,
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryChange: (EntityId) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
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
            RecurringFormHeader(
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
                // İşlem Türü Seçimi (Gider / Gelir)
                RecurringTypeSelector(
                    selectedType = input.type,
                    onTypeChange = onTypeChange,
                    isEnabled = isEnabled,
                )

                // Tutar Alanı
                RecurringAmountField(
                    amountInput = input.amountInput,
                    currency = input.currency,
                    amountError = errors.amountError,
                    onAmountChange = onAmountChange,
                    isEnabled = isEnabled,
                )

                // Kategori Seçim Alanı
                RecurringCategorySection(
                    categories = categories,
                    selectedCategoryId = input.categoryId,
                    categoryError = errors.categoryError,
                    onCategoryChange = onCategoryChange,
                    isEnabled = isEnabled,
                )

                // Tekrar Sıklığı (Frekans) ve Aralık Alanı
                RecurringRecurrenceSection(
                    frequency = input.frequency,
                    intervalInput = input.intervalInput,
                    intervalError = errors.intervalError,
                    onFrequencyChange = onFrequencyChange,
                    onIntervalChange = onIntervalChange,
                    isEnabled = isEnabled,
                )

                // Tarihler (Başlangıç ve Bitiş)
                RecurringDatesSection(
                    startDate = input.startDate,
                    endDate = input.endDate,
                    startDateError = errors.startDateError,
                    endDateError = errors.endDateError,
                    onStartDateClick = onStartDateClick,
                    onEndDateClick = onEndDateClick,
                    onClearEndDate = onClearEndDate,
                    isEnabled = isEnabled,
                )

                // Ödeme Yöntemi
                RecurringPaymentMethodSection(
                    selectedPaymentMethod = input.paymentMethod,
                    onPaymentMethodChange = onPaymentMethodChange,
                    isEnabled = isEnabled,
                )

                // Açıklama Alanı
                RecurringDescriptionField(
                    description = input.description,
                    descriptionError = errors.descriptionError,
                    onDescriptionChange = onDescriptionChange,
                    isEnabled = isEnabled,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }

            // 3. Kaydet / Güncelle Butonu
            RecurringSubmitButton(
                isEditMode = isEditMode,
                isSubmitting = mutationState.isSubmitting,
                onSubmit = onSubmit,
            )

            // 4. Düzenleme Modunda Duraklat / Devam Ettir Aksiyonu
            if (isEditMode) {
                val targetActive = !isActive
                val activeActionText = if (isActive) "Kuralı Duraklat" else "Kuralı Devam Ettir"
                val activeActionDescription = if (isActive) "Tekrarlayan işlemi duraklat" else "Tekrarlayan işlemi devam ettir"

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
                            contentDescription = "Tekrarlayan İşlemi Sil"
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
                        text = "Tekrarlayan İşlemi Sil",
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
private fun RecurringFormHeader(
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onRequestDelete: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = if (isEditMode) "Tekrarlayan İşlemi Düzenle" else "Tekrarlayan İşlem Ekle"
    val subtitle = if (isEditMode) "Kural ayrıntılarını güncelleyin." else "Abonelik veya düzenli gelirinizi planlayın."

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = isEnabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            modifier = Modifier.semantics {
                contentDescription = "Geri Dön"
            },
        ) {
            Text(
                text = "← Geri",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecurringTypeSelector(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(FeniqoSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        val isExpense = selectedType == TransactionType.EXPENSE
        val isIncome = selectedType == TransactionType.INCOME

        Surface(
            selected = isExpense,
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            enabled = isEnabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isExpense) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Gider türü seçeneği"
                },
        ) {
            Box(
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Normal,
                    color = if (isExpense) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            selected = isIncome,
            onClick = { onTypeChange(TransactionType.INCOME) },
            enabled = isEnabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isIncome) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Gelir türü seçeneği"
                },
        ) {
            Box(
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Gelir",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Normal,
                    color = if (isIncome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecurringAmountField(
    amountInput: String,
    currency: Currency,
    amountError: RecurringTransactionFormFieldError?,
    onAmountChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val errorMessage = when (amountError) {
        RecurringTransactionFormFieldError.AMOUNT_REQUIRED -> "Tutar alanı zorunludur."
        RecurringTransactionFormFieldError.AMOUNT_INVALID -> "Geçersiz tutar formatı."
        RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
        RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE -> "Tutar izin verilen limiti aşıyor."
        else -> null
    }

    val currencySymbol = when (currency) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tutar",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        OutlinedTextField(
            value = amountInput,
            onValueChange = onAmountChange,
            enabled = isEnabled,
            isError = errorMessage != null,
            placeholder = { Text("0,00") },
            trailingIcon = {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = FeniqoSpacing.Medium),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Tutar giriş alanı"
                },
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringCategorySection(
    categories: List<Category>,
    selectedCategoryId: EntityId?,
    categoryError: RecurringTransactionFormFieldError?,
    onCategoryChange: (EntityId) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val errorMessage = when (categoryError) {
        RecurringTransactionFormFieldError.CATEGORY_REQUIRED -> "Kategori seçimi zorunludur."
        RecurringTransactionFormFieldError.CATEGORY_TYPE_MISMATCH -> "Seçilen kategori işlem türüyle uyuşmuyor; lütfen yeniden seçin."
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Kategori",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        if (categories.isEmpty()) {
            Text(
                text = "Bu tür için tanımlı kategori bulunamadı.",
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
                    val parsedColor = ColorParser.parseHexColorOrNull(category.color.hex) ?: MaterialTheme.colorScheme.primary
                    val iconKey = category.icon?.key

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryChange(category.id) },
                        enabled = isEnabled,
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = CategoryIconResolver.resolveIconEmojiOrNull(iconKey) ?: "📁",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = parsedColor.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "${category.name} kategorisi, ${if (isSelected) "seçili" else "seçili değil"}"
                        },
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecurringRecurrenceSection(
    frequency: RecurrenceFrequency,
    intervalInput: String,
    intervalError: RecurringTransactionFormFieldError?,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val intervalErrorMessage = when (intervalError) {
        RecurringTransactionFormFieldError.INTERVAL_INVALID -> "Geçersiz aralık değeri."
        RecurringTransactionFormFieldError.INTERVAL_NON_POSITIVE -> "Tekrar aralığı sıfırdan büyük pozitif bir tam sayı olmalıdır."
        else -> null
    }

    val frequencyNameMap = mapOf(
        RecurrenceFrequency.DAILY to "Günlük",
        RecurrenceFrequency.WEEKLY to "Haftalık",
        RecurrenceFrequency.MONTHLY to "Aylık",
        RecurrenceFrequency.YEARLY to "Yıllık",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tekrar Sıklığı",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
        ) {
            RecurrenceFrequency.entries.forEach { freq ->
                val isSelected = freq == frequency
                FilterChip(
                    selected = isSelected,
                    onClick = { onFrequencyChange(freq) },
                    enabled = isEnabled,
                    label = {
                        Text(
                            text = frequencyNameMap[freq] ?: freq.name,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

        Text(
            text = "Tekrar Aralığı",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = intervalInput,
                onValueChange = onIntervalChange,
                enabled = isEnabled,
                isError = intervalErrorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .width(96.dp)
                    .semantics {
                        contentDescription = "Tekrar aralığı giriş alanı"
                    },
            )

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            val freqUnit = when (frequency) {
                RecurrenceFrequency.DAILY -> "günde"
                RecurrenceFrequency.WEEKLY -> "haftada"
                RecurrenceFrequency.MONTHLY -> "ayda"
                RecurrenceFrequency.YEARLY -> "yılda"
            }
            Text(
                text = "Her $intervalInput $freqUnit bir tekrarlanır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (intervalErrorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = intervalErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecurringDatesSection(
    startDate: LocalDate?,
    endDate: LocalDate?,
    startDateError: RecurringTransactionFormFieldError?,
    endDateError: RecurringTransactionFormFieldError?,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearEndDate: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val startDateErrorMessage = when (startDateError) {
        RecurringTransactionFormFieldError.START_DATE_REQUIRED -> "Başlangıç tarihi zorunludur."
        else -> null
    }

    val endDateErrorMessage = when (endDateError) {
        RecurringTransactionFormFieldError.END_DATE_BEFORE_START_DATE -> "Bitiş tarihi başlangıç tarihinden önce olamaz."
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tarihler",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            // Başlangıç Tarihi Kartı
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = isEnabled,
                        role = Role.Button,
                        onClick = onStartDateClick,
                    )
                    .semantics {
                        contentDescription = "Başlangıç tarihi seçimi: ${startDate?.toString() ?: "Seçilmedi"}"
                    },
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(modifier = Modifier.padding(FeniqoSpacing.Medium)) {
                    Text(
                        text = "Başlangıç Tarihi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = startDate?.toString() ?: "Tarih Seç",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (startDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Bitiş Tarihi Kartı (Opsiyonel)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = isEnabled,
                        role = Role.Button,
                        onClick = onEndDateClick,
                    )
                    .semantics {
                        contentDescription = "Bitiş tarihi seçimi: ${endDate?.toString() ?: "Süresiz"}"
                    },
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(modifier = Modifier.padding(FeniqoSpacing.Medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Bitiş Tarihi (Opsiyonel)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = endDate?.toString() ?: "Süresiz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (endDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (endDate != null && isEnabled) {
                            Text(
                                text = "Temizle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable(role = Role.Button, onClick = onClearEndDate)
                                    .semantics {
                                        contentDescription = "Bitiş tarihini temizle"
                                    },
                            )
                        }
                    }
                }
            }
        }

        if (startDateErrorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = startDateErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (endDateErrorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = endDateErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecurringPaymentMethodSection(
    selectedPaymentMethod: PaymentMethod,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val methodNames = mapOf(
        PaymentMethod.CASH to "Nakit",
        PaymentMethod.CREDIT_CARD to "Kredi Kartı",
        PaymentMethod.BANK_TRANSFER to "Banka Transferi",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Ödeme Yöntemi",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            PaymentMethod.entries.forEach { method ->
                val isSelected = method == selectedPaymentMethod
                FilterChip(
                    selected = isSelected,
                    onClick = { onPaymentMethodChange(method) },
                    enabled = isEnabled,
                    label = {
                        Text(
                            text = methodNames[method] ?: method.name,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecurringDescriptionField(
    description: String,
    descriptionError: RecurringTransactionFormFieldError?,
    onDescriptionChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val errorMessage = when (descriptionError) {
        RecurringTransactionFormFieldError.DESCRIPTION_TOO_LONG -> "Açıklama en fazla 500 karakter olabilir."
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Açıklama (Opsiyonel)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            enabled = isEnabled,
            isError = errorMessage != null,
            placeholder = { Text("İşlem hakkında not ekleyin...") },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Açıklama giriş alanı"
                },
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecurringSubmitButton(
    isEditMode: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = if (isEditMode) "Değişiklikleri Kaydet" else "Tekrarlayan İşlemi Kaydet"

    Button(
        onClick = onSubmit,
        enabled = !isSubmitting,
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .semantics {
                contentDescription = buttonText
            },
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
