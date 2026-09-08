package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.domain.usecase.BudgetProgressItem
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Bütçe ve harcama ilerlemesi saf presentation modelidir.
 */
data class BudgetProgressDisplayModel(
    val id: EntityId,
    val categoryId: EntityId,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryIconKey: String?,
    val isCategoryMissing: Boolean,
    val month: YearMonth,
    val formattedLimit: String,
    val limitMinor: Long,
    val formattedSpent: String,
    val spentMinor: Long,
    val formattedRemaining: String,
    val remainingMinor: Long,
    val isRemainingNegative: Boolean,
    val usageRateBasisPoints: Int,
    val formattedUsageRate: String,
    val usageProgressFraction: Float,
    val health: BudgetHealth,
    val excludedDifferentCurrencyTransactionCount: Int,
) {
    val hasExcludedTransactions: Boolean get() = excludedDifferentCurrencyTransactionCount > 0
}

/**
 * Bütçe alan doğrulamalarına ait tipli UI hata kodlarıdır.
 */
enum class BudgetFormFieldError {
    CATEGORY_REQUIRED,
    MONTH_REQUIRED,
    MONTH_INVALID_FORMAT,
    AMOUNT_REQUIRED,
    AMOUNT_INVALID_FORMAT,
    AMOUNT_NON_POSITIVE,
    AMOUNT_EXCESSIVE_DECIMAL_DIGITS,
    AMOUNT_MAX_EXCEEDED,
    SOURCE_AND_TARGET_MONTH_SAME;

    fun toDisplayText(): String = when (this) {
        CATEGORY_REQUIRED -> "Lütfen bir harcama kategorisi seçin."
        MONTH_REQUIRED -> "Lütfen bir ay seçin."
        MONTH_INVALID_FORMAT -> "Geçersiz ay formatı (YYYY-AA)."
        AMOUNT_REQUIRED -> "Lütfen bir bütçe limiti girin."
        AMOUNT_INVALID_FORMAT -> "Geçerli bir tutar girin."
        AMOUNT_NON_POSITIVE -> "Bütçe limiti 0'dan büyük olmalıdır."
        AMOUNT_EXCESSIVE_DECIMAL_DIGITS -> "Kuruş hanesi en fazla 2 basamak olabilir."
        AMOUNT_MAX_EXCEEDED -> "Bütçe limiti izin verilen üst sınırı aşıyor."
        SOURCE_AND_TARGET_MONTH_SAME -> "Kaynak ve hedef ay aynı olamaz."
    }
}

/**
 * Bütçe mutasyon (oluşturma, güncelleme, silme, kopyalama) durum modelidir.
 */
data class BudgetMutationState(
    val isSubmitting: Boolean = false,
    val categoryError: BudgetFormFieldError? = null,
    val amountError: BudgetFormFieldError? = null,
    val monthError: BudgetFormFieldError? = null,
    val copyError: BudgetFormFieldError? = null,
)

/**
 * Bütçe oluşturma ve düzenleme formu UI durum modelidir.
 */
data class BudgetFormUiState(
    val budgetId: EntityId? = null,
    val selectedCategoryId: EntityId? = null,
    val selectedCategoryName: String? = null,
    val selectedCategoryColorHex: String? = null,
    val selectedCategoryIconKey: String? = null,
    val availableCategories: List<com.feniqo.mobile.presentation.category.CategoryDisplayModel> = emptyList(),
    val selectedMonth: YearMonth? = null,
    val limitInput: String = "",
    val currency: com.feniqo.mobile.domain.model.Currency = com.feniqo.mobile.domain.model.Currency.TRY,
    val mutationState: BudgetMutationState = BudgetMutationState(),
) {
    val isEditMode: Boolean get() = budgetId != null
    val isSubmitting: Boolean get() = mutationState.isSubmitting
    val isFormEnabled: Boolean get() = !isSubmitting
    val isCategoryEditable: Boolean get() = isFormEnabled && !isEditMode
    val isMonthEditable: Boolean get() = isFormEnabled && !isEditMode
    val canSubmit: Boolean get() = isFormEnabled &&
        (isEditMode || selectedCategoryId != null) &&
        (isEditMode || selectedMonth != null) &&
        limitInput.isNotBlank()
}

/**
 * Bütçe formu düzenlenebilir taslak modelidir.
 * Compose state içinde tutulur ve saf test edilebilir intent/UI-state dönüşümleri sağlar.
 */
data class BudgetFormDraft(
    val budgetId: EntityId? = null,
    val selectedCategoryId: EntityId? = null,
    val selectedCategoryName: String? = null,
    val selectedCategoryColorHex: String? = null,
    val selectedCategoryIconKey: String? = null,
    val selectedMonth: YearMonth? = null,
    val limitInput: String = "",
    val currency: com.feniqo.mobile.domain.model.Currency = com.feniqo.mobile.domain.model.Currency.TRY,
) {
    val isEditMode: Boolean get() = budgetId != null

    /**
     * Yalnız EXPENSE türündeki kategorileri filtreler ve form için geçerli [BudgetFormUiState] üretir.
     */
    fun toUiState(
        categories: List<com.feniqo.mobile.presentation.category.CategoryDisplayModel>,
        mutationState: BudgetMutationState = BudgetMutationState(),
    ): BudgetFormUiState {
        val expenseCategories = filterExpenseCategories(categories)
        val selectedCategory = if (selectedCategoryId != null) {
            categories.find { it.id == selectedCategoryId }
        } else null

        return BudgetFormUiState(
            budgetId = budgetId,
            selectedCategoryId = selectedCategoryId,
            selectedCategoryName = selectedCategory?.name ?: selectedCategoryName,
            selectedCategoryColorHex = selectedCategory?.colorHex ?: selectedCategoryColorHex,
            selectedCategoryIconKey = selectedCategory?.iconKey ?: selectedCategoryIconKey,
            availableCategories = expenseCategories,
            selectedMonth = selectedMonth,
            limitInput = limitInput,
            currency = currency,
            mutationState = mutationState,
        )
    }

    /**
     * Taslak durumundan uygun [BudgetIntent] mutasyon nesnesini üretir.
     */
    fun toSubmitIntent(): BudgetIntent = if (isEditMode) {
        BudgetIntent.UpdateBudget(
            id = budgetId!!,
            limitInput = limitInput,
            currency = currency,
        )
    } else {
        BudgetIntent.CreateBudget(
            categoryId = selectedCategoryId,
            month = selectedMonth,
            limitInput = limitInput,
            currency = currency,
        )
    }

    companion object {
        fun filterExpenseCategories(
            categories: List<com.feniqo.mobile.presentation.category.CategoryDisplayModel>,
        ): List<com.feniqo.mobile.presentation.category.CategoryDisplayModel> =
            categories.filter { it.type == com.feniqo.mobile.domain.model.TransactionType.EXPENSE }
    }
}

/**
 * Bütçe düzenleme formu başlangıç verisidir (seed).
 */
data class BudgetFormSeed(
    val budgetId: EntityId,
    val categoryId: EntityId,
    val month: YearMonth,
    val limitInput: String,
    val currency: com.feniqo.mobile.domain.model.Currency,
)

/**
 * Düzenleme modunda Room SSOT'tan bütçe ön-yükleme durum modelidir.
 */
sealed interface BudgetEditLoadState {
    data object Idle : BudgetEditLoadState
    data object Loading : BudgetEditLoadState
    data class Ready(val seed: BudgetFormSeed) : BudgetEditLoadState
    data object NotFound : BudgetEditLoadState
    data class Error(val message: FinanceUiMessage) : BudgetEditLoadState
}

/**
 * Bütçe formunun ilk oluşturulma (composition) anında yarış durumlarını önlemek için
 * efektif [BudgetEditLoadState] durumunu hesaplar.
 * Düzenleme modunda (budgetId != null) henüz yükleme başlamamışsa (Idle), UI'ın boş ve
 * gönderilebilir form göstermesini önleyerek [BudgetEditLoadState.Loading] üretir.
 */
fun resolveEffectiveBudgetEditLoadState(
    budgetId: EntityId?,
    loadState: BudgetEditLoadState,
): BudgetEditLoadState {
    return if (budgetId != null && loadState is BudgetEditLoadState.Idle) {
        BudgetEditLoadState.Loading
    } else {
        loadState
    }
}

/**
 * Bütçe tek seferlik kullanıcı olayları (Toast/Snackbar/Navigation).
 */
sealed interface BudgetUiEvent {
    data class MutationSuccess(val message: FinanceUiMessage) : BudgetUiEvent
    data class ShowMessage(val message: FinanceUiMessage) : BudgetUiEvent
    data class CopyCompleted(val copiedCount: Int, val skippedCount: Int) : BudgetUiEvent
}

/**
 * Bütçe silme onay diyaloğu durum modelidir.
 */
data class BudgetDeleteConfirmationState(
    val target: BudgetProgressDisplayModel,
    val isDeleting: Boolean = false,
)

/**
 * Bütçe kopyalama onay diyaloğu durum modelidir.
 */
data class BudgetCopyConfirmationState(
    val sourceMonth: YearMonth,
    val targetMonth: YearMonth,
    val isCopying: Boolean = false,
)

/**
 * Bütçeler ekranı UI durum modelidir.
 */
data class BudgetsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth? = null,
    val budgets: List<BudgetProgressDisplayModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
    val mutationState: BudgetMutationState = BudgetMutationState(),
    val activeWorkspaceName: String? = null,
    val deleteConfirmation: BudgetDeleteConfirmationState? = null,
    val copyConfirmation: BudgetCopyConfirmationState? = null,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && budgets.isEmpty()
}

/**
 * Bütçe kullanıcı etkileşimleridir (MVI Intent).
 */
sealed interface BudgetIntent {
    data class SelectMonth(val month: YearMonth) : BudgetIntent
    data object Retry : BudgetIntent

    data class CreateBudget(
        val categoryId: EntityId?,
        val month: YearMonth?,
        val limitInput: String,
        val currency: com.feniqo.mobile.domain.model.Currency = com.feniqo.mobile.domain.model.Currency.TRY,
    ) : BudgetIntent

    data class UpdateBudget(
        val id: EntityId,
        val limitInput: String,
        val currency: com.feniqo.mobile.domain.model.Currency = com.feniqo.mobile.domain.model.Currency.TRY,
    ) : BudgetIntent

    data class RequestDelete(val budget: BudgetProgressDisplayModel) : BudgetIntent
    data object DismissDelete : BudgetIntent
    data object ConfirmDelete : BudgetIntent

    data class RequestCopy(val sourceMonth: YearMonth, val targetMonth: YearMonth) : BudgetIntent
    data class ChangeCopySourceMonth(val month: YearMonth) : BudgetIntent
    data object DismissCopy : BudgetIntent
    data object ConfirmCopy : BudgetIntent

    data object ClearFieldErrors : BudgetIntent
}

/**
 * Bütçe kopyalama sonuç özeti mesaj formatlayıcısıdır.
 */
fun formatCopyResultMessage(copiedCount: Int, skippedCount: Int): String {
    return when {
        copiedCount > 0 && skippedCount == 0 -> "$copiedCount bütçe kopyalandı."
        copiedCount > 0 && skippedCount > 0 -> "$copiedCount bütçe kopyalandı; $skippedCount mevcut bütçe atlandı."
        copiedCount == 0 && skippedCount > 0 -> "Kopyalanacak yeni bütçe bulunamadı; $skippedCount mevcut bütçe atlandı."
        else -> "Kaynak ayda kopyalanacak bütçe bulunamadı."
    }
}

/**
 * Para birimi sembolü presentation gösterim yardımcısıdır.
 */
val com.feniqo.mobile.domain.model.Currency.symbolText: String
    get() = when (this) {
        com.feniqo.mobile.domain.model.Currency.TRY -> "₺"
        com.feniqo.mobile.domain.model.Currency.USD -> "$"
        com.feniqo.mobile.domain.model.Currency.EUR -> "€"
    }

/**
 * Domain [BudgetProgressItem] nesnesini UI için güvenli ve formatlanmış [BudgetProgressDisplayModel]'e dönüştürür.
 */
object BudgetDisplayModelMapper {
    const val FALLBACK_CATEGORY_NAME = "Kategori Yok"

    fun toDisplayModel(item: BudgetProgressItem): BudgetProgressDisplayModel {
        val progress = item.progress
        val budget = progress.budget
        val category = item.category
        val usageRateValue = progress.usageRate.value

        return BudgetProgressDisplayModel(
            id = budget.id,
            categoryId = budget.categoryId,
            categoryName = category?.name ?: FALLBACK_CATEGORY_NAME,
            categoryColorHex = category?.color?.hex,
            categoryIconKey = category?.icon?.key,
            isCategoryMissing = category == null,
            month = budget.month,
            formattedLimit = MoneyFormatter.format(budget.limit),
            limitMinor = budget.limit.amountMinor,
            formattedSpent = MoneyFormatter.format(progress.spent),
            spentMinor = progress.spent.amountMinor,
            formattedRemaining = MoneyFormatter.formatDelta(progress.remaining, includeSign = false),
            remainingMinor = progress.remaining.amountMinor,
            isRemainingNegative = progress.remaining.amountMinor < 0,
            usageRateBasisPoints = usageRateValue,
            formattedUsageRate = MoneyFormatter.formatBasisPoints(progress.usageRate),
            usageProgressFraction = (usageRateValue.toFloat() / 10000f).coerceIn(0f, 1f),
            health = progress.health,
            excludedDifferentCurrencyTransactionCount = item.excludedDifferentCurrencyTransactionCount,
        )
    }

    fun toDisplayModels(items: List<BudgetProgressItem>): List<BudgetProgressDisplayModel> {
        return items.map { toDisplayModel(it) }
    }
}

/**
 * Yıl sınırını (Ocak <-> Aralık) dikkate alarak bir önceki dönemi hesaplar.
 */
fun YearMonth.previousMonth(): YearMonth {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return this
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return this
    return if (month == 1) {
        YearMonth("${year - 1}-12")
    } else {
        val prevMonthStr = (month - 1).toString().padStart(2, '0')
        YearMonth("$year-$prevMonthStr")
    }
}

/**
 * Yıl sınırını (Aralık <-> Ocak) dikkate alarak bir sonraki dönemi hesaplar.
 */
fun YearMonth.nextMonth(): YearMonth {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return this
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return this
    return if (month == 12) {
        YearMonth("${year + 1}-01")
    } else {
        val nextMonthStr = (month + 1).toString().padStart(2, '0')
        YearMonth("$year-$nextMonthStr")
    }
}

/**
 * Kategori ikon anahtarlarını güvenli biçimde simge/metne eşler.
 */
object CategoryIconResolver {
    fun resolveIconEmojiOrNull(iconKey: String?): String? = when (iconKey?.trim()?.lowercase()) {
        "briefcase" -> "💼"
        "laptop" -> "💻"
        "graduation-cap" -> "🎓"
        "trending-up" -> "📈"
        "dollar-sign" -> "💵"
        "utensils" -> "🍽️"
        "shopping-cart", "cart" -> "🛒"
        "car" -> "🚗"
        "coffee" -> "☕"
        "home" -> "🏠"
        "file-text" -> "📄"
        "music" -> "🎵"
        "book-open" -> "📖"
        "heart-pulse" -> "🩺"
        "credit-card" -> "💳"
        "percent" -> "🏷️"
        "help-circle" -> "❓"
        "piggy-bank" -> "🐷"
        "gift" -> "🎁"
        "tag" -> "🏷️"
        else -> null
    }
}

