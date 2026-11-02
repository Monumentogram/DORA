@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import android.util.Base64
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Local TLS fixture only: encrypted source, signed child uploads and SQLCipher text pages. */
class CloudCompositeHttpTest {
    @Test
    fun signedTwoChildCompositePreservesExistingEditAndRejectsUnaffordable24hQuote() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("cloudCompositeFixtureEnabled") == "true")
        val context = instrumentation.targetContext
        val certificate = requireNotNull(arguments.getString("cloudCertificate"))
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
        val installation = CloudIdentity(context)
        val endpoint = "https://10.0.2.2:8443"
        val api =
            CloudHttpClient(endpoint, installation.installationId, installation::sign) { url ->
                (url.openConnection() as HttpsURLConnection).apply {
                    sslSocketFactory = ssl.socketFactory
                }
            }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            val session = alphaSession(scenario)
            val identity = AudioIdentity(RecordingId(alphaId()), AudioAssetId(alphaId()), alphaId())
            alphaValue(session.writer.createLogicalRecording(identity))
            val seconds = arguments.getString("cloudCompositeSeconds")?.toInt() ?: 1801
            require(seconds in 301..3600)
            var first = 0L
            var physical = alphaId()
            while (first < seconds * 16000L) {
                val count = minOf(80_000L, seconds * 16000L - first)
                if (first % 9_600_000L == 0L) physical = alphaId()
                alphaValue(
                    session.writer.append(
                        AudioStorageUnitIdentity(
                            identity,
                            alphaId(),
                            (first / 80_000).toInt(),
                            first,
                            physical,
                            (first / 9_600_000L) * 9_600_000L,
                            first % 9_600_000L,
                        ),
                        AudioFormat.PCM,
                        ByteArray((count * 2).toInt()),
                    )
                )
                first += count
            }
            alphaValue(session.writer.finalize(identity))
            val source =
                (alphaValue(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val local = alphaId()
            alphaValue(session.transcripts.begin(source, local, "en", 1))
            val original =
                alphaValue(session.transcripts.finish(source, local, "old local", "synthetic", 2))
            alphaValue(
                session.transcripts.edit(identity, local, original.revision, "owner correction", 3)
            )
            val job = alphaId()
            val now = System.currentTimeMillis()
            val spec =
                PagedTranscriptSpec(
                    source,
                    job,
                    "CLOUD",
                    "en",
                    "standard-batch:eu-central-1:en-US",
                    "cloud-balanced-1800-v1",
                    28_800_000,
                    now,
                    CloudConsent(grantedAt = now, destination = endpoint),
                )
            alphaValue(session.transcripts.paged.begin(spec))
            val result =
                alphaValue(
                    CloudCompositeRun(api, EncryptedCloudCancellations(context), pause = {}).run(
                        session,
                        identity,
                        job,
                        RecognitionAttempt(),
                    ) {}
                )
            assertEquals("owner correction", result.rendered(local))
            assertEquals(local, result.activeId)
            assertEquals("CLOUD", result.versions.last().engine)
            assertEquals(spec.partCount, result.versions.last().paged!!.partCount)
            val progress = alphaValue(session.transcripts.paged.progress(identity, job))!!
            assertTrue(progress.published)
            assertEquals(spec.partCount, progress.completedParts)
            assertEquals(
                "Synthetic test.",
                alphaValue(session.transcripts.paged.page(identity, job, null))!!.text,
            )
            val large =
                OriginalAudioReference(
                    source.version,
                    source.identity,
                    source.digest,
                    1_382_400_000L,
                )
            val quote = api.quoteRun(spec.copy(source = large, jobId = alphaId()))
            assertTrue(quote.enabled)
            assertFalse(quote.affordable)
            assertEquals(43_680_000L, quote.requiredMicroUsd)
        }
    }
}
