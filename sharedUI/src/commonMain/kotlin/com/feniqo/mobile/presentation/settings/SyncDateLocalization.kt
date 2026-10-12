package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.sync_last_sync_datetime_format
import feniqomobil.sharedui.generated.resources.sync_never_synced
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

fun formatLocalizedSyncDateTime(
    epochMillis: Long?,
    formatTemplate: String,
    neverSyncedText: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (epochMillis == null) return neverSyncedText
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val ldt = instant.toLocalDateTime(timeZone)
    val day = ldt.dayOfMonth.toString().padStart(2, '0')
    val month = ldt.monthNumber.toString().padStart(2, '0')
    val year = ldt.year.toString()
    val hour = ldt.hour.toString().padStart(2, '0')
    val minute = ldt.minute.toString().padStart(2, '0')
    return formatTemplate
        .replace("%1\$s", day)
        .replace("%2\$s", month)
        .replace("%3\$s", year)
        .replace("%4\$s", hour)
        .replace("%5\$s", minute)
}

@Composable
fun formatLocalizedLastSync(
    epochMillis: Long?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (epochMillis == null) return stringResource(Res.string.sync_never_synced)
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val ldt = instant.toLocalDateTime(timeZone)
    val day = ldt.dayOfMonth.toString().padStart(2, '0')
    val month = ldt.monthNumber.toString().padStart(2, '0')
    val year = ldt.year.toString()
    val hour = ldt.hour.toString().padStart(2, '0')
    val minute = ldt.minute.toString().padStart(2, '0')
    return stringResource(Res.string.sync_last_sync_datetime_format, day, month, year, hour, minute)
}

suspend fun resolveLocalizedLastSync(
    epochMillis: Long?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (epochMillis == null) return getString(Res.string.sync_never_synced)
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val ldt = instant.toLocalDateTime(timeZone)
    val day = ldt.dayOfMonth.toString().padStart(2, '0')
    val month = ldt.monthNumber.toString().padStart(2, '0')
    val year = ldt.year.toString()
    val hour = ldt.hour.toString().padStart(2, '0')
    val minute = ldt.minute.toString().padStart(2, '0')
    return getString(Res.string.sync_last_sync_datetime_format, day, month, year, hour, minute)
}
