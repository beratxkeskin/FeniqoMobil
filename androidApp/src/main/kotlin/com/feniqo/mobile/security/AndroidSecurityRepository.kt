package com.feniqo.mobile.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.domain.repository.DatabaseProtectionStatus
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SecurityRepository
import com.feniqo.mobile.domain.repository.SecuritySettings
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AndroidSecurityRepository(
    private val dataStore: DataStore<Preferences>,
) : SecurityRepository {
    override fun observeSettings(): Flow<SecuritySettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            SecuritySettings(
                biometricLockEnabled = preferences[BiometricEnabledKey] ?: false,
                autoLockTimeout = preferences[AutoLockTimeoutKey]
                    ?.let { stored -> AutoLockTimeout.entries.find { it.name == stored } }
                    ?: AutoLockTimeout.AFTER_1_MINUTE,
                databaseProtectionStatus = DatabaseProtectionStatus.PROTECTED,
            )
        }

    override suspend fun setBiometricLockEnabled(enabled: Boolean): RepositoryResult<Unit> =
        write { it[BiometricEnabledKey] = enabled }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout): RepositoryResult<Unit> =
        write { it[AutoLockTimeoutKey] = timeout.name }

    override suspend fun clearSecureSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    private suspend fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): RepositoryResult<Unit> =
        try {
            dataStore.edit(block)
            RepositoryResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            RepositoryResult.Failure(AppError.Storage("security_preferences_write_failed"))
        } catch (_: Throwable) {
            RepositoryResult.Failure(AppError.Unknown("security_preferences_write_failed"))
        }

    private companion object {
        val BiometricEnabledKey = booleanPreferencesKey("biometric_lock_enabled")
        val AutoLockTimeoutKey = stringPreferencesKey("auto_lock_timeout")
    }
}
