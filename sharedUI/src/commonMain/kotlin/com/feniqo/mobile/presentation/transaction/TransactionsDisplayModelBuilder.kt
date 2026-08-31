package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Transaction
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
    ): List<DateGroupedTransactionsDisplayModel> {
        if (transactions.isEmpty()) return emptyList()

        val categoryMap = categoryHistory.associateBy { it.id }

        // Sıralama:
        // 1. transactionDate azalan
        // 2. createdAt azalan
        // 3. id deterministik sıra
        val sortedTransactions = transactions.sortedWith(
            compareByDescending<Transaction> { it.transactionDate }
                .thenByDescending { it.createdAt }
                .thenBy { it.id.value },
        )

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
                canEdit = true,
                canDelete = true,
            )

            val list = groupedMap.getOrPut(trx.transactionDate) { mutableListOf() }
            list.add(displayModel)
        }

        return groupedMap.map { (date, items) ->
            DateGroupedTransactionsDisplayModel(
                date = date,
                formattedDate = DateFormatter.formatTransactionGroupDate(date, today),
                items = items,
            )
        }
    }
}
