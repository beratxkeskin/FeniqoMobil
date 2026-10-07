package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.sync.WorkspaceConflictDialogState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.workspace_conflict_applying
import feniqomobil.sharedui.generated.resources.workspace_conflict_cancel
import feniqomobil.sharedui.generated.resources.workspace_conflict_description
import feniqomobil.sharedui.generated.resources.workspace_conflict_keep_local
import feniqomobil.sharedui.generated.resources.workspace_conflict_keep_remote
import feniqomobil.sharedui.generated.resources.workspace_conflict_local_version
import feniqomobil.sharedui.generated.resources.workspace_conflict_remote_version
import feniqomobil.sharedui.generated.resources.workspace_conflict_title
import org.jetbrains.compose.resources.stringResource

/**
 * Kullanıcıya Workspace çakışmasını bildiren ve KEEP_REMOTE veya KEEP_LOCAL seçimini sunan
 * durumsuz (stateless) Compose onay diyaloğudur.
 */
@Composable
fun WorkspaceConflictResolutionDialog(
    dialogState: WorkspaceConflictDialogState,
    onResolve: (ConflictResolution) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            if (!dialogState.isResolving) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = stringResource(Res.string.workspace_conflict_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Text(
                    text = stringResource(Res.string.workspace_conflict_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SurfaceVersionInfoBox(
                    localVersion = dialogState.conflict.localVersion,
                    remoteVersion = dialogState.conflict.remoteVersion,
                )

                // Hata bildirimi kutusu
                if (dialogState.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FeniqoRadius.Small))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = dialogState.error.toLocalizedText(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Çözüm sürerken progress
                if (dialogState.isResolving) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FeniqoSpacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                        Text(
                            text = stringResource(Res.string.workspace_conflict_applying),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                // 1. Yerel Değişikliği Koru (KEEP_LOCAL)
                Button(
                    onClick = { onResolve(ConflictResolution.KEEP_LOCAL) },
                    enabled = !dialogState.isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.workspace_conflict_keep_local))
                }

                // 2. Sunucu Verisini Al (KEEP_REMOTE)
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.KEEP_REMOTE) },
                    enabled = !dialogState.isResolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.workspace_conflict_keep_remote))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !dialogState.isResolving,
            ) {
                Text(text = stringResource(Res.string.workspace_conflict_cancel))
            }
        },
    )
}

@Composable
private fun SurfaceVersionInfoBox(
    localVersion: Long,
    remoteVersion: Long,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Small))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.workspace_conflict_local_version, localVersion),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.workspace_conflict_remote_version, remoteVersion),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
