package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.crypto.RecoveryCryptoTestFixtures
import com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingSqlCodec
import com.monumentogram.dora.poc.recovery.journal.StreamingSqliteCell
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStreamingSurvivingPrefixTest {
    @Test
    fun `new proof survives exact journal codec and tampered proof state is rejected`() {
        val outcome = RecoveryStreamingIntentBuilder.buildOutcome(actualTruncatedTail())
        val cells = RecoveryStreamingSqlCodec.encodeOutcome(outcome)
        assertEquals(outcome, RecoveryStreamingSqlCodec.decodeOutcome(cells))
        val changed = cells.toMutableList()
        changed[RecoveryStreamingSqlCodec.OUTCOME_COLUMNS.indexOf("pre_fault_source_match_state")] =
            StreamingSqliteCell.Text("VERIFIED_SAME_DESCRIPTOR")
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingSqlCodec.decodeOutcome(changed)
        }
    }

    @Test
    fun `truncated source without exact checkpoint prefix cannot admit completed reads`() {
        val facts = actualTruncatedTail().copy(checkpointPrefixMatches = false)
        val outcome = RecoveryStreamingIntentBuilder.buildOutcome(facts)
        assertEquals(StreamDecision.FATAL, outcome.decision)
        assertEquals(StreamTerminal.NOT_REACHED, outcome.terminal)
        assertEquals(null, outcome.recoveredEnd)
        assertEquals("UNPROVEN_OR_MISMATCH", outcome.preFaultSourceMatch.name)
    }

    @Test
    fun `real TRU01 uncommitted truncation returns authenticated bounded prefix`() {
        val facts = actualTruncatedTail()
        assertEquals(473_256UL, facts.completed!!.candidateEnd)
        assertEquals(6_744UL, facts.witness.acceptedEnd - facts.completed.candidateEnd)
        assertTrue(facts.checkpointPrefixMatches)
        assertEquals(false, facts.preFaultPrefixMatches)
    }

    @Test
    fun `surviving checkpoint prefix admits real completed reads while preserving full witness`() {
        val facts = actualTruncatedTail()
        val outcome = RecoveryStreamingIntentBuilder.buildOutcome(facts)
        assertEquals(StreamDecision.VALID, outcome.decision)
        assertEquals(479_232UL, outcome.preFaultSourceBytes)
        assertEquals(facts.witness.preFaultSourceSha256, outcome.preFaultSourceSha256)
        assertEquals(477_232UL, outcome.observedSourceBytes)
        assertEquals(473_256UL, outcome.recoveredEnd)
        assertEquals("VERIFIED_SURVIVING_CHECKPOINT_PREFIX", outcome.preFaultSourceMatch.name)
    }

    @Suppress("LongMethod") // One bounded real-crypto fixture preserves the capture/read sequence.
    private fun actualTruncatedTail(): RecoveryStreamingValidatedIntentFacts {
        val run = RecoveryCryptoTestFixtures.RUN_ID
        val keyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val plaintext =
            ByteArray(480_000) { i -> ((1_314_991_822 + i * 31 + (i shr 8) * 17) and 255).toByte() }
        val ciphertext = ByteArrayOutputStream()
        val aad = StreamingAad(RecoveryCandidate.STREAM, run)
        val writer = keyset.newEncryptingStream(ciphertext, aad)
        var offset = 0
        while (offset < plaintext.size) {
            val count = minOf(4_080, plaintext.size - offset)
            writer.write(plaintext, offset, count)
            offset += count
        }
        // Capture before close: the real campaign does not finalize the encrypted stream.
        val before = ciphertext.toByteArray()
        assertEquals(479_232, before.size)
        val after = before.copyOf(before.size - 2_000)
        assertArrayEquals(before.copyOf(475_136), after.copyOf(475_136))
        val recovered = ByteArrayOutputStream()
        var authenticationFailed = false
        keyset.newDecryptingStream(ByteArrayInputStream(after), aad).use { reader ->
            var first = true
            while (!authenticationFailed) {
                val buffer = ByteArray(if (first) 4_056 else 4_080)
                first = false
                val count =
                    try {
                        reader.read(buffer)
                    } catch (failure: IOException) {
                        assertTrue(
                            generateSequence(failure as Throwable?) { it.cause }
                                .any {
                                    it is java.security.GeneralSecurityException ||
                                        it.javaClass.name.startsWith("com.google.crypto.tink.")
                                }
                        )
                        authenticationFailed = true
                        -1
                    }
                if (count > 0) recovered.write(buffer, 0, count)
                else
                    assertTrue(
                        "Truncated nonfinal source cannot authenticate EOF",
                        authenticationFailed,
                    )
            }
        }
        val bytes = recovered.toByteArray()
        assertArrayEquals(plaintext.copyOf(bytes.size), bytes)
        val oracleHash = Sha256Value.calculate(plaintext)
        val witness =
            RecoveryStreamingWitnessInput(
                    run,
                    117UL,
                    Sha256Value.calculate(byteArrayOf(117)),
                    475_136UL,
                    469_176UL,
                    RecoveryStreamingIdentity.oracle(480_000UL, oracleHash, run),
                    480_000UL,
                    oracleHash,
                    before.size.toULong(),
                    Sha256Value.calculate(before),
                    null,
                )
                .let {
                    it.copy(
                        controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it)
                    )
                }
        val digest = Sha256Value.calculate(bytes)
        return RecoveryStreamingValidatedIntentFacts(
            witness,
            after.size.toULong(),
            Sha256Value.calculate(after),
            true,
            false,
            RecoveryStreamingCompletedReadFacts(
                bytes.size.toULong(),
                digest,
                digest,
                true,
                StreamTerminal.AUTHENTICATION_FAILURE,
            ),
        )
    }
}
