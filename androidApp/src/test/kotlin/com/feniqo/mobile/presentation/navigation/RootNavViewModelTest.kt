package com.feniqo.mobile.presentation.navigation

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.RemoteAuthSession
import com.feniqo.mobile.data.repository.OfflineFirstAuthRepository
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import com.feniqo.mobile.navigation.AppAuthState
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootNavViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        val sessionFlow = MutableSharedFlow<AuthSession?>(replay = 1)
        val recoveryStateFlow = MutableStateFlow<AuthRecoveryState>(AuthRecoveryState.Idle)

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override fun observeRecoveryState(): Flow<AuthRecoveryState> = recoveryStateFlow
        override suspend fun clearRecoveryState(): RepositoryResult<Unit> {
            recoveryStateFlow.value = AuthRecoveryState.Idle
            return RepositoryResult.Success(Unit)
        }
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private val fakeSession = AuthSession(
        userId = EntityId("user-1"),
        email = "user@feniqo.com",
        expiresAt = Instant.fromEpochMilliseconds(100_000L),
    )

    private fun createViewModel(repository: AuthRepository): RootNavViewModel {
        return RootNavViewModel(
            observeAuthSessionUseCase = ObserveAuthSessionUseCase(repository),
            observeRecoveryStateUseCase = com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase(repository),
        )
    }

    @Test
    fun initialAuthState_isChecking() {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        assertSame(AppAuthState.Checking, viewModel.authState.value)
    }

    @Test
    fun nullSession_emitsUnauthenticated() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect {}
        }

        repository.sessionFlow.emit(null)
        assertSame(AppAuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun validSession_emitsAuthenticated() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect {}
        }

        repository.sessionFlow.emit(fakeSession)
        assertSame(AppAuthState.Authenticated, viewModel.authState.value)
    }

    @Test
    fun sessionTransitions_updatesStateSequentially() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        val observedStates = mutableListOf<AppAuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect { observedStates.add(it) }
        }

        // Başlangıç: checking
        assertEquals(listOf(AppAuthState.Checking), observedStates)

        // Giriş yapıldı
        repository.sessionFlow.emit(fakeSession)
        assertEquals(listOf(AppAuthState.Checking, AppAuthState.Authenticated), observedStates)

        // Çıkış yapıldı
        repository.sessionFlow.emit(null)
        assertEquals(
            listOf(AppAuthState.Checking, AppAuthState.Authenticated, AppAuthState.Unauthenticated),
            observedStates,
        )

        // Tekrar giriş yapıldı
        repository.sessionFlow.emit(fakeSession)
        assertEquals(
            listOf(AppAuthState.Checking, AppAuthState.Authenticated, AppAuthState.Unauthenticated, AppAuthState.Authenticated),
            observedStates,
        )
    }

    @Test
    fun duplicateConsecutiveSessions_suppressesRedundantStateEmissions() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        val observedStates = mutableListOf<AppAuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect { observedStates.add(it) }
        }

        repository.sessionFlow.emit(fakeSession)
        repository.sessionFlow.emit(fakeSession.copy(email = "updated@feniqo.com"))

        // Her iki oturum da Authenticated olduğundan distinctUntilChanged tekrar yayınlamaz
        assertEquals(listOf(AppAuthState.Checking, AppAuthState.Authenticated), observedStates)
    }

    @Test
    fun recoveryState_overridesAuthenticatedSessionToPasswordRecovery() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = createViewModel(repository)

        val observedStates = mutableListOf<AppAuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect { observedStates.add(it) }
        }

        // Kullanıcı önceden oturum açmış durumda
        repository.sessionFlow.emit(fakeSession)
        assertEquals(listOf(AppAuthState.Checking, AppAuthState.Authenticated), observedStates)

        // Recovery linki geldiğinde ve doğrulandığında, oturum açık olsa bile Dashboard'a yönlendirilmez
        repository.recoveryStateFlow.value = AuthRecoveryState.Verified("user@feniqo.com")
        assertEquals(
            listOf(
                AppAuthState.Checking,
                AppAuthState.Authenticated,
                AppAuthState.PasswordRecovery(AuthRecoveryState.Verified("user@feniqo.com")),
            ),
            observedStates,
        )

        // Şifre güncellendikten sonra recovery state temizlendiğinde ve oturum kapatıldığında Unauthenticated olur
        repository.sessionFlow.emit(null)
        repository.recoveryStateFlow.value = AuthRecoveryState.Idle
        assertEquals(
            listOf(
                AppAuthState.Checking,
                AppAuthState.Authenticated,
                AppAuthState.PasswordRecovery(AuthRecoveryState.Verified("user@feniqo.com")),
                AppAuthState.Unauthenticated,
            ),
            observedStates,
        )
    }

    @Test
    fun realRepository_abandonVerifiedRecovery_neverPromotesRecoverySessionToAuthenticated() = runTest {
        val remote = RecoveryIntegrationRemoteDataSource()
        val scheduler = RecoveryIntegrationSyncScheduler()
        val repository = OfflineFirstAuthRepository(
            remoteDataSource = remote,
            profileDao = RecoveryIntegrationProfileDao(),
            syncScheduler = scheduler,
        )
        val viewModel = createViewModel(repository)
        val observedStates = mutableListOf<AppAuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect { observedStates.add(it) }
        }

        repository.handleAuthDeepLink(
            "feniqo://auth/callback#access_token=recovery-access&refresh_token=recovery-refresh&type=recovery"
        )
        assertTrue(viewModel.authState.value is AppAuthState.PasswordRecovery)
        val firstRecoveryIndex = observedStates.indexOfFirst { it is AppAuthState.PasswordRecovery }

        val clearResult = repository.clearRecoveryState()

        assertTrue(clearResult is RepositoryResult.Success<Unit>)
        assertSame(AppAuthState.Unauthenticated, viewModel.authState.value)
        assertEquals(1, remote.signOutCallCount)
        assertEquals(1, scheduler.cancelCallCount)
        assertTrue(observedStates.drop(firstRecoveryIndex).none { it is AppAuthState.Authenticated })
    }

    @Test
    fun upstreamException_emitsUnauthenticatedForSafety() = runTest {
        val errorRepository = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw RuntimeException("Ağ veya veritabanı oturum hatası")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flow { emit(null) }
            override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
            override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

        val viewModel = createViewModel(errorRepository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect {}
        }

        assertSame(AppAuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun cancellationException_isRethrownAndNotSwallowed() = runTest {
        val cancellingRepository = object : AuthRepository {
            override fun observeSession(): Flow<AuthSession?> = flow {
                throw CancellationException("Scope cancelled")
            }
            override fun observeCurrentProfile(): Flow<UserProfile?> = flow { emit(null) }
            override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
            override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
            override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        }

        val viewModel = createViewModel(cancellingRepository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.authState.collect {}
        }

        // CancellationException catch bloğunda normal hata gibi yakalanıp Unauthenticated yayınlamaz
        assertSame(AppAuthState.Checking, viewModel.authState.value)
    }
}

private class RecoveryIntegrationRemoteDataSource : AuthRemoteDataSource {
    private val session = MutableStateFlow<RemoteAuthSession?>(null)
    var signOutCallCount = 0

    override fun observeSession(): Flow<RemoteAuthSession?> = session
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, fullName: String?): String = "user-1"
    override suspend fun refreshSession() = Unit
    override suspend fun signOut() {
        signOutCallCount++
        session.value = null
    }
    override suspend fun changePassword(email: String, currentPassword: String, newPassword: String) = Unit
    override suspend fun sendPasswordResetEmail(email: String, redirectUrl: String) = Unit
    override suspend fun resendEmailConfirmation(email: String) = Unit
    override suspend fun updatePassword(newPassword: String) = Unit
    override suspend fun exchangeCodeForSession(code: String) = publishRecoverySession()
    override suspend fun importAuthToken(accessToken: String, refreshToken: String) = publishRecoverySession()

    private fun publishRecoverySession() {
        session.value = RemoteAuthSession(
            userId = "11111111-1111-4111-8111-111111111111",
            email = "recovery@feniqo.com",
            expiresAtEpochSeconds = 1_800_000_000,
        )
    }
}

private class RecoveryIntegrationSyncScheduler : BackgroundSyncScheduler {
    var cancelCallCount = 0
    override fun scheduleInitialSync() = Unit
    override fun scheduleOutboxSync() = Unit
    override fun cancelSyncWork() {
        cancelCallCount++
    }
}

private class RecoveryIntegrationProfileDao : ProfileDao {
    private val profile = MutableStateFlow<UserProfileEntity?>(null)
    override fun observeById(id: String): Flow<UserProfileEntity?> = profile
    override suspend fun upsert(entity: UserProfileEntity) {
        profile.value = entity
    }
    override suspend fun insertIfMissing(entity: UserProfileEntity): Long = -1L
    override suspend fun setActiveWorkspaceGuarded(profileId: String, workspaceId: String): Int = 0
    override suspend fun clearActiveWorkspace(profileId: String): Int = 0
}
