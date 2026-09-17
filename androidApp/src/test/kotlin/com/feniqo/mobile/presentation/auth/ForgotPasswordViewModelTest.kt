package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SendPasswordResetEmailUseCase
import com.feniqo.mobile.domain.validation.AuthValidationError
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        var sendResetCallCount = 0
        var lastResetEmail: String? = null
        var sendResetResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun sendPasswordResetEmail(email: String): RepositoryResult<Unit> {
            sendResetCallCount++
            lastResetEmail = email
            return sendResetResult
        }
    }

    @Test
    fun invalidEmail_setsEmailErrorAndDoesNotCallRepository() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(SendPasswordResetEmailUseCase(repo))

        viewModel.onEmailChanged("invalid-email")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }

        assertEquals(AuthValidationError.EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(0, repo.sendResetCallCount)
        assertFalse(onSuccessCalled)
    }

    @Test
    fun validEmail_callsRepositoryAndNavigatesOnSuccess() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(SendPasswordResetEmailUseCase(repo))

        viewModel.onEmailChanged("  user@feniqo.com  ")
        var successEmail: String? = null
        viewModel.submit { email -> successEmail = email }
        advanceUntilIdle()

        assertEquals(1, repo.sendResetCallCount)
        assertEquals("user@feniqo.com", repo.lastResetEmail)
        assertEquals("user@feniqo.com", successEmail)
        assertTrue(viewModel.uiState.value.isSentSuccess)
        assertEquals("", viewModel.uiState.value.email)
        assertNull(viewModel.uiState.value.generalMessage)
    }

    @Test
    fun rateLimitError_displaysRateLimitMessageAndDoesNotNavigate() = runTest {
        val repo = FakeAuthRepository()
        repo.sendResetResult = RepositoryResult.Failure(
            AppError.Network("auth_rate_limited")
        )
        val viewModel = ForgotPasswordViewModel(SendPasswordResetEmailUseCase(repo))

        viewModel.onEmailChanged("user@feniqo.com")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }
        advanceUntilIdle()

        assertEquals(1, repo.sendResetCallCount)
        assertFalse(onSuccessCalled)
        assertFalse(viewModel.uiState.value.isSentSuccess)
        assertEquals(AuthUiMessage.RATE_LIMITED, viewModel.uiState.value.generalMessage)
    }

    @Test
    fun networkError_displaysNetworkMessage() = runTest {
        val repo = FakeAuthRepository()
        repo.sendResetResult = RepositoryResult.Failure(
            AppError.Network("network_unavailable")
        )
        val viewModel = ForgotPasswordViewModel(SendPasswordResetEmailUseCase(repo))

        viewModel.onEmailChanged("user@feniqo.com")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }
        advanceUntilIdle()

        assertEquals(1, repo.sendResetCallCount)
        assertFalse(onSuccessCalled)
        assertEquals(AuthUiMessage.NETWORK_UNAVAILABLE, viewModel.uiState.value.generalMessage)
    }
}
