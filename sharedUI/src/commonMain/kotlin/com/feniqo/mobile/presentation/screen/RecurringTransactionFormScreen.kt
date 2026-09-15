package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CategorySemanticIconResolver
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import com.feniqo.mobile.presentation.component.RecurrencePatternSheet
import com.feniqo.mobile.presentation.component.RecurringCategoryPickerSheet
import com.feniqo.mobile.presentation.component.RecurringDatePickerSheet
import com.feniqo.mobile.presentation.component.RecurringPaymentMethodPickerSheet
import com.feniqo.mobile.presentation.component.RecurringStatusBadge
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModelMapper
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormFieldError
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInput
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInputErrors
import com.feniqo.mobile.presentation.recurring.RecurringTransactionMutationState
import com.feniqo.mobile.presentation.theme.FeniqoExpense
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Tekrarlayan işlem oluşturma ve düzenleme için onaylı tasarım dilindeki (Görsel 02, 03, 08, 11) form ekranıdır.
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
    onStartDateClick: () -> Unit = {},
    onEndDateClick: () -> Unit = {},
    onClearEndDate: () -> Unit = {},
    onSubmit: () -> Unit,
    activeWorkspaceName: String? = null,
    onStartDateChange: (LocalDate) -> Unit = {},
    onEndDateChange: (LocalDate?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEnabled = !mutationState.isSubmitting

    // Modal Bottom Sheet durumları
    var showCategorySheet by remember { mutableStateOf(false) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }
    var showPaymentMethodSheet by remember { mutableStateOf(false) }
    var showStartDateSheet by remember { mutableStateOf(false) }
    var showEndDateSheet by remember { mutableStateOf(false) }

    val selectedCategory = categories.firstOrNull { it.id == input.categoryId }
    val currentInterval = input.intervalInput.toIntOrNull() ?: 1

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoWarmStoneBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 1. Üst Gezinme Barı: "< Yeni kural" / "< Kuralı düzenle", "feniqo" Logosu, Düzenlemede Durum Rozeti
            RecurringFormTopBar(
                isEditMode = isEditMode,
                isActive = isActive,
                onBack = onBack,
                isEnabled = isEnabled,
            )

            // 2. Kaydırılabilir Form İçeriği
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Çalışma Alanı Rozeti
                ActiveWorkspaceIndicator(
                    workspaceName = activeWorkspaceName,
                    isCompact = true,
                )

                // Gider / Gelir Tür Seçimi
                RecurringTypeToggle(
                    selectedType = input.type,
                    onTypeChange = onTypeChange,
                    isEnabled = isEnabled,
                )

                // Tutar Giriş Kartı
                RecurringAmountCard(
                    amountInput = input.amountInput,
                    currency = input.currency,
                    transactionType = input.type,
                    amountError = errors.amountError,
                    onAmountChange = onAmountChange,
                    isEnabled = isEnabled,
                )

                // Beyaz Gruplu Ayar Kartı (Kategori, Tekrar, Başlangıç, Bitiş)
                RecurringSettingsGroupCard(
                    selectedCategory = selectedCategory,
                    categoryError = errors.categoryError,
                    frequency = input.frequency,
                    interval = currentInterval,
                    intervalError = errors.intervalError,
                    startDate = input.startDate,
                    startDateError = errors.startDateError,
                    endDate = input.endDate,
                    endDateError = errors.endDateError,
                    onCategoryClick = {
                        if (isEnabled) showCategorySheet = true
                    },
                    onRecurrenceClick = {
                        if (isEnabled) showRecurrenceSheet = true
                    },
                    onStartDateClick = {
                        if (isEnabled) {
                            showStartDateSheet = true
                            onStartDateClick()
                        }
                    },
                    onEndDateClick = {
                        if (isEnabled) {
                            showEndDateSheet = true
                            onEndDateClick()
                        }
                    },
                    isEnabled = isEnabled,
                )

                // Ödeme Yöntemi Kartı
                RecurringPaymentMethodCard(
                    paymentMethod = input.paymentMethod,
                    onClick = {
                        if (isEnabled) showPaymentMethodSheet = true
                    },
                    isEnabled = isEnabled,
                )

                // Açıklama Alanı Kartı
                RecurringDescriptionCard(
                    description = input.description,
                    descriptionError = errors.descriptionError,
                    onDescriptionChange = onDescriptionChange,
                    isEnabled = isEnabled,
                )

                // Dinamik Bilgi Banner'ı (Görsel 02, 03 & 08)
                RecurringInfoBanner(
                    frequency = input.frequency,
                    interval = currentInterval,
                    isPaused = isEditMode && !isActive,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Alt Aksiyon Butonları
                RecurringFormActionButtons(
                    isEditMode = isEditMode,
                    isActive = isActive,
                    isSubmitting = mutationState.isSubmitting,
                    onSubmit = onSubmit,
                    onSetActive = onSetActive,
                    onRequestDelete = onRequestDelete,
                    isEnabled = isEnabled,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheets
    if (showCategorySheet) {
        RecurringCategoryPickerSheet(
            categories = categories,
            selectedCategoryId = input.categoryId,
            onCategorySelected = { catId ->
                onCategoryChange(catId)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
        )
    }

    if (showRecurrenceSheet) {
        RecurrencePatternSheet(
            initialFrequency = input.frequency,
            initialInterval = currentInterval,
            onApply = { freq, interval ->
                onFrequencyChange(freq)
                onIntervalChange(interval.toString())
                showRecurrenceSheet = false
            },
            onDismiss = { showRecurrenceSheet = false },
        )
    }

    if (showPaymentMethodSheet) {
        RecurringPaymentMethodPickerSheet(
            selectedMethod = input.paymentMethod,
            onMethodSelected = { method ->
                onPaymentMethodChange(method)
                showPaymentMethodSheet = false
            },
            onDismiss = { showPaymentMethodSheet = false },
        )
    }

    if (showStartDateSheet) {
        RecurringDatePickerSheet(
            title = "Başlangıç tarihi",
            initialDate = input.startDate,
            isEndDate = false,
            onDateSelected = { date ->
                if (date != null) {
                    onStartDateChange(date)
                }
                showStartDateSheet = false
            },
            onDismiss = { showStartDateSheet = false },
        )
    }

    if (showEndDateSheet) {
        RecurringDatePickerSheet(
            title = "Bitiş tarihi",
            initialDate = input.endDate,
            minDate = input.startDate,
            isEndDate = true,
            onDateSelected = { date ->
                if (date == null) {
                    onClearEndDate()
                }
                onEndDateChange(date)
                showEndDateSheet = false
            },
            onDismiss = { showEndDateSheet = false },
        )
    }
}

/**
 * Üst Gezinme Barı (Görsel 02 & 03).
 */
@Composable
private fun RecurringFormTopBar(
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val backTitle = if (isEditMode) "Kuralı düzenle" else "Yeni kural"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onBack,
            enabled = isEnabled,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Geri",
                    tint = FeniqoTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = backTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        }

        Text(
            text = "feniqo",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = FeniqoSageGreen,
        )

        // Düzenleme modunda sağda durum rozeti (Görsel 03 & 08)
        Box(
            modifier = Modifier.size(width = 80.dp, height = 36.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (isEditMode) {
                RecurringStatusBadge(isPaused = !isActive)
            }
        }
    }
}

/**
 * Gider / Gelir Tür Seçici Segmenti (Görsel 02).
 */
@Composable
private fun RecurringTypeToggle(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val isExpense = selectedType == TransactionType.EXPENSE
        val isIncome = selectedType == TransactionType.INCOME

        // Gider Butonu
        Surface(
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            enabled = isEnabled,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isExpense) Color(0xFFFFEBEE) else FeniqoPureWhite,
            border = BorderStroke(
                1.dp,
                if (isExpense) Color(0xFFFFCDD2) else Color(0xFFF1F5F9),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(if (isExpense) Color(0xFFDC2626) else Color(0xFF94A3B8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "−",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isExpense) Color(0xFFDC2626) else FeniqoTextSecondary,
                )
            }
        }

        // Gelir Butonu
        Surface(
            onClick = { onTypeChange(TransactionType.INCOME) },
            enabled = isEnabled,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isIncome) Color(0xFFE8F5E9) else FeniqoPureWhite,
            border = BorderStroke(
                1.dp,
                if (isIncome) Color(0xFFC8E6C9) else Color(0xFFF1F5F9),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(if (isIncome) FeniqoTrendGreen else Color(0xFF94A3B8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gelir",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isIncome) FeniqoTrendGreen else FeniqoTextSecondary,
                )
            }
        }
    }
}

/**
 * Tutar Kartı (Görsel 02 & 03, Hata: Görsel 11).
 */
@Composable
private fun RecurringAmountCard(
    amountInput: String,
    currency: Currency,
    transactionType: TransactionType,
    amountError: RecurringTransactionFormFieldError?,
    onAmountChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val currencySymbol = when (currency) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
    }

    val amountColor = when (transactionType) {
        TransactionType.EXPENSE -> FeniqoExpense
        TransactionType.INCOME -> FeniqoTrendGreen
    }

    val isError = amountError != null
    val errorMessage = when (amountError) {
        RecurringTransactionFormFieldError.AMOUNT_REQUIRED,
        RecurringTransactionFormFieldError.AMOUNT_NON_POSITIVE -> "Sıfırdan büyük bir tutar gir."
        RecurringTransactionFormFieldError.AMOUNT_TOO_LARGE -> "Tutar izin verilen limiti aşıyor."
        RecurringTransactionFormFieldError.AMOUNT_INVALID -> "Geçersiz tutar formatı."
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tutar",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = FeniqoTextPrimary,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FeniqoPureWhite),
            border = BorderStroke(
                1.dp,
                if (isError) FeniqoExpense else Color(0xFFF1F5F9),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = amountColor,
                )

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    enabled = isEnabled,
                    placeholder = {
                        Text(
                            text = "0,00",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(0xFFCBD5E1),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                    ).merge(FeniqoTabularNumberStyle),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = currency.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FeniqoTextSecondary,
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = FeniqoExpense,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Beyaz Gruplu Ayar Kartı: Kategori, Tekrar, Başlangıç, Bitiş (Görsel 02 & 03).
 */
@Composable
private fun RecurringSettingsGroupCard(
    selectedCategory: Category?,
    categoryError: RecurringTransactionFormFieldError?,
    frequency: RecurrenceFrequency,
    interval: Int,
    intervalError: RecurringTransactionFormFieldError?,
    startDate: LocalDate?,
    startDateError: RecurringTransactionFormFieldError?,
    endDate: LocalDate?,
    endDateError: RecurringTransactionFormFieldError?,
    onCategoryClick: () -> Unit,
    onRecurrenceClick: () -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FeniqoPureWhite),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Kategori Satırı
            val catColor = selectedCategory?.let { ColorParser.parseHexColorOrNull(it.color.hex) } ?: FeniqoSageGreen
            RecurringFormRow(
                icon = {
                    CategoryTonalIcon(
                        iconKey = selectedCategory?.icon?.key,
                        color = catColor,
                        containerSize = 36.dp,
                    )
                },
                title = "Kategori",
                value = selectedCategory?.name ?: "Kategori seç",
                isValuePlaceholder = selectedCategory == null,
                onClick = onCategoryClick,
                isEnabled = isEnabled,
            )
            if (categoryError != null) {
                Text(
                    text = "Kategori seçimi zorunludur.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // 2. Tekrar Düzeni Satırı
            val recurrenceSummary = RecurringTransactionDisplayModelMapper.formatRecurrenceSummary(frequency, interval)
            RecurringFormRow(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8F5EE), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = null,
                            tint = FeniqoSageGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                title = "Tekrar",
                value = recurrenceSummary,
                isValuePlaceholder = false,
                onClick = onRecurrenceClick,
                isEnabled = isEnabled,
            )
            if (intervalError != null) {
                Text(
                    text = "Tekrar aralığı en az 1 olmalıdır.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // 3. Başlangıç Tarihi Satırı
            val startDateText = startDate?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih seçin"
            RecurringFormRow(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8F5EE), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = FeniqoSageGreen,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                title = "Başlangıç",
                value = startDateText,
                isValuePlaceholder = startDate == null,
                onClick = onStartDateClick,
                isEnabled = isEnabled,
            )
            if (startDateError != null) {
                Text(
                    text = "Başlangıç tarihi zorunludur.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // 4. Bitiş Tarihi Satırı
            val endDateText = endDate?.let { DateFormatter.formatReadableDate(it) } ?: "Süresiz"
            RecurringFormRow(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                title = "Bitiş",
                value = endDateText,
                isValuePlaceholder = false,
                onClick = onEndDateClick,
                isEnabled = isEnabled,
            )
            if (endDateError != null) {
                Text(
                    text = "Bitiş tarihi başlangıçtan önce olamaz.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }
        }
    }
}

/**
 * Tekrarlayan Form Satırı Şablonu.
 */
@Composable
private fun RecurringFormRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    isValuePlaceholder: Boolean,
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = FeniqoTextPrimary,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = if (isValuePlaceholder) Color(0xFF94A3B8) else FeniqoTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Ödeme Yöntemi Kartı (Görsel 02 & 03).
 */
@Composable
private fun RecurringPaymentMethodCard(
    paymentMethod: PaymentMethod,
    onClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when (paymentMethod) {
        PaymentMethod.CASH -> Pair("Nakit", Icons.Outlined.AccountBalanceWallet)
        PaymentMethod.CREDIT_CARD -> Pair("Kredi kartı", Icons.Outlined.CreditCard)
        PaymentMethod.DEBIT_CARD -> Pair("Banka kartı", Icons.Outlined.CreditCard)
        PaymentMethod.BANK_TRANSFER -> Pair("Banka transferi", Icons.AutoMirrored.Outlined.CompareArrows)
        PaymentMethod.OTHER -> Pair("Diğer", Icons.Outlined.Category)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FeniqoPureWhite),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        RecurringFormRow(
            icon = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8F5EE), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            title = "Ödeme yöntemi",
            value = label,
            isValuePlaceholder = false,
            onClick = onClick,
            isEnabled = isEnabled,
        )
    }
}

/**
 * Açıklama Kartı (Görsel 02 & 03, max 500 karakter).
 */
@Composable
private fun RecurringDescriptionCard(
    description: String,
    descriptionError: RecurringTransactionFormFieldError?,
    onDescriptionChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Açıklama (isteğe bağlı)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = FeniqoTextPrimary,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FeniqoPureWhite),
            border = BorderStroke(
                1.dp,
                if (descriptionError != null) FeniqoExpense else Color(0xFFF1F5F9),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        if (it.length <= Transaction.MAX_DESCRIPTION_LENGTH) {
                            onDescriptionChange(it)
                        }
                    },
                    enabled = isEnabled,
                    placeholder = {
                        Text(
                            text = "Örn. Kira ödemesi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1),
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = FeniqoTextPrimary,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "${description.length}/${Transaction.MAX_DESCRIPTION_LENGTH}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF94A3B8),
                    )
                }
            }
        }

        if (descriptionError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Açıklama en fazla ${Transaction.MAX_DESCRIPTION_LENGTH} karakter olabilir.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = FeniqoExpense,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Dinamik Bilgi Banner'ı (Görsel 02, 03 & 08).
 * Aktif kuralda "Her ay işlem kaydı oluşturulur.", duraklatılmışta "Bu kural duraklatıldı."
 */
@Composable
private fun RecurringInfoBanner(
    frequency: RecurrenceFrequency,
    interval: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val bannerText = if (isPaused) {
        "Bu kural duraklatıldı."
    } else {
        when (frequency) {
            RecurrenceFrequency.DAILY -> if (interval == 1) "Her gün işlem kaydı oluşturulur." else "Her $interval günde bir işlem kaydı oluşturulur."
            RecurrenceFrequency.WEEKLY -> if (interval == 1) "Her hafta işlem kaydı oluşturulur." else "Her $interval haftada bir işlem kaydı oluşturulur."
            RecurrenceFrequency.MONTHLY -> if (interval == 1) "Her ay işlem kaydı oluşturulur." else "Her $interval ayda bir işlem kaydı oluşturulur."
            RecurrenceFrequency.YEARLY -> if (interval == 1) "Her yıl işlem kaydı oluşturulur." else "Her $interval yılda bir işlem kaydı oluşturulur."
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF1F5F9),
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
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = bannerText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color(0xFF475569),
            )
        }
    }
}

/**
 * Alt Aksiyon Butonları (Görsel 02, 03, 08).
 */
@Composable
private fun RecurringFormActionButtons(
    isEditMode: Boolean,
    isActive: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isEditMode) {
            // Oluşturma Modu: "Kuralı kaydet"
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
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
                    Text(
                        text = "Kuralı kaydet",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        } else {
            // Düzenleme Modu
            if (isActive) {
                // Aktif Düzenleme: "Değişiklikleri kaydet", "|| Kuralı duraklat", "🗑 Kuralı sil"
                Button(
                    onClick = onSubmit,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
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
                        Text(
                            text = "Değişiklikleri kaydet",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onSetActive(false) },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, FeniqoSageGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FeniqoSageGreen),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kuralı duraklat",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Button(
                    onClick = onRequestDelete,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFDC2626),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kuralı sil",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color(0xFFDC2626),
                        )
                    }
                }
            } else {
                // Duraklatılmış Düzenleme (Görsel 08): "Kuralı devam ettir", "Değişiklikleri kaydet", "🗑 Kuralı sil"
                Button(
                    onClick = { onSetActive(true) },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kuralı devam ettir",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSubmit,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FeniqoTextPrimary),
                ) {
                    Text(
                        text = "Değişiklikleri kaydet",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                Button(
                    onClick = onRequestDelete,
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFDC2626),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kuralı sil",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color(0xFFDC2626),
                        )
                    }
                }
            }
        }
    }
}
