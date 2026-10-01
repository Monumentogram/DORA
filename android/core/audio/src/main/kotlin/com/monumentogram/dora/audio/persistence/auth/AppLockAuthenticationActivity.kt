package com.monumentogram.dora.audio.persistence.auth

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager

/** Manifest-private routing surface; no content and no persisted pending authority. */
internal class AppLockAuthenticationActivity : Activity() {
    private var request: AppLockPromptRequest? = null
    private var cancellation: CancellationSignal? = null
    private var credentialPending = false
    private var started = false
    private var combinedPrompt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        request = intent.getStringExtra("attempt")?.let(AppLockPromptRegistry::take)
        if (request == null) finish()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!started && request != null) {
            started = true
            authenticate()
        }
    }

    private fun authenticate() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure || keyguard.isDeviceLocked) {
            complete(UnlockResult.UNAVAILABLE)
            return
        }
        val builder = BiometricPrompt.Builder(this).setTitle("Unlock DORA")
        val combined = AppLockAuthenticatorPolicy.combinedPrompt(Build.VERSION.SDK_INT)
        combinedPrompt = combined
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            builder.setAllowedAuthenticators(AppLockAuthenticatorPolicy.combinedAuthenticators)
        else
            builder.setNegativeButton("Use device credential", mainExecutor) { _, _ ->
                confirmCredential()
            }
        val signal = CancellationSignal()
        cancellation = signal
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    if (credentialPending || isFinishing) return
                    if (combined || request?.proof?.finishLegacyBiometric(result) == true)
                        complete(UnlockResult.UNLOCKED)
                    else complete(UnlockResult.UNAVAILABLE)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (credentialPending || isFinishing) return
                    if (
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    )
                        complete(UnlockResult.CANCELLED)
                    else confirmCredential()
                }

                override fun onAuthenticationFailed() {
                    /* A rejected match creates no authority. */
                }
            }
        try {
            val prompt = builder.build()
            if (combined) prompt.authenticate(signal, mainExecutor, callback)
            else
                prompt.authenticate(
                    checkNotNull(request).proof.legacyBiometricObject(),
                    signal,
                    mainExecutor,
                    callback,
                )
        } catch (_: Exception) {
            confirmCredential()
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmCredential() {
        if (credentialPending || isFinishing) return
        val intent =
            getSystemService(KeyguardManager::class.java)
                .createConfirmDeviceCredentialIntent(
                    "Unlock DORA",
                    "Confirm your device credential",
                )
        if (intent == null) {
            complete(UnlockResult.CREDENTIAL_SETUP_REQUIRED)
            return
        }
        credentialPending = true
        cancellation?.cancel()
        try {
            startActivityForResult(intent, CREDENTIAL_REQUEST)
        } catch (_: Exception) {
            credentialPending = false
            complete(UnlockResult.UNAVAILABLE)
        }
    }

    @Deprecated("Framework credential routing uses the per-instance request identity")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREDENTIAL_REQUEST || !credentialPending) return
        credentialPending = false
        complete(if (resultCode == RESULT_OK) UnlockResult.UNLOCKED else UnlockResult.CANCELLED)
    }

    override fun onStop() {
        super.onStop()
        if (!credentialPending && !combinedPrompt && !isFinishing) complete(UnlockResult.CANCELLED)
    }

    override fun onDestroy() {
        cancellation?.cancel()
        request?.returned(UnlockResult.CANCELLED)
        request = null
        super.onDestroy()
    }

    private fun complete(result: UnlockResult) {
        request?.returned(result)
        finish()
    }

    companion object {
        private const val CREDENTIAL_REQUEST = 1
    }
}
