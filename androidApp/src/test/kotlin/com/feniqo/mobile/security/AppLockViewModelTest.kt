package com.feniqo.mobile.security

import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.domain.repository.DatabaseProtectionStatus
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SecurityRepository
import com.feniqo.mobile.domain.repository.SecuritySettings
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun enabledPreference_locksColdStart() = runTest {
        val repository = FakeSecurityRepository(settings(enabled = true))
        val viewModel = AppLockViewModel(repository)

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isLocked)
    }

    @Test
    fun foregroundAfterTimeout_locksButEarlyReturnDoesNot() = runTest {
        val repository = FakeSecurityRepository(
            settings(enabled = true, timeout = AutoLockTimeout.AFTER_30_SECONDS),
        )
        val viewModel = AppLockViewModel(repository)
        testScheduler.advanceUntilIdle()
        viewModel.onAuthenticationSucceeded(AppLockAuthenticationAction.UNLOCK)

        viewModel.onBackgrounded(1_000L)
        viewModel.onForegrounded(30_999L)
        assertFalse(viewModel.state.value.isLocked)

        viewModel.onBackgrounded(40_000L)
        viewModel.onForegrounded(70_000L)
        assertTrue(viewModel.state.value.isLocked)
    }

    @Test
    fun enableIsPersistedOnlyAfterSuccessfulAuthentication() = runTest {
        val repository = FakeSecurityRepository(settings(enabled = false))
        val viewModel = AppLockViewModel(repository)
        testScheduler.advanceUntilIdle()

        assertFalse(repository.settings.value.biometricLockEnabled)
        viewModel.onAuthenticationSucceeded(AppLockAuthenticationAction.ENABLE)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.settings.value.biometricLockEnabled)
        assertFalse(viewModel.state.value.isLocked)
    }

    @Test
    fun unavailableAuthentication_hasActionableMessage() = runTest {
        val viewModel = AppLockViewModel(FakeSecurityRepository(settings(enabled = false)))
        testScheduler.advanceUntilIdle()
        viewModel.setAvailability(DeviceAuthenticationAvailability.NONE_ENROLLED)

        viewModel.onAuthenticationError(null)

        assertEquals(
            "Cihaz ayarlarından biyometri veya ekran kilidi tanımlayın.",
            viewModel.state.value.errorMessage,
        )
    }

    private class FakeSecurityRepository(initial: SecuritySettings) : SecurityRepository {
        val settings = MutableStateFlow(initial)
        override fun observeSettings(): Flow<SecuritySettings> = settings

        override suspend fun setBiometricLockEnabled(enabled: Boolean): RepositoryResult<Unit> {
            settings.value = settings.value.copy(biometricLockEnabled = enabled)
            return RepositoryResult.Success(Unit)
        }

        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout): RepositoryResult<Unit> {
            settings.value = settings.value.copy(autoLockTimeout = timeout)
            return RepositoryResult.Success(Unit)
        }

        override suspend fun clearSecureSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private fun settings(
        enabled: Boolean,
        timeout: AutoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
    ) = SecuritySettings(
        biometricLockEnabled = enabled,
        autoLockTimeout = timeout,
        databaseProtectionStatus = DatabaseProtectionStatus.PROTECTED,
    )
}
