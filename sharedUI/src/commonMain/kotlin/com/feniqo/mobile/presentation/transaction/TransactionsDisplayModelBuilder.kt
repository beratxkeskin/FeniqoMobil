package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Domain Transaction listesini gruplanmış display modellerine dönüştüren saf builder.
 */
object TransactionsDisplayModelBuilder {

    fun build(
        transactions: List<Transaction>,
        categoryHistory: List<Category>,
        today: LocalDate,
        sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST,
    ): List<DateGroupedTransactionsDisplayModel> {
        if (transactions.isEmpty()) return emptyList()

        val categoryMap = categoryHistory.associateBy { it.id }

        // 1. Sıralama kuralı:
        // - NEWEST: Tarihe göre azalan, aynı gün içinde createdAt azalan, id deterministik
        // - OLDEST: Tarihe göre artan, aynı gün içinde createdAt artan, id deterministik
        // - AMOUNT_DESC: Tarih grupları en yeni->en eski korunur, aynı gün içinde tutar azalan, createdAt azalan, id deterministik
        // - AMOUNT_ASC: Tarih grupları en yeni->en eski korunur, aynı gün içinde tutar artan, createdAt azalan, id deterministik
        val transactionComparator = when (sortOrder) {
            TransactionSortOrder.NEWEST -> {
                compareByDescending<Transaction> { it.transactionDate }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id.value }
            }
            TransactionSortOrder.OLDEST -> {
                compareBy<Transaction> { it.transactionDate }
                    .thenBy { it.createdAt }
                    .thenBy { it.id.value }
            }
            TransactionSortOrder.AMOUNT_DESC -> {
                compareByDescending<Transaction> { it.transactionDate }
                    .thenByDescending { it.amount.amountMinor }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id.value }
            }
            TransactionSortOrder.AMOUNT_ASC -> {
                compareByDescending<Transaction> { it.transactionDate }
                    .thenBy { it.amount.amountMinor }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id.value }
            }
        }

        val sortedTransactions = transactions.sortedWith(transactionComparator)
        val groupedMap = LinkedHashMap<LocalDate, MutableList<TransactionDisplayModel>>()

        for (trx in sortedTransactions) {
            val category = categoryMap[trx.categoryId]
            val displayModel = TransactionDisplayModel(
                id = trx.id,
                amount = trx.amount,
                formattedAmount = MoneyFormatter.format(
                    money = trx.amount,
                    includeSign = true,
                    type = trx.type,
                ),
                type = trx.type,
                categoryId = trx.categoryId,
                categoryName = category?.name ?: "Bilinmeyen kategori",
                categoryColorHex = category?.color?.hex,
                categoryIconKey = category?.icon?.key,
                description = trx.description,
                paymentMethod = trx.paymentMethod,
                transactionDate = trx.transactionDate,
                installment = trx.installment?.let { inst ->
                    InstallmentDisplayModel(
                        number = inst.number,
                        total = inst.total,
                        badgeText = "${inst.number}/${inst.total}",
                    )
                },
                hasReceipt = trx.receiptPath != null,
                note = trx.note,
                syncStatus = trx.syncStatus,
                canEdit = true,
                canDelete = true,
            )

            val list = groupedMap.getOrPut(trx.transactionDate) { mutableListOf() }
            list.add(displayModel)
        }

        return groupedMap.map { (date, items) ->
            val (dailyNetFormatted, isNegative) = calculateDailyNet(items)
            DateGroupedTransactionsDisplayModel(
                date = date,
                formattedDate = DateFormatter.formatTransactionGroupDate(date, today),
                items = items,
                dailyNetFormatted = dailyNetFormatted,
                isDailyNetNegative = isNegative,
            )
        }
    }

    private fun calculateDailyNet(items: List<TransactionDisplayModel>): Pair<String?, Boolean> {
        if (items.isEmpty()) return null to true

        // Farklı para birimi kontrolü (fail-closed)
        val firstCurrency = items.first().amount.currency
        if (items.any { it.amount.currency != firstCurrency }) {
            return null to true
        }

        var incomeMinor = 0L
        var expenseMinor = 0L

        try {
            for (item in items) {
                when (item.type) {
                    TransactionType.INCOME -> incomeMinor = safeAdd(incomeMinor, item.amount.amountMinor)
                    TransactionType.EXPENSE -> expenseMinor = safeAdd(expenseMinor, item.amount.amountMinor)
                }
            }

            val netMinor = incomeMinor - expenseMinor
            val isNegative = netMinor < 0L
            val absNetMinor = if (netMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(netMinor)
            val money = Money(absNetMinor, firstCurrency)
            val baseFormatted = MoneyFormatter.format(money, includeSign = false)

            val formatted = when {
                netMinor < 0L -> "-$baseFormatted"
                netMinor > 0L -> "+$baseFormatted"
                else -> baseFormatted
            }

            return formatted to isNegative
        } catch (_: Exception) {
            return null to true
        }
    }

    private fun safeAdd(a: Long, b: Long): Long {
        require(a >= 0L && b >= 0L) { "Amounts must be non-negative" }
        val sum = a + b
        if (sum < 0L || ((a xor sum) and (b xor sum)) < 0L) {
            throw ArithmeticException("Long overflow in daily net calculation: $a + $b")
        }
        return sum
    }
}
