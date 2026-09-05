package com.feniqo.mobile.presentation.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.SubscriptionCard
import com.feniqo.mobile.presentation.subscription.SubscriptionsUiState
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Abonelikler liste ekranının durumsuz (stateless) Compose sunumudur.
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    onRetry: () -> Unit,
    onAddSubscription: () -> Unit,
    onSubscriptionClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    bannerContent: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!state.isLoading && state.observationError == null) {
                FloatingActionButton(
                    onClick = onAddSubscription,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics {
                        contentDescription = "Yeni abonelik ekle"
                    },
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Abonelikler",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (bannerContent != null) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                bannerContent()
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Ana İçerik Alanı
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
                        ErrorState(
                            title = "Abonelikler Yüklenemedi",
                            description = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.isEmpty -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                        ) {
                            EmptyState(
                                title = "Henüz abonelik yok.",
                                description = "Düzenli dijital ve fiziksel aboneliklerinizi takip etmek için ekleyin.",
                            )
                            Button(
                                onClick = onAddSubscription,
                            ) {
                                Text("Abonelik Ekle")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            items(
                                items = state.items,
                                key = { it.id.value },
                            ) { item ->
                                SubscriptionCard(
                                    item = item,
                                    onClick = { onSubscriptionClick(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
