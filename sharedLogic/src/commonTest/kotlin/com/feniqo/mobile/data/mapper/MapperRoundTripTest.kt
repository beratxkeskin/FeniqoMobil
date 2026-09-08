package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SubscriptionEntity
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


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
        workspaceId = EntityId("workspace-1"),
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = CATEGORY_ID,
        description = "Market alışverişi",
        paymentMethod = PaymentMethod.DEBIT_CARD,
        transactionDate = LocalDate(2026, 8, 5),
        receiptPath = ReceiptPath("user-1/transaction-1/receipt.jpg"),
        installment = InstallmentInfo(1, 3, EntityId("installment-group-1")),
        createdAt = NOW,
        paidByUserId = EntityId("user-2"),
        participantUserIds = listOf(USER_ID, EntityId("user-2"), EntityId("user-3")),
    )

    @Test
    fun subscription_survives_entity_round_trip_with_all_fields() {
        val subscription = Subscription(
            id = EntityId("subscription-1"),
            ownerId = USER_ID,
            workspaceId = null,
            name = "Spotify Premium",
            amount = Money(5999, Currency.TRY),
            categoryId = CATEGORY_ID,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = LocalDate(2027, 8, 1),
            ),
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
            createdAt = NOW,
        )

        val entity = subscription.toEntity(SYNC)
        assertEquals(SYNC, entity.sync)
        assertEquals("Spotify Premium", entity.name)
        assertEquals(5999L, entity.amountMinor)
        assertEquals("TRY", entity.currencyCode)
        assertEquals(CATEGORY_ID.value, entity.categoryId)
        assertEquals("MONTHLY", entity.frequencyCode)
        assertEquals(1, entity.interval)
        assertEquals("2026-08-01", entity.startDate)
        assertEquals("2027-08-01", entity.endDate)
        assertEquals("2026-09-01", entity.nextRenewalDate)
        assertEquals(true, entity.isActive)
        assertEquals(NOW.toEpochMilliseconds(), entity.createdAtEpochMillis)

        val roundTrip = entity.toDomain()
        assertEquals(subscription, roundTrip)
    }

    @Test
    fun subscription_survives_entity_round_trip_with_nullable_fields() {
        val subscription = Subscription(
            id = EntityId("subscription-1"),
            ownerId = USER_ID,
            workspaceId = null,
            name = "iCloud Storage",
            amount = Money(1999, Currency.TRY),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = false,
            createdAt = NOW,
        )

        val entity = subscription.toEntity(SYNC)
        assertNull(entity.categoryId)
        assertNull(entity.endDate)

        val roundTrip = entity.toDomain()
        assertEquals(subscription, roundTrip)
        assertNull(roundTrip.categoryId)
        assertNull(roundTrip.renewalRule.endDate)
    }

    @Test
    fun subscription_entity_to_domain_fails_closed_on_corrupt_data() {
        val validEntity = Subscription(
            id = EntityId("sub-1"),
            ownerId = USER_ID,
            workspaceId = null,
            name = "Test",
            amount = Money(1000, Currency.TRY),
            categoryId = CATEGORY_ID,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            nextRenewalDate = LocalDate(2026, 9, 1),
            isActive = true,
            createdAt = NOW,
        ).toEntity(SYNC)

        // Invalid currency
        assertFailsWith<IllegalArgumentException> {
            validEntity.copy(currencyCode = "INVALID").toDomain()
        }

        // Invalid recurrence frequency
        assertFailsWith<IllegalArgumentException> {
            validEntity.copy(frequencyCode = "HOURLY").toDomain()
        }

        // Invalid start date
        assertFailsWith<IllegalArgumentException> {
            validEntity.copy(startDate = "not-a-date").toDomain()
        }

        // Invalid next renewal date
        assertFailsWith<IllegalArgumentException> {
            validEntity.copy(nextRenewalDate = "not-a-date").toDomain()
        }

        // Blank category id (fails closed at EntityId)
        assertFailsWith<IllegalArgumentException> {
            validEntity.copy(categoryId = "").toDomain()
        }
    }

    @Test
    fun goal_and_contribution_survive_entity_round_trip() {
        val goal = Goal(
            id = EntityId("goal-1"),
            ownerId = USER_ID,
            workspaceId = null,
            name = "Araba Birikimi",
            targetAmount = Money(500_000, Currency.TRY),
            currentAmount = Money(150_000, Currency.TRY),
            targetDate = LocalDate(2027, 6, 1),
            color = CategoryColor("#0A7A55"),
            icon = CategoryIcon("car"),
            createdAt = NOW,
        )
        val contribution = GoalContribution(
            id = EntityId("contrib-1"),
            goalId = goal.id,
            amount = Money(25_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = "Ağustos maaş birikimi",
            createdAt = NOW,
        )

        val goalEntity = goal.toEntity(SYNC)
        assertEquals(SYNC, goalEntity.sync)
        assertEquals("Araba Birikimi", goalEntity.name)
        assertEquals(500_000L, goalEntity.targetAmountMinor)
        assertEquals(150_000L, goalEntity.currentAmountMinor)
        assertEquals("TRY", goalEntity.currencyCode)
        assertEquals("2027-06-01", goalEntity.targetDate)
        assertEquals("#0A7A55", goalEntity.colorHex)
        assertEquals("car", goalEntity.iconKey)
        assertEquals(goal, goalEntity.toDomain())

        val contribEntity = contribution.toEntity(SYNC)
        assertEquals(SYNC, contribEntity.sync)
        assertEquals("contrib-1", contribEntity.id)
        assertEquals("goal-1", contribEntity.goalId)
        assertEquals(25_000L, contribEntity.amountMinor)
        assertEquals("TRY", contribEntity.currencyCode)
        assertEquals("ADD", contribEntity.directionCode)
        assertEquals("2026-09-01", contribEntity.occurredOn)
        assertEquals("Ağustos maaş birikimi", contribEntity.note)
        assertEquals(contribution, contribEntity.toDomain())
    }

    @Test
    fun goal_and_contribution_corrupt_data_fails_closed() {
        val validGoalEntity = Goal(
            id = EntityId("goal-1"),
            ownerId = USER_ID,
            workspaceId = null,
            name = "Hedef",
            targetAmount = Money(100_000, Currency.TRY),
            currentAmount = Money(10_000, Currency.TRY),
            targetDate = LocalDate(2027, 1, 1),
            color = CategoryColor("#123456"),
            icon = null,
            createdAt = NOW,
        ).toEntity(SYNC)

        // Invalid currency
        assertFailsWith<IllegalArgumentException> {
            validGoalEntity.copy(currencyCode = "UNKNOWN").toDomain()
        }
        // Invalid target date
        assertFailsWith<IllegalArgumentException> {
            validGoalEntity.copy(targetDate = "invalid-date").toDomain()
        }
        // Blank id
        assertFailsWith<IllegalArgumentException> {
            validGoalEntity.copy(id = "").toDomain()
        }
        // Non-positive target amount (model invariant)
        assertFailsWith<IllegalArgumentException> {
            validGoalEntity.copy(targetAmountMinor = 0).toDomain()
        }
        // Negative current amount (model invariant)
        assertFailsWith<IllegalArgumentException> {
            validGoalEntity.copy(currentAmountMinor = -1).toDomain()
        }

        val validContribEntity = GoalContribution(
            id = EntityId("c-1"),
            goalId = EntityId("goal-1"),
            amount = Money(5_000, Currency.TRY),
            direction = GoalContributionDirection.ADD,
            occurredOn = LocalDate(2026, 9, 1),
            note = null,
            createdAt = NOW,
        ).toEntity(SYNC)

        // Invalid direction code
        assertFailsWith<IllegalArgumentException> {
            validContribEntity.copy(directionCode = "INVALID").toDomain()
        }
        // Invalid occurred date
        assertFailsWith<IllegalArgumentException> {
            validContribEntity.copy(occurredOn = "bad-date").toDomain()
        }
        // Non-positive contribution amount
        assertFailsWith<IllegalArgumentException> {
            validContribEntity.copy(amountMinor = 0).toDomain()
        }
        // Blank note (fail-closed if present)
        assertFailsWith<IllegalArgumentException> {
            validContribEntity.copy(note = "   ").toDomain()
        }
    }

    @Test
    fun debt_and_payment_survive_entity_round_trip() {
        val debt = Debt(
            id = EntityId("debt-1"),
            ownerId = USER_ID,
            workspaceId = null,
            title = "Elden Borç",
            amount = Money(30_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 31),
            status = DebtStatus.OPEN,
            description = "Mehmet'ten alındı",
            createdAt = NOW,
        )
        val payment = DebtPayment(
            id = EntityId("pay-1"),
            debtId = debt.id,
            amount = Money(10_000, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = NOW,
        )

        val debtEntity = debt.toEntity(SYNC)
        assertEquals(SYNC, debtEntity.sync)
        assertEquals("Elden Borç", debtEntity.title)
        assertEquals(30_000L, debtEntity.amountMinor)
        assertEquals("TRY", debtEntity.currencyCode)
        assertEquals("DEBT", debtEntity.typeCode)
        assertEquals("2026-12-31", debtEntity.dueDate)
        assertEquals("OPEN", debtEntity.statusCode)
        assertEquals("Mehmet'ten alındı", debtEntity.description)
        assertEquals(debt, debtEntity.toDomain())

        val paymentEntity = payment.toEntity(SYNC)
        assertEquals(SYNC, paymentEntity.sync)
        assertEquals("pay-1", paymentEntity.id)
        assertEquals("debt-1", paymentEntity.debtId)
        assertEquals(10_000L, paymentEntity.amountMinor)
        assertEquals("TRY", paymentEntity.currencyCode)
        assertEquals("2026-09-01", paymentEntity.paidOn)
        assertEquals(payment, paymentEntity.toDomain())
    }

    @Test
    fun debt_and_payment_corrupt_data_fails_closed() {
        val validDebtEntity = Debt(
            id = EntityId("debt-1"),
            ownerId = USER_ID,
            workspaceId = null,
            title = "Borç",
            amount = Money(10_000, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 12, 1),
            status = DebtStatus.OPEN,
            description = null,
            createdAt = NOW,
        ).toEntity(SYNC)

        // Invalid type code
        assertFailsWith<IllegalArgumentException> {
            validDebtEntity.copy(typeCode = "INVALID_TYPE").toDomain()
        }
        // Invalid status code
        assertFailsWith<IllegalArgumentException> {
            validDebtEntity.copy(statusCode = "INVALID_STATUS").toDomain()
        }
        // Invalid due date
        assertFailsWith<IllegalArgumentException> {
            validDebtEntity.copy(dueDate = "invalid-date").toDomain()
        }
        // Non-positive amount
        assertFailsWith<IllegalArgumentException> {
            validDebtEntity.copy(amountMinor = 0).toDomain()
        }
        // Blank description
        assertFailsWith<IllegalArgumentException> {
            validDebtEntity.copy(description = "   ").toDomain()
        }

        val validPaymentEntity = DebtPayment(
            id = EntityId("pay-1"),
            debtId = EntityId("debt-1"),
            amount = Money(2_000, Currency.TRY),
            paidOn = LocalDate(2026, 9, 1),
            createdAt = NOW,
        ).toEntity(SYNC)

        // Invalid currency
        assertFailsWith<IllegalArgumentException> {
            validPaymentEntity.copy(currencyCode = "BAD_CURR").toDomain()
        }
        // Invalid paid on date
        assertFailsWith<IllegalArgumentException> {
            validPaymentEntity.copy(paidOn = "bad-date").toDomain()
        }
        // Non-positive payment amount
        assertFailsWith<IllegalArgumentException> {
            validPaymentEntity.copy(amountMinor = 0).toDomain()
        }
    }

    private companion object {
        val USER_ID = EntityId("user-1")
        val WORKSPACE_ID = EntityId("workspace-1")
        val CATEGORY_ID = EntityId("category-1")
        val NOW = Instant.parse("2026-08-05T00:00:00Z")
        val SYNC = newSyncMetadata(NOW.toEpochMilliseconds())
    }
}
