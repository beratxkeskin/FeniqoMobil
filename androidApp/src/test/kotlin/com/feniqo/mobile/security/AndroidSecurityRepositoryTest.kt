package com.feniqo.mobile.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSecurityRepositoryTest {
    @Test
    fun defaultsAreDisabledWithOneMinuteTimeout() = runTest {
        val repository = repository()

        val settings = repository.observeSettings().first()

        assertFalse(settings.biometricLockEnabled)
        assertEquals(AutoLockTimeout.AFTER_1_MINUTE, settings.autoLockTimeout)
    }

    @Test
    fun enabledAndTimeoutArePersisted() = runTest {
        val repository = repository()

        assertTrue(repository.setBiometricLockEnabled(true) is RepositoryResult.Success)
        val timeoutResult = repository.setAutoLockTimeout(AutoLockTimeout.AFTER_30_SECONDS)
        assertTrue(timeoutResult.toString(), timeoutResult is RepositoryResult.Success)

        val settings = repository.observeSettings().first()
        assertTrue(settings.biometricLockEnabled)
        assertEquals(AutoLockTimeout.AFTER_30_SECONDS, settings.autoLockTimeout)
    }

    private fun repository(): AndroidSecurityRepository = AndroidSecurityRepository(InMemoryPreferencesDataStore())
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
