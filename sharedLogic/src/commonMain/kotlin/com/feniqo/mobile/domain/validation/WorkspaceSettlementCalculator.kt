package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode

/**
 * Kontrollü ortak gider ve ödeşme hesaplama istisnaları.
 * IllegalArgumentException'dan türediği için mevcut yakalama ve test sözleşmelerini bozmaz.
 */
sealed class WorkspaceSettlementException(message: String) : IllegalArgumentException(message) {
    class InvalidExpense(val expenseId: EntityId, reason: String) :
        WorkspaceSettlementException("Geçersiz ortak gider (${expenseId.value}): $reason")
    class TotalMismatchException(val expenseId: EntityId, val totalShares: Long, val expected: Long) :
        WorkspaceSettlementException("Özel paylar toplamı ($totalShares) işlem tutarı ($expected) ile eşleşmelidir: ${expenseId.value}")
    class NegativeShareException(val expenseId: EntityId, val userId: EntityId) :
        WorkspaceSettlementException("Özel pay tutarları negatif olamaz: ${userId.value} (${expenseId.value})")
    class DuplicateParticipantException(val expenseId: EntityId, val userId: EntityId) :
        WorkspaceSettlementException("Özel pay katılımcıları tekrarlanamaz: ${userId.value} (${expenseId.value})")
    class ParticipantSetMismatchException(val expenseId: EntityId) :
        WorkspaceSettlementException("Özel pay katılımcıları ile katılımcı listesi eşleşmelidir: ${expenseId.value}")
    class ZeroShareNotAllowedException(val expenseId: EntityId, val userId: EntityId) :
        WorkspaceSettlementException("Ödeme yapan dışındaki katılımcıların payı sıfırdan büyük olmalıdır: ${userId.value} (${expenseId.value})")
    class AmountOverflowException(val expenseId: EntityId) :
        WorkspaceSettlementException("Özel paylar toplamında Long taşması: ${expenseId.value}")
    class CurrencyMismatch(val expenseId: EntityId, expected: Currency, actual: Currency) :
        WorkspaceSettlementException("Ortak gider (${expenseId.value}) para birimi ($actual) beklenen ($expected) ile uyuşmuyor.")
    class DuplicateExpense(val expenseId: EntityId) :
        WorkspaceSettlementException("Tekrarlanan ortak gider kaydı: ${expenseId.value}")
    class BalanceOverflow(val userId: EntityId) :
        WorkspaceSettlementException("Üye (${userId.value}) bakiyesi güvenli Long aralığını aştı.")
    class SettlementImbalance(val totalBalance: Long) :
        WorkspaceSettlementException("Ortak gider bakiyeleri dengelenemedi: toplam $totalBalance != 0.")
}

/**
 * Ortak gider eşit paylaşım matematiğini kuruş seviyesinde ve deterministik artık dağıtımıyla hesaplar.
 */
object EqualSplitCalculator {

    fun calculateEqualShares(
        amountMinor: Long,
        participantUserIds: List<EntityId>,
    ): List<TransactionParticipantShare> {
        require(amountMinor > 0L) { "İşlem tutarı sıfırdan büyük olmalıdır." }
        require(participantUserIds.isNotEmpty()) { "En az bir katılımcı gereklidir." }
        require(participantUserIds.distinct().size == participantUserIds.size) { "Katılımcılar tekrarlanamaz." }

        val participants = participantUserIds.sortedBy { it.value }
        val baseShare = amountMinor / participants.size
        val remainder = (amountMinor % participants.size).toInt()

        var runningSum = 0L
        val shares = participants.mapIndexed { index, participantId ->
            val shareAmount = baseShare + if (index < remainder) 1L else 0L
            val next = runningSum + shareAmount
            if ((runningSum xor next) and (shareAmount xor next) < 0) {
                throw ArithmeticException("Eşit pay toplamında Long taşması: $runningSum + $shareAmount")
            }
            runningSum = next
            TransactionParticipantShare(participantId, shareAmount)
        }

        check(runningSum == amountMinor) { "Eşit paylar toplamı ($runningSum) ile işlem tutarı ($amountMinor) eşleşmedi." }
        return shares
    }
}

/**
 * Bir ortak gider için ödeme yapanı, katılımcıları ve EQUAL/CUSTOM dağıtım paylarını temsil eder.
 */
data class WorkspaceSharedExpense(
    val id: EntityId,
    val paidByUserId: EntityId,
    val amount: Money,
    val participantUserIds: List<EntityId>,
    val splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
    val participantShares: List<TransactionParticipantShare> = emptyList(),
) {
    init {
        if (amount.amountMinor <= 0) {
            throw WorkspaceSettlementException.InvalidExpense(id, "Ortak gider tutarı sıfırdan büyük olmalıdır.")
        }
        if (participantUserIds.isEmpty()) {
            throw WorkspaceSettlementException.InvalidExpense(id, "Ortak gider için en az bir katılımcı gerekir.")
        }
        if (participantUserIds.distinct().size != participantUserIds.size) {
            throw WorkspaceSettlementException.InvalidExpense(id, "Ortak gider katılımcıları tekrarlanamaz.")
        }
        if (paidByUserId !in participantUserIds) {
            throw WorkspaceSettlementException.InvalidExpense(id, "Ödeme yapan kullanıcı ortak giderin katılımcısı olmalıdır.")
        }

        when (splitMode) {
            TransactionSplitMode.EQUAL -> {
                if (participantShares.isNotEmpty()) {
                    throw WorkspaceSettlementException.InvalidExpense(id, "Eşit paylaşım modunda özel paylar boş olmalıdır.")
                }
            }
            TransactionSplitMode.CUSTOM -> {
                if (participantShares.isEmpty()) {
                    throw WorkspaceSettlementException.InvalidExpense(id, "Özel paylaşım modunda pay listesi boş olamaz.")
                }
                if (participantShares.distinctBy { it.userId }.size != participantShares.size) {
                    val duplicateUser = participantShares.groupBy { it.userId }.entries.first { it.value.size > 1 }.key
                    throw WorkspaceSettlementException.DuplicateParticipantException(id, duplicateUser)
                }
                if (participantShares.map { it.userId }.toSet() != participantUserIds.toSet()) {
                    throw WorkspaceSettlementException.ParticipantSetMismatchException(id)
                }
                val negativeShare = participantShares.firstOrNull { it.amountMinor < 0L }
                if (negativeShare != null) {
                    throw WorkspaceSettlementException.NegativeShareException(id, negativeShare.userId)
                }
                val zeroNonPayer = participantShares.firstOrNull { it.userId != paidByUserId && it.amountMinor <= 0L }
                if (zeroNonPayer != null) {
                    throw WorkspaceSettlementException.ZeroShareNotAllowedException(id, zeroNonPayer.userId)
                }
                var totalShares = 0L
                for (share in participantShares) {
                    val next = totalShares + share.amountMinor
                    if ((totalShares xor next) and (share.amountMinor xor next) < 0) {
                        throw WorkspaceSettlementException.AmountOverflowException(id)
                    }
                    totalShares = next
                }
                if (totalShares != amount.amountMinor) {
                    throw WorkspaceSettlementException.TotalMismatchException(id, totalShares, amount.amountMinor)
                }
            }
        }
    }
}

/** Bir üyenin, dahil edilen tüm ortak giderler sonundaki net bakiyesi. */
data class WorkspaceMemberBalance(
    val userId: EntityId,
    /** Pozitif değer üyenin tahsil edeceğini, negatif değer ödeyeceğini gösterir. */
    val netAmountMinor: Long,
    val currency: Currency,
)

/** Ödeşmeyi tamamlamak için bir borçludan bir alacaklıya önerilen tek transfer. */
data class WorkspaceSettlementTransfer(
    val fromUserId: EntityId,
    val toUserId: EntityId,
    val amount: Money,
) {
    init {
        require(fromUserId != toUserId) { "Ödeşme transferinin göndereni ve alıcısı aynı olamaz." }
        require(amount.amountMinor > 0) { "Ödeşme transferi sıfırdan büyük olmalıdır." }
    }
}

data class WorkspaceSettlement(
    val currency: Currency,
    val balances: List<WorkspaceMemberBalance>,
    val transfers: List<WorkspaceSettlementTransfer>,
)

/**
 * Ortak giderleri (EQUAL ve CUSTOM) kayan nokta kullanmadan, kuruş seviyesinde ve deterministik biçimde hesaplar.
 */
object WorkspaceSettlementCalculator {

    fun calculate(expenses: List<WorkspaceSharedExpense>): WorkspaceSettlement {
        if (expenses.isEmpty()) {
            throw IllegalArgumentException("Ödeşme hesaplamak için en az bir ortak gider gerekir.")
        }

        val currency = expenses.first().amount.currency
        val expenseIds = HashSet<EntityId>(expenses.size)
        val balances = linkedMapOf<EntityId, Long>()

        for (expense in expenses) {
            if (!expenseIds.add(expense.id)) {
                throw WorkspaceSettlementException.DuplicateExpense(expense.id)
            }
            if (expense.amount.currency != currency) {
                throw WorkspaceSettlementException.CurrencyMismatch(expense.id, currency, expense.amount.currency)
            }

            add(balances, expense.paidByUserId, expense.amount.amountMinor)

            val shares = when (expense.splitMode) {
                TransactionSplitMode.EQUAL -> EqualSplitCalculator.calculateEqualShares(
                    expense.amount.amountMinor,
                    expense.participantUserIds,
                )
                TransactionSplitMode.CUSTOM -> expense.participantShares
            }

            for (share in shares) {
                add(balances, share.userId, -share.amountMinor)
            }
        }

        val resultBalances = balances.entries
            .sortedBy { it.key.value }
            .map { (userId, amountMinor) -> WorkspaceMemberBalance(userId, amountMinor, currency) }

        var totalBalanceSum = 0L
        for (balance in resultBalances) {
            val current = balance.netAmountMinor
            val next = totalBalanceSum + current
            if ((totalBalanceSum xor next) and (current xor next) < 0) {
                throw WorkspaceSettlementException.BalanceOverflow(balance.userId)
            }
            totalBalanceSum = next
        }
        if (totalBalanceSum != 0L) {
            throw WorkspaceSettlementException.SettlementImbalance(totalBalanceSum)
        }

        return WorkspaceSettlement(
            currency = currency,
            balances = resultBalances,
            transfers = transfersFor(resultBalances, currency),
        )
    }

    internal fun transfersFor(
        balances: List<WorkspaceMemberBalance>,
        currency: Currency,
    ): List<WorkspaceSettlementTransfer> {
        val debtors = balances
            .filter { it.netAmountMinor < 0L }
            .sortedWith(compareBy<WorkspaceMemberBalance> { it.netAmountMinor }.thenBy { it.userId.value })
            .map {
                if (it.netAmountMinor == Long.MIN_VALUE) {
                    throw WorkspaceSettlementException.BalanceOverflow(it.userId)
                }
                it.userId to -it.netAmountMinor
            }
            .toMutableList()
        val creditors = balances
            .filter { it.netAmountMinor > 0L }
            .sortedWith(compareByDescending<WorkspaceMemberBalance> { it.netAmountMinor }.thenBy { it.userId.value })
            .map { it.userId to it.netAmountMinor }
            .toMutableList()

        val transfers = mutableListOf<WorkspaceSettlementTransfer>()
        var debtorIndex = 0
        var creditorIndex = 0
        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val (debtorId, debt) = debtors[debtorIndex]
            val (creditorId, credit) = creditors[creditorIndex]
            val amount = minOf(debt, credit)
            transfers += WorkspaceSettlementTransfer(debtorId, creditorId, Money(amount, currency))

            debtors[debtorIndex] = debtorId to (debt - amount)
            creditors[creditorIndex] = creditorId to (credit - amount)
            if (debt == amount) debtorIndex++
            if (credit == amount) creditorIndex++
        }
        if (debtorIndex != debtors.size || creditorIndex != creditors.size) {
            throw WorkspaceSettlementException.SettlementImbalance(0L)
        }
        return transfers
    }

    private fun add(balances: MutableMap<EntityId, Long>, userId: EntityId, change: Long) {
        val current = balances[userId] ?: 0L
        val next = current + change
        if ((current xor next) and (change xor next) < 0) {
            throw WorkspaceSettlementException.BalanceOverflow(userId)
        }
        balances[userId] = next
    }
}
