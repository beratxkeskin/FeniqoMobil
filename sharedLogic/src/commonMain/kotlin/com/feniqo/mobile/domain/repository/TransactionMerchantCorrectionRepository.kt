package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.TransactionMerchantCorrection

/**
 * Merchant düzeltmesi için dar ve fail-closed repository sözleşmesi.
 */
interface TransactionMerchantCorrectionRepository {
    suspend fun correctMerchant(correction: TransactionMerchantCorrection): RepositoryResult<Unit>
}
