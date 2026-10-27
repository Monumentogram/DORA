package com.monumentogram.dora.audio.persistence.runtime

import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException
import com.monumentogram.dora.audio.persistence.auth.LocalAccessSession
import com.monumentogram.dora.audio.recording.RecordingRecovery
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeCoordinatorTest {
    @Test
    fun cloudRuntimeHandleForwardsEveryOperationAndFencesRevocation() {
        val lock = LocalAccessSession { true }
        val vault = FixtureVault()
        val source = OriginalAudioReference(1, identity, "a".repeat(64), 16000)
        val id = "00000000-0000-0000-0000-000000000004"
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                val port = session.transcripts
                val grant = com.monumentogram.dora.audio.transcript.CloudConsent(grantedAt = 1)
                assertTrue(port.beginCloud(source, id, "ru", grant, 1) is AudioResult.Value)
                assertTrue(port.resumeCloud(identity, id) is AudioResult.Value)
                val output =
                    com.monumentogram.dora.audio.transcript.CloudTranscriptResult(
                        "cloud",
                        "amazon-transcribe",
                        "standard-batch:eu-central-1:ru-RU",
                        "synthetic",
                        source.digest,
                        1,
                        2,
                    )
                val finished = port.finishCloud(source, id, output) as AudioResult.Value
                assertEquals("CLOUD", finished.value.versions.single().engine)
                val revoked = port.revokeCloud(identity, id) as AudioResult.Value
                assertTrue(revoked.value.jobs.single().consent!!.revoked)
                lock.revoke()
                runtime.revoke()
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    port.revokeCloud(identity, id),
                )
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    port.resumeCloud(identity, id),
                )
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    port.finishCloud(source, id, output),
                )
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    port.beginCloud(source, id, "ru", grant, 1),
                )
            }
    }

    @Test
    fun transcriptDeliveryAndOldHandleRespectRevocation() {
        val lock = LocalAccessSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                assertEquals(AudioResult.Value(null), session.transcripts.load(identity))
                vault.onTranscriptLoad = {
                    lock.revoke()
                    runtime.revoke()
                }
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    session.transcripts.load(identity),
                )
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), session.transcripts.history())
                assertEquals(2, vault.transcriptReads)
            }
    }

    @Test
    fun runtimeHandlePreservesLogicalCreateAndRevocation() {
        val lock = LocalAccessSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                assertEquals(
                    AudioResult.Value(Unit),
                    session.writer.createLogicalRecording(identity),
                )
                assertEquals(1, vault.logicalCreates)
                assertEquals(0, vault.writes)
                lock.revoke()
                runtime.revoke()
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    session.writer.createLogicalRecording(identity),
                )
                assertEquals(1, vault.logicalCreates)
            }
    }

    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    private fun authorize(lock: LocalAccessSession): LocalAccessSession.Authorization {
        lock.resume()
        return lock.capture()
    }

    @Test
    fun revocationAfterSuccessfulPromptBeforeCaptureCompletesLockedExactlyOnce() {
        val lock = LocalAccessSession { true }
        authorize(lock) // The real session state has already accepted successful proof.
        lock.revoke() // Revocation wins before the subsequent capture, without changing auth
        // policy.
        var opens = 0
        val completions = mutableListOf<AudioAvailability>()
        AudioRuntimeCoordinator({ false }) { _, _ ->
                opens++
                AudioResult.Value(FixtureVault())
            }
            .use { runtime ->
                val call = runCatching {
                    runtime.openLocal(
                        AudioOpenMode.CREATE_NEW,
                        lock::capture,
                        completions::add,
                    )
                }
                assertTrue("Capture revocation must not escape the completion path", call.isSuccess)
                assertEquals(listOf(AudioAvailability.Locked), completions)
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertEquals(0, opens)
            }
    }

    @Test
    fun successfulPromptHandoffCapturesOnceAndOpensNormally() {
        val lock = LocalAccessSession { true }
        authorize(lock)
        var captures = 0
        var opens = 0
        val done = CountDownLatch(1)
        AudioRuntimeCoordinator({ false }) { _, _ ->
                opens++
                AudioResult.Value(FixtureVault())
            }
            .use { runtime ->
                runtime.openLocal(
                    AudioOpenMode.OPEN_EXISTING,
                    {
                        captures++
                        lock.capture()
                    },
                ) {
                    done.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(runtime.availability is AudioAvailability.Available)
                assertEquals(1, captures)
                assertEquals(1, opens)
            }
    }

    @Test
    fun oldHandleNeverRevivesAfterReauthentication() {
        val lock = LocalAccessSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val first = opened(runtime, authorize(lock))
                assertEquals(AudioResult.Value(Unit), first.writer.create(identity))
                lock.revoke()
                runtime.revoke()
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.writer.create(identity))
                val second = opened(runtime, authorize(lock))
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.writer.create(identity))
                assertEquals(AudioResult.Value(Unit), second.writer.create(identity))
            }
    }

    @Test
    fun pendingOpenRevokedBeforePublicationClosesAndStaysLocked() {
        val lock = LocalAccessSession { true }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ ->
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                AudioResult.Value(vault)
            }
            .use { runtime ->
                runtime.open(AudioOpenMode.OPEN_EXISTING, authorize(lock)) { complete.countDown() }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                lock.pause()
                runtime.revoke()
                assertEquals(AudioAvailability.Locked, runtime.availability)
                release.countDown()
                assertTrue(complete.await(5, TimeUnit.SECONDS))
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertTrue(vault.closed)
            }
    }

    @Test
    fun borrowedCallbackCannotReenterWorkerAndMainThreadCannotPerformIo() {
        val lock = LocalAccessSession { true }
        var main = false
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ main }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                session.reader.extract(identity) { _, _ ->
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        session.writer.create(identity),
                    )
                }
                main = true
                assertEquals(AudioResult.Failed(AudioFailure.BUSY), session.writer.create(identity))
                assertEquals(0, vault.writes)
            }
    }

    @Test
    fun retryCannotCreateConfirmationAndTypedFailureIsPreserved() {
        val lock = LocalAccessSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                assertEquals(
                    AudioResult.Failed(AudioFailure.INVALID_INPUT),
                    session.retryRemainingDeletion(identity),
                )
                assertEquals(0, vault.deletions)
                assertEquals(VaultKeyProtection.SOFTWARE, session.protection)
            }
        AudioRuntimeCoordinator({ false }) { _, _ ->
                AudioResult.Failed(AudioFailure.KEY_INVALIDATED)
            }
            .use { runtime ->
                val done = CountDownLatch(1)
                runtime.open(AudioOpenMode.OPEN_EXISTING, authorize(lock)) { done.countDown() }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertEquals(
                    AudioAvailability.Failed(AudioFailure.KEY_INVALIDATED),
                    runtime.availability,
                )
            }
    }

    private fun opened(
        runtime: AudioRuntimeCoordinator,
        authorization: LocalAccessSession.Authorization,
    ): ProductAudioSession {
        val done = CountDownLatch(1)
        runtime.open(AudioOpenMode.CREATE_NEW, authorization) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return (runtime.availability as AudioAvailability.Available).session
    }

    @Test
    fun originalReferenceUsesGenerationFenceAndDependentCallbackCannotReenter() {
        val lock = LocalAccessSession { true }
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(FixtureVault()) }
            .use { runtime ->
                val first = opened(runtime, authorize(lock))
                val acquired = first.originals.acquire(identity) as AudioResult.Value
                val ref = (acquired.value as OriginalAudioStatus.Available).reference
                var callbacks = 0
                assertEquals(
                    acquired,
                    first.originals.withAvailable(ref) {
                        callbacks++
                        assertEquals(
                            AudioResult.Failed(AudioFailure.BUSY),
                            first.originals.inspect(ref),
                        )
                    },
                )
                lock.revoke()
                runtime.revoke()
                val second = opened(runtime, authorize(lock))
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    first.originals.withAvailable(ref) { callbacks++ },
                )
                assertEquals(acquired, second.originals.inspect(ref))
                assertEquals(1, callbacks)
            }
    }

    @Test
    fun recoveryScanRevokedDuringReadCompletesLocked() {
        val lock = LocalAccessSession { true }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        var delivered: AudioResult<List<RecordingRecovery>>? = null
        val vault = FixtureVault {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            AudioResult.Value(
                listOf(
                    RecordingRecovery(
                        identity,
                        AudioReadSummary(identity, 160, AudioCompletion.PARTIAL_RECOVERED),
                        null,
                        true,
                        1,
                    )
                )
            )
        }
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                try {
                    runtime.recordingRecoveryPage(session, "") {
                        delivered = it
                        done.countDown()
                    }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    lock.revoke()
                    runtime.revoke()
                } finally {
                    release.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), delivered)
                assertEquals(0, vault.writes)
            }
    }

    @Test
    fun recoveredPageQueuedForUiCannotPassCurrentSessionFenceAfterLock() {
        val lock = LocalAccessSession { true }
        val done = CountDownLatch(1)
        var delivered: AudioResult<List<RecordingRecovery>>? = null
        val vault = FixtureVault { AudioResult.Value(emptyList()) }
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                runtime.recordingRecoveryPage(session, "") {
                    delivered = it
                    done.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(delivered is AudioResult.Value)
                lock.revoke()
                runtime.revoke()
                // AndroidProductAudioRuntime applies this fence on main before invoking UI
                // completion.
                assertThrows(LocalAccessDeniedException::class.java) {
                    runtime.requireCurrent(session)
                }
                assertEquals(0, vault.writes)
            }
    }

    private class FixtureVault(
        private val recovery: () -> AudioResult<List<RecordingRecovery>> = {
            AudioResult.Value(emptyList())
        }
    ) : RuntimeVault {
        var transcriptReads = 0
        var onTranscriptLoad: () -> Unit = {}
        override val transcripts =
            object :
                com.monumentogram.dora.audio.transcript.TranscriptPort by com.monumentogram.dora
                    .audio
                    .transcript
                    .UnavailableTranscriptPort {
                private var cloudDoc: com.monumentogram.dora.audio.transcript.TranscriptDocument? =
                    null

                override fun beginCloud(
                    source: OriginalAudioReference,
                    jobId: String,
                    language: String,
                    consent: com.monumentogram.dora.audio.transcript.CloudConsent,
                    createdAt: Long,
                ) =
                    AudioResult.Value(
                        com.monumentogram.dora.audio.transcript.TranscriptReducer.beginCloud(
                                cloudDoc,
                                source,
                                jobId,
                                language,
                                consent,
                                createdAt,
                            )
                            .also { cloudDoc = it }
                    )

                override fun resumeCloud(identity: AudioIdentity, jobId: String) =
                    AudioResult.Value(
                        com.monumentogram.dora.audio.transcript.TranscriptReducer.resumeCloud(
                                checkNotNull(cloudDoc),
                                jobId,
                            )
                            .also { cloudDoc = it }
                    )

                override fun revokeCloud(identity: AudioIdentity, jobId: String) =
                    AudioResult.Value(
                        com.monumentogram.dora.audio.transcript.TranscriptReducer.revokeCloud(
                                checkNotNull(cloudDoc),
                                jobId,
                            )
                            .also { cloudDoc = it }
                    )

                override fun finishCloud(
                    source: OriginalAudioReference,
                    jobId: String,
                    result: com.monumentogram.dora.audio.transcript.CloudTranscriptResult,
                ) =
                    AudioResult.Value(
                        com.monumentogram.dora.audio.transcript.TranscriptReducer.finishCloud(
                                checkNotNull(cloudDoc),
                                jobId,
                                result,
                            )
                            .also { cloudDoc = it }
                    )

                override fun load(
                    identity: AudioIdentity
                ): AudioResult<com.monumentogram.dora.audio.transcript.TranscriptDocument?> {
                    transcriptReads++
                    onTranscriptLoad()
                    return AudioResult.Value(null)
                }
            }

        override fun recordingRecoveryPage(after: String) = recovery()

        override val originals =
            object : OriginalAudioPort {
                override fun acquire(identity: AudioIdentity) =
                    inspect(OriginalAudioReference(1, identity, "0".repeat(64), 1))

                override fun inspect(reference: OriginalAudioReference) =
                    AudioResult.Value(OriginalAudioStatus.Available(reference))

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    reader.extract(reference.identity, consume)
                    return inspect(reference)
                }

                override fun withAvailable(
                    reference: OriginalAudioReference,
                    action: () -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    action()
                    return inspect(reference)
                }
            }

        var closed = false
        var logicalCreates = 0
        var writes = 0
        var deletions = 0
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
                ) = AudioResult.Value(Unit)

                override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

                override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
            }
        override val reader =
            object : ProductAudioReaderPort {
                override fun extract(
                    identity: AudioIdentity,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<AudioReadSummary> {
                    val bytes = ByteArray(2)
                    try {
                        consume(0, bytes)
                    } finally {
                        bytes.fill(0)
                    }
                    return AudioResult.Value(
                        AudioReadSummary(identity, 1, AudioCompletion.FINALIZED)
                    )
                }
            }

        override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> =
            AudioResult.Value(AudioSourceState.Missing)

        override fun deleteConfirmed(identity: AudioIdentity): AudioResult<Unit> {
            deletions++
            return AudioResult.Value(Unit)
        }

        override fun retryDeletion(identity: AudioIdentity): AudioResult<Unit> =
            AudioResult.Failed(AudioFailure.INVALID_INPUT)

        override fun close() {
            closed = true
        }
    }
}
