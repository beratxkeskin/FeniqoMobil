package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionCategoryOptionUiModel
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.MoneyFormatter
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel

/**
 * İşlem türü seçicisi bileşenidir (Gider / Gelir).
 * Sıcak-lüks segment tasarımıyla aktif türü ve açıklayıcı alt metinleri gösterir.
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

        // Gider Segmenti
        Surface(
            selected = isExpense,
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            enabled = enabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isExpense) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .semantics {
                    role = Role.Tab
                    contentDescription = "Gider seçimi, Harcamalar ve gider takibi"
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small, horizontal = FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isExpense) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isExpense) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

            }
        }

        // Gelir Segmenti
        Surface(
            selected = isIncome,
            onClick = { onTypeChange(TransactionType.INCOME) },
            enabled = enabled,
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = if (isIncome) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .semantics {
                    role = Role.Tab
                    contentDescription = "Gelir seçimi, Maaş, serbest gelir ve ek kazanç"
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small, horizontal = FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = "Gelir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isIncome) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isIncome) {
                        MaterialTheme.colorScheme.onPrimary
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
 * Büyük ve belirgin serif/tabular gösterim sunar; yazım esnasında agresif yeniden biçimlendirme yapmaz.
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
    var currencyExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        val amountStyle = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 40.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, fontFeatureSettings = "tnum",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(MoneyFormatter.getCurrencySymbol(currency), style = amountStyle)
            BasicTextField(
                value = amountText,
                onValueChange = { input -> onAmountChange(input.filter { it.isDigit() || it == ',' || it == '.' }) },
                enabled = enabled,
                singleLine = true,
                textStyle = amountStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .defaultMinSize(minWidth = 100.dp, minHeight = 56.dp)
                    .semantics { contentDescription = "İşlem tutarı girişi" },
                decorationBox = { field ->
                    Box {
                        if (amountText.isEmpty()) Text("0,00", style = amountStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant))
                        field()
                    }
                },
            )
        }
        TextButton(onClick = { currencyExpanded = !currencyExpanded }, enabled = enabled) {
            Text(when (currency) {
                Currency.TRY -> "Türk Lirası (TRY)"
                Currency.USD -> "Amerikan Doları (USD)"
                Currency.EUR -> "Euro (EUR)"
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
        }
        if (currencyExpanded) CurrencySelector(currency, {
            onCurrencyChange(it)
            currencyExpanded = false
        }, enabled)
        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
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
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
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
    var open by remember { mutableStateOf(false) }
    var categoryQuery by remember { mutableStateOf("") }
    val selected = availableCategories.find { it.id == selectedCategoryId }
    Surface(onClick = { open = true }, enabled = enabled, modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.defaultMinSize(minHeight = 80.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryTonalIcon(selected?.iconKey, ColorParser.parseHexColorOrNull(selected?.colorHex) ?: MaterialTheme.colorScheme.primary, containerSize = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("Kategori", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.name ?: "Kategori seç", style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, null)
        }
    }
    if (errorText != null) Text(errorText, color = MaterialTheme.colorScheme.error)
    if (open) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        TextButton(onClick = { open = false }) { Text("Geri") }
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
                    onClick = { open = false; onAddCategoryClick() },
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
            OutlinedTextField(
                value = categoryQuery,
                onValueChange = { categoryQuery = it },
                placeholder = { Text("Kategori ara") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                availableCategories.filter { it.name.contains(categoryQuery, ignoreCase = true) }.forEach { category ->
                    val isSelected = selectedCategoryId == category.id
                    val color = ColorParser.parseHexColorOrNull(category.colorHex)

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (category.isSelectable) {
                                onCategoryChange(category.id); open = false
                            }
                        },
                        enabled = enabled && category.isSelectable,
                        leadingIcon = {
                            CategoryTonalIcon(
                                iconKey = category.iconKey,
                                color = color ?: MaterialTheme.colorScheme.primary,
                                containerSize = 48.dp,
                            )
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = onDateClick, enabled = enabled,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tarih", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date?.let(::formatDisplayDate) ?: "Tarih seçin",
                        style = MaterialTheme.typography.titleMedium)
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
        }
        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
    }
}
/**
 * Ödeme yöntemi seçici bileşenidir.
 * Tüm 5 ödeme yöntemini (Nakit, Kredi Kartı, Banka Kartı, Havale/EFT, Diğer) semantik ikonlarıyla sunar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionPaymentMethodSelector(
    selectedMethod: PaymentMethod,
    onMethodChange: (PaymentMethod) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember(selectedMethod) { mutableStateOf(selectedMethod) }
    Surface(onClick = { draft = selectedMethod; open = true }, enabled = enabled,
        modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.defaultMinSize(minHeight = 80.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.CreditCard, null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("Ödeme yöntemi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedMethod.toDisplayText(), style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, null)
        }
    }
    if (open) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { open = false }) { Text("Geri") }
                    Text("Ödeme yöntemi", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    PaymentMethod.entries.forEach { method ->
                        Surface(onClick = { draft = method }, shape = RoundedCornerShape(16.dp),
                            color = if (draft == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (method) {
                                    PaymentMethod.CASH -> Icons.Outlined.Payments
                                    PaymentMethod.BANK_TRANSFER -> Icons.Outlined.AccountBalance
                                    PaymentMethod.OTHER -> Icons.Outlined.MoreHoriz
                                    else -> Icons.Outlined.CreditCard
                                }, null)
                                Text(method.toDisplayText(), Modifier.weight(1f).padding(horizontal = 16.dp))
                                androidx.compose.material3.RadioButton(selected = draft == method, onClick = null)
                            }
                        }
                    }
                    androidx.compose.material3.Button(onClick = { onMethodChange(draft); open = false },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) { Text("Seçimi uygula") }
                }
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
 * İşlem Adı (zorunlu) giriş alanı bileşenidir.
 */
@Composable
fun TransactionTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    errorText: String?,
    enabled: Boolean,
    isExpense: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(FeniqoRadius.Medium),
        color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("İşlem adı", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title, onValueChange = onTitleChange, enabled = enabled,
                    isError = errorText != null, singleLine = true,
                    placeholder = { Text(if (isExpense) "Örn: Market alışverişi" else "Örn: Maaş") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .semantics { contentDescription = "İşlem Adı" },
                )
                if (errorText != null) Text(errorText, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (title.length >= 90) Text("${title.length}/100",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
/**
 * Not / Ek Açıklama (isteğe bağlı) giriş alanı bileşenidir.
 */
@Composable
fun TransactionNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
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
                text = "Not / Açıklama (İsteğe Bağlı)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "${note.length}/500",
                style = MaterialTheme.typography.labelSmall,
                color = if (note.length > 500) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
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
                .semantics { contentDescription = "İşlem notu" },
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
    TransactionNoteField(
        note = description,
        onNoteChange = onDescriptionChange,
        errorText = errorText,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * Tarihi Türkçe gün ay yıl formatına dönüştürür.
 */
fun formatDisplayDate(date: LocalDate): String {
    val monthName = when (date.month) {
        kotlinx.datetime.Month.JANUARY -> "Ocak"
        kotlinx.datetime.Month.FEBRUARY -> "Şubat"
        kotlinx.datetime.Month.MARCH -> "Mart"
        kotlinx.datetime.Month.APRIL -> "Nisan"
        kotlinx.datetime.Month.MAY -> "Mayıs"
        kotlinx.datetime.Month.JUNE -> "Haziran"
        kotlinx.datetime.Month.JULY -> "Temmuz"
        kotlinx.datetime.Month.AUGUST -> "Ağustos"
        kotlinx.datetime.Month.SEPTEMBER -> "Eylül"
        kotlinx.datetime.Month.OCTOBER -> "Ekim"
        kotlinx.datetime.Month.NOVEMBER -> "Kasım"
        kotlinx.datetime.Month.DECEMBER -> "Aralık"
        else -> ""
    }
    return "${date.dayOfMonth} $monthName ${date.year}"
}

/**
 * Ortak gider paylaşımı (kim ödedi, kimler katılıyor) seçim bileşenidir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionSplitSection(
    members: List<WorkspaceMemberUiModel>,
    isLoading: Boolean,
    selectedPaidByUserId: EntityId?,
    selectedParticipantUserIds: Set<EntityId>,
    onPaidByUserSelected: (EntityId) -> Unit,
    onParticipantToggled: (EntityId) -> Unit,
    errorText: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Gider Paylaşımı Bölümü" },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                Text(
                    text = "Gider Paylaşımı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Harcamayı kimin ödediğini ve paylaşıma kimlerin dahil olduğunu belirleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FeniqoSpacing.Medium),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = "Üyeler yükleniyor" },
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(
                        text = "Çalışma alanı üyeleri yükleniyor...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (members.isEmpty()) {
                Text(
                    text = "Çalışma alanında aktif üye bulunamadı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                // 1. Ödeyen Kişi Seçimi
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = "Ödeyen Kişi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        members.forEach { member ->
                            val isSelected = member.userId == selectedPaidByUserId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onPaidByUserSelected(member.userId) },
                                enabled = enabled,
                                label = {
                                    Text(
                                        text = member.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .semantics {
                                        contentDescription = "${member.displayName} ödedi seçimi"
                                    },
                            )
                        }
                    }
                }

                // 2. Katılımcılar Seçimi
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = "Katılımcılar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    members.forEach { member ->
                        val isParticipant = member.userId in selectedParticipantUserIds
                        val isPayer = member.userId == selectedPaidByUserId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(FeniqoRadius.Small))
                                .clickable(
                                    enabled = enabled && !isPayer,
                                    role = Role.Checkbox,
                                    onClick = { onParticipantToggled(member.userId) },
                                )
                                .padding(vertical = FeniqoSpacing.Small, horizontal = FeniqoSpacing.ExtraSmall)
                                .semantics {
                                    contentDescription = "${member.displayName} katılımcı: ${if (isParticipant) "seçili" else "seçili değil"}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                modifier = Modifier.weight(1f),
                            ) {
                                Checkbox(
                                    checked = isParticipant,
                                    onCheckedChange = if (enabled && !isPayer) {
                                        { onParticipantToggled(member.userId) }
                                    } else null,
                                    enabled = enabled && !isPayer,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = member.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }

                            if (isPayer) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(start = FeniqoSpacing.Small),
                                ) {
                                    Text(
                                        text = "Ödeyen (Zorunlu)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                                    )
                                }
                            }
                        }
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
}
