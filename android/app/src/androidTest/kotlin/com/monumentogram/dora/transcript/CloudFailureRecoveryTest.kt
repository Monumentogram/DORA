@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import androidx.test.core.app.ActivityScenario
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.CloudConsent
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Real encrypted storage and runner; only the remote service is replaced. No AWS calls. */
class CloudFailureRecoveryTest {
    @Test fun networkLossBeforeSendPreservesLocalAndRetryUsesSameAction() = exercise("REGISTER")

    @Test fun lostFinalUploadReplyDoesNotDuplicateCloudResult() = exercise("UPLOAD_REPLY")

    @Test fun pollingDisconnectResumesExistingProcessing() = exercise("POLL")

    private fun exercise(fault: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var session = alphaSession(scenario)
            val fixture = AlphaFixture(session)
            val api = FaultApi(fixture, fault)
            val cancellations =
                object : CloudCancellations {
                    override fun blocked(id: String, destination: String) = false

                    override fun record(id: String, destination: String) =
                        error("Unexpected cancellation")

                    override fun actions(destination: String) = emptyList<String>()
                }
            fun run() =
                CloudRecognitionRun(api, cancellations, pause = {}).run(
                    session,
                    fixture.identity,
                    fixture.cloud,
                    RecognitionAttempt(),
                ) {}
            assertEquals("NETWORK", assertThrows(CloudFailure::class.java) { run() }.code)
            val failed = alphaValue(fixture.session.transcripts.load(fixture.identity))!!
            assertEquals("FAILED", failed.jobs.last().status)
            assertEquals(1, failed.versions.size)
            assertEquals("Правка владельца 😀", failed.rendered(fixture.local))
            scenario.recreate()
            session = alphaSession(scenario)
            assertEquals(failed, alphaValue(session.transcripts.load(fixture.identity)))
            val done = alphaValue(run())
            assertEquals(2, done.versions.size)
            assertEquals(fixture.local, done.activeId)
            assertEquals("Правка владельца 😀", done.rendered(fixture.local))
            assertEquals(fixture.result(), done.versions.single { it.id == fixture.cloud }.cloud)
            // Late duplicate delivery must be idempotent in the real repository.
            assertEquals(
                done,
                alphaValue(
                    session.transcripts.finishCloud(
                        fixture.source,
                        fixture.cloud,
                        fixture.result(),
                    )
                ),
            )
            assertEquals(setOf(fixture.cloud), api.actions)
            val audio = ByteArrayOutputStream()
            alphaValue(session.originals.extract(fixture.source) { _, bytes -> audio.write(bytes) })
            assertArrayEquals(fixture.pcm, audio.toByteArray())
        }
    }

    private class FaultApi(private val fixture: AlphaFixture, private val fault: String) :
        CloudApi {
        override val endpoint = "https://dora.invalid"
        val actions = mutableSetOf<String>()
        private var injected = false
        private var uploaded = 0
        private var phase = "UPLOADING"

        private fun fail(at: String) {
            if (fault == at && !injected) {
                injected = true
                throw CloudFailure("NETWORK")
            }
        }

        private fun state() =
            CloudJobState(
                fixture.cloud,
                phase,
                uploaded,
                false,
                false,
                if (phase == "COMPLETED") fixture.result() else null,
            )

        override fun register(source: OriginalAudioReference) {
            assertEquals(fixture.source, source)
            fail("REGISTER")
        }

        override fun create(
            source: OriginalAudioReference,
            id: String,
            language: String,
            consent: CloudConsent,
        ): CloudJobState {
            assertEquals(fixture.cloud, id)
            actions.add(id)
            return state()
        }

        override fun upload(
            id: String,
            offset: Int,
            bytes: ByteArray,
            final: Boolean,
            cancelled: () -> Boolean,
        ): CloudJobState {
            assertEquals(fixture.cloud, id)
            assertEquals(uploaded, offset)
            uploaded += bytes.size
            if (final) {
                phase = "IN_PROGRESS"
                fail("UPLOAD_REPLY")
            }
            return state()
        }

        override fun status(id: String): CloudJobState {
            assertEquals(fixture.cloud, id)
            fail("POLL")
            phase = "COMPLETED"
            return state()
        }

        override fun retry(id: String, cancelled: () -> Boolean): CloudJobState =
            error("No second provider attempt")

        override fun cancel(id: String): CloudJobState = error("No cancellation")

        override fun abortTransfer() = Unit
    }
}
