package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.persistence.runtime.RuntimeVault
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingAccessTest {
    @Test
    fun logicalCreateForwardsOriginAndKeepsAuthorityAndSingleCreateChecks() {
        val vault = WriterVault()
        val identity = identity()
        val access = access(identity, vault)
        assertEquals(
            AudioResult.Failed(AudioFailure.INVALID_INPUT),
            access.writer.createLogicalRecording(identity()),
        )
        assertEquals(AudioResult.Value(Unit), access.writer.createLogicalRecording(identity))
        assertEquals(1, vault.logicalCreates)
        assertEquals(
            AudioResult.Failed(AudioFailure.COLLISION),
            access.writer.createLogicalRecording(identity),
        )
        access.close()
        assertEquals(
            AudioResult.Failed(AudioFailure.LOCKED),
            access.writer.createLogicalRecording(identity),
        )
        assertEquals(1, vault.logicalCreates)
    }

    @Test
    fun exactIdentityAndSingleCreateAreEnforcedBeforeStorage() {
        val vault = WriterVault()
        val identity = identity()
        val access = access(identity, vault)
        assertEquals(
            AudioResult.Failed(AudioFailure.INVALID_INPUT),
            access.writer.create(identity()),
        )
        assertEquals(0, vault.writes)
        assertEquals(AudioResult.Value(Unit), access.writer.create(identity))
        assertEquals(AudioResult.Failed(AudioFailure.COLLISION), access.writer.create(identity))
        assertEquals(1, vault.writes)
    }

    @Test
    fun closeRevokesBorrowedWriterAndReleasesOnlyOnce() {
        val vault = WriterVault()
        val identity = identity()
        val access = access(identity, vault)
        val borrowed = access.writer
        access.close()
        access.close()
        assertEquals(AudioResult.Failed(AudioFailure.LOCKED), borrowed.create(identity))
        assertEquals(0, vault.writes)
        assertEquals(1, vault.closes)
    }

    @Test
    fun uncertainFinalizeCannotBeRetriedOrAppendedThroughCapability() {
        val vault = WriterVault()
        val identity = identity()
        val access = access(identity, vault)
        access.writer.create(identity)
        assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), access.writer.finalize(identity))
        assertEquals(AudioResult.Failed(AudioFailure.COLLISION), access.writer.finalize(identity))
        val unit = AudioStorageUnitIdentity(identity, id(), 0, 0, id(), 0, 0)
        assertEquals(
            AudioResult.Failed(AudioFailure.COLLISION),
            access.writer.append(unit, AudioFormat.PCM, ByteArray(2)),
        )
        assertEquals(2, vault.writes)
    }

    @Test
    fun mainThreadNeverInvokesStorage() {
        val vault = WriterVault()
        val identity = identity()
        val access = access(identity, vault, main = true)
        assertEquals(AudioResult.Failed(AudioFailure.BUSY), access.writer.create(identity))
        assertEquals(0, vault.writes)
    }

    private fun access(identity: AudioIdentity, vault: WriterVault, main: Boolean = false) =
        RecordingAccess(identity, RecordingAuthority({}, { true }) { it() }, vault, { main }) {
            it.close()
        }

    private class WriterVault : RuntimeVault {
        var logicalCreates = 0
        var writes = 0
        var closes = 0
        override val originals: OriginalAudioPort
            get() = error("Recording cannot obtain reader rights")

        override val reader: ProductAudioReaderPort
            get() = error("Recording cannot obtain reader rights")

        override val protection = VaultKeyProtection.SOFTWARE
        override val writer =
            object : ProductAudioWriterPort {
                override fun createLogicalRecording(identity: AudioIdentity): AudioResult<Unit> {
                    logicalCreates++
                    return AudioResult.Value(Unit)
                }

                override fun create(identity: AudioIdentity): AudioResult<Unit> {
                    writes++
                    return AudioResult.Value(Unit)
                }

                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ): AudioResult<Unit> {
                    writes++
                    return AudioResult.Value(Unit)
                }

                override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
                    writes++
                    return AudioResult.Failed(AudioFailure.UNCERTAIN)
                }

                override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
            }

        override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> =
            error("No catalog authority")

        override fun deleteConfirmed(identity: AudioIdentity): AudioResult<Unit> =
            error("No deletion authority")

        override fun retryDeletion(identity: AudioIdentity): AudioResult<Unit> =
            error("No deletion authority")

        override fun close() {
            closes++
        }
    }

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private fun id() = UUID.randomUUID().toString()
}
