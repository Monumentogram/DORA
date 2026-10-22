package com.monumentogram.dora.recording

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
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber") // Literal independent PCM range oracle.
class SavedRecordingVerificationTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val identity = AudioIdentity(RecordingId(id), AudioAssetId(id), id)

    @Test
    fun validatesFullFinalizedRangeAndClearsEveryBorrowedBuffer() {
        val session = Fixture()
        assertEquals(
            AudioResult.Value(VerifiedRecording(identity, 4, 2)),
            SavedRecordingVerification.verify(session, identity),
        )
        assertTrue(session.borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    fun missingFramesOverlapsEmptyOddAndIncompleteNeverReportVerified() {
        for (session in
            listOf(
                Fixture(starts = listOf(0, 3)),
                Fixture(starts = listOf(0, 0)),
                Fixture(frames = 5),
                Fixture(bytes = byteArrayOf()),
                Fixture(bytes = byteArrayOf(1)),
                Fixture(finalized = false),
                Fixture(frames = 4_800_001),
                Fixture(starts = emptyList()),
            )) {
            assertTrue(SavedRecordingVerification.verify(session, identity) is AudioResult.Failed)
            assertTrue(session.borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
        }
    }

    @Test
    fun exactSourceAndTerminalStatusAreRequired() {
        val wrong = identity.copy(sessionId = "different")
        assertTrue(SavedRecordingVerification.verify(Fixture(), wrong) is AudioResult.Failed)
        for (failure in listOf(AudioFailure.CORRUPT, AudioFailure.LOCKED, AudioFailure.UNCERTAIN)) {
            val session = Fixture(failure = failure)
            assertEquals(
                AudioResult.Failed(failure),
                SavedRecordingVerification.verify(session, identity),
            )
            assertTrue(session.borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
        }
    }

    private inner class Fixture(
        val starts: List<Long> = listOf(0, 2),
        val frames: Long = 4,
        val bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        val finalized: Boolean = true,
        val failure: AudioFailure? = null,
    ) : ProductAudioSession {
        val borrowed = mutableListOf<ByteArray>()
        private val reference = OriginalAudioReference(1, identity, "synthetic", frames)
        override val protection = VaultKeyProtection.SOFTWARE
        override val originals =
            object : OriginalAudioPort {
                override fun acquire(identity: AudioIdentity): AudioResult<OriginalAudioStatus> =
                    status()

                override fun inspect(
                    reference: OriginalAudioReference
                ): AudioResult<OriginalAudioStatus> = status()

                override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                    status()

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    starts.forEach { first ->
                        bytes.copyOf().also {
                            borrowed += it
                            consume(first, it)
                        }
                    }
                    return failure?.let { AudioResult.Failed(it) } ?: status()
                }

                private fun status(): AudioResult<OriginalAudioStatus> =
                    AudioResult.Value(
                        if (finalized) OriginalAudioStatus.Available(reference)
                        else OriginalAudioStatus.NotFinalized
                    )
            }
        override val reader: ProductAudioReaderPort
            get() = error("No alternate reader")

        override val writer: ProductAudioWriterPort
            get() = error("Verification never writes")

        override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> =
            error("No catalog shortcut")

        override fun retryRemainingDeletion(identity: AudioIdentity): AudioResult<Unit> =
            error("No deletion")
    }
}
