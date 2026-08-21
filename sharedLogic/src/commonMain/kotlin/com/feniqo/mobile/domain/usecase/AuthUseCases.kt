package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
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

/**
 * E-posta ve parola ile kullanıcı girişi gerçekleştirir.
 */
class SignInUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): RepositoryResult<Unit> = authRepository.signIn(
        email = email.trim(),
        password = password,
    )
}

/**
 * Yeni kullanıcı hesabı kaydı oluşturur.
 */
class SignUpUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        fullName: String? = null,
    ): RepositoryResult<EntityId> = authRepository.signUp(
        email = email.trim(),
        password = password,
        fullName = fullName?.trim()?.takeIf { it.isNotEmpty() },
    )
}
