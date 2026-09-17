package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ResendEmailConfirmationUseCase
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        var resendCallCount = 0
        var lastResendEmail: String? = null
        var resendResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun resendEmailConfirmation(email: String): RepositoryResult<Unit> {
            resendCallCount++
            lastResendEmail = email
            return resendResult
        }

        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    @Test
    fun resendSuccess_invokesRepositoryAndSetsSuccess() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = EmailVerificationViewModel(ResendEmailConfirmationUseCase(repo))

        viewModel.resend("verify@feniqo.com")
        testScheduler.advanceTimeBy(100)

        assertEquals(1, repo.resendCallCount)
        assertEquals("verify@feniqo.com", repo.lastResendEmail)
        assertTrue(viewModel.uiState.value.resendSuccess)
        assertTrue(viewModel.uiState.value.cooldownSeconds > 0)
    }

    @Test
    fun cooldown_preventsDuplicateCalls() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = EmailVerificationViewModel(ResendEmailConfirmationUseCase(repo))

        viewModel.resend("verify@feniqo.com")
        testScheduler.advanceTimeBy(100)
        assertEquals(1, repo.resendCallCount)

        // İkinci çağrı cooldown devredeyken repo'yu tekrar çağırmamalı
        testScheduler.advanceTimeBy(10_000)
        viewModel.resend("verify@feniqo.com")
        assertEquals(1, repo.resendCallCount)
    }

    @Test
    fun rateLimit_displaysRateLimitMessageAndNoCooldown() = runTest {
        val repo = FakeAuthRepository()
        repo.resendResult = RepositoryResult.Failure(AppError.Network("auth_rate_limited"))
        val viewModel = EmailVerificationViewModel(ResendEmailConfirmationUseCase(repo))

        viewModel.resend("verify@feniqo.com")
        advanceUntilIdle()

        assertEquals(1, repo.resendCallCount)
        assertFalse(viewModel.uiState.value.resendSuccess)
        assertEquals(0, viewModel.uiState.value.cooldownSeconds)
        assertEquals(AuthUiMessage.RATE_LIMITED, viewModel.uiState.value.generalMessage)
    }
}
