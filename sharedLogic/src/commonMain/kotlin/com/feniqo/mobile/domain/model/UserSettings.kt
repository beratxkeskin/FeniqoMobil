package com.feniqo.mobile.domain.model

enum class DateFormatPreference(val pattern: String, val sample: String) {
    DD_MM_YYYY("dd.MM.yyyy", "16.09.2026"),
    MM_DD_YYYY("MM/dd/yyyy", "09/16/2026"),
    YYYY_MM_DD("yyyy-MM-dd", "2026-09-16"),
}

enum class NumberFormatPreference(val decimalSeparator: Char, val groupingSeparator: Char, val sample: String) {
    DOT_COMMA(',', '.', "1.234,56"),
    COMMA_DOT('.', ',', "1,234.56"),
}

enum class FirstDayOfWeekPreference(val label: String) {
    MONDAY("Pazartesi"),
    SUNDAY("Pazar"),
}

enum class ReminderTimeOffset(val daysBefore: Int, val label: String) {
    SAME_DAY(0, "Gününde"),
    ONE_DAY_BEFORE(1, "1 gün önce"),
    THREE_DAYS_BEFORE(3, "3 gün önce"),
}

data class NotificationPreferences(
    val enabled: Boolean = true,
    val remindDebts: Boolean = true,
    val remindSubscriptions: Boolean = true,
    val remindRecurring: Boolean = true,
    val remindBudgets: Boolean = true,
    val reminderOffset: ReminderTimeOffset = ReminderTimeOffset.ONE_DAY_BEFORE,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 22,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndHour: Int = 8,
    val quietHoursEndMinute: Int = 0,
    val hideAmountsInNotifications: Boolean = false,
)

data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val maskAmounts: Boolean = false,
    val currency: Currency = Currency.TRY,
    val language: AppLanguage = AppLanguage.TR,
    val region: String = "TR",
    val dateFormat: DateFormatPreference = DateFormatPreference.DD_MM_YYYY,
    val numberFormat: NumberFormatPreference = NumberFormatPreference.DOT_COMMA,
    val firstDayOfWeek: FirstDayOfWeekPreference = FirstDayOfWeekPreference.MONDAY,
    val notifications: NotificationPreferences = NotificationPreferences(),
)
