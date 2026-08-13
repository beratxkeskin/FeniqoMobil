package com.feniqo.mobile.data.remote.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Kimlik doğrulama işlemlerini Supabase SDK üzerinden gerçekleştiren uzak veri kaynağı.
 * Supabase'e özel sınıflar bu data katmanının dışına çıkarılmaz.
 */
class SupabaseAuthRemoteDataSource(
    private val client: SupabaseClient,
) : AuthRemoteDataSource {

    override fun observeSession(): Flow<RemoteAuthSession?> =
        client.auth.sessionStatus
            .map { status ->
                val session =
                    (status as? SessionStatus.Authenticated)?.session
                        ?: return@map null

                val user = session.user ?: return@map null
                val email = user.email ?: return@map null

                RemoteAuthSession(
                    userId = user.id,
                    email = email,
                    expiresAtEpochSeconds = session.expiresAt.epochSeconds,
                )
            }
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
}
