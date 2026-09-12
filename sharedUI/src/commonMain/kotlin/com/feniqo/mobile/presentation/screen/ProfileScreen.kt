package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item {
                TextButton(onClick = onBack) { Text("‹ Geri") }
                Text("Profil ve Hesap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Kişisel bilgilerini ve uygulama tercihlerini yönet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { ProfileEntry("Kişisel Bilgiler", "Profil bilgileri", available = false, onClick = null) }
            item { ProfileEntry("Hesap", "Hesap yönetimi", available = false, onClick = null) }
            item { ProfileEntry("Bildirimler", "Hatırlatıcı ve uyarı tercihleri", available = false, onClick = null) }
            item { ProfileEntry("Görünüm ve Güvenlik", "Tema, veri araçları ve uygulama kilidi", available = true, onClick = onOpenSettings) }
            item { ProfileEntry("Tercihler", "Kişisel uygulama tercihleri", available = false, onClick = null) }
        }
    }
}

@Composable
private fun ProfileEntry(title: String, subtitle: String, available: Boolean, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (available && onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier.alpha(.62f),
        ),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(FeniqoSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!available) {
                        Spacer(Modifier.width(FeniqoSpacing.Small))
                        Text("Yakında", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (available) Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
