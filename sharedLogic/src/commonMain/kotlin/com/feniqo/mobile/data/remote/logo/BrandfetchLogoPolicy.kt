package com.feniqo.mobile.data.remote.logo

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/*
 * Brandfetch CDN Logo API için provider-özel sözleşme, allowlist ve saf URL çözümleyicisi.
 *
 * Mimari ve Güvenlik Sözleşmesi:
 * 1. Bu dosya data/provider adaptör sınırındadır; domain katmanı bu sınıfları doğrudan bilmez.
 * 2. Sıfır ağ çağrısı: Bu sınıf hiçbir ağ isteği yapmaz; yalnız güvenli ve tipli URL üretir.
 * 3. Fail-closed config: Runtime Client ID, Referer veya User-Agent eksik veya geçersizse
 *    asla exception fırlatmaz, URL üretmez; tipli Unavailable sonucu döner.
 * 4. Sabit HTTPS ve Host: Şema tam olarak `https`, host tam olarak `cdn.brandfetch.io`.
 * 5. Route ve Fallback: `/domain/<domain>/w/<size>/h/<size>/fallback/404/type/icon?c=<clientId>`.
 *    `fallback/404` parametresi ile Brandfetch'in kendi genel görsel placeholder'ı engellenir;
 *    404 dönmesi istenerek uygulamanın yerel fallback zinciri tetiklenir.
 * 6. Gizlilik ve Maskeleme: Client ID hiçbir `toString()`, log veya analytics çıktısına sızmaz (`***`).
 * 7. No-Cache Sözleşmesi: Ücretsiz Logo API doğrudan hotlink edilir; memory, disk veya HTTP cache kapalıdır.
 */

/**
 * Geriye dönük uyumluluk için [MerchantLogoDomainAllowlist]'e delege eden Brandfetch nesnesi.
 * Tek map kuralını korumak için kendi içinde map tutmaz; tüm çağrıları doğrudan
 * merkezi [MerchantLogoDomainAllowlist]'e yönlendirir.
 */
object BrandfetchDomainAllowlist {
    fun resolveDomain(logoKey: String?): String? = MerchantLogoDomainAllowlist.resolveDomain(logoKey)

    fun isAllowlisted(domain: String?): Boolean = MerchantLogoDomainAllowlist.isAllowlisted(domain)

    fun allowlistKeys(): Set<String> = MerchantLogoDomainAllowlist.allowlistKeys()

    fun size(): Int = MerchantLogoDomainAllowlist.size()
}

/**
 * Brandfetch Logo API için runtime yapılandırma modeli.
 *
 * Fail-closed tasarım:
 * - Yapıcı hiçbir exception fırlatmaz; doğrulama [validate] saf fonksiyonuyla tipli olarak yapılır.
 * - Client ID, Referer origin ve User-Agent dış yapılandırmadan (ör. BuildConfig/environment) gelmelidir.
 * - Uydurma/varsayılan Referer kullanılmaz.
 */
data class BrandfetchLogoConfig(
    val clientId: String?,
    val referer: String?,
    val userAgent: String?,
    val iconSizePx: Int = DEFAULT_ICON_SIZE_PX,
) {
    /**
     * Güvenli loglama için Client ID maskelenir.
     */
    override fun toString(): String {
        val maskedClient = if (clientId.isNullOrBlank()) "<missing>" else "***"
        val safeReferer = referer ?: "<missing>"
        val safeUserAgent = userAgent ?: "<missing>"
        return "BrandfetchLogoConfig(clientId=$maskedClient, referer=$safeReferer, userAgent=$safeUserAgent, iconSizePx=$iconSizePx)"
    }

    /**
     * Konfigürasyonun geçerliliğini fail-closed olarak doğrular.
     * Exception fırlatmaz.
     */
    fun validate(): ConfigValidationResult {
        if (clientId.isNullOrBlank()) {
            return ConfigValidationResult.Invalid("clientId_missing_or_blank")
        }
        if (referer.isNullOrBlank()) {
            return ConfigValidationResult.Invalid("referer_missing_or_blank")
        }
        val refererValidation = validateReferer(referer)
        if (refererValidation != null) {
            return ConfigValidationResult.Invalid(refererValidation)
        }
        if (userAgent.isNullOrBlank()) {
            return ConfigValidationResult.Invalid("userAgent_missing_or_blank")
        }
        val userAgentValidation = validateUserAgent(userAgent)
        if (userAgentValidation != null) {
            return ConfigValidationResult.Invalid(userAgentValidation)
        }
        if (iconSizePx !in MIN_ICON_SIZE_PX..MAX_ICON_SIZE_PX) {
            return ConfigValidationResult.Invalid("iconSizePx_out_of_bounds_$iconSizePx")
        }
        return ConfigValidationResult.Valid
    }

    sealed interface ConfigValidationResult {
        data object Valid : ConfigValidationResult

        data class Invalid(
            val reason: String,
        ) : ConfigValidationResult
    }

    companion object {
        const val DEFAULT_ICON_SIZE_PX: Int = 128
        const val MIN_ICON_SIZE_PX: Int = 16
        const val MAX_ICON_SIZE_PX: Int = 512

        private fun validateReferer(ref: String): String? {
            // Kontrol karakteri veya CR/LF engeli
            if (ref.any { it.isISOControl() || it == '\r' || it == '\n' }) {
                return "referer_contains_control_characters"
            }
            // Whitespace engeli
            if (ref.any { it.isWhitespace() }) {
                return "referer_contains_whitespace"
            }
            // Backslash / authority kaçış engeli
            if (ref.contains('\\')) {
                return "referer_contains_backslash"
            }
            // Yalnız tam https:// şeması
            if (!ref.startsWith("https://")) {
                return "referer_must_use_https"
            }
            // Çift şema / malformed authority engeli
            if (ref.indexOf("://", startIndex = 8) != -1) {
                return "referer_multiple_schemes_forbidden"
            }
            // userInfo engeli (@ karakteri)
            if (ref.contains('@')) {
                return "referer_userInfo_forbidden"
            }
            // query engeli (? karakteri)
            if (ref.contains('?')) {
                return "referer_query_forbidden"
            }
            // fragment engeli (# karakteri)
            if (ref.contains('#')) {
                return "referer_fragment_forbidden"
            }

            val afterScheme = ref.removePrefix("https://")
            if (afterScheme.isEmpty() || afterScheme.startsWith('/') || afterScheme.startsWith(':')) {
                return "referer_host_missing"
            }

            val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val hostPart: String
            if (authority.contains(':')) {
                hostPart = authority.substringBefore(':')
                val portPart = authority.substringAfter(':')
                if (hostPart.isEmpty()) {
                    return "referer_host_missing"
                }
                val portNum = portPart.toIntOrNull() ?: return "referer_invalid_port"
                if (portNum !in 1..65535) {
                    return "referer_invalid_port"
                }
            } else {
                hostPart = authority
            }

            if (!isValidHost(hostPart)) {
                return "referer_host_invalid"
            }

            // Ktor Url parser ile ayrıştırma
            val parsedUrl =
                try {
                    Url(ref)
                } catch (_: Throwable) {
                    return "referer_malformed_url"
                }

            if (parsedUrl.protocol != URLProtocol.HTTPS && parsedUrl.protocol.name.lowercase() != "https") {
                return "referer_must_use_https"
            }
            if (parsedUrl.user != null || parsedUrl.password != null) {
                return "referer_userInfo_forbidden"
            }
            if (!parsedUrl.parameters.isEmpty()) {
                return "referer_query_forbidden"
            }
            if (parsedUrl.fragment.isNotEmpty()) {
                return "referer_fragment_forbidden"
            }

            val path = parsedUrl.encodedPath
            if (path.isNotEmpty() && path != "/") {
                return "referer_path_forbidden"
            }

            if (parsedUrl.port !in 1..65535) {
                return "referer_invalid_port"
            }

            return null
        }

        private fun isValidHost(host: String): Boolean {
            if (host.isEmpty()) return false
            if (host == "." || host == ".." || host.startsWith('.') || host.endsWith('.')) return false
            if (host.contains("..")) return false
            val labels = host.split('.')
            for (label in labels) {
                if (label.isEmpty()) return false
                if (label.startsWith('-') || label.endsWith('-')) return false
                if (!label.all { it.isLetterOrDigit() || it == '-' }) return false
            }
            return true
        }

        private fun validateUserAgent(ua: String): String? {
            if (ua.any { it.isISOControl() || it == '\r' || it == '\n' }) {
                return "userAgent_contains_control_characters"
            }
            return null
        }
    }
}

/**
 * Güvenli ve doğrulanmış remote logo CDN istek tanımı.
 */
data class BrandfetchRemoteLogoRequest(
    val url: String,
    val domain: String,
    val headers: Map<String, String>,
) {
    override fun toString(): String {
        val safeUrl = url.replace(Regex("c=[^&]+"), "c=***")
        return "BrandfetchRemoteLogoRequest(url=$safeUrl, domain=$domain, headersCount=${headers.size})"
    }
}

/**
 * Çözümleme sonucu tipli durumları. Fail-closed; çökmeyen ayrık türler.
 */
sealed interface BrandfetchLogoResolutionResult {
    data class Available(
        val request: BrandfetchRemoteLogoRequest,
    ) : BrandfetchLogoResolutionResult

    sealed interface Unavailable : BrandfetchLogoResolutionResult {
        data object MissingOrBlankKey : Unavailable

        data class UnknownKey(
            val key: String,
        ) : Unavailable

        data object ProviderNotConfigured : Unavailable

        data class InvalidConfiguration(
            val reason: String,
        ) : Unavailable
    }
}

/**
 * Ağ çağrısı yapmayan, saf ve deterministik Brandfetch CDN URL çözümleyicisi.
 */
object BrandfetchLogoUrlResolver {
    const val EXPECTED_SCHEME: String = "https"
    const val EXPECTED_HOST: String = "cdn.brandfetch.io"

    /**
     * Tek tipli giriş noktası: stable [logoKey] ve runtime [config].
     * Config eksik veya geçersizse fail-closed Unavailable döner; exception atmaz.
     */
    fun resolve(
        logoKey: String?,
        config: BrandfetchLogoConfig?,
    ): BrandfetchLogoResolutionResult {
        if (logoKey.isNullOrBlank()) {
            return BrandfetchLogoResolutionResult.Unavailable.MissingOrBlankKey
        }
        val domain =
            BrandfetchDomainAllowlist.resolveDomain(logoKey)
                ?: return BrandfetchLogoResolutionResult.Unavailable.UnknownKey(logoKey)

        if (config == null) {
            return BrandfetchLogoResolutionResult.Unavailable.ProviderNotConfigured
        }

        when (val validation = config.validate()) {
            is BrandfetchLogoConfig.ConfigValidationResult.Invalid -> {
                return BrandfetchLogoResolutionResult.Unavailable.InvalidConfiguration(validation.reason)
            }
            BrandfetchLogoConfig.ConfigValidationResult.Valid -> Unit
        }

        val clientId = config.clientId?.trim().orEmpty()
        val referer = config.referer?.trim().orEmpty()
        val userAgent = config.userAgent?.trim().orEmpty()

        val encodedClientId = percentEncode(clientId)
        val size = config.iconSizePx

        // Explicit route: https://cdn.brandfetch.io/domain/<domain>/w/<size>/h/<size>/fallback/404/type/icon?c=<encodedClientId>
        val url =
            "$EXPECTED_SCHEME://$EXPECTED_HOST/domain/$domain/w/$size/h/$size/fallback/404/type/icon?c=$encodedClientId"

        val headers =
            mapOf(
                "Referer" to referer,
                "User-Agent" to userAgent,
            )

        val request =
            BrandfetchRemoteLogoRequest(
                url = url,
                domain = domain,
                headers = headers,
            )
        return BrandfetchLogoResolutionResult.Available(request)
    }

    /**
     * RFC 3986 uyumlu basit ve saf percent encoding.
     */
    private fun percentEncode(input: String): String {
        val out = StringBuilder()
        for (ch in input) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~' -> {
                    out.append(ch)
                }
                else -> {
                    val bytes = ch.toString().encodeToByteArray()
                    for (b in bytes) {
                        val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                        out.append('%')
                        if (hex.length == 1) out.append('0')
                        out.append(hex)
                    }
                }
            }
        }
        return out.toString()
    }
}
