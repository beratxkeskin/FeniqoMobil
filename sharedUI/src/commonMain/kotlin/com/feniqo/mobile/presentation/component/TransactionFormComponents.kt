package com.feniqo.mobile.presentation.component

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionCategoryOptionUiModel
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * İşlem türü seçicisi bileşenidir (Gider / Gelir).
 */
@Composable
fun TransactionTypeSelector(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    enabled: Boolean,
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

        // Gider Seçeneği
        Surface(
            selected = isExpense,
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            enabled = enabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isExpense) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color.Transparent
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.RadioButton
                    contentDescription = "Gider türü seçimi"
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
            ) {
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                    color = if (isExpense) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Gelir Seçeneği
        Surface(
            selected = isIncome,
            onClick = { onTypeChange(TransactionType.INCOME) },
            enabled = enabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isIncome) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.RadioButton
                    contentDescription = "Gelir türü seçimi"
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
            ) {
                Text(
                    text = "Gelir",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                    color = if (isIncome) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Tutar ve para birimi giriş alanı bileşenidir.
 */
@Composable
fun TransactionAmountField(
    amountText: String,
    currency: Currency,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Text(
            text = "Tutar",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                enabled = enabled,
                isError = errorText != null,
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "İşlem tutarı" },
            )

            // Para Birimi Seçicisi
            CurrencySelector(
                selectedCurrency = currency,
                onCurrencyChange = onCurrencyChange,
                enabled = enabled,
            )
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FeniqoSpacing.Small),
            )
        }
    }
}

/**
 * Para birimi seçim çipleri bileşenidir.
 */
@Composable
fun CurrencySelector(
    selectedCurrency: Currency,
    onCurrencyChange: (Currency) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(FeniqoSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Currency.entries.forEach { curr ->
            val isSelected = curr == selectedCurrency
            Surface(
                selected = isSelected,
                onClick = { onCurrencyChange(curr) },
                enabled = enabled,
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 40.dp, minHeight = 44.dp)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = "Para birimi ${curr.code}"
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Small),
                ) {
                    Text(
                        text = curr.code,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Kategori seçimi bileşenidir. Aktif ve tarihsel kategorileri güvenli biçimde listeler.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionCategoryPicker(
    availableCategories: List<TransactionCategoryOptionUiModel>,
    selectedCategoryId: EntityId?,
    onCategoryChange: (EntityId?) -> Unit,
    categoryLoadError: FinanceUiMessage?,
    onRetryCategories: () -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAddCategoryClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedCategoryId != null && enabled) {
                    TextButton(
                        onClick = { onCategoryChange(null) },
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                    ) {
                        Text(
                            text = "Temizle",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                TextButton(
                    onClick = onAddCategoryClick,
                    enabled = enabled,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Bu işlem türü için yeni kategori ekle"
                        },
                ) {
                    Text(
                        text = "+ Yeni Kategori",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (categoryLoadError != null) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
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
                        text = "Kategoriler yüklenemedi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onRetryCategories,
                        enabled = enabled,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(
                            text = "Yeniden Dene",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        } else if (availableCategories.isEmpty()) {
            Text(
                text = "Bu işlem türüne ait kategori bulunmuyor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                availableCategories.forEach { category ->
                    val isSelected = selectedCategoryId == category.id
                    val color = ColorParser.parseHexColorOrNull(category.colorHex)

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (category.isSelectable) {
                                onCategoryChange(if (isSelected) null else category.id)
                            }
                        },
                        enabled = enabled && category.isSelectable,
                        leadingIcon = {
                            if (color != null) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(color, CircleShape),
                                )
                            }
                        },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                contentDescription = if (category.isHistorical) {
                                    "${category.name}, silinmiş kategori"
                                } else {
                                    category.name
                                }
                            },
                    )
                }
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FeniqoSpacing.Small),
            )
        }
    }
}

/**
 * İşlem tarihi seçim alanı bileşenidir. Tıklanınca tarih seçiciyi tetikler.
 */
@Composable
fun TransactionDatePickerField(
    date: LocalDate?,
    onDateClick: () -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Text(
            text = "İşlem Tarihi",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val displayText = if (date != null) {
            formatDisplayDate(date)
        } else {
            "Tarih seçin"
        }

        Surface(
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            ),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(enabled = enabled, onClick = onDateClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "İşlem tarihi: $displayText"
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (date != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Tarih Seç",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FeniqoSpacing.Small),
            )
        }
    }
}

/**
 * Ödeme yöntemi seçici bileşenidir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionPaymentMethodSelector(
    selectedMethod: PaymentMethod,
    onMethodChange: (PaymentMethod) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Text(
            text = "Ödeme Yöntemi",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
        ) {
            PaymentMethod.entries.forEach { method ->
                val isSelected = method == selectedMethod
                FilterChip(
                    selected = isSelected,
                    onClick = { onMethodChange(method) },
                    enabled = enabled,
                    label = { Text(method.toDisplayText()) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Ödeme yöntemi ${method.toDisplayText()}"
                        },
                )
            }
        }
    }
}

/**
 * Taksit seçeneği ve taksit sayısı giriş alanı bileşenidir.
 */
@Composable
fun TransactionInstallmentSection(
    isInstallmentEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    installmentCountText: String,
    onCountChange: (String) -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isInstallmentEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Taksitli İşlem",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Kredi kartı ile yapılan harcamayı taksitlere bölün.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = isInstallmentEnabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Taksitli işlem açma kapatma" },
                )
            }

            if (isInstallmentEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = "Girilen tutar toplam işlem tutarıdır; seçilen taksit sayısına eşit olarak bölünecektir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )

                    OutlinedTextField(
                        value = installmentCountText,
                        onValueChange = onCountChange,
                        enabled = enabled,
                        isError = errorText != null,
                        label = { Text("Taksit Sayısı (2 - 60)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics { contentDescription = "Taksit sayısı" },
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = FeniqoSpacing.Small),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Düzenleme modunda mevcut taksitli işlemin salt okunur bilgi kartıdır.
 */
@Composable
fun TransactionExistingInstallmentBadge(
    installment: InstallmentDisplayModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            InstallmentBadge(badgeText = installment.badgeText)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Taksitli İşlem (${installment.badgeText})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Taksitli işlem düzenlenirken mevcut taksit planı korunur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Açıklama giriş alanı bileşenidir.
 */
@Composable
fun TransactionDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Açıklama (İsteğe Bağlı)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "${description.length}/500",
                style = MaterialTheme.typography.labelSmall,
                color = if (description.length > 500) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            enabled = enabled,
            isError = errorText != null,
            placeholder = { Text("İşlem hakkında not ekleyin...") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "İşlem açıklaması" },
        )

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FeniqoSpacing.Small),
            )
        }
    }
}

/**
 * Tarihi Türkçe gün ay yıl formatına dönüştürür.
 */
fun formatDisplayDate(date: LocalDate): String {
    val monthName = when (date.monthNumber) {
        1 -> "Ocak"
        2 -> "Şubat"
        3 -> "Mart"
        4 -> "Nisan"
        5 -> "Mayıs"
        6 -> "Haziran"
        7 -> "Temmuz"
        8 -> "Ağustos"
        9 -> "Eylül"
        10 -> "Ekim"
        11 -> "Kasım"
        12 -> "Aralık"
        else -> ""
    }
    return "${date.dayOfMonth} $monthName ${date.year}"
}
