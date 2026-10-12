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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class HelpArticleStatus {
    PUBLISHED,
}

data class HelpCategory(
    val id: String,
    val titleRes: StringResource,
    val articles: List<HelpArticle>,
)

data class HelpArticle(
    val id: String,
    val titleRes: StringResource,
    val paragraphResList: List<StringResource>,
    val status: HelpArticleStatus = HelpArticleStatus.PUBLISHED,
)

internal val publishedHelpCategories = listOf(
    HelpCategory(
        id = "account_login",
        titleRes = Res.string.help_category_account_login,
        articles = listOf(
            HelpArticle(
                id = "acc_1",
                titleRes = Res.string.help_article_acc_1_title,
                paragraphResList = listOf(
                    Res.string.help_article_acc_1_p1,
                    Res.string.help_article_acc_1_p2,
                ),
            ),
            HelpArticle(
                id = "acc_2",
                titleRes = Res.string.help_article_acc_2_title,
                paragraphResList = listOf(
                    Res.string.help_article_acc_2_p1,
                    Res.string.help_article_acc_2_p2,
                ),
            ),
        ),
    ),
    HelpCategory(
        id = "transactions_categories",
        titleRes = Res.string.help_category_transactions_categories,
        articles = listOf(
            HelpArticle(
                id = "txn_1",
                titleRes = Res.string.help_article_txn_1_title,
                paragraphResList = listOf(
                    Res.string.help_article_txn_1_p1,
                    Res.string.help_article_txn_1_p2,
                ),
            ),
            HelpArticle(
                id = "txn_2",
                titleRes = Res.string.help_article_txn_2_title,
                paragraphResList = listOf(
                    Res.string.help_article_txn_2_p1,
                    Res.string.help_article_txn_2_p2,
                ),
            ),
        ),
    ),
    HelpCategory(
        id = "backup_sync",
        titleRes = Res.string.help_category_backup_sync,
        articles = listOf(
            HelpArticle(
                id = "sync_1",
                titleRes = Res.string.help_article_sync_1_title,
                paragraphResList = listOf(
                    Res.string.help_article_sync_1_p1,
                    Res.string.help_article_sync_1_p2,
                ),
            ),
            HelpArticle(
                id = "sync_2",
                titleRes = Res.string.help_article_sync_2_title,
                paragraphResList = listOf(
                    Res.string.help_article_sync_2_p1,
                    Res.string.help_article_sync_2_p2,
                ),
            ),
        ),
    ),
    HelpCategory(
        id = "privacy_security",
        titleRes = Res.string.help_category_privacy_security,
        articles = listOf(
            HelpArticle(
                id = "sec_1",
                titleRes = Res.string.help_article_sec_1_title,
                paragraphResList = listOf(
                    Res.string.help_article_sec_1_p1,
                    Res.string.help_article_sec_1_p2,
                ),
            ),
            HelpArticle(
                id = "sec_2",
                titleRes = Res.string.help_article_sec_2_title,
                paragraphResList = listOf(
                    Res.string.help_article_sec_2_p1,
                    Res.string.help_article_sec_2_p2,
                ),
            ),
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
    onArticleClick: (articleId: String) -> Unit,
    onNavigateToFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategoryId by remember { mutableStateOf<String?>("privacy_security") }

    val categories = remember { publishedHelpCategories }

    // Çözülmüş başlıklarla arama için mapping
    val articlesWithTitles = categories.flatMap { it.articles }.map { article ->
        Triple(article, stringResource(article.titleRes), stringResource(article.status.toLocalizedStatusRes()))
    }

    val filteredArticles = remember(searchQuery, articlesWithTitles) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            articlesWithTitles.filter { (_, title, _) ->
                title.contains(searchQuery.trim(), ignoreCase = true)
            }
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
                            text = stringResource(Res.string.help_center_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.help_center_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Arama Kutusu
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(Res.string.help_center_search_placeholder)) },
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
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(Res.string.help_center_search_clear),
                                    )
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
                                    text = stringResource(Res.string.help_center_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredArticles) { (article, title, statusText) ->
                            Card(
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onArticleClick(article.id) },
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
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Text(
                                            text = statusText,
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
                                            text = stringResource(category.titleRes),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(
                                                if (isExpanded) Res.string.help_center_category_collapse else Res.string.help_center_category_expand,
                                            ),
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
                                                        .clickable { onArticleClick(article.id) },
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
                                                                text = stringResource(article.titleRes),
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                            )
                                                            Text(
                                                                text = stringResource(article.status.toLocalizedStatusRes()),
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

                                            // Destek kartı (yalnızca Gizlilik ve güvenlik altında)
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
                                                                text = stringResource(Res.string.help_center_feedback_card_title),
                                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                            Text(
                                                                text = stringResource(Res.string.help_center_feedback_card_subtitle),
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
    articleId: String,
    onBack: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = remember(articleId) {
        publishedHelpCategories.flatMap { it.articles }.firstOrNull { it.id == articleId }
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
                title = stringResource(Res.string.help_center_title),
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
                item {
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
                }

                item {
                    Text(
                        text = if (article != null) stringResource(article.titleRes) else stringResource(Res.string.help_article_not_found_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (article != null) {
                    items(article.paragraphResList) { paragraphRes ->
                        Text(
                            text = stringResource(paragraphRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(Res.string.help_article_not_found_desc),
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
                    Text(stringResource(Res.string.help_article_back_button), fontWeight = FontWeight.SemiBold)
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
                    Text(stringResource(Res.string.help_article_send_feedback_button), fontWeight = FontWeight.SemiBold)
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
                title = stringResource(Res.string.legal_info_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.Medium),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.legal_info_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Hukuki Belgeler Grubu
                item {
                    SettingsGroupCard {
                        defaultLegalDocuments.forEachIndexed { index, doc ->
                            SettingsRowItem(
                                title = stringResource(doc.titleRes),
                                subtitle = stringResource(doc.subtitleRes),
                                badgeText = stringResource(doc.status.toBadgeRes()),
                                badgeColor = when (doc.status) {
                                    LegalDocumentStatus.APPROVED -> FeniqoSageGreen
                                    LegalDocumentStatus.PENDING_APPROVAL -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                icon = when (doc.type) {
                                    LegalDocumentType.PRIVACY_POLICY -> Icons.Outlined.Policy
                                    LegalDocumentType.TERMS_OF_SERVICE -> Icons.Outlined.Description
                                },
                                showDivider = index != defaultLegalDocuments.lastIndex,
                                onClick = {},
                            )
                        }
                    }
                }

                // Açık Kaynak Lisanslar Grubu
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.legal_info_licenses_section_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.legal_info_licenses_section_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = stringResource(Res.string.legal_info_licenses_row_title),
                            subtitle = stringResource(Res.string.legal_info_licenses_row_desc),
                            badgeText = stringResource(Res.string.legal_info_licenses_badge_view),
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
            title = { Text(stringResource(Res.string.legal_info_licenses_dialog_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            stringResource(Res.string.legal_info_licenses_dialog_intro),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(publishedOpenSourceLicenses, key = { it.id }) { license ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                            Text(
                                text = stringResource(license.textRes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text(stringResource(Res.string.legal_info_licenses_dialog_close), color = FeniqoSageGreen)
                }
            },
        )
    }
}

enum class LegalDocumentStatus {
    APPROVED,
    PENDING_APPROVAL,
}

enum class LegalDocumentType {
    PRIVACY_POLICY,
    TERMS_OF_SERVICE,
}

data class LegalDocumentItem(
    val type: LegalDocumentType,
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val status: LegalDocumentStatus = LegalDocumentStatus.PENDING_APPROVAL,
)

fun LegalDocumentStatus.toBadgeRes(): StringResource = when (this) {
    LegalDocumentStatus.APPROVED -> Res.string.legal_info_badge_approved
    LegalDocumentStatus.PENDING_APPROVAL -> Res.string.legal_info_badge_pending
}

val defaultLegalDocuments: List<LegalDocumentItem> = listOf(
    LegalDocumentItem(
        type = LegalDocumentType.PRIVACY_POLICY,
        titleRes = Res.string.legal_info_privacy_title,
        subtitleRes = Res.string.legal_info_privacy_subtitle,
        status = LegalDocumentStatus.PENDING_APPROVAL,
    ),
    LegalDocumentItem(
        type = LegalDocumentType.TERMS_OF_SERVICE,
        titleRes = Res.string.legal_info_terms_title,
        subtitleRes = Res.string.legal_info_terms_subtitle,
        status = LegalDocumentStatus.PENDING_APPROVAL,
    ),
)

data class OpenSourceLicenseItem(
    val id: String,
    val textRes: StringResource,
)

val publishedOpenSourceLicenses: List<OpenSourceLicenseItem> = listOf(
    OpenSourceLicenseItem("kotlin", Res.string.legal_info_license_kotlin),
    OpenSourceLicenseItem("compose_androidx", Res.string.legal_info_license_compose_androidx),
    OpenSourceLicenseItem("kotlinx", Res.string.legal_info_license_kotlinx),
    OpenSourceLicenseItem("ktor", Res.string.legal_info_license_ktor),
    OpenSourceLicenseItem("room", Res.string.legal_info_license_room),
    OpenSourceLicenseItem("supabase", Res.string.legal_info_license_supabase),
    OpenSourceLicenseItem("sqlcipher", Res.string.legal_info_license_sqlcipher),
)

private fun HelpArticleStatus.toLocalizedStatusRes(): StringResource =
    when (this) {
        HelpArticleStatus.PUBLISHED -> Res.string.help_article_status_published
    }
