package com.feniqo.mobile.presentation.component

import androidx.test.core.app.ApplicationProvider
import coil.request.CachePolicy
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoConfig
import com.feniqo.mobile.logo.BrandfetchRemoteMerchantLogoResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h2400dp")
class AndroidRemoteMerchantLogoRequestFactoryTest {
    @Test
    fun createImageRequest_enforcesStrictNoCachePolicy_andPreservesHeaders() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/migros.com.tr/w/128/h/128/fallback/404/type/icon?c=synthetic_client_id",
                headers =
                    mapOf(
                        "Referer" to "https://app.feniqo.com",
                        "User-Agent" to "FeniqoMobil/1.0.0 (Android 34)",
                    ),
            )

        val imageRequest = AndroidRemoteMerchantLogoRequestFactory.createImageRequest(context, request)

        // 1. URL ve Veri
        assertEquals(request.url, imageRequest.data)
        assertTrue(
            "URL must originate from official Brandfetch HTTPS CDN",
            request.url.startsWith("https://cdn.brandfetch.io/"),
        )

        // 2. Cache Politikaları
        assertEquals(
            "Memory cache must be strictly DISABLED",
            CachePolicy.DISABLED,
            imageRequest.memoryCachePolicy,
        )
        assertEquals(
            "Disk cache must be strictly DISABLED",
            CachePolicy.DISABLED,
            imageRequest.diskCachePolicy,
        )
        assertEquals(
            "Network cache policy must be ENABLED to allow outbound network request (not HTTP caching)",
            CachePolicy.ENABLED,
            imageRequest.networkCachePolicy,
        )

        // 3. HTTP Header Sözleşmeleri
        assertEquals(
            "Cache-Control must enforce no-cache, no-store, must-revalidate",
            "no-cache, no-store, must-revalidate",
            imageRequest.headers["Cache-Control"],
        )
        assertEquals(
            "Pragma must be no-cache",
            "no-cache",
            imageRequest.headers["Pragma"],
        )
        assertEquals(
            "Referer header from resolver must be preserved",
            "https://app.feniqo.com",
            imageRequest.headers["Referer"],
        )
        assertEquals(
            "User-Agent header from resolver must be preserved",
            "FeniqoMobil/1.0.0 (Android 34)",
            imageRequest.headers["User-Agent"],
        )
    }

    @Test
    fun createImageRequest_providerHeadersNoLocalStorage_doesNotInjectNoCacheHeaders_andKeepsLocalCacheDisabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_synthetic_sample&size=128&format=png&fallback=404",
                headers =
                    mapOf(
                        "Custom-Provider-Header" to "value123",
                        "User-Agent" to "FeniqoMobil/1.0.0 (Android 34)",
                    ),
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        val imageRequest = AndroidRemoteMerchantLogoRequestFactory.createImageRequest(context, request)

        // 1. URL ve Veri
        assertEquals(request.url, imageRequest.data)

        // 2. Cache Politikaları: Memory ve Disk kesinlikle kapalı, network açık
        assertEquals(
            "Memory cache must be strictly DISABLED",
            CachePolicy.DISABLED,
            imageRequest.memoryCachePolicy,
        )
        assertEquals(
            "Disk cache must be strictly DISABLED",
            CachePolicy.DISABLED,
            imageRequest.diskCachePolicy,
        )
        assertEquals(
            "Network cache policy must be ENABLED to allow outbound network request",
            CachePolicy.ENABLED,
            imageRequest.networkCachePolicy,
        )

        // 3. HTTP Header Sözleşmeleri: Cache-Control ve Pragma enjekte edilmez; sağlayıcı başlıkları korunur
        assertNull(
            "Cache-Control must not be injected by application under PROVIDER_HEADERS_NO_LOCAL_STORAGE",
            imageRequest.headers["Cache-Control"],
        )
        assertNull(
            "Pragma must not be injected by application under PROVIDER_HEADERS_NO_LOCAL_STORAGE",
            imageRequest.headers["Pragma"],
        )
        assertEquals(
            "Custom-Provider-Header must be preserved",
            "value123",
            imageRequest.headers["Custom-Provider-Header"],
        )
        assertEquals(
            "User-Agent header must be preserved",
            "FeniqoMobil/1.0.0 (Android 34)",
            imageRequest.headers["User-Agent"],
        )
    }

    @Test
    fun createImageRequest_policyIsSelectedStrictlyByTypedField_notByUrlOrHost() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Logo.dev biçimli URL, STRICT_NO_STORE ile verilirse strict header'lar enjekte edilmelidir
        val logoDevUrlWithStrictPolicy =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_test_sample",
                cachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            )
        val strictImageRequest =
            AndroidRemoteMerchantLogoRequestFactory.createImageRequest(context, logoDevUrlWithStrictPolicy)
        assertEquals(
            "no-cache, no-store, must-revalidate",
            strictImageRequest.headers["Cache-Control"],
        )
        assertEquals(
            "no-cache",
            strictImageRequest.headers["Pragma"],
        )

        // 2. Brandfetch biçimli URL, PROVIDER_HEADERS_NO_LOCAL_STORAGE ile verilirse header enjekte edilmemelidir
        val brandfetchUrlWithProviderPolicy =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/migros.com.tr/icon?c=sample_id",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )
        val providerImageRequest =
            AndroidRemoteMerchantLogoRequestFactory.createImageRequest(context, brandfetchUrlWithProviderPolicy)
        assertNull(
            providerImageRequest.headers["Cache-Control"],
        )
        assertNull(
            providerImageRequest.headers["Pragma"],
        )
    }

    @Test
    fun productionResolver_feedsSafeHttpsBrandfetchUrl_andFixedTechnicalUserAgent() {
        val config =
            BrandfetchLogoConfig(
                clientId = "synthetic_id_123",
                referer = "https://app.feniqo.com",
                userAgent = "FeniqoMobil/1.0.0 (Android 34)",
            )
        val resolver = BrandfetchRemoteMerchantLogoResolver(config = config)

        val resolved = resolver.resolve("migros")
        assertNotNull("Resolved request must not be null for valid config and pilot key", resolved)

        val safeRequest = resolved!!
        assertTrue(
            "Resolved URL must strictly use HTTPS cdn.brandfetch.io",
            safeRequest.url.startsWith("https://cdn.brandfetch.io/"),
        )
        assertEquals("https://app.feniqo.com", safeRequest.headers["Referer"])
        val userAgent = safeRequest.headers["User-Agent"].orEmpty()
        assertTrue(
            "User-Agent must start with technical app name",
            userAgent.startsWith("FeniqoMobil/"),
        )
        assertTrue(
            "User-Agent must include Android OS info",
            userAgent.contains("Android"),
        )
    }
}
