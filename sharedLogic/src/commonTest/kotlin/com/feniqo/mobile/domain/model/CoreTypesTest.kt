package com.feniqo.mobile.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class CoreTypesTest {

    @Test
    fun money_is_added_in_minor_units_without_floating_point_values() {
        val total = Money(amountMinor = 12_550, currency = Currency.TRY) +
            Money(amountMinor = 450, currency = Currency.TRY)

        assertEquals(Money(amountMinor = 13_000, currency = Currency.TRY), total)
    }

    @Test
    fun transaction_date_policy_rejects_a_future_date() {
        val today = LocalDate(2026, 8, 5)

        assertTrue(TransactionDatePolicy.isAllowed(transactionDate = today, today = today))
        assertFalse(
            TransactionDatePolicy.isAllowed(
                transactionDate = LocalDate(2026, 8, 6),
                today = today,
            ),
        )
    }

    @Test
    fun workspace_requires_a_non_blank_name() {
        assertFailsWith<IllegalArgumentException> {
            Workspace(
                id = EntityId("workspace-1"),
                name = " ",
                ownerId = EntityId("user-1"),
                createdAt = Instant.parse("2026-08-05T00:00:00Z"),
            )
        }
    }

    @Test
    fun category_accepts_semantic_icon_and_hex_color() {
        val category = Category(
            id = EntityId("category-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#0A7A55"),
            icon = CategoryIcon("shopping"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-05T00:00:00Z"),
        )

        assertEquals(TransactionType.EXPENSE, category.type)
    }

    @Test
    fun transaction_rejects_zero_amount_and_normalizes_empty_description() {
        assertEquals(null, Transaction.normalizeDescription("   "))

        assertFailsWith<IllegalArgumentException> {
            Transaction(
                id = EntityId("transaction-1"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                amount = Money.zero(Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("category-1"),
                description = null,
                paymentMethod = PaymentMethod.CASH,
                transactionDate = LocalDate(2026, 8, 5),
                receiptPath = null,
                installment = null,
                createdAt = Instant.parse("2026-08-05T00:00:00Z"),
            )
        }
    }

    @Test
    fun budget_month_and_installment_have_valid_ranges() {
        assertEquals("2026-08", YearMonth("2026-08").value)
        assertFailsWith<IllegalArgumentException> { YearMonth("2026-13") }
        assertFailsWith<IllegalArgumentException> {
            InstallmentInfo(number = 3, total = 2, groupId = EntityId("installment-group-1"))
        }
    }
}
