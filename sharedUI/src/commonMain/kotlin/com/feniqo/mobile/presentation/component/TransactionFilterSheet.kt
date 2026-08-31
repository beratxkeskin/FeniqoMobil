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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.feniqo.mobile.presentation.util.ColorParser

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

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        placeholder = {
            Text(
                text = "İşlemlerde ara",
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
                        .semantics { contentDescription = "Aramayı temizle" },
                ) {
                    Text(
                        text = "Temizle",
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
            val typeText = filter.type.toDisplayText()
            InputChip(
                selected = true,
                onClick = { onTypeFilterChanged(null) },
                label = { Text("Tür: $typeText") },
                trailingIcon = {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "$typeText türü filtresini kaldır"
                    },
            )
        }

        if (filter.categoryId != null) {
            val matchedCategory = availableCategories.find { it.id == filter.categoryId }
            val categoryLabel = matchedCategory?.name ?: "Kategori"
            val removeDescription = if (matchedCategory != null) {
                "${matchedCategory.name} kategori filtresini kaldır"
            } else {
                "Kategori filtresini kaldır"
            }
            InputChip(
                selected = true,
                onClick = { onCategoryFilterChanged(null) },
                label = { Text("Kategori: $categoryLabel") },
                trailingIcon = {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp),
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
            val paymentText = filter.paymentMethod.toDisplayText()
            InputChip(
                selected = true,
                onClick = { onPaymentMethodFilterChanged(null) },
                label = { Text("Ödeme: $paymentText") },
                trailingIcon = {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "$paymentText ödeme filtresini kaldır"
                    },
            )
        }

        if (filter.periodPreset != null) {
            val periodText = filter.periodPreset.toDisplayText()
            InputChip(
                selected = true,
                onClick = { onPeriodPresetChanged(null) },
                label = { Text("Dönem: $periodText") },
                trailingIcon = {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        contentDescription = "$periodText dönem filtresini kaldır"
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
) {
    ModalBottomSheet(
        onDismissRequest = onFilterDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large)
                .padding(bottom = FeniqoSpacing.ExtraLarge)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Text(
                text = "Filtrele",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // 1. İşlem Türü
            Text(
                text = "İşlem Türü",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                FilterChip(
                    selected = filter.type == null,
                    onClick = { onTypeFilterChanged(null) },
                    label = { Text("Tümü") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
                FilterChip(
                    selected = filter.type == TransactionType.EXPENSE,
                    onClick = {
                        onTypeFilterChanged(
                            if (filter.type == TransactionType.EXPENSE) null else TransactionType.EXPENSE,
                        )
                    },
                    label = { Text("Gider") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
                FilterChip(
                    selected = filter.type == TransactionType.INCOME,
                    onClick = {
                        onTypeFilterChanged(
                            if (filter.type == TransactionType.INCOME) null else TransactionType.INCOME,
                        )
                    },
                    label = { Text("Gelir") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
            }

            // 2. Kategori
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (availableCategories.isEmpty()) {
                Text(
                    text = "Kullanılabilir kategori bulunamadı.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                ) {
                    FilterChip(
                        selected = filter.categoryId == null,
                        onClick = { onCategoryFilterChanged(null) },
                        label = { Text("Tümü") },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                    availableCategories.forEach { category ->
                        val isSelected = filter.categoryId == category.id
                        val color = ColorParser.parseHexColorOrNull(category.colorHex)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCategoryFilterChanged(if (isSelected) null else category.id)
                            },
                            label = { Text(category.name) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            leadingIcon = if (color != null) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(color, CircleShape),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // 3. Ödeme Yöntemi
            Text(
                text = "Ödeme Yöntemi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                FilterChip(
                    selected = filter.paymentMethod == null,
                    onClick = { onPaymentMethodFilterChanged(null) },
                    label = { Text("Tümü") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
                PaymentMethod.entries.forEach { method ->
                    val isSelected = filter.paymentMethod == method
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPaymentMethodFilterChanged(if (isSelected) null else method)
                        },
                        label = { Text(method.toDisplayText()) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                }
            }

            // 4. Dönem
            Text(
                text = "Dönem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                FilterChip(
                    selected = filter.periodPreset == null,
                    onClick = { onPeriodPresetChanged(null) },
                    label = { Text("Tümü") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
                TransactionPeriodPreset.entries.forEach { preset ->
                    val isSelected = filter.periodPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPeriodPresetChanged(if (isSelected) null else preset)
                        },
                        label = { Text(preset.toDisplayText()) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // Aksiyon Butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Filtreleri Temizle")
                }
                Button(
                    onClick = onFilterDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Kapat")
                }
            }
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
