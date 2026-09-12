package com.feniqo.mobile.presentation.transaction

/**
 * İşlem kaydedildikten sonra gösterilen başarı ekranı UI durum modelidir.
 */
data class TransactionSuccessUiState(
    val isLoading: Boolean = true,
    val transaction: TransactionDisplayModel? = null,
    val errorMessage: String? = null,
)
