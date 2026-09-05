package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CoreFinanceRemoteMappersTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun maps_target_transaction_contract_without_double() {
        val dto = json.decodeFromString<TransactionDto>(
            """{"id":"tx-1","user_id":"user-1","amount_minor":12550,"currency":"TRY","type":"expense","category_id":"cat-1","payment_method":"credit_card","transaction_date":"2026-08-13","created_at":"2026-08-13T10:00:00Z"}""",
        )

        val transaction = dto.toDomain()

        assertEquals(12_550L, transaction.amount.amountMinor)
        assertEquals(Currency.TRY, transaction.amount.currency)
        assertEquals(TransactionType.EXPENSE, transaction.type)
        assertEquals(PaymentMethod.CREDIT_CARD, transaction.paymentMethod)
    }

    @Test
    fun refuses_legacy_amount_when_amount_minor_is_missing() {
        assertFailsWith<MissingFieldException> {
            json.decodeFromString<TransactionDto>(
                """{"id":"tx-1","user_id":"user-1","amount":125.50,"type":"expense","category_id":"cat-1","payment_method":"Nakit","transaction_date":"2026-08-13","created_at":"2026-08-13T10:00:00Z"}""",
            )
        }
    }

    @Test
    fun reads_legacy_turkish_payment_method_but_writes_stable_code() {
        val domain = transactionDto(paymentMethod = "Kredi Kartı").toDomain()

        assertEquals(PaymentMethod.CREDIT_CARD, domain.paymentMethod)
        assertEquals("credit_card", domain.toDto().paymentMethod)
    }

    @Test
    fun requires_all_installment_fields_together() {
        assertFailsWith<RemoteMappingException> {
            transactionDto(
                installmentNumber = 1,
                totalInstallments = null,
                installmentGroupId = "group-1",
            ).toDomain()
        }
    }

    @Test
    fun rejects_public_receipt_url_at_domain_boundary() {
        assertFailsWith<IllegalArgumentException> {
            transactionDto(receiptPath = "https://example.com/public-receipt.jpg").toDomain()
        }
    }

    @Test
    fun category_serialization_uses_supabase_column_names() {
        val encoded = json.encodeToString(
            CategoryDto(
                id = "cat-1",
                userId = "user-1",
                name = "Market",
                type = "expense",
                color = "#10B981",
                createdAt = "2026-08-13T10:00:00Z",
            ),
        )

        assertTrue("\"user_id\"" in encoded)
        assertTrue("\"is_default\"" in encoded)
        assertFalse("userId" in encoded)
    }

    private fun transactionDto(
        paymentMethod: String = "cash",
        receiptPath: String? = null,
        installmentNumber: Int? = null,
        totalInstallments: Int? = null,
        installmentGroupId: String? = null,
    ): TransactionDto = TransactionDto(
        id = "tx-1",
        userId = "user-1",
        amountMinor = 12_550,
        currency = "TRY",
        type = "expense",
        categoryId = "cat-1",
        paymentMethod = paymentMethod,
        transactionDate = "2026-08-13",
        receiptPath = receiptPath,
        installmentNumber = installmentNumber,
        totalInstallments = totalInstallments,
        installmentGroupId = installmentGroupId,
        createdAt = "2026-08-13T10:00:00Z",
    )

    @Test
    fun subscription_round_trip_with_all_fields_populated() {
        val dto = subscriptionDto(
            categoryId = "cat-1",
            endDate = "2027-08-01",
        )

        val domain = dto.toDomain()

        assertEquals("sub-1", domain.id.value)
        assertEquals("user-1", domain.ownerId.value)
        assertEquals(null, domain.workspaceId)
        assertEquals("Spotify Premium", domain.name)
        assertEquals(5999L, domain.amount.amountMinor)
        assertEquals(Currency.TRY, domain.amount.currency)
        assertEquals("cat-1", domain.categoryId?.value)
        assertEquals(com.feniqo.mobile.domain.model.RecurrenceFrequency.MONTHLY, domain.renewalRule.frequency)
        assertEquals(1, domain.renewalRule.interval)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2026, 8, 1), domain.renewalRule.startDate)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2027, 8, 1), domain.renewalRule.endDate)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2026, 9, 1), domain.nextRenewalDate)
        assertTrue(domain.isActive)

        val roundTripDto = domain.toDto()
        assertEquals(dto.id, roundTripDto.id)
        assertEquals(dto.userId, roundTripDto.userId)
        assertEquals(dto.name, roundTripDto.name)
        assertEquals(dto.amountMinor, roundTripDto.amountMinor)
        assertEquals(dto.currency, roundTripDto.currency)
        assertEquals(dto.categoryId, roundTripDto.categoryId)
        assertEquals(dto.frequency, roundTripDto.frequency)
        assertEquals(dto.interval, roundTripDto.interval)
        assertEquals(dto.startDate, roundTripDto.startDate)
        assertEquals(dto.endDate, roundTripDto.endDate)
        assertEquals(dto.nextRenewalDate, roundTripDto.nextRenewalDate)
        assertEquals(dto.isActive, roundTripDto.isActive)
    }

    @Test
    fun subscription_with_nullable_fields_succeeds() {
        val dto = subscriptionDto(
            categoryId = null,
            endDate = null,
            updatedAt = null,
            deletedAt = null,
            version = null,
        )

        val domain = dto.toDomain()
        assertEquals(null, domain.categoryId)
        assertEquals(null, domain.renewalRule.endDate)

        val mappedDto = domain.toDto()
        assertEquals(null, mappedDto.categoryId)
        assertEquals(null, mappedDto.endDate)
    }

    @Test
    fun subscription_rejects_blank_category_id() {
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(categoryId = "").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(categoryId = "   ").toDomain()
        }
    }

    @Test
    fun subscription_rejects_workspace_id() {

        assertFailsWith<RemoteMappingException> {
            subscriptionDto(workspaceId = "ws-1").toDomain()
        }
    }

    @Test
    fun subscription_rejects_blank_and_too_long_name() {
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(name = "").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(name = "   ").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(name = "s".repeat(501)).toDomain()
        }
    }

    @Test
    fun subscription_rejects_non_positive_amount_and_interval() {
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(amountMinor = 0).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(amountMinor = -100).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(interval = 0).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(interval = -1).toDomain()
        }
    }

    @Test
    fun subscription_rejects_invalid_date_boundaries() {
        // end_date < start_date
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(startDate = "2026-08-01", endDate = "2026-07-31").toDomain()
        }
        // next_renewal_date < start_date
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(startDate = "2026-08-01", nextRenewalDate = "2026-07-31").toDomain()
        }
        // next_renewal_date > end_date
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(startDate = "2026-08-01", endDate = "2026-08-15", nextRenewalDate = "2026-09-01").toDomain()
        }
    }

    @Test
    fun subscription_rejects_invalid_currency_frequency_or_date_format() {
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(currency = "INVALID").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(frequency = "INVALID_FREQ").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(startDate = "not-a-date").toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            subscriptionDto(nextRenewalDate = "not-a-date").toDomain()
        }
    }

    @Test
    fun subscription_dto_serialization_uses_supabase_column_names_and_toDto_trims_name() {
        val domain = subscriptionDto(name = "   Netflix Premium   ").toDomain()
        val dto = domain.toDto()

        assertEquals("Netflix Premium", dto.name)

        val encoded = json.encodeToString(dto)
        assertTrue("\"user_id\"" in encoded)
        assertTrue("\"amount_minor\"" in encoded)
        assertTrue("\"category_id\"" in encoded)
        assertTrue("\"start_date\"" in encoded)
        assertTrue("\"next_renewal_date\"" in encoded)
        assertTrue("\"is_active\"" in encoded)
        assertFalse("userId" in encoded)
        assertFalse("amountMinor" in encoded)
        assertFalse("categoryId" in encoded)
        assertFalse("nextRenewalDate" in encoded)
    }

    private fun subscriptionDto(
        id: String = "sub-1",
        userId: String = "user-1",
        workspaceId: String? = null,
        name: String = "Spotify Premium",
        amountMinor: Long = 5999,
        currency: String = "TRY",
        categoryId: String? = "cat-1",
        frequency: String = "MONTHLY",
        interval: Int = 1,
        startDate: String = "2026-08-01",
        endDate: String? = null,
        nextRenewalDate: String = "2026-09-01",
        isActive: Boolean = true,
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = null,
        deletedAt: String? = null,
        version: Long? = 1L,
    ): com.feniqo.mobile.data.remote.dto.SubscriptionDto = com.feniqo.mobile.data.remote.dto.SubscriptionDto(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        categoryId = categoryId,
        frequency = frequency,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        nextRenewalDate = nextRenewalDate,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    @Test
    fun goal_round_trip_with_all_fields_populated() {
        val dto = goalDto(
            iconKey = "flag",
        )

        val domain = dto.toDomain()

        assertEquals("goal-1", domain.id.value)
        assertEquals("user-1", domain.ownerId.value)
        assertEquals(null, domain.workspaceId)
        assertEquals("Yeni Araba", domain.name)
        assertEquals(500_000_00L, domain.targetAmount.amountMinor)
        assertEquals(100_000_00L, domain.currentAmount.amountMinor)
        assertEquals(Currency.TRY, domain.targetAmount.currency)
        assertEquals(Currency.TRY, domain.currentAmount.currency)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2027, 12, 31), domain.targetDate)
        assertEquals("#10B981", domain.color.hex)
        assertEquals("flag", domain.icon?.key)

        val roundTripDto = domain.toDto()
        assertEquals(dto.id, roundTripDto.id)
        assertEquals(dto.userId, roundTripDto.userId)
        assertEquals(dto.name, roundTripDto.name)
        assertEquals(dto.targetAmountMinor, roundTripDto.targetAmountMinor)
        assertEquals(dto.currentAmountMinor, roundTripDto.currentAmountMinor)
        assertEquals(dto.currency, roundTripDto.currency)
        assertEquals(dto.targetDate, roundTripDto.targetDate)
        assertEquals(dto.colorHex, roundTripDto.colorHex)
        assertEquals(dto.iconKey, roundTripDto.iconKey)
    }

    @Test
    fun goal_fails_closed_on_invalid_fields() {
        assertFailsWith<RemoteMappingException> { goalDto(workspaceId = "ws-1").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(targetAmountMinor = 0).toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(targetAmountMinor = -100).toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(currentAmountMinor = -1).toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(name = "   ").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(targetDate = "invalid-date").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(colorHex = "red").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(colorHex = "#12345").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(iconKey = "   ").toDomain() }
        assertFailsWith<RemoteMappingException> { goalDto(currency = "INVALID").toDomain() }
    }

    @Test
    fun goal_dto_decoding_fails_closed_when_current_amount_or_currency_is_missing() {
        assertFailsWith<MissingFieldException> {
            json.decodeFromString<com.feniqo.mobile.data.remote.dto.GoalDto>(
                """{"id":"goal-1","user_id":"user-1","name":"Tatil","target_amount_minor":500000,"currency":"TRY","target_date":"2027-12-31","color_hex":"#10B981","created_at":"2026-08-01T10:00:00Z"}""",
            )
        }

        assertFailsWith<MissingFieldException> {
            json.decodeFromString<com.feniqo.mobile.data.remote.dto.GoalDto>(
                """{"id":"goal-1","user_id":"user-1","name":"Tatil","target_amount_minor":500000,"current_amount_minor":100000,"target_date":"2027-12-31","color_hex":"#10B981","created_at":"2026-08-01T10:00:00Z"}""",
            )
        }
    }

    @Test
    fun debt_dto_decoding_fails_closed_when_status_or_currency_is_missing() {
        assertFailsWith<MissingFieldException> {
            json.decodeFromString<com.feniqo.mobile.data.remote.dto.DebtDto>(
                """{"id":"debt-1","user_id":"user-1","title":"Borç","amount_minor":15000,"currency":"TRY","type":"DEBT","due_date":"2026-10-01","created_at":"2026-08-01T10:00:00Z"}""",
            )
        }

        assertFailsWith<MissingFieldException> {
            json.decodeFromString<com.feniqo.mobile.data.remote.dto.DebtDto>(
                """{"id":"debt-1","user_id":"user-1","title":"Borç","amount_minor":15000,"type":"DEBT","due_date":"2026-10-01","status":"OPEN","created_at":"2026-08-01T10:00:00Z"}""",
            )
        }
    }

    @Test
    fun goal_contribution_round_trip_with_all_fields_populated() {

        val dto = goalContributionDto(
            direction = "ADD",
            note = "Aylık tasarruf",
        )

        val domain = dto.toDomain()

        assertEquals("contrib-1", domain.id.value)
        assertEquals("goal-1", domain.goalId.value)
        assertEquals(25_000_00L, domain.amount.amountMinor)
        assertEquals(Currency.TRY, domain.amount.currency)
        assertEquals(com.feniqo.mobile.domain.model.GoalContributionDirection.ADD, domain.direction)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2026, 9, 1), domain.occurredOn)
        assertEquals("Aylık tasarruf", domain.note)

        val roundTripDto = domain.toDto()
        assertEquals(dto.id, roundTripDto.id)
        assertEquals(dto.goalId, roundTripDto.goalId)
        assertEquals(dto.amountMinor, roundTripDto.amountMinor)
        assertEquals(dto.currency, roundTripDto.currency)
        assertEquals(dto.direction, roundTripDto.direction)
        assertEquals(dto.occurredOn, roundTripDto.occurredOn)
        assertEquals(dto.note, roundTripDto.note)
    }

    @Test
    fun goal_contribution_fails_closed_on_invalid_fields() {
        assertFailsWith<RemoteMappingException> { goalContributionDto(amountMinor = 0).toDomain() }
        assertFailsWith<RemoteMappingException> { goalContributionDto(amountMinor = -500).toDomain() }
        assertFailsWith<RemoteMappingException> { goalContributionDto(direction = "INVALID").toDomain() }
        assertFailsWith<RemoteMappingException> { goalContributionDto(occurredOn = "not-a-date").toDomain() }
        assertFailsWith<RemoteMappingException> { goalContributionDto(note = "   ").toDomain() }
        assertFailsWith<RemoteMappingException> { goalContributionDto(currency = "XYZ").toDomain() }
    }

    @Test
    fun debt_round_trip_with_all_fields_populated() {
        val dto = debtDto(
            type = "DEBT",
            status = "OPEN",
            description = "Elden borç",
        )

        val domain = dto.toDomain()

        assertEquals("debt-1", domain.id.value)
        assertEquals("user-1", domain.ownerId.value)
        assertEquals(null, domain.workspaceId)
        assertEquals("Ahmet'e Borç", domain.title)
        assertEquals(15_000_00L, domain.amount.amountMinor)
        assertEquals(Currency.TRY, domain.amount.currency)
        assertEquals(com.feniqo.mobile.domain.model.DebtType.DEBT, domain.type)
        assertEquals(com.feniqo.mobile.domain.model.DebtStatus.OPEN, domain.status)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2026, 10, 1), domain.dueDate)
        assertEquals("Elden borç", domain.description)

        val roundTripDto = domain.toDto()
        assertEquals(dto.id, roundTripDto.id)
        assertEquals(dto.userId, roundTripDto.userId)
        assertEquals(dto.title, roundTripDto.title)
        assertEquals(dto.amountMinor, roundTripDto.amountMinor)
        assertEquals(dto.currency, roundTripDto.currency)
        assertEquals(dto.type, roundTripDto.type)
        assertEquals(dto.status, roundTripDto.status)
        assertEquals(dto.dueDate, roundTripDto.dueDate)
        assertEquals(dto.description, roundTripDto.description)
    }

    @Test
    fun debt_fails_closed_on_invalid_fields() {
        assertFailsWith<RemoteMappingException> { debtDto(workspaceId = "ws-1").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(amountMinor = 0).toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(amountMinor = -100).toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(title = "   ").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(type = "INVALID_TYPE").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(status = "INVALID_STATUS").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(dueDate = "bad-date").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(description = "   ").toDomain() }
        assertFailsWith<RemoteMappingException> { debtDto(currency = "FOO").toDomain() }
    }

    @Test
    fun debt_payment_round_trip_with_all_fields_populated() {
        val dto = debtPaymentDto()

        val domain = dto.toDomain()

        assertEquals("payment-1", domain.id.value)
        assertEquals("debt-1", domain.debtId.value)
        assertEquals(5_000_00L, domain.amount.amountMinor)
        assertEquals(Currency.TRY, domain.amount.currency)
        assertEquals(com.feniqo.mobile.domain.model.LocalDate(2026, 9, 15), domain.paidOn)

        val roundTripDto = domain.toDto()
        assertEquals(dto.id, roundTripDto.id)
        assertEquals(dto.debtId, roundTripDto.debtId)
        assertEquals(dto.amountMinor, roundTripDto.amountMinor)
        assertEquals(dto.currency, roundTripDto.currency)
        assertEquals(dto.paidOn, roundTripDto.paidOn)
    }

    @Test
    fun debt_payment_fails_closed_on_invalid_fields() {
        assertFailsWith<RemoteMappingException> { debtPaymentDto(amountMinor = 0).toDomain() }
        assertFailsWith<RemoteMappingException> { debtPaymentDto(amountMinor = -10).toDomain() }
        assertFailsWith<RemoteMappingException> { debtPaymentDto(paidOn = "invalid").toDomain() }
        assertFailsWith<RemoteMappingException> { debtPaymentDto(currency = "ABC").toDomain() }
    }

    @Test
    fun goal_and_debt_dto_serialization_uses_supabase_column_names() {
        val goalEncoded = json.encodeToString(goalDto())
        assertTrue("\"user_id\"" in goalEncoded)
        assertTrue("\"target_amount_minor\"" in goalEncoded)
        assertTrue("\"current_amount_minor\"" in goalEncoded)
        assertTrue("\"target_date\"" in goalEncoded)
        assertTrue("\"color_hex\"" in goalEncoded)
        assertFalse("targetAmountMinor" in goalEncoded)

        val contribEncoded = json.encodeToString(goalContributionDto())
        assertTrue("\"goal_id\"" in contribEncoded)
        assertTrue("\"amount_minor\"" in contribEncoded)
        assertTrue("\"occurred_on\"" in contribEncoded)
        assertFalse("goalId" in contribEncoded)

        val debtEncoded = json.encodeToString(debtDto())
        assertTrue("\"user_id\"" in debtEncoded)
        assertTrue("\"amount_minor\"" in debtEncoded)
        assertTrue("\"due_date\"" in debtEncoded)
        assertFalse("amountMinor" in debtEncoded)

        val paymentEncoded = json.encodeToString(debtPaymentDto())
        assertTrue("\"debt_id\"" in paymentEncoded)
        assertTrue("\"amount_minor\"" in paymentEncoded)
        assertTrue("\"paid_on\"" in paymentEncoded)
        assertFalse("debtId" in paymentEncoded)

        val goalSyncRecord = com.feniqo.mobile.data.remote.dto.GoalContributionSyncRecordDto(
            contribution = goalContributionDto(),
            goal = goalDto(),
        )
        val goalSyncEncoded = json.encodeToString(goalSyncRecord)
        assertTrue("\"contribution\"" in goalSyncEncoded)
        assertTrue("\"goal\"" in goalSyncEncoded)

        val debtSyncRecord = com.feniqo.mobile.data.remote.dto.DebtPaymentSyncRecordDto(
            payment = debtPaymentDto(),
            debt = debtDto(),
        )
        val debtSyncEncoded = json.encodeToString(debtSyncRecord)
        assertTrue("\"payment\"" in debtSyncEncoded)
        assertTrue("\"debt\"" in debtSyncEncoded)
    }

    private fun goalDto(
        id: String = "goal-1",
        userId: String = "user-1",
        workspaceId: String? = null,
        name: String = "Yeni Araba",
        targetAmountMinor: Long = 500_000_00L,
        currentAmountMinor: Long = 100_000_00L,
        currency: String = "TRY",
        targetDate: String = "2027-12-31",
        colorHex: String = "#10B981",
        iconKey: String? = null,
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = null,
        deletedAt: String? = null,
        version: Long? = 1L,
    ): com.feniqo.mobile.data.remote.dto.GoalDto = com.feniqo.mobile.data.remote.dto.GoalDto(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        name = name,
        targetAmountMinor = targetAmountMinor,
        currentAmountMinor = currentAmountMinor,
        currency = currency,
        targetDate = targetDate,
        colorHex = colorHex,
        iconKey = iconKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    private fun goalContributionDto(
        id: String = "contrib-1",
        goalId: String = "goal-1",
        amountMinor: Long = 25_000_00L,
        currency: String = "TRY",
        direction: String = "ADD",
        occurredOn: String = "2026-09-01",
        note: String? = null,
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = null,
        deletedAt: String? = null,
        version: Long? = 1L,
    ): com.feniqo.mobile.data.remote.dto.GoalContributionDto = com.feniqo.mobile.data.remote.dto.GoalContributionDto(
        id = id,
        goalId = goalId,
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        occurredOn = occurredOn,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    private fun debtDto(
        id: String = "debt-1",
        userId: String = "user-1",
        workspaceId: String? = null,
        title: String = "Ahmet'e Borç",
        amountMinor: Long = 15_000_00L,
        currency: String = "TRY",
        type: String = "DEBT",
        dueDate: String = "2026-10-01",
        status: String = "OPEN",
        description: String? = null,
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = null,
        deletedAt: String? = null,
        version: Long? = 1L,
    ): com.feniqo.mobile.data.remote.dto.DebtDto = com.feniqo.mobile.data.remote.dto.DebtDto(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        title = title,
        amountMinor = amountMinor,
        currency = currency,
        type = type,
        dueDate = dueDate,
        status = status,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    private fun debtPaymentDto(
        id: String = "payment-1",
        debtId: String = "debt-1",
        amountMinor: Long = 5_000_00L,
        currency: String = "TRY",
        paidOn: String = "2026-09-15",
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = null,
        deletedAt: String? = null,
        version: Long? = 1L,
    ): com.feniqo.mobile.data.remote.dto.DebtPaymentDto = com.feniqo.mobile.data.remote.dto.DebtPaymentDto(
        id = id,
        debtId = debtId,
        amountMinor = amountMinor,
        currency = currency,
        paidOn = paidOn,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

}
