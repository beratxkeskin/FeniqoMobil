package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.sync.toRemoteSyncMetadata
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecurringTransactionRemoteMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun sampleDto(
        id: String = "rec-1",
        userId: String = "user-1",
        workspaceId: String? = null,
        amountMinor: Long = 50000L,
        currency: String = "TRY",
        type: String = "expense",
        categoryId: String = "cat-1",
        description: String? = "Aylık İnternet Faturası",
        paymentMethod: String = "CREDIT_CARD",
        frequency: String = "MONTHLY",
        interval: Int = 1,
        startDate: String = "2026-08-01",
        endDate: String? = "2026-12-31",
        lastGeneratedDate: String? = "2026-08-01",
        isActive: Boolean = true,
        createdAt: String = "2026-08-01T10:00:00Z",
        updatedAt: String? = "2026-08-15T12:00:00Z",
        deletedAt: String? = null,
        version: Long? = 2L,
    ): RecurringTransactionDto = RecurringTransactionDto(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        amountMinor = amountMinor,
        currency = currency,
        type = type,
        categoryId = categoryId,
        description = description,
        paymentMethod = paymentMethod,
        frequency = frequency,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        lastGeneratedDate = lastGeneratedDate,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        version = version,
    )

    @Test
    fun personal_active_rule_round_trip() {
        val dto = sampleDto(
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
        )

        val domain = dto.toDomain()

        assertEquals(EntityId("rec-1"), domain.id)
        assertEquals(EntityId("user-1"), domain.ownerId)
        assertNull(domain.workspaceId)
        assertEquals(Money(50000L, Currency.TRY), domain.amount)
        assertEquals(TransactionType.EXPENSE, domain.type)
        assertEquals(EntityId("cat-1"), domain.categoryId)
        assertEquals("Aylık İnternet Faturası", domain.description)
        assertEquals(PaymentMethod.CREDIT_CARD, domain.paymentMethod)
        assertEquals(RecurrenceFrequency.MONTHLY, domain.rule.frequency)
        assertEquals(1, domain.rule.interval)
        assertEquals(LocalDate(2026, 8, 1), domain.rule.startDate)
        assertNull(domain.rule.endDate)
        assertNull(domain.lastGeneratedDate)
        assertTrue(domain.isActive)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), domain.createdAt)

        val convertedDto = domain.toDto()
        assertEquals(dto.id, convertedDto.id)
        assertEquals(dto.userId, convertedDto.userId)
        assertNull(convertedDto.workspaceId)
        assertEquals(dto.amountMinor, convertedDto.amountMinor)
        assertEquals("TRY", convertedDto.currency)
        assertEquals("expense", convertedDto.type)
        assertEquals("cat-1", convertedDto.categoryId)
        assertEquals("Aylık İnternet Faturası", convertedDto.description)
        assertEquals("CREDIT_CARD", convertedDto.paymentMethod)
        assertEquals("MONTHLY", convertedDto.frequency)
        assertEquals(1, convertedDto.interval)
        assertEquals("2026-08-01", convertedDto.startDate)
        assertNull(convertedDto.endDate)
        assertNull(convertedDto.lastGeneratedDate)
        assertTrue(convertedDto.isActive)
        assertEquals("2026-08-01T10:00:00Z", convertedDto.createdAt)
    }

    @Test
    fun paused_rule_with_end_date_and_last_generated_date_preserved() {
        val dto = sampleDto(
            endDate = "2026-12-31",
            lastGeneratedDate = "2026-08-15",
            isActive = false,
            paymentMethod = "bank_transfer",
            type = "income",
        )

        val domain = dto.toDomain()

        assertFalse(domain.isActive)
        assertEquals(LocalDate(2026, 12, 31), domain.rule.endDate)
        assertEquals(LocalDate(2026, 8, 15), domain.lastGeneratedDate)
        assertEquals(PaymentMethod.BANK_TRANSFER, domain.paymentMethod)
        assertEquals(TransactionType.INCOME, domain.type)

        val convertedDto = domain.toDto()
        assertFalse(convertedDto.isActive)
        assertEquals("2026-12-31", convertedDto.endDate)
        assertEquals("2026-08-15", convertedDto.lastGeneratedDate)
        assertEquals("BANK_TRANSFER", convertedDto.paymentMethod)
        assertEquals("income", convertedDto.type)
    }

    @Test
    fun serialization_uses_exact_supabase_column_names() {
        val dto = sampleDto()
        val jsonString = json.encodeToString(dto)

        assertTrue("\"user_id\"" in jsonString)
        assertTrue("\"amount_minor\"" in jsonString)
        assertTrue("\"category_id\"" in jsonString)
        assertTrue("\"payment_method\"" in jsonString)
        assertTrue("\"start_date\"" in jsonString)
        assertTrue("\"end_date\"" in jsonString)
        assertTrue("\"last_generated_date\"" in jsonString)
        assertTrue("\"is_active\"" in jsonString)

        assertFalse("userId" in jsonString)
        assertFalse("amountMinor" in jsonString)
        assertFalse("categoryId" in jsonString)
        assertFalse("paymentMethod" in jsonString)
        assertFalse("startDate" in jsonString)
        assertFalse("endDate" in jsonString)
        assertFalse("lastGeneratedDate" in jsonString)
        assertFalse("isActive" in jsonString)
    }

    @Test
    fun rejects_workspace_id_in_v1_personal_scope() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(workspaceId = "ws-1").toDomain()
        }
    }

    @Test
    fun rejects_non_positive_amount() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(amountMinor = 0L).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            sampleDto(amountMinor = -500L).toDomain()
        }
    }

    @Test
    fun rejects_invalid_currency() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(currency = "INVALID").toDomain()
        }
    }

    @Test
    fun rejects_invalid_transaction_type() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(type = "transfer").toDomain()
        }
    }

    @Test
    fun rejects_invalid_payment_method() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(paymentMethod = "CRYPTO").toDomain()
        }
    }

    @Test
    fun rejects_invalid_frequency() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(frequency = "BIWEEKLY").toDomain()
        }
    }

    @Test
    fun rejects_non_positive_interval() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(interval = 0).toDomain()
        }
        assertFailsWith<RemoteMappingException> {
            sampleDto(interval = -1).toDomain()
        }
    }

    @Test
    fun rejects_end_date_before_start_date() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(
                startDate = "2026-08-10",
                endDate = "2026-08-05",
            ).toDomain()
        }
    }

    @Test
    fun rejects_last_generated_date_before_start_date() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(
                startDate = "2026-08-10",
                lastGeneratedDate = "2026-08-05",
            ).toDomain()
        }
    }

    @Test
    fun rejects_last_generated_date_after_end_date() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(
                startDate = "2026-08-01",
                endDate = "2026-08-15",
                lastGeneratedDate = "2026-08-20",
            ).toDomain()
        }
    }

    @Test
    fun normalizes_empty_description_and_rejects_excessive_length() {
        val emptyDescDto = sampleDto(description = "   ")
        assertNull(emptyDescDto.toDomain().description)

        val longDesc = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1)
        assertFailsWith<RemoteMappingException> {
            sampleDto(description = longDesc).toDomain()
        }
    }

    @Test
    fun rejects_invalid_created_at() {
        assertFailsWith<RemoteMappingException> {
            sampleDto(createdAt = "invalid-date").toDomain()
        }
    }

    @Test
    fun toRemoteSyncMetadata_preserves_tombstone_version_and_updated_timestamp() {
        val dto = sampleDto(
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = "2026-08-15T12:00:00Z",
            deletedAt = "2026-08-20T15:00:00Z",
            version = 5L,
        )

        val metadata = dto.toRemoteSyncMetadata(receivedAtEpochMillis = 1700000000000L)

        assertEquals(SyncStatus.SYNCED.name, metadata.syncStatus)
        assertEquals(Instant.parse("2026-08-15T12:00:00Z").toEpochMilliseconds(), metadata.updatedAtEpochMillis)
        assertEquals(1700000000000L, metadata.localUpdatedAtEpochMillis)
        assertEquals(Instant.parse("2026-08-20T15:00:00Z").toEpochMilliseconds(), metadata.deletedAtEpochMillis)
        assertEquals(5L, metadata.version)
        assertEquals(5L, metadata.baseVersion)
        assertNull(metadata.lastSyncError)
    }

    @Test
    fun toRemoteSyncMetadata_falls_back_to_createdAt_when_updatedAt_is_null() {
        val dto = sampleDto(
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = null,
            deletedAt = null,
            version = 1L,
        )

        val metadata = dto.toRemoteSyncMetadata(receivedAtEpochMillis = 1700000000000L)

        assertEquals(Instant.parse("2026-08-01T10:00:00Z").toEpochMilliseconds(), metadata.updatedAtEpochMillis)
        assertNull(metadata.deletedAtEpochMillis)
        assertEquals(1L, metadata.version)
        assertEquals(1L, metadata.baseVersion)
    }
}
