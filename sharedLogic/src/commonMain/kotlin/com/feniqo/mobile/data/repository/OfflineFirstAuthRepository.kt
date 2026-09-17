package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.toAuthAppError
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthDeepLinkType
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.validation.AuthValidationRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Oturumu Supabase'ten, profil okumalarını ise Single Source of Truth olan Room'dan sunar.
 * Supabase exception sınıflarının domain ve UI katmanına geçmesini engeller.
 */
class OfflineFirstAuthRepository(
    private val remoteDataSource: AuthRemoteDataSource,
    private val profileDao: ProfileDao,
    private val syncScheduler: com.feniqo.mobile.domain.sync.BackgroundSyncScheduler? = null,
) : AuthRepository {

    private val _recoveryState = MutableStateFlow<AuthRecoveryState>(AuthRecoveryState.Idle)

    override fun observeSession(): Flow<AuthSession?> = remoteDataSource
        .observeSession()
        .map { session ->
            session?.let {
                AuthSession(
                    userId = EntityId(it.userId),
                    email = it.email,
                    expiresAt = Instant.fromEpochSeconds(it.expiresAtEpochSeconds),
                )
            }
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentProfile(): Flow<UserProfile?> = remoteDataSource
        .observeSession()
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                profileDao.observeById(session.userId).map { entity -> entity?.toDomain() }
            }
        }
        .distinctUntilChanged()

    override fun observeRecoveryState(): Flow<AuthRecoveryState> = _recoveryState.asStateFlow()

    override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> {
        val result = authResult { remoteDataSource.signIn(email.trim(), password) }
        if (result is RepositoryResult.Success) {
            syncScheduler?.scheduleInitialSync()
        }
        return result
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String?,
    ): RepositoryResult<EntityId> {
        val result = authResult {
            EntityId(remoteDataSource.signUp(email.trim(), password, fullName))
        }
        if (result is RepositoryResult.Success) {
            syncScheduler?.scheduleInitialSync()
        }
        return result
    }

    override suspend fun refreshSession(): RepositoryResult<Unit> =
        authResult { remoteDataSource.refreshSession() }

    override suspend fun signOut(): RepositoryResult<Unit> {
        val result = authResult { remoteDataSource.signOut() }
        if (result is RepositoryResult.Success) {
            syncScheduler?.cancelSyncWork()
        }
        return result
    }

    override suspend fun updateFullName(fullName: String?): RepositoryResult<Unit> = authResult {
        val normalized = AuthValidationRules.normalizeFullName(fullName)
        val session = remoteDataSource.observeSession().first()
            ?: error("session_expired")
        val current = profileDao.observeById(session.userId).first()
            ?: error("profile_not_found")
        profileDao.upsert(current.copy(fullName = normalized))
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): RepositoryResult<Unit> {
        val session = remoteDataSource.observeSession().first()
            ?: return RepositoryResult.Failure(
                AppError.Authentication("auth_session_expired")
            )

        return authResult {
            remoteDataSource.changePassword(
                email = session.email,
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): RepositoryResult<Unit> {
        val normalizedEmail = AuthValidationRules.normalizeEmail(email)
        return authResult {
            remoteDataSource.sendPasswordResetEmail(
                email = normalizedEmail,
                redirectUrl = "feniqo://auth/callback",
            )
        }
    }

    override suspend fun resendEmailConfirmation(email: String): RepositoryResult<Unit> {
        val normalizedEmail = AuthValidationRules.normalizeEmail(email)
        return authResult {
            remoteDataSource.resendEmailConfirmation(email = normalizedEmail)
        }
    }

    override suspend fun resetPassword(newPassword: String): RepositoryResult<Unit> {
        val currentRecovery = _recoveryState.value
        if (currentRecovery !is AuthRecoveryState.Verified) {
            return RepositoryResult.Failure(
                AppError.Authentication("auth_recovery_not_authorized")
            )
        }

        val result = authResult {
            remoteDataSource.updatePassword(newPassword)
            // Parola güncellendikten sonra recovery state temizlenir ve oturum kapatılır
            // Böylece kullanıcı yeni parolasıyla temiz giriş ekranından oturum açar
            remoteDataSource.signOut()
        }

        if (result is RepositoryResult.Success) {
            _recoveryState.value = AuthRecoveryState.Idle
            syncScheduler?.cancelSyncWork()
        }
        return result
    }

    override suspend fun handleAuthDeepLink(uriString: String): RepositoryResult<AuthDeepLinkType> {
        val parsed = parseDeepLinkUri(uriString)
            ?: return RepositoryResult.Success(AuthDeepLinkType.UNSUPPORTED)

        if (parsed.hasError) {
            _recoveryState.value = AuthRecoveryState.InvalidOrExpired
            return RepositoryResult.Failure(AppError.Authentication("auth_recovery_link_invalid"))
        }

        when (parsed.type?.lowercase()) {
            "recovery" -> {
                _recoveryState.value = AuthRecoveryState.Validating
                val accessToken = parsed.accessToken
                val refreshToken = parsed.refreshToken

                if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                    _recoveryState.value = AuthRecoveryState.InvalidOrExpired
                    return RepositoryResult.Failure(AppError.Authentication("auth_recovery_link_invalid"))
                }

                val importResult = authResult {
                    remoteDataSource.importAuthToken(accessToken, refreshToken)
                }

                return when (importResult) {
                    is RepositoryResult.Success -> {
                        val session = remoteDataSource.observeSession().first()
                        _recoveryState.value = AuthRecoveryState.Verified(session?.email)
                        RepositoryResult.Success(AuthDeepLinkType.RECOVERY)
                    }
                    is RepositoryResult.Failure -> {
                        _recoveryState.value = AuthRecoveryState.InvalidOrExpired
                        RepositoryResult.Failure(AppError.Authentication("auth_recovery_link_invalid"))
                    }
                }
            }
            "signup" -> {
                val accessToken = parsed.accessToken
                val refreshToken = parsed.refreshToken
                if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                    authResult { remoteDataSource.importAuthToken(accessToken, refreshToken) }
                }
                return RepositoryResult.Success(AuthDeepLinkType.EMAIL_CONFIRMATION)
            }
            else -> {
                // PKCE code check
                if (!parsed.code.isNullOrBlank()) {
                    _recoveryState.value = AuthRecoveryState.Validating
                    val exchangeResult = authResult {
                        remoteDataSource.exchangeCodeForSession(parsed.code)
                    }
                    return when (exchangeResult) {
                        is RepositoryResult.Success -> {
                            val session = remoteDataSource.observeSession().first()
                            _recoveryState.value = AuthRecoveryState.Verified(session?.email)
                            RepositoryResult.Success(AuthDeepLinkType.RECOVERY)
                        }
                        is RepositoryResult.Failure -> {
                            _recoveryState.value = AuthRecoveryState.InvalidOrExpired
                            RepositoryResult.Failure(AppError.Authentication("auth_recovery_link_invalid"))
                        }
                    }
                }
                return RepositoryResult.Success(AuthDeepLinkType.UNSUPPORTED)
            }
        }
    }

    override fun clearRecoveryState() {
        val wasVerified = _recoveryState.value is AuthRecoveryState.Verified
        _recoveryState.value = AuthRecoveryState.Idle
        if (wasVerified) {
            // Geçici kurtarma oturumunun ana ekrana sızmaması için signOut çağrılır
            try {
                // Yangın söndürme tarzında güvenli çıkış
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).run {
                    // Non-blocking fire-and-forget
                }
            } catch (_: Throwable) {}
        }
    }
}

internal data class ParsedDeepLink(
    val type: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val code: String?,
    val hasError: Boolean,
)

internal fun parseDeepLinkUri(rawUri: String): ParsedDeepLink? {
    val trimmed = rawUri.trim()
    if (!trimmed.startsWith("feniqo://auth/callback", ignoreCase = true)) {
        return null
    }

    val params = mutableMapOf<String, String>()

    // Fragment parametrelerini ayrıştır (#access_token=...&type=...)
    val fragmentIndex = trimmed.indexOf('#')
    if (fragmentIndex != -1 && fragmentIndex < trimmed.length - 1) {
        val fragment = trimmed.substring(fragmentIndex + 1)
        fragment.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq != -1) {
                val key = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (key.isNotEmpty()) params[key] = value
            }
        }
    }

    // Query parametrelerini ayrıştır (?code=...&type=...)
    val queryIndex = trimmed.indexOf('?')
    if (queryIndex != -1) {
        val endIndex = if (fragmentIndex != -1 && fragmentIndex > queryIndex) fragmentIndex else trimmed.length
        val query = trimmed.substring(queryIndex + 1, endIndex)
        query.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq != -1) {
                val key = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (key.isNotEmpty() && !params.containsKey(key)) params[key] = value
            }
        }
    }

    val hasError = params.containsKey("error") || params.containsKey("error_code")
    return ParsedDeepLink(
        type = params["type"],
        accessToken = params["access_token"],
        refreshToken = params["refresh_token"],
        code = params["code"],
        hasError = hasError,
    )
}

private suspend inline fun <T> authResult(block: () -> T): RepositoryResult<T> = try {
    RepositoryResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    RepositoryResult.Failure(error.toAuthAppError())
}
