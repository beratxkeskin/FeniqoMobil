package com.feniqo.mobile.data.remote.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Kimlik doğrulama işlemlerini Supabase SDK üzerinden gerçekleştiren uzak veri kaynağı.
 * Supabase'e özel sınıflar bu data katmanının dışına çıkarılmaz.
 */
class SupabaseAuthRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionManager: SessionManager,
) : AuthRemoteDataSource {

    override fun observeSession(): Flow<RemoteAuthSession?> =
        client.auth.sessionStatus
            .mapToRemoteAuthSession(sessionManager)
            .distinctUntilChanged()

    override suspend fun signIn(
        email: String,
        password: String,
    ) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String?,
    ): String {
        val normalizedFullName = fullName
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        val user = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password

            if (normalizedFullName != null) {
                data = buildJsonObject {
                    put(
                        key = "full_name",
                        element = JsonPrimitive(normalizedFullName),
                    )
                }
            }
        }

        return requireNotNull(user?.id) {
            "Supabase kayıt yanıtında kullanıcı kimliği bulunamadı."
        }
    }

    override suspend fun refreshSession() {
        client.auth.refreshCurrentSession()
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun changePassword(
        email: String,
        currentPassword: String,
        newPassword: String,
    ) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = currentPassword
        }
        client.auth.updateUser {
            this.password = newPassword
            this.currentPassword = currentPassword
        }
    }

    override suspend fun sendPasswordResetEmail(email: String, redirectUrl: String) {
        client.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = redirectUrl,
        )
    }

    override suspend fun resendEmailConfirmation(email: String) {
        client.auth.resendEmail(
            type = OtpType.Email.SIGNUP,
            email = email,
        )
    }

    override suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser {
            this.password = newPassword
        }
    }

    override suspend fun exchangeCodeForSession(code: String) {
        client.auth.exchangeCodeForSession(code = code)
    }

    override suspend fun importAuthToken(accessToken: String, refreshToken: String) {
        client.auth.importAuthToken(accessToken = accessToken, refreshToken = refreshToken)
    }
}

internal fun Flow<SessionStatus>.mapToRemoteAuthSession(
    sessionManager: SessionManager,
): Flow<RemoteAuthSession?> = transform { status ->
    when (status) {
        is SessionStatus.Initializing -> {
            // Initializing: Do not emit null/Unauthenticated; keep navigation in Checking
        }
        is SessionStatus.Authenticated -> {
            val user = status.session.user
            val email = user?.email
            if (user != null && email != null) {
                emit(
                    RemoteAuthSession(
                        userId = user.id,
                        email = email,
                        expiresAtEpochSeconds = status.session.expiresAt.epochSeconds,
                        isEmailVerified = user.emailConfirmedAt != null,
                    )
                )
            } else {
                emit(null)
            }
        }
        is SessionStatus.RefreshFailure -> {
            // SDK refresh failed (e.g. offline cold-start).
            // Read safely from the existing SessionManager for local Room access.
            val persistedSession = try {
                sessionManager.loadSession()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            val user = persistedSession?.user
            val email = user?.email
            if (user != null && email != null) {
                emit(
                    RemoteAuthSession(
                        userId = user.id,
                        email = email,
                        expiresAtEpochSeconds = persistedSession.expiresAt.epochSeconds,
                        isEmailVerified = user.emailConfirmedAt != null,
                    )
                )
            } else {
                emit(null)
            }
        }
        is SessionStatus.NotAuthenticated -> {
            // Definite sign-out / invalid session: emit null without persisted fallback
            emit(null)
        }
    }
}
