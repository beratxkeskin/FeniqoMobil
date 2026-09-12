package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Transaction

/**
 * Room-backed domain transactions are exported with a stable, locale-independent schema.
 * Private receipt paths and owner identifiers are deliberately excluded.
 */
class TransactionCsvExporter {
    fun export(transactions: List<Transaction>): String = buildString {
        appendLine(HEADER.joinToString(",", transform = ::csvCell))
        transactions
            .sortedWith(compareBy<Transaction> { it.transactionDate.toString() }.thenBy { it.id.value })
            .forEach { transaction ->
                appendLine(
                    listOf(
                        transaction.id.value,
                        transaction.transactionDate.toString(),
                        transaction.type.name,
                        transaction.amount.amountMinor.toString(),
                        transaction.amount.currency.code,
                        transaction.categoryId.value,
                        transaction.description.orEmpty(),
                        transaction.paymentMethod.name,
                        transaction.workspaceId?.value.orEmpty(),
                        transaction.note.orEmpty(),
                    ).joinToString(",", transform = ::csvCell),
                )
            }
    }

    private fun csvCell(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val safe = if (normalized.dropWhile(Char::isWhitespace).firstOrNull() in FORMULA_PREFIXES) {
            "'$normalized"
        } else {
            normalized
        }
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private companion object {
        val HEADER = listOf(
            "id",
            "transaction_date",
            "type",
            "amount_minor",
            "currency",
            "category_id",
            "description",
            "payment_method",
            "workspace_id",
            "note",
        )
        val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t')
    }
}
