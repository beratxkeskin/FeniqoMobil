package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

data class AppPreferences(
    val currency: Currency,
    val language: AppLanguage,
    val theme: ThemePreference,
)

interface PreferenceRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun updateCurrency(currency: Currency): RepositoryResult<Unit>

    suspend fun updateLanguage(language: AppLanguage): RepositoryResult<Unit>

    suspend fun updateTheme(theme: ThemePreference): RepositoryResult<Unit>
}

enum class DatabaseProtectionStatus {
    UNKNOWN,
    PROTECTED,
    UNPROTECTED,
    UNAVAILABLE,
}

data class SecuritySettings(
    val biometricLockEnabled: Boolean,
    val autoLockTimeoutSeconds: Int,
    val databaseProtectionStatus: DatabaseProtectionStatus,
) {
    init {
        require(autoLockTimeoutSeconds >= 0) { "Otomatik kilit süresi negatif olamaz." }
    }
}

/** BiometricPrompt, Keystore ve Keychain bu ortak sözleşmenin platform uygulamalarıdır. */
interface SecurityRepository {
    fun observeSettings(): Flow<SecuritySettings>

    suspend fun setBiometricLockEnabled(enabled: Boolean): RepositoryResult<Unit>

    suspend fun setAutoLockTimeoutSeconds(seconds: Int): RepositoryResult<Unit>

    suspend fun clearSecureSession(): RepositoryResult<Unit>
}
