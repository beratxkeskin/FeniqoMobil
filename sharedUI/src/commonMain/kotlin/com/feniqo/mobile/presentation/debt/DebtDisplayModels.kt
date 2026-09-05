package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.DebtBalance
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Borç ve alacaklar liste ekranı UI durum modelidir.
 */
data class DebtsUiState(
    val isLoading: Boolean = true,
    val debts: List<DebtDisplayModel> = emptyList(),
    val observationError: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && observationError == null && debts.isEmpty()
}

/**
 * Borç ve alacaklar ekranı MVI kullanıcı niyetleridir.
 */
sealed interface DebtsIntent {
    data object Retry : DebtsIntent
}

/**
 * Borç veya alacak saf presentation modelidir.
 */
data class DebtDisplayModel(
    val id: EntityId,
    val title: String,
    val type: DebtType,
    val status: DebtStatus,
    val principalAmount: Money,
    val formattedPrincipalAmount: String,
    val totalPaid: Money,
    val formattedTotalPaid: String,
    val remainingAmount: Money,
    val formattedRemainingAmount: String,
    val currency: Currency,
    val dueDate: LocalDate,
    val formattedDueDate: String,
    val description: String?,
    val typeLabel: String,
    val isOpen: Boolean,
    val isSettled: Boolean,
)

/**
 * Domain Debt ve DebtPayment listelerini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object DebtDisplayModelMapper {

    fun map(
        debts: List<Debt>,
        paymentsByDebtId: Map<EntityId, List<DebtPayment>> = emptyMap(),
    ): List<DebtDisplayModel> {
        return debts.map { debt ->
            val payments = paymentsByDebtId[debt.id] ?: emptyList()
            mapItem(debt, payments)
        }.sortedWith(
            compareBy<DebtDisplayModel> { !it.isOpen }
                .thenBy { it.dueDate }
                .thenBy { it.id.value }
        )
    }

    fun mapItem(
        debt: Debt,
        payments: List<DebtPayment> = emptyList(),
    ): DebtDisplayModel {
        val balance: DebtBalance = DebtBalanceCalculator.calculate(debt, payments)
        val typeLabel = when (debt.type) {
            DebtType.DEBT -> "Borç"
            DebtType.RECEIVABLE -> "Alacak"
        }
        val isSettled = balance.isSettled
        val isOpen = !isSettled

        return DebtDisplayModel(
            id = debt.id,
            title = debt.title,
            type = debt.type,
            status = balance.status,
            principalAmount = balance.principalAmount,
            formattedPrincipalAmount = MoneyFormatter.format(balance.principalAmount),
            totalPaid = balance.totalPaid,
            formattedTotalPaid = MoneyFormatter.format(balance.totalPaid),
            remainingAmount = balance.remainingAmount,
            formattedRemainingAmount = MoneyFormatter.format(balance.remainingAmount),
            currency = debt.amount.currency,
            dueDate = debt.dueDate,
            formattedDueDate = DateFormatter.formatReadableDate(debt.dueDate),
            description = debt.description,
            typeLabel = typeLabel,
            isOpen = isOpen,
            isSettled = isSettled,
        )
    }
}
