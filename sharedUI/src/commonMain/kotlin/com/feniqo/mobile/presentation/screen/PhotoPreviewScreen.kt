package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import kotlin.math.roundToInt

/**
 * Pano A2: Fotoğraf Önizleme ve Kırpma Ekranı.
 *
 * Saf KMP Compose UI bileşenidir. Android Bitmap/Uri tiplerine bağımlı değildir;
 * platformdan bağımsız ImageBitmap kullanır.
 *
 * Özellikler:
 * - Dairesel maskeli önizleme ve 3x3 kılavuz çizgileri
 * - Dokunarak kaydırma (pan) ve çift parmakla yakınlaştırma (pinch-to-zoom)
 * - Eksi (-) ve Artı (+) butonlu zoom slider'ı
 * - "Yeniden seç" ve "Fotoğrafı kaydet" eylemleri
 * - "Yalnızca bu cihazda saklanır." güvenlik güvencesi
 */
@Composable
fun PhotoPreviewScreen(
    imageBitmap: ImageBitmap?,
    onBack: () -> Unit,
    onReselect: () -> Unit,
    onSavePhoto: (zoom: Float, panX: Float, panY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SettingsTopBar(
                title = "Fotoğraf önizleme",
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Dairesel Kırpma ve Grid Alanı
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                            panX += pan.x
                            panY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (imageBitmap != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val radius = canvasWidth.coerceAtMost(canvasHeight) * 0.45f
                        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)

                        // 1. Görseli zoom ve pan değerleriyle çiz
                        val scaledWidth = canvasWidth * zoom
                        val scaledHeight = canvasHeight * zoom
                        val left = center.x - (scaledWidth / 2f) + panX
                        val top = center.y - (scaledHeight / 2f) + panY

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(scaledWidth.roundToInt(), scaledHeight.roundToInt()),
                        )

                        // 2. Daire dışını yarı saydam karart (Masking)
                        val circlePath = Path().apply {
                            addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                        }
                        clipPath(circlePath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.55f))
                        }

                        // 3. Daire çerçevesi
                        drawCircle(
                            color = FeniqoPureWhite.copy(alpha = 0.85f),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        )

                        // 4. Kılavuz çizgileri (Grid 3x3)
                        clipPath(circlePath) {
                            val oneThird = (radius * 2) / 3
                            val leftX = center.x - radius
                            val topY = center.y - radius

                            // Dikey çizgiler
                            drawLine(
                                color = FeniqoPureWhite.copy(alpha = 0.4f),
                                start = Offset(leftX + oneThird, topY),
                                end = Offset(leftX + oneThird, topY + radius * 2),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawLine(
                                color = FeniqoPureWhite.copy(alpha = 0.4f),
                                start = Offset(leftX + oneThird * 2, topY),
                                end = Offset(leftX + oneThird * 2, topY + radius * 2),
                                strokeWidth = 1.dp.toPx(),
                            )
                            // Yatay çizgiler
                            drawLine(
                                color = FeniqoPureWhite.copy(alpha = 0.4f),
                                start = Offset(leftX, topY + oneThird),
                                end = Offset(leftX + radius * 2, topY + oneThird),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawLine(
                                color = FeniqoPureWhite.copy(alpha = 0.4f),
                                start = Offset(leftX, topY + oneThird * 2),
                                end = Offset(leftX + radius * 2, topY + oneThird * 2),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = FeniqoSageGreen,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Zoom Slider Bar (- / Slider / +)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                IconButton(
                    onClick = { zoom = (zoom - 0.25f).coerceIn(1f, 4f) },
                    modifier = Modifier.size(FeniqoTouchTarget.Minimum),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Uzaklaştır",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 1f..4f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = FeniqoSageGreen,
                        activeTrackColor = FeniqoSageGreen,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                IconButton(
                    onClick = { zoom = (zoom + 0.25f).coerceIn(1f, 4f) },
                    modifier = Modifier.size(FeniqoTouchTarget.Minimum),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Yakınlaştır",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

            // Eylem Butonları: "Yeniden seç" ve "Fotoğrafı kaydet"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                OutlinedButton(
                    onClick = onReselect,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .weight(1f)
                        .height(FeniqoTouchTarget.PrimaryAction),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = "Yeniden seç",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = { onSavePhoto(zoom, panX, panY) },
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = FeniqoPureWhite,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text(
                        text = "Fotoğrafı kaydet",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Yalnızca Bu Cihazda Saklanır Güvencesi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = FeniqoSpacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Yalnızca bu cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
