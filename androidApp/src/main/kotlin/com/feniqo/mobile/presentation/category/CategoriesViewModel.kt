package com.feniqo.mobile.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.DeleteCategoryUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.common.toFinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kategori listeleme, tür filtreleme ve özel kategori silme işlemlerini yöneten ViewModel'dir.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
) : ViewModel() {

    private val _selectedType = MutableStateFlow(TransactionType.EXPENSE)
    private val _retryTrigger = MutableStateFlow(0L)
    private val _deleteTargetCategory = MutableStateFlow<CategoryDisplayModel?>(null)
    private val _isDeleteInProgress = MutableStateFlow(false)
    private val _generalMessage = MutableStateFlow<FinanceUiMessage?>(null)

    private var activeDeleteJob: Job? = null

    private data class ObservationQuery(
        val type: TransactionType,
        val retryCount: Long,
    )

    internal sealed interface ObservationResult {
        data object Loading : ObservationResult
        data class Success(
            val systemCategories: List<CategoryDisplayModel>,
            val customCategories: List<CategoryDisplayModel>,
        ) : ObservationResult
        data class Failure(val message: FinanceUiMessage) : ObservationResult
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observationResultFlow: Flow<ObservationResult> = combine(
        _selectedType,
        _retryTrigger,
    ) { type, retryCount ->
        ObservationQuery(type, retryCount)
    }.flatMapLatest { query ->
        observeCategoriesUseCase(type = query.type)
            .map<List<Category>, ObservationResult> { categories ->
                val (systemList, customList) = categories.partition { it.isDefault }
                ObservationResult.Success(
                    systemCategories = systemList.map { it.toDisplayModel() },
                    customCategories = customList.map { it.toDisplayModel() },
                )
            }
            .onStart { emit(ObservationResult.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                emit(ObservationResult.Failure(FinanceUiMessage.GENERIC_ERROR))
            }
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        _selectedType,
        observationResultFlow,
        _deleteTargetCategory,
        _isDeleteInProgress,
        _generalMessage,
    ) { selectedType, observationResult, deleteTarget, isDeleteInProgress, generalMessage ->
        when (observationResult) {
            is ObservationResult.Loading -> CategoriesUiState(
                isLoading = true,
                selectedType = selectedType,
                systemCategories = emptyList(),
                customCategories = emptyList(),
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                generalMessage = generalMessage,
            )
            is ObservationResult.Success -> CategoriesUiState(
                isLoading = false,
                selectedType = selectedType,
                systemCategories = observationResult.systemCategories,
                customCategories = observationResult.customCategories,
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                generalMessage = generalMessage,
            )
            is ObservationResult.Failure -> CategoriesUiState(
                isLoading = false,
                selectedType = selectedType,
                systemCategories = emptyList(),
                customCategories = emptyList(),
                deleteTargetCategory = deleteTarget,
                isDeleteInProgress = isDeleteInProgress,
                generalMessage = generalMessage ?: observationResult.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(),
    )

    fun onTypeSelected(type: TransactionType) {
        if (_isDeleteInProgress.value) return
        if (_selectedType.value == type) return
        _selectedType.value = type
    }

    fun onDeleteClicked(category: CategoryDisplayModel) {
        if (_isDeleteInProgress.value) return
        if (category.isDefault) {
            _generalMessage.value = FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE
            return
        }
        _deleteTargetCategory.value = category
    }

    fun onDismissDeleteDialog() {
        if (_isDeleteInProgress.value) return
        _deleteTargetCategory.value = null
    }

    fun onConfirmDelete() {
        val target = _deleteTargetCategory.value ?: return
        if (target.isDefault) {
            _deleteTargetCategory.value = null
            _generalMessage.value = FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE
            return
        }
        if (_isDeleteInProgress.value || activeDeleteJob != null) return

        _isDeleteInProgress.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = deleteCategoryUseCase(target.id)
                when (result) {
                    is RepositoryResult.Success -> {
                        _deleteTargetCategory.value = null
                        _generalMessage.value = FinanceUiMessage.CATEGORY_DELETED
                    }
                    is RepositoryResult.Failure -> {
                        _generalMessage.value = result.error.toFinanceUiMessage()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _generalMessage.value = FinanceUiMessage.GENERIC_ERROR
            } finally {
                _isDeleteInProgress.value = false
                activeDeleteJob = null
            }
        }
        activeDeleteJob = job
        job.start()
    }

    fun onDismissMessage() {
        _generalMessage.value = null
    }

    fun retryObservation() {
        _retryTrigger.update { it + 1 }
    }
}

private fun Category.toDisplayModel(): CategoryDisplayModel = CategoryDisplayModel(
    id = id,
    name = name,
    type = type,
    colorHex = color.hex,
    iconKey = icon?.key,
    isDefault = isDefault,
)
