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
import com.feniqo.mobile.domain.model.ReceiptOcrDraft
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddInstallmentGroupCommand
import com.feniqo.mobile.domain.usecase.AddInstallmentGroupUseCase
import com.feniqo.mobile.domain.usecase.AddTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceMembersUseCase
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
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel
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
    data class TransactionCreated(val transactionId: EntityId) : TransactionFormEvent
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
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val observeWorkspaceMembersUseCase: ObserveWorkspaceMembersUseCase,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val currentDateProvider: CurrentDateProvider,
    private val currentInstantProvider: CurrentInstantProvider,
    private val entityIdGenerator: EntityIdGenerator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val targetTransactionId: EntityId?
    private val initialLoadError: FinanceUiMessage?
    private val isEditMode: Boolean
    private val initialTransactionType: TransactionType

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
            initialTransactionType = TransactionType.EXPENSE
        } else {
            initialTransactionType = if (routeResult.transactionId == null) {
                runCatching { TransactionType.valueOf(routeResult.initialTypeCode) }
                    .getOrDefault(TransactionType.EXPENSE)
            } else {
                TransactionType.EXPENSE
            }
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

    private val _typeState = MutableStateFlow(initialTransactionType)
    private val _workspaceIdState = MutableStateFlow<EntityId?>(null)
    private val _historicalCategoryState = MutableStateFlow<TransactionCategoryOptionUiModel?>(null)
    private val _retryTrigger = MutableStateFlow(0)

    private var existingReceiptPath: ReceiptPath? = null

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            transactionDate = currentDateProvider.today(),
            type = initialTransactionType,
            isEditMode = isEditMode,
            isLoadingTransaction = isEditMode && initialLoadError == null,
            loadError = initialLoadError,
            isReceiptFeatureAvailable = !isEditMode,
        ),
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<TransactionFormEvent>(Channel.BUFFERED)
    val events: Flow<TransactionFormEvent> = _events.receiveAsFlow()

    private var submitJob: Job? = null

    init {
        setupCategoryObservationPipeline()
        setupWorkspaceMembersPipeline()

        viewModelScope.launch {
            var previousWorkspaceId: EntityId? = null
            var isFirstEmission = true
            observeActiveWorkspaceUseCase().collect { activeWorkspace ->
                val currentWorkspaceId = activeWorkspace?.id
                val workspaceName = activeWorkspace?.name
                _uiState.update {
                    it.copy(
                        activeWorkspaceId = currentWorkspaceId,
                        activeWorkspaceName = workspaceName,
                    )
                }

                if (!isEditMode) {
                    _workspaceIdState.value = currentWorkspaceId
                    if (!isFirstEmission && currentWorkspaceId != previousWorkspaceId) {
                        submitJob?.cancel()
                        _uiState.update { it.copy(selectedCategoryId = null) }
                    }
                } else if (!isFirstEmission && currentWorkspaceId != previousWorkspaceId) {
                    submitJob?.cancel()
                    val txWorkspaceId = _workspaceIdState.value
                    if (currentWorkspaceId != txWorkspaceId) {
                        _uiState.update { it.copy(loadError = FinanceUiMessage.TRANSACTION_NOT_FOUND) }
                    }
                }
                isFirstEmission = false
                previousWorkspaceId = currentWorkspaceId
            }
        }

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

    private fun setupWorkspaceMembersPipeline() {
        combine(_typeState, _workspaceIdState) { type, wsId ->
            Pair(type, wsId)
        }
            .distinctUntilChanged()
            .flatMapLatest { (type, wsId) ->
                if (type == TransactionType.EXPENSE && wsId != null) {
                    _uiState.update { it.copy(isLoadingWorkspaceMembers = true) }
                    combine(
                        observeWorkspaceMembersUseCase(wsId),
                        observeAuthSessionUseCase(),
                    ) { members, session ->
                        val currentUserId = session?.userId
                        val uiMembers = members.map { member ->
                            val isCurrentUser = currentUserId != null && member.userId == currentUserId
                            val displayName = if (isCurrentUser) {
                                "Siz"
                            } else {
                                "Kullanıcı ${member.userId.value.take(8)}"
                            }
                            WorkspaceMemberUiModel(
                                userId = member.userId,
                                displayName = displayName,
                                role = member.role,
                                isCurrentUser = isCurrentUser,
                            )
                        }.sortedWith(
                            compareByDescending<WorkspaceMemberUiModel> { it.isCurrentUser }
                                .thenBy { it.displayName }
                                .thenBy { it.userId.value },
                        )
                        uiMembers to currentUserId
                    }
                        .map { (uiMembers, currentUserId) ->
                            _uiState.update { state ->
                                val activeMemberIds = uiMembers.map { it.userId }.toSet()

                                var selectedPayer = state.selectedPaidByUserId
                                var selectedParticipants = state.selectedParticipantUserIds.filter { activeMemberIds.contains(it) }.toSet()

                                if (selectedPayer != null && !activeMemberIds.contains(selectedPayer)) {
                                    selectedPayer = selectedParticipants.firstOrNull()
                                        ?: (if (currentUserId != null && activeMemberIds.contains(currentUserId)) currentUserId else uiMembers.firstOrNull()?.userId)
                                } else if (selectedPayer == null) {
                                    selectedPayer = if (currentUserId != null && activeMemberIds.contains(currentUserId)) currentUserId else uiMembers.firstOrNull()?.userId
                                }

                                if (selectedPayer != null && !selectedParticipants.contains(selectedPayer)) {
                                    selectedParticipants = selectedParticipants + selectedPayer
                                }

                                if (selectedParticipants.isEmpty() && selectedPayer != null) {
                                    selectedParticipants = setOf(selectedPayer)
                                }

                                state.copy(
                                    workspaceMembers = uiMembers,
                                    isLoadingWorkspaceMembers = false,
                                    selectedPaidByUserId = selectedPayer,
                                    selectedParticipantUserIds = selectedParticipants,
                                )
                            }
                        }
                        .catch { e ->
                            if (e is CancellationException) throw e
                            if (e !is Exception) throw e
                            _uiState.update { it.copy(isLoadingWorkspaceMembers = false) }
                        }
                } else {
                    _uiState.update {
                        it.copy(
                            workspaceMembers = emptyList(),
                            isLoadingWorkspaceMembers = false,
                            selectedPaidByUserId = null,
                            selectedParticipantUserIds = emptySet(),
                            splitError = null,
                        )
                    }
                    kotlinx.coroutines.flow.emptyFlow()
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
                        title = transaction.description ?: "",
                        note = transaction.note ?: "",
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
                        selectedPaidByUserId = transaction.paidByUserId,
                        selectedParticipantUserIds = transaction.participantUserIds.toSet(),
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

    fun onTitleChanged(title: String) {
        _uiState.update {
            it.copy(
                title = title,
                description = title,
                titleError = null,
                descriptionError = null,
                generalMessage = null,
            )
        }
    }

    fun onNoteChanged(note: String) {
        _uiState.update {
            it.copy(
                note = note,
                noteError = null,
                generalMessage = null,
            )
        }
    }

    fun onDescriptionChanged(description: String) {
        onTitleChanged(description)
    }

    /** OCR adayları yalnız kullanıcı onayından sonra form alanlarına uygulanır; kayıt oluşturmaz. */
    fun applyReceiptOcrDraft(draft: ReceiptOcrDraft) {
        _uiState.update { state ->
            val merchantName = draft.merchantName?.value
            val newTitle = merchantName ?: state.title.ifEmpty { state.description }
            state.copy(
                amountText = draft.total?.value?.let {
                    formatMinorUnitsToInputText(it.amountMinor, state.currency)
                } ?: state.amountText,
                transactionDate = draft.transactionDate?.value ?: state.transactionDate,
                title = newTitle,
                description = merchantName ?: state.description,
                amountError = null,
                dateError = null,
                titleError = null,
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

    fun onPaidByUserSelected(userId: EntityId) {
        val currentState = _uiState.value
        val isMember = currentState.workspaceMembers.any { it.userId == userId }
        if (!isMember) return

        _uiState.update { state ->
            val updatedParticipants = if (!state.selectedParticipantUserIds.contains(userId)) {
                state.selectedParticipantUserIds + userId
            } else {
                state.selectedParticipantUserIds
            }
            state.copy(
                selectedPaidByUserId = userId,
                selectedParticipantUserIds = updatedParticipants,
                splitError = null,
                generalMessage = null,
            )
        }
    }

    fun onParticipantToggled(userId: EntityId) {
        val currentState = _uiState.value
        val isMember = currentState.workspaceMembers.any { it.userId == userId }
        if (!isMember) return

        _uiState.update { state ->
            if (state.selectedPaidByUserId == userId && state.selectedParticipantUserIds.contains(userId)) {
                return@update state
            }

            val updatedParticipants = if (state.selectedParticipantUserIds.contains(userId)) {
                state.selectedParticipantUserIds - userId
            } else {
                state.selectedParticipantUserIds + userId
            }

            state.copy(
                selectedParticipantUserIds = updatedParticipants,
                splitError = null,
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

        // 4. İşlem Adı ve Not Doğrulaması
        val effectiveTitle = currentState.title.ifEmpty { currentState.description }
        val titleValidation = TransactionValidationRules.validateTitle(effectiveTitle)
        val titleError: TransactionFormFieldError? = when (titleValidation) {
            is TransactionValidationResult.Valid -> null
            is TransactionValidationResult.Invalid -> when (titleValidation.error) {
                TransactionValidationError.TITLE_EMPTY -> TransactionFormFieldError.TITLE_REQUIRED
                TransactionValidationError.TITLE_TOO_LONG -> TransactionFormFieldError.TITLE_TOO_LONG
                else -> TransactionFormFieldError.TITLE_REQUIRED
            }
        }

        val noteValidation = TransactionValidationRules.validateNote(currentState.note)
        val noteError: TransactionFormFieldError? = when (noteValidation) {
            is TransactionValidationResult.Valid -> null
            is TransactionValidationResult.Invalid -> TransactionFormFieldError.NOTE_TOO_LONG
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

        // 6. Split Doğrulaması
        var splitError: TransactionFormFieldError? = null
        if (currentState.isSharedExpense) {
            val payer = currentState.selectedPaidByUserId
            val participants = currentState.selectedParticipantUserIds
            val memberIds = currentState.workspaceMembers.map { it.userId }.toSet()

            if (currentState.isLoadingWorkspaceMembers) {
                splitError = TransactionFormFieldError.SPLIT_PAYER_REQUIRED
            } else if (payer == null || !memberIds.contains(payer)) {
                splitError = TransactionFormFieldError.SPLIT_PAYER_REQUIRED
            } else if (participants.isEmpty() || !participants.all { memberIds.contains(it) }) {
                splitError = TransactionFormFieldError.SPLIT_PARTICIPANTS_REQUIRED
            } else if (!participants.contains(payer)) {
                splitError = TransactionFormFieldError.SPLIT_PAYER_NOT_IN_PARTICIPANTS
            }
        }

        if (amountError != null || categoryError != null || dateError != null || titleError != null || noteError != null || installmentError != null || splitError != null || amountMinor == null || date == null) {
            _uiState.update {
                it.copy(
                    amountError = amountError,
                    categoryError = categoryError,
                    dateError = dateError,
                    titleError = titleError,
                    noteError = noteError,
                    descriptionError = titleError,
                    installmentCountError = installmentError,
                    splitError = splitError,
                    generalMessage = null,
                )
            }
            return
        }

        val categoryId = currentState.selectedCategoryId!!
        val paidByUserId = if (currentState.isSharedExpense) currentState.selectedPaidByUserId else null
        val participantUserIds = if (currentState.isSharedExpense) currentState.selectedParticipantUserIds.toList() else emptyList()
        val effectiveTitleTrimmed = effectiveTitle.trim()
        val normalizedNote = currentState.note.trim().ifEmpty { null }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                amountError = null,
                categoryError = null,
                dateError = null,
                titleError = null,
                noteError = null,
                descriptionError = null,
                installmentCountError = null,
                splitError = null,
                generalMessage = null,
            )
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (currentState.isEditMode) {
                    val command = TransactionCommand(
                        id = targetTransactionId!!,
                        workspaceId = _workspaceIdState.value,
                        amount = Money(amountMinor, currentState.currency),
                        type = currentState.type,
                        categoryId = categoryId,
                        description = effectiveTitleTrimmed,
                        paymentMethod = currentState.paymentMethod,
                        transactionDate = date,
                        receiptPath = existingReceiptPath,
                        paidByUserId = paidByUserId,
                        participantUserIds = participantUserIds,
                        note = normalizedNote,
                    )
                    when (val result = updateTransactionUseCase(command, today)) {
                        is RepositoryResult.Success -> {
                            _events.send(TransactionFormEvent.NavigateBack)
                        }
                        is RepositoryResult.Failure -> {
                            _uiState.update {
                                it.copy(generalMessage = result.error.toFinanceUiMessage())
                            }
                        }
                    }
                } else if (currentState.isInstallmentOptionAvailable && currentState.isInstallmentEnabled && installmentCount != null) {
                    val now = currentInstantProvider.now()
                    val command = AddInstallmentGroupCommand(
                        workspaceId = _workspaceIdState.value,
                        totalAmount = Money(amountMinor, currentState.currency),
                        type = TransactionType.EXPENSE,
                        categoryId = categoryId,
                        description = effectiveTitleTrimmed,
                        paymentMethod = PaymentMethod.CREDIT_CARD,
                        anchorDate = date,
                        receiptPath = null,
                        installmentCount = installmentCount,
                        paidByUserId = paidByUserId,
                        participantUserIds = participantUserIds,
                        note = normalizedNote,
                    )
                    when (val result = addInstallmentGroupUseCase(command, today, now)) {
                        is RepositoryResult.Success -> {
                            _events.send(TransactionFormEvent.TransactionCreated(result.value))
                        }
                        is RepositoryResult.Failure -> {
                            _uiState.update {
                                it.copy(generalMessage = result.error.toFinanceUiMessage())
                            }
                        }
                    }
                } else {
                    val now = currentInstantProvider.now()
                    val newId = entityIdGenerator.nextId()
                    val command = TransactionCommand(
                        id = newId,
                        workspaceId = _workspaceIdState.value,
                        amount = Money(amountMinor, currentState.currency),
                        type = currentState.type,
                        categoryId = categoryId,
                        description = effectiveTitleTrimmed,
                        paymentMethod = currentState.paymentMethod,
                        transactionDate = date,
                        receiptPath = null,
                        paidByUserId = paidByUserId,
                        participantUserIds = participantUserIds,
                        note = normalizedNote,
                    )
                    when (val result = addTransactionUseCase(command, today, now)) {
                        is RepositoryResult.Success -> {
                            _events.send(TransactionFormEvent.TransactionCreated(newId))
                        }
                        is RepositoryResult.Failure -> {
                            _uiState.update {
                                it.copy(generalMessage = result.error.toFinanceUiMessage())
                            }
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
