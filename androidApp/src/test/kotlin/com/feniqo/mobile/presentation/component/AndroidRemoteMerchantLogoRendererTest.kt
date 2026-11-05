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
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h2400dp")
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

    private fun triggerDraw() {
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
}
