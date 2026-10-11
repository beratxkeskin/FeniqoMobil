package com.feniqo.mobile.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.hub.AssetsOverviewPayload
import com.feniqo.mobile.presentation.hub.GoalsOverviewPayload
import com.feniqo.mobile.presentation.hub.MoreHubCardEmptyReason
import com.feniqo.mobile.presentation.hub.MoreHubCardErrorReason
import com.feniqo.mobile.presentation.hub.MoreHubOverviewCardState
import com.feniqo.mobile.presentation.hub.MoreHubUiState
import com.feniqo.mobile.presentation.hub.RecurringOverviewPayload
import com.feniqo.mobile.presentation.hub.SubscriptionsOverviewPayload
import com.feniqo.mobile.presentation.screen.MoreHubScreen
import com.feniqo.mobile.presentation.screen.ProfileScreen
import com.feniqo.mobile.presentation.sync.SyncStatusUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class ProfileMoreHubLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileScreen_populated_state_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var currentLanguage by mutableStateOf(AppLanguage.TR)
        var currentCurrency by mutableStateOf(Currency.TRY)
        var workspaceNameInput by mutableStateOf<String?>("Şirket Alanı")

        composeRule.setContent {
            App(languageTag = languageTag) {
                ProfileScreen(
                    displayName = "Berat Keskin",
                    email = "berat@feniqo.com",
                    workspaceName = workspaceNameInput,
                    isProfileLoading = false,
                    signOutError = null,
                    syncStatus = SyncStatusUiState.Initial,
                    language = currentLanguage,
                    currency = currentCurrency,
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenSettings = {},
                    onOpenDataManagement = {},
                    onSignOut = {},
                )
            }
        }

        // TR: Başlıklar, bölümler, menü satırları, dil/bölge etiketi, erişilebilirlik
        composeRule.onNodeWithText("Profil Merkezi").assertIsDisplayed()
        composeRule.onNodeWithText("Hesap").assertIsDisplayed()
        composeRule.onNodeWithText("Kişisel Bilgiler").assertIsDisplayed()
        composeRule.onNodeWithText("Adını ve profilini düzenle").assertIsDisplayed()
        composeRule.onNodeWithText("Ortak Alanlar").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama Ayarları").assertIsDisplayed()
        composeRule.onNodeWithText("Görünüm, bildirimler, güvenlik ve veri araçları").assertIsDisplayed()
        composeRule.onNodeWithText("Çıkış Yap").assertIsDisplayed()
        composeRule.onNodeWithText("Türkçe • TRY").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Geri").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hesap detaylarını aç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Kişisel Bilgiler aç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ortak Alanlar aç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Uygulama Ayarları aç").assertIsDisplayed()

        // Kullanıcı verileri korunur
        composeRule.onNodeWithText("Berat Keskin").assertIsDisplayed()
        composeRule.onNodeWithText("berat@feniqo.com").assertIsDisplayed()
        composeRule.onAllNodesWithText("Şirket Alanı")[0].assertIsDisplayed()

        // EN geçişi
        composeRule.runOnIdle {
            languageTag = "en"
            currentLanguage = AppLanguage.EN
            currentCurrency = Currency.USD
        }

        // EN: Başlıklar, bölümler, menü satırları, dil/bölge etiketi, erişilebilirlik
        composeRule.onNodeWithText("Profile Center").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onNodeWithText("Personal Information").assertIsDisplayed()
        composeRule.onNodeWithText("Edit your name and profile").assertIsDisplayed()
        composeRule.onNodeWithText("Shared Spaces").assertIsDisplayed()
        composeRule.onNodeWithText("Application").assertIsDisplayed()
        composeRule.onNodeWithText("App Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance, notifications, security, and data tools").assertIsDisplayed()
        composeRule.onNodeWithText("Sign Out").assertIsDisplayed()
        composeRule.onNodeWithText("English • USD").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open account details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Personal Information").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Shared Spaces").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open App Settings").assertIsDisplayed()

        // Kullanıcı verileri EN dilinde de korunur
        composeRule.onNodeWithText("Berat Keskin").assertIsDisplayed()
        composeRule.onNodeWithText("berat@feniqo.com").assertIsDisplayed()
        composeRule.onAllNodesWithText("Şirket Alanı")[0].assertIsDisplayed()

        // Workspace fallback testi: workspace null olduğunda TR -> EN kaynakla çözülür
        composeRule.runOnIdle {
            workspaceNameInput = null
            languageTag = "tr"
        }
        composeRule.onAllNodesWithText("Kişisel alan")[0].assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onAllNodesWithText("Personal space")[0].assertIsDisplayed()
    }

    @Test
    fun profileScreen_error_states_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var currentError by mutableStateOf<ProfileUiError?>(ProfileUiError.LoadFailed)
        var currentSignOutError by mutableStateOf<ProfileSignOutError?>(ProfileSignOutError.Failed)

        composeRule.setContent {
            App(languageTag = languageTag) {
                ProfileScreen(
                    displayName = "",
                    email = "",
                    workspaceName = null,
                    isProfileLoading = false,
                    isProfileEmpty = true,
                    profileError = currentError,
                    signOutError = currentSignOutError,
                    syncStatus = SyncStatusUiState.Initial,
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPersonalInfo = {},
                    onOpenSharedSpaces = {},
                    onOpenSettings = {},
                    onOpenDataManagement = {},
                    onSignOut = {},
                    onRetryProfile = {},
                )
            }
        }

        // TR error assertions
        composeRule.onNodeWithText("Profil bilgileri yüklenemedi").assertIsDisplayed()
        composeRule.onNodeWithText("Profil bilgileri yüklenemedi. Lütfen tekrar deneyin.").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrar Dene").assertIsDisplayed()
        composeRule.onNodeWithText("Çıkış yapılamadı. Lütfen tekrar deneyin.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN error assertions
        composeRule.onNodeWithText("Failed to load profile").assertIsDisplayed()
        composeRule.onNodeWithText("Failed to load profile information. Please try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Failed to sign out. Please try again.").assertIsDisplayed()
    }

    @Test
    fun moreHubScreen_populated_state_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val populatedState = MoreHubUiState(
            activeWorkspaceName = "Ortak Aile Alanı",
            assets = MoreHubOverviewCardState.Content(
                AssetsOverviewPayload(
                    totals = listOf(Money(1234500L, Currency.TRY)),
                    assetCount = 2,
                ),
            ),
            goals = MoreHubOverviewCardState.Content(
                GoalsOverviewPayload(
                    activeGoalCount = 3,
                ),
            ),
            subscriptions = MoreHubOverviewCardState.Content(
                SubscriptionsOverviewPayload(
                    activeSubscriptionCount = 1,
                    nearestRenewalDate = LocalDate(2026, 9, 15),
                ),
            ),
            recurringTransactions = MoreHubOverviewCardState.Content(
                RecurringOverviewPayload(
                    upcomingRuleCount = 4,
                ),
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                MoreHubScreen(
                    state = populatedState,
                    onNavigateToAssets = {},
                    onNavigateToGoals = {},
                    onNavigateToDebts = {},
                    onNavigateToSubscriptions = {},
                    onNavigateToRecurringTransactions = {},
                    onNavigateToCategories = {},
                    onNavigateToSharedSpaces = {},
                    onNavigateToSettings = {},
                    onNavigateToProfile = {},
                    onNavigateToReports = {},
                )
            }
        }

        // TR: Bölüm ve kart başlıkları, para formatı, tarih, çoğullar, erişilebilirlik
        composeRule.onNodeWithText("Daha Fazla").assertIsDisplayed()
        composeRule.onNodeWithText("Finansının tüm parçaları, bir arada.").assertIsDisplayed()
        composeRule.onNodeWithText("Genel bakış").assertIsDisplayed()
        composeRule.onAllNodesWithText("Varlıklar")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Hedefler")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Abonelikler")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Tekrarlayan").assertIsDisplayed()
        composeRule.onNodeWithText("Varlık yönetimi").assertIsDisplayed()
        composeRule.onNodeWithText("Para yönetimi").assertIsDisplayed()
        composeRule.onNodeWithText("Ortak kullanım").assertIsDisplayed()
        composeRule.onNodeWithText("İçgörüler").assertIsDisplayed()
        composeRule.onNodeWithText("Ayarlar").assertIsDisplayed()
        composeRule.onNodeWithText("Borç ve Alacaklar").assertIsDisplayed()
        composeRule.onNodeWithText("Tekrarlayan İşlemler").assertIsDisplayed()
        composeRule.onNodeWithText("Kategoriler").assertIsDisplayed()
        composeRule.onNodeWithText("Raporlar").assertIsDisplayed()
        composeRule.onNodeWithText("Uygulama Ayarları").assertIsDisplayed()

        // Para ve tarih
        composeRule.onNodeWithText("12.345,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("15 Eylül 2026", substring = true).assertIsDisplayed()

        // Çoğullar (plurals)
        composeRule.onNodeWithText("2 varlık").assertIsDisplayed()
        composeRule.onNodeWithText("3 aktif hedef").assertIsDisplayed()
        composeRule.onNodeWithText("1 aktif abonelik", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("4 planlı kural", substring = true).assertIsDisplayed()

        // Aktif workspace semantics
        composeRule.onNodeWithContentDescription("Aktif çalışma alanı: Ortak Aile Alanı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Profil ve hesap").assertIsDisplayed()

        // EN geçişi
        composeRule.runOnIdle { languageTag = "en" }

        // EN: Bölüm ve kart başlıkları, para formatı, tarih, çoğullar, erişilebilirlik
        composeRule.onNodeWithText("More").assertIsDisplayed()
        composeRule.onNodeWithText("All pieces of your finances, together.").assertIsDisplayed()
        composeRule.onNodeWithText("Overview").assertIsDisplayed()
        composeRule.onAllNodesWithText("Assets")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Goals")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Subscriptions")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Recurring").assertIsDisplayed()
        composeRule.onNodeWithText("Wealth management").assertIsDisplayed()
        composeRule.onNodeWithText("Money management").assertIsDisplayed()
        composeRule.onNodeWithText("Collaboration").assertIsDisplayed()
        composeRule.onNodeWithText("Insights").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Debts and Receivables").assertIsDisplayed()
        composeRule.onNodeWithText("Recurring Transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
        composeRule.onNodeWithText("App Settings").assertIsDisplayed()

        // Para ve tarih (EN)
        composeRule.onNodeWithText("12,345.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("September 15, 2026", substring = true).assertIsDisplayed()

        // Çoğullar (EN plurals)
        composeRule.onNodeWithText("2 assets").assertIsDisplayed()
        composeRule.onNodeWithText("3 active goals").assertIsDisplayed()
        composeRule.onNodeWithText("1 active subscription", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("4 scheduled rules", substring = true).assertIsDisplayed()

        // Aktif workspace semantics (EN)
        composeRule.onNodeWithContentDescription("Active workspace: Ortak Aile Alanı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Profile and account").assertIsDisplayed()
    }

    @Test
    fun moreHubScreen_empty_and_error_states_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")
        var isErrorScenario by mutableStateOf(false)

        val emptyState = MoreHubUiState(
            activeWorkspaceName = null,
            assets = MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoAssets),
            goals = MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoActiveGoals),
            subscriptions = MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoActiveSubscriptions),
            recurringTransactions = MoreHubOverviewCardState.Empty(MoreHubCardEmptyReason.NoUpcomingRecurringRules),
        )

        val errorState = MoreHubUiState(
            activeWorkspaceName = "Test",
            assets = MoreHubOverviewCardState.Error(MoreHubCardErrorReason.NetWorthUnreliable),
            goals = MoreHubOverviewCardState.Error(MoreHubCardErrorReason.General),
            subscriptions = MoreHubOverviewCardState.Error(MoreHubCardErrorReason.General),
            recurringTransactions = MoreHubOverviewCardState.Error(MoreHubCardErrorReason.General),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                MoreHubScreen(
                    state = if (isErrorScenario) errorState else emptyState,
                    onNavigateToAssets = {},
                    onNavigateToGoals = {},
                    onNavigateToDebts = {},
                    onNavigateToSubscriptions = {},
                    onNavigateToRecurringTransactions = {},
                    onNavigateToCategories = {},
                    onNavigateToSharedSpaces = {},
                    onNavigateToSettings = {},
                    onNavigateToProfile = {},
                    onNavigateToReports = {},
                )
            }
        }

        // TR Empty assertions
        composeRule.onNodeWithText("Henüz varlık yok.").assertIsDisplayed()
        composeRule.onNodeWithText("Henüz aktif hedef yok.").assertIsDisplayed()
        composeRule.onNodeWithText("Henüz aktif abonelik yok.").assertIsDisplayed()
        composeRule.onNodeWithText("Önümüzdeki 30 günde planlı kural yok.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Aktif çalışma alanı: Kişisel").assertIsDisplayed()

        // EN Empty assertions
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("No assets yet.").assertIsDisplayed()
        composeRule.onNodeWithText("No active goals yet.").assertIsDisplayed()
        composeRule.onNodeWithText("No active subscriptions yet.").assertIsDisplayed()
        composeRule.onNodeWithText("No scheduled rules in the next 30 days.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Active workspace: Personal").assertIsDisplayed()

        // Error scenario switch
        composeRule.runOnIdle {
            isErrorScenario = true
            languageTag = "tr"
        }

        // TR Error assertions
        composeRule.onNodeWithText("Varlık toplamı güvenle gösterilemiyor.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Veriler şu an yüklenemedi.")[0].assertIsDisplayed()

        // EN Error assertions
        composeRule.runOnIdle { languageTag = "en" }
        composeRule.onNodeWithText("Asset total cannot be displayed safely.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Data could not be loaded at this time.")[0].assertIsDisplayed()
    }
}
