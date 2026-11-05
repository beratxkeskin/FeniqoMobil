package com.feniqo.mobile.demo

import android.app.Application
import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.remote.logo.LogoDevImageFormat
import com.feniqo.mobile.data.remote.logo.LogoDevLogoConfig
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.repository.ConflictResolution
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.logo.LogoDevRemoteMerchantLogoResolver
import com.feniqo.mobile.logo.RemoteMerchantLogoResolverFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DemoSecurityNetworkGateTest {
    private fun createFakeQueue(): OfflineWriteQueue {
        val mutationDao =
            Proxy.newProxyInstance(
                LocalMutationDao::class.java.classLoader,
                arrayOf(LocalMutationDao::class.java),
                object : InvocationHandler {
                    override fun invoke(
                        proxy: Any?,
                        method: Method,
                        args: Array<out Any>?,
                    ): Any? =
                        when (method.returnType) {
                            java.lang.Integer.TYPE -> 0
                            java.lang.Long.TYPE -> 0L
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                },
            ) as LocalMutationDao

        val operationDao =
            Proxy.newProxyInstance(
                SyncOperationDao::class.java.classLoader,
                arrayOf(SyncOperationDao::class.java),
                object : InvocationHandler {
                    override fun invoke(
                        proxy: Any?,
                        method: Method,
                        args: Array<out Any>?,
                    ): Any? =
                        if (method.name == "observePendingCount") {
                            MutableStateFlow(0)
                        } else {
                            when (method.returnType) {
                                java.lang.Integer.TYPE -> 0
                                java.lang.Long.TYPE -> 0L
                                java.lang.Boolean.TYPE -> false
                                else -> null
                            }
                        }
                },
            ) as SyncOperationDao

        return OfflineWriteQueue(
            mutationDao = mutationDao,
            operationDao = operationDao,
        )
    }

    @Test
    fun mergedManifest_containsInternetPermission_andNoSupabaseHostFilter() {
        val mergedManifestFile =
            sequenceOf(
                File("build/intermediates/merged_manifest/demo/processDemoMainManifest/AndroidManifest.xml"),
                File("androidApp/build/intermediates/merged_manifest/demo/processDemoMainManifest/AndroidManifest.xml"),
                File("build/intermediates/merged_manifest/demo/AndroidManifest.xml"),
                File("androidApp/build/intermediates/merged_manifest/demo/AndroidManifest.xml"),
            ).firstOrNull { it.exists() }

        assertNotNull("Birleşmiş demo AndroidManifest.xml bulunamadı", mergedManifestFile)
        requireNotNull(mergedManifestFile)

        val manifestContent = mergedManifestFile.readText()

        // 1. INTERNET izni birleşmiş manifestte bulunmalıdır (main manifestten devralınır)
        assertTrue(
            "Birleşmiş demo manifestinde INTERNET izni bulunmalıdır",
            manifestContent.contains("""<uses-permission android:name="android.permission.INTERNET""""),
        )

        // 2. tools:node="remove" kuralı bulunmamalıdır
        assertFalse(
            "INTERNET izni tools:node=\"remove\" ile kaldırılmış olmamalıdır",
            manifestContent.contains(
                """<uses-permission android:name="android.permission.INTERNET" tools:node="remove"""",
            ),
        )

        // 3. Staging veya production Supabase auth intent-filter'ı bulunmamalıdır.
        // Not: Bu kontrol işletim sistemi düzeyinde host kısıtı kanıtlamaz;
        // yalnızca Supabase auth deep link filtresinin bulunmadığını doğrular.
        assertFalse(
            "Demo manifestinde Supabase auth/host intent-filter'ı bulunmamalıdır",
            manifestContent.contains("android:host"),
        )
    }

    @Test
    fun demoBuildConfig_enforcesDemoTrue_andDemoInvalidUrls() {
        assertTrue("BuildConfig.DEMO kesinlikle true olmalıdır", BuildConfig.DEMO)
        assertEquals(
            "BuildConfig.SUPABASE_URL demo.invalid olmalıdır",
            "https://demo.invalid",
            BuildConfig.SUPABASE_URL,
        )
    }

    @Test
    fun demoSyncRepository_localSyncOperationsFailWithoutNetworkCalls() =
        runBlocking {
            val queue = createFakeQueue()
            val repository = DemoSyncRepository(queue)

            // requestSync() ağ çağrısı başlatmaz, doğrudan demo_local_only hatası döner
            val syncResult = repository.requestSync()
            assertTrue("requestSync failure dönmelidir", syncResult is RepositoryResult.Failure)
            val syncError = (syncResult as RepositoryResult.Failure).error
            assertTrue("Hata AppError.Validation olmalıdır", syncError is AppError.Validation)
            assertEquals("demo_local_only", (syncError as AppError.Validation).code)

            // retryFailedOperations() ağ çağrısı başlatmaz
            val retryResult = repository.retryFailedOperations()
            assertTrue("retryFailedOperations failure dönmelidir", retryResult is RepositoryResult.Failure)

            // resolveConflict() ağ çağrısı başlatmaz
            val resolveResult = repository.resolveConflict(EntityId("sample-id"), ConflictResolution.KEEP_LOCAL)
            assertTrue("resolveConflict failure dönmelidir", resolveResult is RepositoryResult.Failure)

            // observeOverview() çevrimdışı kalır
            val overview = repository.observeOverview().first()
            assertEquals(SyncPhase.OFFLINE, overview.phase)
            assertNull(overview.lastSuccessfulSyncAt)
            assertNull(overview.lastError)
            assertFalse(overview.hasLegacyQuarantinedData)
        }

    @Test
    fun remoteMerchantLogoResolverFactory_whenProviderMissingOrInvalid_returnsNullEnablingFallback() {
        val validKey = "pk_test_sample_valid_key_12345"

        // Provider null veya boş
        assertNull(
            "Provider null iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = null,
                logoDevPublishableKey = validKey,
                logoDevUsageAuthorized = true,
            ),
        )
        assertNull(
            "Provider boş iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "   ",
                logoDevPublishableKey = validKey,
                logoDevUsageAuthorized = true,
            ),
        )

        // Provider none
        assertNull(
            "Provider 'none' iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "none",
                logoDevPublishableKey = validKey,
                logoDevUsageAuthorized = true,
            ),
        )

        // Provider bilinmeyen CDN
        assertNull(
            "Provider bilinmeyen CDN iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "unknown_cdn",
                logoDevPublishableKey = validKey,
                logoDevUsageAuthorized = true,
            ),
        )

        // Provider logodev ama usageAuthorized = false
        assertNull(
            "usageAuthorized false iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = validKey,
                logoDevUsageAuthorized = false,
            ),
        )

        // Provider logodev ama key boş
        assertNull(
            "Key boş iken resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "",
                logoDevUsageAuthorized = true,
            ),
        )

        // Provider logodev ama key secret key (sk_)
        assertNull(
            "Secret key (sk_) ile resolver null dönmelidir",
            RemoteMerchantLogoResolverFactory.create(
                provider = "logodev",
                logoDevPublishableKey = "sk_live_secret_key",
                logoDevUsageAuthorized = true,
            ),
        )
    }

    @Test
    fun logodevResolver_onlyResolvesAllowlistDomainKeys_andReturnsNullForOthers() {
        val config =
            LogoDevLogoConfig(
                publishableKey = "pk_test_sample_valid_key_12345",
                iconSizePx = 128,
                format = LogoDevImageFormat.PNG,
            )
        val resolver = LogoDevRemoteMerchantLogoResolver(config = config)

        // Pilot canonical allowlist merchant'ları doğrulanır
        val migros = resolver.resolve("migros")
        assertNotNull("migros için request üretilmelidir", migros)
        requireNotNull(migros)
        assertTrue("migros domain içermelidir", migros.url.contains("migros.com.tr"))

        val bim = resolver.resolve("bim")
        assertNotNull("bim için request üretilmelidir", bim)
        requireNotNull(bim)
        assertTrue("bim domain içermelidir", bim.url.contains("bim.com.tr"))

        val carrefoursa = resolver.resolve("carrefoursa")
        assertNotNull("carrefoursa için request üretilmelidir", carrefoursa)
        requireNotNull(carrefoursa)
        assertTrue("carrefoursa domain içermelidir", carrefoursa.url.contains("carrefoursa.com"))

        // Allowlist dışındaki anahtarlar, özel adlar veya serbest metinler fail-closed null döner
        assertNull("Allowlist dışı anahtar null dönmelidir", resolver.resolve("unlisted_brand"))
        assertNull("Kullanıcı özel adı null dönmelidir", resolver.resolve("Mahalle Bakkalı"))
        assertNull("URL formatındaki girdi null dönmelidir", resolver.resolve("https://migros.com.tr"))
        assertNull("Boş anahtar null dönmelidir", resolver.resolve(""))
        assertNull("Boşluklu anahtar null dönmelidir", resolver.resolve("   "))
        assertNull("Null anahtar null dönmelidir", resolver.resolve(null))
    }

    @Test
    fun remoteMerchantLogoResolver_whenMerchantKeyIsNull_returnsNullWithoutNetworkRequest() {
        val decision = TransactionMerchantCreateDecision.UseTransactionName
        assertTrue(
            "Karar UseTransactionName tipinde olmalıdır",
            decision is TransactionMerchantCreateDecision.UseTransactionName,
        )

        // Resolver'a null merchant key verildiğinde (UseTransactionName durumunda logo anahtarı null olur)
        // resolver fail-closed null döner ve herhangi bir uzak ağ isteği üretilmez.
        val config =
            LogoDevLogoConfig(
                publishableKey = "pk_test_sample_valid_key_12345",
                iconSizePx = 128,
                format = LogoDevImageFormat.PNG,
            )
        val resolver = LogoDevRemoteMerchantLogoResolver(config = config)
        val result = resolver.resolve(null)
        assertNull("Null logo anahtarı için resolver null dönmelidir", result)
    }
}
