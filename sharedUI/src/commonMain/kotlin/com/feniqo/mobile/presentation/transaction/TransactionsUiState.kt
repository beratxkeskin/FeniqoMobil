package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * İşlem listesi için dönem filtresi ön ayarları.
 */
enum class TransactionPeriodPreset {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    LAST_30_DAYS,
    THIS_YEAR,
}

/**
 * Filtreleme menüsünde gösterilecek kategori seçeneği UI modelidir.
 */
data class CategoryFilterOptionUiModel(
    val id: EntityId,
    val name: String,
    val type: TransactionType,
    val colorHex: String?,
)

/**
 * Silme onay diyaloglarının kapalı durum modelidir.
 * Single ve Installment diyaloglarının aynı anda açık olmasını imkânsız kılar.
 */
sealed interface TransactionDeleteDialogState {
    data class Single(
        val target: TransactionDisplayModel,
    ) : TransactionDeleteDialogState

    data class Installment(
        val target: TransactionDisplayModel,
    ) : TransactionDeleteDialogState
}

/**
 * İşlem listesi ekranı UI durum modelidir.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val groupedItems: List<DateGroupedTransactionsDisplayModel> = emptyList(),
    val searchQuery: String = "",
    val filter: TransactionFilterUiModel = TransactionFilterUiModel(),
    val isFilterExpanded: Boolean = false,
    val userMessage: FinanceUiMessage? = null,
    val deleteDialog: TransactionDeleteDialogState? = null,
    val isDeleteInProgress: Boolean = false,
    val availableCategories: List<CategoryFilterOptionUiModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
)

/**
 * Tarihe göre gruplanmış işlem kümesi display modelidir.
 */
data class DateGroupedTransactionsDisplayModel(
    val date: LocalDate,
    val formattedDate: String,
    val items: List<TransactionDisplayModel>,
)

/**
 * Tekil işlem satırı display modelidir.
 */
data class TransactionDisplayModel(
    val id: EntityId,
    val amount: Money,
    val formattedAmount: String,
    val type: TransactionType,
    val categoryId: EntityId,
    val categoryName: String,
    val categoryColorHex: String?, // Kategori bulunamazsa null
    val categoryIconKey: String?,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val transactionDate: LocalDate,
    val installment: InstallmentDisplayModel?,
    val hasReceipt: Boolean,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
)

/**
 * Taksitli işlem rozeti display modelidir.
 */
data class InstallmentDisplayModel(
    val number: Int,
    val total: Int,
    val badgeText: String, // "1/3"
)

/**
 * UI filtreleme seçim durumudur.
 */
data class TransactionFilterUiModel(
    val type: TransactionType? = null,
    val categoryId: EntityId? = null,
    val paymentMethod: PaymentMethod? = null,
    val periodPreset: TransactionPeriodPreset? = null,
    val workspaceId: EntityId? = null,
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (type != null) count++
            if (categoryId != null) count++
            if (paymentMethod != null) count++
            if (periodPreset != null) count++
            if (workspaceId != null) count++
            return count
        }
}
