package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddInstallmentGroupCommand
import com.feniqo.mobile.domain.usecase.AddInstallmentGroupUseCase
import com.feniqo.mobile.domain.usecase.AddTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.domain.usecase.TransactionCommand
import com.feniqo.mobile.domain.usecase.UpdateTransactionUseCase
import com.feniqo.mobile.domain.validation.InstallmentPlanCalculator
import com.feniqo.mobile.domain.validation.InstallmentPlanError
import com.feniqo.mobile.domain.validation.InstallmentPlanResult
import com.feniqo.mobile.domain.validation.TransactionValidationError
import com.feniqo.mobile.domain.validation.TransactionValidationResult
import com.feniqo.mobile.domain.validation.TransactionValidationRules
import com.feniqo.mobile.navigation.TransactionFormRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.CurrentInstantProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * İşlem formu tek seferlik tekil olaylarıdır.
 */
sealed interface TransactionFormEvent {
    data object NavigateBack : TransactionFormEvent
}

private data class CategoryPipelineInput(
    val type: TransactionType,
    val workspaceId: EntityId?,
    val historicalCategory: TransactionCategoryOptionUiModel?,
    val retryGeneration: Int,
)

/**
 * İşlem ekleme ve düzenleme formunun iş mantığını, doğrulamasını ve use-case koordinasyonunu yönetir.
 */
@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val addInstallmentGroupUseCase: AddInstallmentGroupUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val observeTransactionUseCase: ObserveTransactionUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    private val currentDateProvider: CurrentDateProvider,
    private val currentInstantProvider: CurrentInstantProvider,
    private val entityIdGenerator: EntityIdGenerator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val targetTransactionId: EntityId?
    private val initialLoadError: FinanceUiMessage?
    private val isEditMode: Boolean

    init {
        val routeResult = try {
            savedStateHandle.toRoute<TransactionFormRoute>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (routeResult == null) {
            isEditMode = true
            targetTransactionId = null
            initialLoadError = FinanceUiMessage.TRANSACTION_NOT_FOUND
        } else {
            val rawId = routeResult.transactionId
            when {
                rawId == null -> {
                    isEditMode = false
                    targetTransactionId = null
                    initialLoadError = null
                }
                rawId.isBlank() -> {
                    isEditMode = true
                    targetTransactionId = null
                    initialLoadError = FinanceUiMessage.TRANSACTION_NOT_FOUND
                }
                else -> {
                    isEditMode = true
                    val parsedId = try {
                        EntityId(rawId.trim())
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (parsedId == null) {
                        targetTransactionId = null
                        initialLoadError = FinanceUiMessage.TRANSACTION_NOT_FOUND
                    } else {
                        targetTransactionId = parsedId
                        initialLoadError = null
                    }
                }
            }
        }
    }

    private val _typeState = MutableStateFlow(TransactionType.EXPENSE)
    private val _workspaceIdState = MutableStateFlow<EntityId?>(null)
    private val _historicalCategoryState = MutableStateFlow<TransactionCategoryOptionUiModel?>(null)
    private val _retryTrigger = MutableStateFlow(0)

    private var existingReceiptPath: ReceiptPath? = null

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            transactionDate = currentDateProvider.today(),
            type = TransactionType.EXPENSE,
            isEditMode = isEditMode,
            isLoadingTransaction = isEditMode && initialLoadError == null,
            loadError = initialLoadError,
        ),
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<TransactionFormEvent>(Channel.BUFFERED)
    val events: Flow<TransactionFormEvent> = _events.receiveAsFlow()

    private var submitJob: Job? = null

    init {
        setupCategoryObservationPipeline()

        if (targetTransactionId != null && initialLoadError == null) {
            loadExistingTransaction(targetTransactionId)
        }
    }

    /**
     * ViewModel yaşam döngüsü boyunca tek bir reaktif kategori akışı çalıştırır.
     * Tür, çalışma alanı, tarihsel kategori veya retry tetiklendiğinde tek pipeline üzerinden güncellenir.
     */
    private fun setupCategoryObservationPipeline() {
        combine(_typeState, _workspaceIdState, _historicalCategoryState, _retryTrigger) { type, wsId, histCat, retry ->
            CategoryPipelineInput(type, wsId, histCat, retry)
        }
            .distinctUntilChanged()
            .flatMapLatest { input ->
                observeCategoriesUseCase(type = input.type, workspaceId = input.workspaceId)
                    .map { categories ->
                        val activeOptions = categories.map { cat ->
                            TransactionCategoryOptionUiModel(
                                id = cat.id,
                                name = cat.name,
                                type = cat.type,
                                colorHex = cat.color.hex,
                                iconKey = cat.icon?.key,
                                isSelectable = true,
                                isHistorical = false,
                            )
                        }.sortedWith(compareBy({ it.name }, { it.id.value }))

                        val finalOptions = if (input.historicalCategory != null &&
                            input.historicalCategory.type == input.type &&
                            activeOptions.none { it.id == input.historicalCategory.id }
                        ) {
                            listOf(input.historicalCategory) + activeOptions
                        } else {
                            activeOptions
                        }

                        _uiState.update { state ->
                            state.copy(
                                availableCategories = finalOptions,
                                categoryLoadError = null,
                            )
                        }
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        if (e !is Exception) throw e
                        _uiState.update { state ->
                            state.copy(categoryLoadError = FinanceUiMessage.GENERIC_ERROR)
                        }
                    }
            }
            .launchIn(viewModelScope)
    }

    fun retryCategories() {
        _retryTrigger.update { it + 1 }
    }

    private fun loadExistingTransaction(id: EntityId) {
        viewModelScope.launch {
            try {
                val transaction = observeTransactionUseCase(id).first()
                if (transaction == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingTransaction = false,
                            loadError = FinanceUiMessage.TRANSACTION_NOT_FOUND,
                        )
                    }
                    return@launch
                }

                _workspaceIdState.value = transaction.workspaceId
                _typeState.value = transaction.type
                existingReceiptPath = transaction.receiptPath

                val historyCategories = observeCategoriesForHistoryLookupUseCase(transaction.workspaceId).first()
                val matchedHistoryCat = historyCategories.find { it.id == transaction.categoryId }
                if (matchedHistoryCat != null) {
                    _historicalCategoryState.value = TransactionCategoryOptionUiModel(
                        id = matchedHistoryCat.id,
                        name = "${matchedHistoryCat.name} (Silinmiş)",
                        type = matchedHistoryCat.type,
                        colorHex = matchedHistoryCat.color.hex,
                        iconKey = matchedHistoryCat.icon?.key,
                        isSelectable = false,
                        isHistorical = true,
                    )
                }

                val amountText = formatMinorUnitsToInputText(transaction.amount.amountMinor, transaction.amount.currency)

                _uiState.update {
                    it.copy(
                        amountText = amountText,
                        currency = transaction.amount.currency,
                        type = transaction.type,
                        selectedCategoryId = transaction.categoryId,
                        transactionDate = transaction.transactionDate,
                        description = transaction.description ?: "",
                        paymentMethod = transaction.paymentMethod,
                        isInstallmentEnabled = false,
                        existingInstallment = transaction.installment?.let { inst ->
                            InstallmentDisplayModel(
                                number = inst.number,
                                total = inst.total,
                                badgeText = "${inst.number}/${inst.total}",
                            )
                        },
                        isLoadingTransaction = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingTransaction = false,
                        loadError = FinanceUiMessage.GENERIC_ERROR,
                    )
                }
            }
        }
    }

    fun onAmountChanged(amountText: String) {
        _uiState.update {
            it.copy(
                amountText = amountText,
                amountError = null,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun onCurrencyChanged(currency: Currency) {
        _uiState.update {
            it.copy(
                currency = currency,
                amountError = null,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun onTypeChanged(type: TransactionType) {
        if (_typeState.value == type) return

        _typeState.value = type
        _uiState.update {
            val isInstallmentAvailable = !it.isEditMode && type == TransactionType.EXPENSE && it.paymentMethod == PaymentMethod.CREDIT_CARD
            it.copy(
                type = type,
                selectedCategoryId = null,
                categoryError = null,
                isInstallmentEnabled = if (!isInstallmentAvailable) false else it.isInstallmentEnabled,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun onCategoryChanged(categoryId: EntityId?) {
        if (categoryId == null) {
            _uiState.update {
                it.copy(
                    selectedCategoryId = null,
                    categoryError = null,
                    generalMessage = null,
                )
            }
            return
        }

        val option = _uiState.value.availableCategories.find { it.id == categoryId }
        if (option != null && option.isSelectable) {
            _uiState.update {
                it.copy(
                    selectedCategoryId = categoryId,
                    categoryError = null,
                    generalMessage = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    categoryError = TransactionFormFieldError.CATEGORY_UNAVAILABLE,
                    generalMessage = null,
                )
            }
        }
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.update {
            it.copy(
                transactionDate = date,
                dateError = null,
                generalMessage = null,
            )
        }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update {
            it.copy(
                description = description,
                descriptionError = null,
                generalMessage = null,
            )
        }
    }

    fun onPaymentMethodChanged(paymentMethod: PaymentMethod) {
        _uiState.update {
            val isInstallmentAvailable = !it.isEditMode && it.type == TransactionType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD
            it.copy(
                paymentMethod = paymentMethod,
                isInstallmentEnabled = if (!isInstallmentAvailable) false else it.isInstallmentEnabled,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun onInstallmentToggle(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isInstallmentEnabled = enabled,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun onInstallmentCountChanged(countText: String) {
        _uiState.update {
            it.copy(
                installmentCountText = countText,
                installmentCountError = null,
                generalMessage = null,
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(generalMessage = null) }
    }

    fun submit() {
        val currentState = _uiState.value
        if (currentState.loadError != null || currentState.isSubmitting || submitJob?.isActive == true) return

        if (currentState.isEditMode && targetTransactionId == null) {
            _uiState.update { it.copy(loadError = FinanceUiMessage.TRANSACTION_NOT_FOUND) }
            return
        }

        val today = currentDateProvider.today()

        // 1. Tutar Doğrulaması
        val amountValidation = TransactionValidationRules.validateAmount(currentState.amountText, currentState.currency)
        val amountMinor: Long? = when (amountValidation) {
            is TransactionValidationResult.Valid -> amountValidation.value
            is TransactionValidationResult.Invalid -> null
        }
        var amountError: TransactionFormFieldError? = when ((amountValidation as? TransactionValidationResult.Invalid)?.error) {
            TransactionValidationError.AMOUNT_EMPTY -> TransactionFormFieldError.AMOUNT_REQUIRED
            TransactionValidationError.AMOUNT_INVALID_FORMAT,
            TransactionValidationError.AMOUNT_EXCESSIVE_DECIMAL_DIGITS -> TransactionFormFieldError.AMOUNT_INVALID
            TransactionValidationError.AMOUNT_NON_POSITIVE -> TransactionFormFieldError.AMOUNT_NON_POSITIVE
            TransactionValidationError.AMOUNT_MAX_EXCEEDED -> TransactionFormFieldError.AMOUNT_TOO_LARGE
            else -> null
        }

        // 2. Kategori Doğrulaması
        val categoryValidation = TransactionValidationRules.validateCategory(currentState.selectedCategoryId)
        var categoryError: TransactionFormFieldError? = if (categoryValidation is TransactionValidationResult.Invalid) {
            TransactionFormFieldError.CATEGORY_REQUIRED
        } else {
            val selectedOption = currentState.availableCategories.find { it.id == currentState.selectedCategoryId }
            if (selectedOption == null || !selectedOption.isSelectable) {
                TransactionFormFieldError.CATEGORY_UNAVAILABLE
            } else {
                null
            }
        }

        // 3. Tarih Doğrulaması
        val date = currentState.transactionDate
        val dateError: TransactionFormFieldError? = if (date == null) {
            TransactionFormFieldError.DATE_REQUIRED
        } else {
            when (TransactionValidationRules.validateDate(date, today)) {
                is TransactionValidationResult.Valid -> null
                is TransactionValidationResult.Invalid -> TransactionFormFieldError.DATE_IN_FUTURE
            }
        }

        // 4. Açıklama Doğrulaması
        val descValidation = TransactionValidationRules.validateDescription(currentState.description)
        val descriptionError: TransactionFormFieldError? = if (descValidation is TransactionValidationResult.Invalid) {
            TransactionFormFieldError.DESCRIPTION_TOO_LONG
        } else {
            null
        }

        // 5. Taksit Doğrulaması ve Ön Hesaplama
        var installmentCount: Int? = null
        var installmentError: TransactionFormFieldError? = null
        if (currentState.isInstallmentOptionAvailable && currentState.isInstallmentEnabled) {
            val parsedCount = currentState.installmentCountText.trim().toIntOrNull()
            if (parsedCount == null || parsedCount !in InstallmentPlanCalculator.MIN_INSTALLMENT_COUNT..InstallmentPlanCalculator.MAX_INSTALLMENT_COUNT) {
                installmentError = TransactionFormFieldError.INSTALLMENT_COUNT_INVALID
            } else {
                installmentCount = parsedCount
                if (amountMinor != null && date != null) {
                    when (val planResult = InstallmentPlanCalculator.calculate(Money(amountMinor, currentState.currency), parsedCount, date)) {
                        is InstallmentPlanResult.Success -> Unit
                        is InstallmentPlanResult.Invalid -> {
                            when (planResult.error) {
                                InstallmentPlanError.COUNT_EXCEEDS_AMOUNT -> installmentError = TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL
                                InstallmentPlanError.AMOUNT_MUST_BE_POSITIVE -> amountError = TransactionFormFieldError.AMOUNT_NON_POSITIVE
                                InstallmentPlanError.COUNT_OUT_OF_RANGE -> installmentError = TransactionFormFieldError.INSTALLMENT_COUNT_INVALID
                            }
                        }
                    }
                }
            }
        }

        if (amountError != null || categoryError != null || dateError != null || descriptionError != null || installmentError != null || amountMinor == null || date == null) {
            _uiState.update {
                it.copy(
                    amountError = amountError,
                    categoryError = categoryError,
                    dateError = dateError,
                    descriptionError = descriptionError,
                    installmentCountError = installmentError,
                    generalMessage = null,
                )
            }
            return
        }

        val categoryId = currentState.selectedCategoryId!!

        _uiState.update {
            it.copy(
                isSubmitting = true,
                amountError = null,
                categoryError = null,
                dateError = null,
                descriptionError = null,
                installmentCountError = null,
                generalMessage = null,
            )
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result: RepositoryResult<*> = if (currentState.isEditMode) {
                    val command = TransactionCommand(
                        id = targetTransactionId!!,
                        workspaceId = _workspaceIdState.value,
                        amount = Money(amountMinor, currentState.currency),
                        type = currentState.type,
                        categoryId = categoryId,
                        description = currentState.description,
                        paymentMethod = currentState.paymentMethod,
                        transactionDate = date,
                        receiptPath = existingReceiptPath,
                    )
                    updateTransactionUseCase(command, today)
                } else if (currentState.isInstallmentOptionAvailable && currentState.isInstallmentEnabled && installmentCount != null) {
                    val now = currentInstantProvider.now()
                    val command = AddInstallmentGroupCommand(
                        workspaceId = _workspaceIdState.value,
                        totalAmount = Money(amountMinor, currentState.currency),
                        type = TransactionType.EXPENSE,
                        categoryId = categoryId,
                        description = currentState.description,
                        paymentMethod = PaymentMethod.CREDIT_CARD,
                        anchorDate = date,
                        receiptPath = null,
                        installmentCount = installmentCount,
                    )
                    addInstallmentGroupUseCase(command, today, now)
                } else {
                    val now = currentInstantProvider.now()
                    val command = TransactionCommand(
                        id = entityIdGenerator.nextId(),
                        workspaceId = _workspaceIdState.value,
                        amount = Money(amountMinor, currentState.currency),
                        type = currentState.type,
                        categoryId = categoryId,
                        description = currentState.description,
                        paymentMethod = currentState.paymentMethod,
                        transactionDate = date,
                        receiptPath = null,
                    )
                    addTransactionUseCase(command, today, now)
                }

                when (result) {
                    is RepositoryResult.Success -> {
                        _events.send(TransactionFormEvent.NavigateBack)
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(generalMessage = result.error.toFinanceUiMessage())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(generalMessage = FinanceUiMessage.GENERIC_ERROR)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                submitJob = null
            }
        }
        submitJob = job
        job.start()
    }

    companion object {
        fun formatMinorUnitsToInputText(amountMinor: Long, currency: Currency): String =
            com.feniqo.mobile.presentation.util.MoneyFormatter.formatMinorUnitsToInputText(amountMinor, currency)
    }
}
