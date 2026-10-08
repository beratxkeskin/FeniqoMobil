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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.rememberGuardedFormExit
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.DebtDatePickerSheet
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.DebtPaymentFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPaymentFormScreenRoute(
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    parentDebtId: EntityId? = null,
    hasInvalidRouteId: Boolean = false,
    viewModel: DebtPaymentFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val parentLoadState by viewModel.parentLoadState.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(parentDebtId, hasInvalidRouteId) {
        if (hasInvalidRouteId) {
            viewModel.setParentLoadInvalidId()
        } else if (parentDebtId != null) {
            viewModel.loadParentDebt(parentDebtId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DebtPaymentFormUiEvent.MutationSuccess -> {
                    onMessage(event.message)
                    onNavigateBack()
                }
                is DebtPaymentFormUiEvent.ShowMessage -> {
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

    val effectiveLoadState = resolveEffectiveDebtPaymentParentLoadState(parentDebtId, parentLoadState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (effectiveLoadState) {
            is DebtPaymentParentLoadState.Loading -> {
                LoadingContent(
                    message = debtRouteLoadingText(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtPaymentParentLoadState.NotFound -> {
                ErrorState(
                    title = debtRouteNotFoundTitleText(),
                    description = debtPaymentRouteNotFoundDescText(),
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = debtRouteBackActionText(),
                )
            }
            is DebtPaymentParentLoadState.Error -> {
                ErrorState(
                    title = debtRouteErrorTitleText(),
                    description = effectiveLoadState.message.toLocalizedText(),
                    onRetry = {
                        if (parentDebtId != null) {
                            viewModel.loadParentDebt(parentDebtId)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtPaymentParentLoadState.Ready -> {
                DebtPaymentFormScreen(
                    parentDebt = effectiveLoadState.debt,
                    remainingAmount = effectiveLoadState.remainingAmount,
                    isSettled = effectiveLoadState.isSettled,
                    totalPaid = effectiveLoadState.totalPaid,
                    input = uiState.input,
                    errors = uiState.errors,
                    isSubmitting = uiState.isSubmitting,
                    onBack = requestExit,
                    onAmountChange = { amount -> viewModel.updateInput { it.copy(amountInput = amount) } },
                    onPaidOnClick = { showDatePicker = true },
                    onSubmit = { viewModel.submit() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            DebtPaymentParentLoadState.Idle -> {
                // Effective state Idle durumunda Loading olarak çözümlenir
            }
        }
    }

    if (showDatePicker) {
        val sheetTitle = if (effectiveLoadState is DebtPaymentParentLoadState.Ready &&
            effectiveLoadState.debt.type == DebtType.RECEIVABLE
        ) {
            debtDatePickerCollectionTitleText()
        } else {
            debtDatePickerPaymentTitleText()
        }
        DebtDatePickerSheet(
            selectedDate = uiState.input.paidOn,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.updateInput { it.copy(paidOn = date) }
                showDatePicker = false
            },
            title = sheetTitle,
        )
    }
}
