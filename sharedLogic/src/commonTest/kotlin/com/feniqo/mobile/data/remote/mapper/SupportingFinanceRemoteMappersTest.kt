package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupportingFinanceRemoteMappersTest {

    @Test
    fun budget_uses_minor_units_and_round_trips() {
        val budget = BudgetDto(
            id = "budget-1",
            userId = "user-1",
            categoryId = "cat-1",
            month = "2026-08",
            limitMinor = 250_000,
            currency = "TRY",
            createdAt = "2026-08-13T10:00:00Z",
        ).toDomain()

        assertEquals(250_000L, budget.limit.amountMinor)
        assertEquals(Currency.TRY, budget.limit.currency)
        assertEquals(250_000L, budget.toDto().limitMinor)
    }

    @Test
    fun rejects_legacy_or_invalid_budget_amount() {
        assertFailsWith<RemoteMappingException> {
            BudgetDto(
                id = "budget-1",
                userId = "user-1",
                categoryId = "cat-1",
                month = "2026-08",
                limitMinor = 0,
                createdAt = "2026-08-13T10:00:00Z",
            ).toDomain()
        }
    }

    @Test
    fun tag_and_transaction_tag_round_trip() {
        val tag = TagDto(
            id = "tag-1",
            userId = "user-1",
            name = "Market",
            createdAt = "2026-08-13T10:00:00Z",
        ).toDomain()
        val relation = TransactionTagDto("tx-1", "tag-1").toDomain()

        assertEquals("Market", tag.name)
        assertEquals("tag-1", tag.toDto().id)
        assertEquals("tx-1", relation.toDto().transactionId)
    }
}
