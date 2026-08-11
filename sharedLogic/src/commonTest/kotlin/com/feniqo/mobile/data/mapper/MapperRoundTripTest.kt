package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Tag
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionTag
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MapperRoundTripTest {

    @Test
    fun identity_models_survive_entity_round_trip() {
        val profile = UserProfile(
            id = USER_ID,
            email = "user@feniqo.com",
            fullName = "Feniqo User",
            currency = Currency.TRY,
            themePreference = ThemePreference.SYSTEM,
            language = AppLanguage.TR,
            activeWorkspaceId = WORKSPACE_ID,
            createdAt = NOW,
        )
        val workspace = Workspace(WORKSPACE_ID, "Ev Bütçesi", USER_ID, NOW)
        val member = WorkspaceMember(WORKSPACE_ID, USER_ID, WorkspaceRole.OWNER, NOW)

        assertEquals(profile, profile.toEntity(SYNC).toDomain())
        assertEquals(workspace, workspace.toEntity(SYNC).toDomain())
        assertEquals(member, member.toEntity(SYNC).toDomain())
    }

    @Test
    fun finance_models_survive_entity_round_trip() {
        val category = category()
        val transaction = transaction()
        val budget = Budget(
            id = EntityId("budget-1"),
            ownerId = USER_ID,
            workspaceId = null,
            categoryId = CATEGORY_ID,
            month = YearMonth("2026-08"),
            limit = Money(50_000, Currency.TRY),
            createdAt = NOW,
        )
        val tag = Tag(EntityId("tag-1"), USER_ID, null, "zorunlu", NOW)
        val relation = TransactionTag(transaction.id, tag.id)

        assertEquals(category, category.toEntity(SYNC).toDomain())
        assertEquals(transaction, transaction.toEntity(SYNC).toDomain())
        assertEquals(budget, budget.toEntity(SYNC).toDomain())
        assertEquals(tag, tag.toEntity(SYNC).toDomain())
        assertEquals(relation, relation.toEntity(NOW.toEpochMilliseconds(), SYNC).toDomain())
    }

    private fun category() = Category(
        id = CATEGORY_ID,
        ownerId = USER_ID,
        workspaceId = null,
        name = "Market",
        type = TransactionType.EXPENSE,
        color = CategoryColor("#0A7A55"),
        icon = CategoryIcon("shopping"),
        isDefault = false,
        createdAt = NOW,
    )

    private fun transaction() = Transaction(
        id = EntityId("transaction-1"),
        ownerId = USER_ID,
        workspaceId = null,
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = CATEGORY_ID,
        description = "Market alışverişi",
        paymentMethod = PaymentMethod.DEBIT_CARD,
        transactionDate = LocalDate(2026, 8, 5),
        receiptPath = ReceiptPath("user-1/transaction-1/receipt.jpg"),
        installment = InstallmentInfo(1, 3, EntityId("installment-group-1")),
        createdAt = NOW,
    )

    private companion object {
        val USER_ID = EntityId("user-1")
        val WORKSPACE_ID = EntityId("workspace-1")
        val CATEGORY_ID = EntityId("category-1")
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
        val SYNC = newSyncMetadata(NOW.toEpochMilliseconds())
    }
}
