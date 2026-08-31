package com.feniqo.mobile.presentation.transaction

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptAttachmentUiTest {

    @Test
    fun transactionFormUiState_defaultReceiptState_isNoReceiptAndNotInProgress() {
        val state = TransactionFormUiState()
        assertFalse(state.hasReceipt)
        assertFalse(state.isReceiptActionInProgress)
        assertFalse(state.isReceiptFeatureAvailable)
    }

    @Test
    fun transactionFormUiState_withReceiptAttached_reflectsState() {
        val state = TransactionFormUiState(
            hasReceipt = true,
            isReceiptActionInProgress = false,
            isReceiptFeatureAvailable = true,
        )
        assertTrue(state.hasReceipt)
        assertFalse(state.isReceiptActionInProgress)
        assertTrue(state.isReceiptFeatureAvailable)
    }

    @Test
    fun transactionFormUiState_withReceiptActionInProgress_reflectsState() {
        val state = TransactionFormUiState(
            hasReceipt = true,
            isReceiptActionInProgress = true,
            isReceiptFeatureAvailable = true,
        )
        assertTrue(state.hasReceipt)
        assertTrue(state.isReceiptActionInProgress)
        assertTrue(state.isReceiptFeatureAvailable)
    }
}
