package com.feniqo.mobile.logo

import com.feniqo.mobile.presentation.component.RemoteMerchantLogoCachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteMerchantLogoResolverFactoryTest {
    private val validSyntheticLogoDevKey = "pk_test_sample_valid_key_12345"
    private val validSyntheticBrandfetchClientId = "id_test_brandfetch_12345"
    private val validSyntheticBrandfetchReferer = "https://app.feniqo.com"

    @Test
    fun providerNone_returnsNull() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "none",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
                brandfetchClientId = validSyntheticBrandfetchClientId,
                brandfetchRefererOrigin = validSyntheticBrandfetchReferer,
            )
        assertNull("Provider NONE iken resolver null olmalıdır", resolver)
    }

    @Test
    fun blankOrUnknownProvider_returnsNull() {
        val blankResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "   ",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
            )
        assertNull("Blank provider null dönmelidir", blankResolver)

        val nullProviderResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = null,
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
            )
        assertNull("Null provider null dönmelidir", nullProviderResolver)

        val unknownResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "custom_unknown_cdn",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
            )
        assertNull("Bilinmeyen provider null dönmelidir", unknownResolver)
    }

    @Test
    fun logodev_withAuthorizationFalse_returnsNull() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = false,
            )
        assertNull("Logo.dev authorization false iken resolver null olmalıdır", resolver)
    }

    @Test
    fun logodev_withValidKeyAndAuthorizationTrue_returnsWorkingResolverForAllowlistedMerchants() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
            )
        assertNotNull("Geçerli pk_ ve onay ile LogoDev resolver üretilmelidir", resolver)
        requireNotNull(resolver)

        val request = resolver.resolve("migros")
        assertNotNull("Allowlist'teki 'migros' için request üretilmelidir", request)
        requireNotNull(request)

        assertTrue("Şema HTTPS olmalıdır: ${request.url}", request.url.startsWith("https://img.logo.dev/"))
        assertTrue("Domain allowlist'ten gelmelidir: ${request.url}", request.url.contains("migros.com.tr"))
        assertTrue("Token parametresi yer almalıdır", request.url.contains("token=$validSyntheticLogoDevKey"))
        assertEquals(
            "Cache policy PROVIDER_HEADERS_NO_LOCAL_STORAGE olmalıdır",
            RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            request.cachePolicy,
        )
    }

    @Test
    fun logodev_withLeadingOrTrailingWhitespaceKey_returnsNullProvingNoSilentTrim() {
        val leadingSpaceResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = " $validSyntheticLogoDevKey",
                logoDevUsageAuthorized = true,
            )
        assertNull("Başında boşluk olan key sessizce trim edilmemeli, null dönmelidir", leadingSpaceResolver)

        val trailingSpaceResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "$validSyntheticLogoDevKey ",
                logoDevUsageAuthorized = true,
            )
        assertNull("Sonunda boşluk olan key sessizce trim edilmemeli, null dönmelidir", trailingSpaceResolver)

        val internalSpaceResolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "pk_test_sample valid_key_12345",
                logoDevUsageAuthorized = true,
            )
        assertNull("İçinde boşluk olan key fail-closed null dönmelidir", internalSpaceResolver)
    }

    @Test
    fun logodev_withSecretKey_returnsNullWithoutThrowing() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "sk_test_forbidden_secret_key_12345",
                logoDevUsageAuthorized = true,
            )
        assertNull("sk_ gizli anahtarı fail-closed null dönmelidir", resolver)
    }

    @Test
    fun logodev_withUnknownMerchantKey_returnsNull() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
            )
        requireNotNull(resolver)

        assertNull("Bilinmeyen merchant key için request üretilmemelidir", resolver.resolve("custom_market"))
    }

    @Test
    fun logodev_whenConfigFails_doesNotFallbackToBrandfetch() {
        // Logo.dev seçili ama key geçersiz; Brandfetch config'i ise geçerli
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "sk_invalid_secret_key",
                logoDevUsageAuthorized = true,
                brandfetchClientId = validSyntheticBrandfetchClientId,
                brandfetchRefererOrigin = validSyntheticBrandfetchReferer,
            )
        assertNull("Logo.dev başarısız olduğunda Brandfetch'e otomatik fallback yapılmamalıdır", resolver)
    }

    @Test
    fun brandfetch_whenSelected_doesNotFallbackToLogoDev() {
        // Brandfetch seçili ama config eksik; Logo.dev config'i ise geçerli
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "brandfetch",
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
                brandfetchClientId = null,
                brandfetchRefererOrigin = null,
            )
        assertNull("Brandfetch config eksikken Logo.dev'e fallback yapılmamalıdır", resolver)
    }

    @Test
    fun brandfetch_withValidConfig_preservesExistingBehavior() {
        val resolver =
            RemoteMerchantLogoResolverFactory.create(
                provider = "brandfetch",
                logoDevPublishableKey = null,
                logoDevUsageAuthorized = false,
                brandfetchClientId = validSyntheticBrandfetchClientId,
                brandfetchRefererOrigin = validSyntheticBrandfetchReferer,
            )
        assertNotNull("Geçerli config ile Brandfetch resolver üretilmelidir", resolver)
        requireNotNull(resolver)

        val request = resolver.resolve("migros")
        assertNotNull("Brandfetch 'migros' için request üretmelidir", request)
        requireNotNull(request)

        assertTrue("Host cdn.brandfetch.io olmalıdır: ${request.url}", request.url.startsWith("https://cdn.brandfetch.io/"))
        assertEquals(
            "Brandfetch cache policy STRICT_NO_STORE olmalıdır",
            RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            request.cachePolicy,
        )
    }

    @Test
    fun toString_doesNotExposeSyntheticKeys() {
        val config =
            RemoteLogoRuntimeConfig(
                provider = RemoteLogoProvider.LOGO_DEV,
                logoDevPublishableKey = validSyntheticLogoDevKey,
                logoDevUsageAuthorized = true,
                brandfetchClientId = validSyntheticBrandfetchClientId,
                brandfetchRefererOrigin = validSyntheticBrandfetchReferer,
            )
        val stringRepresentation = config.toString()

        assertFalse(
            "Gerçek sentetik Logo.dev key toString çıktısında yer almamalıdır",
            stringRepresentation.contains(validSyntheticLogoDevKey),
        )
        assertFalse(
            "Gerçek sentetik Brandfetch client ID toString çıktısında yer almamalıdır",
            stringRepresentation.contains(validSyntheticBrandfetchClientId),
        )
        assertTrue("Logo.dev key maskelenmelidir (pk_***)", stringRepresentation.contains("pk_***"))
        assertTrue("Brandfetch Client ID maskelenmelidir (***)", stringRepresentation.contains("***"))
    }

    @Test
    fun mainActivity_usesCentralResolverFactoryAndHasNoDirectBrandfetchConstruction() {
        val mainActivityFile =
            sequenceOf(
                File("src/main/kotlin/com/feniqo/mobile/MainActivity.kt"),
                File("androidApp/src/main/kotlin/com/feniqo/mobile/MainActivity.kt"),
            ).firstOrNull { it.exists() }
        assertNotNull("MainActivity.kt dosyası bulunabilmelidir", mainActivityFile)
        requireNotNull(mainActivityFile)

        val content = mainActivityFile.readText()

        assertFalse(
            "MainActivity doğrudan BrandfetchRemoteMerchantLogoResolver.createFromBuildConfig çağırmamalıdır",
            content.contains("BrandfetchRemoteMerchantLogoResolver.createFromBuildConfig()"),
        )
        assertFalse(
            "MainActivity BrandfetchRemoteMerchantLogoResolver sınıfını import etmemelidir",
            content.contains("import com.feniqo.mobile.logo.BrandfetchRemoteMerchantLogoResolver"),
        )
        assertTrue(
            "MainActivity RemoteMerchantLogoResolverFactory.createFromBuildConfig() kullanmalıdır",
            content.contains("RemoteMerchantLogoResolverFactory.createFromBuildConfig()"),
        )
    }
}
