package com.feniqo.mobile.logo

import com.feniqo.mobile.data.remote.logo.LogoDevLogoConfig
import com.feniqo.mobile.data.remote.logo.LogoDevLogoResolutionResult
import com.feniqo.mobile.data.remote.logo.LogoDevLogoUrlResolver
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoCachePolicy
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoRequest
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoResolver

/**
 * Android platformu için Logo.dev Logo API çözümleyicisini
 * provider-neutral [RemoteMerchantLogoResolver] sözleşmesine bağlayan adaptör.
 *
 * Mimari Sınırlar:
 * - Presentation katmanı yalnız [RemoteMerchantLogoResolver] arayüzünü tüketir.
 * - Kendi içinde ayrı domain/merchant mapping tutmaz; tek doğruluk kaynağı
 *   [com.feniqo.mobile.data.remote.logo.MerchantLogoDomainAllowlist]'tir.
 * - Publishable key veya URL asla loglanmaz veya sızdırılmaz.
 * - Eksik, geçersiz veya bilinmeyen durumlarda fail-closed null döner.
 * - Önbellek politikası olarak kalıcı istemci önbelleğini kapatıp sağlayıcı
 *   yanıt başlıklarını koruyan [RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE] üretir.
 */
class LogoDevRemoteMerchantLogoResolver(
    private val config: LogoDevLogoConfig?,
) : RemoteMerchantLogoResolver {
    override fun resolve(verifiedLogoKey: String?): RemoteMerchantLogoRequest? {
        if (verifiedLogoKey.isNullOrBlank()) return null
        if (config == null) return null

        return when (val result = LogoDevLogoUrlResolver.resolve(verifiedLogoKey, config)) {
            is LogoDevLogoResolutionResult.Available -> {
                RemoteMerchantLogoRequest(
                    url = result.request.url,
                    headers = result.request.headers,
                    cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                )
            }
            is LogoDevLogoResolutionResult.Unavailable -> null
        }
    }
}
