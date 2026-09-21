package com.feniqo.mobile.presentation.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.CategoryTonalIcon

/**
 * 05 ve 08 Numaralı onaylı tasarım ekranları: Kategori dağılımı (Gider ve Gelir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBreakdownReportScreen(
    periodLabel: String,
    totalExpenseFormatted: String,
    totalIncomeFormatted: String,
    expenseTransactionCount: Int,
    incomeTransactionCount: Int,
    expenseCategories: List<CategoryBreakdownUiItem>,
    incomeCategories: List<CategoryBreakdownUiItem>,
    onSelectCategory: (EntityId?, String) -> Unit,
    onChangePeriod: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Gider, 1: Gelir
    val isExpense = selectedTab == 0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F5F0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F5F0))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color(0xFF303536),
                        )
                    }
                    Text(
                        text = "Kategori dağılımı",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = Color(0xFF303536),
                    )
                }

                // Dönem Seçici Çipi
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChangePeriod() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF6B7280),
                            )
                            Text(text = periodLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF303536))
                        }
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF6B7280),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Gider / Gelir Sekmesi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE5E7EB))
                        .padding(3.dp),
                ) {
                    val activeBg = Color(0xFF2D5A43)
                    val activeText = Color.White
                    val inactiveText = Color(0xFF6B7280)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isExpense) activeBg else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Gider", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isExpense) activeText else inactiveText)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isExpense) activeBg else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Gelir", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (!isExpense) activeText else inactiveText)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        },
    ) { innerPadding ->
        val currentCategories = if (isExpense) expenseCategories else incomeCategories
        val totalAmountFormatted = if (isExpense) totalExpenseFormatted else totalIncomeFormatted
        val txCount = if (isExpense) expenseTransactionCount else incomeTransactionCount

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Dark Hero Kartı (Donut chart ile)
            item("hero_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF303536),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = if (isExpense) "Toplam gider" else "Toplam gelir",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = totalAmountFormatted,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                ),
                                color = if (isExpense) Color(0xFFF87171) else Color(0xFF4ADE80),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$txCount işlem",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                            )
                        }

                        val slices = currentCategories.map { it.name to it.shareRatio }
                        val colors = if (isExpense) {
                            listOf(Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF1E88E5), Color(0xFF00897B))
                        } else {
                            listOf(Color(0xFF2D5A43), Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7))
                        }

                        ReportDonutChart(
                            slices = slices,
                            colors = colors,
                            centerText = "$txCount işlem",
                            modifier = Modifier.size(100.dp),
                        )
                    }
                }
            }

            // Kategori Listesi
            items(currentCategories) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onSelectCategory(item.categoryId, item.name) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryTonalIcon(
                                iconKey = item.name,
                                color = if (isExpense) Color(0xFFDC2626) else Color(0xFF16A34A),
                                containerSize = 40.dp,
                            )

                            Column {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                    color = Color(0xFF303536),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.transactionCount} işlem • ${item.sharePercentageFormatted}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280),
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = item.amountFormatted,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                ),
                                color = if (isExpense) Color(0xFFDC2626) else Color(0xFF2D5A43),
                            )
                            Text(text = "›", fontSize = 20.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * 06 ve 07 Numaralı onaylı tasarım ekranları: Kategori Detayı ve Kategori İşlemleri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailReportScreen(
    categoryName: String,
    categoryDescription: String,
    periodLabel: String,
    totalSpendingFormatted: String,
    transactionCount: Int,
    periodShareFormatted: String,
    weeklyData: List<Pair<String, Pair<Long, Long>>>,
    previousMonthComparisonText: String,
    merchantBreakdown: List<Pair<String, Pair<String, String>>>, // (MerchantName, (AmountFormatted, ShareFormatted))
    transactions: List<Transaction>,
    onNavigateToTransactions: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F5F0),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF303536),
                    )
                }
                Column {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = Color(0xFF303536),
                    )
                    Text(text = categoryDescription, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onNavigateToTransactions,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D5A43)),
                    ) {
                        Text(text = "İşlemleri gör", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Dark Hero Kartı
            item("hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF303536),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "Toplam harcama", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = totalSpendingFormatted,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                            color = Color(0xFFF87171),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$transactionCount işlem • Dönem payı $periodShareFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                        )
                    }
                }
            }

            // Haftalık Harcama Trendi Bar Grafiği
            item("weekly_chart") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Haftalık harcama trendi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                        Spacer(modifier = Modifier.height(14.dp))
                        WeeklyDualBarChart(weeklyData = weeklyData)
                    }
                }
            }

            // Geçen Aya Göre Kıyas Kartı
            if (previousMonthComparisonText.isNotBlank()) {
                item("comparison") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color(0xFFDC2626),
                            )
                            Text(text = previousMonthComparisonText, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF303536))
                        }
                    }
                }
            }

            // İş Yeri Kırılımı (Merchant breakdown)
            if (merchantBreakdown.isNotEmpty()) {
                item("merchant_title") {
                    Text(text = "İş yeri kırılımı", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                }

                items(merchantBreakdown) { (merchant, info) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = Color(0xFFF3F4F6)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Storefront,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = Color(0xFF303536),
                                        )
                                    }
                                }
                                Column {
                                    Text(text = merchant, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF303536))
                                    Text(text = info.second, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                }
                            }
                            Text(text = info.first, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFDC2626))
                        }
                    }
                }
            }

            item("spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
