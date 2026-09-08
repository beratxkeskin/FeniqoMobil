package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoriesUiState
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CategoryDeleteDialog
import com.feniqo.mobile.presentation.component.CategoryListItem
import com.feniqo.mobile.presentation.component.CategoryMessageBanner
import com.feniqo.mobile.presentation.component.CategorySectionHeader
import com.feniqo.mobile.presentation.component.CategoryTypeSelector
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Kategori listesi ve yönetimi ekranının durumsuz (stateless) ana Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; ViewModel, DAO veya Supabase bağımlılığı içermez.
 */
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onTypeSelected: (TransactionType) -> Unit,
    onAddCategory: (TransactionType) -> Unit,
    onEditCategory: (EntityId) -> Unit,
    onDeleteCategory: (CategoryDisplayModel) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 1. Üst Başlık ve Kategori Ekle Aksiyon Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        Text(
                            text = "Kategoriler",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        ActiveWorkspaceIndicator(
                            workspaceName = state.activeWorkspaceName,
                            isCompact = true,
                        )
                    }
                    Text(
                        text = "Gelir ve giderlerinizi kendi kategorilerinizle düzenleyin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { onAddCategory(state.selectedType) },
                    enabled = !state.isDeleteInProgress,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Yeni kategori ekle"
                        },
                ) {
                    Text(
                        text = "Kategori Ekle",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Tür Seçici (Gider / Gelir)
            CategoryTypeSelector(
                selectedType = state.selectedType,
                onTypeSelected = onTypeSelected,
                enabled = !state.isDeleteInProgress,
            )

            // 3. Genel Mesaj Bannerı (Hata veya Bilgilendirme)
            if (state.generalMessage != null) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                CategoryMessageBanner(
                    message = state.generalMessage,
                    onDismiss = onDismissMessage,
                    isDismissEnabled = !state.isDeleteInProgress,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 4. Ana Liste veya Durum Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = FeniqoSpacing.ExtraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                            Text(
                                text = "Kategoriler yükleniyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.isEmpty -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = FeniqoSpacing.ExtraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Henüz kategori bulunmuyor.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                            Text(
                                text = "İlk özel kategorinizi ekleyebilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            contentPadding = PaddingValues(bottom = FeniqoSpacing.Screen),
                        ) {
                            // A. Hazır Kategoriler (Sistem)
                            if (state.systemCategories.isNotEmpty()) {
                                item(key = "section_system_header") {
                                    CategorySectionHeader(
                                        title = "Hazır Kategoriler",
                                        count = state.systemCategories.size,
                                    )
                                }

                                items(
                                    items = state.systemCategories,
                                    key = { it.id.value },
                                ) { category ->
                                    CategoryListItem(
                                        category = category,
                                        onEditClick = onEditCategory,
                                        onDeleteClick = onDeleteCategory,
                                        isActionsEnabled = !state.isDeleteInProgress,
                                    )
                                }

                                item(key = "section_system_spacer") {
                                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                                }
                            }

                            // B. Kategorilerim (Özel)
                            item(key = "section_custom_header") {
                                CategorySectionHeader(
                                    title = "Kategorilerim",
                                    count = state.customCategories.size,
                                )
                            }

                            if (state.customCategories.isEmpty()) {
                                item(key = "empty_custom_categories_message") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ) {
                                        Text(
                                            text = "Henüz özel kategori eklemediniz.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(FeniqoSpacing.Large),
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = state.customCategories,
                                    key = { it.id.value },
                                ) { category ->
                                    CategoryListItem(
                                        category = category,
                                        onEditClick = onEditCategory,
                                        onDeleteClick = onDeleteCategory,
                                        isActionsEnabled = !state.isDeleteInProgress,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Kategori Silme Onay Diyaloğu
        if (state.deleteTargetCategory != null) {
            CategoryDeleteDialog(
                targetCategory = state.deleteTargetCategory,
                isDeleteInProgress = state.isDeleteInProgress,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteDialog,
            )
        }
    }
}

// -------------------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------------------

private val previewSystemExpenseCategories = listOf(
    CategoryDisplayModel(
        id = EntityId("sys-market"),
        name = "Market",
        type = TransactionType.EXPENSE,
        colorHex = "#EF4444",
        iconKey = "shopping-cart",
        isDefault = true,
    ),
    CategoryDisplayModel(
        id = EntityId("sys-ulasim"),
        name = "Ulaşım",
        type = TransactionType.EXPENSE,
        colorHex = "#F59E0B",
        iconKey = "car",
        isDefault = true,
    ),
)

private val previewCustomExpenseCategories = listOf(
    CategoryDisplayModel(
        id = EntityId("cust-evcil-hayvan"),
        name = "Evcil Hayvan",
        type = TransactionType.EXPENSE,
        colorHex = "#10B981",
        iconKey = "heart-pulse",
        isDefault = false,
    ),
)

@Preview(name = "Categories Screen - Full State Light", showBackground = true)
@Composable
private fun CategoriesScreenFullPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedType = TransactionType.EXPENSE,
                systemCategories = previewSystemExpenseCategories,
                customCategories = previewCustomExpenseCategories,
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Categories Screen - Full State Dark", showBackground = true)
@Composable
private fun CategoriesScreenFullPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedType = TransactionType.EXPENSE,
                systemCategories = previewSystemExpenseCategories,
                customCategories = previewCustomExpenseCategories,
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Categories Screen - Custom Empty Light", showBackground = true)
@Composable
private fun CategoriesScreenCustomEmptyPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedType = TransactionType.EXPENSE,
                systemCategories = previewSystemExpenseCategories,
                customCategories = emptyList(),
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Categories Screen - Loading Light", showBackground = true)
@Composable
private fun CategoriesScreenLoadingPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = true,
                selectedType = TransactionType.EXPENSE,
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Categories Screen - Delete Dialog Light", showBackground = true)
@Composable
private fun CategoriesScreenDeleteDialogPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedType = TransactionType.EXPENSE,
                systemCategories = previewSystemExpenseCategories,
                customCategories = previewCustomExpenseCategories,
                deleteTargetCategory = previewCustomExpenseCategories[0],
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Categories Screen - Message Banner Light", showBackground = true)
@Composable
private fun CategoriesScreenMessageBannerPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                selectedType = TransactionType.EXPENSE,
                systemCategories = previewSystemExpenseCategories,
                customCategories = previewCustomExpenseCategories,
                generalMessage = FinanceUiMessage.CATEGORY_DELETED,
            ),
            onTypeSelected = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onDismissMessage = {},
        )
    }
}
