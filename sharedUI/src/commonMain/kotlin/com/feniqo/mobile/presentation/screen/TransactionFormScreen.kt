package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ReceiptAttachmentSection
import com.feniqo.mobile.presentation.component.TransactionAmountField
import com.feniqo.mobile.presentation.component.TransactionCategoryPicker
import com.feniqo.mobile.presentation.component.TransactionDatePickerField
import com.feniqo.mobile.presentation.component.TransactionDescriptionField
import com.feniqo.mobile.presentation.component.TransactionExistingInstallmentBadge
import com.feniqo.mobile.presentation.component.TransactionInstallmentSection
import com.feniqo.mobile.presentation.component.TransactionPaymentMethodSelector
import com.feniqo.mobile.presentation.component.TransactionTypeSelector
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.transaction.InstallmentDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionCategoryOptionUiModel
import com.feniqo.mobile.presentation.transaction.TransactionFormFieldError
import com.feniqo.mobile.presentation.transaction.TransactionFormUiState

/**
 * İşlem ekleme ve düzenleme için stateless ana form ekranıdır.
 * ViewModel ve platform bağımlılığı taşımaz; yalnızca UI durumunu render eder ve olayları üst katmana iletir.
 */
@Composable
fun TransactionFormScreen(
    uiState: TransactionFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryChange: (EntityId?) -> Unit,
    onDateClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onInstallmentToggle: (Boolean) -> Unit,
    onInstallmentCountChange: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onDismissMessage: () -> Unit,
    onAttachReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    onAddCategory: (TransactionType) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst Başlık ve Geri Dön Butonu
            TransactionFormHeader(
                isEditMode = uiState.isEditMode,
                onBack = onBack,
                isBackEnabled = !uiState.isSubmitting,
            )

            when {
                // 1. İşlem verisi yükleniyor durumu
                uiState.isLoadingTransaction -> {
                    TransactionLoadingView(modifier = Modifier.weight(1f))
                }

                // 2. Kalıcı işlem yükleme hatası
                uiState.loadError != null -> {
                    TransactionErrorView(
                        errorMessage = uiState.loadError.toDisplayText(),
                        onBack = onBack,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 3. Form İçeriği
                else -> {
                    TransactionFormContent(
                        uiState = uiState,
                        onAmountChange = onAmountChange,
                        onCurrencyChange = onCurrencyChange,
                        onTypeChange = onTypeChange,
                        onCategoryChange = onCategoryChange,
                        onDateClick = onDateClick,
                        onDescriptionChange = onDescriptionChange,
                        onPaymentMethodChange = onPaymentMethodChange,
                        onInstallmentToggle = onInstallmentToggle,
                        onInstallmentCountChange = onInstallmentCountChange,
                        onRetryCategories = onRetryCategories,
                        onSubmit = onSubmit,
                        onBack = onBack,
                        onDismissMessage = onDismissMessage,
                        onAddCategory = onAddCategory,
                        onAttachReceipt = onAttachReceipt,
                        onRemoveReceipt = onRemoveReceipt,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Form üst başlık ve geri dön aksiyonu bileşenidir.
 */
@Composable
private fun TransactionFormHeader(
    isEditMode: Boolean,
    onBack: () -> Unit,
    isBackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                enabled = isBackEnabled,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Geri dön" },
            ) {
                Text(
                    text = "Geri",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isBackEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = if (isEditMode) "İşlemi Düzenle" else "İşlem Ekle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = FeniqoSpacing.Small),
            )
        }
    }
}

/**
 * Form gövdesi ve dikey kaydırılabilir giriş alanlarıdır.
 */
@Composable
private fun TransactionFormContent(
    uiState: TransactionFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryChange: (EntityId?) -> Unit,
    onDateClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onInstallmentToggle: (Boolean) -> Unit,
    onInstallmentCountChange: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onDismissMessage: () -> Unit,
    onAddCategory: (TransactionType) -> Unit,
    onAttachReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val isFormEnabled = !uiState.isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
    ) {
        // Geçici genel bildirim mesajı (Hata veya bilgi)
        if (uiState.generalMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.generalMessage.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FeniqoSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.generalMessage.toDisplayText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.generalMessage.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.weight(1f),
                    )

                    TextButton(
                        onClick = onDismissMessage,
                        enabled = isFormEnabled,
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                    ) {
                        Text(
                            text = "Kapat",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.generalMessage.isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
        }

        // 1. İşlem Türü (Gider / Gelir)
        TransactionTypeSelector(
            selectedType = uiState.type,
            onTypeChange = onTypeChange,
            enabled = isFormEnabled,
        )

        // 2. Tutar ve Para Birimi
        TransactionAmountField(
            amountText = uiState.amountText,
            currency = uiState.currency,
            onAmountChange = onAmountChange,
            onCurrencyChange = onCurrencyChange,
            errorText = uiState.amountError?.toDisplayText(),
            enabled = isFormEnabled,
        )

        // 3. Kategori Seçimi
        TransactionCategoryPicker(
            availableCategories = uiState.availableCategories,
            selectedCategoryId = uiState.selectedCategoryId,
            onCategoryChange = onCategoryChange,
            categoryLoadError = uiState.categoryLoadError,
            onRetryCategories = onRetryCategories,
            errorText = uiState.categoryError?.toDisplayText(),
            enabled = isFormEnabled,
            onAddCategoryClick = { onAddCategory(uiState.type) },
        )

        // 4. İşlem Tarihi
        TransactionDatePickerField(
            date = uiState.transactionDate,
            onDateClick = onDateClick,
            errorText = uiState.dateError?.toDisplayText(),
            enabled = isFormEnabled,
        )

        // 5. Ödeme Yöntemi
        TransactionPaymentMethodSelector(
            selectedMethod = uiState.paymentMethod,
            onMethodChange = onPaymentMethodChange,
            enabled = isFormEnabled,
        )

        // 6. Taksit Alanı (Yalnız uygun koşullarda gösterilir)
        if (uiState.isInstallmentOptionAvailable) {
            TransactionInstallmentSection(
                isInstallmentEnabled = uiState.isInstallmentEnabled,
                onToggle = onInstallmentToggle,
                installmentCountText = uiState.installmentCountText,
                onCountChange = onInstallmentCountChange,
                errorText = uiState.installmentCountError?.toDisplayText(),
                enabled = isFormEnabled,
            )
        } else if (uiState.isEditMode && uiState.existingInstallment != null) {
            TransactionExistingInstallmentBadge(installment = uiState.existingInstallment)
        }

        // 7. Açıklama
        TransactionDescriptionField(
            description = uiState.description,
            onDescriptionChange = onDescriptionChange,
            errorText = uiState.descriptionError?.toDisplayText(),
            enabled = isFormEnabled,
        )

        // 8. Makbuz Bağlama (Yalnız özellik açıkken gösterilir)
        if (uiState.isReceiptFeatureAvailable) {
            ReceiptAttachmentSection(
                hasReceipt = uiState.hasReceipt,
                isActionInProgress = uiState.isReceiptActionInProgress,
                onAttachReceipt = onAttachReceipt,
                onRemoveReceipt = onRemoveReceipt,
                enabled = isFormEnabled,
            )
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        // 9. Kaydet / Güncelle Butonu
        Button(
            onClick = onSubmit,
            enabled = isFormEnabled,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics {
                    contentDescription = if (uiState.isEditMode) "İşlemi güncelle" else "İşlemi kaydet"
                },
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (uiState.isEditMode) "Güncelle" else "Kaydet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
    }
}

/**
 * İşlem verisi yüklenirken gösterilen görünüm.
 */
@Composable
private fun TransactionLoadingView(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(FeniqoSpacing.Screen),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "İşlem bilgileri yükleniyor...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Kalıcı işlem yükleme hatası durumunda gösterilen görünüm.
 */
@Composable
private fun TransactionErrorView(
    errorMessage: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(FeniqoSpacing.Screen),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(FeniqoRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.ExtraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                Text(
                    text = "İşlem Yüklenemedi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(
                        text = "Geri Dön",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Composable
fun TransactionFormScreenPreview_AddExpense_Light() {
    val state = TransactionFormUiState(
        amountText = "250,00",
        currency = Currency.TRY,
        type = TransactionType.EXPENSE,
        selectedCategoryId = EntityId("cat-1"),
        availableCategories = listOf(
            TransactionCategoryOptionUiModel(
                id = EntityId("cat-1"),
                name = "Market",
                type = TransactionType.EXPENSE,
                colorHex = "#10B981",
                iconKey = "shopping_cart",
                isSelectable = true,
                isHistorical = false,
            ),
            TransactionCategoryOptionUiModel(
                id = EntityId("cat-2"),
                name = "Ulaşım",
                type = TransactionType.EXPENSE,
                colorHex = "#3B82F6",
                iconKey = "directions_bus",
                isSelectable = true,
                isHistorical = false,
            ),
        ),
        transactionDate = LocalDate(2026, 8, 24),
        description = "Haftalık mutfak alışverişi",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        isInstallmentEnabled = true,
        installmentCountText = "3",
    )

    FeniqoTheme(darkTheme = false) {
        TransactionFormScreen(
            uiState = state,
            onAmountChange = {},
            onCurrencyChange = {},
            onTypeChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onDescriptionChange = {},
            onPaymentMethodChange = {},
            onInstallmentToggle = {},
            onInstallmentCountChange = {},
            onRetryCategories = {},
            onSubmit = {},
            onBack = {},
            onDismissMessage = {},
            onAttachReceipt = {},
            onRemoveReceipt = {},
        )
    }
}

@Composable
fun TransactionFormScreenPreview_ValidationErrors_Dark() {
    val state = TransactionFormUiState(
        amountText = "",
        type = TransactionType.EXPENSE,
        amountError = TransactionFormFieldError.AMOUNT_REQUIRED,
        categoryError = TransactionFormFieldError.CATEGORY_REQUIRED,
        dateError = TransactionFormFieldError.DATE_IN_FUTURE,
        descriptionError = TransactionFormFieldError.DESCRIPTION_TOO_LONG,
        installmentCountError = TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL,
        isInstallmentEnabled = true,
        paymentMethod = PaymentMethod.CREDIT_CARD,
    )

    FeniqoTheme(darkTheme = true) {
        TransactionFormScreen(
            uiState = state,
            onAmountChange = {},
            onCurrencyChange = {},
            onTypeChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onDescriptionChange = {},
            onPaymentMethodChange = {},
            onInstallmentToggle = {},
            onInstallmentCountChange = {},
            onRetryCategories = {},
            onSubmit = {},
            onBack = {},
            onDismissMessage = {},
            onAttachReceipt = {},
            onRemoveReceipt = {},
        )
    }
}

@Composable
fun TransactionFormScreenPreview_EditWithHistoricalCategory() {
    val state = TransactionFormUiState(
        amountText = "1500,00",
        currency = Currency.TRY,
        type = TransactionType.EXPENSE,
        isEditMode = true,
        selectedCategoryId = EntityId("cat-hist"),
        availableCategories = listOf(
            TransactionCategoryOptionUiModel(
                id = EntityId("cat-hist"),
                name = "Eski Kira (Silinmiş)",
                type = TransactionType.EXPENSE,
                colorHex = "#9E9E9E",
                iconKey = null,
                isSelectable = false,
                isHistorical = true,
            ),
            TransactionCategoryOptionUiModel(
                id = EntityId("cat-1"),
                name = "Market",
                type = TransactionType.EXPENSE,
                colorHex = "#10B981",
                iconKey = null,
                isSelectable = true,
                isHistorical = false,
            ),
        ),
        transactionDate = LocalDate(2026, 8, 1),
        paymentMethod = PaymentMethod.CREDIT_CARD,
        existingInstallment = InstallmentDisplayModel(number = 2, total = 6, badgeText = "2/6"),
    )

    FeniqoTheme(darkTheme = false) {
        TransactionFormScreen(
            uiState = state,
            onAmountChange = {},
            onCurrencyChange = {},
            onTypeChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onDescriptionChange = {},
            onPaymentMethodChange = {},
            onInstallmentToggle = {},
            onInstallmentCountChange = {},
            onRetryCategories = {},
            onSubmit = {},
            onBack = {},
            onDismissMessage = {},
            onAttachReceipt = {},
            onRemoveReceipt = {},
        )
    }
}

@Composable
fun TransactionFormScreenPreview_CategoryLoadError() {
    val state = TransactionFormUiState(
        amountText = "100,00",
        type = TransactionType.EXPENSE,
        categoryLoadError = FinanceUiMessage.GENERIC_ERROR,
    )

    FeniqoTheme(darkTheme = false) {
        TransactionFormScreen(
            uiState = state,
            onAmountChange = {},
            onCurrencyChange = {},
            onTypeChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onDescriptionChange = {},
            onPaymentMethodChange = {},
            onInstallmentToggle = {},
            onInstallmentCountChange = {},
            onRetryCategories = {},
            onSubmit = {},
            onBack = {},
            onDismissMessage = {},
            onAttachReceipt = {},
            onRemoveReceipt = {},
        )
    }
}

@Composable
fun TransactionFormScreenPreview_TransactionLoadError() {
    val state = TransactionFormUiState(
        isEditMode = true,
        loadError = FinanceUiMessage.TRANSACTION_NOT_FOUND,
    )

    FeniqoTheme(darkTheme = false) {
        TransactionFormScreen(
            uiState = state,
            onAmountChange = {},
            onCurrencyChange = {},
            onTypeChange = {},
            onCategoryChange = {},
            onDateClick = {},
            onDescriptionChange = {},
            onPaymentMethodChange = {},
            onInstallmentToggle = {},
            onInstallmentCountChange = {},
            onRetryCategories = {},
            onSubmit = {},
            onBack = {},
            onDismissMessage = {},
            onAttachReceipt = {},
            onRemoveReceipt = {},
        )
    }
}
