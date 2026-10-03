package com.feniqo.mobile.ocr

import android.net.Uri
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.ReceiptOcrResult

interface ReceiptOcrService {
    suspend fun recognize(
        imageUri: Uri,
        currency: Currency,
    ): ReceiptOcrResult
}

sealed interface CameraPermissionAction {
    data object UseCamera : CameraPermissionAction
    data object RequestPermission : CameraPermissionAction
    data object ExplainDenial : CameraPermissionAction
    data object OpenSettings : CameraPermissionAction
}

object CameraPermissionPolicy {
    fun nextAction(
        isGranted: Boolean,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): CameraPermissionAction = when {
        isGranted -> CameraPermissionAction.UseCamera
        shouldShowRationale -> CameraPermissionAction.ExplainDenial
        hasRequestedBefore -> CameraPermissionAction.OpenSettings
        else -> CameraPermissionAction.RequestPermission
    }
}
