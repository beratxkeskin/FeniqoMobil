package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionParticipantShare
import com.feniqo.mobile.domain.model.TransactionSplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceSettlementCalculatorTest {

    @Test
    fun calculate_equalSplit_preservesStandardHundredDividedByThree() {
        // 1. Mevcut 100,00/3 eşit paylaşım sonucu korunur
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(expense(id = "expense-1", payer = "a", amount = 10_000, participants = listOf("a", "b", "c"))),
        )

        // 100,00 TL = 10_000 kuruş. 10_000 / 3 = 3333, remainder 1.
        // EntityId artan sırası: a, b, c. "a" ilk remainder'ı alır: payı 3334 kuruş.
        // a net: +10_000 - 3334 = +6666
        // b net: -3333
        // c net: -3333
        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 6_666, Currency.TRY),
                WorkspaceMemberBalance(user("b"), -3_333, Currency.TRY),
                WorkspaceMemberBalance(user("c"), -3_333, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("b"), user("a"), Money(3_333, Currency.TRY)),
                WorkspaceSettlementTransfer(user("c"), user("a"), Money(3_333, Currency.TRY)),
            ),
            result.transfers,
        )
        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
    }

    @Test
    fun calculate_customSplit_fiftyThirtyTwentyProducesCorrectNetBalances() {
        // 2. 100,00 TL için 50/30/20 custom paylar doğru net bakiye üretir
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                customExpense(
                    id = "expense-custom-1",
                    payer = "a",
                    amount = 10_000,
                    shares = listOf("a" to 5_000, "b" to 3_000, "c" to 2_000),
                ),
            ),
        )

        // a net: +10_000 - 5_000 = +5_000
        // b net: -3_000
        // c net: -2_000
        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 5_000, Currency.TRY),
                WorkspaceMemberBalance(user("b"), -3_000, Currency.TRY),
                WorkspaceMemberBalance(user("c"), -2_000, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("b"), user("a"), Money(3_000, Currency.TRY)),
                WorkspaceSettlementTransfer(user("c"), user("a"), Money(2_000, Currency.TRY)),
            ),
            result.transfers,
        )
        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
    }

    @Test
    fun calculate_customSplit_payerZeroShareOnBehalfOfOthersCalculatesCorrectly() {
        // 3. Payer sıfır pay ile başkaları adına ödeme sonucu doğru hesaplanır
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                customExpense(
                    id = "expense-on-behalf",
                    payer = "a",
                    amount = 6_000,
                    shares = listOf("a" to 0L, "b" to 4_000L, "c" to 2_000L),
                ),
            ),
        )

        // a net: +6_000 - 0 = +6_000
        // b net: -4_000
        // c net: -2_000
        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 6_000, Currency.TRY),
                WorkspaceMemberBalance(user("b"), -4_000, Currency.TRY),
                WorkspaceMemberBalance(user("c"), -2_000, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("b"), user("a"), Money(4_000, Currency.TRY)),
                WorkspaceSettlementTransfer(user("c"), user("a"), Money(2_000, Currency.TRY)),
            ),
            result.transfers,
        )
        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
    }

    @Test
    fun calculate_mixedEqualAndCustomExpenses_calculatedAccuratelyInSameList() {
        // 4. EQUAL ve CUSTOM işlemlerin aynı listede hesaplanması
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                expense(id = "exp-equal", payer = "a", amount = 6_000, participants = listOf("a", "b")),
                customExpense(
                    id = "exp-custom",
                    payer = "b",
                    amount = 4_000,
                    shares = listOf("a" to 1_000, "b" to 1_000, "c" to 2_000),
                ),
            ),
        )

        // Netler:
        // a: +3_000 - 1_000 = +2_000
        // b: -3_000 + 3_000 = 0
        // c: -2_000
        assertEquals(
            listOf(
                WorkspaceMemberBalance(user("a"), 2_000, Currency.TRY),
                WorkspaceMemberBalance(user("b"), 0, Currency.TRY),
                WorkspaceMemberBalance(user("c"), -2_000, Currency.TRY),
            ),
            result.balances,
        )
        assertEquals(
            listOf(
                WorkspaceSettlementTransfer(user("c"), user("a"), Money(2_000, Currency.TRY)),
            ),
            result.transfers,
        )
        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
    }

    @Test
    fun calculate_customSplit_shareOrderDoesNotChangeTransferResults() {
        // 5. Share sırası transfer sonucunu değiştirmez
        val expenseOrder1 = customExpense(
            id = "exp-1",
            payer = "a",
            amount = 10_000,
            shares = listOf("a" to 5_000, "b" to 3_000, "c" to 2_000),
        )
        val expenseOrder2 = customExpense(
            id = "exp-1",
            payer = "a",
            amount = 10_000,
            shares = listOf("c" to 2_000, "a" to 5_000, "b" to 3_000),
        )

        val result1 = WorkspaceSettlementCalculator.calculate(listOf(expenseOrder1))
        val result2 = WorkspaceSettlementCalculator.calculate(listOf(expenseOrder2))

        assertEquals(result1.balances, result2.balances)
        assertEquals(result1.transfers, result2.transfers)
    }

    @Test
    fun calculate_totalMismatch_producesFailClosedException() {
        // 6. Toplam uyuşmazlığı fail-closed dışlanabilir sonuç üretir
        assertFailsWith<WorkspaceSettlementException.TotalMismatchException> {
            WorkspaceSharedExpense(
                id = user("bad-total"),
                paidByUserId = user("a"),
                amount = Money(10_000, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), 5_000),
                    TransactionParticipantShare(user("b"), 4_000), // toplam 9_000 != 10_000
                ),
            )
        }
    }

    @Test
    fun calculate_rejectsNegativeDuplicateAndInvalidMemberShares() {
        // 7. Negatif/duplicate/geçersiz üye reddedilir
        assertFailsWith<WorkspaceSettlementException.NegativeShareException> {
            WorkspaceSharedExpense(
                id = user("neg-share"),
                paidByUserId = user("a"),
                amount = Money(10_000, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), 12_000),
                    TransactionParticipantShare(user("b"), -2_000),
                ),
            )
        }

        assertFailsWith<WorkspaceSettlementException.DuplicateParticipantException> {
            WorkspaceSharedExpense(
                id = user("dup-share"),
                paidByUserId = user("a"),
                amount = Money(10_000, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), 5_000),
                    TransactionParticipantShare(user("b"), 3_000),
                    TransactionParticipantShare(user("b"), 2_000),
                ),
            )
        }

        assertFailsWith<WorkspaceSettlementException.ParticipantSetMismatchException> {
            WorkspaceSharedExpense(
                id = user("mismatch-share"),
                paidByUserId = user("a"),
                amount = Money(10_000, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), 5_000),
                    TransactionParticipantShare(user("c"), 5_000),
                ),
            )
        }

        assertFailsWith<WorkspaceSettlementException.ZeroShareNotAllowedException> {
            WorkspaceSharedExpense(
                id = user("zero-share"),
                paidByUserId = user("a"),
                amount = Money(10_000, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), 10_000),
                    TransactionParticipantShare(user("b"), 0),
                ),
            )
        }
    }

    @Test
    fun calculate_longOverflow_isPreventedFailClosed() {
        // 8. Long overflow engellenir
        assertFailsWith<WorkspaceSettlementException.AmountOverflowException> {
            WorkspaceSharedExpense(
                id = user("overflow"),
                paidByUserId = user("a"),
                amount = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY),
                participantUserIds = listOf(user("a"), user("b")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("a"), Long.MAX_VALUE - 5L),
                    TransactionParticipantShare(user("b"), 10L),
                ),
            )
        }
    }

    @Test
    fun calculate_netBalancesAlwaysSumToZero() {
        // 9. Net bakiyelerin toplamı sıfırdır
        val result = WorkspaceSettlementCalculator.calculate(
            listOf(
                expense(id = "exp-1", payer = "a", amount = 101, participants = listOf("a", "b", "c")),
                customExpense(
                    id = "exp-2",
                    payer = "b",
                    amount = 777,
                    shares = listOf("a" to 200, "b" to 100, "c" to 477),
                ),
            ),
        )

        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
        assertTrue(result.transfers.all { it.amount.amountMinor > 0L })
    }

    @Test
    fun calculate_transferSuggestionsAreDeterministic() {
        // 10. Transfer önerileri deterministiktir
        val expenses = listOf(
            expense(id = "exp-1", payer = "c", amount = 9_000, participants = listOf("c", "b", "a")),
            customExpense(
                id = "exp-2",
                payer = "a",
                amount = 4_500,
                shares = listOf("b" to 2_000, "c" to 1_500, "a" to 1_000),
            ),
        )

        val run1 = WorkspaceSettlementCalculator.calculate(expenses)
        val run2 = WorkspaceSettlementCalculator.calculate(expenses)

        assertEquals(run1.balances, run2.balances)
        assertEquals(run1.transfers, run2.transfers)
    }

    @Test
    fun calculate_rejectsDuplicateExpenseIdsMixedCurrenciesAndInvalidParticipants() {
        val duplicate = expense(id = "expense-1", payer = "a", amount = 100, participants = listOf("a"))
        assertFailsWith<WorkspaceSettlementException.DuplicateExpense> {
            WorkspaceSettlementCalculator.calculate(listOf(duplicate, duplicate))
        }
        assertFailsWith<WorkspaceSettlementException.CurrencyMismatch> {
            WorkspaceSettlementCalculator.calculate(
                listOf(
                    duplicate,
                    WorkspaceSharedExpense(user("expense-2"), user("a"), Money(100, Currency.USD), listOf(user("a"))),
                ),
            )
        }
        assertFailsWith<WorkspaceSettlementException.InvalidExpense> {
            WorkspaceSharedExpense(user("expense-3"), user("a"), Money(100, Currency.TRY), listOf(user("b")))
        }
    }

    // =========================================================================
    // Taşma ve Güvenlik Testleri (Audit Madde 5)
    // =========================================================================

    @Test
    fun calculate_balanceAccumulationOverflow_throwsBalanceOverflow() {
        // 10_001 adet MAX_AMOUNT_MINOR harcama eklendiğinde net bakiye Long.MAX_VALUE'yu aşar
        val expenses = (1..10_001).map { i ->
            WorkspaceSharedExpense(
                id = user("exp-$i"),
                paidByUserId = user("payer"),
                amount = Money(Money.MAX_AMOUNT_MINOR, Currency.TRY),
                participantUserIds = listOf(user("payer"), user("member")),
                splitMode = TransactionSplitMode.CUSTOM,
                participantShares = listOf(
                    TransactionParticipantShare(user("payer"), 0L),
                    TransactionParticipantShare(user("member"), Money.MAX_AMOUNT_MINOR),
                ),
            )
        }
        assertFailsWith<WorkspaceSettlementException.BalanceOverflow> {
            WorkspaceSettlementCalculator.calculate(expenses)
        }
    }

    @Test
    fun transfersFor_longMinValueDebtor_throwsBalanceOverflow() {
        // Borçlu bakiyesi -Long.MIN_VALUE olduğunda taşma önlenmeli ve BalanceOverflow fırlatılmalıdır
        val balances = listOf(
            WorkspaceMemberBalance(user("debtor"), Long.MIN_VALUE, Currency.TRY),
            WorkspaceMemberBalance(user("creditor"), Long.MAX_VALUE, Currency.TRY),
        )
        assertFailsWith<WorkspaceSettlementException.BalanceOverflow> {
            WorkspaceSettlementCalculator.transfersFor(balances, Currency.TRY)
        }
    }

    @Test
    fun transfersFor_unbalancedDebtorsAndCreditors_throwsSettlementImbalance() {
        // Borçlular ve alacaklılar toplamı eşit olmadığında kontrollü SettlementImbalance üretilmelidir
        val balances = listOf(
            WorkspaceMemberBalance(user("debtor"), -100L, Currency.TRY),
            WorkspaceMemberBalance(user("creditor"), 50L, Currency.TRY),
        )
        assertFailsWith<WorkspaceSettlementException.SettlementImbalance> {
            WorkspaceSettlementCalculator.transfersFor(balances, Currency.TRY)
        }
    }

    @Test
    fun calculate_largeAmountsWithinSafeBounds_calculatesCorrectly() {
        // Büyük ancak güvenli sınırlar içindeki tutarlar doğru hesaplanır
        val largeAmount = Money.MAX_AMOUNT_MINOR / 2
        val expense = customExpense(
            id = "large-exp",
            payer = "a",
            amount = largeAmount,
            shares = listOf("a" to largeAmount / 2, "b" to largeAmount - (largeAmount / 2)),
        )

        val result = WorkspaceSettlementCalculator.calculate(listOf(expense))
        assertEquals(0L, result.balances.sumOf { it.netAmountMinor })
        assertEquals(1, result.transfers.size)
        assertEquals(user("b"), result.transfers.first().fromUserId)
        assertEquals(user("a"), result.transfers.first().toUserId)
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

    private fun customExpense(
        id: String,
        payer: String,
        amount: Long,
        shares: List<Pair<String, Long>>,
    ) = WorkspaceSharedExpense(
        id = user(id),
        paidByUserId = user(payer),
        amount = Money(amount, Currency.TRY),
        participantUserIds = shares.map { user(it.first) },
        splitMode = TransactionSplitMode.CUSTOM,
        participantShares = shares.map { TransactionParticipantShare(user(it.first), it.second) },
    )

    private fun user(value: String) = EntityId(value)
}
