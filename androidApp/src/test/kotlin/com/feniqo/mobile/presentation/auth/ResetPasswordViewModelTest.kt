package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRecoveryState
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.ClearRecoveryStateUseCase
import com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase
import com.feniqo.mobile.domain.usecase.ResetPasswordUseCase
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
class ResetPasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        val recoveryFlow = MutableStateFlow<AuthRecoveryState>(AuthRecoveryState.Idle)
        var resetPasswordCallCount = 0
        var lastResetPassword: String? = null
        var resetPasswordResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var clearRecoveryCallCount = 0

        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override fun observeRecoveryState(): Flow<AuthRecoveryState> = recoveryFlow

        override suspend fun resetPassword(newPassword: String): RepositoryResult<Unit> {
            resetPasswordCallCount++
            lastResetPassword = newPassword
            return resetPasswordResult
        }

        override fun clearRecoveryState() {
            clearRecoveryCallCount++
            recoveryFlow.value = AuthRecoveryState.Idle
        }

        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private fun createViewModel(repo: FakeAuthRepository): ResetPasswordViewModel {
        return ResetPasswordViewModel(
            observeRecoveryStateUseCase = ObserveRecoveryStateUseCase(repo),
            resetPasswordUseCase = ResetPasswordUseCase(repo),
            clearRecoveryStateUseCase = ClearRecoveryStateUseCase(repo),
        )
    }

    @Test
    fun unverifiedRecovery_rejectsSubmissionWithoutCallingRepository() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPasswordChanged("Valid1234")
        viewModel.onConfirmPasswordChanged("Valid1234")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }
        advanceUntilIdle()

        assertFalse(onSuccessCalled)
        assertEquals(0, repo.resetPasswordCallCount)
        assertEquals(AuthUiMessage.RECOVERY_NOT_AUTHORIZED, viewModel.uiState.value.generalMessage)
    }

    @Test
    fun expiredOrInvalidRecovery_displaysExpiredLinkMessage() = runTest {
        val repo = FakeAuthRepository()
        repo.recoveryFlow.value = AuthRecoveryState.InvalidOrExpired
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRecoveryLinkInvalid)
        assertEquals(AuthUiMessage.RECOVERY_LINK_INVALID, viewModel.uiState.value.generalMessage)
    }

    @Test
    fun verifiedRecovery_passwordValidationErrors_doNotCallRepository() = runTest {
        val repo = FakeAuthRepository()
        repo.recoveryFlow.value = AuthRecoveryState.Verified("test@feniqo.com")
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRecoveryAuthorized)

        // Parola çok kısa
        viewModel.onPasswordChanged("123")
        viewModel.onConfirmPasswordChanged("123")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }

        assertEquals(AuthValidationError.NEW_PASSWORD_TOO_SHORT, viewModel.uiState.value.passwordError)
        assertEquals(0, repo.resetPasswordCallCount)
        assertFalse(onSuccessCalled)

        // Parolalar eşleşmiyor
        viewModel.onPasswordChanged("Password1234")
        viewModel.onConfirmPasswordChanged("Different1234")
        viewModel.submit { onSuccessCalled = true }

        assertEquals(AuthValidationError.PASSWORDS_DO_NOT_MATCH, viewModel.uiState.value.confirmPasswordError)
        assertEquals(0, repo.resetPasswordCallCount)
        assertFalse(onSuccessCalled)
    }

    @Test
    fun verifiedRecovery_validPassword_submitsClearsPasswordAndResetsState() = runTest {
        val repo = FakeAuthRepository()
        repo.recoveryFlow.value = AuthRecoveryState.Verified("test@feniqo.com")
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPasswordChanged("SuperSecure123")
        viewModel.onConfirmPasswordChanged("SuperSecure123")
        var onSuccessCalled = false
        viewModel.submit { onSuccessCalled = true }
        advanceUntilIdle()

        assertEquals(1, repo.resetPasswordCallCount)
        assertEquals("SuperSecure123", repo.lastResetPassword)
        assertEquals(1, repo.clearRecoveryCallCount)
        assertTrue(onSuccessCalled)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirmPassword)
    }

    @Test
    fun abandonRecovery_clearsPasswordAndClearsRecoveryState() = runTest {
        val repo = FakeAuthRepository()
        repo.recoveryFlow.value = AuthRecoveryState.Verified("test@feniqo.com")
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        viewModel.onPasswordChanged("TemporaryPass")
        viewModel.onConfirmPasswordChanged("TemporaryPass")
        viewModel.abandonRecovery()

        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirmPassword)
        assertEquals(1, repo.clearRecoveryCallCount)
    }
}
