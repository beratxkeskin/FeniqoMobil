package com.feniqo.mobile.presentation.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SubscriptionAdvanceRenewalDialog
import com.feniqo.mobile.presentation.component.SubscriptionDeleteDialog
import com.feniqo.mobile.presentation.screen.SubscriptionFormScreen
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class SubscriptionDatePickerTarget {
    START_DATE,
    END_DATE,
}

object SubscriptionFormRouteHelper {

    fun resolveCurrentLocalDate(
        instant: Instant,
        timeZone: TimeZone,
    ): LocalDate {
        return instant.toLocalDateTime(timeZone).date
    }

    fun defaultCurrentDateProvider(): LocalDate {
        return resolveCurrentLocalDate(
            instant = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
            timeZone = TimeZone.currentSystemDefault(),
        )
    }

    fun computeInputOnStartDateChange(
        currentInput: SubscriptionFormInput,
        newStartDate: LocalDate,
    ): SubscriptionFormInput {
        val currentEndDate = currentInput.endDate
        val shouldClearEndDate = currentEndDate != null && currentEndDate < newStartDate
        return currentInput.copy(
            startDate = newStartDate,
            endDate = if (shouldClearEndDate) null else currentEndDate,
            nextRenewalDate = if (currentInput.isCreateMode) newStartDate else currentInput.nextRenewalDate,
        )
    }

    fun computeInputOnEndDateChange(
        currentInput: SubscriptionFormInput,
        newEndDate: LocalDate?,
    ): SubscriptionFormInput {
        return currentInput.copy(endDate = newEndDate)
    }

    fun computeInitialDatePickerSelection(
        targetDate: LocalDate?,
        currentDateProvider: () -> LocalDate,
    ): Long {
        val resolvedDate = targetDate ?: currentDateProvider()
        return resolvedDate.toUtcEpochMillis()
    }

    fun computeSubmitResult(
        input: SubscriptionFormInput,
        categories: List<Category>,
    ): SubscriptionFormSubmitResult {
        return when (val result = input.toDraft(categories)) {
            is SubscriptionFormNormalizationResult.Invalid -> {
                SubscriptionFormSubmitResult.ValidationFailed(result.errors)
            }
            is SubscriptionFormNormalizationResult.Valid -> {
                val intent = if (result.draft.isEditMode) {
                    SubscriptionsIntent.Update(result.draft.toUpdateCommand())
                } else {
                    SubscriptionsIntent.Create(result.draft.toCreateCommand())
                }
                SubscriptionFormSubmitResult.IntentReady(intent)
            }
        }
    }

    fun shouldShowDeleteDialog(
        currentSubscriptionId: EntityId?,
        pendingDeleteId: EntityId?,
    ): Boolean {
        if (currentSubscriptionId == null || pendingDeleteId == null) return false
        return currentSubscriptionId == pendingDeleteId
    }

    fun computeRequestDeleteIntent(
        currentSubscriptionId: EntityId?,
    ): SubscriptionsIntent? {
        if (currentSubscriptionId == null) return null
        return SubscriptionsIntent.RequestDelete(currentSubscriptionId)
    }

    fun computeConfirmDeleteIntent(): SubscriptionsIntent {
        return SubscriptionsIntent.ConfirmDelete
    }

    fun computeDismissDeleteIntent(): SubscriptionsIntent {
        return SubscriptionsIntent.DismissDelete
    }

    fun shouldShowAdvanceRenewalDialog(
        currentSubscriptionId: EntityId?,
        pendingAdvanceRenewal: PendingAdvanceRenewalTarget?,
    ): Boolean {
        if (currentSubscriptionId == null || pendingAdvanceRenewal == null) return false
        return currentSubscriptionId == pendingAdvanceRenewal.id
    }

    fun computeRequestAdvanceRenewalIntent(
        currentSubscriptionId: EntityId?,
        nextRenewalDate: LocalDate?,
    ): SubscriptionsIntent? {
        if (currentSubscriptionId == null || nextRenewalDate == null) return null
        return SubscriptionsIntent.RequestAdvanceRenewal(currentSubscriptionId, nextRenewalDate)
    }

    fun computeConfirmAdvanceRenewalIntent(): SubscriptionsIntent {
        return SubscriptionsIntent.ConfirmAdvanceRenewal
    }

    fun computeDismissAdvanceRenewalIntent(): SubscriptionsIntent {
        return SubscriptionsIntent.DismissAdvanceRenewal
    }

    fun computeSetActiveIntent(
        currentSubscriptionId: EntityId?,
        targetActive: Boolean,
    ): SubscriptionsIntent? {
        if (currentSubscriptionId == null) return null
        return SubscriptionsIntent.SetActive(
            SetSubscriptionActiveCommand(
                id = currentSubscriptionId,
                isActive = targetActive,
            ),
        )
    }


    fun isDialogActionEnabled(isSubmitting: Boolean): Boolean {
        return !isSubmitting
    }
}

sealed interface SubscriptionFormSubmitResult {
    data class IntentReady(val intent: SubscriptionsIntent) : SubscriptionFormSubmitResult
    data class ValidationFailed(val errors: SubscriptionFormInputErrors) : SubscriptionFormSubmitResult
}

/**
 * Abonelik oluşturma/düzenleme formu için Android Compose Navigation adaptörüdür.
 * Route-local input durumunu ve platform DatePicker akışını yönetir, ViewModel edit SSOT yükleme akışını bağlar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionFormScreenRoute(
    availableCategories: List<Category>,
    viewModel: SubscriptionsViewModel,
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    initialSubscriptionId: EntityId?,
    hasInvalidRouteId: Boolean = false,
    modifier: Modifier = Modifier,
    currentDateProvider: () -> LocalDate = { SubscriptionFormRouteHelper.defaultCurrentDateProvider() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editLoadState by viewModel.editLoadState.collectAsStateWithLifecycle()

    var input by remember {
        mutableStateOf(
            SubscriptionFormInput(
                subscriptionId = initialSubscriptionId,
                startDate = if (initialSubscriptionId == null) currentDateProvider() else null,
            ),
        )
    }
    var isActive by remember { mutableStateOf(true) }
    var errors by remember { mutableStateOf(SubscriptionFormInputErrors()) }
    var isEditSeedApplied by remember { mutableStateOf(false) }
    var activeDatePicker by remember { mutableStateOf<SubscriptionDatePickerTarget?>(null) }

    val effectiveEditLoadState = remember(hasInvalidRouteId, initialSubscriptionId, editLoadState) {
        if (hasInvalidRouteId) {
            SubscriptionEditLoadState.NotFound
        } else {
            resolveEffectiveSubscriptionEditLoadState(initialSubscriptionId, editLoadState)
        }
    }

    LaunchedEffect(hasInvalidRouteId, initialSubscriptionId) {
        if (hasInvalidRouteId) {
            viewModel.setEditLoadInvalidId()
        } else if (initialSubscriptionId != null) {
            if (initialSubscriptionId.value.isBlank()) {
                viewModel.setEditLoadInvalidId()
            } else {
                viewModel.loadSubscriptionForEdit(initialSubscriptionId)
            }
        }
    }

    LaunchedEffect(effectiveEditLoadState) {
        if (effectiveEditLoadState is SubscriptionEditLoadState.Ready && !isEditSeedApplied) {
            input = SubscriptionFormInput.fromDraft(effectiveEditLoadState.draft)
            isActive = effectiveEditLoadState.isActive
            isEditSeedApplied = true
        }
    }

    // 1. Gönderim sırasında sistem geri hareketini engelleme
    BackHandler(enabled = uiState.mutationState.isSubmitting) {
        // Form submit edilirken yanlışlıkla geri çıkılmasını önler
    }

    // 2. ViewModel tek seferlik olaylarını toplama
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SubscriptionUiEvent.MutationSuccess -> onNavigateBack()
                is SubscriptionUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    // 3. Duruma Göre Ekran Sunumu
    when (effectiveEditLoadState) {
        SubscriptionEditLoadState.Loading -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LoadingContent(
                    message = "Abonelik yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        SubscriptionEditLoadState.NotFound -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "Abonelik Bulunamadı",
                    description = "Düzenlemek istediğiniz abonelik bulunamadı veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        is SubscriptionEditLoadState.Error -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "Abonelik Yüklenemedi",
                    description = effectiveEditLoadState.message.toDisplayText(),
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        SubscriptionEditLoadState.Idle, is SubscriptionEditLoadState.Ready -> {
            val expenseCategories = remember(availableCategories) {
                availableCategories.filter { it.type == TransactionType.EXPENSE }
            }

            SubscriptionFormScreen(
                input = input,
                categories = expenseCategories,
                errors = errors,
                mutationState = uiState.mutationState,
                isEditMode = input.isEditMode,
                isActive = isActive,
                onBack = onNavigateBack,
                onSetActive = { targetActive ->
                    SubscriptionFormRouteHelper.computeSetActiveIntent(
                        currentSubscriptionId = input.subscriptionId,
                        targetActive = targetActive,
                    )?.let { intent ->
                        viewModel.onIntent(intent)
                    }
                },
                onRequestAdvanceRenewal = {
                    SubscriptionFormRouteHelper.computeRequestAdvanceRenewalIntent(
                        currentSubscriptionId = input.subscriptionId,
                        nextRenewalDate = input.nextRenewalDate,
                    )?.let { intent ->
                        viewModel.onIntent(intent)
                    }
                },
                onRequestDelete = {
                    SubscriptionFormRouteHelper.computeRequestDeleteIntent(input.subscriptionId)?.let {
                        viewModel.onIntent(it)
                    }
                },

                onNameChange = {
                    input = input.copy(nameInput = it)
                    errors = errors.copy(nameError = null)
                },
                onAmountChange = {
                    input = input.copy(amountInput = it)
                    errors = errors.copy(amountError = null)
                },
                onCurrencyChange = { newCurrency ->
                    input = input.copy(currency = newCurrency)
                    errors = errors.copy(amountError = null)
                },
                onCategoryChange = {
                    input = input.copy(categoryId = it)
                    errors = errors.copy(categoryError = null)
                },
                onFrequencyChange = {
                    input = input.copy(frequency = it)
                },
                onIntervalChange = {
                    input = input.copy(intervalInput = it)
                    errors = errors.copy(intervalError = null)
                },
                onStartDateClick = {
                    activeDatePicker = SubscriptionDatePickerTarget.START_DATE
                },
                onEndDateClick = {
                    activeDatePicker = SubscriptionDatePickerTarget.END_DATE
                },
                onClearEndDate = {
                    input = input.copy(endDate = null)
                    errors = errors.copy(endDateError = null)
                },
                onSubmit = {
                    when (val submitResult = SubscriptionFormRouteHelper.computeSubmitResult(input, expenseCategories)) {
                        is SubscriptionFormSubmitResult.ValidationFailed -> {
                            errors = submitResult.errors
                        }
                        is SubscriptionFormSubmitResult.IntentReady -> {
                            errors = SubscriptionFormInputErrors()
                            viewModel.onIntent(submitResult.intent)
                        }
                    }
                },
                modifier = modifier,
            )
        }
    }

    // 4. Android Material 3 Tarih Seçici Dialogu
    when (activeDatePicker) {
        SubscriptionDatePickerTarget.START_DATE -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = SubscriptionFormRouteHelper.computeInitialDatePickerSelection(
                    targetDate = input.startDate,
                    currentDateProvider = currentDateProvider,
                ),
            )

            DatePickerDialog(
                onDismissRequest = { activeDatePicker = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedMillis = datePickerState.selectedDateMillis
                            if (selectedMillis != null) {
                                val selectedDate = utcEpochMillisToLocalDate(selectedMillis)
                                input = SubscriptionFormRouteHelper.computeInputOnStartDateChange(input, selectedDate)
                                errors = errors.copy(startDateError = null, endDateError = null)
                            }
                            activeDatePicker = null
                        },
                    ) {
                        Text("Seç")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDatePicker = null }) {
                        Text("İptal")
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
        SubscriptionDatePickerTarget.END_DATE -> {
            val minSelectableDate = remember(input.startDate, input.nextRenewalDate, input.isEditMode) {
                listOfNotNull(
                    input.startDate,
                    if (input.isEditMode) input.nextRenewalDate else null,
                ).maxOrNull()
            }
            val selectableDates = remember(minSelectableDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val minMillis = minSelectableDate?.toUtcEpochMillis() ?: return true
                        return utcTimeMillis >= minMillis
                    }
                }
            }

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = SubscriptionFormRouteHelper.computeInitialDatePickerSelection(
                    targetDate = input.endDate ?: (if (input.isEditMode) input.nextRenewalDate ?: input.startDate else input.startDate),
                    currentDateProvider = currentDateProvider,
                ),
                selectableDates = selectableDates,
            )


            DatePickerDialog(
                onDismissRequest = { activeDatePicker = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedMillis = datePickerState.selectedDateMillis
                            if (selectedMillis != null) {
                                val selectedDate = utcEpochMillisToLocalDate(selectedMillis)
                                input = SubscriptionFormRouteHelper.computeInputOnEndDateChange(input, selectedDate)
                                errors = errors.copy(endDateError = null)
                            }
                            activeDatePicker = null
                        },
                    ) {
                        Text("Seç")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDatePicker = null }) {
                        Text("İptal")
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
        null -> Unit
    }

    // 5. Android Silme Onay Diyaloğu
    val isDeleteDialogOpen = SubscriptionFormRouteHelper.shouldShowDeleteDialog(
        currentSubscriptionId = input.subscriptionId,
        pendingDeleteId = uiState.mutationState.pendingDeleteId,
    )

    if (isDeleteDialogOpen) {
        SubscriptionDeleteDialog(
            isSubmitting = uiState.mutationState.isSubmitting,
            onConfirm = {
                viewModel.onIntent(SubscriptionFormRouteHelper.computeConfirmDeleteIntent())
            },
            onDismiss = {
                viewModel.onIntent(SubscriptionFormRouteHelper.computeDismissDeleteIntent())
            },
        )
    }

    // 6. Android Yenileme İlerletme (Ödendi) Onay Diyaloğu
    val pendingAdvanceRenewal = uiState.mutationState.pendingAdvanceRenewal
    val isAdvanceRenewalDialogOpen = SubscriptionFormRouteHelper.shouldShowAdvanceRenewalDialog(
        currentSubscriptionId = input.subscriptionId,
        pendingAdvanceRenewal = pendingAdvanceRenewal,
    )

    if (isAdvanceRenewalDialogOpen && pendingAdvanceRenewal != null) {
        SubscriptionAdvanceRenewalDialog(
            nextRenewalDate = pendingAdvanceRenewal.nextRenewalDate,
            isSubmitting = uiState.mutationState.isSubmitting,
            onConfirm = {
                viewModel.onIntent(SubscriptionFormRouteHelper.computeConfirmAdvanceRenewalIntent())
            },
            onDismiss = {
                viewModel.onIntent(SubscriptionFormRouteHelper.computeDismissAdvanceRenewalIntent())
            },
        )
    }
}



private fun LocalDate.toUtcEpochMillis(): Long {
    return toEpochDays() * 86_400_000L
}

private fun utcEpochMillisToLocalDate(utcEpochMillis: Long): LocalDate {
    val epochDays = (utcEpochMillis / 86_400_000L).toInt()
    return LocalDate.fromEpochDays(epochDays)
}
