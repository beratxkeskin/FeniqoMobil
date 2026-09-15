package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.goal.*
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

@Composable
fun GoalDetailScreen(state: GoalDetailUiState, onBack: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (state.detail != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Button(onAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Para Ekle", style = MaterialTheme.typography.titleMedium)
            }
        }
    }) { inner ->
        when {
            state.isLoading -> LoadingContent(Modifier.fillMaxSize().padding(inner), "Hedef yükleniyor…")
            state.isNotFound -> ErrorState("Hedef Bulunamadı", "Hedef silinmiş veya bu çalışma alanında bulunmuyor.", onBack, Modifier.fillMaxSize().padding(inner))
            state.observationError != null && state.detail == null -> ErrorState("Hedef Yüklenemedi", state.observationError.toDisplayText(), onBack, Modifier.fillMaxSize().padding(inner))
            state.detail != null -> DetailContent(state.detail, onBack, onEdit, onAdd, onRemove, onDelete, Modifier.padding(inner))
        }
    }
}

@Composable
private fun DetailContent(d: GoalDetailDisplayModel, onBack: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }; var expanded by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack, Modifier.height(48.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("Hedefler", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f)); Box { IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Hedef menüsü") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Hedefi düzenle") }, { menu=false; onEdit() }, leadingIcon={Icon(Icons.Outlined.Edit,null)}); DropdownMenuItem({ Text("Hedefi sil") }, { menu=false; onDelete() }, leadingIcon={Icon(Icons.Outlined.Delete,null)}) } }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(Modifier.size(82.dp), RoundedCornerShape(20.dp), MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment=Alignment.Center) { Text(d.goal.icon?.key?.take(2)?.uppercase() ?: "HE", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onPrimaryContainer) } }
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(d.goal.name, style=FeniqoTypographyTokens.DisplayTitle, maxLines=2, overflow=TextOverflow.Ellipsis); Surface(shape=RoundedCornerShape(50), color=MaterialTheme.colorScheme.primaryContainer) { Text(d.statusLabel, Modifier.padding(horizontal=10.dp, vertical=4.dp), style=MaterialTheme.typography.labelMedium) } }
        } }
        item { ProgressSummary(d) }
        item { QuickActions(onAdd,onRemove,onEdit) }
        d.insight?.let { item { InsightCard(it) } }
        item { SupportingMetrics(d) }
        item { ChartCard(d) }
        item { LuxuryCard { Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text("Son Hareketler",style=MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); if(d.recentContributions.size>3) TextButton({expanded=!expanded}){Text(if(expanded)"Daralt" else "Tümünü gör")} }; if(d.recentContributions.isEmpty()) Text("Henüz hareket yok.",color=MaterialTheme.colorScheme.onSurfaceVariant) else d.recentContributions.take(if(expanded)d.recentContributions.size else 3).forEach{Movement(it)} } }
    }
}

@Composable private fun ProgressSummary(d:GoalDetailDisplayModel)=LuxuryCard {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom){AmountMetric("Biriken",d.formattedCurrent,Modifier.weight(1f),false);AmountMetric("Hedef",d.formattedTarget,Modifier.weight(1f),true)}
    LinearProgressIndicator({d.progress.value.coerceIn(0,10_000)/10_000f},Modifier.fillMaxWidth().height(8.dp).semantics{contentDescription="Hedef ilerlemesi yüzde ${d.progress.value/100}"},color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh,strokeCap=androidx.compose.ui.graphics.StrokeCap.Round)
    Row(Modifier.fillMaxWidth()){Text("${d.formattedRemaining} kaldı",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.weight(1f));Text("%${d.progress.value/100}",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}
    Text("Hedef tarihi · ${DateFormatter.formatReadableDate(d.goal.targetDate)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun AmountMetric(label:String,value:String,modifier:Modifier,end:Boolean)=Column(modifier,horizontalAlignment=if(end)Alignment.End else Alignment.Start){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=if(end)TextAlign.End else TextAlign.Start)}

@Composable private fun QuickActions(add:()->Unit,remove:()->Unit,edit:()->Unit)=Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){QuickAction(Icons.Outlined.Add,"Para Ekle",MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primaryContainer,add);QuickAction(Icons.Outlined.Remove,"Para Çıkar",MaterialTheme.colorScheme.error,MaterialTheme.colorScheme.errorContainer,remove);QuickAction(Icons.Outlined.Edit,"Düzenle",MaterialTheme.colorScheme.onSurfaceVariant,MaterialTheme.colorScheme.surfaceContainerHigh,edit)}
@Composable private fun QuickAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,color:androidx.compose.ui.graphics.Color,container:androidx.compose.ui.graphics.Color,click:()->Unit)=Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.widthIn(min=82.dp).semantics{contentDescription=label}){FilledIconButton(click,Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=container,contentColor=color)){Icon(icon,null)};Text(label,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=3.dp))}

@Composable private fun InsightCard(text:String)=Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){Icon(Icons.Outlined.Lightbulb,null,tint=MaterialTheme.colorScheme.primary);Column{Text("Feniqo İçgörü",style=MaterialTheme.typography.titleSmall);Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)}}}

@Composable private fun SupportingMetrics(d:GoalDetailDisplayModel)=Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){d.monthlyRequired?.let{CompactMetric("Aylık gereken ortalama",MoneyFormatter.format(it),"Hedef tarihine yetişmek için",Icons.Outlined.CalendarMonth,Modifier.weight(1f))};CompactMetric(if(d.estimatedCompletion!=null)"Katkı hızına göre tahmin" else "Hedef zamanı",d.estimatedCompletion?.let(DateFormatter::formatReadableDate)?:d.daysRemaining?.let{"$it gün kaldı"}?:"Tarih geçti",if(d.estimatedCompletion!=null)"Yaklaşık sonuç" else "Kalan süre",Icons.Outlined.TrendingUp,Modifier.weight(1f))}
@Composable private fun CompactMetric(title:String,value:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier)=LuxuryCard(modifier,12.dp){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp));Text(title,style=MaterialTheme.typography.labelMedium,maxLines=2);Text(value,style=MaterialTheme.typography.titleSmall,maxLines=2);Text(subtitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}

@Composable private fun ChartCard(d:GoalDetailDisplayModel)=LuxuryCard {
    Text("Birikim İlerlemesi",style=MaterialTheme.typography.titleMedium);val points=d.chart
    if(points.isNullOrEmpty()) Text("Henüz ilerleme hareketi yok.\nPara eklediğinde gelişimini burada görebilirsin.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) else {
        val max=maxOf(d.goal.targetAmount.amountMinor,points.maxOf{it.amount.amountMinor},1L);val mid=max/2;val grid=MaterialTheme.colorScheme.outlineVariant;val line=MaterialTheme.colorScheme.primary;val target=MaterialTheme.colorScheme.outline
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.width(48.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                AxisMoney(max, d.goal.targetAmount.currency)
                AxisMoney(mid, d.goal.targetAmount.currency)
                AxisMoney(0, d.goal.targetAmount.currency)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "${points.size} tarih noktası. Son birikim ${d.formattedCurrent}, hedef ${d.formattedTarget}." }) {
                    fun y(value: Long) = size.height - (value.toFloat() / max.toFloat()) * size.height
                    listOf(0L, mid, max).forEach { value -> drawLine(grid, Offset(0f, y(value)), Offset(size.width, y(value)), strokeWidth = 1f) }
                    drawLine(target, Offset(0f, y(d.goal.targetAmount.amountMinor)), Offset(size.width, y(d.goal.targetAmount.amountMinor)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
                    val divisor = (points.size - 1).coerceAtLeast(1)
                    points.zipWithNext().forEachIndexed { index, pair -> drawLine(line, Offset(size.width * index / divisor, y(pair.first.amount.amountMinor)), Offset(size.width * (index + 1) / divisor, y(pair.second.amount.amountMinor)), strokeWidth = 5f) }
                    points.forEachIndexed { index, point -> drawCircle(line, 6f, Offset(size.width * index / divisor, y(point.amount.amountMinor))) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AxisDate(points.first().date)
                    if (points.size > 2) AxisDate(points[points.lastIndex / 2].date)
                    AxisDate(points.last().date)
                }
            }
        }
        Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.align(Alignment.End)){Text("${d.formattedCurrent}  ·  %${d.progress.value/100}",Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)}
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Legend(MaterialTheme.colorScheme.primary,"Biriken");Legend(MaterialTheme.colorScheme.outline,"Hedef")}
    }
}
@Composable private fun AxisMoney(amount:Long,currency:Currency){val value=MoneyFormatter.format(Money(amount,currency));Text(if(value.length>8)value.take(6)+"…" else value,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
@Composable private fun AxisDate(date:LocalDate)=Text("${date.day}.${date.monthNumber}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
@Composable private fun Legend(color:androidx.compose.ui.graphics.Color,label:String)=Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(8.dp),CircleShape,color){};Spacer(Modifier.width(4.dp));Text(label,style=MaterialTheme.typography.labelSmall)}
@Composable private fun Movement(c:GoalContribution){val add=c.direction==GoalContributionDirection.ADD;Row(Modifier.fillMaxWidth().semantics{contentDescription="${if(add)"Para eklendi" else "Para çıkarıldı"}, ${MoneyFormatter.format(c.amount)}, ${DateFormatter.formatReadableDate(c.occurredOn)}"}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(32.dp),CircleShape,if(add)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer){Box(contentAlignment=Alignment.Center){Icon(if(add)Icons.Outlined.Add else Icons.Outlined.Remove,null,Modifier.size(18.dp),tint=if(add)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(c.note?:if(add)"Para eklendi" else "Para çıkarıldı",style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(DateFormatter.formatReadableDate(c.occurredOn),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text((if(add)"+" else "−")+MoneyFormatter.format(c.amount),color=if(add)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)}}
@Composable private fun LuxuryCard(modifier:Modifier=Modifier,padding:Dp=14.dp,content:@Composable ColumnScope.()->Unit)=Card(modifier.fillMaxWidth(),RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.75f)),elevation=CardDefaults.cardElevation(1.dp)){Column(Modifier.padding(padding),verticalArrangement=Arrangement.spacedBy(7.dp),content=content)}
