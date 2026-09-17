package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Kullanıcının yerel ve kalıcı ayarlar sözleşmesi (Görünüm, biçim, dil, bildirimler ve tutar gizleme).
 */
interface UserSettingsRepository {
    fun observeSettings(): Flow<UserSettings>

    suspend fun updateTheme(theme: ThemePreference): RepositoryResult<Unit>

    suspend fun updateMaskAmounts(mask: Boolean): RepositoryResult<Unit>

    suspend fun updateCurrency(currency: Currency): RepositoryResult<Unit>

    suspend fun updateLanguage(language: AppLanguage): RepositoryResult<Unit>

    suspend fun updateDateFormat(format: DateFormatPreference): RepositoryResult<Unit>

    suspend fun updateNumberFormat(format: NumberFormatPreference): RepositoryResult<Unit>

    suspend fun updateFirstDayOfWeek(firstDay: FirstDayOfWeekPreference): RepositoryResult<Unit>

    suspend fun updateRegion(region: String): RepositoryResult<Unit>

    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): RepositoryResult<Unit>
}
