package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money

/**
 * Snowball planında her bir borca ait özet simülasyon sonucu.
 */
data class DebtSnowballItemPlan(
    val debtId: EntityId,
    val debtTitle: String,
    val initialRemainingAmount: Money,
    val totalAllocatedAmount: Money,
    val settledInMonth: Int,
    val orderIndex: Int,
) {
    init {
        require(initialRemainingAmount.currency == totalAllocatedAmount.currency) {
            "Kalan tutar ve ayrılan toplam tutar para birimi uyuşmalıdır."
        }
        require(initialRemainingAmount.amountMinor == totalAllocatedAmount.amountMinor) {
            "Simülasyon sonunda borca ayrılan toplam tutar (${totalAllocatedAmount.amountMinor}), borcun başlangıç kalan tutarına (${initialRemainingAmount.amountMinor}) eşit olmalıdır."
        }
        require(settledInMonth >= 1) {
            "Kapanış ayı 1 veya daha büyük olmalıdır: $settledInMonth"
        }
        require(orderIndex >= 1) {
            "Sıra indeksi 1 veya daha büyük olmalıdır: $orderIndex"
        }
    }
}

/**
 * Snowball simülasyonunda aylık ödeme dağılım detayı.
 */
data class DebtSnowballMonthlyAllocation(
    val month: Int,
    val debtId: EntityId,
    val allocatedAmount: Money,
    val remainingBalanceAfterPayment: Money,
) {
    init {
        require(month >= 1) { "Ay numarası 1 veya daha büyük olmalıdır: $month" }
        require(allocatedAmount.currency == remainingBalanceAfterPayment.currency) {
            "Ayrılan tutar ve kalan bakiye para birimi eşleşmelidir."
        }
        require(allocatedAmount.amountMinor > 0) {
            "Ayrılan tutar pozitif olmalıdır: ${allocatedAmount.amountMinor}"
        }
        require(remainingBalanceAfterPayment.amountMinor >= 0) {
            "Kalan bakiye negatif olamaz: ${remainingBalanceAfterPayment.amountMinor}"
        }
    }
}

/**
 * Faizsiz borç snowball planlama simülasyonunun nihai çıktısı.
 */
data class DebtSnowballPlan(
    val currency: Currency,
    val monthlyPaymentBudget: Money,
    val totalMonths: Int,
    val totalDebtAmount: Money,
    val debtPlans: List<DebtSnowballItemPlan>,
    val monthlyAllocations: List<DebtSnowballMonthlyAllocation>,
) {
    init {
        require(monthlyPaymentBudget.currency == currency) {
            "Bütçe para birimi plan para birimiyle uyuşmalıdır."
        }
        require(totalDebtAmount.currency == currency) {
            "Toplam borç para birimi plan para birimiyle uyuşmalıdır."
        }
        require(totalMonths >= 0) { "Toplam ay negatif olamaz: $totalMonths" }
        if (debtPlans.isEmpty()) {
            require(totalMonths == 0) { "Borç listesi boşken toplam ay 0 olmalıdır." }
            require(totalDebtAmount.amountMinor == 0L) { "Borç listesi boşken toplam borç tutarı 0 olmalıdır." }
            require(monthlyAllocations.isEmpty()) { "Borç listesi boşken aylık dağılım boş olmalıdır." }
        } else {
            require(totalMonths >= 1) { "Borç varken toplam ay en az 1 olmalıdır." }
        }
    }
}

/**
 * Faizsiz borç snowball planlama motoru.
 *
 * Kurallar:
 * 1. Yalnızca [DebtType.DEBT] ve açık bakiyeli borçlar plana dahil edilir.
 * 2. Sıralama önceliği: en düşük kalan bakiye -> en yakın vade ([Debt.dueDate]) -> borç kimliği ([EntityId.value]).
 * 3. Her ay bütçe sıradaki borca aktarılır; borç kapandığında artan tutar aynı ayda bir sonraki borca devreder.
 * 4. Tüm borçlar ve bütçe aynı para biriminde olmalıdır; Float/Double kullanılmaz.
 */
object DebtSnowballPlanner {

    const val MAX_SIMULATION_MONTHS = 1200 // 100 yıl üst sınırı (sonsuz döngü koruması)

    private class SimulationDebt(
        val debt: Debt,
        val initialRemainingMinor: Long,
        var currentRemainingMinor: Long,
        var totalAllocatedMinor: Long = 0L,
        var settledInMonth: Int = 0,
    )

    /**
     * Verilen borçlar ve ödeme geçmişi üzerinden faizsiz snowball planı simüle eder.
     *
     * @param debts Sistemdeki borç ve alacak kayıtları.
     * @param payments Mevcut borç ödeme kayıtları.
     * @param monthlyPaymentBudget Kullanıcının aylık ayırdığı simülasyon bütçesi.
     * @return Deterministik hesaplanmış [DebtSnowballPlan].
     * @throws IllegalArgumentException Bütçe <= 0, para birimi uyuşmazlığı, yetim/mükerrer ödeme veya geçersiz bakiye durumunda.
     * @throws IllegalStateException Simülasyon ay sınırı aşıldığında veya aritmetik taşma oluştuğunda.
     */
    fun calculate(
        debts: List<Debt>,
        payments: List<DebtPayment>,
        monthlyPaymentBudget: Money,
    ): DebtSnowballPlan {
        require(monthlyPaymentBudget.amountMinor > 0) {
            "Aylık ödeme bütçesi sıfırdan büyük olmalıdır: ${monthlyPaymentBudget.amountMinor}"
        }

        val currency = monthlyPaymentBudget.currency

        // Duplicate borç ID kontrolü ve map oluşturma
        val seenDebtIds = HashSet<EntityId>(debts.size)
        val debtsById = HashMap<EntityId, Debt>(debts.size)
        for (debt in debts) {
            require(seenDebtIds.add(debt.id)) {
                "Tekrarlanan borç kaydı tespit edildi: ${debt.id.value}"
            }
            debtsById[debt.id] = debt
        }

        // Orphan ödeme ve duplicate ödeme kontrolü

        val seenPaymentIds = HashSet<EntityId>(payments.size)
        val paymentsByDebtId = HashMap<EntityId, MutableList<DebtPayment>>()

        for (payment in payments) {
            require(seenPaymentIds.add(payment.id)) {
                "Tekrarlanan borç ödeme kaydı tespit edildi: ${payment.id.value}"
            }
            val parentDebt = debtsById[payment.debtId]
            require(parentDebt != null) {
                "Ödeme borç listesinde bulunmayan bir borca ait (orphan payment): ${payment.debtId.value}"
            }
            paymentsByDebtId.getOrPut(payment.debtId) { mutableListOf() }.add(payment)
        }

        // Yalnızca DEBT türündeki ve kalan bakiyesi pozitif olan borçları topla
        val eligibleDebts = mutableListOf<SimulationDebt>()
        var totalDebtMinor = 0L

        for (debt in debts) {
            val debtPayments = paymentsByDebtId[debt.id] ?: emptyList()
            val balance = DebtBalanceCalculator.calculate(debt, debtPayments)

            if (debt.type == DebtType.DEBT && balance.remainingAmount.amountMinor > 0 && debt.status != DebtStatus.SETTLED) {
                require(debt.amount.currency == currency) {
                    "Para birimi uyuşmazlığı: Borç para birimi (${debt.amount.currency}) bütçe para birimiyle ($currency) uyuşmuyor."
                }

                try {
                    totalDebtMinor = safeAdd(totalDebtMinor, balance.remainingAmount.amountMinor)
                    if (totalDebtMinor > Money.MAX_AMOUNT_MINOR) {
                        throw IllegalStateException("Toplam borç tutarı desteklenen güvenli sınırı aşıyor: $totalDebtMinor")
                    }
                } catch (e: ArithmeticException) {
                    throw IllegalStateException("Toplam borç tutarı hesaplanırken sayısal taşma oluştu.", e)
                }


                eligibleDebts.add(
                    SimulationDebt(
                        debt = debt,
                        initialRemainingMinor = balance.remainingAmount.amountMinor,
                        currentRemainingMinor = balance.remainingAmount.amountMinor,
                    )
                )
            }
        }

        if (eligibleDebts.isEmpty()) {
            return DebtSnowballPlan(
                currency = currency,
                monthlyPaymentBudget = monthlyPaymentBudget,
                totalMonths = 0,
                totalDebtAmount = Money(0L, currency),
                debtPlans = emptyList(),
                monthlyAllocations = emptyList(),
            )
        }

        // Snowball sıralaması:
        // 1. Kalan anapara bakiye ASC (en küçük borç önce)
        // 2. Vade tarihi ASC
        // 3. EntityId.value ASC
        val sortedDebts = eligibleDebts.sortedWith(
            compareBy<SimulationDebt> { it.initialRemainingMinor }
                .thenBy { it.debt.dueDate }
                .thenBy { it.debt.id.value }
        )

        val monthlyAllocations = mutableListOf<DebtSnowballMonthlyAllocation>()
        var currentMonth = 0

        while (sortedDebts.any { it.currentRemainingMinor > 0L }) {
            currentMonth++
            if (currentMonth > MAX_SIMULATION_MONTHS) {
                throw IllegalStateException("Borç kapatma simülasyonu maksimum ay sınırını ($MAX_SIMULATION_MONTHS) aştı.")
            }

            var monthBudgetRemainingMinor = monthlyPaymentBudget.amountMinor

            for (simDebt in sortedDebts) {
                if (simDebt.currentRemainingMinor > 0L && monthBudgetRemainingMinor > 0L) {
                    val paymentMinor = minOf(monthBudgetRemainingMinor, simDebt.currentRemainingMinor)
                    simDebt.currentRemainingMinor -= paymentMinor
                    simDebt.totalAllocatedMinor = safeAdd(simDebt.totalAllocatedMinor, paymentMinor)
                    monthBudgetRemainingMinor -= paymentMinor

                    monthlyAllocations.add(
                        DebtSnowballMonthlyAllocation(
                            month = currentMonth,
                            debtId = simDebt.debt.id,
                            allocatedAmount = Money(paymentMinor, currency),
                            remainingBalanceAfterPayment = Money(simDebt.currentRemainingMinor, currency),
                        )
                    )

                    if (simDebt.currentRemainingMinor == 0L && simDebt.settledInMonth == 0) {
                        simDebt.settledInMonth = currentMonth
                    }
                }
            }
        }

        val debtPlans = sortedDebts.mapIndexed { index, simDebt ->
            DebtSnowballItemPlan(
                debtId = simDebt.debt.id,
                debtTitle = simDebt.debt.title,
                initialRemainingAmount = Money(simDebt.initialRemainingMinor, currency),
                totalAllocatedAmount = Money(simDebt.totalAllocatedMinor, currency),
                settledInMonth = simDebt.settledInMonth,
                orderIndex = index + 1,
            )
        }

        return DebtSnowballPlan(
            currency = currency,
            monthlyPaymentBudget = monthlyPaymentBudget,
            totalMonths = currentMonth,
            totalDebtAmount = Money(totalDebtMinor, currency),
            debtPlans = debtPlans,
            monthlyAllocations = monthlyAllocations,
        )
    }

    private fun safeAdd(a: Long, b: Long): Long {
        val result = a + b
        // Long addition overflow check
        if ((a xor result) and (b xor result) < 0) {
            throw ArithmeticException("Long overflow: $a + $b")
        }
        return result
    }
}
