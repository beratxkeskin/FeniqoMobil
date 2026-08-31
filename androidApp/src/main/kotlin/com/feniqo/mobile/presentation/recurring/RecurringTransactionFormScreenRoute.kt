package com.feniqo.mobile.presentation.recurring

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
import com.feniqo.mobile.domain.model.SetRecurringTransactionActiveCommand
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.RecurringTransactionDeleteDialog
import com.feniqo.mobile.presentation.screen.RecurringTransactionFormScreen
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun CategoryDisplayModel.toDomainCategory(): Category = Category(
    id = id,
    ownerId = null,
    workspaceId = null,
    name = name,
    type = type,
    color = CategoryColor(colorHex),
    icon = iconKey?.takeIf { it.isNotBlank() }?.let(::CategoryIcon),
    isDefault = isDefault,
    createdAt = Instant.fromEpochMilliseconds(0L),
)

enum class RecurringDatePickerTarget {
    START_DATE,
    END_DATE,
}

object RecurringTransactionFormRouteHelper {

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

    fun computeInputOnTypeChange(
        currentInput: RecurringTransactionFormInput,
        newType: TransactionType,
        allCategories: List<Category>,
    ): RecurringTransactionFormInput {
        if (currentInput.type == newType) return currentInput
        val selectedCategory = allCategories.firstOrNull { it.id == currentInput.categoryId }
        val shouldClearCategory = selectedCategory == null || selectedCategory.type != newType
        return currentInput.copy(
            type = newType,
            categoryId = if (shouldClearCategory) null else currentInput.categoryId,
        )
    }

    fun computeInputOnStartDateChange(
        currentInput: RecurringTransactionFormInput,
        newStartDate: LocalDate,
    ): RecurringTransactionFormInput {
        val currentEndDate = currentInput.endDate
        val shouldClearEndDate = currentEndDate != null && currentEndDate < newStartDate
        return currentInput.copy(
            startDate = newStartDate,
            endDate = if (shouldClearEndDate) null else currentEndDate,
        )
    }

    fun computeInputOnEndDateChange(
        currentInput: RecurringTransactionFormInput,
        newEndDate: LocalDate?,
    ): RecurringTransactionFormInput {
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
        input: RecurringTransactionFormInput,
        categories: List<Category>,
    ): RecurringFormSubmitResult {
        return when (val result = input.toDraft(categories)) {
            is RecurringTransactionFormNormalizationResult.Invalid -> {
                RecurringFormSubmitResult.ValidationFailed(result.errors)
            }
            is RecurringTransactionFormNormalizationResult.Valid -> {
                val intent = if (result.draft.isEditMode) {
                    RecurringTransactionsIntent.Update(result.draft.toUpdateCommand())
                } else {
                    RecurringTransactionsIntent.Create(result.draft.toCreateCommand())
                }
                RecurringFormSubmitResult.IntentReady(intent)
            }
        }
    }

    fun shouldShowDeleteDialog(
        currentRecurringTransactionId: EntityId?,
        pendingDeleteId: EntityId?,
    ): Boolean {
        if (currentRecurringTransactionId == null || pendingDeleteId == null) return false
        return currentRecurringTransactionId == pendingDeleteId
    }

    fun computeRequestDeleteIntent(
        currentRecurringTransactionId: EntityId?,
    ): RecurringTransactionsIntent? {
        if (currentRecurringTransactionId == null) return null
        return RecurringTransactionsIntent.RequestDelete(currentRecurringTransactionId)
    }

    fun computeConfirmDeleteIntent(): RecurringTransactionsIntent {
        return RecurringTransactionsIntent.ConfirmDelete
    }

    fun computeDismissDeleteIntent(): RecurringTransactionsIntent {
        return RecurringTransactionsIntent.DismissDelete
    }

    fun computeSetActiveIntent(
        currentRecurringTransactionId: EntityId?,
        targetActive: Boolean,
    ): RecurringTransactionsIntent? {
        if (currentRecurringTransactionId == null) return null
        return RecurringTransactionsIntent.SetActive(
            SetRecurringTransactionActiveCommand(
                id = currentRecurringTransactionId,
                isActive = targetActive,
            ),
        )
    }

    fun isDialogActionEnabled(isSubmitting: Boolean): Boolean {
        return !isSubmitting
    }
}

sealed interface RecurringFormSubmitResult {
    data class IntentReady(val intent: RecurringTransactionsIntent) : RecurringFormSubmitResult
    data class ValidationFailed(val errors: RecurringTransactionFormInputErrors) : RecurringFormSubmitResult
}

/**
 * Tekrarlayan işlem oluşturma/düzenleme formu için Android Compose Navigation adaptörüdür.
 * Route-local input durumunu ve platform DatePicker akışını yönetir, ViewModel edit SSOT yükleme akışını bağlar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionFormScreenRoute(
    availableCategories: List<Category>,
    viewModel: RecurringTransactionsViewModel,
    onNavigateBack: () -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    initialRecurringTransactionId: EntityId?,
    hasInvalidRouteId: Boolean = false,
    modifier: Modifier = Modifier,
    currentDateProvider: () -> LocalDate = { RecurringTransactionFormRouteHelper.defaultCurrentDateProvider() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editLoadState by viewModel.editLoadState.collectAsStateWithLifecycle()

    var input by remember {
        mutableStateOf(
            RecurringTransactionFormInput(recurringTransactionId = initialRecurringTransactionId),
        )
    }
    var isActive by remember { mutableStateOf(true) }
    var errors by remember { mutableStateOf(RecurringTransactionFormInputErrors()) }
    var isEditSeedApplied by remember { mutableStateOf(false) }
    var activeDatePicker by remember { mutableStateOf<RecurringDatePickerTarget?>(null) }

    val effectiveEditLoadState = remember(hasInvalidRouteId, initialRecurringTransactionId, editLoadState) {
        if (hasInvalidRouteId) {
            RecurringTransactionEditLoadState.NotFound
        } else {
            resolveEffectiveRecurringEditLoadState(initialRecurringTransactionId, editLoadState)
        }
    }

    LaunchedEffect(hasInvalidRouteId, initialRecurringTransactionId) {
        if (hasInvalidRouteId) {
            viewModel.setEditLoadInvalidId()
        } else if (initialRecurringTransactionId != null) {
            if (initialRecurringTransactionId.value.isBlank()) {
                viewModel.setEditLoadInvalidId()
            } else {
                viewModel.loadRecurringForEdit(initialRecurringTransactionId)
            }
        }
    }

    LaunchedEffect(effectiveEditLoadState) {
        if (effectiveEditLoadState is RecurringTransactionEditLoadState.Ready && !isEditSeedApplied) {
            input = RecurringTransactionFormInput.fromDraft(effectiveEditLoadState.draft)
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
                is RecurringTransactionUiEvent.MutationSuccess -> onNavigateBack()
                is RecurringTransactionUiEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    // 3. Duruma Göre Ekran Sunumu
    when (effectiveEditLoadState) {
        RecurringTransactionEditLoadState.Loading -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LoadingContent(
                    message = "Tekrarlayan işlem yükleniyor...",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        RecurringTransactionEditLoadState.NotFound -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "İşlem Bulunamadı",
                    description = "Düzenlemek istediğiniz tekrarlayan işlem bulunamadı veya silinmiş.",
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        is RecurringTransactionEditLoadState.Error -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ErrorState(
                    title = "İşlem Yüklenemedi",
                    description = effectiveEditLoadState.message.toDisplayText(),
                    onRetry = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        RecurringTransactionEditLoadState.Idle, is RecurringTransactionEditLoadState.Ready -> {
            val filteredCategories = remember(availableCategories, input.type) {
                availableCategories.filter { it.type == input.type }
            }

            RecurringTransactionFormScreen(
                input = input,
                categories = filteredCategories,
                errors = errors,
                mutationState = uiState.mutationState,
                isEditMode = input.isEditMode,
                isActive = isActive,
                onBack = onNavigateBack,
                onSetActive = { targetActive ->
                    RecurringTransactionFormRouteHelper.computeSetActiveIntent(
                        currentRecurringTransactionId = input.recurringTransactionId,
                        targetActive = targetActive,
                    )?.let { intent ->
                        viewModel.onIntent(intent)
                    }
                },
                onRequestDelete = {
                    RecurringTransactionFormRouteHelper.computeRequestDeleteIntent(input.recurringTransactionId)?.let {
                        viewModel.onIntent(it)
                    }
                },
                onAmountChange = {
                    input = input.copy(amountInput = it)
                    errors = errors.copy(amountError = null)
                },
                onTypeChange = { newType ->
                    input = RecurringTransactionFormRouteHelper.computeInputOnTypeChange(
                        currentInput = input,
                        newType = newType,
                        allCategories = availableCategories,
                    )
                    errors = errors.copy(categoryError = null)
                },
                onCategoryChange = {
                    input = input.copy(categoryId = it)
                    errors = errors.copy(categoryError = null)
                },
                onDescriptionChange = {
                    input = input.copy(description = it)
                    errors = errors.copy(descriptionError = null)
                },
                onPaymentMethodChange = {
                    input = input.copy(paymentMethod = it)
                },
                onFrequencyChange = {
                    input = input.copy(frequency = it)
                },
                onIntervalChange = {
                    input = input.copy(intervalInput = it)
                    errors = errors.copy(intervalError = null)
                },
                onStartDateClick = {
                    activeDatePicker = RecurringDatePickerTarget.START_DATE
                },
                onEndDateClick = {
                    activeDatePicker = RecurringDatePickerTarget.END_DATE
                },
                onClearEndDate = {
                    input = input.copy(endDate = null)
                    errors = errors.copy(endDateError = null)
                },
                onSubmit = {
                    when (val submitResult = RecurringTransactionFormRouteHelper.computeSubmitResult(input, availableCategories)) {
                        is RecurringFormSubmitResult.ValidationFailed -> {
                            errors = submitResult.errors
                        }
                        is RecurringFormSubmitResult.IntentReady -> {
                            errors = RecurringTransactionFormInputErrors()
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
        RecurringDatePickerTarget.START_DATE -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = RecurringTransactionFormRouteHelper.computeInitialDatePickerSelection(
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
                                input = RecurringTransactionFormRouteHelper.computeInputOnStartDateChange(input, selectedDate)
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
        RecurringDatePickerTarget.END_DATE -> {
            val minSelectableDate = input.startDate
            val selectableDates = remember(minSelectableDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val minMillis = minSelectableDate?.toUtcEpochMillis() ?: return true
                        return utcTimeMillis >= minMillis
                    }
                }
            }

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = RecurringTransactionFormRouteHelper.computeInitialDatePickerSelection(
                    targetDate = input.endDate ?: input.startDate,
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
                                input = RecurringTransactionFormRouteHelper.computeInputOnEndDateChange(input, selectedDate)
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
    val isDeleteDialogOpen = RecurringTransactionFormRouteHelper.shouldShowDeleteDialog(
        currentRecurringTransactionId = input.recurringTransactionId,
        pendingDeleteId = uiState.mutationState.pendingDeleteId,
    )

    if (isDeleteDialogOpen) {
        RecurringTransactionDeleteDialog(
            isSubmitting = uiState.mutationState.isSubmitting,
            onConfirm = {
                viewModel.onIntent(RecurringTransactionFormRouteHelper.computeConfirmDeleteIntent())
            },
            onDismiss = {
                viewModel.onIntent(RecurringTransactionFormRouteHelper.computeDismissDeleteIntent())
            },
        )
    }
}

internal fun LocalDate.toUtcEpochMillis(): Long {
    return toEpochDays() * 86_400_000L
}

internal fun utcEpochMillisToLocalDate(utcEpochMillis: Long): LocalDate {
    val epochDays = (utcEpochMillis / 86_400_000L).toInt()
    return LocalDate.fromEpochDays(epochDays)
}
