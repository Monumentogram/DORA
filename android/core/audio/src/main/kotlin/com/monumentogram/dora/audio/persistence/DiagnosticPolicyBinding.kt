package com.monumentogram.dora.audio.persistence

import java.security.MessageDigest

internal object DiagnosticPolicyBinding {
    fun verify(debug: Boolean, pin: String?, policy: ByteArray?): Boolean {
        if (pin == null && policy == null) return false
        check(debug && pin != null && policy != null) { "Diagnostic policy unavailable" }
        check(pin.matches(Regex("[a-f0-9]{64}"))) { "Diagnostic policy pin rejected" }
        val digest =
            MessageDigest.getInstance("SHA-256").digest(policy).joinToString("") {
                "%02x".format(it)
            }
        check(digest == pin) { "Diagnostic policy binding rejected" }
        return true
    }
}
