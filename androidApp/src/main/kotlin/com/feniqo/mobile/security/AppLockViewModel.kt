package com.feniqo.mobile.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SecurityRepository
import com.feniqo.mobile.domain.repository.SecuritySettings
import com.feniqo.mobile.domain.security.AppLockPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    val settings: SecuritySettings? = null,
    val availability: DeviceAuthenticationAvailability = DeviceAuthenticationAvailability.UNAVAILABLE,
    val isLocked: Boolean = false,
    val errorMessage: String? = null,
)

enum class AppLockAuthenticationAction {
    UNLOCK,
    ENABLE,
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val repository: SecurityRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> = mutableState.asStateFlow()
    private var backgroundedAtEpochMillis: Long? = null
    private var hasLoadedSettings = false

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                mutableState.update { current ->
                    current.copy(
                        settings = settings,
                        isLocked = if (!hasLoadedSettings && settings.biometricLockEnabled) {
                            true
                        } else {
                            current.isLocked && settings.biometricLockEnabled
                        },
                    )
                }
                hasLoadedSettings = true
            }
        }
    }

    fun setAvailability(availability: DeviceAuthenticationAvailability) {
        mutableState.update { it.copy(availability = availability) }
    }

    fun onBackgrounded(nowEpochMillis: Long) {
        backgroundedAtEpochMillis = nowEpochMillis
    }

    fun onForegrounded(nowEpochMillis: Long) {
        val settings = mutableState.value.settings ?: return
        if (AppLockPolicy.shouldLock(settings, backgroundedAtEpochMillis, nowEpochMillis)) {
            mutableState.update { it.copy(isLocked = true) }
        }
        backgroundedAtEpochMillis = null
    }

    fun onAuthenticationSucceeded(action: AppLockAuthenticationAction) {
        mutableState.update { it.copy(isLocked = false, errorMessage = null) }
        if (action == AppLockAuthenticationAction.ENABLE) {
            setEnabled(enabled = true)
        }
    }

    fun onAuthenticationError(message: String?) {
        mutableState.update {
            it.copy(errorMessage = message ?: unavailableMessage(it.availability))
        }
    }

    fun disableLock() = setEnabled(enabled = false)

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            if (repository.setAutoLockTimeout(timeout) is RepositoryResult.Failure) {
                mutableState.update { it.copy(errorMessage = "Otomatik kilit süresi kaydedilemedi.") }
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (repository.setBiometricLockEnabled(enabled) is RepositoryResult.Failure) {
                mutableState.update { it.copy(errorMessage = "Uygulama kilidi tercihi kaydedilemedi.") }
            }
        }
    }

    private fun unavailableMessage(availability: DeviceAuthenticationAvailability): String = when (availability) {
        DeviceAuthenticationAvailability.AVAILABLE -> "Doğrulama tamamlanamadı."
        DeviceAuthenticationAvailability.NONE_ENROLLED ->
            "Cihaz ayarlarından biyometri veya ekran kilidi tanımlayın."
        DeviceAuthenticationAvailability.UNAVAILABLE ->
            "Bu cihazda güvenli sistem doğrulaması kullanılamıyor."
    }
}
