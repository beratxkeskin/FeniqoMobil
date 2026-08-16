package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.toAuthAppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
}

private suspend inline fun <T> authResult(block: () -> T): RepositoryResult<T> = try {
    RepositoryResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    RepositoryResult.Failure(error.toAuthAppError())
}
