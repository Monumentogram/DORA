package com.monumentogram.dora.poc.recovery.candidate

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File

/** Keeps the historical default, with an explicit, separately admitted API33 route. */
internal object RecoveryPreflightInstrumentationIdentity {
    private const val API33 = 33

    fun requireAccepted(arguments: Bundle) {
        val profile = arguments.getString("recoveryDeviceProfile")
        val actual =
            RecoveryE36GapiDeviceIdentity(
                Build.VERSION.SDK_INT,
                Build.FINGERPRINT,
                Build.PRODUCT,
                Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        val expected =
            if (profile == null) null
            else
                RecoveryE36GapiDeviceIdentity(
                    API33,
                    requireNotNull(arguments.getString("recoveryExpectedFingerprint")),
                    requireNotNull(arguments.getString("recoveryExpectedProduct")),
                    "x86_64",
                )
        RecoveryPreflightDeviceIdentityGuard.requireAccepted(actual, profile, expected)
        if (profile != null) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            for ((key, context) in
                listOf(
                    "recoveryExpectedAppSha256" to instrumentation.targetContext,
                    "recoveryExpectedTestSha256" to instrumentation.context,
                )) {
                val digest = requireNotNull(arguments.getString(key))
                require(Regex("[0-9a-f]{64}").matches(digest))
                require(
                    Sha256Value.calculate(File(context.applicationInfo.sourceDir).readBytes())
                        .toString() == digest
                ) {
                    "Installed APK differs from the admitted identity"
                }
            }
        }
    }
}
