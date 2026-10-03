package com.feniqo.mobile.presentation.localization

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.ThemePreference
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.language_english
import feniqomobil.sharedui.generated.resources.language_region_summary
import feniqomobil.sharedui.generated.resources.language_turkish
import feniqomobil.sharedui.generated.resources.theme_dark
import feniqomobil.sharedui.generated.resources.theme_light
import feniqomobil.sharedui.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedThemeLabel(theme: ThemePreference): String =
    when (theme) {
        ThemePreference.SYSTEM -> stringResource(Res.string.theme_system)
        ThemePreference.LIGHT -> stringResource(Res.string.theme_light)
        ThemePreference.DARK -> stringResource(Res.string.theme_dark)
    }

@Composable
fun localizedLanguageName(language: AppLanguage): String =
    when (language) {
        AppLanguage.TR -> stringResource(Res.string.language_turkish)
        AppLanguage.EN -> stringResource(Res.string.language_english)
    }

@Composable
fun localizedLanguageRegionSummary(
    language: AppLanguage,
    currencyCode: String,
): String =
    stringResource(
        Res.string.language_region_summary,
        localizedLanguageName(language),
        currencyCode,
    )

@Composable
fun localizedSettingsLabels(
    theme: ThemePreference,
    language: AppLanguage,
    currencyCode: String,
): Pair<String, String> =
    localizedThemeLabel(theme) to localizedLanguageRegionSummary(language, currencyCode)
