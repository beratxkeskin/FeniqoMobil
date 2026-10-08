@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.common.symbol
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.component.DebtCurrencyPickerSheet
import com.feniqo.mobile.presentation.component.DebtDatePickerSheet
import com.feniqo.mobile.presentation.component.DebtSettledBanner
import com.feniqo.mobile.presentation.debt.DebtBalanceSummaryUiModel
import com.feniqo.mobile.presentation.debt.DebtFormFieldError
import com.feniqo.mobile.presentation.debt.DebtFormInput
import com.feniqo.mobile.presentation.debt.DebtFormInputErrors
import com.feniqo.mobile.presentation.debt.DebtPaymentHistoryItemUiModel
import com.feniqo.mobile.presentation.debt.toLocalizedDebtTypeLabel
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.debt.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Borç ve Alacak kayıt oluşturma ve düzenleme/detay ekranı.
 */
@Composable
fun DebtFormScreen(
    input: DebtFormInput,
    errors: DebtFormInputErrors,
    isSubmitting: Boolean,
    isEditMode: Boolean,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onTypeChange: (DebtType) -> Unit,
    onDueDateClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onSubmit: () -> Unit,
    onAddPayment: (() -> Unit)? = null,
    paymentsHistory: List<DebtPaymentHistoryItemUiModel> = emptyList(),
    balanceSummary: DebtBalanceSummaryUiModel? = null,
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    val titleErrorText = when (errors.titleError) {
        DebtFormFieldError.TITLE_REQUIRED -> stringResource(Res.string.debt_error_title_required)
        DebtFormFieldError.TITLE_TOO_LONG -> stringResource(Res.string.debt_error_title_too_long)
        null -> null
        else -> stringResource(Res.string.debt_error_generic)
    }
    val amountErrorText = when (errors.amountError) {
        DebtFormFieldError.AMOUNT_REQUIRED -> stringResource(Res.string.debt_error_amount_required)
        DebtFormFieldError.AMOUNT_NON_POSITIVE -> stringResource(Res.string.debt_error_amount_non_positive)
        DebtFormFieldError.AMOUNT_INVALID -> stringResource(Res.string.debt_error_amount_invalid)
        null -> null
        else -> stringResource(Res.string.debt_error_generic)
    }
    val descriptionErrorText = errors.descriptionError?.let { stringResource(Res.string.debt_error_description_too_long) }
    val titleFieldDesc = stringResource(Res.string.debt_form_title_content_desc)
    val amountFieldDesc = stringResource(Res.string.debt_form_amount_content_desc)
    val descriptionFieldDesc = stringResource(Res.string.debt_form_description_content_desc)
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(errors) {
        when {
            errors.titleError != null -> titleFocusRequester.requestFocus()
            errors.amountError != null -> amountFocusRequester.requestFocus()
            errors.descriptionError != null -> descriptionFocusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FeniqoSpacing.Large)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık & Geri / Sil Barı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = isEnabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.debt_form_nav_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    val screenTitle = if (isEditMode) {
                        stringResource(Res.string.debt_form_title_edit)
                    } else {
                        stringResource(Res.string.debt_form_title_create)
                    }

                    Text(
                        text = screenTitle,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (isEditMode) {
                    IconButton(
                        onClick = onRequestDelete,
                        enabled = isEnabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(Res.string.debt_form_delete_desc),
                            tint = Color(0xFFDC2626),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Kaydırılabilir Form İçeriği
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isEditMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isDebt = input.type == DebtType.DEBT
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDebt) Color(0xFFFEF2F2) else Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isDebt) Icons.Outlined.CreditCard else Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = input.titleInput.ifBlank { input.type.toLocalizedDebtTypeLabel() },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = input.type.toLocalizedDebtTypeLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val isSettled = balanceSummary?.isSettled == true
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSettled) Color(0xFFDCFCE7) else Color(0xFFE0F2FE))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (isSettled) {
                                        stringResource(Res.string.debt_form_badge_settled)
                                    } else {
                                        stringResource(Res.string.debt_form_badge_active)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSettled) Color(0xFF166534) else Color(0xFF0369A1),
                                )
                            }
                        }
                    }
                }

                // Grafit Bakiye Durum Kartı (#303536)
                if (isEditMode && balanceSummary != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF303536)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            val remainingLabel = if (input.type == DebtType.DEBT) {
                                stringResource(Res.string.debt_payment_form_balance_remaining_debt)
                            } else {
                                stringResource(Res.string.debt_payment_form_balance_remaining_receivable)
                            }

                            Text(
                                text = remainingLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = balanceSummary.remainingAmount.toLocalizedFormatted(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 26.sp,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { balanceSummary.progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF22C55E),
                                trackColor = Color(0xFF4B5563),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.debt_payment_form_balance_total,
                                        balanceSummary.principalAmount.toLocalizedFormatted(),
                                    ),
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1D5DB),
                                )
                                val paidLabel = if (input.type == DebtType.DEBT) {
                                    stringResource(
                                        Res.string.debt_payment_form_balance_paid_debt,
                                        balanceSummary.totalPaid.toLocalizedFormatted(),
                                    )
                                } else {
                                    stringResource(
                                        Res.string.debt_payment_form_balance_paid_receivable,
                                        balanceSummary.totalPaid.toLocalizedFormatted(),
                                    )
                                }
                                Text(
                                    text = paidLabel,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1D5DB),
                                )
                                Text(
                                    text = "%${(balanceSummary.progressRatio * 100).toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF22C55E),
                                )
                            }
                        }
                    }
                }

                // Tamamlandı Banner'ı veya + Ödeme/Tahsilat Ekle Butonu
                if (isEditMode) {
                    if (balanceSummary?.isSettled == true) {
                        DebtSettledBanner(isDebt = input.type == DebtType.DEBT)
                    } else if (onAddPayment != null) {
                        Button(
                            onClick = onAddPayment,
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (input.type == DebtType.DEBT) {
                                    stringResource(Res.string.debt_payment_form_title_debt)
                                } else {
                                    stringResource(Res.string.debt_payment_form_title_receivable)
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                            )
                        }
                    }
                }

                // Kayıt Bilgileri Bölüm Başlığı
                Text(
                    text = stringResource(Res.string.debt_form_section_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )

                // Tür Seçimi Segmenti (Borç / Alacak)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (input.type == DebtType.DEBT) FeniqoSageGreen else Color.Transparent)
                                .clickable(
                                    enabled = isEnabled,
                                    role = Role.Tab,
                                    onClick = { onTypeChange(DebtType.DEBT) },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.debt_type_debt),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (input.type == DebtType.DEBT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (input.type == DebtType.RECEIVABLE) FeniqoSageGreen else Color.Transparent)
                                .clickable(
                                    enabled = isEnabled,
                                    role = Role.Tab,
                                    onClick = { onTypeChange(DebtType.RECEIVABLE) },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.debt_type_receivable),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (input.type == DebtType.RECEIVABLE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Başlık / Kişi / Kurum
                OutlinedTextField(
                    value = input.titleInput,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(Res.string.debt_form_title_label)) },
                    placeholder = { Text(stringResource(Res.string.debt_form_title_placeholder)) },
                    isError = errors.titleError != null,
                    supportingText = {
                        titleErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { amountFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .semantics {
                            contentDescription = titleFieldDesc
                            titleErrorText?.let { error(it) }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                // Toplam Tutar
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(Res.string.debt_form_amount_label)) },
                    placeholder = { Text(stringResource(Res.string.debt_form_amount_placeholder)) },
                    trailingIcon = {
                        Text(
                            text = input.currency.symbol(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    isError = errors.amountError != null,
                    supportingText = {
                        amountErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { descriptionFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester)
                        .semantics {
                            contentDescription = amountFieldDesc
                            amountErrorText?.let { error(it) }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                // Para Birimi Seçimi
                if (isEditMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.debt_currency_picker_title),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${input.currency.code} (${input.currency.symbol()})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(Res.string.debt_form_currency_locked),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = { showCurrencyPicker = true },
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.debt_currency_picker_title),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${input.currency.code} (${input.currency.symbol()})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(Res.string.debt_form_currency_select_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Vade Tarihi Kartı
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = onDueDateClick,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (errors.dueDateError != null) Color(0xFFDC2626) else Color(0xFFE5E7EB),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.debt_form_due_date_label),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = input.dueDate?.toLocalizedReadableDate() ?: stringResource(Res.string.debt_form_due_date_placeholder),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.dueDate != null) MaterialTheme.colorScheme.onSurface else Color(0xFF9CA3AF),
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = stringResource(Res.string.debt_form_due_date_desc),
                                tint = FeniqoSageGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (errors.dueDateError != null) {
                        Text(
                            text = stringResource(Res.string.debt_error_due_date_required),
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }

                // Açıklama
                OutlinedTextField(
                    value = input.descriptionInput,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(Res.string.debt_form_description_label)) },
                    placeholder = { Text(stringResource(Res.string.debt_form_description_placeholder)) },
                    isError = errors.descriptionError != null,
                    supportingText = {
                        descriptionErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    },
                    minLines = 2,
                    maxLines = 4,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester)
                        .semantics {
                            contentDescription = descriptionFieldDesc
                            descriptionErrorText?.let { error(it) }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                // Ödeme / Tahsilat Geçmişi Bölümü (Düzenleme Modunda)
                if (isEditMode) {
                    val isDebt = input.type == DebtType.DEBT
                    val historyTitle = if (isDebt) {
                        stringResource(Res.string.debt_form_history_title_debt)
                    } else {
                        stringResource(Res.string.debt_form_history_title_receivable)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = historyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )

                    if (paymentsHistory.isEmpty()) {
                        Text(
                            text = if (isDebt) {
                                stringResource(Res.string.debt_form_history_empty_debt)
                            } else {
                                stringResource(Res.string.debt_form_history_empty_receivable)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            paymentsHistory.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDebt) Color(0xFFFEF2F2) else Color(0xFFECFDF5)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = if (isDebt) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = item.paidOn.toLocalizedReadableDate(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = if (isDebt) {
                                                        stringResource(Res.string.debt_form_history_badge_debt)
                                                    } else {
                                                        stringResource(Res.string.debt_form_history_badge_receivable)
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${if (isDebt) "-" else "+"}${item.amount.toLocalizedFormatted()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onRequestDelete,
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_form_delete_button),
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Kaydet / Oluştur Butonu
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (isEditMode) {
                            stringResource(Res.string.debt_form_save_changes)
                        } else {
                            stringResource(Res.string.debt_form_create_record)
                        },
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                    )
                }
            }
        }

        if (showCurrencyPicker) {
            DebtCurrencyPickerSheet(
                selectedCurrency = input.currency,
                onCurrencySelected = onCurrencyChange,
                onDismiss = { showCurrencyPicker = false },
            )
        }
    }
}
