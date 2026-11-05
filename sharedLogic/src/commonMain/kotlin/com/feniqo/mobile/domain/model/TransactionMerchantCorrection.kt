package com.feniqo.mobile.domain.model

/**
 * İşlem detayında kullanıcının verdiği merchant düzeltme komutudur.
 */
sealed interface TransactionMerchantCorrection {
    val transactionId: EntityId

    data class Assign(
        override val transactionId: EntityId,
        val merchantId: EntityId,
    ) : TransactionMerchantCorrection

    data class Block(
        override val transactionId: EntityId,
    ) : TransactionMerchantCorrection
}

/**
 * Merchant düzeltme komutlarının fail-closed ret nedenleri.
 * Serbest string yerine tipli sözleşme sağlar.
 */
enum class MerchantCorrectionRejectionReason(val code: String) {
    AUTH_SESSION_REQUIRED("auth_session_required"),
    TRANSACTION_NOT_FOUND("transaction_not_found"),
    OWNER_MISMATCH("owner_mismatch"),
    WORKSPACE_RESTRICTED("workspace_restricted"),
    TRANSACTION_DELETED("transaction_deleted"),
    INVALID_DESCRIPTION("invalid_description"),
    INVALID_ALIAS("invalid_alias"),
    ALIAS_TOO_LONG("alias_too_long"),
    GENERIC_ALIAS_NOT_ALLOWED("generic_alias_not_allowed"),
    EXCLUDED_TRANSACTION("excluded_transaction"),
    UNKNOWN_MERCHANT("unknown_merchant"),
    CATALOG_VERSION_MISMATCH("catalog_version_mismatch"),
    INVALID_STORED_VERIFICATION("invalid_stored_verification"),
    STORAGE_FAILURE("storage_failure");

    fun toAppError(): AppError =
        when (this) {
            AUTH_SESSION_REQUIRED, OWNER_MISMATCH -> AppError.Authentication(code)
            STORAGE_FAILURE -> AppError.Storage(code)
            else -> AppError.Validation(code)
        }

    companion object {
        fun fromCode(code: String): MerchantCorrectionRejectionReason? =
            entries.firstOrNull { it.code == code }
    }
}
