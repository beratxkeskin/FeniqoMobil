@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.debt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.currentLocaleDecimalSeparator
import com.feniqo.mobile.presentation.common.rememberGuardedFormExit
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.DebtDatePickerSheet
import com.feniqo.mobile.presentation.component.DebtDeleteDialog
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.DebtFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtFormScreenRoute(
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    initialDebtId: EntityId? = null,
    hasInvalidRouteId: Boolean = false,
    onAddPayment: ((EntityId) -> Unit)? = null,
    viewModel: DebtFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editLoadState by viewModel.editLoadState.collectAsStateWithLifecycle()
    val decimalSeparator = currentLocaleDecimalSeparator()
    val currentSeparatorState = rememberUpdatedState(decimalSeparator)

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialDebtId, hasInvalidRouteId) {
        if (hasInvalidRouteId) {
            viewModel.setEditLoadInvalidId()
        } else if (initialDebtId != null) {
            viewModel.loadDebtForEdit(initialDebtId, currentSeparatorState.value)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DebtFormUiEvent.MutationSuccess -> {
                    onMessage(event.message)
                    onNavigateBack()
                }
                is DebtFormUiEvent.ShowMessage -> {
                    onMessage(event.message)
                }
            }
        }
    }

    val requestExit = rememberGuardedFormExit(
        isSubmitting = uiState.isSubmitting,
        hasUnsavedChanges = uiState.hasUnsavedChanges,
        onNavigateBack = onNavigateBack,
    )

    val effectiveLoadState = resolveEffectiveDebtEditLoadState(initialDebtId, editLoadState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (effectiveLoadState) {
            is DebtEditLoadState.Loading -> {
                LoadingContent(
                    message = debtRouteLoadingText(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtEditLoadState.NotFound -> {
                ErrorState(
                    title = debtRouteNotFoundTitleText(),
                    description = debtFormRouteNotFoundDescText(),
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = debtRouteBackActionText(),
                )
            }
            is DebtEditLoadState.Error -> {
                ErrorState(
                    title = debtRouteErrorTitleText(),
                    description = effectiveLoadState.message.toLocalizedText(),
                    onRetry = {
                        if (initialDebtId != null) {
                            viewModel.loadDebtForEdit(initialDebtId, decimalSeparator)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtEditLoadState.Ready,
            DebtEditLoadState.Idle -> {
                DebtFormScreen(
                    input = uiState.input,
                    errors = uiState.errors,
                    isSubmitting = uiState.isSubmitting,
                    isEditMode = uiState.input.isEditMode,
                    onBack = requestExit,
                    onTitleChange = { title -> viewModel.updateInput { it.copy(titleInput = title) } },
                    onAmountChange = { amount -> viewModel.updateInput { it.copy(amountInput = amount) } },
                    onCurrencyChange = { currency -> viewModel.updateInput { it.copy(currency = currency) } },
                    onTypeChange = { type -> viewModel.updateInput { it.copy(type = type) } },
                    onDueDateClick = { showDatePicker = true },
                    onDescriptionChange = { desc -> viewModel.updateInput { it.copy(descriptionInput = desc) } },
                    onRequestDelete = { viewModel.requestDelete() },
                    onSubmit = { viewModel.submit() },
                    onAddPayment = if (uiState.input.isEditMode && initialDebtId != null && onAddPayment != null) {
                        { onAddPayment(initialDebtId) }
                    } else null,
                    paymentsHistory = uiState.paymentsHistory,
                    balanceSummary = uiState.balanceSummary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showDatePicker) {
        DebtDatePickerSheet(
            selectedDate = uiState.input.dueDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.updateInput { it.copy(dueDate = date) }
                showDatePicker = false
            },
            title = debtDatePickerDefaultTitleText(),
        )
    }

    if (uiState.pendingDeleteConfirmation) {
        DebtDeleteDialog(
            debtTitle = uiState.input.titleInput.ifBlank { debtDeleteDialogDefaultNameText() },
            isSubmitting = uiState.isSubmitting,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() },
        )
    }
}
