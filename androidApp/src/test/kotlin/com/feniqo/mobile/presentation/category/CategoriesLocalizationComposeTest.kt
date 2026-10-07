package com.feniqo.mobile.presentation.category

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.component.CategoryDeleteDialog
import com.feniqo.mobile.presentation.component.CategoryFilterChips
import com.feniqo.mobile.presentation.component.CategoryInsightCard
import com.feniqo.mobile.presentation.component.CategoryPeriodSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun category_insight_and_filters_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    CategoryInsightCard(
                        insight = CategoryInsight.TopExpense(categoryName = "Market", sharePercent = 42),
                    )
                    CategoryFilterChips(
                        selectedTypeFilter = null,
                        onFilterSelected = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Feniqo İçgörü").assertIsDisplayed()
        composeRule
            .onNodeWithText("Market bu ayki en yüksek harcaman. Toplam giderlerinin yüzde 42’sini oluşturuyor.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Tümü").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Feniqo Insight").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Market is your highest spending category this month. It accounts for 42% of your total expenses.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Filter expense categories").assertIsDisplayed()
    }

    @Test
    fun category_period_selector_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryPeriodSelector(
                    selectedYearMonth = YearMonth("2026-09"),
                    isNextMonthEnabled = true,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onPeriodPickerClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Eylül 2026").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Önceki aya git").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("September 2026").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Go to next month").assertIsDisplayed()
    }

    @Test
    fun category_delete_dialog_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val category = CategoryDisplayModel(
            id = EntityId("category-market"),
            name = "Market",
            type = TransactionType.EXPENSE,
            colorHex = "#10B981",
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryDeleteDialog(
                    targetCategory = category,
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Kategori silinsin mi?").assertIsDisplayed()
        composeRule
            .onNodeWithText("Market kategorisini silmek istiyor musun? Geçmiş işlemlerde kategori adı korunur.")
            .assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Delete category?").assertIsDisplayed()
        composeRule
            .onNodeWithText("Do you want to delete the Market category? Its name will be preserved in past transactions.")
            .assertIsDisplayed()
    }
}
