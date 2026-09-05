package com.feniqo.mobile.presentation.subscription

import android.os.Build

/**
 * Abonelik bildirim izin istemi ve CTA banner görünürlük kararlarını veren saf yardımcı.
 */
object SubscriptionNotificationPermissionHelper {

    /**
     * Android 13 (API 33)+ cihazlarda bildirim izni henüz verilmemişse banner gösterilir.
     * API 32 ve altındaki sürümlerde işletim sistemi çalışma zamanı bildirim izni gerektirmediği için banner gösterilmez.
     *
     * @param sdkInt Cihazın Android SDK API sürüm numarası.
     * @param isPermissionGranted POST_NOTIFICATIONS izninin verilip verilmediği.
     */
    fun shouldShowPermissionPrompt(sdkInt: Int, isPermissionGranted: Boolean): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return !isPermissionGranted
    }
}
