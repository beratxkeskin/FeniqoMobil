package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import kotlinx.coroutines.flow.Flow

/**
 * Mevcut kullanıcı oturumunu gözlemler.
 * Oturum geçerli olduğunda AuthSession, oturum kapalı/yok olduğunda null yayınlar.
 */
class ObserveAuthSessionUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthSession?> = authRepository.observeSession()
}
