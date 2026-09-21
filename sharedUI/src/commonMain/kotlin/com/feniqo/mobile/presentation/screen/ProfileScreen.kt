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
import com.feniqo.mobile.presentation.sync.SyncDisplaySeverity
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.sync.resolveSyncDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens

@Composable
fun ProfileScreen(
    displayName: String,
    email: String,
    workspaceName: String,
    currencyCode: String,
    isProfileLoading: Boolean,
    signOutError: String?,
    themeLabel: String,
    biometricLockEnabled: Boolean,
    autoLockLabel: String,
    syncStatus: SyncStatusUiState,
    appVersion: String,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenSharedSpaces: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenLanguageRegion: () -> Unit,
    onOpenSecurityPrivacy: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenLegalInfo: () -> Unit,
    onOpenHelpAbout: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isProfileEmpty: Boolean = false,
    profileErrorMessage: String? = null,
    onRetryProfile: (() -> Unit)? = null,
    languageRegionLabel: String = "Türkçe · $currencyCode",
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onOpenSettings: () -> Unit = {},
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val sync = resolveSyncDisplayModel(syncStatus)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                    Column {
                        Text("Profil Merkezi", style = FeniqoTypographyTokens.DisplayTitle)
                        Text(
                            "Hesabın, tercihler ve verilerin tek yerde.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ProfileSummary(
                    name = displayName,
                    email = email,
                    workspace = workspaceName,
                    loading = isProfileLoading,
                    isEmpty = isProfileEmpty,
                    errorMessage = profileErrorMessage,
                    onRetry = onRetryProfile,
                    onClick = onOpenAccount,
                )
            }
            item {
                SyncInsight(
                    title = sync.title,
                    message = sync.message,
                    severity = sync.severity,
                    pending = syncStatus.pendingCount > 0,
                )
            }
            item { Section("Hesap") }
            item {
                ProfileGroup {
                    ProfileRow(
                        title = "Kişisel Bilgiler",
                        subtitle = "Adını ve profilini düzenle",
                        icon = Icons.Default.Person,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenPersonalInfo,
                    )
                    ProfileRow(
                        title = "Ortak Alanlar",
                        subtitle = workspaceName,
                        icon = Icons.Default.Groups,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenSharedSpaces,
                        last = true,
                    )
                }
            }
            item { Section("Tercihler") }
            item {
                ProfileGroup {
                    ProfileRow(
                        title = "Görünüm",
                        subtitle = themeLabel,
                        icon = Icons.Default.Palette,
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenAppearance,
                    )
                    ProfileRow(
                        title = "Bildirimler",
                        subtitle = "Hatırlatmalar ve gizlilik",
                        icon = Icons.Default.Notifications,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenNotifications,
                    )
                    ProfileRow(
                        title = "Dil ve Bölge",
                        subtitle = languageRegionLabel,
                        icon = Icons.Default.Language,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenLanguageRegion,
                        last = true,
                    )
                }
            }
            item { Section("Güvenlik ve Veri") }
            item {
                ProfileGroup {
                    val lockText = if (biometricLockEnabled) "Açık · $autoLockLabel" else "Kapalı"
                    ProfileRow(
                        title = "Uygulama Kilidi",
                        subtitle = lockText,
                        icon = Icons.Default.Lock,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenSecurityPrivacy,
                    )
                    ProfileRow(
                        title = "Veri Yönetimi",
                        subtitle = "CSV dışa aktarma ve JSON yedek",
                        icon = Icons.Default.FolderCopy,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenDataManagement,
                    )
                    ProfileRow(
                        title = "Gizlilik",
                        subtitle = "Gizlilik ve hukuki bilgiler",
                        icon = Icons.Default.PrivacyTip,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onOpenLegalInfo,
                        last = true,
                    )
                }
            }
            item { Section("Destek") }
            item {
                ProfileGroup {
                    ProfileRow(
                        title = "Yardım ve Geri Bildirim",
                        subtitle = "Yardım merkezi ve iletişim",
                        icon = Icons.Default.HelpOutline,
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenHelpAbout,
                    )
                    ProfileRow(
                        title = "Uygulama sürümü",
                        subtitle = appVersion,
                        icon = Icons.Default.Info,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = null,
                        last = true,
                        showArrow = false,
                        comingSoon = false,
                    )
                }
            }
            signOutError?.let {
                item {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                TextButton(
                    onClick = { showSignOutConfirmation = true },
                    modifier = Modifier.fillMaxWidth().padding(top = FeniqoSpacing.Small),
                ) {
                    Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(FeniqoSpacing.Small))
                    Text("Çıkış Yap", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Çıkış yapılsın mı?") },
            text = { Text("Bu cihazdaki oturumunuz güvenle kapatılacak.") },
            confirmButton = {
                TextButton(onClick = { showSignOutConfirmation = false; onSignOut() }) {
                    Text("Çıkış Yap")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

@Composable
private fun ProfileSummary(
    name: String,
    email: String,
    workspace: String,
    loading: Boolean,
    isEmpty: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = Card(
    shape = RoundedCornerShape(FeniqoRadius.Large),
    colors = CardDefaults.cardColors(
        containerColor = if (errorMessage != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ),
    elevation = CardDefaults.cardElevation(4.dp),
    modifier = if (onClick != null && errorMessage == null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = "Hesap ve profil fotoğrafı",
            onClick = onClick,
        )
    } else {
        Modifier
    },
) {
    Row(
        Modifier.fillMaxWidth().padding(FeniqoSpacing.LargePlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initialLetter = when {
            loading -> ""
            errorMessage != null -> "!"
            name.isNotBlank() -> name.trim().first().uppercase()
            email.isNotBlank() -> email.trim().first().uppercase()
            else -> "?"
        }
        val avatarContainerColor = when {
            errorMessage != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        val avatarContentColor = when {
            errorMessage != null -> MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        Box(
            Modifier.size(64.dp).background(avatarContainerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialLetter,
                style = MaterialTheme.typography.headlineMedium,
                color = avatarContentColor,
            )
        }
        Spacer(Modifier.width(FeniqoSpacing.Medium))
        Column(Modifier.weight(1f)) {
            val titleText = when {
                loading -> "Profiliniz yükleniyor"
                errorMessage != null -> "Profil bilgileri yüklenemedi"
                name.isNotBlank() -> name
                email.isNotBlank() -> email
                else -> "Profil bilgisi bulunamadı"
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitleText = when {
                loading -> "Bilgileriniz getiriliyor..."
                errorMessage != null -> errorMessage
                isEmpty && email.isNotBlank() -> "$workspace · Profil kaydı oluşturulmadı"
                isEmpty -> "$workspace · Bilgi yok"
                name.isNotBlank() && email.isNotBlank() -> email
                else -> "Hesabım ve profil fotoğrafım"
            }
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(FeniqoSpacing.ExtraSmall))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(workspace) },
                leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(16.dp)) },
            )
        }
        if (errorMessage != null && onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("Tekrar Dene")
            }
        } else if (onClick != null && !loading) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Hesap detaylarını aç",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncInsight(
    title: String,
    message: String,
    severity: SyncDisplaySeverity,
    pending: Boolean,
) {
    val (containerColor, icon, iconTint) = when (severity) {
        SyncDisplaySeverity.CONFLICT -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Warning,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SyncDisplaySeverity.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.ErrorOutline,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SyncDisplaySeverity.OFFLINE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SyncDisplaySeverity.CHECKING,
        SyncDisplaySeverity.SYNCING,
        SyncDisplaySeverity.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Default.Sync,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SyncDisplaySeverity.UP_TO_DATE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.Verified,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(Modifier.fillMaxWidth().padding(FeniqoSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .55f),
                    RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint)
            }
            Spacer(Modifier.width(FeniqoSpacing.Medium))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(title: String) = Text(
    title,
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(top = FeniqoSpacing.Small),
)

@Composable
private fun ProfileGroup(content: @Composable ColumnScope.() -> Unit) = Card(
    shape = RoundedCornerShape(FeniqoRadius.Large),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    elevation = CardDefaults.cardElevation(2.dp),
) { Column(content = content) }

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: (() -> Unit)?,
    last: Boolean = false,
    showArrow: Boolean = true,
    comingSoon: Boolean = onClick == null,
) {
    val enabled = onClick != null
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.semantics { disabled() },
            )
            .alpha(if (enabled || !comingSoon) 1f else .58f)
            .padding(FeniqoSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.width(FeniqoSpacing.Medium))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (comingSoon) {
                    Spacer(Modifier.width(FeniqoSpacing.Small))
                    Text(
                        "Yakında",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled && showArrow) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "$title aç")
        }
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
