package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.debt.DebtPaymentFormFieldError
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInput
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInputErrors
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

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
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting && !isSettled
    val titleText = if (parentDebt.type == DebtType.DEBT) "Borç Ödemesi Ekle" else "Tahsilat Ekle"

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

            // Üst Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !isSubmitting,
                ) {
                    Text("← Geri")
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                // Parent Debt Bilgi Kartı
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    ) {
                        Text(
                            text = parentDebt.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Toplam: ${MoneyFormatter.format(parentDebt.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Kalan: ${MoneyFormatter.format(remainingAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSettled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (isSettled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = "Bu borç / alacak tamamen kapanmıştır. Yeni ödeme eklenemez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(FeniqoSpacing.Medium),
                        )
                    }
                }

                // Tutar Alanı
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Ödeme Tutarı *") },
                    placeholder = { Text("0,00") },
                    trailingIcon = {
                        Text(
                            text = parentDebt.amount.currency.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FeniqoSpacing.Medium),
                        )
                    },
                    isError = errors.amountError != null,
                    supportingText = {
                        if (errors.amountError != null) {
                            Text(
                                text = when (errors.amountError) {
                                    DebtPaymentFormFieldError.AMOUNT_REQUIRED -> "Tutar zorunludur."
                                    DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
                                    DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT -> "Ödeme tutarı kalan borcu (${MoneyFormatter.format(remainingAmount)}) aşamaz."
                                    DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED -> "Borç tamamen kapandığı için ödeme yapılamaz."
                                    DebtPaymentFormFieldError.CURRENCY_MISMATCH -> "Para birimi uyuşmuyor."
                                    else -> "Geçersiz tutar."
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                text = "Kalan: ${MoneyFormatter.format(remainingAmount)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Ödeme Tarihi Seçici
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FeniqoRadius.Medium))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable(
                            role = Role.Button,
                            enabled = isEnabled,
                            onClick = onPaidOnClick,
                        )
                        .padding(FeniqoSpacing.Medium),
                ) {
                    Text(
                        text = "Ödeme Tarihi *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = input.paidOn?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih Seçin",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (input.paidOn != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                }
                if (errors.dateError != null) {
                    Text(
                        text = "Ödeme tarihi seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = FeniqoSpacing.Small),
                    )
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
                        text = if (parentDebt.type == DebtType.DEBT) "Ödemeyi Kaydet" else "Tahsilatı Kaydet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
