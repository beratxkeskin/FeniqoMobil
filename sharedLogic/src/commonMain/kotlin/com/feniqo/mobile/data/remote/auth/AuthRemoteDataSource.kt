package com.feniqo.mobile.data.remote.auth

import kotlinx.coroutines.flow.Flow

/**
 * Supabase oturumundan uygulamanın ihtiyaç duyduğu güvenli ve sade bilgiler.
 * Supabase SDK sınıfları data katmanının dışına çıkarılmaz.
 */
data class RemoteAuthSession(
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
)

/**
 * Uzak kimlik doğrulama servisinin sözleşmesi.
 * Gerçek Supabase işlemleri bir sonraki adımda bu arayüzün arkasında uygulanacaktır.
 */
interface AuthRemoteDataSource {
    fun observeSession(): Flow<RemoteAuthSession?>

    suspend fun signIn(email: String, password: String)

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String?,
    ): String

    suspend fun refreshSession()

    suspend fun signOut()
}
