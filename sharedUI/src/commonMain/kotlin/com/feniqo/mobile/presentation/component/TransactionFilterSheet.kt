package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.transaction.CategoryFilterOptionUiModel
import com.feniqo.mobile.presentation.transaction.TransactionFilterUiModel
import com.feniqo.mobile.presentation.transaction.TransactionPeriodPreset
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.transactions_all
import feniqomobil.sharedui.generated.resources.transactions_all_categories
import feniqomobil.sharedui.generated.resources.transactions_all_payment_methods
import feniqomobil.sharedui.generated.resources.transactions_apply_filters
import feniqomobil.sharedui.generated.resources.transactions_cancel
import feniqomobil.sharedui.generated.resources.transactions_category_section
import feniqomobil.sharedui.generated.resources.transactions_clear
import feniqomobil.sharedui.generated.resources.transactions_date_range
import feniqomobil.sharedui.generated.resources.transactions_end_date
import feniqomobil.sharedui.generated.resources.transactions_filter_category_chip
import feniqomobil.sharedui.generated.resources.transactions_filter_category_fallback
import feniqomobil.sharedui.generated.resources.transactions_filter_clear_search
import feniqomobil.sharedui.generated.resources.transactions_filter_payment_chip
import feniqomobil.sharedui.generated.resources.transactions_filter_period_chip
import feniqomobil.sharedui.generated.resources.transactions_filter_remove_category
import feniqomobil.sharedui.generated.resources.transactions_filter_remove_payment
import feniqomobil.sharedui.generated.resources.transactions_filter_remove_period
import feniqomobil.sharedui.generated.resources.transactions_filter_remove_type
import feniqomobil.sharedui.generated.resources.transactions_filter_search
import feniqomobil.sharedui.generated.resources.transactions_filter_type_chip
import feniqomobil.sharedui.generated.resources.transactions_filters
import feniqomobil.sharedui.generated.resources.transactions_invalid_date_range
import feniqomobil.sharedui.generated.resources.transactions_payment_section
import feniqomobil.sharedui.generated.resources.transactions_period_all_time
import feniqomobil.sharedui.generated.resources.transactions_select_dates
import feniqomobil.sharedui.generated.resources.transactions_select_from_calendar
import feniqomobil.sharedui.generated.resources.transactions_sort_section
import feniqomobil.sharedui.generated.resources.transactions_start_date
import feniqomobil.sharedui.generated.resources.transactions_type_section
import org.jetbrains.compose.resources.stringResource

/**
 * Arama sorgusu giriş alanı bileşenidir.
 */
@Composable
fun TransactionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val clearSearchDescription = stringResource(Res.string.transactions_filter_clear_search)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        placeholder = {
            Text(
                text = stringResource(Res.string.transactions_filter_search),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { focusManager.clearFocus() },
            onDone = { focusManager.clearFocus() },
        ),
        trailingIcon = if (query.isNotBlank()) {
            {
                TextButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = clearSearchDescription },
                ) {
                    Text(
                        text = stringResource(Res.string.transactions_clear),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * Kullanıcının seçtiği aktif filtreleri gösteren ve tek tek kaldırılmasını sağlayan çip listesidir.
 * WorkspaceId asla çip olarak gösterilmez.
 */
@Composable
fun ActiveTransactionFilterChips(
    filter: TransactionFilterUiModel,
    availableCategories: List<CategoryFilterOptionUiModel>,
    onTypeFilterChanged: (TransactionType?) -> Unit,
    onCategoryFilterChanged: (EntityId?) -> Unit,
    onPaymentMethodFilterChanged: (PaymentMethod?) -> Unit,
    onPeriodPresetChanged: (TransactionPeriodPreset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filter.type != null) {
            val typeText = filter.type.toLocalizedText()
            val removeTypeDescription = stringResource(Res.string.transactions_filter_remove_type, typeText)
            InputChip(
                selected = true,
                onClick = { onTypeFilterChanged(null) },
                label = { Text(stringResource(Res.string.transactions_filter_type_chip, typeText)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = removeTypeDescription
                    },
            )
        }

        if (filter.categoryId != null) {
            val matchedCategory = availableCategories.find { it.id == filter.categoryId }
            val categoryLabel = matchedCategory?.name
                ?: stringResource(Res.string.transactions_filter_category_fallback)
            val removeDescription = stringResource(Res.string.transactions_filter_remove_category, categoryLabel)
            InputChip(
                selected = true,
                onClick = { onCategoryFilterChanged(null) },
                label = { Text(stringResource(Res.string.transactions_filter_category_chip, categoryLabel)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = removeDescription
                    },
            )
        }

        if (filter.paymentMethod != null) {
            val paymentText = filter.paymentMethod.toLocalizedText()
            val removePaymentDescription = stringResource(Res.string.transactions_filter_remove_payment, paymentText)
            InputChip(
                selected = true,
                onClick = { onPaymentMethodFilterChanged(null) },
                label = { Text(stringResource(Res.string.transactions_filter_payment_chip, paymentText)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = removePaymentDescription
                    },
            )
        }

        if (filter.periodPreset != null) {
            val periodText = filter.periodPreset.toLocalizedText()
            val removePeriodDescription = stringResource(Res.string.transactions_filter_remove_period, periodText)
            InputChip(
                selected = true,
                onClick = { onPeriodPresetChanged(null) },
                label = { Text(stringResource(Res.string.transactions_filter_period_chip, periodText)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = removePeriodDescription
                    },
            )
        }
    }
}

/**
 * İşlem listesi için kapsamlı filtreleme alt sayfasıdır (Bottom Sheet).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFilterSheet(
    filter: TransactionFilterUiModel,
    availableCategories: List<CategoryFilterOptionUiModel>,
    onTypeFilterChanged: (TransactionType?) -> Unit,
    onCategoryFilterChanged: (EntityId?) -> Unit,
    onPaymentMethodFilterChanged: (PaymentMethod?) -> Unit,
    onPeriodPresetChanged: (TransactionPeriodPreset?) -> Unit,
    onClearFilters: () -> Unit,
    onFilterDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSortOrderChanged: (com.feniqo.mobile.presentation.transaction.TransactionSortOrder) -> Unit = {},
    onCustomPeriodChanged: (com.feniqo.mobile.domain.model.ReportPeriod?) -> Unit = {},
) {
    var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(filter) }
    var showCalendar by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var start by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(filter.customPeriod?.startDate?.toString().orEmpty()) }
    var end by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(filter.customPeriod?.endDate?.toString().orEmpty()) }
    val custom = if (start.isBlank() && end.isBlank()) null else runCatching {
        com.feniqo.mobile.domain.model.ReportPeriod(
            com.feniqo.mobile.domain.model.LocalDate.parse(start), com.feniqo.mobile.domain.model.LocalDate.parse(end))
    }.getOrNull()
    val invalidRange = (start.isNotBlank() || end.isNotBlank()) && custom == null
    ModalBottomSheet(onDismissRequest = onFilterDismiss, modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = FeniqoSpacing.Large).padding(bottom = FeniqoSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.transactions_filters), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { draft = TransactionFilterUiModel(workspaceId = filter.workspaceId); start = ""; end = "" }) {
                    Text(stringResource(Res.string.transactions_clear))
                }
            }
            FilterSection(stringResource(Res.string.transactions_date_range)) {
                OutlinedButton(onClick = { showCalendar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.transactions_select_from_calendar))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.periodPreset == null && custom == null,
                        onClick = { draft = draft.copy(periodPreset = null, customPeriod = null); start = ""; end = "" },
                        label = { Text(stringResource(Res.string.transactions_period_all_time)) })
                    TransactionPeriodPreset.entries.forEach { preset ->
                        FilterChip(selected = draft.periodPreset == preset, onClick = {
                            draft = draft.copy(periodPreset = preset, customPeriod = null); start = ""; end = ""
                        }, label = { Text(preset.toLocalizedText()) })
                    }
                }
                OutlinedTextField(value = start, onValueChange = { start = it; draft = draft.copy(periodPreset = null) },
                    label = { Text(stringResource(Res.string.transactions_start_date)) }, singleLine = true, isError = invalidRange, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = end, onValueChange = { end = it; draft = draft.copy(periodPreset = null) },
                    label = { Text(stringResource(Res.string.transactions_end_date)) }, singleLine = true, isError = invalidRange, modifier = Modifier.fillMaxWidth())
                if (invalidRange) Text(stringResource(Res.string.transactions_invalid_date_range), color = MaterialTheme.colorScheme.error)
            }
            FilterSection(stringResource(Res.string.transactions_type_section)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                        FilterChip(selected = draft.type == type, onClick = { draft = draft.copy(type = type, categoryId = null) },
                            label = { Text(type?.toLocalizedText() ?: stringResource(Res.string.transactions_all)) })
                    }
                }
            }
            FilterSection(stringResource(Res.string.transactions_category_section)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.categoryId == null, onClick = { draft = draft.copy(categoryId = null) },
                        label = { Text(stringResource(Res.string.transactions_all_categories)) })
                    availableCategories.filter { draft.type == null || it.type == draft.type }.forEach { category ->
                        FilterChip(selected = draft.categoryId == category.id, onClick = { draft = draft.copy(categoryId = category.id) }, label = { Text(category.name) })
                    }
                }
            }
            FilterSection(stringResource(Res.string.transactions_payment_section)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<PaymentMethod?>(null) + PaymentMethod.entries).forEach { method ->
                        FilterChip(selected = draft.paymentMethod == method, onClick = { draft = draft.copy(paymentMethod = method) },
                            label = { Text(method?.toLocalizedText() ?: stringResource(Res.string.transactions_all_payment_methods)) })
                    }
                }
            }
            FilterSection(stringResource(Res.string.transactions_sort_section)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.feniqo.mobile.presentation.transaction.TransactionSortOrder.entries.forEach { order ->
                        FilterChip(selected = draft.sortOrder == order, onClick = { draft = draft.copy(sortOrder = order) }, label = { Text(order.toLocalizedText()) })
                    }
                }
            }
            Button(onClick = {
                onTypeFilterChanged(draft.type)
                onCategoryFilterChanged(draft.categoryId)
                onPaymentMethodFilterChanged(draft.paymentMethod)
                onPeriodPresetChanged(draft.periodPreset)
                if (custom != null) onCustomPeriodChanged(custom)
                onSortOrderChanged(draft.sortOrder)
                onFilterDismiss()
            }, enabled = !invalidRange, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
                Text(stringResource(Res.string.transactions_apply_filters))
            }
        }
    }
    if (showCalendar) {
        val calendar = androidx.compose.material3.rememberDateRangePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(enabled = calendar.selectedStartDateMillis != null && calendar.selectedEndDateMillis != null,
                    onClick = {
                        fun date(millis: Long) = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date.toString()
                        start = date(requireNotNull(calendar.selectedStartDateMillis))
                        end = date(requireNotNull(calendar.selectedEndDateMillis))
                        draft = draft.copy(periodPreset = null)
                        showCalendar = false
                    }) { Text(stringResource(Res.string.transactions_select_dates)) }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) {
                    Text(stringResource(Res.string.transactions_cancel))
                }
            },
        ) {
            androidx.compose.material3.DateRangePicker(state = calendar, modifier = Modifier.height(480.dp),
                title = { Text(stringResource(Res.string.transactions_date_range), Modifier.padding(16.dp)) })
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
/**
 * TransactionPeriodPreset enum değerlerinin kullanıcı dostu Türkçe karşılıkları.
 */
fun TransactionPeriodPreset.toDisplayText(): String = when (this) {
    TransactionPeriodPreset.TODAY -> "Bugün"
    TransactionPeriodPreset.THIS_WEEK -> "Bu Hafta"
    TransactionPeriodPreset.THIS_MONTH -> "Bu Ay"
    TransactionPeriodPreset.LAST_30_DAYS -> "Son 30 Gün"
    TransactionPeriodPreset.THIS_YEAR -> "Bu Yıl"
}
