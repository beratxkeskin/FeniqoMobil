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
import androidx.compose.material3.Button
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
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.presentation.transaction.CustomSplitUiHelper
import com.feniqo.mobile.presentation.transaction.TransactionFormFieldError
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
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
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionCategoryOptionUiModel
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.MoneyFormatter
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.transaction_form_add_category_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_amount_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_apply_selection
import feniqomobil.sharedui.generated.resources.transaction_form_back_short
import feniqomobil.sharedui.generated.resources.transaction_form_categories_load_failed
import feniqomobil.sharedui.generated.resources.transaction_form_category
import feniqomobil.sharedui.generated.resources.transaction_form_clear
import feniqomobil.sharedui.generated.resources.transaction_form_currency_eur
import feniqomobil.sharedui.generated.resources.transaction_form_currency_gbp
import feniqomobil.sharedui.generated.resources.transaction_form_currency_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_currency_try
import feniqomobil.sharedui.generated.resources.transaction_form_currency_usd
import feniqomobil.sharedui.generated.resources.transaction_form_date
import feniqomobil.sharedui.generated.resources.transaction_form_deleted_category_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_existing_installment
import feniqomobil.sharedui.generated.resources.transaction_form_existing_installment_hint
import feniqomobil.sharedui.generated.resources.transaction_form_expense_name_example
import feniqomobil.sharedui.generated.resources.transaction_form_expense_selection_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_income_name_example
import feniqomobil.sharedui.generated.resources.transaction_form_income_selection_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_installment
import feniqomobil.sharedui.generated.resources.transaction_form_installment_amount_hint
import feniqomobil.sharedui.generated.resources.transaction_form_installment_count
import feniqomobil.sharedui.generated.resources.transaction_form_installment_count_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_installment_description
import feniqomobil.sharedui.generated.resources.transaction_form_installment_toggle_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_month_april
import feniqomobil.sharedui.generated.resources.transaction_form_month_august
import feniqomobil.sharedui.generated.resources.transaction_form_month_december
import feniqomobil.sharedui.generated.resources.transaction_form_month_february
import feniqomobil.sharedui.generated.resources.transaction_form_month_january
import feniqomobil.sharedui.generated.resources.transaction_form_month_july
import feniqomobil.sharedui.generated.resources.transaction_form_month_june
import feniqomobil.sharedui.generated.resources.transaction_form_month_march
import feniqomobil.sharedui.generated.resources.transaction_form_month_may
import feniqomobil.sharedui.generated.resources.transaction_form_month_november
import feniqomobil.sharedui.generated.resources.transaction_form_month_october
import feniqomobil.sharedui.generated.resources.transaction_form_month_september
import feniqomobil.sharedui.generated.resources.transaction_form_name
import feniqomobil.sharedui.generated.resources.transaction_form_name_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_new_category
import feniqomobil.sharedui.generated.resources.transaction_form_no_categories
import feniqomobil.sharedui.generated.resources.transaction_form_note
import feniqomobil.sharedui.generated.resources.transaction_form_note_placeholder
import feniqomobil.sharedui.generated.resources.transaction_form_note_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_payment_method
import feniqomobil.sharedui.generated.resources.transaction_form_retry
import feniqomobil.sharedui.generated.resources.transaction_form_search_category
import feniqomobil.sharedui.generated.resources.transaction_form_select_category
import feniqomobil.sharedui.generated.resources.transaction_form_select_date
import feniqomobil.sharedui.generated.resources.transaction_form_split_apply_remainder
import feniqomobil.sharedui.generated.resources.transaction_form_split_balanced
import feniqomobil.sharedui.generated.resources.transaction_form_split_custom
import feniqomobil.sharedui.generated.resources.transaction_form_split_custom_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_custom_status_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_description
import feniqomobil.sharedui.generated.resources.transaction_form_split_disable_installment
import feniqomobil.sharedui.generated.resources.transaction_form_split_distributed
import feniqomobil.sharedui.generated.resources.transaction_form_split_equal
import feniqomobil.sharedui.generated.resources.transaction_form_split_equal_hint
import feniqomobil.sharedui.generated.resources.transaction_form_split_equal_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_excess
import feniqomobil.sharedui.generated.resources.transaction_form_split_excess_fallback
import feniqomobil.sharedui.generated.resources.transaction_form_split_inactive_member
import feniqomobil.sharedui.generated.resources.transaction_form_split_installment_warning_hint
import feniqomobil.sharedui.generated.resources.transaction_form_split_installment_warning_title
import feniqomobil.sharedui.generated.resources.transaction_form_split_member_left
import feniqomobil.sharedui.generated.resources.transaction_form_split_members_loading
import feniqomobil.sharedui.generated.resources.transaction_form_split_members_loading_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_mode
import feniqomobil.sharedui.generated.resources.transaction_form_split_money_limit_exceeded
import feniqomobil.sharedui.generated.resources.transaction_form_split_money_limit_excess
import feniqomobil.sharedui.generated.resources.transaction_form_split_money_limit_remaining
import feniqomobil.sharedui.generated.resources.transaction_form_split_no_active_members
import feniqomobil.sharedui.generated.resources.transaction_form_split_numeric_limit_exceeded
import feniqomobil.sharedui.generated.resources.transaction_form_split_participant_selected_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_participant_unselected_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_participants
import feniqomobil.sharedui.generated.resources.transaction_form_split_payer
import feniqomobil.sharedui.generated.resources.transaction_form_split_payer_required
import feniqomobil.sharedui.generated.resources.transaction_form_split_payer_selection_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_remaining
import feniqomobil.sharedui.generated.resources.transaction_form_split_section_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_select_equal
import feniqomobil.sharedui.generated.resources.transaction_form_split_share_amount
import feniqomobil.sharedui.generated.resources.transaction_form_split_share_amount_semantics
import feniqomobil.sharedui.generated.resources.transaction_form_split_title
import feniqomobil.sharedui.generated.resources.transaction_form_split_total
import feniqomobil.sharedui.generated.resources.transactions_expense
import feniqomobil.sharedui.generated.resources.transactions_income
import org.jetbrains.compose.resources.stringResource

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
    val expenseDescription = stringResource(Res.string.transaction_form_expense_selection_semantics)
    val incomeDescription = stringResource(Res.string.transaction_form_income_selection_semantics)
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
                    contentDescription = expenseDescription
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small, horizontal = FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = stringResource(Res.string.transactions_expense),
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
                    contentDescription = incomeDescription
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small, horizontal = FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = stringResource(Res.string.transactions_income),
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
    fieldModifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var currencyExpanded by remember { mutableStateOf(false) }
    val amountDescription = stringResource(Res.string.transaction_form_amount_semantics)
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
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = fieldModifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .defaultMinSize(minWidth = 100.dp, minHeight = 56.dp)
                    .semantics {
                        contentDescription = amountDescription
                        errorText?.let { error(it) }
                    },
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
                Currency.TRY -> stringResource(Res.string.transaction_form_currency_try)
                Currency.USD -> stringResource(Res.string.transaction_form_currency_usd)
                Currency.EUR -> stringResource(Res.string.transaction_form_currency_eur)
                Currency.GBP -> stringResource(Res.string.transaction_form_currency_gbp)
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
            val currencyDescription = stringResource(Res.string.transaction_form_currency_semantics, curr.code)
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
                        contentDescription = currencyDescription
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
    val addCategoryDescription = stringResource(Res.string.transaction_form_add_category_semantics)
    val selected = availableCategories.find { it.id == selectedCategoryId }
    Surface(onClick = { open = true }, enabled = enabled, modifier = modifier.fillMaxWidth().semantics {
        errorText?.let { error(it) }
    },
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.defaultMinSize(minHeight = 80.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryTonalIcon(
                selected?.iconKey,
                ColorParser.parseHexColorOrNull(selected?.colorHex) ?: MaterialTheme.colorScheme.primary,
                containerSize = 48.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.transaction_form_category),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    selected?.name ?: stringResource(Res.string.transaction_form_select_category),
                    style = MaterialTheme.typography.titleMedium,
                )
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
        TextButton(onClick = { open = false }) { Text(stringResource(Res.string.transaction_form_back_short)) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.transaction_form_category),
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
                            text = stringResource(Res.string.transaction_form_clear),
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
                            contentDescription = addCategoryDescription
                        },
                ) {
                    Text(
                        text = stringResource(Res.string.transaction_form_new_category),
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
                        text = stringResource(Res.string.transaction_form_categories_load_failed),
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
                            text = stringResource(Res.string.transaction_form_retry),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        } else if (availableCategories.isEmpty()) {
            Text(
                text = stringResource(Res.string.transaction_form_no_categories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
            )
        } else {
            OutlinedTextField(
                value = categoryQuery,
                onValueChange = { categoryQuery = it },
                placeholder = { Text(stringResource(Res.string.transaction_form_search_category)) },
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
                    val categoryDescription = if (category.isHistorical) {
                        stringResource(Res.string.transaction_form_deleted_category_semantics, category.name)
                    } else {
                        category.name
                    }

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
                                contentDescription = categoryDescription
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
    fieldModifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = onDateClick, enabled = enabled,
            modifier = fieldModifier.fillMaxWidth().semantics {
                errorText?.let { error(it) }
            },
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.transaction_form_date),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        date?.let { formatDisplayDate(it, localizedMonthName(it.month)) }
                            ?: stringResource(Res.string.transaction_form_select_date),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
                Text(
                    stringResource(Res.string.transaction_form_payment_method),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(selectedMethod.toLocalizedText(), style = MaterialTheme.typography.titleMedium)
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
                    TextButton(onClick = { open = false }) {
                        Text(stringResource(Res.string.transaction_form_back_short))
                    }
                    Text(
                        stringResource(Res.string.transaction_form_payment_method),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    PaymentMethod.entries.forEach { method ->
                        Surface(onClick = { draft = method }, shape = RoundedCornerShape(16.dp),
                            color = if (draft == method) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (method) {
                                    PaymentMethod.CASH -> Icons.Outlined.Payments
                                    PaymentMethod.BANK_TRANSFER -> Icons.Outlined.AccountBalance
                                    PaymentMethod.OTHER -> Icons.Outlined.MoreHoriz
                                    else -> Icons.Outlined.CreditCard
                                }, null)
                                Text(
                                    method.toLocalizedText(),
                                    Modifier.weight(1f).padding(horizontal = 16.dp),
                                )
                                androidx.compose.material3.RadioButton(selected = draft == method, onClick = null)
                            }
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            onMethodChange(draft)
                            open = false
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                    ) {
                        Text(stringResource(Res.string.transaction_form_apply_selection))
                    }
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
    val installmentToggleDescription = stringResource(Res.string.transaction_form_installment_toggle_semantics)
    val installmentCountDescription = stringResource(Res.string.transaction_form_installment_count_semantics)
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
                        text = stringResource(Res.string.transaction_form_installment),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.transaction_form_installment_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = isInstallmentEnabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = installmentToggleDescription },
                )
            }

            if (isInstallmentEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = stringResource(Res.string.transaction_form_installment_amount_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )

                    OutlinedTextField(
                        value = installmentCountText,
                        onValueChange = onCountChange,
                        enabled = enabled,
                        isError = errorText != null,
                        label = { Text(stringResource(Res.string.transaction_form_installment_count)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics { contentDescription = installmentCountDescription },
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
                    text = stringResource(Res.string.transaction_form_existing_installment, installment.badgeText),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(Res.string.transaction_form_existing_installment_hint),
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
    fieldModifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val nameDescription = stringResource(Res.string.transaction_form_name_semantics)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(FeniqoRadius.Medium),
        color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.transaction_form_name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title, onValueChange = onTitleChange, enabled = enabled,
                    isError = errorText != null, singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(
                                if (isExpense) {
                                    Res.string.transaction_form_expense_name_example
                                } else {
                                    Res.string.transaction_form_income_name_example
                                },
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = keyboardActions,
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                    modifier = fieldModifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = nameDescription
                            errorText?.let { error(it) }
                        },
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
    fieldModifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val noteDescription = stringResource(Res.string.transaction_form_note_semantics)
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
                text = stringResource(Res.string.transaction_form_note),
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
            placeholder = { Text(stringResource(Res.string.transaction_form_note_placeholder)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            minLines = 2,
            maxLines = 4,
            modifier = fieldModifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = noteDescription
                    errorText?.let { error(it) }
                },
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

@Composable
private fun localizedMonthName(month: kotlinx.datetime.Month): String =
    stringResource(
        when (month) {
            kotlinx.datetime.Month.JANUARY -> Res.string.transaction_form_month_january
            kotlinx.datetime.Month.FEBRUARY -> Res.string.transaction_form_month_february
            kotlinx.datetime.Month.MARCH -> Res.string.transaction_form_month_march
            kotlinx.datetime.Month.APRIL -> Res.string.transaction_form_month_april
            kotlinx.datetime.Month.MAY -> Res.string.transaction_form_month_may
            kotlinx.datetime.Month.JUNE -> Res.string.transaction_form_month_june
            kotlinx.datetime.Month.JULY -> Res.string.transaction_form_month_july
            kotlinx.datetime.Month.AUGUST -> Res.string.transaction_form_month_august
            kotlinx.datetime.Month.SEPTEMBER -> Res.string.transaction_form_month_september
            kotlinx.datetime.Month.OCTOBER -> Res.string.transaction_form_month_october
            kotlinx.datetime.Month.NOVEMBER -> Res.string.transaction_form_month_november
            kotlinx.datetime.Month.DECEMBER -> Res.string.transaction_form_month_december
        },
    )

/**
 * Tarihi gün, ay adı ve yıl formatına dönüştürür. Varsayılan ay adı eski Türkçe saf test sözleşmesini korur.
 */
fun formatDisplayDate(
    date: LocalDate,
    monthName: String = defaultTurkishMonthName(date.month),
): String = "${date.dayOfMonth} $monthName ${date.year}"

private fun defaultTurkishMonthName(month: kotlinx.datetime.Month): String = when (month) {
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
    splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
    customSharesText: Map<EntityId, String> = emptyMap(),
    customShareErrors: Map<EntityId, TransactionFormFieldError> = emptyMap(),
    currency: Currency = Currency.TRY,
    amountText: String = "",
    isInstallmentOptionAvailable: Boolean = false,
    isInstallmentEnabled: Boolean = false,
    onToggleInstallment: ((Boolean) -> Unit)? = null,
    onSplitModeChanged: (TransactionSplitMode) -> Unit = {},
    onCustomShareChanged: (EntityId, String) -> Unit = { _, _ -> },
    onApplyPayerRemainder: () -> Unit = {},
) {
    val sectionDescription = stringResource(Res.string.transaction_form_split_section_semantics)
    val equalModeDescription = stringResource(Res.string.transaction_form_split_equal_semantics)
    val customModeDescription = stringResource(Res.string.transaction_form_split_custom_semantics)
    val membersLoadingDescription = stringResource(Res.string.transaction_form_split_members_loading_semantics)
    val customStatusDescription = stringResource(Res.string.transaction_form_split_custom_status_semantics)
    val applyRemainderDescription = stringResource(Res.string.transaction_form_split_apply_remainder)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = sectionDescription },
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
                    text = stringResource(Res.string.transaction_form_split_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.transaction_form_split_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isInstallmentEnabled && splitMode == TransactionSplitMode.CUSTOM) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Small),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    ) {
                        Text(
                            text = stringResource(Res.string.transaction_form_split_installment_warning_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(Res.string.transaction_form_split_installment_warning_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            modifier = Modifier.padding(top = FeniqoSpacing.ExtraSmall),
                            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            TextButton(
                                onClick = { onSplitModeChanged(TransactionSplitMode.EQUAL) },
                            ) {
                                Text(stringResource(Res.string.transaction_form_split_select_equal))
                            }
                            if (onToggleInstallment != null) {
                                TextButton(
                                    onClick = { onToggleInstallment(false) },
                                ) {
                                    Text(stringResource(Res.string.transaction_form_split_disable_installment))
                                }
                            }
                        }
                    }
                }
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
                            .semantics { contentDescription = membersLoadingDescription },
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(
                        text = stringResource(Res.string.transaction_form_split_members_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (members.isEmpty()) {
                Text(
                    text = stringResource(Res.string.transaction_form_split_no_active_members),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                // 1. Paylaşım Modu Seçimi (Eşit Paylaşım vs Özel Tutarlar)
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = stringResource(Res.string.transaction_form_split_mode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilterChip(
                            selected = splitMode == TransactionSplitMode.EQUAL,
                            onClick = { onSplitModeChanged(TransactionSplitMode.EQUAL) },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = stringResource(Res.string.transaction_form_split_equal),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (splitMode == TransactionSplitMode.EQUAL) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .semantics { contentDescription = equalModeDescription },
                        )
                        FilterChip(
                            selected = splitMode == TransactionSplitMode.CUSTOM,
                            onClick = { onSplitModeChanged(TransactionSplitMode.CUSTOM) },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = stringResource(Res.string.transaction_form_split_custom),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (splitMode == TransactionSplitMode.CUSTOM) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .semantics { contentDescription = customModeDescription },
                        )
                    }
                }

                // 2. Ödeyen Kişi Seçimi
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = stringResource(Res.string.transaction_form_split_payer),
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
                            val payerSelectionDescription = stringResource(
                                Res.string.transaction_form_split_payer_selection_semantics,
                                member.displayName,
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { onPaidByUserSelected(member.userId) },
                                enabled = enabled && member.isActive,
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
                                        contentDescription = payerSelectionDescription
                                    },
                            )
                        }
                    }
                }

                // 3. Katılımcılar Seçimi ve Dağıtım
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = stringResource(Res.string.transaction_form_split_participants),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    members.forEach { member ->
                        val isParticipant = member.userId in selectedParticipantUserIds
                        val isPayer = member.userId == selectedPaidByUserId
                        val participantDescription = stringResource(
                            if (isParticipant) {
                                Res.string.transaction_form_split_participant_selected_semantics
                            } else {
                                Res.string.transaction_form_split_participant_unselected_semantics
                            },
                            member.displayName,
                        )
                        val shareAmountDescription = stringResource(
                            Res.string.transaction_form_split_share_amount_semantics,
                            member.displayName,
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = FeniqoSpacing.ExtraSmall),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                        ) {
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
                                        contentDescription = participantDescription
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
                                        color = if (enabled && member.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                                ) {
                                    if (isPayer) {
                                        Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.transaction_form_split_payer_required),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (!member.isActive) {
                                        Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.errorContainer,
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.transaction_form_split_member_left),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // CUSTOM Modunda Her Katılımcı İçin Tutar Girişi
                            if (splitMode == TransactionSplitMode.CUSTOM && isParticipant) {
                                val shareError = customShareErrors[member.userId]
                                val displayError = shareError ?: if (!member.isActive) TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE else null
                                OutlinedTextField(
                                    value = customSharesText[member.userId] ?: "",
                                    onValueChange = { onCustomShareChanged(member.userId, it) },
                                    enabled = enabled,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    label = { Text(stringResource(Res.string.transaction_form_split_share_amount)) },
                                    trailingIcon = {
                                        Text(
                                            text = currency.symbol,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = FeniqoSpacing.Small),
                                        )
                                    },
                                    isError = displayError != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, bottom = FeniqoSpacing.ExtraSmall)
                                        .semantics {
                                            contentDescription = shareAmountDescription
                                        },
                                )
                                if (displayError != null) {
                                    Text(
                                        text = displayError.toLocalizedText(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 36.dp, bottom = FeniqoSpacing.ExtraSmall),
                                    )
                                }
                            }
                        }
                    }

                    if (splitMode == TransactionSplitMode.EQUAL) {
                        Text(
                            text = stringResource(Res.string.transaction_form_split_equal_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = FeniqoSpacing.ExtraSmall),
                        )
                    } else if (splitMode == TransactionSplitMode.CUSTOM) {
                        // CUSTOM Modu Dağıtım Durumu ve Kalan Aktarma Çubuğu
                        val activeMemberIds = remember(members) {
                            members.filter { it.isActive }.map { it.userId }.toSet()
                        }
                        val summary = remember(
                            amountText,
                            currency,
                            selectedPaidByUserId,
                            selectedParticipantUserIds,
                            customSharesText,
                            activeMemberIds,
                        ) {
                            CustomSplitUiHelper.calculateSplitSummary(
                                amountText = amountText,
                                currency = currency,
                                payer = selectedPaidByUserId,
                                participants = selectedParticipantUserIds,
                                customSharesText = customSharesText,
                                activeMembers = activeMemberIds,
                            )
                        }
                        val inactiveMemberText = summary.inactiveMemberMessage?.toLocalizedText()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = FeniqoSpacing.Small)
                                .semantics { contentDescription = customStatusDescription },
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
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
                                    Column {
                                        Text(
                                            text = stringResource(
                                                Res.string.transaction_form_split_total,
                                                summary.totalAmountMinor?.let { MoneyFormatter.format(Money(it, currency)) } ?: "-",
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val distributedDisplay = when {
                                            summary.isLongOverflow ->
                                                stringResource(Res.string.transaction_form_split_numeric_limit_exceeded)
                                            summary.isMoneyMaxExceeded ->
                                                stringResource(Res.string.transaction_form_split_money_limit_exceeded)
                                            else -> MoneyFormatter.format(Money(summary.distributedAmountMinor, currency))
                                        }
                                        Text(
                                            text = stringResource(
                                                Res.string.transaction_form_split_distributed,
                                                distributedDisplay,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        if (summary.isBalanced) {
                                            Text(
                                                text = stringResource(Res.string.transaction_form_split_balanced),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else if (summary.hasInactiveMember) {
                                            Text(
                                                text = stringResource(Res.string.transaction_form_split_inactive_member),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        } else if (summary.hasExcess) {
                                            val excessText = when {
                                                summary.isLongOverflow ->
                                                    stringResource(Res.string.transaction_form_split_numeric_limit_exceeded)
                                                summary.remainingAmountMinor != null && kotlin.math.abs(summary.remainingAmountMinor) > Money.MAX_AMOUNT_MINOR ->
                                                    stringResource(Res.string.transaction_form_split_money_limit_excess)
                                                summary.remainingAmountMinor != null ->
                                                    stringResource(
                                                        Res.string.transaction_form_split_excess,
                                                        MoneyFormatter.format(
                                                            Money(kotlin.math.abs(summary.remainingAmountMinor), currency),
                                                        ),
                                                    )
                                                else -> stringResource(Res.string.transaction_form_split_excess_fallback)
                                            }
                                            Text(
                                                text = excessText,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        } else if (summary.remainingAmountMinor != null && summary.remainingAmountMinor > 0) {
                                            val remainingText = when {
                                                summary.isLongOverflow ->
                                                    stringResource(Res.string.transaction_form_split_numeric_limit_exceeded)
                                                summary.remainingAmountMinor > Money.MAX_AMOUNT_MINOR ->
                                                    stringResource(Res.string.transaction_form_split_money_limit_remaining)
                                                else ->
                                                    stringResource(
                                                        Res.string.transaction_form_split_remaining,
                                                        MoneyFormatter.format(Money(summary.remainingAmountMinor, currency)),
                                                    )
                                            }
                                            Text(
                                                text = remainingText,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        } else if (summary.isOverflow) {
                                            Text(
                                                text = stringResource(
                                                    if (summary.isLongOverflow) {
                                                        Res.string.transaction_form_split_numeric_limit_exceeded
                                                    } else {
                                                        Res.string.transaction_form_split_money_limit_exceeded
                                                    },
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }

                                if (summary.hasInactiveMember && inactiveMemberText != null) {
                                    Text(
                                        text = inactiveMemberText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = FeniqoSpacing.ExtraSmall),
                                    )
                                }

                                Button(
                                    onClick = onApplyPayerRemainder,
                                    enabled = enabled && summary.canApplyPayerRemainder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = applyRemainderDescription },
                                ) {
                                    Text(stringResource(Res.string.transaction_form_split_apply_remainder))
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
