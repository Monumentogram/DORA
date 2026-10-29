@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import androidx.test.core.app.ActivityScenario
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

internal fun <T> alphaValue(result: AudioResult<T>): T = (result as AudioResult.Value).value

internal fun alphaSession(scenario: ActivityScenario<MainActivity>): ProductAudioSession {
    var session: ProductAudioSession? = null
    val ready = CountDownLatch(1)
    scenario.onActivity { activity ->
        val runtime = (activity.application as DoraApplication).audioRuntime
        runtime.requestOpen(activity, runtime.recordingOpenMode()) {
            session = (it as? AudioAvailability.Available)?.session
            ready.countDown()
        }
    }
    assertTrue(ready.await(30, TimeUnit.SECONDS))
    return checkNotNull(session)
}

internal fun alphaId(): String = UUID.randomUUID().toString()

internal class AlphaFixture(val session: ProductAudioSession) {
    val identity = AudioIdentity(RecordingId(alphaId()), AudioAssetId(alphaId()), alphaId())
    val local = alphaId()
    val cloud = alphaId()
    val pcm = ByteArray(96000) { (it % 127).toByte() }
    val source: OriginalAudioReference

    init {
        alphaValue(session.writer.createLogicalRecording(identity))
        alphaValue(
            session.writer.append(
                AudioStorageUnitIdentity(identity, alphaId(), 0, 0),
                AudioFormat.PCM,
                pcm,
            )
        )
        alphaValue(session.writer.finalize(identity))
        source =
            (alphaValue(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                .reference
        alphaValue(session.transcripts.begin(source, local, "en", 1))
        val doc =
            alphaValue(session.transcripts.finish(source, local, "Original local", "fixture", 2))
        alphaValue(
            session.transcripts.edit(identity, local, doc.revision, "Правка владельца 😀", 3)
        )
        alphaValue(
            session.transcripts.beginCloud(source, cloud, "en", CloudConsent(grantedAt = 4), 4)
        )
    }

    fun result() =
        CloudTranscriptResult(
            "Cloud proposal",
            "amazon-transcribe",
            "standard-batch:eu-central-1:en-US",
            "stage12-synthetic",
            source.digest,
            source.version,
            5,
        )
}
