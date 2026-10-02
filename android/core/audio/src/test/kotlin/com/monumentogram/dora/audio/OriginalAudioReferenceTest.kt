package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OriginalAudioReferenceTest {
    private val owner = "00000000-0000-0000-0000-000000000001"
    private val vault = "00000000-0000-0000-0000-000000000002"
    private val identity = AudioIdentity(RecordingId(owner), AudioAssetId(vault), owner)
    private val unit =
        StoredAudioSegment(
            AudioStorageUnitIdentity(identity, "00000000-0000-0000-0000-000000000003", 0, 0),
            16_000,
            Sha256Value.calculate(byteArrayOf(1, 2, 3)),
        )
    private val asset = StoredAudioAsset(identity, listOf(unit), finalization = listOf(unit))

    @Test
    fun `reference survives reconstructed catalog and contains no path or engine`() {
        val original = OriginalAudioReferenceCodec.derive(owner, vault, asset)
        val reopened =
            OriginalAudioReferenceCodec.derive(
                owner,
                vault,
                asset.copy(segments = asset.segments.toList()),
            )
        assertEquals(original, reopened)
        assertEquals(1, original.version)
        assertEquals(16_000L, original.frames)
        assertFalse(original.toString().contains(identity.recordingId.value))
        assertFalse(original.toString().contains(original.digest))
    }

    @Test
    fun `every authority and finalized source change changes identity`() {
        val reference = OriginalAudioReferenceCodec.derive(owner, vault, asset)
        assertNotEquals(reference, OriginalAudioReferenceCodec.derive(vault, vault, asset))
        assertNotEquals(reference, OriginalAudioReferenceCodec.derive(owner, owner, asset))
        val mutations =
            listOf(
                unit.copy(frames = 15_999),
                unit.copy(manifestDigest = Sha256Value.calculate(byteArrayOf(4))),
                unit.copy(identity = unit.identity.copy(unitId = owner)),
                unit.copy(identity = unit.identity.copy(physicalSegmentId = owner)),
            )
        mutations.forEach {
            assertNotEquals(
                reference,
                OriginalAudioReferenceCodec.derive(
                    owner,
                    vault,
                    asset.copy(segments = listOf(it), finalization = listOf(it)),
                ),
            )
        }
    }

    @Test
    fun `partial uncertain and mismatched finalization cannot create authority`() {
        listOf(
                asset.copy(finalization = null),
                asset.copy(finalization = emptyList()),
                asset.copy(pending = AudioIntent.Finalize(listOf(unit))),
                asset.copy(finalization = listOf(unit.copy(frames = 1))),
                asset.copy(segments = emptyList(), finalization = emptyList()),
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) {
                    OriginalAudioReferenceCodec.derive(owner, vault, it)
                }
            }
    }

    @Test
    fun `holes duplicate namespaces and inconsistent physical mappings reject`() {
        val invalid =
            listOf(
                unit.copy(identity = unit.identity.copy(ordinal = 1)),
                unit.copy(identity = unit.identity.copy(firstFrame = 1)),
                unit.copy(identity = unit.identity.copy(sourceFrameOffset = 1)),
                unit.copy(frames = 0),
                unit.copy(identity = unit.identity.copy(unitId = "not-a-source")),
            )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                OriginalAudioReferenceCodec.derive(
                    owner,
                    vault,
                    asset.copy(segments = listOf(it), finalization = listOf(it)),
                )
            }
        }
        val duplicate =
            listOf(
                unit,
                unit.copy(
                    identity =
                        unit.identity.copy(
                            ordinal = 1,
                            firstFrame = 16_000,
                            sourceFrameOffset = 16_000,
                        )
                ),
            )
        assertThrows(IllegalArgumentException::class.java) {
            OriginalAudioReferenceCodec.derive(
                owner,
                vault,
                asset.copy(segments = duplicate, finalization = duplicate),
            )
        }
    }
}
