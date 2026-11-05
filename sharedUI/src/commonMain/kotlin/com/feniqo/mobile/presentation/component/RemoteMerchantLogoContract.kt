package com.feniqo.mobile.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Uzak logo yüklemelerinde uygulanacak istemci önbellek (cache) politikası sözleşmesi.
 */
enum class RemoteMerchantLogoCachePolicy {
    /**
     * Katı no-store politikası (Brandfetch sözleşmesi).
     * `Cache-Control: no-cache, no-store, must-revalidate` ve `Pragma: no-cache` enjekte edilir.
     * İstemci memory ve disk cache tamamen kapatılır (DISABLED), network erişimi açıktır (ENABLED).
     */
    STRICT_NO_STORE,

    /**
     * Sağlayıcı yanıt başlıklarına saygılı, yerel kalıcı önbelleksiz politika (Logo.dev sözleşmesi).
     * İstemci yapay `Cache-Control` veya `Pragma` header'ı enjekte etmez; istekteki mevcut başlıkları korur.
     * İstemci memory ve disk cache tamamen kapatılır (DISABLED), network erişimi açıktır (ENABLED).
     */
    PROVIDER_HEADERS_NO_LOCAL_STORAGE,
}

/**
 * Sağlayıcıdan bağımsız (provider-neutral) uzak logo istek tanımı.
 * Brandfetch, Coil veya herhangi bir üçüncü parti sağlayıcı/kütüphane bağımlılığı içermez.
 */
data class RemoteMerchantLogoRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val cachePolicy: RemoteMerchantLogoCachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
) {
    override fun toString(): String {
        val safeUrl =
            url
                .replace(CLIENT_ID_QUERY_PARAM_REGEX, "c=***")
                .replace(TOKEN_QUERY_PARAM_REGEX, "token=***")
        val headerNames = headers.keys.sorted().joinToString(", ")
        return "RemoteMerchantLogoRequest(" +
            "url=$safeUrl, " +
            "headers=[$headerNames], " +
            "headersCount=${headers.size}, " +
            "cachePolicy=$cachePolicy" +
            ")"
    }

    private companion object {
        private val CLIENT_ID_QUERY_PARAM_REGEX = Regex("(?<=[?&])c=[^&#]*")
        private val TOKEN_QUERY_PARAM_REGEX = Regex("(?<=[?&])token=[^&#]*")
    }
}

/**
 * Sağlayıcıdan bağımsız uzak logo çözümleyici sözleşmesi.
 * UI katmanı yalnız bu arayüzü tüketir; Brandfetch veya veri katmanına doğrudan bağımlılık kurmaz.
 */
fun interface RemoteMerchantLogoResolver {
    fun resolve(verifiedLogoKey: String?): RemoteMerchantLogoRequest?
}

/**
 * Ağaç genelinde uzak logo çözümleyicisini taşıyan CompositionLocal.
 * Varsayılan olarak null'dır (preview'larda, testlerde veya yapılandırılmamış durumlarda güvenle null döner).
 */
val LocalRemoteMerchantLogoResolver: ProvidableCompositionLocal<RemoteMerchantLogoResolver?> =
    compositionLocalOf { null }

/**
 * Uzak logonun görsel çizim durumunu soyutlayan arayüz.
 * Üretimde Android Coil loader'ı kullanır; testlerde ise sahte durumlar (Success/Loading/Error)
 * veya fake image çizimi simüle edilebilir.
 */
fun interface RemoteMerchantLogoRenderer {
    @Composable
    fun render(
        request: RemoteMerchantLogoRequest,
        modifier: Modifier,
        fallbackContent: @Composable () -> Unit,
    )
}

/**
 * Platforma özgü varsayılan uzak logo renderer'ını sağlayan expect fonksiyonu.
 */
expect fun defaultRemoteMerchantLogoRenderer(): RemoteMerchantLogoRenderer

/**
 * Ağaç genelinde uzak logo renderer'ını taşıyan CompositionLocal.
 */
val LocalRemoteMerchantLogoRenderer: ProvidableCompositionLocal<RemoteMerchantLogoRenderer> =
    compositionLocalOf { defaultRemoteMerchantLogoRenderer() }
