package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.AuthDeepLinkType
import com.feniqo.mobile.domain.repository.AuthRecoveryState
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

/**
 * Parola sıfırlama bağlantısı gönderir.
 */
class SendPasswordResetEmailUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String): RepositoryResult<Unit> =
        authRepository.sendPasswordResetEmail(email.trim())
}

/**
 * E-posta doğrulama bağlantısını yeniden gönderir.
 */
class ResendEmailConfirmationUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String): RepositoryResult<Unit> =
        authRepository.resendEmailConfirmation(email.trim())
}

/**
 * Doğrulanmış recovery oturumunda yeni parolayı kaydeder.
 */
class ResetPasswordUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(newPassword: String): RepositoryResult<Unit> =
        authRepository.resetPassword(newPassword)
}

/**
 * Parola kurtarma akışı durumunu gözlemler.
 */
class ObserveRecoveryStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthRecoveryState> = authRepository.observeRecoveryState()
}

/**
 * Gelen auth deep link bağlantısını işler ve doğrular.
 */
class HandleAuthDeepLinkUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(uriString: String): RepositoryResult<AuthDeepLinkType> =
        authRepository.handleAuthDeepLink(uriString)
}

/**
 * Parola kurtarma durumunu ve geçici oturumunu temizler.
 */
class ClearRecoveryStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke() {
        authRepository.clearRecoveryState()
    }
}
