package com.monumentogram.dora.audio

import com.google.crypto.tink.Aead
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.candidate.MicrofileReconciliationResult
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedRecoveryKeyReuseTest {
    @Test
    fun rejectedLaterManifestKeepsEarlierAuthenticatedPrefixAndQuarantineOutcome() {
        for (scoped in listOf(false, true)) {
            val f = AudioMemoryFixture(scoped)
            val run = RunId.fromCanonicalString(id())
            f.publishCandidateRun(run, listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))
            val alias = f.keys.keys.single()
            val actual = f.keys.getValue(alias)
            var decrypts = 0
            f.keys[alias] =
                object : Aead by actual {
                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray {
                        decrypts++
                        return actual.decrypt(
                            ciphertext,
                            if (decrypts == 2) associatedData + byteArrayOf(0) else associatedData,
                        )
                    }
                }
            f.keyOpens = 0
            val result = f.reconcileCandidate(run)
            assertTrue("scoped=$scoped", result is MicrofileReconciliationResult.PartialPrefix)
            result as MicrofileReconciliationResult.PartialPrefix
            assertArrayEquals(byteArrayOf(1, 2), result.prefix.plaintextSnapshot())
            assertEquals(1UL, result.prefix.manifestGenerationUsed)
            assertEquals(
                RecoveryFailureCategory.AUTHENTICATION_REJECTED,
                result.manifestRejections.single().diagnostic.category,
            )
            assertTrue(result.quarantineOutcomes.isEmpty())
            assertTrue(f.quarantine.rows.isEmpty())
            assertEquals(if (scoped) 3 else 4, f.keyOpens)
            assertEquals(4, decrypts)
        }
    }

    @Test
    fun manifestAndUnitEnvelopeStillAuthenticateExactAadAfterKeyReuse() {
        for (rejectedDecrypt in listOf(2, 3)) {
            val f = AudioMemoryFixture()
            val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
            assertEquals(AudioResult.Value(Unit), f.bridge.create(identity))
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(identity, id(), 0, 0),
                    AudioFormat.PCM,
                    byteArrayOf(1, 2),
                ),
            )
            val alias = f.keys.keys.single()
            val actual = f.keys.getValue(alias)
            var decrypts = 0
            f.keys[alias] =
                object : Aead by actual {
                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray {
                        decrypts++
                        return actual.decrypt(
                            ciphertext,
                            if (decrypts == rejectedDecrypt) associatedData + byteArrayOf(0)
                            else associatedData,
                        )
                    }
                }
            f.keyOpens = 0
            assertEquals(
                AudioResult.Failed(AudioFailure.AUTHENTICATION_FAILED),
                f.bridge.extract(identity) { _, _ -> error("Unauthenticated PCM escaped") },
            )
            assertEquals(1, f.keyOpens)
            assertEquals(rejectedDecrypt, decrypts)
        }
    }

    @Test
    fun finalizeAndAuthenticatedOpenValidateEachRunOnceButAuthenticateAllThreeArtifacts() {
        val f = AudioMemoryFixture()
        val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
        assertEquals(AudioResult.Value(Unit), f.bridge.create(identity))
        repeat(4) { index ->
            assertEquals(
                AudioResult.Value(Unit),
                f.bridge.append(
                    AudioStorageUnitIdentity(identity, id(), index, index.toLong()),
                    AudioFormat.PCM,
                    byteArrayOf(index.toByte(), 0),
                ),
            )
        }
        f.keyOpens = 0
        f.keyDecrypts = 0
        assertEquals(AudioResult.Value(Unit), f.bridge.finalize(identity))
        assertEquals(12, f.keyDecrypts)
        assertEquals(4, f.keyOpens)

        val lifecycle =
            OriginalAudioLifecycle(
                id(),
                id(),
                f.catalog,
                { null },
                { null },
                { _, _ -> },
                f.bridge::extractFinalizedHeld,
                {},
                { it() },
                f.bridge::extractRangeHeld,
            )
        repeat(2) {
            f.keyOpens = 0
            f.keyDecrypts = 0
            val handle = (lifecycle.openAuthenticated(identity) as AudioResult.Value).value
            assertEquals(12, f.keyDecrypts)
            assertEquals(4, f.keyOpens)
            f.keyOpens = 0
            f.keyDecrypts = 0
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.Available(handle.reference)),
                handle.extractRange(2, 1) { first, bytes ->
                    assertEquals(2L, first)
                    assertArrayEquals(byteArrayOf(2, 0), bytes)
                },
            )
            assertEquals(3, f.keyDecrypts)
            assertEquals(1, f.keyOpens)
            handle.close()
        }
    }

    private fun id() = UUID.randomUUID().toString()
}
