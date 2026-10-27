package com.monumentogram.dora.transcript

import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real Android HTTPS/proof/vault against the local synthetic backend. Never contacts AWS. */
class CloudHttpIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> value(result: AudioResult<T>): T = (result as AudioResult.Value).value

    @Test
    fun prepareInvitation() {
        requireFixture()
        EncryptedCloudCancellations(context).record(CANCELLED_ACTION, CANCELLED_DESTINATION)
        context.filesDir
            .resolve("cloud-test-invitation.json")
            .writeText(CloudIdentity(context).publicInvitation())
    }

    @Test
    @Suppress("LongMethod") // Real signed transport plus persisted local and cloud versions.
    fun signedCloudOnlyAndEditedRecordingRoundTrip() {
        requireFixture()
        assertNull(
            "Cloud-only fixture must have no installed local model",
            LocalAsrModel(context).installed(),
        )
        val certificate =
            InstrumentationRegistry.getArguments().getString("cloudCertificate")
                ?: error("Test certificate required")
        val trust =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry(
                    "fixture",
                    CertificateFactory.getInstance("X.509")
                        .generateCertificate(
                            Base64.decode(certificate, Base64.DEFAULT).inputStream()
                        ),
                )
            }
        val managers =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trust)
            }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
        val endpoint = "https://10.0.2.2:8443"
        val installation = CloudIdentity(context)
        val api =
            CloudHttpClient(endpoint, installation.installationId, installation::sign) { url ->
                (url.openConnection() as HttpsURLConnection).apply {
                    sslSocketFactory = ssl.socketFactory
                }
            }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = open(scenario)
            repeat(2) { index ->
                val identity =
                    AudioIdentity(
                        RecordingId(UUID.randomUUID().toString()),
                        AudioAssetId(UUID.randomUUID().toString()),
                        UUID.randomUUID().toString(),
                    )
                value(session.writer.createLogicalRecording(identity))
                value(
                    session.writer.append(
                        AudioStorageUnitIdentity(identity, UUID.randomUUID().toString(), 0, 0),
                        AudioFormat.PCM,
                        ByteArray(32000),
                    )
                )
                value(session.writer.finalize(identity))
                val source =
                    (value(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                        .reference
                val local = UUID.randomUUID().toString()
                if (index == 1) {
                    value(session.transcripts.begin(source, local, "en", 1))
                    val first =
                        value(
                            session.transcripts.finish(
                                source,
                                local,
                                "synthetic local",
                                "test-fixture",
                                2,
                            )
                        )
                    value(
                        session.transcripts.edit(
                            identity,
                            local,
                            first.revision,
                            "my saved correction",
                            3,
                        )
                    )
                }
                val action = UUID.randomUUID().toString()
                val time = System.currentTimeMillis()
                value(
                    session.transcripts.beginCloud(
                        source,
                        action,
                        if (index == 0) "ru" else "en",
                        CloudConsent(grantedAt = time, destination = endpoint),
                        time,
                    )
                )
                val done =
                    value(
                        CloudRecognitionRun(api, EncryptedCloudCancellations(context), pause = {})
                            .run(session, identity, action, RecognitionAttempt()) {}
                    )
                assertEquals("CLOUD", done.versions.last().engine)
                assertEquals("amazon-transcribe", done.versions.last().cloud!!.providerId)
                assertEquals(if (index == 0) action else local, done.activeId)
                if (index == 1) {
                    assertEquals("my saved correction", done.rendered(local))
                    context.filesDir
                        .resolve("cloud-test-recording.txt")
                        .writeText(
                            listOf(
                                    identity.recordingId.value,
                                    identity.assetId.value,
                                    identity.sessionId,
                                    local,
                                )
                                .joinToString("\n")
                        )
                }
                assertEquals("COMPLETED", api.status(action).status)
            }
        }
    }

    @Test
    fun cloudAndEditsRemainAfterProcessRestart() {
        requireFixture()
        assertTrue(
            EncryptedCloudCancellations(context).blocked(CANCELLED_ACTION, CANCELLED_DESTINATION)
        )
        val parts = context.filesDir.resolve("cloud-test-recording.txt").readLines()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val identity = AudioIdentity(RecordingId(parts[0]), AudioAssetId(parts[1]), parts[2])
            val doc = value(open(scenario).transcripts.load(identity))!!
            assertEquals(2, doc.versions.size)
            assertEquals("CLOUD", doc.versions.last().engine)
            assertEquals("my saved correction", doc.rendered(parts[3]))
            assertEquals(parts[3], doc.activeId)
        }
    }

    private fun requireFixture() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("cloudFixtureEnabled") == "true"
        )
    }

    private fun open(scenario: ActivityScenario<MainActivity>): ProductAudioSession {
        var session: ProductAudioSession? = null
        val ready = CountDownLatch(1)
        scenario.onActivity { activity ->
            val runtime = (activity.application as DoraApplication).audioRuntime
            runtime.requestOpen(activity, runtime.recordingOpenMode()) { outcome ->
                session = (outcome as? AudioAvailability.Available)?.session
                ready.countDown()
            }
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS))
        return checkNotNull(session)
    }

    companion object {
        private const val CANCELLED_ACTION = "00000000-0000-0000-0000-00000001810c"
        private const val CANCELLED_DESTINATION = "https://cancelled-fixture.invalid"
    }
}
