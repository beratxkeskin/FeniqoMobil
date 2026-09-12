package com.feniqo.mobile.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class DeviceAuthenticationAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE,
}

class AndroidBiometricAuthenticator(
    private val activity: FragmentActivity,
    private val onSuccess: () -> Unit,
    private val onError: (String?) -> Unit,
) {
    fun availability(): DeviceAuthenticationAvailability = when (
        BiometricManager.from(activity).canAuthenticate(AllowedAuthenticators)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> DeviceAuthenticationAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DeviceAuthenticationAvailability.NONE_ENROLLED
        else -> DeviceAuthenticationAvailability.UNAVAILABLE
    }

    fun authenticate() {
        if (availability() != DeviceAuthenticationAvailability.AVAILABLE) {
            onError(null)
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onError("Doğrulama eşleşmedi. Tekrar deneyin.")
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Feniqo kilidini aç")
                .setSubtitle("Biyometri veya cihaz ekran kilidinizle doğrulayın")
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
