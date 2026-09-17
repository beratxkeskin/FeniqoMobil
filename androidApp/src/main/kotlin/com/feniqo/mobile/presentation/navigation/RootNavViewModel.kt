package com.feniqo.mobile.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase
import com.feniqo.mobile.navigation.AppAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootNavViewModel @Inject constructor(
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    observeRecoveryStateUseCase: ObserveRecoveryStateUseCase,
) : ViewModel() {

    val authState: StateFlow<AppAuthState> = combine(
        observeAuthSessionUseCase(),
        observeRecoveryStateUseCase(),
    ) { session, recoveryState ->
        when {
            recoveryState !is AuthRecoveryState.Idle -> AppAuthState.PasswordRecovery(recoveryState)
            session != null -> AppAuthState.Authenticated
            else -> AppAuthState.Unauthenticated
        }
    }
        .catch { e ->
            if (e is CancellationException) throw e
            // Hata durumunda güvenlik gereği unauthenticated durumuna geçilir
            emit(AppAuthState.Unauthenticated)
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppAuthState.Checking,
        )
}
