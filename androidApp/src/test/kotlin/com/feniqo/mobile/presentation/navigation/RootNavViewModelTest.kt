package com.feniqo.mobile.presentation.navigation

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
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
        override fun clearRecoveryState() {
            recoveryStateFlow.value = AuthRecoveryState.Idle
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
