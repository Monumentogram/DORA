@file:Suppress("LongParameterList")

package com.monumentogram.dora.poc.recovery.contract

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryProcessingIntentTest {
    private val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
    private val digest = Sha256Value.fromBytes(ByteArray(32) { it.toByte() })

    @Test
    fun `encoding is exact inherited big endian identity and hash is independent`() {
        val value = input()
        val expected =
            ByteArrayOutputStream()
                .apply {
                    lp16(RecoveryContract.PROTOCOL_ID)
                    lp16(RecoveryCandidate.MICROFILE.contractId)
                    write(run.toByteArray())
                    write(ByteBuffer.allocate(4).putInt(7).array())
                    write(ByteBuffer.allocate(8).putLong(11).array())
                    write(ByteBuffer.allocate(8).putLong(29).array())
                    write(digest.toByteArray())
                }
                .toByteArray()
        assertArrayEquals(expected, RecoveryProcessingIntent.encodedIdentity(value))
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(expected),
            RecoveryProcessingIntent.calculate(value).toByteArray(),
        )
    }

    @Test
    fun `identity is sensitive to every variable field`() {
        val baseline = RecoveryProcessingIntent.calculate(input())
        listOf(
                input(runId = RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f")),
                input(unitIndex = 8UL),
                input(start = 10UL),
                input(end = 30UL),
                input(ciphertext = Sha256Value.fromBytes(ByteArray(32) { (it + 1).toByte() })),
            )
            .forEach { assertNotEquals(baseline, RecoveryProcessingIntent.calculate(it)) }
    }

    @Test
    fun `invalid candidate range and index fail closed`() {
        assertThrows(RecoveryContractException::class.java) {
            input(candidate = RecoveryCandidate.STREAM)
        }
        assertThrows(RecoveryContractException::class.java) {
            input(unitIndex = RecoveryContract.U32_MAX + 1UL)
        }
        assertThrows(RecoveryContractException::class.java) { input(start = 29UL, end = 29UL) }
    }

    private fun input(
        candidate: RecoveryCandidate = RecoveryCandidate.MICROFILE,
        runId: RunId = run,
        unitIndex: ULong = 7UL,
        start: ULong = 11UL,
        end: ULong = 29UL,
        ciphertext: Sha256Value = digest,
    ) = RecoveryProcessingIntentInput(candidate, runId, unitIndex, start, end, ciphertext)

    private fun ByteArrayOutputStream.lp16(value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        write(byteArrayOf((bytes.size ushr 8).toByte(), bytes.size.toByte()))
        write(bytes)
    }
}
