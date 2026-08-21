package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SignInUseCase
import com.feniqo.mobile.domain.validation.AuthValidationError
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        var signInCallCount = 0
        var lastSignInEmail: String? = null
        var lastSignInPassword: String? = null
        var signInResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        var deferredSignIn: CompletableDeferred<RepositoryResult<Unit>>? = null
        var throwOnSignIn: CancellationException? = null

        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)

        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> {
            signInCallCount++
            lastSignInEmail = email
            lastSignInPassword = password
            throwOnSignIn?.let { throw it }
            deferredSignIn?.let { return it.await() }
            return signInResult
        }

        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("user-1"))

        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    @Test
    fun defaultState_isInitial() {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(SignInUseCase(repository))

        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.generalMessage)
        assertFalse(state.isSubmitting)
        assertFalse(state.isPasswordVisible)
    }

    @Test
    fun onEmailAndOnPasswordChanged_clearCorrespondingErrorsAndGeneralMessage() {
        val repository = FakeAuthRepository().apply {
            signInResult = RepositoryResult.Failure(AppError.Authentication("auth_invalid_credentials"))
        }
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("wrong")
        viewModel.submit()

        assertEquals(AuthUiMessage.INVALID_CREDENTIALS, viewModel.uiState.value.generalMessage)

        viewModel.onEmailChanged("new@feniqo.com")
        assertNull(viewModel.uiState.value.generalMessage)
        assertEquals("new@feniqo.com", viewModel.uiState.value.email)

        viewModel.onPasswordChanged("newPassword")
        assertNull(viewModel.uiState.value.passwordError)
        assertEquals("newPassword", viewModel.uiState.value.password)
    }

    @Test
    fun togglePasswordVisibility_togglesBooleanFlag() {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(SignInUseCase(repository))

        assertFalse(viewModel.uiState.value.isPasswordVisible)
        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.isPasswordVisible)
        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun submit_withInvalidForm_setsValidationErrorsAndDoesNotCallUseCase() {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("invalid-email")
        viewModel.onPasswordChanged("")
        viewModel.submit()

        assertEquals(AuthValidationError.EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(AuthValidationError.PASSWORD_REQUIRED, viewModel.uiState.value.passwordError)
        assertEquals(0, repository.signInCallCount)
    }

    @Test
    fun submit_success_clearsPassword_leavesSubmittingFalse_andLeavesGeneralMessageNull() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("  user@feniqo.com  ")
        viewModel.onPasswordChanged("SamplePassword123")
        viewModel.submit()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("  user@feniqo.com  ", state.email)
        assertEquals("", state.password)
        assertFalse(state.isSubmitting)
        assertNull(state.generalMessage)
        assertEquals(1, repository.signInCallCount)
        assertEquals("user@feniqo.com", repository.lastSignInEmail)
        assertEquals("SamplePassword123", repository.lastSignInPassword)
    }

    @Test
    fun submit_failure_setsCorrectAuthUiMessage() = runTest {
        val repository = FakeAuthRepository().apply {
            signInResult = RepositoryResult.Failure(AppError.Authentication("auth_invalid_credentials"))
        }
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("wrongPassword")
        viewModel.submit()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthUiMessage.INVALID_CREDENTIALS, state.generalMessage)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun submit_rapidDoubleSubmit_executesOnlyOneCall() = runTest {
        val deferred = CompletableDeferred<RepositoryResult<Unit>>()
        val repository = FakeAuthRepository().apply {
            deferredSignIn = deferred
        }
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SamplePassword123")

        viewModel.submit()
        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.signInCallCount)

        deferred.complete(RepositoryResult.Success(Unit))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.signInCallCount)
    }

    @Test
    fun submit_cancellation_resetsSubmitting_andAllowsSubsequentSubmit() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(SignInUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SamplePassword123")

        repository.throwOnSignIn = CancellationException("SignIn cancelled")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)

        repository.throwOnSignIn = null
        repository.signInResult = RepositoryResult.Success(Unit)
        viewModel.onPasswordChanged("SamplePassword123")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(2, repository.signInCallCount)
    }
}
