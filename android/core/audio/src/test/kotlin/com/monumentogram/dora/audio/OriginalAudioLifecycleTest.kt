package com.monumentogram.dora.audio

import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OriginalAudioLifecycleTest {
    @Test
    fun `incomplete reconciliation must recover without permanent loss`() {
        val f = Fixture()
        val ref = f.reference()
        f.failure = AudioFailure.INCOMPLETE
        assertEquals(AudioResult.Failed(AudioFailure.INCOMPLETE), f.lifecycle.inspect(ref))
        assertNull(f.loss)
        f.failure = null
        assertEquals(ref, f.reference())
    }

    @Test
    fun `partial and pending finalize never grant a reference`() {
        val f = Fixture()
        f.asset = f.asset.copy(finalization = null)
        assertEquals(AudioResult.Value(OriginalAudioStatus.NotFinalized), f.lifecycle.acquire(f.id))
        f.asset = f.asset.copy(pending = AudioIntent.Finalize(f.asset.segments))
        assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), f.lifecycle.acquire(f.id))
        assertNull(f.retained)
    }

    @Test
    fun `downstream failure and indefinite waiting preserve exact source`() {
        val f = Fixture()
        val reference = f.reference()
        assertEquals(
            AudioResult.Failed(AudioFailure.UNAVAILABLE),
            f.lifecycle.withAvailable(reference) { error("Synthetic downstream failure") },
        )
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.Available(reference)),
            f.lifecycle.inspect(reference),
        )
        assertEquals(reference, f.reference())
        assertNull(f.loss)
    }

    @Test
    fun `audio deletion fences registration retries late completion and preserves text`() {
        val f = Fixture()
        val reference = f.reference()
        val transcriptAndEdits = listOf("synthetic transcript", "synthetic edit")
        var accepted = 0
        assertTrue(f.lifecycle.withAvailable(reference) { accepted++ } is AudioResult.Value)
        f.source = AudioSourceState.Deleting(emptySet())
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.DeletionPending),
            f.lifecycle.withAvailable(reference) { accepted++ },
        )
        f.source = AudioSourceState.UserDeleted
        repeat(2) {
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                f.lifecycle.withAvailable(reference) { accepted++ },
            )
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                f.lifecycle.extract(reference) { _, _ -> fail("Deleted source delivered") },
            )
        }
        assertEquals(1, accepted)
        assertEquals(listOf("synthetic transcript", "synthetic edit"), transcriptAndEdits)
        assertEquals(reference, f.retained)
    }

    @Test
    fun `wrong digest or version cannot read or commit dependent work`() {
        val f = Fixture()
        val reference = f.reference()
        listOf(
                reference.copy(digest = "0".repeat(64)),
                reference.copy(version = 2),
                reference.copy(frames = 1),
            )
            .forEach {
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.StaleReference),
                    f.lifecycle.extract(it) { _, _ -> fail("Stale source delivered") },
                )
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.StaleReference),
                    f.lifecycle.withAvailable(it) { fail("Stale work accepted") },
                )
            }
    }

    @Test
    fun `temporary key or auth failure cannot become permanent loss`() {
        val f = Fixture()
        val reference = f.reference()
        listOf(
                AudioFailure.KEY_UNAVAILABLE,
                AudioFailure.LOCKED,
                AudioFailure.AUTHENTICATION_FAILED,
                AudioFailure.UNCERTAIN,
            )
            .forEach {
                f.failure = it
                assertEquals(AudioResult.Failed(it), f.lifecycle.inspect(reference))
                assertNull(f.loss)
            }
        f.failure = null
        assertEquals(reference, f.reference())
    }

    @Test
    fun `permanent key loss remains fenced even if a later reader claims success`() {
        val f = Fixture()
        val reference = f.reference()
        f.failure = AudioFailure.KEY_INVALIDATED
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.SourceUnavailable(AudioFailure.KEY_INVALIDATED)),
            f.lifecycle.inspect(reference),
        )
        f.failure = null
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.SourceUnavailable(AudioFailure.KEY_INVALIDATED)),
            f.lifecycle.withAvailable(reference) { fail("Resurrection") },
        )
    }

    @Test
    fun `concurrent readers and deletion share exact lease`() {
        val f = Fixture()
        val reference = f.reference()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.readHook = {
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        val result = AtomicReference<AudioResult<OriginalAudioStatus>>()
        val reader = thread { result.set(f.lifecycle.inspect(reference)) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            assertEquals(AudioResult.Failed(AudioFailure.BUSY), f.lifecycle.inspect(reference))
            assertNull(f.catalog.tryAcquire(f.id))
        } finally {
            release.countDown()
            reader.join(5_000)
        }
        assertFalse(reader.isAlive)
        assertEquals(AudioResult.Value(OriginalAudioStatus.Available(reference)), result.get())
    }

    @Test
    fun `revocation during reference authentication never returns authority`() {
        val f = Fixture()
        f.readHook = { f.locked = true }
        assertEquals(AudioResult.Failed(AudioFailure.LOCKED), f.lifecycle.acquire(f.id))
        assertNull(f.retained)
    }

    @Test
    fun `revocation before callback prevents dependent side effects`() {
        val f = Fixture()
        val reference = f.reference()
        f.readHook = { f.locked = true }
        assertEquals(
            AudioResult.Failed(AudioFailure.LOCKED),
            f.lifecycle.withAvailable(reference) { fail("Revoked callback") },
        )
        assertNull(f.loss)
    }

    private class Fixture {
        val owner = "00000000-0000-0000-0000-000000000001"
        val vault = "00000000-0000-0000-0000-000000000002"
        val id = AudioIdentity(RecordingId(owner), AudioAssetId(vault), owner)
        private val segment =
            StoredAudioSegment(AudioStorageUnitIdentity(id, owner, 0, 0), 2, Sha256Value.ZERO)
        var asset = StoredAudioAsset(id, listOf(segment), finalization = listOf(segment))
        var source: AudioSourceState? = null
        var retained: OriginalAudioReference? = null
        var loss: AudioFailure? = null
        var failure: AudioFailure? = null
        var locked = false
        var readHook: () -> Unit = {}
        private val held = AtomicReference<Thread?>()
        val catalog =
            object : EncryptedAudioCatalog {
                override fun create(identity: AudioIdentity) = false

                override fun tryAcquire(identity: AudioIdentity): AutoCloseable? =
                    if (held.compareAndSet(null, Thread.currentThread()))
                        AutoCloseable { check(held.compareAndSet(Thread.currentThread(), null)) }
                    else null

                override fun load(identity: AudioIdentity) = asset.takeIf {
                    it.identity == identity
                }

                override fun reserve(expected: StoredAudioAsset, intent: AudioIntent) = false

                override fun compareAndSet(expected: StoredAudioAsset, next: StoredAudioAsset) =
                    false
            }

        fun gate() {
            if (locked) throw AppLockedException()
        }

        val lifecycle =
            OriginalAudioLifecycle(
                owner,
                vault,
                catalog,
                { source },
                {
                    retained?.let {
                        check(it == OriginalAudioReferenceCodec.derive(owner, vault, asset))
                    }
                    loss
                },
                { ref, reason ->
                    gate()
                    check(retained == null || retained == ref)
                    retained = ref
                    if (reason != null) loss = reason
                },
                { snapshot, consume ->
                    readHook()
                    failure?.let { AudioResult.Failed(it) }
                        ?: run {
                            val bytes = ByteArray(4)
                            try {
                                consume(0, bytes)
                            } finally {
                                bytes.fill(0)
                            }
                            AudioResult.Value(
                                AudioReadSummary(snapshot.identity, 2, AudioCompletion.FINALIZED)
                            )
                        }
                },
                ::gate,
                { action ->
                    gate()
                    action()
                    gate()
                },
            )

        fun reference() =
            ((lifecycle.acquire(id) as AudioResult.Value).value as OriginalAudioStatus.Available)
                .reference
    }
}
