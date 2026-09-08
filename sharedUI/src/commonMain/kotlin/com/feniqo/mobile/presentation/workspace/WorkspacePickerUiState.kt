package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Workspace seçici ekranında bir çalışma alanını temsil eden UI modeli.
 */
data class WorkspacePickerItemUiModel(
    val id: EntityId,
    val name: String,
    val isOwner: Boolean,
    val isActive: Boolean,
)

/**
 * Workspace seçici ekranının UI durum modelidir.
 */
data class WorkspacePickerUiState(
    val isLoading: Boolean = true,
    val isPersonalModeActive: Boolean = true,
    val activeWorkspaceId: EntityId? = null,
    val workspaces: List<WorkspacePickerItemUiModel> = emptyList(),
    val isSelecting: Boolean = false,
    val errorMessage: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && workspaces.isEmpty()
}
