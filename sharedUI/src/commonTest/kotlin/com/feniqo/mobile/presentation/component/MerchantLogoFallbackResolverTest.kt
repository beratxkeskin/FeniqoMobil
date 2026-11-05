package com.feniqo.mobile.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MerchantLogoFallbackResolverTest {

    @Test
    fun resolve_whenPackagedLogoAvailableAndKeyNonBlank_resolvesToPackagedLogo() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = "verified_key",
            packagedLogoAvailable = true,
            categoryIconKey = "subscriptions",
            fallbackLabel = "Spotify",
        )

        assertEquals(MerchantLogoVisualKind.PACKAGED_LOGO, decision.kind)
        assertEquals("verified_key", decision.logoKey)
        assertNull(decision.initial)
    }

    @Test
    fun resolve_whenPackagedLogoAvailableIsTrueButKeyNullOrBlank_doesNotSelectPackagedLogo() {
        // null key
        val decisionNull = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = true,
            categoryIconKey = "subscriptions",
            fallbackLabel = "Spotify",
        )
        assertNotEquals(MerchantLogoVisualKind.PACKAGED_LOGO, decisionNull.kind)
        assertEquals(MerchantLogoVisualKind.CATEGORY_ICON, decisionNull.kind)

        // blank key
        val decisionBlank = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = "   ",
            packagedLogoAvailable = true,
            categoryIconKey = "subscriptions",
            fallbackLabel = "Spotify",
        )
        assertNotEquals(MerchantLogoVisualKind.PACKAGED_LOGO, decisionBlank.kind)
        assertEquals(MerchantLogoVisualKind.CATEGORY_ICON, decisionBlank.kind)
    }

    @Test
    fun resolve_whenPackagedLogoUnavailableAndCategoryValid_resolvesToCategoryIcon() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = "unresolved_logo",
            packagedLogoAvailable = false,
            categoryIconKey = "groceries",
            fallbackLabel = "Migros",
        )

        assertEquals(MerchantLogoVisualKind.CATEGORY_ICON, decision.kind)
        assertNotNull(decision.iconVector)
        assertNull(decision.logoKey)
    }

    @Test
    fun resolve_whenPackagedLogoUnavailableAndCategoryInvalidAndLetterPresent_resolvesToInitial() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = "unresolved_logo",
            packagedLogoAvailable = false,
            categoryIconKey = "non_existent_category",
            fallbackLabel = "Trendyol",
        )

        assertEquals(MerchantLogoVisualKind.INITIAL, decision.kind)
        assertEquals("T", decision.initial)
        assertNull(decision.iconVector)
    }

    @Test
    fun resolve_whenPackagedLogoUnavailableAndCategoryInvalidAndNoLetter_resolvesToGenericTransactionIcon() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = "unresolved_logo",
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = "123", // Rakamlar harf değildir
        )

        assertEquals(MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON, decision.kind)
        assertSame(MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR, decision.iconVector)
    }

    @Test
    fun resolve_turkishInitial_skipsLeadingNumbers_101Market_returnsM() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = "101 Market",
        )

        assertEquals(MerchantLogoVisualKind.INITIAL, decision.kind)
        assertEquals("M", decision.initial)
    }

    @Test
    fun resolve_turkishInitial_istanbul_resolvesToDottedI() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = "istanbul kart",
        )

        assertEquals(MerchantLogoVisualKind.INITIAL, decision.kind)
        assertEquals("İ", decision.initial)
    }

    @Test
    fun resolve_turkishInitial_isik_resolvesToDotlessI() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = "ışık gıda",
        )

        assertEquals(MerchantLogoVisualKind.INITIAL, decision.kind)
        assertEquals("I", decision.initial)
    }

    @Test
    fun resolve_whenNumericOnly_fallsBackToGenericTransactionIcon() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = "123",
        )

        assertEquals(MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON, decision.kind)
        assertSame(MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR, decision.iconVector)
        assertNotEquals(Icons.Outlined.Category, decision.iconVector)
    }

    @Test
    fun resolve_whenLabelBlankOrOnlySymbols_fallsBackToGenericTransactionIcon() {
        val decision1 = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = null,
        )
        assertEquals(MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON, decision1.kind)
        assertSame(MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR, decision1.iconVector)

        val decision2 = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = "invalid_category",
            fallbackLabel = "---",
        )
        assertEquals(MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON, decision2.kind)
        assertSame(MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR, decision2.iconVector)
    }

    @Test
    fun resolve_genericTransactionIcon_isDistinctFromGenericCategoryIcon() {
        val decision = MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = null,
            packagedLogoAvailable = false,
            categoryIconKey = null,
            fallbackLabel = null,
        )

        assertEquals(MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON, decision.kind)
        assertNotEquals(Icons.Outlined.Category, decision.iconVector)
        assertSame(MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR, decision.iconVector)
    }

    @Test
    fun resolve_productionRegistryIsEmpty() {
        // Üretim registry'si lisanslı logo bulunmadığı için daima boş kalmalıdır
        assertEquals(0, MerchantLogoRegistry.size())
        assertNull(MerchantLogoRegistry.resolve("any_key"))
    }
}
