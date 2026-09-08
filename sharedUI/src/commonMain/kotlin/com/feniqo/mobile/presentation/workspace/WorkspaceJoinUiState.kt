package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.presentation.common.FinanceUiMessage

data class WorkspaceJoinUiState(
    val inviteCode: String = "",
    val codeError: String? = null,
    val errorMessage: FinanceUiMessage? = null,
    val isSubmitting: Boolean = false,
) {
    val isFormEnabled: Boolean get() = !isSubmitting
}
