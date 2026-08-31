package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ActiveTransactionFilterChips
import com.feniqo.mobile.presentation.component.InstallmentTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.SingleTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.TransactionDateGroupHeader
import com.feniqo.mobile.presentation.component.TransactionFilterSheet
import com.feniqo.mobile.presentation.component.TransactionListItem
import com.feniqo.mobile.presentation.component.TransactionSearchField
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.transaction.CategoryFilterOptionUiModel
import com.feniqo.mobile.presentation.transaction.DateGroupedTransactionsDisplayModel
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionDeleteDialogState
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionFilterUiModel
import com.feniqo.mobile.presentation.transaction.TransactionPeriodPreset
import com.feniqo.mobile.presentation.transaction.TransactionsUiState

/**
 * İşlem listesi ekranının durumsuz (stateless) ana Compose sunumudur.
 * Yalnızca UI state'i ve etkileşim callback'lerini tüketir; ViewModel veya platform bağımlılığı içermez.
 */
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    modifier: Modifier = Modifier,
    canAddTransaction: Boolean = false,
    canEditTransaction: Boolean = false,
    onSearchQueryChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    onFilterDismiss: () -> Unit,
    onTypeFilterChanged: (TransactionType?) -> Unit,
    onCategoryFilterChanged: (EntityId?) -> Unit,
    onPaymentMethodFilterChanged: (PaymentMethod?) -> Unit,
    onPeriodPresetChanged: (TransactionPeriodPreset?) -> Unit,
    onClearFilters: () -> Unit,
    onDeleteClicked: (TransactionDisplayModel) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmSingleDelete: () -> Unit,
    onConfirmInstallmentDelete: (InstallmentDeleteScope) -> Unit,
    onRetryObservation: () -> Unit,
    onAddTransactionClick: () -> Unit = {},
    onTransactionClick: (TransactionDisplayModel) -> Unit = {},
) {
    val visibleFilterCount = state.filter.visibleFilterCount()

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

            // 1. Üst Kalıcı Başlık ve İşlem Ekle Aksiyon Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "İşlemler",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (canAddTransaction) {
                    Button(
                        onClick = onAddTransactionClick,
                        enabled = !state.isDeleteInProgress,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics { contentDescription = "Yeni işlem ekle" },
                    ) {
                        Text(
                            text = "İşlem Ekle",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Arama ve Filtre Butonu Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransactionSearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    modifier = Modifier.weight(1f),
                )

                OutlinedButton(
                    onClick = onFilterClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                ) {
                    val filterLabel = if (visibleFilterCount > 0) {
                        "Filtrele ($visibleFilterCount)"
                    } else {
                        "Filtrele"
                    }
                    Text(text = filterLabel)
                }
            }

            // Aktif Filtre Çipleri
            if (visibleFilterCount > 0) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                ActiveTransactionFilterChips(
                    filter = state.filter,
                    availableCategories = state.availableCategories,
                    onTypeFilterChanged = onTypeFilterChanged,
                    onCategoryFilterChanged = onCategoryFilterChanged,
                    onPaymentMethodFilterChanged = onPaymentMethodFilterChanged,
                    onPeriodPresetChanged = onPeriodPresetChanged,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // Durum Önceliklerine Göre İçerik
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    // 1. Yükleniyor Durumu
                    state.isLoading -> {
                        TransactionsLoadingContent(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    // 2. Gözlem Hata Durumu
                    state.observationError != null -> {
                        TransactionsErrorContent(
                            message = state.observationError.toDisplayText(),
                            onRetry = onRetryObservation,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    // 3. Gerçek Boş Liste (Arama ve filtre yokken)
                    state.groupedItems.isEmpty() && state.searchQuery.isBlank() && visibleFilterCount == 0 -> {
                        TransactionsEmptyContent(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    // 4. Arama/Filtre Sonucu Boş
                    state.groupedItems.isEmpty() -> {
                        TransactionsSearchEmptyContent(
                            onClearFilters = onClearFilters,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    // 5. Dolu Liste
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            contentPadding = PaddingValues(bottom = FeniqoSpacing.Large),
                        ) {
                            state.groupedItems.forEach { group ->
                                item(key = "header_${group.date}") {
                                    TransactionDateGroupHeader(
                                        formattedDate = group.formattedDate,
                                    )
                                }

                                items(
                                    items = group.items,
                                    key = { "item_${it.id.value}" },
                                ) { item ->
                                    TransactionListItem(
                                        item = item,
                                        canEditTransaction = canEditTransaction,
                                        isDeleteInProgress = state.isDeleteInProgress,
                                        onTransactionClick = onTransactionClick,
                                        onDeleteClicked = onDeleteClicked,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Filtre Alt Sayfası
    if (state.isFilterExpanded) {
        TransactionFilterSheet(
            filter = state.filter,
            availableCategories = state.availableCategories,
            onTypeFilterChanged = onTypeFilterChanged,
            onCategoryFilterChanged = onCategoryFilterChanged,
            onPaymentMethodFilterChanged = onPaymentMethodFilterChanged,
            onPeriodPresetChanged = onPeriodPresetChanged,
            onClearFilters = onClearFilters,
            onFilterDismiss = onFilterDismiss,
        )
    }

    // Silme Onay Diyalogları
    when (val dialog = state.deleteDialog) {
        is TransactionDeleteDialogState.Single -> {
            SingleTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = onConfirmSingleDelete,
                onDismiss = onDismissDeleteDialog,
            )
        }
        is TransactionDeleteDialogState.Installment -> {
            InstallmentTransactionDeleteDialog(
                dialog = dialog,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = onConfirmInstallmentDelete,
                onDismiss = onDismissDeleteDialog,
            )
        }
        null -> Unit
    }
}

/**
 * Kullanıcı tarafından seçilmiş olan görsel filtrelerin sayısını hesaplar.
 */
private fun TransactionFilterUiModel.visibleFilterCount(): Int {
    var count = 0
    if (type != null) count++
    if (categoryId != null) count++
    if (paymentMethod != null) count++
    if (periodPreset != null) count++
    return count
}

@Composable
private fun TransactionsLoadingContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        CircularProgressIndicator()
        Text(
            text = "İşlemler yükleniyor…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionsErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Text("Yeniden Dene")
        }
    }
}

@Composable
private fun TransactionsEmptyContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = "Henüz işlem yok",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Kayıtlı finansal hareketiniz bulunmuyor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionsSearchEmptyContent(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = "Sonuç bulunamadı",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Arama veya filtrelerinizi değiştirmeyi deneyin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
        OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Text("Filtreleri Temizle")
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun TransactionsScreenLoadingPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(isLoading = true),
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenEmptyPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(isLoading = false, groupedItems = emptyList()),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenListLightPreview() {
    val sampleItems = createSampleGroupedItems()
    FeniqoTheme(darkTheme = false) {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = sampleItems,
                    availableCategories = listOf(
                        CategoryFilterOptionUiModel(
                            id = EntityId("cat-1"),
                            name = "Market",
                            type = TransactionType.EXPENSE,
                            colorHex = "#10B981",
                        ),
                    ),
                ),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenListDarkPreview() {
    val sampleItems = createSampleGroupedItems()
    FeniqoTheme(darkTheme = true) {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = sampleItems,
                ),
                canAddTransaction = true,
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun TransactionsScreenObservationErrorPreview() {
    FeniqoTheme {
        Surface {
            TransactionsScreen(
                state = TransactionsUiState(
                    isLoading = false,
                    groupedItems = emptyList(),
                    observationError = FinanceUiMessage.NETWORK_ERROR,
                ),
                onSearchQueryChanged = {},
                onFilterClick = {},
                onFilterDismiss = {},
                onTypeFilterChanged = {},
                onCategoryFilterChanged = {},
                onPaymentMethodFilterChanged = {},
                onPeriodPresetChanged = {},
                onClearFilters = {},
                onDeleteClicked = {},
                onDismissDeleteDialog = {},
                onConfirmSingleDelete = {},
                onConfirmInstallmentDelete = {},
                onRetryObservation = {},
            )
        }
    }
}

@Preview
@Composable
fun InstallmentDeleteDialogPreview() {
    val targetItem = TransactionDisplayModel(
        id = EntityId("trx-inst"),
        amount = Money(300000L, Currency.TRY),
        formattedAmount = "-3.000,00 ₺",
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-elec"),
        categoryName = "Elektronik",
        categoryColorHex = "#3B82F6",
        categoryIconKey = null,
        description = "Laptop Taksiti",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate(2026, 8, 23),
        installment = InstallmentDisplayModel(number = 1, total = 3, badgeText = "1/3"),
        hasReceipt = true,
    )
    FeniqoTheme {
        Surface {
            InstallmentTransactionDeleteDialog(
                dialog = TransactionDeleteDialogState.Installment(target = targetItem),
                isDeleteInProgress = false,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

private fun createSampleGroupedItems(): List<DateGroupedTransactionsDisplayModel> {
    return listOf(
        DateGroupedTransactionsDisplayModel(
            date = LocalDate(2026, 8, 23),
            formattedDate = "Bugün",
            items = listOf(
                TransactionDisplayModel(
                    id = EntityId("t1"),
                    amount = Money(15000L, Currency.TRY),
                    formattedAmount = "-150,00 ₺",
                    type = TransactionType.EXPENSE,
                    categoryId = EntityId("c1"),
                    categoryName = "Market",
                    categoryColorHex = "#10B981",
                    categoryIconKey = null,
                    description = "Haftalık mutfak alışverişi",
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    transactionDate = LocalDate(2026, 8, 23),
                    installment = null,
                    hasReceipt = true,
                ),
                TransactionDisplayModel(
                    id = EntityId("t2"),
                    amount = Money(500000L, Currency.TRY),
                    formattedAmount = "+5.000,00 ₺",
                    type = TransactionType.INCOME,
                    categoryId = EntityId("c2"),
                    categoryName = "Maaş",
                    categoryColorHex = "#059669",
                    categoryIconKey = null,
                    description = "Ağustos Ek Ödeme",
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    transactionDate = LocalDate(2026, 8, 23),
                    installment = null,
                    hasReceipt = false,
                ),
            ),
        ),
        DateGroupedTransactionsDisplayModel(
            date = LocalDate(2026, 8, 22),
            formattedDate = "Dün",
            items = listOf(
                TransactionDisplayModel(
                    id = EntityId("t3"),
                    amount = Money(120000L, Currency.TRY),
                    formattedAmount = "-1.200,00 ₺",
                    type = TransactionType.EXPENSE,
                    categoryId = EntityId("c3"),
                    categoryName = "Teknoloji",
                    categoryColorHex = "#3B82F6",
                    categoryIconKey = null,
                    description = "Monitör Alımı",
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    transactionDate = LocalDate(2026, 8, 22),
                    installment = InstallmentDisplayModel(number = 1, total = 3, badgeText = "1/3"),
                    hasReceipt = false,
                ),
            ),
        ),
    )
}
