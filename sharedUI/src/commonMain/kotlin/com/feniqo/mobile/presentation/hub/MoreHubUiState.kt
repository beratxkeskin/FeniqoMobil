package com.feniqo.mobile.presentation.hub

/** Daha Fazla genel bakış kartlarının birbirinden bağımsız görünüm durumudur. */
sealed interface MoreHubOverviewCardState {
    data object Loading : MoreHubOverviewCardState

    data class Content(
        val primaryText: String,
        val secondaryText: String? = null,
    ) : MoreHubOverviewCardState

    data class Empty(val message: String) : MoreHubOverviewCardState

    data class Error(val message: String = "Veriler şu an yüklenemedi.") : MoreHubOverviewCardState
}

/** Daha Fazla ekranının Room SSOT akışlarından türetilen durumsuz sunum modeli. */
data class MoreHubUiState(
    val activeWorkspaceName: String? = null,
    val assets: MoreHubOverviewCardState = MoreHubOverviewCardState.Loading,
    val goals: MoreHubOverviewCardState = MoreHubOverviewCardState.Loading,
    val subscriptions: MoreHubOverviewCardState = MoreHubOverviewCardState.Loading,
    val recurringTransactions: MoreHubOverviewCardState = MoreHubOverviewCardState.Loading,
)
