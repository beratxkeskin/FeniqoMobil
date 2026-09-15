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
import com.feniqo.mobile.presentation.util.MoneyFormatter

@Composable
fun TransactionConflictDialog(conflict: SyncConflict, resolving: Boolean, error: String?,
    onResolve: (ConflictResolution) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!resolving) onDismiss() },
        title = { Text("Bu işlem iki cihazda değişti") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hangi sürümü kullanmak istediğini seç.")
                ConflictValues("Bu cihaz", conflict.localTransaction)
                ConflictValues("Diğer cihaz", conflict.remoteTransaction)
                if (conflict.remoteDeleted) Text("Diğer cihazda silinmiş. Bu sürümü seçersen işlem listeden kaldırılır.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (resolving) CircularProgressIndicator()
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onResolve(ConflictResolution.KEEP_LOCAL) },
                    enabled = !resolving && conflict.localTransaction != null && conflict.remoteTransaction != null,
                    modifier = Modifier.fillMaxWidth()) { Text("Bu cihazdakini kullan") }
                Button(onClick = { onResolve(ConflictResolution.KEEP_REMOTE) },
                    enabled = !resolving && conflict.localTransaction != null && conflict.remoteTransaction != null,
                    modifier = Modifier.fillMaxWidth()) { Text("Diğer cihazdakini kullan") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !resolving) { Text("Şimdi değil") } })
}

@Composable
private fun ConflictValues(label: String, transaction: Transaction?) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (transaction == null) Text("Karşılaştırma bilgileri okunamadı.") else {
                Text(MoneyFormatter.format(transaction.amount, includeSign = true, type = transaction.type))
                Text(transaction.description ?: "İsimsiz işlem")
                Text(formatDisplayDate(transaction.transactionDate))
                Text(transaction.paymentMethod.toDisplayText())
                Text(transaction.note ?: "Not yok")
                transaction.installment?.let { Text("Taksit: ${it.number}/${it.total}") }
                Text("Katılımcı sayısı: ${transaction.participantUserIds.size}")
                Text(if (transaction.receiptPath != null) "Makbuz mevcut" else "Makbuz yok")
            }
        }
    }
}
