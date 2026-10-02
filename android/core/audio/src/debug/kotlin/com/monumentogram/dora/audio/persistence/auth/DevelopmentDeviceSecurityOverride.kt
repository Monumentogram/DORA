package com.monumentogram.dora.audio.persistence.auth

import java.io.File

/** ADR-DEV-002 local opt-in; this granting implementation is absent from release. */
internal object DevelopmentDeviceSecurityOverride {
    const val notice = "Режим разработки: системная блокировка устройства отключена"

    fun allowed(packageName: String, debuggable: Boolean, noBackupDirectory: File): Boolean {
        if (!debuggable || packageName != "com.monumentogram.dora.debug") return false
        return try {
            val root = noBackupDirectory.canonicalFile
            val marker = File(root, "development-device-no-lock")
            marker.canonicalFile == marker &&
                marker.isFile &&
                marker.canRead() &&
                marker.length() == 0L
        } catch (_: Exception) {
            false
        }
    }
}
