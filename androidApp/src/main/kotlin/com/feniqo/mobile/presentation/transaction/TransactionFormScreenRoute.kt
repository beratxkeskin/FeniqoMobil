package com.feniqo.mobile.presentation.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionFormScreen

/**
 * Android Compose Navigation için İşlem Formu rotası adaptörüdür.
 * TransactionFormViewModel'e bağlanır, UI durumunu toplar, Android Material 3 DatePickerDialog
 * bileşenini yönetir ve olayları güvenli biçimde ViewModel'e iletir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreenRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddCategory: (TransactionType) -> Unit = {},
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    // 1. Gönderim sırasında sistem geri hareketini engelleme
    BackHandler(enabled = state.isSubmitting) {
        // Form submit edilirken yanlışlıkla geri çıkılmasını önler
    }

    // 2. ViewModel tek seferlik olaylarını toplama
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionFormEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    // 3. Stateless Form Ekranı
    TransactionFormScreen(
        uiState = state,
        onAmountChange = viewModel::onAmountChanged,
        onCurrencyChange = viewModel::onCurrencyChanged,
        onTypeChange = viewModel::onTypeChanged,
        onCategoryChange = viewModel::onCategoryChanged,
        onDateClick = { showDatePicker = true },
        onDescriptionChange = viewModel::onDescriptionChanged,
        onPaymentMethodChange = viewModel::onPaymentMethodChanged,
        onInstallmentToggle = viewModel::onInstallmentToggle,
        onInstallmentCountChange = viewModel::onInstallmentCountChanged,
        onRetryCategories = viewModel::retryCategories,
        onSubmit = viewModel::submit,
        onBack = onNavigateBack,
        onDismissMessage = viewModel::consumeMessage,
        onAddCategory = onAddCategory,
        onAttachReceipt = {},
        onRemoveReceipt = {},
        modifier = modifier,
    )

    // 4. Android Material 3 Tarih Seçici Dialogu
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDatePickerSelection(state.transactionDate),
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val localDate = utcEpochMillisToLocalDate(selectedMillis)
                            viewModel.onDateChanged(localDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Seç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * DatePicker için başlangıç seçim değerini UTC epoch milisaniyesi olarak hesaplar.
 * Tarih null ise DatePicker'ın doğal seçimsiz durumunu korur.
 */
internal fun initialDatePickerSelection(date: LocalDate?): Long? = date?.toUtcEpochMillis()

/**
 * kotlinx.datetime.LocalDate nesnesini UTC epoch milisaniyesine dönüştürür.
 * Saat dilimi kaynaklı gün kaymalarını önlemek için doğrudan epoch day formülünü kullanır.
 */
internal fun LocalDate.toUtcEpochMillis(): Long {
    return toEpochDays() * 86_400_000L
}

/**
 * UTC epoch milisaniyesini kotlinx.datetime.LocalDate nesnesine dönüştürür.
 */
internal fun utcEpochMillisToLocalDate(utcEpochMillis: Long): LocalDate {
    val epochDays = (utcEpochMillis / 86_400_000L).toInt()
    return LocalDate.fromEpochDays(epochDays)
}
