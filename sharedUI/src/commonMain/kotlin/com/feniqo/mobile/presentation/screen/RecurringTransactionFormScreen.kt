package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.outlined.Payment
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import com.feniqo.mobile.presentation.component.RecurrencePatternSheet
import com.feniqo.mobile.presentation.component.RecurringCategoryPickerSheet
import com.feniqo.mobile.presentation.component.RecurringDatePickerSheet
import com.feniqo.mobile.presentation.component.RecurringPaymentMethodPickerSheet
import com.feniqo.mobile.presentation.component.RecurringStatusBadge
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormFieldError
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInput
import com.feniqo.mobile.presentation.recurring.RecurringTransactionFormInputErrors
import com.feniqo.mobile.presentation.recurring.RecurringTransactionMutationState
import com.feniqo.mobile.presentation.recurring.formatLocalizedRecurrenceSummary
import com.feniqo.mobile.presentation.recurring.formatLocalizedRecurringBannerText
import com.feniqo.mobile.presentation.recurring.toLocalizedMessage
import com.feniqo.mobile.presentation.theme.FeniqoExpense
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.recurring_action_back
import feniqomobil.sharedui.generated.resources.recurring_action_delete_rule
import feniqomobil.sharedui.generated.resources.recurring_action_pause_rule
import feniqomobil.sharedui.generated.resources.recurring_action_resume_rule
import feniqomobil.sharedui.generated.resources.recurring_action_save_changes
import feniqomobil.sharedui.generated.resources.recurring_action_save_rule
import feniqomobil.sharedui.generated.resources.recurring_date_indefinite
import feniqomobil.sharedui.generated.resources.recurring_form_amount_content_desc
import feniqomobil.sharedui.generated.resources.recurring_form_amount_label
import feniqomobil.sharedui.generated.resources.recurring_form_amount_placeholder
import feniqomobil.sharedui.generated.resources.recurring_form_category_label
import feniqomobil.sharedui.generated.resources.recurring_form_category_placeholder
import feniqomobil.sharedui.generated.resources.recurring_form_description_content_desc
import feniqomobil.sharedui.generated.resources.recurring_form_description_label
import feniqomobil.sharedui.generated.resources.recurring_form_description_placeholder
import feniqomobil.sharedui.generated.resources.recurring_form_end_date_label
import feniqomobil.sharedui.generated.resources.recurring_form_end_date_title
import feniqomobil.sharedui.generated.resources.recurring_form_payment_method_label
import feniqomobil.sharedui.generated.resources.recurring_form_recurrence_label
import feniqomobil.sharedui.generated.resources.recurring_form_start_date_label
import feniqomobil.sharedui.generated.resources.recurring_form_start_date_placeholder
import feniqomobil.sharedui.generated.resources.recurring_form_start_date_title
import feniqomobil.sharedui.generated.resources.recurring_form_title_create
import feniqomobil.sharedui.generated.resources.recurring_form_title_edit
import feniqomobil.sharedui.generated.resources.recurring_type_expense
import feniqomobil.sharedui.generated.resources.recurring_type_income
import org.jetbrains.compose.resources.stringResource

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        Unit
    }

    LaunchedEffect(errors) {
        when {
            errors.amountError != null -> amountFocusRequester.requestFocus()
            errors.descriptionError != null -> descriptionFocusRequester.requestFocus()
        }
    }

    var showCategorySheet by remember { mutableStateOf(false) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }
    var showPaymentMethodSheet by remember { mutableStateOf(false) }
    var showStartDateSheet by remember { mutableStateOf(false) }
    var showEndDateSheet by remember { mutableStateOf(false) }

    val selectedCategory = categories.firstOrNull { it.id == input.categoryId }
    val currentInterval = input.intervalInput.toIntOrNull() ?: 1

    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            RecurringFormTopBar(
                isEditMode = isEditMode,
                isActive = isActive,
                onBack = onBack,
                isEnabled = isEnabled,
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ActiveWorkspaceIndicator(
                    workspaceName = activeWorkspaceName,
                    isCompact = true,
                )

                RecurringTypeToggle(
                    selectedType = input.type,
                    onTypeChange = onTypeChange,
                    isEnabled = isEnabled,
                )

                RecurringAmountCard(
                    amountInput = input.amountInput,
                    currency = input.currency,
                    transactionType = input.type,
                    amountError = errors.amountError,
                    onAmountChange = onAmountChange,
                    isEnabled = isEnabled,
                    fieldFocusRequester = amountFocusRequester,
                    onImeNext = { descriptionFocusRequester.requestFocus() },
                )

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

                RecurringPaymentMethodCard(
                    paymentMethod = input.paymentMethod,
                    onClick = {
                        if (isEnabled) showPaymentMethodSheet = true
                    },
                    isEnabled = isEnabled,
                )

                RecurringDescriptionCard(
                    description = input.description,
                    descriptionError = errors.descriptionError,
                    onDescriptionChange = onDescriptionChange,
                    isEnabled = isEnabled,
                    fieldFocusRequester = descriptionFocusRequester,
                    onImeDone = dismissKeyboard,
                )

                RecurringInfoBanner(
                    frequency = input.frequency,
                    interval = currentInterval,
                    isPaused = isEditMode && !isActive,
                )

                Spacer(modifier = Modifier.height(8.dp))

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
            title = stringResource(Res.string.recurring_form_start_date_title),
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
            title = stringResource(Res.string.recurring_form_end_date_title),
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
    val backTitle =
        if (isEditMode) {
            stringResource(Res.string.recurring_form_title_edit)
        } else {
            stringResource(Res.string.recurring_form_title_create)
        }

    Row(
        modifier =
            modifier
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
                    contentDescription = stringResource(Res.string.recurring_action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = backTitle,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Text(
            text = "feniqo",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
            color = FeniqoSageGreen,
        )

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

        Surface(
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            enabled = isEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isExpense) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
            border =
                BorderStroke(
                    1.dp,
                    if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .background(if (isExpense) Color(0xFFDC2626) else Color(0xFF94A3B8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "−",
                        color = Color.White,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.recurring_type_expense),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (isExpense) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            onClick = { onTypeChange(TransactionType.INCOME) },
            enabled = isEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isIncome) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border =
                BorderStroke(
                    1.dp,
                    if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .background(if (isIncome) FeniqoTrendGreen else Color(0xFF94A3B8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = Color.White,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.recurring_type_income),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (isIncome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
    fieldFocusRequester: FocusRequester,
    onImeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencySymbol =
        when (currency) {
            Currency.TRY -> "₺"
            Currency.USD -> "$"
            Currency.EUR -> "€"
            Currency.GBP -> "£"
        }

    val amountColor =
        when (transactionType) {
            TransactionType.EXPENSE -> FeniqoExpense
            TransactionType.INCOME -> FeniqoTrendGreen
        }

    val isError = amountError != null
    val errorMessage = amountError?.toLocalizedMessage()
    val amountContentDesc = stringResource(Res.string.recurring_form_amount_content_desc)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.recurring_form_amount_label),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border =
                BorderStroke(
                    1.dp,
                    if (isError) FeniqoExpense else Color(0xFFF1F5F9),
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currencySymbol,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
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
                            text = stringResource(Res.string.recurring_form_amount_placeholder),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions = KeyboardActions(onNext = { onImeNext() }),
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor,
                        ).merge(FeniqoTabularNumberStyle),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(fieldFocusRequester)
                            .semantics {
                                contentDescription = amountContentDesc
                                errorMessage?.let { error(it) }
                            },
                )

                Text(
                    text = currency.name,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val catColor = selectedCategory?.let { ColorParser.parseHexColorOrNull(it.color.hex) } ?: FeniqoSageGreen
            RecurringFormRow(
                icon = {
                    CategoryTonalIcon(
                        iconKey = selectedCategory?.icon?.key,
                        color = catColor,
                        containerSize = 36.dp,
                    )
                },
                title = stringResource(Res.string.recurring_form_category_label),
                value = selectedCategory?.name ?: stringResource(Res.string.recurring_form_category_placeholder),
                isValuePlaceholder = selectedCategory == null,
                onClick = onCategoryClick,
                isEnabled = isEnabled,
            )
            if (categoryError != null) {
                Text(
                    text = categoryError.toLocalizedMessage(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            val recurrenceSummary = formatLocalizedRecurrenceSummary(frequency, interval)
            RecurringFormRow(
                icon = {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
                title = stringResource(Res.string.recurring_form_recurrence_label),
                value = recurrenceSummary,
                isValuePlaceholder = false,
                onClick = onRecurrenceClick,
                isEnabled = isEnabled,
            )
            if (intervalError != null) {
                Text(
                    text = intervalError.toLocalizedMessage(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            val startDateText = startDate?.toLocalizedReadableDate() ?: stringResource(Res.string.recurring_form_start_date_placeholder)
            RecurringFormRow(
                icon = {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
                title = stringResource(Res.string.recurring_form_start_date_label),
                value = startDateText,
                isValuePlaceholder = startDate == null,
                onClick = onStartDateClick,
                isEnabled = isEnabled,
            )
            if (startDateError != null) {
                Text(
                    text = startDateError.toLocalizedMessage(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoExpense,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            val endDateText = endDate?.toLocalizedReadableDate() ?: stringResource(Res.string.recurring_date_indefinite)
            RecurringFormRow(
                icon = {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                title = stringResource(Res.string.recurring_form_end_date_label),
                value = endDateText,
                isValuePlaceholder = false,
                onClick = onEndDateClick,
                isEnabled = isEnabled,
            )
            if (endDateError != null) {
                Text(
                    text = endDateError.toLocalizedMessage(),
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = value,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    color = if (isValuePlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val icon =
        when (paymentMethod) {
            PaymentMethod.CASH -> Icons.Outlined.AccountBalanceWallet
            PaymentMethod.CREDIT_CARD -> Icons.Outlined.CreditCard
            PaymentMethod.DEBIT_CARD -> Icons.Outlined.Payment
            PaymentMethod.BANK_TRANSFER -> Icons.AutoMirrored.Outlined.CompareArrows
            PaymentMethod.OTHER -> Icons.Outlined.Category
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        RecurringFormRow(
            icon = {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
            title = stringResource(Res.string.recurring_form_payment_method_label),
            value = paymentMethod.toLocalizedText(),
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
    fieldFocusRequester: FocusRequester,
    onImeDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorMessage = descriptionError?.toLocalizedMessage()
    val descriptionContentDesc = stringResource(Res.string.recurring_form_description_content_desc)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.recurring_form_description_label),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border =
                BorderStroke(
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
                            text = stringResource(Res.string.recurring_form_description_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onImeDone() }),
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocusRequester)
                            .semantics {
                                contentDescription = descriptionContentDesc
                                if (errorMessage != null) error(errorMessage)
                            },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "${description.length}/${Transaction.MAX_DESCRIPTION_LENGTH}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    val bannerText =
        formatLocalizedRecurringBannerText(
            frequency = frequency,
            interval = interval,
            isPaused = isPaused,
        )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = bannerText,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
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
                        text = stringResource(Res.string.recurring_action_save_rule),
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }
        } else {
            if (isActive) {
                Button(
                    onClick = onSubmit,
                    enabled = isEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
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
                            text = stringResource(Res.string.recurring_action_save_changes),
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onSetActive(false) },
                    enabled = isEnabled,
                    modifier =
                        Modifier
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
                            text = stringResource(Res.string.recurring_action_pause_rule),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                    }
                }

                Button(
                    onClick = onRequestDelete,
                    enabled = isEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
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
                            text = stringResource(Res.string.recurring_action_delete_rule),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = Color(0xFFDC2626),
                        )
                    }
                }
            } else {
                Button(
                    onClick = { onSetActive(true) },
                    enabled = isEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
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
                            text = stringResource(Res.string.recurring_action_resume_rule),
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSubmit,
                    enabled = isEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_action_save_changes),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }

                Button(
                    onClick = onRequestDelete,
                    enabled = isEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
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
                            text = stringResource(Res.string.recurring_action_delete_rule),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
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
