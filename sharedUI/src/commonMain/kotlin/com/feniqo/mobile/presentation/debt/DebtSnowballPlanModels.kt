package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.DebtSnowballPlan
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Borç snowball planında bir borcun UI gösterim modeli.
 */
data class DebtSnowballItemUiModel(
    val debtId: EntityId,
    val debtTitle: String,
    val initialRemainingAmount: Money,
    val totalAllocatedAmount: Money,
    val settledInMonth: Int,
    val orderIndex: Int,
)

/**
 * Snowball planında aylık ödeme dağılımının UI satır modeli.
 */
data class DebtSnowballMonthlyAllocationUiModel(
    val month: Int,
    val debtId: EntityId,
    val debtTitle: String?,
    val isMissingDebt: Boolean = debtTitle == null,
    val allocatedAmount: Money,
    val remainingBalanceAfterPayment: Money,
)

/**
 * Hesaplanmış snowball planının kullanıcıya sunulan özet gösterim modeli.
 */
data class DebtSnowballPlanDisplayUiModel(
    val currency: Currency,
    val monthlyPaymentBudget: Money,
    val totalDebtAmount: Money,
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
            initialRemainingAmount = planItem.initialRemainingAmount,
            totalAllocatedAmount = planItem.totalAllocatedAmount,
            settledInMonth = planItem.settledInMonth,
            orderIndex = planItem.orderIndex,
        )
    }

    val allocations = monthlyAllocations.map { allocation ->
        val title = debtsById[allocation.debtId]?.title
        DebtSnowballMonthlyAllocationUiModel(
            month = allocation.month,
            debtId = allocation.debtId,
            debtTitle = title,
            isMissingDebt = title == null,
            allocatedAmount = allocation.allocatedAmount,
            remainingBalanceAfterPayment = allocation.remainingBalanceAfterPayment,
        )
    }

    return DebtSnowballPlanDisplayUiModel(
        currency = currency,
        monthlyPaymentBudget = monthlyPaymentBudget,
        totalDebtAmount = totalDebtAmount,
        totalMonths = totalMonths,
        debtItems = items,
        monthlyAllocations = allocations,
    )
}

/**
 * Snowball simülasyon bütçe form alanı hataları.
 */
enum class DebtSnowballFormFieldError {
    BUDGET_REQUIRED,
    BUDGET_NON_POSITIVE,
    BUDGET_MAX_EXCEEDED,
    BUDGET_INVALID,
}

/**
 * Snowball simülasyon ekranının durum modeli (UI State).
 */
data class DebtSnowballPlanUiState(
    val availableCurrencies: List<Currency> = listOf(Currency.TRY, Currency.USD, Currency.EUR),
    val selectedCurrency: Currency = Currency.TRY,
    val budgetInput: String = "",
    val budgetError: DebtSnowballFormFieldError? = null,
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
