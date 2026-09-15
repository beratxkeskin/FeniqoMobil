package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.RecurringOverviewGraphiteCard
import com.feniqo.mobile.presentation.component.RecurringTransactionCard
import com.feniqo.mobile.presentation.recurring.RecurringTransactionsUiState
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground

/**
 * Tekrarlayan işlemler (abonelikler ve düzenli ödemeler) liste ekranının onaylı tasarım (Görsel 01, 10, 12) Compose sunumudur.
 */
@Composable
fun RecurringTransactionsScreen(
    state: RecurringTransactionsUiState,
    onRetry: () -> Unit,
    onAddRecurringTransaction: () -> Unit,
    onRecurringTransactionClick: (EntityId) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FeniqoWarmStoneBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 1. Üst Gezinme Barı: "< Daha Fazla", Merkezde "feniqo" Logosu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Geri Dön",
                            tint = FeniqoTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Daha Fazla",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = FeniqoTextPrimary,
                        )
                    }
                }

                Text(
                    text = "feniqo",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = FeniqoSageGreen,
                )

                // Sağ Dengeleyici Boşluk / Profil Alanı
                Box(modifier = Modifier.size(40.dp))
            }

            // 2. Ana İçerik
            when {
                state.isLoading -> {
                    RecurringListLoadingState(modifier = Modifier.fillMaxSize())
                }

                state.observationError != null -> {
                    RecurringListErrorState(
                        errorMessage = state.observationError.toDisplayText(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.isEmpty -> {
                    RecurringListEmptyState(
                        activeWorkspaceName = state.activeWorkspaceName,
                        onAddClick = onAddRecurringTransaction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    val activeItems = state.items.filter { it.isActive }
                    val pausedItems = state.items.filter { it.isPaused }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 90.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Başlık ve Çalışma Alanı
                        item("header_section") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Tekrarlayan\nişlemler",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 34.sp,
                                    ),
                                    color = FeniqoTextPrimary,
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                ActiveWorkspaceIndicator(
                                    workspaceName = state.activeWorkspaceName,
                                    isCompact = true,
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                RecurringOverviewGraphiteCard()
                            }
                        }

                        // Aktif Kurallar Grubu
                        if (activeItems.isNotEmpty()) {
                            item("active_header") {
                                Text(
                                    text = "Aktif kurallar",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = FeniqoTextPrimary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            items(
                                items = activeItems,
                                key = { it.id.value },
                            ) { item ->
                                RecurringTransactionCard(
                                    item = item,
                                    onClick = { onRecurringTransactionClick(item.id) },
                                )
                            }
                        }

                        // Duraklatılanlar Grubu
                        if (pausedItems.isNotEmpty()) {
                            item("paused_header") {
                                Text(
                                    text = "Duraklatılan",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = FeniqoTextPrimary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }

                            items(
                                items = pausedItems,
                                key = { it.id.value },
                            ) { item ->
                                RecurringTransactionCard(
                                    item = item,
                                    onClick = { onRecurringTransactionClick(item.id) },
                                )
                            }
                        }

                        // Alt Aksiyon Butonu ve Bilgilendirme
                        item("footer_actions") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Button(
                                    onClick = onAddRecurringTransaction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FeniqoSageGreen,
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Text(
                                        text = "+ Yeni kural",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = FeniqoTextSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Banka hesabından para çekilmez.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = FeniqoTextSecondary,
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

/**
 * Görsel 10: Boş Liste (İlk Kullanım) Durumu.
 */
@Composable
private fun RecurringListEmptyState(
    activeWorkspaceName: String?,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Başlık ve Çalışma Alanı
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tekrarlayan\nişlemler",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                ),
                color = FeniqoTextPrimary,
            )

            Spacer(modifier = Modifier.height(10.dp))

            ActiveWorkspaceIndicator(
                workspaceName = activeWorkspaceName,
                isCompact = true,
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Yuvarlak İllüstrasyon Kutusu
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(Color(0xFFE8F5EE), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(54.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Henüz bir kuralın yok",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = FeniqoTextPrimary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Düzenli gelir ve giderlerin için\nilk tekrar kuralını oluştur.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = FeniqoTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoSageGreen,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "+ Yeni kural",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = FeniqoTextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Banka hesabından para çekilmez.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = FeniqoTextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Görsel 12: Liste Yüklenemedi Hata Durumu.
 */
@Composable
private fun RecurringListErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Liste yüklenemedi.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = FeniqoTextPrimary,
                        )
                        Text(
                            text = "Lütfen daha sonra tekrar deneyin.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = FeniqoTextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FeniqoTextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Tekrar dene",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Görsel 12: İskelet / Yükleme Durumu.
 */
@Composable
private fun RecurringListLoadingState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFF1F5F9), CircleShape),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(12.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(16.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
