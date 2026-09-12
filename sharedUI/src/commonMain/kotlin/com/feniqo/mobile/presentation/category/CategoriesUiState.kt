package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Kategori formu alan bazlı doğrulama hatalarıdır.
 */
enum class CategoryFormFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    COLOR_INVALID;

    fun toDisplayText(): String = when (this) {
        NAME_REQUIRED -> "Kategori adı boş bırakılamaz."
        NAME_TOO_LONG -> "Kategori adı en fazla 50 karakter olabilir."
        COLOR_INVALID -> "Lütfen geçerli bir renk seçin."
    }
}

/**
 * Kategori formu başlangıç yükleme hatalarıdır.
 */
enum class CategoryFormLoadError {
    CATEGORY_NOT_FOUND,
    DEFAULT_CATEGORY_READ_ONLY,
    INVALID_ROUTE,
    LOAD_FAILED;

    fun toDisplayText(): String = when (this) {
        CATEGORY_NOT_FOUND -> "Kategori bulunamadı."
        DEFAULT_CATEGORY_READ_ONLY -> "Sistem kategorileri düzenlenemez."
        INVALID_ROUTE -> "Geçersiz kategori bağlantısı."
        LOAD_FAILED -> "Kategori yüklenemedi."
    }
}

/**
 * Kategori dönem karşılaştırmasında hareket yönüdür.
 */
enum class TrendMovement {
    INCREASED,
    DECREASED,
    UNCHANGED,
}

/**
 * Kategori trendinin kullanıcı açısından duygu/anlam karşılığıdır.
 * Gider artışı NEGATIVE (kırmızı), gider azalışı POSITIVE (yeşil).
 * Gelir artışı POSITIVE (yeşil), gelir azalışı NEGATIVE (kırmızı).
 */
enum class TrendSentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

/**
 * Kategori önceki aya göre değişim durumudur.
 */
sealed interface CategoryTrend {
    data object None : CategoryTrend
    data object New : CategoryTrend
    data class Changed(
        val changeBasisPoints: Int,
        val movement: TrendMovement,
        val sentiment: TrendSentiment,
    ) : CategoryTrend
}

/**
 * Seçili dönem harcama ve hareket analizini taşıyan kategori satırı display modelidir.
 */
data class CategorySpendingDisplayModel(
    val category: CategoryDisplayModel,
    val transactionCount: Int,
    val currentPeriodAmount: Money,
    val previousPeriodAmount: Money,
    val formattedCurrentAmount: String,
    val trend: CategoryTrend,
    val proportionBasisPoints: Int = 0,
)

/**
 * Kategoriler ekranı üst özet kartı presentation modelidir.
 * Float/Double içermez; mini bar oranları 0..10_000 baz puan aralığındadır.
 */
data class CategoriesSummaryUiModel(
    val totalCategoriesCount: Int = 0,
    val customCategoriesCount: Int = 0,
    val topCategoryName: String? = null,
    val formattedTopCategoryAmount: String? = null,
    val topCategoryType: TransactionType = TransactionType.EXPENSE,
    val topCategoryShareBasisPoints: Int = 0,
    val miniBarProportionsBasisPoints: List<Int> = emptyList(),
    val insightText: String? = null,
    val balanceMessage: String = "Harcamaların dengede.",
) {
    val topExpenseCategoryName: String?
        get() = if (topCategoryType == TransactionType.EXPENSE) topCategoryName else null

    val formattedTopExpenseAmount: String?
        get() = if (topCategoryType == TransactionType.EXPENSE) formattedTopCategoryAmount else null

    val topExpenseShareBasisPoints: Int
        get() = if (topCategoryType == TransactionType.EXPENSE) topCategoryShareBasisPoints else 0
}

/**
 * Kategori listesi ve analiz ekranı UI durum modelidir.
 */
data class CategoriesUiState(
    val isLoading: Boolean = true,
    val selectedYearMonth: YearMonth,
    val selectedTypeFilter: TransactionType? = null, // null = Tümü, EXPENSE = Gider, INCOME = Gelir
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val isPeriodPickerVisible: Boolean = false,
    val summary: CategoriesSummaryUiModel = CategoriesSummaryUiModel(),
    val items: List<CategorySpendingDisplayModel> = emptyList(),
    val systemCategories: List<CategoryDisplayModel> = emptyList(),
    val customCategories: List<CategoryDisplayModel> = emptyList(),
    val deleteTargetCategory: CategoryDisplayModel? = null,
    val isDeleteInProgress: Boolean = false,
    val activeWorkspaceName: String? = null,
    val generalMessage: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty() && systemCategories.isEmpty() && customCategories.isEmpty()
}

/**
 * Kategori ekleme/düzenleme formu UI durum modelidir.
 */
data class CategoryFormUiState(
    val categoryId: EntityId? = null,
    val isLoadingInitialData: Boolean = false,
    val loadError: CategoryFormLoadError? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val colorHex: String = "#10B981",
    val iconKey: String? = null,
    val isSubmitting: Boolean = false,
    val activeWorkspaceName: String? = null,
    val nameError: CategoryFormFieldError? = null,
    val colorError: CategoryFormFieldError? = null,
    val generalMessage: FinanceUiMessage? = null,
) {
    val isEditMode: Boolean get() = categoryId != null
    val isFormEnabled: Boolean get() = !isSubmitting && !isLoadingInitialData && loadError == null
    val isTypeEditable: Boolean get() = isFormEnabled && !isEditMode
    val canSubmit: Boolean get() = isFormEnabled && name.isNotBlank() && nameError == null && colorError == null
}

/**
 * Kategori display modelidir.
 */
data class CategoryDisplayModel(
    val id: EntityId,
    val name: String,
    val type: TransactionType,
    val colorHex: String,
    val iconKey: String? = null,
    val isDefault: Boolean = false,
) {
    val canEdit: Boolean get() = !isDefault
    val canDelete: Boolean get() = !isDefault
}

/**
 * Kategori form tek seferlik navigation ve aksiyon olaylarıdır.
 */
sealed interface CategoryFormEvent {
    data object NavigateBack : CategoryFormEvent
}
