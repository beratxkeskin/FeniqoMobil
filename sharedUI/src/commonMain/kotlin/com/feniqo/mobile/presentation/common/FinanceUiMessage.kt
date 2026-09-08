package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.AppError

/**
 * Finans akışına ait tipli ve güvenli UI mesajlarıdır.
 */
enum class FinanceUiMessage {
    INVALID_AMOUNT,
    AMOUNT_REQUIRED,
    AMOUNT_TOO_SMALL,
    DATE_IN_FUTURE,
    CATEGORY_NOT_FOUND,
    CATEGORY_TYPE_MISMATCH,
    CATEGORY_WORKSPACE_MISMATCH,
    WORKSPACE_IMMUTABLE,
    TRANSACTION_NOT_FOUND,
    INSTALLMENT_COUNT_INVALID,
    INSTALLMENT_SCOPE_REQUIRED,
    INSTALLMENT_SCOPE_NOT_APPLICABLE,
    INSTALLMENT_DATA_INVALID,
    CATEGORY_NAME_REQUIRED,
    CATEGORY_NAME_TOO_LONG,
    CATEGORY_DUPLICATE_NAME,
    CATEGORY_COLOR_INVALID,
    DEFAULT_CATEGORY_IMMUTABLE,
    SESSION_EXPIRED,
    PERMISSION_DENIED,
    STORAGE_ERROR,
    NETWORK_ERROR,
    CONFLICT,
    GENERIC_ERROR,
    TRANSACTION_SAVED,
    TRANSACTION_DELETED,
    CATEGORY_SAVED,
    CATEGORY_DELETED,
    BUDGET_SAVED,
    BUDGET_DELETED,
    BUDGETS_COPIED,
    BUDGET_COPY_MONTHS_SAME,
    SUBSCRIPTION_SAVED,
    SUBSCRIPTION_DELETED,
    SUBSCRIPTION_RENEWED,
    SUBSCRIPTION_COMPLETED,
    GOAL_SAVED,
    GOAL_DELETED,
    GOAL_CONTRIBUTION_ADDED,
    DEBT_SAVED,
    DEBT_DELETED,
    DEBT_PAYMENT_ADDED,
    CONFLICT_RESOLUTION_STALE,
    WORKSPACE_CONFLICT_REMOTE_TOMBSTONE,
    WORKSPACE_CONFLICT_OWNER_MISMATCH,
    CONFLICT_NOT_FOUND,
    CONFLICT_RESOLUTION_FAILED,
    WORKSPACE_NOT_FOUND,
    WORKSPACE_INVITATION_NOT_FOUND,
    WORKSPACE_INVITATION_EXPIRED,
    WORKSPACE_INVITATION_LIMIT_REACHED,
    WORKSPACE_CANNOT_LEAVE_AS_OWNER,
    WORKSPACE_ACTOR_NOT_MEMBER,
    WORKSPACE_ACTOR_NOT_PERMITTED,
    WORKSPACE_CANNOT_CHANGE_OWN_ROLE,
    WORKSPACE_OWNER_ROLE_CHANGE_REQUIRES_TRANSFER,
    WORKSPACE_TARGET_MEMBER_NOT_FOUND,
    WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE,
    WORKSPACE_TRANSFER_ACTOR_NOT_OWNER,
    WORKSPACE_TRANSFER_TARGET_NOT_MEMBER,
    WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER,
    WORKSPACE_TRANSFER_VERSION_CONFLICT,
    WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER,
    WORKSPACE_CANNOT_REMOVE_SELF_MEMBER,
    WORKSPACE_CANNOT_REMOVE_WORKSPACE_OWNER;


    val isError: Boolean
        get() = when (this) {
            TRANSACTION_SAVED,
            TRANSACTION_DELETED,
            CATEGORY_SAVED,
            CATEGORY_DELETED,
            BUDGET_SAVED,
            BUDGET_DELETED,
            BUDGETS_COPIED,
            SUBSCRIPTION_SAVED,
            SUBSCRIPTION_DELETED,
            SUBSCRIPTION_RENEWED,
            SUBSCRIPTION_COMPLETED,
            GOAL_SAVED,
            GOAL_DELETED,
            GOAL_CONTRIBUTION_ADDED,
            DEBT_SAVED,
            DEBT_DELETED,
            DEBT_PAYMENT_ADDED -> false
            else -> true
        }

    fun toDisplayText(): String = when (this) {

        INVALID_AMOUNT -> "Geçerli bir tutar girin."
        AMOUNT_REQUIRED -> "Lütfen bir tutar girin."
        AMOUNT_TOO_SMALL -> "Tutar taksit sayısına bölünemeyecek kadar küçük."
        DATE_IN_FUTURE -> "İşlem tarihi bugünden ileri olamaz."
        CATEGORY_NOT_FOUND -> "Seçilen kategori bulunamadı."
        CATEGORY_TYPE_MISMATCH -> "Kategori türü işlem türü ile eşleşmiyor."
        CATEGORY_WORKSPACE_MISMATCH -> "Seçilen kategori bu çalışma alanında kullanılamaz."
        WORKSPACE_IMMUTABLE -> "İşlemin çalışma alanı değiştirilemez."
        TRANSACTION_NOT_FOUND -> "İşlem bulunamadı."
        INSTALLMENT_COUNT_INVALID -> "Taksit sayısı 2 ile 60 arasında olmalıdır."
        INSTALLMENT_SCOPE_REQUIRED -> "Lütfen silme kapsamını seçin."
        INSTALLMENT_SCOPE_NOT_APPLICABLE -> "Bu işlem taksitli olmadığı için silme kapsamı seçilemez."
        INSTALLMENT_DATA_INVALID -> "Taksit bilgileri geçersiz veya tutarsız."
        CATEGORY_NAME_REQUIRED -> "Kategori adı boş olamaz."
        CATEGORY_NAME_TOO_LONG -> "Kategori adı en fazla 50 karakter olabilir."
        CATEGORY_DUPLICATE_NAME -> "Bu isim daha önce kullanılmış."
        CATEGORY_COLOR_INVALID -> "Geçersiz kategori rengi."
        DEFAULT_CATEGORY_IMMUTABLE -> "Sistem kategorileri düzenlenemez veya silinemez."
        SESSION_EXPIRED -> "Oturum süreniz doldu, lütfen tekrar giriş yapın."
        PERMISSION_DENIED -> "Bu işlem için yetkiniz bulunmuyor."
        STORAGE_ERROR -> "Veritabanı işlemi sırasında bir hata oluştu."
        NETWORK_ERROR -> "Ağ bağlantısı kurulamadı. Lütfen internet bağlantınızı kontrol edin."
        CONFLICT -> "Bu kayıt sunucudaki güncel veriyle çakıştı."
        GENERIC_ERROR -> "Beklenmeyen bir hata oluştu."
        TRANSACTION_SAVED -> "İşlem başarıyla kaydedildi."
        TRANSACTION_DELETED -> "İşlem başarıyla silindi."
        CATEGORY_SAVED -> "Kategori başarıyla kaydedildi."
        CATEGORY_DELETED -> "Kategori başarıyla silindi."
        BUDGET_SAVED -> "Bütçe başarıyla kaydedildi."
        BUDGET_DELETED -> "Bütçe başarıyla silindi."
        BUDGETS_COPIED -> "Bütçeler başarıyla kopyalandı."
        BUDGET_COPY_MONTHS_SAME -> "Kaynak ve hedef ay aynı olamaz."
        SUBSCRIPTION_SAVED -> "Abonelik başarıyla kaydedildi."
        SUBSCRIPTION_DELETED -> "Abonelik başarıyla silindi."
        SUBSCRIPTION_RENEWED -> "Abonelik yenilemesi kaydedildi."
        SUBSCRIPTION_COMPLETED -> "Abonelik süresi tamamlandı ve pasife alındı."
        GOAL_SAVED -> "Hedef başarıyla kaydedildi."
        GOAL_DELETED -> "Hedef başarıyla silindi."
        GOAL_CONTRIBUTION_ADDED -> "Hedef hareketi başarıyla kaydedildi."
        DEBT_SAVED -> "Borç / alacak kaydı başarıyla kaydedildi."
        DEBT_DELETED -> "Borç / alacak kaydı başarıyla silindi."
        DEBT_PAYMENT_ADDED -> "Ödeme / tahsilat başarıyla kaydedildi."
        CONFLICT_RESOLUTION_STALE -> "Çakışma bilgisi güncel değil veya yeni bir değişiklik yapıldı. Lütfen tekrar deneyin."
        WORKSPACE_CONFLICT_REMOTE_TOMBSTONE -> "Çalışma alanı sunucuda silinmiş olduğundan oluşturma işlemi korunamadı."
        WORKSPACE_CONFLICT_OWNER_MISMATCH -> "Çalışma alanı sahibi eşleşmediğinden yerel değişiklik uygulanamadı."
        CONFLICT_NOT_FOUND -> "Çakışma kaydı bulunamadı."
        CONFLICT_RESOLUTION_FAILED -> "Çakışma çözülemedi. Lütfen daha sonra tekrar deneyin."
        WORKSPACE_NOT_FOUND -> "Çalışma alanı bulunamadı veya erişim yetkiniz yok."
        WORKSPACE_INVITATION_NOT_FOUND -> "Davet kodu bulunamadı veya geçersiz."
        WORKSPACE_INVITATION_EXPIRED -> "Davet kodunun süresi dolmuş."
        WORKSPACE_INVITATION_LIMIT_REACHED -> "Davet kodunun kullanım limiti dolmuş."
        WORKSPACE_CANNOT_LEAVE_AS_OWNER -> "Çalışma alanı sahibi ayrılmadan önce sahipliği devretmelidir."
        WORKSPACE_ACTOR_NOT_MEMBER -> "Bu çalışma alanının üyesi değilsiniz."
        WORKSPACE_ACTOR_NOT_PERMITTED -> "Bu işlem için yetkiniz bulunmuyor."
        WORKSPACE_CANNOT_CHANGE_OWN_ROLE -> "Kendi rolünüzü değiştiremezsiniz."
        WORKSPACE_OWNER_ROLE_CHANGE_REQUIRES_TRANSFER -> "Sahiplik rolü atamak için sahiplik devri yapılmalıdır."
        WORKSPACE_TARGET_MEMBER_NOT_FOUND -> "İşlem yapılmak istenen üye bulunamadı."
        WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE -> "Üyelik sürüm bilgisi alınamadı. Lütfen senkronizasyonun tamamlanmasını bekleyin."
        WORKSPACE_TRANSFER_ACTOR_NOT_OWNER -> "Sahiplik devrini yalnızca çalışma alanı sahibi gerçekleştirebilir."
        WORKSPACE_TRANSFER_TARGET_NOT_MEMBER -> "Sahipliğin devredileceği üye bulunamadı."
        WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER -> "Seçilen üye zaten bu çalışma alanının sahibidir."
        WORKSPACE_TRANSFER_VERSION_CONFLICT -> "Çalışma alanı bilgileri güncel değil. Lütfen sayfayı yenileyip tekrar deneyin."
        WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER -> "Bekleyen yerel değişiklikler varken sahiplik devri yapılamaz. Lütfen senkronizasyonun tamamlanmasını bekleyin."
        WORKSPACE_CANNOT_REMOVE_SELF_MEMBER -> "Kendinizi üye çıkarma işlemiyle çıkaramazsınız. Alandan ayrılmak için alandan ayrılma seçeneğini kullanın."
        WORKSPACE_CANNOT_REMOVE_WORKSPACE_OWNER -> "Çalışma alanı sahibi üyelikten çıkarılamaz."
    }

}


/**
 * Domain AppError hatalarını tipli FinanceUiMessage'a dönüştürür.
 */
fun AppError.toFinanceUiMessage(): FinanceUiMessage = when (this) {
    is AppError.Validation -> when (code) {
        "workspace_not_found" -> FinanceUiMessage.WORKSPACE_NOT_FOUND
        "workspace_invitation_not_found" -> FinanceUiMessage.WORKSPACE_INVITATION_NOT_FOUND
        "workspace_invitation_expired" -> FinanceUiMessage.WORKSPACE_INVITATION_EXPIRED
        "workspace_invitation_limit_reached" -> FinanceUiMessage.WORKSPACE_INVITATION_LIMIT_REACHED
        "cannot_leave_as_owner_requires_transfer" -> FinanceUiMessage.WORKSPACE_CANNOT_LEAVE_AS_OWNER
        "cannot_remove_self_member" -> FinanceUiMessage.WORKSPACE_CANNOT_REMOVE_SELF_MEMBER
        "cannot_remove_workspace_owner", "cannot_remove_last_owner" -> FinanceUiMessage.WORKSPACE_CANNOT_REMOVE_WORKSPACE_OWNER
        "cannot_change_own_role" -> FinanceUiMessage.WORKSPACE_CANNOT_CHANGE_OWN_ROLE
        "owner_role_change_requires_transfer" -> FinanceUiMessage.WORKSPACE_OWNER_ROLE_CHANGE_REQUIRES_TRANSFER
        "target_member_not_found" -> FinanceUiMessage.WORKSPACE_TARGET_MEMBER_NOT_FOUND
        "ownership_transfer_target_not_member" -> FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_NOT_MEMBER
        "ownership_transfer_target_already_owner", "transfer_target_already_owner" -> FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER
        "ownership_transfer_actor_not_owner" -> FinanceUiMessage.WORKSPACE_TRANSFER_ACTOR_NOT_OWNER
        "local_member_version_unavailable" -> FinanceUiMessage.WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE
        "amount_empty" -> FinanceUiMessage.AMOUNT_REQUIRED
        "amount_invalid_format", "amount_out_of_range" -> FinanceUiMessage.INVALID_AMOUNT
        "amount_not_positive", "transaction_amount_must_be_positive" -> FinanceUiMessage.INVALID_AMOUNT
        "installment_amount_too_small" -> FinanceUiMessage.AMOUNT_TOO_SMALL
        "transaction_date_cannot_be_future" -> FinanceUiMessage.DATE_IN_FUTURE
        "transaction_category_not_found", "category_not_found", "budget_category_not_found" -> FinanceUiMessage.CATEGORY_NOT_FOUND
        "transaction_category_type_mismatch", "budget_category_must_be_expense" -> FinanceUiMessage.CATEGORY_TYPE_MISMATCH
        "category_workspace_mismatch" -> FinanceUiMessage.CATEGORY_WORKSPACE_MISMATCH
        "transaction_workspace_immutable" -> FinanceUiMessage.WORKSPACE_IMMUTABLE
        "transaction_not_found" -> FinanceUiMessage.TRANSACTION_NOT_FOUND
        "installment_count_out_of_range" -> FinanceUiMessage.INSTALLMENT_COUNT_INVALID
        "installment_delete_scope_required" -> FinanceUiMessage.INSTALLMENT_SCOPE_REQUIRED
        "installment_scope_not_applicable" -> FinanceUiMessage.INSTALLMENT_SCOPE_NOT_APPLICABLE
        "installment_type_must_be_expense",
        "installment_payment_must_be_credit_card",
        "installment_info_required",
        "installment_group_mismatch",
        "installment_total_mismatch" -> FinanceUiMessage.INSTALLMENT_DATA_INVALID
        "category_name_required" -> FinanceUiMessage.CATEGORY_NAME_REQUIRED
        "category_name_too_long" -> FinanceUiMessage.CATEGORY_NAME_TOO_LONG
        "category_duplicate_name" -> FinanceUiMessage.CATEGORY_DUPLICATE_NAME
        "category_color_invalid" -> FinanceUiMessage.CATEGORY_COLOR_INVALID
        "category_default_cannot_be_deleted",
        "category_default_cannot_be_modified" -> FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE
        "budget_source_and_target_month_same" -> FinanceUiMessage.BUDGET_COPY_MONTHS_SAME
        else -> FinanceUiMessage.GENERIC_ERROR
    }
    is AppError.Authentication -> when (code) {
        "auth_session_required", "auth_session_expired" -> FinanceUiMessage.SESSION_EXPIRED
        "actor_not_member" -> FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER
        "actor_not_permitted" -> FinanceUiMessage.WORKSPACE_ACTOR_NOT_PERMITTED
        "ownership_transfer_actor_not_owner", "transfer_actor_not_owner" -> FinanceUiMessage.WORKSPACE_TRANSFER_ACTOR_NOT_OWNER
        "transaction_owner_mismatch", "category_owner_mismatch", "budget_owner_mismatch" -> FinanceUiMessage.PERMISSION_DENIED
        else -> FinanceUiMessage.GENERIC_ERROR
    }
    is AppError.Network -> FinanceUiMessage.NETWORK_ERROR
    is AppError.Storage -> when (code) {
        "sync.conflict_resolution_failed" -> FinanceUiMessage.CONFLICT_RESOLUTION_FAILED
        else -> FinanceUiMessage.STORAGE_ERROR
    }
    is AppError.Conflict -> when (code) {
        "sync.conflict_resolution_stale" -> FinanceUiMessage.CONFLICT_RESOLUTION_STALE
        "sync.workspace_create_conflict_remote_tombstone" -> FinanceUiMessage.WORKSPACE_CONFLICT_REMOTE_TOMBSTONE
        "sync.workspace_create_conflict_owner_mismatch" -> FinanceUiMessage.WORKSPACE_CONFLICT_OWNER_MISMATCH
        "sync.conflict_not_found" -> FinanceUiMessage.CONFLICT_NOT_FOUND
        "ownership_transfer_version_conflict" -> FinanceUiMessage.WORKSPACE_TRANSFER_VERSION_CONFLICT
        "local_uncommitted_changes_prevent_ownership_transfer" -> FinanceUiMessage.WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER
        else -> FinanceUiMessage.CONFLICT
    }
    is AppError.Unknown -> FinanceUiMessage.GENERIC_ERROR
}

