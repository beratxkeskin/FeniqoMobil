package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.MoneyDelta
import com.feniqo.mobile.domain.validation.DebtBalance
import com.feniqo.mobile.domain.validation.DebtBalanceCalculator
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Borç veya alacağın vade durumunu ifade eden sunum modeli.
 */
sealed interface DebtDueStatus {
    data class Overdue(val daysOverdue: Long) : DebtDueStatus
    data object DueToday : DebtDueStatus
    data class DueSoon(val daysRemaining: Long) : DebtDueStatus
    data class OnTime(val daysRemaining: Long) : DebtDueStatus
    data object Settled : DebtDueStatus
}

/**
 * Borç ve alacaklar ekranı üçlü finansal özet kartları sunum modelidir.
 */
data class DebtsSummaryUiModel(
    val totalDebt: Money,
    val formattedTotalDebt: String,
    val activeDebtCount: Int,
    val totalReceivable: Money,
    val formattedTotalReceivable: String,
    val activeReceivableCount: Int,
    val netBalanceMinor: Long,
    val formattedNetBalance: String,
    val isNetPositive: Boolean,
    val isNetNegative: Boolean,
    val isNetZero: Boolean,
    val netStatusText: String,
    val baseCurrency: Currency,
    val excludedCurrenciesCount: Int = 0,
    val excludedCurrencies: List<Currency> = emptyList(),
)

/**
 * Borç ve alacaklar ekranında gösterilen açıklanabilir içgörü kartı modelidir.
 */
enum class DebtInsightType {
    INFO,
    WARNING,
    SUCCESS,
}

data class DebtInsightUiModel(
    val title: String,
    val message: String,
    val type: DebtInsightType = DebtInsightType.INFO,
)

/**
 * Borç ve alacaklar liste ekranı UI durum modelidir.
 */
data class DebtsUiState(
    val isLoading: Boolean = true,
    val debts: List<DebtDisplayModel> = emptyList(),
    val activeDebts: List<DebtDisplayModel> = emptyList(),
    val activeReceivables: List<DebtDisplayModel> = emptyList(),
    val settledItems: List<DebtDisplayModel> = emptyList(),
    val upcomingItems: List<DebtDisplayModel> = emptyList(),
    val summary: DebtsSummaryUiModel? = null,
    val insight: DebtInsightUiModel? = null,
    val activeWorkspaceName: String? = null,
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
    val avatarInitial: String = DebtDisplayModelMapper.deriveAvatarInitial(title),
    val dueStatus: DebtDueStatus = if (isSettled) DebtDueStatus.Settled else DebtDueStatus.OnTime(0L),
    val formattedDueStatus: String = DebtDisplayModelMapper.formatDueStatusLabel(dueStatus),
    val daysDiff: Long = 0L,
    val formattedShortDueDate: String = DateFormatter.formatShortReadableDate(dueDate),
)

/**
 * Domain Debt ve DebtPayment listelerini deterministik sıralı presentation modellerine dönüştüren saf mapper.
 */
object DebtDisplayModelMapper {

    fun map(
        debts: List<Debt>,
        paymentsByDebtId: Map<EntityId, List<DebtPayment>> = emptyMap(),
        today: LocalDate? = null,
    ): List<DebtDisplayModel> {
        return debts.map { debt ->
            val payments = paymentsByDebtId[debt.id] ?: emptyList()
            mapItem(debt, payments, today)
        }.sortedWith(
            compareBy<DebtDisplayModel> { !it.isOpen }
                .thenBy { it.dueDate }
                .thenBy { it.id.value }
        )
    }

    fun mapItem(
        debt: Debt,
        payments: List<DebtPayment> = emptyList(),
        today: LocalDate? = null,
    ): DebtDisplayModel {
        val balance: DebtBalance = DebtBalanceCalculator.calculate(debt, payments)
        val typeLabel = when (debt.type) {
            DebtType.DEBT -> "Borç"
            DebtType.RECEIVABLE -> "Alacak"
        }
        val isSettled = balance.isSettled
        val isOpen = !isSettled

        val (dueStatus, daysDiff) = calculateDueStatus(debt.dueDate, today, isSettled)

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
            avatarInitial = deriveAvatarInitial(debt.title),
            dueStatus = dueStatus,
            formattedDueStatus = formatDueStatusLabel(dueStatus),
            daysDiff = daysDiff,
            formattedShortDueDate = DateFormatter.formatShortReadableDate(debt.dueDate),
        )
    }

    fun calculateDueStatus(
        dueDate: LocalDate,
        today: LocalDate?,
        isSettled: Boolean,
    ): Pair<DebtDueStatus, Long> {
        if (isSettled) {
            return DebtDueStatus.Settled to 0L
        }
        if (today == null) {
            return DebtDueStatus.OnTime(0L) to 0L
        }
        val daysDiff = (dueDate.toEpochDays() - today.toEpochDays()).toLong()
        val status = when {
            daysDiff < 0L -> DebtDueStatus.Overdue(daysOverdue = -daysDiff)
            daysDiff == 0L -> DebtDueStatus.DueToday
            daysDiff in 1L..7L -> DebtDueStatus.DueSoon(daysRemaining = daysDiff)
            else -> DebtDueStatus.OnTime(daysRemaining = daysDiff)
        }
        return status to daysDiff
    }

    fun formatDueStatusLabel(dueStatus: DebtDueStatus): String = when (dueStatus) {
        is DebtDueStatus.Overdue -> "Gecikti"
        is DebtDueStatus.DueToday -> "Bugün"
        is DebtDueStatus.DueSoon -> "Yaklaşıyor"
        is DebtDueStatus.OnTime -> "Zamanında"
        is DebtDueStatus.Settled -> "Kapandı"
    }

    fun deriveAvatarInitial(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return "?"
        val firstChar = trimmed.first()
        return if (firstChar.isLetter()) {
            firstChar.uppercase()
        } else {
            firstChar.toString()
        }
    }

    fun buildUiState(
        debts: List<DebtDisplayModel>,
        baseCurrency: Currency = Currency.TRY,
        workspaceName: String? = null,
    ): DebtsUiState {
        val activeDebts = debts.filter { it.type == DebtType.DEBT && it.isOpen }
        val activeReceivables = debts.filter { it.type == DebtType.RECEIVABLE && it.isOpen }
        val settledItems = debts.filter { it.isSettled }
        val upcomingItems = debts.filter { it.isOpen && it.daysDiff <= 7L }
            .sortedWith(
                compareBy<DebtDisplayModel> { it.daysDiff }
                    .thenBy { it.dueDate }
            )

        val summary = DebtsSummaryCalculator.calculate(debts, baseCurrency)
        val insight = DebtInsightBuilder.build(debts, activeDebts, activeReceivables, summary)

        return DebtsUiState(
            isLoading = false,
            debts = debts,
            activeDebts = activeDebts,
            activeReceivables = activeReceivables,
            settledItems = settledItems,
            upcomingItems = upcomingItems,
            summary = summary,
            insight = insight,
            activeWorkspaceName = workspaceName,
            observationError = null,
        )
    }
}

/**
 * Çoklu para birimi güvenliğiyle toplam borç, toplam alacak ve net durumu hesaplar.
 */
object DebtsSummaryCalculator {

    fun calculate(
        debts: List<DebtDisplayModel>,
        baseCurrency: Currency = Currency.TRY,
    ): DebtsSummaryUiModel {
        val matchingDebts = debts.filter { it.currency == baseCurrency }
        val excludedItems = debts.filter { it.currency != baseCurrency }
        val excludedCurrencies = excludedItems.map { it.currency }.distinct()

        val openDebts = matchingDebts.filter { it.type == DebtType.DEBT && it.isOpen }
        val openReceivables = matchingDebts.filter { it.type == DebtType.RECEIVABLE && it.isOpen }

        var totalDebtMinor = 0L
        for (item in openDebts) {
            totalDebtMinor += item.remainingAmount.amountMinor
        }

        var totalReceivableMinor = 0L
        for (item in openReceivables) {
            totalReceivableMinor += item.remainingAmount.amountMinor
        }

        val netBalanceMinor = totalReceivableMinor - totalDebtMinor
        val isNetPositive = netBalanceMinor > 0L
        val isNetNegative = netBalanceMinor < 0L
        val isNetZero = netBalanceMinor == 0L

        val netStatusText = when {
            isNetNegative -> "Borcunuz alacağınızdan fazla."
            isNetPositive -> "Alacağınız borcunuzdan fazla."
            else -> "Borç ve alacaklarınız dengede."
        }

        val totalDebtMoney = Money(totalDebtMinor, baseCurrency)
        val totalReceivableMoney = Money(totalReceivableMinor, baseCurrency)

        val formattedNetBalance = when {
            isNetNegative -> {
                val delta = MoneyDelta(amountMinor = netBalanceMinor, currency = baseCurrency)
                MoneyFormatter.formatDelta(delta, includeSign = true)
            }
            isNetPositive -> {
                val delta = MoneyDelta(amountMinor = netBalanceMinor, currency = baseCurrency)
                MoneyFormatter.formatDelta(delta, includeSign = true)
            }
            else -> MoneyFormatter.format(Money(0L, baseCurrency))
        }

        return DebtsSummaryUiModel(
            totalDebt = totalDebtMoney,
            formattedTotalDebt = MoneyFormatter.format(totalDebtMoney),
            activeDebtCount = openDebts.size,
            totalReceivable = totalReceivableMoney,
            formattedTotalReceivable = MoneyFormatter.format(totalReceivableMoney),
            activeReceivableCount = openReceivables.size,
            netBalanceMinor = netBalanceMinor,
            formattedNetBalance = formattedNetBalance,
            isNetPositive = isNetPositive,
            isNetNegative = isNetNegative,
            isNetZero = isNetZero,
            netStatusText = netStatusText,
            baseCurrency = baseCurrency,
            excludedCurrenciesCount = excludedItems.size,
            excludedCurrencies = excludedCurrencies,
        )
    }
}

/**
 * Gerçek borç/alacak verilerinden açıklanabilir, finansal içgörü kartı üretir.
 */
object DebtInsightBuilder {

    fun build(
        allDebts: List<DebtDisplayModel>,
        activeDebts: List<DebtDisplayModel>,
        activeReceivables: List<DebtDisplayModel>,
        summary: DebtsSummaryUiModel?,
    ): DebtInsightUiModel? {
        if (allDebts.isEmpty()) return null

        val overdueDebts = activeDebts.filter { it.dueStatus is DebtDueStatus.Overdue }
        if (overdueDebts.isNotEmpty()) {
            return DebtInsightUiModel(
                title = "Gecikmiş Borç Uyarısı",
                message = "${overdueDebts.size} adet vadesi geçmiş borcunuz bulunmaktadır. Gecikme maliyetlerini önlemek için bu ödemeleri önceliklendirmeniz önerilir.",
                type = DebtInsightType.WARNING,
            )
        }

        if (activeReceivables.isNotEmpty() && summary != null && summary.totalReceivable.amountMinor > 0L) {
            return DebtInsightUiModel(
                title = "Feniqo İçgörü",
                message = "Tahsil edilmeyi bekleyen toplam ${summary.formattedTotalReceivable} alacağınız var. Gecikmeleri önlemek için karşı tarafa hatırlatma yapmayı düşünebilirsiniz.",
                type = DebtInsightType.INFO,
            )
        }

        if (activeDebts.size >= 2) {
            return DebtInsightUiModel(
                title = "Kartopu Planı Önerisi",
                message = "Kayıtlı ${activeDebts.size} aktif borcunuz var. En düşük bakiyeli borcu önce kapatıp psikolojik ivme kazanmak için Ödeme Planı'nı inceleyebilirsiniz.",
                type = DebtInsightType.INFO,
            )
        }

        if (activeDebts.size == 1) {
            val single = activeDebts.first()
            return DebtInsightUiModel(
                title = "Düzenli Takip",
                message = "${single.title} için kalan borcunuz ${single.formattedRemainingAmount}. Vade tarihine (${single.formattedShortDueDate}) kadar planlı ödemelerle bakiyenizi sıfırlayabilirsiniz.",
                type = DebtInsightType.INFO,
            )
        }

        if (allDebts.all { it.isSettled }) {
            return DebtInsightUiModel(
                title = "Harika Durum!",
                message = "Tüm borç ve alacak kayıtlarınız kapandı. Tebrikler, finansal yükümlülükleriniz tamamen kontrol altında.",
                type = DebtInsightType.SUCCESS,
            )
        }

        return null
    }
}
