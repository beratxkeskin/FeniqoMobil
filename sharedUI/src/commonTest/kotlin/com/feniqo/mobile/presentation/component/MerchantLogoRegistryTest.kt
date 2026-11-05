package com.feniqo.mobile.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MerchantLogoRegistryTest {

    @Test
    fun productionRegistry_isEmptyUntilRealLicensedAssetsAreVerified() {
        assertEquals(0, MerchantLogoRegistry.size())
    }

    @Test
    fun resolve_forNullOrBlank_returnsNull() {
        assertNull(MerchantLogoRegistry.resolve(null))
        assertNull(MerchantLogoRegistry.resolve(""))
        assertNull(MerchantLogoRegistry.resolve("   "))
    }

    @Test
    fun resolve_forUnknownKey_returnsNull() {
        assertNull(MerchantLogoRegistry.resolve("logo_spotify"))
        assertNull(MerchantLogoRegistry.resolve("logo_netflix"))
        assertNull(MerchantLogoRegistry.resolve("non_existent_key"))
    }

    @Test
    fun hasLogo_returnsFalseForEmptyRegistry() {
        assertFalse(MerchantLogoRegistry.hasLogo("logo_spotify"))
        assertFalse(MerchantLogoRegistry.hasLogo(null))
    }
}
