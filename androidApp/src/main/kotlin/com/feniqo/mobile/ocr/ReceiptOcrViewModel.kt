package com.feniqo.mobile.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.ReceiptOcrDraft
import com.feniqo.mobile.domain.model.ReceiptOcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiptOcrUiState(
    val isProcessing: Boolean = false,
    val draft: ReceiptOcrDraft? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ReceiptOcrViewModel @Inject constructor(
    private val service: ReceiptOcrService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReceiptOcrUiState())
    val state: StateFlow<ReceiptOcrUiState> = mutableState.asStateFlow()

    fun recognize(uri: Uri, currency: Currency) {
        if (mutableState.value.isProcessing) return
        viewModelScope.launch {
            mutableState.value = ReceiptOcrUiState(isProcessing = true)
            try {
                when (val result = service.recognize(uri, currency)) {
                    is ReceiptOcrResult.Candidates -> mutableState.value = ReceiptOcrUiState(draft = result.draft)
                    ReceiptOcrResult.NoCandidates -> mutableState.value = ReceiptOcrUiState(
                        errorMessage = "Makbuzdan kullanılabilir bilgi okunamadı.",
                    )
                    ReceiptOcrResult.InputTooLarge -> mutableState.value = ReceiptOcrUiState(
                        errorMessage = "Makbuz metni güvenli işleme sınırını aşıyor.",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.value = ReceiptOcrUiState(
                    errorMessage = "Makbuz okunamadı. Daha net bir görselle tekrar deneyin.",
                )
            }
        }
    }

    fun consumeDraft() {
        mutableState.update { it.copy(draft = null) }
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }
}
