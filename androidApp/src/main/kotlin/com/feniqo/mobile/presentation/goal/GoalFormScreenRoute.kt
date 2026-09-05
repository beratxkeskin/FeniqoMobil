package com.feniqo.mobile.presentation.goal

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
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.GoalDeleteDialog
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.GoalFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreenRoute(
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    initialGoalId: EntityId? = null,
    hasInvalidRouteId: Boolean = false,
    onAddContribution: ((EntityId) -> Unit)? = null,
    viewModel: GoalFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editLoadState by viewModel.editLoadState.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialGoalId, hasInvalidRouteId) {
        if (hasInvalidRouteId) {
            viewModel.setEditLoadInvalidId()
        } else if (initialGoalId != null) {
            viewModel.loadGoalForEdit(initialGoalId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GoalFormUiEvent.MutationSuccess -> {
                    onMessage(event.message)
                    onNavigateBack()
                }
                is GoalFormUiEvent.ShowMessage -> {
                    onMessage(event.message)
                }
            }
        }
    }

    BackHandler(enabled = !uiState.isSubmitting) {
        onNavigateBack()
    }

    val effectiveLoadState = resolveEffectiveGoalEditLoadState(initialGoalId, editLoadState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (effectiveLoadState) {
            is GoalEditLoadState.Loading -> {
                LoadingContent(
                    message = "Hedef bilgileri yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalEditLoadState.NotFound -> {
                ErrorState(
                    title = "Hedef Bulunamadı",
                    description = "Düzenlemek istediğiniz hedef mevcut değil veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalEditLoadState.Error -> {
                ErrorState(
                    title = "Hedef Yüklenemedi",
                    description = effectiveLoadState.message.toDisplayText(),
                    onRetry = {
                        if (initialGoalId != null) {
                            viewModel.loadGoalForEdit(initialGoalId)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalEditLoadState.Ready,
            GoalEditLoadState.Idle -> {
                GoalFormScreen(
                    input = uiState.input,
                    errors = uiState.errors,
                    isSubmitting = uiState.isSubmitting,
                    isEditMode = uiState.input.isEditMode,
                    onBack = onNavigateBack,
                    onNameChange = { name -> viewModel.updateInput { it.copy(nameInput = name) } },
                    onTargetAmountChange = { amount -> viewModel.updateInput { it.copy(targetAmountInput = amount) } },
                    onCurrencyChange = { currency -> viewModel.updateInput { it.copy(currency = currency) } },
                    onInitialAmountChange = { initAmount -> viewModel.updateInput { it.copy(initialAmountInput = initAmount) } },
                    onTargetDateClick = { showDatePicker = true },
                    onColorChange = { color -> viewModel.updateInput { it.copy(colorHex = color) } },
                    onRequestDelete = { viewModel.requestDelete() },
                    onSubmit = { viewModel.submit() },
                    onAddContribution = if (uiState.input.isEditMode && initialGoalId != null && onAddContribution != null) {
                        { onAddContribution(initialGoalId) }
                    } else null,
                    contributionsHistory = uiState.contributionsHistory,
                    modifier = Modifier.fillMaxSize(),
                )

            }
        }
    }


    // Material DatePicker Dialog
    if (showDatePicker) {
        val initialMillis = GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = uiState.input.targetDate,
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
                            val localDate = GoalDebtFormRouteHelper.utcEpochMillisToLocalDate(millis)
                            viewModel.updateInput { it.copy(targetDate = localDate) }
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
        GoalDeleteDialog(
            isSubmitting = uiState.isSubmitting,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() },
        )
    }
}

