package com.feniqo.mobile.presentation.profile

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency

sealed interface ProfileUiError {
    data object LoadFailed : ProfileUiError
}

sealed interface ProfileSignOutError {
    data object Failed : ProfileSignOutError
}

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val workspaceName: String? = null,
    val currency: Currency = Currency.TRY,
    val language: AppLanguage = AppLanguage.TR,
    val isLoading: Boolean = true,
    val isProfileEmpty: Boolean = false,
    val profileError: ProfileUiError? = null,
    val signOutError: ProfileSignOutError? = null,
)
