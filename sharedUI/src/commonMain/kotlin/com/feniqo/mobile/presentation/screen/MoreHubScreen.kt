package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.hub.MoreHubOverviewCardState
import com.feniqo.mobile.presentation.hub.MoreHubUiState
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground

/**
 * Onaylı tasarım sunumuna ("01 Daha Fazla" ve "02 Aşağı kaydırınca" tek dikey akışı)
 * tam sadakatle oluşturulmuş Daha Fazla merkezi ekranı.
 */
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoWarmStoneBackground,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
        ) {
            // 1. Üst Alan (feniqo wordmark, profil butonu, başlık, alt metin ve çalışma alanı rozeti)
            item("top_header") {
                MoreHubTopHeader(
                    workspaceName = state.activeWorkspaceName,
                    onNavigateToProfile = onNavigateToProfile,
                )
            }

            // 2. Genel Bakış (2x2 hücreli tek grafit kart)
            item("overview") {
                Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
                    Text(
                        text = "Genel bakış",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        ),
                        color = FeniqoTextPrimary,
                    )
                    MoreHubOverviewGraphiteCard(
                        state = state,
                        onNavigateToAssets = onNavigateToAssets,
                        onNavigateToGoals = onNavigateToGoals,
                        onNavigateToSubscriptions = onNavigateToSubscriptions,
                        onNavigateToRecurringTransactions = onNavigateToRecurringTransactions,
                    )
                }
            }

            // 3. Varlık Yönetimi (Varlıklar, Hedefler, Borç ve Alacaklar)
            item("wealth_management") {
                SectionContainer(title = "Varlık yönetimi") {
                    MoreHubMenuCard {
                        MoreHubMenuItemRow(
                            title = "Varlıklar",
                            subtitle = "Banka, nakit ve yatırımlar",
                            icon = { MoreHubWalletIcon() },
                            onClick = onNavigateToAssets,
                        )
                        HorizontalDivider(
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large),
                        )
                        MoreHubMenuItemRow(
                            title = "Hedefler",
                            subtitle = "Birikimlerini planla",
                            icon = { MoreHubTargetIcon() },
                            onClick = onNavigateToGoals,
                        )
                        HorizontalDivider(
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large),
                        )
                        MoreHubMenuItemRow(
                            title = "Borç ve Alacaklar",
                            subtitle = "Ödemelerini takip et",
                            icon = { MoreHubExchangeArrowsIcon() },
                            onClick = onNavigateToDebts,
                        )
                    }
                }
            }

            // 4. Para Yönetimi (Abonelikler, Tekrarlayan İşlemler, Kategoriler)
            item("money_management") {
                SectionContainer(title = "Para yönetimi") {
                    MoreHubMenuCard {
                        MoreHubMenuItemRow(
                            title = "Abonelikler",
                            subtitle = "Düzenli abonelik ödemeleri",
                            icon = { MoreHubCalendarIcon() },
                            onClick = onNavigateToSubscriptions,
                        )
                        HorizontalDivider(
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large),
                        )
                        MoreHubMenuItemRow(
                            title = "Tekrarlayan İşlemler",
                            subtitle = "Planlı gelir ve giderler",
                            icon = { MoreHubLoopArrowsIcon() },
                            onClick = onNavigateToRecurringTransactions,
                        )
                        HorizontalDivider(
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large),
                        )
                        MoreHubMenuItemRow(
                            title = "Kategoriler",
                            subtitle = "Gelir ve giderlerini düzenle",
                            icon = { MoreHubTagIcon() },
                            onClick = onNavigateToCategories,
                        )
                    }
                }
            }

            // 5. Ortak Kullanım (Ortak Alanlar)
            item("collaboration") {
                SectionContainer(title = "Ortak kullanım") {
                    MoreHubSharedSpacesCard(onClick = onNavigateToSharedSpaces)
                }
            }

            // 6. İçgörüler (Raporlar - Pasif)
            item("insights") {
                SectionContainer(title = "İçgörüler") {
                    MoreHubMenuCard {
                        MoreHubMenuItemRow(
                            title = "Raporlar",
                            subtitle = "Aylık ve yıllık finansal analizler",
                            icon = { MoreHubBarChartIcon() },
                            trailing = {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEFF1F0),
                                ) {
                                    Text(
                                        text = "Yakında",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            },
                            onClick = null,
                        )
                    }
                }
            }

            // 7. Ayarlar (Uygulama Ayarları)
            item("settings") {
                SectionContainer(title = "Ayarlar") {
                    MoreHubMenuCard {
                        MoreHubMenuItemRow(
                            title = "Uygulama Ayarları",
                            subtitle = "Görünüm, güvenlik ve veri araçları",
                            icon = { MoreHubSettingsGearIcon() },
                            onClick = onNavigateToSettings,
                        )
                    }
                }
            }

            // 8. Profil Bilgi Satırı
            item("profile_info_row") {
                MoreHubProfileHintRow()
            }

            // Doğal alt içerik payı
            item("bottom_spacing") {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
            }
        }
    }
}

/**
 * 1. Üst Alan: Sol üstte "feniqo" wordmark, sağ üstte açık adaçayı profil butonu,
 * "Daha Fazla" büyük başlığı, alt metin ve aktif çalışma alanı hap rozeti.
 */
@Composable
private fun MoreHubTopHeader(
    workspaceName: String?,
    onNavigateToProfile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // Üst logo ve profil ikonu satırı
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "feniqo",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = FeniqoSageGreen,
            )

            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = onNavigateToProfile,
                    )
                    .semantics { contentDescription = "Profil ve hesap" },
                shape = CircleShape,
                color = FeniqoSageGreenContainer,
                contentColor = FeniqoSageGreen,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MoreHubPersonOutlineIcon(
                        color = FeniqoSageGreen,
                        size = 20.dp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Büyük Başlık
        Text(
            text = "Daha Fazla",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = FeniqoTextPrimary,
        )

        // Alt Metin
        Text(
            text = "Finansının tüm parçaları, bir arada.",
            style = MaterialTheme.typography.bodyMedium,
            color = FeniqoTextSecondary,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Aktif Çalışma Alanı Rozeti
        val currentWorkspace = workspaceName?.trim()?.takeIf { it.isNotBlank() } ?: "Kişisel"
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFEFF2F0),
            modifier = Modifier.semantics {
                contentDescription = "Aktif çalışma alanı: $currentWorkspace"
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MoreHubPersonOutlineIcon(
                    color = FeniqoTextPrimary,
                    size = 14.dp,
                )
                Text(
                    text = currentWorkspace,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }
        }
    }
}

/**
 * 2. Genel Bakış: 2x2 hücreli tek grafit kart.
 * Sol üst: Varlıklar | Sağ üst: Hedefler
 * Sol alt: Abonelikler | Sağ alt: Tekrarlayan
 */
@Composable
private fun MoreHubOverviewGraphiteCard(
    state: MoreHubUiState,
    onNavigateToAssets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToRecurringTransactions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F262B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Sağ üst köşede hafif kavisli dekoratif çizgiler (sahte grafik değil)
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 110.dp, height = 90.dp),
            ) {
                val strokeColor = Color.White.copy(alpha = 0.07f)
                val stroke = Stroke(width = 1.dp.toPx())
                drawArc(
                    color = strokeColor,
                    startAngle = 180f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.1f, -size.height * 0.4f),
                    size = Size(size.width * 1.4f, size.height * 1.8f),
                    style = stroke,
                )
                drawArc(
                    color = strokeColor,
                    startAngle = 180f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.35f, -size.height * 0.2f),
                    size = Size(size.width * 1.1f, size.height * 1.4f),
                    style = stroke,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Üst Satır: Varlıklar | Hedefler
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    OverviewCell(
                        modifier = Modifier.weight(1f),
                        title = "Varlıklar",
                        icon = { MoreHubWalletIcon(size = 18.dp) },
                        cardState = state.assets,
                        defaultSecondary = null,
                        onClick = onNavigateToAssets,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    OverviewCell(
                        modifier = Modifier.weight(1f),
                        title = "Hedefler",
                        icon = { MoreHubTargetIcon(size = 18.dp) },
                        cardState = state.goals,
                        defaultSecondary = "aktif hedef",
                        onClick = onNavigateToGoals,
                    )
                }

                // Yatay İnce Ayırıcı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )

                // Alt Satır: Abonelikler | Tekrarlayan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    OverviewCell(
                        modifier = Modifier.weight(1f),
                        title = "Abonelikler",
                        icon = { MoreHubCalendarIcon(size = 18.dp) },
                        cardState = state.subscriptions,
                        defaultSecondary = "aktif abonelik",
                        onClick = onNavigateToSubscriptions,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    OverviewCell(
                        modifier = Modifier.weight(1f),
                        title = "Tekrarlayan",
                        icon = { MoreHubLoopArrowsIcon(size = 18.dp) },
                        cardState = state.recurringTransactions,
                        defaultSecondary = "planlı kural",
                        onClick = onNavigateToRecurringTransactions,
                    )
                }
            }
        }
    }
}

/**
 * Genel bakış kartı içerisindeki tekil hücre.
 */
@Composable
private fun OverviewCell(
    title: String,
    icon: @Composable () -> Unit,
    cardState: MoreHubOverviewCardState,
    defaultSecondary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(FeniqoSpacing.LargePlus),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // İkon ve Başlık
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = FeniqoSageGreenContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color(0xFF9EACB2),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Değer ve Alt Metin
        when (cardState) {
            MoreHubOverviewCardState.Loading -> {
                Text(
                    text = "Yükleniyor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8C9B9F),
                )
            }

            is MoreHubOverviewCardState.Content -> {
                Text(
                    text = cardState.primaryText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (cardState.primaryText.length > 8) 19.sp else 23.sp,
                    ).merge(FeniqoTabularNumberStyle),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = cardState.secondaryText ?: defaultSecondary
                if (!secondary.isNullOrBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = Color(0xFF8C9B9F),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            is MoreHubOverviewCardState.Empty -> {
                Text(
                    text = cardState.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFF8C9B9F),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            is MoreHubOverviewCardState.Error -> {
                Text(
                    text = cardState.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFFFFB4AB),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Bölüm başlığı ve alt içerik slotu.
 */
@Composable
private fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
            color = FeniqoTextPrimary,
        )
        content()
    }
}

/**
 * Beyaz gruplanmış menü kartı.
 */
@Composable
private fun MoreHubMenuCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Menü satırı: Açık adaçayı ikon kutusu, başlık, açıklama ve sağ aksiyon (chevron veya rozet).
 */
@Composable
private fun MoreHubMenuItemRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val isClickable = onClick != null
    val rowModifier = if (isClickable) {
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = requireNotNull(onClick))
    } else {
        modifier
            .fillMaxWidth()
            .semantics { disabled() }
    }

    Row(
        modifier = rowModifier.padding(horizontal = FeniqoSpacing.Large, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        // Yumuşak köşeli açık adaçayı ikon kutusu
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = FeniqoSageGreenContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }

        // Başlık ve Açıklama
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = FeniqoTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                ),
                color = FeniqoTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Sağ aksiyon (Chevron veya Özel Bileşen)
        if (trailing != null) {
            trailing()
        } else if (isClickable) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = Color(0xFF94A3B8),
            )
        }
    }
}

/**
 * 5. Ortak Kullanım: "Ortak Alanlar" açık adaçayı renkli belirgin kart.
 */
@Composable
private fun MoreHubSharedSpacesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6EFE9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.LargePlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = "Ortak Alanlar",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    color = Color(0xFF1E3A2B),
                )
                Text(
                    text = "Aile ve ekibinle\nbirlikte planla.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = Color(0xFF385A47),
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Yön oku: →
                Canvas(modifier = Modifier.size(20.dp, 16.dp)) {
                    val color = Color(0xFF1E3A2B)
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawLine(color, Offset(0f, size.height * 0.5f), Offset(size.width * 0.85f, size.height * 0.5f), stroke.width, StrokeCap.Round)
                    val arrowPath = Path().apply {
                        moveTo(size.width * 0.55f, size.height * 0.15f)
                        lineTo(size.width * 0.9f, size.height * 0.5f)
                        lineTo(size.width * 0.55f, size.height * 0.85f)
                    }
                    drawPath(arrowPath, color, style = stroke)
                }
            }

            // Sağda sade iki kişi çizgisel illüstrasyonu
            Canvas(modifier = Modifier.size(100.dp, 90.dp)) {
                // Açık yumuşak dairesel fon
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                )

                val color = Color(0xFF1E3A2B)
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                // 1. Kişi (Sol)
                val c1X = size.width * 0.38f
                val c1Y = size.height * 0.40f
                drawCircle(color, radius = size.minDimension * 0.13f, center = Offset(c1X, c1Y), style = stroke)
                val body1 = Path().apply {
                    moveTo(c1X - size.minDimension * 0.22f, size.height * 0.78f)
                    cubicTo(
                        c1X - size.minDimension * 0.20f, size.height * 0.56f,
                        c1X + size.minDimension * 0.20f, size.height * 0.56f,
                        c1X + size.minDimension * 0.22f, size.height * 0.78f,
                    )
                }
                drawPath(body1, color, style = stroke)

                // 2. Kişi (Sağ)
                val c2X = size.width * 0.68f
                val c2Y = size.height * 0.44f
                drawCircle(color, radius = size.minDimension * 0.13f, center = Offset(c2X, c2Y), style = stroke)
                val body2 = Path().apply {
                    moveTo(c2X - size.minDimension * 0.22f, size.height * 0.78f)
                    cubicTo(
                        c2X - size.minDimension * 0.20f, size.height * 0.58f,
                        c2X + size.minDimension * 0.20f, size.height * 0.58f,
                        c2X + size.minDimension * 0.22f, size.height * 0.78f,
                    )
                }
                drawPath(body2, color, style = stroke)
            }
        }
    }
}

/**
 * 8. Profil Bilgi Satırı: "Profil ve hesabına sağ üstteki simgeden ulaşabilirsin."
 */
@Composable
private fun MoreHubProfileHintRow(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F2F1),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            MoreHubPersonOutlineIcon(
                color = Color(0xFF4B5563),
                size = 20.dp,
            )
            Text(
                text = "Profil ve hesabına sağ üstteki simgeden ulaşabilirsin.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                ),
                color = Color(0xFF4B5563),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Vektörel İkonlar (Compose Canvas ile tutarlı ve modern çizimler)
// -----------------------------------------------------------------------------

@Composable
private fun MoreHubPersonOutlineIcon(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Baş
        drawCircle(
            color = color,
            radius = this.size.minDimension * 0.22f,
            center = Offset(this.size.width * 0.5f, this.size.height * 0.32f),
            style = stroke,
        )
        // Omuz kavis
        val body = Path().apply {
            moveTo(this@Canvas.size.width * 0.16f, this@Canvas.size.height * 0.86f)
            cubicTo(
                this@Canvas.size.width * 0.18f, this@Canvas.size.height * 0.58f,
                this@Canvas.size.width * 0.82f, this@Canvas.size.height * 0.58f,
                this@Canvas.size.width * 0.84f, this@Canvas.size.height * 0.86f,
            )
        }
        drawPath(body, color, style = stroke)
    }
}

@Composable
private fun MoreHubWalletIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Cüzdan gövdesi
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.60f),
            cornerRadius = CornerRadius(w * 0.14f),
            style = stroke,
        )
        // Üst kapak çizgisi
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.40f),
            end = Offset(w * 0.60f, h * 0.40f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        // Kilit tokası
        drawCircle(
            color = color,
            radius = w * 0.05f,
            center = Offset(w * 0.68f, h * 0.52f),
        )
    }
}

@Composable
private fun MoreHubTargetIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val center = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
        val radius = this.size.minDimension * 0.42f

        // Dış halka
        drawCircle(color = color, radius = radius, center = center, style = stroke)
        // İç halka
        drawCircle(color = color, radius = radius * 0.55f, center = center, style = stroke)
        // Merkez nokta
        drawCircle(color = color, radius = radius * 0.18f, center = center)
    }
}

@Composable
private fun MoreHubExchangeArrowsIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Üst ok (sağa doğru →)
        drawLine(color, Offset(w * 0.20f, h * 0.36f), Offset(w * 0.76f, h * 0.36f), stroke.width, StrokeCap.Round)
        val topArrowHead = Path().apply {
            moveTo(w * 0.58f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.36f)
            lineTo(w * 0.58f, h * 0.50f)
        }
        drawPath(topArrowHead, color, style = stroke)

        // Alt ok (sola doğru ←)
        drawLine(color, Offset(w * 0.80f, h * 0.64f), Offset(w * 0.24f, h * 0.64f), stroke.width, StrokeCap.Round)
        val bottomArrowHead = Path().apply {
            moveTo(w * 0.42f, h * 0.50f)
            lineTo(w * 0.22f, h * 0.64f)
            lineTo(w * 0.42f, h * 0.78f)
        }
        drawPath(bottomArrowHead, color, style = stroke)
    }
}

@Composable
private fun MoreHubCalendarIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Takvim gövdesi
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.24f),
            size = Size(w * 0.68f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.12f),
            style = stroke,
        )
        // Üst askılar
        drawLine(color, Offset(w * 0.32f, h * 0.14f), Offset(w * 0.32f, h * 0.24f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.14f), Offset(w * 0.68f, h * 0.24f), stroke.width, StrokeCap.Round)
        // Başlık çizgisi
        drawLine(color, Offset(w * 0.16f, h * 0.42f), Offset(w * 0.84f, h * 0.42f), stroke.width, StrokeCap.Round)
        // İç takvim noktaları
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.34f, h * 0.58f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.58f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.66f, h * 0.58f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.34f, h * 0.72f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.72f))
    }
}

@Composable
private fun MoreHubLoopArrowsIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Üst yay
        drawArc(
            color = color,
            startAngle = 190f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            style = stroke,
        )
        // Üst ok ucu
        val arrowTop = Path().apply {
            moveTo(w * 0.68f, h * 0.12f)
            lineTo(w * 0.84f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.36f)
        }
        drawPath(arrowTop, color, style = stroke)

        // Alt yay
        drawArc(
            color = color,
            startAngle = 10f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            style = stroke,
        )
        // Alt ok ucu
        val arrowBottom = Path().apply {
            moveTo(w * 0.32f, h * 0.64f)
            lineTo(w * 0.16f, h * 0.76f)
            lineTo(w * 0.32f, h * 0.88f)
        }
        drawPath(arrowBottom, color, style = stroke)
    }
}

@Composable
private fun MoreHubTagIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        val tagPath = Path().apply {
            moveTo(w * 0.18f, h * 0.24f)
            lineTo(w * 0.54f, h * 0.24f)
            lineTo(w * 0.84f, h * 0.54f)
            lineTo(w * 0.54f, h * 0.84f)
            lineTo(w * 0.18f, h * 0.54f)
            close()
        }
        drawPath(tagPath, color, style = stroke)
        drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.36f, h * 0.42f))
    }
}

@Composable
private fun MoreHubBarChartIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.4.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // 3 dikey çubuk
        drawLine(color, Offset(w * 0.24f, h * 0.80f), Offset(w * 0.24f, h * 0.54f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.50f, h * 0.80f), Offset(w * 0.50f, h * 0.24f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.76f, h * 0.80f), Offset(w * 0.76f, h * 0.40f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun MoreHubSettingsGearIcon(
    color: Color = FeniqoSageGreen,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val center = Offset(this.size.width * 0.5f, this.size.height * 0.5f)
        val radius = this.size.minDimension * 0.26f

        // Merkez halka
        drawCircle(color = color, radius = radius, center = center, style = stroke)

        // Dış dişler
        val toothLength = this.size.minDimension * 0.14f
        val toothRadius = radius + toothLength
        for (i in 0 until 8) {
            val angle = i * 45f * (kotlin.math.PI / 180f).toFloat()
            val startX = center.x + radius * kotlin.math.cos(angle)
            val startY = center.y + radius * kotlin.math.sin(angle)
            val endX = center.x + toothRadius * kotlin.math.cos(angle)
            val endY = center.y + toothRadius * kotlin.math.sin(angle)
            drawLine(color, Offset(startX, startY), Offset(endX, endY), stroke.width, StrokeCap.Round)
        }
    }
}
