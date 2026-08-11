package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class AuthSession(
    val userId: EntityId,
    val email: String,
    val expiresAt: Instant,
)

interface AuthRepository {
    fun observeSession(): Flow<AuthSession?>

    fun observeCurrentProfile(): Flow<UserProfile?>

    suspend fun signIn(email: String, password: String): RepositoryResult<Unit>

    suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId>

    suspend fun refreshSession(): RepositoryResult<Unit>

    suspend fun signOut(): RepositoryResult<Unit>
}
