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
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.DebtCard
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.debt.DebtsUiState
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Borç ve alacaklar liste ekranının durumsuz (stateless) Compose sunumudur.
 */
@Composable
fun DebtsScreen(
    state: DebtsUiState,
    onRetry: () -> Unit,
    onAddDebt: () -> Unit,
    onDebtClick: (EntityId) -> Unit,
    onNavigateToSnowballPlan: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!state.isLoading && state.observationError == null) {
                FloatingActionButton(
                    onClick = onAddDebt,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics {
                        contentDescription = "Yeni borç veya alacak ekle"
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
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = "Borç / Alacak",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        ActiveWorkspaceIndicator(
                            workspaceName = state.activeWorkspaceName,
                            isCompact = true,
                        )
                    }
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = "Kişi ve kurum bazlı borç ve alacaklarınızı takip edin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (onNavigateToSnowballPlan != null && !state.isLoading && state.observationError == null) {
                    Spacer(modifier = Modifier.padding(horizontal = FeniqoSpacing.ExtraSmall))
                    androidx.compose.material3.OutlinedButton(
                        onClick = onNavigateToSnowballPlan,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.feniqo.mobile.presentation.theme.FeniqoRadius.Medium),
                    ) {
                        Text(
                            text = "Ödeme Planı",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
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
                            message = "Borç ve alacaklar yükleniyor...",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.observationError != null -> {
                        ErrorState(
                            title = "Borç ve Alacaklar Yüklenemedi",
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
                                title = "Kayıtlı borç veya alacak yok.",
                                description = "Borç ve alacaklarınızı takip etmek için ekleyin.",
                            )
                            Button(onClick = onAddDebt) {
                                Text("Borç / Alacak Ekle")
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
                                items = state.debts,
                                key = { it.id.value },
                            ) { item ->
                                DebtCard(
                                    item = item,
                                    onClick = { onDebtClick(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
