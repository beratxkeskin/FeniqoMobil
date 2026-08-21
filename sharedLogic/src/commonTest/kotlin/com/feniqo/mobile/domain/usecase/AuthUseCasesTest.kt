package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUseCasesTest {

    private class FakeAuthRepository : AuthRepository {
        val sessionFlow = MutableStateFlow<AuthSession?>(null)
        var lastSignInEmail: String? = null
        var lastSignInPassword: String? = null
        var signInResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        var lastSignUpEmail: String? = null
        var lastSignUpPassword: String? = null
        var lastSignUpFullName: String? = null
        var signUpResult: RepositoryResult<EntityId> = RepositoryResult.Success(EntityId("user-1"))

        var throwOnSignIn: CancellationException? = null
        var throwOnSignUp: CancellationException? = null

        override fun observeSession(): Flow<AuthSession?> = sessionFlow
        override fun observeCurrentProfile(): Flow<UserProfile?> = MutableStateFlow(null)

        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> {
            throwOnSignIn?.let { throw it }
            lastSignInEmail = email
            lastSignInPassword = password
            return signInResult
        }

        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> {
            throwOnSignUp?.let { throw it }
            lastSignUpEmail = email
            lastSignUpPassword = password
            lastSignUpFullName = fullName
            return signUpResult
        }

        override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun signOut(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    @Test
    fun observeAuthSessionUseCase_delegatesToRepositorySessionFlow() = runTest {
        val repository = FakeAuthRepository()
        val useCase = ObserveAuthSessionUseCase(repository)

        assertNull(useCase().first())

        val expectedSession = AuthSession(
            userId = EntityId("user-1"),
            email = "test@example.com",
            expiresAt = Instant.fromEpochMilliseconds(1000L),
        )
        repository.sessionFlow.value = expectedSession

        assertEquals(expectedSession, useCase().first())
    }

    @Test
    fun signInUseCase_passesTrimmedEmailAndUntouchedPassword() = runTest {
        val repository = FakeAuthRepository()
        val useCase = SignInUseCase(repository)

        val result = useCase(email = "  user@feniqo.com  ", password = "SecretPassword123")

        assertTrue(result is RepositoryResult.Success)
        assertEquals("user@feniqo.com", repository.lastSignInEmail)
        assertEquals("SecretPassword123", repository.lastSignInPassword)
    }

    @Test
    fun signInUseCase_propagatesFailureResult() = runTest {
        val repository = FakeAuthRepository().apply {
            signInResult = RepositoryResult.Failure(AppError.Authentication("auth_invalid_credentials"))
        }
        val useCase = SignInUseCase(repository)

        val result = useCase(email = "user@feniqo.com", password = "wrong")

        assertTrue(result is RepositoryResult.Failure)
        assertEquals("auth_invalid_credentials", result.error.code)
    }

    @Test
    fun signInUseCase_rethrowsCancellationException() = runTest {
        val repository = FakeAuthRepository().apply {
            throwOnSignIn = CancellationException("SignIn cancelled")
        }
        val useCase = SignInUseCase(repository)

        var cancellationThrown = false
        try {
            useCase(email = "user@feniqo.com", password = "password")
        } catch (e: CancellationException) {
            cancellationThrown = true
        }

        assertTrue(cancellationThrown, "CancellationException tekrar fırlatılmalıdır")
    }

    @Test
    fun signUpUseCase_passesTrimmedArgumentsAndNormalizedFullName() = runTest {
        val repository = FakeAuthRepository()
        val useCase = SignUpUseCase(repository)

        val result = useCase(
            email = "  newuser@feniqo.com  ",
            password = "SecurePassword456",
            fullName = "  Ali Veli  ",
        )

        assertTrue(result is RepositoryResult.Success)
        assertEquals(EntityId("user-1"), result.value)
        assertEquals("newuser@feniqo.com", repository.lastSignUpEmail)
        assertEquals("SecurePassword456", repository.lastSignUpPassword)
        assertEquals("Ali Veli", repository.lastSignUpFullName)
    }

    @Test
    fun signUpUseCase_normalizesEmptyOrBlankFullNameToNull() = runTest {
        val repository = FakeAuthRepository()
        val useCase = SignUpUseCase(repository)

        useCase(
            email = "newuser@feniqo.com",
            password = "SecurePassword456",
            fullName = "   ",
        )

        assertNull(repository.lastSignUpFullName)
    }

    @Test
    fun signUpUseCase_propagatesConflictFailureResult() = runTest {
        val repository = FakeAuthRepository().apply {
            signUpResult = RepositoryResult.Failure(AppError.Conflict("auth_email_already_registered"))
        }
        val useCase = SignUpUseCase(repository)

        val result = useCase(email = "existing@feniqo.com", password = "password")

        assertTrue(result is RepositoryResult.Failure)
        assertEquals("auth_email_already_registered", result.error.code)
    }

    @Test
    fun signUpUseCase_rethrowsCancellationException() = runTest {
        val repository = FakeAuthRepository().apply {
            throwOnSignUp = CancellationException("SignUp cancelled")
        }
        val useCase = SignUpUseCase(repository)

        var cancellationThrown = false
        try {
            useCase(email = "user@feniqo.com", password = "password")
        } catch (e: CancellationException) {
            cancellationThrown = true
        }

        assertTrue(cancellationThrown, "CancellationException tekrar fırlatılmalıdır")
    }
}
