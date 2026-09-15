package com.feniqo.mobile.presentation.budget

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Currency
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
    val currency: Currency = Currency.TRY,
) {
    val hasExcludedTransactions: Boolean get() = excludedDifferentCurrencyTransactionCount > 0
    val prefixLimit: String get() = MoneyFormatter.formatPrefix(limitMinor, currency, dropZeroDecimals = true)
    val prefixSpent: String get() = MoneyFormatter.formatPrefix(spentMinor, currency, dropZeroDecimals = true)
    val prefixRemaining: String get() = MoneyFormatter.formatPrefix(kotlin.math.abs(remainingMinor), currency, dropZeroDecimals = true)
    val statusSummaryText: String get() = if (isRemainingNegative) "$prefixRemaining aşıldı" else "$prefixRemaining kaldı"
    val compactUsageRate: String get() = "%${usageRateBasisPoints / 100}"
}

/** A single-currency, fail-closed monthly total used by the budget overview. */
data class BudgetMonthlySummaryDisplayModel(
    val currency: Currency,
    val formattedLimit: String,
    val formattedSpent: String,
    val formattedRemaining: String,
    val usageRateBasisPoints: Int,
    val formattedUsageRate: String,
    val usageProgressFraction: Float,
    val health: BudgetHealth,
    val excludedTransactionCount: Int,
    val limitMinor: Long = 0L,
    val spentMinor: Long = 0L,
    val remainingMinor: Long = 0L,
) {
    val prefixLimit: String get() = MoneyFormatter.formatPrefix(limitMinor, currency, dropZeroDecimals = true)
    val prefixSpent: String get() = MoneyFormatter.formatPrefix(spentMinor, currency, dropZeroDecimals = true)
    val prefixRemaining: String get() = MoneyFormatter.formatPrefix(kotlin.math.abs(remainingMinor), currency, dropZeroDecimals = true)
    val compactUsageRate: String get() = "%${usageRateBasisPoints / 100}"
}

sealed interface BudgetOverview {
    data object None : BudgetOverview
    data class Ready(val summaries: List<BudgetMonthlySummaryDisplayModel>, val insight: String) : BudgetOverview
    data object UnsafeTotal : BudgetOverview
}

/**
 * Keeps all arithmetic out of Compose. Amounts are only aggregated within a currency and every
 * addition is checked; an invalid aggregate is deliberately withheld instead of being guessed.
 */
object BudgetOverviewCalculator {
    fun calculate(budgets: List<BudgetProgressDisplayModel>): BudgetOverview {
        if (budgets.isEmpty()) return BudgetOverview.None
        val summaries = budgets.groupBy { it.currency }.map { (currency, group) ->
            val limit = safeSum(group.map { it.limitMinor }) ?: return BudgetOverview.UnsafeTotal
            val spent = safeSum(group.map { it.spentMinor }) ?: return BudgetOverview.UnsafeTotal
            val excluded = safeIntSum(group.map { it.excludedDifferentCurrencyTransactionCount })
                ?: return BudgetOverview.UnsafeTotal
            val remaining = safeSubtract(limit, spent) ?: return BudgetOverview.UnsafeTotal
            val usage = rateBasisPoints(spent, limit).coerceAtLeast(0)
            val health = when {
                usage >= 10_000 -> BudgetHealth.EXCEEDED
                usage >= 8_000 -> BudgetHealth.WARNING
                else -> BudgetHealth.SAFE
            }
            BudgetMonthlySummaryDisplayModel(
                currency = currency,
                formattedLimit = MoneyFormatter.format(com.feniqo.mobile.domain.model.Money(limit, currency)),
                formattedSpent = MoneyFormatter.format(com.feniqo.mobile.domain.model.Money(spent, currency)),
                formattedRemaining = MoneyFormatter.formatDelta(com.feniqo.mobile.domain.model.MoneyDelta(remaining, currency), includeSign = false),
                usageRateBasisPoints = usage,
                formattedUsageRate = MoneyFormatter.formatBasisPoints(com.feniqo.mobile.domain.model.RateBasisPoints(usage)),
                usageProgressFraction = (usage.toFloat() / 10_000f).coerceIn(0f, 1f),
                health = health,
                excludedTransactionCount = excluded,
                limitMinor = limit,
                spentMinor = spent,
                remainingMinor = remaining,
            )
        }.sortedBy { it.currency.name }
        val primary = summaries.first()
        val fastest = budgets.maxWithOrNull(compareBy<BudgetProgressDisplayModel> { it.usageRateBasisPoints }.thenBy { it.categoryName })
        val alertCount = budgets.count { it.health != BudgetHealth.SAFE }
        val insight = when {
            fastest != null && fastest.usageRateBasisPoints >= 8_000 ->
                "${fastest.categoryName} bütçenin ${fastest.formattedUsageRate}'ini kullandın."
            alertCount > 0 -> "${budgets.size} bütçenden $alertCount'i uyarı seviyesinde."
            else -> "Bu ay toplam bütçenin ${MoneyFormatter.formatBasisPoints(com.feniqo.mobile.domain.model.RateBasisPoints((10_000 - primary.usageRateBasisPoints).coerceAtLeast(0)))}'i kaldı."
        }
        return BudgetOverview.Ready(summaries, insight)
    }

    private fun safeSum(values: List<Long>): Long? = values.fold(0L) { total, value ->
        if ((value > 0 && total > Long.MAX_VALUE - value) || (value < 0 && total < Long.MIN_VALUE - value)) return null
        total + value
    }
    private fun safeSubtract(left: Long, right: Long): Long? =
        if ((right > 0 && left < Long.MIN_VALUE + right) || (right < 0 && left > Long.MAX_VALUE + right)) null else left - right
    private fun safeIntSum(values: List<Int>): Int? = values.fold(0) { total, value ->
        if (total > Int.MAX_VALUE - value) return null
        total + value
    }
    private fun rateBasisPoints(numerator: Long, denominator: Long): Int {
        if (denominator <= 0L || numerator <= 0L) return 0
        val whole = numerator / denominator
        val remainder = numerator % denominator
        return when {
            whole >= Int.MAX_VALUE / 10_000L -> Int.MAX_VALUE
            else -> (whole * 10_000L + (remainder * 10_000L) / denominator).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
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
 * Bütçe düzenleme ekranındaki canlı önizleme modelidir.
 */
data class BudgetEditPreview(
    val formattedSpent: String,
    val formattedNewRemaining: String,
    val newRemainingMinor: Long,
    val isRemainingNegative: Boolean,
    val formattedUsageRate: String,
    val usageProgressFraction: Float,
    val health: BudgetHealth,
)

object BudgetEditPreviewCalculator {
    fun calculate(spentMinor: Long, newLimitMinor: Long, currency: Currency): BudgetEditPreview {
        val remaining = newLimitMinor - spentMinor
        val isNegative = remaining < 0
        val absRemaining = if (remaining < 0) -remaining else remaining
        val usageRate = if (newLimitMinor > 0) {
            ((spentMinor.toDouble() / newLimitMinor.toDouble()) * 10_000).toInt()
        } else {
            if (spentMinor > 0) 10_000 else 0
        }
        val health = when {
            usageRate >= 10_000 -> BudgetHealth.EXCEEDED
            usageRate >= 8_000 -> BudgetHealth.WARNING
            else -> BudgetHealth.SAFE
        }
        val compactRate = "%${usageRate / 100}"
        return BudgetEditPreview(
            formattedSpent = MoneyFormatter.formatPrefix(spentMinor, currency, dropZeroDecimals = false),
            formattedNewRemaining = MoneyFormatter.formatPrefix(absRemaining, currency, dropZeroDecimals = false),
            newRemainingMinor = remaining,
            isRemainingNegative = isNegative,
            formattedUsageRate = compactRate,
            usageProgressFraction = (usageRate.toFloat() / 10_000f).coerceIn(0f, 1f),
            health = health,
        )
    }
}

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
    val currentSpentMinor: Long? = null,
    val editPreview: BudgetEditPreview? = null,
    val showCategoryPicker: Boolean = false,
    val showPeriodPicker: Boolean = false,
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
    val formattedCurrentSpent: String? get() = currentSpentMinor?.let {
        MoneyFormatter.formatPrefix(it, currency, dropZeroDecimals = true)
    }
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
    val currentSpentMinor: Long? = null,
    val showCategoryPicker: Boolean = false,
    val showPeriodPicker: Boolean = false,
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

        val preview = if (isEditMode && currentSpentMinor != null) {
            val amountResult = com.feniqo.mobile.domain.validation.BudgetValidationRules.validateAmount(limitInput, currency)
            if (amountResult is com.feniqo.mobile.domain.validation.BudgetValidationResult.Valid) {
                BudgetEditPreviewCalculator.calculate(currentSpentMinor, amountResult.value.amountMinor, currency)
            } else null
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
            currentSpentMinor = currentSpentMinor,
            editPreview = preview,
            showCategoryPicker = showCategoryPicker,
            showPeriodPicker = showPeriodPicker,
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
    val spentMinor: Long = 0L,
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
    val overview: BudgetOverview = BudgetOverview.None,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && budgets.isEmpty()
    val exceededBudgetsCount: Int get() = budgets.count { it.health == BudgetHealth.EXCEEDED }
    val firstExceededBudget: BudgetProgressDisplayModel? get() = budgets.firstOrNull { it.health == BudgetHealth.EXCEEDED }
}

/**
 * Bütçe detay ekranındaki son harcama kaydı gösterim modelidir.
 */
data class BudgetDetailTransactionItem(
    val id: EntityId,
    val description: String,
    val formattedDate: String,
    val formattedAmount: String,
    val categoryIconKey: String? = null,
    val categoryColorHex: String? = null,
)

/**
 * Bütçe detay ekranı UI durum modelidir.
 */
data class BudgetDetailUiState(
    val isLoading: Boolean = true,
    val budget: BudgetProgressDisplayModel? = null,
    val recentTransactions: List<BudgetDetailTransactionItem> = emptyList(),
    val observationError: FinanceUiMessage? = null,
    val isDeleting: Boolean = false,
) {
    val isExceeded: Boolean get() = budget?.health == BudgetHealth.EXCEEDED
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
            currency = budget.limit.currency,
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
