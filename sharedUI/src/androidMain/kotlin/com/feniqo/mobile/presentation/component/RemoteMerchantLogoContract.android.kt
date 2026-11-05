package com.feniqo.mobile.presentation.component

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Android Remote ImageRequest üretim fabrikası.
 * Üretimde [AndroidRemoteMerchantLogoRenderer] tarafından kullanılır;
 * ayrıca testlerde ImageRequest cache ve header politikalarını doğrulamak için tüketilir.
 */
object AndroidRemoteMerchantLogoRequestFactory {
    fun createImageRequest(
        context: Context,
        request: RemoteMerchantLogoRequest,
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(request.url)
            .apply {
                request.headers.forEach { (name, value) ->
                    addHeader(name, value)
                }
                when (request.cachePolicy) {
                    RemoteMerchantLogoCachePolicy.STRICT_NO_STORE -> {
                        setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                        setHeader("Pragma", "no-cache")
                    }
                    RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE -> {
                        // Uygulama yapay Cache-Control veya Pragma enjekte etmez; mevcut istek başlıkları korunur.
                    }
                }
                memoryCachePolicy(CachePolicy.DISABLED)
                diskCachePolicy(CachePolicy.DISABLED)
                networkCachePolicy(CachePolicy.ENABLED)
            }.build()
}

/**
 * Android platformu için Coil tabanlı, katı no-cache politikası uygulayan uzak logo renderer'ı.
 *
 * LazyList (LazyColumn / LazyRow) yüzeylerinde performans düşüşünü ve gereksiz subcomposition
 * maliyetini önlemek için `SubcomposeAsyncImage` yerine [rememberAsyncImagePainter] kullanır.
 *
 * Cache politikası (Brandfetch Resmî Logo API sözleşmesi):
 * - memoryCachePolicy: DISABLED
 * - diskCachePolicy: DISABLED
 * - networkCachePolicy: ENABLED (ağ isteğine izin verilir)
 * - Cache-Control: no-cache, no-store, must-revalidate (HTTP önbelleği kapatılır)
 * - Pragma: no-cache
 *
 * Hata / Yükleme durumları ve Yaşam Döngüsü:
 * - Image ve Coil painter tüm durumlarda (Loading, Empty, Error, Success) composition, layout
 *   ve draw ağacında tutulur; böylece painter yaşam döngüsü kesintiye uğramaz.
 * - Loading, Empty ve Error durumlarında Image alpha = 0f ile çizim ağacında kalır ve ortalanmış
 *   [fallbackContent] görünür.
 * - Success durumunda Image görünür hale gelir (alpha = 1f) ve [fallbackContent] ağaçtan kaldırılır.
 * - Ekran hata state'ine düşmez; crash, spinner veya boş kutu oluşmaz.
 */
class AndroidRemoteMerchantLogoRenderer : RemoteMerchantLogoRenderer {
    @Composable
    override fun render(
        request: RemoteMerchantLogoRequest,
        modifier: Modifier,
        fallbackContent: @Composable () -> Unit,
    ) {
        val context = LocalContext.current
        val imageRequest =
            remember(request, context) {
                AndroidRemoteMerchantLogoRequestFactory.createImageRequest(context, request)
            }

        val painter = rememberAsyncImagePainter(model = imageRequest)
        val isSuccess = painter.state is AsyncImagePainter.State.Success

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(if (isSuccess) 1f else 0f),
            )

            if (!isSuccess) {
                fallbackContent()
            }
        }
    }
}

actual fun defaultRemoteMerchantLogoRenderer(): RemoteMerchantLogoRenderer = AndroidRemoteMerchantLogoRenderer()
