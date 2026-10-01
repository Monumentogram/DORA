package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.StoredAudioAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAudioVaultFailureTest {
    @Test
    fun publishedBeforeCatalogCommitReconcilesExactlyAfterReopen() {
        val fixture = EncryptedAudioVaultFaultFixture()
        var observed: StoredAudioAsset? = null
        val dependencies =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : EncryptedAudioCatalog by real {
                        override fun compareAndSet(
                            expected: StoredAudioAsset,
                            next: StoredAudioAsset,
                        ): Boolean {
                            observed = real.load(fixture.audio)
                            error("SYNTHETIC_PHASE_FAILURE")
                        }
                    }
                }
            )
        fixture.open(dependencies = dependencies).use { vault ->
            EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
            assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), fixture.append(vault))
            assertEquals(AudioIntent.Append(fixture.unit, 160), observed!!.pending)
            assertTrue(observed!!.segments.isEmpty())
            fixture.scan()
        }
        fixture.open(false).use { vault ->
            repeat(2) {
                EncryptedAudioVaultFaultFixture.success(vault.writer.reconcile(fixture.audio))
            }
            fixture.exact(vault)
            assertEquals(AudioResult.Failed(AudioFailure.INVALID_INPUT), fixture.append(vault))
            fixture.scan()
        }
    }

    @Test
    fun encryptedWritePhaseFailuresRetainDurableIntentWithoutReplacement() {
        for (phase in WriteFault.entries) {
            val fixture = EncryptedAudioVaultFaultFixture()
            lateinit var injected: CandidateFault
            var catalog: EncryptedAudioCatalog? = null
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    catalog = { it.also { catalog = it } },
                    candidateStorage = { CandidateFault(it, phase).also { injected = it } },
                )
            fixture.open(dependencies = dependencies).use { vault ->
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                assertEquals(
                    phase.name,
                    AudioResult.Failed(AudioFailure.UNCERTAIN),
                    fixture.append(vault),
                )
                assertTrue(phase.name, injected.fired)
                catalog!!.tryAcquire(fixture.audio)!!.use {
                    assertEquals(
                        AudioIntent.Append(fixture.unit, 160),
                        catalog!!.load(fixture.audio)!!.pending,
                    )
                }
                assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault))
                fixture.scan()
            }
            val artifacts =
                fixture
                    .run()
                    .walkTopDown()
                    .filter { it.isFile }
                    .associate { it.relativeTo(fixture.run()).path to it.readBytes().toList() }
            fixture.open(false).use { vault ->
                repeat(2) {
                    assertTrue(vault.writer.reconcile(fixture.audio) is AudioResult.Failed)
                }
                assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault))
                assertTrue(vault.writer.finalize(fixture.audio) is AudioResult.Failed)
                fixture.scan()
            }
            val retained =
                fixture.context.noBackupFilesDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .map { it.readBytes().toList() }
                    .toSet()
            assertTrue(
                "Original encrypted artifacts retained in active or quarantine storage",
                artifacts.values.all { it in retained },
            )
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Phase and completion-side matrix with scoped resources.
    fun actualTransactionCompletionMatrixReopensExactOrFenced() {
        for (phase in
            listOf(
                "APPEND_INTENT",
                "PUBLICATION",
                "APPEND_CAS",
                "FINALIZE_INTENT",
                "FINALIZE_CAS",
            )) {
            for (after in listOf(false, true)) {
                val fixture = EncryptedAudioVaultFaultFixture()
                val fault = TransactionFault(phase, after)
                fixture.open(dependencies = fault.dependencies()).use { vault ->
                    EncryptedAudioVaultFaultFixture.success(vault.writer.create(fixture.audio))
                    val result =
                        if (phase.startsWith("FINALIZE")) {
                            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
                            vault.writer.finalize(fixture.audio)
                        } else fixture.append(vault)
                    assertEquals(
                        "$phase/$after",
                        AudioResult.Failed(AudioFailure.UNCERTAIN),
                        result,
                    )
                    assertTrue("$phase/$after", fault.fired)
                    assertTrue(vault.writer.finalize(fixture.audio) is AudioResult.Failed)
                    fixture.scan()
                }
                assertReopenedTransaction(fixture, phase, after)
            }
        }
    }

    private fun assertReopenedTransaction(
        fixture: EncryptedAudioVaultFaultFixture,
        phase: String,
        after: Boolean,
    ) {
        fixture.open(false).use { vault ->
            if (phase == "APPEND_INTENT" || (phase == "PUBLICATION" && !after)) {
                assertTrue(
                    vault.reader.extract(fixture.audio) { _, _ ->
                        error("Unpublished source")
                    } is AudioResult.Failed
                )
            } else if (!after && phase.endsWith("CAS")) {
                // A Java exception before delegate end leaves the actual native transaction
                // unended. Orderly Room close does not release its write lock. Assert the
                // real fence; abrupt-process recovery is exercised by the host protocol.
                if (phase == "FINALIZE_CAS") fixture.exact(vault)
                else
                    assertEquals(
                        AudioResult.Failed(AudioFailure.CORRUPT),
                        vault.reader.extract(fixture.audio) { _, _ ->
                            error("Pending unit delivered")
                        },
                    )
                repeat(2) {
                    assertEquals(
                        AudioResult.Failed(AudioFailure.UNCERTAIN),
                        vault.writer.reconcile(fixture.audio),
                    )
                }
                assertEquals(
                    AudioResult.Failed(AudioFailure.UNCERTAIN),
                    vault.reader.extract(fixture.audio) { _, _ ->
                        error("Uncertain journal delivered")
                    },
                )
            } else {
                repeat(2) {
                    assertEquals(
                        "$phase/$after",
                        AudioResult.Value(Unit),
                        vault.writer.reconcile(fixture.audio),
                    )
                }
                fixture.exact(
                    vault,
                    finalized = phase == "FINALIZE_CAS" || (phase == "FINALIZE_INTENT" && after),
                )
            }
            fixture.scan()
        }
    }
}
