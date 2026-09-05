package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.usecase.rateBasisPoints

/**
 * Birikim hedefinin anlık ilerleme durumu özeti.
 * Kayan nokta (Float/Double) kullanılmaz; oranlar tamsayı baz puan (basis points) cinsinden tutulur.
 */
data class GoalProgress(
    val currentAmount: Money,
    val targetAmount: Money,
    val remainingAmount: Money,
    val status: GoalStatus,
    val progressBasisPoints: RateBasisPoints,
    val isAchieved: Boolean,
) {
    init {
        require(currentAmount.currency == targetAmount.currency) {
            "Mevcut ve hedef tutar aynı para biriminde olmalıdır: ${currentAmount.currency} != ${targetAmount.currency}"
        }
        require(remainingAmount.currency == targetAmount.currency) {
            "Kalan ve hedef tutar aynı para biriminde olmalıdır: ${remainingAmount.currency} != ${targetAmount.currency}"
        }
        require(currentAmount.amountMinor >= 0) { "Mevcut tutar negatif olamaz: ${currentAmount.amountMinor}" }
        require(targetAmount.amountMinor > 0) { "Hedef tutarı pozitif olmalıdır: ${targetAmount.amountMinor}" }
        require(remainingAmount.amountMinor >= 0) { "Kalan tutar negatif olamaz: ${remainingAmount.amountMinor}" }
    }
}

/**
 * Hedef ilerlemesi ve katkı hesaplamalarını yürüten platformdan bağımsız saf hesaplayıcı.
 */
object GoalProgressCalculator {

    /**
     * Hedef ve mevcut birikim tutarları üzerinden ilerleme özetini hesaplar.
     * Hedef tutarının aşılması durumunda hata üretilmez; gerçek tutar korunur ve durum [GoalStatus.ACHIEVED] olur.
     */
    fun calculateProgress(targetAmount: Money, currentAmount: Money): GoalProgress {
        require(targetAmount.currency == currentAmount.currency) {
            "Hedef ve birikmiş tutar aynı para biriminde olmalıdır: ${targetAmount.currency} != ${currentAmount.currency}"
        }
        require(targetAmount.amountMinor > 0) {
            "Hedef tutarı sıfırdan büyük olmalıdır: ${targetAmount.amountMinor}"
        }
        require(currentAmount.amountMinor >= 0) {
            "Mevcut birikim tutarı negatif olamaz: ${currentAmount.amountMinor}"
        }

        val isAchieved = currentAmount.amountMinor >= targetAmount.amountMinor
        val status = if (isAchieved) GoalStatus.ACHIEVED else GoalStatus.IN_PROGRESS
        val remainingMinor = maxOf(0L, targetAmount.amountMinor - currentAmount.amountMinor)
        val remainingAmount = Money(remainingMinor, targetAmount.currency)
        val basisPoints = rateBasisPoints(currentAmount.amountMinor, targetAmount.amountMinor)

        return GoalProgress(
            currentAmount = currentAmount,
            targetAmount = targetAmount,
            remainingAmount = remainingAmount,
            status = status,
            progressBasisPoints = RateBasisPoints(basisPoints),
            isAchieved = isAchieved,
        )
    }

    /**
     * [Goal] nesnesi üzerinden ilerleme özetini hesaplar.
     */
    fun calculateProgress(goal: Goal): GoalProgress {
        return calculateProgress(targetAmount = goal.targetAmount, currentAmount = goal.currentAmount)
    }

    /**
     * Mevcut birikim tutarına bir katkı uygulandığında oluşacak yeni tutarı hesaplar.
     * [GoalContributionDirection.REMOVE] işlemi mevcut birikimi negatife düşürürse fail-closed reddedilir.
     */
    fun calculateNewAmount(
        currentAmount: Money,
        contributionAmount: Money,
        direction: GoalContributionDirection,
    ): Money {
        require(currentAmount.currency == contributionAmount.currency) {
            "Katkı (${contributionAmount.currency}) ve hedef (${currentAmount.currency}) para birimi eşleşmelidir."
        }
        require(currentAmount.amountMinor >= 0) {
            "Mevcut tutar negatif olamaz: ${currentAmount.amountMinor}"
        }
        require(contributionAmount.amountMinor > 0) {
            "Katkı tutarı pozitif olmalıdır: ${contributionAmount.amountMinor}"
        }

        return when (direction) {
            GoalContributionDirection.ADD -> {
                currentAmount + contributionAmount
            }
            GoalContributionDirection.REMOVE -> {
                val newMinor = currentAmount.amountMinor - contributionAmount.amountMinor
                require(newMinor >= 0) {
                    "Hedef birikimi negatife düşemez: mevcut ${currentAmount.amountMinor}, çıkarılan ${contributionAmount.amountMinor}"
                }
                Money(newMinor, currentAmount.currency)
            }
        }
    }

    /**
     * [GoalContribution] nesnesi üzerinden yeni tutarı hesaplar.
     */
    fun calculateNewAmount(
        currentAmount: Money,
        contribution: GoalContribution,
    ): Money {
        return calculateNewAmount(
            currentAmount = currentAmount,
            contributionAmount = contribution.amount,
            direction = contribution.direction,
        )
    }

    /**
     * Bir hedefe katkı uygulayarak güncellenmiş yeni [Goal] nesnesini döndürür.
     */
    fun applyContribution(goal: Goal, contribution: GoalContribution): Goal {
        require(goal.id == contribution.goalId) {
            "Katkı hedef kimliği (${contribution.goalId.value}) hedef kimliğiyle (${goal.id.value}) eşleşmelidir."
        }
        val newAmount = calculateNewAmount(
            currentAmount = goal.currentAmount,
            contributionAmount = contribution.amount,
            direction = contribution.direction,
        )
        return goal.copy(currentAmount = newAmount)
    }
}
