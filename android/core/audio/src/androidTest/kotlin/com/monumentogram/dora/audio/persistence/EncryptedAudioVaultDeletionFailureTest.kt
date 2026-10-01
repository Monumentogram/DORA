package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAudioVaultDeletionFailureTest {
    @Test
    @Suppress("LongMethod") // Immediate and reopened assertions share the phase fixture.
    fun interruptedDurableDeletionRetriesOnlyRemainingVerifiedSteps() {
        for (phase in
            listOf("TOMBSTONE", "KEY_REMOVED", "FILE_REMOVED", "DIRECTORY_SYNC", "COMPLETE")) {
            val fixture = EncryptedAudioVaultFaultFixture()
            val other =
                fixture.audio.copy(
                    assetId =
                        com.monumentogram.dora.model.alpha.AudioAssetId(
                            EncryptedAudioVaultFaultFixture.id()
                        )
                )
            val otherUnit =
                fixture.unit.copy(audio = other, unitId = EncryptedAudioVaultFaultFixture.id())
            var fired = false
            var deleting = false
            val completedRemovals = mutableListOf<String>()
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    deletionStep = { selected ->
                        deleting = true
                        if (selected == phase && !fired) {
                            fired = true
                            error("SYNTHETIC_DELETE_FAILURE")
                        }
                        if (selected == "KEY_REMOVED" || selected == "FILE_REMOVED")
                            completedRemovals += selected
                    },
                    deletionFsync = { real ->
                        { descriptor ->
                            if (deleting && phase == "DIRECTORY_SYNC" && !fired) {
                                fired = true
                                error("SYNTHETIC_SYNC_FAILURE")
                            }
                            real(descriptor)
                        }
                    },
                )
            fixture.open(dependencies = dependencies).use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(other))
                EncryptedAudioVaultFaultFixture.success(fixture.append(vault, otherUnit))
                assertEquals(
                    AudioResult.Failed(AudioFailure.UNAVAILABLE),
                    vault.deleteAudio(fixture.audio),
                )
                assertTrue(phase, fired)
                val state = vault.sourceState(fixture.audio)
                assertTrue(
                    phase,
                    state is AudioResult.Value && state.value is AudioSourceState.Deleting,
                )
                assertTrue(
                    vault.reader.extract(fixture.audio) { _, _ ->
                        error("Deleting source delivered")
                    } is AudioResult.Failed
                )
                fixture.scan()
            }
            val retried = mutableListOf<String>()
            fixture
                .open(false, EncryptedAudioVault.Dependencies(deletionStep = { retried += it }))
                .use { vault ->
                    assertTrue(
                        (vault.sourceState(fixture.audio) as AudioResult.Value).value
                            is AudioSourceState.Deleting
                    )
                    EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
                    assertEquals(
                        AudioResult.Value(AudioSourceState.UserDeleted),
                        vault.sourceState(fixture.audio),
                    )
                    EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
                    assertTrue(
                        vault.reader.extract(other) { _, pcm ->
                            assertTrue(fixture.pcm.contentEquals(pcm))
                        } is AudioResult.Value
                    )
                    assertTrue(fixture.append(vault) is AudioResult.Failed)
                    if (phase == "FILE_REMOVED" || phase == "COMPLETE")
                        assertFalse("Verified key removal not repeated", "KEY_REMOVED" in retried)
                    fixture.scan()
                }
            assertFalse(fixture.run().exists())
        }
    }

    @Test
    fun actualFinalDeletionTransactionCompletionBeforeAndAfterRemainTruthful() {
        for (after in listOf(false, true)) {
            val fixture = EncryptedAudioVaultFaultFixture()
            val fault = TransactionFault("DELETE_COMPLETE", after)
            fixture
                .open(
                    dependencies =
                        EncryptedAudioVault.Dependencies(
                            helperFactory = fault::factory,
                            deletionStep = { if (it == "COMPLETE") fault.arm("DELETE_COMPLETE") },
                        )
                )
                .use { vault ->
                    EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                    EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNAVAILABLE),
                        vault.deleteAudio(fixture.audio),
                    )
                    assertTrue(fault.fired)
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNCERTAIN),
                        vault.sourceState(fixture.audio),
                    )
                    fixture.scan()
                }
            fixture.open(false).use { vault ->
                val state = (vault.sourceState(fixture.audio) as AudioResult.Value).value
                if (after) {
                    assertEquals(AudioSourceState.UserDeleted, state)
                    EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
                    assertEquals(
                        AudioResult.Value(AudioSourceState.UserDeleted),
                        vault.sourceState(fixture.audio),
                    )
                } else {
                    assertTrue(state is AudioSourceState.Deleting)
                    // Deliberately unended native transaction remains write-locked until process
                    // death.
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNAVAILABLE),
                        vault.retryDeletion(fixture.audio),
                    )
                    assertTrue(vault.sourceState(fixture.audio) is AudioResult.Failed)
                }
                assertFalse(fixture.run().exists())
                fixture.scan()
            }
        }
    }

    @Test
    fun explicitDeletionIncludesPendingAppendAndRetainedEncryptedRemainder() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture
            .open(
                dependencies =
                    EncryptedAudioVault.Dependencies(
                        candidateStorage = { CandidateFault(it, WriteFault.OPEN) }
                    )
            )
            .use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), fixture.append(vault))
            }
        fixture.open(false).use { vault ->
            assertTrue(vault.writer.reconcile(fixture.audio) is AudioResult.Failed)
            val retained =
                java.io.File(
                    fixture.context.noBackupFilesDir,
                    "dora-vault-v1/poc-recovery/v1/quarantine",
                )
            assertTrue(
                "Uncommitted encrypted envelope is retained",
                retained.walkTopDown().any { it.isFile },
            )
            assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault))
            fixture.scan()
            EncryptedAudioVaultFaultFixture.success(vault.deleteAudio(fixture.audio))
            assertFalse(fixture.run().exists())
            assertFalse(java.io.File(retained, fixture.unit.unitId).exists())
        }
        fixture.open(false).use { vault ->
            assertEquals(
                AudioResult.Value(AudioSourceState.UserDeleted),
                vault.sourceState(fixture.audio),
            )
            EncryptedAudioVaultFaultFixture.success(vault.retryDeletion(fixture.audio))
            assertTrue(fixture.append(vault) is AudioResult.Failed)
        }
    }
}
