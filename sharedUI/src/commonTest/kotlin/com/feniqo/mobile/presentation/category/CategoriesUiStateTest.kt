package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoriesUiStateTest {

    @Test
    fun categoriesUiState_defaultValues_matchContract() {
        val state = CategoriesUiState()
        assertTrue(state.isLoading)
        assertEquals(TransactionType.EXPENSE, state.selectedType)
        assertTrue(state.systemCategories.isEmpty())
        assertTrue(state.customCategories.isEmpty())
        assertFalse(state.isDeleteInProgress)
        assertEquals(null, state.generalMessage)
        assertFalse(state.isEmpty)
    }

    @Test
    fun categoriesUiState_isEmpty_returnsTrue_onlyWhenNotLoadingAndBothListsEmpty() {
        val loadingEmpty = CategoriesUiState(isLoading = true, systemCategories = emptyList(), customCategories = emptyList())
        assertFalse(loadingEmpty.isEmpty)

        val loadedEmpty = CategoriesUiState(isLoading = false, systemCategories = emptyList(), customCategories = emptyList())
        assertTrue(loadedEmpty.isEmpty)

        val withSystem = CategoriesUiState(
            isLoading = false,
            systemCategories = listOf(
                CategoryDisplayModel(
                    id = EntityId("sys-1"),
                    name = "Market",
                    type = TransactionType.EXPENSE,
                    colorHex = "#EF4444",
                    isDefault = true,
                ),
            ),
            customCategories = emptyList(),
        )
        assertFalse(withSystem.isEmpty)

        val withCustom = CategoriesUiState(
            isLoading = false,
            systemCategories = emptyList(),
            customCategories = listOf(
                CategoryDisplayModel(
                    id = EntityId("cust-1"),
                    name = "Kişisel",
                    type = TransactionType.EXPENSE,
                    colorHex = "#10B981",
                    isDefault = false,
                ),
            ),
        )
        assertFalse(withCustom.isEmpty)
    }

    @Test
    fun categoryDisplayModel_computedProperties_preventUnsafeActionsOnSystemCategories() {
        val systemCategory = CategoryDisplayModel(
            id = EntityId("sys-1"),
            name = "Market",
            type = TransactionType.EXPENSE,
            colorHex = "#EF4444",
            isDefault = true,
        )
        assertFalse(systemCategory.canEdit)
        assertFalse(systemCategory.canDelete)

        val customCategory = CategoryDisplayModel(
            id = EntityId("cust-1"),
            name = "Abonelikler",
            type = TransactionType.EXPENSE,
            colorHex = "#6366F1",
            isDefault = false,
        )
        assertTrue(customCategory.canEdit)
        assertTrue(customCategory.canDelete)
    }

    @Test
    fun categoryFormUiState_computedProperties_reflectEditAndCreationModes() {
        val createForm = CategoryFormUiState(categoryId = null, name = "Kira")
        assertFalse(createForm.isEditMode)
        assertTrue(createForm.isFormEnabled)
        assertTrue(createForm.isTypeEditable)
        assertTrue(createForm.canSubmit)

        val editForm = CategoryFormUiState(categoryId = EntityId("cat-100"), name = "Kira")
        assertTrue(editForm.isEditMode)
        assertTrue(editForm.isFormEnabled)
        assertFalse(editForm.isTypeEditable)
        assertTrue(editForm.canSubmit)
    }

    @Test
    fun categoryFormUiState_isFormEnabled_isFalse_duringLoadingLoadErrorOrSubmitting() {
        val loadingState = CategoryFormUiState(isLoadingInitialData = true, name = "Market")
        assertFalse(loadingState.isFormEnabled)
        assertFalse(loadingState.isTypeEditable)
        assertFalse(loadingState.canSubmit)

        val loadErrorState = CategoryFormUiState(
            loadError = CategoryFormLoadError.CATEGORY_NOT_FOUND,
            name = "Market",
        )
        assertFalse(loadErrorState.isFormEnabled)
        assertFalse(loadErrorState.isTypeEditable)
        assertFalse(loadErrorState.canSubmit)

        val loadFailedState = CategoryFormUiState(
            loadError = CategoryFormLoadError.LOAD_FAILED,
            name = "Market",
        )
        assertFalse(loadFailedState.isFormEnabled)
        assertFalse(loadFailedState.isTypeEditable)
        assertFalse(loadFailedState.canSubmit)

        val submittingState = CategoryFormUiState(isSubmitting = true, name = "Market")
        assertFalse(submittingState.isFormEnabled)
        assertFalse(submittingState.isTypeEditable)
        assertFalse(submittingState.canSubmit)
    }

    @Test
    fun categoryFormUiState_canSubmit_validatesFieldsAndState() {
        val blankName = CategoryFormUiState(name = "   ")
        assertFalse(blankName.canSubmit)

        val withNameError = CategoryFormUiState(name = "Market", nameError = CategoryFormFieldError.NAME_TOO_LONG)
        assertFalse(withNameError.canSubmit)

        val withColorError = CategoryFormUiState(name = "Market", colorError = CategoryFormFieldError.COLOR_INVALID)
        assertFalse(withColorError.canSubmit)

        val validState = CategoryFormUiState(name = "Market", colorHex = "#10B981")
        assertTrue(validState.canSubmit)
    }

    @Test
    fun errorModels_provideExpectedTurkishDisplayTexts() {
        assertEquals("Kategori adı boş bırakılamaz.", CategoryFormFieldError.NAME_REQUIRED.toDisplayText())
        assertEquals("Kategori adı en fazla 50 karakter olabilir.", CategoryFormFieldError.NAME_TOO_LONG.toDisplayText())
        assertEquals("Lütfen geçerli bir renk seçin.", CategoryFormFieldError.COLOR_INVALID.toDisplayText())

        assertEquals("Kategori bulunamadı.", CategoryFormLoadError.CATEGORY_NOT_FOUND.toDisplayText())
        assertEquals("Sistem kategorileri düzenlenemez.", CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY.toDisplayText())
        assertEquals("Geçersiz kategori bağlantısı.", CategoryFormLoadError.INVALID_ROUTE.toDisplayText())
        assertEquals("Kategori yüklenemedi.", CategoryFormLoadError.LOAD_FAILED.toDisplayText())
    }
}
