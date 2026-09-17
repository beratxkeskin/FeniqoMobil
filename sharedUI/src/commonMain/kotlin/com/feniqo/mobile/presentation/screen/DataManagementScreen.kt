package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

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
                    title = "Veri yönetimi",
                    onBack = onBack,
                )
            }

            // DIŞA AKTARMA
            item {
                Text(
                    text = "DIŞA AKTARMA",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "İşlemleri CSV olarak kaydet",
                        subtitle = "Aktif alandaki işlemleri CSV dosyasına kaydeder.",
                        icon = Icons.Outlined.FileDownload,
                        showDivider = false,
                        onClick = onExportCsv,
                    )
                }
            }

            // YEDEKLEME
            item {
                Text(
                    text = "YEDEKLEME",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "JSON yedeği oluştur",
                        subtitle = "Kişisel verilerini cihazına kaydet.",
                        icon = Icons.Outlined.SaveAlt,
                        onClick = { showScopeSheet = true },
                    )
                    SettingsRowItem(
                        title = "JSON yedeğini içe aktar",
                        subtitle = "Daha önce oluşturduğun yedek dosyasını yükle.",
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
                        text = "Yedek kapsamı işlem öncesinde gösterilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // SENKRONİZASYON
            item {
                Text(
                    text = "SENKRONİZASYON",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Senkronizasyon durumu",
                        subtitle = "Mevcut durumu görüntüle.",
                        icon = Icons.Outlined.CloudSync,
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
                        text = "Yedek dosyası ile senkronizasyon farklı işlemlerdir.",
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
                    text = "JSON yedeği",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Kişisel alandaki kategori ve işlemler dahil edilir.",
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
                            text = "$categoryScopeCount kategori",
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
                            text = "$transactionScopeCount işlem",
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
                        text = "Makbuz dosyaları dahil değildir.",
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
                    Text("Dosyaya kaydet", fontWeight = FontWeight.SemiBold)
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
                    text = "${backup.categoryCount} kategori, ${backup.transactionCount} işlem",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = "Kişisel alana yeni kayıtlar eklenecek. Mevcut kayıtlar değiştirilmeyecek.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmImport,
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    Text("İçe aktar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissImport) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

data class PendingBackupInfo(
    val categoryCount: Int,
    val transactionCount: Int,
)
