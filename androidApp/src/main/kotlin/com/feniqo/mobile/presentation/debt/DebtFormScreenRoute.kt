package com.feniqo.mobile.presentation.debt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.presentation.common.FinanceUiMessage
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

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialDebtId, hasInvalidRouteId) {
        if (hasInvalidRouteId) {
            viewModel.setEditLoadInvalidId()
        } else if (initialDebtId != null) {
            viewModel.loadDebtForEdit(initialDebtId)
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

    BackHandler(enabled = !uiState.isSubmitting) {
        onNavigateBack()
    }

    val effectiveLoadState = resolveEffectiveDebtEditLoadState(initialDebtId, editLoadState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (effectiveLoadState) {
            is DebtEditLoadState.Loading -> {
                LoadingContent(
                    message = "Borç / alacak bilgileri yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtEditLoadState.NotFound -> {
                ErrorState(
                    title = "Kayıt Bulunamadı",
                    description = "Düzenlemek istediğiniz borç / alacak kaydı mevcut değil veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is DebtEditLoadState.Error -> {
                ErrorState(
                    title = "Kayıt Yüklenemedi",
                    description = effectiveLoadState.message.toDisplayText(),
                    onRetry = {
                        if (initialDebtId != null) {
                            viewModel.loadDebtForEdit(initialDebtId)
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
                    onBack = onNavigateBack,
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


    // Material DatePicker Dialog
    if (showDatePicker) {
        val initialMillis = com.feniqo.mobile.presentation.goal.GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = uiState.input.dueDate,
        )
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = com.feniqo.mobile.presentation.goal.GoalDebtFormRouteHelper.utcEpochMillisToLocalDate(millis)
                            viewModel.updateInput { it.copy(dueDate = localDate) }
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Vazgeç")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Onaylı Silme Diyaloğu
    if (uiState.pendingDeleteConfirmation) {
        DebtDeleteDialog(
            isSubmitting = uiState.isSubmitting,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() },
        )
    }
}

