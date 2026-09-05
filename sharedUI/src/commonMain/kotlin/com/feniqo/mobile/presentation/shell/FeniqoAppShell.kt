package com.feniqo.mobile.presentation.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.SyncStatusIndicator
import com.feniqo.mobile.presentation.sync.SyncStatusUiState

enum class AppSection(val label: String) {
    DASHBOARD("Ana Sayfa"),
    TRANSACTIONS("İşlemler"),
    PLAN("Plan"),
    MORE("Profil"),
}

/**
 * Platformdan bağımsız, durumsuz (stateless) uygulama ana kabuğudur.
 * Navigation durumunu barındırmaz; seçili sekmeyi, birincil eylemi ve içerik slot'unu dışarıdan alır.
 * 5 görsel alt bar öğesi sunar: Ana Sayfa, İşlemler, + (hızlı işlem), Plan, Profil.
 */
@Composable
fun FeniqoAppShell(
    selectedSection: AppSection,
    onSectionSelect: (AppSection) -> Unit,
    onPrimaryAction: () -> Unit,
    syncStatus: SyncStatusUiState = SyncStatusUiState.Initial,
    onManualSync: () -> Unit = {},
    onRetryFailed: () -> Unit = {},
    showNavigationChrome: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavigationChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    // 1. Ana Sayfa
                    NavigationBarItem(
                        selected = selectedSection == AppSection.DASHBOARD,
                        onClick = { onSectionSelect(AppSection.DASHBOARD) },
                        icon = { FeniqoBottomBarIcon(AppSection.DASHBOARD) },
                        label = { Text(AppSection.DASHBOARD.label) },
                    )

                    // 2. İşlemler
                    NavigationBarItem(
                        selected = selectedSection == AppSection.TRANSACTIONS,
                        onClick = { onSectionSelect(AppSection.TRANSACTIONS) },
                        icon = { FeniqoBottomBarIcon(AppSection.TRANSACTIONS) },
                        label = { Text(AppSection.TRANSACTIONS.label) },
                    )

                    // 3. Birincil Hızlı Eylem (+)
                    NavigationBarItem(
                        selected = false,
                        onClick = onPrimaryAction,
                        icon = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .offset(y = (-6).dp)
                                    .shadow(6.dp, CircleShape)
                                    .size(56.dp)
                                    .semantics { contentDescription = "Hızlı işlem ekle" },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        },
                        label = { Text("Ekle") },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                        ),
                    )

                    // 4. Plan
                    NavigationBarItem(
                        selected = selectedSection == AppSection.PLAN,
                        onClick = { onSectionSelect(AppSection.PLAN) },
                        icon = { FeniqoBottomBarIcon(AppSection.PLAN) },
                        label = { Text(AppSection.PLAN.label) },
                    )

                    // 5. Daha Fazla
                    NavigationBarItem(
                        selected = selectedSection == AppSection.MORE,
                        onClick = { onSectionSelect(AppSection.MORE) },
                        icon = { FeniqoBottomBarIcon(AppSection.MORE) },
                        label = { Text(AppSection.MORE.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (showNavigationChrome) {
                SyncStatusIndicator(
                    uiState = syncStatus,
                    onManualSync = onManualSync,
                    onRetryFailed = onRetryFailed,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FeniqoBottomBarIcon(
    section: AppSection,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics { contentDescription = section.label },
    ) {
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val w = size.width
        val h = size.height

        when (section) {
            AppSection.DASHBOARD -> {
                val roof = Path().apply {
                    moveTo(w * 0.16f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.46f)
                }
                drawPath(roof, color, style = stroke)
                val house = Path().apply {
                    moveTo(w * 0.24f, h * 0.4f)
                    lineTo(w * 0.24f, h * 0.82f)
                    lineTo(w * 0.76f, h * 0.82f)
                    lineTo(w * 0.76f, h * 0.4f)
                    moveTo(w * 0.43f, h * 0.82f)
                    lineTo(w * 0.43f, h * 0.61f)
                    lineTo(w * 0.57f, h * 0.61f)
                    lineTo(w * 0.57f, h * 0.82f)
                }
                drawPath(house, color, style = stroke)
            }

            AppSection.TRANSACTIONS -> {
                drawLine(color, Offset(w * 0.34f, h * 0.2f), Offset(w * 0.34f, h * 0.8f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, h * 0.34f), Offset(w * 0.34f, h * 0.2f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.48f, h * 0.34f), Offset(w * 0.34f, h * 0.2f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, h * 0.2f), Offset(w * 0.66f, h * 0.8f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.52f, h * 0.66f), Offset(w * 0.66f, h * 0.8f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.8f, h * 0.66f), Offset(w * 0.66f, h * 0.8f), strokeWidth, StrokeCap.Round)
            }

            AppSection.PLAN -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.2f, h * 0.22f),
                    size = Size(w * 0.6f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.34f, h * 0.16f), Offset(w * 0.34f, h * 0.3f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, h * 0.16f), Offset(w * 0.66f, h * 0.3f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.48f), Offset(w * 0.68f, h * 0.48f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.64f), Offset(w * 0.58f, h * 0.64f), strokeWidth, StrokeCap.Round)
            }

            AppSection.MORE -> {
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.34f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(w * 0.22f, h * 0.82f)
                    cubicTo(w * 0.25f, h * 0.58f, w * 0.75f, h * 0.58f, w * 0.78f, h * 0.82f)
                }
                drawPath(shoulders, color, style = stroke)
            }
        }
    }
}
