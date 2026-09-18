package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryExternalSigkillProbeGateTest {
    private val nonce = "0123456789abcdef0123456789abcdef"

    @Test
    fun `accepts only explicit physical opt in and lowercase hex32 nonce`() {
        assertEquals(
            nonce,
            RecoveryExternalSigkillProbeGate.requireAccepted(
                "true",
                "POCO-M5-PHYSICAL",
                nonce,
            ),
        )
    }

    @Test
    fun `rejects absent opt in and other profiles`() {
        for (optIn in listOf(null, "false", "TRUE", "1")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryExternalSigkillProbeGate.requireAccepted(
                    optIn,
                    "POCO-M5-PHYSICAL",
                    nonce,
                )
            }
        }
        for (profile in listOf(null, "", "API33-GAPI", "E36-GAPI", "PHYSICAL")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryExternalSigkillProbeGate.requireAccepted("true", profile, nonce)
            }
        }
    }

    @Test
    fun `rejects missing malformed or uppercase nonce`() {
        for (invalid in
            listOf(
                null,
                "",
                "0123456789abcdef0123456789abcde",
                "0123456789abcdef0123456789abcdef0",
                "0123456789ABCDEF0123456789ABCDEF",
                "0123456789abcdef0123456789abcdeg",
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryExternalSigkillProbeGate.requireAccepted(
                    "true",
                    "POCO-M5-PHYSICAL",
                    invalid,
                )
            }
        }
    }

    @Test
    fun `normalizes proc SELinux context with terminal NUL and whitespace`() {
        assertEquals(
            "u:r:untrusted_app:s0:c1,c2",
            RecoveryExternalSigkillProbeGate.normalizeSelinuxContext(
                "\u0000 \r\nu:r:untrusted_app:s0:c1,c2\u0000\r\n "
            ),
        )
    }
}
