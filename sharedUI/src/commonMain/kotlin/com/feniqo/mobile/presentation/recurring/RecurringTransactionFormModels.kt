package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Düzenleme modu için SSOT veri yükleme durumları.
 */
sealed interface RecurringTransactionEditLoadState {
    data object Idle : RecurringTransactionEditLoadState
    data object Loading : RecurringTransactionEditLoadState
    data class Ready(
        val draft: RecurringTransactionFormDraft,
        val isActive: Boolean,
    ) : RecurringTransactionEditLoadState
    data object NotFound : RecurringTransactionEditLoadState
    data class Error(val message: FinanceUiMessage) : RecurringTransactionEditLoadState
}

/**
 * İlk composition anında edit ID mevcutken formun boş görünmesini önlemek için geçerli yükleme durumunu belirler.
 */
fun resolveEffectiveRecurringEditLoadState(
    recurringTransactionId: EntityId?,
    loadState: RecurringTransactionEditLoadState,
): RecurringTransactionEditLoadState {
    return if (recurringTransactionId != null && loadState is RecurringTransactionEditLoadState.Idle) {
        RecurringTransactionEditLoadState.Loading
    } else {
        loadState
    }
}

/**
 * Tekrarlayan işlem oluşturma veya düzenleme formunun saf taslak modelidir.
 */
data class RecurringTransactionFormDraft(
    val recurringTransactionId: EntityId? = null,
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String? = null,
    val paymentMethod: PaymentMethod,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
) {
    val isEditMode: Boolean get() = recurringTransactionId != null
    val isCreateMode: Boolean get() = recurringTransactionId == null

    /**
     * Taslaktan yeni kayıt oluşturma komutu üretir.
     * Edit modunda çağrılırsa fail-closed olarak IllegalStateException fırlatır.
     */
    fun toCreateCommand(): CreateRecurringTransactionCommand {
        check(isCreateMode) {
            "Düzenleme taslağından (ID: ${recurringTransactionId?.value}) create komutu üretilemez."
        }
        return CreateRecurringTransactionCommand(
            amount = amount,
            type = type,
            categoryId = categoryId,
            description = description?.trim()?.ifBlank { null },
            paymentMethod = paymentMethod,
            rule = RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    /**
     * Taslaktan mevcut kaydı güncelleme komutu üretir.
     * Create modunda çağrılırsa fail-closed olarak IllegalStateException fırlatır.
     */
    fun toUpdateCommand(): UpdateRecurringTransactionCommand {
        val targetId = checkNotNull(recurringTransactionId) {
            "Yeni kayıt taslağından update komutu üretilemez; geçerli bir recurringTransactionId gereklidir."
        }
        return UpdateRecurringTransactionCommand(
            id = targetId,
            amount = amount,
            type = type,
            categoryId = categoryId,
            description = description?.trim()?.ifBlank { null },
            paymentMethod = paymentMethod,
            rule = RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    companion object {
        /**
         * Var olan bir domain [RecurringTransaction] nesnesinden düzenleme taslağı tohumlar (seed eder).
         */
        fun fromDomain(recurring: RecurringTransaction): RecurringTransactionFormDraft =
            RecurringTransactionFormDraft(
                recurringTransactionId = recurring.id,
                amount = recurring.amount,
                type = recurring.type,
                categoryId = recurring.categoryId,
                description = recurring.description,
                paymentMethod = recurring.paymentMethod,
                frequency = recurring.rule.frequency,
                interval = recurring.rule.interval,
                startDate = recurring.rule.startDate,
                endDate = recurring.rule.endDate,
            )
    }
}

/**
 * Tekrarlayan işlem formu UI durum modelidir.
 */
data class RecurringTransactionFormUiState(
    val draft: RecurringTransactionFormDraft,
    val availableCategories: List<Category> = emptyList(),
    val mutationState: RecurringTransactionMutationState = RecurringTransactionMutationState(),
) {
    val isEditMode: Boolean get() = draft.isEditMode
    val isCreateMode: Boolean get() = draft.isCreateMode
    val selectedCategory: Category? get() = availableCategories.firstOrNull { it.id == draft.categoryId }
    val isSelectedCategoryMissing: Boolean get() = selectedCategory == null

    companion object {
        /**
         * Form taslağı ve tüm kategori listesinden, tür (income/expense) filtresini uygulayarak UI durumunu üretir.
         */
        fun create(
            draft: RecurringTransactionFormDraft,
            categories: List<Category>,
            mutationState: RecurringTransactionMutationState = RecurringTransactionMutationState(),
        ): RecurringTransactionFormUiState {
            val filteredCategories = categories.filter { it.type == draft.type }
            return RecurringTransactionFormUiState(
                draft = draft,
                availableCategories = filteredCategories,
                mutationState = mutationState,
            )
        }
    }
}
