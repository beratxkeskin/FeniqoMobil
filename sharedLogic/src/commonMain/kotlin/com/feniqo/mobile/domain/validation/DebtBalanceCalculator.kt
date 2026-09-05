package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money

/**
 * Borç veya alacağın anlık ödeme ve bakiye durumu özeti.
 */
data class DebtBalance(
    val debtId: EntityId,
    val principalAmount: Money,
    val totalPaid: Money,
    val remainingAmount: Money,
    val status: DebtStatus,
    val isSettled: Boolean,
    val paymentCount: Int,
) {
    init {
        require(principalAmount.currency == totalPaid.currency) {
            "Anapara ve ödenen tutar aynı para biriminde olmalıdır: ${principalAmount.currency} != ${totalPaid.currency}"
        }
        require(principalAmount.currency == remainingAmount.currency) {
            "Anapara ve kalan tutar aynı para biriminde olmalıdır: ${principalAmount.currency} != ${remainingAmount.currency}"
        }
        require(totalPaid.amountMinor <= principalAmount.amountMinor) {
            "Ödenen tutar (${totalPaid.amountMinor}) anapara tutarını (${principalAmount.amountMinor}) aşamaz."
        }
        require(remainingAmount.amountMinor >= 0) {
            "Kalan tutar negatif olamaz: ${remainingAmount.amountMinor}"
        }
    }
}

/**
 * Borç ve alacak bakiye, kısmi/tam ödeme durumunu hesaplayan platformdan bağımsız saf hesaplayıcı.
 */
object DebtBalanceCalculator {

    /**
     * Ana borç ve ilişkili ödemeler üzerinden ödenen, kalan ve türetilmiş durumu hesaplar.
     *
     * @throws IllegalArgumentException Tekrarlanan ödeme kimliği, borç kimliği uyuşmazlığı,
     * para birimi uyuşmazlığı veya fazla ödeme durumunda fail-closed fırlatılır.
     */
    fun calculate(debt: Debt, payments: List<DebtPayment>): DebtBalance {
        val seenPaymentIds = HashSet<EntityId>(payments.size)
        var totalPaidMinor = 0L

        for (payment in payments) {
            require(seenPaymentIds.add(payment.id)) {
                "Tekrarlanan borç ödeme kaydı tespit edildi: ${payment.id.value}"
            }
            require(payment.debtId == debt.id) {
                "Ödeme borç kimliği (${payment.debtId.value}) ana borç kimliğiyle (${debt.id.value}) eşleşmiyor."
            }
            require(payment.amount.currency == debt.amount.currency) {
                "Ödeme para birimi (${payment.amount.currency}) ana borç para birimiyle (${debt.amount.currency}) eşleşmiyor."
            }
            require(payment.amount.amountMinor > 0) {
                "Ödeme tutarı sıfırdan büyük olmalıdır: ${payment.amount.amountMinor}"
            }
            val currentRemaining = debt.amount.amountMinor - totalPaidMinor
            require(payment.amount.amountMinor <= currentRemaining) {
                "Ödeme tutarı (${payment.amount.amountMinor}) kalan borç tutarını ($currentRemaining) veya ana borç tutarını (${debt.amount.amountMinor}) aşamaz."
            }
            totalPaidMinor += payment.amount.amountMinor
        }

        val remainingMinor = debt.amount.amountMinor - totalPaidMinor
        val status = if (remainingMinor == 0L) DebtStatus.SETTLED else DebtStatus.OPEN

        return DebtBalance(
            debtId = debt.id,
            principalAmount = debt.amount,
            totalPaid = Money(totalPaidMinor, debt.amount.currency),
            remainingAmount = Money(remainingMinor, debt.amount.currency),
            status = status,
            isSettled = status == DebtStatus.SETTLED,
            paymentCount = payments.size,
        )
    }

    /**
     * Yeni bir ödeme eklendiğinde oluşacak bakiyeyi hesaplar ve doğrular.
     */
    fun calculateWithNewPayment(
        debt: Debt,
        existingPayments: List<DebtPayment>,
        newPayment: DebtPayment,
    ): DebtBalance {
        return calculate(debt, existingPayments + newPayment)
    }
}
