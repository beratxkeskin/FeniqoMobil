package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatusCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModelMapper
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Abonelik yenileme ilerletme onay hedefidir.
 */
data class PendingAdvanceRenewalTarget(
    val id: EntityId,
    val nextRenewalDate: LocalDate,
)

/**
 * Abonelik mutasyon durum modelidir.
 */
data class SubscriptionMutationState(
    val isSubmitting: Boolean = false,
    val pendingDeleteId: EntityId? = null,
    val pendingAdvanceRenewal: PendingAdvanceRenewalTarget? = null,
)

/**
 * Abonelikler liste ekranı UI durum modelidir.
 */
data class SubscriptionsUiState(
    val isLoading: Boolean = true,
    val items: List<SubscriptionDisplayModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
    val mutationState: SubscriptionMutationState = SubscriptionMutationState(),
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && items.isEmpty()
}

/**
 * Abonelikler ekranı MVI kullanıcı niyetleridir.
 */
sealed interface SubscriptionsIntent {
    data object Retry : SubscriptionsIntent
    data class Create(val command: CreateSubscriptionCommand) : SubscriptionsIntent
    data class Update(val command: UpdateSubscriptionCommand) : SubscriptionsIntent
    data class SetActive(val command: SetSubscriptionActiveCommand) : SubscriptionsIntent
    data class RequestDelete(val id: EntityId) : SubscriptionsIntent
    data object ConfirmDelete : SubscriptionsIntent
    data object DismissDelete : SubscriptionsIntent
    data class RequestAdvanceRenewal(val id: EntityId, val nextRenewalDate: LocalDate) : SubscriptionsIntent
    data object ConfirmAdvanceRenewal : SubscriptionsIntent
    data object DismissAdvanceRenewal : SubscriptionsIntent
}


/**
 * Abonelikler ekranı tek-seferlik (one-shot) UI olaylarıdır.
 */
sealed interface SubscriptionUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : SubscriptionUiEvent
    data class MutationSuccess(val message: FinanceUiMessage) : SubscriptionUiEvent
}

/**
 * Abonelik saf presentation modelidir.
 */
data class SubscriptionDisplayModel(
    val id: EntityId,
    val name: String,
    val categoryId: EntityId?,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryIconKey: String?,
    val isCategoryUnassigned: Boolean,
    val isCategoryMissing: Boolean,
    val amount: Money,
    val formattedAmount: String,
    val currency: Currency,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val formattedFrequency: String,
    val startDate: LocalDate,
    val formattedStartDate: String,
    val endDate: LocalDate?,
    val formattedEndDate: String?,
    val nextRenewalDate: LocalDate,
    val formattedNextRenewalDate: String,
    val isActive: Boolean,
    val isPaused: Boolean,
    val renewalStatus: SubscriptionRenewalStatus,
)

/**
 * Domain Subscription listesini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object SubscriptionDisplayModelMapper {

    fun map(
        subscriptions: List<Subscription>,
        categories: List<Category>,
        today: LocalDate,
        upcomingWindowDays: Int = SubscriptionRenewalStatusCalculator.DEFAULT_UPCOMING_WINDOW_DAYS,
    ): List<SubscriptionDisplayModel> {
        if (subscriptions.isEmpty()) return emptyList()

        val categoryMap = categories.associateBy { it.id }

        return subscriptions
            .map { item ->
                mapItem(
                    item = item,
                    category = item.categoryId?.let { categoryMap[it] },
                    today = today,
                    upcomingWindowDays = upcomingWindowDays,
                )
            }
            .sortedWith(
                compareByDescending<SubscriptionDisplayModel> { it.isActive }
                    .thenBy { it.nextRenewalDate }
                    .thenBy { it.id.value },
            )
    }

    fun mapItem(
        item: Subscription,
        category: Category?,
        today: LocalDate,
        upcomingWindowDays: Int = SubscriptionRenewalStatusCalculator.DEFAULT_UPCOMING_WINDOW_DAYS,
    ): SubscriptionDisplayModel {
        val (categoryName, isUnassigned, isMissing) = when {
            item.categoryId == null -> Triple("Kategorisiz", true, false)
            category == null -> Triple("Bilinmeyen Kategori", false, true)
            else -> Triple(category.name, false, false)
        }

        val categoryColorHex = category?.color?.hex
        val categoryIconKey = category?.icon?.key

        val formattedAmount = MoneyFormatter.format(
            money = item.amount,
            includeSign = false,
        )

        val formattedFrequency = RecurringTransactionDisplayModelMapper.formatRecurrenceSummary(
            frequency = item.renewalRule.frequency,
            interval = item.renewalRule.interval,
        )

        val renewalStatus = SubscriptionRenewalStatusCalculator.calculate(
            subscription = item,
            today = today,
            upcomingWindowDays = upcomingWindowDays,
        )

        return SubscriptionDisplayModel(
            id = item.id,
            name = item.name,
            categoryId = item.categoryId,
            categoryName = categoryName,
            categoryColorHex = categoryColorHex,
            categoryIconKey = categoryIconKey,
            isCategoryUnassigned = isUnassigned,
            isCategoryMissing = isMissing,
            amount = item.amount,
            formattedAmount = formattedAmount,
            currency = item.amount.currency,
            frequency = item.renewalRule.frequency,
            interval = item.renewalRule.interval,
            formattedFrequency = formattedFrequency,
            startDate = item.renewalRule.startDate,
            formattedStartDate = DateFormatter.formatReadableDate(item.renewalRule.startDate),
            endDate = item.renewalRule.endDate,
            formattedEndDate = item.renewalRule.endDate?.let { DateFormatter.formatReadableDate(it) },
            nextRenewalDate = item.nextRenewalDate,
            formattedNextRenewalDate = DateFormatter.formatReadableDate(item.nextRenewalDate),
            isActive = item.isActive,
            isPaused = !item.isActive,
            renewalStatus = renewalStatus,
        )
    }
}

