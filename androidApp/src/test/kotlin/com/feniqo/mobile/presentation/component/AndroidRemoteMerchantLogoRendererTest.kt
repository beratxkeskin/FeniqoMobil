package com.feniqo.mobile.presentation.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h2400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidRemoteMerchantLogoRendererTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val recordedRequests = mutableListOf<ImageRequest>()
    private var shouldFailNextRequest = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        recordedRequests.clear()
        shouldFailNextRequest = false
    }

    @After
    fun tearDown() {
        recordedRequests.clear()
        Coil.reset()
    }

    private fun triggerDraw(): Bitmap {
        composeRule.waitForIdle()
        val activity = (composeRule as AndroidComposeTestRule<*, *>).activity
        val root = activity.window.decorView
        if (root.width <= 0 || root.height <= 0) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 1080, 2400)
        }
        val width = root.width.coerceAtLeast(1080)
        val height = root.height.coerceAtLeast(2400)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        composeRule.waitForIdle()
        return bitmap
    }

    private fun createFakeImageLoader(): ImageLoader {
        val loader =
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            recordedRequests.add(chain.request)
                            if (shouldFailNextRequest) {
                                ErrorResult(
                                    drawable = null,
                                    request = chain.request,
                                    throwable = IOException("Controlled test network failure"),
                                )
                            } else {
                                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                                val drawable = BitmapDrawable(context.resources, bitmap)
                                SuccessResult(
                                    drawable = drawable,
                                    request = chain.request,
                                    dataSource = DataSource.MEMORY,
                                )
                            }
                        },
                    )
                }.build()
        Coil.setImageLoader(loader)
        return loader
    }

    @Test
    fun render_whenSuccess_removesFallbackContentAfterImageLoad() {
        val fakeLoader = createFakeImageLoader()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_test_sample&size=128&format=png&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        composeRule.setContent {
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = request,
                    modifier = Modifier.size(40.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        triggerDraw()

        // Success sonrasında fallback içeriği kaldırılmış olmalıdır
        composeRule.onNodeWithTag("fallback_content_tag").assertDoesNotExist()

        // Fake loader'a tam olarak 1 istek ulaşmalıdır
        assertEquals(1, recordedRequests.size)
        assertEquals(request.url, recordedRequests.first().data)
    }

    @Test
    fun render_whenError_keepsFallbackVisibleWithoutCrash() {
        shouldFailNextRequest = true
        val fakeLoader = createFakeImageLoader()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/unknown.com?token=pk_test_sample&size=128&format=png&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        composeRule.setContent {
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = request,
                    modifier = Modifier.size(40.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        triggerDraw()

        // Hata durumunda fallback içeriği görünür kalmalı ve crash olmamalıdır
        composeRule.onNodeWithTag("fallback_content_tag").assertIsDisplayed()
        assertEquals(1, recordedRequests.size)
    }

    @Test
    fun render_recompositionWithSameRequest_doesNotTriggerSecondRequest() {
        val fakeLoader = createFakeImageLoader()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_test_sample&size=128&format=png&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        var recompositionTrigger by mutableIntStateOf(0)

        composeRule.setContent {
            // recompositionTrigger okunarak recomposition tetiklenir
            @Suppress("UNUSED_VARIABLE")
            val trigger = recompositionTrigger
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = request,
                    modifier = Modifier.size(40.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        triggerDraw()
        assertEquals(1, recordedRequests.size)

        // Yeniden derleme (recomposition) tetiklenir
        recompositionTrigger++
        composeRule.waitForIdle()
        triggerDraw()

        // Aynı istek için gereksiz ikinci bir ImageRequest tetiklenmemelidir
        assertEquals(1, recordedRequests.size)
    }

    @Test
    fun render_whenRequestChanges_initiatesNewLoadForNewMerchant() {
        val fakeLoader = createFakeImageLoader()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val requestMigros =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=pk_test_sample&size=128&format=png&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )
        val requestCarrefour =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/carrefoursa.com?token=pk_test_sample&size=128&format=png&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        var activeRequest by mutableStateOf(requestMigros)

        composeRule.setContent {
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = activeRequest,
                    modifier = Modifier.size(40.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        triggerDraw()
        assertEquals(1, recordedRequests.size)
        assertEquals(requestMigros.url, recordedRequests.first().data)

        // İstek CarrefourSA olarak değiştiğinde
        activeRequest = requestCarrefour
        composeRule.waitForIdle()
        triggerDraw()

        // Yeni istek tetiklenmeli ve toplam 2 istek kaydedilmelidir
        assertEquals(2, recordedRequests.size)
        assertEquals(requestCarrefour.url, recordedRequests[1].data)
    }

    private data class MarkPixelAnalysis(
        val count: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val maxDistanceFromCenter: Float,
        val clippedPixelsOutsideRadius: Int,
    ) {
        val width: Int get() = if (count > 0) maxX - minX + 1 else 0
        val height: Int get() = if (count > 0) maxY - minY + 1 else 0
    }

    private fun analyzeRedMark(
        bitmap: Bitmap,
        centerX: Float = 64f,
        centerY: Float = 64f,
        circularRadius: Float = 64f,
    ): MarkPixelAnalysis {
        var count = 0
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var maxDist = 0f
        var clippedCount = 0
        val rSquared = circularRadius * circularRadius

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r > 200 && g < 50 && b < 50) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y

                    val dx = x - centerX
                    val dy = y - centerY
                    val distSquared = dx * dx + dy * dy
                    val dist = kotlin.math.sqrt(distSquared)
                    if (dist > maxDist) {
                        maxDist = dist
                    }
                    if (distSquared > rSquared) {
                        clippedCount++
                    }
                }
            }
        }
        return MarkPixelAnalysis(
            count = count,
            minX = if (count > 0) minX else 0,
            maxX = if (count > 0) maxX else 0,
            minY = if (count > 0) minY else 0,
            maxY = if (count > 0) maxY else 0,
            maxDistanceFromCenter = maxDist,
            clippedPixelsOutsideRadius = clippedCount,
        )
    }

    @Test
    fun render_withAppIconEmphasizedScale_rendersSuccessfullyWithoutCrash() {
        val fakeLoader = createFakeImageLoader()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/netflix.com?token=pk_test_sample&size=128&format=png&theme=dark&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                surfacePolicy = RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL,
                contentScalePolicy = RemoteMerchantLogoContentScalePolicy.APP_ICON_EMPHASIZED,
            )

        composeRule.setContent {
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = request,
                    modifier = Modifier.size(40.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        triggerDraw()

        // Success sonrasında fallback içeriği kaldırılmış olmalıdır
        composeRule.onNodeWithTag("fallback_content_tag").assertDoesNotExist()

        // Fake loader'a tam olarak 1 istek ulaşmalıdır
        assertEquals(1, recordedRequests.size)
        assertEquals(request.url, recordedRequests.first().data)
    }

    @Test
    fun render_withAppIconEmphasizedScale_enlargesVisibleMarkDeterministically_withoutCircularClipping() {
        // Kontrollü sentetik bitmap: 128x128 şeffaf zemin üzerinde Netflix 'N' sembolünün
        // resmî Logo.dev sınırlayıcı kutusuna (bounding box [51..76], [40..87]) birebir uyan
        // 26x48 piksellik kırmızı (#E41E26) sentetik amblem.
        val syntheticBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        for (x in 51..76) {
            for (y in 40..87) {
                syntheticBitmap.setPixel(x, y, android.graphics.Color.RED)
            }
        }
        val fakeLoader =
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            recordedRequests.add(chain.request)
                            val drawable = BitmapDrawable(context.resources, syntheticBitmap)
                            SuccessResult(
                                drawable = drawable,
                                request = chain.request,
                                dataSource = DataSource.MEMORY,
                            )
                        },
                    )
                }.build()
        val renderer = AndroidRemoteMerchantLogoRenderer()
        val requestDefault =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/netflix.com?token=pk_test_sample&size=128&format=png&theme=dark&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                surfacePolicy = RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL,
                contentScalePolicy = RemoteMerchantLogoContentScalePolicy.DEFAULT,
            )
        val requestEmphasized =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/netflix.com?token=pk_test_sample&size=128&format=png&theme=dark&fallback=404",
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
                surfacePolicy = RemoteMerchantLogoSurfacePolicy.DARK_NEUTRAL,
                contentScalePolicy = RemoteMerchantLogoContentScalePolicy.APP_ICON_EMPHASIZED,
            )

        var activeRequest by mutableStateOf(requestDefault)

        composeRule.setContent {
            CompositionLocalProvider(LocalImageLoader provides fakeLoader) {
                renderer.render(
                    request = activeRequest,
                    modifier = Modifier.size(128.dp),
                    fallbackContent = {
                        Text(
                            text = "FALLBACK_ICON",
                            modifier = Modifier.testTag("fallback_content_tag"),
                        )
                    },
                )
            }
        }

        // 1. DEFAULT (1.0f) ölçek ile çizim ve piksel analizi
        composeRule.waitForIdle()
        triggerDraw()
        composeRule.waitForIdle()
        val bitmapDefault = triggerDraw()
        val defaultAnalysis = analyzeRedMark(bitmapDefault, centerX = 64f, centerY = 64f, circularRadius = 64f)

        assertEquals("Varsayılan genişlik 26 px olmalıdır", 26, defaultAnalysis.width)
        assertEquals("Varsayılan yükseklik 48 px olmalıdır", 48, defaultAnalysis.height)
        assertEquals("Varsayılan piksel alanı 26x48=1248 px olmalıdır", 1248, defaultAnalysis.count)

        // 2. APP_ICON_EMPHASIZED (1.8f) ölçek ile çizim ve piksel analizi
        activeRequest = requestEmphasized
        composeRule.waitForIdle()
        triggerDraw()
        composeRule.waitForIdle()
        val bitmapEmphasized = triggerDraw()
        val emphasizedAnalysis = analyzeRedMark(bitmapEmphasized, centerX = 64f, centerY = 64f, circularRadius = 64f)

        // 3. Regresyon İspatı: Emphasized durumda görünür kırmızı alan belirgin şekilde büyür
        assertTrue(
            "Vurgulu piksel alanı (${emphasizedAnalysis.count}) varsayılandan (${defaultAnalysis.count}) büyük olmalıdır",
            emphasizedAnalysis.count > defaultAnalysis.count,
        )
        assertTrue(
            "Vurgulu piksel alanı en az 2.5 katına çıkmalıdır (beklenen ~3.2x)",
            emphasizedAnalysis.count >= (defaultAnalysis.count * 2.5).toInt(),
        )
        assertTrue(
            "Vurgulu genişlik (${emphasizedAnalysis.width}) varsayılan genişlikten (${defaultAnalysis.width}) büyük olmalıdır",
            emphasizedAnalysis.width > defaultAnalysis.width,
        )
        assertTrue(
            "Vurgulu yükseklik (${emphasizedAnalysis.height}) varsayılan yükseklikten (${defaultAnalysis.height}) büyük olmalıdır",
            emphasizedAnalysis.height > defaultAnalysis.height,
        )

        val widthScaleRatio = emphasizedAnalysis.width.toFloat() / defaultAnalysis.width.toFloat()
        val heightScaleRatio = emphasizedAnalysis.height.toFloat() / defaultAnalysis.height.toFloat()
        assertTrue("Genişlik ölçek oranı 1.7f..1.9f aralığında olmalıdır: $widthScaleRatio", widthScaleRatio in 1.7f..1.9f)
        assertTrue("Yükseklik ölçek oranı 1.7f..1.9f aralığında olmalıdır: $heightScaleRatio", heightScaleRatio in 1.7f..1.9f)

        // 4. Kırpılmama Kanıtı (P2-1 gereksinimi): 64 px dairesel sınır dışında hiçbir renkli piksel bulunmamalıdır
        assertEquals(
            "Dairesel maske yarıçapı (64 px) dışında renkli piksel bulunmamalıdır (sıfır kırpılma)",
            0,
            emphasizedAnalysis.clippedPixelsOutsideRadius,
        )
        assertTrue(
            "En uzak piksel mesafesi (${emphasizedAnalysis.maxDistanceFromCenter} px) dairesel yarıçaptan (64 px) küçük kalmalıdır",
            emphasizedAnalysis.maxDistanceFromCenter < 64f,
        )

        // 5. Success sonrasında fallback içeriği kaldırılmış olmalıdır
        composeRule.onNodeWithTag("fallback_content_tag").assertDoesNotExist()

        // 6. Fake loader'a en az 1 istek ulaşmıştır
        assertTrue("Fake loader isteği kaydedilmelidir", recordedRequests.isNotEmpty())
    }
}
