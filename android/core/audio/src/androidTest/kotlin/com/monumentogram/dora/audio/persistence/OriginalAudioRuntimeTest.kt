package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OriginalAudioRuntimeTest {
    @Test
    fun retryableQuarantineNeverPermanentlyDisablesFinalizedOriginal() {
        val f = EncryptedAudioVaultFaultFixture()
        val ref =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                reference(vault, f.audio)
            }
        val original =
            java.io.File(
                f.run(),
                com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
                    .microfileCiphertext(0UL),
            )
        java.io
            .File(
                f.run(),
                com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
                    .microfileCiphertext(1UL),
            )
            .writeBytes(original.readBytes())
        f.open(create = false).use { vault ->
            assertEquals(AudioResult.Failed(AudioFailure.INCOMPLETE), vault.originals.inspect(ref))
            repeat(2) { success(vault.writer.reconcile(f.audio)) }
            assertEquals(ref, reference(vault, f.audio))
        }
        f.open(create = false).use { assertEquals(ref, reference(it, f.audio)) }
    }

    @Test
    fun competingSourceVersionCannotSilentlySupersedeOrAdmitOldWork() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            success(vault.writer.finalize(f.audio))
            val ref = reference(vault, f.audio)
            val other =
                f.audio.copy(
                    assetId =
                        com.monumentogram.dora.model.alpha.AudioAssetId(
                            EncryptedAudioVaultFaultFixture.id()
                        )
                )
            success(vault.writer.create(other))
            assertEquals(
                AudioResult.Failed(AudioFailure.COLLISION),
                vault.originals.withAvailable(ref) { fail("Ambiguous current source") },
            )
            assertEquals(AudioResult.Failed(AudioFailure.COLLISION), vault.originals.acquire(other))
            f.exact(vault, finalized = true)
        }
    }

    @Test
    fun finalizeIntentExcludesReferenceAndInterruptedFinalizeReconcilesExactly() {
        val f = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dependencies =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : com.monumentogram.dora.audio.EncryptedAudioCatalog by real {
                        override fun reserve(
                            expected: com.monumentogram.dora.audio.StoredAudioAsset,
                            intent: com.monumentogram.dora.audio.AudioIntent,
                        ): Boolean {
                            val accepted = real.reserve(expected, intent)
                            if (
                                intent is com.monumentogram.dora.audio.AudioIntent.Finalize &&
                                    accepted
                            ) {
                                entered.countDown()
                                check(release.await(10, TimeUnit.SECONDS))
                                error("Synthetic finalization interruption")
                            }
                            return accepted
                        }
                    }
                }
            )
        f.open(dependencies = dependencies).use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            val finalizer = thread {
                assertTrue(vault.writer.finalize(f.audio) is AudioResult.Failed)
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            try {
                assertEquals(
                    AudioResult.Failed(AudioFailure.BUSY),
                    vault.originals.acquire(f.audio),
                )
            } finally {
                release.countDown()
                finalizer.join(10_000)
            }
            assertFalse(finalizer.isAlive)
            assertEquals(
                AudioResult.Failed(AudioFailure.UNCERTAIN),
                vault.originals.acquire(f.audio),
            )
        }
        f.open(create = false).use { vault ->
            success(vault.writer.reconcile(f.audio))
            val ref = reference(vault, f.audio)
            repeat(2) {
                success(vault.writer.reconcile(f.audio))
                assertEquals(ref, reference(vault, f.audio))
            }
        }
    }

    @Test
    fun uncertainFinalizationEndRequiresReopenBeforeReferenceAdmission() {
        val f = EncryptedAudioVaultFaultFixture()
        val fault = TransactionFault("FINALIZE_CAS", true)
        f.open(dependencies = fault.dependencies()).use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            assertTrue(vault.writer.finalize(f.audio) is AudioResult.Failed)
            assertTrue(fault.fired)
            assertEquals(
                AudioResult.Failed(AudioFailure.UNCERTAIN),
                vault.originals.acquire(f.audio),
            )
        }
        f.open(create = false).use { vault ->
            val ref = reference(vault, f.audio)
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.Available(ref)),
                vault.originals.inspect(ref),
            )
        }
    }

    @Test
    fun exactOriginalSurvivesOfflineReopenWithoutLocalOrCloudEngine() {
        val f = EncryptedAudioVaultFaultFixture()
        val reference =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.NotFinalized),
                    vault.originals.acquire(f.audio),
                )
                success(vault.writer.finalize(f.audio))
                reference(vault, f.audio)
            }
        f.open(create = false).use { vault ->
            assertEquals(reference, reference(vault, f.audio))
            assertEquals(
                AudioResult.Failed(AudioFailure.UNAVAILABLE),
                vault.originals.withAvailable(reference) { error("Synthetic Cloud failure") },
            )
            val borrowed = mutableListOf<ByteArray>()
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.Available(reference)),
                vault.originals.extract(reference) { _, bytes ->
                    assertTrue(f.pcm.contentEquals(bytes))
                    borrowed += bytes
                },
            )
            assertTrue(borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
            f.exact(vault, finalized = true)
        }
        f.scan()
    }

    @Test
    fun deletionAndRestartFenceExactReferenceAndLateWorkWithoutTextCascade() {
        val f = EncryptedAudioVaultFaultFixture()
        val text = listOf("synthetic text", "synthetic edit")
        val reference =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                val ref = reference(vault, f.audio)
                success(vault.deleteAudio(f.audio))
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    vault.originals.acquire(f.audio),
                )
                ref
            }
        f.open(create = false).use { vault ->
            repeat(2) {
                success(vault.retryDeletion(f.audio))
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    vault.originals.withAvailable(reference) { fail("Late callback") },
                )
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    vault.originals.extract(reference) { _, _ -> fail("Deleted bytes") },
                )
            }
            assertTrue(vault.writer.create(f.audio) is AudioResult.Failed)
        }
        assertEquals(listOf("synthetic text", "synthetic edit"), text)
        f.scan()
    }

    @Test
    fun referenceReadAndDependentRegistrationSerializeAgainstDeletionFence() {
        val f = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val deletion = AtomicBoolean(false)
        val dependencies =
            EncryptedAudioVault.Dependencies(
                deletionStep = {
                    if (it == "TOMBSTONE") {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            )
        f.open(dependencies = dependencies).use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            success(vault.writer.finalize(f.audio))
            val ref = reference(vault, f.audio)
            val worker = thread { deletion.set(vault.deleteAudio(f.audio) is AudioResult.Value) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            try {
                repeat(2) {
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        vault.originals.extract(ref) { _, _ -> fail("Deletion race") },
                    )
                }
                assertEquals(
                    AudioResult.Failed(AudioFailure.BUSY),
                    vault.originals.withAvailable(ref) { fail("Registration race") },
                )
            } finally {
                release.countDown()
                worker.join(10_000)
            }
            assertFalse(worker.isAlive)
            assertTrue(deletion.get())
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                vault.originals.inspect(ref),
            )
        }
    }

    @Test
    fun lockRevocationDuringExactReadCannotAdmitWorkOrPersistPermanentLoss() {
        val f = EncryptedAudioVaultFaultFixture()
        val locked = AtomicBoolean(false)
        val result =
            EncryptedAudioVault.open(
                f.context,
                true,
                { if (locked.get()) throw AppLockedException() },
                { it() },
            )
        val ref =
            (result as AudioResult.Value).value.use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                val reference = reference(vault, f.audio)
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    vault.originals.extract(reference) { _, _ -> locked.set(true) },
                )
                reference
            }
        f.open(create = false).use { assertEquals(ref, reference(it, f.audio)) }
    }

    companion object {
        internal fun reference(vault: EncryptedAudioVault, identity: AudioIdentity) =
            ((vault.originals.acquire(identity) as AudioResult.Value).value
                    as OriginalAudioStatus.Available)
                .reference
    }
}
