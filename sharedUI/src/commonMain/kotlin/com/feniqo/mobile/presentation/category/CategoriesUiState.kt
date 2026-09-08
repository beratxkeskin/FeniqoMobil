package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
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
 * Kategori listesi ekranı UI durum modelidir.
 */
data class CategoriesUiState(
    val isLoading: Boolean = true,
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val systemCategories: List<CategoryDisplayModel> = emptyList(),
    val customCategories: List<CategoryDisplayModel> = emptyList(),
    val deleteTargetCategory: CategoryDisplayModel? = null,
    val isDeleteInProgress: Boolean = false,
    val activeWorkspaceName: String? = null,
    val generalMessage: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && systemCategories.isEmpty() && customCategories.isEmpty()
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
