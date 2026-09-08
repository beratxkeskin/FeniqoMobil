package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.presentation.category.CategoryFormFieldError
import com.feniqo.mobile.presentation.transaction.TransactionFormFieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinanceUiMessageTest {

    @Test
    fun toFinanceUiMessage_mapsKnownValidationCodesCorrectly() {
        assertEquals(FinanceUiMessage.AMOUNT_REQUIRED, AppError.Validation("amount_empty").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INVALID_AMOUNT, AppError.Validation("amount_invalid_format").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INVALID_AMOUNT, AppError.Validation("amount_out_of_range").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INVALID_AMOUNT, AppError.Validation("amount_not_positive").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INVALID_AMOUNT, AppError.Validation("transaction_amount_must_be_positive").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.DATE_IN_FUTURE, AppError.Validation("transaction_date_cannot_be_future").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_NOT_FOUND, AppError.Validation("transaction_category_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_TYPE_MISMATCH, AppError.Validation("transaction_category_type_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_WORKSPACE_MISMATCH, AppError.Validation("category_workspace_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_IMMUTABLE, AppError.Validation("transaction_workspace_immutable").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.TRANSACTION_NOT_FOUND, AppError.Validation("transaction_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_COUNT_INVALID, AppError.Validation("installment_count_out_of_range").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.AMOUNT_TOO_SMALL, AppError.Validation("installment_amount_too_small").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_SCOPE_REQUIRED, AppError.Validation("installment_delete_scope_required").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_SCOPE_NOT_APPLICABLE, AppError.Validation("installment_scope_not_applicable").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_DATA_INVALID, AppError.Validation("installment_info_required").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_DATA_INVALID, AppError.Validation("installment_group_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.INSTALLMENT_DATA_INVALID, AppError.Validation("installment_total_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_NAME_REQUIRED, AppError.Validation("category_name_required").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_NAME_TOO_LONG, AppError.Validation("category_name_too_long").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_DUPLICATE_NAME, AppError.Validation("category_duplicate_name").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_COLOR_INVALID, AppError.Validation("category_color_invalid").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE, AppError.Validation("category_default_cannot_be_deleted").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.DEFAULT_CATEGORY_IMMUTABLE, AppError.Validation("category_default_cannot_be_modified").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CATEGORY_NOT_FOUND, AppError.Validation("category_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_NOT_FOUND, AppError.Validation("workspace_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_NOT_FOUND, AppError.Validation("workspace_invitation_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_EXPIRED, AppError.Validation("workspace_invitation_expired").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_INVITATION_LIMIT_REACHED, AppError.Validation("workspace_invitation_limit_reached").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_CANNOT_LEAVE_AS_OWNER, AppError.Validation("cannot_leave_as_owner_requires_transfer").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_CANNOT_CHANGE_OWN_ROLE, AppError.Validation("cannot_change_own_role").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_OWNER_ROLE_CHANGE_REQUIRES_TRANSFER, AppError.Validation("owner_role_change_requires_transfer").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TARGET_MEMBER_NOT_FOUND, AppError.Validation("target_member_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_NOT_MEMBER, AppError.Validation("ownership_transfer_target_not_member").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER, AppError.Validation("ownership_transfer_target_already_owner").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_TARGET_ALREADY_OWNER, AppError.Validation("transfer_target_already_owner").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_LOCAL_MEMBER_VERSION_UNAVAILABLE, AppError.Validation("local_member_version_unavailable").toFinanceUiMessage())
    }

    @Test
    fun toFinanceUiMessage_mapsAuthenticationAndSystemErrorsCorrectly() {
        assertEquals(FinanceUiMessage.SESSION_EXPIRED, AppError.Authentication("auth_session_required").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.SESSION_EXPIRED, AppError.Authentication("auth_session_expired").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER, AppError.Authentication("actor_not_member").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_ACTOR_NOT_PERMITTED, AppError.Authentication("actor_not_permitted").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_ACTOR_NOT_OWNER, AppError.Authentication("ownership_transfer_actor_not_owner").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_ACTOR_NOT_OWNER, AppError.Authentication("transfer_actor_not_owner").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.PERMISSION_DENIED, AppError.Authentication("transaction_owner_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.PERMISSION_DENIED, AppError.Authentication("category_owner_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.NETWORK_ERROR, AppError.Network("net_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.STORAGE_ERROR, AppError.Storage("disk_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CONFLICT, AppError.Conflict("conflict_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.GENERIC_ERROR, AppError.Unknown("unknown_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CONFLICT_RESOLUTION_STALE, AppError.Conflict("sync.conflict_resolution_stale").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_CONFLICT_REMOTE_TOMBSTONE, AppError.Conflict("sync.workspace_create_conflict_remote_tombstone").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_CONFLICT_OWNER_MISMATCH, AppError.Conflict("sync.workspace_create_conflict_owner_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CONFLICT_NOT_FOUND, AppError.Conflict("sync.conflict_not_found").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_TRANSFER_VERSION_CONFLICT, AppError.Conflict("ownership_transfer_version_conflict").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.WORKSPACE_LOCAL_CHANGES_PREVENT_TRANSFER, AppError.Conflict("local_uncommitted_changes_prevent_ownership_transfer").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CONFLICT_RESOLUTION_FAILED, AppError.Storage("sync.conflict_resolution_failed").toFinanceUiMessage())
    }

    @Test
    fun toFinanceUiMessage_mapsUnknownCodeToGenericError() {
        assertEquals(FinanceUiMessage.GENERIC_ERROR, AppError.Validation("unrecognized_code_123").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.GENERIC_ERROR, AppError.Authentication("unrecognized_auth_code").toFinanceUiMessage())
    }

    @Test
    fun isError_returnsFalseForSuccessMessagesAndTrueForErrorMessages() {
        assertFalse(FinanceUiMessage.TRANSACTION_SAVED.isError)
        assertFalse(FinanceUiMessage.TRANSACTION_DELETED.isError)
        assertFalse(FinanceUiMessage.CATEGORY_SAVED.isError)
        assertFalse(FinanceUiMessage.CATEGORY_DELETED.isError)

        assertTrue(FinanceUiMessage.INVALID_AMOUNT.isError)
        assertTrue(FinanceUiMessage.DATE_IN_FUTURE.isError)
        assertTrue(FinanceUiMessage.GENERIC_ERROR.isError)
        assertTrue(FinanceUiMessage.SESSION_EXPIRED.isError)
    }

    @Test
    fun toDisplayText_returnsNonEmptyTurkishStringsWithoutTechnicalCodes() {
        for (message in FinanceUiMessage.entries) {
            val text = message.toDisplayText()
            assertTrue(text.isNotBlank())
            assertFalse(text.contains("AppError"))
            assertFalse(text.contains("Exception"))
            assertFalse(text.contains("_"))
        }
    }

    @Test
    fun fieldErrors_returnValidTurkishDisplayTexts() {
        for (error in TransactionFormFieldError.entries) {
            val text = error.toDisplayText()
            assertTrue(text.isNotBlank())
            assertFalse(text.contains("_"))
        }
        for (error in CategoryFormFieldError.entries) {
            val text = error.toDisplayText()
            assertTrue(text.isNotBlank())
            assertFalse(text.contains("_"))
        }
    }
}
