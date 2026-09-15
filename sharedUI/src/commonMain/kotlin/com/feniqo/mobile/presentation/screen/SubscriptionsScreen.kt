package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.validation.SubscriptionFilter
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SubscriptionActualSpendingCard
import com.feniqo.mobile.presentation.component.SubscriptionAddActionCard
import com.feniqo.mobile.presentation.component.SubscriptionCard
import com.feniqo.mobile.presentation.component.SubscriptionEmptyState
import com.feniqo.mobile.presentation.component.SubscriptionErrorCard
import com.feniqo.mobile.presentation.component.SubscriptionFilterRow
import com.feniqo.mobile.presentation.component.SubscriptionHeroCard
import com.feniqo.mobile.presentation.component.SubscriptionInsightCard
import com.feniqo.mobile.presentation.component.SubscriptionOverdueSection
import com.feniqo.mobile.presentation.component.SubscriptionUpcomingSection
import com.feniqo.mobile.presentation.subscription.SubscriptionsUiState
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens

private val FeniqoBackgroundSand = Color(0xFFF7F5F0)
private val FeniqoSageGreen = Color(0xFF2D5A43)

/**
 * Görsel 1: Abonelikler Liste Ekranı (Onaylı Tasarım).
 * Durumsuz (stateless) Compose ekranıdır. Room Single Source of Truth verilerini sunar.
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    onRetry: () -> Unit,
    onAddSubscription: () -> Unit,
    onSubscriptionClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onFilterSelected: (SubscriptionFilter) -> Unit = {},
    bannerContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoBackgroundSand,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Üst Bar: Geri Butonu (varsa "Daha Fazla")
            if (onBack != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri dön",
                        tint = Color(0xFF212121),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Daha Fazla",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF212121),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. Ekran Başlığı ve Aktif Çalışma Alanı Hapı
            Text(
                text = "Abonelikler",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E232A),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Çalışma alanı hapı (Kişisel)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = state.activeWorkspaceName ?: "Kişisel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )
                }
            }

            // 3. İsteğe Bağlı Bildirim Banner'ı
            if (bannerContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                bannerContent()
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Ana Liste / İçerik Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> {
                        LoadingContent(
                            message = "Abonelikler yükleniyor...",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.observationError != null -> {
                        SubscriptionErrorCard(
                            message = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.isEmpty -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            SubscriptionEmptyState(onAddSubscription = onAddSubscription)
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            // a) Koyu Grafit Hero Özet Kartı
                            item(key = "hero_card") {
                                SubscriptionHeroCard(
                                    estimatedSummaries = state.estimatedSummaries,
                                    totalActiveCount = state.totalActiveCount,
                                    upcomingCount = state.upcomingCount,
                                )
                            }

                            // b) Bu Ay Kaydedilen Ödemeler Kartı
                            item(key = "actual_spending_card") {
                                SubscriptionActualSpendingCard(
                                    actualSpendings = state.actualSpendings,
                                )
                            }

                            // c) Yatay Filtre Çipleri
                            item(key = "filter_chips") {
                                SubscriptionFilterRow(
                                    selectedFilter = state.selectedFilter,
                                    onFilterSelected = onFilterSelected,
                                )
                            }

                            // d) Filtrelenmiş Abonelik Kartları
                            if (state.filteredItems.isEmpty()) {
                                item(key = "empty_filter_state") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "Bu filtreye uygun abonelik bulunamadı.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF757575),
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = state.filteredItems,
                                    key = { it.id.value },
                                ) { item ->
                                    SubscriptionCard(
                                        item = item,
                                        onClick = { onSubscriptionClick(item.id) },
                                    )
                                }
                            }

                            // e) Gerçek Veriye Dayalı İçgörüler (Varsa)
                            if (state.insights.isNotEmpty()) {
                                items(
                                    items = state.insights,
                                    key = { it.id },
                                ) { insight ->
                                    SubscriptionInsightCard(insight = insight)
                                }
                            }

                            // f) Alt Ekle Butonu ve Dipnot
                            item(key = "bottom_add_action") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = onAddSubscription,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FeniqoSageGreen,
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Abonelik ekle",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Tutarlar kayıtlarına göre hesaplanır.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

