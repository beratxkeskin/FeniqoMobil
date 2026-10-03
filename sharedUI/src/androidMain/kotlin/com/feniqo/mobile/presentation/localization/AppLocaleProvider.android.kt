package com.feniqo.mobile.presentation.localization

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
actual fun provideAppLocale(
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    val resources = LocalContext.current.resources
    val localizedConfiguration = Configuration(LocalConfiguration.current)
    val locale = languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()

    Locale.setDefault(locale)
    localizedConfiguration.setLocale(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(localizedConfiguration, resources.displayMetrics)

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
    ) {
        key(languageTag) {
            content()
        }
    }
}
