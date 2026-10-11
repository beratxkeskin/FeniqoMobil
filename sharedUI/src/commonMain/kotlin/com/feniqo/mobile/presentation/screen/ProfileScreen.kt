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
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.localization.localizedLanguageRegionSummary
import com.feniqo.mobile.presentation.profile.ProfileSignOutError
import com.feniqo.mobile.presentation.profile.ProfileUiError
import com.feniqo.mobile.presentation.sync.SyncDisplaySeverity
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import com.feniqo.mobile.presentation.sync.resolveSyncDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileScreen(
    displayName: String,
    email: String,
    workspaceName: String?,
    isProfileLoading: Boolean,
    signOutError: ProfileSignOutError?,
    syncStatus: SyncStatusUiState,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenSharedSpaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isProfileEmpty: Boolean = false,
    profileError: ProfileUiError? = null,
    onRetryProfile: (() -> Unit)? = null,
    language: AppLanguage? = null,
    currency: Currency? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val sync = resolveSyncDisplayModel(syncStatus)
    val effectiveWorkspaceName = workspaceName?.trim()?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.profile_personal_space_fallback)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.profile_back_content_description),
                        )
                    }
                    Column {
                        Text(stringResource(Res.string.profile_title), style = FeniqoTypographyTokens.DisplayTitle)
                        Text(
                            stringResource(Res.string.profile_description),
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
                    workspace = effectiveWorkspaceName,
                    loading = isProfileLoading,
                    isEmpty = isProfileEmpty,
                    profileError = profileError,
                    onRetry = onRetryProfile,
                    onClick = onOpenAccount,
                    language = language,
                    currency = currency,
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
            item { Section(stringResource(Res.string.profile_section_account)) }
            item {
                ProfileGroup {
                    ProfileRow(
                        title = stringResource(Res.string.profile_personal_info_title),
                        subtitle = stringResource(Res.string.profile_personal_info_subtitle),
                        icon = Icons.Default.Person,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenPersonalInfo,
                    )
                    ProfileRow(
                        title = stringResource(Res.string.profile_shared_spaces_title),
                        subtitle = effectiveWorkspaceName,
                        icon = Icons.Default.Groups,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenSharedSpaces,
                        last = true,
                    )
                }
            }
            item { Section(stringResource(Res.string.profile_section_app)) }
            item {
                ProfileGroup {
                    ProfileRow(
                        title = stringResource(Res.string.profile_app_settings_title),
                        subtitle = stringResource(Res.string.profile_app_settings_subtitle),
                        icon = Icons.Default.Settings,
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenSettings,
                        last = true,
                    )
                }
            }
            signOutError?.let {
                item {
                    Text(
                        stringResource(Res.string.profile_error_sign_out_message),
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
                    Text(stringResource(Res.string.profile_sign_out_action), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showSignOutConfirmation) {
        SignOutConfirmDialog(
            pendingChangesCount = syncStatus.pendingCount,
            onDismiss = { showSignOutConfirmation = false },
            onNavigateToSync = {
                showSignOutConfirmation = false
                onOpenDataManagement()
            },
            onConfirmSignOut = {
                showSignOutConfirmation = false
                onSignOut()
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
    profileError: ProfileUiError? = null,
    onRetry: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    language: AppLanguage? = null,
    currency: Currency? = null,
) = Card(
    shape = RoundedCornerShape(FeniqoRadius.Large),
    colors = CardDefaults.cardColors(
        containerColor = if (profileError != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ),
    elevation = CardDefaults.cardElevation(4.dp),
    modifier = if (onClick != null && profileError == null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(Res.string.profile_account_and_photo_semantics),
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
            profileError != null -> "!"
            name.isNotBlank() -> name.trim().first().uppercase()
            email.isNotBlank() -> email.trim().first().uppercase()
            else -> "?"
        }
        val avatarContainerColor = when {
            profileError != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        val avatarContentColor = when {
            profileError != null -> MaterialTheme.colorScheme.onError
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
                loading -> stringResource(Res.string.profile_loading_title)
                profileError != null -> stringResource(Res.string.profile_error_load_title)
                name.isNotBlank() -> name
                email.isNotBlank() -> email
                else -> stringResource(Res.string.profile_not_found_title)
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitleText = when {
                loading -> stringResource(Res.string.profile_loading_subtitle)
                profileError != null -> stringResource(Res.string.profile_error_load_message)
                isEmpty && email.isNotBlank() -> stringResource(Res.string.profile_empty_no_record, workspace)
                isEmpty -> stringResource(Res.string.profile_empty_no_info, workspace)
                name.isNotBlank() && email.isNotBlank() -> email
                else -> stringResource(Res.string.profile_fallback_subtitle)
            }
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (profileError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(FeniqoSpacing.ExtraSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(workspace) },
                    leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(16.dp)) },
                )
                if (language != null && currency != null) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(localizedLanguageRegionSummary(language, currency.code)) },
                    )
                }
            }
        }
        if (profileError != null && onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.profile_retry_action))
            }
        } else if (onClick != null && !loading) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.profile_open_account_details_content_description),
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
                        stringResource(Res.string.profile_badge_coming_soon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled && showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.profile_open_row_semantics, title),
            )
        }
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
