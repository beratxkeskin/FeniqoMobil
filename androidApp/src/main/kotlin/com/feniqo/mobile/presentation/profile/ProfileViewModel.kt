package com.feniqo.mobile.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val workspaceName: String = "Kişisel alan",
    val currencyCode: String = "TRY",
    val languageRegionLabel: String = "Türkçe · TRY",
    val isLoading: Boolean = true,
    val isProfileEmpty: Boolean = false,
    val profileErrorMessage: String? = null,
    val signOutError: String? = null,
)

/** Room-backed profile, session fallback, active workspace and true retry support for the profile center. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)
    private val signOutError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = retryTrigger.flatMapLatest {
        combine(
            combine(
                authRepository.observeCurrentProfile(),
                authRepository.observeSession(),
            ) { profile, session -> profile to session },
            combine(
                observeActiveWorkspaceUseCase(),
                userSettingsRepository.observeSettings(),
                signOutError,
            ) { workspace, settings, sError -> Triple(workspace, settings, sError) },
        ) { (profile, session), (workspace, settings, sError) ->
            val sessionEmail = session?.email?.trim().orEmpty()
            val profileEmail = profile?.email?.trim().orEmpty()
            val effectiveEmail = profileEmail.ifBlank { sessionEmail }

            val fullName = profile?.fullName?.trim().orEmpty()
            val effectiveDisplayName = when {
                fullName.isNotBlank() -> fullName
                effectiveEmail.isNotBlank() -> effectiveEmail
                else -> ""
            }

            val languageName = when (settings.language) {
                AppLanguage.TR -> "Türkçe"
                AppLanguage.EN -> "English"
            }
            val languageRegion = "$languageName · ${settings.currency.code}"

            // Veri akışları ilk değerlerini ürettiğinde yükleme bitmiştir.
            // Boş profil: Room'da profil yok ve oturumda geçerli bir e-posta bulunmuyor.
            val isEmpty = profile == null && effectiveEmail.isBlank()

            ProfileUiState(
                displayName = effectiveDisplayName,
                email = effectiveEmail,
                workspaceName = workspace?.name ?: "Kişisel alan",
                currencyCode = settings.currency.code,
                languageRegionLabel = languageRegion,
                isLoading = false,
                isProfileEmpty = isEmpty,
                profileErrorMessage = null,
                signOutError = sError,
            )
        }.catch {
            emit(
                ProfileUiState(
                    isLoading = false,
                    isProfileEmpty = true,
                    profileErrorMessage = "Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.",
                    signOutError = signOutError.value,
                ),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(isLoading = true),
    )

    fun retryProfile() {
        retryTrigger.value += 1
    }

    fun signOut() {
        viewModelScope.launch {
            if (authRepository.signOut() is RepositoryResult.Failure) {
                signOutError.value = "Çıkış yapılamadı. Lütfen tekrar deneyin."
            }
        }
    }
}
