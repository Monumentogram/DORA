package com.monumentogram.dora.operator

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber") // Independent literal synthetic canonical ranges.
class IsolatedAlphaOperatorTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val otherId = "00000000-0000-0000-0000-000000000002"
    private val identity = AudioIdentity(RecordingId(id), AudioAssetId(otherId), id)

    @Test
    fun completeAuthenticatedReadbackProducesOneExactSessionBoundDeletion() {
        val session = SyntheticSession()
        val operator = admitted(session)
        assertEquals(AudioResult.Value(IsolatedAlphaOperator.Readback(4, 2)), operator.readback(id))
        assertTrue(session.borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
        assertEquals(AudioResult.Value(identity), operator.consumeDeletion(id))
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
    }

    @Test
    fun badCanonicalRangesCountsCorruptionAndIncompleteNeverAuthorizeDeletion() {
        for (session in
            listOf(
                SyntheticSession(starts = listOf(0, 3)),
                SyntheticSession(starts = listOf(0, 0)),
                SyntheticSession(frames = 5),
                SyntheticSession(failure = AudioFailure.CORRUPT),
                SyntheticSession(finalized = false),
            )) {
            val operator = admitted(session)
            assertTrue(operator.readback(id) is AudioResult.Failed)
            assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
        }
    }

    @Test
    fun wrongRecordingCancellationStaleSessionAndRevokedSourceConsumeTheProof() {
        val session = SyntheticSession()
        var current: ProductAudioSession? = session
        val operator = IsolatedAlphaOperator(::environment) { current }
        assertTrue(operator.admit("10", "FULL_SECONDARY_USER:10:42"))
        assertTrue(operator.readback(otherId) is AudioResult.Failed)
        assertTrue(operator.readback("../audio") is AudioResult.Failed)
        assertTrue(operator.readback(id) is AudioResult.Value)
        assertTrue(operator.consumeDeletion(otherId) is AudioResult.Failed)
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
        assertTrue(operator.readback(id) is AudioResult.Value)
        current = SyntheticSession()
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
        current = session
        assertTrue(operator.readback(id) is AudioResult.Value)
        operator.cancel()
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
        session.duringRead = { operator.cancel() }
        assertTrue(operator.readback(id) is AudioResult.Failed)
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
        session.duringRead = {}
        assertTrue(operator.readback(id) is AudioResult.Value)
        session.active = false
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
    }

    @Test
    fun cancellationDuringDeletionRevalidationCannotPublishAnIdentity() {
        val session = SyntheticSession()
        val operator = admitted(session)
        assertTrue(operator.readback(id) is AudioResult.Value)
        session.duringInspect = { operator.cancel() }
        assertTrue(operator.consumeDeletion(id) is AudioResult.Failed)
    }

    private fun admitted(session: ProductAudioSession) =
        IsolatedAlphaOperator(::environment) { session }
            .also {
                assertTrue(it.admit("10", "FULL_SECONDARY_USER:10:42"))
            }

    private inner class SyntheticSession(
        val starts: List<Long> = listOf(0, 2),
        val frames: Long = 4,
        val failure: AudioFailure? = null,
        val finalized: Boolean = true,
    ) : ProductAudioSession {
        var active = true
        var duringRead: () -> Unit = {}
        var duringInspect: () -> Unit = {}
        val borrowed = mutableListOf<ByteArray>()
        private val reference = OriginalAudioReference(1, identity, "synthetic", frames)
        override val protection = VaultKeyProtection.SOFTWARE

        override fun findRecording(recordingId: RecordingId): AudioResult<AudioIdentity> =
            if (recordingId == identity.recordingId && active) AudioResult.Value(identity)
            else AudioResult.Failed(AudioFailure.INVALID_INPUT)

        override val originals =
            object : OriginalAudioPort {
                override fun acquire(identity: AudioIdentity) = status()

                override fun inspect(
                    reference: OriginalAudioReference
                ): AudioResult<OriginalAudioStatus> {
                    duringInspect()
                    return status()
                }

                override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                    status()

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    for (first in starts) {
                        val bytes = byteArrayOf(1, 2, 3, 4)
                        borrowed += bytes
                        consume(first, bytes)
                        duringRead()
                    }
                    return failure?.let { AudioResult.Failed(it) } ?: status()
                }

                private fun status(): AudioResult<OriginalAudioStatus> =
                    if (!active) AudioResult.Failed(AudioFailure.LOCKED)
                    else
                        AudioResult.Value(
                            if (finalized) OriginalAudioStatus.Available(reference)
                            else OriginalAudioStatus.NotFinalized
                        )
            }
        override val reader: ProductAudioReaderPort
            get() = error("Use authoritative originals")

        override val writer: ProductAudioWriterPort
            get() = error("Operator cannot write PCM")

        override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> =
            error("Not used")

        override fun retryRemainingDeletion(identity: AudioIdentity): AudioResult<Unit> =
            error("No unconfirmed deletion")
    }

    private fun environment() = OperatorEnvironment("com.monumentogram.dora.alpha.test", 10, 42)

    @Test
    fun explicitFullSecondaryProcessIsRequired() {
        val operator = IsolatedAlphaOperator(::environment) { null }
        assertTrue(operator.admit("10", "FULL_SECONDARY_USER:10:42"))
        assertFalse(operator.admit("0", "FULL_SECONDARY_USER:0:0"))
        assertFalse(operator.admit("11", "FULL_SECONDARY_USER:11:42"))
        assertFalse(operator.admit("10", ""))
    }

    @Test
    fun packageOwnerManagedRestrictedAndUnknownTopologyFailClosed() {
        for (state in
            listOf(
                environment().copy(packageName = "com.monumentogram.dora"),
                environment().copy(processUserId = 0),
                environment().copy(system = true),
                environment().copy(managed = true),
                environment().copy(profile = true),
                environment().copy(demo = true),
                environment().copy(unlocked = false),
                environment().copy(serial = -1),
            )) {
            val operator = IsolatedAlphaOperator({ state }) { null }
            assertFalse(operator.admit("10", "FULL_SECONDARY_USER:10:42"))
        }
    }
}
