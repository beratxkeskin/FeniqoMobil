package com.feniqo.mobile.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.domain.model.ReminderTimeOffset
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AndroidUserSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : UserSettingsRepository {

    override fun observeSettings(): Flow<UserSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { p ->
            val mask = p[MaskAmountsKey] ?: false
            val notifHide = p[NotifHideAmountsKey] ?: mask
            UserSettings(
                theme = p[ThemeKey]?.let { stored -> ThemePreference.entries.find { it.name == stored } }
                    ?: ThemePreference.SYSTEM,
                maskAmounts = mask,
                currency = p[CurrencyKey]?.let { stored -> runCatching { Currency.valueOf(stored) }.getOrNull() }
                    ?: Currency.TRY,
                language = p[LanguageKey]?.let { stored -> AppLanguage.entries.find { it.name == stored } }
                    ?: AppLanguage.TR,
                region = p[RegionKey] ?: "TR",
                dateFormat = p[DateFormatKey]?.let { stored -> DateFormatPreference.entries.find { it.name == stored } }
                    ?: DateFormatPreference.DD_MM_YYYY,
                numberFormat = p[NumberFormatKey]?.let { stored -> NumberFormatPreference.entries.find { it.name == stored } }
                    ?: NumberFormatPreference.DOT_COMMA,
                firstDayOfWeek = p[FirstDayOfWeekKey]?.let { stored -> FirstDayOfWeekPreference.entries.find { it.name == stored } }
                    ?: FirstDayOfWeekPreference.MONDAY,
                notifications = NotificationPreferences(
                    enabled = p[NotifEnabledKey] ?: true,
                    remindDebts = p[NotifDebtsKey] ?: true,
                    remindSubscriptions = p[NotifSubscriptionsKey] ?: true,
                    remindRecurring = p[NotifRecurringKey] ?: true,
                    remindBudgets = p[NotifBudgetsKey] ?: true,
                    reminderOffset = p[NotifOffsetKey]?.let { stored -> ReminderTimeOffset.entries.find { it.name == stored } }
                        ?: ReminderTimeOffset.ONE_DAY_BEFORE,
                    reminderHour = p[NotifHourKey] ?: 9,
                    reminderMinute = p[NotifMinuteKey] ?: 0,
                    quietHoursEnabled = p[QuietHoursEnabledKey] ?: true,
                    quietHoursStartHour = p[QuietHoursStartHourKey] ?: 22,
                    quietHoursStartMinute = p[QuietHoursStartMinuteKey] ?: 0,
                    quietHoursEndHour = p[QuietHoursEndHourKey] ?: 8,
                    quietHoursEndMinute = p[QuietHoursEndMinuteKey] ?: 0,
                    hideAmountsInNotifications = notifHide,
                ),
            )
        }

    override suspend fun updateTheme(theme: ThemePreference): RepositoryResult<Unit> =
        write { it[ThemeKey] = theme.name }

    override suspend fun updateMaskAmounts(mask: Boolean): RepositoryResult<Unit> =
        write {
            it[MaskAmountsKey] = mask
            it[NotifHideAmountsKey] = mask
        }

    override suspend fun updateCurrency(currency: Currency): RepositoryResult<Unit> =
        write { it[CurrencyKey] = currency.name }

    override suspend fun updateLanguage(language: AppLanguage): RepositoryResult<Unit> =
        write { it[LanguageKey] = language.name }

    override suspend fun updateDateFormat(format: DateFormatPreference): RepositoryResult<Unit> =
        write { it[DateFormatKey] = format.name }

    override suspend fun updateNumberFormat(format: NumberFormatPreference): RepositoryResult<Unit> =
        write { it[NumberFormatKey] = format.name }

    override suspend fun updateFirstDayOfWeek(firstDay: FirstDayOfWeekPreference): RepositoryResult<Unit> =
        write { it[FirstDayOfWeekKey] = firstDay.name }

    override suspend fun updateRegion(region: String): RepositoryResult<Unit> =
        write { it[RegionKey] = region }

    override suspend fun updateNotificationPreferences(preferences: NotificationPreferences): RepositoryResult<Unit> =
        write { p ->
            p[NotifEnabledKey] = preferences.enabled
            p[NotifDebtsKey] = preferences.remindDebts
            p[NotifSubscriptionsKey] = preferences.remindSubscriptions
            p[NotifRecurringKey] = preferences.remindRecurring
            p[NotifBudgetsKey] = preferences.remindBudgets
            p[NotifOffsetKey] = preferences.reminderOffset.name
            p[NotifHourKey] = preferences.reminderHour
            p[NotifMinuteKey] = preferences.reminderMinute
            p[QuietHoursEnabledKey] = preferences.quietHoursEnabled
            p[QuietHoursStartHourKey] = preferences.quietHoursStartHour
            p[QuietHoursStartMinuteKey] = preferences.quietHoursStartMinute
            p[QuietHoursEndHourKey] = preferences.quietHoursEndHour
            p[QuietHoursEndMinuteKey] = preferences.quietHoursEndMinute
            p[NotifHideAmountsKey] = preferences.hideAmountsInNotifications
            p[MaskAmountsKey] = preferences.hideAmountsInNotifications
        }

    private suspend fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): RepositoryResult<Unit> =
        try {
            dataStore.edit(block)
            RepositoryResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            RepositoryResult.Failure(AppError.Storage("user_settings_write_failed"))
        } catch (_: Throwable) {
            RepositoryResult.Failure(AppError.Unknown("user_settings_write_failed"))
        }

    companion object {
        val ThemeKey = stringPreferencesKey("theme_preference")
        val MaskAmountsKey = booleanPreferencesKey("mask_amounts")
        val CurrencyKey = stringPreferencesKey("currency_code")
        val LanguageKey = stringPreferencesKey("language_code")
        val RegionKey = stringPreferencesKey("user_settings_region")
        val DateFormatKey = stringPreferencesKey("date_format")
        val NumberFormatKey = stringPreferencesKey("number_format")
        val FirstDayOfWeekKey = stringPreferencesKey("first_day_of_week")

        val NotifEnabledKey = booleanPreferencesKey("notif_enabled")
        val NotifDebtsKey = booleanPreferencesKey("notif_debts")
        val NotifSubscriptionsKey = booleanPreferencesKey("notif_subscriptions")
        val NotifRecurringKey = booleanPreferencesKey("notif_recurring")
        val NotifBudgetsKey = booleanPreferencesKey("notif_budgets")
        val NotifOffsetKey = stringPreferencesKey("notif_offset")
        val NotifHourKey = intPreferencesKey("notif_hour")
        val NotifMinuteKey = intPreferencesKey("notif_minute")
        val QuietHoursEnabledKey = booleanPreferencesKey("quiet_hours_enabled")
        val QuietHoursStartHourKey = intPreferencesKey("quiet_hours_start_hour")
        val QuietHoursStartMinuteKey = intPreferencesKey("quiet_hours_start_minute")
        val QuietHoursEndHourKey = intPreferencesKey("quiet_hours_end_hour")
        val QuietHoursEndMinuteKey = intPreferencesKey("quiet_hours_end_minute")
        val NotifHideAmountsKey = booleanPreferencesKey("notif_hide_amounts")
    }
}
