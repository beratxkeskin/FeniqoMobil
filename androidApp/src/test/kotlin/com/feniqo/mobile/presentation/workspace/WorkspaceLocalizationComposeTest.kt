package com.feniqo.mobile.presentation.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import com.feniqo.mobile.presentation.screen.WorkspaceCreateScreen
import com.feniqo.mobile.presentation.screen.WorkspaceDetailsScreen
import com.feniqo.mobile.presentation.screen.WorkspaceJoinScreen
import com.feniqo.mobile.presentation.screen.WorkspacePickerScreen
import com.feniqo.mobile.presentation.screen.WorkspaceSettlementScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class WorkspaceLocalizationComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun workspacePickerScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspacePickerUiState(
            isLoading = false,
            isPersonalModeActive = false,
            workspaces = listOf(
                WorkspacePickerItemUiModel(
                    id = EntityId("ws-1"),
                    name = "Ev Harcamaları",
                    isOwner = true,
                    isActive = true,
                ),
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspacePickerScreen(
                    state = state,
                    onSelectPersonalMode = {},
                    onSelectWorkspace = {},
                    onDismissError = {},
                    onNavigateBack = {},
                    onCreateWorkspace = {},
                    onJoinWorkspace = {},
                    onWorkspaceDetails = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Ortak Alanlar").assertIsDisplayed()
        composeRule.onNodeWithText("Birlikte planla, net takip et.").assertIsDisplayed()
        composeRule.onNodeWithText("Kişisel").assertIsDisplayed()
        composeRule.onNodeWithText("Alanların").assertIsDisplayed()
        composeRule.onNodeWithText("Seçili alan").assertIsDisplayed()
        composeRule.onNodeWithText("Detayları gör").assertIsDisplayed()
        composeRule.onNodeWithText("Alan oluştur").assertIsDisplayed()
        composeRule.onNodeWithText("Davet koduyla katıl").assertIsDisplayed()
        composeRule.onNodeWithText("Ev Harcamaları").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
        }

        // EN
        composeRule.onNodeWithText("Workspaces").assertIsDisplayed()
        composeRule.onNodeWithText("Plan together, track clearly.").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Your spaces").assertIsDisplayed()
        composeRule.onNodeWithText("Active space").assertIsDisplayed()
        composeRule.onNodeWithText("View details").assertIsDisplayed()
        composeRule.onNodeWithText("Create space").assertIsDisplayed()
        composeRule.onNodeWithText("Join with invite code").assertIsDisplayed()
        composeRule.onNodeWithText("Ev Harcamaları").assertIsDisplayed()
    }

    @Test
    fun workspaceCreateScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspaceCreateUiState(
            name = "Yeni Ev",
            description = "Açıklama",
            type = WorkspaceType.SHARED,
            currency = Currency.TRY,
            nameError = WorkspaceCreateFormFieldError.NAME_BLANK,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspaceCreateScreen(
                    state = state,
                    onBack = {},
                    onNameChanged = {},
                    onDescriptionChanged = {},
                    onTypeChanged = {},
                    onCurrencyChanged = {},
                    onSubmit = {},
                    onDismissError = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Alan oluştur").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni ortak alan").assertIsDisplayed()
        composeRule.onNodeWithText("Alan adı").assertIsDisplayed()
        composeRule.onNodeWithText("Çalışma alanı adı zorunludur.").assertIsDisplayed()
        composeRule.onNodeWithText("Alan türü").assertIsDisplayed()
        composeRule.onNodeWithText("Ortak").assertIsDisplayed()
        composeRule.onNodeWithText("Para birimi").assertIsDisplayed()
        composeRule.onNodeWithText("TRY · Türk lirası").assertIsDisplayed()
        composeRule.onNodeWithText("Alanı oluştur").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
        }

        // EN
        composeRule.onAllNodesWithText("Create space")[0].assertIsDisplayed()
        composeRule.onNodeWithText("New workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Space name").assertIsDisplayed()
        composeRule.onNodeWithText("Workspace name is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Space type").assertIsDisplayed()
        composeRule.onNodeWithText("Shared").assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("TRY · Turkish lira").assertIsDisplayed()
    }

    @Test
    fun workspaceJoinScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspaceJoinUiState(
            inviteCode = "",
            codeError = WorkspaceJoinFormFieldError.CODE_BLANK,
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspaceJoinScreen(
                    state = state,
                    onBack = {},
                    onInviteCodeChanged = {},
                    onSubmit = {},
                    onDismissError = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Alana katıl").assertIsDisplayed()
        composeRule.onNodeWithText("Davet kodun hazır mı?").assertIsDisplayed()
        composeRule.onNodeWithText("Davet kodu").assertIsDisplayed()
        composeRule.onNodeWithText("Davet kodu boş olamaz.").assertIsDisplayed()
        composeRule.onNodeWithText("Katıl").assertIsDisplayed()
        composeRule.onNodeWithText("Kodu yalnızca güvendiğin kişilerden al.").assertIsDisplayed()

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
        }

        // EN
        composeRule.onNodeWithText("Join space").assertIsDisplayed()
        composeRule.onNodeWithText("Do you have an invite code?").assertIsDisplayed()
        composeRule.onNodeWithText("Invite code").assertIsDisplayed()
        composeRule.onNodeWithText("Invite code cannot be empty.").assertIsDisplayed()
        composeRule.onNodeWithText("Join").assertIsDisplayed()
        composeRule.onNodeWithText("Only accept codes from people you trust.").assertIsDisplayed()
    }

    @Test
    fun workspaceDetailsScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspaceDetailsUiState(
            isLoading = false,
            workspace = WorkspaceDetailsUiModel(
                id = EntityId("ws-1"),
                name = "Bizim Ev",
                type = WorkspaceType.SHARED,
                currency = Currency.TRY,
                description = "Giderler",
                ownerId = EntityId("user-owner"),
            ),
            currentUserRole = WorkspaceRole.OWNER,
            members = listOf(
                WorkspaceMemberUiModel(
                    userId = EntityId("user-owner"),
                    displayName = null,
                    role = WorkspaceRole.OWNER,
                    isCurrentUser = true,
                ),
                WorkspaceMemberUiModel(
                    userId = EntityId("user-2-fallback"),
                    displayName = null,
                    role = WorkspaceRole.EDITOR,
                    isCurrentUser = false,
                ),
                WorkspaceMemberUiModel(
                    userId = EntityId("user-3-named"),
                    displayName = "Mehmet",
                    role = WorkspaceRole.VIEWER,
                    isCurrentUser = false,
                ),
            ),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspaceDetailsScreen(
                    state = state,
                    onBack = {},
                    onRequestLeave = {},
                    onDismissLeaveConfirmation = {},
                    onConfirmLeave = {},
                    onDismissError = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Alan detayı").assertIsDisplayed()
        composeRule.onNodeWithText("Ortak alan · TRY").assertIsDisplayed()
        composeRule.onNodeWithText("Davet kodu oluştur").assertIsDisplayed()
        composeRule.onNodeWithText("Ödeşme ve transfer önerileri").assertIsDisplayed()
        composeRule.onNodeWithText("3 üye").assertIsDisplayed()
        composeRule.onNodeWithText("Sahipliği devret").assertIsDisplayed()
        composeRule.onNodeWithText("Siz").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sahip")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Kullanıcı user-2-f").assertIsDisplayed()
        composeRule.onNodeWithText("Düzenleyici").assertIsDisplayed()
        composeRule.onNodeWithText("Mehmet").assertIsDisplayed()
        composeRule.onNodeWithText("İzleyici").assertIsDisplayed()
        composeRule.onAllNodesWithText("Siz (sen)").assertCountEquals(0)
        composeRule.onAllNodesWithText("Siz (you)").assertCountEquals(0)

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
        }

        // EN
        composeRule.onNodeWithText("Space details").assertIsDisplayed()
        composeRule.onNodeWithText("Shared space · TRY").assertIsDisplayed()
        composeRule.onNodeWithText("Create invite code").assertIsDisplayed()
        composeRule.onNodeWithText("Settlement and transfer suggestions").assertIsDisplayed()
        composeRule.onNodeWithText("3 members").assertIsDisplayed()
        composeRule.onNodeWithText("Transfer ownership").assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()
        composeRule.onAllNodesWithText("Owner")[0].assertIsDisplayed()
        composeRule.onNodeWithText("User user-2-f").assertIsDisplayed()
        composeRule.onNodeWithText("Editor").assertIsDisplayed()
        composeRule.onNodeWithText("Mehmet").assertIsDisplayed()
        composeRule.onNodeWithText("Viewer").assertIsDisplayed()
        composeRule.onAllNodesWithText("Siz").assertCountEquals(0)
        composeRule.onAllNodesWithText("Kullanıcı user-2-f").assertCountEquals(0)
        composeRule.onAllNodesWithText("You (you)").assertCountEquals(0)
        composeRule.onAllNodesWithText("Siz (you)").assertCountEquals(0)
    }

    @Test
    fun workspaceSettlementScreen_reactsToRuntimeLocaleChange() {
        var languageTag by mutableStateOf("tr")
        val state = WorkspaceSettlementUiState(
            isLoading = false,
            workspaceId = EntityId("ws-1"),
            workspaceName = "Bizim Ev",
            memberCount = 2,
            isCurrentUserMember = true,
            currency = Currency.TRY,
            memberBalances = listOf(
                WorkspaceMemberBalanceUiModel(
                    userId = EntityId("user-1-owner"),
                    displayName = null,
                    role = WorkspaceRole.OWNER,
                    isCurrentUser = true,
                    netAmountMinor = 20000L,
                    status = MemberBalanceStatus.CREDITOR,
                ),
                WorkspaceMemberBalanceUiModel(
                    userId = EntityId("user-2-fallback"),
                    displayName = null,
                    role = WorkspaceRole.EDITOR,
                    isCurrentUser = false,
                    netAmountMinor = -20000L,
                    status = MemberBalanceStatus.DEBTOR,
                ),
            ),
            suggestedTransfers = listOf(
                WorkspaceSettlementTransferUiModel(
                    fromUserId = EntityId("user-2-fallback"),
                    fromDisplayName = null,
                    fromIsCurrentUser = false,
                    toUserId = EntityId("user-1-owner"),
                    toDisplayName = null,
                    toIsCurrentUser = true,
                    amount = Money(20000L, Currency.TRY),
                ),
            ),
            totalExpenseAmount = Money(40000L, Currency.TRY),
        )

        composeRule.setContent {
            App(languageTag = languageTag) {
                WorkspaceSettlementScreen(
                    state = state,
                    onBack = {},
                    onDismissError = {},
                )
            }
        }

        // TR
        composeRule.onNodeWithText("Ödeşme").assertIsDisplayed()
        composeRule.onNodeWithText("Üye bakiyeleri").assertIsDisplayed()
        composeRule.onNodeWithText("Ortak gider paylaşımına göre").assertIsDisplayed()
        composeRule.onNodeWithText("Alacaklı").assertIsDisplayed()
        composeRule.onNodeWithText("Borçlu").assertIsDisplayed()
        composeRule.onNodeWithText("Transfer önerileri").assertIsDisplayed()
        composeRule.onNodeWithText("Bunlar hesaplanan önerilerdir; uygulama para göndermez.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Siz")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Kullanıcı user-2-f")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Siz (sen)").assertCountEquals(0)
        composeRule.onAllNodesWithText("Siz (you)").assertCountEquals(0)

        // Switch to EN
        composeRule.runOnIdle {
            languageTag = "en"
        }

        // EN
        composeRule.onNodeWithText("Settlement").assertIsDisplayed()
        composeRule.onNodeWithText("Member balances").assertIsDisplayed()
        composeRule.onNodeWithText("Based on shared expense split").assertIsDisplayed()
        composeRule.onNodeWithText("Creditor").assertIsDisplayed()
        composeRule.onNodeWithText("Debtor").assertIsDisplayed()
        composeRule.onNodeWithText("Transfer suggestions").assertIsDisplayed()
        composeRule.onNodeWithText("These are calculated suggestions; the app does not transfer money.").assertIsDisplayed()
        composeRule.onAllNodesWithText("You")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("User user-2-f")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Siz").assertCountEquals(0)
        composeRule.onAllNodesWithText("Kullanıcı user-2-f").assertCountEquals(0)
        composeRule.onAllNodesWithText("You (you)").assertCountEquals(0)
        composeRule.onAllNodesWithText("Siz (you)").assertCountEquals(0)
    }
}
