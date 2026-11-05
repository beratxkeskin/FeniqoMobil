package com.feniqo.mobile.logo

import android.os.Build
import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoConfig
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoResolutionResult
import com.feniqo.mobile.data.remote.logo.BrandfetchLogoUrlResolver
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoRequest
import com.feniqo.mobile.presentation.component.RemoteMerchantLogoResolver

/**
 * Android platformu için Brandfetch Logo API çözümleyicisini
 * provider-neutral [RemoteMerchantLogoResolver] sözleşmesine bağlayan adaptör.
 *
 * Mimari Sınırlar:
 * - Domain katmanı bu sınıfı ve Brandfetch'i bilmez.
 * - Presentation katmanı yalnız [RemoteMerchantLogoResolver] sözleşmesini tüketir.
 * - Client ID, referer veya User-Agent asla loglanmaz.
 * - Eksik, geçersiz veya bilinmeyen anahtarlarda fail-closed null döner.
 */
class BrandfetchRemoteMerchantLogoResolver(
    private val config: BrandfetchLogoConfig?,
) : RemoteMerchantLogoResolver {
    override fun resolve(verifiedLogoKey: String?): RemoteMerchantLogoRequest? {
        if (verifiedLogoKey.isNullOrBlank()) return null
        if (config == null) return null

        return when (val result = BrandfetchLogoUrlResolver.resolve(verifiedLogoKey, config)) {
            is BrandfetchLogoResolutionResult.Available -> {
                RemoteMerchantLogoRequest(
                    url = result.request.url,
                    headers = result.request.headers,
                )
            }
            is BrandfetchLogoResolutionResult.Unavailable -> null
        }
    }

    companion object {
        /**
         * Güvenli, teknik ve kişisel/finansal veri içermeyen User-Agent değeri üretir.
         */
        fun buildTechnicalUserAgent(
            versionName: String = BuildConfig.VERSION_NAME,
            sdkInt: Int = Build.VERSION.SDK_INT,
        ): String {
            val safeVersion = versionName.trim().takeIf { it.isNotEmpty() } ?: "0.0.0-dev"
            return "FeniqoMobil/$safeVersion (Android $sdkInt)"
        }

        /**
         * BuildConfig ve runtime ortamından güvenli resolver örneği oluşturur.
         * Değerler eksik veya geçersizse resolver crash olmaz, disabled (null config) döner.
         */
        fun createFromBuildConfig(): BrandfetchRemoteMerchantLogoResolver {
            val clientId = BuildConfig.BRANDFETCH_CLIENT_ID.trim().takeIf { it.isNotEmpty() }
            val referer = BuildConfig.BRANDFETCH_REFERER_ORIGIN.trim().takeIf { it.isNotEmpty() }
            if (clientId == null || referer == null) {
                return BrandfetchRemoteMerchantLogoResolver(config = null)
            }
            val userAgent = buildTechnicalUserAgent()
            val config =
                BrandfetchLogoConfig(
                    clientId = clientId,
                    referer = referer,
                    userAgent = userAgent,
                )
            return BrandfetchRemoteMerchantLogoResolver(config = config)
        }
    }
}
