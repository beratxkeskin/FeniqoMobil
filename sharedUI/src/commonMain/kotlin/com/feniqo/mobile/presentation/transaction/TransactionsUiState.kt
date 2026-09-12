package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReportPeriod
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
    THIS_YEAR;

    fun toLabel(): String = when (this) {
        THIS_MONTH -> "Bu ay"
        THIS_WEEK -> "Bu hafta"
        TODAY -> "Bugün"
        LAST_30_DAYS -> "Son 30 gün"
        THIS_YEAR -> "Bu yıl"
    }
}

fun TransactionPeriodPreset?.toLabel(): String = this?.toLabel() ?: "Tüm zamanlar"

/**
 * İşlem listesi için sıralama seçenekleri.
 */
enum class TransactionSortOrder {
    NEWEST,
    OLDEST,
    AMOUNT_DESC,
    AMOUNT_ASC;

    fun toDisplayText(): String = when (this) {
        NEWEST -> "En yeni"
        OLDEST -> "En eski"
        AMOUNT_DESC -> "Tutar: Azalan"
        AMOUNT_ASC -> "Tutar: Artan"
    }
}

/**
 * Günlük mini sütun grafik çubuğu UI modelidir.
 * Finansal hesaplarda float kullanılmaz; oranlar baz puan (0..10_000 bps) olarak taşınır.
 */
data class DailyTransactionBarUiModel(
    val date: LocalDate,
    val dayLabel: String,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val heightRatioBps: Int,
    val isDominantIncome: Boolean,
)

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
 * İşlemler ekranının üst kısmındaki özet istatistik kartları display modelidir.
 */
data class TransactionSummaryUiModel(
    val totalSpendingFormatted: String = "₺0,00",
    val totalIncomeFormatted: String = "₺0,00",
    val netFormatted: String = "₺0,00",
    val isNetPositive: Boolean = true,
    val transactionCount: Int = 0,
    val dateRangeText: String = "",
    val periodTitle: String = "Bu Ay",
    val dailyBars: List<DailyTransactionBarUiModel> = emptyList(),
)

/**
 * İşlem listesi ekranı UI durum modelidir.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val groupedItems: List<DateGroupedTransactionsDisplayModel> = emptyList(),
    val summary: TransactionSummaryUiModel = TransactionSummaryUiModel(),
    val searchQuery: String = "",
    val filter: TransactionFilterUiModel = TransactionFilterUiModel(),
    val isFilterExpanded: Boolean = false,
    val activeWorkspaceName: String? = null,
    val userMessage: FinanceUiMessage? = null,
    val deleteDialog: TransactionDeleteDialogState? = null,
    val isDeleteInProgress: Boolean = false,
    val availableCategories: List<CategoryFilterOptionUiModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
) {
    /**
     * Hızlı dönem filtre hapında gösterilecek kullanıcı dostu etiket.
     * Özel dönem aktifken "Tüm zamanlar" göstermez; tarih aralığını veya "Özel Dönem" gösterir.
     */
    val periodChipLabel: String
        get() = when {
            filter.customPeriod != null -> summary.dateRangeText.ifBlank { "Özel Dönem" }
            else -> filter.periodPreset.toLabel()
        }
}

/**
 * Tarihe göre gruplanmış işlem kümesi display modelidir.
 */
data class DateGroupedTransactionsDisplayModel(
    val date: LocalDate,
    val formattedDate: String,
    val items: List<TransactionDisplayModel>,
    val dailyNetFormatted: String? = null,
    val isDailyNetNegative: Boolean = true,
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
    val note: String? = null,
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
    val customPeriod: ReportPeriod? = null,
    val workspaceId: EntityId? = null,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST,
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (type != null) count++
            if (categoryId != null) count++
            if (paymentMethod != null) count++
            if (periodPreset != null || customPeriod != null) count++
            if (workspaceId != null) count++
            return count
        }
}
