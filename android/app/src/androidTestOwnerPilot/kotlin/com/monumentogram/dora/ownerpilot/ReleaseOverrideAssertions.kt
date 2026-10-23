package com.monumentogram.dora.ownerpilot

import org.junit.Assert.assertThrows

/** ADR-0021 removes credential authentication and its former development bypass entirely. */
internal fun assertReleaseOverridesDenyAllInputs(context: android.content.Context) {
    val prefix = "com.monumentogram.dora.audio.persistence.auth."
    for (name in
        listOf(
            "DevelopmentDeviceSecurityOverride",
            "DevelopmentAppLockOverride",
            "AndroidAppLock",
            "AndroidAuthProof",
            "AppLockAuthenticationActivity",
            "AppLockSession",
        )) {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName(prefix + name, false, context.classLoader)
        }
    }
}
