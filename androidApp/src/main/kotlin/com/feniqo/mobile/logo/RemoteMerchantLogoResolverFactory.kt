package com.feniqo.mobile.logo

import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoConfig
import com.feniqo.mobile.data.remote.logo.LogoDevLogoConfig
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoResolver

/**
 * Android çalışma zamanında desteklenen uzak logo sağlayıcıları.
 */
enum class RemoteLogoProvider {
    NONE,
    LOGO_DEV,
    BRANDFETCH,
    ;

    companion object {
        /**
         * Sağlayıcı adını case-insensitive ve whitespace-trimmed biçimde tipli [RemoteLogoProvider] değerine dönüştürür.
         * Boş, null veya bilinmeyen sağlayıcılar için fail-closed null döner.
         */
        fun fromValue(value: String?): RemoteLogoProvider? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().lowercase()) {
                "none" -> NONE
                "logodev", "logo_dev", "logo.dev" -> LOGO_DEV
                "brandfetch" -> BRANDFETCH
                else -> null
            }
        }
    }
}

/**
 * Uzak logo sağlayıcı çalışma zamanı yapılandırması.
 *
 * Güvenlik Sözleşmesi:
 * - [toString] içinde hiçbir publishable veya gizli anahtar açığa çıkmaz.
 */
data class RemoteLogoRuntimeConfig(
    val provider: RemoteLogoProvider,
    val logoDevPublishableKey: String?,
    val logoDevUsageAuthorized: Boolean,
    val brandfetchClientId: String? = null,
    val brandfetchRefererOrigin: String? = null,
) {
    override fun toString(): String {
        val maskedLogoDevKey =
            when {
                logoDevPublishableKey.isNullOrBlank() -> "<missing>"
                logoDevPublishableKey.startsWith("sk_") -> "<secret_key_forbidden>"
                logoDevPublishableKey.startsWith("pk_") -> "pk_***"
                else -> "***"
            }
        val maskedBrandfetchClient =
            when {
                brandfetchClientId.isNullOrBlank() -> "<missing>"
                else -> "***"
            }
        val safeReferer = brandfetchRefererOrigin ?: "<missing>"
        return "RemoteLogoRuntimeConfig(provider=$provider, logoDevPublishableKey=$maskedLogoDevKey, " +
            "logoDevUsageAuthorized=$logoDevUsageAuthorized, brandfetchClientId=$maskedBrandfetchClient, " +
            "brandfetchRefererOrigin=$safeReferer)"
    }
}

/**
 * Android presentation katmanı için tipli, güvenli ve fail-closed
 * [RemoteMerchantLogoResolver] üreticisi.
 *
 * Mimari Kurallar:
 * 1. Default provider [RemoteLogoProvider.NONE]'dır; tanımsız veya geçersiz provider'lar fail-closed null döner.
 * 2. Provider'lar arasında otomatik fallback ASLA yapılmaz (Logo.dev başarısızsa Brandfetch'e geçilmez, tersi de geçerlidir).
 * 3. Logo.dev publishable key değeri SESSİZCE TRİM EDİLMEZ; leading/trailing/internal whitespace ham haliyle korunur
 *    ve [LogoDevLogoConfig.validate] tarafından reddedilir.
 * 4. Gizli anahtarlar (`sk_`) koşulsuz fail-closed reddedilir.
 * 5. Logo.dev kullanım izni ([RemoteLogoRuntimeConfig.logoDevUsageAuthorized]) tam olarak `true` değilse resolver üretilmez.
 * 6. Hiçbir hassas anahtar veya URL loglanmaz.
 */
object RemoteMerchantLogoResolverFactory {
    /**
     * Tipli [RemoteLogoRuntimeConfig] ile resolver örneği üretir.
     */
    fun create(config: RemoteLogoRuntimeConfig): RemoteMerchantLogoResolver? {
        return when (config.provider) {
            RemoteLogoProvider.NONE -> null
            RemoteLogoProvider.LOGO_DEV -> {
                if (!config.logoDevUsageAuthorized) return null
                if (config.logoDevPublishableKey == null) return null

                // Ham key kesinlikle trim edilmez; validate kontrolü whitespace ve formatı denetler
                val logoConfig = LogoDevLogoConfig(publishableKey = config.logoDevPublishableKey)
                if (logoConfig.validate() !is LogoDevLogoConfig.ConfigValidationResult.Valid) {
                    return null
                }
                LogoDevRemoteMerchantLogoResolver(config = logoConfig)
            }
            RemoteLogoProvider.BRANDFETCH -> {
                val clientId = config.brandfetchClientId?.trim()?.takeIf { it.isNotEmpty() }
                val referer = config.brandfetchRefererOrigin?.trim()?.takeIf { it.isNotEmpty() }
                if (clientId == null || referer == null) return null

                val userAgent = BrandfetchRemoteMerchantLogoResolver.buildTechnicalUserAgent()
                val brandfetchConfig =
                    BrandfetchLogoConfig(
                        clientId = clientId,
                        referer = referer,
                        userAgent = userAgent,
                    )
                if (brandfetchConfig.validate() !is BrandfetchLogoConfig.ConfigValidationResult.Valid) {
                    return null
                }
                BrandfetchRemoteMerchantLogoResolver(config = brandfetchConfig)
            }
        }
    }

    /**
     * Açık parametrelerle test edilebilir resolver üretir.
     */
    fun create(
        provider: String?,
        logoDevPublishableKey: String?,
        logoDevUsageAuthorized: Boolean,
        brandfetchClientId: String? = null,
        brandfetchRefererOrigin: String? = null,
    ): RemoteMerchantLogoResolver? {
        val parsedProvider = RemoteLogoProvider.fromValue(provider) ?: return null
        val config =
            RemoteLogoRuntimeConfig(
                provider = parsedProvider,
                logoDevPublishableKey = logoDevPublishableKey,
                logoDevUsageAuthorized = logoDevUsageAuthorized,
                brandfetchClientId = brandfetchClientId,
                brandfetchRefererOrigin = brandfetchRefererOrigin,
            )
        return create(config)
    }

    /**
     * BuildConfig üzerindeki derleme alanlarından güvenli biçimde resolver üretir.
     */
    fun createFromBuildConfig(): RemoteMerchantLogoResolver? =
        create(
            provider = BuildConfig.REMOTE_LOGO_PROVIDER,
            logoDevPublishableKey = BuildConfig.LOGODEV_PUBLISHABLE_KEY,
            logoDevUsageAuthorized = BuildConfig.LOGODEV_USAGE_AUTHORIZED,
            brandfetchClientId = BuildConfig.BRANDFETCH_CLIENT_ID,
            brandfetchRefererOrigin = BuildConfig.BRANDFETCH_REFERER_ORIGIN,
        )
}
