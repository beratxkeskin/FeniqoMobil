package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.transaction.TransactionDeleteDialogState

/**
 * Tekil işlem silme onay diyaloğudur.
 */
@Composable
fun SingleTransactionDeleteDialog(
    dialog: TransactionDeleteDialogState.Single,
    isDeleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = "İşlemi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Text(
                    text = "Bu işlemi silmek istediğinizden emin misiniz?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${dialog.target.categoryName} • ${dialog.target.formattedAmount}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleteInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                if (isDeleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Sil")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleteInProgress,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text("Vazgeç")
            }
        },
    )
}

/**
 * Taksitli işlem silme kapsamı seçim ve onay diyaloğudur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallmentTransactionDeleteDialog(
    dialog: TransactionDeleteDialogState.Installment,
    isDeleteInProgress: Boolean,
    onConfirm: (InstallmentDeleteScope) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedScope by remember(dialog.target.id) {
        mutableStateOf(InstallmentDeleteScope.ONLY_THIS)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = "Taksitli İşlemi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Text(
                        text = "${dialog.target.categoryName} • ${dialog.target.formattedAmount}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (dialog.target.installment != null) {
                        InstallmentBadge(badgeText = dialog.target.installment.badgeText)
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

                Text(
                    text = "Silme kapsamını seçin:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Scope 1: ONLY_THIS
                InstallmentScopeOption(
                    title = "Yalnızca bu taksiti sil",
                    subtitle = "Sadece seçili olan bu taksit kaydı silinir.",
                    selected = selectedScope == InstallmentDeleteScope.ONLY_THIS,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.ONLY_THIS },
                )

                // Scope 2: THIS_AND_FOLLOWING
                InstallmentScopeOption(
                    title = "Bu ve sonraki taksitleri sil",
                    subtitle = "Seçili taksit ve bu gruptaki sonraki taksitler silinir.",
                    selected = selectedScope == InstallmentDeleteScope.THIS_AND_FOLLOWING,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.THIS_AND_FOLLOWING },
                )

                // Scope 3: ALL_GROUP
                InstallmentScopeOption(
                    title = "Tüm taksit grubunu sil",
                    subtitle = "Bu gruba ait geçmiş ve gelecek tüm taksitler silinir.",
                    selected = selectedScope == InstallmentDeleteScope.ALL_GROUP,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.ALL_GROUP },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedScope) },
                enabled = !isDeleteInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                if (isDeleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Sil")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleteInProgress,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text("Vazgeç")
            }
        },
    )
}

@Composable
private fun InstallmentScopeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // selectable handles row click
            enabled = enabled,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
