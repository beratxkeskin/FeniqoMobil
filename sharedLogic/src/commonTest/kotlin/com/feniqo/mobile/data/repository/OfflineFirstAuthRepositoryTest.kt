package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.RemoteAuthSession
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OfflineFirstAuthRepositoryTest {

    @Test
    fun maps_remote_session_without_exposing_supabase_types() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "user@example.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val session = repository.observeSession().first()

        assertEquals("user-1", session?.userId?.value)
        assertEquals("user@example.com", session?.email)
        assertEquals(1_800_000_000, session?.expiresAt?.epochSeconds)
    }

    @Test
    fun returns_entity_id_after_signup_and_normalizes_email() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.signUp("  USER@example.com ", "password", "Feniqo User")

        val success = assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals("created-user", success.value.value)
        assertEquals("USER@example.com", remote.lastEmail)
    }

    @Test
    fun converts_unexpected_remote_failure_to_domain_error() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            nextError = IllegalStateException("SDK ayrıntısı UI katmanına çıkmamalı")
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.signIn("user@example.com", "password")

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Unknown("auth_unknown"), failure.error)
    }

    @Test
    fun triggers_schedule_initial_sync_on_successful_sign_in_and_sign_up() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val scheduler = FakeBackgroundSyncScheduler()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao(), scheduler)

        repository.signIn("user@example.com", "password")
        assertEquals(1, scheduler.initialSyncCalls)

        repository.signUp("user2@example.com", "password", "User Two")
        assertEquals(2, scheduler.initialSyncCalls)
    }

    @Test
    fun triggers_cancel_sync_work_on_successful_sign_out() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val scheduler = FakeBackgroundSyncScheduler()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao(), scheduler)

        repository.signOut()
        assertEquals(1, scheduler.cancelSyncCalls)
    }
}

private class FakeBackgroundSyncScheduler : com.feniqo.mobile.domain.sync.BackgroundSyncScheduler {
    var initialSyncCalls = 0
    var outboxSyncCalls = 0
    var cancelSyncCalls = 0

    override fun scheduleInitialSync() {
        initialSyncCalls++
    }

    override fun scheduleOutboxSync() {
        outboxSyncCalls++
    }

    override fun cancelSyncWork() {
        cancelSyncCalls++
    }
}

private class FakeAuthRemoteDataSource : AuthRemoteDataSource {
    val session = MutableStateFlow<RemoteAuthSession?>(null)
    var nextError: Throwable? = null
    var lastEmail: String? = null

    override fun observeSession(): Flow<RemoteAuthSession?> = session

    override suspend fun signIn(email: String, password: String) {
        throwNextErrorIfPresent()
        lastEmail = email
    }

    override suspend fun signUp(email: String, password: String, fullName: String?): String {
        throwNextErrorIfPresent()
        lastEmail = email
        return "created-user"
    }

    override suspend fun refreshSession() = throwNextErrorIfPresent()

    override suspend fun signOut() = throwNextErrorIfPresent()

    private fun throwNextErrorIfPresent() {
        nextError?.let { throw it }
    }
}

private class FakeProfileDao : ProfileDao {
    private val profile = MutableStateFlow<UserProfileEntity?>(null)

    override fun observeById(id: String): Flow<UserProfileEntity?> = profile

    override suspend fun upsert(entity: UserProfileEntity) {
        profile.value = entity
    }
}
