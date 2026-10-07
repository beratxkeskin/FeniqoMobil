package com.feniqo.mobile.presentation.finance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import com.feniqo.mobile.presentation.debt.DebtFormFieldError
import com.feniqo.mobile.presentation.debt.DebtFormInput
import com.feniqo.mobile.presentation.debt.DebtFormInputErrors
import com.feniqo.mobile.presentation.goal.GoalFormFieldError
import com.feniqo.mobile.presentation.goal.GoalFormInput
import com.feniqo.mobile.presentation.goal.GoalFormInputErrors
import com.feniqo.mobile.presentation.screen.DebtFormScreen
import com.feniqo.mobile.presentation.screen.GoalFormScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h3000dp")
class FinanceFormAccessibilityComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun goalForm_imeNextAndValidationFocus_areDeterministic() {
        compose.setContent {
            FeniqoTheme {
                GoalFormScreen(
                    input = GoalFormInput(),
                    errors = GoalFormInputErrors(nameError = GoalFormFieldError.NAME_REQUIRED),
                    isSubmitting = false,
                    isEditMode = false,
                    onBack = {}, onNameChange = {}, onTargetAmountChange = {}, onCurrencyChange = {},
                    onInitialAmountChange = {}, onTargetDateClick = {}, onColorChange = {},
                    onRequestDelete = {}, onSubmit = {},
                )
            }
        }

        val name = compose.onNodeWithContentDescription("Hedef adı")
        name.assertIsFocused().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Hedef adı zorunludur."),
        )
        name.performClick()
        name.performImeAction()
        compose.onNodeWithContentDescription("Hedef tutarı").assertIsFocused()
    }

    @Test
    fun debtForm_imeNextAndValidationFocus_areDeterministic() {
        compose.setContent {
            FeniqoTheme(darkTheme = true) {
                DebtFormScreen(
                    input = DebtFormInput(),
                    errors = DebtFormInputErrors(titleError = DebtFormFieldError.TITLE_REQUIRED),
                    isSubmitting = false,
                    isEditMode = false,
                    onBack = {}, onTitleChange = {}, onAmountChange = {}, onCurrencyChange = {},
                    onTypeChange = {}, onDueDateClick = {}, onDescriptionChange = {},
                    onRequestDelete = {}, onSubmit = {},
                )
            }
        }

        val title = compose.onNodeWithContentDescription("Borç kayıt adı")
        title.assertIsFocused().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Başlık zorunludur."),
        )
        title.performClick()
        title.performImeAction()
        compose.onNodeWithContentDescription("Borç toplam tutarı").assertIsFocused()
    }
}
