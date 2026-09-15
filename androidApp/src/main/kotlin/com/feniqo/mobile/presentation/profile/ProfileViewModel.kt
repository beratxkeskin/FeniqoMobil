package com.feniqo.mobile.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "Feniqo kullanıcısı",
    val email: String = "",
    val workspaceName: String = "Kişisel alan",
    val currencyCode: String = "TRY",
    val isLoading: Boolean = true,
    val signOutError: String? = null,
)

/** Room-backed profile and active workspace summary for the profile center. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
) : ViewModel() {
    private val signOutError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.observeCurrentProfile(),
        observeActiveWorkspaceUseCase(),
        signOutError,
    ) { profile, workspace, error ->
        ProfileUiState(
            displayName = profile?.fullName?.trim()?.takeIf { it.isNotBlank() } ?: "Feniqo kullanıcısı",
            email = profile?.email.orEmpty(),
            workspaceName = workspace?.name ?: "Kişisel alan",
            currencyCode = workspace?.currency?.code ?: profile?.currency?.code ?: "TRY",
            isLoading = profile == null,
            signOutError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun signOut() {
        viewModelScope.launch {
            if (authRepository.signOut() is RepositoryResult.Failure) {
                signOutError.value = "Çıkış yapılamadı. Lütfen tekrar deneyin."
            }
        }
    }
}
