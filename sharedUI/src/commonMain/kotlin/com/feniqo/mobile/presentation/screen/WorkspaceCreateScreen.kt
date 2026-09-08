package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.workspace.WorkspaceCreateUiState

@Composable
fun WorkspaceCreateScreen(
    state: WorkspaceCreateUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onTypeChanged: (WorkspaceType) -> Unit,
    onCurrencyChanged: (Currency) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, enabled = !state.isSubmitting) { Text("Geri") }
                Text("Yeni Çalışma Alanı", style = MaterialTheme.typography.titleLarge)
            }
            state.errorMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.toDisplayText(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError, enabled = !state.isSubmitting) { Text("Kapat") }
                }
            }
            OutlinedTextField(
                value = state.name, onValueChange = onNameChanged, enabled = state.isFormEnabled,
                label = { Text("Ad") }, isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Çalışma alanı adı" },
            )
            OutlinedTextField(
                value = state.description, onValueChange = onDescriptionChanged, enabled = state.isFormEnabled,
                label = { Text("Açıklama (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Çalışma alanı açıklaması" },
            )
            Text("Tür", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceType.entries.forEach { type ->
                    TextButton(onClick = { onTypeChanged(type) }, enabled = state.isFormEnabled, modifier = Modifier.semantics { contentDescription = "Tür ${type.name}" }) {
                        Text(if (state.type == type) "✓ ${type.name}" else type.name)
                    }
                }
            }
            Text("Para birimi", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { currency ->
                    TextButton(onClick = { onCurrencyChanged(currency) }, enabled = state.isFormEnabled, modifier = Modifier.semantics { contentDescription = "Para birimi ${currency.code}" }) {
                        Text(if (state.currency == currency) "✓ ${currency.code}" else currency.code)
                    }
                }
            }
            Button(onClick = onSubmit, enabled = state.isFormEnabled, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Çalışma alanı oluştur" }) {
                if (state.isSubmitting) CircularProgressIndicator() else Text("Oluştur")
            }
        }
    }
}
