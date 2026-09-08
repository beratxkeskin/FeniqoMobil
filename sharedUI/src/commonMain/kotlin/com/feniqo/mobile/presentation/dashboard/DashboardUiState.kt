package com.feniqo.mobile.presentation.dashboard

import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Dashboard ekranı UI durum modelidir.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardDisplayModel? = null,
    val selectedMonth: YearMonth? = null,
    val activeWorkspaceName: String? = null,
    val userMessage: FinanceUiMessage? = null,
    val observationError: FinanceUiMessage? = null,
)
