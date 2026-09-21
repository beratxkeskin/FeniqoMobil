package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Money
import kotlin.math.max

private val SageGreen = Color(0xFF2D5A43)
private val LightSage = Color(0xFFE2EBE6)
private val ExpenseRed = Color(0xFFDC2626)
private val NeutralGraphite = Color(0xFF303536)
private val CardBorder = Color(0xFFE5E7EB)

@Composable
fun TrendDualLineChart(
    points: List<MonthlyTrendUiModel>,
    modifier: Modifier = Modifier,
    accessibleDescription: String = "Aylık gelir ve gider trend grafiği",
) {
    if (points.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibleDescription },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (points.size > 1) w / (points.size - 1) else w

            val maxVal = points.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 1L
            val safeMax = if (maxVal > 0) maxVal.toFloat() else 1f

            val incPath = Path()
            val expPath = Path()

            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val yInc = h - (pt.incomeMinor.toFloat() / safeMax * (h - 20f)) - 10f
                val yExp = h - (pt.expenseMinor.toFloat() / safeMax * (h - 20f)) - 10f

                if (i == 0) {
                    incPath.moveTo(x, yInc)
                    expPath.moveTo(x, yExp)
                } else {
                    incPath.lineTo(x, yInc)
                    expPath.lineTo(x, yExp)
                }
            }

            // Gelir çizgisi (Yeşil)
            drawPath(
                path = incPath,
                color = SageGreen,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Gider çizgisi (Kırmızı)
            drawPath(
                path = expPath,
                color = ExpenseRed,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )

            // Noktalar
            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val yInc = h - (pt.incomeMinor.toFloat() / safeMax * (h - 20f)) - 10f
                val yExp = h - (pt.expenseMinor.toFloat() / safeMax * (h - 20f)) - 10f

                drawCircle(color = SageGreen, radius = 4.dp.toPx(), center = Offset(x, yInc))
                drawCircle(color = ExpenseRed, radius = 4.dp.toPx(), center = Offset(x, yExp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ay etiketleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.monthLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

@Composable
fun WeeklyDualBarChart(
    weeklyData: List<Pair<String, Pair<Long, Long>>>, // (WeekLabel, (IncomeMinor, ExpenseMinor))
    modifier: Modifier = Modifier,
    accessibleDescription: String = "Haftalık gelir ve gider sütun grafiği",
) {
    if (weeklyData.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibleDescription },
    ) {
        val maxMinor = weeklyData.maxOfOrNull { max(it.second.first, it.second.second) } ?: 1L
        val safeMax = if (maxMinor > 0) maxMinor.toFloat() else 1f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            weeklyData.forEach { (_, pair) ->
                val incHeightRatio = (pair.first.toFloat() / safeMax).coerceIn(0.05f, 1f)
                val expHeightRatio = (pair.second.toFloat() / safeMax).coerceIn(0.05f, 1f)

                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(incHeightRatio)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(SageGreen),
                    )
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(expHeightRatio)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ExpenseRed),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            weeklyData.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

@Composable
fun ReportDonutChart(
    slices: List<Pair<String, Float>>, // (CategoryName, Ratio 0..1)
    colors: List<Color>,
    centerText: String,
    modifier: Modifier = Modifier,
    accessibleDescription: String = "Kategori dağılımı pasta grafiği",
) {
    Box(
        modifier = modifier
            .size(140.dp)
            .semantics { contentDescription = accessibleDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            var startAngle = -90f

            if (slices.isEmpty()) {
                drawArc(
                    color = Color(0xFFE5E7EB),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                )
            } else {
                slices.forEachIndexed { i, (_, ratio) ->
                    val sweep = (ratio * 360f).coerceAtLeast(0f)
                    val color = colors.getOrElse(i) { SageGreen }
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += sweep
                }
            }
        }

        Text(
            text = centerText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = NeutralGraphite,
        )
    }
}

@Composable
fun BidirectionalCashFlowChart(
    points: List<CashFlowMonthUiItem>,
    modifier: Modifier = Modifier,
    accessibleDescription: String = "Çift yönlü nakit akışı grafiği",
) {
    if (points.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibleDescription },
    ) {
        val maxVal = points.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 1L
        val safeMax = if (maxVal > 0) maxVal.toFloat() else 1f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            val barW = 12.dp.toPx()
            val stepX = w / points.size

            // Sıfır çizgisi
            drawLine(
                color = Color(0xFFE5E7EB),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx(),
            )

            val netPath = Path()

            points.forEachIndexed { i, pt ->
                val centerX = i * stepX + stepX / 2f
                val incH = (pt.incomeMinor.toFloat() / safeMax) * (midY - 10f)
                val expH = (pt.expenseMinor.toFloat() / safeMax) * (midY - 10f)

                // Gelir yukarı barı
                drawRoundRect(
                    color = SageGreen,
                    topLeft = Offset(centerX - barW - 2f, midY - incH),
                    size = Size(barW, incH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                // Gider aşağı barı
                drawRoundRect(
                    color = ExpenseRed,
                    topLeft = Offset(centerX + 2f, midY),
                    size = Size(barW, expH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )

                // Net nokta
                val netH = ((pt.incomeMinor - pt.expenseMinor).toFloat() / safeMax) * (midY - 10f)
                val netY = midY - netH
                if (i == 0) netPath.moveTo(centerX, netY) else netPath.lineTo(centerX, netY)
            }

            // Net trend çizgisi
            drawPath(
                path = netPath,
                color = Color(0xFF10B981),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Net noktaları
            points.forEachIndexed { i, pt ->
                val centerX = i * stepX + stepX / 2f
                val netH = ((pt.incomeMinor - pt.expenseMinor).toFloat() / safeMax) * (midY - 10f)
                val netY = midY - netH
                drawCircle(color = Color(0xFF10B981), radius = 3.dp.toPx(), center = Offset(centerX, netY))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.monthLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

/**
 * Rapor ekranlarında kullanılan standart yükseltilmiş beyaz yüzey kartı.
 */
@Composable
fun ReportCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = 1.dp,
    ) {
        content()
    }
}
