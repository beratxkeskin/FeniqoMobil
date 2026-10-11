package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.presentation.common.FinanceUiMessage

enum class WorkspaceJoinFormFieldError {
    CODE_BLANK,
    CODE_INVALID,
}

data class WorkspaceJoinUiState(
    val inviteCode: String = "",
    val codeError: WorkspaceJoinFormFieldError? = null,
    val errorMessage: FinanceUiMessage? = null,
    val isSubmitting: Boolean = false,
) {
    val isFormEnabled: Boolean get() = !isSubmitting
}
