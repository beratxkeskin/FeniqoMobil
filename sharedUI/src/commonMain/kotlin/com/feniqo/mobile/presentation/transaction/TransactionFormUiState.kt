package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel

/**
 * İşlem formu alan bazlı doğrulama hatalarıdır.
 */
enum class TransactionFormFieldError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_NON_POSITIVE,
    AMOUNT_TOO_LARGE,
    CATEGORY_REQUIRED,
    CATEGORY_UNAVAILABLE,
    DATE_REQUIRED,
    DATE_IN_FUTURE,
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    NOTE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    INSTALLMENT_COUNT_INVALID,
    INSTALLMENT_AMOUNT_TOO_SMALL,
    SPLIT_PAYER_REQUIRED,
    SPLIT_PARTICIPANTS_REQUIRED,
    SPLIT_PAYER_NOT_IN_PARTICIPANTS,
    SPLIT_CUSTOM_SHARES_REQUIRED,
    SPLIT_CUSTOM_SHARE_REQUIRED,
    SPLIT_CUSTOM_SHARE_INVALID,
    SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED,
    SPLIT_CUSTOM_SHARE_NEGATIVE,
    SPLIT_CUSTOM_SHARE_TOO_LARGE,
    SPLIT_CUSTOM_TOTAL_MISMATCH,
    SPLIT_CUSTOM_TOTAL_OVERFLOW,
    SPLIT_CUSTOM_MEMBER_NOT_ACTIVE,
    SPLIT_PARTICIPANT_SET_MISMATCH,
    SPLIT_CUSTOM_NOT_ALLOWED_IN_PERSONAL,
    SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT;

    fun toDisplayText(): String = when (this) {
        AMOUNT_REQUIRED -> "Tutar boş bırakılamaz."
        AMOUNT_INVALID -> "Geçerli bir tutar girin."
        AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
        AMOUNT_TOO_LARGE -> "Tutar izin verilen sınırı aşıyor."
        CATEGORY_REQUIRED -> "Lütfen bir kategori seçin."
        CATEGORY_UNAVAILABLE -> "Bu kategori artık kullanılamıyor. Lütfen başka bir kategori seçin."
        DATE_REQUIRED -> "Lütfen bir tarih seçin."
        DATE_IN_FUTURE -> "İşlem tarihi bugünden ileri olamaz."
        TITLE_REQUIRED -> "İşlem adı boş bırakılamaz."
        TITLE_TOO_LONG -> "İşlem adı 100 karakterden uzun olamaz."
        NOTE_TOO_LONG -> "Not 500 karakterden uzun olamaz."
        DESCRIPTION_TOO_LONG -> "Açıklama 100 karakterden uzun olamaz."
        INSTALLMENT_COUNT_INVALID -> "Taksit sayısı 2 ile 60 arasında olmalıdır."
        INSTALLMENT_AMOUNT_TOO_SMALL -> "Toplam tutar seçilen taksit sayısı için çok küçük."
        SPLIT_PAYER_REQUIRED -> "Lütfen harcamayı ödeyen kişiyi seçin."
        SPLIT_PARTICIPANTS_REQUIRED -> "En az bir katılımcı seçilmelidir."
        SPLIT_PAYER_NOT_IN_PARTICIPANTS -> "Ödeyen kişi katılımcılar arasında olmalıdır."
        SPLIT_CUSTOM_SHARES_REQUIRED -> "Özel dağıtım için tüm katılımcıların payları girilmelidir."
        SPLIT_CUSTOM_SHARE_REQUIRED -> "Pay tutarı boş bırakılamaz."
        SPLIT_CUSTOM_SHARE_INVALID -> "Geçerli bir pay tutarı girin."
        SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED -> "Yalnızca harcamayı ödeyen kişi 0 ₺ pay alabilir."
        SPLIT_CUSTOM_SHARE_NEGATIVE -> "Pay tutarı negatif olamaz."
        SPLIT_CUSTOM_SHARE_TOO_LARGE -> "Pay tutarı izin verilen sınırı aşıyor."
        SPLIT_CUSTOM_TOTAL_MISMATCH -> "Paylar toplamı harcama tutarına eşit olmalıdır."
        SPLIT_CUSTOM_TOTAL_OVERFLOW -> "Paylar toplamı sayı sınırını aşıyor."
        SPLIT_CUSTOM_MEMBER_NOT_ACTIVE -> "Pay sahibi çalışma alanında aktif bir üye olmalıdır."
        SPLIT_PARTICIPANT_SET_MISMATCH -> "Katılımcılar ile pay sahipleri aynı küme olmalıdır."
        SPLIT_CUSTOM_NOT_ALLOWED_IN_PERSONAL -> "Özel dağıtım yalnızca ortak çalışma alanı harcamalarında kullanılabilir."
        SPLIT_CUSTOM_NOT_SUPPORTED_WITH_INSTALLMENT -> "Özel tutarlı paylaşım taksitli işlemlerde kullanılamaz. Lütfen eşit paylaşımı seçin veya taksiti kapatın."
    }
}

/**
 * İşlem formunda gösterilecek kategori seçeneği UI modelidir.
 */
data class TransactionCategoryOptionUiModel(
    val id: EntityId,
    val name: String,
    val type: TransactionType,
    val colorHex: String?,
    val iconKey: String?,
    val isSelectable: Boolean,
    val isHistorical: Boolean,
)

/**
 * İşlem ekleme ve düzenleme formu UI durum modelidir.
 */
data class TransactionFormUiState(
    val amountText: String = "",
    val currency: Currency = Currency.TRY,
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedCategoryId: EntityId? = null,
    val availableCategories: List<TransactionCategoryOptionUiModel> = emptyList(),
    val transactionDate: LocalDate? = null,
    val title: String = "",
    val note: String = "",
    val description: String = title,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isInstallmentEnabled: Boolean = false,
    val installmentCountText: String = "3",
    val hasReceipt: Boolean = false,
    val isReceiptActionInProgress: Boolean = false,
    val isReceiptFeatureAvailable: Boolean = false,
    val isEditMode: Boolean = false,
    val isLoadingTransaction: Boolean = false,
    val existingInstallment: InstallmentDisplayModel? = null,
    val isSubmitting: Boolean = false,
    val activeWorkspaceId: EntityId? = null,
    val activeWorkspaceName: String? = null,
    val activeWorkspaceType: com.feniqo.mobile.domain.model.WorkspaceType = com.feniqo.mobile.domain.model.WorkspaceType.SHARED,
    val workspaceMembers: List<WorkspaceMemberUiModel> = emptyList(),
    val isLoadingWorkspaceMembers: Boolean = false,
    val selectedPaidByUserId: EntityId? = null,
    val selectedParticipantUserIds: Set<EntityId> = emptySet(),
    val splitMode: com.feniqo.mobile.domain.model.TransactionSplitMode = com.feniqo.mobile.domain.model.TransactionSplitMode.EQUAL,
    val customSharesText: Map<EntityId, String> = emptyMap(),
    val customShareErrors: Map<EntityId, TransactionFormFieldError> = emptyMap(),
    val splitError: TransactionFormFieldError? = null,
    val loadError: FinanceUiMessage? = null,
    val categoryLoadError: FinanceUiMessage? = null,
    val amountError: TransactionFormFieldError? = null,
    val categoryError: TransactionFormFieldError? = null,
    val dateError: TransactionFormFieldError? = null,
    val titleError: TransactionFormFieldError? = null,
    val noteError: TransactionFormFieldError? = null,
    val descriptionError: TransactionFormFieldError? = titleError,
    val installmentCountError: TransactionFormFieldError? = null,
    val generalMessage: FinanceUiMessage? = null,
    val hasUnsavedChanges: Boolean = false,
) {
    val isInstallmentOptionAvailable: Boolean
        get() = !isEditMode && type == TransactionType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD

    val isSharedExpense: Boolean
        get() = activeWorkspaceId != null &&
            activeWorkspaceType == com.feniqo.mobile.domain.model.WorkspaceType.SHARED &&
            type == TransactionType.EXPENSE

    val canManageSplit: Boolean
        get() = isSharedExpense && workspaceMembers.any {
            it.isCurrentUser && it.role != com.feniqo.mobile.domain.model.WorkspaceRole.VIEWER
        }

    val eligibleSplitMembers: List<WorkspaceMemberUiModel>
        get() = workspaceMembers

    val canSubmitSplit: Boolean
        get() {
            if (!isSharedExpense) return true
            if (isLoadingWorkspaceMembers) return false
            if (isInstallmentOptionAvailable && isInstallmentEnabled && splitMode == com.feniqo.mobile.domain.model.TransactionSplitMode.CUSTOM) {
                return false
            }
            val payer = selectedPaidByUserId ?: return false
            if (selectedParticipantUserIds.isEmpty()) return false
            if (payer !in selectedParticipantUserIds) return false
            val activeMemberIds = workspaceMembers.filter { it.isActive }.map { it.userId }.toSet()
            if (payer !in activeMemberIds) return false
            if (!selectedParticipantUserIds.all { it in activeMemberIds }) return false
            if (splitMode == com.feniqo.mobile.domain.model.TransactionSplitMode.CUSTOM) {
                if (selectedParticipantUserIds.any { it !in customSharesText.keys || customSharesText[it].isNullOrBlank() }) {
                    return false
                }
                if (customShareErrors.isNotEmpty()) return false
                if (splitError != null) return false
            }
            return true
        }
}
