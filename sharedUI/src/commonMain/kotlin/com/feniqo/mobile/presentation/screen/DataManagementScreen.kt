package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.settings.formatLocalizedBackupCounts
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.backup_category_count_plural
import feniqomobil.sharedui.generated.resources.backup_transaction_count_plural
import feniqomobil.sharedui.generated.resources.data_management_backup_create_subtitle
import feniqomobil.sharedui.generated.resources.data_management_backup_create_title
import feniqomobil.sharedui.generated.resources.data_management_backup_import_subtitle
import feniqomobil.sharedui.generated.resources.data_management_backup_import_title
import feniqomobil.sharedui.generated.resources.data_management_backup_info
import feniqomobil.sharedui.generated.resources.data_management_export_csv_subtitle
import feniqomobil.sharedui.generated.resources.data_management_export_csv_title
import feniqomobil.sharedui.generated.resources.data_management_import_confirm_action
import feniqomobil.sharedui.generated.resources.data_management_import_confirm_body
import feniqomobil.sharedui.generated.resources.data_management_import_dismiss_action
import feniqomobil.sharedui.generated.resources.data_management_scope_receipt_note
import feniqomobil.sharedui.generated.resources.data_management_scope_save_button
import feniqomobil.sharedui.generated.resources.data_management_scope_subtitle
import feniqomobil.sharedui.generated.resources.data_management_scope_title
import feniqomobil.sharedui.generated.resources.data_management_section_backup
import feniqomobil.sharedui.generated.resources.data_management_section_export
import feniqomobil.sharedui.generated.resources.data_management_section_sync
import feniqomobil.sharedui.generated.resources.data_management_sync_info
import feniqomobil.sharedui.generated.resources.data_management_sync_status_subtitle
import feniqomobil.sharedui.generated.resources.data_management_sync_status_title
import feniqomobil.sharedui.generated.resources.data_management_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 10 Veri Yönetimi Ekranı, 22 Yedek Kapsamı ve 23 İçe Aktarma Onayı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    categoryScopeCount: Int,
    transactionScopeCount: Int,
    pendingImport: PendingBackupInfo?,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJsonBackup: () -> Unit,
    onSelectBackupFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
    onNavigateToSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showScopeSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Screen,
                vertical = FeniqoSpacing.Medium,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
        ) {
            item {
                SettingsTopBar(
                    title = stringResource(Res.string.data_management_title),
                    onBack = onBack,
                )
            }

            // DIŞA AKTARMA
            item {
                Text(
                    text = stringResource(Res.string.data_management_section_export),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.data_management_export_csv_title),
                        subtitle = stringResource(Res.string.data_management_export_csv_subtitle),
                        icon = Icons.Outlined.FileDownload,
                        showDivider = false,
                        onClick = onExportCsv,
                    )
                }
            }

            // YEDEKLEME
            item {
                Text(
                    text = stringResource(Res.string.data_management_section_backup),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.data_management_backup_create_title),
                        subtitle = stringResource(Res.string.data_management_backup_create_subtitle),
                        icon = Icons.Outlined.SaveAlt,
                        onClick = { showScopeSheet = true },
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.data_management_backup_import_title),
                        subtitle = stringResource(Res.string.data_management_backup_import_subtitle),
                        icon = Icons.Outlined.FileUpload,
                        showDivider = false,
                        onClick = onSelectBackupFile,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.data_management_backup_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // SENKRONİZASYON
            item {
                Text(
                    text = stringResource(Res.string.data_management_section_sync),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.data_management_sync_status_title),
                        subtitle = stringResource(Res.string.data_management_sync_status_subtitle),
                        icon = Icons.Outlined.Sync,
                        showDivider = false,
                        onClick = onNavigateToSyncStatus,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.data_management_sync_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 22 Yedek Kapsamı Modal Bottom Sheet
    if (showScopeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScopeSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Text(
                    text = stringResource(Res.string.data_management_scope_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(Res.string.data_management_scope_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsGroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, null, tint = FeniqoSageGreen)
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                        Text(
                            text = pluralStringResource(
                                Res.plurals.backup_category_count_plural,
                                categoryScopeCount,
                                categoryScopeCount,
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                            colors = CheckboxDefaults.colors(checkedColor = FeniqoSageGreen),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, null, tint = FeniqoSageGreen)
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                        Text(
                            text = pluralStringResource(
                                Res.plurals.backup_transaction_count_plural,
                                transactionScopeCount,
                                transactionScopeCount,
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                            colors = CheckboxDefaults.colors(checkedColor = FeniqoSageGreen),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.data_management_scope_receipt_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = {
                        showScopeSheet = false
                        onExportJsonBackup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text(
                        text = stringResource(Res.string.data_management_scope_save_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }

    // 23 İçe Aktarma Onayı Diyaloğu
    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = onDismissImport,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(FeniqoSageGreen.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                    )
                }
            },
            title = {
                Text(
                    text = formatLocalizedBackupCounts(backup.categoryCount, backup.transactionCount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.data_management_import_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmImport,
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    Text(stringResource(Res.string.data_management_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissImport) {
                    Text(stringResource(Res.string.data_management_import_dismiss_action))
                }
            },
        )
    }
}

data class PendingBackupInfo(
    val categoryCount: Int,
    val transactionCount: Int,
)
