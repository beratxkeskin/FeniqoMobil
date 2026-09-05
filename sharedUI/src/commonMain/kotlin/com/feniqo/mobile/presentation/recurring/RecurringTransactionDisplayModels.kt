package com.feniqo.mobile.presentation.recurring

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateRecurringTransactionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateRecurringTransactionCommand
import com.feniqo.mobile.domain.validation.RecurrenceScheduleCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Tekrarlayan işlem mutasyon durum modelidir.
 */
data class RecurringTransactionMutationState(
    val isSubmitting: Boolean = false,
    val pendingDeleteId: EntityId? = null,
)

/**
 * Tekrarlayan işlemler liste ekranı UI durum modelidir.
 */
data class RecurringTransactionsUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringTransactionDisplayModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
    val mutationState: RecurringTransactionMutationState = RecurringTransactionMutationState(),
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && items.isEmpty()
}

/**
 * Tekrarlayan işlemler ekranı MVI kullanıcı niyetleridir.
 */
sealed interface RecurringTransactionsIntent {
    data object Retry : RecurringTransactionsIntent
    data class Create(val command: CreateRecurringTransactionCommand) : RecurringTransactionsIntent
    data class Update(val command: UpdateRecurringTransactionCommand) : RecurringTransactionsIntent
    data class SetActive(val command: SetRecurringTransactionActiveCommand) : RecurringTransactionsIntent
    data class RequestDelete(val id: EntityId) : RecurringTransactionsIntent
    data object ConfirmDelete : RecurringTransactionsIntent
    data object DismissDelete : RecurringTransactionsIntent
}

/**
 * Tekrarlayan işlemler ekranı tek-seferlik (one-shot) UI olaylarıdır.
 */
sealed interface RecurringTransactionUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : RecurringTransactionUiEvent
    data class MutationSuccess(val message: FinanceUiMessage) : RecurringTransactionUiEvent
}

/**
 * Tekrarlayan işlem (recurring rule) saf presentation modelidir.
 */
data class RecurringTransactionDisplayModel(
    val id: EntityId,
    val categoryId: EntityId,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryIconKey: String?,
    val isCategoryMissing: Boolean,
    val amount: Money,
    val formattedAmount: String,
    val currency: Currency,
    val type: TransactionType,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val formattedFrequency: String,
    val startDate: LocalDate,
    val formattedStartDate: String,
    val endDate: LocalDate?,
    val formattedEndDate: String?,
    val lastGeneratedDate: LocalDate?,
    val formattedLastGeneratedDate: String?,
    val isNeverGenerated: Boolean,
    val nextOccurrenceDate: LocalDate?,
    val formattedNextOccurrenceDate: String?,
    val isActive: Boolean,
    val isPaused: Boolean,
    val description: String?,
    val paymentMethod: PaymentMethod,
)

/**
 * Domain RecurringTransaction listesini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object RecurringTransactionDisplayModelMapper {

    fun map(
        recurringTransactions: List<RecurringTransaction>,
        categories: List<Category>,
    ): List<RecurringTransactionDisplayModel> {
        if (recurringTransactions.isEmpty()) return emptyList()

        val categoryMap = categories.associateBy { it.id }

        return recurringTransactions
            .map { item -> mapItem(item, categoryMap[item.categoryId]) }
            .sortedWith(
                compareByDescending<RecurringTransactionDisplayModel> { it.isActive }
                    .thenBy(nullsLast()) { it.nextOccurrenceDate }
                    .thenBy { it.id.value },
            )
    }

    fun mapItem(
        item: RecurringTransaction,
        category: Category?,
    ): RecurringTransactionDisplayModel {
        val categoryName = category?.name ?: "Bilinmeyen Kategori"
        val categoryColorHex = category?.color?.hex
        val categoryIconKey = category?.icon?.key
        val isCategoryMissing = category == null

        val formattedAmount = MoneyFormatter.format(
            money = item.amount,
            includeSign = true,
            type = item.type,
        )

        val formattedFrequency = formatRecurrenceSummary(
            frequency = item.rule.frequency,
            interval = item.rule.interval,
        )

        val nextOccurrenceDate = RecurrenceScheduleCalculator.nextOccurrenceAfter(
            rule = item.rule,
            lastGeneratedDate = item.lastGeneratedDate,
        )

        return RecurringTransactionDisplayModel(
            id = item.id,
            categoryId = item.categoryId,
            categoryName = categoryName,
            categoryColorHex = categoryColorHex,
            categoryIconKey = categoryIconKey,
            isCategoryMissing = isCategoryMissing,
            amount = item.amount,
            formattedAmount = formattedAmount,
            currency = item.amount.currency,
            type = item.type,
            frequency = item.rule.frequency,
            interval = item.rule.interval,
            formattedFrequency = formattedFrequency,
            startDate = item.rule.startDate,
            formattedStartDate = DateFormatter.formatReadableDate(item.rule.startDate),
            endDate = item.rule.endDate,
            formattedEndDate = item.rule.endDate?.let { DateFormatter.formatReadableDate(it) },
            lastGeneratedDate = item.lastGeneratedDate,
            formattedLastGeneratedDate = item.lastGeneratedDate?.let { DateFormatter.formatReadableDate(it) },
            isNeverGenerated = item.lastGeneratedDate == null,
            nextOccurrenceDate = nextOccurrenceDate,
            formattedNextOccurrenceDate = nextOccurrenceDate?.let { DateFormatter.formatReadableDate(it) },
            isActive = item.isActive,
            isPaused = !item.isActive,
            description = item.description,
            paymentMethod = item.paymentMethod,
        )
    }

    fun formatRecurrenceSummary(frequency: RecurrenceFrequency, interval: Int): String = when (frequency) {
        RecurrenceFrequency.DAILY -> if (interval == 1) "Her gün" else "Her $interval günde bir"
        RecurrenceFrequency.WEEKLY -> if (interval == 1) "Her hafta" else "Her $interval haftada bir"
        RecurrenceFrequency.MONTHLY -> if (interval == 1) "Her ay" else "Her $interval ayda bir"
        RecurrenceFrequency.YEARLY -> if (interval == 1) "Her yıl" else "Her $interval yılda bir"
    }
}
