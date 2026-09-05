package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.validation.DebtSnowballPlan
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Borç snowball planında bir borcun UI gösterim modeli.
 */
data class DebtSnowballItemUiModel(
    val debtId: EntityId,
    val debtTitle: String,
    val initialRemainingFormatted: String,
    val totalAllocatedFormatted: String,
    val settledInMonth: Int,
    val orderIndex: Int,
)

/**
 * Snowball planında aylık ödeme dağılımının UI satır modeli.
 */
data class DebtSnowballMonthlyAllocationUiModel(
    val month: Int,
    val debtId: EntityId,
    val debtTitle: String,
    val allocatedFormatted: String,
    val remainingBalanceFormatted: String,
)

/**
 * Hesaplanmış snowball planının kullanıcıya sunulan özet gösterim modeli.
 */
data class DebtSnowballPlanDisplayUiModel(
    val currency: Currency,
    val monthlyBudgetFormatted: String,
    val totalDebtFormatted: String,
    val totalMonths: Int,
    val debtItems: List<DebtSnowballItemUiModel>,
    val monthlyAllocations: List<DebtSnowballMonthlyAllocationUiModel>,
)

/**
 * Domain [DebtSnowballPlan] nesnesini UI gösterim modeline dönüştürür.
 */
fun DebtSnowballPlan.toDisplayUiModel(debtsById: Map<EntityId, Debt>): DebtSnowballPlanDisplayUiModel {
    val items = debtPlans.map { planItem ->
        DebtSnowballItemUiModel(
            debtId = planItem.debtId,
            debtTitle = planItem.debtTitle,
            initialRemainingFormatted = MoneyFormatter.format(planItem.initialRemainingAmount),
            totalAllocatedFormatted = MoneyFormatter.format(planItem.totalAllocatedAmount),
            settledInMonth = planItem.settledInMonth,
            orderIndex = planItem.orderIndex,
        )
    }

    val allocations = monthlyAllocations.map { allocation ->
        val title = debtsById[allocation.debtId]?.title ?: "Borç (${allocation.debtId.value})"
        DebtSnowballMonthlyAllocationUiModel(
            month = allocation.month,
            debtId = allocation.debtId,
            debtTitle = title,
            allocatedFormatted = MoneyFormatter.format(allocation.allocatedAmount),
            remainingBalanceFormatted = MoneyFormatter.format(allocation.remainingBalanceAfterPayment),
        )
    }

    return DebtSnowballPlanDisplayUiModel(
        currency = currency,
        monthlyBudgetFormatted = MoneyFormatter.format(monthlyPaymentBudget),
        totalDebtFormatted = MoneyFormatter.format(totalDebtAmount),
        totalMonths = totalMonths,
        debtItems = items,
        monthlyAllocations = allocations,
    )
}

/**
 * Snowball simülasyon ekranının durum modeli (UI State).
 */
data class DebtSnowballPlanUiState(
    val availableCurrencies: List<Currency> = listOf(Currency.TRY, Currency.USD, Currency.EUR),
    val selectedCurrency: Currency = Currency.TRY,
    val budgetInput: String = "",
    val budgetError: String? = null,
    val isLoadingDebts: Boolean = true,
    val eligibleDebtsCount: Int = 0,
    val plan: DebtSnowballPlanDisplayUiModel? = null,
    val observationError: FinanceUiMessage? = null,
)


/**
 * Snowball simülasyon tek seferlik UI eventleri.
 */
sealed interface DebtSnowballPlanUiEvent {
    data class ShowMessage(val message: FinanceUiMessage) : DebtSnowballPlanUiEvent
}
