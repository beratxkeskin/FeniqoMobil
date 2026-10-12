package com.feniqo.mobile.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.feniqo.mobile.R
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.util.localizedContext

enum class DeviceAuthenticationAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE,
}

class AndroidBiometricAuthenticator(
    private val activity: FragmentActivity,
    private val onSuccess: () -> Unit,
    private val onError: () -> Unit,
) {
    fun availability(): DeviceAuthenticationAvailability = when (
        BiometricManager.from(activity).canAuthenticate(AllowedAuthenticators)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> DeviceAuthenticationAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DeviceAuthenticationAvailability.NONE_ENROLLED
        else -> DeviceAuthenticationAvailability.UNAVAILABLE
    }

    fun authenticate(appLanguage: AppLanguage) {
        if (availability() != DeviceAuthenticationAvailability.AVAILABLE) {
            onError()
            return
        }

        val localized = activity.localizedContext(appLanguage)
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }

                override fun onAuthenticationFailed() {
                    onError()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(localized.getString(R.string.biometric_prompt_title))
                .setSubtitle(localized.getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(AllowedAuthenticators)
                .build(),
        )
    }

    private companion object {
        const val AllowedAuthenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
