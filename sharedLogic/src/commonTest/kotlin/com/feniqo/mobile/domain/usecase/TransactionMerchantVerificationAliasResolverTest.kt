package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantAliasType
import com.feniqo.mobile.domain.model.MerchantCorrectionRejectionReason
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionMerchantVerificationAliasResolverTest {

    private val catalog = StarterMerchantCatalog.merchants

    @Test
    fun resolve_knownMerchant_genericDescriptionYemek_resolvesCanonicalAliasYemeksepeti() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Yemek",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
            catalog = catalog,
        )

        val resolved = assertIs<TransactionMerchantVerificationAliasResolver.Result.Resolved>(result)
        assertEquals("YEMEKSEPETI", resolved.normalizedAlias)
        assertNotNull(resolved.targetMerchant)
        assertEquals("yemeksepeti", resolved.targetMerchant?.id)
    }

    @Test
    fun resolve_knownMerchant_nonGenericDescription_preservesNormalizedDescriptionAlias() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Ofis Siparişi",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
            catalog = catalog,
        )

        val resolved = assertIs<TransactionMerchantVerificationAliasResolver.Result.Resolved>(result)
        assertEquals("OFIS SIPARISI", resolved.normalizedAlias)
        assertNotNull(resolved.targetMerchant)
        assertEquals("yemeksepeti", resolved.targetMerchant?.id)
    }

    @Test
    fun resolve_useTransactionName_genericDescriptionYemek_rejectedGenericNotAllowed() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Yemek",
            decision = TransactionMerchantCreateDecision.UseTransactionName,
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED,
            rejected.reason,
        )
    }

    @Test
    fun resolve_useTransactionName_nonGenericDescription_resolvesNormalizedDescription() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Mahalle Kasabı",
            decision = TransactionMerchantCreateDecision.UseTransactionName,
            catalog = catalog,
        )

        val resolved = assertIs<TransactionMerchantVerificationAliasResolver.Result.Resolved>(result)
        assertEquals("MAHALLE KASABI", resolved.normalizedAlias)
        assertNull(resolved.targetMerchant)
    }

    @Test
    fun resolve_knownMerchant_unknownMerchantId_rejectedUnknownMerchant() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Yemek",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("non_existent_merchant")),
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT,
            rejected.reason,
        )
    }

    @Test
    fun resolve_auto_rejectedInvalidAlias() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Yemek",
            decision = TransactionMerchantCreateDecision.Auto,
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.INVALID_ALIAS,
            rejected.reason,
        )
    }

    @Test
    fun resolve_blankDescription_rejectedInvalidDescription() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "   ",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.INVALID_DESCRIPTION,
            rejected.reason,
        )
    }

    @Test
    fun resolve_descriptionTooLong_rejectedAliasTooLong() {
        val longDesc = "a".repeat(501)
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = longDesc,
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.ALIAS_TOO_LONG,
            rejected.reason,
        )
    }

    @Test
    fun resolve_excludedTransferDescription_rejectedExcludedTransaction() {
        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Havale EFT",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("yemeksepeti")),
            catalog = catalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.EXCLUDED_TRANSACTION,
            rejected.reason,
        )
    }

    @Test
    fun resolve_knownMerchant_merchantWithOnlyGenericAliases_failsClosed() {
        val fakeMerchant = Merchant(
            id = "generic_only",
            displayName = "Generic Only",
            categoryIconKey = CategoryIcon("food_dining"),
            aliases = listOf(
                MerchantAlias(
                    value = "YEMEK",
                    type = MerchantAliasType.EXACT,
                ),
            ),
        )
        val customCatalog = listOf(fakeMerchant)

        val result = TransactionMerchantVerificationAliasResolver.resolve(
            rawDescription = "Yemek",
            decision = TransactionMerchantCreateDecision.KnownMerchant(EntityId("generic_only")),
            catalog = customCatalog,
        )

        val rejected = assertIs<TransactionMerchantVerificationAliasResolver.Result.Rejected>(result)
        assertEquals(
            MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED,
            rejected.reason,
        )
    }
}