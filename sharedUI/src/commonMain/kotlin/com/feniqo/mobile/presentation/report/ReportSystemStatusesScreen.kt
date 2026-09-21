package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary

/**
 * 26 Sistem durumları (Rapor durumu örnekleri) ekranı.
 * Tasarım panosundaki 3 sistem durumunu (Yükleniyor, Çevrimdışı/Senkronizasyon Bekliyor, Hata)
 * onaylı görseldeki hiyerarşi ve stil ile sunar.
 */
@Composable
fun ReportSystemStatusesScreen(
    pendingCount: Int = 3,
    onRetry: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = FeniqoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Rapor durumu örnekleri",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Durum: Rapor hazırlanıyor
            ReportLoadingCard()

            // 2. Durum: Çevrimdışısın ve senkronizasyon bekliyor
            ReportOfflineStatusSection(
                pendingCount = pendingCount,
            )

            // 3. Durum: Rapor hesaplanamadı ve Tekrar dene
            ReportErrorCard(
                onRetry = onRetry,
            )
        }
    }
}
