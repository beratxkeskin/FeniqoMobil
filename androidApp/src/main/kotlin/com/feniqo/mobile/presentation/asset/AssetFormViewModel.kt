package com.feniqo.mobile.presentation.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.*
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AssetFormViewModel @Inject constructor(
    private val observeAsset: ObserveAssetUseCase,
    private val createAsset: CreateAssetUseCase,
    private val updateAsset: UpdateAssetUseCase,
    private val deleteAsset: DeleteAssetUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssetFormUiState())
    val uiState: StateFlow<AssetFormUiState> = mutableState.asStateFlow()
    private val mutableLoadState = MutableStateFlow<AssetEditLoadState>(AssetEditLoadState.Idle)
    val editLoadState: StateFlow<AssetEditLoadState> = mutableLoadState.asStateFlow()
    private val eventChannel = Channel<AssetFormUiEvent>(Channel.BUFFERED)
    val events: Flow<AssetFormUiEvent> = eventChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    fun loadForEdit(id: EntityId) {
        loadJob?.cancel()
        mutableLoadState.value = AssetEditLoadState.Loading
        loadJob = viewModelScope.launch {
            try {
                observeAsset(id).collect { asset ->
                    if (asset == null) mutableLoadState.value = AssetEditLoadState.NotFound
                    else {
                        val input = AssetFormInput.fromDomain(asset)
                        mutableState.value = AssetFormUiState(
                            input = input,
                            calculatedCostPreview = input.computeCostPreview(),
                        )
                        mutableLoadState.value = AssetEditLoadState.Ready
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableLoadState.value = AssetEditLoadState.Error(FinanceUiMessage.GENERIC_ERROR)
            }
        }
    }

    fun setInvalidRouteId() {
        loadJob?.cancel()
        mutableLoadState.value = AssetEditLoadState.NotFound
    }

    fun updateInput(transform: (AssetFormInput) -> AssetFormInput) {
        mutableState.update { state ->
            val changed = transform(state.input)
            state.copy(
                input = changed.copy(assetId = state.input.assetId),
                calculatedCostPreview = changed.computeCostPreview(),
                errors = AssetFormErrors(),
            )
        }
    }

    fun submit() {
        if (mutationJob != null || mutableState.value.isSubmitting) return
        when (val normalized = mutableState.value.input.toDraft()) {
            is AssetFormNormalizationResult.Invalid -> mutableState.update { it.copy(errors = normalized.errors) }
            is AssetFormNormalizationResult.Valid -> {
                mutableState.update { it.copy(isSubmitting = true) }
                mutationJob = viewModelScope.launch {
                    try {
                        val result = if (normalized.draft.assetId == null) {
                            createAsset(normalized.draft.toCreateCommand())
                        } else updateAsset(normalized.draft.toUpdateCommand())
                        eventChannel.send(
                            if (result is RepositoryResult.Success) AssetFormUiEvent.MutationSuccess(FinanceUiMessage.ASSET_SAVED)
                            else AssetFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR),
                        )
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { eventChannel.send(AssetFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR)) }
                    finally { mutableState.update { it.copy(isSubmitting = false) }; mutationJob = null }
                }
            }
        }
    }

    fun requestDelete() = mutableState.update {
        if (it.input.assetId == null) it else it.copy(pendingDeleteConfirmation = true)
    }
    fun dismissDelete() = mutableState.update { it.copy(pendingDeleteConfirmation = false) }
    fun confirmDelete() {
        val id = mutableState.value.input.assetId ?: return
        if (mutationJob != null) return
        mutableState.update { it.copy(isSubmitting = true, pendingDeleteConfirmation = false) }
        mutationJob = viewModelScope.launch {
            try {
                val result = deleteAsset(id)
                eventChannel.send(
                    if (result is RepositoryResult.Success) AssetFormUiEvent.MutationSuccess(FinanceUiMessage.ASSET_DELETED)
                    else AssetFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR),
                )
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { eventChannel.send(AssetFormUiEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR)) }
            finally { mutableState.update { it.copy(isSubmitting = false) }; mutationJob = null }
        }
    }
}
