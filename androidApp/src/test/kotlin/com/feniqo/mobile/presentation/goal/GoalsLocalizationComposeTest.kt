@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:argument-list-wrapping",
)

package com.feniqo.mobile.presentation.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.AddGoalContributionCommand
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.domain.repository.GoalRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.CreateGoalUseCase
import com.feniqo.mobile.domain.usecase.DeleteGoalUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalContributionsUseCase
import com.feniqo.mobile.domain.usecase.ObserveGoalUseCase
import com.feniqo.mobile.domain.usecase.UpdateGoalUseCase
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.screen.GoalContributionFormScreen
import com.feniqo.mobile.presentation.screen.GoalDetailScreen
import com.feniqo.mobile.presentation.screen.GoalFormScreen
import com.feniqo.mobile.presentation.screen.GoalsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class GoalsLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCurrentDateProvider(private val today: LocalDate) : CurrentDateProvider {
        override fun today(): LocalDate = today
    }

    private class FakeGoalRepository : GoalRepository {
        val goalsFlow = MutableStateFlow<Map<EntityId, Goal>>(emptyMap())
        val contributionsFlow = MutableStateFlow<Map<EntityId, List<GoalContribution>>>(emptyMap())

        override fun observeGoals(): Flow<List<Goal>> = flowOf(goalsFlow.value.values.toList())

        override fun observeGoal(id: EntityId): Flow<Goal?> = flow {
            goalsFlow.collect { map -> emit(map[id]) }
        }

        override fun observeContributions(goalId: EntityId): Flow<List<GoalContribution>> = flow {
            contributionsFlow.collect { map -> emit(map[goalId] ?: emptyList()) }
        }

        override suspend fun create(command: com.feniqo.mobile.domain.model.CreateGoalCommand): RepositoryResult<EntityId> {
            return RepositoryResult.Success(EntityId("goal-created"))
        }

        override suspend fun update(command: com.feniqo.mobile.domain.model.UpdateGoalCommand): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }

        override suspend fun addContribution(command: AddGoalContributionCommand): RepositoryResult<EntityId> {
            return RepositoryResult.Success(EntityId("contrib-created"))
        }

        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
            return RepositoryResult.Success(Unit)
        }
    }

    @Test
    fun goals_screen_and_cards_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val sampleGoal = GoalDisplayModel(
            id = EntityId("g-1"),
            name = "Yeni Araba",
            targetAmount = Money(1_000_000L, Currency.TRY),
            currentAmount = Money(750_000L, Currency.TRY),
            remainingAmount = Money(250_000L, Currency.TRY),
            currency = Currency.TRY,
            targetDate = LocalDate(2026, 12, 31),
            colorHex = "#2E7D32",
            iconKey = "savings",
            status = GoalStatus.IN_PROGRESS,
            progressBasisPoints = RateBasisPoints(7500),
            progressFraction = 0.75f,
            isAchieved = false,
        )

        val summary = GoalsSummaryUiModel(
            scopedGoalCount = 1,
            activeGoalCount = 1,
            achievedGoalCount = 0,
            averageProgressBasisPoints = RateBasisPoints(7500),
            currencySummaries = listOf(
                GoalCurrencySummaryUiModel(
                    currency = Currency.TRY,
                    savedAmount = Money(750_000L, Currency.TRY),
                    targetAmount = Money(1_000_000L, Currency.TRY),
                ),
            ),
        )

        val insight = GoalInsightUiModel(
            id = "ins-1",
            payload = GoalInsightPayload.ClosestGoal(
                goalName = "Yeni Araba",
                progressBasisPoints = RateBasisPoints(7500),
                remainingAmount = Money(250_000L, Currency.TRY),
            ),
        )

        val state = GoalsUiState(
            isLoading = false,
            allGoals = listOf(sampleGoal),
            visibleGoals = listOf(sampleGoal),
            selectedFilter = GoalStatusFilter.ALL,
            summary = summary,
            insights = listOf(insight),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalsScreen(
                    state = state,
                    onRetry = {},
                    onAddGoal = {},
                    onGoalClick = {},
                    onFilterSelected = {},
                    onBack = null,
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Hedefler").assertIsDisplayed()
        composeRule.onNodeWithText("Hedeflerde biriken").assertIsDisplayed()
        composeRule.onNodeWithText("Tümü").assertIsDisplayed()
        composeRule.onNodeWithText("Aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Tamamlanan").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni Araba").assertIsDisplayed()
        composeRule.onNodeWithText("İçgörüler").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni hedef").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Goals").assertIsDisplayed()
        composeRule.onNodeWithText("Saved in goals").assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni Araba").assertIsDisplayed()
        composeRule.onNodeWithText("Insights").assertIsDisplayed()
        composeRule.onNodeWithText("New goal").assertIsDisplayed()
    }

    @Test
    fun goal_detail_screen_and_actions_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val domainGoal = Goal(
            id = EntityId("g-1"),
            ownerId = EntityId("u-1"),
            workspaceId = EntityId("ws-1"),
            name = "Tatil Fonu",
            targetAmount = Money(1_000_000L, Currency.TRY),
            currentAmount = Money(600_000L, Currency.TRY),
            targetDate = LocalDate(2026, 8, 15),
            color = CategoryColor("#1E88E5"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val contribution = GoalContribution(
            id = EntityId("c-1"),
            goalId = EntityId("g-1"),
            amount = Money(200_000L, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 7, 1),
            note = "Maaş katkısı",
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val detail = GoalDetailDisplayModel(
            goal = domainGoal,
            status = GoalDetailStatus.ACTIVE,
            typedInsight = GoalDetailInsight.InProgress(
                progress = RateBasisPoints(6000),
                remaining = Money(400_000L, Currency.TRY),
            ),
            remainingAmount = Money(400_000L, Currency.TRY),
            progress = RateBasisPoints(6000),
            daysRemaining = 30L,
            monthlyRequired = Money(100_000L, Currency.TRY),
            estimatedCompletion = LocalDate(2026, 8, 15),
            chart = emptyList(),
            recentContributions = listOf(contribution),
        )

        val state = GoalDetailUiState(
            isLoading = false,
            detail = detail,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onAdd = {},
                    onRemove = {},
                    onDelete = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Tatil Fonu").assertIsDisplayed()
        composeRule.onNodeWithText("Aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Biriken").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef").assertIsDisplayed()
        composeRule.onNodeWithText("Para ekle").assertIsDisplayed()
        composeRule.onNodeWithText("Para çıkar").assertIsDisplayed()
        composeRule.onNodeWithText("Hareketler").assertIsDisplayed()
        composeRule.onNodeWithText("Maaş katkısı").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Tatil Fonu").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("Target").assertIsDisplayed()
        composeRule.onNodeWithText("Add money").assertIsDisplayed()
        composeRule.onNodeWithText("Withdraw money").assertIsDisplayed()
        composeRule.onNodeWithText("Movements").assertIsDisplayed()
        composeRule.onNodeWithText("Maaş katkısı").assertIsDisplayed()
    }

    @Test
    fun goal_form_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val input = GoalFormInput(
            goalId = null,
            nameInput = "",
            targetAmountInput = "",
            currency = Currency.TRY,
            initialAmountInput = "",
            targetDate = LocalDate(2026, 12, 31),
            colorHex = "#2E7D32",
        )

        val errors = GoalFormInputErrors(
            nameError = GoalFormFieldError.NAME_REQUIRED,
            targetAmountError = GoalFormFieldError.TARGET_AMOUNT_REQUIRED,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalFormScreen(
                    input = input,
                    errors = errors,
                    isSubmitting = false,
                    isEditMode = false,
                    onBack = {},
                    onNameChange = {},
                    onTargetAmountChange = {},
                    onCurrencyChange = {},
                    onInitialAmountChange = {},
                    onTargetDateClick = {},
                    onColorChange = {},
                    onRequestDelete = {},
                    onSubmit = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Yeni hedef").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef adı zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef tutar zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef adı").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef tutar").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef tarihi").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef rengi").assertIsDisplayed()
        composeRule.onNodeWithText("Hedef oluştur").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("New goal").assertIsDisplayed()
        composeRule.onNodeWithText("Goal name is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Target amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Goal name").assertIsDisplayed()
        composeRule.onNodeWithText("Target amount").assertIsDisplayed()
        composeRule.onNodeWithText("Target date").assertIsDisplayed()
        composeRule.onNodeWithText("Goal color").assertIsDisplayed()
        composeRule.onNodeWithText("Create goal").assertIsDisplayed()
    }

    @Test
    fun goal_contribution_form_screen_and_validation_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val parentGoal = Goal(
            id = EntityId("g-1"),
            ownerId = EntityId("u-1"),
            workspaceId = EntityId("ws-1"),
            name = "Acil Durum Fonu",
            targetAmount = Money(1_000_000L, Currency.TRY),
            currentAmount = Money(500_000L, Currency.TRY),
            targetDate = LocalDate(2026, 12, 31),
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val input = GoalContributionFormInput(
            amountInput = "",
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 6, 15),
            noteInput = "",
        )

        val errors = GoalContributionFormInputErrors(
            amountError = GoalContributionFormFieldError.AMOUNT_REQUIRED,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalContributionFormScreen(
                    parentGoal = parentGoal,
                    input = input,
                    errors = errors,
                    isSubmitting = false,
                    onBack = {},
                    onAmountChange = {},
                    onDirectionChange = {},
                    onDateClick = {},
                    onNoteChange = {},
                    onSubmit = {},
                )
            }
        }

        // TR assertions
        composeRule.onNodeWithText("Hedef hareketi").assertIsDisplayed()
        composeRule.onNodeWithText("Tutar zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Para ekle").assertIsDisplayed()
        composeRule.onNodeWithText("Para çıkar").assertIsDisplayed()
        composeRule.onNodeWithText("Tarih").assertIsDisplayed()
        composeRule.onNodeWithText("Birikime ekle").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions
        composeRule.onNodeWithText("Goal movement").assertIsDisplayed()
        composeRule.onNodeWithText("Amount is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Add money").assertIsDisplayed()
        composeRule.onNodeWithText("Withdraw money").assertIsDisplayed()
        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithText("Add to savings").assertIsDisplayed()
    }

    @Test
    fun goal_form_edit_route_preserves_unsaved_draft_across_language_change() {
        var languageTag by mutableStateOf("tr")

        val repo = FakeGoalRepository()
        val existingGoalId = EntityId("g-edit-1")
        val existingGoal = Goal(
            id = existingGoalId,
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Kayıtlı Hedef",
            targetAmount = Money(500_000L, Currency.TRY),
            currentAmount = Money(100_000L, Currency.TRY),
            targetDate = LocalDate(2027, 5, 20),
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        repo.goalsFlow.value = mapOf(existingGoalId to existingGoal)

        val viewModel = GoalFormViewModel(
            observeGoalUseCase = ObserveGoalUseCase(repo),
            observeGoalContributionsUseCase = ObserveGoalContributionsUseCase(repo),
            createGoalUseCase = CreateGoalUseCase(repo),
            updateGoalUseCase = UpdateGoalUseCase(repo),
            deleteGoalUseCase = DeleteGoalUseCase(repo),
            currentDateProvider = FakeCurrentDateProvider(LocalDate(2026, 9, 3)),
        )

        composeRule.setContent {
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            val locale = java.util.Locale.forLanguageTag(languageTag)
            java.util.Locale.setDefault(locale)
            configuration.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
            ) {
                GoalFormScreenRoute(
                    onNavigateBack = {},
                    onMessage = {},
                    initialGoalId = existingGoalId,
                    viewModel = viewModel,
                )
            }
        }

        // İlk yüklemede kayıtlı ad görünür
        composeRule.onNodeWithText("Kayıtlı Hedef").assertIsDisplayed()

        // Kullanıcı taslağı UI üzerinden değiştirir
        composeRule.onNodeWithContentDescription("Hedef adı").performTextReplacement("Kaydedilmemiş Taslak Adı")
        composeRule.onNodeWithText("Kaydedilmemiş Taslak Adı").assertIsDisplayed()

        // Dil değişimi (TR -> EN): decimalSeparator değişse bile edit formu Room'dan yeniden yüklenmemeli
        composeRule.runOnIdle { languageTag = "en" }

        // EN modunda da kaydedilmemiş taslak adı korunmalıdır
        composeRule.onNodeWithText("Kaydedilmemiş Taslak Adı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Goal name").assertIsDisplayed()
        assertEquals("Kaydedilmemiş Taslak Adı", viewModel.uiState.value.input.nameInput)
    }

    @Test
    fun goal_form_screen_contribution_history_date_and_amount_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val historyItem = GoalContributionHistoryItemUiModel(
            id = EntityId("c-hist-1"),
            amount = Money(150_000L, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 12, 31),
            note = "Özel Yatırım",
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalFormScreen(
                    input = GoalFormInput(goalId = EntityId("g-1"), nameInput = "Hedef"),
                    errors = GoalFormInputErrors(),
                    isSubmitting = false,
                    isEditMode = true,
                    onBack = {},
                    onNameChange = {},
                    onTargetAmountChange = {},
                    onCurrencyChange = {},
                    onInitialAmountChange = {},
                    onTargetDateClick = {},
                    onColorChange = {},
                    onRequestDelete = {},
                    onSubmit = {},
                    contributionsHistory = listOf(historyItem),
                )
            }
        }

        // TR assertions: Türkçe tarih ve para formatı
        composeRule.onNodeWithText("31 Aralık 2026").assertIsDisplayed()
        composeRule.onNodeWithText("+1.500,00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Özel Yatırım").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // EN assertions: İngilizce tarih ve para formatı (TRY için 1,500.00 ₺)
        composeRule.onNodeWithText("December 31, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("+1,500.00 ₺").assertIsDisplayed()
        composeRule.onNodeWithText("Özel Yatırım").assertIsDisplayed()
    }

    @Test
    fun goal_form_screen_content_descriptions_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalFormScreen(
                    input = GoalFormInput(),
                    errors = GoalFormInputErrors(),
                    isSubmitting = false,
                    isEditMode = false,
                    onBack = {},
                    onNameChange = {},
                    onTargetAmountChange = {},
                    onCurrencyChange = {},
                    onInitialAmountChange = {},
                    onTargetDateClick = {},
                    onColorChange = {},
                    onRequestDelete = {},
                    onSubmit = {},
                )
            }
        }

        // GoalFormScreen TR content descriptions
        composeRule.onNodeWithContentDescription("Hedef adı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hedef tutarı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Başlangıç birikimi").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // GoalFormScreen EN content descriptions
        composeRule.onNodeWithContentDescription("Goal name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Target amount").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Initial savings").assertIsDisplayed()
    }

    @Test
    fun goal_contribution_form_screen_content_descriptions_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        val parentGoal = Goal(
            id = EntityId("g-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Test Hedef",
            targetAmount = Money(100_000L, Currency.TRY),
            currentAmount = Money(20_000L, Currency.TRY),
            targetDate = LocalDate(2026, 12, 31),
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                GoalContributionFormScreen(
                    parentGoal = parentGoal,
                    input = GoalContributionFormInput(),
                    errors = GoalContributionFormInputErrors(),
                    isSubmitting = false,
                    onBack = {},
                    onAmountChange = {},
                    onDirectionChange = {},
                    onDateClick = {},
                    onNoteChange = {},
                    onSubmit = {},
                )
            }
        }

        // GoalContributionFormScreen TR content descriptions
        composeRule.onNodeWithContentDescription("Hedef hareket tutarı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hedef hareket notu").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle { languageTag = "en" }

        // GoalContributionFormScreen EN content descriptions
        composeRule.onNodeWithContentDescription("Goal movement amount").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Goal movement note").assertIsDisplayed()
    }

    @Test
    fun goal_insight_and_detail_models_have_no_locale_dependent_fallback_strings() {
        val payload = GoalInsightPayload.ClosestGoal(
            goalName = "Hedef",
            progressBasisPoints = RateBasisPoints(5000),
            remainingAmount = Money(50_000L, Currency.TRY),
        )
        val insightModel = GoalInsightUiModel(
            id = "test-id",
            payload = payload,
        )

        // GoalInsightUiModel yalnız id ve tipli payload taşımalı, title/description string taşımamalıdır
        assertEquals("test-id", insightModel.id)
        assertEquals(payload, insightModel.payload)

        val domainGoal = Goal(
            id = EntityId("g-test"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Tipli Hedef",
            targetAmount = Money(100_000L, Currency.TRY),
            currentAmount = Money(50_000L, Currency.TRY),
            targetDate = LocalDate(2026, 12, 31),
            color = CategoryColor("#2E7D32"),
            icon = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
        val detailModel = GoalDetailDisplayModel(
            goal = domainGoal,
            status = GoalDetailStatus.ACTIVE,
            typedInsight = GoalDetailInsight.Achieved,
            remainingAmount = Money(50_000L, Currency.TRY),
            progress = RateBasisPoints(5000),
            daysRemaining = 100L,
            monthlyRequired = null,
            estimatedCompletion = null,
            chart = null,
            recentContributions = emptyList(),
        )

        // GoalDetailDisplayModel tipli alanları taşımalı, fallback String alanları barındırmamalıdır
        assertEquals(GoalDetailStatus.ACTIVE, detailModel.status)
        assertEquals(GoalDetailInsight.Achieved, detailModel.typedInsight)
        assertEquals(Money(50_000L, Currency.TRY), detailModel.remainingAmount)
        assertNull(detailModel.monthlyRequired)
    }
}
