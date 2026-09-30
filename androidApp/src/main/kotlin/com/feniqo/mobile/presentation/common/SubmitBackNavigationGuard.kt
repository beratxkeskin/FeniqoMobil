package com.feniqo.mobile.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Gönderim sürerken kullanıcı kaynaklı geri navigasyonunun aktif mutasyonu yarıda kesmesini önler. */
internal fun handleSubmitGuardedBack(
    isSubmitting: Boolean,
    onNavigateBack: () -> Unit,
) {
    if (!isSubmitting) {
        onNavigateBack()
    }
}

internal enum class FormExitDecision {
    BLOCK,
    CONFIRM_DISCARD,
    NAVIGATE_BACK,
}

internal fun resolveFormExitDecision(
    isSubmitting: Boolean,
    hasUnsavedChanges: Boolean,
): FormExitDecision = when {
    isSubmitting -> FormExitDecision.BLOCK
    hasUnsavedChanges -> FormExitDecision.CONFIRM_DISCARD
    else -> FormExitDecision.NAVIGATE_BACK
}

/** Sistem geri tuşu ve üst geri eylemi için tek bir kaydedilmemiş değişiklik politikası sağlar. */
@Composable
internal fun rememberGuardedFormExit(
    isSubmitting: Boolean,
    hasUnsavedChanges: Boolean,
    onNavigateBack: () -> Unit,
): () -> Unit {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val requestExit: () -> Unit = {
        when (resolveFormExitDecision(isSubmitting, hasUnsavedChanges)) {
            FormExitDecision.BLOCK -> Unit
            FormExitDecision.CONFIRM_DISCARD -> showDiscardConfirmation = true
            FormExitDecision.NAVIGATE_BACK -> onNavigateBack()
        }
    }

    BackHandler(onBack = requestExit)

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Değişiklikler kaydedilmedi") },
            text = { Text("Bu ekrandan çıkarsan yaptığın değişiklikler kaybolacak.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onNavigateBack()
                    },
                ) { Text("Değişiklikleri sil") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text("Formda kal")
                }
            },
        )
    }
    return requestExit
}
