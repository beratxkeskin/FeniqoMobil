package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.feniqo.mobile.presentation.hub.MoreHubRegistry
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Profil sekmesinin hesap ve finans araçları erişim ekranıdır.
 * Kategoriler ve Ayarlar modüllerine geçiş sağlar.
 * Gelecek modüller (Varlıklar, Raporlar, Ortak Alanlar, Bankalar, Bildirimler) pasif bilgi kartı olarak gösterilir.
 */
@Composable
fun MoreHubScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSharedSpaces: () -> Unit = {},
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
                        text = "Profil",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = "Hesabını, tercihlerini ve finansal araçlarını yönet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = MoreHubRegistry.items,
                key = { it.id },
            ) { item ->
                HubMenuItemCard(
                    item = item,
                    onClick = when (item.id) {
                        MoreHubRegistry.CATEGORIES.id -> onNavigateToCategories
                        MoreHubRegistry.SETTINGS.id -> onNavigateToSettings
                        MoreHubRegistry.SHARED_SPACES.id -> onNavigateToSharedSpaces
                        else -> null
                    },
                )
            }
        }
    }
}
