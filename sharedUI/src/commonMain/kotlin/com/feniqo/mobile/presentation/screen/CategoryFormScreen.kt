package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoryFormFieldError
import com.feniqo.mobile.presentation.category.CategoryFormLoadError
import com.feniqo.mobile.presentation.category.CategoryFormUiState
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.CategoryColorPicker
import com.feniqo.mobile.presentation.component.CategoryFormHeader
import com.feniqo.mobile.presentation.component.CategoryIconPicker
import com.feniqo.mobile.presentation.component.CategoryMessageBanner
import com.feniqo.mobile.presentation.component.CategoryNameField
import com.feniqo.mobile.presentation.component.CategorySubmitButton
import com.feniqo.mobile.presentation.component.CategoryTypeSection
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Kategori oluşturma ve düzenleme ekranının durumsuz (stateless) Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; ViewModel veya platform bağımlılığı içermez.
 */
@Composable
fun CategoryFormScreen(
    state: CategoryFormUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (TransactionType) -> Unit,
    onColorChanged: (String) -> Unit,
    onIconChanged: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.isEditMode) "Kategoriyi Düzenle" else "Kategori Ekle"
    val description = if (state.isEditMode) {
        "Kategori adı, rengi ve simgesini güncelleyin."
    } else {
        "Kendi gelir veya gider kategorinizi oluşturun."
    }

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

            // 1. Üst Başlık ve Geri Dönüş Alanı
            CategoryFormHeader(
                title = title,
                description = description,
                onBack = onBack,
                isBackEnabled = !state.isSubmitting,
                activeWorkspaceName = state.activeWorkspaceName,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Durum Alanı (Initial Loading / Load Error / Form İçeriği)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoadingInitialData -> {
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
                                text = "Kategori yükleniyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.loadError != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = FeniqoSpacing.ExtraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Kategori Yüklenemedi",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                            Text(
                                text = state.loadError.toDisplayText(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text("Geri Dön")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                            contentPadding = PaddingValues(bottom = FeniqoSpacing.Screen),
                        ) {
                            // Genel Hata/Bilgi Mesajı Bannerı
                            if (state.generalMessage != null) {
                                item(key = "general_message_banner") {
                                    CategoryMessageBanner(
                                        message = state.generalMessage,
                                        onDismiss = onDismissMessage,
                                        isDismissEnabled = !state.isSubmitting,
                                    )
                                }
                            }

                            // Kategori Adı Alanı
                            item(key = "category_name_field") {
                                CategoryNameField(
                                    name = state.name,
                                    onNameChanged = onNameChanged,
                                    nameError = state.nameError,
                                    enabled = state.isFormEnabled,
                                )
                            }

                            // Kategori Türü Seçimi
                            item(key = "category_type_section") {
                                CategoryTypeSection(
                                    selectedType = state.type,
                                    isEditMode = state.isEditMode,
                                    isTypeEditable = state.isTypeEditable,
                                    onTypeSelected = onTypeChanged,
                                )
                            }

                            // Kategori Rengi Seçimi
                            item(key = "category_color_picker") {
                                CategoryColorPicker(
                                    selectedColorHex = state.colorHex,
                                    onColorChanged = onColorChanged,
                                    colorError = state.colorError,
                                    enabled = state.isFormEnabled,
                                )
                            }

                            // Kategori Simgesi Seçimi
                            item(key = "category_icon_picker") {
                                CategoryIconPicker(
                                    selectedIconKey = state.iconKey,
                                    onIconChanged = onIconChanged,
                                    enabled = state.isFormEnabled,
                                )
                            }

                            // Form Kaydet / Gönder Butonu
                            item(key = "category_submit_button") {
                                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                                CategorySubmitButton(
                                    isEditMode = state.isEditMode,
                                    canSubmit = state.canSubmit,
                                    isSubmitting = state.isSubmitting,
                                    onSubmit = onSubmit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------------------

@Preview(name = "Create Expense Light", showBackground = true)
@Composable
private fun CategoryFormCreateExpensePreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                type = TransactionType.EXPENSE,
                name = "",
                colorHex = "#10B981",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Create Income Dark", showBackground = true)
@Composable
private fun CategoryFormCreateIncomePreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                type = TransactionType.INCOME,
                name = "Yatırım Getirisi",
                colorHex = "#34D399",
                iconKey = "trending-up",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Edit Custom Light", showBackground = true)
@Composable
private fun CategoryFormEditCustomPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                type = TransactionType.EXPENSE,
                name = "Evcil Hayvan",
                colorHex = "#EF4444",
                iconKey = "heart-pulse",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Validation Error Light", showBackground = true)
@Composable
private fun CategoryFormValidationErrorPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                name = "",
                nameError = CategoryFormFieldError.NAME_REQUIRED,
                colorError = CategoryFormFieldError.COLOR_INVALID,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Initial Loading Light", showBackground = true)
@Composable
private fun CategoryFormInitialLoadingPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                isLoadingInitialData = true,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Load Error Dark", showBackground = true)
@Composable
private fun CategoryFormLoadErrorPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                loadError = CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Submitting Dark", showBackground = true)
@Composable
private fun CategoryFormSubmittingPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                name = "Abonelik",
                colorHex = "#6366F1",
                isSubmitting = true,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}
