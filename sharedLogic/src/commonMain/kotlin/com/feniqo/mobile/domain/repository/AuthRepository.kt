package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

sealed interface AuthRecoveryState {
    data object Idle : AuthRecoveryState
    data object Validating : AuthRecoveryState
    data class Verified(val email: String?) : AuthRecoveryState
    data object InvalidOrExpired : AuthRecoveryState
}

enum class AuthDeepLinkType {
    RECOVERY,
    EMAIL_CONFIRMATION,
    UNSUPPORTED,
}

data class AuthSession(
    val userId: EntityId,
    val email: String,
    val expiresAt: Instant,
    val emailVerificationStatus: EmailVerificationStatus = EmailVerificationStatus.UNKNOWN,
)

interface AuthRepository {
    fun observeSession(): Flow<AuthSession?>

    fun observeCurrentProfile(): Flow<UserProfile?>

    fun observeRecoveryState(): Flow<AuthRecoveryState> = kotlinx.coroutines.flow.flowOf(AuthRecoveryState.Idle)

    suspend fun signIn(email: String, password: String): RepositoryResult<Unit>

    suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId>

    suspend fun refreshSession(): RepositoryResult<Unit>

    suspend fun signOut(): RepositoryResult<Unit>

    suspend fun updateFullName(fullName: String?): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): RepositoryResult<Unit> = RepositoryResult.Failure(
        AppError.Authentication("auth_password_change_unsupported")
    )

    suspend fun sendPasswordResetEmail(email: String): RepositoryResult<Unit> = RepositoryResult.Failure(
        AppError.Authentication("auth_recovery_unsupported")
    )

    suspend fun resendEmailConfirmation(email: String): RepositoryResult<Unit> = RepositoryResult.Failure(
        AppError.Authentication("auth_confirmation_unsupported")
    )

    suspend fun resetPassword(newPassword: String): RepositoryResult<Unit> = RepositoryResult.Failure(
        AppError.Authentication("auth_recovery_unsupported")
    )

    suspend fun handleAuthDeepLink(uriString: String): RepositoryResult<AuthDeepLinkType> =
        RepositoryResult.Success(AuthDeepLinkType.UNSUPPORTED)

    fun clearRecoveryState() {}
}
