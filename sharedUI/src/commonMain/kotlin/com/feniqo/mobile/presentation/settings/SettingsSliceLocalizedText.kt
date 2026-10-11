package com.feniqo.mobile.presentation.settings

import com.feniqo.mobile.domain.model.ReminderTimeOffset
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.appearance_theme_dark_subtitle
import feniqomobil.sharedui.generated.resources.appearance_theme_dark_title
import feniqomobil.sharedui.generated.resources.appearance_theme_light_subtitle
import feniqomobil.sharedui.generated.resources.appearance_theme_light_title
import feniqomobil.sharedui.generated.resources.appearance_theme_system_subtitle
import feniqomobil.sharedui.generated.resources.appearance_theme_system_title
import feniqomobil.sharedui.generated.resources.notifications_reminder_offset_one_day_before
import feniqomobil.sharedui.generated.resources.notifications_reminder_offset_same_day
import feniqomobil.sharedui.generated.resources.notifications_reminder_offset_three_days_before
import feniqomobil.sharedui.generated.resources.security_privacy_autolock_1_minute
import feniqomobil.sharedui.generated.resources.security_privacy_autolock_30_seconds
import feniqomobil.sharedui.generated.resources.security_privacy_autolock_5_minutes
import feniqomobil.sharedui.generated.resources.security_privacy_autolock_immediately
import org.jetbrains.compose.resources.StringResource

fun ReminderTimeOffset.toLocalizedResource(): StringResource =
    when (this) {
        ReminderTimeOffset.SAME_DAY -> Res.string.notifications_reminder_offset_same_day
        ReminderTimeOffset.ONE_DAY_BEFORE -> Res.string.notifications_reminder_offset_one_day_before
        ReminderTimeOffset.THREE_DAYS_BEFORE -> Res.string.notifications_reminder_offset_three_days_before
    }

fun AutoLockTimeout.toLocalizedResource(): StringResource =
    when (this) {
        AutoLockTimeout.IMMEDIATELY -> Res.string.security_privacy_autolock_immediately
        AutoLockTimeout.AFTER_30_SECONDS -> Res.string.security_privacy_autolock_30_seconds
        AutoLockTimeout.AFTER_1_MINUTE -> Res.string.security_privacy_autolock_1_minute
        AutoLockTimeout.AFTER_5_MINUTES -> Res.string.security_privacy_autolock_5_minutes
    }

fun ThemePreference.toTitleResource(): StringResource =
    when (this) {
        ThemePreference.SYSTEM -> Res.string.appearance_theme_system_title
        ThemePreference.LIGHT -> Res.string.appearance_theme_light_title
        ThemePreference.DARK -> Res.string.appearance_theme_dark_title
    }

fun ThemePreference.toSubtitleResource(): StringResource =
    when (this) {
        ThemePreference.SYSTEM -> Res.string.appearance_theme_system_subtitle
        ThemePreference.LIGHT -> Res.string.appearance_theme_light_subtitle
        ThemePreference.DARK -> Res.string.appearance_theme_dark_subtitle
    }
