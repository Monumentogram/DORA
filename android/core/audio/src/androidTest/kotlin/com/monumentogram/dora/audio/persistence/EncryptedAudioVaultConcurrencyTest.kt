package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.StoredAudioAsset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAudioVaultConcurrencyTest {
    @Test fun sameUnitWritersSerializeAfterActualReservation() = heldWriter("SAME")

    @Test fun differentUnitAppendsSerializeAndRetryInOrder() = heldWriter("NEXT")

    @Test
    fun writerAndFinalizationSerializeWithoutPrematureFinalizedState() = heldWriter("FINALIZE")

    @Test fun readerAndAppendSerializeWithoutIntermediateMapping() = heldWriter("READ")

    @Suppress(
        "LongMethod",
        "NestedBlockDepth",
    ) // Keep bounded worker release and assertions in one scope.
    private fun heldWriter(contender: String) {
        val fixture = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val dependencies =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : EncryptedAudioCatalog by real {
                        override fun reserve(
                            expected: StoredAudioAsset,
                            intent: AudioIntent,
                        ): Boolean {
                            val reserved = real.reserve(expected, intent)
                            if (
                                reserved &&
                                    intent is AudioIntent.Append &&
                                    intent.identity.ordinal == 0
                            ) {
                                entered.countDown()
                                check(release.await(20, TimeUnit.SECONDS))
                            }
                            return reserved
                        }
                    }
                }
            )
        try {
            fixture.open(dependencies = dependencies).use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                val writer = executor.submit<AudioResult<Unit>> { fixture.append(vault) }
                try {
                    assertTrue(
                        "Writer owns lease and durable intent",
                        entered.await(20, TimeUnit.SECONDS),
                    )
                    val next =
                        fixture.unit.copy(
                            unitId = EncryptedAudioVaultFaultFixture.id(),
                            ordinal = 1,
                            firstFrame = 160,
                            sourceFrameOffset = 160,
                        )
                    val result =
                        when (contender) {
                            "SAME" -> fixture.append(vault)
                            "NEXT" -> fixture.append(vault, next)
                            "FINALIZE" -> vault.writer.finalize(fixture.audio)
                            else ->
                                vault.reader.extract(fixture.audio) { _, _ ->
                                    error("Uncommitted mapping delivered")
                                }
                        }
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), result)
                } finally {
                    release.countDown()
                }
                EncryptedAudioVaultFaultFixture.success(writer.get(20, TimeUnit.SECONDS))
                fixture.exact(vault)
                when (contender) {
                    "SAME" ->
                        assertEquals(
                            AudioResult.Failed(AudioFailure.INVALID_INPUT),
                            fixture.append(vault),
                        )
                    "NEXT" -> {
                        EncryptedAudioVaultFaultFixture.success(
                            fixture.append(
                                vault,
                                fixture.unit.copy(
                                    unitId = EncryptedAudioVaultFaultFixture.id(),
                                    ordinal = 1,
                                    firstFrame = 160,
                                    sourceFrameOffset = 160,
                                ),
                            )
                        )
                        fixture.exact(vault, 2)
                    }
                    "FINALIZE" -> {
                        EncryptedAudioVaultFaultFixture.success(
                            vault.writer.finalize(fixture.audio)
                        )
                        fixture.exact(vault, finalized = true)
                    }
                    else -> fixture.exact(vault)
                }
                fixture.scan()
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))
        }
    }

    @Test
    fun deletionAndReadWriteSerializeAndDurableFencePreventsResurrection() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            fixture.open().use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                val reader =
                    executor.submit<AudioResult<AudioReadSummary>> {
                        vault.reader.extract(fixture.audio) { _, _ ->
                            entered.countDown()
                            check(release.await(20, TimeUnit.SECONDS))
                        }
                    }
                try {
                    assertTrue(entered.await(20, TimeUnit.SECONDS))
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        vault.deleteAudio(fixture.audio),
                    )
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), fixture.append(vault))
                } finally {
                    release.countDown()
                }
                EncryptedAudioVaultFaultFixture.success(reader.get(20, TimeUnit.SECONDS))
                EncryptedAudioVaultFaultFixture.success(vault.deleteAudio(fixture.audio))
            }
            fixture.open(false).use { vault ->
                assertEquals(
                    AudioResult.Value(AudioSourceState.UserDeleted),
                    vault.sourceState(fixture.audio),
                )
                assertTrue(fixture.append(vault) is AudioResult.Failed)
                assertTrue(
                    vault.reader.extract(fixture.audio) { _, _ -> error("Deleted source") }
                        is AudioResult.Failed
                )
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))
        }
    }

    @Test
    fun duplicateRetryAfterRestartOwnsOneLeaseAndOneExactMapping() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture
            .open(
                dependencies =
                    EncryptedAudioVault.Dependencies(
                        catalog = { real ->
                            object : EncryptedAudioCatalog by real {
                                override fun compareAndSet(
                                    expected: StoredAudioAsset,
                                    next: StoredAudioAsset,
                                ): Boolean = error("SYNTHETIC_INTERRUPTION")
                            }
                        }
                    )
            )
            .use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), fixture.append(vault))
            }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val dependencies =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : EncryptedAudioCatalog by real {
                        override fun load(identity: AudioIdentity): StoredAudioAsset? {
                            val value = real.load(identity)
                            if (value?.pending is AudioIntent.Append) {
                                entered.countDown()
                                check(release.await(20, TimeUnit.SECONDS))
                            }
                            return value
                        }
                    }
                }
            )
        try {
            fixture.open(false, dependencies).use { vault ->
                val recovery =
                    executor.submit<AudioResult<Unit>> { vault.writer.reconcile(fixture.audio) }
                try {
                    assertTrue(entered.await(20, TimeUnit.SECONDS))
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        vault.writer.reconcile(fixture.audio),
                    )
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), fixture.append(vault))
                } finally {
                    release.countDown()
                }
                EncryptedAudioVaultFaultFixture.success(recovery.get(20, TimeUnit.SECONDS))
                repeat(2) {
                    EncryptedAudioVaultFaultFixture.success(vault.writer.reconcile(fixture.audio))
                }
                fixture.exact(vault)
                assertEquals(AudioResult.Failed(AudioFailure.INVALID_INPUT), fixture.append(vault))
                fixture.scan()
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))
        }
    }

    @Test
    fun durableDeletionLeaseBlocksReaderAndWriterThenRetryCompletesAfterRestart() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val dependencies =
            EncryptedAudioVault.Dependencies(
                deletionStep = { phase ->
                    if (phase == "TOMBSTONE") {
                        entered.countDown()
                        check(release.await(20, TimeUnit.SECONDS))
                        error("SYNTHETIC_DELETE_INTERRUPTION")
                    }
                }
            )
        try {
            fixture.open(dependencies = dependencies).use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                val deletion =
                    executor.submit<AudioResult<Unit>> { vault.deleteAudio(fixture.audio) }
                try {
                    assertTrue(entered.await(20, TimeUnit.SECONDS))
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), fixture.append(vault))
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        vault.reader.extract(fixture.audio) { _, _ -> error("Tombstone bypassed") },
                    )
                } finally {
                    release.countDown()
                }
                assertEquals(
                    AudioResult.Failed(AudioFailure.UNAVAILABLE),
                    deletion.get(20, TimeUnit.SECONDS),
                )
            }
            fixture.open(false).use { vault ->
                assertTrue(
                    (vault.sourceState(fixture.audio) as AudioResult.Value).value
                        is AudioSourceState.Deleting
                )
                assertTrue(fixture.append(vault) is AudioResult.Failed)
                assertTrue(
                    vault.reader.extract(fixture.audio) { _, _ -> error("Deletion fence bypassed") }
                        is AudioResult.Failed
                )
                EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
                assertEquals(
                    AudioResult.Value(AudioSourceState.UserDeleted),
                    vault.sourceState(fixture.audio),
                )
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))
        }
    }
}
