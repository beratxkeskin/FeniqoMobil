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
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.screen.GoalContributionFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalContributionFormScreenRoute(
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
    parentGoalId: EntityId? = null,
    hasInvalidRouteId: Boolean = false,
    viewModel: GoalContributionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val parentLoadState by viewModel.parentLoadState.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(parentGoalId, hasInvalidRouteId) {
        if (hasInvalidRouteId) {
            viewModel.setParentLoadInvalidId()
        } else if (parentGoalId != null) {
            viewModel.loadParentGoal(parentGoalId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GoalContributionFormUiEvent.MutationSuccess -> {
                    onMessage(event.message)
                    onNavigateBack()
                }
                is GoalContributionFormUiEvent.ShowMessage -> {
                    onMessage(event.message)
                }
            }
        }
    }

    BackHandler(enabled = !uiState.isSubmitting) {
        onNavigateBack()
    }

    val effectiveLoadState = resolveEffectiveGoalContributionParentLoadState(parentGoalId, parentLoadState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (effectiveLoadState) {
            is GoalContributionParentLoadState.Loading -> {
                LoadingContent(
                    message = "Hedef bilgileri yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalContributionParentLoadState.NotFound -> {
                ErrorState(
                    title = "Hedef Bulunamadı",
                    description = "Hareket eklemek istediğiniz hedef mevcut değil veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalContributionParentLoadState.Error -> {
                ErrorState(
                    title = "Hedef Yüklenemedi",
                    description = effectiveLoadState.message.toDisplayText(),
                    onRetry = {
                        if (parentGoalId != null) {
                            viewModel.loadParentGoal(parentGoalId)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is GoalContributionParentLoadState.Ready -> {
                GoalContributionFormScreen(
                    parentGoal = effectiveLoadState.goal,
                    input = uiState.input,
                    errors = uiState.errors,
                    isSubmitting = uiState.isSubmitting,
                    onBack = onNavigateBack,
                    onAmountChange = { amount -> viewModel.updateInput { it.copy(amountInput = amount) } },
                    onDirectionChange = { dir -> viewModel.updateInput { it.copy(direction = dir) } },
                    onDateClick = { showDatePicker = true },
                    onNoteChange = { note -> viewModel.updateInput { it.copy(noteInput = note) } },
                    onSubmit = { viewModel.submit() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            GoalContributionParentLoadState.Idle -> {
                // Effective state Idle durumunda Loading olarak çözümlenir
            }
        }
    }

    // Material DatePicker Dialog
    if (showDatePicker) {
        val initialMillis = GoalDebtFormRouteHelper.computeInitialDatePickerSelection(
            targetDate = uiState.input.occurredOn,
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
                            viewModel.updateInput { it.copy(occurredOn = localDate) }
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
}
