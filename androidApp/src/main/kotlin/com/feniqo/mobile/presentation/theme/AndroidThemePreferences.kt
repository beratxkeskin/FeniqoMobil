package com.feniqo.mobile.presentation.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ThemePreferencesFileName = "feniqo_theme_preferences"
private val Context.themePreferencesDataStore by preferencesDataStore(ThemePreferencesFileName)
private val ThemeModeKey = stringPreferencesKey("theme_mode")

/**
 * Android'de tema seçimini küçük ve kalıcı bir kullanıcı tercihi olarak saklar.
 * Bu sınıf yalnız platform depolama ayrıntısını bilir; ThemeMode ortak UI katmanındadır.
 */
class AndroidThemePreferences(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.themePreferencesDataStore.data.map { preferences ->
        preferences[ThemeModeKey]
            ?.let { storedValue -> ThemeMode.entries.find { it.name == storedValue } }
            ?: ThemeMode.SYSTEM
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.themePreferencesDataStore.edit { preferences ->
            preferences[ThemeModeKey] = themeMode.name
        }
    }
}
