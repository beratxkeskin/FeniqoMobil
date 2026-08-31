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
    }

    @Test
    fun toFinanceUiMessage_mapsAuthenticationAndSystemErrorsCorrectly() {
        assertEquals(FinanceUiMessage.SESSION_EXPIRED, AppError.Authentication("auth_session_required").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.SESSION_EXPIRED, AppError.Authentication("auth_session_expired").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.PERMISSION_DENIED, AppError.Authentication("transaction_owner_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.PERMISSION_DENIED, AppError.Authentication("category_owner_mismatch").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.NETWORK_ERROR, AppError.Network("net_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.STORAGE_ERROR, AppError.Storage("disk_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.CONFLICT, AppError.Conflict("conflict_err").toFinanceUiMessage())
        assertEquals(FinanceUiMessage.GENERIC_ERROR, AppError.Unknown("unknown_err").toFinanceUiMessage())
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
