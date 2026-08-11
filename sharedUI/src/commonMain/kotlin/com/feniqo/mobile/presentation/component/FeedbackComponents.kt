package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/** Ağ veya veritabanı bekleme durumunda kullanılan ortak görünüm. */
@Composable
fun LoadingContent(modifier: Modifier = Modifier, message: String = "Yükleniyor…") {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}

/** Henüz veri oluşmadığında ekranlara tutarlı bir boş durum sağlar. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Kullanıcının tekrar deneyebileceği hatalarda kullanılan ortak görünüm. */
@Composable
fun ErrorState(
    title: String,
    description: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Tekrar dene") }
    }
}

/** Geri alınması zor kullanıcı işlemlerinde kullanılan onay diyaloğu. */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}

/** Ekranların tek seferlik bilgilendirme mesajlarını göstermek için ortak host. */
@Composable
fun FeniqoSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState)
}
