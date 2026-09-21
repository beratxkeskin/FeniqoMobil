package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.ReceiptAttachmentSection
import com.feniqo.mobile.presentation.component.TransactionAmountField
import com.feniqo.mobile.presentation.component.TransactionCategoryPicker
import com.feniqo.mobile.presentation.component.TransactionDatePickerField
import com.feniqo.mobile.presentation.component.TransactionDescriptionField
import com.feniqo.mobile.presentation.component.TransactionExistingInstallmentBadge
import com.feniqo.mobile.presentation.component.TransactionInstallmentSection
import com.feniqo.mobile.presentation.component.TransactionNoteField
import com.feniqo.mobile.presentation.component.TransactionPaymentMethodSelector
import com.feniqo.mobile.presentation.component.TransactionSplitSection
import com.feniqo.mobile.presentation.component.TransactionTitleField
import com.feniqo.mobile.presentation.component.TransactionTypeSelector
import com.feniqo.mobile.domain.model.TransactionSplitMode
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens
import com.feniqo.mobile.presentation.transaction.CustomSplitUiHelper
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
    onTitleChange: (String) -> Unit = onDescriptionChange,
    onNoteChange: (String) -> Unit = {},
    onAddCategory: (TransactionType) -> Unit = {},
    onPaidByUserSelected: (EntityId) -> Unit = {},
    onParticipantToggled: (EntityId) -> Unit = {},
    onSplitDetailsApplied: (
        payer: EntityId?,
        participants: Set<EntityId>,
        splitMode: TransactionSplitMode,
        customSharesText: Map<EntityId, String>,
    ) -> Unit = { _, _, _, _ -> },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TransactionFormHeader(
                isEditMode = uiState.isEditMode,
                type = uiState.type,
                activeWorkspaceName = uiState.activeWorkspaceName,
                onBack = onBack,
                isBackEnabled = !uiState.isSubmitting,
            )
        },
        bottomBar = {
            if (!uiState.isLoadingTransaction && uiState.loadError == null) {
                TransactionFormBottomBar(
                    isEditMode = uiState.isEditMode,
                    type = uiState.type,
                    isSubmitting = uiState.isSubmitting,
                    onSubmit = onSubmit,
                    onCancel = onBack,
                )
            }
        },
    ) { paddingValues ->
        when {
            // 1. İşlem verisi yükleniyor durumu (kullanıcı etkileşimi kapalıdır)
            uiState.isLoadingTransaction -> {
                TransactionLoadingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            // 2. Kalıcı işlem yükleme hatası
            uiState.loadError != null -> {
                TransactionErrorView(
                    errorMessage = uiState.loadError.toDisplayText(),
                    onBack = onBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                    onTitleChange = onTitleChange,
                    onNoteChange = onNoteChange,
                    onDescriptionChange = onDescriptionChange,
                    onPaymentMethodChange = onPaymentMethodChange,
                    onInstallmentToggle = onInstallmentToggle,
                    onInstallmentCountChange = onInstallmentCountChange,
                    onRetryCategories = onRetryCategories,
                    onDismissMessage = onDismissMessage,
                    onAddCategory = onAddCategory,
                    onAttachReceipt = onAttachReceipt,
                    onRemoveReceipt = onRemoveReceipt,
                    onPaidByUserSelected = onPaidByUserSelected,
                    onParticipantToggled = onParticipantToggled,
                    onSplitDetailsApplied = onSplitDetailsApplied,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
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
    type: TransactionType,
    activeWorkspaceName: String?,
    onBack: () -> Unit,
    isBackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Small, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = isBackEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Geri dön" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = if (isBackEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }

                val titleText = when {
                    isEditMode -> "İşlemi düzenle"
                    type == TransactionType.EXPENSE -> "Gider ekle"
                    else -> "Gelir ekle"
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ActiveWorkspaceIndicator(
                workspaceName = activeWorkspaceName,
                isCompact = true,
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
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onInstallmentToggle: (Boolean) -> Unit,
    onInstallmentCountChange: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onDismissMessage: () -> Unit,
    onAddCategory: (TransactionType) -> Unit,
    onAttachReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onPaidByUserSelected: (EntityId) -> Unit,
    onParticipantToggled: (EntityId) -> Unit,
    onSplitDetailsApplied: (
        payer: EntityId?,
        participants: Set<EntityId>,
        splitMode: TransactionSplitMode,
        customSharesText: Map<EntityId, String>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val isFormEnabled = !uiState.isSubmitting

    Column(
        modifier = modifier
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

        // 3. İşlem Adı
        TransactionTitleField(
            title = uiState.title.ifEmpty { uiState.description },
            onTitleChange = onTitleChange,
            errorText = (uiState.titleError ?: uiState.descriptionError)?.toDisplayText(),
            enabled = isFormEnabled,
            isExpense = uiState.type == TransactionType.EXPENSE,
        )

        // 4. Kategori Seçimi
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

        // 5. Ödeme Yöntemi
        TransactionPaymentMethodSelector(
            selectedMethod = uiState.paymentMethod,
            onMethodChange = onPaymentMethodChange,
            enabled = isFormEnabled,
        )

        // 6. İşlem Tarihi
        TransactionDatePickerField(
            date = uiState.transactionDate,
            onDateClick = onDateClick,
            errorText = uiState.dateError?.toDisplayText(),
            enabled = isFormEnabled,
        )

        // 7. Daha Fazla Ayrıntı (Akordeon)
        var isMoreDetailsExpanded by remember { mutableStateOf(false) }

        // Yalnız ayrıntı alanlarında doğrulama hatası oluştuğunda akordeonu otomatik aç
        LaunchedEffect(uiState.noteError, uiState.installmentCountError, uiState.splitError, uiState.customShareErrors) {
            if (uiState.noteError != null || uiState.installmentCountError != null || uiState.splitError != null || uiState.customShareErrors.isNotEmpty()) {
                isMoreDetailsExpanded = true
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { isMoreDetailsExpanded = !isMoreDetailsExpanded },
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Ayrıntılar",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val hasDetailData = uiState.note.isNotBlank() ||
                        uiState.hasReceipt ||
                        uiState.isInstallmentEnabled ||
                        uiState.existingInstallment != null ||
                        (uiState.isSharedExpense && uiState.selectedParticipantUserIds.size > 1)
                    if (hasDetailData && !isMoreDetailsExpanded) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "Dolu",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isMoreDetailsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isMoreDetailsExpanded) "Ayrıntıları gizle" else "Ayrıntıları göster",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isMoreDetailsExpanded) {
            var draft by remember { mutableStateOf(com.feniqo.mobile.presentation.transaction.TransactionDetailsDraft.from(uiState)) }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isMoreDetailsExpanded = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isMoreDetailsExpanded = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vazgeç")
                            }
                            Text("Ayrıntılar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        TransactionNoteField(note = draft.note, onNoteChange = { draft = draft.copy(note = it) },
                            errorText = uiState.noteError?.toDisplayText(), enabled = isFormEnabled)
                        if (uiState.isInstallmentOptionAvailable) {
                            TransactionInstallmentSection(isInstallmentEnabled = draft.installmentEnabled,
                                onToggle = { draft = draft.copy(installmentEnabled = it) },
                                installmentCountText = draft.installmentCount,
                                onCountChange = { draft = draft.copy(installmentCount = it) },
                                errorText = uiState.installmentCountError?.toDisplayText(), enabled = isFormEnabled)
                        } else if (uiState.existingInstallment != null) {
                            TransactionExistingInstallmentBadge(uiState.existingInstallment)
                        }
                        if (uiState.isReceiptFeatureAvailable) {
                            Text("Makbuz", style = MaterialTheme.typography.titleMedium)
                            Text("Tarama, bilgileri forma aktarır. Dosyayı kalıcı makbuz eki olarak saklamaz.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ReceiptAttachmentSection(hasReceipt = draft.hasReceipt,
                                isActionInProgress = uiState.isReceiptActionInProgress,
                                onAttachReceipt = onAttachReceipt,
                                onRemoveReceipt = { draft = draft.copy(hasReceipt = false) }, enabled = isFormEnabled)
                        }
                        if (uiState.isSharedExpense) {
                            val activeMemberIds = remember(uiState.workspaceMembers) {
                                uiState.workspaceMembers.filter { it.isActive }.map { it.userId }.toSet()
                            }
                            val liveDraftErrors = remember(
                                uiState.currency,
                                draft.payer,
                                draft.participants,
                                draft.customSharesText,
                                uiState.customShareErrors,
                                activeMemberIds,
                            ) {
                                CustomSplitUiHelper.validateDraftParticipantShares(
                                    currency = uiState.currency,
                                    payer = draft.payer,
                                    participants = draft.participants,
                                    customSharesText = draft.customSharesText,
                                    priorSubmitErrors = uiState.customShareErrors,
                                    activeMembers = activeMemberIds,
                                )
                            }
                            TransactionSplitSection(
                                members = uiState.workspaceMembers,
                                isLoading = uiState.isLoadingWorkspaceMembers,
                                selectedPaidByUserId = draft.payer,
                                selectedParticipantUserIds = draft.participants,
                                onPaidByUserSelected = { draft = draft.selectPayer(it) },
                                onParticipantToggled = { draft = draft.toggleParticipant(it) },
                                errorText = uiState.splitError?.toDisplayText(),
                                enabled = isFormEnabled && uiState.canManageSplit,
                                splitMode = draft.splitMode,
                                customSharesText = draft.customSharesText,
                                customShareErrors = liveDraftErrors,
                                currency = uiState.currency,
                                amountText = uiState.amountText,
                                isInstallmentOptionAvailable = uiState.isInstallmentOptionAvailable,
                                isInstallmentEnabled = draft.installmentEnabled,
                                onToggleInstallment = { draft = draft.copy(installmentEnabled = it) },
                                onSplitModeChanged = { draft = draft.setSplitMode(it) },
                                onCustomShareChanged = { userId, text -> draft = draft.updateCustomShare(userId, text) },
                                onApplyPayerRemainder = {
                                    val isPayerActive = draft.payer != null && draft.payer in activeMemberIds
                                    val areAllParticipantsActive = draft.participants.all { it in activeMemberIds }
                                    if (isPayerActive && areAllParticipantsActive) {
                                        draft = draft.copy(
                                            customSharesText = CustomSplitUiHelper.applyPayerRemainder(
                                                amountText = uiState.amountText,
                                                currency = uiState.currency,
                                                payer = draft.payer,
                                                participants = draft.participants,
                                                customSharesText = draft.customSharesText,
                                                activeMembers = activeMemberIds,
                                            ),
                                        )
                                    }
                                },
                            )
                        } else {
                            Text("Ortak harcama yalnız uygun ortak çalışma alanında kullanılabilir.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
                            onNoteChange(draft.note)
                            if (uiState.hasReceipt && !draft.hasReceipt) onRemoveReceipt()
                            if (uiState.isInstallmentOptionAvailable) {
                                onInstallmentToggle(draft.installmentEnabled)
                                onInstallmentCountChange(draft.installmentCount)
                            }
                            if (uiState.canManageSplit) {
                                onSplitDetailsApplied(
                                    draft.payer,
                                    draft.participants,
                                    draft.splitMode,
                                    draft.customSharesText,
                                )
                            }
                            isMoreDetailsExpanded = false
                        }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) { Text("Ayrıntıları uygula") }
                        TextButton(onClick = { isMoreDetailsExpanded = false }, modifier = Modifier.fillMaxWidth()) { Text("Vazgeç") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
    }
}

/**
 * Ekranın alt kısmında sabit duran tek birincil işlem butonu.
 */
@Composable
private fun TransactionFormBottomBar(
    isEditMode: Boolean,
    type: TransactionType,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
        ) {
            val submitButtonText = when {
                isEditMode -> "Değişiklikleri kaydet"
                type == TransactionType.EXPENSE -> "Gideri kaydet"
                else -> "Geliri kaydet"
            }

            if (isEditMode) {
                TextButton(onClick = onCancel, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text("Vazgeç")
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        contentDescription = submitButtonText
                    },
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = submitButtonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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
                colorHex = "#EF4444",
                iconKey = "groceries",
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
                colorHex = "#EF4444",
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
