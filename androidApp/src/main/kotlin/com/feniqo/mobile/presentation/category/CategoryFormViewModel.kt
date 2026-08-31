package com.feniqo.mobile.presentation.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AddCategoryCommand
import com.feniqo.mobile.domain.usecase.AddCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoryUseCase
import com.feniqo.mobile.domain.usecase.UpdateCategoryCommand
import com.feniqo.mobile.domain.usecase.UpdateCategoryUseCase
import com.feniqo.mobile.domain.validation.CategoryValidationError
import com.feniqo.mobile.domain.validation.CategoryValidationResult
import com.feniqo.mobile.domain.validation.CategoryValidationRules
import com.feniqo.mobile.navigation.CategoryFormRoute
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kategori oluşturma ve düzenleme formunu yöneten ViewModel'dir.
 */
@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    private val addCategoryUseCase: AddCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val observeCategoryUseCase: ObserveCategoryUseCase,
    private val currentInstantProvider: CurrentInstantProvider,
    private val entityIdGenerator: EntityIdGenerator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryFormUiState())
    val uiState: StateFlow<CategoryFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<CategoryFormEvent>(Channel.BUFFERED)
    val events: Flow<CategoryFormEvent> = _events.receiveAsFlow()

    private var activeSubmitJob: Job? = null

    init {
        resolveRoute(savedStateHandle)
    }

    private fun resolveRoute(savedStateHandle: SavedStateHandle) {
        val routeResult = runCatching { savedStateHandle.toRoute<CategoryFormRoute>() }
        val route = routeResult.getOrNull()
        if (routeResult.isFailure || route == null) {
            val exception = routeResult.exceptionOrNull()
            if (exception is CancellationException) throw exception
            _uiState.update {
                it.copy(
                    loadError = CategoryFormLoadError.INVALID_ROUTE,
                    isLoadingInitialData = false,
                )
            }
            return
        }

        val rawCategoryId = route.categoryId
        if (rawCategoryId == null) {
            val parsedType = parseTransactionType(route.initialTypeCode)
            _uiState.update {
                it.copy(
                    categoryId = null,
                    type = parsedType,
                    isLoadingInitialData = false,
                    loadError = null,
                )
            }
        } else if (rawCategoryId.isBlank()) {
            _uiState.update {
                it.copy(
                    loadError = CategoryFormLoadError.INVALID_ROUTE,
                    isLoadingInitialData = false,
                )
            }
        } else {
            val entityId = runCatching { EntityId(rawCategoryId.trim()) }.getOrNull()
            if (entityId == null) {
                _uiState.update {
                    it.copy(
                        loadError = CategoryFormLoadError.INVALID_ROUTE,
                        isLoadingInitialData = false,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        categoryId = entityId,
                        isLoadingInitialData = true,
                        loadError = null,
                    )
                }
                loadCategoryForEdit(entityId)
            }
        }
    }

    private fun loadCategoryForEdit(id: EntityId) {
        viewModelScope.launch {
            try {
                val category = observeCategoryUseCase(id).first()
                if (category == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingInitialData = false,
                            loadError = CategoryFormLoadError.CATEGORY_NOT_FOUND,
                        )
                    }
                } else if (category.isDefault || category.ownerId == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingInitialData = false,
                            loadError = CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            name = category.name,
                            type = category.type,
                            colorHex = category.color.hex,
                            iconKey = category.icon?.key,
                            isLoadingInitialData = false,
                            loadError = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingInitialData = false,
                        loadError = CategoryFormLoadError.LOAD_FAILED,
                    )
                }
            }
        }
    }

    private fun parseTransactionType(code: String): TransactionType {
        return when (code.trim().uppercase()) {
            "INCOME" -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
    }

    fun onNameChanged(name: String) {
        if (!_uiState.value.isFormEnabled) return
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onTypeChanged(type: TransactionType) {
        if (!_uiState.value.isTypeEditable) return
        _uiState.update { it.copy(type = type) }
    }

    fun onColorChanged(colorHex: String) {
        if (!_uiState.value.isFormEnabled) return
        _uiState.update { it.copy(colorHex = colorHex, colorError = null) }
    }

    fun onIconChanged(iconKey: String?) {
        if (!_uiState.value.isFormEnabled) return
        val normalized = iconKey?.trim()?.takeIf { it.isNotBlank() }
        _uiState.update { it.copy(iconKey = normalized) }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(generalMessage = null) }
    }

    fun onSubmit() {
        val currentState = _uiState.value
        if (!currentState.isFormEnabled || currentState.isSubmitting || activeSubmitJob != null) return

        val nameValidation = CategoryValidationRules.validateName(currentState.name)
        val colorValidation = CategoryValidationRules.validateColor(currentState.colorHex)

        var hasError = false
        var nameError: CategoryFormFieldError? = null
        var colorError: CategoryFormFieldError? = null

        val validName = when (nameValidation) {
            is CategoryValidationResult.Valid -> nameValidation.value
            is CategoryValidationResult.Invalid -> {
                hasError = true
                nameError = when (nameValidation.error) {
                    CategoryValidationError.NAME_EMPTY -> CategoryFormFieldError.NAME_REQUIRED
                    CategoryValidationError.NAME_TOO_LONG -> CategoryFormFieldError.NAME_TOO_LONG
                    else -> CategoryFormFieldError.NAME_REQUIRED
                }
                null
            }
        }

        val validColor = when (colorValidation) {
            is CategoryValidationResult.Valid -> colorValidation.value
            is CategoryValidationResult.Invalid -> {
                hasError = true
                colorError = CategoryFormFieldError.COLOR_INVALID
                null
            }
        }

        if (hasError || validName == null || validColor == null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    colorError = colorError,
                )
            }
            return
        }

        val normalizedIcon = currentState.iconKey?.trim()?.takeIf { it.isNotBlank() }?.let { key ->
            try {
                CategoryIcon(key)
            } catch (e: Exception) {
                null
            }
        }

        _uiState.update { it.copy(isSubmitting = true) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = if (currentState.isEditMode) {
                    val command = UpdateCategoryCommand(
                        id = currentState.categoryId!!,
                        name = validName,
                        colorHex = validColor.hex,
                        icon = normalizedIcon,
                    )
                    updateCategoryUseCase(command)
                } else {
                    val command = AddCategoryCommand(
                        id = entityIdGenerator.nextId(),
                        workspaceId = null,
                        name = validName,
                        type = currentState.type,
                        colorHex = validColor.hex,
                        icon = normalizedIcon,
                    )
                    addCategoryUseCase(command, currentInstantProvider.now())
                }

                when (result) {
                    is RepositoryResult.Success -> {
                        _events.send(CategoryFormEvent.NavigateBack)
                    }
                    is RepositoryResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                generalMessage = result.error.toFinanceUiMessage(),
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        generalMessage = FinanceUiMessage.GENERIC_ERROR,
                    )
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
                activeSubmitJob = null
            }
        }
        activeSubmitJob = job
        job.start()
    }
}
