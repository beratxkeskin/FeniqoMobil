package com.feniqo.mobile.domain.model

/** UI metninden bağımsız, katmanlar arasında taşınabilen uygulama hata sözleşmesi. */
sealed interface AppError {
    val code: String

    data class Validation(override val code: String) : AppError
    data class Network(override val code: String) : AppError
    data class Authentication(override val code: String) : AppError
    data class Storage(override val code: String) : AppError
    data class Conflict(override val code: String) : AppError
    data class Unknown(override val code: String) : AppError
}
