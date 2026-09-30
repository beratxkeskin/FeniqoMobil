package com.feniqo.mobile.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SubmitBackNavigationGuardTest {
    @Test
    fun formExitDecision_whileSubmitting_blocksExit() {
        assertEquals(
            FormExitDecision.BLOCK,
            resolveFormExitDecision(isSubmitting = true, hasUnsavedChanges = true),
        )
    }

    @Test
    fun formExitDecision_withUnsavedChanges_requiresConfirmation() {
        assertEquals(
            FormExitDecision.CONFIRM_DISCARD,
            resolveFormExitDecision(isSubmitting = false, hasUnsavedChanges = true),
        )
    }

    @Test
    fun formExitDecision_forCleanForm_navigatesBack() {
        assertEquals(
            FormExitDecision.NAVIGATE_BACK,
            resolveFormExitDecision(isSubmitting = false, hasUnsavedChanges = false),
        )
    }

    @Test
    fun userBack_whenFormIsIdle_navigatesOnce() {
        var navigationCount = 0

        handleSubmitGuardedBack(isSubmitting = false) { navigationCount++ }

        assertEquals(1, navigationCount)
    }

    @Test
    fun userBack_whileSubmitting_isConsumedWithoutNavigation() {
        var navigationCount = 0

        handleSubmitGuardedBack(isSubmitting = true) { navigationCount++ }

        assertEquals(0, navigationCount)
    }

    @Test
    fun userBack_afterSubmittingFinishes_navigatesOnce() {
        var navigationCount = 0

        handleSubmitGuardedBack(isSubmitting = true) { navigationCount++ }
        handleSubmitGuardedBack(isSubmitting = false) { navigationCount++ }

        assertEquals(1, navigationCount)
    }
}
