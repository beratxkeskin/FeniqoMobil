package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.hub.HubMenuItem
import com.feniqo.mobile.presentation.hub.PlanHubRegistry
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Plan sekmesinin ana erişim merkezi (Hub) ekranıdır.
 * Bütçeler, Tekrarlayan İşlemler ve Abonelikler modüllerine geçiş sağlar.
 * Gelecek özellikler (Hedefler, Borç/Alacak) pasif bilgi kartı olarak gösterilir.
 */
@Composable
fun PlanHubScreen(
    onNavigateToBudgets: () -> Unit,
    onNavigateToRecurringTransactions: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToDebts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Large,
                vertical = FeniqoSpacing.Large,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = FeniqoSpacing.Small),
                ) {
                    Text(
                        text = "Plan",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = "Bütçe, hedef ve otomatik ödeme planlarını yönet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = PlanHubRegistry.items,
                key = { it.id },
            ) { item ->
                HubMenuItemCard(
                    item = item,
                    onClick = when (item.id) {
                        PlanHubRegistry.BUDGETS.id -> onNavigateToBudgets
                        PlanHubRegistry.RECURRING_TRANSACTIONS.id -> onNavigateToRecurringTransactions
                        PlanHubRegistry.SUBSCRIPTIONS.id -> onNavigateToSubscriptions
                        PlanHubRegistry.GOALS.id -> onNavigateToGoals
                        PlanHubRegistry.DEBTS.id -> onNavigateToDebts
                        else -> null
                    },
                )
            }
        }
    }
}


@Composable
internal fun HubMenuItemCard(
    item: HubMenuItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isClickable = item.isAvailable && onClick != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (item.isAvailable && onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier.alpha(0.6f)
                },
            ),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Surface(
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = if (item.isAvailable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.iconSymbol,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!item.isAvailable) {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "Yakında",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.isAvailable) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
