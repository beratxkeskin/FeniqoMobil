package com.feniqo.mobile.presentation.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.component.BudgetExceededBanner
import com.feniqo.mobile.presentation.component.BudgetInsightCard
import com.feniqo.mobile.presentation.component.BudgetProgressCard
import com.feniqo.mobile.presentation.screen.BudgetFormScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class BudgetLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleBudget = BudgetProgressDisplayModel(
        id = EntityId("b-1"),
        categoryId = EntityId("c-market"),
        categoryName = "Market",
        categoryColorHex = "#10B981",
        categoryIconKey = "shopping-cart",
        isCategoryMissing = false,
        month = YearMonth("2026-08"),
        formattedLimit = "2.000,00 \u20BA",
        limitMinor = 200_000L,
        formattedSpent = "1.500,00 \u20BA",
        spentMinor = 150_000L,
        formattedRemaining = "500,00 \u20BA",
        remainingMinor = 50_000L,
        isRemainingNegative = false,
        usageRateBasisPoints = 7500,
        formattedUsageRate = "%75,00",
        usageProgressFraction = 0.75f,
        health = BudgetHealth.SAFE,
        excludedDifferentCurrencyTransactionCount = 0,
        currency = Currency.TRY,
    )

    private val missingCategoryBudget = sampleBudget.copy(
        id = EntityId("b-missing"),
        isCategoryMissing = true,
        categoryName = "",
    )

    @Test
    fun budget_list_and_insight_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    BudgetInsightCard(
                        insight = BudgetInsight.FastestCategoryUsage(
                            categoryName = "Market",
                            isCategoryMissing = false,
                            usageRateBasisPoints = 8099,
                        ),
                    )
                    BudgetExceededBanner(
                        exceededCount = 2,
                        onClick = {},
                    )
                }
            }
        }

        // TR: 8_099 basis-points precision (80,99)
        composeRule.onNodeWithText("Feniqo \u0130\u00e7g\u00f6r\u00fc").assertIsDisplayed()
        composeRule.onNodeWithText("Market b\u00fct\u00e7esinin y\u00fczde 80,99\u2019ini kulland\u0131n.").assertIsDisplayed()
        composeRule.onNodeWithText("2 b\u00fct\u00e7e a\u015f\u0131ld\u0131").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: 8_099 basis-points precision (80.99%)
        composeRule.onNodeWithText("Feniqo Insight").assertIsDisplayed()
        composeRule.onNodeWithText("You used 80.99% of your Market budget.").assertIsDisplayed()
        composeRule.onNodeWithText("2 budget(s) exceeded").assertIsDisplayed()
    }

    @Test
    fun budget_insight_missing_category_fastest_usage_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    BudgetInsightCard(
                        insight = BudgetInsight.FastestCategoryUsage(
                            categoryName = "",
                            isCategoryMissing = true,
                            usageRateBasisPoints = 8500,
                        ),
                    )
                }
            }
        }

        // TR: Shows localized fallback category name "Kategori Yok"
        composeRule.onNodeWithText("Feniqo \u0130\u00e7g\u00f6r\u00fc").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori Yok b\u00fct\u00e7esinin y\u00fczde 85\u2019ini kulland\u0131n.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Shows English fallback category name "No category", NEVER Turkish "Kategori Yok"
        composeRule.onNodeWithText("Feniqo Insight").assertIsDisplayed()
        composeRule.onNodeWithText("You used 85% of your No category budget.").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori Yok", substring = true).assertDoesNotExist()
    }

    @Test
    fun budget_detail_and_status_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val distinctMissingBudget = missingCategoryBudget.copy(
            remainingMinor = 25_000L,
            limitMinor = 100_000L,
            spentMinor = 75_000L,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    BudgetProgressCard(
                        budget = sampleBudget,
                        onClick = {},
                    )
                    BudgetProgressCard(
                        budget = distinctMissingBudget,
                        onClick = {},
                    )
                }
            }
        }

        // TR: status summary "%1$s kaldı" and fallback category "Kategori Yok"
        composeRule.onNodeWithText("\u20BA500 kald\u0131").assertIsDisplayed()
        composeRule.onNodeWithText("\u20BA250 kald\u0131").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori Yok").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: status summary "%1$s remaining" and fallback category "No category"
        composeRule.onNodeWithText("\u20BA500 remaining").assertIsDisplayed()
        composeRule.onNodeWithText("\u20BA250 remaining").assertIsDisplayed()
        composeRule.onNodeWithText("No category").assertIsDisplayed()
    }

    @Test
    fun budget_form_screen_semantics_and_error_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val formState = BudgetFormUiState(
            selectedCategoryId = EntityId("cat-expense-1"),
            selectedCategoryName = "Market",
            selectedMonth = YearMonth("2026-08"),
            limitInput = "2500",
            mutationState = BudgetMutationState(amountError = BudgetFormFieldError.AMOUNT_REQUIRED),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                Box(modifier = Modifier.size(width = 400.dp, height = 1500.dp)) {
                    BudgetFormScreen(
                        state = formState,
                        onBack = {},
                        onCategorySelected = {},
                        onMonthSelected = {},
                        onLimitChanged = {},
                        onCurrencySelected = {},
                        onSubmit = {},
                    )
                }
            }
        }

        // TR: Semantics contentDescription and form labels/errors
        composeRule.onNodeWithContentDescription("B\u00fct\u00e7e ayl\u0131k limiti").assertExists()
        composeRule.onNodeWithText("L\u00fctfen bir b\u00fct\u00e7e limiti girin.").assertExists()
        composeRule.onNodeWithText("A\u011fustos 2026").assertExists()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Semantics contentDescription switches to English, old TR description disappears
        composeRule.onNodeWithContentDescription("Monthly budget limit").assertExists()
        composeRule.onNodeWithText("Please enter a budget limit.").assertExists()
        composeRule.onNodeWithText("August 2026").assertExists()
        composeRule.onNodeWithContentDescription("B\u00fct\u00e7e ayl\u0131k limiti").assertDoesNotExist()
    }

    @Test
    fun budget_copy_and_result_message_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    Text(
                        text = localizedBudgetCopyResultMessage(copiedCount = 2, skippedCount = 1),
                    )
                    Text(
                        text = localizedBudgetCopyResultMessage(copiedCount = 3, skippedCount = 0),
                    )
                    Text(
                        text = localizedBudgetCopyResultMessage(copiedCount = 0, skippedCount = 2),
                    )
                    Text(
                        text = localizedBudgetCopyResultMessage(copiedCount = 0, skippedCount = 0),
                    )
                }
            }
        }

        // TR
        composeRule.onNodeWithText("2 b\u00fct\u00e7e kopyaland\u0131; 1 mevcut b\u00fct\u00e7e atland\u0131.").assertIsDisplayed()
        composeRule.onNodeWithText("3 b\u00fct\u00e7e kopyaland\u0131.").assertIsDisplayed()
        composeRule.onNodeWithText("Kopyalanacak yeni b\u00fct\u00e7e bulunamad\u0131; 2 mevcut b\u00fct\u00e7e atland\u0131.").assertIsDisplayed()
        composeRule.onNodeWithText("Kaynak ayda kopyalanacak b\u00fct\u00e7e bulunamad\u0131.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN
        composeRule.onNodeWithText("2 budget(s) copied; 1 existing budget(s) skipped.").assertIsDisplayed()
        composeRule.onNodeWithText("3 budget(s) copied.").assertIsDisplayed()
        composeRule.onNodeWithText("No new budgets to copy; 2 existing budget(s) skipped.").assertIsDisplayed()
        composeRule.onNodeWithText("No budgets found to copy in source month.").assertIsDisplayed()
    }
}
