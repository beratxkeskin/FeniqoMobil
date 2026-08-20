package com.feniqo.mobile.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.sync.SyncConnectionUiState
import com.feniqo.mobile.presentation.sync.SyncDisplayActionType
import com.feniqo.mobile.presentation.sync.SyncDisplaySeverity
import com.feniqo.mobile.presentation.sync.SyncErrorUiType
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.sync.resolveSyncDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoEmerald
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import com.feniqo.mobile.presentation.theme.PhoenixGold
import kotlin.time.Clock

/**
 * Feniqo senkronizasyon durum göstergesi.
 *
 * Ekran akışını engellemeden, Emerald Phoenix tema token'ları ve Material 3 ile
 * kompakt, erişilebilir ve animasyonlu bir durum şeridi sunar.
 */
@Composable
fun SyncStatusIndicator(
    uiState: SyncStatusUiState,
    onManualSync: () -> Unit,
    onRetryFailed: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val displayModel = resolveSyncDisplayModel(uiState, nowEpochMillis)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val (containerColor, contentColor, accentColor, actionColor) = when (displayModel.severity) {
            SyncDisplaySeverity.CONFLICT -> Quadruple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                PhoenixGold,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
            SyncDisplaySeverity.ERROR -> Quadruple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.onErrorContainer,
            )
            SyncDisplaySeverity.OFFLINE -> Quadruple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.colorScheme.primary,
            )
            SyncDisplaySeverity.CHECKING -> Quadruple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary,
            )
            SyncDisplaySeverity.SYNCING -> Quadruple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                FeniqoEmerald,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SyncDisplaySeverity.PENDING -> Quadruple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                PhoenixGold,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
            SyncDisplaySeverity.UP_TO_DATE -> Quadruple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                FeniqoEmerald,
                MaterialTheme.colorScheme.primary,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.ExtraSmall)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${displayModel.title}: ${displayModel.message}"
                },
            shape = RoundedCornerShape(FeniqoRadius.Small),
            color = containerColor,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Durum Gösterge İkonu / Noktası
                    if (displayModel.showProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = accentColor,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                    }

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                    Column {
                        Text(
                            text = displayModel.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = contentColor,
                        )
                    }
                }

                // Aksiyon Butonu (Açık SyncDisplayActionType türüne göre çalışır)
                if (displayModel.actionText != null && displayModel.actionType != null) {
                    TextButton(
                        onClick = {
                            when (displayModel.actionType) {
                                SyncDisplayActionType.RETRY_FAILED -> onRetryFailed()
                                SyncDisplayActionType.MANUAL_SYNC -> onManualSync()
                            }
                        },
                        enabled = displayModel.isActionEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = actionColor,
                            disabledContentColor = contentColor.copy(alpha = 0.38f),
                        ),
                        contentPadding = PaddingValues(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                    ) {
                        Text(
                            text = displayModel.actionText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun IndicatorPreviewContainer(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FeniqoTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.padding(vertical = FeniqoSpacing.Small)) {
                content()
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorCheckingLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState.Initial,
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorCheckingDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState.Initial,
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorOnlineLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = Clock.System.now().toEpochMilliseconds() - 120_000,
                errorType = null,
                canManualSync = true,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorOnlineDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = Clock.System.now().toEpochMilliseconds() - 120_000,
                errorType = null,
                canManualSync = true,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorOfflineLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.OFFLINE,
                isSyncing = false,
                pendingCount = 2,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = false,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorOfflineDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.OFFLINE,
                isSyncing = false,
                pendingCount = 2,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = false,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorSyncingLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = true,
                pendingCount = 1,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = false,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorSyncingDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = true,
                pendingCount = 1,
                failedCount = 0,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = false,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorErrorLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 1,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = SyncErrorUiType.NETWORK,
                canManualSync = true,
                canRetryFailed = true,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorErrorDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 1,
                conflictCount = 0,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = SyncErrorUiType.NETWORK,
                canManualSync = true,
                canRetryFailed = true,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorConflictLightPreview() {
    IndicatorPreviewContainer(darkTheme = false) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 2,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = true,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SyncStatusIndicatorConflictDarkPreview() {
    IndicatorPreviewContainer(darkTheme = true) {
        SyncStatusIndicator(
            uiState = SyncStatusUiState(
                connectionState = SyncConnectionUiState.ONLINE,
                isSyncing = false,
                pendingCount = 0,
                failedCount = 0,
                conflictCount = 2,
                lastSuccessfulSyncAtEpochMillis = null,
                errorType = null,
                canManualSync = true,
                canRetryFailed = false,
            ),
            onManualSync = {},
            onRetryFailed = {},
        )
    }
}
