package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.sync.resolveSyncDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens

@Composable
fun ProfileScreen(
    displayName: String, email: String, workspaceName: String, currencyCode: String,
    isProfileLoading: Boolean, signOutError: String?, themeLabel: String,
    biometricLockEnabled: Boolean, autoLockLabel: String, syncStatus: SyncStatusUiState,
    appVersion: String, onBack: () -> Unit, onOpenSettings: () -> Unit,
    onOpenSharedSpaces: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier,
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val sync = resolveSyncDisplayModel(syncStatus)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Large), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                    Column {
                        Text("Profil Merkezi", style = FeniqoTypographyTokens.DisplayTitle)
                        Text("Hesabın, tercihler ve verilerin tek yerde.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { ProfileSummary(displayName, email, workspaceName, isProfileLoading) }
            item { SyncInsight(sync.title, sync.message, syncStatus.pendingCount > 0) }
            item { Section("Hesap") }
            item { ProfileGroup {
                ProfileRow("Kişisel Bilgiler", "Profil düzenleme yakında", Icons.Default.Person, MaterialTheme.colorScheme.primary, null)
                ProfileRow("Ortak Alanlar", workspaceName, Icons.Default.Groups, MaterialTheme.colorScheme.tertiary, onOpenSharedSpaces, true)
            } }
            item { Section("Tercihler") }
            item { ProfileGroup {
                ProfileRow("Görünüm", themeLabel, Icons.Default.Palette, MaterialTheme.colorScheme.secondary, onOpenSettings)
                ProfileRow("Bildirimler", "Tercihler yakında", Icons.Default.Notifications, MaterialTheme.colorScheme.tertiary, null)
                ProfileRow("Dil ve Bölge", "Türkçe · $currencyCode", Icons.Default.Language, MaterialTheme.colorScheme.primary, null, true)
            } }
            item { Section("Güvenlik ve Veri") }
            item { ProfileGroup {
                val lockText = if (biometricLockEnabled) "Açık · $autoLockLabel" else "Kapalı"
                ProfileRow("Uygulama Kilidi", lockText, Icons.Default.Lock, MaterialTheme.colorScheme.primary, onOpenSettings)
                ProfileRow("Veri Yönetimi", "CSV dışa aktarma ve JSON yedek", Icons.Default.FolderCopy, MaterialTheme.colorScheme.tertiary, onOpenSettings)
                ProfileRow("Gizlilik", "Gizlilik bilgileri yakında", Icons.Default.PrivacyTip, MaterialTheme.colorScheme.onSurfaceVariant, null, true)
            } }
            item { Section("Destek") }
            item { ProfileGroup {
                ProfileRow("Yardım ve Geri Bildirim", "Destek merkezi yakında", Icons.Default.HelpOutline, MaterialTheme.colorScheme.secondary, null)
                ProfileRow("Uygulama sürümü", appVersion, Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant, null, true, showArrow = false, comingSoon = false)
            } }
            signOutError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
            item { TextButton(onClick = { showSignOutConfirmation = true }, modifier = Modifier.fillMaxWidth().padding(top = FeniqoSpacing.Small)) {
                Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(FeniqoSpacing.Small)); Text("Çıkış Yap", color = MaterialTheme.colorScheme.error)
            } }
        }
    }
    if (showSignOutConfirmation) AlertDialog(onDismissRequest = { showSignOutConfirmation = false }, title = { Text("Çıkış yapılsın mı?") }, text = { Text("Bu cihazdaki oturumunuz güvenle kapatılacak.") }, confirmButton = { TextButton(onClick = { showSignOutConfirmation = false; onSignOut() }) { Text("Çıkış Yap") } }, dismissButton = { TextButton(onClick = { showSignOutConfirmation = false }) { Text("Vazgeç") } })
}

@Composable private fun ProfileSummary(name: String, email: String, workspace: String, loading: Boolean) = Card(shape = RoundedCornerShape(FeniqoRadius.Large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), elevation = CardDefaults.cardElevation(4.dp)) {
    Row(Modifier.fillMaxWidth().padding(FeniqoSpacing.LargePlus), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(name.firstOrNull()?.uppercase() ?: "F", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(FeniqoSpacing.Medium)); Column(Modifier.weight(1f)) {
            Text(if (loading) "Profiliniz yükleniyor" else name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(FeniqoSpacing.ExtraSmall)); AssistChip(onClick = {}, enabled = false, label = { Text(workspace) }, leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable private fun SyncInsight(title: String, message: String, pending: Boolean) = Card(shape = RoundedCornerShape(FeniqoRadius.Large), colors = CardDefaults.cardColors(containerColor = if (pending) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
    Row(Modifier.fillMaxWidth().padding(FeniqoSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .55f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(if (pending) Icons.Default.Sync else Icons.Default.Verified, null) }
        Spacer(Modifier.width(FeniqoSpacing.Medium)); Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = FeniqoSpacing.Small))

@Composable private fun ProfileGroup(content: @Composable ColumnScope.() -> Unit) = Card(shape = RoundedCornerShape(FeniqoRadius.Large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), elevation = CardDefaults.cardElevation(2.dp)) { Column(content = content) }

@Composable private fun ProfileRow(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: (() -> Unit)?, last: Boolean = false, showArrow: Boolean = true, comingSoon: Boolean = onClick == null) {
    val enabled = onClick != null
    Row(Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.semantics { disabled() }).alpha(if (enabled || !comingSoon) 1f else .58f).padding(FeniqoSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
        Spacer(Modifier.width(FeniqoSpacing.Medium)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium); if (comingSoon) { Spacer(Modifier.width(FeniqoSpacing.Small)); Text("Yakında", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (enabled && showArrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "$title aç")
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
