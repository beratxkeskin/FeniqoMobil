package com.feniqo.mobile.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TransactionMerchantCreateDecisionTest {
    @Test
    fun autoDecision_isSingleton() {
        val decision: TransactionMerchantCreateDecision = TransactionMerchantCreateDecision.Auto
        assertIs<TransactionMerchantCreateDecision.Auto>(decision)
        assertEquals(TransactionMerchantCreateDecision.Auto, decision)
    }

    @Test
    fun useTransactionNameDecision_isSingleton() {
        val decision: TransactionMerchantCreateDecision = TransactionMerchantCreateDecision.UseTransactionName
        assertIs<TransactionMerchantCreateDecision.UseTransactionName>(decision)
        assertEquals(TransactionMerchantCreateDecision.UseTransactionName, decision)
    }

    @Test
    fun knownMerchantDecision_validConstruction() {
        val decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("migros"))
        assertEquals("migros", decision.merchantId.value)

        val stringConstructor = TransactionMerchantCreateDecision.KnownMerchant("spotify")
        assertEquals("spotify", stringConstructor.merchantId.value)
    }

    @Test
    fun knownMerchantDecision_invalidConstruction_blankIdFails() {
        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantCreateDecision.KnownMerchant("")
        }

        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantCreateDecision.KnownMerchant("   ")
        }

        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantCreateDecision.KnownMerchant(EntityId(" \t "))
        }
    }

    @Test
    fun patternMatching_coversAllThreeVariants() {
        val decisions: List<TransactionMerchantCreateDecision> =
            listOf(
                TransactionMerchantCreateDecision.Auto,
                TransactionMerchantCreateDecision.KnownMerchant("netflix"),
                TransactionMerchantCreateDecision.UseTransactionName,
            )

        val labels =
            decisions.map { decision ->
                when (decision) {
                    is TransactionMerchantCreateDecision.Auto -> "AUTO"
                    is TransactionMerchantCreateDecision.KnownMerchant -> "KNOWN:${decision.merchantId.value}"
                    is TransactionMerchantCreateDecision.UseTransactionName -> "USE_TX_NAME"
                }
            }

        assertEquals(listOf("AUTO", "KNOWN:netflix", "USE_TX_NAME"), labels)
    }
}
