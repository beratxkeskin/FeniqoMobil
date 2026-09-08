package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money

/**
 * Bir ortak gider için ödeme yapanı ve eşit paylaştırılacak üyeleri temsil eder.
 * Kalıcı Transaction şemasından özellikle ayrıdır: Room/sync entegrasyonu sonraki dilimde
 * bu saf sözleşmeyi kullanacaktır.
 */
data class WorkspaceSharedExpense(
    val id: EntityId,
    val paidByUserId: EntityId,
    val amount: Money,
    val participantUserIds: List<EntityId>,
) {
    init {
        require(amount.amountMinor > 0) { "Ortak gider tutarı sıfırdan büyük olmalıdır." }
        require(participantUserIds.isNotEmpty()) { "Ortak gider için en az bir katılımcı gerekir." }
        require(participantUserIds.distinct().size == participantUserIds.size) {
            "Ortak gider katılımcıları tekrarlanamaz."
        }
        require(paidByUserId in participantUserIds) {
            "Ödeme yapan kullanıcı ortak giderin katılımcısı olmalıdır."
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
 * Ortak giderleri kayan nokta kullanmadan, kuruş seviyesinde ve deterministik biçimde
 * eşit böler. Bölünemeyen kuruşlar katılımcı kimliğine göre artan sırayla dağıtılır.
 */
object WorkspaceSettlementCalculator {

    fun calculate(expenses: List<WorkspaceSharedExpense>): WorkspaceSettlement {
        require(expenses.isNotEmpty()) { "Ödeşme hesaplamak için en az bir ortak gider gerekir." }

        val currency = expenses.first().amount.currency
        val expenseIds = HashSet<EntityId>(expenses.size)
        val balances = linkedMapOf<EntityId, Long>()

        for (expense in expenses) {
            require(expenseIds.add(expense.id)) { "Tekrarlanan ortak gider kaydı: ${expense.id.value}" }
            require(expense.amount.currency == currency) {
                "Farklı para birimlerindeki ortak giderler birlikte hesaplanamaz."
            }

            val participants = expense.participantUserIds.sortedBy(EntityId::value)
            val baseShare = expense.amount.amountMinor / participants.size
            val remainder = expense.amount.amountMinor % participants.size

            add(balances, expense.paidByUserId, expense.amount.amountMinor)
            participants.forEachIndexed { index, participantId ->
                val share = baseShare + if (index < remainder) 1L else 0L
                add(balances, participantId, -share)
            }
        }

        val resultBalances = balances.entries
            .sortedBy { it.key.value }
            .map { (userId, amountMinor) -> WorkspaceMemberBalance(userId, amountMinor, currency) }

        require(resultBalances.sumOf(WorkspaceMemberBalance::netAmountMinor) == 0L) {
            "Ortak gider bakiyeleri dengelenemedi."
        }

        return WorkspaceSettlement(
            currency = currency,
            balances = resultBalances,
            transfers = transfersFor(resultBalances, currency),
        )
    }

    private fun transfersFor(
        balances: List<WorkspaceMemberBalance>,
        currency: Currency,
    ): List<WorkspaceSettlementTransfer> {
        val debtors = balances
            .filter { it.netAmountMinor < 0L }
            .sortedWith(compareBy<WorkspaceMemberBalance> { it.netAmountMinor }.thenBy { it.userId.value })
            .map { it.userId to -it.netAmountMinor }
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
        require(debtorIndex == debtors.size && creditorIndex == creditors.size) {
            "Ortak gider ödeşme transferleri dengelenemedi."
        }
        return transfers
    }

    private fun add(balances: MutableMap<EntityId, Long>, userId: EntityId, change: Long) {
        val current = balances[userId] ?: 0L
        require(
            (change >= 0L && current <= Long.MAX_VALUE - change) ||
                (change < 0L && current >= Long.MIN_VALUE - change),
        ) { "Ortak gider bakiyesi güvenli aralığı aştı." }
        val next = current + change
        balances[userId] = next
    }
}
