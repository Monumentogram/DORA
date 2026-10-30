package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceCacheTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    @Test
    fun threeSeeksAuthenticateWholeSourceOnceButRecheckAndAuthenticateEveryRange() {
        val cache = PlaybackSourceCache()
        val source = Source(identity)
        var opens = 0
        for (first in listOf(0L, source.reference.frames / 2, source.reference.frames - 16_000)) {
            val result =
                cache.reuse(identity)
                    ?: cache.remember(identity, AudioResult.Value(source).also { opens++ })
            val borrowed = (result as AudioResult.Value).value
            assertTrue(borrowed.extractRange(first, 16_000) { _, _ -> } is AudioResult.Value)
            borrowed.close() // PcmWindows owns this view, not the cached authentication snapshot.
            assertEquals(0, source.closes)
        }
        assertEquals(1, opens)
        assertEquals(3, source.checks)
        assertEquals(listOf(0L, 691_200_000L, 1_382_384_000L), source.ranges)
        cache.close()
        assertEquals(1, source.closes)
    }

    @Test
    fun revocationDeletionStaleSourceAndReplacedVaultDenyReuseAndRetireSnapshot() {
        for (status in
            listOf(
                AudioResult.Failed(AudioFailure.LOCKED),
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                AudioResult.Value(OriginalAudioStatus.StaleReference),
                AudioResult.Value(
                    OriginalAudioStatus.SourceUnavailable(AudioFailure.KEY_UNAVAILABLE)
                ),
            )) {
            val cache = PlaybackSourceCache()
            val source = Source(identity)
            cache.remember(identity, AudioResult.Value(source))
            source.status = status
            assertTrue(cache.reuse(identity) is AudioResult.Failed)
            assertEquals(1, source.closes)
            assertEquals(emptyList<Long>(), source.ranges)
            assertEquals(null, cache.reuse(identity))
        }
    }

    @Test
    fun identityChangeAndExplicitClearRetireTheOnlyOwnedHandle() {
        val cache = PlaybackSourceCache()
        val first = Source(identity)
        cache.remember(identity, AudioResult.Value(first))
        val other = identity.copy(sessionId = "different-session")
        assertEquals(null, cache.reuse(other))
        assertEquals(1, first.closes)
        val second = Source(other)
        cache.remember(other, AudioResult.Value(second))
        cache.close()
        cache.close()
        assertEquals(1, second.closes)
    }

    @Test
    fun wrongIdentityOrChangedReferenceCannotBeRememberedOrReused() {
        val cache = PlaybackSourceCache()
        val wrong = Source(identity.copy(sessionId = "wrong"))
        assertTrue(cache.remember(identity, AudioResult.Value(wrong)) is AudioResult.Failed)
        assertEquals(1, wrong.closes)
        val source = Source(identity)
        cache.remember(identity, AudioResult.Value(source))
        source.status =
            AudioResult.Value(
                OriginalAudioStatus.Available(source.reference.copy(digest = "b".repeat(64)))
            )
        assertTrue(cache.reuse(identity) is AudioResult.Failed)
        assertEquals(1, source.closes)
    }

    private class Source(identity: AudioIdentity) : AuthenticatedAudioSource {
        override val reference = OriginalAudioReference(1, identity, "a".repeat(64), 1_382_400_000L)
        var status: AudioResult<OriginalAudioStatus> =
            AudioResult.Value(OriginalAudioStatus.Available(reference))
        var checks = 0
        var closes = 0
        val ranges = mutableListOf<Long>()

        override fun checkAvailable(): AudioResult<OriginalAudioStatus> {
            checks++
            return status
        }

        override fun extractRange(
            firstFrame: Long,
            frameCount: Long,
            consume: (Long, ByteArray) -> Unit,
        ): AudioResult<OriginalAudioStatus> {
            check(closes == 0)
            ranges += firstFrame
            val bytes = ByteArray(2)
            try {
                consume(firstFrame, bytes)
            } finally {
                bytes.fill(0)
            }
            return status
        }

        override fun close() {
            closes++
        }
    }
}
