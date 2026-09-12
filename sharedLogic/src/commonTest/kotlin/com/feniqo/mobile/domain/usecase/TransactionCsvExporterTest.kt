package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionCsvExporterTest {
    private val exporter = TransactionCsvExporter()

    @Test
    fun export_usesStableMinorUnitSchemaAndExcludesPrivateFields() {
        val csv = exporter.export(
            listOf(
                transaction(id = "b", date = "2026-09-10", description = "Market, \"haftalık\""),
                transaction(id = "a", date = "2026-09-09", description = null),
            ),
        )

        val lines = csv.lines().filter(String::isNotEmpty)
        assertEquals(
            "\"id\",\"transaction_date\",\"type\",\"amount_minor\",\"currency\",\"category_id\",\"description\",\"payment_method\",\"workspace_id\",\"note\"",
            lines[0],
        )
        assertTrue(lines[1].startsWith("\"a\",\"2026-09-09\""))
        assertTrue(lines[2].contains("\"12550\",\"TRY\""))
        assertTrue(lines[2].contains("\"Market, \"\"haftalık\"\"\""))
        assertFalse(csv.contains("receipt"))
        assertFalse(csv.contains("owner-1"))
        assertFalse(csv.contains("private/path"))
    }

    @Test
    fun export_neutralizesSpreadsheetFormulaPrefixes() {
        val csv = exporter.export(listOf(transaction(description = "  =HYPERLINK(\"bad\")")))

        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"bad\"\")\""))
    }

    @Test
    fun export_emptyList_containsOnlyHeader() {
        assertEquals(1, exporter.export(emptyList()).lines().count(String::isNotEmpty))
    }

    private fun transaction(
        id: String = "tx-1",
        date: String = "2026-09-10",
        description: String? = "Market",
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("owner-1"),
        workspaceId = null,
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("category-1"),
        description = description,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date.split('-').map(String::toInt).let { (year, month, day) ->
            LocalDate(year, month, day)
        },
        receiptPath = com.feniqo.mobile.domain.model.ReceiptPath("private/path"),
        installment = null,
        createdAt = Instant.parse("2026-09-10T10:00:00Z"),
    )
}
