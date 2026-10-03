package com.monumentogram.dora.audio.persistence.auth

import java.io.File

/** Release cannot read a marker or grant the insecure-device development exception. */
internal object DevelopmentDeviceSecurityOverride {
    val notice: String? = null

    @Suppress("UNUSED_PARAMETER", "UnusedParameter", "FunctionOnlyReturningConstant")
    fun allowed(packageName: String, debuggable: Boolean, noBackupDirectory: File): Boolean = false
}
