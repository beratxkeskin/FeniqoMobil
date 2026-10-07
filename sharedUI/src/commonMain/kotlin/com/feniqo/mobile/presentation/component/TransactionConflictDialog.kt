@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.SyncConflict
import com.feniqo.mobile.presentation.transaction.TransactionSurfaceUiMessage
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import com.feniqo.mobile.presentation.transaction.toLocalizedTransactionDate
import com.feniqo.mobile.presentation.util.MoneyFormatter
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionConflictDialog(conflict: SyncConflict, resolving: Boolean, error: TransactionSurfaceUiMessage?,
    onResolve: (ConflictResolution) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!resolving) onDismiss() },
        title = { Text(stringResource(Res.string.transaction_conflict_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.transaction_conflict_instruction))
                ConflictValues(stringResource(Res.string.transaction_conflict_local_device), conflict.localTransaction)
                ConflictValues(stringResource(Res.string.transaction_conflict_other_device), conflict.remoteTransaction)
                if (conflict.remoteDeleted) Text(stringResource(Res.string.transaction_conflict_remote_deleted))
                error?.let { Text(it.toLocalizedText(), color = MaterialTheme.colorScheme.error) }
                if (resolving) CircularProgressIndicator()
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onResolve(ConflictResolution.KEEP_LOCAL) },
                    enabled = !resolving && conflict.localTransaction != null && conflict.remoteTransaction != null,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.transaction_conflict_keep_local))
                }
                Button(onClick = { onResolve(ConflictResolution.KEEP_REMOTE) },
                    enabled = !resolving && conflict.localTransaction != null && conflict.remoteTransaction != null,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.transaction_conflict_keep_remote))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resolving) {
                Text(stringResource(Res.string.transaction_conflict_later))
            }
        })
}

@Composable
private fun ConflictValues(label: String, transaction: Transaction?) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (transaction == null) Text(stringResource(Res.string.transaction_conflict_comparison_unavailable)) else {
                Text(MoneyFormatter.format(transaction.amount, includeSign = true, type = transaction.type))
                Text(transaction.description ?: stringResource(Res.string.transaction_conflict_unnamed))
                Text(transaction.transactionDate.toLocalizedTransactionDate())
                Text(transaction.paymentMethod.toLocalizedText())
                Text(transaction.note ?: stringResource(Res.string.transaction_conflict_note_missing))
                transaction.installment?.let {
                    Text(stringResource(Res.string.transaction_conflict_installment, "${it.number}/${it.total}"))
                }
                Text(
                    stringResource(
                        Res.string.transaction_conflict_participant_count,
                        transaction.participantUserIds.size,
                    ),
                )
                Text(
                    stringResource(
                        if (transaction.receiptPath != null) {
                            Res.string.transaction_conflict_receipt_available
                        } else {
                            Res.string.transaction_conflict_receipt_missing
                        },
                    ),
                )
            }
        }
    }
}
