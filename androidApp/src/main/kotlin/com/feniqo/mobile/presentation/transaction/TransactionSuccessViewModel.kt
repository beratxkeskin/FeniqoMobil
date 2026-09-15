package com.feniqo.mobile.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.usecase.ObserveCategoriesForHistoryLookupUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionUseCase
import com.feniqo.mobile.navigation.TransactionSuccessRoute
import com.feniqo.mobile.presentation.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Başarı ekranı için Room SSOT'tan işlem ve ilişkili kategori detayını yükleyen ViewModel.
 */
@HiltViewModel
class TransactionSuccessViewModel @Inject constructor(
    observeTransactionUseCase: ObserveTransactionUseCase,
    observeCategoriesForHistoryLookupUseCase: ObserveCategoriesForHistoryLookupUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: TransactionSuccessRoute? = try {
        savedStateHandle.toRoute<TransactionSuccessRoute>()
    } catch (_: Exception) {
        null
    }

    val uiState: StateFlow<TransactionSuccessUiState> = if (route == null) {
        MutableStateFlow(
            TransactionSuccessUiState(
                isLoading = false,
                transaction = null,
                errorMessage = "Geçersiz işlem parametresi.",
            ),
        )
    } else {
        val transactionId = EntityId(route.transactionId)
        combine(
            observeTransactionUseCase(transactionId),
            observeCategoriesForHistoryLookupUseCase(),
        ) { transaction, categories ->
            if (transaction == null) {
                TransactionSuccessUiState(
                    isLoading = false,
                    transaction = null,
                    errorMessage = "İşlem bulunamadı.",
                )
            } else {
                val category = categories.find { it.id == transaction.categoryId }
                val displayModel = TransactionDisplayModel(
                    id = transaction.id,
                    amount = transaction.amount,
                    formattedAmount = MoneyFormatter.format(
                        money = transaction.amount,
                        includeSign = true,
                        type = transaction.type,
                    ),
                    type = transaction.type,
                    categoryId = transaction.categoryId,
                    categoryName = category?.name ?: "Bilinmeyen Kategori",
                    categoryColorHex = category?.color?.hex,
                    categoryIconKey = category?.icon?.key,
                    description = transaction.description,
                    paymentMethod = transaction.paymentMethod,
                    transactionDate = transaction.transactionDate,
                    installment = transaction.installment?.let { inst ->
                        InstallmentDisplayModel(
                            number = inst.number,
                            total = inst.total,
                            badgeText = "${inst.number}/${inst.total}",
                        )
                    },
                    hasReceipt = transaction.receiptPath != null,
                    note = transaction.note,
                    syncStatus = transaction.syncStatus,
                    canEdit = true,
                    canDelete = true,
                )
                TransactionSuccessUiState(
                    isLoading = false,
                    transaction = displayModel,
                    errorMessage = null,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionSuccessUiState(isLoading = true),
        )
    }
}
