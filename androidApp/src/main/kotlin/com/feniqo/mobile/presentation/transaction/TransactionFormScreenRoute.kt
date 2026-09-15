package com.feniqo.mobile.presentation.transaction

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.ocr.CameraPermissionAction
import com.feniqo.mobile.ocr.CameraPermissionPolicy
import com.feniqo.mobile.ocr.ReceiptCameraCaptureDialog
import com.feniqo.mobile.ocr.ReceiptOcrViewModel
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
    onTransactionCreated: (EntityId) -> Unit = { onNavigateBack() },
    onAddCategory: (TransactionType) -> Unit = {},
    viewModel: TransactionFormViewModel = hiltViewModel(),
    ocrViewModel: ReceiptOcrViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ocrState by ocrViewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var hasRequestedCamera by rememberSaveable { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { ocrViewModel.recognize(it, state.currency) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedCamera = true
        if (granted) showCamera = true else showPermissionDialog = true
    }

    LaunchedEffect(ocrState.isProcessing) {
        if (!ocrState.isProcessing) {
            pendingCameraFile?.delete()
            pendingCameraFile = null
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val handleBackPress: () -> Unit = {
        if (state.hasUnsavedChanges && !state.isSubmitting) {
            showExitConfirmDialog = true
        } else if (!state.isSubmitting) {
            onNavigateBack()
        }
    }

    // 1. Geri hareketini yakalama (Kaydedilmemiş değişiklik varsa onay ister)
    BackHandler(enabled = !state.isSubmitting) {
        handleBackPress()
    }

    // 2. ViewModel tek seferlik olaylarını toplama
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionFormEvent.NavigateBack -> onNavigateBack()
                is TransactionFormEvent.TransactionCreated -> onTransactionCreated(event.transactionId)
            }
        }
    }

    // 3. Stateless Form Ekranı
    TransactionFormScreen(
        uiState = state.copy(isReceiptActionInProgress = ocrState.isProcessing),
        onAmountChange = viewModel::onAmountChanged,
        onCurrencyChange = viewModel::onCurrencyChanged,
        onTypeChange = viewModel::onTypeChanged,
        onCategoryChange = viewModel::onCategoryChanged,
        onDateClick = { showDatePicker = true },
        onTitleChange = viewModel::onTitleChanged,
        onNoteChange = viewModel::onNoteChanged,
        onDescriptionChange = viewModel::onDescriptionChanged,
        onPaymentMethodChange = viewModel::onPaymentMethodChanged,
        onInstallmentToggle = viewModel::onInstallmentToggle,
        onInstallmentCountChange = viewModel::onInstallmentCountChanged,
        onRetryCategories = viewModel::retryCategories,
        onSubmit = viewModel::submit,
        onBack = handleBackPress,
        onDismissMessage = viewModel::consumeMessage,
        onAddCategory = onAddCategory,
        onAttachReceipt = { showSourceDialog = true },
        onRemoveReceipt = viewModel::onReceiptRemoved,
        onPaidByUserSelected = viewModel::onPaidByUserSelected,
        onParticipantToggled = viewModel::onParticipantToggled,
        modifier = modifier,
    )

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Makbuz Tara") },
            text = { Text("Makbuzu kamerayla çekin veya galeriden bir görsel seçin.") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    when (CameraPermissionPolicy.nextAction(
                        isGranted = granted,
                        hasRequestedBefore = hasRequestedCamera,
                        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                            Manifest.permission.CAMERA,
                        ) == true,
                    )) {
                        CameraPermissionAction.UseCamera -> showCamera = true
                        CameraPermissionAction.RequestPermission ->
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        CameraPermissionAction.ExplainDenial,
                        CameraPermissionAction.OpenSettings,
                        -> showPermissionDialog = true
                    }
                }) { Text("Kamera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) { Text("Galeri") }
            },
        )
    }

    if (showCamera) {
        ReceiptCameraCaptureDialog(
            onCaptured = { uri, file ->
                showCamera = false
                pendingCameraFile = file
                ocrViewModel.recognize(uri, state.currency)
            },
            onError = {
                showCamera = false
                showPermissionDialog = true
            },
            onDismiss = { showCamera = false },
        )
    }

    if (showPermissionDialog) {
        val permanentlyDenied = hasRequestedCamera &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) != true
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Kamera izni gerekli") },
            text = {
                Text(
                    if (permanentlyDenied) {
                        "Kamera izni kapalı. Ayarlardan izin verebilir veya galeriden görsel seçebilirsiniz."
                    } else {
                        "Makbuz çekmek için kamera izni gerekir. İzin vermeden galeriyi kullanabilirsiniz."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) { Text(if (permanentlyDenied) "Ayarları Aç" else "Tekrar İste") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    galleryLauncher.launch("image/*")
                }) { Text("Galeriyi Kullan") }
            },
        )
    }

    ocrState.draft?.let { draft ->
        AlertDialog(
            onDismissRequest = ocrViewModel::consumeDraft,
            title = { Text("Okunan Bilgileri Kontrol Et") },
            text = {
                Text(
                    listOfNotNull(
                        draft.merchantName?.value?.let { "İşyeri: $it" },
                        draft.total?.value?.let {
                            val major = it.amountMinor / 100L
                            val minor = (it.amountMinor % 100L).toString().padStart(2, '0')
                            "Tutar: $major,$minor ${it.currency.code}"
                        },
                        draft.transactionDate?.value?.let { "Tarih: $it" },
                    ).joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyReceiptOcrDraft(draft)
                    ocrViewModel.consumeDraft()
                }) { Text("Forma Aktar") }
            },
            dismissButton = {
                TextButton(onClick = ocrViewModel::consumeDraft) { Text("Kullanma") }
            },
        )
    }

    ocrState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = ocrViewModel::dismissError,
            title = { Text("Makbuz Okunamadı") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = ocrViewModel::dismissError) { Text("Tamam") }
            },
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Değişiklikler Kaydedilmedi") },
            text = { Text("Yaptığınız değişiklikler kaydedilmedi. Ayrılmak istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        onNavigateBack()
                    },
                ) {
                    Text("Değişikliklerden Vazgeç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Düzenlemeye Devam Et")
                }
            },
        )
    }

    // 4. Android Material 3 Tarih Seçici Dialogu
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDatePickerSelection(state.transactionDate),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = utcEpochMillisToLocalDate(utcTimeMillis)
                    val today = java.time.LocalDate.now()
                    return date <= LocalDate(today.year, today.monthValue, today.dayOfMonth)
                }
            },
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
