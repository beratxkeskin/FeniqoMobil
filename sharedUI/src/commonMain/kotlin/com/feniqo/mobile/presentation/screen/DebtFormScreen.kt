package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.debt.DebtFormFieldError
import com.feniqo.mobile.presentation.debt.DebtFormInput
import com.feniqo.mobile.presentation.debt.DebtFormInputErrors
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Borç ve alacak oluşturma ve düzenleme için durumsuz (stateless) form ekranıdır.
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
    paymentsHistory: List<com.feniqo.mobile.presentation.debt.DebtPaymentHistoryItemUiModel> = emptyList(),
    balanceSummary: com.feniqo.mobile.presentation.debt.DebtBalanceSummaryUiModel? = null,
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
                        text = if (isEditMode) "Borç / Alacağı Düzenle" else "Yeni Borç / Alacak",
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

            if (isEditMode && balanceSummary != null) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Bakiye Durumu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = balanceSummary.statusText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (balanceSummary.isSettled) com.feniqo.mobile.presentation.theme.FeniqoStatusColor.Success else MaterialTheme.colorScheme.primary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Toplam Tutar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = balanceSummary.formattedPrincipalAmount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Column {
                                val paidLabel = if (balanceSummary.type == DebtType.RECEIVABLE) "Tahsil Edilen" else "Ödenen"
                                Text(
                                    text = paidLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = balanceSummary.formattedTotalPaid,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val remainingLabel = if (balanceSummary.type == DebtType.RECEIVABLE) "Kalan Alacak" else "Kalan Borç"
                                Text(
                                    text = remainingLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = balanceSummary.formattedRemainingAmount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (balanceSummary.isSettled) com.feniqo.mobile.presentation.theme.FeniqoStatusColor.Success else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (isEditMode && onAddPayment != null) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                OutlinedButton(
                    onClick = onAddPayment,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                ) {
                    val addPaymentLabel = if (input.type == DebtType.RECEIVABLE) "+ Tahsilat Ekle" else "+ Ödeme Ekle"
                    Text(addPaymentLabel)
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

                // Tür Seçimi (Borç vs Alacak)
                Column {
                    Text(
                        text = "İşlem Türü",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        FilterChip(
                            selected = input.type == DebtType.DEBT,
                            onClick = { onTypeChange(DebtType.DEBT) },
                            enabled = isEnabled,
                            label = { Text("Borç (Vereceğim)") },
                        )
                        FilterChip(
                            selected = input.type == DebtType.RECEIVABLE,
                            onClick = { onTypeChange(DebtType.RECEIVABLE) },
                            enabled = isEnabled,
                            label = { Text("Alacak (Alacağım)") },
                        )
                    }
                }

                // Başlık
                OutlinedTextField(
                    value = input.titleInput,
                    onValueChange = onTitleChange,
                    label = { Text("Başlık / Kişi / Kurum *") },
                    placeholder = { Text("Örn: Ahmet Borç, Kredi Kartı, Banka Kredisi") },
                    isError = errors.titleError != null,
                    supportingText = {
                        errors.titleError?.let {
                            Text(
                                text = when (it) {
                                    DebtFormFieldError.TITLE_REQUIRED -> "Başlık zorunludur."
                                    DebtFormFieldError.TITLE_TOO_LONG -> "Başlık en fazla 500 karakter olabilir."
                                    else -> "Geçersiz başlık."
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

                // Tutar
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Toplam Tutar *") },
                    placeholder = { Text("0,00") },
                    isError = errors.amountError != null,
                    supportingText = {
                        errors.amountError?.let {
                            Text(
                                text = when (it) {
                                    DebtFormFieldError.AMOUNT_REQUIRED -> "Tutar zorunludur."
                                    DebtFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
                                    DebtFormFieldError.AMOUNT_INVALID -> "Geçerli bir tutar girin."
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

                // Vade Tarihi Seçici
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FeniqoRadius.Medium))
                        .clickable(
                            role = Role.Button,
                            enabled = isEnabled,
                            onClick = onDueDateClick,
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
                                text = "Vade Tarihi *",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (input.dueDate != null) {
                                    DateFormatter.formatReadableDate(input.dueDate)
                                } else {
                                    "Tarih Seçin"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (input.dueDate != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        Text(text = "📅", fontSize = 20.sp)
                    }
                }
                if (errors.dueDateError != null) {
                    Text(
                        text = "Vade tarihi seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = FeniqoSpacing.Small),
                    )
                }

                // Açıklama
                OutlinedTextField(
                    value = input.descriptionInput,
                    onValueChange = onDescriptionChange,
                    label = { Text("Açıklama (Opsiyonel)") },
                    placeholder = { Text("Not veya detay ekleyin") },
                    isError = errors.descriptionError != null,
                    supportingText = {
                        errors.descriptionError?.let {
                            Text(
                                text = "Açıklama en fazla 500 karakter olabilir.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    maxLines = 3,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isEditMode) {
                    val historyTitle = if (input.type == com.feniqo.mobile.domain.model.DebtType.DEBT) "Ödeme Geçmişi" else "Tahsilat Geçmişi"
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    Text(
                        text = historyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    if (paymentsHistory.isEmpty()) {
                        val emptyText = if (input.type == com.feniqo.mobile.domain.model.DebtType.DEBT) {
                            "Henüz bir ödeme kaydı bulunmuyor."
                        } else {
                            "Henüz bir tahsilat kaydı bulunmuyor."
                        }
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            paymentsHistory.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(FeniqoSpacing.Medium),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = item.formattedDate,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = item.formattedAmount,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
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
                        text = if (isEditMode) "Değişiklikleri Kaydet" else "Kaydet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
