package com.monumentogram.dora.audio

import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/** v1 canonicalization hashes admitted metadata, never filenames or mutable ASR configuration. */
internal object OriginalAudioReferenceCodec {
    private const val MAX_UNIT_FRAMES = 80_000L
    private val canonical = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    @Suppress("LongMethod") // Keep the complete versioned canonical byte order visible together.
    fun derive(owner: String, vault: String, asset: StoredAudioAsset): OriginalAudioReference {
        require(asset.pending == null && !asset.finalization.isNullOrEmpty())
        require(asset.finalization == asset.segments)
        val digest = MessageDigest.getInstance("SHA-256")
        val output =
            DataOutputStream(
                DigestOutputStream(
                    object : OutputStream() {
                        override fun write(value: Int) = Unit

                        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
                    },
                    digest,
                )
            )
        output.string("DORA_ORIGINAL_AUDIO_REFERENCE")
        output.writeInt(1)
        listOf(
                owner,
                vault,
                asset.identity.recordingId.value,
                asset.identity.assetId.value,
                asset.identity.sessionId,
            )
            .forEach { output.uuid(it) }
        output.string(AudioFormat.PCM.encoding)
        output.writeInt(AudioFormat.PCM.sampleRateHz)
        output.writeInt(AudioFormat.PCM.channels)
        output.writeInt(asset.segments.size)
        val runs = mutableSetOf<String>()
        val physical = mutableMapOf<String, Long>()
        var frames = 0L
        asset.segments.forEachIndexed { ordinal, segment ->
            val unit = segment.identity
            require(unit.audio == asset.identity && unit.ordinal == ordinal)
            require(unit.firstFrame == frames && runs.add(unit.unitId))
            require(segment.frames in 1..MAX_UNIT_FRAMES)
            require(unit.physicalFirstFrame >= 0 && unit.sourceFrameOffset >= 0)
            require(Math.addExact(unit.physicalFirstFrame, unit.sourceFrameOffset) == frames)
            require(
                physical.putIfAbsent(unit.physicalSegmentId, unit.physicalFirstFrame)?.let {
                    it == unit.physicalFirstFrame
                } != false
            )
            output.writeInt(ordinal)
            output.uuid(unit.unitId)
            output.writeLong(unit.firstFrame)
            output.writeLong(segment.frames)
            output.uuid(unit.physicalSegmentId)
            output.writeLong(unit.physicalFirstFrame)
            output.writeLong(unit.sourceFrameOffset)
            output.writeLong(1) // Accepted Recovery manifest generation for each storage unit.
            output.string(segment.manifestDigest.toLowercaseHex())
            frames = Math.addExact(frames, segment.frames)
        }
        output.writeLong(frames)
        output.flush()
        return OriginalAudioReference(
            1,
            asset.identity,
            Sha256Value.fromBytes(digest.digest()).toLowercaseHex(),
            frames,
        )
    }

    private fun DataOutputStream.uuid(value: String) {
        require(canonical.matches(value))
        string(value)
    }

    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
