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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.runtime.*
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
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.debt.DebtPaymentFormFieldError
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInput
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInputErrors
import com.feniqo.mobile.presentation.debt.toLocalizedDebtTypeLabel
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Borç ödeme ve alacak tahsilat formu ekranı.
 */
@Composable
fun DebtPaymentFormScreen(
    parentDebt: Debt,
    remainingAmount: Money,
    isSettled: Boolean,
    input: DebtPaymentFormInput,
    errors: DebtPaymentFormInputErrors,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onPaidOnClick: () -> Unit,
    onSubmit: () -> Unit,
    totalPaid: Money = Money(0L, parentDebt.amount.currency),
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting && !isSettled
    val isDebt = parentDebt.type == DebtType.DEBT
    val titleText = if (isDebt) {
        stringResource(Res.string.debt_payment_form_title_debt)
    } else {
        stringResource(Res.string.debt_payment_form_title_receivable)
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }

    val amountErrorText = when (errors.amountError) {
        DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT ->
            stringResource(Res.string.debt_payment_error_exceeds_remaining, remainingAmount.toLocalizedFormatted())
        DebtPaymentFormFieldError.AMOUNT_REQUIRED ->
            stringResource(Res.string.debt_payment_error_amount_required)
        DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE ->
            stringResource(Res.string.debt_payment_error_amount_non_positive)
        DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED ->
            stringResource(Res.string.debt_payment_error_already_settled)
        DebtPaymentFormFieldError.CURRENCY_MISMATCH ->
            stringResource(Res.string.debt_payment_error_currency_mismatch)
        null -> null
        else -> stringResource(Res.string.debt_payment_error_amount_invalid)
    }
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(errors.amountError) {
        if (errors.amountError != null) amountFocusRequester.requestFocus()
    }

    val totalMinor = parentDebt.amount.amountMinor
    val paidMinor = totalPaid.amountMinor
    val progressRatio = if (totalMinor > 0L) {
        (paidMinor.toFloat() / totalMinor.toFloat()).coerceIn(0f, 1f)
    } else 0f

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

            // Üst Başlık & Geri Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !isSubmitting,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.debt_payment_form_nav_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = titleText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Kaydırılabilir Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Üst Kayıt Bilgi Kartı
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

                        Column {
                            Text(
                                text = parentDebt.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = parentDebt.type.toLocalizedDebtTypeLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 2. Grafit Bakiye Durum Kartı (#303536)
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
                        val balanceLabel = if (isDebt) {
                            stringResource(Res.string.debt_payment_form_balance_remaining_debt)
                        } else {
                            stringResource(Res.string.debt_payment_form_balance_remaining_receivable)
                        }

                        Text(
                            text = balanceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = remainingAmount.toLocalizedFormatted(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 26.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progressRatio },
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
                                    parentDebt.amount.toLocalizedFormatted(),
                                ),
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                            )
                            val paidText = if (isDebt) {
                                stringResource(
                                    Res.string.debt_payment_form_balance_paid_debt,
                                    totalPaid.toLocalizedFormatted(),
                                )
                            } else {
                                stringResource(
                                    Res.string.debt_payment_form_balance_paid_receivable,
                                    totalPaid.toLocalizedFormatted(),
                                )
                            }
                            Text(
                                text = paidText,
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                            )
                            Text(
                                text = "%${(progressRatio * 100).toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF22C55E),
                            )
                        }
                    }
                }

                // Kapanmış Borç Durumu Bilgisi
                if (isSettled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_payment_form_settled_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // 3. Tutar Giriş Kartı
                Column {
                    val hasAmountError = errors.amountError != null
                    val amountLabel = if (isDebt) {
                        stringResource(Res.string.debt_payment_form_amount_label_debt)
                    } else {
                        stringResource(Res.string.debt_payment_form_amount_label_receivable)
                    }
                    val amountDesc = if (isDebt) {
                        stringResource(Res.string.debt_payment_form_amount_desc_debt)
                    } else {
                        stringResource(Res.string.debt_payment_form_amount_desc_receivable)
                    }

                    OutlinedTextField(
                        value = input.amountInput,
                        onValueChange = onAmountChange,
                        label = { Text(amountLabel) },
                        placeholder = { Text(stringResource(Res.string.debt_form_amount_placeholder)) },
                        trailingIcon = {
                            Text(
                                text = parentDebt.amount.currency.code,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        isError = hasAmountError,
                        singleLine = true,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester)
                            .semantics {
                                contentDescription = amountDesc
                                amountErrorText?.let { error(it) }
                            },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = if (hasAmountError) Color(0xFFDC2626) else FeniqoSageGreen,
                            unfocusedBorderColor = if (hasAmountError) Color(0xFFDC2626) else Color(0xFFE5E7EB),
                        ),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (errors.amountError != null) {
                        val errorText = checkNotNull(amountErrorText)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorText,
                                    fontSize = 12.sp,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(
                                Res.string.debt_payment_form_max_limit,
                                remainingAmount.toLocalizedFormatted(),
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                // 4. Ödeme Tarihi Seçici Kartı
                Column {
                    val dateLabel = if (isDebt) {
                        stringResource(Res.string.debt_payment_form_date_label_debt)
                    } else {
                        stringResource(Res.string.debt_payment_form_date_label_receivable)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = onPaidOnClick,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (errors.dateError != null) Color(0xFFDC2626) else Color(0xFFE5E7EB),
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
                                    text = dateLabel,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = input.paidOn?.toLocalizedReadableDate() ?: stringResource(Res.string.debt_payment_form_date_placeholder),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.paidOn != null) MaterialTheme.colorScheme.onSurface else Color(0xFF9CA3AF),
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = stringResource(Res.string.debt_payment_form_date_desc),
                                tint = FeniqoSageGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (errors.dateError != null) {
                        Text(
                            text = stringResource(Res.string.debt_payment_error_date_required),
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }

                // 5. Bilgilendirme Notu
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val infoNotice = if (isDebt) {
                            stringResource(Res.string.debt_payment_form_info_notice_debt)
                        } else {
                            stringResource(Res.string.debt_payment_form_info_notice_receivable)
                        }
                        Text(
                            text = infoNotice,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Birincil Eylem Butonu
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
                    val submitText = if (isDebt) {
                        stringResource(Res.string.debt_payment_form_submit_debt)
                    } else {
                        stringResource(Res.string.debt_payment_form_submit_receivable)
                    }
                    Text(
                        text = submitText,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }
}
