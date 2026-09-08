package com.feniqo.mobile.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.CopyBudgetsCommand
import com.feniqo.mobile.domain.model.CreateBudgetCommand
import com.feniqo.mobile.domain.model.DeleteBudgetCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateBudgetCommand
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CopyBudgetsUseCase
import com.feniqo.mobile.domain.usecase.CreateBudgetUseCase
import com.feniqo.mobile.domain.usecase.DeleteBudgetUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveBudgetUseCase
import com.feniqo.mobile.domain.usecase.ObserveBudgetsWithProgressUseCase
import com.feniqo.mobile.domain.usecase.UpdateBudgetUseCase
import com.feniqo.mobile.domain.validation.BudgetValidationError
import com.feniqo.mobile.domain.validation.BudgetValidationResult
import com.feniqo.mobile.domain.validation.BudgetValidationRules
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bütçe listeleme, ilerleme gözlemi ve mutasyon (oluşturma, güncelleme, silme, kopyalama) işlemlerini yöneten ViewModel'dir.
 *
 * Sorumluluklar:
 * 1. Yalnızca use-case ve date provider tüketir; doğrudan DAO veya Supabase çağırmaz.
 * 2. İlk seçili ayı CurrentDateProvider üzerinden YYYY-MM olarak dinamik belirler.
 * 3. BudgetIntent üzerinden ay değişimi, yeniden deneme (retry) ve mutasyonları (CRUD + Copy) yönetir.
 * 4. Seçili ay değiştiğinde önceki akış aboneliğini flatMapLatest ile iptal eder.
 * 5. Tek seferlik olayları (başarı/hata bildirimleri) Channel/Flow üzerinden yayar; StateFlow içine kalıcı mesaj yazmaz.
 * 6. CancellationException'ı yutmaz; doğrudan yeniden fırlatır.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val observeBudgetsWithProgressUseCase: ObserveBudgetsWithProgressUseCase,
    private val observeBudgetUseCase: ObserveBudgetUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    private val copyBudgetsUseCase: CopyBudgetsUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    val initialSelectedMonth: YearMonth by lazy {
        val today = currentDateProvider.today()
        val monthStr = (today.month.ordinal + 1).toString().padStart(2, '0')
        YearMonth("${today.year}-$monthStr")
    }

    private val _editLoadState = MutableStateFlow<BudgetEditLoadState>(BudgetEditLoadState.Idle)
    val editLoadState: StateFlow<BudgetEditLoadState> = _editLoadState.asStateFlow()

    private var editLoadJob: Job? = null

    private val _selectedMonth = MutableStateFlow<YearMonth?>(null)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _mutationState = MutableStateFlow(BudgetMutationState())

    private val _events = Channel<BudgetUiEvent>(Channel.BUFFERED)
    val events: Flow<BudgetUiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            var previousWorkspaceId: EntityId? = null
            var isFirstEmission = true
            observeActiveWorkspaceUseCase().collect { workspace ->
                val currentWorkspaceId = workspace?.id
                if (!isFirstEmission && currentWorkspaceId != previousWorkspaceId) {
                    editLoadJob?.cancel()
                    _editLoadState.value = BudgetEditLoadState.Idle
                    _deleteConfirmation.value = null
                    _copyConfirmation.value = null
                }
                isFirstEmission = false
                previousWorkspaceId = currentWorkspaceId
            }
        }
    }

    private sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(val budgets: List<BudgetProgressDisplayModel>) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = combine(
        _selectedMonth,
        _retryTrigger,
    ) { selected, retryCount ->
        (selected ?: initialSelectedMonth) to retryCount
    }.flatMapLatest { (month, _) ->
        observeBudgetsWithProgressUseCase(month = month)
            .map { items ->
                ObservationResult.Success(BudgetDisplayModelMapper.toDisplayModels(items)) as ObservationResult
            }
            .onStart { emit(ObservationResult.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
            }
    }

    private val _deleteConfirmation = MutableStateFlow<BudgetDeleteConfirmationState?>(null)
    private val _copyConfirmation = MutableStateFlow<BudgetCopyConfirmationState?>(null)

    private val _confirmationsAndWorkspaceFlow = combine(
        _deleteConfirmation,
        _copyConfirmation,
        observeActiveWorkspaceUseCase(),
    ) { deleteConfirmation, copyConfirmation, activeWorkspace ->
        BudgetConfirmationsAndWorkspace(
            deleteConfirmation = deleteConfirmation,
            copyConfirmation = copyConfirmation,
            workspaceName = activeWorkspace?.name,
        )
    }

    val uiState: StateFlow<BudgetsUiState> = combine(
        observationResultFlow,
        _selectedMonth,
        _mutationState,
        _confirmationsAndWorkspaceFlow,
    ) { obsResult, selectedMonth, mutationState, extra ->
        val currentMonth = selectedMonth ?: initialSelectedMonth
        val workspaceName = extra.workspaceName
        val deleteConfirmation = extra.deleteConfirmation
        val copyConfirmation = extra.copyConfirmation
        when (obsResult) {
            is ObservationResult.Loading -> BudgetsUiState(
                isLoading = true,
                selectedMonth = currentMonth,
                budgets = emptyList(),
                observationError = null,
                mutationState = mutationState,
                activeWorkspaceName = workspaceName,
                deleteConfirmation = deleteConfirmation,
                copyConfirmation = copyConfirmation,
            )
            is ObservationResult.Success -> BudgetsUiState(
                isLoading = false,
                selectedMonth = currentMonth,
                budgets = obsResult.budgets,
                observationError = null,
                mutationState = mutationState,
                activeWorkspaceName = workspaceName,
                deleteConfirmation = deleteConfirmation,
                copyConfirmation = copyConfirmation,
            )
            is ObservationResult.Failure -> BudgetsUiState(
                isLoading = false,
                selectedMonth = currentMonth,
                budgets = emptyList(),
                observationError = obsResult.message,
                mutationState = mutationState,
                activeWorkspaceName = workspaceName,
                deleteConfirmation = deleteConfirmation,
                copyConfirmation = copyConfirmation,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(
            isLoading = true,
            selectedMonth = initialSelectedMonth,
            budgets = emptyList(),
            observationError = null,
            mutationState = BudgetMutationState(),
            deleteConfirmation = null,
            copyConfirmation = null,
        ),
    )

    fun processIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.SelectMonth -> selectMonth(intent.month)
            BudgetIntent.Retry -> retry()
            is BudgetIntent.CreateBudget -> createBudget(intent)
            is BudgetIntent.UpdateBudget -> updateBudget(intent)
            is BudgetIntent.RequestDelete -> requestDelete(intent.budget)
            BudgetIntent.DismissDelete -> dismissDelete()
            BudgetIntent.ConfirmDelete -> confirmDelete()
            is BudgetIntent.RequestCopy -> requestCopy(intent.sourceMonth, intent.targetMonth)
            is BudgetIntent.ChangeCopySourceMonth -> changeCopySourceMonth(intent.month)
            BudgetIntent.DismissCopy -> dismissCopy()
            BudgetIntent.ConfirmCopy -> confirmCopy()
            BudgetIntent.ClearFieldErrors -> clearFieldErrors()
        }
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun retry() {
        _retryTrigger.update { it + 1 }
    }

    fun clearFieldErrors() {
        _mutationState.update {
            it.copy(
                categoryError = null,
                amountError = null,
                monthError = null,
                copyError = null,
            )
        }
    }

    fun loadBudgetForEdit(id: EntityId) {
        editLoadJob?.cancel()
        _editLoadState.value = BudgetEditLoadState.Loading
        editLoadJob = viewModelScope.launch {
            try {
                observeBudgetUseCase(id)
                    .catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _editLoadState.value = BudgetEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
                    }
                    .collect { budget ->
                        if (budget == null) {
                            _editLoadState.value = BudgetEditLoadState.NotFound
                        } else {
                            val limitInputText = com.feniqo.mobile.presentation.util.MoneyFormatter.formatMinorUnitsToInputText(
                                budget.limit.amountMinor,
                                budget.limit.currency,
                            )
                            _editLoadState.value = BudgetEditLoadState.Ready(
                                BudgetFormSeed(
                                    budgetId = budget.id,
                                    categoryId = budget.categoryId,
                                    month = budget.month,
                                    limitInput = limitInputText,
                                    currency = budget.limit.currency,
                                ),
                            )
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _editLoadState.value = BudgetEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
            }
        }
    }

    fun setEditLoadInvalidId() {
        editLoadJob?.cancel()
        _editLoadState.value = BudgetEditLoadState.NotFound
    }

    private fun createBudget(intent: BudgetIntent.CreateBudget) {
        var hasError = false
        var categoryError: BudgetFormFieldError? = null
        var monthError: BudgetFormFieldError? = null
        var amountError: BudgetFormFieldError? = null

        val categoryId = intent.categoryId
        if (categoryId == null) {
            hasError = true
            categoryError = BudgetFormFieldError.CATEGORY_REQUIRED
        }

        val month = intent.month
        if (month == null) {
            hasError = true
            monthError = BudgetFormFieldError.MONTH_REQUIRED
        }

        val amountResult = BudgetValidationRules.validateAmount(intent.limitInput, intent.currency)
        val validAmount: Money? = when (amountResult) {
            is BudgetValidationResult.Valid -> amountResult.value
            is BudgetValidationResult.Invalid -> {
                hasError = true
                amountError = mapValidationError(amountResult.error)
                null
            }
        }

        if (hasError || categoryId == null || month == null || validAmount == null) {
            _mutationState.update {
                it.copy(
                    categoryError = categoryError,
                    monthError = monthError,
                    amountError = amountError,
                )
            }
            return
        }

        _mutationState.update {
            it.copy(
                isSubmitting = true,
                categoryError = null,
                monthError = null,
                amountError = null,
            )
        }

        viewModelScope.launch {
            try {
                val command = CreateBudgetCommand(
                    categoryId = categoryId,
                    month = month,
                    limit = validAmount,
                )
                when (val result = createBudgetUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(BudgetUiEvent.MutationSuccess(FinanceUiMessage.BUDGET_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(BudgetUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun updateBudget(intent: BudgetIntent.UpdateBudget) {
        val amountResult = BudgetValidationRules.validateAmount(intent.limitInput, intent.currency)
        val validAmount: Money = when (amountResult) {
            is BudgetValidationResult.Valid -> amountResult.value
            is BudgetValidationResult.Invalid -> {
                _mutationState.update {
                    it.copy(amountError = mapValidationError(amountResult.error))
                }
                return
            }
        }

        _mutationState.update {
            it.copy(
                isSubmitting = true,
                amountError = null,
            )
        }

        viewModelScope.launch {
            try {
                val command = UpdateBudgetCommand(
                    id = intent.id,
                    limit = validAmount,
                )
                when (val result = updateBudgetUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _events.send(BudgetUiEvent.MutationSuccess(FinanceUiMessage.BUDGET_SAVED))
                    }
                    is RepositoryResult.Failure -> {
                        _events.send(BudgetUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun requestDelete(budget: BudgetProgressDisplayModel) {
        if (_deleteConfirmation.value?.isDeleting == true) return
        _deleteConfirmation.value = BudgetDeleteConfirmationState(target = budget, isDeleting = false)
    }

    fun dismissDelete() {
        if (_deleteConfirmation.value?.isDeleting == true) return
        _deleteConfirmation.value = null
    }

    fun confirmDelete() {
        val current = _deleteConfirmation.value ?: return
        if (current.isDeleting) return
        _deleteConfirmation.value = current.copy(isDeleting = true)
        _mutationState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                val command = DeleteBudgetCommand(id = current.target.id)
                when (val result = deleteBudgetUseCase(command)) {
                    is RepositoryResult.Success -> {
                        _deleteConfirmation.value = null
                        _events.send(BudgetUiEvent.MutationSuccess(FinanceUiMessage.BUDGET_DELETED))
                    }
                    is RepositoryResult.Failure -> {
                        _deleteConfirmation.value = null
                        _events.send(BudgetUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteConfirmation.value = null
                _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun requestCopy(sourceMonth: YearMonth, targetMonth: YearMonth) {
        if (_copyConfirmation.value?.isCopying == true) return

        val validationResult = BudgetValidationRules.validateCopyMonths(
            sourceMonth = sourceMonth,
            targetMonth = targetMonth,
        )
        when (validationResult) {
            is BudgetValidationResult.Invalid -> {
                val error = mapValidationError(validationResult.error)
                _mutationState.update { it.copy(copyError = error) }
                viewModelScope.launch {
                    _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.BUDGET_COPY_MONTHS_SAME))
                }
            }
            is BudgetValidationResult.Valid -> {
                _mutationState.update { it.copy(copyError = null) }
                _copyConfirmation.value = BudgetCopyConfirmationState(
                    sourceMonth = sourceMonth,
                    targetMonth = targetMonth,
                    isCopying = false,
                )
            }
        }
    }

    fun changeCopySourceMonth(month: YearMonth) {
        val current = _copyConfirmation.value ?: return
        if (current.isCopying) return

        val validationResult = BudgetValidationRules.validateCopyMonths(
            sourceMonth = month,
            targetMonth = current.targetMonth,
        )
        when (validationResult) {
            is BudgetValidationResult.Invalid -> {
                val error = mapValidationError(validationResult.error)
                _mutationState.update { it.copy(copyError = error) }
            }
            is BudgetValidationResult.Valid -> {
                _mutationState.update { it.copy(copyError = null) }
            }
        }
        _copyConfirmation.value = current.copy(sourceMonth = month)
    }

    fun dismissCopy() {
        if (_copyConfirmation.value?.isCopying == true) return
        _mutationState.update { it.copy(copyError = null) }
        _copyConfirmation.value = null
    }

    fun confirmCopy() {
        val current = _copyConfirmation.value ?: return
        if (current.isCopying) return

        val validationResult = BudgetValidationRules.validateCopyMonths(
            sourceMonth = current.sourceMonth,
            targetMonth = current.targetMonth,
        )
        val validCommand: CopyBudgetsCommand = when (validationResult) {
            is BudgetValidationResult.Valid -> validationResult.value
            is BudgetValidationResult.Invalid -> {
                val error = mapValidationError(validationResult.error)
                _mutationState.update { it.copy(copyError = error) }
                viewModelScope.launch {
                    _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.BUDGET_COPY_MONTHS_SAME))
                }
                return
            }
        }

        _copyConfirmation.value = current.copy(isCopying = true)
        _mutationState.update { it.copy(isSubmitting = true, copyError = null) }

        viewModelScope.launch {
            try {
                when (val result = copyBudgetsUseCase(validCommand)) {
                    is RepositoryResult.Success -> {
                        _copyConfirmation.value = null
                        _events.send(
                            BudgetUiEvent.CopyCompleted(
                                copiedCount = result.value.copiedCount,
                                skippedCount = result.value.skippedCount,
                            ),
                        )
                    }
                    is RepositoryResult.Failure -> {
                        _copyConfirmation.value = null
                        _events.send(BudgetUiEvent.ShowMessage(result.error.toFinanceUiMessage()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _copyConfirmation.value = null
                _events.send(BudgetUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
            } finally {
                _mutationState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun mapValidationError(error: BudgetValidationError): BudgetFormFieldError = when (error) {
        BudgetValidationError.CATEGORY_REQUIRED -> BudgetFormFieldError.CATEGORY_REQUIRED
        BudgetValidationError.MONTH_REQUIRED -> BudgetFormFieldError.MONTH_REQUIRED
        BudgetValidationError.MONTH_INVALID_FORMAT -> BudgetFormFieldError.MONTH_INVALID_FORMAT
        BudgetValidationError.AMOUNT_EMPTY -> BudgetFormFieldError.AMOUNT_REQUIRED
        BudgetValidationError.AMOUNT_INVALID_FORMAT -> BudgetFormFieldError.AMOUNT_INVALID_FORMAT
        BudgetValidationError.AMOUNT_NON_POSITIVE -> BudgetFormFieldError.AMOUNT_NON_POSITIVE
        BudgetValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS -> BudgetFormFieldError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS
        BudgetValidationError.AMOUNT_MAX_EXCEEDED -> BudgetFormFieldError.AMOUNT_MAX_EXCEEDED
        BudgetValidationError.SOURCE_AND_TARGET_MONTH_SAME -> BudgetFormFieldError.SOURCE_AND_TARGET_MONTH_SAME
    }
}

private data class BudgetConfirmationsAndWorkspace(
    val deleteConfirmation: BudgetDeleteConfirmationState?,
    val copyConfirmation: BudgetCopyConfirmationState?,
    val workspaceName: String?,
)
