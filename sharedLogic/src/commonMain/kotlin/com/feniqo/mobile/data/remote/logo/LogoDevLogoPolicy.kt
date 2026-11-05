package com.feniqo.mobile.data.remote.logo

import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Desteklenen resmî Logo.dev görsel formatları.
 */
enum class LogoDevImageFormat(
    val extension: String,
) {
    PNG("png"),
    WEBP("webp"),
    JPG("jpg"),
}

/**
 * Logo.dev Logo API için runtime yapılandırma modeli.
 *
 * Mimari ve Güvenlik Sözleşmesi:
 * 1. Fail-closed tasarım: Yapıcı hiçbir exception fırlatmaz; doğrulama [validate] saf
 *    fonksiyonuyla tipli olarak yapılır.
 * 2. Publishable Key: Yalnızca resmî `pk_` ile başlayan publishable key kabul edilir.
 * 3. Secret Key Yasağı: `sk_` ile başlayan gizli anahtarlar kesinlikle reddedilir.
 * 4. Kontrol Karakteri ve Boşluk Yasağı: Key içinde CR/LF, ISO kontrol karakteri veya
 *    boşluk kesinlikle bulunamaz.
 * 5. Maskeleme: [toString] metodu anahtarı asla açıkta bırakmaz (`pk_***`).
 * 6. Boyut Sınırları: [MIN_ICON_SIZE_PX]..[MAX_ICON_SIZE_PX] (16..512 px) aralığı doğrulanır.
 */
data class LogoDevLogoConfig(
    val publishableKey: String?,
    val iconSizePx: Int = DEFAULT_ICON_SIZE_PX,
    val format: LogoDevImageFormat = LogoDevImageFormat.PNG,
) {
    override fun toString(): String {
        val maskedKey =
            when {
                publishableKey.isNullOrBlank() -> "<missing>"
                publishableKey.startsWith(SECRET_KEY_PREFIX) -> "<secret_key_forbidden>"
                publishableKey.startsWith(PUBLISHABLE_KEY_PREFIX) -> "pk_***"
                else -> "***"
            }
        return "LogoDevLogoConfig(publishableKey=$maskedKey, iconSizePx=$iconSizePx, format=${format.extension})"
    }

    /**
     * Konfigürasyonun geçerliliğini fail-closed olarak doğrular.
     * Exception fırlatmaz.
     */
    fun validate(): ConfigValidationResult {
        if (publishableKey.isNullOrBlank()) {
            return ConfigValidationResult.Invalid("publishableKey_missing_or_blank")
        }
        val key = publishableKey

        if (key.any { it.isISOControl() || it == '\r' || it == '\n' }) {
            return ConfigValidationResult.Invalid("publishableKey_contains_control_characters")
        }
        if (key.any { it.isWhitespace() }) {
            return ConfigValidationResult.Invalid("publishableKey_contains_whitespace")
        }
        if (key.startsWith(SECRET_KEY_PREFIX)) {
            return ConfigValidationResult.Invalid("secret_key_forbidden")
        }
        if (!key.startsWith(PUBLISHABLE_KEY_PREFIX)) {
            return ConfigValidationResult.Invalid("publishableKey_must_start_with_pk")
        }
        if (key.length <= PUBLISHABLE_KEY_PREFIX.length) {
            return ConfigValidationResult.Invalid("publishableKey_too_short")
        }
        if (!key.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            return ConfigValidationResult.Invalid("publishableKey_invalid_characters")
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
        const val PUBLISHABLE_KEY_PREFIX: String = "pk_"
        const val SECRET_KEY_PREFIX: String = "sk_"
    }
}

/**
 * Logo.dev CDN için doğrulanmış logo isteği tanımı.
 *
 * Header Sözleşmesi:
 * - Sahte Referer üretilmez.
 * - Domain restriction açılmadığı sürece Referer gerekmez.
 * - Secret Authorization header üretilmez.
 * - Headers varsayılan olarak boştur.
 */
data class LogoDevRemoteLogoRequest(
    val url: String,
    val domain: String,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun toString(): String {
        val safeUrl = url.replace(Regex("(?<=[?&])token=[^&#]*"), "token=pk_***")
        return "LogoDevRemoteLogoRequest(url=$safeUrl, domain=$domain, headersCount=${headers.size})"
    }
}

/**
 * Çözümleme sonucu tipli durumları. Fail-closed; asla exception fırlatmaz.
 */
sealed interface LogoDevLogoResolutionResult {
    data class Available(
        val request: LogoDevRemoteLogoRequest,
    ) : LogoDevLogoResolutionResult

    sealed interface Unavailable : LogoDevLogoResolutionResult {
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
 * Sıfır ağ çağrısı yapan, saf ve deterministik Logo.dev CDN URL çözümleyicisi.
 */
object LogoDevLogoUrlResolver {
    const val EXPECTED_SCHEME: String = "https"
    const val EXPECTED_HOST: String = "img.logo.dev"
    const val FALLBACK_PARAMETER_VALUE: String = "404"

    /**
     * Tek tipli giriş noktası: stable [logoKey] ve runtime [config].
     * Eksik, geçersiz veya bilinmeyen durumlarda fail-closed Unavailable döner; exception atmaz.
     */
    fun resolve(
        logoKey: String?,
        config: LogoDevLogoConfig?,
    ): LogoDevLogoResolutionResult {
        if (logoKey.isNullOrBlank()) {
            return LogoDevLogoResolutionResult.Unavailable.MissingOrBlankKey
        }
        val domain =
            MerchantLogoDomainAllowlist.resolveDomain(logoKey)
                ?: return LogoDevLogoResolutionResult.Unavailable.UnknownKey(logoKey)

        if (config == null) {
            return LogoDevLogoResolutionResult.Unavailable.ProviderNotConfigured
        }

        when (val validation = config.validate()) {
            is LogoDevLogoConfig.ConfigValidationResult.Invalid -> {
                return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration(validation.reason)
            }
            LogoDevLogoConfig.ConfigValidationResult.Valid -> Unit
        }

        val key = config.publishableKey!!
        val size = config.iconSizePx
        val format = config.format.extension

        val encodedKey = percentEncode(key)
        val rawUrl =
            "$EXPECTED_SCHEME://$EXPECTED_HOST/$domain?token=$encodedKey&size=$size&format=$format&fallback=$FALLBACK_PARAMETER_VALUE"

        val parsedUrl =
            try {
                Url(rawUrl)
            } catch (_: URLParserException) {
                return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("url_build_failed")
            } catch (_: IllegalArgumentException) {
                return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("url_build_failed")
            }

        // Sıkı invariant doğrulaması
        if (parsedUrl.protocol != URLProtocol.HTTPS && parsedUrl.protocol.name.lowercase() != "https") {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("protocol_must_be_https")
        }
        if (parsedUrl.host != EXPECTED_HOST) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("host_mismatch")
        }
        if (parsedUrl.encodedPath != "/$domain") {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("path_mismatch")
        }
        if (parsedUrl.parameters["token"] != key) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("token_parameter_mismatch")
        }
        if (parsedUrl.parameters["size"] != size.toString()) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("size_parameter_mismatch")
        }
        if (parsedUrl.parameters["format"] != format) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("format_parameter_mismatch")
        }
        if (parsedUrl.parameters["fallback"] != FALLBACK_PARAMETER_VALUE) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("fallback_parameter_mismatch")
        }
        if (parsedUrl.user != null || parsedUrl.password != null) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("userInfo_forbidden")
        }
        if (parsedUrl.fragment.isNotEmpty()) {
            return LogoDevLogoResolutionResult.Unavailable.InvalidConfiguration("fragment_forbidden")
        }

        val request =
            LogoDevRemoteLogoRequest(
                url = rawUrl,
                domain = domain,
                headers = emptyMap(),
            )
        return LogoDevLogoResolutionResult.Available(request)
    }

    /**
     * RFC 3986 uyumlu saf percent encoding.
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
