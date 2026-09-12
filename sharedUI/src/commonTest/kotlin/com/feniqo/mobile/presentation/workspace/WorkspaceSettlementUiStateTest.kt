package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceSettlementUiStateTest {

    @Test
    fun defaultState_isLoadingTrue_andEmptyFalse() {
        val state = WorkspaceSettlementUiState()
        assertTrue(state.isLoading)
        assertFalse(state.isEmpty)
        assertFalse(state.isAllSettled)
    }

    @Test
    fun loadedState_withNoBalances_isEmptyTrue() {
        val state = WorkspaceSettlementUiState(
            isLoading = false,
            errorMessage = null,
            memberBalances = emptyList(),
        )
        assertTrue(state.isEmpty)
        assertFalse(state.isAllSettled)
    }

    @Test
    fun loadedState_withBalancesAndNoTransfers_isAllSettledTrue() {
        val memberBalance = WorkspaceMemberBalanceUiModel(
            userId = EntityId("user-1"),
            displayName = "Siz",
            role = WorkspaceRole.OWNER,
            isCurrentUser = true,
            netAmountMinor = 0L,
            formattedAmount = "₺0,00",
            status = MemberBalanceStatus.SETTLED,
        )
        val state = WorkspaceSettlementUiState(
            isLoading = false,
            errorMessage = null,
            memberBalances = listOf(memberBalance),
            suggestedTransfers = emptyList(),
        )
        assertFalse(state.isEmpty)
        assertTrue(state.isAllSettled)
    }

    @Test
    fun loadedState_withTransfers_isAllSettledFalse() {
        val memberBalance = WorkspaceMemberBalanceUiModel(
            userId = EntityId("user-1"),
            displayName = "Siz",
            role = WorkspaceRole.OWNER,
            isCurrentUser = true,
            netAmountMinor = 5000L,
            formattedAmount = "+₺50,00",
            status = MemberBalanceStatus.CREDITOR,
        )
        val transfer = WorkspaceSettlementTransferUiModel(
            fromUserId = EntityId("user-2"),
            fromDisplayName = "Kullanıcı 2",
            toUserId = EntityId("user-1"),
            toDisplayName = "Siz",
            amount = Money(5000L, Currency.TRY),
            formattedAmount = "₺50,00",
            suggestionText = "Kullanıcı 2, Siz kişisine ₺50,00 ödesin.",
        )
        val state = WorkspaceSettlementUiState(
            isLoading = false,
            errorMessage = null,
            memberBalances = listOf(memberBalance),
            suggestedTransfers = listOf(transfer),
        )
        assertFalse(state.isEmpty)
        assertFalse(state.isAllSettled)
    }

    @Test
    fun errorState_isEmptyFalse_andIsAllSettledFalse() {
        val state = WorkspaceSettlementUiState(
            isLoading = false,
            errorMessage = FinanceUiMessage.WORKSPACE_NOT_FOUND,
            memberBalances = emptyList(),
        )
        assertFalse(state.isEmpty)
        assertFalse(state.isAllSettled)
    }
}
