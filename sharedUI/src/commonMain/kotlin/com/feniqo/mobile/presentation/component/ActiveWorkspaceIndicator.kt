package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.theme.FeniqoEmerald
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.PhoenixGold

/**
 * Aktif çalışma alanı bağlamını (Kişisel Alan veya paylaşılan Ortak Alan)
 * kullanıcıya açık, erişilebilir ve tutarlı biçimde gösteren stateless bileşen.
 *
 * @param workspaceName Aktif çalışma alanı adı. Null veya boş ise "Kişisel Alan" gösterilir.
 * @param isCompact Kompakt satır içi gösterim modu.
 * @param onClick İsteğe bağlı tıklama eylemi (örneğin çalışma alanı seçiciye geçiş).
 */
@Composable
fun ActiveWorkspaceIndicator(
    workspaceName: String?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isShared = !workspaceName.isNullOrBlank()
    val displayName = if (isShared) workspaceName!!.trim() else "Kişisel Alan"
    val accessibilityLabel = "Aktif çalışma alanı: $displayName"

    val indicatorColor: Color = if (isShared) {
        PhoenixGold
    } else {
        FeniqoEmerald
    }

    val containerColor: Color = if (isShared) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }

    val contentColor: Color = if (isShared) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = RoundedCornerShape(if (isCompact) FeniqoRadius.Small else FeniqoRadius.Medium)
    val horizontalPadding = if (isCompact) FeniqoSpacing.Small else FeniqoSpacing.Medium
    val verticalPadding = if (isCompact) 4.dp else FeniqoSpacing.ExtraSmall
    val dotSize = if (isCompact) 6.dp else 8.dp

    val clickModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                if (onClick != null) {
                    role = Role.Button
                }
            }
            .then(clickModifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isCompact) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(color = indicatorColor, shape = CircleShape),
            )
            Spacer(modifier = Modifier.width(if (isCompact) 6.dp else FeniqoSpacing.Small))
            Text(
                text = displayName,
                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = if (isShared) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
