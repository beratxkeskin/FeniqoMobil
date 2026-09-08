package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceSettlementCalculatorTest {

    @Test
    fun calculate_equalSplit_producesSingleTransferFromDebtorToPayer() {
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(expense(id = "expense-1", payer = "a", amount = 10_000, participants = listOf("a", "b"))),
        )

        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 5_000, Currency.TRY),
                WorkspaceMemberBalance(user("b"), -5_000, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(WorkspaceSettlementTransfer(user("b"), user("a"), Money(5_000, Currency.TRY))),
            result.transfers,
        )
    }

    @Test
    fun calculate_remainderIsAssignedDeterministicallyByParticipantId() {
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(expense(id = "expense-1", payer = "c", amount = 10, participants = listOf("c", "b", "a"))),
        )

        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), -4, Currency.TRY),
                WorkspaceMemberBalance(user("b"), -3, Currency.TRY),
                WorkspaceMemberBalance(user("c"), 7, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("a"), user("c"), Money(4, Currency.TRY)),
                WorkspaceSettlementTransfer(user("b"), user("c"), Money(3, Currency.TRY)),
            ),
            result.transfers,
        )
    }

    @Test
    fun calculate_multipleExpenses_netsBalancesAndUsesDeterministicTransfers() {
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                expense(id = "expense-1", payer = "a", amount = 9_000, participants = listOf("a", "b", "c")),
                expense(id = "expense-2", payer = "b", amount = 6_000, participants = listOf("a", "b", "c")),
            ),
        )

        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 4_000, Currency.TRY),
                WorkspaceMemberBalance(user("b"), 1_000, Currency.TRY),
                WorkspaceMemberBalance(user("c"), -5_000, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("c"), user("a"), Money(4_000, Currency.TRY)),
                WorkspaceSettlementTransfer(user("c"), user("b"), Money(1_000, Currency.TRY)),
            ),
            result.transfers,
        )
    }

    @Test
    fun calculate_rejectsDuplicateExpenseIdsMixedCurrenciesAndInvalidParticipants() {
        val duplicate = expense(id = "expense-1", payer = "a", amount = 100, participants = listOf("a"))
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSettlementCalculator.calculate(listOf(duplicate, duplicate))
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSettlementCalculator.calculate(
                listOf(
                    duplicate,
                    WorkspaceSharedExpense(user("expense-2"), user("a"), Money(100, Currency.USD), listOf(user("a"))),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSharedExpense(user("expense-3"), user("a"), Money(100, Currency.TRY), listOf(user("b")))
        }
    }

    @Test
    fun calculate_settlementAlwaysBalancesToZero() {
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                expense(id = "expense-1", payer = "a", amount = 101, participants = listOf("a", "b", "c")),
                expense(id = "expense-2", payer = "c", amount = 70, participants = listOf("a", "c")),
            ),
        )

        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
        assertTrue(result.transfers.all { it.amount.amountMinor > 0L })
    }

    private fun expense(
        id: String,
        payer: String,
        amount: Long,
        participants: List<String>,
    ) = WorkspaceSharedExpense(
        id = user(id),
        paidByUserId = user(payer),
        amount = Money(amount, Currency.TRY),
        participantUserIds = participants.map(::user),
    )

    private fun user(value: String) = EntityId(value)
}
