package com.feniqo.mobile.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

data class HelpCategory(
    val id: String,
    val title: String,
    val articles: List<HelpArticle>,
)

data class HelpArticle(
    val id: String,
    val title: String,
    val status: String = "Hazırlanıyor",
)

/**
 * Pano B1: Yardım Merkezi Ekranı.
 *
 * Arama ve akordeon kategoriler sunar. Doğrulanmış gerçek metinler olmadığı sürece
 * uydurma cevap üretmez; makaleleri dürüstçe "Hazırlanıyor" olarak sunar.
 */
@Composable
fun HelpCenterScreen(
    onBack: () -> Unit,
    onArticleClick: (articleTitle: String) -> Unit,
    onNavigateToFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategoryId by remember { mutableStateOf<String?>("privacy_security") }

    val categories = remember {
        listOf(
            HelpCategory(
                id = "account_login",
                title = "Hesap ve giriş",
                articles = listOf(
                    HelpArticle("acc_1", "Hesap nasıl oluşturulur?"),
                    HelpArticle("acc_2", "Parolamı unuttum, ne yapmalıyım?"),
                ),
            ),
            HelpCategory(
                id = "transactions_categories",
                title = "İşlemler ve kategoriler",
                articles = listOf(
                    HelpArticle("txn_1", "Gelir ve gider nasıl eklenir?"),
                    HelpArticle("txn_2", "Özel kategori nasıl oluşturulur?"),
                ),
            ),
            HelpCategory(
                id = "backup_sync",
                title = "Yedekleme ve senkronizasyon",
                articles = listOf(
                    HelpArticle("sync_1", "Çevrimdışı kayıtlar ne zaman eşitlenir?"),
                    HelpArticle("sync_2", "Verilerimi nasıl yedekleyebilirim?"),
                ),
            ),
            HelpCategory(
                id = "privacy_security",
                title = "Gizlilik ve güvenlik",
                articles = listOf(
                    HelpArticle("sec_1", "Hesap verilerim güvende mi?"),
                    HelpArticle("sec_2", "Verilerim nasıl saklanıyor?"),
                ),
            ),
        )
    }

    val filteredArticles = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else categories.flatMap { it.articles }.filter {
            it.title.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
        ) {
            SettingsTopBar(
                title = "",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                contentPadding = PaddingValues(bottom = FeniqoSpacing.Large),
            ) {
                // Feniqo Marka Logosu & Başlık
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Eco,
                                contentDescription = null,
                                tint = FeniqoSageGreen,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "feniqo",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoSageGreen,
                            )
                        }
                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                        Text(
                            text = "Yardım merkezi",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sorularına hızlıca yanıt bul, ihtiyacın olduğunda bizimle iletişime geç.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Arama Kutusu ("Nasıl yardımcı olabiliriz?")
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Nasıl yardımcı olabiliriz?") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Temizle")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (searchQuery.isNotBlank()) {
                    // Arama Sonuçları Listesi
                    if (filteredArticles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = FeniqoSpacing.ExtraLarge),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Eşleşen yardım içeriği bulunamadı.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredArticles) { article ->
                            Card(
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onArticleClick(article.title) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(FeniqoSpacing.Large),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Description, null, tint = FeniqoSageGreen)
                                    Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(article.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(article.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    // Akordeon Kategorileri
                    categories.forEach { category ->
                        item {
                            val isExpanded = expandedCategoryId == category.id
                            Card(
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategoryId = if (isExpanded) null else category.id
                                            }
                                            .padding(FeniqoSpacing.Large),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Kapat" else "Aç",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                        ) {
                                            category.articles.forEach { article ->
                                                Surface(
                                                    shape = RoundedCornerShape(FeniqoRadius.Small),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onArticleClick(article.title) },
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(FeniqoSpacing.Medium),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Description,
                                                            contentDescription = null,
                                                            tint = FeniqoSageGreen,
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = article.title,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                            )
                                                            Text(
                                                                text = article.status,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }

                                            // Destek kartı (yalnızca Gizlilik ve güvenlik altında veya alt kısımda)
                                            if (category.id == "privacy_security") {
                                                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                                                Surface(
                                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onNavigateToFeedback() },
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(FeniqoSpacing.Large),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                                            contentDescription = null,
                                                            tint = FeniqoSageGreen,
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "Sorununu bildirebilirsin",
                                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                            Text(
                                                                text = "Yaşadığın bir sorun mu var? Ekibimize bildir, birlikte çözelim.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
            }
        }
    }
}

/**
 * Pano B2: Hazırlanan Makale Durumu Ekranı.
 *
 * Henüz onaylı metni olmayan başlıklar için dürüst boş durum sunar.
 */
@Composable
fun HelpArticleStatusScreen(
    articleTitle: String,
    onBack: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
        ) {
            SettingsTopBar(
                title = "Yardım merkezi",
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Büyük dairesel açık yeşil zemin + belge ikonu
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(FeniqoSageGreenContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Article,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(56.dp),
                    )
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraLarge))

                Text(
                    text = "Bu içerik hazırlanıyor",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                Text(
                    text = "Doğrulanmış yardım metni yayımlandığında burada görünecek.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium),
                )
            }

            // Alt Butonlar: "Geri dön" ve "Geri bildirim gönder"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = FeniqoPureWhite,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text("Geri dön", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onNavigateToFeedback,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Geri bildirim gönder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Pano B3: Gizlilik ve Hukuki Bilgiler Ekranı.
 *
 * Onaylı metin bekleniyor durumlarını şeffaf sunar.
 */
@Composable
fun LegalInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLicensesDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
        ) {
            SettingsTopBar(
                title = "Gizlilik ve hukuki bilgiler",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.Medium),
            ) {
                item {
                    Text(
                        text = "Feniqo'da şeffaflık senin için önemli. Hukuki belgeler hazır olduğunda bu bölümden erişebilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Hukuki Belgeler Grubu
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = "Gizlilik politikası",
                            subtitle = "Henüz onaylı belge sunulmadı. Bağlantı hazır olduğunda bu bölümden erişilebilir.",
                            badgeText = "Onaylı metin bekleniyor",
                            badgeColor = Color(0xFF757575),
                            icon = Icons.Outlined.Policy,
                            onClick = {},
                        )
                        SettingsRowItem(
                            title = "Kullanım koşulları",
                            subtitle = "Henüz onaylı belge sunulmadı. Bağlantı hazır olduğunda bu bölümden erişilebilir.",
                            badgeText = "Onaylı metin bekleniyor",
                            badgeColor = Color(0xFF757575),
                            icon = Icons.Outlined.Description,
                            showDivider = false,
                            onClick = {},
                        )
                    }
                }

                // Açık Kaynak Lisanslar Grubu
                item {
                    Column {
                        Text(
                            text = "Açık kaynak lisanslar",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Uygulamada kullanılan açık kaynak yazılımlar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = "Açık kaynak lisanslar",
                            badgeText = "Hazırlanıyor",
                            badgeColor = Color(0xFF757575),
                            icon = Icons.Outlined.Code,
                            showDivider = false,
                            onClick = { showLicensesDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Açık kaynak lisanslar") },
            text = {
                Text(
                    "FeniqoMobil; Kotlin, Jetpack Compose, AndroidX, Kotlinx Coroutines, Room ve Supabase-kt gibi açık kaynak kütüphaneler kullanır. Tam yasal lisans metinleri ve telif bildirimleri derleme paketine dahil edilmek üzere hazırlanmaktadır."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Tamam", color = FeniqoSageGreen)
                }
            },
        )
    }
}
