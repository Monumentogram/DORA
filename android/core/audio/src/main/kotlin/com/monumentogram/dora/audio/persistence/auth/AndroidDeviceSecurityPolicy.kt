package com.monumentogram.dora.audio.persistence.auth

import android.app.Application
import android.app.KeyguardManager
import android.content.pm.ApplicationInfo

/** The only effective device-security boundary for the local product runtime. */
internal class AndroidDeviceSecurityPolicy(application: Application) {
    private val keyguard = application.getSystemService(KeyguardManager::class.java)
    private val policy =
        DeviceSecurityPolicy({ keyguard.isDeviceSecure }, { keyguard.isDeviceLocked }) {
            DevelopmentDeviceSecurityOverride.allowed(
                application.packageName,
                application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                application.noBackupFilesDir,
            )
        }

    fun credentialAvailable(): Boolean = policy.credentialAvailable()

    fun deviceReady(): Boolean = policy.deviceReady()

    fun developmentNotice(): String? =
        if (!keyguard.isDeviceSecure && deviceReady()) DevelopmentDeviceSecurityOverride.notice
        else null
}
