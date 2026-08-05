package com.monumentogram.dora.poc.capture.report

import com.monumentogram.dora.poc.capture.testReceipt
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeExportPolicyTest {
    @Test
    fun `sanitized allowlist entries are accepted`() {
        SafeExportPolicy.validate(
            listOf(
                ExportEntry(
                    SafeExportArchive.DEVICE_PROFILE,
                    "{\"model\":\"Synthetic Phone\",\"uniqueHardwareIdentifierRecorded\":false}"
                        .toByteArray(),
                ),
                ExportEntry(
                    SafeExportArchive.RUN_RESULT,
                    "{\"schemaVersion\":1,\"capture.audio_deleted\":true}".toByteArray(),
                ),
            )
        )
    }

    @Test
    fun `raw audio entry is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeExportPolicy.validate(listOf(ExportEntry("recording.wav", byteArrayOf(1, 2))))
        }
    }

    @Test
    fun `unique identifier fields and absolute paths are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeExportPolicy.validate(
                listOf(
                    ExportEntry(
                        SafeExportArchive.DEVICE_PROFILE,
                        "{\"androidId\":\"value\"}".toByteArray(),
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeExportPolicy.validate(
                listOf(
                    ExportEntry(
                        SafeExportArchive.README,
                        "local file C:\\\\Users\\\\person\\\\capture.wav".toByteArray(),
                    )
                )
            )
        }
    }

    @Test
    fun `export is blocked before verified deletion or while a private capture exists`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportReadiness.requireSafe(testReceipt(success = false), privateCaptureExists = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExportReadiness.requireSafe(testReceipt(), privateCaptureExists = true)
        }
        ExportReadiness.requireSafe(testReceipt(), privateCaptureExists = false)
    }
}
