package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import com.feniqo.mobile.presentation.component.formatDisplayDate
import com.feniqo.mobile.presentation.component.toDisplayText
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.ColorParser

fun SyncStatus?.transactionStatusText(): String = when (this) {
    SyncStatus.SYNCED -> "Senkronize edildi"
    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE -> "Senkronizasyon bekleniyor"
    SyncStatus.CONFLICT -> "Değişiklik çakışması var"
    SyncStatus.FAILED -> "Senkronizasyon tamamlanamadı"
    null -> "Senkronizasyon durumu alınamadı"
}

@Composable
fun TransactionDetailScreen(
    item: TransactionDisplayModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onResolveConflict: (() -> Unit)? = null,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text("İşlem detayı", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            CategoryTonalIcon(iconKey = item.categoryIconKey,
                color = ColorParser.parseHexColorOrNull(item.categoryColorHex) ?: MaterialTheme.colorScheme.primary,
                containerSize = 88.dp)
            Text(item.description ?: item.categoryName, style = MaterialTheme.typography.titleLarge)
            Text(item.formattedAmount, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(item.categoryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailField("İşlem adı", item.description ?: "—")
                    HorizontalDivider()
                    DetailField("Ödeme yöntemi", item.paymentMethod.toDisplayText())
                    HorizontalDivider()
                    DetailField("Tarih", formatDisplayDate(item.transactionDate))
                    HorizontalDivider()
                    DetailField("Not", item.note ?: "Not eklenmemiş")
                    item.installment?.let { DetailField("Taksit", it.badgeText) }
                }
            }
            Text(item.syncStatus.transactionStatusText(), style = MaterialTheme.typography.bodyMedium)
            if (onResolveConflict != null) {
                Button(onClick = onResolveConflict, modifier = Modifier.fillMaxWidth()) { Text("Çakışmayı çöz") }
            }
            if (item.canEdit) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (item.hasReceipt) "Makbuz bilgileri" else "Makbuzdan bilgi oku")
                }
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(14.dp)) { Text("İşlemi düzenle") }
            }
            if (item.canDelete) {
                FilledTonalButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(14.dp)) {
                    Text("İşlemi sil")
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
