package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.goal.GoalDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.util.ColorParser

@Composable
fun GoalCard(item: GoalDisplayModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val semanticColor = ColorParser.parseHexColorOrNull(item.colorHex) ?: MaterialTheme.colorScheme.primary
    val statusText = if (item.isAchieved) "Tamamlandı" else "Aktif"
    Card(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = "${item.name}. ${item.formattedCurrentAmount} birikmiş, hedef ${item.formattedTargetAmount}, yüzde ${item.progressBasisPoints.value / 100}, $statusText"
        },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(FeniqoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalTonalIcon(item, semanticColor)
                Spacer(Modifier.width(FeniqoSpacing.Medium))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.formattedTargetDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(FeniqoSpacing.Small))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                    GoalStatusBadge(item.status)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Text("${item.formattedCurrentAmount} / ${item.formattedTargetAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).semantics {
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(item.progressFraction, 0f..1f)
                    contentDescription = "${item.name} ilerlemesi: ${item.formattedCurrentAmount} / ${item.formattedTargetAmount}, yüzde ${item.progressBasisPoints.value / 100}"
                },
                color = if (item.isAchieved) FeniqoStatusColor.Success else semanticColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("%${item.progressBasisPoints.value / 100}", style = MaterialTheme.typography.labelLarge, color = if (item.isAchieved) FeniqoStatusColor.Success else semanticColor)
                when {
                    item.isTargetDatePast -> Text("Hedef tarihi geçti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.isAchieved -> Text("Hedefe ulaşıldı", style = MaterialTheme.typography.labelMedium, color = FeniqoStatusColor.Success)
                    else -> Text("${item.formattedRemainingAmount} kaldı", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun GoalTonalIcon(item: GoalDisplayModel, color: Color) {
    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(FeniqoRadius.Small), color = color.copy(alpha = .16f), contentColor = color) {
        Box(contentAlignment = Alignment.Center) {
            val emoji = CategoryIconResolver.resolveIconEmojiOrNull(item.iconKey)
            Text(emoji ?: "◎", fontSize = if (emoji == null) 24.sp else 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GoalStatusBadge(status: GoalStatus) {
    val achieved = status == GoalStatus.ACHIEVED
    Surface(shape = RoundedCornerShape(FeniqoRadius.Small), color = if (achieved) FeniqoStatusColor.Success.copy(alpha = .16f) else MaterialTheme.colorScheme.primaryContainer) {
        Text(if (achieved) "Tamamlandı" else "Aktif", modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (achieved) FeniqoStatusColor.Success else MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
fun CreateGoalActionCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Yeni hedef oluştur" },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = FeniqoTouchTarget.Minimum).padding(FeniqoSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, contentDescription = null) } }
            Spacer(Modifier.width(FeniqoSpacing.Medium))
            Column(Modifier.weight(1f)) {
                Text("Yeni hedef oluştur", style = MaterialTheme.typography.titleMedium)
                Text("Hayallerini somut bir plana dönüştür.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GoalDeleteDialog(isSubmitting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(onDismissRequest = { if (!isSubmitting) onDismiss() }, modifier = modifier, title = { Text("Hedefi Sil") }, text = { Text("Bu birikim hedefini silmek istediğinizden emin misiniz?") }, confirmButton = { Button(onClick = onConfirm, enabled = !isSubmitting, modifier = Modifier.defaultMinSize(minHeight = FeniqoTouchTarget.Minimum), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sil") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.defaultMinSize(minHeight = FeniqoTouchTarget.Minimum)) { Text("Vazgeç") } })
}
