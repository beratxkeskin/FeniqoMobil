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
    val paragraphs: List<String>,
    val status: String = "Yayında",
)

internal val publishedHelpCategories = listOf(
    HelpCategory(
        id = "account_login",
        title = "Hesap ve giriş",
        articles = listOf(
            HelpArticle("acc_1", "Hesap nasıl oluşturulur?", listOf(
                "Karşılama ekranından Kayıt Ol'u seç. E-posta adresini ve güçlü bir parola girdikten sonra hesabını oluşturabilirsin.",
                "E-posta doğrulama iletisi geldiyse bağlantıyı açıp uygulamaya dön. Doğrulama tamamlandığında aynı e-posta ve parolayla giriş yapabilirsin.",
            )),
            HelpArticle("acc_2", "Parolamı unuttum, ne yapmalıyım?", listOf(
                "Giriş ekranındaki Parolamı Unuttum bağlantısını aç ve hesap e-posta adresini gir.",
                "E-postadaki güvenli bağlantı Feniqo'yu parola yenileme ekranında açar. Yeni parolanı kaydettikten sonra giriş ekranından devam edebilirsin. Bağlantı süresi dolmuşsa yeni bir ileti iste.",
            )),
        ),
    ),
    HelpCategory(
        id = "transactions_categories",
        title = "İşlemler ve kategoriler",
        articles = listOf(
            HelpArticle("txn_1", "Gelir ve gider nasıl eklenir?", listOf(
                "Alt çubuktaki + düğmesine dokunup Gider veya Gelir'i seç. Tutarı, işlem adını, kategoriyi ve tarihi doldurup kaydet.",
                "Kayıt önce cihazındaki güvenli yerel veritabanına yazılır. İnternet yoksa kullanmaya devam edebilir, bağlantı geri geldiğinde eşitlemenin tamamlanmasını bekleyebilirsin.",
            )),
            HelpArticle("txn_2", "Özel kategori nasıl oluşturulur?", listOf(
                "Daha Fazla > Kategoriler bölümüne gir ve yeni kategori eylemini seç. Gelir veya gider türünü, adı, rengi ve ikonu belirleyip kaydet.",
                "Kullanılmış özel kategoriler veri bütünlüğünü korumak için kalıcı olarak silinmek yerine arşivlenebilir.",
            )),
        ),
    ),
    HelpCategory(
        id = "backup_sync",
        title = "Yedekleme ve senkronizasyon",
        articles = listOf(
            HelpArticle("sync_1", "Çevrimdışı kayıtlar ne zaman eşitlenir?", listOf(
                "Çevrimdışıyken yaptığın değişiklikler cihazda saklanır ve eşitleme kuyruğuna eklenir. Ağ geri geldiğinde uygulama bunları güvenli sırayla göndermeyi dener.",
                "Bekleyen, başarısız veya çakışan kayıtları Profil Merkezi ya da Veri Yönetimi içindeki senkronizasyon durumundan inceleyebilirsin. Çakışmalar kullanıcı kararı olmadan sessizce ezilmez.",
            )),
            HelpArticle("sync_2", "Verilerimi nasıl yedekleyebilirim?", listOf(
                "Uygulama Ayarları > Veri Yönetimi bölümünden kişisel kategori ve işlemlerini JSON yedeği olarak dışa aktarabilirsin.",
                "Yedek; parola, oturum anahtarı, makbuz yolu veya senkronizasyon metadata'sı içermez. İçe aktarmadan önce dosyanın özeti gösterilir ve açık onayın istenir.",
            )),
        ),
    ),
    HelpCategory(
        id = "privacy_security",
        title = "Gizlilik ve güvenlik",
        articles = listOf(
            HelpArticle("sec_1", "Hesap verilerim güvende mi?", listOf(
                "Finansal veriler kullanıcı ve çalışma alanı sahipliği kurallarıyla ayrılır. Mobil uygulamada service-role anahtarı bulunmaz; uzak erişim doğrulanmış kullanıcı oturumuyla yapılır.",
                "Uygulama hassas finansal içeriği, parolayı, tokenı veya makbuz metnini günlük kayıtlarına yazmaz.",
            )),
            HelpArticle("sec_2", "Verilerim nasıl saklanıyor?", listOf(
                "Ekranlar veriyi doğrudan ağdan değil, cihazdaki Room veritabanından okur. Yerel değişiklik ve eşitleme kuyruğu aynı veritabanı işlemi içinde kaydedilir.",
                "Silme işlemleri cihazlar arası tutarlılık için soft-delete olarak eşitlenir. Aynı kayıt iki cihazda değişirse iki kopya karar verene kadar korunur.",
            )),
        ),
    ),
)

/**
 * Pano B1: Yardım Merkezi Ekranı.
 *
 * Arama ve akordeon kategorilerle ürün sözleşmesinden türetilen yardım içeriklerini sunar.
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

    val categories = remember { publishedHelpCategories }

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
 * Pano B2: yayımlanmış yardım makalesi ekranı.
 */
@Composable
fun HelpArticleStatusScreen(
    articleTitle: String,
    onBack: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = remember(articleTitle) {
        publishedHelpCategories.flatMap { it.articles }.firstOrNull { it.title == articleTitle }
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
                title = "Yardım merkezi",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.ExtraLarge),
            ) {
                item { Box(
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
                } }

                item { Text(
                    text = article?.title ?: "Yardım içeriği bulunamadı",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                ) }

                if (article != null) {
                    items(article.paragraphs) { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Yardım merkezine dönerek yayımlanmış bir başlık seçebilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Outlined.Policy,
                            onClick = {},
                        )
                        SettingsRowItem(
                            title = "Kullanım koşulları",
                            subtitle = "Henüz onaylı belge sunulmadı. Bağlantı hazır olduğunda bu bölümden erişilebilir.",
                            badgeText = "Onaylı metin bekleniyor",
                            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            subtitle = "Kullanılan temel açık kaynak bileşenleri ve lisansları",
                            badgeText = "Görüntüle",
                            badgeColor = FeniqoSageGreen,
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "FeniqoMobil aşağıdaki temel açık kaynak projelerden yararlanır. Lisansların tam metinleri ilgili proje dağıtımlarında ve kaynak depolarında yer alır.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(
                        listOf(
                            "Kotlin — Apache License 2.0",
                            "Jetpack Compose ve AndroidX — Apache License 2.0",
                            "Kotlinx Coroutines, Serialization ve DateTime — Apache License 2.0",
                            "Ktor — Apache License 2.0",
                            "Room — Apache License 2.0",
                            "Supabase Kotlin — MIT License",
                            "SQLCipher Community Edition — BSD-style License",
                        ),
                    ) { notice ->
                        Text("• $notice", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Tamam", color = FeniqoSageGreen)
                }
            },
        )
    }
}
