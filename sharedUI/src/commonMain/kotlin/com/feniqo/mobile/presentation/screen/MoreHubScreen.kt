package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.hub.HubMenuItem
import com.feniqo.mobile.presentation.hub.MoreHubOverviewCardState
import com.feniqo.mobile.presentation.hub.MoreHubRegistry
import com.feniqo.mobile.presentation.hub.MoreHubUiState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens

/** Bottom navigation dışında kalan finansal özellik ve yönlendirme merkezi. */
@Composable
fun MoreHubScreen(
    state: MoreHubUiState,
    onNavigateToAssets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToRecurringTransactions: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToSharedSpaces: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = mapOf(
        MoreHubRegistry.ASSETS.id to onNavigateToAssets,
        MoreHubRegistry.GOALS.id to onNavigateToGoals,
        MoreHubRegistry.DEBTS.id to onNavigateToDebts,
        MoreHubRegistry.SUBSCRIPTIONS.id to onNavigateToSubscriptions,
        MoreHubRegistry.RECURRING_TRANSACTIONS.id to onNavigateToRecurringTransactions,
        MoreHubRegistry.CATEGORIES.id to onNavigateToCategories,
        MoreHubRegistry.SHARED_SPACES.id to onNavigateToSharedSpaces,
        MoreHubRegistry.SETTINGS.id to onNavigateToSettings,
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item("header") { MoreHubHeader(state.activeWorkspaceName, onNavigateToProfile) }
            item("message") { MoreHubMessageCard() }
            item("overview_header") { MoreHubSectionHeader("Genel Bakış") }
            item("overview") {
                OverviewGrid(listOf(
                    OverviewCard("Varlıklar", "assets", state.assets, onNavigateToAssets),
                    OverviewCard("Hedefler", "goals", state.goals, onNavigateToGoals),
                    OverviewCard("Abonelikler", "subscriptions", state.subscriptions, onNavigateToSubscriptions),
                    OverviewCard("Tekrarlayan İşlemler", "recurring_transactions", state.recurringTransactions, onNavigateToRecurringTransactions),
                ))
            }
            item("wealth_header") { MoreHubSectionHeader("Varlık Yönetimi", "Birikimlerini ve yükümlülüklerini tek yerde takip et.") }
            item("wealth") { HubGrid(MoreHubRegistry.wealthItems, actions) }
            item("money_header") { MoreHubSectionHeader("Para Yönetimi", "Düzenli harcamalarını ve kategorilerini yönet.") }
            item("money") { HubGrid(MoreHubRegistry.moneyManagementItems, actions) }
            item("shared_header") { MoreHubSectionHeader("Ortak Kullanım", "Paranı birlikte planla ve takip et.") }
            item("shared") { FullWidthHubCard(MoreHubRegistry.SHARED_SPACES, actions[MoreHubRegistry.SHARED_SPACES.id]) }
            item("insights_header") { MoreHubSectionHeader("İçgörüler", "Finansal yolculuğunu daha iyi anla.") }
            item("insights") { FullWidthHubCard(MoreHubRegistry.REPORTS, null) }
            item("settings_header") { MoreHubSectionHeader("Ayarlar", "Uygulama deneyimini kendi ihtiyaçlarına göre düzenle.") }
            item("settings") { FullWidthHubCard(MoreHubRegistry.SETTINGS, actions[MoreHubRegistry.SETTINGS.id]) }
            item("bottom_spacer") { Spacer(Modifier.height(FeniqoSpacing.Screen)) }
        }
    }
}

@Composable
private fun MoreHubHeader(workspaceName: String?, onNavigateToProfile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                Text("Daha Fazla", style = FeniqoTypographyTokens.DisplayTitle, color = MaterialTheme.colorScheme.onBackground)
                Text("Finansal hayatının önemli alanlarına buradan ulaş.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                modifier = Modifier.size(FeniqoTouchTarget.Minimum).clickable(role = Role.Button, onClick = onNavigateToProfile).semantics { contentDescription = "Profil ve hesap" },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Box(contentAlignment = Alignment.Center) { NeutralProfileIcon() } }
        }
        ActiveWorkspaceIndicator(workspaceName = workspaceName, isCompact = true)
    }
}

@Composable
private fun MoreHubMessageCard() {
    Card(shape = RoundedCornerShape(FeniqoRadius.Large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(FeniqoSpacing.Large)) {
            Column(modifier = Modifier.padding(end = 72.dp), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
                Text("Küçük adımlar, net bir yarın.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Finansal farkındalığını her gün biraz daha güçlendir.", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LeafDecoration(Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun MoreHubSectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

private data class OverviewCard(val title: String, val iconId: String, val state: MoreHubOverviewCardState, val onClick: () -> Unit)

@Composable
private fun OverviewGrid(cards: List<OverviewCard>) = ResponsiveRows(cards) { OverviewSummaryCard(it) }

@Composable
private fun HubGrid(items: List<HubMenuItem>, actions: Map<String, () -> Unit>) = ResponsiveRows(items) { CompactHubCard(it, actions[it.id]) }

@Composable
private fun <T> ResponsiveRows(items: List<T>, content: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = responsiveColumnCount(maxWidth, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
            items.chunked(columns).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small), verticalAlignment = Alignment.Top) {
                    rowItems.forEach { item -> Box(modifier = Modifier.weight(1f)) { content(item) } }
                    repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun responsiveColumnCount(availableWidth: Dp, fontScale: Float): Int {
    val scaledMinimumCardWidth = MinimumCardWidth * fontScale
    val twoColumnCardWidth = (availableWidth - FeniqoSpacing.Small) / 2
    val threeColumnCardWidth = (availableWidth - (FeniqoSpacing.Small * 2)) / 3
    return when {
        availableWidth >= TabletMinimumWidth && threeColumnCardWidth >= scaledMinimumCardWidth -> 3
        twoColumnCardWidth >= scaledMinimumCardWidth -> 2
        else -> 1
    }
}

private val MinimumCardWidth = 152.dp
private val TabletMinimumWidth = 600.dp

@Composable
private fun OverviewSummaryCard(card: OverviewCard) {
    Card(modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 132.dp).clickable(role = Role.Button, onClick = card.onClick), shape = RoundedCornerShape(FeniqoRadius.Medium), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(FeniqoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(FeniqoRadius.Small), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { FinancialHubIcon(card.iconId, card.title) } }
                Text(card.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when (val cardState = card.state) {
                MoreHubOverviewCardState.Loading -> Text("Yükleniyor…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is MoreHubOverviewCardState.Content -> {
                    Text(cardState.primaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    cardState.secondaryText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                is MoreHubOverviewCardState.Empty -> Text(cardState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                is MoreHubOverviewCardState.Error -> Text(cardState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CompactHubCard(item: HubMenuItem, onClick: (() -> Unit)?) {
    val enabled = item.isAvailable && onClick != null
    Card(modifier = Modifier.fillMaxWidth().sizeIn(minHeight = FeniqoTouchTarget.Minimum).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = requireNotNull(onClick)) else Modifier.alpha(.64f).semantics { disabled() }), shape = RoundedCornerShape(FeniqoRadius.Medium), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(FeniqoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(FeniqoRadius.Small), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { FinancialHubIcon(item.id, item.title) } }
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FullWidthHubCard(item: HubMenuItem, onClick: (() -> Unit)?) {
    val enabled = item.isAvailable && onClick != null
    Card(modifier = Modifier.fillMaxWidth().sizeIn(minHeight = FeniqoTouchTarget.Minimum).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = requireNotNull(onClick)) else Modifier.alpha(.64f).semantics { disabled() }), shape = RoundedCornerShape(FeniqoRadius.Medium), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(FeniqoSpacing.Medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
            Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(FeniqoRadius.Small), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { FinancialHubIcon(item.id, item.title) } }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!item.isAvailable) Surface(shape = RoundedCornerShape(FeniqoRadius.Small), color = MaterialTheme.colorScheme.surfaceVariant) { Text("Yakında", modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                }
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (enabled) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeutralProfileIcon() {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = size.minDimension * .16f, center = Offset(size.width * .5f, size.height * .34f), style = stroke)
        drawArc(color, 200f, 140f, false, Offset(size.width * .2f, size.height * .38f), Size(size.width * .6f, size.height * .5f), style = stroke)
    }
}

@Composable
private fun LeafDecoration(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .55f)
    Canvas(modifier.size(64.dp, 54.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * .12f, size.height * .9f), Offset(size.width * .75f, size.height * .12f), stroke.width, StrokeCap.Round)
        drawOval(color, Offset(size.width * .28f, size.height * .38f), Size(size.width * .28f, size.height * .16f), style = stroke)
        drawOval(color, Offset(size.width * .52f, size.height * .16f), Size(size.width * .3f, size.height * .18f), style = stroke)
        drawOval(color, Offset(size.width * .06f, size.height * .6f), Size(size.width * .28f, size.height * .16f), style = stroke)
    }
}

@Composable
private fun FinancialHubIcon(id: String, title: String) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "$title simgesi" }) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (id) {
            "goals" -> { drawCircle(color, size.minDimension * .38f, style = stroke); drawCircle(color, size.minDimension * .18f, style = stroke); drawCircle(color, size.minDimension * .04f) }
            "shared_spaces" -> { drawCircle(color, size.minDimension * .13f, Offset(size.width * .35f, size.height * .35f), style = stroke); drawCircle(color, size.minDimension * .13f, Offset(size.width * .67f, size.height * .39f), style = stroke); drawLine(color, Offset(size.width * .16f, size.height * .78f), Offset(size.width * .84f, size.height * .78f), stroke.width, StrokeCap.Round) }
            "reports" -> { drawLine(color, Offset(size.width * .2f, size.height * .8f), Offset(size.width * .2f, size.height * .55f), stroke.width, StrokeCap.Round); drawLine(color, Offset(size.width * .5f, size.height * .8f), Offset(size.width * .5f, size.height * .3f), stroke.width, StrokeCap.Round); drawLine(color, Offset(size.width * .8f, size.height * .8f), Offset(size.width * .8f, size.height * .18f), stroke.width, StrokeCap.Round) }
            "categories" -> { val path = Path().apply { moveTo(size.width*.18f,size.height*.25f); lineTo(size.width*.58f,size.height*.25f); lineTo(size.width*.84f,size.height*.5f); lineTo(size.width*.5f,size.height*.84f); lineTo(size.width*.18f,size.height*.52f); close() }; drawPath(path, color, style = stroke) }
            "recurring_transactions" -> { drawCircle(color, size.minDimension * .31f, style = stroke); drawLine(color, Offset(size.width * .62f, size.height * .16f), Offset(size.width * .82f, size.height * .2f), stroke.width, StrokeCap.Round) }
            "settings" -> { drawCircle(color, size.minDimension * .24f, style = stroke); drawLine(color, Offset(size.width * .5f, size.height * .06f), Offset(size.width * .5f, size.height * .18f), stroke.width, StrokeCap.Round); drawLine(color, Offset(size.width * .5f, size.height * .82f), Offset(size.width * .5f, size.height * .94f), stroke.width, StrokeCap.Round) }
            else -> { drawRoundRect(color, Offset(size.width*.16f,size.height*.24f), Size(size.width*.68f,size.height*.56f), style = stroke); drawLine(color, Offset(size.width*.3f,size.height*.42f), Offset(size.width*.7f,size.height*.42f), stroke.width, StrokeCap.Round) }
        }
    }
}
