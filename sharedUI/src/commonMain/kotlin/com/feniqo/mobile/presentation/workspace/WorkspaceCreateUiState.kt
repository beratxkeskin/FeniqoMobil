package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.common.FinanceUiMessage

data class WorkspaceCreateUiState(
    val name: String = "",
    val type: WorkspaceType = WorkspaceType.SHARED,
    val currency: Currency = Currency.TRY,
    val description: String = "",
    val nameError: String? = null,
    val errorMessage: FinanceUiMessage? = null,
    val isSubmitting: Boolean = false,
) {
    val isFormEnabled: Boolean get() = !isSubmitting
}
