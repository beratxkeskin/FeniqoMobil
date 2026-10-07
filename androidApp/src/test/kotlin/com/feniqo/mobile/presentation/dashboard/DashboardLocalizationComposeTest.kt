package com.feniqo.mobile.presentation.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.MoneyScoreLevel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.DashboardHeader
import com.feniqo.mobile.presentation.component.HomeInsightCard
import com.feniqo.mobile.presentation.component.HomeMoneyScoreSection
import com.feniqo.mobile.presentation.screen.DashboardScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboard_header_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                DashboardHeader(
                    formattedMonth = "Ekim 2026",
                    userName = "",
                )
            }
        }

        composeRule.onNodeWithText("Merhaba, Kullanıcı").assertIsDisplayed()
        composeRule.onNodeWithText("Ayına bir bakış").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Hello, User").assertIsDisplayed()
        composeRule.onNodeWithText("Your month at a glance").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
    }

    @Test
    fun dashboard_loading_state_uses_english_catalog() {
        composeRule.setContent {
            App(languageTag = "en") {
                val snackbarHostState = remember { SnackbarHostState() }
                DashboardScreen(
                    state = DashboardUiState(isLoading = true),
                    snackbarHostState = snackbarHostState,
                    onRetry = {},
                    onAddTransaction = {},
                    onViewAllTransactions = {},
                    onTransactionClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Loading your financial summary…").assertIsDisplayed()
    }

    @Test
    fun dashboard_finance_error_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                val snackbarHostState = remember { SnackbarHostState() }
                DashboardScreen(
                    state =
                        DashboardUiState(
                            isLoading = false,
                            observationError = FinanceUiMessage.NETWORK_ERROR,
                        ),
                    snackbarHostState = snackbarHostState,
                    onRetry = {},
                    onAddTransaction = {},
                    onViewAllTransactions = {},
                    onTransactionClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Ağ bağlantısı kurulamadı. Lütfen internet bağlantınızı kontrol edin.")
            .assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule
            .onNodeWithText("Could not connect to the network. Check your internet connection.")
            .assertIsDisplayed()
    }

    @Test
    fun dashboard_dynamic_content_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        val insight =
            DashboardInsightModel(
                type = DashboardInsightType.TOP_EXPENSE,
                categoryName = "Market",
                formattedAmount = "4.200 ₺",
            )
        val moneyScore =
            MoneyScoreDisplayModel(
                totalScore = 78,
                level = MoneyScoreLevel.HEALTHY,
                savingsScore = 24,
                budgetScore = 22,
                debtScore = 16,
                goalScore = 16,
                isProvisional = true,
                explanationText = "",
            )

        composeRule.setContent {
            App(languageTag = languageTag) {
                Column {
                    HomeInsightCard(insight = insight)
                    HomeMoneyScoreSection(moneyScore = moneyScore)
                }
            }
        }

        composeRule.onNodeWithText("Feniqo İçgörü").assertIsDisplayed()
        composeRule.onNodeWithText("Sağlıklı").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Bu ay en yüksek harcaman Market kategorisinde (4.200 ₺). Harcamalarını dengede tutmak için harika bir fırsat!",
            ).assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Feniqo Insight").assertIsDisplayed()
        composeRule.onNodeWithText("Healthy").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Your highest spending this month was in Market (4.200 ₺). A great opportunity to bring your spending into balance!",
            ).assertIsDisplayed()
    }
}
