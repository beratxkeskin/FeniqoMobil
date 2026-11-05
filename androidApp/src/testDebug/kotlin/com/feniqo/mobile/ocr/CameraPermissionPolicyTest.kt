package com.feniqo.mobile.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionPolicyTest {
    @Test
    fun grantedPermission_usesCamera() {
        assertEquals(
            CameraPermissionAction.UseCamera,
            CameraPermissionPolicy.nextAction(true, hasRequestedBefore = true, shouldShowRationale = false),
        )
    }

    @Test
    fun firstRequest_requestsPermission() {
        assertEquals(
            CameraPermissionAction.RequestPermission,
            CameraPermissionPolicy.nextAction(false, hasRequestedBefore = false, shouldShowRationale = false),
        )
    }

    @Test
    fun deniedPermission_explainsWhenRationaleIsAvailable() {
        assertEquals(
            CameraPermissionAction.ExplainDenial,
            CameraPermissionPolicy.nextAction(false, hasRequestedBefore = true, shouldShowRationale = true),
        )
    }

    @Test
    fun permanentlyDeniedPermission_opensSettings() {
        assertEquals(
            CameraPermissionAction.OpenSettings,
            CameraPermissionPolicy.nextAction(false, hasRequestedBefore = true, shouldShowRationale = false),
        )
    }
}
