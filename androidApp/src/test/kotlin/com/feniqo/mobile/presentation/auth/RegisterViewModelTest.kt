package com.feniqo.mobile.presentation.auth

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.SignUpUseCase
import com.feniqo.mobile.domain.validation.AuthValidationError
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        var signUpCallCount = 0
        var lastSignUpEmail: String? = null
        var lastSignUpPassword: String? = null
        var lastSignUpFullName: String? = null
        var signUpResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))
        var deferredSignUp: CompletableDeferred<RepositoryResult<EntityId>>? = null
        var throwOnSignUp: CancellationException? = null

        override fun observeSession(): Flow<AuthSession?> = MutableStateFlow(null)
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)

        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> {
            signUpCallCount++
            lastSignUpEmail = email
            lastSignUpPassword = password
            lastSignUpFullName = fullName
            throwOnSignUp?.let { throw it }
            deferredSignUp?.let { return it.await() }
            return signUpResult
        }

        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    @Test
    fun defaultState_isInitial() {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        val state = viewModel.uiState.value
        assertEquals("", state.fullName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.fullNameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
        assertNull(state.generalMessage)
        assertFalse(state.isSubmitting)
        assertFalse(state.isPasswordVisible)
        assertFalse(state.isConfirmPasswordVisible)
        assertFalse(state.isEmailConfirmationPending)
    }

    @Test
    fun onFieldChanges_clearCorrespondingErrors() {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onFullNameChanged("A")
        viewModel.onEmailChanged("invalid")
        viewModel.onPasswordChanged("123")
        viewModel.onConfirmPasswordChanged("1234")
        viewModel.submit()

        val errorState = viewModel.uiState.value
        assertEquals(AuthValidationError.FULL_NAME_TOO_SHORT, errorState.fullNameError)
        assertEquals(AuthValidationError.EMAIL_INVALID, errorState.emailError)
        assertEquals(AuthValidationError.NEW_PASSWORD_TOO_SHORT, errorState.passwordError)
        assertEquals(AuthValidationError.PASSWORDS_DO_NOT_MATCH, errorState.confirmPasswordError)

        viewModel.onFullNameChanged("Ahmet")
        assertNull(viewModel.uiState.value.fullNameError)

        viewModel.onEmailChanged("user@feniqo.com")
        assertNull(viewModel.uiState.value.emailError)

        viewModel.onPasswordChanged("SecurePassword123")
        assertNull(viewModel.uiState.value.passwordError)

        viewModel.onConfirmPasswordChanged("SecurePassword123")
        assertNull(viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun submit_withMultipleValidationErrors_setsAllFieldErrorsSimultaneously() {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onFullNameChanged("X")
        viewModel.onEmailChanged("")
        viewModel.onPasswordChanged("123")
        viewModel.onConfirmPasswordChanged("456")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(AuthValidationError.FULL_NAME_TOO_SHORT, state.fullNameError)
        assertEquals(AuthValidationError.EMAIL_REQUIRED, state.emailError)
        assertEquals(AuthValidationError.NEW_PASSWORD_TOO_SHORT, state.passwordError)
        assertEquals(AuthValidationError.PASSWORDS_DO_NOT_MATCH, state.confirmPasswordError)
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun submit_withOptionalFullName_passesNormalizedFullName() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onFullNameChanged("   ")
        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")
        viewModel.submit()

        advanceUntilIdle()

        assertEquals(1, repository.signUpCallCount)
        assertNull(repository.lastSignUpFullName)
        assertEquals("user@feniqo.com", repository.lastSignUpEmail)
        assertEquals("SecurePassword123", repository.lastSignUpPassword)
    }

    @Test
    fun submit_success_clearsPasswords_setsEmailConfirmationPending_andSetsConfirmationMessage() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onFullNameChanged("Ali Veli")
        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")
        viewModel.submit()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertTrue(state.isEmailConfirmationPending)
        assertEquals(AuthUiMessage.EMAIL_CONFIRMATION_SENT, state.generalMessage)
        assertFalse(state.isSubmitting)
        assertEquals(1, repository.signUpCallCount)
        assertEquals("Ali Veli", repository.lastSignUpFullName)
    }

    @Test
    fun submit_failure_setsCorrectAuthUiMessage() = runTest {
        val repository = FakeAuthRepository().apply {
            signUpResult = RepositoryResult.Failure(AppError.Conflict("auth_email_already_registered"))
        }
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onEmailChanged("existing@feniqo.com")
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")
        viewModel.submit()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthUiMessage.EMAIL_ALREADY_REGISTERED, state.generalMessage)
        assertFalse(state.isSubmitting)
        assertFalse(state.isEmailConfirmationPending)
    }

    @Test
    fun submit_rapidDoubleSubmit_executesOnlyOneCall() = runTest {
        val deferred = CompletableDeferred<RepositoryResult<EntityId>>()
        val repository = FakeAuthRepository().apply {
            deferredSignUp = deferred
        }
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")

        viewModel.submit()
        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.signUpCallCount)

        deferred.complete(RepositoryResult.Success(EntityId("user-1")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.signUpCallCount)
    }

    @Test
    fun submit_cancellation_resetsSubmitting_andAllowsSubsequentSubmit() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = RegisterViewModel(SignUpUseCase(repository))

        viewModel.onEmailChanged("user@feniqo.com")
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")

        repository.throwOnSignUp = CancellationException("SignUp cancelled")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)

        repository.throwOnSignUp = null
        repository.signUpResult = RepositoryResult.Success(EntityId("user-1"))
        viewModel.onPasswordChanged("SecurePassword123")
        viewModel.onConfirmPasswordChanged("SecurePassword123")
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(2, repository.signUpCallCount)
    }
}
