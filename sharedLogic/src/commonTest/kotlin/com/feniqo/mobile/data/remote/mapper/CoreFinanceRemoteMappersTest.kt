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
}
