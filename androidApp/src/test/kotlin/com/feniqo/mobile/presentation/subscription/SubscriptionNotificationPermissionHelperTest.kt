package com.feniqo.mobile.presentation.subscription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionNotificationPermissionHelperTest {

    @Test
    fun `shouldShowPermissionPrompt returns false on API less than 33 regardless of permission state`() {
        // API 32 (Android 12L) - izin yok olsa bile işletim sistemi çalışma zamanı izni gerektirmediğinden banner gizlenir
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 32,
                isPermissionGranted = false,
            ),
        )

        // API 32 - izin var durumunda da banner gizlenir
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 32,
                isPermissionGranted = true,
            ),
        )

        // API 28 (Android 9)
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 28,
                isPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `shouldShowPermissionPrompt returns true on API 33 or higher when permission is not granted`() {
        // API 33 (Android 13) - İzin verilmemişse CTA görünür
        assertTrue(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 33,
                isPermissionGranted = false,
            ),
        )

        // API 34 (Android 14) - İzin verilmemişse CTA görünür
        assertTrue(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 34,
                isPermissionGranted = false,
            ),
        )

        // API 35 (Android 15) - İzin verilmemişse CTA görünür
        assertTrue(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 35,
                isPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `shouldShowPermissionPrompt returns false on API 33 or higher when permission is granted`() {
        // API 33 (Android 13) - İzin verilmişse CTA gizlenir
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 33,
                isPermissionGranted = true,
            ),
        )

        // API 34 (Android 14) - İzin verilmişse CTA gizlenir
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 34,
                isPermissionGranted = true,
            ),
        )

        // API 35 (Android 15) - İzin verilmişse CTA gizlenir
        assertFalse(
            SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
                sdkInt = 35,
                isPermissionGranted = true,
            ),
        )
    }
}
