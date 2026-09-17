package com.feniqo.mobile.presentation.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.theme.FeniqoGraphite
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Giriş, kayıt ve parola kurtarma ekranlarının standart üst çubuğu.
 * Geri eylemi varsa ok ve marka adını, yoksa yalnız marka adını gösterir.
 */
@Composable
fun AuthTopBar(
    title: String = "Feniqo",
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Geri dön" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = FeniqoSageGreen,
        )
    }
}

/**
 * Pano A - 01 Karşılama ekranının özel kod tabanlı illüstrasyonu.
 * Grafit kart içinde yeşil yükselen sütunlar, önünde halka grafik ve hedef rozeti.
 */
@Composable
fun WelcomeHeroIllustration(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Arka plan adaçayı dairesel hale
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(FeniqoSageGreenContainer.copy(alpha = 0.65f)),
        )

        // Arka plan grafit kartı
        Box(
            modifier = Modifier
                .size(width = 150.dp, height = 160.dp)
                .align(Alignment.Center)
                .offset(x = (-16).dp, y = (-12).dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(FeniqoGraphite),
            contentAlignment = Alignment.Center,
        ) {
            // Yükselen 4 adet yeşil sütun
            Canvas(modifier = Modifier.size(width = 100.dp, height = 90.dp)) {
                val barWidth = 14.dp.toPx()
                val spacing = 12.dp.toPx()
                val heights = listOf(0.35f, 0.55f, 0.75f, 0.95f)
                val baseColors = listOf(
                    Color(0xFF3E7558),
                    Color(0xFF34D399),
                    Color(0xFF2D5A43),
                    Color(0xFF10B981),
                )

                heights.forEachIndexed { index, ratio ->
                    val x = index * (barWidth + spacing)
                    val h = size.height * ratio
                    val y = size.height - h
                    drawRoundRect(
                        color = baseColors[index],
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            }
        }

        // Ön planda yüzen beyaz kart: halka grafik ve hedef ikonu
        Surface(
            modifier = Modifier
                .size(width = 130.dp, height = 74.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-8).dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Halka (donut) grafik
                Canvas(modifier = Modifier.size(46.dp)) {
                    val strokeWidth = 6.5.dp.toPx()
                    // Arka plan tam halka
                    drawCircle(
                        color = Color(0xFFE8F1EC),
                        style = Stroke(width = strokeWidth),
                    )
                    // Dolu adaçayı yeşil yay (~%70)
                    drawArc(
                        color = FeniqoSageGreen,
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }

                // Hedef (bullseye) ikonu çizimi
                Canvas(modifier = Modifier.size(34.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    // Dış daire
                    drawCircle(
                        color = FeniqoSageGreen,
                        radius = size.minDimension / 2 - 2.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    // İç orta daire
                    drawCircle(
                        color = FeniqoSageGreen.copy(alpha = 0.5f),
                        radius = (size.minDimension / 2) * 0.55f,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    // Merkez nokta
                    drawCircle(
                        color = FeniqoSageGreen,
                        radius = 3.dp.toPx(),
                    )
                }
            }
        }
    }
}

/**
 * Pano A - 04 E-posta doğrulama ekranının özel kod tabanlı illüstrasyonu.
 */
@Composable
fun EmailVerificationHeroIllustration(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Arka plan adaçayı dairesel hale
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(FeniqoSageGreenContainer.copy(alpha = 0.7f)),
        )

        // Arka plan grafit gölge kart
        Box(
            modifier = Modifier
                .size(80.dp)
                .offset(x = (-14).dp, y = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(FeniqoGraphite),
        )

        // Ön planda mint zeminli zarf kartı
        Surface(
            modifier = Modifier
                .size(86.dp)
                .offset(x = 6.dp, y = (-6).dp)
                .shadow(8.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.MailOutline,
                    contentDescription = null,
                    tint = FeniqoSageGreen,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        // Dekoratif küçük yeşil çizgiler/noktalar
        Canvas(modifier = Modifier.size(170.dp)) {
            val sparkColor = FeniqoSageGreen.copy(alpha = 0.8f)
            val stroke = 2.dp.toPx()
            // Sağ üst mini çizgi
            drawLine(
                color = sparkColor,
                start = Offset(size.width * 0.82f, size.height * 0.22f),
                end = Offset(size.width * 0.88f, size.height * 0.16f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = sparkColor,
                start = Offset(size.width * 0.75f, size.height * 0.18f),
                end = Offset(size.width * 0.76f, size.height * 0.10f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Pano B ve C ekranlarında kullanılan 72dp dairesel durum rozeti.
 */
@Composable
fun AuthStatusBadge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    contentDescription: String? = null,
) {
    val backgroundColor = if (isError) {
        Color(0xFFFEECEB)
    } else {
        FeniqoSageGreenContainer
    }

    val iconColor = if (isError) {
        Color(0xFFBA1A1A)
    } else {
        FeniqoSageGreen
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(34.dp),
        )
    }
}

/**
 * Pano C 09 (Giriş hatası) ve 11 (Bağlantı sorunu) için kırmızı hata bildirim kartı.
 */
@Composable
fun AuthErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    isNetworkError: Boolean = false,
) {
    val lines = message.lines()
    val title = lines.firstOrNull() ?: ""
    val description = lines.drop(1).joinToString("\n")

    val icon = if (isNetworkError) {
        Icons.Outlined.WifiOff
    } else {
        Icons.Outlined.ErrorOutline
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEECEB),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFBA1A1A),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFFBA1A1A),
                    )
                }
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBA1A1A),
                    )
                }
            }
        }
    }
}

/**
 * Dolu Feniqo yeşili birincil buton. Yüklenme durumunda spinner gösterir ve çift tıklamayı engeller.
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingText: String? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FeniqoSageGreen,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFE2E8F0),
            disabledContentColor = Color(0xFF94A3B8),
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .semantics {
                        contentDescription = loadingText ?: "İşlem sürüyor"
                    },
                strokeWidth = 2.dp,
                color = Color.White,
            )
            if (loadingText != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = loadingText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

/**
 * İnce çerçeveli ikincil buton.
 */
@Composable
fun AuthSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FeniqoSageGreen),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FeniqoSageGreen,
            disabledContentColor = Color(0xFF94A3B8),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
