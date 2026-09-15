package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionConflictViewModel @Inject constructor(private val repository: SyncRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val resolving = MutableStateFlow(false)
    val conflicts = repository.observeConflicts().catch {
        error.value = "Çakışma bilgileri okunamadı."
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resolve(id: EntityId, choice: ConflictResolution) {
        if (resolving.value) return
        resolving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                if (repository.resolveConflict(id, choice) is RepositoryResult.Failure) {
                    error.value = "Çakışma çözülemedi. Yeniden deneyin."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = "Çakışma çözülemedi. Yeniden deneyin."
            } finally {
                resolving.value = false
            }
        }
    }
}
