package com.monumentogram.dora.audio

import com.google.crypto.tink.subtle.AesGcmJce
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.RecoveryInventoryEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryAudioBridgeTest {
    private val identity =
        AudioIdentity(
            RecordingId("20112233-4455-4677-8899-aabbccddeeff"),
            AudioAssetId("30112233-4455-4677-8899-aabbccddeeff"),
            "40112233-4455-4677-8899-aabbccddeeff",
        )
    private val first = "00112233-4455-6677-8899-aabbccddeeff"
    private val second = "10112233-4455-6677-8899-aabbccddeeff"

    @Test
    fun `quarantine pending outcome cannot authorize finalization`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val orphan = byteArrayOf(42)
        f.files["$first/unknown.bin"] = orphan
        f.inventory =
            listOf(
                RecoveryInventoryEntry(
                    RecoveryQuarantineIntentInput(
                        RecoveryCandidate.MICROFILE,
                        RunId.fromCanonicalString(first),
                        "unknown.bin",
                        RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
                        1UL,
                        Sha256Value.calculate(orphan),
                    ),
                    RecoveryQuarantineObservedState.UNKNOWN_OR_NON_ALLOWLISTED_NAME,
                    QuarantineBootstrapBinding.PRESENT,
                )
            )
        f.quarantine.failRename = true
        assertEquals(AudioCompletion.PARTIAL_RECOVERED, read(f).second.completion)
        assertEquals(AudioResult.Failed(AudioFailure.INCOMPLETE), f.bridge.finalize(identity))
        assertTrue(f.quarantine.rows.isNotEmpty())
        f.quarantine.failRename = false
        assertEquals(AudioResult.Failed(AudioFailure.INCOMPLETE), f.bridge.reconcile(identity))
        assertEquals(AudioResult.Value(Unit), f.bridge.reconcile(identity))
        assertEquals(AudioCompletion.FINALIZED, read(f).second.completion)
    }

    @Test
    fun `cross asset namespace reuse and concurrent owner are rejected`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val other = identity.copy(assetId = AudioAssetId("60112233-4455-4677-8899-aabbccddeeff"))
        f.bridge.create(other)
        assertEquals(
            AudioResult.Failed(AudioFailure.COLLISION),
            f.bridge.append(
                AudioStorageUnitIdentity(other, first, 0, 0),
                AudioFormat.PCM,
                byteArrayOf(3, 4),
            ),
        )
        requireNotNull(f.catalog.tryAcquire(identity)).use {
            assertEquals(
                AudioResult.Failed(AudioFailure.BUSY),
                f.bridge.extract(identity) { _, _ -> error("Lease bypass") },
            )
        }
        assertEquals(1, f.units.size)
    }

    @Test
    fun `borrowed callback buffer is zeroed even if consumer fails`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        var borrowed = byteArrayOf()
        assertEquals(
            AudioResult.Failed(AudioFailure.UNCERTAIN),
            f.bridge.extract(identity) { _, bytes ->
                borrowed = bytes
                error("Consumer failure")
            },
        )
        assertArrayEquals(byteArrayOf(0, 0), borrowed)
        assertArrayEquals(byteArrayOf(1, 2), read(f).first)
    }

    @Test
    fun `stale finalization and reordered metadata never produce final audio`() {
        for (mutation in 0..3) {
            val f = AudioMemoryFixture()
            f.bridge.create(identity)
            append(f, first, 0, 0, byteArrayOf(1, 2))
            append(f, second, 1, 1, byteArrayOf(3, 4))
            f.bridge.finalize(identity)
            val asset = f.catalog.assets.getValue(identity.assetId.value)
            f.catalog.assets[identity.assetId.value] =
                when (mutation) {
                    0 -> asset.copy(finalization = asset.segments.take(1))
                    1 -> asset.copy(segments = asset.segments.reversed())
                    2 -> asset.copy(segments = asset.segments.map { it.copy(frames = 2) })
                    else ->
                        asset.copy(
                            segments =
                                asset.segments.map {
                                    it.copy(manifestDigest = Sha256Value.calculate(byteArrayOf(9)))
                                }
                        )
                }
            assertTrue(f.bridge.extract(identity) { _, _ -> } is AudioResult.Failed)
        }
    }

    @Test
    fun `physical overlap mapping does not duplicate logical frames`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2, 3, 4))
        val next = AudioStorageUnitIdentity(identity, second, 1, 2, second, 1, 1)
        assertEquals(
            AudioResult.Value(Unit),
            f.bridge.append(next, AudioFormat.PCM, byteArrayOf(5, 6)),
        )
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), read(f).first)
        assertEquals(3L, read(f).second.frames)
        assertEquals(
            AudioResult.Failed(AudioFailure.INVALID_INPUT),
            f.bridge.append(
                next.copy(ordinal = 2, firstFrame = 3, sourceFrameOffset = 0),
                AudioFormat.PCM,
                byteArrayOf(7, 8),
            ),
        )
    }

    @Test
    fun `unknown replacement key and missing confirmation do not recreate keys`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val alias = f.keys.keys.single()
        f.keys[alias] = AesGcmJce(ByteArray(32).also { SecureRandom().nextBytes(it) })
        assertEquals(
            AudioResult.Failed(AudioFailure.UNCERTAIN),
            f.bridge.extract(identity) { _, _ -> error("Replaced key") },
        )
        f.files.remove("$first/key-confirmation/run.kc")
        assertEquals(
            AudioResult.Failed(AudioFailure.INCOMPLETE),
            f.bridge.extract(identity) { _, _ -> error("Missing confirmation") },
        )
        assertEquals(1, f.keys.size)
    }

    @Test
    fun `publication interrupted before catalog commit is recovered after restart`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        f.catalog.rejectCommit = true
        assertEquals(
            AudioResult.Failed(AudioFailure.UNCERTAIN),
            f.bridge.append(
                AudioStorageUnitIdentity(identity, first, 0, 0),
                AudioFormat.PCM,
                byteArrayOf(1, 2),
            ),
        )
        assertEquals(1, f.units.size)
        f.catalog.rejectCommit = false
        assertEquals(AudioResult.Value(Unit), f.newBridge().reconcile(identity))
        assertEquals(AudioResult.Value(Unit), f.newBridge().reconcile(identity))
        assertArrayEquals(byteArrayOf(1, 2), read(f).first)
        assertEquals(1, f.units.size)
    }

    @Test
    fun `uncertain finalization stays fenced across restart`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        f.catalog.rejectCommit = true
        assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), f.bridge.finalize(identity))
        assertEquals(
            AudioResult.Failed(AudioFailure.COLLISION),
            f.newBridge()
                .append(
                    AudioStorageUnitIdentity(identity, second, 1, 1),
                    AudioFormat.PCM,
                    byteArrayOf(3, 4),
                ),
        )
        f.catalog.rejectCommit = false
        assertEquals(AudioResult.Value(Unit), f.newBridge().reconcile(identity))
        assertEquals(AudioCompletion.FINALIZED, read(f).second.completion)
    }

    @Test
    fun `corrupted confirmation is not missing key`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val confirmation = f.files.getValue("$first/key-confirmation/run.kc")
        confirmation[0] = (confirmation[0].toInt() xor 1).toByte()
        assertEquals(
            AudioResult.Failed(AudioFailure.CORRUPT),
            f.bridge.extract(identity) { _, _ -> error("Untrusted bytes") },
        )
    }

    @Test
    fun `actual Recovery write read preserves identity bytes order and finalization`() {
        val f = AudioMemoryFixture()
        assertEquals(AudioResult.Value(Unit), f.bridge.create(identity))
        append(f, first, 0, 0, byteArrayOf(1, 2, 3, 4))
        append(f, second, 1, 2, byteArrayOf(5, 6))
        val before = read(f)
        assertEquals(AudioCompletion.PARTIAL_RECOVERED, before.second.completion)
        assertEquals(AudioResult.Value(Unit), f.bridge.finalize(identity))
        val after = read(f)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), after.first)
        assertEquals(identity, after.second.identity)
        assertEquals(3L, after.second.frames)
        assertEquals(187L, after.second.durationUs)
        assertEquals(AudioCompletion.FINALIZED, after.second.completion)
    }

    @Test
    fun `identity ordering and duplicate mutations cannot publish`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        assertEquals(AudioResult.Failed(AudioFailure.COLLISION), f.bridge.create(identity))
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val invalid =
            listOf(
                AudioStorageUnitIdentity(identity, first, 1, 1),
                AudioStorageUnitIdentity(identity, second, 0, 1),
                AudioStorageUnitIdentity(identity, second, 1, 2),
                AudioStorageUnitIdentity(identity.copy(sessionId = "other"), second, 1, 1),
                AudioStorageUnitIdentity(
                    identity.copy(
                        recordingId = RecordingId("50112233-4455-4677-8899-aabbccddeeff")
                    ),
                    second,
                    1,
                    1,
                ),
            )
        invalid.forEach { segment ->
            assertTrue(
                f.bridge.append(segment, AudioFormat.PCM, byteArrayOf(3, 4)) is AudioResult.Failed
            )
        }
        assertEquals(1, f.units.size)
    }

    @Test
    fun `finalized and uncertain state reject append mutations`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        f.bridge.finalize(identity)
        assertEquals(
            AudioResult.Failed(AudioFailure.COLLISION),
            f.bridge.append(
                AudioStorageUnitIdentity(identity, second, 1, 1),
                AudioFormat.PCM,
                byteArrayOf(3, 4),
            ),
        )
        assertEquals(AudioResult.Failed(AudioFailure.COLLISION), f.bridge.finalize(identity))
    }

    @Test
    fun `format and alignment mutations have zero publication effects`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        val segment = AudioStorageUnitIdentity(identity, first, 0, 0)
        for (pcm in listOf(byteArrayOf(), byteArrayOf(1), ByteArray(160_002))) {
            assertEquals(
                AudioResult.Failed(AudioFailure.INVALID_INPUT),
                f.bridge.append(segment, AudioFormat.PCM, pcm),
            )
        }
        assertEquals(
            AudioResult.Failed(AudioFailure.INVALID_INPUT),
            f.bridge.append(segment, AudioFormat("PCM_S16LE", 48_000, 1), byteArrayOf(1, 2)),
        )
        assertTrue(f.files.isEmpty())
    }

    @Test
    fun `ciphertext mutation rejects authenticated extraction`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        val key = f.files.keys.single { it.endsWith("units/u-0000000000.ct") }
        f.files.getValue(key)[0] = (f.files.getValue(key)[0].toInt() xor 1).toByte()
        repeat(2) {
            assertEquals(
                AudioResult.Failed(AudioFailure.CORRUPT),
                f.bridge.extract(identity) { _, _ -> error("Untrusted bytes escaped") },
            )
        }
    }

    @Test
    fun `missing key is distinguished from corrupted audio`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        f.keys.clear()
        assertEquals(
            AudioResult.Failed(AudioFailure.KEY_UNAVAILABLE),
            f.bridge.extract(identity) { _, _ -> error("Unavailable key exposed bytes") },
        )
    }

    @Test
    fun `missing final segment never promotes surviving prefix to complete`() {
        val f = AudioMemoryFixture()
        f.bridge.create(identity)
        append(f, first, 0, 0, byteArrayOf(1, 2))
        append(f, second, 1, 1, byteArrayOf(3, 4))
        f.bridge.finalize(identity)
        f.files.remove("$second/units/u-0000000000.ct")
        val recovered = read(f)
        assertArrayEquals(byteArrayOf(1, 2), recovered.first)
        assertEquals(1L, recovered.second.frames)
        assertEquals(62L, recovered.second.durationUs)
        assertEquals(AudioCompletion.PARTIAL_RECOVERED, recovered.second.completion)
        assertEquals(AudioFailure.INCOMPLETE, recovered.second.tailFailure)
    }

    @Test
    fun `unobserved provider or unknown envelope decrypt failure stays operational`() {
        for (failure in
            listOf(
                java.security.ProviderException("provider-canary"),
                java.security.GeneralSecurityException("unknown-canary"),
            )) {
            val f = AudioMemoryFixture()
            f.bridge.create(identity)
            append(f, first, 0, 0, byteArrayOf(1, 2))
            val alias = f.keys.keys.single()
            val original = f.keys.getValue(alias)
            var decrypts = 0
            f.keys[alias] =
                object : com.google.crypto.tink.Aead by original {
                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray {
                        decrypts++
                        if (decrypts == 2) throw failure
                        return original.decrypt(ciphertext, associatedData)
                    }
                }
            assertEquals(
                AudioResult.Failed(AudioFailure.UNCERTAIN),
                f.bridge.extract(identity) { _, _ -> error("Unauthenticated bytes") },
            )
        }
    }

    private fun append(
        f: AudioMemoryFixture,
        id: String,
        ordinal: Int,
        start: Long,
        bytes: ByteArray,
    ) {
        assertEquals(
            AudioResult.Value(Unit),
            f.bridge.append(
                AudioStorageUnitIdentity(identity, id, ordinal, start),
                AudioFormat.PCM,
                bytes,
            ),
        )
    }

    private fun read(f: AudioMemoryFixture): Pair<ByteArray, AudioReadSummary> {
        val chunks = mutableListOf<Byte>()
        var next = 0L
        val result =
            f.bridge.extract(identity) { start, bytes ->
                assertEquals(next, start)
                next += bytes.size / 2
                chunks.addAll(bytes.toList())
            } as AudioResult.Value
        return chunks.toByteArray() to result.value
    }
}
