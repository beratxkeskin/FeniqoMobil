package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.RemoteAuthSession
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OfflineFirstAuthRepositoryTest {

    @Test
    fun update_name_queues_profile_update_instead_of_direct_dao_write() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession("user-1", "user@example.com", 1_800_000_000)
        }
        val profiles = FakeProfileDao()
        profiles.upsert(profileRow())
        var queued: UserProfileEntity? = null
        var operation: OutboxOperationType? = null
        var payload = ""
        val repository = OfflineFirstAuthRepository(
            remote, profiles,
            enqueueProfileUpdate = { entity, type, json ->
                queued = entity
                operation = type
                payload = json
                profiles.upsert(entity)
            },
            nowEpochMillisProvider = { 2_000L },
        )

        assertIs<RepositoryResult.Success<Unit>>(repository.updateFullName("  Deniz Yılmaz  "))
        assertEquals("Deniz Yılmaz", queued?.fullName)
        assertEquals("PENDING_UPDATE", queued?.sync?.syncStatus)
        assertEquals(3L, queued?.sync?.baseVersion)
        assertEquals(OutboxOperationType.UPDATE, operation)
        assertEquals(true, payload.contains("Deniz Yılmaz"))
        assertEquals("Deniz Yılmaz", profiles.observeById("user-1").first()?.fullName)
    }

    @Test
    fun update_name_bootstraps_missing_local_profile_from_remote_before_queuing() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession("user-1", "user@example.com", 1_800_000_000)
        }
        val profiles = FakeProfileDao()
        var queued: UserProfileEntity? = null
        val repository = OfflineFirstAuthRepository(
            remote, profiles,
            fetchRemoteProfile = { id ->
                assertEquals("user-1", id)
                ProfileDto(
                    id = id, email = "user@example.com", fullName = null,
                    createdAt = "2026-09-20T10:00:00Z", updatedAt = "2026-09-20T10:00:00Z", version = 4,
                )
            },
            enqueueProfileUpdate = { entity, type, _ ->
                assertEquals(OutboxOperationType.UPDATE, type)
                queued = entity
                profiles.upsert(entity)
            },
            nowEpochMillisProvider = { 2_000L },
        )

        assertIs<RepositoryResult.Success<Unit>>(repository.updateFullName("Deniz Yılmaz"))
        assertEquals("Deniz Yılmaz", queued?.fullName)
        assertEquals(4L, queued?.sync?.baseVersion)
    }

    @Test
    fun update_name_does_not_claim_success_when_profile_cannot_be_loaded() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession("user-1", "user@example.com", 1_800_000_000)
        }
        val profiles = FakeProfileDao()
        var enqueued = false
        val repository = OfflineFirstAuthRepository(
            remote, profiles,
            fetchRemoteProfile = { null },
            enqueueProfileUpdate = { _, _, _ -> enqueued = true },
        )

        assertIs<RepositoryResult.Failure>(repository.updateFullName("Deniz Yılmaz"))
        assertEquals(false, enqueued)
        assertEquals(null, profiles.observeById("user-1").first())
    }

    private fun profileRow() = UserProfileEntity(
        id = "user-1", email = "user@example.com", fullName = null,
        currencyCode = "TRY", themeCode = "SYSTEM", languageCode = "TR",
        activeWorkspaceId = null, createdAtEpochMillis = 1_000L,
        sync = newSyncMetadata(1_000L, SyncStatus.SYNCED).copy(version = 3, baseVersion = 3),
    )

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

    @Test
    fun changePassword_without_active_session_returns_session_expired_without_remote_call() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.changePassword("oldPass", "newPass")

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Authentication("auth_session_expired"), failure.error)
        assertEquals(0, remote.changePasswordCallCount)
    }

    @Test
    fun changePassword_with_active_session_passes_email_and_passwords_to_remote() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "user@example.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.changePassword("currentSecret", "newSecret")

        assertIs<RepositoryResult.Success<Unit>>(result)
        assertEquals(1, remote.changePasswordCallCount)
        assertEquals("user@example.com", remote.lastChangePasswordEmail)
        assertEquals("currentSecret", remote.lastChangePasswordCurrentPassword)
        assertEquals("newSecret", remote.lastChangePasswordNewPassword)
    }

    @Test
    fun changePassword_remote_failure_maps_to_safe_domain_error() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "user@example.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
            nextError = IllegalStateException("SDK internal message")
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.changePassword("oldPass", "newPass")

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Unknown("auth_unknown"), failure.error)
    }

    @Test
    fun changePassword_rethrows_cancellation_exception() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "user@example.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
            nextError = CancellationException("Job cancelled")
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        assertFailsWith<CancellationException> {
            repository.changePassword("oldPass", "newPass")
        }
    }

    @Test
    fun handleAuthDeepLink_validRecoveryToken_importsTokenAndSetsVerifiedRecoveryState() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "recovered@feniqo.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val deepLink = "feniqo://auth/callback#access_token=acc123&refresh_token=ref456&type=recovery"
        val result = repository.handleAuthDeepLink(deepLink)

        val success = assertIs<RepositoryResult.Success<com.feniqo.mobile.domain.repository.AuthDeepLinkType>>(result)
        assertEquals(com.feniqo.mobile.domain.repository.AuthDeepLinkType.RECOVERY, success.value)
        assertEquals(1, remote.importTokenCallCount)
        assertEquals("acc123", remote.lastImportAccessToken)
        assertEquals("ref456", remote.lastImportRefreshToken)

        val recoveryState = repository.observeRecoveryState().first()
        val verified = assertIs<com.feniqo.mobile.domain.repository.AuthRecoveryState.Verified>(recoveryState)
        assertEquals("recovered@feniqo.com", verified.email)
    }

    @Test
    fun handleAuthDeepLink_validSignupConfirmation_importsTokenAndReturnsConfirmationType() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val deepLink = "feniqo://auth/callback#access_token=acc123&refresh_token=ref456&type=signup"
        val result = repository.handleAuthDeepLink(deepLink)

        val success = assertIs<RepositoryResult.Success<com.feniqo.mobile.domain.repository.AuthDeepLinkType>>(result)
        assertEquals(com.feniqo.mobile.domain.repository.AuthDeepLinkType.EMAIL_CONFIRMATION, success.value)
        assertEquals(1, remote.importTokenCallCount)

        // Email confirmation recovery durumunu verified yapmamalıdır
        val recoveryState = repository.observeRecoveryState().first()
        assertEquals(com.feniqo.mobile.domain.repository.AuthRecoveryState.Idle, recoveryState)
    }

    @Test
    fun handleAuthDeepLink_pkceCode_exchangesCodeAndSetsVerifiedRecoveryState() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "pkce@feniqo.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val deepLink = "feniqo://auth/callback?code=pkce-auth-code-123"
        val result = repository.handleAuthDeepLink(deepLink)

        val success = assertIs<RepositoryResult.Success<com.feniqo.mobile.domain.repository.AuthDeepLinkType>>(result)
        assertEquals(com.feniqo.mobile.domain.repository.AuthDeepLinkType.RECOVERY, success.value)
        assertEquals(1, remote.exchangeCodeCallCount)
        assertEquals("pkce-auth-code-123", remote.lastExchangeCode)

        val recoveryState = repository.observeRecoveryState().first()
        val verified = assertIs<com.feniqo.mobile.domain.repository.AuthRecoveryState.Verified>(recoveryState)
        assertEquals("pkce@feniqo.com", verified.email)
    }

    @Test
    fun handleAuthDeepLink_expiredOrBrokenLink_setsInvalidOrExpiredRecoveryState() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        // Supabase error fragment'ı
        val deepLink = "feniqo://auth/callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"
        val result = repository.handleAuthDeepLink(deepLink)

        val failure = assertIs<RepositoryResult.Failure>(result)
        val recoveryState = repository.observeRecoveryState().first()
        assertEquals(com.feniqo.mobile.domain.repository.AuthRecoveryState.InvalidOrExpired, recoveryState)
    }

    @Test
    fun handleAuthDeepLink_unsupportedUrl_returnsUnsupportedWithoutAlteringRecoveryState() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val deepLink = "feniqo://other/path"
        val result = repository.handleAuthDeepLink(deepLink)

        val success = assertIs<RepositoryResult.Success<com.feniqo.mobile.domain.repository.AuthDeepLinkType>>(result)
        assertEquals(com.feniqo.mobile.domain.repository.AuthDeepLinkType.UNSUPPORTED, success.value)
        assertEquals(com.feniqo.mobile.domain.repository.AuthRecoveryState.Idle, repository.observeRecoveryState().first())
    }

    @Test
    fun resetPassword_whenRecoveryVerified_updatesPasswordClearsRecoveryAndSignsOut() = runTest {
        val remote = FakeAuthRemoteDataSource().apply {
            session.value = RemoteAuthSession(
                userId = "user-1",
                email = "user@feniqo.com",
                expiresAtEpochSeconds = 1_800_000_000,
            )
        }
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        // Önce linki verify et
        repository.handleAuthDeepLink("feniqo://auth/callback#access_token=token&refresh_token=ref&type=recovery")
        assertEquals(1, remote.importTokenCallCount)

        // Şimdi şifreyi sıfırla
        val resetResult = repository.resetPassword("NewSuperSecret123")
        assertIs<RepositoryResult.Success<Unit>>(resetResult)

        assertEquals(1, remote.updatePasswordCallCount)
        assertEquals("NewSuperSecret123", remote.lastUpdatePasswordValue)

        // Şifre sıfırlama sonrası recovery Idle olmalı
        assertEquals(com.feniqo.mobile.domain.repository.AuthRecoveryState.Idle, repository.observeRecoveryState().first())
    }

    @Test
    fun resetPassword_whenNotVerified_failsWithoutCallingRemote() = runTest {
        val remote = FakeAuthRemoteDataSource()
        val repository = OfflineFirstAuthRepository(remote, FakeProfileDao())

        val result = repository.resetPassword("NewSuperSecret123")
        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals(AppError.Authentication("auth_recovery_not_authorized"), failure.error)
        assertEquals(0, remote.updatePasswordCallCount)
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

    var changePasswordCallCount = 0
    var lastChangePasswordEmail: String? = null
    var lastChangePasswordCurrentPassword: String? = null
    var lastChangePasswordNewPassword: String? = null

    var sendResetEmailCallCount = 0
    var lastSendResetEmail: String? = null
    var lastSendResetRedirectUrl: String? = null

    var resendEmailCallCount = 0
    var lastResendEmail: String? = null

    var updatePasswordCallCount = 0
    var lastUpdatePasswordValue: String? = null

    var importTokenCallCount = 0
    var lastImportAccessToken: String? = null
    var lastImportRefreshToken: String? = null

    var exchangeCodeCallCount = 0
    var lastExchangeCode: String? = null

    override suspend fun changePassword(
        email: String,
        currentPassword: String,
        newPassword: String,
    ) {
        throwNextErrorIfPresent()
        changePasswordCallCount++
        lastChangePasswordEmail = email
        lastChangePasswordCurrentPassword = currentPassword
        lastChangePasswordNewPassword = newPassword
    }

    override suspend fun sendPasswordResetEmail(email: String, redirectUrl: String) {
        throwNextErrorIfPresent()
        sendResetEmailCallCount++
        lastSendResetEmail = email
        lastSendResetRedirectUrl = redirectUrl
    }

    override suspend fun resendEmailConfirmation(email: String) {
        throwNextErrorIfPresent()
        resendEmailCallCount++
        lastResendEmail = email
    }

    override suspend fun updatePassword(newPassword: String) {
        throwNextErrorIfPresent()
        updatePasswordCallCount++
        lastUpdatePasswordValue = newPassword
    }

    override suspend fun importAuthToken(accessToken: String, refreshToken: String) {
        throwNextErrorIfPresent()
        importTokenCallCount++
        lastImportAccessToken = accessToken
        lastImportRefreshToken = refreshToken
    }

    override suspend fun exchangeCodeForSession(code: String) {
        throwNextErrorIfPresent()
        exchangeCodeCallCount++
        lastExchangeCode = code
    }

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

    override suspend fun insertIfMissing(entity: UserProfileEntity): Long {
        if (profile.value != null) return -1L
        profile.value = entity
        return 1L
    }

    override suspend fun setActiveWorkspaceGuarded(profileId: String, workspaceId: String): Int {
        val current = profile.value ?: return 0
        if (current.id != profileId) return 0
        profile.value = current.copy(activeWorkspaceId = workspaceId)
        return 1
    }

    override suspend fun clearActiveWorkspace(profileId: String): Int {
        val current = profile.value ?: return 0
        if (current.id != profileId) return 0
        profile.value = current.copy(activeWorkspaceId = null)
        return 1
    }
}
