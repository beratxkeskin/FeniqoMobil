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
 * Uzak logo render edilirken uygulanacak görsel yüzey politikası sözleşmesi.
 * Sağlayıcı adından bağımsızdır (provider-neutral).
 */
enum class RemoteMerchantLogoSurfacePolicy {
    /**
     * Mevcut ve varsayılan yüzey davranışı.
     * Logo yüzeyi standart kategori arkaplan rengini ve stilini kullanır.
     */
    DEFAULT,

    /**
     * Sabit açık nötr logo yüzeyi (Logo.dev sözleşmesi).
     * Şeffaf veya koyu amblemli logoların açık ve koyu temalarda kontrast güvenliğini
     * sağlamak için dairesel açık nötr (beyaz) zemin üzerinde çizilmesini sağlar.
     */
    LIGHT_NEUTRAL,

    /**
     * Sabit koyu nötr logo yüzeyi (Netflix gibi şeffaf kırmızı amblemli markalar için).
     * Şeffaf arka planlı kırmızı amblemin siyah zemin üzerinde uygulama ikonu benzeri
     * kontrast güvenliğiyle çizilmesini sağlar.
     */
    DARK_NEUTRAL,
}

/**
 * Uzak logonun çiziminde uygulanacak içerik ölçeklendirme politikası sözleşmesi.
 */
enum class RemoteMerchantLogoContentScalePolicy(
    val scaleFactor: Float,
) {
    /**
     * Standart ölçekleme (ölçek çarpanı 1.0f).
     * Logo görseli sınırları koruyarak standart boyutunda çizilir.
     */
    DEFAULT(1.0f),

    /**
     * Uygulama ikonu benzeri vurgulu ölçekleme (ölçek çarpanı 1.8f).
     * Geniş iç boşluk taşıyan şeffaf logoların (örn. Netflix 'N') daire sınırında kırpılma
     * oluşturmadan alanın çoğunu dengeli kaplamasını sağlar.
     */
    APP_ICON_EMPHASIZED(1.8f),
    ;

    companion object {
        const val APP_ICON_EMPHASIZED_SCALE: Float = 1.8f
    }
}

/**
 * Sağlayıcıdan bağımsız (provider-neutral) uzak logo istek tanımı.
 * Brandfetch, Coil veya herhangi bir üçüncü parti sağlayıcı/kütüphane bağımlılığı içermez.
 */
data class RemoteMerchantLogoRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val cachePolicy: RemoteMerchantLogoCachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
    val surfacePolicy: RemoteMerchantLogoSurfacePolicy = RemoteMerchantLogoSurfacePolicy.DEFAULT,
    val contentScalePolicy: RemoteMerchantLogoContentScalePolicy = RemoteMerchantLogoContentScalePolicy.DEFAULT,
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
            "cachePolicy=$cachePolicy, " +
            "surfacePolicy=$surfacePolicy, " +
            "contentScalePolicy=$contentScalePolicy" +
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
