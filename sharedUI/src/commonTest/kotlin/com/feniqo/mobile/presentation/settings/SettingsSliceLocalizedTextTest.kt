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
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSliceLocalizedTextTest {
    @Test
    fun reminderTimeOffset_mapsToExpectedResourcesExhaustively() {
        assertEquals(Res.string.notifications_reminder_offset_same_day, ReminderTimeOffset.SAME_DAY.toLocalizedResource())
        assertEquals(Res.string.notifications_reminder_offset_one_day_before, ReminderTimeOffset.ONE_DAY_BEFORE.toLocalizedResource())
        assertEquals(Res.string.notifications_reminder_offset_three_days_before, ReminderTimeOffset.THREE_DAYS_BEFORE.toLocalizedResource())
    }

    @Test
    fun autoLockTimeout_mapsToExpectedResourcesExhaustively() {
        assertEquals(Res.string.security_privacy_autolock_immediately, AutoLockTimeout.IMMEDIATELY.toLocalizedResource())
        assertEquals(Res.string.security_privacy_autolock_30_seconds, AutoLockTimeout.AFTER_30_SECONDS.toLocalizedResource())
        assertEquals(Res.string.security_privacy_autolock_1_minute, AutoLockTimeout.AFTER_1_MINUTE.toLocalizedResource())
        assertEquals(Res.string.security_privacy_autolock_5_minutes, AutoLockTimeout.AFTER_5_MINUTES.toLocalizedResource())
    }

    @Test
    fun themePreference_mapsToExpectedTitleAndSubtitleResources() {
        assertEquals(Res.string.appearance_theme_system_title, ThemePreference.SYSTEM.toTitleResource())
        assertEquals(Res.string.appearance_theme_system_subtitle, ThemePreference.SYSTEM.toSubtitleResource())
        assertEquals(Res.string.appearance_theme_light_title, ThemePreference.LIGHT.toTitleResource())
        assertEquals(Res.string.appearance_theme_light_subtitle, ThemePreference.LIGHT.toSubtitleResource())
        assertEquals(Res.string.appearance_theme_dark_title, ThemePreference.DARK.toTitleResource())
        assertEquals(Res.string.appearance_theme_dark_subtitle, ThemePreference.DARK.toSubtitleResource())
    }
}
